package circolareplus.ui.screens

import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.util.CivilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarConflictsTest {
    private val exam = CalendarEvent("exam", "Interrogazione", "2026-11-19", "09:00", CalendarEventCategory.INTERROGAZIONE)
    private val circular = CalendarEvent("notice", "Assemblea", "2026-11-19", "9:00:00", CalendarEventCategory.AVVISO, circularNumber = 314)

    @Test
    fun classifiesSameClockTimeAndOverlappingLessonRanges() {
        assertEquals(CalendarConflictKind.OVERLAP, calendarConflicts(listOf(exam, circular)).single().kind)
        val ranges = listOf(exam.copy(time = "2ª ora"), circular.copy(time = "Dalla 2ª alla 4ª ora"))
        assertEquals(CalendarConflictKind.OVERLAP, calendarConflicts(ranges).single().kind)
    }

    @Test
    fun distinguishesUnscheduledExamFromPossibleLessonOverlap() {
        assertEquals(CalendarConflictKind.SAME_DAY, calendarConflicts(listOf(exam.copy(time = null), circular)).single().kind)
        assertEquals(CalendarConflictKind.POSSIBLE, calendarConflicts(listOf(exam.copy(time = "2ª ora"), circular)).single().kind)
    }

    @Test
    fun ignoresAnotherDayDifferentTimeAndOtherStudents() {
        assertTrue(calendarConflicts(listOf(exam, circular.copy(time = "10:00"))).isEmpty())
        assertTrue(calendarConflicts(listOf(exam, circular.copy(date = "2026-11-20"))).isEmpty())
        assertTrue(calendarConflicts(listOf(exam.copy(isRelevantToMe = false), circular)).isEmpty())
    }

    @Test fun glyphShowsTheNextSevenDatesAndSkipsAnOverlapThatHasAlreadyStarted() {
        val today = CivilDate(2026, 11, 19)
        val exact = calendarConflicts(listOf(exam, circular)).single()
        val untimed = calendarConflicts(listOf(exam.copy(time = null), circular)).single()
        val lastDay = exact.copy(date = "2026-11-25")
        val later = exact.copy(date = "2026-11-26")
        val past = exact.copy(date = "2026-11-18")

        assertEquals(listOf(exact, untimed, lastDay), currentGlyphConflicts(
            listOf(past, exact, untimed, lastDay, later), today, 9 * 60
        ))
        assertEquals(listOf(untimed, lastDay), currentGlyphConflicts(
            listOf(past, exact, untimed, lastDay, later), today, 9 * 60 + 1
        ))
    }
}
