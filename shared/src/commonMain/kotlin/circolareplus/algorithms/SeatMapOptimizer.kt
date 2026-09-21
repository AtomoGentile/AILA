package circolareplus.algorithms

import circolareplus.domain.model.RepresentativeRating
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/**
 * Risultato della disposizione calcolata dal SeatMapOptimizer.
 * @Serializable: viene pubblicata così com'è come JSON in /api/seat-map/publish.
 */
@Serializable
data class DeskAssignment(
    val row: Int,        // 0-indexed da davanti a dietro (0 = prima fila)
    val column: Int,     // 0-indexed da sinistra a destra
    val studentAId: String?,
    val studentBId: String?,
    // Terzo posto per i "banchi da trio" (SeatMapOptimizer.optimize(..., seatsPerDesk = 3)).
    // Default null: i banchi da coppia esistenti (storico, mappe già pubblicate) restano
    // validi senza bisogno di migrazione, sia lato client sia nel JSON già salvato sul server.
    val studentCId: String? = null,
    // Capienza del banco (2 = coppia, 3 = trio), salvata nel JSON pubblicato. Serve perché un
    // banco da trio può avere meno di tre occupanti (classe piccola, coppie vietate): dedurre
    // "trio" da studentCId != null faceva sparire il terzo posto in una mappa già pubblicata.
    // Default 2: le mappe pubblicate prima di questo campo restano banchi da coppia.
    val seats: Int = 2
)

data class SeatMapProposal(
    val id: String,
    val assignments: List<DeskAssignment>,
    val totalScore: Double,
    // null = nessun voto espresso: vedi SeatMapOptimizer.voteSatisfaction.
    val satisfaction: SeatMapOptimizer.VoteSatisfaction?,
    val socialScore: Double,
    val disciplinePenalty: Double,
    val didacticScore: Double,
    val heightPenalty: Double,
    val memoryPenalty: Double,
    val burnoutPenalty: Double,
    // Con default: le componenti sotto contribuiscono al totale ma non hanno un campo dedicato
    // fra le tre "voci principali" (sociale/didattica/disciplina) mostrate nelle schede.
    val priorityBonus: Double = 0.0,
    val columnNoisePenalty: Double = 0.0
)

data class SeatMapHistoryRecord(
    val mapIndex: Int, // 1 for N-1, 2 for N-2, 3 for N-3, 4 for N-4
    val pairs: Set<Pair<String, String>>,
    val deskAssignments: Map<String, Pair<Int, Int>> // studentId -> (row, col)
)

data class OptimizerWeights(
    val wSocial: Double = 1.0,      // Range 0.5 - 1.5
    val wDiscipline: Double = 1.0,  // Range 0.5 - 1.5
    val wDidactic: Double = 1.0     // Range 0.5 - 1.5
) {
    init {
        require(wSocial in 0.5..1.5)
        require(wDiscipline in 0.5..1.5)
        require(wDidactic in 0.5..1.5)
    }
}

/**
 * Motore di ottimizzazione per la Mappa Posti Aula (Specifica v2.6 / v3.0 Master)
 */
object SeatMapOptimizer {

    /**
     * Calcolo Matrice Sociale (S) e blindatura rifiuti
     */
    fun calculateSocialScore(scoreAtoB: SocialPreferenceScore, scoreBtoA: SocialPreferenceScore): Int {
        val a = scoreAtoB.value
        val b = scoreBtoA.value

        // Doppio Rifiuto Assoluto (-2 / -2)
        if (a == -2 && b == -2) return -1000

        // Rifiuto Singolo Assoluto (-2 / QUALSIASI)
        if (a == -2 || b == -2) return -500

        // Conflitto Medio (-1 / -1)
        if (a == -1 && b == -1) return -100

        // Rifiuto Lieve Unilaterale (-1 con 0 o +1)
        if ((a == -1 && (b == 0 || b == 1)) || (b == -1 && (a == 0 || a == 1))) return -40

        // Match Reciproco Massimo (+2 / +2)
        if (a == 2 && b == 2) return 90

        // Match Asimmetrico Forte (+2 / +1 o +1 / +2)
        if ((a == 2 && b == 1) || (a == 1 && b == 2)) return 65

        // Match Reciproco Medio (+1 / +1)
        if (a == 1 && b == 1) return 40

        // Unilaterale Massimo (+2 / 0 o 0 / +2)
        if ((a == 2 && b == 0) || (a == 0 && b == 2)) return 20

        // Unilaterale Medio (+1 / 0 o 0 / +1)
        if ((a == 1 && b == 0) || (a == 0 && b == 1)) return 10

        // Indifferenza Reciproca (0 / 0)
        return 0
    }

