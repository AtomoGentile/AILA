package circolareplus.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

private fun readPickedContent(context:Context,uri:Uri):IncomingContent {
 var name="contenuto";var size:Long?=null
 context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{cursor->
  if(cursor.moveToFirst()){
   val n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0&&!cursor.isNull(n))name=cursor.getString(n)
   val s=cursor.getColumnIndex(OpenableColumns.SIZE);if(s>=0&&!cursor.isNull(s))size=cursor.getLong(s)
  }
 }
 require((size?:0)<=CONTENT_MAX_BYTES){"File troppo grande: massimo 20 MB"}
 val bytes=context.contentResolver.openInputStream(uri)?.use{input->
  val out=ByteArrayOutputStream();val buf=ByteArray(64*1024);var count=0
  while(true){val n=input.read(buf);if(n<0)break;count+=n;require(count<=CONTENT_MAX_BYTES){"File troppo grande: massimo 20 MB"};out.write(buf,0,n)};out.toByteArray()
 }?:error("Non riesco a leggere il file")
 require(bytes.isNotEmpty()){"Il file è vuoto"}
 return IncomingContent(name,context.contentResolver.getType(uri)?:contentMime(name),bytes)
}
@Composable actual fun rememberContentPicker(onError:(String)->Unit,onPicked:(IncomingContent)->Unit):()->Unit {
 val context=LocalContext.current;val scope=rememberCoroutineScope();val picked by rememberUpdatedState(onPicked);val error by rememberUpdatedState(onError)
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
  try{val file=withContext(Dispatchers.IO){readPickedContent(context,uri)};picked(file)}catch(e:CancellationException){throw e}catch(e:Exception){error(e.message?:"Impossibile leggere il file")}
 }}
 return remember(launcher){{launcher.launch(arrayOf("application/pdf","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document","image/png","image/jpeg","image/webp","audio/wav","audio/x-wav","audio/mp4","audio/mpeg","audio/ogg"))}}
}
actual suspend fun openContentFile(bytes:ByteArray,fileName:String,mime:String){
 val context=AndroidAppContext.require()
 val safe=fileName.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^\\p{L}\\p{N} ._()-]"),"_").ifBlank{"contenuto"}
 val file=withContext(Dispatchers.IO){File(File(context.cacheDir,"exports").apply{mkdirs()},safe).apply{writeBytes(bytes)}}
 val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
 context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Apri contenuto").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
/** Called only for supported system share/open intents; no network access. */
suspend fun readIncomingContent(context:Context,intent:Intent):IncomingContent?=withContext(Dispatchers.IO){
 if(intent.action==Intent.ACTION_SEND){
  @Suppress("DEPRECATION") val uri=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
  if(uri!=null)return@withContext readPickedContent(context,uri)
  val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
  if(text!=null){val bytes=text.encodeToByteArray();require(bytes.size<=CONTENT_MAX_BYTES){"Testo troppo lungo"};return@withContext IncomingContent("Testo condiviso.txt","text/plain",bytes)}
 }
 if(intent.action==Intent.ACTION_VIEW&&intent.data?.scheme in setOf("content","file"))return@withContext readPickedContent(context,intent.data!!)
 null
}

@Composable actual fun rememberVoiceRecorder(onRecording:(Boolean)->Unit,onError:(String)->Unit,onRecorded:(IncomingContent)->Unit):AudioRecorderActions {
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 val recording by rememberUpdatedState(onRecording);val error by rememberUpdatedState(onError);val recorded by rememberUpdatedState(onRecorded)
 var alive by remember{mutableStateOf(true)}
 var wantsRecording by remember{mutableStateOf(false)}
 var recorder by remember{mutableStateOf<MediaRecorder?>(null)};var file by remember{mutableStateOf<File?>(null)}
 fun finish(cancel:Boolean){
  val r=recorder?:return;val f=file;recorder=null;file=null
  var valid=true;try{r.stop()}catch(_:Exception){valid=false}finally{r.release();recording(false)}
  if(cancel||!valid){f?.delete();if(!cancel)error("Registrazione troppo breve o non disponibile");return}
  if(f!=null)scope.launch{try{
   val bytes=withContext(Dispatchers.IO){require(f.length()<=CONTENT_MAX_BYTES){"Audio troppo grande"};f.readBytes().also{f.delete()}}
   recorded(IncomingContent("Vocale.m4a","audio/mp4",bytes))
  }catch(e:CancellationException){f.delete();throw e}catch(e:Exception){f.delete();error(e.message?:"Registrazione non riuscita")}}
 }
 fun startRecording(){
  if(!alive||!wantsRecording||recorder!=null)return
  try{
   val target=File.createTempFile("aila-voice-",".m4a",context.cacheDir)
   @Suppress("DEPRECATION") val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(context) else MediaRecorder()
   file=target;recorder=r
   r.setAudioSource(MediaRecorder.AudioSource.MIC);r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
   r.setAudioSamplingRate(16000);r.setAudioChannels(1);r.setAudioEncodingBitRate(64000);r.setOutputFile(target.absolutePath)
   r.setMaxDuration(599000);r.setMaxFileSize(CONTENT_MAX_BYTES.toLong());r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)finish(false)}
   r.prepare();r.start();recording(true)
  }catch(e:Exception){recorder?.release();recorder=null;file?.delete();file=null;recording(false);error("Microfono non disponibile: ${e.message.orEmpty()}")}
 }
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(alive&&wantsRecording){if(granted)startRecording()else error("Consenti il microfono per registrare un vocale. Puoi comunque scegliere un file audio.")}}
 DisposableEffect(Unit){alive=true;onDispose{alive=false;wantsRecording=false;recorder?.let{r->try{r.stop()}catch(_:Exception){};r.release()};recorder=null;file?.delete();file=null}}
 return AudioRecorderActions(start={wantsRecording=true;launcher.launch(Manifest.permission.RECORD_AUDIO)},stop={wantsRecording=false;finish(false)},cancel={wantsRecording=false;finish(true)})
}
