package circolareplus.algorithms

import circolareplus.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapCompanionHistoryTest {
    private fun record(date: String, pairs: Set<Pair<String, String>> = setOf("a" to "b")) =
        SeatMapHistoryRecord(1, pairs, emptyMap(), date)

    @Test fun companionsUnlockOnePublicationAtATime() {
        val partners = listOf("f", "e", "d", "c", "b")
        val history = partners.mapIndexed { index, partner ->
            SeatMapHistoryRecord(index + 1, setOf("a" to partner), emptyMap(), "2026-01-01")
        }
        val blocked = SeatMapOptimizer.recentCompanionPairs(history, mapsToWait = 4)
        assertEquals(setOf("c", "d", "e", "f"), blocked.filter { it.first == "a" }.map { it.second }.toSet())
        val sixthMap = SeatMapHistoryRecord(1, setOf("a" to "b"), emptyMap())
        val next = SeatMapOptimizer.recentCompanionPairs(listOf(sixthMap) + history.map {
            it.copy(mapIndex = it.mapIndex + 1)
        }, mapsToWait = 4)
        assertEquals(setOf("b", "d", "e", "f"), next.filter { it.first == "a" }.map { it.second }.toSet())
        assertTrue(SeatMapOptimizer.recentCompanionPairs(history, mapsToWait = 0).isEmpty())
    }

    @Test fun everyPairInATrioIsCheckedInBothDirections() {
        val layout = listOf(DeskAssignment(0, 0, "a", "b", "c", seats = 3))
        val history = listOf(record("2026-10-01", setOf("c" to "a", "b" to "c")))
        assertEquals(2, SeatMapOptimizer.recentCompanionViolations(layout, history, mapsToWait = 1).size)
    }

    @Test fun optimizerNeverReusesRecentCompanions() {
        val students = listOf("a", "b", "c", "d").map {
            User(id = it, firstName = it, lastName = it, username = it)
        }
        val history = listOf(record(circolareplus.util.today().toIso(), setOf("a" to "b", "c" to "d")))
        repeat(4) { seed ->
            val layout = SeatMapOptimizer.optimize(students, emptyMap(), emptyMap(), emptyMap(), history,
                OptimizerWeights(), true, seed.toLong(), maxIterations = 100)
            assertTrue(SeatMapOptimizer.recentCompanionViolations(layout, history).isEmpty())
            assertEquals(4, layout.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }.toSet().size)
        }
    }
}
