package circolareplus.algorithms

import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User
import circolareplus.data.repository.SeatMapRepository
import circolareplus.ui.screens.satisfactionLabel
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapStudentCountTest {
    private fun occupants(layout: List<DeskAssignment>) =
        layout.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }

    @Test
    fun sameTwentyTwoStudentsCanHaveNineteenOrTwentyOneEvaluatedWithoutMissingAnyone() {
        val pairs = (0 until 11).map { i -> DeskAssignment(i / 3, i % 3, "s${2 * i}", "s${2 * i + 1}") }
        val split = pairs.dropLast(1) + listOf(DeskAssignment(3, 1, "s20", null), DeskAssignment(3, 2, "s21", null))
        val votes = (0 until 22).filter { it != 19 }.associate {
            ("s$it" to "s${(it + 1) % 22}") to SocialPreferenceScore.STRONG_AFFINITY
        }
        val pairedSatisfaction = SeatMapOptimizer.voteSatisfaction(pairs, votes)!!
        val splitSatisfaction = SeatMapOptimizer.voteSatisfaction(split, votes)!!
        assertEquals(21, pairedSatisfaction.evaluatedStudents)
        assertEquals(19, splitSatisfaction.evaluatedStudents)
        assertEquals(occupants(pairs).toSet(), occupants(split).toSet())
        assertEquals(22, occupants(split).size)
        val label = satisfactionLabel(splitSatisfaction, split)
        assertTrue(label.contains("22 studenti in mappa"))
        assertTrue(label.contains("19 valutati"))
    }

    @Test
    fun noUsefulVotesStillShowsTheTotalOccupants() {
        assertTrue(satisfactionLabel(null, listOf(DeskAssignment(0, 0, "a", "b"))).startsWith("2 studenti in mappa"))
    }

    @Test
    fun allThreeGeneratedProposalsContainEveryStudentExactlyOnceAndLeaveHistoryUnchanged() = runTest(timeout = 5.minutes) {
        val students = (0 until 22).map { User(id = "s$it", firstName = "Studente", lastName = "$it", username = "s$it") }
        val history = listOf(SeatMapHistoryRecord(1, emptySet(), emptyMap(), "2020-01-01 00:00:00"))
        val before = history.toList()
        val progress = mutableListOf<Pair<Int, Int>>()
        val proposals = SeatMapRepository().generateThreeProposals(students, emptyMap(), emptyMap(), emptyMap(), history,
            onProgress = { completed, total -> progress.add(completed to total) })
        assertEquals((0..2000).map { it to 2000 }, progress)
        assertTrue(proposals.size in 1..3)
        assertEquals(proposals.sortedWith(circolareplus.algorithms.SeatMapRanking.bestFirst), proposals)
        val best = proposals.first().totalScore
        val tolerance = maxOf(kotlin.math.abs(best) * 0.05, 1.0)
        assertTrue(proposals.all { it.totalScore >= best - tolerance })
        assertEquals(proposals.size, proposals.map { it.id }.toSet().size)
        proposals.forEach { proposal ->
            val assigned = occupants(proposal.assignments)
            assertEquals(22, assigned.size)
            assertEquals(students.map { it.id }.toSet(), assigned.toSet())
        }
        assertEquals(before, history)
    }
    @Test fun reservedDeskRemainsEmptyAndEveryUsableSeatIsFilled() = runTest(timeout = 5.minutes) {
        val students = (0 until 22).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }
        val slots = (0 until 4).flatMap { row -> (0 until 3).map { col ->
            SeatMapOptimizer.DeskSlot(row, col, if (row == 3 && col == 1) 0 else 2)
        } }
        val proposals = SeatMapRepository().generateThreeProposals(students, emptyMap(), emptyMap(), emptyMap(), emptyList(),
            customDeskSlots = slots)
        proposals.forEach { proposal ->
            val reserved = proposal.assignments.single { it.row == 3 && it.column == 1 }
            assertEquals(0, reserved.seats)
            assertTrue(occupants(listOf(reserved)).isEmpty())
            assertTrue(proposal.assignments.filter { it.seats > 0 }.all {
                it.studentAId != null && it.studentBId != null
            })
            assertEquals(students.map { it.id }.toSet(), occupants(proposal.assignments).toSet())
        }
    }

    @Test fun regenerationNeverLosesAValidIncumbentEvenWithUntrustedSavedScores() = runTest(timeout = 5.minutes) {
        val students = (0 until 6).map { User(id = "s$it", firstName = "S", lastName = "$it", username = "s$it") }
        val repo = SeatMapRepository()
        val first = repo.generateThreeProposals(students, emptyMap(), emptyMap(), emptyMap(), emptyList())
        val second = repo.generateThreeProposals(students, emptyMap(), emptyMap(), emptyMap(), emptyList(),
            previousProposals = first.map { it.copy(totalScore = -999999.0) })
        assertTrue(second.first().totalScore >= first.first().totalScore)
    }

}
