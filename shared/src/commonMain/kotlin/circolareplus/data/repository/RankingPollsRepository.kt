package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.CreateRankingPollRequestDto
import circolareplus.data.remote.dto.RankingPollsListResponseDto
import circolareplus.data.remote.dto.SubmitRankingRequestDto
import circolareplus.data.remote.dto.SuccessDto

/**
 * Sondaggi a ordinamento: ognuno mette in ordine le opzioni e la classe ottiene una classifica
 * a punti. I risultati li calcola il server a ogni lettura, non c'è un calcolo da far partire.
 */
class RankingPollsRepository(private val api: ApiClient) {

    suspend fun listPolls(): RankingPollsListResponseDto = api.get("/api/ranking-polls")

    /** Solo Rappresentante. Il sondaggio è pubblicato subito e la classe riceve la notifica. */
    suspend fun createPoll(question: String, options: List<String>): SuccessDto =
        api.post("/api/ranking-polls", CreateRankingPollRequestDto(question, options))

    /** Invia o sostituisce la propria classifica: [optionIds] deve contenere tutte le opzioni. */
    suspend fun submitRanking(pollId: String, optionIds: List<String>) {
        api.put<SubmitRankingRequestDto, SuccessDto>("/api/ranking-polls/$pollId/ranking", SubmitRankingRequestDto(optionIds))
    }

    /** Solo Rappresentante: da qui in poi le classifiche non si cambiano più. */
    suspend fun closePoll(pollId: String) {
        api.put<Unit, SuccessDto>("/api/ranking-polls/$pollId/close", Unit)
    }

    /** Solo Rappresentante. */
    suspend fun deletePoll(pollId: String) {
        api.delete<SuccessDto>("/api/ranking-polls/$pollId")
    }
}
