package circolareplus.push

import circolareplus.web.WebPush
import kotlinx.coroutines.CancellationException

/**
 * Sul web niente FCM: le notifiche passano da Web Push (rotte /api/webpush/... del Worker), gestito
 * da [circolareplus.web.WebPush]. Il contratto comune viene rispettato cosi':
 * - [getToken] fa l'iscrizione web della sessione e restituisce null: MainAppShell salta quindi
 *   /api/fcm/token e i topic, che il server accetta solo per "android" e "ios";
 * - [unsubscribeFromTopics] sul web la chiama solo AuthRepository.signOut (FcmRepository la usa
 *   anche dopo un'iscrizione ai topic, che qui non avviene mai): e' il punto in cui togliere
 *   l'iscrizione, con la sessione ancora salvata per autorizzare la richiesta.
 */
actual class PushTokenProvider actual constructor() {
    actual suspend fun getToken(): String? {
        try {
            WebPush.onSessionStart()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Rete assente o push non configurato sul Worker (503): si riprova al prossimo avvio.
            // Qui non deve uscire nessuna eccezione: MainAppShell chiama getToken fuori dal try.
            println("[AILA] Web Push non attivato: ${e.message}")
        }
        return null
    }

    actual suspend fun subscribeToTopics(topics: List<String>): Boolean = false

    actual fun unsubscribeFromTopics(topics: List<String>) {
        WebPush.onSignOut()
    }
}

// Usato solo da DELETE /api/fcm/token al logout: con "web" il server non trova token FCM da
// togliere e risponde comunque 200, senza toccare quelli Android/iOS dello stesso account.
actual fun currentPushPlatform(): String = "web"
