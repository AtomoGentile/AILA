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
            """{"answer":"Il 20 novembre gli eventi coincidono alla terza ora.","sources":[]}""",
            """{"answer":"Il 20 novembre gli eventi coincidono alla terza ora.","sources":[],"verified":true}"""
        )
        val events = listOf(
            CalendarEvent("g", "Giornalino", "2026-11-20", "3ª ora", CalendarEventCategory.ALTRO),
            CalendarEvent("f", "FCE", "2026-11-20", "Dalla 2ª alla 4ª ora", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Il giornalino si sovrappone al FCE?", emptyList(), knowledge.copy(calendarEvents = events))
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in model.prompts[1].userPrompt)
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in reply.readContext)
        assertEquals(3, model.prompts.size)
        assertNull(reply.actionPlan)
    }

    @Test fun aGenericCandidateCannotSkipTheUnfinishedComparison() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"Il giornalino si svolge il venerdì. Le attività possono sovrapporsi: gestisci bene gli impegni.","verified":true}""",
            """{"answer":"","calendarQueries":[{"circularNumber":11,"compareWith":"FCE"}],"verified":false}""",
            """{"answer":"Ci sono coincidenze il 20 novembre e il 4 dicembre, ma manca la durata FCE."}""",
            """{"answer":"Gli incontri del giornalino sono il 20 novembre e il 4 dicembre, 14:15–15:45. L'FCE inizia alle 14:30 in entrambi i giorni: le date coincidono, ma manca l'orario finale per verificare tutto l'intervallo.","verified":true}"""
        )
        val events = listOf("2026-11-20", "2026-12-04").flatMap { date -> listOf(
            CalendarEvent("g$date", "Riunione di redazione", date, "14:15–15:45", CalendarEventCategory.ALTRO, circularNumber = 11),
            CalendarEvent("f$date", "FCE", date, "14:30", CalendarEventCategory.ALTRO)
        ) }
        val reply = assistant(model).ask("Quando sono gli incontri del giornalino? Si intrecciano con l'FCE?", emptyList(), knowledge.copy(calendarEvents = events))
        assertFalse(reply.isError)
        assertEquals(4, model.prompts.size)
        assertTrue("VERIFICA PRIMA DELL'INVIO" in model.prompts[1].systemPrompt)
        assertTrue("2026-12-04" in model.prompts[2].userPrompt)
        assertTrue("4 dicembre" in reply.text)
        assertFalse("gestisci bene" in reply.text)
    }

    @Test fun followupCompletenessIsCheckedAgainstTheConversationNotTheOldAnswer() = runTest {
        val history = listOf(
            AssistantMessage("u", AssistantAuthor.USER, "Quando si sovrappongono giornalino e FCE?"),
            AssistantMessage("a", AssistantAuthor.ASSISTANT, "Il 20 novembre.")
        )
        val model = ScriptedClassifier(
            """{"answer":"Ho già elencato tutto."}""",
            """{"answer":"","calendarQueries":[{"text":"giornalino","compareWith":"FCE"}]}""",
            """{"answer":"Mancava anche il 4 dicembre."}""",
            """{"answer":"Mancava anche il 4 dicembre: 14:30–15:30, dentro l'incontro 14:15–15:45.","verified":true}"""
        )
        val events = listOf(
            CalendarEvent("g", "Giornalino", "2026-12-04", "14:15–15:45", CalendarEventCategory.ALTRO),
            CalendarEvent("f", "FCE", "2026-12-04", "14:30–15:30", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Poi?", history, knowledge.copy(calendarEvents = events))
        assertTrue("giornalino e FCE" in model.prompts[1].userPrompt)
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in reply.readContext)
        assertTrue("4 dicembre" in reply.text)
    }

    @Test fun anUnverifiedReviewIsNotPresentedAsAFactualAnswer() = runTest {
        val model = ScriptedClassifier("""{"answer":"Tutti gli eventi sono stati elencati."}""", """{"answer":"Tutti gli eventi sono stati elencati."}""")
        val reply = assistant(model).ask("Controlla gli incontri del corso", emptyList(), knowledge)
        assertTrue(reply.isError)
        assertFalse("Tutti gli eventi" in reply.text)
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
