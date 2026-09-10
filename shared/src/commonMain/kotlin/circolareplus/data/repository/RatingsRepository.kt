package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.data.remote.dto.RatingsListResponseDto
import circolareplus.data.remote.dto.SetPriorityPassRequestDto
import circolareplus.data.remote.dto.SetRatingRequestDto
import circolareplus.data.remote.dto.SuccessDto

/** Scheda Classe del Rappresentante: valutazioni didattica/comportamento 1-5 + Priority Pass. */
class RatingsRepository(private val api: ApiClient) {

    suspend fun listRatings(): List<RatingEntryDto> = api.get<RatingsListResponseDto>("/api/ratings").ratings

    suspend fun setRating(studentId: String, didactic: Int?, behavior: Int?) {
        api.put<SetRatingRequestDto, SuccessDto>("/api/ratings/$studentId", SetRatingRequestDto(didactic, behavior))
    }

    suspend fun setPriorityPass(studentId: String, enabled: Boolean) {
        api.put<SetPriorityPassRequestDto, SuccessDto>(
            "/api/ratings/$studentId/priority-pass",
            SetPriorityPassRequestDto(enabled)
        )
    }
}
