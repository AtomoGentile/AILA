@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package circolareplus.platform

import androidx.compose.runtime.*
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

@Composable actual fun rememberContentPicker(onError:(String)->Unit,onPicked:(IncomingContent)->Unit):()->Unit {
 val error=rememberUpdatedState(onError);val picked=rememberUpdatedState(onPicked)
 return remember{{pickContentFile(CONTENT_MAX_BYTES.toDouble(),{name,mime,data->
  try{picked.value(IncomingContent(name,mime.ifBlank{contentMime(name)},Base64.decode(data)))}catch(_:Exception){error.value("File non leggibile")}
 },{error.value(it)})}}
}
private fun pickContentFile(limit:Double,onPicked:(String,String,String)->Unit,onError:(String)->Unit){js("""(function(){
 const input=document.createElement('input');input.type='file';input.accept='.pdf,.docx,.txt,.png,.jpg,.jpeg,.webp,.wav,.m4a,.mp3,.ogg';
 input.style.display='none';input.onchange=()=>{const f=input.files&&input.files[0];if(!f){input.remove();return;}
 if(!f.size||f.size>limit){onError('Scegli un file non vuoto fino a 20 MB');input.remove();return;}
 const r=new FileReader();r.onload=()=>{onPicked(f.name,f.type,String(r.result).split(',')[1]);input.remove();};r.onerror=()=>{onError('File non leggibile');input.remove();};r.readAsDataURL(f);};
 input.oncancel=()=>input.remove();document.body.appendChild(input);input.click();})()""")}
actual suspend fun openContentFile(bytes:ByteArray,fileName:String,mime:String){openBrowserContent(Base64.encode(bytes),fileName,mime).await<JsAny?>()}
private fun openBrowserContent(data:String,name:String,mime:String):Promise<JsAny?> = js("""(async function(){
 const s=atob(data),b=new Uint8Array(s.length);for(let i=0;i<s.length;i++)b[i]=s.charCodeAt(i);const f=new File([b],name,{type:mime});
 if(navigator.canShare&&navigator.canShare({files:[f]})){try{await navigator.share({files:[f],title:name});return null;}catch(e){if(e.name==='AbortError')return null;}}
 const u=URL.createObjectURL(f),a=document.createElement('a');a.href=u;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(u),60000);return null;})()""")

