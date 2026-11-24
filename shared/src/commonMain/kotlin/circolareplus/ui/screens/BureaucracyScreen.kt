@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.dto.BureaucracyDeadlineDto
import circolareplus.data.remote.dto.BureaucracyDeliveriesDto
import circolareplus.data.remote.dto.BureaucracyDeliveryDto
import circolareplus.data.remote.dto.BureaucracyFeedDto
import circolareplus.data.remote.dto.BureaucracyMaterialDto
import circolareplus.data.remote.dto.BureaucracyPracticeCreatedDto
import circolareplus.data.repository.CircularsRepository
import circolareplus.domain.model.Circular
import circolareplus.data.remote.dto.CreateBureaucracyPracticeDto
import circolareplus.data.remote.dto.SetBureaucracyDeliveryDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.data.remote.dto.UpdateBureaucracyPracticeDto
import circolareplus.design.*
import circolareplus.platform.AppForeground
import circolareplus.platform.PlatformBackHandler
import circolareplus.platform.rememberPdfFilePicker
import circolareplus.platform.sharePdfFile
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
private const val CIRCULAR_PAGE_SIZE = 50
private const val MAX_PDF_BYTES = 10 * 1024 * 1024

@Composable
internal fun BureaucracyScreen(
    api: ApiClient,
    circularsRepository: CircularsRepository,
    onOpenCircular: (Circular) -> Unit,
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
    var editingInstructions by rememberSaveable { mutableStateOf(false) }
    var editingDeadline by rememberSaveable { mutableStateOf(false) }
    var confirmingArchive by rememberSaveable { mutableStateOf(false) }
    var addingMaterial by rememberSaveable { mutableStateOf(false) }
    var materialSaved by remember { mutableStateOf(false) }
    var linkingCircular by rememberSaveable { mutableStateOf(false) }
    var removingMaterial by remember { mutableStateOf<BureaucracyMaterialDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var inFlightStudents by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var deliveryFilter by rememberSaveable(selection) { mutableStateOf(0) }
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
                if (!updated.canManage || selected == null || selected.archivedAt != null) {
                    addingMaterial = false
                    linkingCircular = false
                    materialSaved = false
                    editingInstructions = false
                    editingDeadline = false
                }
                if (updated.canManage && selected != null) {
                    val fresh = api.get<BureaucracyDeliveriesDto>(
                        "$PRACTICES/${selected.id}/deliveries", offlineCopy = false
                    ).deliveries
                    val pending = inFlightStudents
                    deliveries = if (pending.isEmpty()) fresh else fresh.map { d ->
                        pending[d.studentId]?.let { d.copy(received = it) } ?: d
                    }
                } else {
                    if (selected == null && selection != null) selection = null
                    deliveries = null
                }
                loadError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = (e as? ApiException)?.message ?: "Impossibile aggiornare le pratiche."
            }
            delay(15_000)
        } while (true)
    }

    fun toggleDelivery(practiceId: String, studentId: String, received: Boolean) {
        if (studentId in inFlightStudents) return
        val previous = deliveries?.firstOrNull { it.studentId == studentId }?.received ?: !received
        inFlightStudents = inFlightStudents + (studentId to received)
        actionError = null
        deliveries = deliveries?.map {
            if (it.studentId == studentId) it.copy(received = received) else it
        }
        scope.launch {
            try {
                api.put<SetBureaucracyDeliveryDto, SuccessDto>(
                    "$PRACTICES/$practiceId/deliveries/$studentId",
                    SetBureaucracyDeliveryDto(received)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deliveries = deliveries?.map {
                    if (it.studentId == studentId) it.copy(received = previous) else it
                }
                if (e is ApiException && e.statusCode in listOf(403, 404, 409)) revision++
                actionError = (e as? ApiException)?.message ?: "Impossibile salvare la consegna."
            } finally {
                inFlightStudents = inFlightStudents - studentId
            }
        }
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

    val pickPdf = rememberPdfFilePicker(
        onError = { actionError = it },
        onPicked = { fileName, bytes ->
            if (active && addingMaterial && canManage && practice != null && practice.archivedAt == null && !busy) {
                if (bytes.size > MAX_PDF_BYTES) {
                    actionError = "Il PDF supera il limite di 10 MB."
                } else {
                    save(change = {
                        val safeName = fileName.filterNot { it in "\"\r\n" }
                        api.postMultipart<SuccessDto>(
                            "$PRACTICES/${practice.id}/materials/documents",
                            formData {
                                append("file", bytes, Headers.build {
                                    append(HttpHeaders.ContentType, "application/pdf")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                                })
                            }
                        )
                    }, onDone = { materialSaved = addingMaterial })
                }
            }
        }
    )

    fun openMaterial(practiceId: String, material: BureaucracyMaterialDto) {
        if (busy) return
        busy = true
        actionError = null
        scope.launch {
            try {
                val number = material.circularNumber
                if (number != null) {
                    onOpenCircular(circularsRepository.getCircular(number))
                } else {
                    val pdf = api.getBytes("$PRACTICES/$practiceId/materials/${material.id}/file", offlineCopy = false)
                    sharePdfFile(pdf, material.title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = (e as? ApiException)?.message ?: "Impossibile aprire il materiale."
            } finally {
                busy = false
            }
        }
    }

    val back: () -> Unit = back@{
        if (busy) return@back
        if (selection != null) {
            addingMaterial = false
            linkingCircular = false
            removingMaterial = null
            editingInstructions = false
            editingDeadline = false
            selection = null
            deliveries = null
            loadError = null
            actionError = null
        } else {
            onBackClick()
        }
    }
    PlatformBackHandler(
        enabled = active && !creating && !confirmingArchive && !editingInstructions && !editingDeadline && !addingMaterial && removingMaterial == null,
        onBack = back
    )

    val gutter = if (AppTheme.isNothing) NothingSpacing.Edge else AppTheme.Space16
    val shown = feed?.practices.orEmpty().filter { (it.archivedAt != null) == archived }
    Column(
        Modifier.fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        AilaBackBar(title = practice?.title ?: "Burocrazia", onBackClick = back) {
            if (canManage && selection == null) {
                AilaIconButton("Nuova pratica", { actionError = null; creating = true }, primary = true, enabled = !busy) {
                    AppIcons.Plus(Modifier.size(20.dp), it)
                }
            } else if (canManage && practice != null && practice.archivedAt == null) {
                AilaIconButton(
                    "Aggiungi materiale",
                    onClick = {
                        actionError = null
                        materialSaved = false
                        addingMaterial = true
                    },
                    primary = true,
                    enabled = !busy
                ) { AppIcons.Plus(Modifier.size(20.dp), it) }
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
                actionError?.takeIf { !creating && !confirmingArchive && !editingInstructions && !editingDeadline && !addingMaterial && removingMaterial == null }?.let { error ->
                    item { Text(error, color = AppTheme.TintRedInk, style = MaterialTheme.typography.bodyMedium) }
                }
                when {
                    feed == null -> if (loadError == null) {
                        item { PracticeLoader("Caricamento pratiche…") }
                    }
                    practice != null -> {
                        val canEdit = canManage && practice.archivedAt == null
                        val description = practice.description
                        val hasDetails = description.isNotBlank() || practice.dueDate != null || practice.materials.isNotEmpty() || canEdit
                        if (hasDetails) {
                            item(key = "practice_details_menu") {
                                PracticeDetailsMenuCard(
                                    description = description,
                                    dueDate = practice.dueDate,
                                    materials = practice.materials,
                                    canEdit = canEdit,
                                    busy = busy,
                                    onEditInstructions = if (canEdit && !busy) {
                                        { actionError = null; editingInstructions = true }
                                    } else null,
                                    onEditDeadline = if (canEdit && !busy) {
                                        { actionError = null; editingDeadline = true }
                                    } else null,
                                    onOpenMaterial = { material -> openMaterial(practice.id, material) },
                                    onRemoveMaterial = if (canEdit) {
                                        { material -> actionError = null; removingMaterial = material }
                                    } else null
                                )
                            }
                            item(key = "practice_details_divider") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = AppTheme.Hairline
                                )
                            }
                        }
                        val roster = deliveries
                        if (canManage && roster != null) {
                            val deliveredCount = roster.count { it.received }
                            val pendingCount = roster.size - deliveredCount
                            val filteredRoster = when (deliveryFilter) {
                                1 -> roster.filter { !it.received }
                                2 -> roster.filter { it.received }
                                else -> roster
                            }
                            item(key = "deliveries_summary") {
                                DeliveryProgressCard(
                                    delivered = deliveredCount,
                                    total = roster.size,
                                    canEdit = canEdit
                                )
                            }
                            item(key = "deliveries_filter") {
                                AilaSegmentedTabs(
                                    labels = listOf(
                                        "Tutti (${roster.size})",
                                        "Mancanti ($pendingCount)",
                                        "Consegnate ($deliveredCount)"
                                    ),
                                    selectedIndex = deliveryFilter,
                                    onSelect = { deliveryFilter = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (filteredRoster.isEmpty()) {
                                item(key = "deliveries_empty") {
                                    AilaEmptyState(
                                        when (deliveryFilter) {
                                            1 -> "Tutti gli alunni hanno consegnato!"
                                            2 -> "Nessuna consegna registrata"
                                            else -> "Nessun alunno in elenco"
                                        }
                                    )
                                }
                            } else {
                                item(key = "deliveries_list") {
                                    AilaCard {
                                        Column(Modifier.fillMaxWidth()) {
                                            filteredRoster.forEachIndexed { index, person ->
                                                if (index > 0) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(horizontal = 16.dp),
                                                        color = AppTheme.Hairline
                                                    )
                                                }
                                                DeliveryStudentRow(
                                                    person = person,
                                                    canEdit = canEdit,
                                                    onToggle = { received ->
                                                        toggleDelivery(practice.id, person.studentId, received)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (canEdit) {
                                item(key = "archive_practice") {
                                    AilaCard {
                                        AilaListRow(
                                            title = "Archivia pratica",
                                            subtitle = "Sposta la pratica in archivio quando tutte le consegne sono chiuse",
                                            tint = AppTheme.TintSlate,
                                            icon = { AppIcons.Check(Modifier.size(18.dp), AppTheme.TintSlateInk) },
                                            onClick = if (!busy) {
                                                { actionError = null; confirmingArchive = true }
                                            } else null
                                        )
                                    }
                                }
                            }
                        } else if (canManage && loadError == null) {
                            item { PracticeLoader("Caricamento consegne…") }
                        } else if (!canManage) {
                            practice.received?.let { received ->
                                item { PracticeSectionTitle("La tua consegna") }
                                item {
                                    AilaCard {
                                        AilaListRow(
                                            title = studentName,
                                            subtitle = if (received) "Registrata dal rappresentante · Consegnata" else "Da consegnare al rappresentante",
                                            tint = if (received) AppTheme.TintGreen else AppTheme.TintAmber,
                                            icon = { DeliveryMark(received) },
                                            trailing = {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(if (received) AppTheme.TintGreen else AppTheme.TintAmber)
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (received) "Consegnata" else "Mancante",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (received) AppTheme.TintGreenInk else AppTheme.TintAmberInk
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        item {
                            AilaSegmentedTabs(
                                labels = listOf("Attive", "Archivio"),
                                selectedIndex = if (archived) 1 else 0,
                                onSelect = { archived = it == 1 },
                                modifier = Modifier.fillMaxWidth()
                            )
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
                                    AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextFaint)
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
                            val created = api.post<CreateBureaucracyPracticeDto, BureaucracyPracticeCreatedDto>(
                                PRACTICES, CreateBureaucracyPracticeDto(title.trim(), description.trim(), dueDate)
                            )
                            selection = created.id
                        }, onDone = { creating = false; archived = false })
                    }
                )
            }
        }
    }
    if (canManage && editingInstructions && practice != null && practice.archivedAt == null) {
        AilaBottomSheet(onDismissRequest = { if (!busy) editingInstructions = false }) {
            var title by rememberSaveable(practice.id) { mutableStateOf(practice.title) }
            var description by rememberSaveable(practice.id) { mutableStateOf(practice.description) }
            val trimmedTitle = title.trim()
            val trimmedDesc = description.trim()
            val changed = trimmedTitle != practice.title || trimmedDesc != practice.description
            PracticeSheetBody("Modifica pratica e istruzioni", actionError) {
                AilaPillTextField(title, { title = it.take(120) }, "Titolo della pratica", Modifier.fillMaxWidth(), singleLine = true)
                AilaPillTextField(
                    description, { description = it.take(1000) }, "Istruzioni per la classe (cosa portare o fare)",
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), maxLines = 5
                )
                AilaPrimaryButton(
                    if (busy) "Salvataggio…" else "Salva modifiche",
                    fillMaxWidth = true,
                    enabled = !busy && trimmedTitle.isNotBlank() && changed,
                    onClick = {
                        save(change = {
                            api.put<UpdateBureaucracyPracticeDto, SuccessDto>(
                                "$PRACTICES/${practice.id}",
                                UpdateBureaucracyPracticeDto(trimmedTitle, trimmedDesc)
                            )
                        }, onDone = { editingInstructions = false })
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
    if (addingMaterial && canManage && practice != null && practice.archivedAt == null) {
        AilaOriginSheet(
            originKey = "bureaucracyMaterial",
            closeRequested = materialSaved,
            useOrigin = false,
            onDismiss = {
                addingMaterial = false
                linkingCircular = false
                materialSaved = false
            }
        ) { close ->
            val backToChoices = { actionError = null; linkingCircular = false }
            PlatformBackHandler(enabled = linkingCircular && !busy, onBack = backToChoices)
            AilaStepSheetBody(
                title = if (linkingCircular) "Scegli una circolare" else "Aggiungi alla pratica",
                canGoBack = linkingCircular,
                onBack = { if (!busy) backToChoices() },
                onClose = { if (!busy) close() }
            ) {
                actionError?.let { Text(it, color = AppTheme.TintRedInk, modifier = Modifier.padding(bottom = 12.dp)) }
                if (busy) PracticeLoader("Salvataggio…")
                if (linkingCircular) {
                    val linked = practice.materials.mapNotNull { it.circularNumber }.toSet()
                    PracticeCircularPicker(circularsRepository, linked, busy) { number ->
                        save(change = {
                            api.post<Map<String, Int>, SuccessDto>(
                                "$PRACTICES/${practice.id}/materials/circulars", mapOf("number" to number)
                            )
                        }, onDone = { materialSaved = addingMaterial })
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                        AilaCard {
                            AilaListRow(
                                title = "Carica PDF",
                                subtitle = "Allega un file dal dispositivo, fino a 10 MB",
                                tint = AppTheme.TintBlue,
                                icon = { AppIcons.Document(Modifier.size(20.dp), AppTheme.TintBlueInk) },
                                onClick = pickPdf.takeUnless { busy }
                            )
                        }
                        AilaCard {
                            AilaListRow(
                                title = "Collega circolare",
                                subtitle = "Scegli una circolare già pubblicata in AILA",
                                tint = AppTheme.SecondaryContainer,
                                icon = { AppIcons.ExternalLink(Modifier.size(20.dp), AppTheme.OnSecondaryContainer) },
                                onClick = if (busy) null else {
                                    { actionError = null; linkingCircular = true }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    val materialToRemove = removingMaterial
    if (canManage && practice != null && practice.archivedAt == null && materialToRemove != null) {
        AilaBottomSheet(onDismissRequest = { if (!busy) removingMaterial = null }) {
            PracticeSheetBody("Rimuovi dalla pratica", actionError) {
                Text("«${materialToRemove.title}» non sarà più disponibile da questa pratica.", color = AppTheme.TextMuted)
                AilaPrimaryButton("Rimuovi", enabled = !busy, fillMaxWidth = true, onClick = {
                    save(change = {
                        api.delete<SuccessDto>("$PRACTICES/${practice.id}/materials/${materialToRemove.id}")
                    }, onDone = { removingMaterial = null })
                })
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
private fun PracticeSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = AppTheme.TextDark
    )
}

@Composable
private fun PracticeInstructions(description: String, onEdit: (() -> Unit)? = null) {
    AilaCard(containerColor = AppTheme.SecondaryContainer) {
        Column(
            Modifier.fillMaxWidth().padding(AppTheme.Space16),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AilaIconTile(tint = AppTheme.OnSecondaryContainer.copy(alpha = 0.14f), size = 38.dp) {
                    AppIcons.Document(Modifier.size(18.dp), AppTheme.OnSecondaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Istruzioni per la classe",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTheme.OnSecondaryContainer
                    )
                    Text(
                        "Cosa portare o compilare per questa pratica",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.OnSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
                if (onEdit != null) {
                    AilaIconButton("Modifica istruzioni", onEdit, size = 40.dp) {
                        AppIcons.Pencil(Modifier.size(18.dp), it)
                    }
                }
            }
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.OnSecondaryContainer
                )
            } else {
                Text(
                    "Nessuna istruzione inserita. Tocca la matita per spiegare cosa consegnare.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.OnSecondaryContainer.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun PracticeDeadline(date: String?, onEdit: (() -> Unit)?) {
    AilaCard {
        AilaListRow(
            title = "Scadenza",
            subtitle = date?.let(::formatItalianDateWithWeekday) ?: "Nessuna data impostata",
            tint = AppTheme.TertiaryContainer,
            icon = { AppIcons.Calendar(Modifier.size(20.dp), AppTheme.OnTertiaryContainer) },
            trailing = if (onEdit != null) {
                {
                    AilaIconButton(if (date == null) "Aggiungi scadenza" else "Modifica scadenza", onEdit, size = 40.dp) {
                        AppIcons.Pencil(Modifier.size(18.dp), it)
                    }
                }
            } else null
        )
    }
}

@Composable
private fun PracticeCircularPicker(
    repository: CircularsRepository,
    linked: Set<Int>,
    busy: Boolean,
    onPick: (Int) -> Unit
) {
    var circulars by remember { mutableStateOf(emptyList<Circular>()) }
    var offset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadPage() {
        if (loading || !hasMore) return
        loading = true
        pageError = null
        scope.launch {
            try {
                val page = repository.listCirculars(CIRCULAR_PAGE_SIZE, offset)
                offset += page.size
                circulars = (circulars + page).distinctBy { it.number }
                hasMore = page.size == CIRCULAR_PAGE_SIZE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pageError = "Impossibile caricare le circolari. Riprova."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadPage() }
    CircularPicker(
        circulars.filterNot { it.number in linked }, loading, pageError, busy,
        onLoadMore = ::loadPage, hasMore = hasMore, onPick = onPick
    )
}

@Composable
private fun PracticeDetailsMenuCard(
    description: String,
    dueDate: String?,
    materials: List<BureaucracyMaterialDto>,
    canEdit: Boolean,
    busy: Boolean,
    onEditInstructions: (() -> Unit)?,
    onEditDeadline: (() -> Unit)?,
    onOpenMaterial: (BureaucracyMaterialDto) -> Unit,
    onRemoveMaterial: ((BureaucracyMaterialDto) -> Unit)?
) {
    AilaCard {
        Column(Modifier.fillMaxWidth()) {
            var hasPreviousSection = false

            if (description.isNotBlank() || canEdit) {
                hasPreviousSection = true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onEditInstructions != null) Modifier.clickable(onClick = onEditInstructions)
                            else Modifier
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AilaIconTile(tint = AppTheme.TintAmber, size = 38.dp) {
                            AppIcons.Document(Modifier.size(19.dp), AppTheme.TintAmberInk)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "Istruzioni per la classe",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.TextDark
                            )
                            Text(
                                if (description.isBlank()) "Tocca per aggiungere cosa portare o compilare"
                                else "Cosa portare o compilare per questa pratica",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.TextMuted
                            )
                        }
                        if (onEditInstructions != null) {
                            AppIcons.Pencil(Modifier.size(16.dp), AppTheme.TextFaint)
                        }
                    }
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.TextDark
                        )
                    }
                }
            }

            if (dueDate != null || canEdit) {
                if (hasPreviousSection) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.Hairline)
                }
                hasPreviousSection = true
                AilaListRow(
                    title = "Scadenza",
                    subtitle = dueDate?.let(::formatItalianDateWithWeekday) ?: "Nessuna scadenza impostata",
                    tint = AppTheme.TintGreen,
                    icon = { AppIcons.Calendar(Modifier.size(19.dp), AppTheme.TintGreenInk) },
                    onClick = onEditDeadline,
                    trailing = if (onEditDeadline != null) {
                        { AppIcons.Pencil(Modifier.size(16.dp), AppTheme.TextFaint) }
                    } else null
                )
            }

            if (materials.isNotEmpty()) {
                if (hasPreviousSection) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.Hairline)
                }
                Text(
                    text = "Documenti e circolari",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
                materials.forEachIndexed { index, material ->
                    if (index > 0) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = AppTheme.Hairline)
                    }
                    val number = material.circularNumber
                    AilaListRow(
                        title = material.title,
                        subtitle = if (number == null) "Documento PDF" else "Circolare n. $number",
                        tint = if (number == null) AppTheme.TintBlue else AppTheme.SecondaryContainer,
                        icon = {
                            if (number == null) AppIcons.Document(Modifier.size(19.dp), AppTheme.TintBlueInk)
                            else AppIcons.ExternalLink(Modifier.size(19.dp), AppTheme.OnSecondaryContainer)
                        },
                        onClick = { onOpenMaterial(material) }.takeIf { !busy },
                        trailing = {
                            if (onRemoveMaterial != null) {
                                AilaIconButton("Rimuovi ${material.title}", { onRemoveMaterial(material) }, enabled = !busy, size = 38.dp) {
                                    AppIcons.Trash(Modifier.size(17.dp), it)
                                }
                            } else {
                                AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextFaint)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeMaterialRow(
    material: BureaucracyMaterialDto,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    AilaCard {
        val number = material.circularNumber
        AilaListRow(
            title = material.title,
            subtitle = if (number == null) "Documento PDF" else "Circolare n. $number",
            tint = if (number == null) AppTheme.TintBlue else AppTheme.SecondaryContainer,
            icon = {
                if (number == null) AppIcons.Document(Modifier.size(20.dp), AppTheme.TintBlueInk)
                else AppIcons.ExternalLink(Modifier.size(20.dp), AppTheme.OnSecondaryContainer)
            },
            onClick = onOpen.takeIf { enabled },
            trailing = {
                if (onRemove != null) {
                    AilaIconButton("Rimuovi ${material.title}", onRemove, enabled = enabled) {
                        AppIcons.Trash(Modifier.size(18.dp), it)
                    }
                } else {
                    AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextFaint)
                }
            }
        )
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
private fun DeliveryProgressCard(
    delivered: Int,
    total: Int,
    canEdit: Boolean
) {
    val progressTarget = if (total > 0) delivered.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progressTarget, label = "deliveryProgress")
    val pct = if (total > 0) (delivered * 100) / total else 0
    val pending = (total - delivered).coerceAtLeast(0)
    val allDone = total > 0 && delivered == total
    val accentBg = if (allDone) AppTheme.TintGreen else AppTheme.TintBlue
    val accentInk = if (allDone) AppTheme.TintGreenInk else AppTheme.TintBlueInk

    AilaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PracticeSectionTitle("Consegne della classe")
                    Text(
                        text = if (canEdit) "Tocca un compagno sotto per segnare chi ha consegnato."
                        else "Pratica archiviata · stato delle consegne in sola lettura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentBg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accentInk
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppTheme.Hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentInk)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppTheme.TintGreen)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$delivered consegnate",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.TintGreenInk
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (pending == 0) AppTheme.TintGreen else AppTheme.TintAmber)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (pending == 0) "Completate" else "$pending da consegnare",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pending == 0) AppTheme.TintGreenInk else AppTheme.TintAmberInk
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryStudentRow(
    person: BureaucracyDeliveryDto,
    canEdit: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val avatarBg by animateColorAsState(
        targetValue = if (person.received) AppTheme.TintGreen else AppTheme.BackgroundLight,
        label = "avatarBg"
    )
    val avatarInk by animateColorAsState(
        targetValue = if (person.received) AppTheme.TintGreenInk else AppTheme.TextMuted,
        label = "avatarInk"
    )
    val checkBg by animateColorAsState(
        targetValue = if (person.received) AppTheme.TintGreenInk else Color.Transparent,
        label = "checkBg"
    )
    val checkBorder by animateColorAsState(
        targetValue = if (person.received) AppTheme.TintGreenInk else AppTheme.TextFaint,
        label = "checkBorder"
    )
    val initials = remember(person.name) {
        person.name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "?" }
    }
    val rowModifier = if (!canEdit) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = person.received,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onToggle
            )
    }

    Row(
        modifier = rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = avatarInk
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = AppTheme.TextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (person.received) "Consegnata" else "Da consegnare",
                style = MaterialTheme.typography.bodySmall,
                color = if (person.received) AppTheme.TintGreenInk else AppTheme.TextMuted
            )
        }
        if (!canEdit) {
            DeliveryMark(person.received)
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(checkBg)
                    .border(1.5.dp, checkBorder, CircleShape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center
            ) {
                if (person.received) {
                    AppIcons.Check(Modifier.size(15.dp), Color.White)
                }
            }
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
