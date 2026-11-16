package circolareplus.algorithms

import circolareplus.data.repository.SeatMapRepository
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapSearchTest {
    @Test fun simultaneousCyclePreservesOccupantsAndReservedDesk() {
        val layout = listOf(DeskAssignment(0, 0, "a", "b"),
            DeskAssignment(0, 1, "c", "d"), DeskAssignment(1, 0, null, null, seats = 0))
        val rotated = SeatMapOptimizer.rotateSeats(layout, SeatMapOptimizer.SeatRef(0, 0),
            SeatMapOptimizer.SeatRef(0, 1), SeatMapOptimizer.SeatRef(1, 0))
        assertEquals(listOf("c", "a", "b", "d"), rotated.flatMap { listOfNotNull(it.studentAId, it.studentBId) })
        assertEquals(layout.last(), rotated.last())
        assertEquals("a", layout.first().studentAId)
    }

    @Test fun systematicPolishFindsBetterPairsWithoutRandomMoves() {
        val students = (0..3).map { User("s$it", "S", "$it", "s$it") }
        val layout = listOf(DeskAssignment(0, 0, "s0", "s1"), DeskAssignment(0, 1, "s2", "s3"))
        val preferences = listOf("s0" to "s2", "s2" to "s0", "s1" to "s3", "s3" to "s1")
            .associateWith { SocialPreferenceScore.STRONG_AFFINITY }
        val result = SeatMapOptimizer.optimize(students, emptyMap(), emptyMap(), preferences,
            emptyList(), OptimizerWeights(), true, 1L, maxIterations = 0,
            slots = listOf(SeatMapOptimizer.DeskSlot(0, 0, 2), SeatMapOptimizer.DeskSlot(0, 1, 2)),
            initialLayout = layout)
        assertEquals(100.0, SeatMapOptimizer.voteSatisfaction(result, preferences)?.percentage)
    }

    @Test fun stopKeepsBestAndReportsOnlyCompletedSearches() = runTest {
        val students = (0..3).map { User("s$it", "S", "$it", "s$it") }
        var completed = 0
        val updates = mutableListOf<SeatMapProposal>()
        val result = SeatMapRepository().generateThreeProposals(students, emptyMap(), emptyMap(),
            emptyMap(), emptyList(), onProgress = { done, total ->
                assertEquals(2000, total)
                completed = done
            }, onBest = { updates.add(it) }, shouldStop = { completed >= 3 })
        assertEquals(3, completed)
        assertTrue(updates.isNotEmpty())
        assertTrue(result.any { SeatMapRanking.compare(it, updates.last()) == 0 })
        assertTrue(updates.zipWithNext().all { (a, b) -> SeatMapRanking.compare(b, a) > 0 })
    }
}
