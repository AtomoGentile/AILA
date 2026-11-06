package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val expanded = question.isNotBlank() || attachedScreen || attachedFiles.isNotEmpty() || recording || transcribing || thinking || answer != null || action?.actionPlan != null || voiceError != null
    val expansion by animateFloatAsState(if(expanded) 1f else 0f, tween(if(AppTheme.reduceMotion) 0 else 280, easing=FastOutSlowInEasing),label="ACA composer expansion")
    val dark = AppTheme.isDarkMode
    val shape = RoundedCornerShape(32.dp)
    // Opaque ink surfaces keep text from the page underneath out of the controls.
    val surface = if(dark) listOf(Color(0xFF08090E), Color(0xFF0B0C13), Color(0xFF161827))
        else listOf(Color(0xFFF7F9FF), Color(0xFFECF0FA))
    val insetFill = if(dark) Color(0xFF151B27) else Color(0xFFE3E9F5)
    val muted = if(dark) Color(0xFFA5AFC2) else AppTheme.TextMuted
    val edge = if(dark) Color.White.copy(alpha = .085f) else Color.White.copy(alpha = .85f)
    val canSend = question.isNotBlank() && !thinking && !recording && !transcribing
    val sendFill by animateColorAsState(
        if(canSend) AppTheme.PrimaryBlue else if(dark) Color(0xFF272F40) else Color(0xFFD0D9EA),
        tween(if(AppTheme.reduceMotion) 80 else 160), label = "aca send color"
    )
    val submit = {
        if(canSend && isInteractive) {
            val text = question; question = ""; keyboard?.hide(); onSend(text, attachedScreen)
            attachedScreen = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight * .86f)
            .shadow(18.dp, shape).clip(shape)
            .then(if(AppTheme.isGlass) Modifier.ailaGlassSurface(shape,tint=(if(dark) Color(0xFF090C13) else Color.White).copy(alpha=.76f)) else Modifier.background(Brush.linearGradient(surface)).border(1.dp,edge,shape))
            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f, fill=false).verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(28.dp)) {
                IconButton(onClick={keyboard?.hide();onClose()},modifier=Modifier.align(Alignment.CenterEnd).size(28.dp).semantics{contentDescription="Chiudi ACA"}){AppIcons.Close(Modifier.size(15.dp),muted)}
            }
            if(action?.actionPlan != null) {
                circolareplus.ui.screens.AssistantActionPreview(message = action, isBusy = thinking, onConfirm = { if(isInteractive) onConfirm() }, onCancel = { if(isInteractive) onCancel() }, onRecover = {})
            } else answer?.let {
                AilaResponseText(
                    text = androidx.compose.ui.text.AnnotatedString(it),
                    responseId = action?.id ?: it,
                    modifier = Modifier.animateContentSize(ailaSpatialSpring()).heightIn(max = 250.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
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
                Text(voiceError ?: if(recording) "Registrazione vocale in corso…" else "Trascrivo il vocale…",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=if(voiceError!=null) AppTheme.TintRedInk else muted)
                if(recording) TextButton(onClick={recorder.cancel()}) { Text("Annulla") }
            }
            Layout(modifier=Modifier.fillMaxWidth(),content={
            TextField(question,{question=it},placeholder={Text("Chiedi ad AILA…",style=MaterialTheme.typography.titleMedium)},modifier=Modifier.focusRequester(focus),readOnly=!isInteractive||recording||transcribing,minLines=1,maxLines=4,
                colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedTextColor=AppTheme.TextDark,unfocusedTextColor=AppTheme.TextDark,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedPlaceholderColor=muted,unfocusedPlaceholderColor=muted))

                IconButton(onClick={keyboard?.hide();attachmentMenu=true},enabled=isInteractive&&!thinking&&!recording&&!transcribing,modifier=Modifier.size(48.dp).semantics{contentDescription="Aggiungi allegato"}) { AppIcons.Plus(Modifier.size(28.dp),AppTheme.TextDark) }
                IconButton(onClick={if(recording)recorder.stop()else{keyboard?.hide();recorder.start()}},enabled=isInteractive&&!transcribing&&!thinking,modifier=Modifier.size(48.dp).semantics{contentDescription=if(recording)"Termina vocale ACA"else"Registra un vocale per ACA"}) { if(recording)AppIcons.Close(Modifier.size(22.dp),AppTheme.TintRedInk)else AcaMic() }
                IconButton(onClick=submit,enabled=canSend&&isInteractive&&!recording&&!transcribing,modifier=Modifier.size(48.dp).background(sendFill,RoundedCornerShape(24.dp)).semantics{contentDescription="Invia ad ACA"}) {
                    if(thinking||transcribing)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=muted)else AppIcons.ArrowUp(Modifier.size(25.dp),if(canSend)Color.White else muted)
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
                val h=(compactH+(field.height+gap+button-compactH)*expansion).toInt()
                layout(w,h) {
                    field.placeRelative(((button+gap)*(1f-expansion)).toInt(),((compactH-field.height)/2*(1f-expansion)).toInt())
                    controls[0].placeRelative(0,rowY)
                    controls[1].placeRelative(w-button*2-gap,rowY)
                    controls[2].placeRelative(w-button,rowY)
                }
            }
            }
            // The handle stays outside scrolling content and owns only this drag gesture.
            Box(Modifier.fillMaxWidth().height(32.dp)
                .semantics { contentDescription="Apri la chat di ACA" }
                .clickable(enabled=isInteractive&&!recording&&!transcribing, onClick=openChat)
                .pointerInput(isInteractive, recording, transcribing) {
                    var distance=0f
                    detectVerticalDragGestures(
                        onDragStart={distance=0f},
                        onVerticalDrag={change,amount->change.consume();distance+=amount},
                        onDragEnd={if(distance>48.dp.toPx())openChat()},
                        onDragCancel={distance=0f}
                    )
                },contentAlignment=Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).background(muted.copy(alpha=.5f),RoundedCornerShape(2.dp)))
            }
        }

        if(attachmentMenu) ModalBottomSheet(
            onDismissRequest={attachmentMenu=false},
            containerColor=Color.Transparent, tonalElevation=0.dp, dragHandle=null,
            shape=RoundedCornerShape(0.dp)
        ) {
            Column(Modifier.padding(horizontal=16.dp).padding(bottom=16.dp).widthIn(max=600.dp).fillMaxWidth()
                .clip(shape).background(Brush.linearGradient(surface)).border(1.dp,edge,shape)
                .padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(muted.copy(alpha=.5f),RoundedCornerShape(2.dp)))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Aggiungi alla richiesta",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark,fontWeight=FontWeight.SemiBold)
                    IconButton(onClick={attachmentMenu=false},modifier=Modifier.size(32.dp).semantics{contentDescription="Chiudi allegati"}) { AppIcons.Close(Modifier.size(16.dp),muted) }
                }
                AcaAttachmentOption("Schermata", "Allega il contenuto aperto", insetFill, muted, isInteractive) {
                    attachedScreen=true;attachmentMenu=false
                }
                AcaAttachmentOption("File, foto o audio", "Scegli dal dispositivo · fino a 20 MB", insetFill, muted, isInteractive&&!thinking) { picker() }
                Text("Gli allegati della chat sono temporanei: nessuna copia salvata automaticamente sul dispositivo.",style=MaterialTheme.typography.labelSmall,color=muted)
            }
        }
    }
}

@Composable
private fun AcaAttachmentOption(title:String,subtitle:String,fill:Color,muted:Color,enabled:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fill)
        .clickable(enabled=enabled,onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
        AppIcons.Document(Modifier.size(22.dp),AppTheme.PrimaryBlue)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall,color=AppTheme.TextDark)
            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=muted)
        }
    }
}

@Composable private fun AcaMic() { androidx.compose.foundation.Canvas(Modifier.size(23.dp)) { val c=Color.White;drawRoundRect(c,androidx.compose.ui.geometry.Offset(size.width*.36f,size.height*.08f),androidx.compose.ui.geometry.Size(size.width*.28f,size.height*.52f),androidx.compose.ui.geometry.CornerRadius(size.width*.14f));drawArc(c,0f,180f,false,androidx.compose.ui.geometry.Offset(size.width*.22f,size.height*.2f),androidx.compose.ui.geometry.Size(size.width*.56f,size.height*.58f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawLine(c,androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.78f),androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.94f),2.dp.toPx()) } }
