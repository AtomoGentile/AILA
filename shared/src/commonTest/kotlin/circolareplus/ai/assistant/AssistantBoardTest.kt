package circolareplus.ai.assistant

import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalOutcome
import circolareplus.domain.model.ProposalStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantBoardTest {

    private fun proposal(
        id: String,
        title: String,
        status: ProposalStatus,
        outcome: ProposalOutcome? = null,
        up: Int = 0,
        down: Int = 0
    ) = Proposal(
        id = id,
        authorName = "Anonimo",
        isAnonymous = true,
        title = title,
        description = "",
        category = "Altro",
        status = status,
        outcome = outcome,
        upvotes = up,
        downvotes = down,
        createdAt = "2026-09-20T10:00:00Z"
    )

    private val knowledge = AssistantKnowledge(
        todayIso = "2026-09-27",
        user = null,
        profile = null,
        proposals = listOf(
            proposal("1", "Distributore d'acqua", ProposalStatus.NUOVA, up = 3),
            proposal("2", "Gita a Venezia", ProposalStatus.IN_ANALISI, up = 10, down = 1),
            proposal("3", "Musica in ricreazione", ProposalStatus.CHIUSA, ProposalOutcome.ACCETTATA, up = 8),
            proposal("4", "Niente compiti il lunedi'", ProposalStatus.CHIUSA, ProposalOutcome.RIFIUTATA)
        )
    )

    // Il caso della segnalazione: la domanda suggerita dall'app, a cui Gemini Nano rispondeva
    // "Non risulta".
    @Test
    fun openProposalsAreListedByCode() {
        val reply = assertNotNull(AssistantBoard.answer(knowledge, "Quali proposte della bacheca sono ancora aperte?"))
        assertTrue(reply.text.startsWith("Proposte aperte (2):"), reply.text)
        assertTrue(reply.text.indexOf("Gita a Venezia") < reply.text.indexOf("Distributore"), reply.text)
        assertFalse(reply.text.contains("Musica"))
        assertEquals(AssistantSourceKind.BOARD, reply.sources.single().kind)
    }

    @Test
    fun acceptedAndRejected() {
        val accepted = assertNotNull(AssistantBoard.answer(knowledge, "Quali proposte sono state accettate?"))
        assertTrue(accepted.text.contains("Musica in ricreazione"))
        assertFalse(accepted.text.contains("Niente compiti"))

        val rejected = assertNotNull(AssistantBoard.answer(knowledge, "proposte rifiutate"))
        assertTrue(rejected.text.contains("Niente compiti"))
    }

    @Test
    fun questionAboutASpecificProposalGoesToTheModel() {
        assertNull(AssistantBoard.answer(knowledge, "Cosa dice la proposta sulla gita a Venezia?"))
        assertNull(AssistantBoard.answer(knowledge, "Cosa devo fare questa settimana?"))
    }

    @Test
    fun emptyBoard() {
        val reply = assertNotNull(AssistantBoard.answer(knowledge.copy(proposals = emptyList()), "Quali proposte sono aperte?"))
        assertEquals("In bacheca non c'e' nessuna proposta.", reply.text)
    }
}
