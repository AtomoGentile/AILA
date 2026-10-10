package circolareplus.platform

import kotlin.io.encoding.Base64
import kotlin.js.Promise
import kotlinx.coroutines.await

/**
 * Copia offline nel browser: IndexedDB, che a differenza del localStorage regge i PDF (decine di
 * MB) e non e' limitata a 5 MB.
 *
 * Le altre piattaforme leggono e scrivono in modo sincrono, e IndexedDB e' asincrona. Si risolve
 * caricando tutto in memoria all'avvio ([init], da chiamare e attendere prima di far partire la
 * UI) e scrivendo poi "di rimbalzo" sul database: le letture sono istantanee, le scritture
 * arrivano su disco un momento dopo. Se il browser chiude la pagina nel mezzo, si perde al piu'
 * l'ultimo salvataggio, che e' una copia di comodo e viene rifatta alla prossima sincronizzazione.
 *
 * Nel database i file sono stringhe base64: IndexedDB le accetta in tutti i browser (anche in
 * navigazione privata di Safari, dove i Blob a volte no) e il passaggio dalla memoria di
 * WebAssembly a JavaScript avviene senza copie byte per byte.
 */
actual object OfflineStore {
    private val files = mutableMapOf<String, ByteArray>()
    private var db: JsAny? = null

    /** Apre il database e carica in memoria tutto quello che c'e'. Se IndexedDB manca, si lavora solo in memoria. */
    suspend fun init() {
        requestPersistentStorage()
        val opened = idbOpen().await<JsAny?>() ?: return
        db = opened
        val rows = idbReadAll(opened).await<JsAny?>() ?: return
        val count = rowCount(rows)
        for (i in 0 until count) {
            val name = rowName(rows, i)
            val data = try {
                Base64.decode(rowData(rows, i))
            } catch (e: IllegalArgumentException) {
                continue
            }
            files[name] = data
        }
    }

    actual fun readBytes(name: String): ByteArray? = files[name]

    actual fun writeBytes(name: String, bytes: ByteArray) {
        files[name] = bytes
        db?.let { idbPut(it, name, Base64.encode(bytes)) }
    }

    actual fun exists(name: String): Boolean = name in files

    actual fun totalBytes(): Long = files.values.sumOf { it.size.toLong() }

    actual fun clear() {
        files.clear()
        db?.let { idbClear(it) }
    }

    actual fun list(): List<String> = files.keys.toList()

    actual fun delete(name: String) {
        files.remove(name)
        db?.let { idbDelete(it, name) }
    }
}

// Chiede al browser di non sfrattare i dati dell'app quando manca spazio (Chrome e Safari lo
// fanno con le origini "non persistenti"). La risposta non serve: se negata, restano valide le
// regole di default.
private fun requestPersistentStorage() {
    js("try { if (navigator.storage && navigator.storage.persist) navigator.storage.persist(); } catch (e) {}")
}

private fun idbOpen(): Promise<JsAny?> = js(
    "new Promise(function (res) { try { var r = indexedDB.open('aila-offline', 1); " +
        "r.onupgradeneeded = function () { r.result.createObjectStore('files'); }; " +
        "r.onsuccess = function () { res(r.result); }; r.onerror = function () { res(null); }; r.onblocked = function () { res(null); }; " +
        "} catch (e) { res(null); } })"
)

private fun idbReadAll(db: JsAny): Promise<JsAny?> = js(
    "new Promise(function (res) { var out = []; try { var c = db.transaction('files', 'readonly').objectStore('files').openCursor(); " +
        "c.onsuccess = function () { var cur = c.result; if (cur) { out.push([cur.key, cur.value]); cur.continue(); } else { res(out); } }; " +
        "c.onerror = function () { res(out); }; } catch (e) { res(out); } })"
)

private fun rowCount(rows: JsAny): Int = js("rows.length")

private fun rowName(rows: JsAny, i: Int): String = js("String(rows[i][0])")

private fun rowData(rows: JsAny, i: Int): String = js("String(rows[i][1])")

private fun idbPut(db: JsAny, name: String, base64: String) {
    js("try { db.transaction('files', 'readwrite').objectStore('files').put(base64, name); } catch (e) {}")
}

private fun idbDelete(db: JsAny, name: String) {
    js("try { db.transaction('files', 'readwrite').objectStore('files').delete(name); } catch (e) {}")
}

private fun idbClear(db: JsAny) {
    js("try { db.transaction('files', 'readwrite').objectStore('files').clear(); } catch (e) {}")
}
