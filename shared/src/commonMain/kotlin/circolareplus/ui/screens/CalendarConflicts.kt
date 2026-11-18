package circolareplus.ui.screens

import circolareplus.ai.LessonHours
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory.INTERROGAZIONE
import circolareplus.util.parseIsoDate
import circolareplus.util.parseTimeToMinutes

internal enum class CalendarConflictKind(val label: String) {
    OVERLAP("Sovrapposizione di orario"),
    POSSIBLE("Possibile sovrapposizione"),
    SAME_DAY("Stesso giorno · orario da verificare")
}

internal data class CalendarConflict(
    val date: String,
    val first: CalendarEvent,
    val second: CalendarEvent,
    val kind: CalendarConflictKind
)

internal fun calendarConflicts(events: List<CalendarEvent>): List<CalendarConflict> {
    val byDate = events.asSequence()
        .filter { it.isRelevantToMe != false }
        .mapNotNull { event -> parseIsoDate(event.date)?.toIso()?.let { it to event } }
        .groupBy({ it.first }, { it.second })

    return buildList {
        for ((date, sameDay) in byDate.entries.sortedBy { it.key }) {
            for ((index, first) in sameDay.withIndex()) {
                for (secondIndex in index + 1 until sameDay.size) {
                    val second = sameDay[secondIndex]
                    if (first.id == second.id) continue
                    val kind = conflictKind(first, second) ?: continue
                    add(CalendarConflict(date, first, second, kind))
                }
            }
        }
    }
}

private fun conflictKind(first: CalendarEvent, second: CalendarEvent): CalendarConflictKind? {
    val firstTime = first.time.orEmpty()
    val secondTime = second.time.orEmpty()
    val firstClock = parseTimeToMinutes(firstTime)
    val secondClock = parseTimeToMinutes(secondTime)
    if (firstClock != null && secondClock != null) {
        return CalendarConflictKind.OVERLAP.takeIf { firstClock == secondClock }
    }

    val firstHours = LessonHours.parse(firstTime, LessonHours.MAX)
    val secondHours = LessonHours.parse(secondTime, LessonHours.MAX)
    if (firstHours != null && secondHours != null) {
        return CalendarConflictKind.OVERLAP.takeIf { firstHours.from <= secondHours.to && secondHours.from <= firstHours.to }
    }

    val hours = firstHours ?: secondHours
    val clock = if (firstHours != null) secondClock else firstClock
    if (hours != null && clock != null) {
        val clockHour = LessonHours.fromClock(
            if (firstHours != null) secondTime.split(':').take(2).joinToString(":")
            else firstTime.split(':').take(2).joinToString(":"),
            LessonHours.MAX
        ) ?: return null
        return CalendarConflictKind.POSSIBLE.takeIf { clockHour in hours.from..hours.to }
    }

    val circularAndExam = (first.circularNumber != null && second.category == INTERROGAZIONE) ||
        (second.circularNumber != null && first.category == INTERROGAZIONE)
    return CalendarConflictKind.SAME_DAY.takeIf { circularAndExam }
}
