package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RatingEntryDto(
    val studentId: String,
    val firstName: String,
    val lastName: String,
    val heightCm: Int? = null,
    val didactic: Int? = null,
    val behavior: Int? = null,
    val priorityPass: Boolean = false,
    val updatedAt: String? = null
)

@Serializable
data class RatingsListResponseDto(val ratings: List<RatingEntryDto>)

@Serializable
data class SetRatingRequestDto(val didactic: Int? = null, val behavior: Int? = null)

@Serializable
data class SetPriorityPassRequestDto(val enabled: Boolean)