    /**
     * Calcolo Matrice Didattica (D) & Tutoring
     */
    fun calculateDidacticScore(levelA: Int, levelB: Int): Int {
        val pair = listOf(levelA, levelB).sortedDescending()
        val high = pair[0]
        val low = pair[1]

        return when {
            // Tutoring Diretto (5+1, 4+1)
            (high == 5 && low == 1) || (high == 4 && low == 1) -> 25
            // Pari Eccellenti (5+5)
            high == 5 && low == 5 -> 25
            // Pari Alti (5+4, 4+4)
            (high == 5 && low == 4) || (high == 4 && low == 4) -> 20
            // Tutoring Leggero (5+2: +20pt, 4+2: +15pt)
            high == 5 && low == 2 -> 20
            high == 4 && low == 2 -> 15
            // Supporto Didattico (5+3, 4+3, 3+1)
            (high == 5 && low == 3) || (high == 4 && low == 3) || (high == 3 && low == 1) -> 15
            // Pari Medi / Supporto Lieve (3+3, 3+2)
            (high == 3 && low == 3) || (high == 3 && low == 2) -> 10
            // Pari Bassi (2+2)
            high == 2 && low == 2 -> 0
            // Debole / Rischio Stallo (2+1)
            high == 2 && low == 1 -> -15
            // Stallo Didattico Assoluto (1+1)
            high == 1 && low == 1 -> -40
            else -> 0
        }
    }

    /**
     * Penalità Chiasso / Disciplina (C)
     */
    fun calculateDeskNoisePenalty(noiseA: Int, noiseB: Int): Int {
        return if (noiseA == 5 && noiseB == 5) {
            -180 // Azzera qualsiasi match +2/+2
        } else if ((noiseA == 5 && noiseB in 1..2) || (noiseB == 5 && noiseA in 1..2)) {
            0    // Bilanciamento virtuoso
        } else {
            0
        }
    }

    fun calculateColumnNoisePenalty(noiseFront: Int, noiseBack: Int): Int {
        return if (noiseFront == 5 && noiseBack == 5) -120 else 0
    }

    /**
     * Penalità Altezza a Scalare (Paltezza)
     * Paltezza = max(0, floor((Hdavanti - Hdietro) / 5)) * 30 pt
     */
    fun calculateHeightPenalty(heightFrontCm: Int, heightBackCm: Int): Int {
        val diff = heightFrontCm - heightBackCm
        if (diff <= 0) return 0
        val steps = diff / 5
        return steps * 30
    }

    /**
     * Prevenzione Tutor Burnout (Pburnout) per studenti Livello 5
     * 2 mappe consecutive in tutoring (5+1): -80 pt
     * 3 mappe consecutive: -150 pt (forzatura a riposo didattico con L4 o L5)
     */
    fun calculateTutorBurnoutPenalty(
        studentId: String,
        partnerId: String,
        ratings: Map<String, RepresentativeRating>,
        consecutiveTutoringCount: Int
    ): Int {
        val rA = ratings[studentId] ?: return 0
        val rB = ratings[partnerId] ?: return 0

        val isTutoring5_1 = (rA.didactic == 5 && rB.didactic == 1) || (rB.didactic == 5 && rA.didactic == 1)
        if (!isTutoring5_1) return 0

        return when {
            consecutiveTutoringCount >= 2 -> 150
            consecutiveTutoringCount == 1 -> 80
            else -> 0
        }
    }

