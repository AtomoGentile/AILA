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
        var failAt: Int? = null
        override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
            prompts += prompt.build(60_000)
            if (prompts.size == failAt) return AiTextResult.Failure("Nessuna risposta entro 30 secondi.")
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

    @Test fun screenshotComparisonUsesBothSchedulesEvenIfTheProviderIsOffline() = runTest {
        val model = ScriptedClassifier()
        val question = "Quando sono gli incontri del giornalino scolastico?\nSi intrecciano con L’FCE?"
        val g = CalendarEvent("g", "Riunione di redazione", "2026-11-20", "14:15–15:45", CalendarEventCategory.ALTRO)
        val f = g.copy(id = "f", title = "Corso B2 First", time = "dalle 14.30 alle 16.00")
        suspend fun ask(vararg events: CalendarEvent) = assistant(model).ask(question, emptyList(), knowledge.copy(calendarEvents = events.toList())).text
        assertTrue("14:30–15:45" in ask(g, f))
        assertTrue("Mi manca" in ask(g))
        assertTrue("da verificare" in ask(g, f.copy(time = "14:30")))
        assertTrue("non risultano sovrapposizioni" in ask(g, f.copy(time = "15:45–16:30")))
        assertTrue(model.prompts.isEmpty())
    }

    @Test fun missingGroupCanBeRecoveredFromExplicitDatedPdfRows() = runTest {
        val circular = Circular(71, "Corso B2 First", "2026-09-10", "b2-english")
        val g = CalendarEvent("g", "Giornalino", "2026-11-20", "14:15–15:45", CalendarEventCategory.ALTRO)
        val data = knowledge.copy(calendarEvents = listOf(g), circulars = listOf(circular))
        val reply = assertNotNull(AssistantMeetingComparison.answer("Il giornalino si sovrappone al FCE?", data) { ids ->
            assertEquals(listOf(71), ids)
            mapOf(71 to "Lezione B2 First: 20 novembre 2026 dalle 14.30 alle 16.00\nLezione B2 First annullata: 20/11/2026 13:00–16:00")
        })
        assertTrue("14:30–15:45" in reply.text)
        assertFalse("14:15–15:45" in reply.text.substringAfter("Sovrapposizioni verificate:"))
        assertTrue(reply.sources.any { it.circularNumber == 71 })
    }

    @Test fun semanticActionAlsoRequiresAFreshCalendar() = runTest {
        val model = ScriptedClassifier("""{"answer":"","actionRequest":"Sposta la verifica di inglese"}""")
        val reply = assistant(model).ask("Mettila domani", emptyList(), knowledge.copy(calendarWarning = "Calendario non aggiornato"))
        assertTrue(reply.isError)
        assertNull(reply.actionPlan)
        assertEquals(1, model.prompts.size)
    }

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
            """{"answer":"Il 20 novembre gli eventi coincidono alla terza ora.","verified":true}"""
        )
        val events = listOf(
            CalendarEvent("g", "Giornalino", "2026-11-20", "3ª ora", CalendarEventCategory.ALTRO),
            CalendarEvent("f", "FCE", "2026-11-20", "Dalla 2ª alla 4ª ora", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Verifica sul calendario le sovrapposizioni del giornalino con FCE.", emptyList(), knowledge.copy(calendarEvents = events))
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in model.prompts[1].userPrompt)
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in reply.readContext)
        assertEquals(3, model.prompts.size)
        assertNull(reply.actionPlan)
    }

    @Test fun readingOneActivityDoesNotBypassTheMissingComparison() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"","calendarQueries":[{"circularNumber":11}]}""",
            """{"answer":"Il giornalino si riunisce il 20 novembre dalle 14:15 alle 15:45."}""",
            """{"answer":"","calendarQueries":[{"circularNumber":11,"compareWith":"FCE"}],"verified":false}""",
            """{"answer":"Il 20 novembre FCE e giornalino coincidono alle 14:30; manca la durata FCE."}"""
        )
        val events = listOf(
            CalendarEvent("g", "Riunione di redazione", "2026-11-20", "14:15–15:45", CalendarEventCategory.ALTRO, circularNumber = 11),
            CalendarEvent("f", "FCE", "2026-11-20", "14:30", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Leggi gli incontri del giornalino e verifica il confronto con FCE.", emptyList(), knowledge.copy(calendarEvents = events))
        assertEquals(4, model.prompts.size)
        assertTrue("VERIFICA PRIMA DELL'INVIO" in model.prompts[2].systemPrompt)
        assertTrue("STESSA_DATA_ORARIO_INSUFFICIENTE" in model.prompts[3].userPrompt)
        assertTrue("FCE e giornalino" in reply.text)
        assertNull(reply.actionPlan)
    }

    @Test fun aFailedCheckAfterReadingKeepsTheEvidenceWithAPartialNotice() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"","calendarQueries":[{"text":"FCE"}]}""",
            """{"answer":"FCE il 20 novembre."}"""
        ).apply { failAt = 3 }
        val reply = assistant(model).ask("Elenca e confronta le attività", emptyList(), knowledge)
        assertFalse(reply.isError)
        assertTrue("20 novembre" in reply.text)
        assertTrue("parziale" in reply.text)
    }

    @Test fun aGenericCandidateCannotSkipTheUnfinishedComparison() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"Il giornalino si svolge il venerdì. Le attività possono sovrapporsi: gestisci bene gli impegni.","verified":true}""",
            """{"answer":"","calendarQueries":[{"circularNumber":11,"compareWith":"FCE"}],"verified":false}""",
            """{"answer":"Ci sono coincidenze il 20 novembre e il 4 dicembre, ma manca la durata FCE."}"""
        )
        val events = listOf("2026-11-20", "2026-12-04").flatMap { date -> listOf(
            CalendarEvent("g$date", "Riunione di redazione", date, "14:15–15:45", CalendarEventCategory.ALTRO, circularNumber = 11),
            CalendarEvent("f$date", "FCE", date, "14:30", CalendarEventCategory.ALTRO)
        ) }
        val reply = assistant(model).ask("Verifica tutte le date del giornalino e del FCE, incluse le sovrapposizioni.", emptyList(), knowledge.copy(calendarEvents = events))
        assertFalse(reply.isError)
        assertEquals(3, model.prompts.size)
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
            """{"answer":"Mancava anche il 4 dicembre: 14:30–15:30, dentro l'incontro 14:15–15:45."}"""
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

    @Test fun aMissingAdvisoryFlagDoesNotDiscardAValidReview() = runTest {
        for (review in listOf("""{"answer":"Il giornalino è il 20 novembre; per il confronto manca la durata FCE."}""",
            "Il giornalino è il 20 novembre; per il confronto manca la durata FCE.")) {
            val model = ScriptedClassifier("""{"answer":"Controllo le date."}""", review)
            val reply = assistant(model).ask("Controlla gli incontri del corso", emptyList(), knowledge)
            assertFalse(reply.isError)
            assertTrue("manca la durata FCE" in reply.text)
        }
    }

    @Test fun aCircularSearchStillChecksThatTheTerminalAnswerCoversTheRequest() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"","searches":["FCE"]}""",
            """{"answer":"Non ho trovato il calendario FCE nei testi disponibili."}""",
            """{"answer":"Non ho trovato il calendario FCE nei testi disponibili.","verified":true}"""
        )
        val reply = assistant(model).ask("Cerca gli incontri FCE nelle circolari", emptyList(), knowledge)
        assertFalse(reply.isError)
        assertEquals(3, model.prompts.size)
        assertTrue("testi disponibili" in reply.text)
    }

    @Test fun aFailedReviewKeepsTheExistingAnswerWithAnExplicitLimit() = runTest {
        val model = ScriptedClassifier("""{"answer":"Il giornalino si riunisce il 20 novembre."}""").apply { failAt = 2 }
        val reply = assistant(model).ask("Controlla gli incontri del corso", emptyList(), knowledge)
        assertFalse(reply.isError)
        assertTrue("20 novembre" in reply.text)
        assertTrue("parziale" in reply.text)
    }

    @Test fun aNegativeReviewRecoversAutomaticallyWithoutAskingTheUserAgain() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"Ci sono incontri del giornalino."}""",
            """{"answer":"Manca il confronto con FCE.","verified":false}""",
            """{"answer":"","calendarQueries":[{"text":"giornalino","compareWith":"FCE"}]}""",
            """{"answer":"Il 4 dicembre coincidono; manca la durata FCE."}"""
        )
        val events = listOf(
            CalendarEvent("g", "Giornalino", "2026-12-04", "14:15–15:45", CalendarEventCategory.ALTRO),
            CalendarEvent("f", "FCE", "2026-12-04", "14:30", CalendarEventCategory.ALTRO)
        )
        val reply = assistant(model).ask("Confronta gli incontri", emptyList(), knowledge.copy(calendarEvents = events))
        assertFalse(reply.isError)
        assertTrue("4 dicembre" in reply.text)
        assertTrue("STESSA_DATA_ORARIO_INSUFFICIENTE" in reply.readContext)
        assertEquals(4, model.prompts.size)
    }

    @Test fun aFailedRecoveryPreservesPartialInformation() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"Giornalino il 20 novembre."}""",
            """{"answer":"Giornalino il 20 novembre. Mancano gli orari FCE.","verified":false}"""
        ).apply { failAt = 3 }
        val reply = assistant(model).ask("Confronta gli incontri", emptyList(), knowledge)
        assertFalse(reply.isError)
        assertTrue("20 novembre" in reply.text)
        assertTrue("parziale" in reply.text)
    }

    @Test fun recoveryWithoutNewEvidenceRetainsThePartialResultNotice() = runTest {
        val model = ScriptedClassifier(
            """{"answer":"Giornalino il 20 novembre."}""",
            """{"answer":"Il confronto non è completato.","verified":false}""",
            """{"answer":"Gli incontri coincidono il 20 novembre."}"""
        )
        val reply = assistant(model).ask("Confronta gli incontri", emptyList(), knowledge)
        assertFalse(reply.isError)
        assertTrue("parziali" in reply.text)
        assertEquals(3, model.prompts.size)
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
