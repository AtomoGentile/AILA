package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.CalendarEventDto
import circolareplus.data.remote.dto.CalendarEventsListResponseDto
import circolareplus.data.remote.dto.CreateCalendarEventRequestDto
import circolareplus.data.remote.dto.CreateCalendarEventResponseDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory

class CalendarRepository(private val api: ApiClient) {

    suspend fun listEvents(category: CalendarEventCategory? = null): List<CalendarEvent> {
        val query = category?.let { "?category=${it.name}" } ?: ""
        val response: CalendarEventsListResponseDto = api.get("/api/calendar$query")
        return response.events.map { it.toDomain() }
    }

    /**
     * Crea un evento. Se il server rileva un possibile doppione (stessa categoria/materia entro
     * ±3 giorni per VERIFICA/INTERROGAZIONE) risponde con "warning" invece di un errore: l'evento
     * NON viene creato in quel caso, e va chiesto conferma all'utente prima di ripetere la request
     * lasciando decidere manualmente (gestione doppioni descritta nel Riepilogo Moduli v1.2).
     */
    suspend fun createEvent(
        title: String,
        eventDate: String,
        startTime: String?,
        category: CalendarEventCategory,
        isForAll: Boolean = true,
        isAiGenerated: Boolean = false,
        visibleToUserIds: List<String>? = null,
        notes: String? = null
    ): CreateCalendarEventResponseDto = api.post(
        "/api/calendar",
        CreateCalendarEventRequestDto(
            title, eventDate, startTime, category.name, isForAll, isAiGenerated,
            visibleToUserIds = visibleToUserIds, notes = notes
        )
    )

    suspend fun deleteEvent(id: String) {
        api.delete<SuccessDto>("/api/calendar/$id")
    }
}

private fun CalendarEventDto.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    title = title,
    date = eventDate,
    time = startTime,
    category = try {
        CalendarEventCategory.valueOf(category)
    } catch (e: Exception) {
        CalendarEventCategory.ALTRO
    },
    isForAll = isForAll,
    isAiGenerated = isAiGenerated,
    createdByUserId = createdBy,
    // Prima mancavano qui: il DTO li leggeva dal server ma il dettaglio evento in app li vedeva
    // sempre null, indipendentemente da cosa fosse stato salvato.
    visibleToUserIds = visibleToUserIds,
    notes = notes
)
