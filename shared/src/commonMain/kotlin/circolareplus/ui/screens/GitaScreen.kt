package circolareplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaFeed
import circolareplus.domain.model.GitaItem
import circolareplus.domain.model.GitaReport
import circolareplus.domain.model.GitaVersion
import circolareplus.platform.rememberPdfFilePicker
import circolareplus.platform.sharePdfFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Quale finestra è aperta sopra la schermata Gita. */
private sealed interface GitaSheet {
    data class AddDocument(val fileName: String, val bytes: ByteArray) : GitaSheet
    data class ReplaceDocument(val item: GitaItem, val fileName: String, val bytes: ByteArray) : GitaSheet
    data object AddLink : GitaSheet
    data class ReplaceLink(val item: GitaItem) : GitaSheet
    data class EditItem(val item: GitaItem) : GitaSheet
    data class Versions(val item: GitaItem) : GitaSheet
    data class Report(val item: GitaItem) : GitaSheet
}

/**
 * Gita: documenti e link della classe, con lo storico delle versioni. Chiunque legge; solo il
 * Rappresentante carica, sostituisce, modifica e ritira. Ogni utente può segnalare una voce, e il
 * segnalante vede lo stato della segnalazione.
 *
 * Il pulsante AILA Assistant in basso a destra apre la chat dell'assistente: quella chat risponde
 * solo con il materiale della gita (vedi [onOpenAssistant]).
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
    var feed by remember { mutableStateOf<GitaFeed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<GitaSheet?>(null) }
    var toWithdraw by remember { mutableStateOf<GitaItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Voce da sostituire mentre si sceglie il file: il picker torna con il file, non con la voce.
    var replaceTarget by remember { mutableStateOf<GitaItem?>(null) }

    fun failure(e: Throwable): String? = when (e) {
        is CancellationException -> throw e
        is ApiException -> e.message
        else -> "Operazione non riuscita. Riprova."
    }

    /** Esegue una scrittura: occupato durante l'attesa, poi ricarica e avvisa l'assistente. */
    fun write(successMessage: String, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                action()
                notice = successMessage
                sheet = null
                onMaterialChanged()
                reloadKey++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = failure(e)
            } finally {
                busy = false
            }
        }
    }

    val pickPdf = rememberPdfFilePicker { fileName, bytes ->
        val target = replaceTarget
        sheet = if (target != null) GitaSheet.ReplaceDocument(target, fileName, bytes)
        else GitaSheet.AddDocument(fileName, bytes)
        replaceTarget = null
    }

    LaunchedEffect(reloadKey) {
        loading = feed == null
        try {
            feed = repository.feed()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = failure(e)
        } finally {
            loading = false
        }
    }

    val canEdit = feed?.canEdit == true

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AilaBackBar(title = "Gita", onBackClick = onBackClick)
            val current = feed
            when {
                current == null && loading -> Box(Modifier.fillMaxSize()) {
                    AilaEmptyState(title = "Caricamento della gita…")
                }
                current == null -> AilaErrorState(
                    message = error ?: "Gita non disponibile.",
                    onRetry = { reloadKey++ }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = AppTheme.Space16),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp, top = AppTheme.Space8)
                ) {
                    notice?.let { text ->
                        item(key = "notice") {
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                        }
                    }
                    if (canEdit) {
                        item(key = "rep-actions") {
                            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                                AilaPrimaryButton(
                                    text = "Carica PDF",
                                    compact = true,
                                    onClick = {
                                        replaceTarget = null
                                        pickPdf()
                                    }
                                )
                                AilaSecondaryButton(
                                    text = "Aggiungi link",
                                    compact = true,
                                    onClick = { sheet = GitaSheet.AddLink }
                                )
                            }
                        }
                    }

                    val documents = current.items.filter { !it.isLink }
                    val links = current.items.filter { it.isLink }

                    item(key = "docs-title") { AilaSectionTitle(text = "Documenti") }
                    if (documents.isEmpty()) {
                        item(key = "docs-empty") {
                            Text("Nessun documento caricato.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                        }
                    }
                    items(documents, key = { "doc-${it.id}" }) { item ->
                        GitaItemCard(
                            item = item,
                            canEdit = canEdit,
                            onOpen = {
                                scope.launch {
                                    try {
                                        val version = item.current ?: return@launch
                                        val bytes = repository.downloadPdf(version.id)
                                        sharePdfFile(bytes, pdfFileName(item.title))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        notice = failure(e)
                                    }
                                }
                            },
                            onVersions = { sheet = GitaSheet.Versions(item) },
                            onReport = { sheet = GitaSheet.Report(item) },
                            onReplace = {
                                replaceTarget = item
                                pickPdf()
                            },
                            onEdit = { sheet = GitaSheet.EditItem(item) },
                            onWithdraw = { toWithdraw = item }
                        )
                    }

                    item(key = "links-title") { AilaSectionTitle(text = "Link utili") }
                    if (links.isEmpty()) {
                        item(key = "links-empty") {
                            Text("Nessun link.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                        }
                    }
                    items(links, key = { "link-${it.id}" }) { item ->
                        GitaLinkCard(
                            item = item,
                            canEdit = canEdit,
                            onVersions = { sheet = GitaSheet.Versions(item) },
                            onReport = { sheet = GitaSheet.Report(item) },
                            onReplace = { sheet = GitaSheet.ReplaceLink(item) },
                            onEdit = { sheet = GitaSheet.EditItem(item) },
                            onWithdraw = { toWithdraw = item }
                        )
                    }

                    if (canEdit) {
                        val open = current.reports.filter { !it.isResolved }
                        if (open.isNotEmpty()) {
                            item(key = "reports-title") { AilaSectionTitle(text = "Segnalazioni da gestire") }
                            items(open, key = { "rep-${it.id}" }) { report ->
                                GitaReportCard(
                                    report = report,
                                    showAction = true,
                                    onResolve = {
                                        write("Segnalazione risolta") { repository.setReportResolved(report.id, resolved = true) }
                                    }
                                )
                            }
                        }
                    }

                    val mine = current.reports.filter { canEdit.not() || it.isResolved }
                    if (mine.isNotEmpty()) {
                        item(key = "mine-title") {
                            AilaSectionTitle(text = if (canEdit) "Segnalazioni risolte" else "Le tue segnalazioni")
                        }
                        items(mine, key = { "mine-${it.id}" }) { report ->
                            GitaReportCard(report = report, showAction = false, onResolve = {})
                        }
                    }
                }
            }
        }

        // Pulsante AILA Assistant in basso a destra, come quello della Bacheca ma con il marchio.
        AilaFab(
            contentDescription = "Chiedi ad AILA Assistant della gita",
            onClick = onOpenAssistant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = AppTheme.Space16, bottom = AppTheme.Space16)
        ) { tint ->
            AilaAssistantMark(size = 22.dp, brush = androidx.compose.ui.graphics.SolidColor(tint))
        }
    }

    when (val open = sheet) {
        is GitaSheet.AddDocument -> GitaDocumentSheet(
            title = "Nuovo documento",
            defaultTitle = pdfTitle(open.fileName),
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, category, note ->
                write("Documento caricato") { repository.addDocument(title, category, open.fileName, open.bytes, note) }
            }
        )
        is GitaSheet.ReplaceDocument -> GitaReplaceSheet(
            item = open.item,
            fileName = open.fileName,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { note ->
                write("Nuova versione caricata") {
                    repository.replaceDocument(open.item.id, open.fileName, open.bytes, note)
                }
            }
        )
        GitaSheet.AddLink -> GitaLinkSheet(
            title = "Nuovo link",
            initialTitle = "",
            initialUrl = "",
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, url, note ->
                write("Link aggiunto") { repository.addLink(title, url, note) }
            }
        )
        is GitaSheet.ReplaceLink -> GitaLinkSheet(
            title = "Nuovo indirizzo",
            initialTitle = open.item.title,
            initialUrl = open.item.current?.url.orEmpty(),
            titleEditable = false,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { _, url, note ->
                write("Link aggiornato") { repository.replaceLink(open.item.id, url, note) }
            }
        )
        is GitaSheet.EditItem -> GitaEditSheet(
            item = open.item,
            busy = busy,
            onDismiss = { sheet = null },
            onSave = { title, category ->
                write("Modifiche salvate") { repository.updateItem(open.item.id, title, category) }
            }
        )
        is GitaSheet.Versions -> GitaVersionsSheet(
            item = open.item,
            repository = repository,
            onDismiss = { sheet = null }
        )
        is GitaSheet.Report -> GitaReportSheet(
            item = open.item,
            busy = busy,
            onDismiss = { sheet = null },
            onSend = { reason ->
                write("Segnalazione inviata: grazie") { repository.report(open.item.id, reason) }
            }
        )
        null -> Unit
    }

    toWithdraw?.let { item ->
        AilaConfirmDialog(
            title = "Ritirare «${item.title}»?",
            message = "La voce non sarà più visibile in classe. Le versioni precedenti restano consultabili dal Rappresentante.",
            confirmLabel = "Ritira",
            onDismiss = { toWithdraw = null },
            onConfirm = {
                toWithdraw = null
                write("Voce ritirata") { repository.withdraw(item.id) }
            }
        )
    }
}

