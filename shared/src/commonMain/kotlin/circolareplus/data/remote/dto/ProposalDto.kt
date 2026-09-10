package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProposalDto(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: String,
    val isAnonymous: Boolean,
    val authorId: String? = null,
    val authorName: String? = null,
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
data class ChangeProposalStatusRequestDto(val status: String)

@Serializable
data class ProposalCommentDto(
    val id: String,
    val userId: String,
    val authorName: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class ProposalCommentsListResponseDto(val comments: List<ProposalCommentDto>)

@Serializable
data class AddCommentRequestDto(val content: String)

@Serializable
data class UnlockIdentityRequestDto(
    val rep1Id: String,
    val rep2Id: String,
    val securityGuardId: String,
    val reason: String
)

@Serializable
data class UnlockIdentityResponseDto(
    val success: Boolean,
    val unlockedAt: String,
    val author: UserDto? = null
)
