@file:OptIn(ExperimentalWasmJsInterop::class)

package circolareplus.web

import circolareplus.data.AppContainer
import circolareplus.platform.AppForeground
import circolareplus.push.DataRefreshEvents
import circolareplus.push.PushPreferencesHook
import circolareplus.ui.PendingDeepLink
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private var installed = false

/**
 * Ciclo di vita della PWA: l'equivalente di MainActivity.onStart/onStop/onResume/onNewIntent.
 * Da chiamare una volta da main(), prima di ComposeViewport.
 *
 * - pagina visibile/nascosta -> [AppForeground.isForeground] (i cicli periodici di MainAppShell
 *   si fermano in background) e, al ritorno, [DataRefreshEvents.request] come onResume su Android;
 * - service worker: registrazione, push ricevuti (campanella + dati riletti subito), tocco su una
 *   notifica (-> [PendingDeepLink], anche all'avvio a freddo via ?push=...), aggiornamenti;
 * - ponte IndexedDB per il SW (sessione e preferenze, vedi [SwBridge]);
 * - [PushPreferencesHook]: gli interruttori delle notifiche nelle Impostazioni accendono e spengono
 *   l'iscrizione Web Push, chiedendo il permesso dentro lo stesso tocco.
 */
fun installWebLifecycle() {
    if (installed) return
    installed = true

    // Avvio a freddo dal tocco su una notifica: il SW ha aperto la finestra con ?push=...
    consumeLaunchPushParam()?.let { applyNotificationOpen(it) }

    AppForeground.isForeground = jsIsVisible()
    jsOnVisibilityChange { visible ->
        AppForeground.isForeground = visible
        AppContainer.appScope.launch {
            // Anche all'uscita: preferenze e segnalibro aggiornati per il controllo in background.
            SwBridge.sync()
            if (visible) SwBridge.drainInbox()
        }
        if (visible) DataRefreshEvents.request()
    }

    PushPreferencesHook.onChangedByUser = { WebPush.onPreferencesChangedByUser() }

    AppContainer.appScope.launch {
        WebServiceWorker.register(::onServiceWorkerMessage)
        SwBridge.sync()
        // Push arrivati ad app chiusa: in campanella, come fa Android dal servizio FCM.
        SwBridge.drainInbox()
    }
}

private val messageJson = Json { ignoreUnknownKeys = true }

private fun onServiceWorkerMessage(raw: String) {
    val message = runCatching { messageJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
    when (message["type"]?.jsonPrimitive?.contentOrNull) {
        "aila-push" -> {
            // Push con l'app aperta: la lista si aggiorna subito invece che al giro successivo.
            AppContainer.appScope.launch { SwBridge.drainInbox() }
            DataRefreshEvents.request()
        }
        "aila-open" -> applyNotificationOpen(message)
        "aila-app-updated" -> WebServiceWorker.updateReady = true
    }
}

/** { kind, data } della notifica toccata -> schermata (stessa strada della campanella). */
private fun applyNotificationOpen(message: JsonObject) {
    val kind = (message["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val data = (message["data"] as? JsonObject)
        ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
        ?.toMap()
        .orEmpty()
    val category = SwBridge.categoryOf(kind, data)
    if (category.isNotBlank()) PendingDeepLink.category = category
}

/** Legge ?push=... e lo toglie dall'indirizzo, cosi' una ricarica non riapre la stessa schermata. */
private fun consumeLaunchPushParam(): JsonObject? {
    val raw = jsConsumeLaunchParam() ?: return null
    if (raw.length > 4_000) return null
    return runCatching { messageJson.parseToJsonElement(raw).jsonObject }.getOrNull()
}

private fun jsIsVisible(): Boolean = js("document.visibilityState !== 'hidden'")

private fun jsOnVisibilityChange(callback: (Boolean) -> Unit): Unit = js(
    "document.addEventListener('visibilitychange', () => callback(document.visibilityState !== 'hidden'))"
)

private fun jsConsumeLaunchParam(): String? = js(
    """(() => {
      const url = new URL(location.href);
      const value = url.searchParams.get('push');
      if (value === null) return null;
      url.searchParams.delete('push');
      history.replaceState(history.state, '', url.pathname + url.search + url.hash);
      return value;
    })()"""
)
