@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.ApiException
import circolareplus.design.AilaBottomSheet
import circolareplus.design.AilaCard
import circolareplus.design.AilaConfirmDialog
import circolareplus.design.AilaDuration
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaListRow
import circolareplus.design.AilaMorphingLoader
import circolareplus.design.AilaOriginSheet
import circolareplus.design.AilaPillTextField
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.GlassEdge
import circolareplus.design.ailaColorSpec
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaPressable
import circolareplus.design.ailaSheetReveal
import circolareplus.design.appImePadding
import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaItem
import circolareplus.domain.model.GitaVersion
import circolareplus.platform.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Fogli della Gita. Due famiglie, come nel resto dell'app:
 *  - il "+" (Rappresentante): un solo foglio a passi (scelta, documento, link) che in Material nasce
 *    dal pulsante e ci rientra, in Liquid Glass sale dal basso ([AilaOriginSheet]);
 *  - il tocco su una voce: un solo foglio dal basso con i passi dettaglio, storico, segnalazione,
 *    modifica, nuovo indirizzo e nuova versione ([AilaBottomSheet]).
 * Ogni passo che non e' il primo ha la freccia indietro nell'intestazione e risponde al gesto di
 * indietro del sistema: torna al passo di prima invece di chiudere tutto.
 */

/** Passi del foglio "+". */
internal sealed interface AddStep {
    data object Choose : AddStep
    class Document(val fileName: String, val bytes: ByteArray) : AddStep
    data object Link : AddStep
}

/** Passi del foglio di una voce. Il primo e' sempre [Detail]; gli altri tornano a lui. */
internal sealed interface ItemStep {
    val item: GitaItem

    data class Detail(override val item: GitaItem) : ItemStep
    data class Versions(override val item: GitaItem) : ItemStep
    data class Report(override val item: GitaItem) : ItemStep
    data class Edit(override val item: GitaItem) : ItemStep
    data class ReplaceLink(override val item: GitaItem) : ItemStep
    class ReplaceDocument(override val item: GitaItem, val fileName: String, val bytes: ByteArray) : ItemStep
}

/**
 * Passaggio fra due passi di un foglio: "fade through" come il foglio "Nuovo evento" del
 * Calendario. Il passo vecchio si dissolve del tutto e solo dopo entra il nuovo (un filo piu'
 * piccolo che si distende); l'altezza del foglio segue con la molla di navigazione.
 */
private fun stepTransition(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(ailaFadeSpec(190, delayMillis = 100)) +
        scaleIn(ailaMoveSpec(190, delayMillis = 100), initialScale = 0.94f),
    initialContentExit = fadeOut(ailaFadeSpec(90)),
    sizeTransform = SizeTransform(clip = false) { _, _ -> ailaNavigationSpring<IntSize>() }
)

/**
 * Corpo di un foglio: intestazione (freccia indietro che entra ed esce, titolo che si dissolve da
 * un passo all'altro, chiudi) e contenuto che scorre se non ci sta, sopra la tastiera.
 */
@Composable
private fun GitaSheetBody(
    title: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appImePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTheme.Space20)
            .padding(bottom = AppTheme.Space32)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = canGoBack,
                enter = fadeIn(ailaFadeSpec()) + expandHorizontally(ailaMoveSpec()),
                exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)) + shrinkHorizontally(ailaMoveSpec())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AilaIconButton(contentDescription = "Indietro", onClick = onBack, size = 40.dp) { tint ->
                        AppIcons.ChevronLeft(modifier = Modifier.size(18.dp), color = tint)
                    }
                    Spacer(modifier = Modifier.width(AppTheme.Space12))
                }
            }
            AnimatedContent(
                targetState = title,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(ailaFadeSpec(180, delayMillis = 60)),
                        initialContentExit = fadeOut(ailaFadeSpec(100))
                    )
                },
                label = "gitaSheetTitle"
            ) { shown ->
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            AilaIconButton(contentDescription = "Chiudi", onClick = onClose, size = 40.dp) { tint ->
                AppIcons.Close(modifier = Modifier.size(16.dp), color = tint)
            }
        }
        content()
    }
}

// -------------------------------------------------------------------------------------------------
// Foglio "+"
// -------------------------------------------------------------------------------------------------

