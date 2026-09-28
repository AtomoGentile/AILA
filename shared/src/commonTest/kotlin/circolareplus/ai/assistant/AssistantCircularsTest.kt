package circolareplus.ai.assistant

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

class AssistantCircularsTest {

    private fun circular(number: Int, title: String, date: String) =
        Circular(number = number, title = title, publishDate = date, r2PdfKey = "k$number")

    private fun analysis(number: Int, badge: CircularRelevanceBadge, summary: String, vararg deadlines: ExtractedDeadline) =
        CircularAiClassification(number, badge, summary, deadlines.toList())

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-28",
        user = null,
        profile = null,
        circulars = listOf(
            circular(210, "Assemblea di istituto", "2026-09-20"),
            circular(214, "Uscita didattica al museo", "2026-09-25"),
            circular(212, "Corso di aggiornamento docenti", "2026-09-22"),
            circular(215, "Sciopero del 2 ottobre", "2026-09-26")
        ),
        classifications = mapOf(
            210 to analysis(210, CircularRelevanceBadge.RELEVANT, "Assemblea venerdi' in aula magna."),
            214 to analysis(
                214, CircularRelevanceBadge.RELEVANT, "Uscita al museo per la tua classe. Serve l'autorizzazione firmata.",
                ExtractedDeadline("Consegna autorizzazione", "2026-09-30", "10:00", "USCITA_DIDATTICA")
            ),
            212 to analysis(212, CircularRelevanceBadge.NOT_RELEVANT, "Riguarda solo i docenti."),
            215 to analysis(215, CircularRelevanceBadge.POTENTIAL, "Possibili lezioni sospese.")
        )
    )

    @Test
    fun laDomandaDelloScreenshotEUnElenco() {
        val listing = assertNotNull(AssistantCirculars.answer(knowledge, "Riassumimi le ultime circolari che mi riguardano"))
        val text = listing.reply.text
        // Prima le pertinenti (dalla piu' recente), poi quelle di possibile interesse; niente docenti.
        assertEquals(listOf(214, 210, 215), listing.shown.map { it.number })
        assertFalse(text.contains("212"), text)
        assertTrue(text.contains("Serve l'autorizzazione firmata."), text)
        assertTrue(text.contains("30 settembre, ore 10:00 — Consegna autorizzazione"), text)
        assertTrue(text.contains("potrebbe interessarti"), text)
        assertEquals(listOf(214, 210, 215), listing.reply.sources.map { it.circularNumber })
        assertNotNull(listing.introPrompt)
    }

    @Test
    fun domandeConUnArgomentoRestanoAlModello() {
        assertNull(AssistantCirculars.answer(knowledge, "Cosa dice la circolare sulla gita a Roma?"))
        assertNull(AssistantCirculars.answer(knowledge, "Riassumimi la circolare 214"))
        assertNull(AssistantCirculars.answer(knowledge, "Ci sono circolari sulla mensa?"))
        assertNull(AssistantCirculars.answer(knowledge, "Quali proposte sono aperte?"))
    }

    @Test
    fun senzaFiltroLeUltimeInOrdineDiData() {
        val listing = assertNotNull(AssistantCirculars.answer(knowledge, "Ci sono circolari nuove?"))
        assertEquals(listOf(215, 214, 212, 210), listing.shown.map { it.number })
    }

    @Test
    fun frasiDelModelloConNumeriInventatiVengonoScartate() {
        val listing = assertNotNull(AssistantCirculars.answer(knowledge, "Riassumimi le ultime circolari che mi riguardano"))
        val good = AssistantCirculars.acceptIntro(
            "La piu' urgente e' la 214: consegna l'autorizzazione entro mercoledi' 30 settembre.",
            listing.shown, knowledge
        )
        assertNotNull(good)
        assertNull(AssistantCirculars.acceptIntro("Ricorda la circolare 999.", listing.shown, knowledge))
        assertNull(AssistantCirculars.acceptIntro("Entro il 17 ottobre.", listing.shown, knowledge))
        // Formato JSON del client cloud: si prende la risposta.
        assertEquals(
            "Niente di urgente.",
            AssistantCirculars.acceptIntro("{\"answer\":\"Niente di urgente.\"}", listing.shown, knowledge)
        )
    }
}
