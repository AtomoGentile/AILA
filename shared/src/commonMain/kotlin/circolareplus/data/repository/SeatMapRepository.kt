package circolareplus.data.repository

import circolareplus.algorithms.*
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.apiJson
import circolareplus.data.remote.dto.CurrentSeatMapResponseDto
import circolareplus.data.remote.dto.PublishSeatMapRequestDto
import circolareplus.data.remote.dto.SeatMapHistoryResponseDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Repository per il recupero dati dell'aula e l'esecuzione dell'ottimizzatore.
 * La generazione delle 3 proposte (calcolo puro) resta sul dispositivo del Rappresentante;
 * pubblicazione, storico e reset passano invece dal Worker (rotte /api/seat-map), unica fonte
 * di verità condivisa da tutta la classe.
 */
class SeatMapRepository(private val api: ApiClient? = null) {

    private val _isPreferencesWindowOpen = MutableStateFlow(false)
    val isPreferencesWindowOpen: StateFlow<Boolean> = _isPreferencesWindowOpen.asStateFlow()

    private val _currentActiveProposal = MutableStateFlow<SeatMapProposal?>(null)
    val currentActiveProposal: StateFlow<SeatMapProposal?> = _currentActiveProposal.asStateFlow()

    fun setPreferencesWindowOpen(open: Boolean) {
        _isPreferencesWindowOpen.value = open
    }

    private fun requireApi(): ApiClient =
        api ?: error("SeatMapRepository creato senza ApiClient: le chiamate di rete non sono disponibili")

    /** Mappa attualmente pubblicata e visibile a tutta la classe (null se non è mai stata pubblicata). */
    suspend fun getCurrentSeatMap(): List<DeskAssignment>? {
        val response: CurrentSeatMapResponseDto = requireApi().get("/api/seat-map/current")
        return response.seatMap?.let { apiJson.decodeFromJsonElement<List<DeskAssignment>>(it.layout) }
    }

    /** Storico regressivo (max 4 mappe) usato da [SeatMapOptimizer.calculateMemoryPenalty]. Un
     * banco da trio genera tutte e tre le coppie possibili (A-B, A-C, B-C): sono tre fatti
     * storici distinti ("erano insieme"), coerenti con come SeatMapOptimizer li ricontrolla uno
     * per uno per i banchi futuri — vedi il commento su SeatMapOptimizer.pairRepeatPenalty. */
    suspend fun getHistoryForOptimizer(): List<SeatMapHistoryRecord> {
        val response: SeatMapHistoryResponseDto = requireApi().get("/api/seat-map/history")
        return response.history.mapNotNull { entry ->
            val mapIndex = entry.mapIndex ?: return@mapNotNull null
            val assignments = apiJson.decodeFromJsonElement<List<DeskAssignment>>(entry.layout)
            val pairs = assignments.flatMap { a ->
                val occupants = listOfNotNull(a.studentAId, a.studentBId, a.studentCId)
                occupants.indices.flatMap { i -> ((i + 1) until occupants.size).map { j -> occupants[i] to occupants[j] } }
            }.toSet()
            val deskPositions = buildMap {
                assignments.forEach { a ->
                    listOfNotNull(a.studentAId, a.studentBId, a.studentCId).forEach { put(it, a.row to a.column) }
                }
            }
            SeatMapHistoryRecord(mapIndex = mapIndex, pairs = pairs, deskAssignments = deskPositions)
        }
    }

    /** Solo Rappresentante: pubblica la disposizione scelta (ruota automaticamente lo storico N-1..N-4). */
    suspend fun publish(assignments: List<DeskAssignment>): SuccessDto {
        val layoutJson = apiJson.encodeToString(assignments)
        val layoutElement = apiJson.parseToJsonElement(layoutJson)
        return requireApi().post("/api/seat-map/publish", PublishSeatMapRequestDto(layoutElement))
    }

    /** Solo Rappresentante: azzera lo storico (inizio quadrimestre / cambio aula). */
    suspend fun resetHistory(): SuccessDto = requireApi().post("/api/seat-map/reset-history", Unit)

    /**
     * Genera le 3 proposte d'aula eseguendo l'ottimizzatore a ricerca locale (hill-climbing con
     * accettazione probabilistica, vincolo hard sulle coppie -2/-2) con 3 seed distinti, per dare
     * varietà al Rappresentante pur partendo da punti di partenza diversi.
     */
    fun generateThreeProposals(
        students: List<User>,
        profiles: Map<String, StudentProfile>,
        ratings: Map<String, RepresentativeRating>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
        history: List<SeatMapHistoryRecord>,
        weights: OptimizerWeights = OptimizerWeights(),
        // 2 = banchi da coppia, 3 = banchi da trio (stesso algoritmo, vedi SeatMapOptimizer.optimize).
        seatsPerDesk: Int = SeatMapOptimizer.SEATS_PER_DESK_PAIR,
        // Coppie da separare per disciplina (Scheda Classe): penalizzate, non vietate.
        disciplinePairs: Set<Pair<String, String>> = emptySet()
    ): List<SeatMapProposal> {
        val isSmallClass = students.size < 22
        val baseSeed = kotlin.random.Random.nextLong()

        val proposals = (1..3).map { i ->
            val seed = baseSeed + i
            val assignments = SeatMapOptimizer.optimize(
                students = students,
                profiles = profiles,
                ratings = ratings,
                socialPreferences = socialPreferences,
                history = history,
                weights = weights,
                isSmallClass = isSmallClass,
                seed = seed,
                seatsPerDesk = seatsPerDesk,
                disciplinePairs = disciplinePairs
            )
            val breakdown = SeatMapOptimizer.scoreLayout(
                assignments = assignments,
                profiles = profiles,
                ratings = ratings,
                socialPreferences = socialPreferences,
                history = history,
                weights = weights,
                isSmallClass = isSmallClass,
                disciplinePairs = disciplinePairs
            )

            SeatMapProposal(
                id = "proposal_$i",
                assignments = assignments,
                totalScore = breakdown.total,
                satisfaction = SeatMapOptimizer.voteSatisfaction(assignments, socialPreferences),
                socialScore = breakdown.social,
                disciplinePenalty = breakdown.discipline,
                didacticScore = breakdown.didactic,
                heightPenalty = breakdown.heightPenalty,
                memoryPenalty = breakdown.memoryPenalty,
                burnoutPenalty = breakdown.burnoutPenalty,
                priorityBonus = breakdown.priorityBonus,
                columnNoisePenalty = breakdown.columnNoisePenalty
            )
        }

        return proposals.sortedByDescending { it.totalScore }
    }
}
