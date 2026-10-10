@file:OptIn(ExperimentalWasmJsInterop::class)

package circolareplus.web

import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.SuccessDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Promise

/** Il browser sa ricevere Web Push? Su iPhone/iPad solo con la PWA aggiunta alla Home (iOS 16.4+). */
fun isWebPushSupported(): Boolean = jsWebPushSupported()

/** La PWA e' aperta come app installata (dalla Home o dal launcher), non in una scheda del browser. */
fun isPwaInstalled(): Boolean = jsPwaInstalled()

/** Stato del permesso notifiche: "granted", "denied", "default" o "unsupported". */
fun webNotificationPermission(): String = jsNotificationPermission()

/**
 * Notifiche della PWA via Web Push (rotte /api/webpush/... del Worker), al posto di FCM.
 *
 * Il flusso comune (MainAppShell + FcmRepository) e' pensato per un token FCM: qui
 * [circolareplus.push.PushTokenProvider.getToken] fa l'iscrizione web e restituisce null, cosi'
 * la parte comune salta /api/fcm/token (che accetta solo "android" e "ios") senza errori.
 *
 * Sul web l'interruttore "Notifiche di sistema" delle Impostazioni accende e spegne l'iscrizione:
 * un push web deve SEMPRE mostrare una notifica (Safari revoca l'iscrizione altrimenti), quindi
 * "solo campanella" come su Android non e' possibile. Il permesso si chiede solo da quel tocco.
 */
internal object WebPush {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class VapidKeyDto(val publicKey: String)

    @Serializable
    private data class SubscriptionKeysDto(val p256dh: String, val auth: String)

    @Serializable
    private data class SubscriptionDto(val endpoint: String, val keys: SubscriptionKeysDto)

    @Serializable
    private data class SubscriptionRequestDto(val subscription: SubscriptionDto, val mutedKinds: List<String>)

    @Serializable
    private data class EndpointDto(val endpoint: String)

    /**
     * Inizio sessione (utente loggato, una volta per avvio). Non chiede mai il permesso: se c'e'
     * gia' rinnova l'iscrizione e le preferenze sul server, altrimenti mette l'interruttore su
     * spento, cosi' riaccenderlo e' il tocco che fa comparire la richiesta del browser.
     */
    suspend fun onSessionStart() {
        SwBridge.sync()
        WebServiceWorker.registerPeriodicSync()
        val settings = AppContainer.settings
        val canNotify = isWebPushSupported() && webNotificationPermission() == "granted"
        when {
            canNotify && settings.isSystemNotificationsEnabled -> subscribeAndRegister()
            settings.isSystemNotificationsEnabled -> {
                // Acceso nelle Impostazioni ma senza permesso (mai chiesto, negato o browser senza
                // push): mostrarlo acceso sarebbe una promessa che non si mantiene.
                settings.isSystemNotificationsEnabled = false
                SwBridge.sync()
            }
            else -> removeSubscription(settings.authToken)
        }
    }

