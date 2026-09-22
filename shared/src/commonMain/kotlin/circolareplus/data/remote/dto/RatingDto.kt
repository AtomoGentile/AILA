package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RatingEntryDto(
    val studentId: String,
    val firstName: String,
    val lastName: String,
    val role: String = "STUDENT",
    val isSecurityGuard: Boolean = false,
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

/** Coppia da separare per disciplina, con la data in cui smette di valere (YYYY-MM-DD). */
@Serializable
data class DisciplinePairDto(
    val id: String,
    val studentA: String,
    val studentB: String,
    val expiresAt: String
)

@Serializable
data class DisciplinePairsResponseDto(val pairs: List<DisciplinePairDto> = emptyList())

/** `duration`: MONTH, QUARTER o SCHOOL_YEAR. */
@Serializable
data class AddDisciplinePairRequestDto(val studentA: String, val studentB: String, val duration: String)
