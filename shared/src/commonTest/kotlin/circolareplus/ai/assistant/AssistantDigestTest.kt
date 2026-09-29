package circolareplus.ai.assistant

import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantDigestTest {

    private fun analysis(number: Int, badge: CircularRelevanceBadge, summary: String) =
        CircularAiClassification(circularNumber = number, badge = badge, personalSummary = summary)

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-29",
        user = null,
        profile = null,
        circulars = listOf(
            Circular(number = 9, title = "Erogazione liberale classi prime 2026-27", publishDate = "2026-09-21", r2PdfKey = "k9"),
            Circular(number = 11, title = "Corso di teatro", publishDate = "2026-09-23", r2PdfKey = "k11"),
            Circular(number = 12, title = "Collegio docenti", publishDate = "2026-09-24", r2PdfKey = "k12"),
            Circular(number = 13, title = "Proclamazione sciopero 02 ottobre 2026", publishDate = "2026-09-26", r2PdfKey = "k13"),
            Circular(number = 14, title = "Assemblea di istituto", publishDate = "2026-09-28", r2PdfKey = "k14")
        ),
        classifications = mapOf(
            9 to analysis(9, CircularRelevanceBadge.RELEVANT, "Contributo volontario di 90 euro entro il 15 ottobre."),
            11 to analysis(11, CircularRelevanceBadge.POTENTIAL, "Corso pomeridiano facoltativo."),
            12 to analysis(12, CircularRelevanceBadge.NOT_RELEVANT, "Riguarda solo i docenti."),
            13 to analysis(13, CircularRelevanceBadge.RELEVANT, "Il 2 ottobre le lezioni potrebbero non essere garantite.")
        )
    )

    // Il caso della segnalazione: Gemini Nano elencava i titoli senza riassumerli e citava
    // una sola delle due circolari.
    @Test
    fun laDomandaSuggeritaHaIRiassuntiETutteLeFonti() {
        val reply = assertNotNull(AssistantDigest.answer(knowledge, "Riassumimi le ultime circolari che mi riguardano"))
        assertTrue(reply.text.contains("2 ottobre le lezioni"), reply.text)
        assertTrue(reply.text.contains("90 euro"), reply.text)
        assertTrue(reply.text.indexOf("n. 13") < reply.text.indexOf("n. 9"), reply.text)
        assertFalse(reply.text.contains("docenti"), reply.text)
        assertTrue(reply.text.contains("n. 14 (Assemblea di istituto)"), reply.text)
        assertEquals(setOf(13, 11, 9, 14), reply.sources.mapNotNull { it.circularNumber }.toSet())
    }

    @Test
    fun senzaMiRiguardanoSonoLeUltimeEBasta() {
        val reply = assertNotNull(AssistantDigest.answer(knowledge, "Riassumi le ultime circolari"))
        assertEquals(listOf(14, 13, 12, 11), reply.sources.mapNotNull { it.circularNumber })
        assertTrue(reply.text.contains("non sembra riguardarti"), reply.text)
    }

    @Test
    fun conUnArgomentoVaAlModello() {
        assertNull(AssistantDigest.requestOf("Riassumi la circolare sulla gita"))
        assertNull(AssistantDigest.requestOf("Riassumi la circolare 13"))
        assertNull(AssistantDigest.requestOf("Ci sono circolari con scadenze?"))
        assertNull(AssistantDigest.requestOf("ciao"))
    }

    @Test
    fun riassuntoLungoTagliatoAFineFrase() {
        val long = "Prima frase abbastanza lunga da contare davvero. ".repeat(10)
        val short = AssistantDigest.shorten(long)
        assertTrue(short.length <= 320 && short.endsWith("."), short)
    }
}