    /**
     * Penalità Memoria Storica (Pmemoria) su 4 Mappe
     * N-1: -300 pt ripetizione coppia, -150 pt stesso banco, -60 pt prima fila / -30 pt altre
     * N-2: -200 pt coppia, -100 pt stesso banco
     * N-3: -100 pt coppia, -50 pt stesso banco
     * N-4: -50 pt coppia, -25 pt stesso banco
     * Se classe < 22 studenti e media d'aula negativa: abbattimento del 50%
     */
    fun calculateMemoryPenalty(
        studentAId: String,
        studentBId: String,
        deskRow: Int,
        deskCol: Int,
        history: List<SeatMapHistoryRecord>,
        isSmallClass: Boolean = false
    ): Int {
        var totalPenalty = pairRepeatPenalty(studentAId, studentBId, history) +
            occupantDeskHistoryPenalty(studentAId, deskRow, deskCol, history) +
            occupantDeskHistoryPenalty(studentBId, deskRow, deskCol, history)

        if (isSmallClass) {
            totalPenalty = (totalPenalty * 0.5).toInt()
        }

        return totalPenalty
    }

    /**
     * Componente "erano una coppia" della penalità memoria: [studentAId] e [studentBId] sedevano
     * insieme (stesso banco) in una delle ultime 4 mappe. Fattorizzata fuori da
     * [calculateMemoryPenalty] perché nei banchi da trio va sommata una volta per OGNI coppia
     * possibile del banco (A-B, A-C, B-C: tre fatti storici distinti, nessun doppio conteggio),
     * mentre [occupantDeskHistoryPenalty] — l'altra componente, legata al singolo studente e non
     * alla coppia — va sommata una volta sola per studente, non per coppia in cui compare.
     */
    private fun pairRepeatPenalty(studentAId: String, studentBId: String, history: List<SeatMapHistoryRecord>): Int {
        val pairAB = studentAId to studentBId
        val pairBA = studentBId to studentAId
        var total = 0
        for (record in history) {
            if (record.pairs.contains(pairAB) || record.pairs.contains(pairBA)) {
                total += when (record.mapIndex) {
                    1 -> 300 // N-1
                    2 -> 200 // N-2
                    3 -> 100 // N-3
                    4 -> 50  // N-4
                    else -> 0
                }
            }
        }
        return total
    }

    /**
     * Componente "ero già a QUESTO banco fisico" della penalità memoria (più il bonus negativo di
     * "stessa fila" per N-1): dipende solo da [studentId] e dalla posizione del banco, non da chi
     * gli siede accanto. Vedi il commento su [pairRepeatPenalty] per il perché della separazione.
     */
    private fun occupantDeskHistoryPenalty(
        studentId: String,
        deskRow: Int,
        deskCol: Int,
        history: List<SeatMapHistoryRecord>
    ): Int {
        val deskPos = deskRow to deskCol
        var total = 0
        for (record in history) {
            val pos = record.deskAssignments[studentId]
            if (pos == deskPos) {
                total += when (record.mapIndex) {
                    1 -> 150 // N-1
                    2 -> 100 // N-2
                    3 -> 50  // N-3
                    4 -> 25  // N-4
                    else -> 0
                }
            }
            if (record.mapIndex == 1 && pos?.first == deskRow) {
                total += if (deskRow == 0) 60 else 30
            }
        }
        return total
    }

    /**
     * Priority Pass Bonus: +1000 pt per assegnazione entro le prime 3 file (row 0-indexed, quindi
     * righe 0, 1, 2), penalità severa se il priority pass c'è ma l'assegnazione è più indietro.
     * In precedenza il vincolo era "sempre e solo la prima fila" (row == 0); allargato su richiesta
     * a "prima, seconda o terza fila" per lasciare più margine all'ottimizzatore.
     */
    fun calculatePriorityBonus(profileA: StudentProfile?, profileB: StudentProfile?, row: Int): Int {
        var bonus = 0
        if (row <= MAX_PRIORITY_ROW) {
            if (profileA?.priorityPass == true) bonus += 1000
            if (profileB?.priorityPass == true) bonus += 1000
        } else {
            // Penalità severa se ha il priority pass ma è oltre la terza fila
            if (profileA?.priorityPass == true) bonus -= 1000
            if (profileB?.priorityPass == true) bonus -= 1000
        }
        return bonus
    }

