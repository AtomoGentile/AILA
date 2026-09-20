package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.AddCommentRequestDto
import circolareplus.data.remote.dto.ChangeProposalStatusRequestDto
import circolareplus.data.remote.dto.CreateProposalRequestDto
import circolareplus.data.remote.dto.ProposalCommentDto
import circolareplus.data.remote.dto.ProposalCommentsListResponseDto
import circolareplus.data.remote.dto.ProposalDto
import circolareplus.data.remote.dto.ProposalsListResponseDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.ApproveUnlockResponseDto
import circolareplus.data.remote.dto.CreateUnlockRequestDto
import circolareplus.data.remote.dto.EmptyBodyDto
import circolareplus.data.remote.dto.SetSecurityGuardRequestDto
import circolareplus.data.remote.dto.UnlockRequestDto
import circolareplus.data.remote.dto.UnlockRequestsListResponseDto
import circolareplus.data.remote.dto.UpdateProposalRequestDto
import circolareplus.data.remote.dto.VoteProposalRequestDto
import circolareplus.data.remote.dto.VoteProposalResponseDto
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalComment
import circolareplus.domain.model.ProposalOutcome
import circolareplus.domain.model.ProposalStatus
import circolareplus.domain.model.UnlockRequest
import circolareplus.domain.model.UnlockRequestsState

class ProposalsRepository(private val api: ApiClient) {

    suspend fun listProposals(status: ProposalStatus? = null): List<Proposal> {
        val query = status?.let { "?status=${it.name}" } ?: ""
        val response: ProposalsListResponseDto = api.get("/api/proposals$query")
        return response.proposals.map { it.toDomain() }
    }

    suspend fun createProposal(title: String, description: String, category: String, isAnonymous: Boolean) {
        api.post<CreateProposalRequestDto, SuccessDto>(
            "/api/proposals",
            CreateProposalRequestDto(title, description, category, isAnonymous)
        )
    }

    /**
     * Modifica il testo di una proposta. La può modificare l'autore, e il Rappresentante può
     * modificarne una qualunque della classe. In bacheca compare poi la dicitura "Modificato".
     *
     * Prima non esisteva: chi si accorgeva di un errore poteva solo cancellare la proposta e
     * riscriverla, perdendo voti e commenti già raccolti.
     */
    suspend fun updateProposal(
        proposalId: String,
        title: String? = null,
        description: String? = null,
        category: String? = null
    ) {
        api.put<UpdateProposalRequestDto, SuccessDto>(
            "/api/proposals/$proposalId",
            UpdateProposalRequestDto(title, description, category)
        )
    }

    /** voteType: 1 (favorevole), -1 (contrario), 0 (rimuovi il proprio voto). */
    suspend fun vote(proposalId: String, voteType: Int): VoteProposalResponseDto =
        api.post("/api/proposals/$proposalId/vote", VoteProposalRequestDto(voteType))

    /**
     * Solo Rappresentante: unico modo per cambiare stato di una proposta. Chiudendo (`CHIUSA`)
     * l'esito è obbligatorio: il server rifiuta una chiusura senza accettata/rifiutata.
     */
    suspend fun changeStatus(proposalId: String, status: ProposalStatus, outcome: ProposalOutcome? = null) {
        api.put<ChangeProposalStatusRequestDto, SuccessDto>(
            "/api/proposals/$proposalId/status",
            ChangeProposalStatusRequestDto(status.name, outcome?.name)
        )
    }

    /** L'autore può cancellare la propria proposta; il Rappresentante può cancellarne qualunque. */
    suspend fun delete(proposalId: String) {
        api.delete<SuccessDto>("/api/proposals/$proposalId")
    }

    suspend fun listComments(proposalId: String): List<ProposalComment> {
        val response: ProposalCommentsListResponseDto = api.get("/api/proposals/$proposalId/comments")
        return response.comments.map { it.toDomain(proposalId) }
    }

    suspend fun addComment(proposalId: String, content: String, isAnonymous: Boolean = false) {
        api.post<AddCommentRequestDto, SuccessDto>(
            "/api/proposals/$proposalId/comments",
            AddCommentRequestDto(content, isAnonymous)
        )
    }

