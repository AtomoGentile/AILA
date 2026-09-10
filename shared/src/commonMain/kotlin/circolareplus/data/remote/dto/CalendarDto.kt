package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CalendarEventDto(
    val id: String,
    val title: String,
    val eventDate: String,
    val startTime: String? = null,
    val category: String,
    val isForAll: Boolean = true,
    val isAiGenerated: Boolean = false,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val visibleToUserIds: List<String>? = null,
    val notes: String? = null
)

@Serializable
data class CalendarEventsListResponseDto(val events: List<CalendarEventDto>)

@Serializable
data class CreateCalendarEventRequestDto(
    val title: String,
    val eventDate: String,
    val startTime: String? = null,
    val category: String,
    val isForAll: Boolean = true,
    val isAiGenerated: Boolean = false,
    val visibleToUserIds: List<String>? = null,
    val notes: String? = null
)

@Serializable
data class CreateCalendarEventResponseDto(
    val success: Boolean = false,
    val id: String? = null,
    val warning: String? = null,
    val existingEventId: String? = null
)
