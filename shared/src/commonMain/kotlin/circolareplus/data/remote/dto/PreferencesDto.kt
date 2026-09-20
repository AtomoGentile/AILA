package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesConfigDto(
    val preferencesOpen: Boolean,
    val classLabel: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class SetPreferencesConfigRequestDto(val open: Boolean)

@Serializable
data class VoteSocialPreferenceRequestDto(val toStudentId: String, val score: Int)

@Serializable
data class MyPreferenceVoteDto(
    val toStudentId: String,
    val toName: String,
    val score: Int,
    val updatedAt: String
)

@Serializable
data class MyPreferencesResponseDto(val votes: List<MyPreferenceVoteDto>)

@Serializable
data class PreferenceMatrixEntryDto(val from: String, val to: String, val score: Int)

@Serializable
data class PreferenceMatrixResponseDto(val matrix: List<PreferenceMatrixEntryDto>)

@Serializable
data class PendingVoterDto(val id: String, val firstName: String, val lastName: String)

/**
 * Avanzamento della raccolta preferenze: quanti compagni hanno votato e quanti no. L'elenco
 * `pending` lo riceve solo il Rappresentante (per gli altri e' vuoto).
 */
@Serializable
data class PreferencesProgressDto(
    val totalStudents: Int = 0,
    val votedCount: Int = 0,
    val pendingCount: Int = 0,
    val allVoted: Boolean = false,
    val pending: List<PendingVoterDto> = emptyList()
)
