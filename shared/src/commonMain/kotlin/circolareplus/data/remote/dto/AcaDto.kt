package circolareplus.data.remote.dto

import circolareplus.ai.assistant.AssistantActionPlan
import kotlinx.serialization.Serializable

@Serializable data class AcaPreferencesDto(
 val aiEnabled:Boolean=false,val acaEnabled:Boolean=false,val holdToOpen:Boolean=true,val services:List<String> = listOf("CIRCULARS","CHAT"),
 val cloudConsent:Boolean=false,val externalConsent:Boolean=false,val attachmentConsent:Boolean=false,
 val audioConsent:Boolean=false,val nudgesEnabled:Boolean=false,val advancedEnabled:Boolean=false
)
@Serializable data class AcaCapabilitiesDto(val cloud:Boolean=false,val advanced:Boolean=false,val advancedProvider:String?=null,
 val transcription:Boolean=false,val imageAnalysis:Boolean=false,val localModels:String="")
@Serializable data class AcaPreferencesResponseDto(val preferences:AcaPreferencesDto=AcaPreferencesDto(),val capabilities:AcaCapabilitiesDto=AcaCapabilitiesDto())
@Serializable data class AcaExperienceDto(val id:String="aca-pere-2026",val title:String="La tua classe. Un passo avanti.",
 val subtitle:String="AILA ACA ed Educazione tra pere",val videoUrl:String?=null,val posterUrl:String?=null)
@Serializable data class AcaNudgeDto(val id:String,val eventId:String,val eventDate:String,val title:String,val message:String,val suggestion:String,val canPlanStudy:Boolean=false)
@Serializable data class AcaNudgesResponseDto(val nudges:List<AcaNudgeDto> = emptyList())
@Serializable data class AcaAiResultDto(val text:String,val provider:String,val generatedAt:String,val feature:String="DOCUMENT",
 val sources:List<ForumAiSourceDto> = emptyList(),val postId:String?=null,val attachment:ContentAttachmentDto?=null)
@Serializable data class ForumAiSourceDto(val postId:String,val label:String)
@Serializable data class ContentAttachmentDto(val id:String,val fileName:String,val mime:String,val bytes:Long,val expiresAt:String,
 val expired:Boolean=false,val durationSeconds:Double?=null,val transcript:String?=null,val transcriptGeneratedAt:String?=null)
@Serializable data class ContentUploadedDto(val attachment:ContentAttachmentDto)
@Serializable data class ContentTextDto(val text:String,val isTranscript:Boolean=false,val generatedAt:String?=null)
@Serializable data class ForumParticipantDto(val key:String,val label:String,val isMe:Boolean=false,val isOp:Boolean=false,val anonymous:Boolean=false)
@Serializable data class ForumThreadDto(val id:String,val title:String,val resolved:Boolean=false,val featuredPostId:String?=null,
 val replies:Int=0,val createdAt:String="",val lastMessageAt:String="",val isAuthor:Boolean=false,val needsResolution:Boolean=false)
@Serializable data class ForumListDto(val threads:List<ForumThreadDto> = emptyList(),val hasMore:Boolean=false)
@Serializable data class ForumPostDto(val id:String,val parentId:String?=null,val body:String,val author:ForumParticipantDto,
 val mentions:List<ForumParticipantDto> = emptyList(),val isAi:Boolean=false,val generatedAt:String?=null,val provider:String?=null,
 val hidden:Boolean=false,val createdAt:String="",val attachments:List<ContentAttachmentDto> = emptyList())
@Serializable data class ForumSnapshotDto(val thread:ForumThreadDto,val participants:List<ForumParticipantDto> = emptyList(),
 val posts:List<ForumPostDto> = emptyList(),val hasMore:Boolean=false,val nextBefore:String?=null,val notificationMode:String="REPLIES",val myIdentity:ForumParticipantDto?=null)
@Serializable data class ForumWriteDto(val id:String,val invokePerabot:Boolean=false)
@Serializable data class ForumCreateRequestDto(val title:String,val body:String,val anonymous:Boolean,val forumConsent:Boolean)
@Serializable data class ForumReplyRequestDto(val body:String,val anonymous:Boolean,val parentId:String?=null,val mentions:List<String> = emptyList(),
 val attachmentIds:List<String> = emptyList(),val forumConsent:Boolean)
@Serializable data class ForumStateRequestDto(val resolved:Boolean,val featuredPostId:String?)
@Serializable data class ForumReportRequestDto(val reason:String,val postId:String?=null)
@Serializable data class ForumModerationRequestDto(val hidden:Boolean,val postId:String?=null,val reportId:String?=null)
@Serializable data class ForumReportDto(val id:String,val postId:String?=null,val reason:String,val status:String,val createdAt:String)
@Serializable data class ForumReportsDto(val reports:List<ForumReportDto> = emptyList())
@Serializable data class ForumAiRequestDto(val question:String?=null,val publish:Boolean=false,val replyToId:String?=null,val localOnly:Boolean=false)
@Serializable data class AcaTaskRequestDto(val feature:String,val question:String,val context:String="",val includeAppContext:Boolean=false,val localOnly:Boolean=false)
@Serializable data class ContentAiRequestDto(val action:String,val question:String?=null,val localOnly:Boolean=false)
@Serializable data class AcaOperationStepDto(val step:Int,val tool:String,val eventId:String?=null,val status:String)
@Serializable data class AcaOperationDto(val id:String,val status:String,val expiresAt:String,val digest:String,
 val plan:AssistantActionPlan,val results:List<AcaOperationStepDto> = emptyList(),val text:String="")
@Serializable data class AcaConfirmationDto(val confirmed:Boolean,val digest:String)

@Serializable data class ContentDeliveryDto(val attachment:ContentAttachmentDto,val cursor:String)
@Serializable data class ContentInboxDto(val attachments:List<ContentDeliveryDto> = emptyList(),val hasMore:Boolean=false,val classId:String)
@Serializable data class ForumCalendarProposalDto(val id:String,val threadId:String,val status:String,val plan:AssistantActionPlan,
 val digest:String,val sourcePostIds:List<String> = emptyList(),val provider:String="",val createdAt:String="",val expiresAt:String="",val acceptedAt:String?=null)
@Serializable data class ForumCalendarProposalsDto(val proposals:List<ForumCalendarProposalDto> = emptyList())
@Serializable data class SharedCalendarPreviewRequestDto(val sharedProposalId:String,val digest:String)
