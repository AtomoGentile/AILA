package circolareplus.data.repository

import circolareplus.algorithms.*
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.apiJson
import circolareplus.data.remote.dto.CurrentSeatMapResponseDto
import circolareplus.data.remote.dto.PublishSeatMapRequestDto
import circolareplus.data.remote.dto.SeatMapHistoryResponseDto
import circolareplus.data.remote.dto.SeatMapDummiesResponseDto
import circolareplus.data.remote.dto.SeatMapDummyDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.CreateSeatMapDummyRequestDto
import circolareplus.data.remote.dto.CreatedSeatMapDummyResponseDto
import circolareplus.data.remote.dto.toDomain
import circolareplus.domain.model.*
import kotlinx.coroutines.ensureActive
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
    companion object {
        const val GENERATION_SEARCH_COUNT = 2000
    }


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

    /** Archivio consultabile: solo pubblicazioni confermate dal server, mai bozze locali. */
    suspend fun getPublishedHistory(): List<PublishedSeatMap> = readPublishedHistory(archive = true)

    private suspend fun readPublishedHistory(archive: Boolean): List<PublishedSeatMap> {
        val path = if (archive) "/api/seat-map/history?archive=true" else "/api/seat-map/history"
        // Lo storico deve riflettere anche pubblicazioni/reset fatti da un altro rappresentante.
        val response: SeatMapHistoryResponseDto = requireApi().get(path, offlineCopy = false)
        return response.history.mapNotNull { entry ->
            val index = entry.mapIndex?.takeIf { it >= 1 } ?: return@mapNotNull null
            PublishedSeatMap(entry.id, index, entry.publishedAt,
                apiJson.decodeFromJsonElement<List<DeskAssignment>>(entry.layout))
        }.sortedBy { it.mapIndex }
    }

    /** Storico regressivo (almeno 4 mappe e gli ultimi 12 mesi) usato da [SeatMapOptimizer.calculateMemoryPenalty]. Un
     * banco da trio genera tutte e tre le coppie possibili (A-B, A-C, B-C): sono tre fatti
     * storici distinti ("erano insieme"), coerenti con come SeatMapOptimizer li ricontrolla uno
     * per uno per i banchi futuri — vedi il commento su SeatMapOptimizer.pairRepeatPenalty. */
    suspend fun getHistoryForOptimizer(): List<SeatMapHistoryRecord> {
        return readPublishedHistory(archive = false).map { entry ->
            val assignments = entry.assignments
            val pairs = assignments.flatMap { a ->
                val occupants = listOfNotNull(a.studentAId, a.studentBId, a.studentCId)
                occupants.indices.flatMap { i -> ((i + 1) until occupants.size).map { j -> occupants[i] to occupants[j] } }
            }.toSet()
            val deskPositions = buildMap {
                assignments.forEach { a ->
                    listOfNotNull(a.studentAId, a.studentBId, a.studentCId).forEach { put(it, a.row to a.column) }
                }
            }
            SeatMapHistoryRecord(mapIndex = entry.mapIndex, pairs = pairs, deskAssignments = deskPositions, publishedAt = entry.publishedAt)
        }
    }

    /** Solo Rappresentante: pubblica la disposizione scelta e la aggiunge allo storico condiviso. */
    suspend fun publish(assignments: List<DeskAssignment>): SuccessDto {
        val latestHistory = getHistoryForOptimizer()
        require(SeatMapOptimizer.recentCompanionViolations(assignments, latestHistory).isEmpty()) {
            "Alcuni compagni sono già stati insieme negli ultimi tre mesi. Separa queste coppie prima di pubblicare."
        }
        val layoutJson = apiJson.encodeToString(assignments)
        val layoutElement = apiJson.parseToJsonElement(layoutJson)
        return requireApi().post("/api/seat-map/publish", PublishSeatMapRequestDto(layoutElement))
    }

    suspend fun saveHistoricalMap(id: String?, assignments: List<DeskAssignment>, publishedAt: String) {
        val layout = apiJson.parseToJsonElement(apiJson.encodeToString(assignments))
        val body = kotlinx.serialization.json.buildJsonObject {
            put("layout", layout)
            put("publishedAt", kotlinx.serialization.json.JsonPrimitive(publishedAt))
        }
        if (id == null) requireApi().post<kotlinx.serialization.json.JsonObject, SuccessDto>("/api/seat-map/history", body)
        else requireApi().put<kotlinx.serialization.json.JsonObject, SuccessDto>("/api/seat-map/history/$id", body)
    }

    suspend fun relinkStudent(previousId: String, studentId: String) {
        requireApi().post<Map<String, String>, SuccessDto>("/api/seat-map/relink-student", mapOf("previousId" to previousId, "studentId" to studentId))
    }

    /** Solo Rappresentante: azzera lo storico (inizio quadrimestre / cambio aula). */
    suspend fun resetHistory(): SuccessDto = requireApi().post("/api/seat-map/reset-history", Unit)

    /** Elimina una sola pubblicazione; il server ricompatta gli indici dello storico. */
    suspend fun deletePublishedMap(id: String): SuccessDto = requireApi().delete("/api/seat-map/history/$id")

    // -------------------------------------------------------------------------
    // Fantocci: studenti inventati dal Rappresentante, con nome e cognome, che
    // occupano un posto come tutti. Non sono utenti: non votano le preferenze e
    // non possono essere votati. L'elenco serve anche agli altri compagni, che
    // devono leggere i nomi dei fantocci nella mappa pubblicata.
    // -------------------------------------------------------------------------

    /** Elenco dei fantocci della classe, in ordine di cognome. */
    suspend fun listDummies(): List<SeatMapDummy> =
        requireApi().get<SeatMapDummiesResponseDto>("/api/seat-map/dummies").dummies.map { it.toDomain() }

    /** Solo Rappresentante: crea un fantoccio e lo restituisce, con l'id del server. */
    suspend fun createDummy(firstName: String, lastName: String): SeatMapDummy {
        val name = firstName.trim()
        val surname = lastName.trim()
        val response = requireApi().post<CreateSeatMapDummyRequestDto, CreatedSeatMapDummyResponseDto>(
            "/api/seat-map/dummies",
            CreateSeatMapDummyRequestDto(firstName = name, lastName = surname)
        )
        response.dummy?.let { return it.toDomain() }
        // Il server non ha restituito il fantoccio creato (aggiornamento a metà):
        // si rilegge l'elenco e si prende quello appena aggiunto, se c'è.
        return listDummies()
            .lastOrNull { it.firstName.equals(name, ignoreCase = true) && it.lastName.equals(surname, ignoreCase = true) }
            // Id provvisorio, valido finché il device non rilegge l'elenco dal server.
            ?: SeatMapDummy(id = "dummy_${kotlin.random.Random.nextLong().toString(16)}", firstName = name, lastName = surname)
    }

    /** Solo Rappresentante: elimina il fantoccio (chi resta sui banchi appare come posto occupato ma non risolto). */
    suspend fun deleteDummy(id: String): SuccessDto =
        requireApi().delete("/api/seat-map/dummies/$id")

    /**
     * Genera fino a 3 proposte d'aula eseguendo l'ottimizzatore a ricerca locale (hill-climbing con
     * accettazione probabilistica, vincolo hard sulle coppie -2/-2) con 2000 seed distinti, selezionando i risultati migliori entro il 5%
     * del punteggio complessivo massimo trovato.
     */
    suspend fun generateThreeProposals(
        students: List<User>,
        profiles: Map<String, StudentProfile>,
        ratings: Map<String, RepresentativeRating>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
        history: List<SeatMapHistoryRecord>,
        weights: OptimizerWeights = OptimizerWeights(),
        // 2 = banchi da coppia, 3 = banchi da trio (stesso algoritmo, vedi SeatMapOptimizer.optimize).
        seatsPerDesk: Int = SeatMapOptimizer.SEATS_PER_DESK_PAIR,
        // Coppie da separare per disciplina (Scheda Classe): penalizzate, non vietate.
        disciplinePairs: Set<Pair<String, String>> = emptySet(),
        // Posti di ogni fila (da davanti a dietro), es. [7, 8, 7]. Vuoto = disposizione automatica.
        rowSeats: List<Int> = emptyList(),
        customDeskSlots: List<SeatMapOptimizer.DeskSlot>? = null,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
        previousProposals: List<SeatMapProposal> = emptyList(),
        onBest: suspend (SeatMapProposal) -> Unit = {},
        shouldStop: () -> Boolean = { false }
    ): List<SeatMapProposal> {
        val slots = customDeskSlots?.filter { it.seats > 0 }
            ?: if (rowSeats.isEmpty()) null else SeatMapOptimizer.buildDeskSlots(rowSeats, seatsPerDesk, students.size)
        require(slots == null || slots.sumOf { it.seats } >= students.size) { "Posti insufficienti per gli studenti della classe" }
        val isSmallClass = students.size < 22
        val baseSeed = kotlin.random.Random.nextLong()

        // Re-evaluate old layouts under the CURRENT inputs; never trust their saved scores.
        val expectedIds = students.map { it.id }.toSet()
        val expectedSlots = slots?.map { Triple(it.row, it.column, it.seats) }?.toSet()
        val reusable = previousProposals.filter { proposal ->
            val ids = proposal.assignments.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }
            ids.size == students.size && ids.toSet() == expectedIds &&
                (expectedSlots == null || proposal.assignments.filter { it.seats > 0 }
                    .map { Triple(it.row, it.column, it.seats) }.toSet() == expectedSlots) &&
                SeatMapOptimizer.rowRotationViolations(proposal.assignments.filter { it.seats > 0 }, profiles, history).isEmpty() &&
                SeatMapOptimizer.recentCompanionViolations(proposal.assignments, history).isEmpty() &&
                !SeatMapOptimizer.scoreLayout(proposal.assignments, profiles, ratings, socialPreferences,
                    history, weights, isSmallClass, disciplinePairs).hasForbiddenPair
        }
        var incumbentProposal = reusable.map { proposal ->
            proposal.copy(
                totalScore = SeatMapOptimizer.scoreLayout(proposal.assignments, profiles, ratings,
                    socialPreferences, history, weights, isSmallClass, disciplinePairs).total,
                satisfaction = SeatMapOptimizer.voteSatisfaction(proposal.assignments, socialPreferences)
            )
        }.minWithOrNull(SeatMapRanking.bestFirst)
        var incumbent = incumbentProposal?.assignments
        onProgress(0, GENERATION_SEARCH_COUNT)
        // Visit different three-desk neighborhoods instead of repeatedly repairing the same one.
        val neighborhoodOrder = mutableListOf<List<Int>>()
        var neighborhoodCursor = 0
        var neighborhoodGeometry: List<Triple<Int, Int, Int>>? = null
        val proposals = mutableListOf<SeatMapProposal>()
        for (i in 1..GENERATION_SEARCH_COUNT) {
            if (proposals.isNotEmpty() && shouldStop()) break
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // A real suspension lets the browser render progress between CPU-bound searches.
            kotlinx.coroutines.delay(1)
            val seed = baseSeed + i
            var generated = SeatMapOptimizer.optimize(
                students = students,
                profiles = profiles,
                ratings = ratings,
                socialPreferences = socialPreferences,
                history = history,
                weights = weights,
                isSmallClass = isSmallClass,
                seed = seed,
                seatsPerDesk = seatsPerDesk,
                disciplinePairs = disciplinePairs,
                slots = slots,
                // Alternate fresh starts with refinement of the best layout found so far.
                initialLayout = if (i % 2 == 1) incumbent else null
            )
            val geometry = generated.map { Triple(it.row, it.column, it.seats) }
            if (geometry != neighborhoodGeometry) {
                neighborhoodGeometry = geometry
                neighborhoodOrder.clear()
                neighborhoodCursor = 0
                val usable = generated.indices.filter { generated[it].seats > 0 }
                if (usable.size == 2) neighborhoodOrder.add(usable)
                for (a in usable.indices) for (b in a + 1 until usable.size) for (c in b + 1 until usable.size)
                    neighborhoodOrder.add(listOf(usable[a], usable[b], usable[c]))
                neighborhoodOrder.shuffle(kotlin.random.Random(baseSeed))
            }
            // Keep early results fast; spend extra work periodically on the best map, rather
            // than repairing weak random starts. Reserved positions are not part of this pool.
            if (i % 8 == 0 && neighborhoodOrder.isNotEmpty()) {
                val origin = incumbent?.filter { it.seats > 0 } ?: generated
                val indices = neighborhoodOrder[neighborhoodCursor++ % neighborhoodOrder.size]
                // Auto-generated geometry can vary with forbidden pairs: only reuse matching grids.
                val matchingGeometry = origin.map { Triple(it.row, it.column, it.seats) } ==
                    generated.map { Triple(it.row, it.column, it.seats) }
                if (matchingGeometry) {
                    val repaired = SeatMapOptimizer.improveDeskNeighborhood(origin, indices,
                        profiles, ratings, socialPreferences, history, weights, isSmallClass, disciplinePairs)
                    fun total(map: List<circolareplus.algorithms.DeskAssignment>) =
                        SeatMapOptimizer.scoreLayout(map, profiles, ratings, socialPreferences, history,
                            weights, isSmallClass, disciplinePairs).total
                    if (SeatMapRanking.compare(total(repaired), SeatMapOptimizer.voteSatisfaction(repaired, socialPreferences),
                        total(generated), SeatMapOptimizer.voteSatisfaction(generated, socialPreferences)) > 0) generated = repaired
                }
            }
            val assignments = if (customDeskSlots != null) SeatMapOptimizer.withVacantDesks(generated, customDeskSlots) else generated
            check(customDeskSlots.orEmpty().filter { it.seats == 0 }.all { slot ->
                assignments.none { it.row == slot.row && it.column == slot.column &&
                    listOfNotNull(it.studentAId, it.studentBId, it.studentCId).isNotEmpty() }
            }) { "Una posizione riservata libera risulta occupata. Ricalcola le proposte." }
            val assignedIds = assignments.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }
            check(assignedIds.size == students.size && assignedIds.toSet().size == assignedIds.size &&
                assignedIds.toSet() == students.map { it.id }.toSet()) {
                "La proposta non assegna esattamente un posto a ogni studente. Ricalcola le proposte."
            }
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

            val proposal = SeatMapProposal(
                id = "proposal_${baseSeed}_$i",
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
            if (incumbentProposal?.let { SeatMapRanking.compare(proposal, it) > 0 } != false) {
                incumbent = proposal.assignments
                incumbentProposal = proposal
                onBest(proposal)
            }
            proposals.add(proposal)
            onProgress(i, GENERATION_SEARCH_COUNT)
        }

        // Multi-start: prioritize the weighted objective, not social satisfaction alone.
        // Do not force diversity at the expense of quality; return fewer alternatives if needed.
        val ranked = proposals.sortedWith(SeatMapRanking.bestFirst).distinctBy { proposal ->
            proposal.assignments.sortedWith(compareBy({ it.row }, { it.column })).map {
                Triple(it.row, it.column, listOfNotNull(it.studentAId, it.studentBId, it.studentCId).sorted())
            }
        }
        val best = ranked.firstOrNull()?.totalScore ?: return emptyList()
        val tolerance = maxOf(kotlin.math.abs(best) * 0.05, 1.0)
        return ranked.filter { it.totalScore >= best - tolerance }.take(3)
    }
}
