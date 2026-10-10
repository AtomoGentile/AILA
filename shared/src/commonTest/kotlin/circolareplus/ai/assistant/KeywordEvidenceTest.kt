package circolareplus.ai.assistant

import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeywordEvidenceTest {

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-10-10",
        user = null,
        profile = null,
        circulars = listOf(
            Circular(214, "Corso ICDL per le classi terze", "2026-10-01", "k214"),
            Circular(215, "Gita di istruzione", "2026-10-02", "k215"),
            Circular(216, "Assemblea di istituto", "2026-10-03", "k216")
        ),
        classifications = mapOf(
            216 to CircularAiClassification(
                circularNumber = 216,
                badge = CircularRelevanceBadge.NOT_RELEVANT,
                personalSummary = "Assemblea con tema di informatica e coding."
            )
        )
    )

    private fun scoreOf(evidence: List<KeywordEvidence.Evidence>, term: String) =
        evidence.first { it.term == term }.score

    @Test
    fun parolaNelTitoloPrendeIlPunteggioPiuAlto() {
        val evidence = KeywordEvidence.check(knowledge, "Si è parlato di ICDL nelle circolari?", emptyMap())
        assertEquals(KeywordEvidence.TITLE_HIT, scoreOf(evidence, "icdl"))
        assertEquals(listOf(214), evidence.first { it.term == "icdl" }.circulars)
    }

    @Test
    fun parolaSoloNelRiassuntoPrendeIlPunteggioMedio() {
        val evidence = KeywordEvidence.check(knowledge, "E di informatica?", emptyMap())
        assertEquals(KeywordEvidence.SUMMARY_HIT, scoreOf(evidence, "informatica"))
        assertEquals(listOf(216), evidence.first { it.term == "informatica" }.circulars)
    }

    @Test
    fun parolaAssenteDaTuttiIDatiPrendeMenoDieci() {
        val withText = knowledge.copy(textHits = AssistantTextHits(circularsWithText = 3))
        val evidence = KeywordEvidence.check(withText, "Ci sono verifiche di matematica?", emptyMap())
        assertEquals(KeywordEvidence.NO_HIT, scoreOf(evidence, "matematica"))
        val line = KeywordEvidence.render(evidence).first { it.contains("matematica") }
        assertTrue("-10" in line)
        assertTrue("Non dire che compare" in line)
    }

    @Test
    fun conCircolariSenzaTestoIlMessaggioNonDichiaraAssenza() {
        // Due circolari su tre hanno il testo sul telefono: il "non compare" non e' verificato per tutte.
        val partial = knowledge.copy(textHits = AssistantTextHits(circularsWithText = 2))
        val evidence = KeywordEvidence.check(partial, "Ci sono verifiche di matematica?", emptyMap())
        val line = KeywordEvidence.render(evidence).first { it.contains("matematica") }
        assertTrue("non sono controllate" in line)
        assertTrue("Non dire che compare" !in line)
    }

    @Test
    fun ilTestoNellIndiceContaComeRiscontroNelCorpo() {
        // "pagamento" non sta in titoli e riassunti: lo trova solo l'indice del testo.
        val withIndex = knowledge.copy(
            textHits = AssistantTextHits(circularsWithText = 3, byTerm = mapOf("pagamento" to setOf(215)))
        )
        val evidence = KeywordEvidence.check(withIndex, "Quando scade il pagamento?", emptyMap())
        assertEquals(KeywordEvidence.SUMMARY_HIT, scoreOf(evidence, "pagamento"))
        assertEquals(listOf(215), evidence.first { it.term == "pagamento" }.circulars)
    }

    @Test
    fun paroleDiContornoNonEntranoNelControllo() {
        val evidence = KeywordEvidence.check(knowledge, "Puoi controllare?", emptyMap())
        assertTrue(evidence.isEmpty())
    }

    @Test
    fun testoIntegraleGiaLettoContaComeRiscontro() {
        val evidence = KeywordEvidence.check(
            knowledge, "Quando scade il pagamento?", mapOf(215 to "Versare entro venerdi il pagamento.")
        )
        val score = scoreOf(evidence, "pagamento")
        assertEquals(KeywordEvidence.SUMMARY_HIT, score)
    }

    @Test
    fun contextRendeLaSezioneSoloQuandoCiSonoParoleSignificative() {
        val withTerm = AssistantContext.render(knowledge, "Parlami dell'ICDL", maxChars = 20_000)
        assertTrue("RISCONTRI PER LE PAROLE DELLA DOMANDA" in withTerm)
        val without = AssistantContext.render(knowledge, "Puoi controllare?", maxChars = 20_000)
        assertTrue("RISCONTRI PER LE PAROLE DELLA DOMANDA" !in without)
    }
}
