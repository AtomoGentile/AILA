package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.platform.rememberVoiceRecorder
import circolareplus.data.AppContainer
import circolareplus.ai.prepareChatAttachment
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcaContextOverlay(
    thinking: Boolean, answer: String?, onClose: () -> Unit, onOpenChat: (String, Boolean) -> Unit,
    onSend: (String, Boolean) -> Unit, contextPreview: String = "", providerLabel: String = "",
    action: circolareplus.ai.assistant.AssistantMessage? = null,
    onConfirm: () -> Unit = {}, onCancel: () -> Unit = {}, isInteractive: Boolean = true,
    attachedFiles: List<circolareplus.ai.assistant.AssistantAttachedFile> = emptyList(),
    onAttachContent: (circolareplus.platform.IncomingContent) -> Unit = {},
    onRemoveAttachedFile: (String) -> Unit = {}
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var question by remember { mutableStateOf("") }
    var attachedScreen by remember(contextPreview, providerLabel) { mutableStateOf(false) }
    var attachmentMenu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var dragProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val picker = circolareplus.platform.rememberContentPicker({ voiceError = it }) {
        attachmentMenu = false
        onAttachContent(it)
    }
    val openChat by rememberUpdatedState<() -> Unit> { if(isInteractive && !recording && !transcribing) { keyboard?.hide(); onOpenChat(question, attachedScreen) } }
    val recorder = rememberVoiceRecorder({ recording = it }, { voiceError = it }) { audio ->
        scope.launch {
            transcribing = true; voiceError = null
            try {
                val prepared = prepareChatAttachment(audio)
                val entry = AppContainer.localContents.entry(prepared.localId)
                val transcript = entry?.let { AppContainer.localContents.text(it) }.orEmpty()
                check(transcript.isNotBlank()) { "Non sono riuscito a trascrivere il vocale." }
                question = listOf(question.trim(), transcript).filter { it.isNotBlank() }.joinToString("\n")
                withFrameNanos { }
                focus.requestFocus()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { voiceError = e.message ?: "Trascrizione non riuscita." }
            finally { transcribing = false }
        }
    }
    LaunchedEffect(isInteractive) {
        if (isInteractive) {
            attachedScreen = false
            delay(if(AppTheme.reduceMotion) 0 else 220)
            focus.requestFocus()
        } else keyboard?.hide()
    }
    val expanded = question.isNotBlank() || attachedScreen || attachedFiles.isNotEmpty() || recording || transcribing || voiceError != null
    val expansion by animateFloatAsState(if(expanded) 1f else 0f, tween(if(AppTheme.reduceMotion) 0 else 280, easing=FastOutSlowInEasing),label="ACA composer expansion")
    val dark = AppTheme.isDarkMode
    val shape = RoundedCornerShape(28.dp)
    val composerRadius by animateDpAsState(if(expanded) 28.dp else 48.dp, tween(if(AppTheme.reduceMotion) 0 else 280),label="ACA composer corners")
    val composerShape = RoundedCornerShape(composerRadius)
    // Opaque ink surfaces keep text from the page underneath out of the controls.
    val surface = if(dark) listOf(Color(0xFF08090E), Color(0xFF0B0C13), Color(0xFF161827))
        else listOf(Color(0xFFF7F9FF), Color(0xFFECF0FA))
    val insetFill = if(dark) Color(0xFF151B27) else Color(0xFFE3E9F5)
    val muted = if(dark) Color(0xFFA5AFC2) else AppTheme.TextMuted
    val edge = if(dark) Color.White.copy(alpha = .085f) else Color.White.copy(alpha = .85f)
    val canSend = question.isNotBlank() && !thinking && !recording && !transcribing
    val sendFill by animateColorAsState(
        if(canSend || recording) AppTheme.PrimaryBlue else if(dark) Color(0xFF272F40) else Color(0xFFD0D9EA),
        tween(if(AppTheme.reduceMotion) 80 else 160), label = "aca send color"
    )
    val submit = {
        if(canSend && isInteractive) {
            val text = question; question = ""; keyboard?.hide(); onSend(text, attachedScreen)
            attachedScreen = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Own the outside tap so closing ACA never activates the screen underneath.
        Box(Modifier.matchParentSize().clickable(
            interactionSource=remember { MutableInteractionSource() }, indication=null
        ) { if(isInteractive) { keyboard?.hide(); onClose() } }
            .semantics { contentDescription="Chiudi ACA" })
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).imePadding()) {
        val availableHeight = maxHeight
        val panelSurface = if(AppTheme.isGlass) Modifier.ailaGlassSurface(shape,
            tint=(if(dark) Color(0xFF090C13) else Color.White).copy(alpha=.88f))
            else Modifier.background(Brush.linearGradient(surface)).border(1.dp,edge,shape)
        Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = availableHeight * .86f),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The response owns its scroll area; the composer stays available below it.
            if(action?.actionPlan != null || answer != null) {
                Column(Modifier.weight(1f, fill=false).heightIn(max=320.dp).fillMaxWidth()
                    .shadow(12.dp,shape).clip(shape).then(panelSurface)
                    .pointerInput(Unit) { detectTapGestures { } }.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("AILA",style=MaterialTheme.typography.labelLarge,color=muted)
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {
                        if(action?.actionPlan != null) {
                            circolareplus.ui.screens.AssistantActionPreview(message = action, isBusy = thinking,
                                onConfirm = { if(isInteractive) onConfirm() }, onCancel = { if(isInteractive) onCancel() }, onRecover = {})
                        } else answer?.let {
                            AilaResponseText(text=androidx.compose.ui.text.AnnotatedString(it),responseId=action?.id ?: it)
                        }
                    }
                }
            }
            val composerSurface = if(AppTheme.isGlass) Modifier.ailaGlassSurface(composerShape,
                tint=(if(dark) Color(0xFF090C13) else Color.White).copy(alpha=.88f))
                else Modifier.background(Brush.linearGradient(surface)).border(1.dp,edge,composerShape)
            Column(Modifier.fillMaxWidth().shadow(12.dp,composerShape).clip(composerShape).then(composerSurface)
                .pointerInput(Unit) { detectTapGestures { } }
                .pointerInput(isInteractive,recording,transcribing) {
                    var distance=0f
                    val threshold=48.dp.toPx()
                    detectVerticalDragGestures(onDragStart={distance=0f;dragProgress=0f},
                        onVerticalDrag={change,amount->
                            change.consume()
                            distance+=amount
                            dragProgress=(distance/threshold).coerceIn(0f,1f)
                        },
                        onDragEnd={
                            val shouldOpen=distance>threshold
                            dragProgress=0f
                            if(shouldOpen)openChat()
                        },
                        onDragCancel={distance=0f;dragProgress=0f})
                }
                .graphicsLayer {
                    translationY=dragProgress*18.dp.toPx()
                    val scale=1f+dragProgress*.025f
                    scaleX=scale
                    scaleY=scale
                }
                .border(
                    width=(1.5f+dragProgress*3f).dp,
                    brush=Brush.sweepGradient(listOf(
                        AppTheme.PrimaryBlue.copy(alpha=.72f+dragProgress*.28f),
                        AilaAssistantViolet.copy(alpha=.55f+dragProgress*.4f),
                        AilaAssistantTeal.copy(alpha=.55f+dragProgress*.4f),
                        AppTheme.PrimaryBlue.copy(alpha=.72f+dragProgress*.28f)
                    )),
                    shape=composerShape
                )
                .semantics {
                    if(isInteractive && !recording && !transcribing) customActions=listOf(
                        CustomAccessibilityAction("Apri la chat di ACA") { openChat(); true })
                }
                .padding(horizontal=12.dp,vertical=12.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Column(Modifier.heightIn(max=availableHeight*.25f).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(4.dp)) {
            if(attachedScreen) Row(Modifier.fillMaxWidth().background(insetFill,RoundedCornerShape(14.dp)).padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                AppIcons.Document(Modifier.size(17.dp),AppTheme.PrimaryBlue)
                Spacer(Modifier.width(8.dp))
                Text("Schermata allegata",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=AppTheme.TextDark)
                IconButton(onClick={attachedScreen=false},modifier=Modifier.size(32.dp).semantics{contentDescription="Rimuovi schermata"}){AppIcons.Close(Modifier.size(15.dp),muted)}
            }
            attachedFiles.forEach { file ->
                Row(Modifier.fillMaxWidth().background(insetFill,RoundedCornerShape(14.dp)).padding(start=12.dp,end=4.dp), verticalAlignment=Alignment.CenterVertically) {
                    AppIcons.Document(Modifier.size(17.dp),AppTheme.PrimaryBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(file.name,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=AppTheme.TextDark,maxLines=2)
                    IconButton(onClick={onRemoveAttachedFile(file.localId)},enabled=isInteractive&&!thinking,modifier=Modifier.size(40.dp).semantics{contentDescription="Rimuovi ${file.name}"}) { AppIcons.Close(Modifier.size(15.dp),muted) }
                }
            }
            if(recording || transcribing || voiceError != null) Row(Modifier.fillMaxWidth().background(insetFill,RoundedCornerShape(16.dp)).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(voiceError ?: if(recording) "Registrazione vocale in corso…" else "Trascrivo il vocale…",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=if(voiceError!=null) AppTheme.TintRedInk else muted)
            }
            }
            Layout(modifier=Modifier.fillMaxWidth(),content={
            TextField(question,{question=it},placeholder={Text("Chiedi ad AILA…",style=MaterialTheme.typography.titleMedium,maxLines=1)},modifier=Modifier.focusRequester(focus),readOnly=!isInteractive||recording||transcribing,minLines=1,maxLines=4,
                textStyle=MaterialTheme.typography.titleMedium,
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedTextColor=AppTheme.TextDark,unfocusedTextColor=AppTheme.TextDark,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedPlaceholderColor=muted,unfocusedPlaceholderColor=muted))

                IconButton(onClick={keyboard?.hide();attachmentMenu=true},enabled=isInteractive&&!thinking&&!recording&&!transcribing,modifier=Modifier.size(48.dp).background(if(dark) Color(0xFF252A35) else Color(0xFFE3E9F5),CircleShape).semantics{contentDescription="Aggiungi allegato"}) { AppIcons.Plus(Modifier.size(28.dp),AppTheme.TextDark) }
                IconButton(onClick={if(recording){recorder.cancel();voiceError=null;focus.requestFocus();keyboard?.show()}else{keyboard?.hide();voiceError=null;recorder.start()}},enabled=isInteractive&&!transcribing&&!thinking,modifier=Modifier.size(48.dp).background(if(recording) AppTheme.TintRed else if(!expanded && !thinking && !transcribing) AppTheme.PrimaryBlue else Color.Transparent,RoundedCornerShape(24.dp)).semantics{contentDescription=if(recording)"Elimina vocale"else"Registra un vocale per ACA"}) { if(recording)AppIcons.Close(Modifier.size(22.dp),AppTheme.TintRedInk)else AcaMic(if(!expanded && !thinking && !transcribing) Color.White else AppTheme.TextDark) }
                IconButton(onClick={if(recording)recorder.stop()else submit()},enabled=isInteractive && !thinking && !transcribing && (recording || canSend),modifier=Modifier.size(48.dp).background(sendFill,RoundedCornerShape(24.dp)).semantics{contentDescription=if(recording)"Ferma e trascrivi vocale"else"Invia ad AILA"}) {
                    if(thinking||transcribing)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=muted)else androidx.compose.animation.Crossfade(targetState=recording,animationSpec=tween(if(AppTheme.reduceMotion) 0 else 180),label="Vocale stop") { stopping ->
                        if(stopping) Box(Modifier.size(25.dp),contentAlignment=Alignment.Center) { Box(Modifier.size(18.dp).background(Color.White,RoundedCornerShape(4.dp))) }
                        else AppIcons.ArrowUp(Modifier.size(25.dp),if(canSend)Color.White else muted)
                    }
                }
            }) { measurables, constraints ->
                val w=constraints.maxWidth
                val button=48.dp.roundToPx()
                val gap=4.dp.roundToPx()
                val fieldWidth=(w-(1f-expansion)*(button*3+gap*2)).toInt().coerceAtLeast(1)
                val field=measurables[0].measure(constraints.copy(minWidth=fieldWidth,maxWidth=fieldWidth,minHeight=0))
                val controls=measurables.drop(1).map { it.measure(androidx.compose.ui.unit.Constraints.fixed(button,button)) }
                val compactH=maxOf(field.height,button)
                val rowY=((field.height+gap)*expansion).toInt()
                val controlY=(rowY+((compactH-button)/2*(1f-expansion))).toInt()
                val h=(compactH+(field.height+gap+button-compactH)*expansion).toInt()
                layout(w,h) {
                    field.placeRelative(((button+gap)*(1f-expansion)).toInt(),((compactH-field.height)/2*(1f-expansion)).toInt())
                    controls[0].placeRelative(0,controlY)
                    controls[1].placeRelative(w-button*2-gap,controlY)
                    controls[2].placeRelative(w-button,controlY)
                }
            }
            }
        }


        if(attachmentMenu) AilaBottomSheet(onDismissRequest={attachmentMenu=false}) {
            Column(Modifier.widthIn(max=600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Aggiungi alla richiesta",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,color=AppTheme.TextDark,fontWeight=FontWeight.SemiBold)
                    AilaIconButton(contentDescription="Chiudi allegati",onClick={attachmentMenu=false}) { tint -> AppIcons.Close(Modifier.size(18.dp),tint) }
                }
                AcaAttachmentOption("Schermata", "Allega il contenuto aperto", AppTheme.TintViolet, AppTheme.TintVioletInk, isInteractive) {
                    attachedScreen=true;attachmentMenu=false
                }
                AcaAttachmentOption("File, foto o audio", "Scegli dal dispositivo · fino a 20 MB", AppTheme.TintBlue, AppTheme.TintBlueInk, isInteractive&&!thinking) { picker() }
                Text("Gli allegati della chat sono temporanei: nessuna copia salvata automaticamente sul dispositivo.",style=MaterialTheme.typography.bodySmall,color=muted)
            }
        }
    }
    }
}