@Composable actual fun rememberVoiceRecorder(onRecording:(Boolean)->Unit,onError:(String)->Unit,onRecorded:(IncomingContent)->Unit):AudioRecorderActions {
 val state=rememberUpdatedState(onRecording);val error=rememberUpdatedState(onError);val recorded=rememberUpdatedState(onRecorded)
 val picked:(String)->Unit={data->try{recorded.value(IncomingContent("Vocale.wav","audio/wav",Base64.decode(data)))}catch(_:Exception){error.value("Audio non leggibile")}}
 DisposableEffect(Unit){onDispose{voiceControl("cancel",{}, {}, {})}}
 return AudioRecorderActions(start={voiceControl("start",{state.value(it)},{error.value(it)},picked)},
  stop={voiceControl("stop",{state.value(it)},{error.value(it)},picked)},cancel={voiceControl("cancel",{state.value(it)},{error.value(it)},picked)})
}
private fun voiceControl(action:String,onState:(Boolean)->Unit,onError:(String)->Unit,onPicked:(String)->Unit){js("""(async function(){
 if(action!=='start'){const v=window.__ailaVoice;if(v){if(action==='cancel')v.cancelled=true;clearTimeout(v.timer);if(v.recorder&&v.recorder.state!=='inactive')v.recorder.stop();if(v.stream)v.stream.getTracks().forEach(t=>t.stop());}onState(false);return;}
 if(window.__ailaVoice){onError('Una registrazione è già in corso');return;}
 if(!navigator.mediaDevices||!window.MediaRecorder){onError('Registrazione non supportata: puoi scegliere un file audio');return;}
 const v={stream:null,recorder:null,cancelled:false,timer:null};window.__ailaVoice=v;
 try{const stream=await navigator.mediaDevices.getUserMedia({audio:true});if(v.cancelled){stream.getTracks().forEach(t=>t.stop());window.__ailaVoice=null;return;}const chunks=[];const recorder=new MediaRecorder(stream);v.stream=stream;v.recorder=recorder;
 recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};recorder.onerror=()=>{v.cancelled=true;onError('Registrazione interrotta');stream.getTracks().forEach(t=>t.stop());window.__ailaVoice=null;onState(false);};
 recorder.onstop=async()=>{clearTimeout(v.timer);stream.getTracks().forEach(t=>t.stop());onState(false);if(v.cancelled){window.__ailaVoice=null;return;}
 let audioContext;try{const raw=await new Blob(chunks,{type:recorder.mimeType}).arrayBuffer();audioContext=new (window.AudioContext||window.webkitAudioContext)();const decoded=await audioContext.decodeAudioData(raw);await audioContext.close();
 if(decoded.duration>600||decoded.duration<=0)throw new Error('Il vocale deve durare al massimo 10 minuti');
 const length=Math.ceil(decoded.duration*16000),offline=new OfflineAudioContext(1,length,16000),src=offline.createBufferSource();src.buffer=decoded;src.connect(offline.destination);src.start();const mono=await offline.startRendering();
 const pcm=mono.getChannelData(0),out=new ArrayBuffer(44+pcm.length*2),view=new DataView(out),str=(p,s)=>{for(let i=0;i<s.length;i++)view.setUint8(p+i,s.charCodeAt(i));};
 str(0,'RIFF');view.setUint32(4,36+pcm.length*2,true);str(8,'WAVE');str(12,'fmt ');view.setUint32(16,16,true);view.setUint16(20,1,true);view.setUint16(22,1,true);view.setUint32(24,16000,true);view.setUint32(28,32000,true);view.setUint16(32,2,true);view.setUint16(34,16,true);str(36,'data');view.setUint32(40,pcm.length*2,true);
 for(let i=0;i<pcm.length;i++){const n=Math.max(-1,Math.min(1,pcm[i]));view.setInt16(44+i*2,n<0?n*32768:n*32767,true);}const bytes=new Uint8Array(out);let binary='';for(let i=0;i<bytes.length;i+=16384)binary+=String.fromCharCode.apply(null,bytes.subarray(i,i+16384));if(!v.cancelled)onPicked(btoa(binary));
 }catch(e){if(!v.cancelled)onError(e.message||'Registrazione non leggibile');if(audioContext&&audioContext.state!=='closed')audioContext.close();}finally{window.__ailaVoice=null;}};
 recorder.start(1000);onState(true);v.timer=setTimeout(()=>{if(recorder.state!=='inactive')recorder.stop();},599000);
 }catch(e){window.__ailaVoice=null;onState(false);onError('Consenti il microfono per registrare, oppure scegli un file audio.');}})()""")}

/** Consume the same-origin share-target inbox once, before displaying the local preview. */
fun initializeWebContentIngress(){consumeSharedContent{name,mime,data->try{IncomingContentInbox.receive(name,mime,Base64.decode(data))}catch(_:Exception){}}}
private fun consumeSharedContent(onFile:(String,String,String)->Unit){js("""(function(){
 if(!location.search.includes('aila_import=1')||!window.indexedDB)return;
 const req=indexedDB.open('aila-content-ingress',1);req.onupgradeneeded=()=>req.result.createObjectStore('files');
 req.onsuccess=()=>{const db=req.result,tx=db.transaction('files','readwrite'),store=tx.objectStore('files'),get=store.get('pending');
 get.onsuccess=()=>{const f=get.result;if(f&&f.bytes&&f.bytes.byteLength<=20971520){const b=new Uint8Array(f.bytes);let s='';for(let i=0;i<b.length;i+=16384)s+=String.fromCharCode.apply(null,b.subarray(i,i+16384));onFile(f.name,f.mime,btoa(s));}store.delete('pending');};tx.oncomplete=()=>{db.close();history.replaceState(null,'',location.pathname);};};})()""")}
