package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `layout` è un JSON libero — lato client corrisponde alla serializzazione di
 * List<circolareplus.algorithms.DeskAssignment> scelta dal Rappresentante e pubblicata.
 * Il Worker lo salva/restituisce così com'è (schema.sql: seat_map_history.layout_json).
 */
@Serializable
data class SeatMapEntryDto(
    val id: String,
    val layout: JsonElement,
    val publishedAt: String,
    val mapIndex: Int? = null
)

@Serializable
data class CurrentSeatMapResponseDto(val seatMap: SeatMapEntryDto? = null)

@Serializable
data class SeatMapHistoryResponseDto(val history: List<SeatMapEntryDto>)

@Serializable
data class PublishSeatMapRequestDto(val layout: JsonElement)
