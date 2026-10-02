package circolareplus.algorithms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SondaggiEngineTest {

    @Test
    fun testValidazioneBudgetVoti() {
        // Budget valido: 2 Verdi, 2 Rossi Chiari, 1 Rosso Scuro
        val validVotes = listOf(
            StudentInterrogationVote("s1", "slot_1", InterrogationVoteType.GREEN),
            StudentInterrogationVote("s1", "slot_2", InterrogationVoteType.GREEN),
            StudentInterrogationVote("s1", "slot_3", InterrogationVoteType.LIGHT_RED),
            StudentInterrogationVote("s1", "slot_4", InterrogationVoteType.LIGHT_RED),
            StudentInterrogationVote("s1", "slot_5", InterrogationVoteType.DARK_RED)
        )
        assertTrue(SondaggiEngine.validateStudentVoteBudget(validVotes).isSuccess)

        // Verdi e Rossi Chiari non hanno tetto
        val manyGreen = validVotes + StudentInterrogationVote("s1", "slot_6", InterrogationVoteType.GREEN) +
                StudentInterrogationVote("s1", "slot_7", InterrogationVoteType.GREEN) +
                StudentInterrogationVote("s1", "slot_8", InterrogationVoteType.LIGHT_RED) +
                StudentInterrogationVote("s1", "slot_9", InterrogationVoteType.LIGHT_RED)
        assertTrue(SondaggiEngine.validateStudentVoteBudget(manyGreen).isSuccess)

        // Superamento limite 2 scelte Rosso Scuro
        val invalidDarkRed = validVotes + StudentInterrogationVote("s1", "slot_6", InterrogationVoteType.DARK_RED) +
                StudentInterrogationVote("s1", "slot_7", InterrogationVoteType.DARK_RED)
        assertTrue(SondaggiEngine.validateStudentVoteBudget(invalidDarkRed).isFailure)
    }

    @Test
    fun testBonusSacrificioAttualizzazioneEReset() {
        // 1. Assegnazione su Rosso Chiaro genera +40 pt
        val bonusAfterLightRed = SondaggiEngine.calculateNextSacrificeBonus(
            currentBonus = 0,
            assignedVoteType = InterrogationVoteType.LIGHT_RED
        )
        assertEquals(40, bonusAfterLightRed)

        // 2. Ulteriore assegnazione su Rosso Scuro aggiunge +150 pt (totale 190)
        val bonusAfterDarkRed = SondaggiEngine.calculateNextSacrificeBonus(
            currentBonus = bonusAfterLightRed,
            assignedVoteType = InterrogationVoteType.DARK_RED
        )
        assertEquals(190, bonusAfterDarkRed)

        // 3. Attualizzazione del voto Verde: base 50 + bonus 190 = 240 pt
        val slot = InterrogationSlotInfo(slotId = "slot_test", dateIso = "2026-09-10", capacity = 1)
        val greenVote = StudentInterrogationVote("s1", "slot_test", InterrogationVoteType.GREEN)
        val score = SondaggiEngine.calculateSlotScore(greenVote, slot, accumulatedSacrificeBonus = bonusAfterDarkRed)
        assertEquals(240, score)

        // 4. Una volta ottenuto lo slot Verde, il bonus si azzera
        val resetBonus = SondaggiEngine.calculateNextSacrificeBonus(
            currentBonus = bonusAfterDarkRed,
            assignedVoteType = InterrogationVoteType.GREEN
        )
        assertEquals(0, resetBonus)

        // 5. L'accumulo ha un tetto
        var capped = 0
        repeat(10) { capped = SondaggiEngine.calculateNextSacrificeBonus(capped, InterrogationVoteType.DARK_RED) }
        assertEquals(SondaggiEngine.MAX_SACRIFICE_BONUS, capped)
    }

    @Test
    fun testNeutralizzazionePenalitaCompitoPerTutti() {
        // Se c'è un compito in classe "Per Tutti", il giorno non applica penalità (0 pt)
        val slotWithExam = InterrogationSlotInfo(
            slotId = "slot_exam",
            dateIso = "2026-09-15",
            capacity = 1,
            isCompitoInClasseForAll = true
        )
        val redVote = StudentInterrogationVote("s1", "slot_exam", InterrogationVoteType.LIGHT_RED)
        val score = SondaggiEngine.calculateSlotScore(redVote, slotWithExam, accumulatedSacrificeBonus = 0)
        assertEquals(0, score, "Un compito in classe per tutti deve azzerare la penalità dello slot a 0 pt")
    }

    @Test
    fun testRossoChiaroPesaMenoDelCodice() {
        val slot = InterrogationSlotInfo(slotId = "slot", dateIso = "2026-09-20", capacity = 1)
        val redVote = StudentInterrogationVote("s1", "slot", InterrogationVoteType.LIGHT_RED)
        assertEquals(-60, SondaggiEngine.calculateSlotScore(redVote, slot, accumulatedSacrificeBonus = 0))
    }
}
