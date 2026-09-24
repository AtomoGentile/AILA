package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RankingPollOptionDto(val id: String, val label: String)

/** Una riga della classifica della classe (punti Borda: la prima di ogni classifica vale N-1). */
@Serializable
data class RankingPollResultDto(
    val optionId: String,
    val label: String,
    val points: Int,
    val firstPlaces: Int = 0,
    val averagePosition: Double? = null
)

@Serializable
data class RankingPollDto(
    val id: String,
    val question: String,
    val isClosed: Boolean,
    val createdAt: String,
    val options: List<RankingPollOptionDto>,
    val voterCount: Int = 0,
    val maxPoints: Int = 0,
    /** Id delle opzioni nell'ordine scelto, null se non si è ancora risposto. */
    val myRanking: List<String>? = null,
    /** Null finché non si invia la propria classifica (e il sondaggio è aperto). */
    val results: List<RankingPollResultDto>? = null
)

@Serializable
data class RankingPollsListResponseDto(
    val totalStudents: Int = 0,
    val polls: List<RankingPollDto>
)

@Serializable
data class CreateRankingPollRequestDto(val question: String, val options: List<String>)

@Serializable
data class SubmitRankingRequestDto(val optionIds: List<String>)
