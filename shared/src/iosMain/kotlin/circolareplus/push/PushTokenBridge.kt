package circolareplus.push

/**
 * Implementata in Swift (FirebasePushTokenBridge.swift) sopra FirebaseMessaging, aggiunta via
 * Swift Package Manager. Iniettata da iOSApp.swift in PushTokenBridgeHolder.bridge prima che
 * PushTokenProvider possa essere interrogato.
 *
 * Stesso pattern di AppleIntelligenceBridge (vedi circolareplus.ai): un'interfaccia Kotlin,
 * implementata da una classe Swift che il framework `shared` esporta come protocollo
 * Objective-C, iniettata dall'app all'avvio.
 */
interface PushTokenBridge {
    /**
     * Il token FCM corrente, o null se Firebase non è configurato (manca
     * GoogleService-Info.plist) o se la richiesta fallisce. Non lancia mai.
     */
    suspend fun currentToken(): String?
}

/** Un solo bridge per tutta l'app, iniettato da Swift all'avvio. */
object PushTokenBridgeHolder {
    var bridge: PushTokenBridge? = null
}