@Composable
private fun GitaItemCard(
    item: GitaItem,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onVersions: () -> Unit,
    onReport: () -> Unit,
    onReplace: () -> Unit,
    onEdit: () -> Unit,
    onWithdraw: () -> Unit
) {
    AilaCard(modifier = Modifier.fillMaxWidth()) {
        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(AppTheme.Space4))
        Text(
            "${item.category.label} · caricato il ${formatUploadDate(item.current?.uploadedAt ?: item.createdAt)}" +
                (item.current?.versionNo?.let { " · versione $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.TextMuted
        )
        Spacer(Modifier.height(AppTheme.Space12))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            AilaPrimaryButton(text = "Apri", compact = true, onClick = onOpen, enabled = item.current?.hasFile == true)
            AilaSecondaryButton(text = "Storico", compact = true, onClick = onVersions)
            AilaSecondaryButton(text = "Segnala", compact = true, onClick = onReport)
        }
        if (canEdit) {
            Spacer(Modifier.height(AppTheme.Space8))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                AilaSecondaryButton(text = "Sostituisci", compact = true, onClick = onReplace)
                AilaSecondaryButton(text = "Modifica", compact = true, onClick = onEdit)
                AilaSecondaryButton(text = "Ritira", compact = true, onClick = onWithdraw)
            }
        }
    }
}

