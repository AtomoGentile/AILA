@file:OptIn(ExperimentalWasmJsInterop::class)

package circolareplus.web

import circolareplus.data.AppContainer
import circolareplus.domain.model.NotificationCategoryMapper
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.js.Promise

/**
 * Ponte fra l'app e il service worker (webApp/.../resources/sw.js), che non vede il localStorage.
 *
 * Database IndexedDB 'aila-sw', stesso schema creato dal SW (chi arriva prima crea gli archivi):
 * - 'kv': token di sessione, URL del Worker, preferenze notifiche e l'ultimo numero circolare
 *   visto dall'app, che servono al controllo periodico delle circolari ad app chiusa;
 * - 'inbox': i push arrivati, che l'app svuota nella campanella (come fa Android dal servizio FCM
 *   anche ad app chiusa).
 *
 * Il token qui non e' piu' esposto di quanto lo sia gia' nel localStorage della stessa origine; al
 * logout si cancella tutto, altrimenti il SW continuerebbe a interrogare il server per l'account
 * uscito.
 */
internal object SwBridge {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Snapshot(
        val token: String,
        val apiBase: String,
        val muted: List<String>,
        val system: Boolean,
        val appLastCircular: Int
    )

    @Serializable
    private data class InboxEntry(
        val id: String,
        val title: String = "AILA",
        val body: String = "",
        val kind: String = "",
        val data: Map<String, String> = emptyMap(),
        val at: Double = 0.0
    )

    /** Copia nel database lo stato che serve al SW. Token vuoto = sessione chiusa: lo si toglie. */
    suspend fun sync() {
        val settings = AppContainer.settings
        val snapshot = Snapshot(
            token = settings.authToken,
            apiBase = AppContainer.api.baseUrl,
            muted = settings.mutedNotificationKinds,
            system = settings.isSystemNotificationsEnabled,
            appLastCircular = settings.lastSeenCircularNumber
        )
        runCatching { idbWriteSnapshot(json.encodeToString(snapshot)).await<JsAny?>() }
    }

    /** Al logout: via token, segnalibri e casella (sullo stesso browser puo' entrare un altro account). */
    suspend fun clear() {
        runCatching { idbClearAll().await<JsAny?>() }
    }

    /**
     * Svuota la casella dei push nella campanella. Lettura e cancellazione nella stessa
     * transazione: due chiamate ravvicinate (messaggio del SW e ritorno in primo piano) non
     * scrivono la stessa voce due volte. La dedup per id di onPushReceived fa il resto.
     */
    suspend fun drainInbox(): Int {
        val raw = runCatching { idbDrainInbox().await<JsString?>()?.toString() }.getOrNull() ?: return 0
        val entries = runCatching { json.decodeFromString<List<InboxEntry>>(raw) }.getOrNull() ?: return 0
        val settings = AppContainer.settings
        for (entry in entries.sortedBy { it.at }) {
            settings.onPushReceived(entry.id, entry.title, entry.body, categoryOf(entry.kind, entry.data))
        }
        return entries.size
    }

    /**
     * Categoria per la navigazione e la campanella: prima quella ricavata dai dati del push
     * (come su Android e iOS, cosi' "seatmap_preferences" e "ranking_polls" restano distinte),
     * altrimenti la categoria delle Impostazioni mandata dal server (es. "calendar").
     */
    fun categoryOf(kind: String, data: Map<String, String>): String =
        NotificationCategoryMapper.categoryFrom(data).ifBlank { kind }
}

// --- IndexedDB -------------------------------------------------------------------------------
// Stesso codice di apertura di sw.js: se il database non esiste ancora lo crea chi arriva prima.

private fun idbWriteSnapshot(snapshotJson: String): Promise<JsAny?> = js(
    """{
    const s = JSON.parse(snapshotJson);
    return new Promise((resolve, reject) => {
      const req = indexedDB.open('aila-sw', 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('kv')) db.createObjectStore('kv');
        if (!db.objectStoreNames.contains('inbox')) db.createObjectStore('inbox', { keyPath: 'id' });
      };
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result;
        const tx = db.transaction('kv', 'readwrite');
        const kv = tx.objectStore('kv');
        if (s.token) kv.put(s.token, 'token'); else kv.delete('token');
        kv.put(s.apiBase, 'apiBase');
        kv.put(s.muted, 'muted');
        kv.put(s.system, 'system');
        kv.put(s.appLastCircular, 'appLastCircular');
        tx.oncomplete = () => { db.close(); resolve(null); };
        tx.onerror = () => { db.close(); reject(tx.error); };
      };
    });
  }"""
)

private fun idbClearAll(): Promise<JsAny?> = js(
    """new Promise((resolve, reject) => {
      const req = indexedDB.open('aila-sw', 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('kv')) db.createObjectStore('kv');
        if (!db.objectStoreNames.contains('inbox')) db.createObjectStore('inbox', { keyPath: 'id' });
      };
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result;
        const tx = db.transaction(['kv', 'inbox'], 'readwrite');
        tx.objectStore('kv').clear();
        tx.objectStore('inbox').clear();
        tx.oncomplete = () => { db.close(); resolve(null); };
        tx.onerror = () => { db.close(); reject(tx.error); };
      };
    })"""
)

private fun idbDrainInbox(): Promise<JsString?> = js(
    """new Promise((resolve, reject) => {
      const req = indexedDB.open('aila-sw', 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains('kv')) db.createObjectStore('kv');
        if (!db.objectStoreNames.contains('inbox')) db.createObjectStore('inbox', { keyPath: 'id' });
      };
      req.onerror = () => reject(req.error);
      req.onsuccess = () => {
        const db = req.result;
        const tx = db.transaction('inbox', 'readwrite');
        const store = tx.objectStore('inbox');
        let entries = [];
        store.getAll().onsuccess = (e) => { entries = e.target.result || []; store.clear(); };
        tx.oncomplete = () => { db.close(); resolve(JSON.stringify(entries)); };
        tx.onerror = () => { db.close(); reject(tx.error); };
      };
    })"""
)
