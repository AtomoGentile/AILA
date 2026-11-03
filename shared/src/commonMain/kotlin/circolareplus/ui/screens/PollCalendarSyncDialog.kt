package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import circolareplus.data.remote.dto.PollCalendarSyncPreviewDto
import circolareplus.design.*
import circolareplus.util.formatDayMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PollCalendarSyncDialog(
    subject: String,
    loadPreview: suspend () -> PollCalendarSyncPreviewDto,
    synchronize: suspend (PollCalendarSyncPreviewDto, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var preview by remember { mutableStateOf<PollCalendarSyncPreviewDto?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) {
        loading = true
        preview = null
        error = null
        try {
            preview = loadPreview()
            selected = preview!!.previousEvents.filter { it.selectedByDefault }.map { it.id }.toSet()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.message ?: "Impossibile leggere il calendario."
        } finally { loading = false }
    }
    if (confirm) preview?.let { data ->
        AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Aggiornare il calendario?") },
            text = { Text("Saranno aggiornate ${data.dates.size} date del sondaggio e sostituiti ${selected.size} vecchi eventi selezionati. Gli altri eventi resteranno nel calendario. Assegnazioni e bonus non cambiano.") },
            confirmButton = { TextButton(onClick = {
                confirm = false
                busy = true
                error = null
                scope.launch {
                    try {
                        synchronize(data, selected.toList())
                        message = "Calendario aggiornato."
                        refresh++
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { error = e.message ?: "Aggiornamento non riuscito."
                    } finally { busy = false }
                }
            }) { Text("Aggiorna calendario") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } })
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().appSafeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(AppTheme.Space16), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                    AilaIconButton(contentDescription = "Chiudi", enabled = !busy, onClick = onDismiss) { tint -> AppIcons.ChevronLeft(color = tint) }
                    Column(Modifier.weight(1f)) {
                        Text("Aggiorna calendario", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text(subject, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                    AilaIconButton(contentDescription = "Ricarica calendario", enabled = !loading && !busy, onClick = { message = null; refresh++ }) { tint -> AppIcons.Refresh(color = tint) }
                }
                if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                    item {
                        Text("Le date pubblicate del sondaggio aggiorneranno gli eventi già collegati. Seleziona anche le vecchie date da sostituire: quelle non selezionate resteranno presenti.", color = AppTheme.TextMuted)
                        message?.let { Text(it, color = AppTheme.TintGreenInk) }
                        error?.let { Text(it, color = AppTheme.TintRedInk) }
                    }
                    preview?.let { data ->
                        item { Text("Date da riportare nel calendario", fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark) }
                        items(data.dates) { date ->
                            AilaCard { Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                Text("${formatDayMonth(date.date)} ${date.date.take(4)}", fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                                Text(date.notes, color = AppTheme.TextMuted)
                            } }
                        }
                        item { Text("Vecchi eventi da sostituire (${selected.size})", fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark) }
                        if (data.previousEvents.isEmpty()) item { Text("Nessuna vecchia data da selezionare. Gli eventi collegati (${data.linkedCount}) saranno aggiornati senza duplicarli.", color = AppTheme.TextMuted) }
                        items(data.previousEvents, key = { it.id }) { event ->
                            AilaCard { Row(Modifier.padding(AppTheme.Space12).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = event.id in selected, enabled = !busy,
                                    onCheckedChange = { checked -> selected = if (checked) selected + event.id else selected - event.id })
                                Column(Modifier.weight(1f)) {
                                    Text("${formatDayMonth(event.date)} ${event.date.take(4)}" + (event.startTime?.let { " · $it" } ?: ""), color = AppTheme.TextDark)
                                    Text(event.notes ?: "Senza note", color = AppTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            } }
                        }
                    }
                }
                AilaPrimaryButton(text = if (busy) "Aggiornamento…" else "Aggiorna calendario",
                    enabled = !loading && !busy && preview != null, modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
                    onClick = { confirm = true })
            }
        }
    }
}
