package circolareplus.algorithms

import circolareplus.domain.model.*
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.TimeSource

class SeatMapNeighborhoodTest {
    @Test fun movesWholeCompatibleGroupsThroughABarrierThatSwapsCannotCross() {
        val students = (0..5).map { User("s$it", "S", "$it", "s$it") }
        val layout = (0..2).map { DeskAssignment(it, 0, "s${2 * it}", "s${2 * it + 1}") }
        val profiles = (0..5).associate { "s$it" to StudentProfile("s$it", 190 - (it / 2) * 20) }
        val prefs = buildMap {
            for (a in 0..5) for (b in 0..5) if (a / 2 != b / 2)
                put("s$a" to "s$b", SocialPreferenceScore.STRONG_REJECTION)
        }
        val oldSearch = SeatMapOptimizer.optimize(students, profiles, emptyMap(), prefs, emptyList(),
            OptimizerWeights(), true, 17L, initialLayout = layout)
        fun score(map: List<DeskAssignment>) = SeatMapOptimizer.scoreLayout(map, profiles, emptyMap(),
            prefs, emptyList(), OptimizerWeights(), true).total
        assertEquals(score(layout), score(oldSearch))
        val repaired = SeatMapOptimizer.improveDeskNeighborhood(oldSearch, listOf(0, 1, 2), profiles,
            emptyMap(), prefs, emptyList(), OptimizerWeights(), true)
        assertTrue(score(repaired) > score(oldSearch), "Joint repair must cross the hard-pair barrier")
        assertEquals(0.0, SeatMapOptimizer.scoreLayout(repaired, profiles, emptyMap(), prefs,
            emptyList(), OptimizerWeights(), true).heightPenalty)
        assertFalse(SeatMapOptimizer.scoreLayout(repaired, profiles, emptyMap(), prefs,
            emptyList(), OptimizerWeights(), true).hasForbiddenPair)
    }

    @Test fun neighborhoodMatchesExhaustiveOracleAndPreservesReservedPositions() {
        val layout = listOf(DeskAssignment(0, 0, "s0", "s1"), DeskAssignment(1, 0, "s2", "s3"),
            DeskAssignment(2, 0, "s4", "s5"), DeskAssignment(2, 1, null, null, seats = 0))
        val profiles = (0..5).associate { "s$it" to StudentProfile("s$it", 150 + it * 10) }
        val ratings = (0..5).associate { "s$it" to RepresentativeRating("s$it", it % 5 + 1, (it * 2) % 5 + 1) }
        val prefs = (0..5).flatMap { a -> (0..5).filter { it != a }.map { b ->
            ("s$a" to "s$b") to SocialPreferenceScore.fromValue((a * 3 + b) % 4 - 1)
        } }.toMap()
        val weights = OptimizerWeights(0.7, 1.3, 1.1)
        val history = listOf(SeatMapHistoryRecord(1, setOf("s0" to "s2"), (0..5).associate {
            "s$it" to (when (it / 2) { 0 -> 2; 1 -> 0; else -> 1 } to 0)
        }, circolareplus.util.today().toIso()))
        fun score(map: List<DeskAssignment>) = SeatMapOptimizer.scoreLayout(map, profiles, ratings, prefs,
            history, weights, true).total
        fun satisfaction(map: List<DeskAssignment>) = SeatMapOptimizer.voteSatisfaction(map, prefs)
        var oracle = layout
        fun permutations(remaining: List<String>, order: List<String>) {
            if (remaining.isEmpty()) {
                val candidate = (0..2).map { i -> layout[i].copy(studentAId = order[2 * i], studentBId = order[2 * i + 1]) } + layout.last()
                if (SeatMapOptimizer.recentCompanionViolations(candidate, history).isNotEmpty() ||
                    SeatMapOptimizer.rowRotationViolations(candidate, profiles, history).isNotEmpty()) return
                if (SeatMapRanking.compare(score(candidate), satisfaction(candidate), score(oracle), satisfaction(oracle)) > 0) oracle = candidate
            } else for (id in remaining) permutations(remaining - id, order + id)
        }
        permutations((0..5).map { "s$it" }, emptyList())
        val actual = SeatMapOptimizer.improveDeskNeighborhood(layout, listOf(0, 1, 2), profiles, ratings,
            prefs, history, weights, true)
        assertEquals(0, SeatMapRanking.compare(score(oracle), satisfaction(oracle), score(actual), satisfaction(actual)))
        assertEquals(layout.last(), actual.last())
        assertTrue(SeatMapOptimizer.recentCompanionViolations(actual, history).isEmpty())
        assertTrue(SeatMapOptimizer.rowRotationViolations(actual, profiles, history).isEmpty())
        assertEquals(layout, SeatMapOptimizer.improveDeskNeighborhood(layout, listOf(0, 1, 2), profiles,
            ratings, prefs, history, weights, true, maxCandidates = 0))
    }

