package circolareplus.platform

import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

private val localSpeechLock = Mutex()

actual fun isLocalAudioTranscriptionAvailable(): Boolean = Build.VERSION.SDK_INT >= 33 &&
    runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(AndroidAppContext.require()) }.getOrDefault(false)

private data class SpeechPcm(val bytes:ByteArray,val rate:Int)

/** Decode on the device, keeping the original compressed audio in the user's archive. */
private fun decodeSpeechAudio(audio:IncomingContent,checkActive:()->Unit):SpeechPcm {
    val input=File.createTempFile("aila-offline-speech-", ".audio", AndroidAppContext.require().cacheDir)
    val extractor=MediaExtractor()
    var codec:MediaCodec?=null
    try {
        input.writeBytes(audio.bytes);extractor.setDataSource(input.absolutePath)
        val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }?:error("Audio non riconosciuto")
        extractor.selectTrack(track)
        val format=extractor.getTrackFormat(track)
        if(format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION)<=600_000_000L)
        format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
        val decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!);codec=decoder
        decoder.configure(format,null,null,0);decoder.start()
        var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding=AudioFormat.ENCODING_PCM_16BIT
        val out=ByteArrayOutputStream();val info=MediaCodec.BufferInfo()
        var inputDone=false;var outputDone=false
        val deadline=android.os.SystemClock.elapsedRealtime()+30_000
        while(!outputDone) {
            checkActive()
            check(android.os.SystemClock.elapsedRealtime()<deadline){"Decodifica audio locale non disponibile"}
            if(!inputDone) {
                val index=decoder.dequeueInputBuffer(10_000)
                if(index>=0) {
                    val buffer=decoder.getInputBuffer(index)!!
                    val count=extractor.readSampleData(buffer,0)
                    if(count<0) {decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                    else {decoder.queueInputBuffer(index,0,count,extractor.sampleTime,0);extractor.advance()}
                }
            }
            when(val index=decoder.dequeueOutputBuffer(info,10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f=decoder.outputFormat;rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    require(encoding==AudioFormat.ENCODING_PCM_16BIT && channels in 1..8 && rate in 8000..192000)
                }
                else -> if(index>=0) {
                    val buffer=decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset);buffer.limit(info.offset+info.size)
                    while(buffer.remaining()>=channels*2) {
                        var sum=0;repeat(channels){sum+=buffer.short.toInt()}
                        val sample=sum/channels;out.write(sample and 255);out.write((sample shr 8) and 255)
                    }
                    require(out.size()<=64*1024*1024) { "Audio troppo lungo per il motore locale" }
                    outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index,false)
                }
            }
        }
        return SpeechPcm(out.toByteArray(),rate)
    } finally { runCatching{codec?.stop()};runCatching{codec?.release()};runCatching{extractor.release()};input.delete() }
}

actual suspend fun transcribeAudioLocally(audio:IncomingContent):String? {
    if(!isLocalAudioTranscriptionAvailable())return null
    return localSpeechLock.withLock {
        try {
            val pcm=withContext(Dispatchers.IO){val job=currentCoroutineContext();decodeSpeechAudio(audio){job.ensureActive()}}
            withTimeoutOrNull(180_000) { recognizeLocalPcm(pcm) }
        } catch(e:CancellationException){throw e} catch(_:Exception){null}
    }
}

@android.annotation.TargetApi(33)
private suspend fun recognizeLocalPcm(pcm:SpeechPcm):String? = coroutineScope {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val context=AndroidAppContext.require()
            val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val pipe=try{ParcelFileDescriptor.createPipe()}catch(_:Exception){recognizer.destroy();continuation.resume(null);return@suspendCancellableCoroutine}
            var writer:Job?=null
            val segments=mutableListOf<String>()
            val fullyFed=AtomicBoolean(false)
            fun cleanup(){runCatching{pipe[0].close()};runCatching{pipe[1].close()};writer?.cancel();recognizer.destroy()}
            fun finish(text:String?){if(!continuation.isActive)return;cleanup();continuation.resume(text?.takeIf{it.isNotBlank()&&fullyFed.get()})}
            fun first(results:Bundle?)=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            recognizer.setRecognitionListener(object:RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?){}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){}
                override fun onError(error:Int){finish(null)}
                override fun onResults(results:Bundle?){finish(if(segments.isEmpty())first(results)else segments.joinToString(" "))}
                override fun onPartialResults(partialResults:Bundle?){}
                override fun onEvent(eventType:Int,params:Bundle?){}
                override fun onSegmentResults(segmentResults:Bundle){first(segmentResults).takeIf{it.isNotBlank()}?.let{segments+=it}}
                override fun onEndOfSegmentedSession(){finish(segments.joinToString(" "))}
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"it-IT")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,pcm.rate)
                .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE)
            continuation.invokeOnCancellation { runCatching{pipe[0].close()};runCatching{pipe[1].close()};writer?.cancel();android.os.Handler(android.os.Looper.getMainLooper()).post{recognizer.destroy()} }
            try { recognizer.checkRecognitionSupport(intent,context.mainExecutor,object:RecognitionSupportCallback {
                override fun onSupportResult(support:RecognitionSupport) {
                    if(!continuation.isActive)return
                    if(support.installedOnDeviceLanguages.none{it.startsWith("it",ignoreCase=true)}){finish(null);return}
                    try {
                        recognizer.startListening(intent)
                        writer=launch(Dispatchers.IO){try{ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use{it.write(pcm.bytes);fullyFed.set(true)}}catch(e:CancellationException){throw e}catch(_:Exception){withContext(Dispatchers.Main){finish(null)}}}
                    }catch(_:Exception){finish(null)}
                }
                override fun onError(error:Int){finish(null)}
            }) } catch(_:Exception){finish(null)}
        }
    }
}
