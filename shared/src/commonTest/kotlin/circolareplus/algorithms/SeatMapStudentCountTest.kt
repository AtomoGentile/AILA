package circolareplus.algorithms

import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User
import circolareplus.data.repository.SeatMapRepository
import circolareplus.ui.screens.satisfactionLabel
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
    fun allThreeGeneratedProposalsContainEveryStudentExactlyOnceAndLeaveHistoryUnchanged() {
        val students = (0 until 22).map { User(id = "s$it", firstName = "Studente", lastName = "$it", username = "s$it") }
        val history = listOf(SeatMapHistoryRecord(1, emptySet(), emptyMap(), "2020-01-01 00:00:00"))
        val before = history.toList()
        val proposals = SeatMapRepository().generateThreeProposals(students, emptyMap(), emptyMap(), emptyMap(), history)
        assertEquals(3, proposals.size)
        proposals.forEach { proposal ->
            val assigned = occupants(proposal.assignments)
            assertEquals(22, assigned.size)
            assertEquals(students.map { it.id }.toSet(), assigned.toSet())
        }
        assertEquals(before, history)
    }
}
