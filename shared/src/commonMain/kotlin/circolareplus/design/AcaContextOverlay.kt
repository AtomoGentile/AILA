package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcaContextOverlay(
    thinking: Boolean, answer: String?, onClose: () -> Unit, onOpenChat: () -> Unit,
    onSend: (String, Boolean) -> Unit, contextPreview: String = "", providerLabel: String = "",
    action: circolareplus.ai.assistant.AssistantMessage? = null,
    onConfirm: () -> Unit = {}, onCancel: () -> Unit = {}, isInteractive: Boolean = true
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var question by remember { mutableStateOf("") }
    var attachedScreen by remember(contextPreview, providerLabel) { mutableStateOf(false) }
    var attachmentMenu by remember { mutableStateOf(false) }
    LaunchedEffect(isInteractive) {
        if (isInteractive) {
            attachedScreen = false
            withFrameNanos { }
            focus.requestFocus()
        } else keyboard?.hide()
    }
    val dark = AppTheme.isDarkMode
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 38.dp, bottomEnd = 38.dp)
    // Opaque ink surfaces keep text from the page underneath out of the controls.
    val surface = if(dark) listOf(Color(0xFF141524), Color(0xFF090C13), Color(0xFF0D191F))
        else listOf(Color(0xFFF7F9FF), Color(0xFFECF0FA))
    val insetFill = if(dark) Color(0xFF151B27) else Color(0xFFE3E9F5)
    val fieldFill = if(dark) Color(0xFF191F2C) else Color(0xFFE2E8F5)
    val fieldFocus = if(dark) Color(0xFF1C2433) else Color(0xFFDCE5F7)
    val muted = if(dark) Color(0xFFA5AFC2) else AppTheme.TextMuted
    val edge = if(dark) Color.White.copy(alpha = .085f) else Color.White.copy(alpha = .85f)
    val canSend = question.isNotBlank() && !thinking
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
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight * .86f)
            .shadow(18.dp, shape).clip(shape).background(Brush.linearGradient(surface))
            .border(1.dp, edge, shape)
            .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AcaNeuralMark(thinking, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text("AILA ACA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                    Text(if(thinking) "Collego le informazioni…" else "Un aiuto, qui con te", style=MaterialTheme.typography.labelSmall, color=muted)
                }
                IconButton(onClick = { if(isInteractive) { keyboard?.hide(); onClose() } }, modifier = Modifier.size(36.dp).background(insetFill, RoundedCornerShape(18.dp)).semantics { contentDescription = "Chiudi ACA" }) {
                    AppIcons.Close(Modifier.size(16.dp), muted)
                }
            }
            if(action?.actionPlan != null) {
                circolareplus.ui.screens.AssistantActionPreview(message = action, isBusy = thinking, onConfirm = { if(isInteractive) onConfirm() }, onCancel = { if(isInteractive) onCancel() }, onRecover = {})
            } else answer?.let {
                Text(it, Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark)
            }
            if(attachedScreen) Row(Modifier.fillMaxWidth().background(insetFill,RoundedCornerShape(14.dp)).padding(start=12.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                AppIcons.Document(Modifier.size(17.dp),AppTheme.PrimaryBlue)
                Spacer(Modifier.width(8.dp))
                Text("Schermata allegata",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=AppTheme.TextDark)
                IconButton(onClick={attachedScreen=false},modifier=Modifier.size(32.dp).semantics{contentDescription="Rimuovi schermata"}){AppIcons.Close(Modifier.size(15.dp),muted)}
            }
            Row(Modifier.fillMaxWidth().background(fieldFocus,RoundedCornerShape(40.dp)).border(1.dp,edge.copy(alpha=.7f),RoundedCornerShape(40.dp)).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                IconButton(onClick={keyboard?.hide();attachmentMenu=true},enabled=isInteractive,modifier=Modifier.size(44.dp).semantics{contentDescription="Aggiungi allegato"}) { AppIcons.Plus(Modifier.size(25.dp),AppTheme.TextDark) }
                TextField(question,{question=it},placeholder={Text("Chiedi ad AILA…")},modifier=Modifier.weight(1f).focusRequester(focus),readOnly=!isInteractive,maxLines=3,
                    colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedTextColor=AppTheme.TextDark,unfocusedTextColor=AppTheme.TextDark,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedPlaceholderColor=muted,unfocusedPlaceholderColor=muted))
                if(question.isBlank()) IconButton(onClick={keyboard?.hide();attachmentMenu=true},enabled=isInteractive,modifier=Modifier.size(48.dp).background(AppTheme.PrimaryBlue,RoundedCornerShape(24.dp)).semantics{contentDescription="Microfono ACA"}) { AcaMic() }
                else IconButton(onClick=submit,enabled=canSend&&isInteractive,modifier=Modifier.size(48.dp).background(sendFill,RoundedCornerShape(24.dp)).semantics{contentDescription="Invia ad ACA"}) { AppIcons.ArrowUp(Modifier.size(22.dp),Color.White) }
            }
            TextButton(onClick = { if(isInteractive) { keyboard?.hide(); onOpenChat() } }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Continua nell’assistente", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp)); AppIcons.ChevronRight(Modifier.size(15.dp), AppTheme.PrimaryBlue)
            }
        }

        if(attachmentMenu) AilaBottomSheet(onDismissRequest={attachmentMenu=false}) {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text("Aggiungi alla richiesta",style=MaterialTheme.typography.titleLarge,color=AppTheme.TextDark,fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().background(insetFill,RoundedCornerShape(20.dp))
                    .semantics{contentDescription="Schermata, allega il contenuto aperto"}, verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={if(isInteractive){attachedScreen=true;attachmentMenu=false}}) {
                        AppIcons.Document(Modifier.size(22.dp),AppTheme.PrimaryBlue);Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment=Alignment.Start) {
                            Text("Schermata",style=MaterialTheme.typography.titleSmall,color=AppTheme.TextDark)
                            Text("Allega il contenuto aperto",style=MaterialTheme.typography.bodySmall,color=muted)
                        }
                    }
                }
                Text("Condividi solo le informazioni che vuoi inviare ad AILA.",style=MaterialTheme.typography.labelSmall,color=muted)
            }
        }
    }
}

@Composable private fun AcaMic() { androidx.compose.foundation.Canvas(Modifier.size(23.dp)) { val c=Color.White;drawRoundRect(c,androidx.compose.ui.geometry.Offset(size.width*.36f,size.height*.08f),androidx.compose.ui.geometry.Size(size.width*.28f,size.height*.52f),androidx.compose.ui.geometry.CornerRadius(size.width*.14f));drawArc(c,0f,180f,false,androidx.compose.ui.geometry.Offset(size.width*.22f,size.height*.2f),androidx.compose.ui.geometry.Size(size.width*.56f,size.height*.58f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawLine(c,androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.78f),androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.94f),2.dp.toPx()) } }
