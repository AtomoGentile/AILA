package circolareplus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import circolareplus.design.*
import circolareplus.domain.model.PublishedSeatMap
import circolareplus.domain.model.User
import circolareplus.platform.localUtcOffsetMillis
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.civilFromEpochMillis
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

/** Archivio in sola lettura: non seleziona proposte e non modifica lo storico dell'algoritmo. */
@Composable
fun SeatMapHistoryDialog(
    studentsMap: Map<String, User>,
    loadHistory: suspend () -> List<PublishedSeatMap>,
    onDismiss: () -> Unit
) {
    var history by remember { mutableStateOf<List<PublishedSeatMap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<PublishedSeatMap?>(null) }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            history = loadHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Impossibile caricare lo storico. Controlla la connessione e riprova."
        } finally {
            loading = false
        }
    }
    val back: () -> Unit = { if (selected != null) selected = null else onDismiss() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = AppTheme.BackgroundLight, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().appSafeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                ) {
                    AilaIconButton(contentDescription = "Indietro", onClick = back) { tint ->
                        AppIcons.ChevronLeft(color = tint)
                    }
                    Text(
                        if (selected == null) "Storico mappe" else "Mappa pubblicata",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected == null) AilaIconButton(
                        contentDescription = "Aggiorna storico", enabled = !loading,
                        onClick = { refresh++ }
                    ) { tint -> AppIcons.Refresh(color = tint) }
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
                        items(history, key = { it.id }) { map ->
                            AilaCard(onClick = { selected = map }) {
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
                                    AppIcons.ChevronRight(color = AppTheme.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
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
