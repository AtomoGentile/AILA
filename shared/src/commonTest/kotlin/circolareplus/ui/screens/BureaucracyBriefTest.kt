package circolareplus.ui.screens

import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.data.remote.dto.BureaucracyPracticeDto
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class BureaucracyBriefTest {
    @Test
    fun mergesTheMatchingDeadlineAndKeepsOtherCalendarDates() {
        val deadline = CalendarEvent("first", "Autorizzazione gita", "2026-12-01", category = CalendarEventCategory.AVVISO)
        val later = deadline.copy(id = "later", date = "2026-12-10")
        val other = deadline.copy(id = "other", title = "Riunione")
        val practice = BureaucracyPracticeDto("practice", " AUTORIZZAZIONE  GITA ", "", null, false)
        val events = listOf(deadline, later, other)
        val brief = mergeBureaucracyBrief(events.map(::briefEvent), listOf(practice), emptySet(), events)

        assertEquals(listOf("practice", "later", "other"), brief.map { it.sourceId })
        assertEquals(AilaBriefKind.BUREAUCRACY, brief.first().kind)
        assertEquals(deadline.date, brief.first().date)
        assertEquals(listOf(later, other), calendarPreviewWithoutBriefDuplicates(events, brief))
    }

    @Test
    fun keepsOnlyPendingPracticesAndWorksWithTheCalendarAlone() {
        val deadline = CalendarEvent("event", "Modulo", "2026-12-01", category = CalendarEventCategory.AVVISO)
        val pending = BureaucracyPracticeDto("pending", "Modulo", "", null, false)
        val practices = listOf(
            pending,
            pending.copy(id = "archived", archivedAt = "2026-11-20"),
            pending.copy(id = "delivered", received = true),
            pending.copy(id = "dismissed")
        )

        val brief = mergeBureaucracyBrief(emptyList(), practices, setOf("dismissed"), listOf(deadline))
        assertEquals(listOf("pending"), brief.map { it.sourceId })
        assertEquals(deadline.date, brief.single().date)
        assertEquals(listOf(deadline), calendarPreviewWithoutBriefDuplicates(listOf(deadline), emptyList()))
    }

    private fun briefEvent(event: CalendarEvent) = AilaBriefItemDto(
        id = event.id, revision = event.id, kind = AilaBriefKind.EVENT,
        sourceId = event.id, title = event.title, message = "", actionLabel = "Apri", date = event.date
    )
}
