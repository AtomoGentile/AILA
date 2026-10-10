@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.ApiException
import circolareplus.data.repository.GitaRepository
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaBottomSheet
import circolareplus.design.AilaCard
import circolareplus.design.AilaConfirmDialog
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaErrorState
import circolareplus.design.AilaFab
import circolareplus.design.AilaListRow
import circolareplus.design.AilaPillTextField
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AilaSlidingChipRow
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaCircular
import circolareplus.domain.model.GitaFeed
import circolareplus.domain.model.GitaItem
import circolareplus.domain.model.GitaReport
import circolareplus.domain.model.GitaVersion
import circolareplus.platform.rememberPdfFilePicker
import circolareplus.platform.sharePdfFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Finestra aperta sopra la schermata Gita. */
private sealed interface GitaSheet {
    data object Choose : GitaSheet
    data class NewDocument(val fileName: String, val bytes: ByteArray) : GitaSheet
    data class ReplaceDocument(val item: GitaItem, val fileName: String, val bytes: ByteArray) : GitaSheet
    data object NewLink : GitaSheet
    data class ReplaceLink(val item: GitaItem) : GitaSheet
    data class Detail(val item: GitaItem) : GitaSheet
    data class Edit(val item: GitaItem) : GitaSheet
    data class Versions(val item: GitaItem) : GitaSheet
    data class Report(val item: GitaItem) : GitaSheet
}

/** Colori di ogni categoria: sfondo tenue per la riga e colore pieno per la selezione. */
private data class CategoryTint(val tint: Color, val ink: Color, val label: String)

private fun GitaCategory.look(): CategoryTint = when (this) {
    GitaCategory.PROGRAMMA -> CategoryTint(AppTheme.TintBlue, AppTheme.TintBlueInk, label)
    GitaCategory.PREVENTIVO -> CategoryTint(AppTheme.TintAmber, AppTheme.TintAmberInk, label)
    GitaCategory.SCADENZA -> CategoryTint(AppTheme.TintRed, AppTheme.TintRedInk, label)
    GitaCategory.REGOLAMENTO -> CategoryTint(AppTheme.TintViolet, AppTheme.TintVioletInk, label)
    GitaCategory.PAGAMENTO -> CategoryTint(AppTheme.TintGreen, AppTheme.TintGreenInk, label)
    GitaCategory.ALTRO -> CategoryTint(AppTheme.TintSlate, AppTheme.TintSlateInk, label)
}

/**
 * Gita: documenti, link e circolari della classe, con lo storico delle versioni.
 * Chiunque legge; solo il Rappresentante carica, sostituisce, modifica, ritira e sceglie le
 * circolari. Ogni utente può segnalare una voce e vede lo stato della sua segnalazione.
 * Il pulsante AILA Assistant apre la chat che risponde solo con il materiale della gita.
 */
