package circolareplus.ai.assistant

import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.SeatMapHistoryRecord
import circolareplus.data.remote.dto.MyPreferenceVoteDto
import circolareplus.data.remote.dto.PollAssignmentDto
import circolareplus.data.remote.dto.PollDetailDto
import circolareplus.data.remote.dto.PollSummaryDto
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.data.repository.PollsRepository
import circolareplus.data.repository.PreferencesRepository
import circolareplus.data.repository.RatingsRepository
import circolareplus.data.repository.SeatMapRepository
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Tutto quello che l'assistente puo' guardare per rispondere a una domanda.
 *
 * Diviso in due pezzi di proposito. La parte "statica" ([circulars], [calendarEvents],
 * [proposals]...) e' gia' in memoria nella schermata principale: l'app la tiene aggiornata per
 * conto suo, quindi ripescarla dal server per l'assistente sarebbe traffico sprecato e
 * rischierebbe pure di mostrare in chat dati diversi da quelli a video. La parte "dinamica"
 * ([dynamic]) invece sta dietro a rotte che l'app chiama solo quando si apre la schermata
 * corrispondente (sondaggi, mappa posti, valutazioni): la si carica una volta all'apertura della
 * chat e la si riusa per tutte le domande della sessione.
 */
data class AssistantKnowledge(
    val todayIso: String,
    val user: User?,
    val profile: StudentProfile?,
    val classmates: List<User> = emptyList(),
    val circulars: List<Circular> = emptyList(),
    val classifications: Map<Int, CircularAiClassification> = emptyMap(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val proposals: List<Proposal> = emptyList(),
    val dynamic: AssistantDynamicKnowledge = AssistantDynamicKnowledge()
) {
    val role: UserRole get() = user?.role ?: UserRole.STUDENT

    /** Nome leggibile di uno studente dal suo id, per non mostrare mai UUID in chat. */
    fun nameOf(studentId: String?): String? {
        if (studentId == null) return null
        classmates.firstOrNull { it.id == studentId }?.let { return "${it.firstName} ${it.lastName}" }
        dynamic.ratings.firstOrNull { it.studentId == studentId }
            ?.let { return "${it.firstName} ${it.lastName}" }
        val me = user
        if (me != null && me.id == studentId) return "${me.firstName} ${me.lastName}"
        return null
    }
}

/**
 * La parte della conoscenza che arriva da chiamate di rete dedicate, gia' filtrata per ruolo.
 *
 * [unavailable] non e' un dettaglio: una sezione che non si e' caricata (rete caduta, rotta che
 * risponde 403) deve finire nel prompt come "non disponibile", altrimenti il modello legge
 * l'assenza come "non esiste" e risponde con sicurezza che la mappa posti non e' mai stata
 * pubblicata quando in realta' non e' riuscito a leggerla.
 */
data class AssistantDynamicKnowledge(
    val polls: List<PollSummaryDto> = emptyList(),
    val currentPoll: PollDetailDto? = null,
    val pollAssignments: List<PollAssignmentDto> = emptyList(),
    val seatMap: List<DeskAssignment> = emptyList(),
    val seatMapHistory: List<SeatMapHistoryRecord> = emptyList(),
    val ratings: List<RatingEntryDto> = emptyList(),
    val myPreferences: List<MyPreferenceVoteDto> = emptyList(),
    val preferencesOpen: Boolean? = null,
    val unavailable: List<String> = emptyList()
)

/**
 * Carica la parte dinamica della conoscenza, **filtrando per ruolo**.
 *
 * Il filtro sta qui e non nel prompt di proposito: chiedere al modello di non parlare di certi
 * dati e' una raccomandazione, non una garanzia, e su dati di compagni di classe reali la
 * differenza conta. Quello che uno studente non puo' vedere non viene proprio caricato, quindi
 * non c'e' niente da far trapelare — nemmeno con una domanda costruita apposta.
 *
 * Cosa vede chi:
 * - **Tutti**: sondaggi della classe, mappa posti attualmente pubblicata, le *proprie*
 *   preferenze sociali, se la finestra delle preferenze e' aperta.
 * - **Solo Rappresentante**: storico delle mappe pubblicate e la tabella valutazioni della
 *   classe (didattica, chiasso, Priority Pass) — gli stessi dati che la sua schermata Classe
 *   gli mostra gia'.
 * - **Nessuno**: la matrice completa delle preferenze sociali (chi ha messo -2 a chi). E'
 *   l'unico dato dell'app che nessuna schermata mostra mai a nessuno, nemmeno al
 *   Rappresentante, che vede solo il risultato dell'ottimizzatore. Darla in pasto a un modello
 *   che poi ne discute in chat sarebbe il modo piu' rapido di trasformare l'assistente in una
 *   fuga di notizie sui rapporti fra compagni.
 */
class AssistantKnowledgeLoader(
    private val pollsRepository: PollsRepository,
    private val seatMapRepository: SeatMapRepository,
    private val ratingsRepository: RatingsRepository,
    private val preferencesRepository: PreferencesRepository
) {

    suspend fun load(role: UserRole): AssistantDynamicKnowledge = coroutineScope {
        val pollsDeferred = async {
            section("Sondaggi") {
                val list = pollsRepository.listPolls()
                // Dettaglio e assegnazioni solo dell'ultimo sondaggio pubblicato: sono quelle le
                // domande che la gente fa davvero ("quando ho l'interrogazione?"), e caricare il
                // dettaglio di tutti moltiplicherebbe le chiamate per un contesto che poi non
                // entrerebbe comunque nel prompt.
                val latest = list.firstOrNull { it.isPublished }
                val detail = latest?.let { poll -> runCatchingNull { pollsRepository.getPoll(poll.id) } }
                val assignments = latest
                    ?.takeIf { it.isCalculated }
                    ?.let { poll -> runCatchingNull { pollsRepository.getAssignments(poll.id).assignments } }
                    .orEmpty()
                Triple(list, detail, assignments)
            }
        }

        val seatMapDeferred = async {
            section("Mappa posti") { seatMapRepository.getCurrentSeatMap().orEmpty() }
        }

        val historyDeferred = async {
            if (role != UserRole.REPRESENTATIVE) {
                Section.skipped<List<SeatMapHistoryRecord>>()
            } else {
                section("Storico mappe") { seatMapRepository.getHistoryForOptimizer() }
            }
        }

        val ratingsDeferred = async {
            if (role != UserRole.REPRESENTATIVE) {
                Section.skipped<List<RatingEntryDto>>()
            } else {
                section("Valutazioni classe") { ratingsRepository.listRatings() }
            }
        }

        val myPrefsDeferred = async {
            section("Preferenze sociali") { preferencesRepository.myVotes().votes }
        }

        val configDeferred = async {
            section("Configurazione preferenze") { preferencesRepository.getConfig().preferencesOpen }
        }

        val polls = pollsDeferred.await()
        val seatMap = seatMapDeferred.await()
        val history = historyDeferred.await()
        val ratings = ratingsDeferred.await()
        val myPrefs = myPrefsDeferred.await()
        val config = configDeferred.await()

        AssistantDynamicKnowledge(
            polls = polls.value?.first.orEmpty(),
            currentPoll = polls.value?.second,
            pollAssignments = polls.value?.third.orEmpty(),
            seatMap = seatMap.value.orEmpty(),
            seatMapHistory = history.value.orEmpty(),
            ratings = ratings.value.orEmpty(),
            myPreferences = myPrefs.value.orEmpty(),
            preferencesOpen = config.value,
            // Raccolti solo qui, dopo gli await: le sezioni girano in parallelo e una lista
            // mutabile condivisa fra coroutine su dispatcher multi-thread non e' sicura.
            unavailable = listOfNotNull(
                polls.error, seatMap.error, history.error, ratings.error, myPrefs.error, config.error
            )
        )
    }

    /** Esito di una sezione: il valore se e' andata, il motivo leggibile se non e' andata. */
    private data class Section<T>(val value: T?, val error: String?) {
        companion object {
            fun <T> skipped(): Section<T> = Section(null, null)
        }
    }

    /**
     * Esegue un caricamento e, se fallisce, ne annota il motivo invece di far cadere tutta la
     * raccolta: una rotta che non risponde deve costare quella sezione, non l'intera chat.
     */
    private suspend fun <T> section(label: String, block: suspend () -> T): Section<T> =
        try {
            Section(block(), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Section(null, "$label: non caricato (${e.message ?: e::class.simpleName})")
        }

    private suspend fun <T> runCatchingNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
