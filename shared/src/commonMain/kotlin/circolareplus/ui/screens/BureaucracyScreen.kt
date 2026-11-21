@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.dto.BureaucracyDeadlineDto
import circolareplus.data.remote.dto.BureaucracyDeliveriesDto
import circolareplus.data.remote.dto.BureaucracyDeliveryDto
import circolareplus.data.remote.dto.BureaucracyFeedDto
import circolareplus.data.remote.dto.CreateBureaucracyPracticeDto
import circolareplus.data.remote.dto.SetBureaucracyDeliveryDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.design.*
import circolareplus.platform.AppForeground
import circolareplus.platform.PlatformBackHandler
import circolareplus.util.CivilDate
import circolareplus.util.civilFromEpochMillis
import circolareplus.util.daysBetween
import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.parseIsoDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

private const val PRACTICES = "/api/burocrazia"

@Composable
internal fun BureaucracyScreen(
    api: ApiClient,
    studentName: String,
    active: Boolean,
    initialPracticeId: String? = null,
    onPracticesRead: (BureaucracyFeedDto) -> Unit = {},
    onBackClick: () -> Unit
) {
    var feed by remember { mutableStateOf<BureaucracyFeedDto?>(null) }
    var deliveries by remember { mutableStateOf<List<BureaucracyDeliveryDto>?>(null) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(initialPracticeId) { mutableStateOf(initialPracticeId) }
    var revision by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingDeadline by rememberSaveable { mutableStateOf(false) }
    var confirmingArchive by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val practice = feed?.practices?.firstOrNull { it.id == selection }
    val canManage = feed?.canManage == true

    LaunchedEffect(active, AppForeground.isForeground, revision, selection) {
        if (!active || !AppForeground.isForeground) return@LaunchedEffect
        do {
            try {
                val updated = api.get<BureaucracyFeedDto>(PRACTICES, offlineCopy = false)
                feed = updated
                onPracticesRead(updated)
                val selected = updated.practices.firstOrNull { it.id == selection }
                if (updated.canManage && selected != null) {
                    deliveries = api.get<BureaucracyDeliveriesDto>(
                        "$PRACTICES/${selected.id}/deliveries", offlineCopy = false
                    ).deliveries
                } else {
                    if (selected == null) selection = null
                    deliveries = null
                }
                loadError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = (e as? ApiException)?.message ?: "Impossibile aggiornare le pratiche."
            }
            if (feed?.canManage == true) break
            delay(15_000)
        } while (true)
    }

    fun save(change: suspend () -> Unit, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                change()
                onDone()
                revision++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.statusCode in listOf(403, 404, 409)) revision++
                actionError = (e as? ApiException)?.message ?: "Impossibile salvare la modifica."
            } finally {
                busy = false
            }
        }
    }

    val back: () -> Unit = {
        if (selection != null) {
            selection = null
            deliveries = null
            loadError = null
            actionError = null
        } else {
            onBackClick()
        }
    }
    PlatformBackHandler(enabled = active && !creating && !confirmingArchive && !editingDeadline, onBack = back)

    val gutter = if (AppTheme.isNothing) NothingSpacing.Edge else AppTheme.Space16
    val shown = feed?.practices.orEmpty().filter { (it.archivedAt != null) == archived }
    Column(
        Modifier.fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        AilaBackBar(title = practice?.title ?: "Burocrazia", onBackClick = back) {
            AilaIconButton("Aggiorna", { revision++ }, enabled = !busy) {
                AppIcons.Refresh(Modifier.size(19.dp), it)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                loadError?.let { error ->
                    item {
                        AilaErrorState(error, onRetry = { revision++ })
                    }
                }
                actionError?.takeIf { !creating && !confirmingArchive && !editingDeadline }?.let { error ->
                    item { Text(error, color = AppTheme.TintRedInk, style = MaterialTheme.typography.bodyMedium) }
                }
                when {
                    feed == null -> if (loadError == null) {
                        item { PracticeLoader("Caricamento pratiche…") }
                    }
                    canManage && practice != null -> {
                        val closed = practice.archivedAt != null
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(practice.dueDate?.let(::formatItalianDateWithWeekday) ?: "Nessuna scadenza",
                                    Modifier.weight(1f), color = AppTheme.TextMuted)
                                if (!closed) AilaTextButton({ actionError = null; editingDeadline = true }, enabled = !busy) {
                                    Text(if (practice.dueDate == null) "Aggiungi scadenza" else "Modifica scadenza")
                                }
                            }
                        }
                        if (practice.description.isNotBlank()) {
                            item { Text(practice.description, color = AppTheme.TextMuted) }
                        }
                        val roster = deliveries
                        if (roster != null) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${roster.count { it.received }} su ${roster.size} consegnate",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AppTheme.TextDark
                                    )
                                    if (!closed) {
                                        AilaTextButton(
                                            { actionError = null; confirmingArchive = true },
                                            enabled = !busy
                                        ) { Text("Archivia") }
                                    }
                                }
                            }
                            items(roster, key = { it.studentId }) { person ->
                                val rowModifier = if (closed) Modifier else Modifier.toggleable(
                                    value = person.received,
                                    enabled = !busy,
                                    role = Role.Checkbox,
                                    onValueChange = { received ->
                                        save(change = {
                                            api.put<SetBureaucracyDeliveryDto, SuccessDto>(
                                                "$PRACTICES/${practice.id}/deliveries/${person.studentId}",
                                                SetBureaucracyDeliveryDto(received)
                                            )
                                        }, onDone = {
                                            deliveries = deliveries?.map {
                                                if (it.studentId == person.studentId) it.copy(received = received) else it
                                            }
                                        })
                                    }
                                )
                                AilaCard {
                                    AilaListRow(
                                        title = person.name,
                                        subtitle = if (person.received) "Consegnata" else "Da consegnare",
                                        modifier = rowModifier,
                                        trailing = {
                                            if (closed) DeliveryMark(person.received)
                                            else AilaCheckbox(
                                                checked = person.received,
                                                enabled = !busy,
                                                modifier = Modifier.clearAndSetSemantics {},
                                                onCheckedChange = null
                                            )
                                        }
                                    )
                                }
                            }
                        } else if (loadError == null) {
                            item { PracticeLoader("Caricamento consegne…") }
                        }
                    }
                    practice != null -> item {
                        AilaCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(practice.title, style = MaterialTheme.typography.titleLarge)
                                if (practice.description.isNotBlank()) Text(practice.description, color = AppTheme.TextMuted)
                                practice.dueDate?.let { Text("Scadenza · ${formatItalianDateWithWeekday(it)}", color = AppTheme.TextMuted) }
                                Text(if (practice.received == true) "Consegnata" else "Da consegnare")
                            }
                        }
                    }
                    else -> {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AilaSegmentedTabs(
                                    labels = listOf("Attive", "Archivio"),
                                    selectedIndex = if (archived) 1 else 0,
                                    onSelect = { archived = it == 1 },
                                    modifier = Modifier.weight(1f)
                                )
                                if (canManage) {
                                    AilaIconButton("Nuova pratica", { actionError = null; creating = true }, primary = true, enabled = !busy) {
                                        AppIcons.Plus(Modifier.size(20.dp), it)
                                    }
                                }
                            }
                        }
                        if (shown.isEmpty()) {
                            item {
                                AilaEmptyState(if (archived) "Nessuna pratica archiviata" else "Nessuna pratica attiva")
                            }
                        }
                        items(shown, key = { it.id }) { item ->
                            val open: (() -> Unit)? = if (!busy) {
                                { deliveries = null; actionError = null; selection = item.id }
                            } else null
                            AilaCard(onClick = open) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AilaIconTile(tint = AppTheme.TintBlue) {
                                        AppIcons.Document(Modifier.size(20.dp), AppTheme.TintBlueInk)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                                        item.dueDate?.let { Text("Scadenza · ${formatItalianDateWithWeekday(it)}", color = AppTheme.TextMuted) }
                                        if (item.description.isNotBlank()) {
                                            Text(
                                                item.description,
                                                color = AppTheme.TextMuted,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = if (canManage) 2 else Int.MAX_VALUE
                                            )
                                        }
                                    }
                                    if (canManage) AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextFaint)
                                }
                                item.received?.let { received ->
                                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.Hairline)
                                    AilaListRow(
                                        title = studentName,
                                        subtitle = if (received) "Consegnata" else "Da consegnare",
                                        trailing = { DeliveryMark(received) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (canManage && creating) {
        AilaBottomSheet(onDismissRequest = { if (!busy) creating = false }) {
            var title by rememberSaveable { mutableStateOf("") }
            var description by rememberSaveable { mutableStateOf("") }
            var dueDate by rememberSaveable { mutableStateOf<String?>(null) }
            PracticeSheetBody("Nuova pratica", actionError) {
                AilaPillTextField(title, { title = it.take(120) }, "Titolo della pratica", Modifier.fillMaxWidth(), singleLine = true)
                AilaPillTextField(
                    description, { description = it.take(1000) }, "Istruzioni (facoltative)",
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), maxLines = 4
                )
                PracticeDeadlineField(dueDate, { dueDate = it }, enabled = !busy)
                AilaPrimaryButton(
                    if (busy) "Apertura in corso…" else "Apri pratica",
                    enabled = title.isNotBlank() && !busy,
                    fillMaxWidth = true,
                    onClick = {
                        save(change = {
                            api.post<CreateBureaucracyPracticeDto, JsonObject>(
                                PRACTICES, CreateBureaucracyPracticeDto(title.trim(), description.trim(), dueDate)
                            )
                        }, onDone = { creating = false; archived = false })
                    }
                )
            }
        }
    }
    if (canManage && editingDeadline && practice != null && practice.archivedAt == null) {
        AilaBottomSheet(onDismissRequest = { if (!busy) editingDeadline = false }) {
            var dueDate by rememberSaveable(practice.id) { mutableStateOf(practice.dueDate) }
            PracticeSheetBody("Scadenza della pratica", actionError) {
                PracticeDeadlineField(dueDate, { dueDate = it }, enabled = !busy)
                AilaPrimaryButton(
                    "Salva scadenza", fillMaxWidth = true, enabled = !busy && dueDate != practice.dueDate,
                    onClick = {
                        save(change = {
                            api.put<BureaucracyDeadlineDto, SuccessDto>(
                                "$PRACTICES/${practice.id}/deadline", BureaucracyDeadlineDto(dueDate)
                            )
                        }, onDone = { editingDeadline = false })
                    }
                )
            }
        }
    }
    if (canManage && confirmingArchive && practice != null) {
        AilaBottomSheet(onDismissRequest = { if (!busy) confirmingArchive = false }) {
            PracticeSheetBody("Archivia pratica", actionError) {
                Text("«${practice.title}» passerà nell’archivio. Lo stato delle consegne resterà consultabile.", color = AppTheme.TextMuted)
                AilaPrimaryButton(
                    if (busy) "Archiviazione in corso…" else "Archivia",
                    enabled = !busy,
                    fillMaxWidth = true,
                    onClick = {
                        save(change = {
                            api.put<JsonObject, SuccessDto>("$PRACTICES/${practice.id}/archive", JsonObject(emptyMap()))
                        }, onDone = {
                            confirmingArchive = false
                            archived = true
                            selection = null
                            deliveries = null
                        })
                    }
                )
                AilaTextButton({ confirmingArchive = false }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Annulla")
                }
            }
        }
    }
}

