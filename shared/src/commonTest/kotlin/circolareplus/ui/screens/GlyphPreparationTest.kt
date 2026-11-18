package circolareplus.ui.screens

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlyphPreparationTest {
    // 09:00 in Italy on 18 November.
    private val now = 1_794_988_800_000L
    private val italyOffset: (Long) -> Long = { 3_600_000L }

    @Test fun picksNearestRelevantEventWithAnExplicitClockTime() {
        val next = event("next", "9:27:30")
        val result = nextGlyphAppointment(
            listOf(event("all-day", null), event("lesson-hours", "2ª ora"),
                event("passed", "08:59"), event("other-student", "09:10").copy(isRelevantToMe = false),
                event("later", "10:05"), next),
            now,
            italyOffset
        )
        assertEquals(next, result?.event)
        assertEquals(now + 27 * 60_000L + 30_000L, result?.startsAt)
        assertEquals(now + 22 * 60_000L + 30_000L, result?.preparationAt)
    }

    @Test fun preparationCanFallOnThePreviousDay() {
        val beforeMidnight = 1_795_042_740_000L
        val event = event("midnight", "00:03", date = "2026-11-19")
        val result = nextGlyphAppointment(listOf(event), beforeMidnight, italyOffset)
        assertEquals(beforeMidnight + 4 * 60_000L, result?.startsAt)
        assertEquals(beforeMidnight - 60_000L, result?.preparationAt)
    }

    @Test fun rejectsInvalidDatesOrNonexistentLocalTimes() {
        assertNull(nextGlyphAppointment(listOf(event("invalid", "09:05", "2026-11-31")), now, italyOffset))
        val offsetAfterSpringChange: (Long) -> Long = { if (it >= now + 300_000L) 7_200_000L else 3_600_000L }
        assertNull(nextGlyphAppointment(listOf(event("gap", "09:20")), now, offsetAfterSpringChange))
    }

    private fun event(id: String, time: String?, date: String = "2026-11-18") =
        CalendarEvent(id, id, date, time, CalendarEventCategory.INTERROGAZIONE)
}
