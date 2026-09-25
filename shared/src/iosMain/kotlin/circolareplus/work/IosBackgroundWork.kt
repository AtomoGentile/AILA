package circolareplus.work

import circolareplus.ai.AnalysisActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * La parte di sistema del lavoro in background, implementata in Swift (BackgroundWorkBridge.swift)
 * sopra UIKit/BackgroundTasks e iniettata da iOSApp.swift. Stesso schema degli altri bridge.
 *
 * Tutte le chiamate arrivano sul thread principale.
 */
interface BackgroundWorkBridge {
    /**
     * C'e' un'analisi da tenere viva con l'app in background (o e' cambiata): l'equivalente del
     * servizio in primo piano di Android. [title] e [detail] sono gli stessi testi della notifica.
     */
    fun analysisActive(title: String, detail: String)

    /** Nessuna analisi da tenere viva: il task di sistema si chiude. */
    fun analysisIdle()
}

object BackgroundWorkBridgeHolder {
    var bridge: BackgroundWorkBridge? = null
}

/** Un giro in background avviato da Swift, che lo annulla se iOS ritira il tempo concesso. */
class BackgroundRun internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

/**
 * Lavoro in background su iOS, l'equivalente di `AnalysisForegroundService` e
 * `CircularsSyncWorker` di Android.
 *
 * - Analisi sul telefono: osserva [AnalysisActivity] e avvisa il bridge, che tiene viva l'app
 *   (task di sistema con avanzamento e tasto per annullare) finche' la coda non e' vuota. Prima
 *   l'analisi si fermava appena si usciva dall'app.
 * - Riassunti delle circolari rimaste indietro: [runRefresh], chiamato dal `BGAppRefreshTask` di
 *   AilaBackground.swift dopo il controllo delle circolari nuove.
 */
object IosBackgroundWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing = false

    /** Da chiamare una volta all'avvio (iOSApp.swift), dopo aver iniettato il bridge. */
    fun start() {
        if (observing) return
        observing = true
        scope.launch {
            AnalysisActivity.state.collect { state ->
                val bridge = BackgroundWorkBridgeHolder.bridge ?: return@collect
                if (state.needsKeepAlive) {
                    bridge.analysisActive(state.displayTitle, state.displayDetail)
                } else {
                    bridge.analysisIdle()
                }
            }
        }
    }

    /** Il tasto per annullare del task di sistema, o il tempo concesso da iOS finito. */
    fun stopAllAnalyses() {
        AnalysisActivity.requestStopAll()
    }

    /**
     * Un giro di [BackgroundCircularsSync] nel tempo che resta a un `BGAppRefreshTask` dopo il
     * controllo delle circolari nuove (AilaBackground.swift, che registra il task): al massimo due
     * circolari, e si chiude comunque entro [budgetMillis].
     * [onDone] viene chiamata una sola volta, sul thread principale, con l'esito.
     */
    fun runRefresh(budgetMillis: Long, onDone: (Boolean) -> Unit): BackgroundRun {
        val job = scope.launch {
            val ok = try {
                withTimeout(budgetMillis) {
                    BackgroundCircularsSync.run(maxCirculars = 2)
                }
                true
            } catch (e: Throwable) {
                // Rete assente, tempo finito o giro annullato da iOS: si riprova al prossimo.
                false
            }
            onDone(ok)
        }
        return BackgroundRun(job)
    }

}
