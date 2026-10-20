package circolareplus.data.remote.dto

import circolareplus.domain.model.SeatMapDummy
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

/**
 * Fantoccio della Mappa Posti: studente inventato dal Rappresentante per riempire la mappa.
 * Non è un utente vero: non vota le preferenze sociali e non può essere votato.
 */
@Serializable
data class SeatMapDummyDto(
    val id: String,
    val firstName: String,
    val lastName: String
)

@Serializable
data class SeatMapDummiesResponseDto(val dummies: List<SeatMapDummyDto> = emptyList())

@Serializable
data class CreateSeatMapDummyRequestDto(val firstName: String, val lastName: String)

@Serializable
data class CreatedSeatMapDummyResponseDto(val success: Boolean = false, val dummy: SeatMapDummyDto? = null)

/** Converte il fantoccio in modello di dominio. */
fun SeatMapDummyDto.toDomain(): SeatMapDummy = SeatMapDummy(id = id, firstName = firstName, lastName = lastName)
