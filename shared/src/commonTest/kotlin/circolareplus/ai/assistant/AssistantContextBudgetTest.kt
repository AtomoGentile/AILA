package circolareplus.ai.assistant

import circolareplus.domain.model.Circular
import kotlin.test.Test
import kotlin.test.assertTrue

class AssistantContextBudgetTest {

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-22",
        user = null,
        profile = null,
        circulars = listOf(Circular(15, "Capienza ambienti scolastici", "2026-09-18", "k15"))
    )

    // Regole della palestra in fondo a un documento lungo, lontane dalla prima riga.
    private val longText = (List(40) { "Riga $it sulla capienza delle aule e dei laboratori." } +
        "Palestra: controllo mensile delle attrezzature e divieto di usare le scarpe da strada." +
        List(10) { "Altra riga $it." }).joinToString("\n")

    @Test
    fun sulTelefonoIlTestoDellaCircolareOccupaLoSpazioCheResta() {
        val context = AssistantContext.render(
            knowledge, "regole palestra", mapOf(15 to longText), maxChars = 6_000
        )
        assertTrue(context.length <= 6_000)
        assertTrue("divieto di usare le scarpe" in context)
    }

    @Test
    fun conIlTestoLettoNonCiSonoSezioniDiRiempimento() {
        val context = AssistantContext.render(
            knowledge, "regole palestra", mapOf(15 to longText), maxChars = 6_000
        )
        assertTrue("MAPPA" !in context.uppercase() || "POSTI" !in context.uppercase())
        assertTrue("SONDAGGI" !in context.uppercase())
    }
}
