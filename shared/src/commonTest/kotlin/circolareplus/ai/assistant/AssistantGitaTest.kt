package circolareplus.ai.assistant

import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaSourceDoc
import kotlin.test.Test
import kotlin.test.assertTrue

class AssistantGitaTest {

    private val gitaDoc = GitaSourceDoc(
        itemId = "g1",
        title = "Preventivo pullman",
        category = GitaCategory.PREVENTIVO,
        uploadedAt = "2026-10-03 09:15:00",
        versionNo = 2,
        url = null,
        text = "Quota di partecipazione 85 euro. Saldo entro il 10 novembre. Partenza ore 7:30 da piazza."
    )

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-10-10",
        user = null,
        profile = null,
        gita = listOf(gitaDoc)
    )

    @Test
    fun ilMaterialeDellaGitaEntraNelContestoConTitoloEData() {
        val context = AssistantContext.render(knowledge, "quanto costa la gita", mode = AssistantMode.GITA_ONLY)
        assertTrue("Preventivo pullman" in context)
        assertTrue("caricato 3 ottobre 2026" in context || "caricato 03/10/2026" in context || "3 ottobre" in context)
        assertTrue("85 euro" in context)
    }

    @Test
    fun laFonteGitaHaUnaEtichettaCheIlCodiceCostruisce() {
        val label = AssistantContext.gitaSourceLabel(gitaDoc, knowledge.todayIso)
        assertTrue(label.startsWith("Gita - Preventivo pullman"))
    }

    @Test
    fun inModalitaSoloMaterialeIlPromptVietaLeConoscenzeGenerali() {
        val prompt = AssistantPrompt.build(
            maxChars = 20_000,
            knowledge = knowledge,
            history = emptyList(),
            question = "quanto costa la gita",
            deepTexts = emptyMap(),
            mode = AssistantMode.GITA_ONLY
        ).systemPrompt
        assertTrue("MODALITA' SOLO MATERIALE" in prompt)
        assertTrue("Non lo trovo nel materiale disponibile" in prompt)
    }

    @Test
    fun senzaMaterialeGitaIlPromptNonCambia() {
        val plain = AssistantKnowledge(todayIso = "2026-10-10", user = null, profile = null)
        val prompt = AssistantPrompt.build(
            maxChars = 20_000,
            knowledge = plain,
            history = emptyList(),
            question = "ciao",
            deepTexts = emptyMap()
        ).systemPrompt
        assertTrue("sezione GITA del CONTESTO" !in prompt)
    }
}
