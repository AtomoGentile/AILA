package circolareplus.push

/**
 * TODO iOS: a differenza di Android, ottenere un token FCM su iOS richiede codice Swift lato
 * AppDelegate (registrazione APNs in `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)`
 * e assegnazione a `Messaging.messaging().apnsToken`) PRIMA che `Messaging.messaging().token`
 * restituisca qualcosa — un flusso che tocca l'app delegate nativa e non è raggiungibile da
 * codice Kotlin condiviso puro. Questo può essere completato solo quando esisterà un vero
 * progetto Xcode con Firebase aggiunto via Swift Package Manager (vedi FIREBASE_SETUP.md):
 * a quel punto la via più semplice è implementare la registrazione in Swift e passare il token
 * già ottenuto a questa classe (es. tramite un piccolo bridge Kotlin/Native), oppure sostituire
 * questo file con un binding diretto a FirebaseMessaging una volta disponibile il cinterop.
 *
 * Per ora restituisce sempre null: l'app iOS resta pienamente funzionante, semplicemente senza
 * notifiche push finché questo pezzo non viene completato lato Xcode.
 */
actual class PushTokenProvider actual constructor() {
    actual suspend fun getToken(): String? = null
}

actual fun currentPushPlatform(): String = "ios"