@Composable
private fun GitaLinkCard(
    item: GitaItem,
    canEdit: Boolean,
    onVersions: () -> Unit,
    onReport: () -> Unit,
    onReplace: () -> Unit,
    onEdit: () -> Unit,
    onWithdraw: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AilaCard(modifier = Modifier.fillMaxWidth()) {
        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(AppTheme.Space4))
        Text(
            "Aggiornato il ${formatUploadDate(item.current?.uploadedAt ?: item.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.TextMuted
        )
        Spacer(Modifier.height(AppTheme.Space12))
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            AilaPrimaryButton(text = "Apri", compact = true, onClick = { item.current?.url?.let { uriHandler.openUri(it) } })
            AilaSecondaryButton(text = "Storico", compact = true, onClick = onVersions)
            AilaSecondaryButton(text = "Segnala", compact = true, onClick = onReport)
        }
        if (canEdit) {
            Spacer(Modifier.height(AppTheme.Space8))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                AilaSecondaryButton(text = "Nuovo indirizzo", compact = true, onClick = onReplace)
                AilaSecondaryButton(text = "Modifica", compact = true, onClick = onEdit)
                AilaSecondaryButton(text = "Ritira", compact = true, onClick = onWithdraw)
            }
        }
    }
}

@Composable
private fun GitaReportCard(report: GitaReport, showAction: Boolean, onResolve: () -> Unit) {
    AilaCard(modifier = Modifier.fillMaxWidth()) {
        Text(report.itemTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(AppTheme.Space4))
        Text(report.reason, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(AppTheme.Space4))
        Text(
            if (report.isResolved) "Risolta il ${formatUploadDate(report.resolvedAt ?: report.createdAt)}"
            else "In attesa · inviata il ${formatUploadDate(report.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (report.isResolved) AppTheme.TintGreenInk else AppTheme.TextMuted
        )
        if (showAction) {
            Spacer(Modifier.height(AppTheme.Space8))
            AilaPrimaryButton(text = "Segna come risolta", compact = true, onClick = onResolve)
        }
    }
}

