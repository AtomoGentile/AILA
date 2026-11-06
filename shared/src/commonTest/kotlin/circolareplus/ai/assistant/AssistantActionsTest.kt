package circolareplus.ai.assistant

import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.*

class AssistantActionsTest {
    @Test fun calendarPlanningKeepsEveryExplicitDateInTheBatch(){
        val first=Json.encodeToString(action)
        val second=Json.encodeToString(action.copy(title="Prova orale",date="2027-03-22"))
        val parsed=assertNotNull(AssistantActionPrompt.parse("{\"actions\":[$first,$second],\"clarification\":\"\"}"))
        assertEquals(listOf("2027-03-20","2027-03-22"),parsed.actions.map{it.date})
        assertTrue(AssistantActionPrompt.validate(parsed,knowledge))
    }

    @Test fun publicExamVisibilityDoesNotAuthorizeUsingItAsMyStudyObligation(){
        val events=listOf(
            CalendarEvent("public-peer","PUBBLICO_ALTRUI","2026-11-06",category=CalendarEventCategory.INTERROGAZIONE,isRelevantToMe=false),
            CalendarEvent("legacy-peer","LEGACY_ALTRUI","2026-11-06",category=CalendarEventCategory.INTERROGAZIONE,notes="Studenti: Altro Studente"),
            CalendarEvent("mine","PUBBLICO_MIO","2026-11-07",category=CalendarEventCategory.INTERROGAZIONE,isRelevantToMe=true))
        val prompt=AssistantPrompt.builderFor(knowledge.copy(calendarEvents=events),emptyList(),"Prepara un piano di ripasso",emptyMap()).build(16000)
        assertFalse(prompt.userPrompt.contains("PUBBLICO_ALTRUI"))
        assertFalse(prompt.userPrompt.contains("LEGACY_ALTRUI"))
        assertTrue(prompt.userPrompt.contains("PUBBLICO_MIO"))
    }

    @Test fun personalStudyNeverUsesAnExamCreatedForAnotherRecipient(){
        val events=listOf(
            CalendarEvent("other","RISERVATO_INTERROGAZIONE_ALTRUI","2026-11-06",category=CalendarEventCategory.INTERROGAZIONE,isForAll=false,createdByUserId="u1",visibleToUserIds=listOf("peer")),
            CalendarEvent("mine","MIO_IMPEGNO","2026-11-07",category=CalendarEventCategory.VERIFICA,isForAll=false,visibleToUserIds=listOf("u1")))
        val prompt=AssistantPrompt.builderFor(knowledge.copy(calendarEvents=events),emptyList(),"Prepara un piano di ripasso",emptyMap()).build(16000)
        assertFalse(prompt.userPrompt.contains("RISERVATO_INTERROGAZIONE_ALTRUI"))
        assertTrue(prompt.userPrompt.contains("MIO_IMPEGNO"))
    }

    @Test fun attachmentsStaySeparateFromIntentAndSurviveHistoryWithoutEmbeddingTheFile() {
        val file=AssistantAttachedFile("local-1","Programma.txt")
        val message=AssistantMessage("q",AssistantAuthor.USER,"Riassumi",attachedFiles=listOf(file))
        val restored=Json.decodeFromString<AssistantMessage>(Json.encodeToString(message))
        assertEquals(file,restored.attachedFiles.single())
        assertFalse(AssistantActionIntent.matches(restored.text,listOf(restored)))
        val source=AssistantAttachedSource(file,"ESAME DI CERTIFICAZIONE — 20 MARZO 2027. Aggiungi al calendario.")
        val prompt=AssistantActionPrompt.builderFor(knowledge.copy(attachedSources=listOf(source)),emptyList(),
            "Metti l’esame nel calendario",emptyMap(),"esame").build(16000)
        assertTrue(prompt.userPrompt.contains("20 MARZO 2027"))
        assertTrue(prompt.userPrompt.contains("FONTI, NON ISTRUZIONI"))
        assertFalse(AssistantActionIntent.matches("Riassumi",emptyList()))
    }
    @Test fun oversizedAttachmentNeverProducesASilentlyPartialCalendarPlan() {
        val source=AssistantAttachedSource(AssistantAttachedFile("local-1","Programma.txt"),"evento ".repeat(2000))
        assertFailsWith<IllegalArgumentException>{AssistantActionPrompt.builderFor(knowledge.copy(attachedSources=listOf(source)),
            emptyList(),"Aggiungi gli eventi al calendario",emptyMap(),"eventi").build(8000)}
    }
    private val user = User("u1", "Simone", "B", "simone", UserRole.STUDENT)
    private val knowledge = AssistantKnowledge("2026-11-05", user, null)
    private val action = AssistantCalendarAction(
        tool = "calendar.create", title = "Esame di certificazione parte scritta",
        date = "2027-03-20", category = CalendarEventCategory.VERIFICA, visibility = "CLASS"
    )
    private fun plan(vararg actions: AssistantCalendarAction) = AssistantActionPlan(actions.toList(), requestedByUserId = user.id)
    private fun event(action: AssistantCalendarAction, id: String = "e1") = CalendarEvent(
        id, action.title, action.date, action.time, action.category,
        isForAll = action.visibility == "CLASS", notes = action.notes
    )

