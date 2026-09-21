import FirebaseCore
import FirebaseMessaging
import shared

/**
 * Implementazione del bridge PushTokenBridge in Swift, sopra FirebaseMessaging (SPM).
 *
 * Non presuppone che Firebase sia configurato: se GoogleService-Info.plist manca (in CI,
 * o finche' non lo aggiungi al progetto Xcode locale), FirebaseApp.configure() non viene mai
 * chiamato da AppDelegate, e Messaging.messaging() andrebbe in crash se interrogato prima.
 * Per questo currentToken() controlla FirebaseApp.app() prima di toccare Messaging: stesso
 * principio di degrado silenzioso gia' usato in PushTokenProvider.android.kt.
 *
 * Il token FCM si puo' ottenere solo dopo che iOS ha consegnato il token APNs
 * (AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken, dopo il dialog dei permessi): a
 * freddo, Kotlin chiede il token una sola volta per sessione e spesso arriva prima, ottenendo un
 * errore e quindi nessuna notifica push per tutta la sessione. Per questo currentToken() aspetta
 * qualche secondo che l'APNs token compaia, invece di fallire subito.
 */
class FirebasePushTokenBridge: PushTokenBridge {

    func currentToken() async throws -> String? {
        guard FirebaseApp.app() != nil else {
            return nil
        }

        // Attesa dell'APNs token (max 30 secondi, un tentativo al secondo): al primo avvio l'utente
        // puo' metterci un po' a rispondere al dialog dei permessi. Non blocca nulla: gira nella
        // coroutine Kotlin che ha chiesto il token. Se non arriva (permesso negato, simulatore
        // senza push) si procede comunque: Messaging.token fallira' con il suo errore, che Kotlin
        // (PushTokenProvider) trasforma in null come prima.
        var attempts = 0
        while Messaging.messaging().apnsToken == nil && attempts < 30 {
            try await Task.sleep(nanoseconds: 1_000_000_000)
            attempts += 1
        }

        return try await withCheckedThrowingContinuation { continuation in
            Messaging.messaging().token { token, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: token)
                }
            }
        }
    }
}
