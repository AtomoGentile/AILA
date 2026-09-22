package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Livello di qualita' di un'analisi, lo stesso che usa il server (migrazione 006): un'analisi
 * sostituisce quella che c'e' solo se ha un livello uguale o superiore.
 * 0 = ripiego euristico, 1 = AI locale, 2 = Google Gemini.
 */
val CircularAiClassification.tier: Int
    get() = when {
        isFallback -> 0
        modelLabel.startsWith("Google Gemini") -> 2
        else -> 1
    }

/**
 * Quali analisi di circolari sono in corso su questo telefono, visto da fuori della schermata.
 *
 * Serve al servizio in primo piano di Android (la notifica "Analisi della circolare n. X" con il
 * tasto Stop): l'analisi vive nell'interfaccia condivisa, la notifica nel codice Android, e
 * questo e' il punto dove si incontrano. Su iOS nessuno lo osserva.
 */
object AnalysisActivity {

    /**
     * Dove girano le analisi: non nella schermata, cosi' non si fermano uscendo dal dettaglio
     * della circolare o chiudendo l'interfaccia. Un'analisi che fallisce non ferma le altre.
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    data class State(
        /** Circolare in analisi adesso, `null` se nessuna. */
        val running: Int? = null,
        val runningTitle: String = "",
        /** `true` se l'analisi in corso usa il modello sul telefono (e quindi scalda e consuma). */
        val onDevice: Boolean = false,
        /** Circolari in attesa del loro turno. */
        val queued: List<Int> = emptyList()
    ) {
        val isIdle: Boolean get() = running == null && queued.isEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /** Numeri di circolare di cui qualcuno (la notifica) ha chiesto lo stop. */
    val stopRequests: SharedFlow<Int> = _stopRequests.asSharedFlow()

    fun update(state: State) {
        _state.value = state
    }

    fun requestStop(circularNumber: Int) {
        _stopRequests.tryEmit(circularNumber)
    }
}