@Composable
internal fun GitaAddSheet(
    step: AddStep,
    onStep: (AddStep) -> Unit,
    busy: Boolean,
    closeRequested: Boolean,
    onDismiss: () -> Unit,
    onPickPdf: () -> Unit,
    onSaveDocument: (title: String, category: GitaCategory, note: String?, fileName: String, bytes: ByteArray) -> Unit,
    onSaveLink: (title: String, url: String, note: String?) -> Unit,
) {
    AilaOriginSheet(
        originKey = "gitaAddSheet",
        onDismiss = onDismiss,
        closeRequested = closeRequested
    ) { close ->
        // Composto dopo il gestore di chiusura del foglio: ha la precedenza e torna alla scelta.
        PlatformBackHandler(enabled = step !is AddStep.Choose) { onStep(AddStep.Choose) }
        GitaSheetBody(
            title = when (step) {
                AddStep.Choose -> "Aggiungi alla gita"
                is AddStep.Document -> "Nuovo documento"
                AddStep.Link -> "Nuovo link"
            },
            canGoBack = step !is AddStep.Choose,
            onBack = { onStep(AddStep.Choose) },
            onClose = close
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxWidth(),
                // Per classe: cambiare i campi dello stesso passo non lo rianima.
                contentKey = { it::class },
                transitionSpec = { stepTransition() },
                label = "gitaAddStep"
            ) { shown ->
                // Colonna propria: AnimatedContent impila i figli come una Box.
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (shown) {
                        AddStep.Choose -> ChooseBody(
                            onDocument = onPickPdf,
                            onLink = { onStep(AddStep.Link) }
                        )
                        is AddStep.Document -> DocumentForm(
                            fileName = shown.fileName,
                            busy = busy,
                            onSave = { title, category, note -> onSaveDocument(title, category, note, shown.fileName, shown.bytes) }
                        )
                        AddStep.Link -> LinkForm(
                            titleEditable = true,
                            initialTitle = "",
                            initialUrl = "",
                            saveLabel = "Salva",
                            busy = busy,
                            onSave = { title, url, note -> onSaveLink(title, url, note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseBody(onDocument: () -> Unit, onLink: () -> Unit) {
    Text(
        text = "Che cosa vuoi aggiungere?",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.TextMuted,
        modifier = Modifier.ailaSheetReveal(0).padding(bottom = AppTheme.Space12)
    )
    AilaCard(modifier = Modifier.ailaSheetReveal(1)) {
        AilaListRow(
            title = "Carica un PDF",
            subtitle = "Programma, preventivo, scadenze, regolamento",
            tint = AppTheme.TintBlue,
            onClick = onDocument,
            icon = { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) }
        )
        AilaListRow(
            title = "Aggiungi un link",
            subtitle = "Sito dell'agenzia o altre pagine",
            tint = AppTheme.TintSlate,
            onClick = onLink,
            icon = { AppIcons.ExternalLink(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk) }
        )
    }
}

@Composable
private fun DocumentForm(fileName: String, busy: Boolean, onSave: (String, GitaCategory, String?) -> Unit) {
    var title by remember { mutableStateOf(fileName.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').trim()) }
    var category by remember { mutableStateOf(GitaCategory.PROGRAMMA) }
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        FileLine(fileName = fileName, modifier = Modifier.ailaSheetReveal(0))
        AilaPillTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Titolo",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(1)
        )
        CategoryChips(selected = category, onSelect = { category = it }, modifier = Modifier.ailaSheetReveal(2))
        AilaPillTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Nota (facoltativa)",
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(3)
        )
        AilaPrimaryButton(
            text = "Carica",
            fillMaxWidth = true,
            enabled = !busy && title.isNotBlank(),
            onClick = { onSave(title.trim(), category, note.trim().ifEmpty { null }) },
            modifier = Modifier.ailaSheetReveal(4)
        )
    }
}

/** Il file scelto: icona e nome, per non dover rileggere il titolo per sapere cosa si sta caricando. */
@Composable
private fun FileLine(fileName: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AilaIconTile(tint = AppTheme.TintBlue, size = 36.dp) {
            AppIcons.Document(modifier = Modifier.size(18.dp), color = AppTheme.TintBlueInk)
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LinkForm(
    titleEditable: Boolean,
    initialTitle: String,
    initialUrl: String,
    saveLabel: String,
    busy: Boolean,
    onSave: (String, String, String?) -> Unit,
) {
    var linkTitle by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        if (titleEditable) {
            AilaPillTextField(
                value = linkTitle,
                onValueChange = { linkTitle = it },
                placeholder = "Titolo",
                singleLine = true,
                modifier = Modifier.fillMaxWidth().ailaSheetReveal(0)
            )
        }
        AilaPillTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = "https://…",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(1)
        )
        AilaPillTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Nota (facoltativa)",
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(2)
        )
        AilaPrimaryButton(
            text = saveLabel,
            fillMaxWidth = true,
            enabled = !busy && url.isNotBlank() && (!titleEditable || linkTitle.isNotBlank()),
            onClick = { onSave(linkTitle.trim(), url.trim(), note.trim().ifEmpty { null }) },
            modifier = Modifier.ailaSheetReveal(3)
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Foglio di una voce
// -------------------------------------------------------------------------------------------------

@Composable
internal fun GitaItemSheet(
    step: ItemStep,
    onStep: (ItemStep) -> Unit,
    canEdit: Boolean,
    busy: Boolean,
    closeRequested: Boolean,
    onDismiss: () -> Unit,
    /** Apre il PDF o il link della versione corrente. */
    onOpen: (GitaItem) -> Unit,
    /** Sostituisce il file: il selettore di sistema si apre dopo la chiusura del foglio. */
    onReplaceFile: (GitaItem) -> Unit,
    loadVersions: suspend (itemId: String) -> List<GitaVersion>,
    onOpenVersion: (GitaItem, GitaVersion) -> Unit,
    onSaveEdit: (GitaItem, title: String, category: GitaCategory) -> Unit,
    onSaveLink: (GitaItem, url: String, note: String?) -> Unit,
    onSaveDocument: (GitaItem, fileName: String, bytes: ByteArray, note: String?) -> Unit,
    onSendReport: (GitaItem, reason: String) -> Unit,
    onWithdraw: (GitaItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Una sola chiusura per volta: X, "operazione finita" e un secondo tocco non devono lanciare
    // due hide(). Prima la tastiera, poi il foglio scende con la sua animazione, poi si toglie.
    val closing = remember { booleanArrayOf(false) }
    val close: () -> Unit = {
        if (!closing[0]) {
            closing[0] = true
            scope.launch {
                keyboard?.hide()
                focusManager.clearFocus(force = true)
                sheetState.hide()
                currentOnDismiss()
            }
        }
    }
    LaunchedEffect(closeRequested) { if (closeRequested) close() }

    // Ritirare toglie la voce alla classe: conferma, e il dialogo sta sopra il foglio (come
    // "Elimina evento" nel Calendario). Se l'operazione fallisce il foglio resta e c'e' l'avviso.
    var confirmWithdraw by remember { mutableStateOf(false) }
    if (confirmWithdraw) {
        AilaConfirmDialog(
            title = "Ritirare «${step.item.title}»?",
            message = "La voce non sarà più visibile in classe. Lo storico resta consultabile dal Rappresentante.",
            confirmLabel = "Ritira",
            onDismiss = { confirmWithdraw = false },
            onConfirm = {
                confirmWithdraw = false
                onWithdraw(step.item)
            }
        )
    }

    AilaBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Dai passi interni "indietro" torna al dettaglio; dal dettaglio chiude il foglio.
        PlatformBackHandler(enabled = step !is ItemStep.Detail) { onStep(ItemStep.Detail(step.item)) }
        GitaSheetBody(
            title = when (step) {
                is ItemStep.Detail -> if (step.item.isLink) "Link" else step.item.category.label
                is ItemStep.Versions -> "Storico"
                is ItemStep.Report -> "Segnala un errore"
                is ItemStep.Edit -> "Modifica"
                is ItemStep.ReplaceLink -> "Nuovo indirizzo"
                is ItemStep.ReplaceDocument -> "Nuova versione"
            },
            canGoBack = step !is ItemStep.Detail,
            onBack = { onStep(ItemStep.Detail(step.item)) },
            onClose = close
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxWidth(),
                contentKey = { it::class },
                transitionSpec = { stepTransition() },
                label = "gitaItemStep"
            ) { shown ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (shown) {
                        is ItemStep.Detail -> DetailBody(
                            item = shown.item,
                            canEdit = canEdit,
                            onOpen = { onOpen(shown.item) },
                            onVersions = { onStep(ItemStep.Versions(shown.item)) },
                            onReport = { onStep(ItemStep.Report(shown.item)) },
                            onReplace = {
                                if (shown.item.isLink) onStep(ItemStep.ReplaceLink(shown.item))
                                else onReplaceFile(shown.item)
                            },
                            onEdit = { onStep(ItemStep.Edit(shown.item)) },
                            onWithdraw = { confirmWithdraw = true }
                        )
                        is ItemStep.Versions -> VersionsBody(
                            item = shown.item,
                            loadVersions = loadVersions,
                            onOpenVersion = { onOpenVersion(shown.item, it) }
                        )
                        is ItemStep.Report -> ReportForm(
                            item = shown.item,
                            busy = busy,
                            onSend = { onSendReport(shown.item, it) }
                        )
                        is ItemStep.Edit -> EditForm(
                            item = shown.item,
                            busy = busy,
                            onSave = { title, category -> onSaveEdit(shown.item, title, category) }
                        )
                        is ItemStep.ReplaceLink -> LinkForm(
                            titleEditable = false,
                            initialTitle = shown.item.title,
                            initialUrl = shown.item.current?.url.orEmpty(),
                            saveLabel = "Salva",
                            busy = busy,
                            onSave = { _, url, note -> onSaveLink(shown.item, url, note) }
                        )
                        is ItemStep.ReplaceDocument -> ReplaceDocumentForm(
                            item = shown.item,
                            fileName = shown.fileName,
                            busy = busy,
                            onSave = { note -> onSaveDocument(shown.item, shown.fileName, shown.bytes, note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBody(
    item: GitaItem,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onVersions: () -> Unit,
    onReport: () -> Unit,
    onReplace: () -> Unit,
    onEdit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val look = item.look()
    Row(
        modifier = Modifier.fillMaxWidth().ailaSheetReveal(0),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaIconTile(tint = look.tint) {
            if (item.isLink) AppIcons.ExternalLink(modifier = Modifier.size(20.dp), color = look.ink)
            else AppIcons.Document(modifier = Modifier.size(20.dp), color = look.ink)
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.TextDark,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Aggiornato il ${formatDay(item.current?.uploadedAt ?: item.createdAt)}" +
                    (item.current?.let { " · versione ${it.versionNo}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.TextMuted
            )
        }
    }
    Spacer(modifier = Modifier.height(AppTheme.Space16))
    AilaPrimaryButton(
        text = if (item.isLink) "Apri il link" else "Apri il PDF",
        fillMaxWidth = true,
        enabled = if (item.isLink) item.current?.url != null else item.current?.hasFile == true,
        onClick = onOpen,
        modifier = Modifier.ailaSheetReveal(1)
    )
    Spacer(modifier = Modifier.height(AppTheme.Space12))
    AilaCard(modifier = Modifier.ailaSheetReveal(2)) {
        AilaListRow(
            title = "Storico versioni",
            tint = AppTheme.TintBlue,
            onClick = onVersions,
            icon = { AppIcons.History(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) }
        )
        AilaListRow(
            title = "Segnala un errore",
            subtitle = "Dati sbagliati o non aggiornati",
            tint = AppTheme.TintAmber,
            onClick = onReport,
            icon = { AppIcons.Warning(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk) }
        )
        if (canEdit) {
            AilaListRow(
                title = if (item.isLink) "Nuovo indirizzo" else "Sostituisci il file",
                tint = AppTheme.TintBlue,
                onClick = onReplace,
                icon = { AppIcons.Refresh(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) }
            )
            AilaListRow(
                title = "Modifica",
                tint = AppTheme.TintSlate,
                onClick = onEdit,
                icon = { AppIcons.Pencil(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk) }
            )
            AilaListRow(
                title = "Ritira",
                subtitle = "Non sarà più visibile in classe",
                tint = AppTheme.TintRed,
                onClick = onWithdraw,
                icon = { AppIcons.Trash(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk) }
            )
        }
    }
}

private enum class VersionsState { LOADING, FAILED, EMPTY, LIST }

@Composable
private fun VersionsBody(
    item: GitaItem,
    loadVersions: suspend (String) -> List<GitaVersion>,
    onOpenVersion: (GitaVersion) -> Unit,
) {
    var versions by remember { mutableStateOf<List<GitaVersion>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        try {
            versions = loadVersions(item.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = (e as? ApiException)?.message ?: "Storico non disponibile."
        }
    }
    val state = when {
        failed != null -> VersionsState.FAILED
        versions == null -> VersionsState.LOADING
        versions.orEmpty().isEmpty() -> VersionsState.EMPTY
        else -> VersionsState.LIST
    }
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.TextMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = AppTheme.Space12)
    )
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = { stepTransition() },
        label = "gitaVersions"
    ) { shown ->
        Column(modifier = Modifier.fillMaxWidth()) {
            when (shown) {
                VersionsState.LOADING -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.Space24),
                    contentAlignment = Alignment.Center
                ) { AilaMorphingLoader(size = 32.dp) }
                VersionsState.FAILED -> InfoLine(failed ?: "Storico non disponibile.")
                VersionsState.EMPTY -> InfoLine("Nessuna versione.")
                VersionsState.LIST -> AilaCard {
                    versions.orEmpty().forEach { v ->
                        val current = v.id == item.current?.id
                        AilaListRow(
                            title = "Versione ${v.versionNo}${if (current) " · attuale" else ""}",
                            subtitle = listOfNotNull("Caricata il ${formatDay(v.uploadedAt)}", v.note).joinToString(" · "),
                            tint = if (current) AppTheme.TintBlue else AppTheme.TintSlate,
                            onClick = if (v.hasFile || v.url != null) ({ onOpenVersion(v) }) else null,
                            icon = {
                                Text(
                                    text = "v${v.versionNo}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (current) AppTheme.TintBlueInk else AppTheme.TintSlateInk
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.TextMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.Space12)
    )
}

@Composable
private fun ReportForm(item: GitaItem, busy: Boolean, onSend: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        Text(
            text = "«${item.title}» · errore, data o importo sbagliati, documento vecchio. " +
                "Il Rappresentante la vede e tu ricevi lo stato.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.TextMuted,
            modifier = Modifier.ailaSheetReveal(0)
        )
        AilaPillTextField(
            value = reason,
            onValueChange = { reason = it },
            placeholder = "Cosa non va?",
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(1),
            maxLines = 4
        )
        AilaPrimaryButton(
            text = "Invia segnalazione",
            fillMaxWidth = true,
            enabled = !busy && reason.isNotBlank(),
            onClick = { onSend(reason.trim()) },
            modifier = Modifier.ailaSheetReveal(2)
        )
    }
}

@Composable
private fun EditForm(item: GitaItem, busy: Boolean, onSave: (String, GitaCategory) -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var category by remember { mutableStateOf(item.category) }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        AilaPillTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Titolo",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(0)
        )
        if (!item.isLink) CategoryChips(selected = category, onSelect = { category = it }, modifier = Modifier.ailaSheetReveal(1))
        AilaPrimaryButton(
            text = "Salva",
            fillMaxWidth = true,
            enabled = !busy && title.isNotBlank(),
            onClick = { onSave(title.trim(), category) },
            modifier = Modifier.ailaSheetReveal(2)
        )
    }
}

@Composable
private fun ReplaceDocumentForm(item: GitaItem, fileName: String, busy: Boolean, onSave: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
        Text(
            text = "Sostituisci «${item.title}». La versione attuale resta nello storico.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.TextMuted,
            modifier = Modifier.ailaSheetReveal(0)
        )
        FileLine(fileName = fileName, modifier = Modifier.ailaSheetReveal(1))
        AilaPillTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Nota (facoltativa)",
            modifier = Modifier.fillMaxWidth().ailaSheetReveal(2)
        )
        AilaPrimaryButton(
            text = "Carica la nuova versione",
            fillMaxWidth = true,
            enabled = !busy,
            onClick = { onSave(note.trim().ifEmpty { null }) },
            modifier = Modifier.ailaSheetReveal(3)
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Categorie
// -------------------------------------------------------------------------------------------------

/** Categorie come chip colorati: il selezionato si riempie del colore della categoria, senza spunte. */
@Composable
private fun CategoryChips(selected: GitaCategory, onSelect: (GitaCategory) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
    val shape = RoundedCornerShape(100.dp)
    val background by animateColorAsState(if (selected) ink else tint, animationSpec = ailaColorSpec(), label = "gitaChipBg")
    val text by animateColorAsState(if (selected) Color.White else ink, animationSpec = ailaColorSpec(), label = "gitaChipText")
    Box(
        modifier = Modifier
            .clip(shape)
            .background(background)
            // Liquid Glass: il filo di luce dei controlli di vetro anche sulle chip colorate.
            .then(if (AppTheme.isGlass) Modifier.border(1.dp, AppTheme.GlassEdge, shape) else Modifier)
            .ailaPressable(pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