@Composable
private fun GitaDocumentSheet(
    title: String,
    defaultTitle: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, GitaCategory, String?) -> Unit
) {
    var docTitle by remember { mutableStateOf(defaultTitle) }
    var category by remember { mutableStateOf(GitaCategory.PROGRAMMA) }
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = docTitle, onValueChange = { docTitle = it }, label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CategoryPicker(selected = category, onSelect = { category = it })
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (facoltativa)") }, modifier = Modifier.fillMaxWidth())
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
private fun GitaReplaceSheet(
    item: GitaItem,
    fileName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Sostituisci «${item.title}»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("File scelto: $fileName. La versione attuale resta nello storico.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (facoltativa)") }, modifier = Modifier.fillMaxWidth())
            AilaPrimaryButton(text = "Carica nuova versione", fillMaxWidth = true, enabled = !busy, onClick = { onSave(note.trim().ifEmpty { null }) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun GitaLinkSheet(
    title: String,
    initialTitle: String,
    initialUrl: String,
    titleEditable: Boolean = true,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var linkTitle by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var note by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (titleEditable) {
                OutlinedTextField(value = linkTitle, onValueChange = { linkTitle = it }, label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Indirizzo (https://…)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Nota (facoltativa)") }, modifier = Modifier.fillMaxWidth())
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
private fun GitaEditSheet(item: GitaItem, busy: Boolean, onDismiss: () -> Unit, onSave: (String, GitaCategory) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Modifica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (!item.isLink) CategoryPicker(selected = category, onSelect = { category = it })
            AilaPrimaryButton(text = "Salva", fillMaxWidth = true, enabled = !busy && title.isNotBlank(), onClick = { onSave(title.trim(), category) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun GitaVersionsSheet(item: GitaItem, repository: GitaRepository, onDismiss: () -> Unit) {
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
            Text("Storico · ${item.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val list = versions
            when {
                failed != null -> Text(failed!!, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                list == null -> Text("Caricamento…", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                list.isEmpty() -> Text("Nessuna versione.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                else -> list.forEach { v ->
                    AilaCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Versione ${v.versionNo}${if (v.id == item.current?.id) " (attuale)" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Caricata il ${formatUploadDate(v.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                        v.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (v.hasFile) {
                            Spacer(Modifier.height(AppTheme.Space8))
                            AilaSecondaryButton(text = "Apri questa versione", compact = true, onClick = {
                                scope.launch {
                                    try {
                                        sharePdfFile(repository.downloadPdf(v.id), pdfFileName(item.title, v.versionNo))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failed = (e as? ApiException)?.message ?: "File non disponibile."
                                    }
                                }
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun GitaReportSheet(item: GitaItem, busy: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16), verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
            Text("Segnala «${item.title}»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Dici cosa non va: errore, data o importo sbagliati, o un documento vecchio. Il Rappresentante la vede e tu ricevi lo stato.",
                style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Cosa non va") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            AilaPrimaryButton(text = "Invia segnalazione", fillMaxWidth = true, enabled = !busy && reason.isNotBlank(), onClick = { onSend(reason.trim()) })
            Spacer(Modifier.height(AppTheme.Space16))
        }
    }
}

@Composable
private fun CategoryPicker(selected: GitaCategory, onSelect: (GitaCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)) {
        Text("Tipo", style = MaterialTheme.typography.labelLarge, color = AppTheme.TextMuted)
        GitaCategory.entries.forEach { category ->
            AilaSecondaryButton(
                text = if (category == selected) "✓ ${category.label}" else category.label,
                compact = true,
                onClick = { onSelect(category) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "2026-10-10 12:30:00" o "2026-10-10" → "10/10/2026": la data che legge chi usa l'app. */
internal fun formatUploadDate(raw: String): String {
    val day = raw.take(10).split("-")
    return if (day.size == 3 && day.all { it.isNotEmpty() }) "${day[2]}/${day[1]}/${day[0]}" else raw
}

private fun pdfTitle(fileName: String): String = fileName.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').trim()

private fun pdfFileName(title: String, versionNo: Int? = null): String {
    val base = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifEmpty { "gita" }
    return if (versionNo == null) "$base.pdf" else "${base}_v$versionNo.pdf"
}
