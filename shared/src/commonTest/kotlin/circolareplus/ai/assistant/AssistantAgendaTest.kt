package circolareplus.ai.assistant

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantAgendaTest {

    // Il caso della segnalazione: la risposta dell'AI locale a "Ci sono pagamenti o scadenze in
    // arrivo?" era fuori ordine, con doppioni, orari storpiati e le vacanze di Natale in mezzo.
    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-28",
        user = null,
        profile = null,
        circulars = listOf(
            Circular(number = 40, title = "Erogazione liberale", publishDate = "2026-09-20", r2PdfKey = "k40"),
            Circular(number = 41, title = "Buono libri Regione Veneto", publishDate = "2026-09-21", r2PdfKey = "k41")
        ),
        classifications = mapOf(
            40 to CircularAiClassification(
                circularNumber = 40,
                badge = CircularRelevanceBadge.RELEVANT,
                personalSummary = "Contributo volontario.",
                detectedDeadlines = listOf(
                    ExtractedDeadline("Scadenza pagamento erogazione liberale (classi prime)", "2026-10-20", null, "PAGAMENTO")
                )
            ),
            41 to CircularAiClassification(
                circularNumber = 41,
                badge = CircularRelevanceBadge.RELEVANT,
                personalSummary = "Buono libri.",
                detectedDeadlines = listOf(
                    ExtractedDeadline("Termine presentazione domanda Buono Libri", "2026-10-09", "14:00", "AVVISO"),
                    ExtractedDeadline("Vacanze natalizie", "2026-12-24", null, "AVVISO")
                )
            )
        ),
        calendarEvents = listOf(
            CalendarEvent("e1", "Adesione progetti", "2026-10-08", null, CalendarEventCategory.PAGAMENTO),
            CalendarEvent("e2", "Assemblee ed elezioni dei rappresentanti", "2026-10-08", "08:00", CalendarEventCategory.AVVISO),
            CalendarEvent("e3", "Ponte per l'Immacolata Concezione", "2026-12-07", null, CalendarEventCategory.AVVISO),
            CalendarEvent("e4", "Ponte per l'Immacolata Concezione", "2026-12-07", null, CalendarEventCategory.AVVISO),
            CalendarEvent("e5", "Consiglio di Classe 4^CSA", "2026-10-14", "16:30", CalendarEventCategory.AVVISO),
            CalendarEvent("e6", "Buono libri: termine domanda", "2026-10-09", null, CalendarEventCategory.AVVISO)
        )
    )

    @Test
    fun pagamentiEScadenzeInOrdineSenzaDoppioniNeAvvisi() {
        val reply = assertNotNull(AssistantAgenda.answer(knowledge, "Ci sono pagamenti o scadenze in arrivo?"))
        val lines = reply.text.lines().filter { it.startsWith("• ") }
        assertEquals(
            listOf(
                "• giovedì 8 ottobre — Adesione progetti (pagamento)",
                "• venerdì 9 ottobre, ore 14:00 — Buono libri: termine domanda · circolare n. 41",
                "• martedì 20 ottobre — Scadenza pagamento erogazione liberale (classi prime) (pagamento) · circolare n. 40"
            ),
            lines
        )
        assertFalse(reply.text.contains("Vacanze"))
        assertFalse(reply.text.contains("Assemblee"))
        assertTrue(reply.sources.any { it.circularNumber == 41 })
    }

    @Test
    fun agendaCompletaTieneUnSoloPonte() {
        val items = AssistantAgenda.collectItems(knowledge)
        assertEquals(1, items.count { it.title.startsWith("Ponte") })
        assertEquals(items.sortedBy { it.date }.map { it.date }, items.map { it.date })
    }

    @Test
    fun conUnArgomentoLaDomandaVaAlModello() {
        assertNull(AssistantAgenda.kindOf(knowledge, "Quando e' la verifica di storia?"))
        assertNull(AssistantAgenda.kindOf(knowledge, "Quanto costa la gita a Roma?"))
        assertNull(AssistantAgenda.kindOf(knowledge, "Riassumimi le ultime circolari che mi riguardano"))
        assertNull(AssistantAgenda.kindOf(knowledge, "Cosa dice la circolare 41 sulle scadenze?"))
        assertNull(AssistantAgenda.kindOf(knowledge, "ciao"))
    }

    @Test
    fun riconosceLeDomandeElenco() {
        assertEquals(AssistantAgenda.Kind.DEADLINES, AssistantAgenda.kindOf(knowledge, "Ci sono pagamenti o scadenze in arrivo?"))
        assertEquals(AssistantAgenda.Kind.PAYMENTS, AssistantAgenda.kindOf(knowledge, "Quali pagamenti ci sono?"))
        assertEquals(AssistantAgenda.Kind.TESTS, AssistantAgenda.kindOf(knowledge, "Ho verifiche la prossima settimana?"))
        assertEquals(AssistantAgenda.Kind.ALL, AssistantAgenda.kindOf(knowledge, "Cosa devo fare questa settimana?"))
    }
}