@Composable
fun GitaScreen(
    repository: GitaRepository,
    onBackClick: () -> Unit,
    onOpenAssistant: () -> Unit,
    /** Chiamato dopo ogni modifica: l'assistente rilegge il materiale alla domanda successiva. */
    onMaterialChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var feed by remember { mutableStateOf<GitaFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<GitaSheet?>(null) }
    var toWithdraw by remember { mutableStateOf<GitaItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Voce da sostituire mentre si sceglie il file: il picker torna con il file, non con la voce.
    var replaceTarget by remember { mutableStateOf<GitaItem?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun messageOf(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Operazione non riuscita."
        else -> "Operazione non riuscita. Riprova."
    }

    /** Una scrittura: occupato durante l'attesa, poi ricarica e chiude la finestra. */
    fun write(message: String, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            notice = null
            try {
                action()
                notice = message
                sheet = null
                onMaterialChanged()
                reloadKey++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = messageOf(e)
            } finally {
                busy = false
            }
        }
    }

    val pickPdf = rememberPdfFilePicker { fileName, bytes ->
        val target = replaceTarget
        sheet = if (target != null) GitaSheet.ReplaceDocument(target, fileName, bytes)
        else GitaSheet.NewDocument(fileName, bytes)
        replaceTarget = null
    }

    LaunchedEffect(reloadKey) {
        refreshing = true
        try {
            feed = repository.feed()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Con dati già a schermo un errore di rete non li cancella: resta la barra e il messaggio.
            if (feed == null) error = messageOf(e) else notice = messageOf(e)
        } finally {
            refreshing = false
        }
    }

    val current = feed
    val canEdit = current?.canEdit == true

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AilaBackBar(
                title = "Gita",
                onBackClick = onBackClick,
                action = if (canEdit) {
                    { GitaPlusButton(enabled = !busy, onClick = { sheet = GitaSheet.Choose }) }
                } else null
            )
            if (refreshing && current != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppTheme.PrimaryBlue)
            }
            when {
                current == null && error == null -> Box(Modifier.fillMaxSize()) {
                    AilaEmptyState(title = "Carico la gita…")
                }
                current == null -> AilaErrorState(message = error ?: "Gita non disponibile.", onRetry = { reloadKey++ })
                else -> GitaList(
                    feed = current,
                    notice = notice,
                    onOpen = { sheet = GitaSheet.Detail(it) },
                    onPinCircular = { circular, pinned ->
                        write(if (pinned) "Circolare aggiunta alla gita." else "Circolare tolta dalla gita.") {
                            repository.setCircularPinned(circular.number, pinned)
                        }
                    },
                    onResolveReport = { report ->
                        write("Segnalazione risolta.") { repository.setReportResolved(report.id, resolved = true) }
                    },
                    canEdit = canEdit,
                    busy = busy,
                )
            }
        }

        // AILA Assistant in basso a destra: stessa origine del container transform della lente.
        AilaFab(
            contentDescription = "Chiedi ad AILA Assistant della gita",
            onClick = onOpenAssistant,
            transformInGlass = true,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppTheme.Space16, bottom = AppTheme.Space16)
        ) { tint ->
            AilaAssistantMark(size = 22.dp, brush = SolidColor(tint))
        }
    }

    when (val open = sheet) {
        GitaSheet.Choose -> ChooseSheet(
            onDocument = {
                replaceTarget = null
                sheet = null
                pickPdf()
            },
            onLink = { sheet = GitaSheet.NewLink },
            onDismiss = { sheet = null }
        )
        is GitaSheet.NewDocument -> DocumentSheet(
            title = "Nuovo documento",
            fileName = open.fileName,
            defaultTitle = open.fileName.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').trim(),
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, category, note ->
                write("Documento caricato.") {
                    repository.addDocument(title, category, open.fileName, open.bytes, note)
                }
            }
        )
        is GitaSheet.ReplaceDocument -> ReplaceSheet(
            item = open.item,
            fileName = open.fileName,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { note ->
                write("Nuova versione caricata.") {
                    repository.replaceDocument(open.item.id, open.fileName, open.bytes, note)
                }
            }
        )
        GitaSheet.NewLink -> LinkSheet(
            title = "Nuovo link",
            initialTitle = "",
            initialUrl = "",
            titleEditable = true,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, url, note -> write("Link aggiunto.") { repository.addLink(title, url, note) } }
        )
        is GitaSheet.ReplaceLink -> LinkSheet(
            title = "Nuovo indirizzo",
            initialTitle = open.item.title,
            initialUrl = open.item.current?.url.orEmpty(),
            titleEditable = false,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { _, url, note -> write("Indirizzo aggiornato.") { repository.replaceLink(open.item.id, url, note) } }
        )
        is GitaSheet.Detail -> DetailSheet(
            item = open.item,
            canEdit = canEdit,
            onDismiss = { sheet = null },
            onOpen = {
                val version = open.item.current
                if (open.item.isLink) {
                    version?.url?.let { uriHandler.openUri(it) }
                } else if (version != null) {
                    scope.launch { openPdfVersion(repository, version.id, open.item.title, version.versionNo, onError = { notice = it }) }
                }
                sheet = null
            },
            onVersions = { sheet = GitaSheet.Versions(open.item) },
            onReport = { sheet = GitaSheet.Report(open.item) },
            onReplace = {
                if (open.item.isLink) {
                    sheet = GitaSheet.ReplaceLink(open.item)
                } else {
                    replaceTarget = open.item
                    sheet = null
                    pickPdf()
                }
            },
            onEdit = { sheet = GitaSheet.Edit(open.item) },
            onWithdraw = {
                sheet = null
                toWithdraw = open.item
            }
        )
        is GitaSheet.Edit -> EditSheet(
            item = open.item,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, category ->
                write("Modifiche salvate.") { repository.updateItem(open.item.id, title, category) }
            }
        )
        is GitaSheet.Versions -> VersionsSheet(
            item = open.item,
            repository = repository,
            onDismiss = { sheet = null },
            onNotice = { notice = it },
            onOpenUrl = { uriHandler.openUri(it) }
        )
        is GitaSheet.Report -> ReportSheet(
            item = open.item,
            busy = busy,
            onDismiss = { sheet = null },
            onSend = { reason ->
                write("Segnalazione inviata: riceverai lo stato.") { repository.report(open.item.id, reason) }
            }
        )
        null -> Unit
    }

    toWithdraw?.let { item ->
        AilaConfirmDialog(
            title = "Ritirare «${item.title}»?",
            message = "La voce non sarà più visibile in classe. Lo storico resta consultabile dal Rappresentante.",
            confirmLabel = "Ritira",
            onDismiss = { toWithdraw = null },
            onConfirm = {
                toWithdraw = null
                write("Voce ritirata.") { repository.withdraw(item.id) }
            }
        )
    }
}

