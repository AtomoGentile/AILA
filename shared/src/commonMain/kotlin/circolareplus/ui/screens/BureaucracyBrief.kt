package circolareplus.ui.screens

import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.data.remote.dto.BureaucracyPracticeDto
import circolareplus.domain.model.CalendarEvent

internal fun mergeBureaucracyBrief(
    brief: List<AilaBriefItemDto>,
    practices: List<BureaucracyPracticeDto>,
    dismissed: Set<String>,
    upcoming: List<CalendarEvent> = emptyList()
): List<AilaBriefItemDto> {
    val pending = practices.filter { it.archivedAt == null && it.received == false && it.id !in dismissed }
    if (pending.isEmpty()) return brief

    val upcomingByTitle = upcoming.groupBy { paperworkTitleKey(it.title) }
    val briefEventsByTitle = brief.filter { it.kind == AilaBriefKind.EVENT }.groupBy { paperworkTitleKey(it.title) }
    val paperwork = pending.map { practice ->
        val title = paperworkTitleKey(practice.title)
        val date = (upcomingByTitle[title].orEmpty().map { it.date } +
            briefEventsByTitle[title].orEmpty().mapNotNull { it.date }).minOrNull()
        AilaBriefItemDto(
            id = "BUREAUCRACY:${practice.id}", revision = practice.id, kind = AilaBriefKind.BUREAUCRACY,
            sourceId = practice.id, title = practice.title, message = "Da consegnare", actionLabel = "Apri la pratica",
            date = date
        )
    }
    val coveredDates = paperwork.mapNotNull { item -> item.date?.let { paperworkTitleKey(item.title) to it } }.toSet()
    return paperwork + brief.filterNot {
        it.kind == AilaBriefKind.EVENT && (paperworkTitleKey(it.title) to it.date) in coveredDates
    }
}

internal fun calendarPreviewWithoutBriefDuplicates(
    events: List<CalendarEvent>,
    visibleBrief: List<AilaBriefItemDto>
): List<CalendarEvent> {
    val coveredDates = visibleBrief.filter { it.kind == AilaBriefKind.BUREAUCRACY }
        .mapNotNull { item -> item.date?.let { paperworkTitleKey(item.title) to it } }.toSet()
    return events.filterNot { (paperworkTitleKey(it.title) to it.date) in coveredDates }
}

private val paperworkTitleSpacing = Regex("\\s+")

private fun paperworkTitleKey(title: String) = title.trim().lowercase().replace(paperworkTitleSpacing, " ")
