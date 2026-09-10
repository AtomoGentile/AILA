import UIKit
import UserNotifications

/**
 * AppDelegate per la registrazione delle notifiche remote (APNs) su iOS.
 *
 * Implementa i callback necessari perché il sistema operativo registri il dispositivo alle
 * notifiche push di Apple. Una volta registrato, il token APNs viene salvato in una property
 * statica, dove Firebase Messaging potrà leggerlo (quando Firebase sarà aggiunto via SPM).
 *
 * Attualmente il token è solo salvato; non viene ancora inviato a Firebase o al backend.
 * Quel passaggio richiede:
 * 1. GoogleService-Info.plist importato nel progetto Xcode
 * 2. Firebase iOS SDK aggiunto via Swift Package Manager
 * 3. Integrazione con PushTokenProvider.ios.kt per comunicare il token al backend
 */
class AppDelegate: UIResponder, UIApplicationDelegate {

    /**
     * Token APNs grezzo (binario) ricevuto da Apple, convertito in stringa esadecimale.
     * Accessibile da qualunque punto dell'app che abbia bisogno di leggerlo.
     */
    static var apnsToken: String?

    /**
     * Inizializzazione dell'app: richiesta dei permessi per le notifiche remote.
     *
     * Questo metodo viene chiamato automaticamente all'avvio dell'app. Qui chiediamo
     * all'utente il permesso di ricevere notifiche (con il sistema di autorizzazione iOS),
     * e una volta ottenuto, registriamo il dispositivo alle notifiche remote di Apple.
     */
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Richiedi il permesso di notifica (mostra il dialog all'utente la prima volta)
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                print("Errore richiesta permesso notifiche: \(error.localizedDescription)")
                return
            }

            if granted {
                print("Utente ha consentito le notifiche")
                // Registra il dispositivo alle notifiche remote di Apple
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            } else {
                print("Utente ha rifiutato le notifiche")
            }
        }

        return true
    }

    /**
     * Callback: il sistema ha registrato il dispositivo con successo e ha assegnato un token APNs.
     *
     * Il token è un valore binario fornito da Apple; lo convertiamo in stringa esadecimale
     * per poterlo trasmettere come testo (via HTTP, file config, ecc.).
     *
     * @param application L'app delegate
     * @param deviceToken Il token binario ricevuto da Apple
     */
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Converti il token binario in stringa esadecimale (es. "a1b2c3d4...ef01")
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        AppDelegate.apnsToken = token
        print("APNs token registrato: \(token)")

        // TODO Firebase: Quando Firebase iOS SDK sarà aggiunto via SPM e
        // GoogleService-Info.plist sarà presente nel progetto, qui si dovrà aggiungere:
        //
        // import FirebaseMessaging
        // ...
        // Messaging.messaging().apnsToken = deviceToken
        //
        // Questo permetterà a Firebase di ottenere un token FCM (diverso da questo token APNs,
        // ma derivato da esso) che il backend userà per mandare notifiche tramite FCM.
    }

    /**
     * Callback: la registrazione alle notifiche remote è fallita.
     *
     * Questo può succedere se l'utente non ha dato il permesso, se il dispositivo non supporta
     * le notifiche, o per errori di rete. Non è fatale: l'app continua a funzionare normalmente,
     * solo senza ricevere notifiche push.
     *
     * @param application L'app delegate
     * @param error L'errore che ha impedito la registrazione
     */
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("Errore registrazione notifiche remote: \(error.localizedDescription)")
        // Questo non è un errore fatale: l'app continua a funzionare senza notifiche push
    }

    /**
     * Callback: è arrivata una notifica push mentre l'app è in foreground (aperta).
     *
     * Per ora non fare nulla: iOS mostra comunque la notifica di sistema. In futuro,
     * se volessi gestire la notifica all'interno dell'app (navigare a una schermata specifica,
     * aggiornare dati, ecc.) lo faresti qui.
     */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Per ora mostra la notifica normalmente
        completionHandler([.banner, .sound, .badge])
    }

    /**
     * Callback: l'utente ha tappato su una notifica push.
     *
     * Qui potremmo navigare a una schermata specifica in base al contenuto della notifica,
     * o eseguire altre azioni. Per ora non fare nulla (il tap chiude il drawer di notifica).
     */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // TODO: parsing della notifica e navigazione in-app se necessario
        completionHandler()
    }
}