    /**
     * Generalizza [calculatePriorityBonus] a un numero qualunque di occupanti del banco (coppia
     * o trio): stessa logica per ciascuno, un bonus/malus indipendente per persona invece che
     * incastrato in una firma a due parametri fissi.
     */
    private fun calculatePriorityBonusForOccupants(profiles: List<StudentProfile?>, row: Int): Int {
        var bonus = 0
        for (profile in profiles) {
            if (profile?.priorityPass != true) continue
            bonus += if (row <= MAX_PRIORITY_ROW) 1000 else -1000
        }
        return bonus
    }

    /** Riga massima (0-indexed) considerata valida per il Priority Pass: 2 = terza fila. */
    const val MAX_PRIORITY_ROW = 2

    /** Numero di banchi per fila usato in generazione (valore fisso, isolato per essere ritarabile in futuro). */
    const val DEFAULT_DESKS_PER_ROW = 3

    const val DEFAULT_MAX_ITERATIONS = 3000
    const val DEFAULT_MAX_NO_IMPROVEMENT = 600

    /**
     * Divieto Assoluto: true se A e B si rifiutano reciprocamente con -2/-2. Una disposizione che
     * affianca questa coppia nello stesso banco non deve MAI essere accettata dall'ottimizzatore,
     * indipendentemente dallo score (che pure la penalizza pesantemente con -1000, ma questo da solo
     * non basta a impedirla in modo strutturale).
     */
    fun isForbiddenPair(
        studentAId: String,
        studentBId: String,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>
    ): Boolean {
        val aToB = socialPreferences[studentAId to studentBId]?.value ?: 0
        val bToA = socialPreferences[studentBId to studentAId]?.value ?: 0
        return aToB == -2 && bToA == -2
    }

    private fun hasForbiddenPairAtDesk(
        desk: DeskAssignment,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>
    ): Boolean = deskPairIds(desk).any { (a, b) -> isForbiddenPair(a, b, socialPreferences) }

    /** Occupanti effettivi di un banco (2 per una coppia, fino a 3 per un trio), posti vuoti esclusi. */
    private fun deskOccupantIds(desk: DeskAssignment): List<String> =
        listOfNotNull(desk.studentAId, desk.studentBId, desk.studentCId)

    /** Tutte le coppie possibili fra gli occupanti di un banco: una sola (A,B) per una coppia,
     * tre — (A,B), (A,C), (B,C) — per un trio. Ogni funzione di punteggio "a coppia" esistente
     * (sociale, didattica, disciplina, burnout, ripetizione storica) viene sommata su ognuna: è
     * così che l'algoritmo resta lo stesso, solo applicato a più coppie per banco. */
    private fun deskPairIds(desk: DeskAssignment): List<Pair<String, String>> {
        val ids = deskOccupantIds(desk)
        if (ids.size < 2) return emptyList()
        val pairs = mutableListOf<Pair<String, String>>()
        for (i in ids.indices) {
            for (j in (i + 1) until ids.size) {
                pairs.add(ids[i] to ids[j])
            }
        }
        return pairs
    }

    /**
     * Conta quante mappe consecutive (a partire da N-1, indietro nel tempo) vedono [studentId]
     * (livello didattico 5) appaiato con un compagno di livello 1, usando i rating ATTUALI come
     * proxy dei rating storici (lo storico non persiste i rating al momento della pubblicazione,
     * solo le coppie e i banchi — vedi [SeatMapHistoryRecord]). Si ferma al primo "buco".
     */
    fun computeConsecutiveTutoringCount(
        studentId: String,
        ratings: Map<String, RepresentativeRating>,
        history: List<SeatMapHistoryRecord>
    ): Int {
        val myRating = ratings[studentId] ?: return 0
        if (myRating.didactic != 5) return 0

        var count = 0
        for (mapIndex in 1..4) {
            val record = history.firstOrNull { it.mapIndex == mapIndex } ?: break
            val pair = record.pairs.firstOrNull { it.first == studentId || it.second == studentId } ?: break
            val partnerId = if (pair.first == studentId) pair.second else pair.first
            val partnerRating = ratings[partnerId] ?: break
            if (partnerRating.didactic != 1) break
            count++
        }
        return count
    }

    private fun deskAvgBehavior(desk: DeskAssignment, ratings: Map<String, RepresentativeRating>): Int? {
        val values = deskOccupantIds(desk).mapNotNull { ratings[it]?.behavior }
        return if (values.isEmpty()) null else values.sum() / values.size
    }