    // --- Svelamento dell'autore anonimo -------------------------------------------------------
    // Quorum: 2 Rappresentanti + 1 Guardia di Sicurezza (Specifica Tecnica v3.0, sez. 3.3). Chi
    // apre la richiesta firma per primo; gli altri approvano dal proprio account. Sostituisce
    // il vecchio `unlockIdentity`, in cui una persona sola indicava gli id dei tre firmatari.

    /** Apre la richiesta (e la firma). [commentId] null = l'autore della proposta. */
    suspend fun requestUnlock(proposalId: String, reason: String, commentId: String? = null) {
        api.post<CreateUnlockRequestDto, SuccessDto>(
            "/api/proposals/$proposalId/unlock-requests",
            CreateUnlockRequestDto(reason, commentId)
        )
    }

    /**
     * Richieste in attesa nella classe. Le vedono solo i firmatari; per gli altri la risposta è
     * `canSign = false` e nessuna richiesta.
     */
    suspend fun listUnlockRequests(): UnlockRequestsState {
        val response: UnlockRequestsListResponseDto = api.get("/api/proposals/unlock-requests")
        return UnlockRequestsState(response.canSign, response.requests.map { it.toDomain() })
    }

    /**
     * Solo Rappresentante: sceglie la Guardia di Sicurezza (terza firma) fra i compagni, oppure
     * la revoca con `null`. Ce n'è una sola per classe: una nuova sostituisce la precedente.
     */
    suspend fun setSecurityGuard(userId: String?) {
        api.put<SetSecurityGuardRequestDto, SuccessDto>(
            "/api/proposals/security-guard",
            SetSecurityGuardRequestDto(userId)
        )
    }

    suspend fun approveUnlock(requestId: String): ApproveUnlockResponseDto =
        api.post("/api/proposals/unlock-requests/$requestId/approve", EmptyBodyDto())

    /** Ognuno dei tre firmatari può opporsi: la richiesta si chiude senza svelare nulla. */
    suspend fun rejectUnlock(requestId: String) {
        api.post<EmptyBodyDto, SuccessDto>("/api/proposals/unlock-requests/$requestId/reject", EmptyBodyDto())
    }
}

private fun ProposalDto.toDomain(): Proposal = Proposal(
    id = id,
    authorId = authorId,
    authorName = when {
        isAnonymous && authorName == null -> "Anonimo"
        else -> authorName ?: "Studente"
    },
    isAnonymous = isAnonymous,
    title = title,
    description = description,
    category = category,
    status = try {
        ProposalStatus.valueOf(status)
    } catch (e: Exception) {
        ProposalStatus.NUOVA
    },
    outcome = outcome?.let { runCatching { ProposalOutcome.valueOf(it) }.getOrNull() },
    upvotes = upVotes,
    downvotes = downVotes,
    myVote = myVote ?: 0,
    identityRevealed = identityRevealed,
    unlockRequestStatus = unlockRequestStatus,
    commentsCount = commentCount,
    // Il campo JSON si chiama ancora modifiedByRep per non rompere le app già installate;
    // il server lo valorizza a true per qualunque modifica, non solo per quelle del Rappresentante.
    isEdited = modifiedByRep,
    createdAt = createdAt
)

private fun ProposalCommentDto.toDomain(proposalId: String): ProposalComment = ProposalComment(
    id = id,
    proposalId = proposalId,
    authorName = authorName,
    content = content,
    createdAt = createdAt,
    isAnonymous = isAnonymous,
    isMine = isMine,
    identityRevealed = identityRevealed,
    unlockRequestStatus = unlockRequestStatus
)

private fun UnlockRequestDto.toDomain(): UnlockRequest = UnlockRequest(
    id = id,
    proposalId = proposalId,
    proposalTitle = proposalTitle,
    commentId = commentId,
    commentExcerpt = commentExcerpt,
    reason = reason,
    requestedByName = requestedByName,
    representativeApprovals = representativeApprovals,
    guardApprovals = guardApprovals,
    representativesNeeded = representativesNeeded,
    guardsNeeded = guardsNeeded,
    approvedByMe = approvedByMe,
    approverNames = approverNames,
    createdAt = createdAt
)
