package circolareplus.push

/**
 * Ottiene il token del dispositivo per le notifiche push (Firebase Cloud Messaging).
 *
 * Restituisce null (invece di lanciare un'eccezione) quando Firebase non è ancora configurato
 * per questa piattaforma — cioè finché mancano rispettivamente google-services.json (Android) o
 * GoogleService-Info.plist (iOS) nel progetto — così l'app resta completamente utilizzabile senza
 * notifiche push fino a quando Simone non crea il progetto Firebase e completa la configurazione
 * nativa descritta in FIREBASE_SETUP.md.
 */
expect class PushTokenProvider() {
    suspend fun getToken(): String?
}

/** Nome piattaforma nel formato atteso da POST /api/fcm/token ("android" | "ios"). */
expect fun currentPushPlatform(): String