    private fun deskAvgHeight(desk: DeskAssignment, profiles: Map<String, StudentProfile>): Int? {
        val values = deskOccupantIds(desk).mapNotNull { profiles[it]?.heightCm }
        return if (values.isEmpty()) null else values.sum() / values.size
    }

    data class ScoreBreakdown(
        val social: Double,
        val discipline: Double,
        val didactic: Double,
        val heightPenalty: Double,
        val columnNoisePenalty: Double,
        val memoryPenalty: Double,
        val burnoutPenalty: Double,
        val priorityBonus: Double,
        val total: Double,
        val hasForbiddenPair: Boolean
    )

    /**
     * Calcola il punteggio completo di una disposizione, cablando TUTTE le penalità della
     * specifica (incluse quelle storicamente mai invocate in produzione: altezza, chiasso di
     * colonna, burnout tutor).
     */
    fun scoreLayout(
        assignments: List<DeskAssignment>,
        profiles: Map<String, StudentProfile>,
        ratings: Map<String, RepresentativeRating>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
        history: List<SeatMapHistoryRecord>,
        weights: OptimizerWeights,
        isSmallClass: Boolean
    ): ScoreBreakdown {
        var social = 0.0
        var discipline = 0.0
        var didactic = 0.0
        var memory = 0.0
        var priority = 0.0
        var burnout = 0.0
        var height = 0.0
        var columnNoise = 0.0
        var hasForbiddenPair = false

        val byPosition = assignments.associateBy { it.row to it.column }

        for (desk in assignments) {
            val occupantIds = deskOccupantIds(desk)
            // Ogni coppia possibile del banco (una sola per una coppia, tre per un trio) prende
            // esattamente lo stesso trattamento di prima: stesse funzioni pairwise, sommate.
            for ((a, b) in deskPairIds(desk)) {
                val prefAtoB = socialPreferences[a to b] ?: SocialPreferenceScore.NEUTRAL
                val prefBtoA = socialPreferences[b to a] ?: SocialPreferenceScore.NEUTRAL
                social += calculateSocialScore(prefAtoB, prefBtoA)
                if (isForbiddenPair(a, b, socialPreferences)) hasForbiddenPair = true

                val ratA = ratings[a]
                val ratB = ratings[b]
                if (ratA != null && ratB != null) {
                    didactic += calculateDidacticScore(ratA.didactic, ratB.didactic)
                    discipline += calculateDeskNoisePenalty(ratA.behavior, ratB.behavior)

                    if (ratA.didactic == 5 && ratB.didactic == 1) {
                        burnout += calculateTutorBurnoutPenalty(a, b, ratings, computeConsecutiveTutoringCount(a, ratings, history))
                    } else if (ratB.didactic == 5 && ratA.didactic == 1) {
                        burnout += calculateTutorBurnoutPenalty(b, a, ratings, computeConsecutiveTutoringCount(b, ratings, history))
                    }
                }
            }

            // Penalità memoria: componente "erano insieme" per coppia + componente "già a questo
            // banco" per singolo occupante (vedi i commenti su pairRepeatPenalty/
            // occupantDeskHistoryPenalty per il perché sono tenute separate).
            var deskMemory = deskPairIds(desk).sumOf { (a, b) -> pairRepeatPenalty(a, b, history) } +
                occupantIds.sumOf { occupantDeskHistoryPenalty(it, desk.row, desk.column, history) }
            if (isSmallClass) deskMemory = (deskMemory * 0.5).toInt()
            memory += deskMemory

            priority += calculatePriorityBonusForOccupants(occupantIds.map { profiles[it] }, desk.row)

            // Confronto col banco immediatamente davanti nella stessa colonna (chiasso/altezza a scalare).
            val deskFront = byPosition[desk.row - 1 to desk.column]
            if (deskFront != null) {
                val noiseFront = deskAvgBehavior(deskFront, ratings)
                val noiseBack = deskAvgBehavior(desk, ratings)
                if (noiseFront != null && noiseBack != null) {
                    // calculateColumnNoisePenalty restituisce un valore <= 0: lo riportiamo a magnitudine positiva.
                    columnNoise += -calculateColumnNoisePenalty(noiseFront, noiseBack)
                }

                val heightFront = deskAvgHeight(deskFront, profiles)
                val heightBack = deskAvgHeight(desk, profiles)
                if (heightFront != null && heightBack != null) {
                    height += calculateHeightPenalty(heightFront, heightBack)
                }
            }
        }

        val total = (social * weights.wSocial) +
                (discipline * weights.wDiscipline) +
                (didactic * weights.wDidactic) -
                memory - height - burnout - columnNoise + priority

        return ScoreBreakdown(
            social = social,
            discipline = discipline,
            didactic = didactic,
            heightPenalty = height,
            columnNoisePenalty = columnNoise,
            memoryPenalty = memory,
            burnoutPenalty = burnout,
            priorityBonus = priority,
            total = total,
            hasForbiddenPair = hasForbiddenPair
        )
    }