@Composable
private fun PracticeDeadlineField(date: String?, onChange: (String?) -> Unit, enabled: Boolean) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AilaTextButton({ picking = true }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(date?.let(::formatItalianDateWithWeekday) ?: "Scegli la scadenza (facoltativa)")
        }
        if (date != null) AilaIconButton("Rimuovi scadenza", { onChange(null) }, enabled = enabled) {
            AppIcons.Close(Modifier.size(18.dp), it)
        }
    }
    if (picking) {
        val initialDate = date?.let(::parseIsoDate)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.let { daysBetween(CivilDate(1970, 1, 1), it) * 86_400_000L }
        )
        AilaDatePickerDialog(state, onDismiss = { picking = false }, confirmLabel = "Scegli") {
            state.selectedDateMillis?.let { onChange(civilFromEpochMillis(it).toIso()) }
            picking = false
        }
    }
}

@Composable
private fun DeliveryMark(received: Boolean) {
    val color = if (AppTheme.isNothing) AppTheme.TextDark else if (received) AppTheme.TintGreenInk else AppTheme.TintRedInk
    if (received) AppIcons.Check(Modifier.size(22.dp), color)
    else AppIcons.Close(Modifier.size(22.dp), color)
}

@Composable
private fun PracticeSheetBody(title: String, error: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = if (AppTheme.isNothing) nothingDisplayStyle(24, 32) else MaterialTheme.typography.titleLarge,
            color = AppTheme.TextDark
        )
        error?.let { Text(it, color = AppTheme.TintRedInk) }
        content()
    }
}

@Composable
private fun PracticeLoader(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaMorphingLoader()
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
    }
}
