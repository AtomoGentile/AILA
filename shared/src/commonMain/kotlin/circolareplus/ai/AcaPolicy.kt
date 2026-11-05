package circolareplus.ai

import androidx.compose.runtime.*
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.AcaPreferencesDto
import circolareplus.domain.model.*

object AcaPolicyState { var preferences by mutableStateOf(AcaPreferencesDto()) }
fun AcaPreferencesDto.allows(service:String,needsAca:Boolean=true)=aiEnabled&&(!needsAca||acaEnabled)&&service in services

/** Every individual provider checks live consent, including a fallback after settings change. */
class PolicyAiClassifier(private val delegate:AiClassifier,private val kind:String):AiClassifier {
 private fun denied(service:String,aca:Boolean):String? {
  val p=AppContainer.settings.cachedAiPreferences
  if(!p.aiEnabled)return "L’AI è disattivata. Puoi attivarla in Impostazioni → AI."
  if(aca&&!p.acaEnabled)return "ACA è opzionale ed è disattivata. Puoi attivarla in Impostazioni → AI."
  if(service !in p.services)return "Questo servizio AI è disattivato in Impostazioni → AI."
  if(kind!="LOCAL"&&!p.cloudConsent)return "L’elaborazione cloud non è autorizzata. Controlla Impostazioni → AI."
  if(kind=="EXTERNAL"&&!p.externalConsent)return "Il provider esterno non è autorizzato. Controlla Impostazioni → AI."
  return null
 }
 override suspend fun classifyCircularText(circularNumber:Int,circularTitle:String,pdfText:String,studentContext:String):CircularAiClassification {
  val reason=denied("CIRCULARS",false)
  return if(reason==null)delegate.classifyCircularText(circularNumber,circularTitle,pdfText,studentContext)
   else CircularAiClassification(circularNumber,CircularRelevanceBadge.POTENTIAL,"",isFallback=true,modelLabel=reason)
 }
 override suspend fun parseEventPrompt(userPrompt:String):EventDraft {
  if(denied("CALENDAR",true)!=null)return EventDraft("","","ALTRO",null,null,null)
  return delegate.parseEventPrompt(userPrompt)
 }
 override suspend fun generateAnswer(prompt:AiPromptBuilder):AiTextResult {
  for(required in prompt.attachmentServices){
   denied(required,true)?.let{return AiTextResult.Failure(it)}
  }
  if(kind!="LOCAL"&&prompt.attachmentServices.isNotEmpty()){
   val p=AppContainer.settings.cachedAiPreferences
   if(!p.attachmentConsent)return AiTextResult.Failure("Consenti l’analisi degli allegati in Impostazioni → AI.")
   if("AUDIO" in prompt.attachmentServices&&!p.audioConsent)return AiTextResult.Failure("Consenti l’uso delle trascrizioni audio in Impostazioni → AI.")
  }
  val service=when(prompt.feature){AiFeature.CHAT->"CHAT";AiFeature.AGENT->"CALENDAR";AiFeature.STUDY_PLAN->"STUDY_PLAN";AiFeature.PERABOT->"FORUM";AiFeature.TRANSCRIBE->"AUDIO";AiFeature.IMAGE->"IMAGES";else->"DOCUMENTS"}
  val reason=denied(service,prompt.feature!=AiFeature.CHAT)
  return if(reason!=null)AiTextResult.Failure(reason)else delegate.generateAnswer(prompt)
 }
 override suspend fun testConfiguration():String=denied("CHAT",false)?:delegate.testConfiguration()
}
