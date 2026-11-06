package circolareplus.design

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

@Composable
fun AcaContextOverlay(
    thinking: Boolean, answer: String?, onClose: () -> Unit, onOpenChat: () -> Unit,
    onSend: (String, Boolean) -> Unit, contextPreview: String = "", providerLabel: String = "",
    action: circolareplus.ai.assistant.AssistantMessage? = null,
    onConfirm: () -> Unit = {}, onCancel: () -> Unit = {}
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    var question by remember { mutableStateOf("") }
    var includeContext by remember(contextPreview, providerLabel) { mutableStateOf(false) }
    val shape = RoundedCornerShape(34.dp)
    val surface = if(AppTheme.isDarkMode) listOf(Color(0xF51B1F2C), Color(0xF50D111B))
        else listOf(Color(0xFAFFFFFF), Color(0xF0EDF1FC))
    val submit = {
        if(question.isNotBlank() && !thinking) {
            val text = question; question = ""; keyboard?.hide(); onSend(text, includeContext)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = maxHeight * .86f)
            .shadow(22.dp, shape).clip(shape).background(Brush.linearGradient(surface))
            .border(1.dp, if(AppTheme.isDarkMode) Color.White.copy(alpha = .17f) else Color.White, shape)
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AilaAssistantMark(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("AILA ACA", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                IconButton(onClick = { keyboard?.hide(); onClose() }, modifier = Modifier.size(32.dp).background(AppTheme.TextMuted.copy(alpha = .10f), RoundedCornerShape(16.dp)).semantics { contentDescription = "Chiudi ACA" }) {
                    AppIcons.Close(Modifier.size(16.dp), AppTheme.TextMuted)
                }
            }
            if(thinking) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = AppTheme.PrimaryBlue)
                Text("Ci sto pensando…", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            }
            if(action?.actionPlan != null) {
                circolareplus.ui.screens.AssistantActionPreview(message = action, isBusy = thinking, onConfirm = onConfirm, onCancel = onCancel, onRecover = {})
            } else answer?.let {
                Text(it, Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextDark)
            }
            // The checkbox remains an explicit choice before any screen text leaves the device.
            Column(Modifier.fillMaxWidth().background(AppTheme.TextMuted.copy(alpha = .07f), RoundedCornerShape(20.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeContext, { includeContext = it }, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Usa il contesto della schermata", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark)
                        Text(if(includeContext) providerLabel else "Scegli cosa condividere con AILA", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                    }
                }
                if(includeContext) Text(contextPreview.take(6000), Modifier.padding(horizontal = 8.dp, vertical = 6.dp).heightIn(max = 100.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            TextField(question, { question = it }, placeholder = { Text("Chiedi ad AILA…", style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus), maxLines = 3, shape = RoundedCornerShape(24.dp),
                trailingIcon = {
                    IconButton(enabled = question.isNotBlank() && !thinking, onClick = submit,
                        modifier = Modifier.padding(end = 6.dp).size(38.dp).background(if(question.isNotBlank() && !thinking) AppTheme.PrimaryBlue else AppTheme.TextMuted.copy(alpha = .12f), RoundedCornerShape(19.dp)).semantics { contentDescription = "Invia ad ACA" }) {
                        AppIcons.ArrowUp(Modifier.size(19.dp), if(question.isNotBlank() && !thinking) Color.White else AppTheme.TextFaint)
                    }
                },
                colors = TextFieldDefaults.colors(focusedContainerColor = AppTheme.TextMuted.copy(alpha = .10f), unfocusedContainerColor = AppTheme.TextMuted.copy(alpha = .10f),
                    focusedTextColor = AppTheme.TextDark, unfocusedTextColor = AppTheme.TextDark,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = AppTheme.TextMuted, unfocusedPlaceholderColor = AppTheme.TextMuted))
            TextButton(onClick = { keyboard?.hide(); onOpenChat() }, modifier = Modifier.align(Alignment.CenterHorizontally), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                Text("Continua nell’assistente", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp)); AppIcons.ChevronRight(Modifier.size(15.dp), AppTheme.PrimaryBlue)
            }
        }
    }
}
