package circolareplus.ai.assistant

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlin.test.*

class AssistantAudienceEditTest {
    private val me = User("me-id", "Simone", "Bianchi", "simone", UserRole.STUDENT)
    private val manuel = me.copy(id = "manuel-id", firstName = "Manuel", lastName = "Rossi")
    private val domenico = me.copy(id = "domenico-id", firstName = "Domenico", lastName = "Verdi")
    private val request = "Trova tutte le date a calendario in cui si parla di First FCE " +
        "(circolare 53 se vuoi vedere qualcosa in più) e cambia i destinatari: " +
        "deve essere visibile solo a me, Manuel e Domenico."
    private val events = (1..7).map {
        CalendarEvent("fce-$it", "Lezioni FCE", "2027-03-${it.toString().padStart(2, '0')}",
            time = "8ª ora", category = CalendarEventCategory.ALTRO, isAiGenerated = true,
            notes = "Note già presenti", circularNumber = 53)
    }
    private val knowledge = AssistantKnowledge("2026-11-07", me, null,
        classmates = listOf(manuel, domenico), calendarEvents = events + events.first().copy(id = "ielts", title = "Lezioni IELTS"))

    @Test fun screenshotUsesAllExistingFceDatesWithoutModelCopiesOrCircularLookup() {
        val plan = assertNotNull(AssistantAudienceEdit.plan(request, emptyList(), knowledge))
        assertEquals(events.map { it.id }, plan.actions.map { it.eventId })
        assertTrue(AssistantActionPrompt.validate(plan, knowledge))
        plan.actions.forEachIndexed { index, action ->
            assertEquals("calendar.update", action.tool)
            assertEquals(listOf("visibility"), action.fields)
            assertEquals(listOf(me.id, manuel.id, domenico.id), action.visibleToUserIds)
            assertEquals(events[index].date, action.date)
            assertEquals(events[index].time, action.time)
            assertEquals(events[index].notes, action.notes)
        }
    }

    @Test fun legacyClarificationRetainsRecipientsAndRefinesTheTitle() {
        val history = listOf(
            AssistantMessage("q", AssistantAuthor.USER, request),
            AssistantMessage("a", AssistantAuthor.ASSISTANT, "Non ho ottenuto un piano valido", awaitingActionInput = true)
        )
        val plan = assertNotNull(AssistantAudienceEdit.plan("Lezioni FCE", history, knowledge))
        assertEquals(7, plan.actions.size)
        assertEquals(listOf(me.id, manuel.id, domenico.id), plan.actions.first().visibleToUserIds)
        assertNull(AssistantAudienceEdit.plan("Non modificare gli eventi", history, knowledge))
    }

    @Test fun ambiguousOrMissingPeopleNeverProduceAPartialAudience() {
        for (context in listOf(knowledge.copy(classmates = listOf(manuel)),
            knowledge.copy(classmates = listOf(manuel, domenico, manuel.copy(id = "other-manuel"))))) {
            val plan = assertNotNull(AssistantAudienceEdit.plan(request, emptyList(), context))
            assertTrue(plan.actions.isEmpty())
            assertTrue(plan.clarification.isNotBlank())
        }
    }

    @Test fun tooManyDatesGiveTheCountAndAskForASmallerInterval() {
        val context = knowledge.copy(calendarEvents = (1..13).map { events.first().copy(id = "fce-$it") })
        val plan = assertNotNull(AssistantAudienceEdit.plan(request, emptyList(), context))
        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.clarification.contains("13"))
        assertTrue(plan.clarification.contains("12"))
    }

    @Test fun mixedEditsAndDateRestrictionsStayWithTheGeneralPlanner() {
        assertNull(AssistantAudienceEdit.plan(request + " Aggiungi alle note di portare l'autorizzazione.", emptyList(), knowledge))
        assertNull(AssistantAudienceEdit.plan(request.replace("First FCE", "FCE domani"), emptyList(), knowledge))
        assertNull(AssistantAudienceEdit.plan(request.replace("First FCE", "FCE del 20 marzo"), emptyList(), knowledge))
    }

    @Test fun cannotSilentlySkipEventsWithoutEditPermission() {
        val context = knowledge.copy(calendarEvents = events + events.first().copy(id = "other-owner", isAiGenerated = false, createdByUserId = "another"))
        val plan = assertNotNull(AssistantAudienceEdit.plan(request, emptyList(), context))
        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.clarification.contains("permessi"))
    }
}
