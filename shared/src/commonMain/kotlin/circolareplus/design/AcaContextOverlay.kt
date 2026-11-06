package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var includeContext by remember(contextPreview, providerLabel) { mutableStateOf(false) }
    LaunchedEffect(isInteractive) {
        if (isInteractive) {
            includeContext = false
            withFrameNanos { }
            focus.requestFocus()
        } else keyboard?.hide()
    }
    val dark = AppTheme.isDarkMode
    val shape = RoundedCornerShape(32.dp)
    // Opaque ink surfaces keep text from the page underneath out of the controls.
    val surface = if(dark) listOf(Color(0xFF101520), Color(0xFF090C13))
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
            val text = question; question = ""; keyboard?.hide(); onSend(text, includeContext)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight * .86f)
            .shadow(18.dp, shape).clip(shape).background(Brush.linearGradient(surface))
            .border(1.dp, edge, shape)
            .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AilaAssistantMark(modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("AILA ACA", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                IconButton(onClick = { if(isInteractive) { keyboard?.hide(); onClose() } }, modifier = Modifier.size(36.dp).background(insetFill, RoundedCornerShape(18.dp)).semantics { contentDescription = "Chiudi ACA" }) {
                    AppIcons.Close(Modifier.size(16.dp), muted)
                }
            }
            if(thinking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = AppTheme.PrimaryBlue)
                Text("Ci sto pensando…", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            }
            if(action?.actionPlan != null) {
                circolareplus.ui.screens.AssistantActionPreview(message = action, isBusy = thinking, onConfirm = { if(isInteractive) onConfirm() }, onCancel = { if(isInteractive) onCancel() }, onRecover = {})
            } else answer?.let {
                Text(it, Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark)
            }
            // The checkbox remains an explicit choice before any screen text leaves the device.
            Column(Modifier.fillMaxWidth().background(insetFill, RoundedCornerShape(20.dp))) {
                Row(Modifier.fillMaxWidth().toggleable(value = includeContext, role = Role.Checkbox,
                    onValueChange = { if(isInteractive) includeContext = it }).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeContext, onCheckedChange = null, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Usa il contesto della schermata", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark)
                        Text(if(includeContext) providerLabel else "Controlla cosa condividere con AILA", style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
                AnimatedVisibility(includeContext,
                    enter = fadeIn(tween(140)) + expandVertically(tween(if(AppTheme.reduceMotion) 0 else 180)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(if(AppTheme.reduceMotion) 0 else 140))) {
                    Text(contextPreview.take(6000), Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).heightIn(max = 100.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            TextField(question, { question = it }, placeholder = { Text("Chiedi ad AILA…", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.fillMaxWidth().border(1.dp, edge, RoundedCornerShape(24.dp)).focusRequester(focus), readOnly = !isInteractive, maxLines = 3, shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(enabled = canSend, onClick = submit,
                        modifier = Modifier.padding(end = 6.dp).size(38.dp).background(if(canSend && isInteractive) AppTheme.PrimaryBlue else AppTheme.TextMuted.copy(alpha = .12f), RoundedCornerShape(19.dp)).semantics { contentDescription = "Invia ad ACA" }) {
                        AppIcons.ArrowUp(Modifier.size(19.dp), if(canSend && isInteractive) Color.White else AppTheme.TextFaint)
                    }
                },
                colors = TextFieldDefaults.colors(focusedContainerColor = fieldFocus, unfocusedContainerColor = fieldFill,
                    focusedTextColor = AppTheme.TextDark, unfocusedTextColor = AppTheme.TextDark,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = muted, unfocusedPlaceholderColor = muted))
            TextButton(onClick = { if(isInteractive) { keyboard?.hide(); onOpenChat() } }, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Continua nell’assistente", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp)); AppIcons.ChevronRight(Modifier.size(15.dp), AppTheme.PrimaryBlue)
            }
        }
    }
}
