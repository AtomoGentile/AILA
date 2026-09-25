package circolareplus.ai.assistant

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import circolareplus.util.CivilDate
import circolareplus.util.plusDays
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeScopeTest {

    // 2026-09-21 e' un lunedi.
    private val monday = "2026-09-21"

    private fun range(question: String, today: String = monday): Pair<String, String>? =
        TimeScopeParser.parse(question, today)?.let { it.from to it.to }

    @Test
    fun plusDaysAttraversaMesiAnniEBisestili() {
        assertEquals(CivilDate(2026, 10, 1), CivilDate(2026, 9, 30).plusDays(1))
        assertEquals(CivilDate(2027, 1, 1), CivilDate(2026, 12, 31).plusDays(1))
        assertEquals(CivilDate(2024, 3, 1), CivilDate(2024, 2, 28).plusDays(2))
        assertEquals(CivilDate(2025, 12, 31), CivilDate(2026, 1, 1).plusDays(-1))
        assertEquals(CivilDate(1970, 1, 1), CivilDate(1970, 1, 1).plusDays(0))
        assertEquals(CivilDate(2026, 9, 27), CivilDate(2026, 9, 21).plusDays(6))
    }

    @Test
    fun questaSettimanaVaDaLunediADomenica() {
        assertEquals("2026-09-21" to "2026-09-27", range("Cosa devo fare questa settimana?"))
        // Stessa settimana anche se oggi e' mercoledi o domenica.
        assertEquals("2026-09-21" to "2026-09-27", range("Cosa devo fare questa settimana?", "2026-09-23"))
        assertEquals("2026-09-21" to "2026-09-27", range("Cosa devo fare questa settimana?", "2026-09-27"))
    }

    @Test
    fun settimanaProssimaEScorsa() {
        assertEquals("2026-09-28" to "2026-10-04", range("che ho la prossima settimana"))
        assertEquals("2026-09-14" to "2026-09-20", range("cosa e' successo la settimana scorsa"))
    }

    @Test
    fun giorniSingoli() {
        assertEquals("2026-09-22" to "2026-09-22", range("cosa c'e' domani?"))
        assertEquals("2026-09-21" to "2026-09-21", range("cosa c'e' oggi?"))
        // "dopodomani" non deve far scattare anche "domani".
        assertEquals("2026-09-23" to "2026-09-23", range("e dopodomani?"))
    }

    @Test
    fun piuIndicazioniSiUniscono() {
        assertEquals("2026-09-21" to "2026-09-22", range("cosa ho oggi e domani?"))
        assertEquals("2026-09-21" to "2026-10-04", range("questa settimana e la prossima"))
    }

    @Test
    fun weekendEMese() {
        assertEquals("2026-09-26" to "2026-09-27", range("cosa faccio nel weekend?"))
        assertEquals("2026-09-01" to "2026-09-30", range("scadenze di questo mese"))
        assertEquals("2026-10-01" to "2026-10-31", range("scadenze del mese prossimo"))
    }

    @Test
    fun senzaIndicazioneNonCeIntervallo() {
        assertNull(range("Quando e' la festa di sport?"))
        assertNull(range("Come funziona la mappa posti"))
    }

    private fun knowledge(question: String): String {
        val knowledge = AssistantKnowledge(
            todayIso = monday,
            user = null,
            profile = null,
            circulars = listOf(
                Circular(number = 9, title = "Sportelli didattici", publishDate = "2026-09-15", r2PdfKey = "k9"),
                Circular(number = 8, title = "Buono libri", publishDate = "2026-09-10", r2PdfKey = "k8")
            ),
            classifications = mapOf(
                9 to CircularAiClassification(
                    circularNumber = 9,
                    badge = CircularRelevanceBadge.RELEVANT,
                    personalSummary = "Sportelli aperti dal 28 settembre al 4 giugno.",
                    detectedDeadlines = listOf(
                        ExtractedDeadline("Iscrizione sportelli", "2026-09-23", null, "AVVISO"),
                        ExtractedDeadline("Fine sportelli", "2027-06-04", null, "AVVISO")
                    )
                ),
                8 to CircularAiClassification(
                    circularNumber = 8,
                    badge = CircularRelevanceBadge.POTENTIAL,
                    personalSummary = "Presentare il buono libri.",
                    detectedDeadlines = listOf(ExtractedDeadline("Buono libri", "2026-10-09", null, "PAGAMENTO"))
                )
            ),
            calendarEvents = listOf(
                CalendarEvent("e1", "Festa di sport", "2026-09-24", null, CalendarEventCategory.AVVISO),
                CalendarEvent("e2", "Ponte dell'Immacolata", "2026-12-07", null, CalendarEventCategory.AVVISO),
                CalendarEvent("e3", "Giornate dello sport", "2027-02-11", null, CalendarEventCategory.AVVISO)
            )
        )
        return AssistantContext.render(knowledge, question)
    }

    @Test
    fun conPeriodoIlContestoContieneSoloCioCheCiEntra() {
        val context = knowledge("Cosa devo fare questa settimana?")
        assertTrue(context.contains("PERIODO CHIESTO"))
        assertTrue(context.contains("Festa di sport"))
        assertTrue(context.contains("Iscrizione sportelli"))
        // Fuori dalla settimana: ne' gli eventi, ne' le scadenze delle circolari.
        assertFalse(context.contains("Ponte dell'Immacolata"))
        assertFalse(context.contains("Giornate dello sport"))
        assertFalse(context.contains("Fine sportelli"))
        assertFalse(context.contains("9 ottobre"))
    }

    @Test
    fun senzaPeriodoIlContestoRestaCompleto() {
        val context = knowledge("Elencami gli eventi dell'anno")
        assertFalse(context.contains("PERIODO CHIESTO"))
        assertTrue(context.contains("Ponte dell'Immacolata"))
        assertTrue(context.contains("Giornate dello sport"))
    }

    @Test
    fun leDateArrivanoGiaLeggibiliSenzaCifre() {
        val context = knowledge("Cosa devo fare questa settimana?")
        assertTrue(context.contains("- giovedì 24 settembre — Festa di sport (avviso)"))
        assertTrue(context.contains("mercoledì 23 settembre — Iscrizione sportelli (avviso)"))
        assertFalse(context.contains("2026-09-24"))
        assertFalse(context.contains(" | "))
    }

    @Test
    fun rispostaConDateInCifreVieneRipulita() {
        val raw = "Questa settimana:\n" +
            "• Incontro | 2026-9-25 (venerdì 5 settembre) 14:15 | AVVISO\n" +
            "• Chiusura | 206-9-27 (domenica 27 settembre) 14:00"
        val tidy = AssistantPrompt.tidyAnswer(raw)
        assertTrue(tidy.contains("• Incontro — venerdì 25 settembre 14:15"), tidy)
        assertTrue(tidy.contains("• Chiusura — domenica 27 settembre 14:00"), tidy)
        assertFalse(tidy.contains("AVVISO"), tidy)
    }

    @Test
    fun periodoSenzaEventiLoDiceEsplicitamente() {
        val context = knowledge("Cosa c'e' domani?")
        assertTrue(context.contains("Nessun evento in calendario in questo periodo."))
    }
}
