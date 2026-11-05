@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package circolareplus.platform

import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

actual fun createLocalContentStorage(): LocalContentStorage = object : LocalContentStorage {
    override suspend fun read(key: String): ByteArray? = vaultRead(key).await<JsAny?>()?.let { Base64.decode(vaultString(it)) }
    override suspend fun write(key: String, bytes: ByteArray) { vaultWrite(key, Base64.encode(bytes)).await<JsAny?>() }
    override suspend fun remove(key: String) { vaultDelete(key).await<JsAny?>() }
    override suspend fun keys(): List<String> {
        val keys = vaultKeys().await<JsAny?>() ?: return emptyList()
        return List(vaultCount(keys)) { vaultKey(keys, it) }
    }
}
private fun vaultString(value: JsAny): String = js("String(value)")
private fun vaultCount(value: JsAny): Int = js("value.length")
private fun vaultKey(value: JsAny, index: Int): String = js("String(value[index])")
private fun vaultRead(key: String): Promise<JsAny?> = js("""(async()=>{
 const db=await new Promise((res,rej)=>{const r=indexedDB.open('aila-saved-content',1);r.onupgradeneeded=()=>r.result.createObjectStore('files');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(new Error('Archivio locale non disponibile'));});
 try{return await new Promise((res,rej)=>{const tx=db.transaction('files','readonly'),r=tx.objectStore('files').get(key);let value=null;r.onsuccess=()=>value=r.result;tx.oncomplete=()=>res(value==null?null:String(value));tx.onerror=()=>rej(new Error('File non leggibile'));});}finally{db.close();}
})()""")
private fun vaultWrite(key: String, data: String): Promise<JsAny?> = js("""(async()=>{
 if(navigator.storage&&navigator.storage.persist)try{await navigator.storage.persist();}catch(_){}
 const db=await new Promise((res,rej)=>{const r=indexedDB.open('aila-saved-content',1);r.onupgradeneeded=()=>r.result.createObjectStore('files');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(new Error('Archivio locale non disponibile'));});
 try{await new Promise((res,rej)=>{const tx=db.transaction('files','readwrite');tx.objectStore('files').put(data,key);tx.oncomplete=()=>res(null);tx.onerror=tx.onabort=()=>rej(new Error('Spazio insufficiente: elimina un file dal dispositivo e riprova'));});return null;}finally{db.close();}
})()""")
private fun vaultDelete(key: String): Promise<JsAny?> = js("""(async()=>{
 const db=await new Promise((res,rej)=>{const r=indexedDB.open('aila-saved-content',1);r.onupgradeneeded=()=>r.result.createObjectStore('files');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(new Error('Archivio locale non disponibile'));});
 try{await new Promise((res,rej)=>{const tx=db.transaction('files','readwrite');tx.objectStore('files').delete(key);tx.oncomplete=()=>res(null);tx.onerror=tx.onabort=()=>rej(new Error('Copia locale non eliminata: riprova'));});return null;}finally{db.close();}
})()""")
private fun vaultKeys(): Promise<JsAny?> = js("""(async()=>{
 const db=await new Promise((res,rej)=>{const r=indexedDB.open('aila-saved-content',1);r.onupgradeneeded=()=>r.result.createObjectStore('files');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(new Error('Archivio locale non disponibile'));});
 try{return await new Promise((res,rej)=>{const tx=db.transaction('files','readonly'),r=tx.objectStore('files').getAllKeys();let keys=[];r.onsuccess=()=>keys=r.result;tx.oncomplete=()=>res(keys);tx.onerror=()=>rej(new Error('Archivio non leggibile'));});}finally{db.close();}
})()""")
