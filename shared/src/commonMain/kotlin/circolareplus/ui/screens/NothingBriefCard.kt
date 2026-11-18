package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import circolareplus.design.*
import circolareplus.util.today

/** A monochrome editorial feed: a single surface, no nested Material cards. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NothingBriefCard(
    items: List<AilaBriefItemDto>, onDismiss: (AilaBriefItemDto) -> Unit,
    onOpen: (AilaBriefItemDto) -> Unit, onStudy: (String) -> Unit,
    loading: Boolean, error: String?, onRefresh: () -> Unit, modifier: Modifier,
    subjectColorsEnabled: Boolean, subjectColorOverrides: Map<String, String>,
    conflicts: List<CalendarConflict>, onOpenConflict: (CalendarConflict) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val entries = items.distinctBy { it.id }
    val visible = if(expanded) entries else entries.take(3)
    val mono = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AppTheme.SurfaceWhite)
        .animateContentSize(tween(if(AppTheme.reduceMotion) 0 else 180)), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Column(Modifier.fillMaxWidth().background(AppTheme.TextDark).padding(NothingSpacing.Edge), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(8.dp).background(AppTheme.PrimaryBlue, CircleShape))
                Text("AILA / PERSONALE", Modifier.weight(1f), color = AppTheme.BackgroundLight, style = mono)
                Text(entries.size.toString().padStart(2,'0'), color = AppTheme.BackgroundLight, style = mono)
            }
            Text("Brief.", style = nothingDisplayStyle(38, 48), color = AppTheme.BackgroundLight)
            Text("Le tue prossime mosse.", color = AppTheme.BackgroundLight, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.padding(horizontal = NothingSpacing.Edge), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if(loading) Text("Aggiornamento in corso…", Modifier.padding(vertical = 20.dp), color = AppTheme.TextMuted)
            if(error != null) {
                Text(error, Modifier.padding(top = 20.dp), color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                AilaTextButton(onClick = onRefresh) { Text("Riprova") }
            }
            conflicts.forEach { conflict ->
                CalendarConflictNotice(conflict, includeDate = true, onClick = { onOpenConflict(conflict) })
            }
            if(conflicts.isEmpty() && entries.isEmpty() && !loading && error == null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("00", color = AppTheme.TextDark, style = nothingDisplayStyle(36,46))
                    Column {
                        Text("Sei in pari.", color = AppTheme.TextDark, style = MaterialTheme.typography.titleMedium)
                        Text("Le nuove voci appariranno qui.", color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            visible.forEachIndexed { index, item ->
                key(item.id) {
                    Column(Modifier.fillMaxWidth().padding(top = NothingSpacing.Item, bottom = NothingSpacing.Item), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                            Text((index+1).toString().padStart(2,'0'), color = AppTheme.TextDark, style = nothingDisplayStyle(22,30))
                            Text(nothingBriefLabel(item), Modifier.weight(1f), color = AppTheme.TextMuted, style = mono)
                            if(subjectColorsEnabled && item.kind == AilaBriefKind.EVENT) {
                                val subject = SubjectColors.choiceForSubject(item.title, subjectColorOverrides)
                                Box(Modifier.width(3.dp).height(20.dp).background(subject?.color ?: AppTheme.TextMuted, CircleShape))
                            }
                            AilaIconButton("Non ora: ${item.title}", { onDismiss(item) }) {
                                AppIcons.Close(Modifier.size(16.dp), AppTheme.TextMuted)
                            }
                        }
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, color = if(item.kind == AilaBriefKind.CIRCULAR && !item.isActive) AppTheme.TextMuted else AppTheme.TextDark,
                                style = MaterialTheme.typography.titleMedium)
                            if(item.message.isNotBlank() && item.kind != AilaBriefKind.EVENT) Text(item.message,
                                color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                        // Actions wrap on narrow displays and with large system fonts.
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Section), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                            AilaTextButton(onClick = { onOpen(item) }, contentPadding = PaddingValues(vertical = NothingSpacing.Inline)) {
                                Text(item.actionLabel.ifBlank { "Apri" }, color = AppTheme.TextDark)
                                Spacer(Modifier.width(10.dp))
                                AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextDark)
                            }
                            if(item.canPlanStudy && item.kind == AilaBriefKind.EVENT) AilaTextButton(onClick = { onStudy("Prepara un piano di studio per ${item.title}${item.date?.let { ", in programma il $it" }.orEmpty()}. Usa i miei impegni reali e non creare eventi senza conferma.") }, contentPadding = PaddingValues(vertical = NothingSpacing.Inline)) {
                                Text("Pianifica studio", color = AppTheme.TextDark)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
                }
            }
            if(entries.size > 3) AilaTextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if(expanded) "Mostra meno" else "Tutte le ${entries.size} voci", color = AppTheme.TextDark)
            }
            if(entries.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(vertical = NothingSpacing.Content), verticalAlignment = Alignment.CenterVertically) {
                repeat(9) { dot ->
                    Box(Modifier.padding(end = 4.dp).size(3.dp).background(if(dot == 0) AppTheme.PrimaryBlue else AppTheme.TextFaint, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                Text("FINE / ${visible.size.toString().padStart(2,'0')}", color = AppTheme.TextMuted, style = mono)
            }
        }
    }
}

private fun nothingBriefLabel(item: AilaBriefItemDto): String = when(item.kind) {
    AilaBriefKind.EVENT -> when(item.date) {
        today().toIso() -> "OGGI"
        null -> "CALENDARIO"
        else -> item.date.orEmpty().split('-').let { parts ->
            if(parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else item.date.orEmpty()
        }
    }
    AilaBriefKind.CIRCULAR -> "CIRCOLARE N. ${item.sourceId}"
    AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> if(item.isResult) "RISULTATI" else "SONDAGGIO APERTO"
    AilaBriefKind.FORUM -> "REVISIONE TRA PERE"
    AilaBriefKind.PROPOSAL -> "BACHECA"
    AilaBriefKind.SEATMAP -> "POSTI"
}
