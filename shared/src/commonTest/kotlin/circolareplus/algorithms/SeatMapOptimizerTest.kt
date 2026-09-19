package circolareplus.algorithms

import circolareplus.domain.model.RepresentativeRating
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapOptimizerTest {

    @Test
    fun testBlindaturaRifiutiAssoluti() {
        // Rifiuto assoluto doppio (-2 / -2) deve restituire -1000 pt
        val scoreDoubleRejection = SeatMapOptimizer.calculateSocialScore(
            SocialPreferenceScore.STRONG_REJECTION,
            SocialPreferenceScore.STRONG_REJECTION
        )
        assertEquals(-1000, scoreDoubleRejection, "Il doppio rifiuto assoluto deve valere -1000 pt")

        // Rifiuto assoluto singolo (-2 / +2) deve annullare il match e restituire -500 pt
        val scoreSingleRejection = SeatMapOptimizer.calculateSocialScore(
            SocialPreferenceScore.STRONG_REJECTION,
            SocialPreferenceScore.STRONG_AFFINITY
        )
        assertEquals(-500, scoreSingleRejection, "Il rifiuto assoluto singolo deve valere -500 pt blindati")

        // Match reciproco massimo (+2 / +2) deve restituire 90 pt
        val scoreReciprocal = SeatMapOptimizer.calculateSocialScore(
            SocialPreferenceScore.STRONG_AFFINITY,
            SocialPreferenceScore.STRONG_AFFINITY
        )
        assertEquals(90, scoreReciprocal, "Il match reciproco massimo deve valere 90 pt")
    }

    @Test
    fun testPenalitaAltezza() {
        // Nessun ostacolo se Hdavanti <= Hdietro
        assertEquals(0, SeatMapOptimizer.calculateHeightPenalty(170, 175))
        assertEquals(0, SeatMapOptimizer.calculateHeightPenalty(170, 170))

        // Scarto di 5 cm davanti -> -30 pt
        assertEquals(30, SeatMapOptimizer.calculateHeightPenalty(175, 170))

        // Scarto di 10 cm davanti -> -60 pt
        assertEquals(60, SeatMapOptimizer.calculateHeightPenalty(180, 170))

        // Scarto di 15 cm davanti -> -90 pt
        assertEquals(90, SeatMapOptimizer.calculateHeightPenalty(185, 170))
    }

    @Test
    fun testPrevenzioneTutorBurnout() {
        val ratings = mapOf(
            "student_l5" to RepresentativeRating("student_l5", didactic = 5, behavior = 2),
            "student_l1" to RepresentativeRating("student_l1", didactic = 1, behavior = 3)
        )

        // 1 mappa consecutiva -> penalità 80 pt
        val pen2 = SeatMapOptimizer.calculateTutorBurnoutPenalty("student_l5", "student_l1", ratings, consecutiveTutoringCount = 1)
        assertEquals(80, pen2)

        // 2 o più mappe consecutive -> penalità 150 pt
        val pen3 = SeatMapOptimizer.calculateTutorBurnoutPenalty("student_l5", "student_l1", ratings, consecutiveTutoringCount = 2)
        assertEquals(150, pen3)
    }

    @Test
    fun testPriorityPass() {
        val profileWithPriority = StudentProfile(userId = "s1", heightCm = 170, priorityPass = true)
        val normalProfile = StudentProfile(userId = "s2", heightCm = 175, priorityPass = false)

        // Prima fila (row = 0) assegna +1000 pt
        val bonusRow0 = SeatMapOptimizer.calculatePriorityBonus(profileWithPriority, normalProfile, row = 0)
        assertEquals(1000, bonusRow0)

        // Fila successiva applica penalità per mancato rispetto del priority pass
        val penaltyRow1 = SeatMapOptimizer.calculatePriorityBonus(profileWithPriority, normalProfile, row = 1)
        assertEquals(-1000, penaltyRow1)
    }

    @Test
    fun testVincoloHardCoppiaVietataMaiPresenteInOutput() {
        val students = (1..10).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }
        // s1 e s2 si rifiutano reciprocamente in modo assoluto: non devono MAI finire nello stesso banco.
        val socialPreferences = mapOf(
            ("s1" to "s2") to SocialPreferenceScore.STRONG_REJECTION,
            ("s2" to "s1") to SocialPreferenceScore.STRONG_REJECTION
        )

        repeat(50) { i ->
            val assignments = SeatMapOptimizer.optimize(
                students = students,
                profiles = emptyMap(),
                ratings = emptyMap(),
                socialPreferences = socialPreferences,
                history = emptyList(),
                weights = OptimizerWeights(),
                isSmallClass = false,
                seed = i.toLong(),
                maxIterations = 200,
                maxNoImprovement = 100
            )

            val forbiddenDesk = assignments.firstOrNull { desk ->
                val ids = setOfNotNull(desk.studentAId, desk.studentBId)
                ids.containsAll(setOf("s1", "s2"))
            }
            assertTrue(forbiddenDesk == null, "La coppia vietata s1/s2 non deve mai comparire nello stesso banco (seed=$i)")
        }
    }

    @Test
    fun testBanchiDaTrioProduconoTreOccupantiPerBanco() {
        val students = (1..12).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }

        val assignments = SeatMapOptimizer.optimize(
            students = students,
            profiles = emptyMap(),
            ratings = emptyMap(),
            socialPreferences = emptyMap(),
            history = emptyList(),
            weights = OptimizerWeights(),
            isSmallClass = false,
            seed = 1L,
            maxIterations = 100,
            maxNoImprovement = 50,
            seatsPerDesk = SeatMapOptimizer.SEATS_PER_DESK_TRIO
        )

        // 12 studenti / 3 per banco = 4 banchi, tutti pieni (nessun vincolo -2/-2 da rispettare qui).
        assertEquals(4, assignments.size)
        assertTrue(assignments.all { it.studentAId != null && it.studentBId != null && it.studentCId != null })

        // Nessuno studente deve comparire due volte nella disposizione.
        val allIds = assignments.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }
        assertEquals(students.size, allIds.toSet().size)
    }

    @Test
    fun testBanchiDaTrioRispettanoIlVincoloHardCoppiaVietata() {
        val students = (1..12).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }
        // s1 rifiuta in modo assoluto sia s2 sia s3: nessuno dei due può mai finire al suo banco,
        // nemmeno come "terzo" di un trio altrimenti compatibile.
        val socialPreferences = mapOf(
            ("s1" to "s2") to SocialPreferenceScore.STRONG_REJECTION,
            ("s2" to "s1") to SocialPreferenceScore.STRONG_REJECTION,
            ("s1" to "s3") to SocialPreferenceScore.STRONG_REJECTION,
            ("s3" to "s1") to SocialPreferenceScore.STRONG_REJECTION
        )

        repeat(30) { i ->
            val assignments = SeatMapOptimizer.optimize(
                students = students,
                profiles = emptyMap(),
                ratings = emptyMap(),
                socialPreferences = socialPreferences,
                history = emptyList(),
                weights = OptimizerWeights(),
                isSmallClass = false,
                seed = i.toLong(),
                maxIterations = 200,
                maxNoImprovement = 100,
                seatsPerDesk = SeatMapOptimizer.SEATS_PER_DESK_TRIO
            )

            val violatingDesk = assignments.firstOrNull { desk ->
                val ids = setOfNotNull(desk.studentAId, desk.studentBId, desk.studentCId)
                ids.contains("s1") && (ids.contains("s2") || ids.contains("s3"))
            }
            assertTrue(violatingDesk == null, "s1 non deve mai condividere un banco da trio con s2 o s3 (seed=$i)")
        }
    }

    @Test
    fun testConsecutiveTutoringCount() {
        val ratings = mapOf(
            "tutor" to RepresentativeRating("tutor", didactic = 5, behavior = 2),
            "partnerN1" to RepresentativeRating("partnerN1", didactic = 1, behavior = 3),
            "partnerN2" to RepresentativeRating("partnerN2", didactic = 1, behavior = 3),
            "partnerN3" to RepresentativeRating("partnerN3", didactic = 3, behavior = 3) // non L1: interrompe la catena
        )

        val history = listOf(
            SeatMapHistoryRecord(mapIndex = 1, pairs = setOf("tutor" to "partnerN1"), deskAssignments = emptyMap()),
            SeatMapHistoryRecord(mapIndex = 2, pairs = setOf("tutor" to "partnerN2"), deskAssignments = emptyMap()),
            SeatMapHistoryRecord(mapIndex = 3, pairs = setOf("tutor" to "partnerN3"), deskAssignments = emptyMap())
        )

        // Le prime 2 mappe (N-1, N-2) sono tutoring 5+1 consecutivo; la terza (N-3) rompe la catena -> conteggio 2.
        val count = SeatMapOptimizer.computeConsecutiveTutoringCount("tutor", ratings, history)
        assertEquals(2, count)

        // Uno studente non di livello 5 non accumula mai burnout.
        val countNonTutor = SeatMapOptimizer.computeConsecutiveTutoringCount("partnerN1", ratings, history)
        assertEquals(0, countNonTutor)
    }

    @Test
    fun testScoreLayoutCablaTutteLePenalita() {
        val profiles = mapOf(
            "f1" to StudentProfile(userId = "f1", heightCm = 180),
            "f2" to StudentProfile(userId = "f2", heightCm = 180),
            "b1" to StudentProfile(userId = "b1", heightCm = 170),
            "b2" to StudentProfile(userId = "b2", heightCm = 170)
        )
        val ratings = mapOf(
            "f1" to RepresentativeRating("f1", didactic = 3, behavior = 5),
            "f2" to RepresentativeRating("f2", didactic = 3, behavior = 5),
            "b1" to RepresentativeRating("b1", didactic = 3, behavior = 5),
            "b2" to RepresentativeRating("b2", didactic = 3, behavior = 5)
        )
        val assignments = listOf(
            DeskAssignment(row = 0, column = 0, studentAId = "f1", studentBId = "f2"),
            DeskAssignment(row = 1, column = 0, studentAId = "b1", studentBId = "b2")
        )

        val breakdown = SeatMapOptimizer.scoreLayout(
            assignments = assignments,
            profiles = profiles,
            ratings = ratings,
            socialPreferences = emptyMap(),
            history = emptyList(),
            weights = OptimizerWeights(),
            isSmallClass = false
        )

        // Banco davanti e dietro entrambi a chiasso 5 nella stessa colonna -> -120 pt (magnitudine 120).
        assertEquals(120.0, breakdown.columnNoisePenalty)
        // Differenza altezza 10cm tra fila davanti e dietro -> 2 scaglioni da 30 pt = 60 pt.
        assertEquals(60.0, breakdown.heightPenalty)
    }

    @Test
    fun testOptimizeMiglioraLoScoreRispettoAShuffleNaive() {
        val students = (1..12).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }
        val ratings = students.associate { it.id to RepresentativeRating(it.id, didactic = (it.id.removePrefix("s").toInt() % 5) + 1, behavior = 3) }
        val socialPreferences = mapOf(
            ("s1" to "s2") to SocialPreferenceScore.STRONG_AFFINITY,
            ("s2" to "s1") to SocialPreferenceScore.STRONG_AFFINITY,
            ("s3" to "s4") to SocialPreferenceScore.MILD_REJECTION,
            ("s4" to "s3") to SocialPreferenceScore.MILD_REJECTION
        )
        val weights = OptimizerWeights()

        fun naiveShuffleScore(seed: Long): Double {
            val shuffled = students.shuffled(kotlin.random.Random(seed)).chunked(2)
            val assignments = shuffled.mapIndexed { index, pair ->
                DeskAssignment(
                    row = index / SeatMapOptimizer.DEFAULT_DESKS_PER_ROW,
                    column = index % SeatMapOptimizer.DEFAULT_DESKS_PER_ROW,
                    studentAId = pair.getOrNull(0)?.id,
                    studentBId = pair.getOrNull(1)?.id
                )
            }
            return SeatMapOptimizer.scoreLayout(
                assignments, emptyMap(), ratings, socialPreferences, emptyList(), weights, isSmallClass = false
            ).total
        }

        val naiveAverage = (0 until 20).map { naiveShuffleScore(it.toLong()) }.average()

        val optimizedAssignments = SeatMapOptimizer.optimize(
            students = students,
            profiles = emptyMap(),
            ratings = ratings,
            socialPreferences = socialPreferences,
            history = emptyList(),
            weights = weights,
            isSmallClass = false,
            seed = 42L
        )
        val optimizedScore = SeatMapOptimizer.scoreLayout(
            optimizedAssignments, emptyMap(), ratings, socialPreferences, emptyList(), weights, isSmallClass = false
        ).total

        assertTrue(
            optimizedScore >= naiveAverage,
            "La ricerca locale ($optimizedScore) non dovrebbe fare peggio della media di shuffle casuali ($naiveAverage)"
        )
    }
}
