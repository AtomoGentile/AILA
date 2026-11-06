@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package circolareplus.platform

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val localSpeechLock=Mutex()

actual fun isLocalAudioTranscriptionAvailable():Boolean = ContentFileBridgeHolder.bridge?.isLocalSpeechAvailable()==true

actual suspend fun transcribeAudioLocally(audio:IncomingContent):String? = localSpeechLock.withLock { transcribeIosAudio(audio) }

private suspend fun transcribeIosAudio(audio:IncomingContent):String? {
    if(audio.bytes.isEmpty() || !isLocalAudioTranscriptionAvailable())return null
    val suffix=audio.fileName.substringAfterLast('.',"m4a").lowercase().takeIf{it in setOf("m4a","wav","mp3","aac","caf","ogg")}?:"m4a"
    val path=NSTemporaryDirectory()+NSUUID().UUIDString+".$suffix"
    try {
        audio.bytes.usePinned{NSData.create(bytes=it.addressOf(0),length=audio.bytes.size.toULong())}.writeToFile(path,true)
        return withTimeoutOrNull(180_000){withContext(Dispatchers.Main){
            suspendCancellableCoroutine<String?> { continuation ->
                val bridge=ContentFileBridgeHolder.bridge
                if(bridge==null){continuation.resume(null);return@suspendCancellableCoroutine}
                bridge.transcribeLocalAudio(path){if(continuation.isActive)continuation.resume(it?.takeIf{it.isNotBlank()})}
                continuation.invokeOnCancellation{NSOperationQueue.mainQueue.addOperationWithBlock{bridge.cancelLocalAudio(path)}}
            }
        }}
    } catch(e:CancellationException){throw e} catch(_:Exception){return null}
    finally{NSFileManager.defaultManager.removeItemAtPath(path,null)}
}
