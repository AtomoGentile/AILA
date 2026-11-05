package circolareplus.platform

import androidx.compose.runtime.*

const val CONTENT_MAX_BYTES=20*1024*1024
data class IncomingContent(val fileName:String,val mime:String,val bytes:ByteArray,val extractedText:String?=null,val localId:String?=null,val isTranscript:Boolean=false,val generatedAt:String?=null)

/** In-memory ingress only: receiving a file never sends it to a server or model. */
object IncomingContentInbox {
 var pending by mutableStateOf<IncomingContent?>(null)
  private set
 var error by mutableStateOf<String?>(null)
  private set
 fun receive(fileName:String,mime:String,bytes:ByteArray){
  if(bytes.isEmpty()||bytes.size>CONTENT_MAX_BYTES){error="Scegli un file non vuoto fino a 20 MB.";return}
  pending=IncomingContent(fileName.substringAfterLast('/').substringAfterLast('\\').take(160),mime.ifBlank{contentMime(fileName)},bytes);error=null
 }
 fun clear(){pending=null;error=null}
 fun reject(message:String){error=message}
}
fun contentMime(name:String):String=when(name.substringAfterLast('.').lowercase()){
 "pdf"->"application/pdf";"txt"->"text/plain";"docx"->"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
 "png"->"image/png";"jpg","jpeg"->"image/jpeg";"webp"->"image/webp"
 "wav"->"audio/wav";"m4a"->"audio/mp4";"mp3"->"audio/mpeg";"ogg"->"audio/ogg"
 else->"application/octet-stream"
}
@Composable expect fun rememberContentPicker(onError:(String)->Unit,onPicked:(IncomingContent)->Unit):()->Unit
expect suspend fun openContentFile(bytes:ByteArray,fileName:String,mime:String)

class AudioRecorderActions(val start:()->Unit,val stop:()->Unit,val cancel:()->Unit)
@Composable expect fun rememberVoiceRecorder(onRecording:(Boolean)->Unit,onError:(String)->Unit,onRecorded:(IncomingContent)->Unit):AudioRecorderActions

/** Fail closed on expired or malformed server metadata, for remote access. A separately saved local copy has no automatic expiry. */
fun circolareplus.data.remote.dto.ContentAttachmentDto.isExpired(atMillis:Long=currentTimeMillis()):Boolean =
 expired || runCatching { kotlin.time.Instant.parse(expiresAt).toEpochMilliseconds() <= atMillis }.getOrDefault(true)
