package circolareplus.ai

import circolareplus.data.SchoolTimetable
import kotlin.test.*

class SchoolTimetableTest {
    @Test fun classLabelsAndTheScreenshotEnglishLessonMatchThePublishedGrid() {
        assertEquals(SchoolTimetable.week("4Csa"), SchoolTimetable.week("4^ CSA"))
        val lesson = SchoolTimetable.suggestions("4CSA", "2026-11-25", "ing").single()
        assertEquals("INGLESE", lesson.subject)
        assertEquals("3ª ora", lesson.timeLabel)
        assertEquals(5, SchoolTimetable.maxHours("4CSA"))
    }

    @Test fun consecutiveLessonsGroupButSeparateBlocksRemainSelectable() {
        val lessons = SchoolTimetable.lessons("4CSA", "2026-11-10")
        assertEquals("Dalla 1ª alla 2ª ora", lessons.first().timeLabel)
        assertEquals("FISICA", lessons.first().subject)
        assertEquals(4, lessons.size)
    }

    @Test fun unknownClassesAndDatesOutsideTheSchoolYearDoNotGetAnInventedSchedule() {
        assertNull(SchoolTimetable.week("6ZZ"))
        assertTrue(SchoolTimetable.lessons("4CSA", "2027-11-25").isEmpty())
        assertTrue(SchoolTimetable.lessons("4CSA", "2026-11-15").isEmpty())
        assertTrue(SchoolTimetable.lessons("4CSA", "2026-02-30").isEmpty())
        assertEquals(9, SchoolTimetable.maxHours("4ASC"))
        assertEquals(9, LessonHours.parse("nona ora", maxHours = 9)?.from)
        assertNull(LessonHours.parse("nona ora", maxHours = 5))
    }
}
