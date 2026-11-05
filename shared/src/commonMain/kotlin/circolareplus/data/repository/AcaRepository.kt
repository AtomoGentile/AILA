package circolareplus.data.repository

import circolareplus.data.AppContainer
import circolareplus.platform.isExpired
import circolareplus.ai.PdfTextExtractor
import circolareplus.ai.assistant.AssistantActionPlan
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.*
import circolareplus.platform.IncomingContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter

/** Expiring files, transcripts and permissions never enter the indefinite offline cache. */
class AcaRepository(private val api:ApiClient,private val pdf:PdfTextExtractor) {
 suspend fun preferences():AcaPreferencesResponseDto {
  if(api.settings.pendingAiPolicySync)return savePreferences(api.settings.cachedAiPreferences)
  return api.get<AcaPreferencesResponseDto>("/api/aca/preferences",offlineCopy=false).also{api.settings.cachedAiPreferences=it.preferences}
 }
 suspend fun savePreferences(value:AcaPreferencesDto):AcaPreferencesResponseDto {
  api.settings.cachedAiPreferences=value;api.settings.pendingAiPolicySync=true
  api.put<AcaPreferencesDto,AcaPreferencesResponseDto>("/api/aca/preferences",value).also{
   api.settings.cachedAiPreferences=it.preferences;api.settings.pendingAiPolicySync=false
  }
  return preferences()
 }
 suspend fun experience():AcaExperienceDto=api.get("/api/aca/experience",offlineCopy=false)
 suspend fun nudges():List<AcaNudgeDto> = api.get<AcaNudgesResponseDto>("/api/aca/nudges",offlineCopy=false).nudges
 suspend fun dismissNudge(id:String){api.post<Map<String,String>,SuccessDto>("/api/aca/nudges/$id/dismiss",emptyMap())}
 suspend fun task(feature:String,question:String,context:String="",includeAppContext:Boolean=false):AcaAiResultDto=
  api.post("/api/aca/task",AcaTaskRequestDto(feature,question,context,includeAppContext))
 suspend fun upload(content:IncomingContent,threadId:String?=null):ContentAttachmentDto {
  val saved=AppContainer.localContents.save(content)
  val local=content.copy(localId=saved.id)
  val text=content.extractedText?:if(content.mime=="application/pdf")pdf.extractText(content.bytes) else ""
  val response:ContentUploadedDto=api.postMultipart("/api/content",formData {
   append("file",content.bytes,Headers.build {
    append(HttpHeaders.ContentType,content.mime)
    append(HttpHeaders.ContentDisposition,"filename=\"${content.fileName.replace(Regex("[\\r\\n\\\"/\\\\]"),"_")}\"")
   })
   if(threadId!=null)append("threadId",threadId)
   if(text.isNotBlank())append("text",text.take(100_000))
  });AppContainer.localContents.save(local,response.attachment);return response.attachment
 }
 suspend fun attachment(id:String):ContentAttachmentDto=api.get<ContentUploadedDto>("/api/content/$id",offlineCopy=false).attachment
 suspend fun inbox(cursor:String):ContentInboxDto=api.get("/api/content/inbox?cursor=${cursor.encodeURLParameter()}",offlineCopy=false)
 suspend fun downloadFromServer(id:String):ByteArray=api.getBytes("/api/content/$id/file",offlineCopy=false)
 suspend fun download(id:String):ByteArray {
  AppContainer.localContents.findByRemote(id)?.let{return AppContainer.localContents.read(it).bytes}
  val item=attachment(id);check(!item.isExpired()){ "La copia sul server è scaduta e non è stata salvata su questo dispositivo." }
  val bytes=downloadFromServer(id)
  AppContainer.localContents.save(IncomingContent(item.fileName,item.mime,bytes),item)
  return bytes
 }
 suspend fun text(id:String):ContentTextDto=api.get("/api/content/$id/text",offlineCopy=false)
 suspend fun analyze(id:String,action:String,question:String?=null):AcaAiResultDto=api.post("/api/content/$id/ai",ContentAiRequestDto(action,question))
 suspend fun removeAttachment(id:String){api.delete<SuccessDto>("/api/content/$id")}
 suspend fun preview(plan:AssistantActionPlan):AcaOperationDto=api.post("/api/aca/operations",plan)
 suspend fun previewShared(proposal:ForumCalendarProposalDto):AcaOperationDto=api.post("/api/aca/operations",SharedCalendarPreviewRequestDto(proposal.id,proposal.digest))
 suspend fun confirm(id:String,digest:String):AcaOperationDto=api.post("/api/aca/operations/$id/confirm",AcaConfirmationDto(true,digest))
 suspend fun operation(id:String):AcaOperationDto=api.get("/api/aca/operations/$id",offlineCopy=false)
 suspend fun cancel(id:String):AcaOperationDto=api.post("/api/aca/operations/$id/cancel",emptyMap<String,String>())
}

class ForumRepository(private val api:ApiClient) {
 suspend fun calendarProposals(id:String):List<ForumCalendarProposalDto> = api.get<ForumCalendarProposalsDto>("/api/forum/$id/calendar-proposals",offlineCopy=false).proposals
 suspend fun analyzeCalendarProposals(id:String):List<ForumCalendarProposalDto> = api.post<Map<String,String>,ForumCalendarProposalsDto>("/api/forum/$id/calendar-proposals/analyze",emptyMap()).proposals
 suspend fun list(offset:Int=0):ForumListDto=api.get("/api/forum?offset=$offset",offlineCopy=false)
 suspend fun thread(id:String,before:String?=null):ForumSnapshotDto=api.get("/api/forum/$id"+(before?.let{"?before=${it.encodeURLParameter()}"}?:""),offlineCopy=false)
 suspend fun create(title:String,body:String,anonymous:Boolean):ForumSnapshotDto=api.post("/api/forum",ForumCreateRequestDto(title,body,anonymous,true))
 suspend fun reply(id:String,body:String,anonymous:Boolean,parentId:String?,mentions:List<String>,attachments:List<String>):ForumWriteDto=
  api.post("/api/forum/$id/posts",ForumReplyRequestDto(body,anonymous,parentId,mentions,attachments,true))
 suspend fun subscribe(id:String,mode:String){api.put<Map<String,String>,SuccessDto>("/api/forum/$id/subscription",mapOf("mode" to mode))}
 suspend fun state(id:String,resolved:Boolean,featuredPostId:String?){api.put<ForumStateRequestDto,SuccessDto>("/api/forum/$id/state",ForumStateRequestDto(resolved,featuredPostId))}
 suspend fun report(id:String,postId:String?,reason:String){api.post<ForumReportRequestDto,ForumWriteDto>("/api/forum/$id/reports",ForumReportRequestDto(reason,postId))}
 suspend fun reports(id:String):List<ForumReportDto> = api.get<ForumReportsDto>("/api/forum/$id/reports",offlineCopy=false).reports
 suspend fun moderate(id:String,postId:String?,hidden:Boolean,reportId:String?=null){api.put<ForumModerationRequestDto,SuccessDto>("/api/forum/$id/moderation",ForumModerationRequestDto(hidden,postId,reportId))}
 suspend fun ai(id:String,question:String?=null,publish:Boolean=false,replyToId:String?=null):AcaAiResultDto=api.post("/api/forum/$id/ai",ForumAiRequestDto(question,publish,replyToId))
}