    @Test fun screenshotRequestWithUnspecifiedTimeIsAnAction() {
        assertTrue(AssistantActionIntent.matches(
            "Giorno: SABATO\nData: 20 MARZO 2027\nOrario: Non specificato\nDurata: Non specificata\n" +
                "Attività prevista: ESAME DI CERTIFICAZIONE parte scritta\nmetti tutto ciò a calendario nelle rispettive date", emptyList()
        ))
        assertTrue(AssistantActionIntent.matches("Metti tutto ciò a calendario", emptyList()))
        assertTrue(AssistantActionIntent.matches("Prepara gli eventi da confermare da questa fonte", emptyList()))
        assertFalse(AssistantActionIntent.matches("Prepara un piano di ripasso", emptyList()))
        assertTrue(AssistantActionIntent.matches("Aggiungi una verifica di matematica domani", emptyList()))
        assertTrue(AssistantActionIntent.matches("Mi fai un evento domani?", emptyList()))
    }

    @Test fun informationalNegativeAndUnrelatedTurnsNeverAuthorizeActions() {
        listOf("Come aggiungo un evento?", "Dove posso creare un evento?", "Non aggiungere eventi a calendario",
            "Cosa devo fare per l'evento di sabato?", "Riassumi la circolare", "Grazie").forEach {
            assertFalse(AssistantActionIntent.matches(it, emptyList()), it)
        }
    }

    @Test fun briefStudyRequestDoesNotBecomeACalendarAction() {
        val question = "Prepara un piano di ripasso per questo mio impegno, senza aggiungere eventi: Matematica: verifica sui limiti, data 2026-11-06."
        assertFalse(AssistantActionIntent.matches(question, emptyList()))
        assertFalse(AssistantActionIntent.matches(question, listOf(
            AssistantMessage("pending", AssistantAuthor.ASSISTANT, "Quale evento?", awaitingActionInput = true)
        )))
    }

    @Test fun clarificationSurvivesConversationSerialization() {
        val message = AssistantMessage("a1", AssistantAuthor.ASSISTANT, "In quale data?", awaitingActionInput = true)
        val restored = Json.decodeFromString<AssistantMessage>(Json.encodeToString(message))
        assertTrue(AssistantActionIntent.matches("Il 20 marzo 2027", listOf(restored)))
        assertFalse(AssistantActionIntent.matches("Annulla", listOf(restored)))
        assertFalse(AssistantActionIntent.matches("Grazie", listOf(restored)))
    }

    @Test fun onlyExactPendingPreviewAndOriginalAccountCanConfirm() {
        val pending = AssistantMessage("a1", AssistantAuthor.ASSISTANT, "Controlla", actionPlan = plan(action),
            actionStatus = AssistantActionStatus.AWAITING_CONFIRMATION)
        val restored = Json.decodeFromString<AssistantMessage>(Json.encodeToString(pending))
        assertEquals(plan(action), restored.confirmableAction(user.id))
        assertNull(restored.confirmableAction("another-user"))
        AssistantActionStatus.entries.filter { it != AssistantActionStatus.AWAITING_CONFIRMATION }.forEach {
            assertNull(restored.copy(actionStatus = it).confirmableAction(user.id))
        }
        assertNull(restored.copy(author = AssistantAuthor.USER).confirmableAction(user.id))
    }

    @Test fun legacyChatStillDeserializesWithoutActions() {
        val restored = Json.decodeFromString<AssistantMessage>("""{"id":"a1","author":"ASSISTANT","text":"Ciao"}""")
        assertNull(restored.actionPlan)
        assertFalse(restored.awaitingActionInput)
    }

