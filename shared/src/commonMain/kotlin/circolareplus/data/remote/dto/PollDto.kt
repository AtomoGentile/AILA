package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PollSummaryDto(
    val id: String,
    val subject: String,
    val isPublished: Boolean,
    val createdAt: String,
    val totalStudents: Int = 0,
    val submittedCount: Int = 0,
    val isCalculated: Boolean = false,
    /** Destinatari scelti dal Rappresentante; null = tutta la classe. */
    val audienceUserIds: List<String>? = null,
    /** False solo per il Rappresentante quando il sondaggio non è rivolto a lui. */
    val isTarget: Boolean = true
)

@Serializable
data class PollsListResponseDto(val polls: List<PollSummaryDto>)

@Serializable
data class SlotCountsDto(val green: Int, val yellow: Int, val redLight: Int, val redDark: Int)

@Serializable
data class PollSlotDto(
    val id: String,
    val slotDate: String,
    val capacity: Int,
    val teacherMandatory: Boolean,
    val counts: SlotCountsDto,
    val myVote: Int? = null
)

@Serializable
data class PollDetailDto(
    val id: String,
    val subject: String,
    val isPublished: Boolean,
    val createdAt: String,
    val mySacrificeBonus: Int,
    val slots: List<PollSlotDto>,
    val audienceUserIds: List<String>? = null,
    val isTarget: Boolean = true
)

@Serializable
data class CreatePollSlotRequestDto(val slotDate: String, val capacity: Int, val teacherMandatory: Boolean = false)

@Serializable
data class CreatePollRequestDto(
    val subject: String,
    val slots: List<CreatePollSlotRequestDto>,
    /** Chi deve essere interrogato; null = tutta la classe. */
    val audienceUserIds: List<String>? = null
)

@Serializable
data class VotePollSlotRequestDto(val slotId: String, val voteScore: Int? = null)

@Serializable
data class PollAssignmentDto(
    val id: String? = null,
    val slotId: String,
    val slotDate: String? = null,
    val studentId: String,
    val studentName: String? = null,
    val score: Int? = null,
    val assignedAt: String? = null
)

@Serializable
data class PollAssignmentsResponseDto(val subject: String? = null, val assignments: List<PollAssignmentDto>)

@Serializable
data class RunAssignmentsResponseDto(val success: Boolean, val assignmentCount: Int, val assignments: List<PollAssignmentDto>)

@Serializable
data class SwapRequestDto(val targetStudentId: String)

@Serializable
data class ConfirmSwapRequestDto(val accept: Boolean)