    /**
     * Soddisfazione media degli studenti di una disposizione.
     * @property percentage 0-100, media delle soddisfazioni dei singoli studenti valutati.
     * @property evaluatedStudents studenti che hanno votato e siedono con almeno un compagno.
     * @property uncomfortableStudents fra questi, quanti siedono con almeno una persona che hanno
     * votato in negativo (-1 o -2).
     */
    data class VoteSatisfaction(val percentage: Double, val evaluatedStudents: Int, val uncomfortableStudents: Int)

    /**
     * Soddisfazione stimata di una disposizione, calcolata sul SINGOLO studente: ciascuno guarda
     * solo i compagni con cui siede davvero, non l'insieme di tutte le sue preferenze (che in un
     * banco da due o tre non possono essere soddisfatte in blocco).
     *
     * Ogni compagno di banco vale in base al voto dato dallo studente: +2 = 100%, +1 = 75%,
     * 0 = 50%, -1 = 25%, -2 = 0% (lineare, `(voto + 2) / 4`). La soddisfazione dello studente è la
     * media sui suoi compagni (uno in un banco da coppia, fino a due in un trio); quella della
     * disposizione è la media sugli studenti valutati.
     *
     * Sono valutati solo gli studenti che hanno espresso almeno un voto (diverso da 0) su
     * qualcuno seduto in disposizione, e che siedono con almeno un compagno: chi non ha votato
     * varrebbe un 50% "neutro" che diluisce la media senza dire nulla, chi siede solo non ha
     * compagni da giudicare.
     *
     * Volutamente NON dipende da pesi dei cursori, modalità coppia/trio, storico, altezza, chiasso,
     * didattica o Priority Pass. Ritorna null se nessuno studente è valutabile ("non calcolabile" è
     * più onesto di un 0% o un 100% inventato).
     */
    fun voteSatisfaction(
        assignments: List<DeskAssignment>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>
    ): VoteSatisfaction? {
        val seated = assignments.flatMap { deskOccupantIds(it) }.toSet()
        val voters = socialPreferences
            .filter { (pair, score) -> score.value != 0 && pair.first in seated && pair.second in seated }
            .keys.map { it.first }.toSet()

        var evaluated = 0
        var uncomfortable = 0
        var sumOfSatisfactions = 0.0
        for (desk in assignments) {
            val occupants = deskOccupantIds(desk)
            for (student in occupants) {
                if (student !in voters) continue
                val mates = occupants.filter { it != student }
                if (mates.isEmpty()) continue
                val votes = mates.map { (socialPreferences[student to it]?.value ?: 0) }
                sumOfSatisfactions += votes.map { (it + 2) / 4.0 }.average()
                evaluated++
                if (votes.any { it < 0 }) uncomfortable++
            }
        }
        if (evaluated == 0) return null
        return VoteSatisfaction(sumOfSatisfactions / evaluated * 100.0, evaluated, uncomfortable)
    }

    /** Numero di posti per banco supportati dall'algoritmo: coppia (default, storico) o trio. */
    const val SEATS_PER_DESK_PAIR = 2
    const val SEATS_PER_DESK_TRIO = 3