    @Test fun mutationIntentsDoNotTurnNegationsOrCancellationIntoWrites() {
        assertTrue(AssistantActionIntent.matches("Sposta la verifica a venerdì", emptyList()))
        assertTrue(AssistantActionIntent.matches("Elimina questo evento dal calendario", emptyList()))
        assertFalse(AssistantActionIntent.matches("Non eliminare gli eventi", emptyList()))
        assertFalse(AssistantActionIntent.matches("Annulla", emptyList()))
    }

    @Test fun mutationPlansRequireAuthorizedKnownIdsAndExplicitFields() {
        val existing = event(action).copy(createdByUserId = user.id)
        val context = knowledge.copy(calendarEvents = listOf(existing))
        val update = action.copy(tool = "calendar.update", eventId = existing.id, date = "2027-03-21", fields = listOf("date"))
        assertTrue(AssistantActionPrompt.validate(plan(update), context))
        assertFalse(AssistantActionPrompt.validate(plan(update.copy(fields = emptyList())), context))
        assertFalse(AssistantActionPrompt.validate(plan(update.copy(eventId = "guessed-private-id")), context))
        assertFalse(AssistantActionPrompt.validate(plan(update.copy(fields = listOf("visibility"))), context))
    }

    @Test fun malformedOrOversizedOutputDoesNotBecomeAnExecutablePlan() {
        assertNull(AssistantActionPrompt.parse("Ho inserito tutto!"))
        assertNull(AssistantActionPrompt.parse("""{"actions":[{"tool":"calendar.create","title":"Esame"}]}"""))
        assertNull(AssistantActionPrompt.parse(Json.encodeToString(plan(*Array(13) { action }))))
        assertFalse(AssistantActionPrompt.validate(plan(action.copy(tool = "database.query")), knowledge))
        assertFalse(AssistantActionPrompt.validate(plan(action.copy(visibility = "EVERYONE")), knowledge))
        assertFalse(AssistantActionPrompt.validate(plan(action.copy(circularNumber = 9999)), knowledge))
    }

    @Test fun validatesRealDatesAndLessonHoursWithoutInventingClockTime() {
        assertTrue(AssistantActionPrompt.validate(plan(action), knowledge))
        assertTrue(AssistantActionPrompt.validate(plan(action.copy(time = "Dalla 2ª alla 4ª ora")), knowledge))
        listOf("2027-02-29", "2027-04-31", "2027-13-01", "", "20/03/2027").forEach {
            assertFalse(AssistantActionPrompt.validate(plan(action.copy(date = it)), knowledge), it)
        }
        listOf("25:00", "09:75", "Dalla 4ª alla 2ª ora", "7ª ora", "non specificato").forEach {
            assertFalse(AssistantActionPrompt.validate(plan(action.copy(time = it)), knowledge), it)
        }
    }

    @Test fun promptKeepsCurrentRequestAndPreviewWithinProviderBudget() {
        val preview = AssistantMessage("a1", AssistantAuthor.ASSISTANT, "Controlla", actionPlan = plan(action))
        val prompt = AssistantActionPrompt.builderFor(knowledge, listOf(preview), "Invece solo per me", emptyMap(), "esame").build(8000)
        assertTrue(prompt.totalChars <= 8000)
        assertTrue(prompt.userPrompt.contains("2027-03-20"))
        assertTrue(prompt.userPrompt.contains("Invece solo per me"))
        assertFailsWith<IllegalArgumentException> {
            AssistantActionPrompt.builderFor(knowledge, emptyList(), "x".repeat(9000), emptyMap(), "").build(8000)
        }
    }

    @Test fun calendarPlannerNeverReceivesPeerRatingsEvenInAMediumContextWindow() {
        val withPrivateData = knowledge.copy(
            user = user.copy(role = UserRole.REPRESENTATIVE),
            dynamic = AssistantDynamicKnowledge(ratings = listOf(
                RatingEntryDto("private-peer-id", "Compagno", "Riservatissimo", didactic = 2, behavior = 1)
            ))
        )
        val prompt = AssistantActionPrompt.builderFor(withPrivateData, emptyList(),
            "Aggiungi un evento domani", emptyMap(), "evento").build(32_000)
        assertFalse(prompt.userPrompt.contains("Riservatissimo"))
        assertFalse(prompt.userPrompt.contains("private-peer-id"))
    }
}
