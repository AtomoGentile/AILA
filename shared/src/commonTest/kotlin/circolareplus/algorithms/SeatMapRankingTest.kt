package circolareplus.algorithms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeatMapRankingTest {
    private fun satisfaction(percent: Double, discomfort: Int) =
        SeatMapOptimizer.VoteSatisfaction(percent, 22, discomfort)

    @Test fun totalScoreHasPriorityOverBothTieBreakers() {
        assertTrue(SeatMapRanking.compare(101.0, satisfaction(40.0, 5), 100.0, satisfaction(100.0, 0)) > 0)
    }

    @Test fun fewerUncomfortableStudentsWinsBeforePercentage() {
        assertTrue(SeatMapRanking.compare(100.0, satisfaction(60.0, 0), 100.0, satisfaction(99.0, 1)) > 0)
    }

    @Test fun higherSatisfactionWinsWhenScoreAndDiscomfortTie() {
        assertTrue(SeatMapRanking.compare(100.0, satisfaction(78.2, 0), 100.0, satisfaction(77.4, 0)) > 0)
    }

    @Test fun completeTiesRemainEqualAndMissingVotesAreNotZeroDiscomfort() {
        assertEquals(0, SeatMapRanking.compare(100.0, satisfaction(80.0, 0), 100.0, satisfaction(80.0, 0)))
        assertEquals(0, SeatMapRanking.compare(100.0, null, 100.0, null))
        assertTrue(SeatMapRanking.compare(100.0, satisfaction(80.0, 1), 100.0, null) > 0)
    }

    @Test fun proposalOrderUsesTheSamePriority() {
        fun proposal(id: String, score: Double, percent: Double, discomfort: Int) = SeatMapProposal(
            id, emptyList(), score, satisfaction(percent, discomfort),
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0
        )
        val layouts = listOf(proposal("uncomfortable", 100.0, 99.0, 1),
            proposal("lowerPercent", 100.0, 70.0, 0), proposal("bestTie", 100.0, 80.0, 0),
            proposal("bestScore", 101.0, 50.0, 2))
        assertEquals(listOf("bestScore", "bestTie", "lowerPercent", "uncomfortable"),
            layouts.sortedWith(SeatMapRanking.bestFirst).map { it.id })
    }
}
