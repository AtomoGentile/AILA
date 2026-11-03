package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

@Composable
internal fun PollAssignmentPreviewDialog(
    pollId: String,
    subject: String,
    loadPreview: suspend (String?) -> PollAssignmentPreviewDto,
    publishRevision: (suspend (PollAssignmentPreviewDto) -> Unit)? = null,
    restoreRevision: (suspend (PollAssignmentPreviewDto) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var basis by remember(pollId) { mutableStateOf<String?>(null) }
    var preview by remember(pollId) { mutableStateOf<PollAssignmentPreviewDto?>(null) }
    var loading by remember(pollId) { mutableStateOf(true) }
    var error by remember(pollId) { mutableStateOf<String?>(null) }
    var refresh by remember(pollId) { mutableStateOf(0) }
    var onlyChanges by remember(pollId) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var busy by remember(pollId) { mutableStateOf(false) }
    var actionError by remember(pollId) { mutableStateOf<String?>(null) }
    var actionMessage by remember(pollId) { mutableStateOf<String?>(null) }
    var confirmation by remember(pollId) { mutableStateOf<PollAssignmentPreviewDto?>(null) }
    var restoring by remember(pollId) { mutableStateOf(false) }
    confirmation?.let { data ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (restoring) "Ripristinare il calendario precedente?" else "Pubblicare il calendario rivisto?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                Text(if (restoring) "Verranno ripristinati i posti e le date presenti prima della revisione. Il ripristino si ferma se nel frattempo sono cambiati dati, scambi o bonus."
                    else "${data.moved} studenti cambieranno data: ${data.improved} migliorerebbero e ${data.worsened} peggiorerebbero. Il nuovo calendario sarà visibile alla classe.")
                Text(if (data.bonusPolicy == "RECONCILE") "I bonus saranno riconciliati usando la fotografia iniziale, senza ripetere gli accrediti."
                    else "I saldi dei bonus attuali resteranno invariati: i dati storici disponibili non consentono una riconciliazione certa per questa modalità.")
                if (!restoring) Text("Gli scambi ancora in attesa saranno annullati. La versione precedente resterà disponibile per il ripristino.")
                Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    (if (restoring) data.restoreAssignments else data.assignments).filter { it.changed }.forEach { row ->
                        Text("${row.studentName}: ${formatDayMonth(row.beforeDate)} → ${formatDayMonth(row.afterDate)}")
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                busy = true
                actionError = null
                actionMessage = null
                scope.launch {
                    try {
                        if (restoring) restoreRevision?.invoke(data) else publishRevision?.invoke(data)
                        actionMessage = if (restoring) "Calendario precedente ripristinato." else "Calendario rivisto pubblicato."
                        refresh++
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { actionError = e.message ?: "Operazione non riuscita. Aggiorna e riprova."
                    } finally { busy = false }
                }
            }) { Text(if (restoring) "Ripristina" else "Pubblica") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Annulla") } }
        )
    }
    LaunchedEffect(pollId, refresh) {
        loading = true
        error = null
        preview = null
        try { preview = loadPreview(null)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.message ?: "Impossibile caricare il confronto. Riprova."
        } finally { loading = false }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().appSafeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(AppTheme.Space16), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                    AilaIconButton(contentDescription = "Chiudi anteprima", enabled = !busy, onClick = onDismiss) { tint -> AppIcons.ChevronLeft(color = tint) }
                    Column(Modifier.weight(1f)) {
                        Text("Anteprima nuovo algoritmo", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text(subject, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                    AilaIconButton(contentDescription = "Aggiorna confronto", enabled = !loading && !busy, onClick = { actionError = null; refresh++ }) { tint -> AppIcons.Refresh(color = tint) }
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
                                        Text("Confronto prima della pubblicazione", color = AppTheme.TintBlueInk, fontWeight = FontWeight.SemiBold)
                                        Text("Il confronto non cambia i dati finché non confermi la pubblicazione.", color = AppTheme.TextMuted)
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
                                            if (data.originalAvailable) FilterChip(enabled = !busy, selected = data.basis == "ORIGINAL", onClick = { basis = "ORIGINAL" }, label = { Text("Bonus iniziali") })
                                            FilterChip(enabled = !busy, selected = data.basis == "CURRENT_BONUSES", onClick = { basis = "CURRENT_BONUSES" }, label = { Text("Bonus attuali") })
                                            FilterChip(enabled = !busy, selected = data.basis == "VOTES_ONLY", onClick = { basis = "VOTES_ONLY" }, label = { Text("Solo voti") })
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
                                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                    actionMessage?.let { Text(it, color = AppTheme.TintGreenInk) }
                                    actionError?.let { Text(it, color = AppTheme.TintRedInk) }
                                    if (data.activeRevisionId != null && restoreRevision != null) {
                                        Text("Questa revisione è pubblicata. Per pubblicare un confronto diverso, ripristina prima la versione precedente.", color = AppTheme.TextMuted)
                                        if (data.canRestore && !busy && data.revisionToken != null) AilaSecondaryButton(
                                            text = "Ripristina calendario precedente", modifier = Modifier.fillMaxWidth(),
                                            onClick = { restoring = true; confirmation = data })
                                        else Text(if (busy) "Operazione in corso…" else "Il ripristino automatico non è disponibile: dopo la pubblicazione sono cambiati dati o lo storico è incompleto.", color = AppTheme.TintAmberInk)
                                    } else if (data.canPublish && publishRevision != null && data.revisionToken != null) {
                                        Text(if (data.bonusPolicy == "RECONCILE") "Pubblicando, i bonus saranno riconciliati con quelli iniziali."
                                            else "Pubblicando, i saldi dei bonus attuali resteranno invariati.", color = AppTheme.TextMuted)
                                        AilaPrimaryButton(text = if (busy) "Pubblicazione…" else "Pubblica calendario rivisto",
                                            enabled = !busy && data.moved > 0, modifier = Modifier.fillMaxWidth(),
                                            onClick = { restoring = false; confirmation = data })
                                        if (data.moved == 0) Text("Nessuna data da modificare in questo confronto.", color = AppTheme.TextMuted)
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