private enum class GitaFilter(val label: String) {
    ALL("Tutti"), CIRCULARS("Circolari"), DOCUMENTS("Documenti"), LINKS("Link"), REPORTS("Segnalazioni")
}

@Composable
private fun GitaList(
    feed: GitaFeed,
    notice: String?,
    onOpen: (GitaItem) -> Unit,
    onPinCircular: (GitaCircular, Boolean) -> Unit,
    onResolveReport: (GitaReport) -> Unit,
    canEdit: Boolean,
    busy: Boolean,
) {
    var filter by remember { mutableStateOf(GitaFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()

    val circulars = feed.circulars.filter { needle.isEmpty() || it.title.lowercase().contains(needle) || "${it.number}".contains(needle) }
    val documents = feed.items.filter { !it.isLink && (needle.isEmpty() || it.title.lowercase().contains(needle)) }
    val links = feed.items.filter { it.isLink && (needle.isEmpty() || it.title.lowercase().contains(needle)) }
    val reports = feed.reports
    val show = { f: GitaFilter -> filter == GitaFilter.ALL || filter == f }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppTheme.Space16, end = AppTheme.Space16, top = AppTheme.Space12, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
    ) {
        item(key = "search") {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                AilaPillTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Cerca nella gita…",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint) },
                )
                AilaSlidingChipRow(
                    selectedIndex = GitaFilter.entries.indexOf(filter),
                    itemCount = GitaFilter.entries.size,
                    modifier = Modifier.fillMaxWidth()
                ) { chipModifier ->
                    GitaFilter.entries.forEachIndexed { index, f ->
                        AnimatedFilterChip(
                            label = f.label,
                            isSelected = filter == f,
                            onClick = { filter = f },
                            drawSelectionBackground = false,
                            modifier = chipModifier(index)
                        )
                    }
                }
            }
        }
        notice?.let { text ->
            item(key = "notice") { SubtleLine(text, tint = AppTheme.TintGreenInk) }
        }

        if (show(GitaFilter.CIRCULARS)) {
            item(key = "circulars") {
                GitaSection(title = "Circolari della gita", count = circulars.size) {
                    if (circulars.isEmpty()) {
                        SubtleLine(if (needle.isEmpty()) "Nessuna circolare sulla gita per questa classe." else "Nessuna circolare trovata.")
                    }
                    circulars.forEach { circular ->
                        AilaListRow(
                            title = "Circolare n. ${circular.number}",
                            subtitle = "${circular.title} · ${formatDay(circular.publishDate)}",
                            tint = AppTheme.TintBlue,
                            icon = { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) },
                            trailing = if (canEdit) {
                                {
                                    AilaRowText(
                                        text = if (circular.pinned) "Togli" else "Aggiungi",
                                        enabled = !busy,
                                        onClick = { onPinCircular(circular, !circular.pinned) }
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
        }

        if (show(GitaFilter.DOCUMENTS)) {
            item(key = "documents") {
                GitaSection(title = "Documenti", count = documents.size) {
                    if (documents.isEmpty()) SubtleLine(if (needle.isEmpty()) "Nessun documento caricato." else "Nessun documento trovato.")
                    documents.forEach { item -> ItemRow(item = item, onClick = { onOpen(item) }) }
                }
            }
        }

        if (show(GitaFilter.LINKS)) {
            item(key = "links") {
                GitaSection(title = "Link utili", count = links.size) {
                    if (links.isEmpty()) SubtleLine(if (needle.isEmpty()) "Nessun link." else "Nessun link trovato.")
                    links.forEach { item -> ItemRow(item = item, onClick = { onOpen(item) }) }
                }
            }
        }

        if (show(GitaFilter.REPORTS) && reports.isNotEmpty()) {
            item(key = "reports") {
                GitaSection(title = if (canEdit) "Segnalazioni" else "Le tue segnalazioni", count = reports.count { !it.isResolved }, countLabel = "da leggere") {
                    reports.forEach { report ->
                        ReportRow(
                            report = report,
                            showResolve = canEdit && !report.isResolved,
                            busy = busy,
                            onResolve = { onResolveReport(report) },
                        )
                    }
                }
            }
        }
    }
}

/** Sezione della Gita: un titolo con il conteggio e le righe raccolte in una sola card. */
@Composable
private fun GitaSection(title: String, count: Int, countLabel: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = AppTheme.Space4)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                if (countLabel != null) "$count $countLabel" else "$count",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.TextMuted
            )
        }
        AilaCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** "+" in intestazione: cerchio tenue con il segno, area di tocco di 44dp. */
@Composable
private fun GitaPlusButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = AppTheme.Space8)
            .size(44.dp)
            .clip(CircleShape)
            .background(AppTheme.TintBlue)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Aggiungi alla gita"; role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        AppIcons.Plus(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk)
    }
}

