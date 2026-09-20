package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProposalDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: String,
    val outcome: String? = null,
    val isAnonymous: Boolean,
    val authorId: String? = null,
    val authorName: String? = null,
    val identityRevealed: Boolean = false,
    val unlockRequestStatus: String? = null,
    val modifiedByRep: Boolean = false,
    val upVotes: Int = 0,
    val downVotes: Int = 0,
    val commentCount: Int = 0,
    val myVote: Int? = null,
    val createdAt: String
)

@Serializable
data class ProposalsListResponseDto(val proposals: List<ProposalDto>)

@Serializable
data class CreateProposalRequestDto(
    val title: String,
    val description: String,
    val category: String = "GENERALE",
    val isAnonymous: Boolean = false
)

@Serializable
data class VoteProposalRequestDto(val voteType: Int)

@Serializable
data class VoteProposalResponseDto(val success: Boolean, val upVotes: Int, val downVotes: Int)

@Serializable
data class ChangeProposalStatusRequestDto(val status: String, val outcome: String? = null)

@Serializable
data class ProposalCommentDto(
    val id: String,
    val userId: String = "",
    val authorName: String,
    val content: String,
    val createdAt: String,
    val isAnonymous: Boolean = false,
    val isMine: Boolean = false,
    val identityRevealed: Boolean = false,
    val unlockRequestStatus: String? = null
)

@Serializable
data class ProposalCommentsListResponseDto(val comments: List<ProposalCommentDto>)

@Serializable
data class AddCommentRequestDto(val content: String, val isAnonymous: Boolean = false)

/** commentId null = si chiede l'autore della proposta, valorizzato = quello di un commento. */
@Serializable
data class CreateUnlockRequestDto(val reason: String, val commentId: String? = null)

@Serializable
data class UnlockRequestDto(
    val id: String,
    val proposalId: String,
    val proposalTitle: String,
    val commentId: String? = null,
    val commentExcerpt: String? = null,
    val reason: String,
    val requestedByName: String,
    val createdAt: String,
    val representativeApprovals: Int = 0,
    val guardApprovals: Int = 0,
    val representativesNeeded: Int = 2,
    val guardsNeeded: Int = 1,
    val approvedByMe: Boolean = false,
    val approverNames: List<String> = emptyList()
)

@Serializable
data class UnlockRequestsListResponseDto(
    val canSign: Boolean = false,
    val requests: List<UnlockRequestDto> = emptyList()
)

/** userId null = revoca la Guardia di Sicurezza della classe. */
@Serializable
data class SetSecurityGuardRequestDto(val userId: String? = null)

@Serializable
data class ApproveUnlockResponseDto(
    val success: Boolean = true,
    /** "APPROVED" quando questa firma ha completato il quorum, altrimenti "PENDING". */
    val status: String = "PENDING",
    val representativeApprovals: Int = 0,
    val guardApprovals: Int = 0
)

/** Corpo vuoto per le POST che non portano dati (approva/rifiuta). */
@Serializable
class EmptyBodyDto
