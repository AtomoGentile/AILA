package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import circolareplus.ai.*
import circolareplus.ai.assistant.AssistantAgentPhase
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.*
import circolareplus.design.*
import circolareplus.platform.*
import circolareplus.util.today
import kotlinx.coroutines.*

private data class ContentRequest(val action:String,val question:String,val text:String)

/** The file/selection is explicit before any paid or cloud operation starts. */
@Composable
fun ContentAssistanceScreen(
 content:IncomingContent?, attachment:ContentAttachmentDto?, onBack:()->Unit,
 onPrepareCalendar:(String)->Unit,onActivity:(AssistantAgentPhase?)->Unit,onOpenLocalFiles:()->Unit={},
 onUseInChat:((circolareplus.ai.assistant.AssistantAttachedFile)->Unit)?=null,onContextChange:(String)->Unit={}
) {
 val scope=rememberCoroutineScope()
 var activeFile by remember(content,attachment?.id){mutableStateOf(content)}
 var savedCopy by remember(content,attachment?.id){mutableStateOf<circolareplus.data.repository.LocalContentEntry?>(null)}
 var remote by remember(content,attachment?.id){mutableStateOf(attachment)}
 val name=activeFile?.fileName?:savedCopy?.fileName?:remote?.fileName.orEmpty()
 val mime=activeFile?.mime?:remote?.mime.orEmpty()
 var source by remember(content,attachment?.id){mutableStateOf(content?.extractedText?:attachment?.transcript.orEmpty())}
 var transcript by remember(content,attachment?.id){mutableStateOf(content?.isTranscript==true||attachment?.transcript!=null)}
 var preferences by remember{mutableStateOf(AppContainer.settings.cachedAiPreferences)}
 var error by remember{mutableStateOf<String?>(null)}
 var loading by remember(content,attachment?.id){mutableStateOf(true)}
 var busy by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)}
 var result by remember{mutableStateOf<AcaAiResultDto?>(null)};var resultAction by remember{mutableStateOf("")}
 var question by remember{mutableStateOf("")};var consent by remember{mutableStateOf<ContentRequest?>(null)}
 var localOnly by remember{mutableStateOf(AppContainer.settings.aiProvider==AiProvider.ON_DEVICE.id)}
 var page by remember{mutableStateOf(0)}
 val sourceValue=remember(source,page){mutableStateOf(TextFieldValue(source.drop(page*6000).take(6000)))}
 val selected=sourceValue.value.let{if(it.selection.collapsed)""else it.text.substring(it.selection.min,it.selection.max)}
 val caps=remember{acaDeviceCapabilities()}
 val audio=mime.startsWith("audio/");val image=mime.startsWith("image/")
 val unavailable=activeFile==null&&remote?.isExpired()==true
 LaunchedEffect(content,attachment?.id){
  try{preferences=AppContainer.acaRepository.preferences().preferences}
  catch(e:CancellationException){throw e}catch(_:Exception){}
  try{
   onActivity(AssistantAgentPhase.READING)
   val archive=AppContainer.localContents
   savedCopy=content?.localId?.let{archive.entry(it)}?:attachment?.let{archive.findByRemote(it.id)}
   savedCopy?.let{entry->
    if(activeFile==null)activeFile=archive.read(entry)
    source=entry.transcript?:entry.extractedText?:activeFile?.extractedText?:source
    transcript=entry.transcript!=null||activeFile?.isTranscript==true
   }
   if(activeFile==null&&attachment!=null){
    if(attachment.isExpired())throw IllegalStateException("La copia sul server è scaduta. Questo file non è salvato sul dispositivo.")
    val bytes=AppContainer.acaRepository.download(attachment.id)
    savedCopy=archive.findByRemote(attachment.id)
    activeFile=IncomingContent(attachment.fileName,attachment.mime,bytes,localId=savedCopy?.id)
   }
   val file=activeFile
   if(file!=null){
    if(source.isBlank())source=when(file.mime){
     "text/plain"->file.bytes.decodeToString()
     "application/pdf"->AppContainer.pdfTextExtractor.extractText(file.bytes)
     else->""
    }
    if(source.isBlank()&&attachment!=null&&!attachment.isExpired()&&!audio&&!image){
     val text=AppContainer.acaRepository.text(attachment.id);source=text.text;transcript=text.isTranscript
    }
    savedCopy=archive.save(file.copy(localId=savedCopy?.id,extractedText=source.takeIf{it.isNotBlank()},isTranscript=transcript),attachment)
    activeFile=file.copy(localId=savedCopy?.id)
   }
  }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
  finally{loading=false;onActivity(null)}
 }
 DisposableEffect(Unit){onDispose{job?.cancel();onActivity(null)}}
 fun run(request:ContentRequest){
  if(busy||loading)return
  if(activeFile==null&&remote?.isExpired()==true){error="La copia sul server è scaduta e non è presente sul dispositivo.";return}
  val policy=AcaPolicyState.preferences
  val service=if(request.action=="TRANSCRIBE") "AUDIO" else if(image) "IMAGES" else "DOCUMENTS"
  if(!policy.allows(service)){error="Attiva AI, ACA e questo servizio in Impostazioni → AI per continuare.";return}
  busy=true;error=null
  job=scope.launch{
   try{
    onActivity(if(request.action=="TRANSCRIBE")AssistantAgentPhase.TRANSCRIBING else AssistantAgentPhase.WRITING)
    val text=request.text
    if(!image&&request.action!="TRANSCRIBE"&&text.isNotBlank()&&(localOnly||caps.localText&&AppContainer.settings.isLocalAiEnabled)){
     when(val answer=runLocalAcaText(request.question,text)){
      is AiTextResult.Success->{result=AcaAiResultDto(answer.text,answer.modelLabel,today().toIso());resultAction=request.action;return@launch}
      is AiTextResult.Failure->if(localOnly){error=answer.reason;return@launch}
     }
    }
    if(localOnly){error="Questa operazione non è disponibile solo sul dispositivo. Puoi scegliere un passaggio di testo o autorizzare il cloud.";return@launch}
    if(!preferences.cloudConsent||!preferences.attachmentConsent||request.action=="TRANSCRIBE"&&!preferences.audioConsent){consent=request;return@launch}
    val response=if(!image&&request.action!="TRANSCRIBE"&&text.isNotBlank()){
     val capped=text.take(46_000)+(if(text.length>46_000)"\n[Documento troncato: non sono incluse tutte le pagine.]"else"")
     AppContainer.acaRepository.task("DOCUMENT",request.question,"FONTE: $name\n${if(transcript)"TRASCRIZIONE AUTOMATICA\n"else""}$capped")
    }else{
     val item=remote?.takeUnless{it.isExpired()}?:AppContainer.acaRepository.upload(activeFile?.copy(localId=savedCopy?.id)?:throw IllegalStateException("File non disponibile")).also{remote=it}
     AppContainer.acaRepository.analyze(item.id,request.action,request.question)
    }
    result=response;resultAction=request.action
    response.attachment?.let{remote=it;if(it.transcript!=null){source=it.transcript;transcript=true}}
    if(source.isBlank()&&!audio&&!image&&remote!=null&&!remote!!.isExpired())source=AppContainer.acaRepository.text(remote!!.id).text
    activeFile?.let{file->savedCopy=AppContainer.localContents.save(file.copy(localId=savedCopy?.id,extractedText=source.takeIf{it.isNotBlank()},isTranscript=transcript,generatedAt=if(request.action=="TRANSCRIBE")response.generatedAt else file.generatedAt),remote)}
   }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Operazione non completata"}
   finally{busy=false;onActivity(null)}
  }
 }
 LaunchedEffect(source,selected,page){onContextChange("File: $name · pagina di testo ${page+1}\n"+selected.ifBlank{sourceValue.value.text}.take(6000))}
 DisposableEffect(Unit){onDispose{onContextChange("")}}
 fun action(id:String){val prompts=mapOf("SUMMARIZE" to "Riassumi il contenuto usando solo la fonte.","EXPLAIN" to "Spiega questo passaggio con parole semplici.",
  "KEY_POINTS" to "Estrai i punti chiave e i riferimenti alla fonte.","REVIEW_QUESTIONS" to "Crea domande di ripasso basate sul documento.",
  "COMPARE" to "Confronta le sezioni del documento e indica i riferimenti.","EXTRACT_DATES" to "Elenca gli impegni con le date certe presenti nella fonte. Separa quelli senza data o ancora da definire. Non creare eventi.",
  "TRANSCRIBE" to "Trascrivi fedelmente l’audio senza inventare parole.","ASK" to question)
  run(ContentRequest(id,prompts[id].orEmpty(),selected.ifBlank{source}))
 }
 Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).windowInsetsPadding(WindowInsets.safeDrawing)){
  AilaBackBar("Chiedi ad AILA",onBackClick={job?.cancel();onBack()})
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Text(name,style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark)
   Text("${if(audio)"Audio"else if(image)"Immagine"else"Documento"} · ${((activeFile?.bytes?.size?.toLong()?:remote?.bytes?:0)/1024)} KB",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
   Text(if(selected.isBlank())"Contesto: il contenuto aperto."else"Contesto: il passaggio selezionato (${selected.length} caratteri).",color=AppTheme.TextMuted)
   OutlinedButton(onClick={scope.launch{try{val bytes=activeFile?.bytes?:AppContainer.acaRepository.download(remote!!.id);openContentFile(bytes,name,mime)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}},enabled=!loading&&!unavailable){Text(if(audio)"Ascolta l’originale"else"Apri l’originale")}
   if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
   savedCopy?.let{Text("Copia salvata sul dispositivo · resta finché non la elimini manualmente.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted);TextButton(onClick=onOpenLocalFiles,enabled=!busy){Text("Gestisci le copie sul dispositivo")}}
   if(onUseInChat!=null){
    val chatText=selected.ifBlank{source}.ifBlank{if(image)result?.text.orEmpty()else ""}
    OutlinedButton(enabled=!loading&&!busy&&activeFile!=null&&chatText.isNotBlank(),onClick={scope.launch{
     try{
      val file=activeFile?:return@launch
      // A selected passage is a separate local source; it must not replace the full archive text.
      val selectedOnly=selected.isNotBlank()
      val chosen=if(selectedOnly)IncomingContent("Passaggio · $name.txt","text/plain",chatText.encodeToByteArray(),chatText,isTranscript=transcript)
       else file.copy(localId=savedCopy?.id,extractedText=chatText,isTranscript=transcript)
      val saved=AppContainer.localContents.save(chosen,if(selectedOnly)null else remote)
      onUseInChat(circolareplus.ai.assistant.AssistantAttachedFile(saved.id,saved.fileName,if(audio)"AUDIO"else if(image)"IMAGES"else"DOCUMENTS",transcript||image))
     }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
    }}){Text(if(selected.isNotBlank())"Allega il passaggio alla chat"else"Allega alla chat")}
    if(audio&&chatText.isBlank())Text("Trascrivi il vocale per usarlo nella chat. L’originale resta disponibile.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
    if(image&&chatText.isBlank())Text("Analizza l’immagine per usare la descrizione nella chat. La descrizione AI può contenere errori.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
   }
   if(remote?.isExpired()==true)Text(if(activeFile!=null)"La copia sul server è scaduta. Puoi continuare a usare la copia su questo dispositivo."else"Copia sul server scaduta. I messaggi della discussione restano disponibili.",color=AppTheme.TextMuted)
   if(source.isNotBlank()){
    if(transcript)Text("Trascrizione automatica · verifica sempre con l’audio originale",style=MaterialTheme.typography.labelLarge,color=AppTheme.PrimaryBlue)
    OutlinedTextField(value=sourceValue.value,onValueChange={sourceValue.value=it},readOnly=true,
     label={Text(if(transcript)"Trascrizione"else"Testo del documento: seleziona un passaggio")},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=9)
    if(selected.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     TextButton(onClick={action("EXPLAIN")},enabled=!busy&&!loading){Text("Spiega la selezione")}
     TextButton(onClick={question="Spiega il passaggio selezionato"},enabled=!busy&&!loading){Text("Chiedi sulla selezione")}
    }
    if(source.length>6000)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
     TextButton(onClick={page--},enabled=page>0&&!busy){Text("Precedente")};Text("Parte ${page+1}/${(source.length+5999)/6000}",color=AppTheme.TextMuted)
     TextButton(onClick={page++},enabled=(page+1)*6000<source.length&&!busy){Text("Successiva")}
    }
   }
   Row(verticalAlignment=Alignment.CenterVertically){Text("Solo sul dispositivo",Modifier.weight(1f),color=AppTheme.TextDark);Switch(localOnly,{localOnly=it},enabled=!busy&&!loading)}
   if(localOnly&&!caps.localText)Text(caps.reason.orEmpty(),style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
   if(audio&&source.isBlank())Button(onClick={action("TRANSCRIBE")},enabled=!busy&&!loading&&!unavailable,modifier=Modifier.fillMaxWidth()){Text("Trascrivi il vocale")}
   if(!audio||source.isNotBlank()){
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={action("SUMMARIZE")},enabled=!busy&&!loading){Text("Riassumi")};OutlinedButton(onClick={action("KEY_POINTS")},enabled=!busy&&!loading){Text("Punti chiave")}}
    if(!image)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={action("REVIEW_QUESTIONS")},enabled=!busy&&!loading){Text("Domande di ripasso")};TextButton(onClick={action("COMPARE")},enabled=!busy&&!loading){Text("Confronta sezioni")}}
    OutlinedButton(onClick={action("EXTRACT_DATES")},enabled=!busy&&!loading,modifier=Modifier.fillMaxWidth()){Text("Estrai attività e date")}
   }
   OutlinedTextField(question,{question=it},label={Text("La tua domanda sul contenuto")},modifier=Modifier.fillMaxWidth(),enabled=!busy&&!loading,maxLines=4)
   Button(onClick={action("ASK")},enabled=!busy&&!loading&&question.isNotBlank()&&!unavailable,modifier=Modifier.fillMaxWidth()){Text("Chiedi ad AILA")}
   if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text("Interrompi")}}
   error?.let{Text(it,color=AppTheme.TintRedInk)}
   result?.let{answer->
    HorizontalDivider(color=AppTheme.Hairline)
    Text("Assistenza AI · ${answer.provider} · ${answer.generatedAt}",style=MaterialTheme.typography.labelMedium,color=AppTheme.TextMuted)
    SelectionContainer{Text(answer.text,color=AppTheme.TextDark,style=MaterialTheme.typography.bodyLarge)}
    if(resultAction=="EXTRACT_DATES")Button(onClick={onPrepareCalendar(answer.text)},enabled=!busy&&!loading,modifier=Modifier.fillMaxWidth()){Text("Prepara gli eventi da confermare")}
   }
  }
 }
 consent?.let{request->AlertDialog(onDismissRequest={consent=null},title={Text("Elaborare questo contenuto nel cloud?")},
  text={Text("AILA userà ${if(request.text.isNotBlank())"il testo selezionato o estratto"else"il file $name"} per questa richiesta. ${if(request.action=="TRANSCRIBE")"L’audio sarà trascritto automaticamente. "else""}Sul server i file fino a 3 MB restano 90 giorni, quelli più grandi 7 giorni. Le copie salvate sul dispositivo restano fino alla cancellazione manuale. Puoi modificare i consensi in Impostazioni → AI.")},
  confirmButton={TextButton(onClick={consent=null;scope.launch{try{preferences=AppContainer.acaRepository.savePreferences(preferences.copy(cloudConsent=true,attachmentConsent=true,audioConsent=preferences.audioConsent||request.action=="TRANSCRIBE")).preferences;run(request)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}}}){Text("Consenti e continua")}},
  dismissButton={TextButton(onClick={consent=null}){Text("Annulla")}})}
}
