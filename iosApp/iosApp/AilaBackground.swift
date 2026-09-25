import BackgroundTasks
import Foundation
import UserNotifications
import shared

/**
 * Refresh in background delle circolari senza push remote (niente APNs: l'app è firmata con
 * Apple ID gratuito via SideStore). iOS risveglia l'app con un BGAppRefreshTask, si chiama
 * backgroundSync() (Kotlin) e, se ci sono circolari nuove, si mostra una notifica locale. Con il
 * tempo che resta si riassumono le circolari rimaste indietro (IosBackgroundWork.runRefresh,
 * l'equivalente di CircularsSyncWorker su Android).
 *
 * È l'unico punto che registra `taskId`: registrare due volte lo stesso identificativo fa
 * terminare l'app.
 *
 * Ogni evento finisce nel log circolare "bg_log" (UserDefaults), letto dalla schermata
 * "Diagnostica background" delle Impostazioni.
 */
final class AilaBackground: BackgroundRefreshBridge {

    /** Deve coincidere con BGTaskSchedulerPermittedIdentifiers in Info.plist. */
    static let taskId = "com.circolareplus.refresh"

    /** Tempo che ci si concede per risveglio, sotto i ~30 s di iOS. */
    private static let refreshBudget: TimeInterval = 25

    /** Giro dei riassunti in corso, annullato se iOS ritira il tempo. Solo dal main thread. */
    private static var currentRun: BackgroundRun?

    private static let logKey = "bg_log"
    private static let maxLogLines = 100
    private static let logLock = NSLock()

    // MARK: - Registrazione e programmazione

    /** Da chiamare in iOSApp.init, prima che finisca il lancio. */
    static func register() {
        let ok = BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refresh)
        }
        if !ok { log("register FALLITO (id non in Info.plist?)") }
    }

    /** Chiede il prossimo risveglio fra almeno 1 ora (iOS decide quando, davvero). */
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 60 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
            log("submit ok (+1h)")
        } catch {
            log("submit FALLITO: \(error.localizedDescription)")
        }
    }

    // MARK: - Esecuzione

    private static func handle(_ task: BGAppRefreshTask) {
        // Riprogramma subito: se il task scade, il prossimo è già in coda.
        schedule()
        log("risveglio")

        // setTaskCompleted va chiamato una volta sola: scadenza e fine lavoro possono incrociarsi.
        let once = CompletionOnce(task: task)
        let work = Task { @MainActor in
            let start = Date()
            let result = await runSync(source: "bg")
            // Prima le circolari nuove (veloce, serve alla notifica), poi i riassunti se avanza tempo.
            let left = refreshBudget - Date().timeIntervalSince(start)
            if result.success && left >= 5 && !Task.isCancelled {
                await summarizeBacklog(budgetMillis: Int64(left * 1000))
            }
            once.complete(success: result.success)
        }
        task.expirationHandler = {
            work.cancel()
            DispatchQueue.main.async { currentRun?.cancel() }
            log("scaduto (expirationHandler)")
            once.complete(success: false)
        }
    }

    /**
     * Sync + notifica. Sul main actor: Kotlin/Native permette di chiamare le funzioni suspend
     * solo dal main thread (impostazione di default).
     */
    @MainActor
    static func runSync(source: String) async -> (success: Bool, message: String) {
        do {
            let count = try await BackgroundSyncKt.backgroundSync().intValue
            if count > 0 { await notify(count: count) }
            let message = "ok: \(count) nuove"
            log("[\(source)] \(message)")
            return (true, message)
        } catch {
            let message = "errore: \(error.localizedDescription)"
            log("[\(source)] \(message)")
            return (false, message)
        }
    }

    /** Riassume al massimo due circolari arretrate (Kotlin, BackgroundCircularsSync). */
    @MainActor
    private static func summarizeBacklog(budgetMillis: Int64) async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            currentRun = IosBackgroundWork.shared.runRefresh(budgetMillis: budgetMillis) { ok in
                currentRun = nil
                log("[bg] riassunti: \(ok.boolValue ? "ok" : "interrotti")")
                continuation.resume()
            }
        }
    }

    @MainActor
    private static func notify(count: Int) async {
        // Rispetta gli interruttori delle Impostazioni dell'app.
        let settings = AppContainer.shared.settings
        guard settings.isSystemNotificationsEnabled,
              settings.isNotificationKindEnabled(kind: "circulars") else {
            log("notifica silenziata dalle Impostazioni")
            return
        }

        let center = UNUserNotificationCenter.current()
        let status = await center.notificationSettings().authorizationStatus
        guard status == .authorized || status == .provisional else {
            log("notifica non autorizzata (stato \(status.rawValue))")
            return
        }

        let content = UNMutableNotificationContent()
        content.title = "AILA"
        content.body = count == 1 ? "1 nuova circolare" : "\(count) nuove circolari"
        content.sound = .default
        // Il tocco apre le Circolari (NotificationCategoryMapper: action=new_circular).
        content.userInfo = ["action": "new_circular"]

        let request = UNNotificationRequest(
            identifier: "\(localNotificationPrefix)\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        do {
            try await center.add(request)
        } catch {
            log("notifica FALLITA: \(error.localizedDescription)")
        }
    }

    /** Prefisso delle notifiche locali del refresh: AppDelegate non le duplica in campanella. */
    static let localNotificationPrefix = "aila-bg-"

    // MARK: - Ponte per la schermata debug

    func simulateWakeUp() async throws -> String {
        AilaBackground.log("risveglio simulato")
        return await AilaBackground.runSync(source: "sim").message
    }

    // MARK: - Log circolare

    static func log(_ line: String) {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = .current
        let entry = "\(formatter.string(from: Date())) \(line)"

        logLock.lock()
        defer { logLock.unlock() }
        let defaults = UserDefaults.standard
        var lines = (defaults.string(forKey: logKey) ?? "")
            .split(separator: "\n")
            .map(String.init)
        lines.append(entry)
        if lines.count > maxLogLines {
            lines.removeFirst(lines.count - maxLogLines)
        }
        defaults.set(lines.joined(separator: "\n"), forKey: logKey)
    }
}

/** Chiama setTaskCompleted al massimo una volta, da qualunque thread. */
private final class CompletionOnce {
    private let task: BGTask
    private let lock = NSLock()
    private var done = false

    init(task: BGTask) { self.task = task }

    func complete(success: Bool) {
        lock.lock()
        defer { lock.unlock() }
        guard !done else { return }
        done = true
        task.setTaskCompleted(success: success)
    }
}
