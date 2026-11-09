package circolareplus.ai.assistant

import circolareplus.ai.*
import circolareplus.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AssistantAgentLoopTest {
    private val user = User("me", "Nome", "Cognome", "me")
    private val knowledge = AssistantKnowledge("2026-11-09", user, null, classLabel = "4CSA")

    private class ScriptedClassifier(vararg responses: String) : AiClassifier {
        val remaining = responses.toMutableList()
        val prompts = mutableListOf<AiPrompt>()
        override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
            prompts += prompt.build(60_000)
            return AiTextResult.Success(remaining.removeAt(0), "test-model")
        }
        override suspend fun classifyCircularText(circularNumber: Int, circularTitle: String, pdfText: String, studentContext: String): CircularAiClassification = error("Unexpected classification")
        override suspend fun parseEventPrompt(userPrompt: String): EventDraft = error("Unexpected parser")
        override suspend fun testConfiguration(): String = error("Unexpected probe")
    }

    private fun assistant(model: ScriptedClassifier, actions: Boolean = true) = AilaAssistant(
        classifierFactory = { model }, downloadPdf = { error("Unexpected network call") },
        extractPdf = { error("Unexpected PDF extraction") }, actionsEnabled = actions
    )

    @Test fun aShortCorrectionCanBeSemanticallyRoutedToTheExistingDraft() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"","actionRequest":"Prepara la verifica di inglese su U7 il 25 novembre 2026 alla terza ora"}""",
            """{"actions":[{"tool":"calendar.create","title":"Verifica di inglese su U7","date":"2026-11-25","category":"VERIFICA","time":"3ª ora","visibility":"CLASS"}],"clarification":""}"""
        )
        val draft = AssistantActionPlan(listOf(AssistantCalendarAction("calendar.create", "Verifica di inglese su U7", "2026-11-25", CalendarEventCategory.VERIFICA, visibility = "CLASS")))
        val history = listOf(AssistantMessage("a", AssistantAuthor.ASSISTANT, "Ho preparato queste azioni.", actionPlan = draft, actionStatus = AssistantActionStatus.AWAITING_CONFIRMATION))
        assertFalse(AssistantActionIntent.matches("Terza ora", history)) // No keyword-specific fix.
        val reply = assistant(model).ask("Terza ora", history, knowledge)
        val action = assertNotNull(reply.actionPlan).actions.single()
        assertEquals("2026-11-25", action.date)
        assertEquals("3ª ora", action.time)
        assertEquals(user.id, reply.actionPlan?.requestedByUserId)
        assertTrue("2026-11-25" in model.prompts.first().userPrompt)
        assertEquals(2, model.prompts.size)
    }

    @Test fun theModelCanReadBothGroupsThenAnswerFromActualResults() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"","calendarQueries":[{"text":"giornalino","compareWith":"FCE"}]}""",
            """{"answer":"Il 20 novembre gli eventi coincidono alla terza ora.","sources":[]}"""
        )
        val events = listOf(
            CalendarEvent("g", "Giornalino", "2026-11-20", "3ª ora", CalendarEventCategory.ALTRO),
            CalendarEvent("f", "FCE", "2026-11-20", "Dalla 2ª alla 4ª ora", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Il giornalino si sovrappone al FCE?", emptyList(), knowledge.copy(calendarEvents = events))
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in model.prompts[1].userPrompt)
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in reply.readContext)
        assertEquals(2, model.prompts.size)
        assertNull(reply.actionPlan)
    }

    @Test fun readLoopsStopAndExposeTheLimitInsteadOfPretendingToFinish() = runTest {
        val model = ScriptedClassifier(*(0..2).map { """{"answer":"Controllo in corso","calendarQueries":[{"text":"FCE","offset":${it * 20}}]}""" }.toTypedArray())
        val reply = assistant(model).ask("Confronta FCE e giornalino", emptyList(), knowledge)
        assertEquals(AssistantReadTools.MAX_ROUNDS, model.prompts.size)
        assertTrue("parziali" in reply.text)
        assertTrue("Ultimo giro" in model.prompts.last().systemPrompt)
    }

    @Test fun semanticRoutingCannotBypassDisabledActionsOrTheTripScope() = runTest {
        val request = """{"answer":"Solo materiale gita","actionRequest":"Inserisci una verifica domani"}"""
        val disabled = assistant(ScriptedClassifier(request), actions = false).ask("Mettila domani", emptyList(), knowledge)
        assertNull(disabled.actionPlan)
        assertTrue("disattivate" in disabled.text)
        val trip = assistant(ScriptedClassifier(request)).ask("Mettila domani", emptyList(), knowledge, AssistantMode.GITA_ONLY)
        assertNull(trip.actionPlan)
    }
}