    /**
     * Riferimento a un singolo posto di un banco: usato dalla ricerca locale e dall'editor
     * manuale. `seatIndex` 0/1/2 corrisponde a studentAId/studentBId/studentCId — prima era un
     * `isSeatA: Boolean` che copriva solo i banchi da coppia, generalizzato a un indice per
     * supportare anche il terzo posto dei banchi da trio.
     */
    data class SeatRef(val deskIndex: Int, val seatIndex: Int) {
        fun studentId(assignments: List<DeskAssignment>): String? {
            val desk = assignments[deskIndex]
            return when (seatIndex) {
                0 -> desk.studentAId
                1 -> desk.studentBId
                else -> desk.studentCId
            }
        }
    }

    private fun withSeat(desk: DeskAssignment, seatIndex: Int, studentId: String?): DeskAssignment =
        when (seatIndex) {
            0 -> desk.copy(studentAId = studentId)
            1 -> desk.copy(studentBId = studentId)
            else -> desk.copy(studentCId = studentId)
        }

    /** Tutti i posti esistenti (occupati o vuoti) della disposizione, [seatsPerDesk] per banco:
     * 2 per i banchi da coppia, 3 per i banchi da trio. Usata dalla ricerca locale per scegliere
     * a caso due posti da scambiare — mai il terzo posto quando si genera in modalità coppia,
     * altrimenti l'ottimizzatore "inventerebbe" trii anche quando non richiesto. */
    private fun allSeats(assignments: List<DeskAssignment>, seatsPerDesk: Int): List<SeatRef> =
        assignments.indices.flatMap { deskIndex -> (0 until seatsPerDesk).map { SeatRef(deskIndex, it) } }

    /** Scambia gli occupanti di due posti (anche di banchi diversi, anche il terzo posto di un
     * trio). Usata sia dalla ricerca locale sia dall'editor manuale (swap-by-tap) per applicare
     * uno scambio scelto dall'utente. */
    fun swapSeats(assignments: List<DeskAssignment>, seat1: SeatRef, seat2: SeatRef): List<DeskAssignment> {
        if (seat1 == seat2) return assignments

        if (seat1.deskIndex == seat2.deskIndex) {
            // Stesso banco: scambiare due posti equivale a invertirli fra loro.
            val desk = assignments[seat1.deskIndex]
            val student1 = seat1.studentId(assignments)
            val student2 = seat2.studentId(assignments)
            val swappedDesk = withSeat(withSeat(desk, seat1.seatIndex, student2), seat2.seatIndex, student1)
            return assignments.toMutableList().also { it[seat1.deskIndex] = swappedDesk }
        }

        val student1 = seat1.studentId(assignments)
        val student2 = seat2.studentId(assignments)
        return assignments.mapIndexed { index, desk ->
            when (index) {
                seat1.deskIndex -> withSeat(desk, seat1.seatIndex, student2)
                seat2.deskIndex -> withSeat(desk, seat2.seatIndex, student1)
                else -> desk
            }
        }
    }

    /**
     * Costruisce la disposizione iniziale: shuffle deterministico (seed) + raggruppamento
     * sequenziale a gruppi di [groupSize] (2 per i banchi da coppia, 3 per i banchi da trio) con
     * "repair pass" che salta i candidati che formerebbero una coppia vietata (-2/-2) con
     * QUALUNQUE membro già scelto per il gruppo, cercando il primo compagno compatibile più avanti
     * in coda. Se non ne restano abbastanza per completare il gruppo, questo resta più piccolo del
     * previsto (fino a un solo occupante): meglio un banco sotto-occupato che violare il Divieto
     * Assoluto.
     */
    private fun buildInitialLayout(
        students: List<User>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
        random: Random,
        groupSize: Int
    ): List<DeskAssignment> {
        val queue = students.shuffled(random).toMutableList()
        val groups = mutableListOf<List<User>>()

        while (queue.isNotEmpty()) {
            val group = mutableListOf(queue.removeAt(0))

            while (group.size < groupSize && queue.isNotEmpty()) {
                var candidateIndex = 0
                while (candidateIndex < queue.size &&
                    group.any { member -> isForbiddenPair(member.id, queue[candidateIndex].id, socialPreferences) }
                ) {
                    candidateIndex++
                }
                if (candidateIndex >= queue.size) break // Nessun compagno compatibile rimasto in coda.
                group.add(queue.removeAt(candidateIndex))
            }
            groups.add(group)
        }

        val maxCols = DEFAULT_DESKS_PER_ROW
        return groups.mapIndexed { index, group ->
            DeskAssignment(
                row = index / maxCols,
                column = index % maxCols,
                studentAId = group.getOrNull(0)?.id,
                studentBId = group.getOrNull(1)?.id,
                studentCId = group.getOrNull(2)?.id,
                seats = groupSize
            )
        }
    }