    /**
     * Interruttori delle notifiche cambiati dall'utente. Chiamata SINCRONA dentro il tocco:
     * Notification.requestPermission() deve partire prima di qualunque attesa, altrimenti Safari
     * (iOS e macOS) e Firefox la rifiutano perche' non piu' legata al gesto.
     */
    fun onPreferencesChangedByUser() {
        val settings = AppContainer.settings
        if (settings.authToken.isBlank()) return
        val enabled = settings.isSystemNotificationsEnabled
        val permission = webNotificationPermission()
        val pendingPermission: Promise<JsString>? =
            if (enabled && permission == "default" && isWebPushSupported()) jsRequestPermission() else null

        AppContainer.appScope.launch {
            try {
                val granted = when {
                    !enabled -> false
                    pendingPermission != null -> pendingPermission.await<JsString>().toString() == "granted"
                    else -> permission == "granted" && isWebPushSupported()
                }
                if (enabled && !granted) {
                    // Permesso negato o push non disponibile (Safari su iPhone fuori dalla Home):
                    // l'interruttore torna spento, come sara' mostrato alla prossima apertura.
                    settings.isSystemNotificationsEnabled = false
                }
                SwBridge.sync()
                if (granted) subscribeAndRegister() else removeSubscription(settings.authToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Rete assente: iscrizione e preferenze si rimandano al prossimo avvio.
                println("[AILA] Preferenze Web Push non aggiornate: ${e.message}")
            }
        }
    }

    /**
     * Logout scelto dall'utente: chiamata quando la sessione e' ancora salvata (vedi
     * AuthRepository.signOut), si cattura qui il token per la richiesta che parte dopo.
     */
    fun onSignOut() {
        val token = AppContainer.settings.authToken
        AppContainer.appScope.launch {
            try {
                removeSubscription(token)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
            WebServiceWorker.unregisterPeriodicSync()
            SwBridge.clear()
        }
    }

    /**
     * Crea (o riusa) l'iscrizione del browser con la chiave VAPID del Worker e la registra sul
     * server insieme alle categorie silenziate. L'upsert del server sull'endpoint fa anche da
     * aggiornamento delle preferenze, e passa il browser all'account che ha appena fatto l'accesso.
     */
    private suspend fun subscribeAndRegister() {
        val api = AppContainer.api
        val key = api.get<VapidKeyDto>("/api/webpush/vapid-public-key", auth = false, offlineCopy = false)
        val raw = jsSubscribe(key.publicKey).await<JsString?>()?.toString() ?: return
        val subscription = json.decodeFromString<SubscriptionDto>(raw)
        api.post<SubscriptionRequestDto, SuccessDto>(
            "/api/webpush/subscription",
            SubscriptionRequestDto(subscription, AppContainer.settings.mutedNotificationKinds)
        )
    }

    /**
     * Toglie l'iscrizione dal server (con [authToken]) e dal browser. Dal browser comunque, anche
     * se il server non risponde: e' quello che garantisce che non arrivino piu' notifiche;
     * l'endpoint rimasto sul server risponde 410 al primo invio e il Worker lo cancella da se'.
     */
    private suspend fun removeSubscription(authToken: String) {
        val endpoint = jsCurrentEndpoint().await<JsString?>()?.toString() ?: return
        try {
            if (authToken.isNotBlank()) {
                AppContainer.api.deleteWithBody<EndpointDto, SuccessDto>(
                    "/api/webpush/subscription",
                    EndpointDto(endpoint),
                    bearerOverride = authToken
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Offline o sessione gia' scaduta: si procede col browser.
        } finally {
            runCatching { jsUnsubscribe().await<JsAny?>() }
        }
    }
}

// --- JS ----------------------------------------------------------------------------------------

private fun jsWebPushSupported(): Boolean = js(
    "window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window"
)

private fun jsPwaInstalled(): Boolean = js(
    """(window.matchMedia && (window.matchMedia('(display-mode: standalone)').matches ||
       window.matchMedia('(display-mode: fullscreen)').matches ||
       window.matchMedia('(display-mode: minimal-ui)').matches)) || navigator.standalone === true"""
)

private fun jsNotificationPermission(): String = js(
    "('Notification' in window) ? Notification.permission : 'unsupported'"
)

// Promise e non callback: il vecchio Safari accettava solo la forma a callback, ma le versioni con
// Web Push (16.4+) hanno quella a Promise.
private fun jsRequestPermission(): Promise<JsString> = js("Notification.requestPermission()")

/**
 * Iscrizione con la chiave del server. Se il browser ne ha gia' una fatta con un'altra chiave
 * (chiavi VAPID cambiate sul Worker) la si rifa': quella vecchia non riceverebbe piu' nulla.
 */
private fun jsSubscribe(publicKey: String): Promise<JsString?> = js(
    """(async () => {
      const toBytes = (b64u) => {
        const b64 = b64u.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (b64u.length % 4)) % 4);
        const raw = atob(b64);
        const out = new Uint8Array(raw.length);
        for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
        return out;
      };
      const key = toBytes(publicKey);
      const reg = await navigator.serviceWorker.ready;
      let sub = await reg.pushManager.getSubscription();
      if (sub && sub.options && sub.options.applicationServerKey) {
        const current = new Uint8Array(sub.options.applicationServerKey);
        const same = current.length === key.length && current.every((b, i) => b === key[i]);
        if (!same) { await sub.unsubscribe(); sub = null; }
      }
      if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
      return JSON.stringify(sub.toJSON());
    })()"""
)

private fun jsCurrentEndpoint(): Promise<JsString?> = js(
    """(async () => {
      if (!('serviceWorker' in navigator) || !('PushManager' in window)) return null;
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      return sub ? sub.endpoint : null;
    })().catch(() => null)"""
)

private fun jsUnsubscribe(): Promise<JsAny?> = js(
    """(async () => {
      const reg = await navigator.serviceWorker.getRegistration();
      const sub = reg ? await reg.pushManager.getSubscription() : null;
      if (sub) await sub.unsubscribe();
      return null;
    })()"""
)
