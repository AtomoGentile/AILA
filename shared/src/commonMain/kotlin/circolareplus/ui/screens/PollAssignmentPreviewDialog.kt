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
import circolareplus.data.remote.dto.PollAssignmentPreviewDto
import circolareplus.data.remote.dto.PollPreviewAssignmentDto
import circolareplus.design.*
import circolareplus.util.formatDayMonth
import kotlinx.coroutines.CancellationException

@Composable
internal fun PollAssignmentPreviewDialog(
    pollId: String,
    subject: String,
    loadPreview: suspend (String?) -> PollAssignmentPreviewDto,
    onDismiss: () -> Unit
) {
    var basis by remember(pollId) { mutableStateOf<String?>(null) }
    var preview by remember(pollId) { mutableStateOf<PollAssignmentPreviewDto?>(null) }
    var loading by remember(pollId) { mutableStateOf(true) }
    var error by remember(pollId) { mutableStateOf<String?>(null) }
    var refresh by remember(pollId) { mutableStateOf(0) }
    var onlyChanges by remember(pollId) { mutableStateOf(true) }
    LaunchedEffect(pollId, refresh) {
        loading = true
        error = null
        preview = null
        try { preview = loadPreview(null)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.message ?: "Impossibile caricare il confronto. Riprova."
        } finally { loading = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().appSafeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(AppTheme.Space16), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                    AilaIconButton(contentDescription = "Chiudi anteprima", onClick = onDismiss) { tint -> AppIcons.ChevronLeft(color = tint) }
                    Column(Modifier.weight(1f)) {
                        Text("Anteprima nuovo algoritmo", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text(subject, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                    AilaIconButton(contentDescription = "Aggiorna confronto", enabled = !loading, onClick = { refresh++ }) { tint -> AppIcons.Refresh(color = tint) }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppTheme.PrimaryBlue) }
                    error != null -> Column(Modifier.padding(AppTheme.Space24), verticalArrangement = Arrangement.spacedBy(AppTheme.Space16)) {
                        Text(error.orEmpty(), color = AppTheme.TintRedInk)
                        AilaPrimaryButton(text = "Riprova", onClick = { refresh++ })
                    }
                    else -> preview?.let { generation ->
                        val data = (listOf(generation) + generation.comparisons).firstOrNull { it.basis == basis } ?: generation
                        val shown = data.assignments.filter { !onlyChanges || it.changed }
                        LazyColumn(contentPadding = PaddingValues(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                            item {
                                AilaCard {
                                    Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                        Text("Solo anteprima", color = AppTheme.TintBlueInk, fontWeight = FontWeight.SemiBold)
                                        Text("Il confronto non cambia date, calendario o bonus.", color = AppTheme.TextMuted)
                                        Text(when (data.basis) {
                                            "ORIGINAL" -> "Usa i voti e i bonus iniziali salvati al momento del calcolo."
                                            "CURRENT_BONUSES" -> "Usa i bonus attuali, fissati quando generi questa anteprima."
                                            else -> "Simulazione basata sui soli voti, senza bonus."
                                        }, color = AppTheme.TextDark)
                                        if (data.basis == "CURRENT_BONUSES") Text(
                                            "I bonus restano fissi mentre guardi il confronto. Aggiorna genera una nuova simulazione con i valori di quel momento.",
                                            color = AppTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
                                        if (!data.originalAvailable) Text(
                                            "I bonus iniziali di questo vecchio calcolo non sono disponibili. I bonus attuali sono una simulazione, non una ricostruzione storica.",
                                            color = AppTheme.TintAmberInk, style = MaterialTheme.typography.bodySmall)
                                        if (data.historicalDataIncomplete) Text("Sono considerati soltanto gli studenti e i dati ancora presenti.", color = AppTheme.TintAmberInk)
                                        if (data.acceptedSwaps > 0) Text("Il calendario attuale include ${data.acceptedSwaps} scambi accettati.", color = AppTheme.TextMuted)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                            if (data.originalAvailable) FilterChip(selected = data.basis == "ORIGINAL", onClick = { basis = "ORIGINAL" }, label = { Text("Bonus iniziali") })
                                            FilterChip(selected = data.basis == "CURRENT_BONUSES", onClick = { basis = "CURRENT_BONUSES" }, label = { Text("Bonus attuali") })
                                            FilterChip(selected = data.basis == "VOTES_ONLY", onClick = { basis = "VOTES_ONLY" }, label = { Text("Solo voti") })
                                        }
                                    }
                                }
                            }
                            item {
                                AilaCard {
                                    Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                        Text("Attuale → anteprima", style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
                                        PreviewCount("Verdi", data.before.green, data.after.green)
                                        PreviewCount("Gialli", data.before.yellow, data.after.yellow)
                                        PreviewCount("Rossi chiari", data.before.redLight, data.after.redLight)
                                        PreviewCount("Rossi scuri", data.before.redDark, data.after.redDark)
                                        PreviewCount("Senza voto sulla data", data.before.missing, data.after.missing)
                                        HorizontalDivider(color = AppTheme.Hairline)
                                        Text("${data.moved} cambierebbero data · ${data.totalStudents - data.moved} resterebbero nella stessa data", color = AppTheme.TextDark)
                                        Text("${data.improved} migliorerebbero · ${data.worsened} peggiorerebbero",
                                            color = if (data.worsened > 0) AppTheme.TintRedInk else AppTheme.TintGreenInk)
                                    }
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (onlyChanges) "Date che cambierebbero" else "Tutti gli studenti", modifier = Modifier.weight(1f), color = AppTheme.TextDark)
                                    TextButton(onClick = { onlyChanges = !onlyChanges }) { Text(if (onlyChanges) "Mostra tutti" else "Solo cambiamenti") }
                                }
                            }
                            if (shown.isEmpty()) item { Text("Nessuna data cambierebbe con questa simulazione.", color = AppTheme.TextMuted) }
                            items(shown, key = { it.studentId }) { assignment -> PreviewStudent(assignment) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCount(label: String, before: Int, after: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppTheme.TextMuted)
        Text("$before → $after", color = AppTheme.TextDark)
    }
}

private fun previewVoteLabel(vote: Int?) = when (vote) {
    50 -> "Verde"; 0 -> "Giallo · indifferente"; -60 -> "Rosso chiaro"; -300 -> "Rosso scuro"; else -> "Nessun voto espresso"
}

@Composable
private fun PreviewStudent(row: PollPreviewAssignmentDto) {
    AilaCard {
        Column(Modifier.padding(AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            Text(row.studentName, style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            Text("Bonus usato nel confronto: ${row.bonusUsed}", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            Text("Attuale: ${formatDayMonth(row.beforeDate)} · ${previewVoteLabel(row.beforeVote)}", color = AppTheme.TextMuted)
            Text("Anteprima: ${formatDayMonth(row.afterDate)} · ${previewVoteLabel(row.afterVote)}", color = AppTheme.TextDark)
            Text(when (row.preferenceChange) { "IMPROVED" -> "Voto migliore sulla nuova data"; "WORSENED" -> "Voto peggiore sulla nuova data"; else -> "Punteggio equivalente" },
                color = when (row.preferenceChange) { "IMPROVED" -> AppTheme.TintGreenInk; "WORSENED" -> AppTheme.TintRedInk; else -> AppTheme.TextMuted },
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
