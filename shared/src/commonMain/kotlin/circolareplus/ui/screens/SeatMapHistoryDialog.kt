package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import circolareplus.design.*
import circolareplus.domain.model.PublishedSeatMap
import circolareplus.domain.model.User
import circolareplus.platform.localUtcOffsetMillis
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.civilFromEpochMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Pubblicazioni condivise; soltanto i rappresentanti possono eliminarle. */
@Composable
fun SeatMapHistoryDialog(
    studentsMap: Map<String, User>,
    loadHistory: suspend () -> List<PublishedSeatMap>,
    canManage: Boolean,
    onDeleteMap: suspend (String) -> Unit,
    onClearHistory: suspend () -> Unit,
    onHistoryChanged: (List<PublishedSeatMap>) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var relinkId by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<PublishedSeatMap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<PublishedSeatMap?>(null) }
    var pendingDelete by remember { mutableStateOf<PublishedSeatMap?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun deletePublication(map: PublishedSeatMap?) {
        if (!canManage || deleting) return
        deleting = true
        actionError = null
        scope.launch {
            try {
                if (map == null) onClearHistory() else onDeleteMap(map.id)
                // Aggiorna subito dopo la conferma del server, anche se il ricaricamento
                // successivo fallisce: una pubblicazione eliminata non deve riapparire.
                history = if (map == null) emptyList() else history.filterNot { it.id == map.id }
                    .mapIndexed { index, entry -> entry.copy(mapIndex = index + 1) }
                selected = null
                onHistoryChanged(history)
                refresh++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = "Eliminazione non riuscita: ${e.message}"
            } finally {
                deleting = false
            }
        }
    }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            history = loadHistory()
            if (refresh > 0) onHistoryChanged(history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Impossibile caricare lo storico. Controlla la connessione e riprova."
        } finally {
            loading = false
        }
    }
    val back: () -> Unit = { if (!deleting) { if (selected != null) selected = null else onDismiss() } }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().appSafeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                ) {
                    AilaIconButton(contentDescription = "Indietro", enabled = !deleting, onClick = back) { tint ->
                        AppIcons.ChevronLeft(color = tint)
                    }
                    Text(
                        if (selected == null) "Storico mappe" else "Mappa pubblicata",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (canManage) TextButton(onClick = { creating = true; editing = true }, enabled = !loading && !deleting) { Text("Aggiungi") }
                    if (selected != null && canManage) TextButton(onClick = { creating = false; editing = true }, enabled = !deleting) { Text("Modifica") }
                    if (selected == null) AilaIconButton(
                        contentDescription = "Aggiorna storico", enabled = !loading && !deleting,
                        onClick = { refresh++ }
                    ) { tint -> AppIcons.Refresh(color = tint) }
                    if (selected != null && canManage) AilaIconButton(
                        contentDescription = "Elimina questa mappa", enabled = !loading && !deleting,
                        onClick = { pendingDelete = selected }
                    ) { tint -> AppIcons.Trash(color = tint) }
                }
                if (deleting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppTheme.PrimaryBlue)
                actionError?.let { message ->
                    Text(message, color = AppTheme.TintRedInk,
                        modifier = Modifier.padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8))
                }
                val entry = selected
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppTheme.PrimaryBlue)
                    }
                    error != null -> Column(
                        Modifier.fillMaxWidth().padding(AppTheme.Space24),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppTheme.Space16)
                    ) {
                        Text(error.orEmpty(), color = AppTheme.TextMuted)
                        AilaPrimaryButton(text = "Riprova", onClick = { refresh++ })
                    }
                    entry != null -> {
                        Text(
                            "Pubblicata il ${publicationLabel(entry.publishedAt)}",
                            modifier = Modifier.padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
                            color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium
                        )
                        Surface(
                            color = AppTheme.TintBlue,
                            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16)
                        ) {
                            Text("Lavagna e cattedra", modifier = Modifier.padding(AppTheme.Space12),
                                color = AppTheme.TintBlueInk,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        val (columns, cells) = remember(entry.id) { deskGridCells(entry.assignments) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(AppTheme.Space16),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
                            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                        ) {
                            items(cells.size) { index ->
                                val desk = cells[index]?.value
                                when {
                                    desk == null -> Spacer(Modifier)
                                    desk.isVacantDesk -> VacantDeskBlankSlot(desk)
                                    else -> SeatMapDeskCard(desk, studentsMap, showThirdSeat = desk.hasThirdSeat)
                                }
                            }
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(AppTheme.Space16),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                    ) {
                        item {
                            Text("Solo le mappe pubblicate contano per la rotazione. Le proposte e le modifiche non pubblicate sono escluse.",
                                color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (history.isEmpty()) item {
                            Text("Nessuna mappa pubblicata nello storico.", color = AppTheme.TextMuted,
                                modifier = Modifier.padding(vertical = AppTheme.Space24))
                        }
                        if (canManage) {
                            val orphanIds = history.flatMap { it.assignments }.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }.distinct().filter { it !in studentsMap }
                            items(orphanIds) { oldId ->
                                AilaCard {
                                    Column(Modifier.padding(AppTheme.Space12)) {
                                        val desk = history.flatMap { it.assignments }.first { oldId in listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }
                                        Text("Studente non più registrato · fila ${desk.row + 1}, banco ${desk.column + 1}", color = AppTheme.TextMuted)
                                        TextButton(onClick = { relinkId = oldId }) { Text("Collega allo studente registrato di nuovo") }
                                    }
                                }
                            }
                        }
                        if (canManage && history.isNotEmpty()) item {
                            AilaDestructiveButton(
                                text = if (deleting) "Eliminazione in corso…" else "Elimina storico mappe",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { if (!deleting) confirmClear = true }
                            )
                        }
                        items(history, key = { it.id }) { map ->
                            AilaCard(onClick = if (deleting) null else ({ selected = map })) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (map.mapIndex == 1) "Mappa attuale" else "Mappa precedente ${map.mapIndex - 1}",
                                            fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                                        Text(publicationLabel(map.publishedAt), color = AppTheme.TextMuted,
                                            style = MaterialTheme.typography.bodySmall)
                                        val occupied = map.assignments.sumOf {
                                            listOfNotNull(it.studentAId, it.studentBId, it.studentCId).size
                                        }
                                        Text("$occupied studenti", color = AppTheme.TextMuted,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (canManage) AilaIconButton(
                                        contentDescription = "Elimina mappa del ${publicationLabel(map.publishedAt)}",
                                        enabled = !deleting,
                                        onClick = { pendingDelete = map }
                                    ) { tint -> AppIcons.Trash(color = tint) }
                                    AppIcons.ChevronRight(color = AppTheme.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editing) HistoricalMapEditor(
            initial = if (creating) selected?.assignments ?: history.firstOrNull()?.assignments.orEmpty() else selected?.assignments.orEmpty(),
            studentsMap = studentsMap,
            isNew = creating,
            onDismiss = { editing = false },
            onSave = { layout, date ->
                circolareplus.data.AppContainer.seatMapRepository.saveHistoricalMap(if (creating) null else selected?.id, layout, date)
                editing = false
                selected = null
                refresh++
            }
        )
        relinkId?.let { previousId ->
            AlertDialog(onDismissRequest = { relinkId = null }, title = { Text("Collega account ricreato") },
                text = {
                    LazyColumn {
                        actionError?.let { message -> item { Text(message, color = AppTheme.TintRedInk) } }
                        items(studentsMap.values.filter { it.username.isNotBlank() }, key = { it.id }) { student ->
                            TextButton(enabled = !deleting, onClick = {
                                deleting = true
                                scope.launch {
                                    try {
                                        circolareplus.data.AppContainer.seatMapRepository.relinkStudent(previousId, student.id)
                                        relinkId = null
                                        selected = null
                                        refresh++
                                    } catch (e: CancellationException) { throw e
                                    } catch (e: Exception) { actionError = e.message
                                    } finally { deleting = false }
                                }
                            }) { Text("${student.firstName} ${student.lastName}") }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { relinkId = null }) { Text("Chiudi") } })
        }
        pendingDelete?.let { map ->
            AilaConfirmDialog(
                title = "Eliminare questa mappa?",
                message = "La pubblicazione del ${publicationLabel(map.publishedAt)} non conterà più per la rotazione delle file. " +
                    (if (map.mapIndex == 1) "La mappa precedente, se presente, diventerà quella attuale. " else "") +
                    "L'azione non può essere annullata.",
                onDismiss = { pendingDelete = null },
                onConfirm = { pendingDelete = null; deletePublication(map) },
                confirmLabel = "Elimina"
            )
        }
        if (confirmClear) AilaConfirmDialog(
            title = "Eliminare lo storico?",
            message = "Tutte le mappe dell'archivio, compresa quella attualmente pubblicata, saranno eliminate. " +
                "L'algoritmo non terrà più conto delle disposizioni passate. L'azione non può essere annullata.",
            onDismiss = { confirmClear = false },
            onConfirm = { confirmClear = false; deletePublication(null) },
            confirmLabel = "Elimina"
        )
    }
}

/** D1 restituisce UTC senza suffisso: la data mostrata usa il fuso del dispositivo. */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun publicationLabel(timestamp: String): String = runCatching {
    val normalized = if ('T' !in timestamp) timestamp.replace(' ', 'T') + "Z" else timestamp
    val millis = Instant.parse(normalized).toEpochMilliseconds()
    val localMillis = millis + localUtcOffsetMillis(millis)
    val date = civilFromEpochMillis(localMillis)
    val minutes = ((localMillis % 86_400_000L) / 60_000L).toInt()
    val time = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
    "${date.day} ${ITALIAN_MONTHS[date.month].lowercase()} ${date.year} · $time"
}.getOrElse { timestamp }
