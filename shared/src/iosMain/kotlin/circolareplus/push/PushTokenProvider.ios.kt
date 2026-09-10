package circolareplus.push

/**
 * Ottiene il token FCM tramite il bridge Swift (FirebasePushTokenBridge.swift, sopra
 * FirebaseMessaging via Swift Package Manager). La registrazione APNs vera e propria
 * (`application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` +
 * `Messaging.messaging().apnsToken`) avviene lato AppDelegate nativo, prima che questo possa
 * restituire qualcosa — non è raggiungibile da Kotlin puro, da qui si interroga solo il
 * risultato finale.
 *
 * Se il bridge non è stato iniettato (Firebase non configurato: manca
 * GoogleService-Info.plist) o la richiesta fallisce, ritorna null invece di lanciare: l'app
 * resta pienamente funzionante, semplicemente senza notifiche push.
 */
actual class PushTokenProvider actual constructor() {
    actual suspend fun getToken(): String? = try {
        PushTokenBridgeHolder.bridge?.currentToken()
    } catch (e: Exception) {
        null
    }
}

actual fun currentPushPlatform(): String = "ios"
