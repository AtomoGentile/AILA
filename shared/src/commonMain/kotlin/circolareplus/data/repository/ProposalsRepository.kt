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
import circolareplus.data.remote.dto.UnlockIdentityRequestDto
import circolareplus.data.remote.dto.UpdateProposalRequestDto
import circolareplus.data.remote.dto.UnlockIdentityResponseDto
import circolareplus.data.remote.dto.VoteProposalRequestDto
import circolareplus.data.remote.dto.VoteProposalResponseDto
import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalComment
import circolareplus.domain.model.ProposalStatus

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

    /** Solo Rappresentante: unico modo per cambiare stato di una proposta. */
    suspend fun changeStatus(proposalId: String, status: ProposalStatus) {
        api.put<ChangeProposalStatusRequestDto, SuccessDto>(
            "/api/proposals/$proposalId/status",
            ChangeProposalStatusRequestDto(status.name)
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

    suspend fun addComment(proposalId: String, content: String) {
        api.post<AddCommentRequestDto, SuccessDto>(
            "/api/proposals/$proposalId/comments",
            AddCommentRequestDto(content)
        )
    }

    /** Quorum: 2 Rappresentanti + 1 Guardia di Sicurezza (Specifica Tecnica v3.0, sez. 3.3). */
    suspend fun unlockIdentity(
        proposalId: String,
        rep1Id: String,
        rep2Id: String,
        securityGuardId: String,
        reason: String
    ): UnlockIdentityResponseDto = api.post(
        "/api/proposals/$proposalId/unlock-identity",
        UnlockIdentityRequestDto(rep1Id, rep2Id, securityGuardId, reason)
    )
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
    upvotes = upVotes,
    downvotes = downVotes,
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
    createdAt = createdAt
)
