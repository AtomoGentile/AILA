package circolareplus.ui.screens

import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.data.remote.dto.BureaucracyPracticeDto
import circolareplus.domain.model.CalendarEvent

internal fun mergeBureaucracyBrief(
    brief: List<AilaBriefItemDto>,
    practices: List<BureaucracyPracticeDto>,
    dismissed: Set<String>
): List<AilaBriefItemDto> {
    val paperwork = practices.mapNotNull { practice ->
        val date = practice.dueDate ?: return@mapNotNull null
        val revision = "${practice.id}:$date"
        if (practice.archivedAt != null || practice.received != false || revision in dismissed) return@mapNotNull null
        AilaBriefItemDto(
            id = "BUREAUCRACY:${practice.id}", revision = revision, kind = AilaBriefKind.BUREAUCRACY,
            sourceId = practice.id, title = practice.title, message = "Da consegnare", actionLabel = "Apri la pratica",
            date = date
        )
    }
    if (paperwork.isEmpty()) return brief

    val coveredDates = paperwork.map { paperworkTitleKey(it.title) to it.date }.toSet()
    val (timeline, otherItems) = brief.partition { it.kind == AilaBriefKind.EVENT }
    val remainingEvents = timeline.filterNot { (paperworkTitleKey(it.title) to it.date) in coveredDates }
    return (paperwork + remainingEvents).sortedWith(compareBy { it.date ?: "9999-12-31" }) + otherItems
}

internal fun calendarPreviewWithoutBriefDuplicates(
    events: List<CalendarEvent>,
    brief: List<AilaBriefItemDto>
): List<CalendarEvent> {
    val coveredDates = brief.filter { it.kind == AilaBriefKind.BUREAUCRACY }
        .mapNotNull { item -> item.date?.let { paperworkTitleKey(item.title) to it } }.toSet()
    return events.filterNot { (paperworkTitleKey(it.title) to it.date) in coveredDates }
}

private val paperworkTitleSpacing = Regex("\\s+")

private fun paperworkTitleKey(title: String) = title.trim().lowercase().replace(paperworkTitleSpacing, " ")
