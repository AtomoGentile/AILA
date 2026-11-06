package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.design.*

@Composable
internal fun ForumIntroduction(busy: Boolean, onCreate: () -> Unit, onRules: () -> Unit,
    title: String = "Educazione tra pere", description: String = "Un posto per chiedere, capire e dare una mano ai compagni.",
    createLabel: String = "Nuova domanda") {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AppTheme.CardSurface, AppTheme.TintGreen.copy(alpha = .65f))), RoundedCornerShape(28.dp))
        .border(1.dp, AppTheme.TintGreenInk.copy(alpha = .13f), RoundedCornerShape(28.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(AppTheme.TintGreen, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { AppIcons.ChatBubble(Modifier.size(24.dp), AppTheme.TintGreenInk) }
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
        }
        Text(description, style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onCreate, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                AppIcons.Plus(Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(6.dp)); Text(createLabel)
            }
            TextButton(onClick = onRules) { Text("Come funziona") }
        }
    }
}

@Composable
internal fun ForumEmptyState(title: String = "La prima domanda può essere la tua",
    description: String = "Un dubbio sui compiti o un passaggio da spiegare? Inizia una conversazione: le risposte resteranno qui per tutta la classe.",
    privacyLabel: String = "Puoi partecipare con uno pseudonimo.") {
    Column(Modifier.fillMaxWidth().background(AppTheme.CardSurface, RoundedCornerShape(26.dp)).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcons.People(Modifier.size(38.dp), AppTheme.TintGreenInk)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = AppTheme.TextDark, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description,
            style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcons.Shield(Modifier.size(14.dp), AppTheme.TextFaint)
            Text(privacyLabel, style = MaterialTheme.typography.labelSmall, color = AppTheme.TextFaint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForumNewQuestionSheet(title: String, question: String, anonymous: Boolean, accepted: Boolean, busy: Boolean, error: String?,
    onTitle: (String) -> Unit, onQuestion: (String) -> Unit, onAnonymous: (Boolean) -> Unit, onAccepted: (Boolean) -> Unit,
    onRules: () -> Unit, onClose: () -> Unit, onPublish: () -> Unit) {
    AilaBottomSheet(onDismissRequest = { if(!busy) onClose() }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nuova domanda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                    Text("Apri una conversazione con la tua classe.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                }
                IconButton(onClick = onClose, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Chiudi nuova domanda" }) { AppIcons.Close(Modifier.size(20.dp), AppTheme.TextMuted) }
            }
            OutlinedTextField(title,onTitle,modifier=Modifier.fillMaxWidth(),label={Text("Di cosa vuoi parlare?")},placeholder={Text("Es. Un dubbio sugli esercizi di matematica")},maxLines=2,enabled=!busy,
                shape=RoundedCornerShape(20.dp),colors=ailaFieldColors(),isError=title.length>180,supportingText={Text("${title.length}/180")})
            OutlinedTextField(question,onQuestion,modifier=Modifier.fillMaxWidth(),label={Text("La tua domanda")},placeholder={Text("Racconta cosa hai provato e dove ti sei bloccato…")},minLines=4,maxLines=8,enabled=!busy,
                shape=RoundedCornerShape(20.dp),colors=ailaFieldColors(),isError=question.length>12000,supportingText={Text("${question.length}/12000")})
            Column(Modifier.fillMaxWidth().background(AppTheme.TrackFill, RoundedCornerShape(22.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Come vuoi apparire?",style=MaterialTheme.typography.titleSmall,color=AppTheme.TextDark)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=anonymous,onClick={onAnonymous(true)},enabled=!busy,label={Text("Pseudonimo OP")})
                    FilterChip(selected=!anonymous,onClick={onAnonymous(false)},enabled=!busy,label={Text("Nome e cognome")})
                }
                Text(if(anonymous) "Alla classe apparirai come OP. L’identità scelta resterà fissa in questa discussione." else "La classe vedrà il tuo nome. L’identità scelta resterà fissa in questa discussione.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
            }
            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth().toggleable(value=accepted,enabled=!busy,role=Role.Checkbox,onValueChange=onAccepted),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(accepted,onCheckedChange=null);Text("Accetto le regole e l’informativa",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextDark)
                }
                TextButton(onClick=onRules,modifier=Modifier.padding(start=36.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) { Text("Leggi regole e informativa") }
            }
            error?.let { Text(it,color=AppTheme.TintRedInk,style=MaterialTheme.typography.bodySmall) }
            Button(onClick=onPublish,enabled=!busy&&accepted&&title.trim().length in 1..180&&question.trim().length in 1..12000,
                modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),contentPadding=PaddingValues(16.dp)) {
                if(busy) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp)) }
                Text(if(busy) "Pubblicazione…" else "Pubblica nella classe")
            }
        }
    }
}