    @Test fun threeFullTriosCanReachTheirKnownScoreUpperBound() {
        val layout = (0..2).map { i -> DeskAssignment(0, i, "s${3 * i}", "s${3 * i + 1}", "s${3 * i + 2}", 3) }
        val prefs = buildMap {
            for (a in 0..8) for (b in 0..8) if (a != b && a % 3 == b % 3)
                put("s$a" to "s$b", SocialPreferenceScore.STRONG_AFFINITY)
        }
        val actual = SeatMapOptimizer.improveDeskNeighborhood(layout, listOf(0, 1, 2), emptyMap(),
            emptyMap(), prefs, emptyList(), OptimizerWeights(), true)
        // Nine pair contributions, each bounded above by 90. This fixture has no other terms.
        assertEquals(810.0, SeatMapOptimizer.scoreLayout(actual, emptyMap(), emptyMap(), prefs,
            emptyList(), OptimizerWeights(), true).total)
        assertEquals(9, actual.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }.toSet().size)
    }

    @Test fun cachedScoringMatchesReferenceWithMixedDesksHistoryAndFractionalWeights() {
        val random = Random(917)
        val ids = (0..20).map { "s$it" }
        val profiles = ids.associateWith { StudentProfile(it, 140 + random.nextInt(15) * 5, priorityPass = random.nextBoolean()) }
        val ratings = ids.associateWith { RepresentativeRating(it, random.nextInt(1, 6), random.nextInt(1, 6)) }
        val prefs = ids.flatMap { a -> ids.filter { it != a }.map { b ->
            (a to b) to SocialPreferenceScore.fromValue(random.nextInt(-2, 3))
        } }.toMap()
        val history = (1..4).map { index -> SeatMapHistoryRecord(index, ids.chunked(2).filter { it.size == 2 }
            .map { it[0] to it[1] }.toSet(), ids.associateWith { random.nextInt(4) to random.nextInt(3) }, "2020-01-01") }
        val weights = OptimizerWeights(0.7, 1.3, 0.9)
        val discipline = setOf("s1" to "s5", "s3" to "s2")
        val evaluator = SeatMapOptimizer.PreparedScoreEvaluator(profiles, ratings, prefs, history, weights, true, discipline)
        val layouts = (0 until 300).map {
            val shuffled = ids.shuffled(random)
            var offset = 0
            listOf(3, 2, 3, 2, 3, 2, 3, 3).mapIndexed { i, size ->
                val members = shuffled.subList(offset, offset + size)
                offset += size
                DeskAssignment(i / 3, i % 3, members[0], members[1], members.getOrNull(2), seats = size)
            } + DeskAssignment(3, 1, null, null, seats = 0)
        }
        layouts.forEach { layout ->
            assertEquals(SeatMapOptimizer.scoreLayout(layout, profiles, ratings, prefs, history, weights, true, discipline).total,
                evaluator.total(layout), "Cached evaluation must retain exact primary-score ties")
        }
        val referenceStart = TimeSource.Monotonic.markNow()
        var referenceSum = 0.0
        repeat(10) { layouts.forEach { referenceSum += SeatMapOptimizer.scoreLayout(it, profiles, ratings, prefs, history, weights, true, discipline).total } }
        val referenceTime = referenceStart.elapsedNow()
        val cachedStart = TimeSource.Monotonic.markNow()
        var cachedSum = 0.0
        repeat(10) { layouts.forEach { cachedSum += evaluator.total(it) } }
        val cachedTime = cachedStart.elapsedNow()
        assertEquals(referenceSum, cachedSum)
        println("Seat score benchmark: reference=$referenceTime cached=$cachedTime (3000 evaluations, warm cache; runner-specific)")
    }
}
