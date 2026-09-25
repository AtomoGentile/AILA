import UIKit
import BackgroundTasks
import shared

/**
 * Implementazione Swift di BackgroundWorkBridge (Kotlin, IosBackgroundWork.kt): tiene viva l'app
 * mentre il modello sul telefono analizza una circolare, come il servizio in primo piano di
 * Android (AnalysisForegroundService).
 *
 * Due livelli, perche' il primo da solo non basta a un'analisi da minuti:
 * - `beginBackgroundTask`: qualche decina di secondi in piu' appena si esce dall'app, sempre
 *   concesso. Copre le analisi brevi e il tempo per chiudere bene quelle lunghe.
 * - `BGContinuedProcessingTask` (iOS 26): il lavoro iniziato dall'utente continua in background,
 *   con l'avanzamento mostrato dal sistema e un tasto per annullarlo, che qui ferma l'analisi e la
 *   coda come il tasto Stop della notifica Android. Se iOS non lo concede (strategia `.fail`) resta
 *   il primo livello.
 *
 * Tutte le chiamate arrivano sul thread principale (Dispatchers.Main lato Kotlin, coda `.main` per
 * il launch handler del task).
 */
final class BackgroundWorkBridgeImpl: BackgroundWorkBridge {

    private var backgroundTaskId: UIBackgroundTaskIdentifier = .invalid
    private var continuedTask: BGContinuedProcessingTask?
    private var continuedTaskRequested = false
    private var progressTimer: Timer?

    func analysisActive(title: String, detail: String) {
        if backgroundTaskId == .invalid {
            backgroundTaskId = UIApplication.shared.beginBackgroundTask(withName: "Analisi circolare") { [weak self] in
                self?.endBackgroundTask()
            }
        }

        if let task = continuedTask {
            task.updateTitle(title, subtitle: detail)
        } else if !continuedTaskRequested {
            submitContinuedTask(title: title, detail: detail)
        }
    }

    func analysisIdle() {
        continuedTaskRequested = false
        if let task = continuedTask {
            continuedTask = nil
            progressTimer?.invalidate()
            progressTimer = nil
            task.progress.completedUnitCount = task.progress.totalUnitCount
            task.setTaskCompleted(success: true)
        }
        endBackgroundTask()
    }

    private func endBackgroundTask() {
        guard backgroundTaskId != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTaskId)
        backgroundTaskId = .invalid
    }

    /**
     * Registra e chiede il task. L'identificatore e' nuovo a ogni analisi (registrare due volte lo
     * stesso fa terminare l'app): Info.plist ammette `com.circolareplus.analysis.*`.
     */
    private func submitContinuedTask(title: String, detail: String) {
        continuedTaskRequested = true
        let identifier = "com.circolareplus.analysis.\(UUID().uuidString)"

        let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
            guard let task = task as? BGContinuedProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            guard let self = self, self.continuedTaskRequested else {
                // L'analisi e' gia' finita prima che iOS avviasse il task.
                task.setTaskCompleted(success: true)
                return
            }
            self.start(task)
        }
        guard registered else {
            continuedTaskRequested = false
            return
        }

        let request = BGContinuedProcessingTaskRequest(identifier: identifier, title: title, subtitle: detail)
        // Se iOS non puo' avviarlo subito non lo mette in coda: un'analisi iniziata ora non ha
        // senso che parta fra dieci minuti. Resta beginBackgroundTask.
        request.strategy = .fail
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Task di analisi in background non concesso: \(error.localizedDescription)")
        }
    }

    private func start(_ task: BGContinuedProcessingTask) {
        continuedTask = task

        // L'annullamento dal sistema (tasto dell'utente o tempo finito) ferma l'analisi e la coda,
        // come il tasto Stop della notifica Android.
        task.expirationHandler = { [weak self] in
            DispatchQueue.main.async {
                IosBackgroundWork.shared.stopAllAnalyses()
                guard let self = self, let current = self.continuedTask, current === task else { return }
                self.continuedTask = nil
                self.progressTimer?.invalidate()
                self.progressTimer = nil
                task.setTaskCompleted(success: false)
            }
        }

        // Il modello non dice a che punto e' della risposta: l'avanzamento sale piano verso il 95%
        // per mostrare che il lavoro prosegue (iOS chiude i task che non avanzano), e si completa
        // in analysisIdle.
        task.progress.totalUnitCount = 100
        task.progress.completedUnitCount = 0
        progressTimer?.invalidate()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 3, repeats: true) { [weak task] _ in
            guard let task = task else { return }
            let completed = task.progress.completedUnitCount
            if completed < 95 {
                task.progress.completedUnitCount = completed + max(1, (95 - completed) / 10)
            }
        }
    }
}
