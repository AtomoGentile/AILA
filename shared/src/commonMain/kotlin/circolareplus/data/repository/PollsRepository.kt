package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.apiJson
import circolareplus.data.remote.dto.ConfirmSwapRequestDto
import circolareplus.data.remote.dto.CreatePollRequestDto
import circolareplus.data.remote.dto.CreatePollSlotRequestDto
import circolareplus.data.remote.dto.PollAssignmentsResponseDto
import circolareplus.data.remote.dto.PollDetailDto
import circolareplus.data.remote.dto.PollProgressDto
import circolareplus.data.remote.dto.PollSubmitResponseDto
import circolareplus.data.remote.dto.PollSummaryDto
import circolareplus.data.remote.dto.SetPollDeadlineRequestDto
import circolareplus.data.remote.dto.PollsListResponseDto
import circolareplus.data.remote.dto.RunAssignmentsResponseDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.SwapRequestDto
import circolareplus.data.remote.dto.VotePollSlotRequestDto
import kotlinx.serialization.decodeFromString

/** Dettaglio del sondaggio più lo stato della compilazione della classe. */
data class PollWithProgress(
    val detail: PollDetailDto,
    val progress: PollProgressDto
)

/**
 * Sondaggi Interrogazioni & Slot Date.
 *
 * Il tetto ai voti e il bonus sacrificio restano applicati lato server; qui ci si limita a
 * chiamare le API. **L'unico tetto rimasto è quello dei Rosso Scuro (max 2)**: Verde e Rosso
 * Chiaro erano limitati a 3 ciascuno, ma con dieci date o più si restava senza opzioni prima di
 * aver espresso un giudizio su tutte, e l'app costringeva a mettere Giallo su giorni su cui si
 * aveva un'idea precisa. Client e server applicano ora la stessa identica regola.
 */
class PollsRepository(private val api: ApiClient) {

    suspend fun listPolls(): List<PollSummaryDto> = api.get<PollsListResponseDto>("/api/polls").polls

    suspend fun getPoll(id: String): PollDetailDto = api.get("/api/polls/$id")

    /**
     * Dettaglio e avanzamento in **una sola chiamata**: la risposta del server contiene entrambi,
     * quindi si scarica una volta e la si deserializza due volte con i due DTO, invece di
     * interrogare due volte lo stesso endpoint.
     */
    suspend fun getPollWithProgress(id: String): PollWithProgress {
        val raw = api.getBytes("/api/polls/$id").decodeToString()
        return PollWithProgress(
            detail = apiJson.decodeFromString(raw),
            progress = apiJson.decodeFromString(raw)
        )
    }

    /**
     * Invia in via definitiva le proprie scelte. Da qui in poi i voti sono bloccati finché non
     * si annulla l'invio: prima "Invia le mie scelte" era solo un interruttore locale sul
     * telefono e il server non ne sapeva nulla.
     */
    suspend fun submitVotes(pollId: String): PollSubmitResponseDto =
        api.post("/api/polls/$pollId/submit", Unit)

    /** Annulla il proprio invio per poter correggere le scelte (non funziona a tempo scaduto). */
    suspend fun withdrawSubmission(pollId: String) {
        api.delete<SuccessDto>("/api/polls/$pollId/submit")
    }

    /** Solo Rappresentante: imposta (o rimuove, con null) la scadenza della compilazione. */
    suspend fun setDeadline(pollId: String, closesAtIso: String?) {
        api.put<SetPollDeadlineRequestDto, SuccessDto>(
            "/api/polls/$pollId/deadline",
            SetPollDeadlineRequestDto(closesAtIso)
        )
    }

    /** Solo Rappresentante/Referente materia. La capienza totale deve essere >= studenti registrati. */
    suspend fun createPoll(subject: String, slots: List<CreatePollSlotRequestDto>): SuccessDto =
        api.post("/api/polls", CreatePollRequestDto(subject, slots))

    suspend fun publishPoll(id: String) {
        api.put<Unit, SuccessDto>("/api/polls/$id/publish", Unit)
    }

    /** voteScore: 50 (verde), 0 (giallo), -80 (rosso chiaro), -300 (rosso scuro), null = rimuovi voto. */
    suspend fun voteSlot(pollId: String, slotId: String, voteScore: Int?) {
        api.post<VotePollSlotRequestDto, SuccessDto>("/api/polls/$pollId/vote", VotePollSlotRequestDto(slotId, voteScore))
    }

    /** Solo Rappresentante. */
    suspend fun deletePoll(id: String) {
        api.delete<SuccessDto>("/api/polls/$id")
    }

    suspend fun getAssignments(pollId: String): PollAssignmentsResponseDto = api.get("/api/polls/$pollId/assignments")

    /**
     * Fa girare l'algoritmo. Il server rifiuta con 409 finché non hanno inviato tutti e la
     * scadenza non è passata; [force] serve al Rappresentante per procedere lo stesso quando si
     * sa che qualcuno non voterà (assente da settimane, telefono rotto) e la classe non può
     * restare ferma. Chi non ha votato viene assegnato comunque, come se avesse messo Giallo.
     */
    suspend fun runAssignments(pollId: String, force: Boolean = false): RunAssignmentsResponseDto =
        api.post("/api/polls/$pollId/assignments/run${if (force) "?force=1" else ""}", Unit)

    suspend fun requestSwap(pollId: String, targetStudentId: String) {
        api.post<SwapRequestDto, SuccessDto>("/api/polls/$pollId/swap-request", SwapRequestDto(targetStudentId))
    }

    suspend fun confirmSwap(swapId: String, accept: Boolean) {
        api.put<ConfirmSwapRequestDto, SuccessDto>("/api/polls/swap/$swapId/confirm", ConfirmSwapRequestDto(accept))
    }
}
