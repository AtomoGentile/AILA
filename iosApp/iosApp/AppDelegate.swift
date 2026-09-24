import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import shared

/**
 * AppDelegate per la registrazione delle notifiche remote (APNs) su iOS, e per l'avvio di
 * Firebase quando è configurato.
 *
 * `FirebaseApp.configure()` va chiamato una sola volta, il prima possibile, ma SOLO se
 * `GoogleService-Info.plist` è presente nel bundle: chiamarlo senza quel file crasha subito
 * l'app. Il file esiste solo in locale (vedi .gitignore, come google-services.json per
 * Android) — quindi qui si controlla la sua presenza invece di assumerla, per restare
 * compilabile ed eseguibile anche in CI, dove non c'è.
 */
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {

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
        // Configura Firebase solo se GoogleService-Info.plist è nel bundle (vedi doc classe).
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }

        // Senza questo, willPresent/didReceive qui sotto non vengono MAI chiamati: iOS non
        // mostra le notifiche ad app aperta, e il tocco non registra né naviga. Va impostato
        // qui, prima che finisca il lancio (serve anche perché il tocco che avvia l'app a
        // freddo arrivi a didReceive), e dopo FirebaseApp.configure(), che si aggancia al
        // delegate esistente invece di sostituirlo.
        UNUserNotificationCenter.current().delegate = self

        // Azzera il numerino rosso sull'icona ogni volta che l'app torna attiva (e all'avvio): le
        // push FCM lo incrementano ma nessuno lo scala mai, quindi resterebbe li' per sempre.
        // Si osserva la notifica di sistema invece di implementare applicationDidBecomeActive:
        // con il ciclo di vita SwiftUI (UIApplicationDelegateAdaptor + scene) quel metodo del
        // delegate non e' garantito, la notifica UIApplication.didBecomeActiveNotification si'.
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { _ in
            AppDelegate.clearBadge()
        }

        // Avvio a freddo: l'app era completamente chiusa ed è stata aperta tappando una
        // notifica push. Il deep link si imposta anche qui da launchOptions, cosi' e' pronto
        // prima ancora che MainAppShell venga composta (didReceive puo' arrivare dopo).
        if let remoteNotification = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            let category = categoryFromUserInfo(remoteNotification)
            if !category.isEmpty {
                PendingDeepLink.shared.category = category
            }
        }

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

        // Solo se Firebase e' stato configurato (vedi didFinishLaunchingWithOptions): senza
        // GoogleService-Info.plist, FirebaseApp.app() e' nil e Messaging.messaging() crasherebbe.
        if FirebaseApp.app() != nil {
            Messaging.messaging().apnsToken = deviceToken
        }
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
     * Azzera il badge dell'icona. `setBadgeCount` (iOS 16+) e' l'API corrente: il deployment
     * target del progetto e' iOS 26 (vedi project.yml), quindi non serve il ripiego
     * `applicationIconBadgeNumber`, deprecato da iOS 17.
     */
    private static func clearBadge() {
        UNUserNotificationCenter.current().setBadgeCount(0) { error in
            if let error = error {
                print("Errore azzeramento badge: \(error.localizedDescription)")
            }
        }
    }

    /**
     * Id del messaggio FCM (`gcm.message_id` nel payload) usato da [onPushReceived] per non
     * registrare due volte in campanella lo stesso push (arriva sia da willPresent sia da
     * didReceive). Ripiego sull'identifier della richiesta di notifica se il payload non lo ha
     * (es. notifiche locali o payload non FCM).
     */
    private func messageId(for notification: UNNotification) -> String {
        if let fcmId = notification.request.content.userInfo["gcm.message_id"] as? String, !fcmId.isEmpty {
            return fcmId
        }
        return notification.request.identifier
    }

    /**
     * Converte lo `userInfo` di una notifica (il payload `data` di FCM/APNs, con valori a volte
     * tipizzati come `AnyObject`/`NSString`) in un `[String: String]`, poi lo passa a
     * [NotificationCategoryMapper] (Kotlin condiviso, vedi
     * shared/.../domain/model/NotificationCategoryMapper.kt) per ottenere la categoria di
     * destinazione ("circulars", "board", "seatmap", "seatmap_preferences", "polls", "ranking_polls", oppure ""
     * se non riconosciuta). Non deve mai crashare: valori non convertibili vengono ignorati.
     */
    private func categoryFromUserInfo(_ userInfo: [AnyHashable: Any]) -> String {
        var data: [String: String] = [:]
        for (key, value) in userInfo {
            guard let stringKey = key as? String else { continue }
            data[stringKey] = "\(value)"
        }
        return NotificationCategoryMapper.shared.categoryFrom(data: data)
    }

    /**
     * Callback: è arrivata una notifica push mentre l'app è in foreground (aperta).
     *
     * iOS non mostrerebbe la notifica di sistema in questo caso, quindi ci pensiamo noi a
     * registrarla nello storico locale (la campanella in-app, vedi NotificationsScreen) così
     * l'utente la ritrova anche se non tocca il banner. `onPushReceived` (Kotlin condiviso,
     * LocalSettingsManager) applica gli interruttori dell'utente per categoria, deduplica e scrive
     * in campanella, e dice se mostrare anche il banner di sistema: se ritorna false (categoria
     * silenziata o "notifiche di sistema" spento) non si mostra nulla. Non tocchiamo
     * PendingDeepLink qui: questo callback scatta quando la notifica arriva soltanto, non quando
     * l'utente la tocca — la navigazione forzata avviene solo in didReceive, sotto.
     */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let content = notification.request.content
        let category = categoryFromUserInfo(content.userInfo)
        let showBanner = AppContainer.shared.settings.onPushReceived(
            messageId: messageId(for: notification),
            title: content.title,
            body: content.body,
            category: category
        )
        // A schermata aperta la lista deve aggiornarsi da sola (vedi DataRefreshEvents, Kotlin).
        DataRefreshEvents.shared.request()

        if showBanner {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([])
        }
    }

    /**
     * Callback: l'utente ha tappato su una notifica push (app in background o avviata a freddo
     * dal tocco: in quel secondo caso il deep link e' gia' stato impostato anche da
     * launchOptions in didFinishLaunchingWithOptions).
     *
     * Registriamo la notifica nello storico locale: con l'app in background iOS mostra il banner
     * da solo e willPresent non scatta mai, quindi il tocco e' l'unico punto in cui la campanella
     * la vede. Se invece era gia' passata da willPresent, `onPushReceived` la riconosce dallo
     * stesso messageId e non la duplica. Poi impostiamo PendingDeepLink perché MainAppShell
     * navighi alla schermata giusta (anche se la categoria e' silenziata: l'utente l'ha tappata).
     */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let content = response.notification.request.content
        let category = categoryFromUserInfo(content.userInfo)
        _ = AppContainer.shared.settings.onPushReceived(
            messageId: messageId(for: response.notification),
            title: content.title,
            body: content.body,
            category: category
        )

        if !category.isEmpty {
            PendingDeepLink.shared.category = category
        }

        completionHandler()
    }
}