@Composable
private fun AcaAttachmentOption(title:String,subtitle:String,fill:Color,muted:Color,enabled:Boolean,onClick:()->Unit) {
    val interaction = remember { MutableInteractionSource() }
    val optionShape = ailaMorphShape(interaction)
    Row(Modifier.fillMaxWidth().ailaSheetReveal(if(title=="Schermata") 1 else 2)
        .ailaPressScale(interaction, .97f).ailaTopicSurface(optionShape,fill,muted)
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=androidx.compose.ui.semantics.Role.Button,onClick=onClick)
        .padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(muted.copy(alpha=.12f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
            AppIcons.Document(Modifier.size(24.dp),muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium,color=AppTheme.TextDark)
            Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=muted)
        }
        AppIcons.ChevronRight(Modifier.size(20.dp),muted)
    }
}

@Composable private fun AcaMic(c: Color) { androidx.compose.foundation.Canvas(Modifier.size(23.dp)) { drawRoundRect(c,androidx.compose.ui.geometry.Offset(size.width*.36f,size.height*.08f),androidx.compose.ui.geometry.Size(size.width*.28f,size.height*.52f),androidx.compose.ui.geometry.CornerRadius(size.width*.14f));drawArc(c,0f,180f,false,androidx.compose.ui.geometry.Offset(size.width*.22f,size.height*.2f),androidx.compose.ui.geometry.Size(size.width*.56f,size.height*.58f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawLine(c,androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.78f),androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.94f),2.dp.toPx()) } }
