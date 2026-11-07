package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.ai.AcaPolicyState
import circolareplus.ai.allows
import circolareplus.ai.assistant.AssistantAgentPhase
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.*
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(
 isModerator:Boolean,onBack:()->Unit,onOpenAttachment:(ContentAttachmentDto)->Unit,
 onPrepareCalendar:(String)->Unit,onActivity:(AssistantAgentPhase?)->Unit,
 initialThreadId:String?=null,inline:Boolean=false,isActive:Boolean=true,onCalendarChanged:()->Unit={},onContextChange:(String)->Unit={}
) {
 val scope=rememberCoroutineScope();val repo=AppContainer.forumRepository
 var manualPhase by remember{mutableStateOf<AssistantAgentPhase?>(null)}
 var automaticPhase by remember{mutableStateOf<AssistantAgentPhase?>(null)}
 fun activity(phase:AssistantAgentPhase?){manualPhase=phase}
 LaunchedEffect(manualPhase,automaticPhase){onActivity(manualPhase?:automaticPhase)}
 val policy=AcaPolicyState.preferences
 var calendarProposals by remember{mutableStateOf<List<ForumCalendarProposalDto>>(emptyList())}
 var confirmingProposal by remember{mutableStateOf<ForumCalendarProposalDto?>(null)}
 var proposalError by remember{mutableStateOf<String?>(null)}

 var threadFilter by rememberSaveable { mutableStateOf("ALL") }
 var threads by remember{mutableStateOf<List<ForumThreadDto>>(emptyList())};var moreThreads by remember{mutableStateOf(false)}
 var threadId by rememberSaveable(initialThreadId){mutableStateOf(initialThreadId)}
 var snapshot by remember{mutableStateOf<ForumSnapshotDto?>(null)}
 var focusPostId by rememberSaveable{mutableStateOf<String?>(null)}
 var replyTo by remember{mutableStateOf<ForumPostDto?>(null)}
 var body by rememberSaveable{mutableStateOf("")};var anonymous by rememberSaveable{mutableStateOf(true)}
 var rulesAccepted by rememberSaveable{mutableStateOf(false)};var showRules by remember{mutableStateOf(false)}
 var creating by remember{mutableStateOf(false)};var createTitle by remember{mutableStateOf("")};var createBody by remember{mutableStateOf("")}
 var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
 var listLoading by remember{mutableStateOf(true)}
 var notificationDialog by remember{mutableStateOf(false)};var mentionsDialog by remember{mutableStateOf(false)}
 var menuPost by remember{mutableStateOf<ForumPostDto?>(null)};var reportPost by remember{mutableStateOf<ForumPostDto?>(null)}
 var reportReason by remember{mutableStateOf("")};var reports by remember{mutableStateOf<List<ForumReportDto>?>(null)}
 var summary by remember{mutableStateOf<AcaAiResultDto?>(null)};var consentRequest by remember{mutableStateOf<ForumAiRequestDto?>(null)}
 var pendingAttachments by remember{mutableStateOf<List<ContentAttachmentDto>>(emptyList())};var recording by remember{mutableStateOf(false)}
 suspend fun refresh(older:Boolean=false){
  val id=threadId
  if(id==null){val result=repo.list(filter=threadFilter);if(result.threads!=threads)threads=result.threads;moreThreads=result.hasMore;return}
  val next=repo.thread(id,if(older)snapshot?.nextBefore else null)
  try{val proposals=repo.calendarProposals(id);if(threadId==id&&calendarProposals!=proposals)calendarProposals=proposals}
  catch(e:CancellationException){throw e}catch(_:Exception){}

  if(threadId!=id)return
  val previous=snapshot?.takeIf{it.thread.id==id}
  val refreshed=if(previous==null)next else next.copy(posts=(previous.posts+next.posts).associateBy{it.id}.values
   .sortedWith(compareBy<ForumPostDto>{it.id!=id}.thenBy{it.createdAt}.thenBy{it.id}),
   hasMore=if(older)next.hasMore else previous.hasMore||next.hasMore,
   nextBefore=if(older)next.nextBefore else previous.nextBefore?:next.nextBefore)
  if(snapshot!=refreshed)snapshot=refreshed
 }
 fun launchWork(block:suspend()->Unit){if(busy)return;busy=true;error=null;scope.launch{try{block()}
  catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Operazione non completata"}finally{busy=false;activity(null)}}}
 LaunchedEffect(threadId,threadFilter){calendarProposals=emptyList();proposalError=null;confirmingProposal=null;snapshot=null;focusPostId=null;replyTo=null;body="";pendingAttachments=emptyList();error=null;listLoading=true
  try{refresh()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{listLoading=false}
  while(isActive){delay(15_000);if(AppForeground.isForeground)try{refresh()}catch(e:CancellationException){throw e}catch(_:Exception){}}
 }
 DisposableEffect(Unit){onDispose{onActivity(null)}}
 val automaticCloudAllowed=policy.cloudConsent&&AppContainer.settings.aiProvider!=circolareplus.ai.AiProvider.ON_DEVICE.id
 LaunchedEffect(threadId,snapshot?.posts?.map{it.id to it.hidden},policy.aiEnabled,policy.acaEnabled,policy.services,automaticCloudAllowed){
  val id=threadId
  if(id!=null&&snapshot!=null&&policy.allows("FORUM_SUGGESTIONS")&&automaticCloudAllowed){
   delay(1500)
   try{automaticPhase=AssistantAgentPhase.PLANNING;proposalError=null
    val result=repo.analyzeCalendarProposals(id);if(threadId==id)calendarProposals=result
   }catch(e:CancellationException){throw e}catch(e:Exception){proposalError="Proposte automatiche non disponibili: ${e.message.orEmpty()}"}
   finally{automaticPhase=null}
  }
 }
 val addFile:(IncomingContent)->Unit={file->if(threadId!=null&&pendingAttachments.size<5)launchWork{
  activity(AssistantAgentPhase.READING);pendingAttachments=pendingAttachments+AppContainer.acaRepository.upload(file,threadId,persistLocal=false)
 }else error="Apri una discussione e allega al massimo 5 file per risposta."}
 val pickFile=rememberContentPicker({error=it},addFile)
 val recorder=rememberVoiceRecorder({recording=it},{error=it},addFile)
 fun askAi(request:ForumAiRequestDto){launchWork{
  val p=AppContainer.acaRepository.preferences().preferences
  if(!p.aiEnabled||!p.acaEnabled||"FORUM" !in p.services){error="Attiva AI, ACA e il servizio Forum in Impostazioni → AI.";return@launchWork}
  if(!p.cloudConsent){consentRequest=request;return@launchWork}
  activity(AssistantAgentPhase.WRITING)
  val result=repo.ai(threadId?:return@launchWork,request.question,request.publish,request.replyToId)
  if(request.publish)refresh()else summary=result
 }}
 confirmingProposal?.let{proposal->AlertDialog(
  onDismissRequest={confirmingProposal=null},title={Text("Aggiungere al calendario della classe?")},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   proposal.plan.actions.forEach{event->Text("${event.title}\n${event.date}${event.time?.let{" · $it"}.orEmpty()} · ${event.category}\n${event.notes.orEmpty()}")}
   Text("Visibile a tutta la classe. La prima conferma salva gli eventi e aggiorna la proposta per tutti.")
  }},
  confirmButton={TextButton(enabled=!busy,onClick={confirmingProposal=null;launchWork{
   activity(AssistantAgentPhase.EXECUTING)
   try{val operation=AppContainer.acaRepository.previewShared(proposal)
    AppContainer.acaRepository.confirm(operation.id,operation.digest)
   }catch(e:CancellationException){throw e}catch(e:Exception){
    val current=repo.calendarProposals(proposal.threadId);calendarProposals=current
    if(current.none{it.id==proposal.id&&it.status=="ACCEPTED"})throw e
   }
   onCalendarChanged();refresh()
  }}){Text("Conferma per la classe")}},
  dismissButton={TextButton(onClick={confirmingProposal=null}){Text("Annulla")}}
 )}
 LaunchedEffect(snapshot?.posts,focusPostId){
  onContextChange(snapshot?.let{snap->"Discussione: ${snap.thread.title}\n"+snap.posts.filter{!it.hidden}.takeLast(30).joinToString("\n"){"${it.author.label}: ${it.body}"}.take(6000)}.orEmpty())
 }
 DisposableEffect(Unit){onDispose{onContextChange("")}}
 val goBack:()->Unit={when{focusPostId!=null->focusPostId=null;threadId!=null->{threadId=null;snapshot=null};else->onBack()}}
 PlatformBackHandler(enabled=isActive&&(!inline||threadId!=null||focusPostId!=null)){goBack()}
  Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).then(if(inline)Modifier.padding(bottom=LocalBottomBarPadding.current) else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)).imePadding()){
  if(!inline || threadId!=null)AilaBackBar(title=when{focusPostId!=null->"Conversazione";threadId!=null->"Revisione tra pere";else->"Revisione tra pere"},onBackClick=goBack,
   action={if(threadId!=null)AilaIconButton("Preferenze della discussione",{notificationDialog=true}){tint->AppIcons.Bell(color=tint)}})
  error?.let{Text(it,Modifier.padding(16.dp),color=AppTheme.TintRedInk)}
  if(threadId==null){
   LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item { ForumIntroduction(busy, onCreate = { creating = true }, onRules = { showRules = true }) }
    item {
     val filters=listOf("ALL","OPEN","RESOLVED","MINE")
     AilaFilterBar(labels=listOf("Tutte","Aperte","Risolte","Le mie"),selectedIndex=filters.indexOf(threadFilter),
      onSelect={if(threadFilter!=filters[it]) {threadFilter=filters[it];threads=emptyList();moreThreads=false}})
    }
    item { Spacer(Modifier.height(4.dp)) }
    if(threads.isEmpty()&&!busy&&!listLoading) item {
     if(threadFilter=="ALL") ForumEmptyState()
     else ForumEmptyState(title="Nessuna discussione qui",description="Non ci sono discussioni con questo filtro. Prova un’altra categoria.")
    }
    items(threads,key={it.id}){t->AilaCard(onClick={threadId=t.id}){
     Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Text(t.title,style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
      Text("${t.replies} risposte · ${if(t.resolved)"Risolta"else"Aperta"}"+(if(t.featuredPostId!=null)" · Risposta in evidenza"else""),color=AppTheme.TextMuted,style=MaterialTheme.typography.bodySmall)
      if(t.needsResolution)Text("Sei ancora qui? Puoi indicare se la domanda è risolta.",color=AppTheme.PrimaryBlue,style=MaterialTheme.typography.bodySmall)
     }
    }}
    if(moreThreads)item{TextButton(onClick={launchWork{val next=repo.list(threads.size,filter=threadFilter);threads=(threads+next.threads).distinctBy{it.id};moreThreads=next.hasMore}}){Text("Altre discussioni")}}
   }
  }else{
   val snap=snapshot
   if(snap!=null){
    val descendants=remember(snap.posts,focusPostId){
     val ids=mutableSetOf<String>();focusPostId?.let{ids+=it};var changed=true
     while(changed){changed=false;snap.posts.forEach{if(it.parentId in ids&&ids.add(it.id))changed=true}};ids
    }
    val visible=snap.posts.filter{if(focusPostId!=null)it.id in descendants else it.id==snap.thread.id||it.parentId==snap.thread.id}
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item{
      Text(snap.thread.title,style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark)
      Text(if(snap.thread.resolved)"Risolta · puoi continuare a consultare e aggiungere risposte"else"Discussione aperta",color=AppTheme.TextMuted)
      if(snap.thread.needsResolution)Text("Sei ancora qui? Se hai trovato la risposta, puoi segnare la discussione come risolta.",color=AppTheme.PrimaryBlue)
      Row{TextButton(onClick={askAi(ForumAiRequestDto())},enabled=!busy){Text("Riassumi")}
       if(snap.thread.isAuthor||isModerator)TextButton(onClick={launchWork{repo.state(snap.thread.id,!snap.thread.resolved,snap.thread.featuredPostId);refresh()}},enabled=!busy){Text(if(snap.thread.resolved)"Riapri"else"Segna risolta")}
       if(isModerator)TextButton(onClick={launchWork{reports=repo.reports(snap.thread.id)}},enabled=!busy){Text("Segnalazioni")}
      }
      if(focusPostId!=null)TextButton(onClick={focusPostId=null}){Text("Tutta la discussione")}
     }
     if(focusPostId==null){
      if(automaticPhase!=null)item{Text("ACA cerca impegni nella conversazione…",color=AppTheme.PrimaryBlue)}
      calendarProposals.filter{it.status!="STALE"}.forEach{proposal->item(key="calendar-${proposal.id}"){
       AilaCard{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(if(proposal.status=="ACCEPTED")"Già aggiunto al calendario"else"ACA propone un impegno per la classe",style=MaterialTheme.typography.titleMedium,color=AppTheme.PrimaryBlue)
        proposal.plan.actions.forEach{event->
         Text("${event.date} · ${event.title}${event.time?.let{" · $it"}.orEmpty()}",color=AppTheme.TextDark)
         event.notes?.let{Text(it,color=AppTheme.TextMuted)}
        }
        Text("Proposta AI dalla conversazione · ${proposal.provider} · verifica date e dettagli nelle fonti.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
        TextButton(onClick={focusPostId=proposal.sourcePostIds.firstOrNull{it!=snap.thread.id}?:snap.thread.id}){Text("Mostra i messaggi di origine")}
        if(proposal.status=="PENDING"){
         Button(onClick={confirmingProposal=proposal},enabled=!busy&&policy.allows("CALENDAR")){Text("Aggiungi al calendario")}
         if(!policy.allows("CALENDAR"))Text("Per confermare, attiva ACA e il servizio Calendario nelle impostazioni AI. Non serve una chiave personale.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
        }
       }}
      }}
      proposalError?.let{message->item{Text(message,color=AppTheme.TextMuted,style=MaterialTheme.typography.bodySmall)}}
     }
     if(snap.hasMore)item{TextButton(onClick={launchWork{refresh(true)}},enabled=!busy){Text("Carica messaggi precedenti")}}
     items(visible,key={it.id}){post->
      AilaCard{
       Column(Modifier.fillMaxWidth().combinedClickable(onClick={if(post.id!=snap.thread.id)focusPostId=post.id},onLongClick={menuPost=post}).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(post.author.label+(if(post.author.isMe)" · tu"else""),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=AppTheme.PrimaryBlue)
         TextButton(onClick={menuPost=post}){Text("•••")}}
        if(post.id==snap.thread.featuredPostId)Text("Risposta in evidenza",color=AppTheme.PrimaryBlue,style=MaterialTheme.typography.labelSmall)
        var shown=post.body
        post.mentions.filter{it.isMe}.forEach{shown=shown.replace("@${it.label}","@Tu")}
        Text(shown,style=MaterialTheme.typography.bodyLarge,color=AppTheme.TextDark)
        if(post.isAi)Text("Assistenza AI · ${post.generatedAt.orEmpty()} · ${post.provider.orEmpty()}",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
        post.attachments.forEach{a->LocalForumAttachment(a,!busy){onOpenAttachment(a)}}
        val replies=snap.posts.count{it.parentId==post.id}
        Row{TextButton(onClick={replyTo=post;focusPostId=if(post.id==snap.thread.id)null else post.id},enabled=!busy&&!post.hidden){Text("Rispondi")}
         if(post.id!=snap.thread.id&&replies>0)TextButton(onClick={focusPostId=post.id}){Text("$replies risposte →")}}
       }
      }
     }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     replyTo?.let{p->Row(verticalAlignment=Alignment.CenterVertically){Text("Riprendi: ${p.author.label}",Modifier.weight(1f),color=AppTheme.TextMuted);TextButton(onClick={replyTo=null}){Text("Annulla")}}}
     if(pendingAttachments.isNotEmpty())Column{pendingAttachments.forEach{a->Row(verticalAlignment=Alignment.CenterVertically){Text(a.fileName,Modifier.weight(1f),color=AppTheme.TextMuted);TextButton(onClick={pendingAttachments=pendingAttachments.filterNot{it.id==a.id};scope.launch{try{AppContainer.acaRepository.removeAttachment(a.id)}catch(_:Exception){}}}){Text("Rimuovi")}}}}
     OutlinedTextField(body,{body=it},modifier=Modifier.fillMaxWidth(),maxLines=4,placeholder={Text("Rispondi · richiama @perabot per chiedere ad AILA")},enabled=!busy)
     Row(verticalAlignment=Alignment.CenterVertically){
      TextButton(onClick=pickFile,enabled=!busy&&pendingAttachments.size<5){Text("Allega")}
      TextButton(onClick={if(recording)recorder.stop()else recorder.start()},enabled=!busy){Text(if(recording)"Termina"else"Vocale")}
      TextButton(onClick={mentionsDialog=true},enabled=!busy){Text("@")}
      Spacer(Modifier.weight(1f))
      Button(enabled=!busy&&rulesAccepted&&(body.isNotBlank()||pendingAttachments.isNotEmpty()),onClick={
       val text=body;val parent=replyTo?.id?:focusPostId?:snap.thread.id
       val mentions=snap.participants.filter{text.contains("@${it.label}")}.map{it.key}
       launchWork{
        val sent=repo.reply(snap.thread.id,text,snap.myIdentity?.anonymous?:anonymous,parent,mentions,pendingAttachments.map{it.id})
        body="";pendingAttachments=emptyList();replyTo=null;refresh()
        if(sent.invokePerabot){val p=AppContainer.acaRepository.preferences().preferences
         val request=ForumAiRequestDto(question=text,publish=true,replyToId=sent.id)
         if(!p.aiEnabled||!p.acaEnabled||"FORUM" !in p.services){error="Attiva AI, ACA e il servizio Forum in Impostazioni → AI.";return@launchWork}
  if(!p.cloudConsent)consentRequest=request else{activity(AssistantAgentPhase.WRITING);repo.ai(snap.thread.id,text,true,sent.id);refresh()}}
       }
      }){Text("Invia")}
     }
     if(recording)TextButton(onClick=recorder.cancel){Text("Annulla registrazione")}
     if(snap.myIdentity==null)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(anonymous,{anonymous=it});Text("Partecipa in forma anonima",color=AppTheme.TextMuted)}
     else Text("Identità in questa discussione: ${snap.myIdentity.label}. Rimarrà la stessa.",style=MaterialTheme.typography.labelSmall,color=AppTheme.TextMuted)
     if(!rulesAccepted)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(rulesAccepted,{rulesAccepted=it});TextButton(onClick={showRules=true}){Text("Accetto le regole del forum e l’informativa")}}
    }
   }
  }
 }
 if(creating) ForumNewQuestionSheet(
  title=createTitle,question=createBody,anonymous=anonymous,accepted=rulesAccepted,busy=busy,error=error,
  onTitle={createTitle=it},onQuestion={createBody=it},onAnonymous={anonymous=it},onAccepted={rulesAccepted=it},
  onRules={showRules=true},onClose={if(!busy)creating=false},onPublish={launchWork{
   val created=repo.create(createTitle,createBody,anonymous);creating=false;createTitle="";createBody="";threadId=created.thread.id
  }})
 if(showRules)AlertDialog(onDismissRequest={showRules=false},title={Text("Revisione tra pere")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  Text("Le discussioni sono visibili alla tua classe e restano disponibili nel tempo. Scegli nome reale o pseudonimo al primo intervento: l’identità resta fissa in quella discussione. Non pubblicare dati personali o contenuti offensivi. Puoi segnalare gli abusi; i moderatori possono rimuovere i contenuti.")
  Spacer(Modifier.height(12.dp));Text("Puoi richiamare @perabot o chiedere un riassunto esplicitamente. I messaggi condivisi possono essere analizzati anche da altri lettori autorizzati che attivano le proposte automatiche: le proposte sono visibili alla classe e nessun evento viene aggiunto senza conferma. Disattivare la tua AI non impedisce l’analisi dei messaggi già condivisi da parte degli altri lettori. Le risposte AI sono assistenza e possono contenere errori. L’elaborazione cloud richiede il consenso in Impostazioni → AI. Sul server: 90 giorni fino a 3 MB; oltre: 7 giorni. Le copie sul dispositivo restano fino alla cancellazione manuale. Gli audio durano al massimo 10 minuti. La scadenza degli allegati non elimina i messaggi.")
 }},confirmButton={TextButton(onClick={rulesAccepted=true;showRules=false}){Text("Ho letto e accetto")}})
 if(notificationDialog)AlertDialog(onDismissRequest={notificationDialog=false},title={Text("Notifiche di questa discussione")},text={Column{
  listOf("REPLIES" to "Solo risposte a me","ALL" to "Tutte le risposte","NONE" to "Nessuna notifica").forEach{(value,label)->
   Row(verticalAlignment=Alignment.CenterVertically){RadioButton(snapshot?.notificationMode==value,onClick={launchWork{repo.subscribe(threadId!!,value);refresh();notificationDialog=false}});Text(label,color=AppTheme.TextDark)}
  }
 }},confirmButton={TextButton(onClick={notificationDialog=false}){Text("Chiudi")}})
 if(mentionsDialog)AlertDialog(onDismissRequest={mentionsDialog=false},title={Text("Menziona una persona")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  TextButton(onClick={body+=" @perabot ";mentionsDialog=false}){Text("@perabot · AILA")}
  snapshot?.participants?.forEach{p->TextButton(onClick={body+=" @${p.label} ";mentionsDialog=false}){Text(if(p.isMe)"@Tu"else"@${p.label}")}}
 }},confirmButton={TextButton(onClick={mentionsDialog=false}){Text("Chiudi")}})
 menuPost?.let{p->AlertDialog(onDismissRequest={menuPost=null},title={Text(p.author.label)},text={Column{
  TextButton(onClick={replyTo=p;focusPostId=if(p.id==threadId)null else p.id;menuPost=null}){Text("Riprendi")}
  TextButton(onClick={menuPost=null;onPrepareCalendar(p.body)}){Text("Prepara eventi da questo messaggio")}
  if(snapshot?.thread?.isAuthor==true||isModerator)TextButton(onClick={menuPost=null;launchWork{val s=snapshot!!;repo.state(s.thread.id,s.thread.resolved,if(s.thread.featuredPostId==p.id)null else p.id);refresh()}}){Text(if(snapshot?.thread?.featuredPostId==p.id)"Togli dall’evidenza"else"Risposta in evidenza")}
  TextButton(onClick={reportPost=p;reportReason="";menuPost=null}){Text("Segnala")}
  if(isModerator)TextButton(onClick={menuPost=null;launchWork{repo.moderate(threadId!!,p.id,!p.hidden);refresh()}}){Text(if(p.hidden)"Ripristina messaggio"else"Rimuovi dalla discussione")}
 }},confirmButton={TextButton(onClick={menuPost=null}){Text("Chiudi")}})}
 reportPost?.let{p->AlertDialog(onDismissRequest={reportPost=null},title={Text("Segnala un contenuto")},text={OutlinedTextField(reportReason,{reportReason=it},label={Text("Motivo")},maxLines=5)},
  confirmButton={TextButton(enabled=reportReason.isNotBlank(),onClick={reportPost=null;launchWork{repo.report(threadId!!,p.id,reportReason)}}){Text("Invia segnalazione")}},dismissButton={TextButton(onClick={reportPost=null}){Text("Annulla")}})}
 reports?.let{items->AlertDialog(onDismissRequest={reports=null},title={Text("Segnalazioni")},text={Column(Modifier.verticalScroll(rememberScrollState())){
  if(items.isEmpty())Text("Nessuna segnalazione.")
  items.forEach{r->Text("${r.status} · ${r.reason}",color=AppTheme.TextDark)
   if(r.status=="OPEN")TextButton(onClick={reports=null;launchWork{repo.moderate(threadId!!,r.postId,true,r.id);refresh()}}){Text("Rimuovi e chiudi segnalazione")}}
 }},confirmButton={TextButton(onClick={reports=null}){Text("Chiudi")}})}
 summary?.let{r->AlertDialog(onDismissRequest={summary=null},title={Text("Riassunto generato da AILA")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("${r.provider} · ${r.generatedAt}",style=MaterialTheme.typography.labelSmall)
  SelectionContainer{Text(r.text)}
  Text("Torna ai messaggi originali:",style=MaterialTheme.typography.labelLarge)
  r.sources.take(20).forEach{s->TextButton(onClick={focusPostId=s.postId;summary=null}){Text(s.label)}}
 }},confirmButton={TextButton(onClick={summary=null}){Text("Chiudi")}})}
 consentRequest?.let{request->AlertDialog(onDismissRequest={consentRequest=null},title={Text("Attivare AILA per questa discussione?")},
  text={Text("Il servizio cloud elaborerà i messaggi visibili alla classe e le fonti AILA autorizzate. ${if(request.publish)"La risposta di Perabot sarà pubblicata nella discussione."else"Il riassunto sarà visibile solo a te."} Nessun evento verrà modificato automaticamente.")},
  confirmButton={TextButton(onClick={consentRequest=null;launchWork{
   val p=AppContainer.acaRepository.preferences().preferences;AppContainer.acaRepository.savePreferences(p.copy(cloudConsent=true));activity(AssistantAgentPhase.WRITING)
   val result=repo.ai(threadId!!,request.question,request.publish,request.replyToId);if(request.publish)refresh()else summary=result
  }}){Text("Consenti e continua")}},dismissButton={TextButton(onClick={consentRequest=null}){Text("Annulla")}})}
}

@Composable private fun LocalForumAttachment(item:ContentAttachmentDto,enabled:Boolean,onOpen:()->Unit){
 val revision=AppContainer.localContents.revision
 var local by remember(item.id){mutableStateOf<circolareplus.data.repository.LocalContentEntry?>(null)}
 LaunchedEffect(item.id,revision){try{local=AppContainer.localContents.findByRemote(item.id)}catch(e:CancellationException){throw e}catch(_:Exception){}}
 OutlinedButton(onClick=onOpen,enabled=enabled&&(!item.isExpired()||local!=null),modifier=Modifier.fillMaxWidth()){
  Text(when{local!=null->"${local!!.fileName} · Sul dispositivo";item.isExpired()->"Copia sul server scaduta";else->"${if(item.mime.startsWith("audio/"))"Audio"else"Allegato"}: ${item.fileName}"})
 }
}