@Composable
private fun ItemRow(item: GitaItem, onClick: () -> Unit) {
    val look = if (item.isLink) CategoryTint(AppTheme.TintSlate, AppTheme.TintSlateInk, "Link") else item.category.look()
    val version = item.current
    val subtitle = if (item.isLink) {
        "Link · aggiornato il ${formatDay(version?.uploadedAt ?: item.createdAt)}"
    } else {
        "${look.label} · ${formatDay(version?.uploadedAt ?: item.createdAt)}" + (version?.let { " · v${it.versionNo}" } ?: "")
    }
    AilaListRow(
        title = item.title,
        subtitle = subtitle,
        tint = look.tint,
        onClick = onClick,
        icon = {
            if (item.isLink) AppIcons.ExternalLink(modifier = Modifier.size(20.dp), color = look.ink)
            else AppIcons.Document(modifier = Modifier.size(20.dp), color = look.ink)
        },
    )
}

@Composable
private fun ReportRow(report: GitaReport, showResolve: Boolean, busy: Boolean, onResolve: () -> Unit) {
    AilaCard(modifier = Modifier.fillMaxWidth()) {
        Text(report.itemTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(AppTheme.Space4))
        Text(report.reason, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(AppTheme.Space4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = if (report.isResolved) "Risolta" else "In attesa",
                tint = if (report.isResolved) AppTheme.TintGreen else AppTheme.TintAmber,
                ink = if (report.isResolved) AppTheme.TintGreenInk else AppTheme.TintAmberInk,
            )
            Spacer(Modifier.size(AppTheme.Space8))
            Text(formatDay(report.createdAt), style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            if (showResolve) {
                Spacer(Modifier.weight(1f))
                AilaRowText(text = "Risolvi", enabled = !busy, onClick = onResolve)
            }
        }
    }
}

@Composable
private fun DetailSheet(
    item: GitaItem,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onVersions: () -> Unit,
    onReport: () -> Unit,
    onReplace: () -> Unit,
    onEdit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val look = if (item.isLink) CategoryTint(AppTheme.TintSlate, AppTheme.TintSlateInk, "Link") else item.category.look()
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            StatusChip(text = look.label, tint = look.tint, ink = look.ink)
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Aggiornato il ${formatDay(item.current?.uploadedAt ?: item.createdAt)}" + (item.current?.let { " · versione ${it.versionNo}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.TextMuted
            )
            AilaPrimaryButton(
                text = if (item.isLink) "Apri il link" else "Apri il PDF",
                fillMaxWidth = true,
                enabled = if (item.isLink) item.current?.url != null else item.current?.hasFile == true,
                onClick = onOpen
            )
            AilaCard(modifier = Modifier.fillMaxWidth()) {
                AilaListRow(title = "Storico versioni", tint = AppTheme.TintBlue, onClick = onVersions,
                    icon = { AppIcons.History(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) })
                AilaListRow(title = "Segnala un errore", subtitle = "Dati sbagliati o non aggiornati", tint = AppTheme.TintAmber, onClick = onReport,
                    icon = { AppIcons.Warning(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk) })
                if (canEdit) {
                    AilaListRow(title = if (item.isLink) "Nuovo indirizzo" else "Sostituisci il file", tint = AppTheme.TintBlue, onClick = onReplace,
                        icon = { AppIcons.Refresh(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) })
                    AilaListRow(title = "Modifica", tint = AppTheme.TintSlate, onClick = onEdit,
                        icon = { AppIcons.Pencil(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk) })
                    AilaListRow(title = "Ritira", subtitle = "Non sarà più visibile in classe", tint = AppTheme.TintRed, onClick = onWithdraw,
                        icon = { AppIcons.Trash(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk) })
                }
            }
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun ChooseSheet(onDocument: () -> Unit, onLink: () -> Unit, onDismiss: () -> Unit) {
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            Text("Aggiungi alla gita", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            AilaCard(modifier = Modifier.fillMaxWidth()) {
                AilaListRow(title = "Carica un PDF", subtitle = "Programma, preventivo, scadenze, regolamento", tint = AppTheme.TintBlue, onClick = onDocument,
                    icon = { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) })
                AilaListRow(title = "Aggiungi un link", subtitle = "Sito dell'agenzia o altre pagine", tint = AppTheme.TintSlate, onClick = onLink,
                    icon = { AppIcons.ExternalLink(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk) })
            }
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun DocumentSheet(
    title: String,
    fileName: String,
    defaultTitle: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, GitaCategory, String?) -> Unit,
) {
    var docTitle by remember { mutableStateOf(defaultTitle) }
    var category by remember { mutableStateOf(GitaCategory.PROGRAMMA) }
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(fileName, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AilaPillTextField(value = docTitle, onValueChange = { docTitle = it }, placeholder = "Titolo", singleLine = true, modifier = Modifier.fillMaxWidth())
            CategoryChips(selected = category, onSelect = { category = it })
            AilaPillTextField(value = note, onValueChange = { note = it }, placeholder = "Nota (facoltativa)", modifier = Modifier.fillMaxWidth())
            AilaPrimaryButton(
                text = "Carica",
                fillMaxWidth = true,
                enabled = !busy && docTitle.isNotBlank(),
                onClick = { onSave(docTitle.trim(), category, note.trim().ifEmpty { null }) }
            )
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun ReplaceSheet(item: GitaItem, fileName: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Sostituisci «${item.title}»", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Nuovo file: $fileName. La versione attuale resta nello storico.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            AilaPillTextField(value = note, onValueChange = { note = it }, placeholder = "Nota (facoltativa)", modifier = Modifier.fillMaxWidth())
            AilaPrimaryButton(text = "Carica la nuova versione", fillMaxWidth = true, enabled = !busy, onClick = { onSave(note.trim().ifEmpty { null }) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun LinkSheet(
    title: String,
    initialTitle: String,
    initialUrl: String,
    titleEditable: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    var linkTitle by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (titleEditable) {
                AilaPillTextField(value = linkTitle, onValueChange = { linkTitle = it }, placeholder = "Titolo", singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            AilaPillTextField(value = url, onValueChange = { url = it }, placeholder = "https://…", singleLine = true, modifier = Modifier.fillMaxWidth())
            AilaPillTextField(value = note, onValueChange = { note = it }, placeholder = "Nota (facoltativa)", modifier = Modifier.fillMaxWidth())
            AilaPrimaryButton(
                text = "Salva",
                fillMaxWidth = true,
                enabled = !busy && url.isNotBlank() && (!titleEditable || linkTitle.isNotBlank()),
                onClick = { onSave(linkTitle.trim(), url.trim(), note.trim().ifEmpty { null }) }
            )
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun EditSheet(item: GitaItem, busy: Boolean, onDismiss: () -> Unit, onSave: (String, GitaCategory) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Modifica", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            AilaPillTextField(value = title, onValueChange = { title = it }, placeholder = "Titolo", singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!item.isLink) CategoryChips(selected = category, onSelect = { category = it })
            AilaPrimaryButton(text = "Salva", fillMaxWidth = true, enabled = !busy && title.isNotBlank(), onClick = { onSave(title.trim(), category) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun VersionsSheet(
    item: GitaItem,
    repository: GitaRepository,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<GitaVersion>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        try {
            versions = repository.versions(item.id).second
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = (e as? ApiException)?.message ?: "Storico non disponibile."
        }
    }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            Text("Storico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            val list = versions
            when {
                failed != null -> SubtleLine(failed!!)
                list == null -> SubtleLine("Caricamento…")
                list.isEmpty() -> SubtleLine("Nessuna versione.")
                else -> AilaCard(modifier = Modifier.fillMaxWidth()) {
                    list.forEach { v ->
                        AilaListRow(
                            title = "Versione ${v.versionNo}${if (v.id == item.current?.id) " · attuale" else ""}",
                            subtitle = listOfNotNull("Caricata il ${formatDay(v.uploadedAt)}", v.note).joinToString(" · "),
                            tint = AppTheme.TintBlue,
                            onClick = if (v.hasFile || v.url != null) {
                                {
                                    if (v.url != null) onOpenUrl(v.url)
                                    else scope.launch { openPdfVersion(repository, v.id, item.title, v.versionNo, onError = onNotice) }
                                }
                            } else null,
                            modifier = Modifier,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun ReportSheet(item: GitaItem, busy: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space20), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Segnala un errore", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("«${item.title}» · errore, data o importo sbagliati, documento vecchio. Il Rappresentante la vede e tu ricevi lo stato.",
                style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            AilaPillTextField(value = reason, onValueChange = { reason = it }, placeholder = "Cosa non va?", modifier = Modifier.fillMaxWidth(), maxLines = 4)
            AilaPrimaryButton(text = "Invia segnalazione", fillMaxWidth = true, enabled = !busy && reason.isNotBlank(), onClick = { onSend(reason.trim()) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

/** Categorie come chip colorati: il selezionato si riempie del colore della categoria, senza spunte. */
@Composable
private fun CategoryChips(selected: GitaCategory, onSelect: (GitaCategory) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
    ) {
        GitaCategory.entries.forEach { category ->
            val look = category.look()
            CategoryChip(
                label = category.label,
                tint = look.tint,
                ink = look.ink,
                selected = category == selected,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, tint: Color, ink: Color, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) ink else tint, label = "gitaChipBg")
    val text by animateColorAsState(if (selected) Color.White else ink, label = "gitaChipText")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun StatusChip(text: String, tint: Color, ink: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(tint).padding(horizontal = AppTheme.Space8, vertical = AppTheme.Space4),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

/** Azione di testo dentro una riga o una card, con area di tocco di 44dp. */
@Composable
private fun AilaRowText(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = AppTheme.Space8).height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) AppTheme.PrimaryBlue else AppTheme.TextMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SubtleLine(text: String, tint: Color = AppTheme.TextMuted) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.padding(horizontal = AppTheme.Space4))
}

/** Scarica il PDF di una versione con il login e apre il foglio di condivisione del sistema. */
private suspend fun openPdfVersion(repository: GitaRepository, versionId: String, title: String, versionNo: Int, onError: (String) -> Unit) {
    try {
        sharePdfFile(repository.downloadPdf(versionId), pdfFileName(title, versionNo))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError((e as? ApiException)?.message ?: "Impossibile aprire il file.")
    }
}

/** "2026-10-10 12:30:00" o "2026-10-10" → "10/10/2026". */
internal fun formatDay(raw: String): String {
    val day = raw.take(10).split("-")
    return if (day.size == 3 && day.all { it.isNotEmpty() }) "${day[2]}/${day[1]}/${day[0]}" else raw
}

private fun pdfFileName(title: String, versionNo: Int): String {
    val base = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifEmpty { "gita" }
    return "${base}_v$versionNo.pdf"
}
