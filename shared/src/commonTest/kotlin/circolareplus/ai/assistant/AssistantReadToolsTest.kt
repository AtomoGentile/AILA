package circolareplus.ai.assistant

import circolareplus.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class AssistantReadToolsTest {
    private val user = User("me", "Nome", "Cognome", "me")
    private fun event(id: String, title: String, date: String = "2026-11-25") =
        CalendarEvent(id, title, date, "3ª ora", CalendarEventCategory.VERIFICA)
    private fun knowledge(events: List<CalendarEvent>) = AssistantKnowledge("2026-11-09", user, null, calendarEvents = events)

    @Test fun targetedSearchFindsEventsBeyondTheCalendarPreview() {
        val data = (1..80).map { event("e$it", "Altro impegno") } + event("english", "Inglese U7")
        val result = AssistantReadTools.execute(AssistantCalendarQuery("inglese", "2026-11-25", "2026-11-25"), knowledge(data))
        assertTrue("english" in result)
        assertTrue("totale=1" in result)
        assertTrue("completo=true" in result)
        assertFalse("Altro impegno" in result)
    }

    @Test fun paginationAndUnavailableDataNeverClaimCompleteness() {
        val data = (1..25).map { event("e$it", "FCE") }
        val first = AssistantReadTools.execute(AssistantCalendarQuery("FCE"), knowledge(data))
        assertTrue("totale=25" in first)
        assertTrue("completo=false" in first)
        assertTrue("nextOffset=20" in first)
        val next = AssistantReadTools.execute(AssistantCalendarQuery("FCE", offset = 20), knowledge(data))
        assertTrue("mostrati=5" in next)
        val missing = AssistantReadTools.execute(AssistantCalendarQuery("inglese"), knowledge(data).copy(calendarWarning = "Offline"))
        assertTrue("non prova l'assenza" in missing)
        assertTrue("parametri non validi" in AssistantReadTools.execute(AssistantCalendarQuery(from = "2026-02-30"), knowledge(data)))
    }

    @Test fun studySearchKeepsOtherStudentsObligationsOut() {
        val other = event("other", "Inglese").copy(visibleToUserIds = listOf("peer"))
        val k = knowledge(listOf(event("mine", "Inglese"), other))
        assertTrue("other" in AssistantReadTools.execute(AssistantCalendarQuery("inglese"), k))
        assertFalse("other" in AssistantReadTools.execute(AssistantCalendarQuery("inglese"), k, studyOnly = true))
    }

    @Test fun comparisonsDistinguishKnownLessonOverlapFromUnknownClockDuration() {
        val first = event("g", "Giornalino")
        val second = event("f", "FCE").copy(time = "Dalla 2ª alla 4ª ora")
        val query = AssistantCalendarQuery("giornalino", compareWith = "FCE")
        assertTrue("SOVRAPPOSIZIONE_VERIFICATA" in AssistantReadTools.execute(query, knowledge(listOf(first, second))))
        val separate = AssistantReadTools.execute(query, knowledge(listOf(first, second.copy(time = "4ª ora"))))
        assertTrue("totale=0" in separate)
        val unknown = AssistantReadTools.execute(query, knowledge(listOf(first.copy(time = "14:15"), second.copy(time = "14:30"))))
        assertTrue("STESSA_DATA_ORARIO_INSUFFICIENTE" in unknown)
        assertFalse("SOVRAPPOSIZIONE_VERIFICATA" in unknown)
    }

    @Test fun largeRequestsAreNeverSilentlyClippedBeforePlanning() {
        val request = "Prepara gli eventi: " + "Evento con dettagli; ".repeat(160) + "ULTIMO EVENTO DA CONSERVARE"
        val prompt = AssistantPrompt.build(60_000, knowledge(emptyList()), emptyList(), request, emptyMap())
        assertTrue(request in prompt.userPrompt)
        assertFailsWith<IllegalArgumentException> {
            AssistantPrompt.build(6_000, knowledge(emptyList()), emptyList(), request.repeat(3), emptyMap())
        }
    }

    @Test fun reservingWorkingMemoryDoesNotReintroducePeerRatingsIntoCloudPrompts() {
        val privateData = knowledge(emptyList()).copy(
            user = user.copy(role = UserRole.REPRESENTATIVE),
            dynamic = AssistantDynamicKnowledge(ratings = listOf(
                circolareplus.data.remote.dto.RatingEntryDto("peer-private-id", "NomePrivatissimo", "CognomePrivatissimo", didactic = 2, behavior = 1)
            )),
            readContext = "calendar.search: ".repeat(1_200)
        )
        val prompt = AssistantPrompt.build(60_000, privateData, emptyList(), "Com’è la classe?", emptyMap())
        assertFalse("NomePrivatissimo" in prompt.userPrompt)
        assertFalse("peer-private-id" in prompt.userPrompt)
    }

    @Test fun pendingDraftAndReadResultsSurviveConversationPersistence() {
        val plan = AssistantActionPlan(listOf(AssistantCalendarAction("calendar.create", "Inglese U7", "2026-11-25", CalendarEventCategory.VERIFICA, visibility = "CLASS")))
        val message = AssistantMessage("a", AssistantAuthor.ASSISTANT, "Ho preparato queste azioni.",
            actionPlan = plan, actionStatus = AssistantActionStatus.AWAITING_CONFIRMATION, readContext = "calendar.search: FCE")
        val restored = Json.decodeFromString<AssistantMessage>(Json.encodeToString(message))
        val prompt = AssistantPrompt.build(60_000, knowledge(emptyList()), listOf(restored), "Terza ora", emptyMap())
        assertTrue("2026-11-25" in prompt.userPrompt)
        assertTrue("Inglese U7" in prompt.userPrompt)
        assertTrue("AWAITING_CONFIRMATION" in prompt.userPrompt)
        assertTrue("calendar.search: FCE" in prompt.userPrompt)
        assertTrue("actionRequest" in prompt.systemPrompt)
    }

    @Test fun readsAndSemanticActionRequestsHaveARealParsedContract() {
        val parsed = AssistantPrompt.parse("""{"answer":"","calendarQueries":[{"text":"FCE","offset":20}],"actionRequest":""}""")
        assertEquals(listOf(AssistantCalendarQuery("FCE", offset = 20)), parsed.calendarQueries)
        assertFalse(parsed.incomplete)
        val action = AssistantPrompt.parse("""{"answer":"","actionRequest":"Prepara la verifica di inglese del 25 novembre alla terza ora"}""")
        assertTrue(action.actionRequest.startsWith("Prepara"))
        assertFalse(action.incomplete)
        assertTrue(AssistantPrompt.parse("""{"answer":"Ecco gli altri incontri del giornalino (\"Il rasoio di Ockham\"""").incomplete)
    }
}
