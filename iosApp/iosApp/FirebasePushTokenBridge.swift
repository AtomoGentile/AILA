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
 */
class FirebasePushTokenBridge: PushTokenBridge {

    func currentToken() async throws -> String? {
        guard FirebaseApp.app() != nil else {
            return nil
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