    /**
     * Ricerca locale (hill-climbing con accettazione probabilistica tipo simulated annealing) che
     * sostituisce il vecchio shuffle-e-scegli-il-migliore-di-3. Ad ogni iterazione tenta lo scambio
     * di due posti a caso: se lo scambio produrrebbe una coppia vietata (-2/-2) viene scartato a
     * priori, PRIMA di guardare lo score (vincolo strutturale, non solo penalizzante). Altrimenti la
     * mossa è accettata se migliora lo score, o con probabilità decrescente nel tempo se lo peggiora
     * (per sfuggire ai minimi locali). Tiene sempre traccia del miglior layout visto.
     */
    fun optimize(
        students: List<User>,
        profiles: Map<String, StudentProfile>,
        ratings: Map<String, RepresentativeRating>,
        socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
        history: List<SeatMapHistoryRecord>,
        weights: OptimizerWeights,
        isSmallClass: Boolean,
        seed: Long,
        maxIterations: Int = DEFAULT_MAX_ITERATIONS,
        maxNoImprovement: Int = DEFAULT_MAX_NO_IMPROVEMENT,
        // 2 = banchi da coppia (default, storico), 3 = banchi da trio. Stesso identico algoritmo
        // (stesse funzioni di punteggio, stesso hill-climbing): cambia solo quante persone per
        // banco costruisce buildInitialLayout e quanti posti esplora la ricerca locale.
        seatsPerDesk: Int = SEATS_PER_DESK_PAIR
    ): List<DeskAssignment> {
        require(seatsPerDesk == SEATS_PER_DESK_PAIR || seatsPerDesk == SEATS_PER_DESK_TRIO) {
            "seatsPerDesk deve essere $SEATS_PER_DESK_PAIR (coppia) o $SEATS_PER_DESK_TRIO (trio), ricevuto $seatsPerDesk"
        }
        if (students.isEmpty()) return emptyList()

        val random = Random(seed)
        fun score(layout: List<DeskAssignment>) =
            scoreLayout(layout, profiles, ratings, socialPreferences, history, weights, isSmallClass).total

        var current = buildInitialLayout(students, socialPreferences, random, seatsPerDesk)
        var currentScore = score(current)
        var best = current
        var bestScore = currentScore
        var noImprovement = 0
        var iteration = 0

        while (iteration < maxIterations && noImprovement < maxNoImprovement) {
            iteration++

            val seats = allSeats(current, seatsPerDesk)
            if (seats.size < 2) break

            val seat1 = seats.random(random)
            var seat2 = seats.random(random)
            var guard = 0
            while (seat2 == seat1 && guard < 10) {
                seat2 = seats.random(random)
                guard++
            }
            if (seat2 == seat1) continue

            val candidate = swapSeats(current, seat1, seat2)

            val affectedDesks = if (seat1.deskIndex == seat2.deskIndex) {
                listOf(candidate[seat1.deskIndex])
            } else {
                listOf(candidate[seat1.deskIndex], candidate[seat2.deskIndex])
            }
            if (affectedDesks.any { hasForbiddenPairAtDesk(it, socialPreferences) }) continue

            val candidateScore = score(candidate)
            val delta = candidateScore - currentScore

            val accepted = if (delta >= 0) {
                true
            } else {
                val temperature = (1.0 - iteration.toDouble() / maxIterations).coerceAtLeast(0.0)
                val acceptProbability = exp(delta / (1.0 + temperature * 50.0))
                random.nextDouble() < acceptProbability
            }

            if (accepted) {
                current = candidate
                currentScore = candidateScore
                if (currentScore > bestScore) {
                    best = current
                    bestScore = currentScore
                    noImprovement = 0
                } else {
                    noImprovement++
                }
            } else {
                noImprovement++
            }
        }

        return best
    }
}
