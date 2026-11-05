@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package circolareplus.platform

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.memcpy

interface ContentFileBridge {
 fun pickFile(onResult:(String?)->Unit)
 fun openFile(path:String):String?
 fun startRecording(onStarted:()->Unit,onResult:(String?)->Unit,onError:(String)->Unit)
 fun stopRecording()
 fun cancelRecording()
}
object ContentFileBridgeHolder{var bridge:ContentFileBridge?=null}
private fun loadContent(path:String):IncomingContent {
 val attributes=NSFileManager.defaultManager.attributesOfItemAtPath(path,null)
 val declared=(attributes?.get(NSFileSize) as? NSNumber)?.longLongValue?:0
 require(declared<=CONTENT_MAX_BYTES){"File troppo grande: massimo 20 MB"}
 val data=NSData.dataWithContentsOfFile(path)?:error("File non leggibile")
 require(data.length>0u&&data.length<=CONTENT_MAX_BYTES.toULong()){"Scegli un file non vuoto fino a 20 MB"}
 val bytes=ByteArray(data.length.toInt()).also{out->out.usePinned{memcpy(it.addressOf(0),data.bytes,data.length)}}
 return IncomingContent(path.substringAfterLast('/'),contentMime(path),bytes)
}
object IosContentIngress {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 fun importFile(path:String){scope.launch{try{val item=withContext(Dispatchers.Default){loadContent(path)};IncomingContentInbox.receive(item.fileName,item.mime,item.bytes)}
  catch(e:CancellationException){throw e}catch(e:Exception){IncomingContentInbox.reject(e.message?:"File non leggibile")}
  finally{NSFileManager.defaultManager.removeItemAtPath(path,null)}}}
}
@Composable actual fun rememberContentPicker(onError:(String)->Unit,onPicked:(IncomingContent)->Unit):()->Unit {
 val scope=rememberCoroutineScope();val picked by rememberUpdatedState(onPicked);val error by rememberUpdatedState(onError)
 return remember{{val bridge=ContentFileBridgeHolder.bridge
  if(bridge==null)error("Selettore file non disponibile") else bridge.pickFile{path->if(path!=null)scope.launch{
   try{picked(withContext(Dispatchers.Default){loadContent(path)})}catch(e:CancellationException){throw e}catch(e:Exception){error(e.message?:"File non leggibile")}
   finally{NSFileManager.defaultManager.removeItemAtPath(path,null)}
  }}
 }}
}
actual suspend fun openContentFile(bytes:ByteArray,fileName:String,mime:String){
 val safe=fileName.substringAfterLast('/').substringAfterLast('\\');val path=NSTemporaryDirectory()+safe
 bytes.usePinned{NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong())}.writeToFile(path,true)
 val result=withContext(Dispatchers.Main){(ContentFileBridgeHolder.bridge?:error("Apertura file non disponibile")).openFile(path)}
 if(result!=null)error(result)
}
@Composable actual fun rememberVoiceRecorder(onRecording:(Boolean)->Unit,onError:(String)->Unit,onRecorded:(IncomingContent)->Unit):AudioRecorderActions {
 val scope=rememberCoroutineScope();val state by rememberUpdatedState(onRecording);val error by rememberUpdatedState(onError);val recorded by rememberUpdatedState(onRecorded)
 var active by remember{mutableStateOf(false)}
 DisposableEffect(Unit){onDispose{if(active)ContentFileBridgeHolder.bridge?.cancelRecording()}}
 return AudioRecorderActions(start={
  val bridge=ContentFileBridgeHolder.bridge
  if(bridge==null)error("Registrazione non disponibile") else {active=true;bridge.startRecording(onStarted={state(true)},onResult={path->
   active=false;state(false);if(path!=null)scope.launch{try{recorded(withContext(Dispatchers.Default){loadContent(path)})}
    catch(e:CancellationException){throw e}catch(e:Exception){error(e.message?:"Registrazione non leggibile")}
    finally{NSFileManager.defaultManager.removeItemAtPath(path,null)}}
  },onError={active=false;state(false);error(it)})}
 },stop={ContentFileBridgeHolder.bridge?.stopRecording()},cancel={ContentFileBridgeHolder.bridge?.cancelRecording();active=false;state(false)})
}
