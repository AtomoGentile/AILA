package circolareplus.algorithms

import circolareplus.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapCompanionHistoryTest {
    private fun record(date: String, pairs: Set<Pair<String, String>> = setOf("a" to "b")) =
        SeatMapHistoryRecord(1, pairs, emptyMap(), date)

    @Test fun threeMonthsExpireOnTheAnniversaryIncludingShortMonths() {
        val history = listOf(record("2026-01-31T12:00:00Z"))
        assertTrue(("b" to "a") in SeatMapOptimizer.recentCompanionPairs(history, "2026-04-29"))
        assertTrue(SeatMapOptimizer.recentCompanionPairs(history, "2026-04-30").isEmpty())
        assertTrue(SeatMapOptimizer.recentCompanionPairs(history, "2026-01-30").isEmpty())
        assertTrue(SeatMapOptimizer.recentCompanionPairs(listOf(record("2025-11-15")), "2026-02-14").isNotEmpty())
        assertTrue(SeatMapOptimizer.recentCompanionPairs(listOf(record("2025-11-15")), "2026-02-15").isEmpty())
    }

    @Test fun everyPairInATrioIsCheckedInBothDirections() {
        val layout = listOf(DeskAssignment(0, 0, "a", "b", "c", seats = 3))
        val history = listOf(record("2026-10-01", setOf("c" to "a", "b" to "c")))
        assertEquals(2, SeatMapOptimizer.recentCompanionViolations(layout, history, "2026-11-15").size)
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
