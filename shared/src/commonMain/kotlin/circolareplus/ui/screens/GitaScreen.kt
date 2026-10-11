@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.ApiException
import circolareplus.data.repository.GitaRepository
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaDuration
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaErrorState
import circolareplus.design.AilaFab
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaListRow
import circolareplus.design.AilaMorphingLoader
import circolareplus.design.AilaPillTextField
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AilaSlidingChipRow
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaCollapseExit
import circolareplus.design.ailaColorSpec
import circolareplus.design.ailaExpandEnter
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaPressable
import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaCircular
import circolareplus.domain.model.GitaFeed
import circolareplus.domain.model.GitaItem
import circolareplus.domain.model.GitaReport
import circolareplus.domain.model.GitaVersion
import circolareplus.platform.rememberPdfFilePicker
import circolareplus.platform.sharePdfFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Colori di ogni categoria: sfondo tenue per la riga e colore pieno per la selezione. */
internal data class CategoryTint(val tint: Color, val ink: Color, val label: String)

internal fun GitaCategory.look(): CategoryTint = when (this) {
    GitaCategory.PROGRAMMA -> CategoryTint(AppTheme.TintBlue, AppTheme.TintBlueInk, label)
    GitaCategory.PREVENTIVO -> CategoryTint(AppTheme.TintAmber, AppTheme.TintAmberInk, label)
    GitaCategory.SCADENZA -> CategoryTint(AppTheme.TintRed, AppTheme.TintRedInk, label)
    GitaCategory.REGOLAMENTO -> CategoryTint(AppTheme.TintViolet, AppTheme.TintVioletInk, label)
    GitaCategory.PAGAMENTO -> CategoryTint(AppTheme.TintGreen, AppTheme.TintGreenInk, label)
    GitaCategory.ALTRO -> CategoryTint(AppTheme.TintSlate, AppTheme.TintSlateInk, label)
}

/** I link non hanno una categoria: tinta neutra. */
internal fun GitaItem.look(): CategoryTint =
    if (isLink) CategoryTint(AppTheme.TintSlate, AppTheme.TintSlateInk, "Link") else category.look()

/** Avviso in cima alla lista dopo un'operazione: riuscita (verde) o fallita (rossa). */
private data class GitaNotice(val text: String, val isError: Boolean = false)

/** Cosa mostra il corpo della schermata: attesa, errore o la lista. */
private enum class GitaBody { LOADING, ERROR, READY }

/**
 * Gita: documenti, link e circolari della classe, con lo storico delle versioni.
 * Chiunque legge; solo il Rappresentante carica, sostituisce, modifica, ritira e sceglie le
 * circolari. Ogni utente può segnalare una voce e vede lo stato della sua segnalazione.
 * Il pulsante AILA Assistant apre la chat che risponde solo con il materiale della gita.
 *
 * Il movimento segue quello delle altre schermate: il "+" è un pulsante tondo che in Material apre
 * il foglio da sé e ci rientra, in Liquid Glass è vetro; i fogli hanno passi con la freccia
 * indietro (vedi GitaSheets.kt); caricamento, errore e lista si dissolvono l'uno nell'altro.
 */
@Composable
fun GitaScreen(
    repository: GitaRepository,
    /** Ultimo elenco gia' letto: mostrato subito, senza schermata di caricamento. */
    cachedFeed: GitaFeed?,
    /** Chiamato a ogni lettura riuscita, per tenere la copia di chi sta sopra. */
    onFeedLoaded: (GitaFeed) -> Unit,
    onBackClick: () -> Unit,
    onOpenAssistant: () -> Unit,
    /** Chiamato dopo ogni modifica: l'assistente rilegge il materiale alla domanda successiva. */
    onMaterialChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var feed by remember { mutableStateOf(cachedFeed) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    // Foglio "+" e foglio di una voce. Il "closeRequested" li fa scendere con la loro animazione
    // a operazione finita (togliendoli dalla composizione sparirebbero di colpo).
    var addStep by remember { mutableStateOf<AddStep?>(null) }
    var addClose by remember { mutableStateOf(false) }
    var itemStep by remember { mutableStateOf<ItemStep?>(null) }
    var itemClose by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<GitaNotice?>(null) }
    // Voce da sostituire mentre si sceglie il file: il picker torna con il file, non con la voce.
    var replaceTarget by remember { mutableStateOf<GitaItem?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun messageOf(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Operazione non riuscita."
        else -> "Operazione non riuscita. Riprova."
    }

    /** Una scrittura: occupato durante l'attesa, poi ricarica e fa scendere i fogli aperti. */
    fun write(message: String, action: suspend () -> Unit) {
        scope.launch {
            busy = true
            notice = null
            try {
                action()
                notice = GitaNotice(message)
                if (addStep != null) addClose = true
                if (itemStep != null) itemClose = true
                onMaterialChanged()
                reloadKey++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Anche gli Error (memoria finita con un PDF molto grande): un messaggio, non la
                // chiusura dell'app. Il foglio resta aperto: quello che si era scritto non si perde.
                notice = GitaNotice(messageOf(e), isError = true)
            } finally {
                busy = false
            }
        }
    }

    val pickPdf = rememberPdfFilePicker(
        onError = { message ->
            replaceTarget = null
            notice = GitaNotice(message, isError = true)
        },
        onPicked = { fileName, bytes ->
            val target = replaceTarget
            if (target != null) itemStep = ItemStep.ReplaceDocument(target, fileName, bytes)
            else addStep = AddStep.Document(fileName, bytes)
            replaceTarget = null
        }
    )

    fun openPdf(item: GitaItem, version: GitaVersion) {
        scope.launch {
            openPdfVersion(
                repository, version.id, item.title, version.versionNo,
                onError = { notice = GitaNotice(it, isError = true) }
            )
        }
    }

    LaunchedEffect(reloadKey) {
        refreshing = true
        try {
            val loaded = repository.feed()
            feed = loaded
            onFeedLoaded(loaded)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Con dati già a schermo un errore di rete non li cancella: resta l'avviso.
            if (feed == null) error = messageOf(e) else notice = GitaNotice(messageOf(e), isError = true)
        } finally {
            refreshing = false
        }
    }

    // L'avviso resta qualche secondo e poi se ne va da solo.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4500)
            notice = null
        }
    }

    val current = feed
    val canEdit = current?.canEdit == true
    val body = when {
        current != null -> GitaBody.READY
        error != null -> GitaBody.ERROR
        else -> GitaBody.LOADING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AilaBackBar(
                title = "Gita",
                onBackClick = onBackClick,
                action = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Aggiornamento in corso con i dati gia' a schermo: il loader dell'app nella
                        // barra, senza spostare la lista (prima una barra di Material sopra di lei).
                        AnimatedVisibility(
                            visible = refreshing && current != null,
                            enter = fadeIn(ailaFadeSpec()) + scaleIn(ailaMoveSpec(), initialScale = 0.6f) +
                                expandHorizontally(ailaMoveSpec()),
                            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)) + scaleOut(ailaMoveSpec(), targetScale = 0.6f) +
                                shrinkHorizontally(ailaMoveSpec())
                        ) {
                            Box(modifier = Modifier.padding(end = AppTheme.Space12)) {
                                AilaMorphingLoader(size = 20.dp)
                            }
                        }
                        // Solo il Rappresentante: compare quando arriva il feed, non di colpo.
                        AnimatedVisibility(
                            visible = canEdit,
                            enter = fadeIn(ailaFadeSpec()) + scaleIn(ailaMoveSpec(), initialScale = 0.6f) +
                                expandHorizontally(ailaMoveSpec()),
                            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)) + scaleOut(ailaMoveSpec(), targetScale = 0.6f) +
                                shrinkHorizontally(ailaMoveSpec())
                        ) {
                            // Come il "+" del Calendario: gradiente del brand (vetro in Glass) e, in
                            // Material, il foglio nasce da qui e ci rientra.
                            AilaIconButton(
                                contentDescription = "Aggiungi alla gita",
                                onClick = {
                                    addClose = false
                                    addStep = AddStep.Choose
                                },
                                primary = true,
                                enabled = !busy,
                                opensPage = true
                            ) { tint ->
                                AppIcons.Plus(modifier = Modifier.size(18.dp), color = tint)
                            }
                        }
                    }
                }
            )
            AnimatedContent(
                targetState = body,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(ailaFadeSpec(190, delayMillis = 60)),
                        initialContentExit = fadeOut(ailaFadeSpec(AilaDuration.Quick))
                    )
                },
                label = "gitaBody"
            ) { shown ->
                when (shown) {
                    GitaBody.LOADING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AilaMorphingLoader(size = 48.dp)
                    }
                    GitaBody.ERROR -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        AilaErrorState(
                            message = error ?: "Gita non disponibile.",
                            onRetry = {
                                error = null
                                reloadKey++
                            }
                        )
                    }
                    GitaBody.READY -> feed?.let { ready ->
                        GitaList(
                            feed = ready,
                            notice = notice,
                            onOpen = { itemStep = ItemStep.Detail(it) },
                            onPinCircular = { circular, pinned ->
                                write(if (pinned) "Circolare aggiunta alla gita." else "Circolare tolta dalla gita.") {
                                    repository.setCircularPinned(circular.number, pinned)
                                }
                            },
                            onResolveReport = { report ->
                                write("Segnalazione risolta.") { repository.setReportResolved(report.id, resolved = true) }
                            },
                            canEdit = ready.canEdit,
                            busy = busy,
                        )
                    }
                }
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

    addStep?.let { step ->
        GitaAddSheet(
            step = step,
            onStep = { addStep = it },
            busy = busy,
            closeRequested = addClose,
            onDismiss = {
                addStep = null
                addClose = false
            },
            // Il selettore dei file di sistema si apre a foglio chiuso; il file scelto riapre il foglio
            // gia' sul passo "Nuovo documento", con la freccia per tornare alla scelta.
            onPickPdf = {
                replaceTarget = null
                addStep = null
                addClose = false
                pickPdf()
            },
            onSaveDocument = { title, category, note, fileName, bytes ->
                write("Documento caricato.") { repository.addDocument(title, category, fileName, bytes, note) }
            },
            onSaveLink = { title, url, note -> write("Link aggiunto.") { repository.addLink(title, url, note) } }
        )
    }

    itemStep?.let { step ->
        GitaItemSheet(
            step = step,
            onStep = { itemStep = it },
            canEdit = canEdit,
            busy = busy,
            closeRequested = itemClose,
            onDismiss = {
                itemStep = null
                itemClose = false
            },
            onOpen = { item ->
                val version = item.current
                if (item.isLink) {
                    version?.url?.let { uriHandler.openUri(it) }
                } else if (version != null) {
                    openPdf(item, version)
                }
                itemClose = true
            },
            onReplaceFile = { item ->
                replaceTarget = item
                itemStep = null
                itemClose = false
                pickPdf()
            },
            loadVersions = { id -> repository.versions(id).second },
            onOpenVersion = { item, version ->
                if (version.url != null) uriHandler.openUri(version.url) else openPdf(item, version)
            },
            onSaveEdit = { item, title, category ->
                write("Modifiche salvate.") { repository.updateItem(item.id, title, category) }
            },
            onSaveLink = { item, url, note ->
                write("Indirizzo aggiornato.") { repository.replaceLink(item.id, url, note) }
            },
            onSaveDocument = { item, fileName, bytes, note ->
                write("Nuova versione caricata.") { repository.replaceDocument(item.id, fileName, bytes, note) }
            },
            onSendReport = { item, reason ->
                write("Segnalazione inviata: riceverai lo stato.") { repository.report(item.id, reason) }
            },
            onWithdraw = { item -> write("Voce ritirata.") { repository.withdraw(item.id) } }
        )
    }
}

private enum class GitaFilter(val label: String) {
    ALL("Tutti"), CIRCULARS("Circolari"), DOCUMENTS("Documenti"), LINKS("Link"), REPORTS("Segnalazioni")
}

/**
 * Movimento delle sezioni della lista: cambiando filtro una sezione si dissolve e le altre
 * scivolano al loro posto con la molla di navigazione (come le card della Bacheca).
 */
private fun LazyItemScope.gitaMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = ailaFadeSpec(),
    placementSpec = ailaNavigationSpring(),
    fadeOutSpec = ailaFadeSpec(AilaDuration.Quick)
)

@Composable
private fun GitaList(
    feed: GitaFeed,
    notice: GitaNotice?,
    onOpen: (GitaItem) -> Unit,
    onPinCircular: (GitaCircular, Boolean) -> Unit,
    onResolveReport: (GitaReport) -> Unit,
    canEdit: Boolean,
    busy: Boolean,
) {
    var filter by remember { mutableStateOf(GitaFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val listState = rememberLazyListState()

    // "Segnalazioni" ha senso solo se ce ne sono: prima la chip c'era sempre e portava a una lista vuota.
    val filters = if (feed.reports.isNotEmpty()) GitaFilter.entries else GitaFilter.entries.filter { it != GitaFilter.REPORTS }
    LaunchedEffect(filters.size) { if (filter !in filters) filter = GitaFilter.ALL }
    // Cambiando filtro si torna in cima, come nella lista delle Circolari.
    LaunchedEffect(filter) { listState.scrollToItem(0) }

    fun matches(title: String) = needle.isEmpty() || title.lowercase().contains(needle)
    fun circularMatches(c: GitaCircular) = matches(c.title) || "${c.number}".contains(needle)
    val documents = feed.items.filter { !it.isLink }
    val links = feed.items.filter { it.isLink }
    val circularsShown = feed.circulars.count { circularMatches(it) }
    val documentsShown = documents.count { matches(it.title) }
    val linksShown = links.count { matches(it.title) }
    val show = { f: GitaFilter -> filter == GitaFilter.ALL || filter == f }
    val allEmpty = feed.circulars.isEmpty() && feed.items.isEmpty() && feed.reports.isEmpty()

    // Voci gia' presenti alla prima apertura: le righe che arrivano dopo (un documento appena
    // caricato) entrano con l'animazione, le altre sono semplicemente al loro posto.
    val initialKeys = remember {
        buildSet<Any> {
            feed.circulars.forEach { add("c${it.number}") }
            feed.items.forEach { add(it.id) }
            feed.reports.forEach { add("r${it.id}") }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = AppTheme.Space16, end = AppTheme.Space16, top = AppTheme.Space12, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space16)
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
                    trailingIcon = {
                        // Compare solo quando c'e' qualcosa da svuotare, con un piccolo ingresso.
                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn(ailaFadeSpec()) + scaleIn(ailaMoveSpec(), initialScale = 0.6f),
                            exit = fadeOut(ailaFadeSpec(AilaDuration.Quick)) + scaleOut(ailaMoveSpec(), targetScale = 0.6f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = AppTheme.Space4)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable { query = "" }
                                    .semantics {
                                        contentDescription = "Svuota la ricerca"
                                        role = Role.Button
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Close(modifier = Modifier.size(16.dp), color = AppTheme.TextFaint)
                            }
                        }
                    }
                )
                AilaSlidingChipRow(
                    selectedIndex = filters.indexOf(filter).coerceAtLeast(0),
                    itemCount = filters.size,
                    modifier = Modifier.fillMaxWidth()
                ) { chipModifier ->
                    filters.forEachIndexed { index, f ->
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

        // L'avviso entra e esce dalla lista: si dissolve e le sezioni sotto scivolano al loro posto
        // (un elemento sempre presente ma alto zero lascerebbe comunque lo spazio fra gli elementi).
        notice?.let { shown ->
            item(key = "notice") {
                NoticeBanner(shown, modifier = gitaMotion())
            }
        }

        if (allEmpty && filter == GitaFilter.ALL) {
            item(key = "empty") {
                AilaEmptyState(
                    title = "La gita è ancora vuota",
                    message = if (canEdit) {
                        "Tocca + per caricare il programma, un preventivo o il link dell'agenzia."
                    } else {
                        "Quando il Rappresentante aggiungerà documenti, circolari o link li troverai qui."
                    },
                    modifier = gitaMotion(),
                    icon = { AppIcons.Bus(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        } else {
            if (show(GitaFilter.CIRCULARS)) {
                item(key = "circulars") {
                    GitaSection(title = "Circolari della gita", count = circularsShown, modifier = gitaMotion()) {
                        EmptyLine(
                            visible = circularsShown == 0,
                            text = if (needle.isEmpty()) "Nessuna circolare sulla gita per questa classe." else "Nessuna circolare trovata."
                        )
                        feed.circulars.forEach { circular ->
                            key(circular.number) {
                                RowReveal(visible = circularMatches(circular), appear = "c${circular.number}" !in initialKeys) {
                                    AilaListRow(
                                        title = "Circolare n. ${circular.number}",
                                        subtitle = "${circular.title} · ${formatDay(circular.publishDate)}",
                                        tint = AppTheme.TintBlue,
                                        icon = { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) },
                                        trailing = if (canEdit) {
                                            {
                                                PinToggle(
                                                    pinned = circular.pinned,
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
                }
            }

            if (show(GitaFilter.DOCUMENTS)) {
                item(key = "documents") {
                    GitaSection(title = "Documenti", count = documentsShown, modifier = gitaMotion()) {
                        EmptyLine(
                            visible = documentsShown == 0,
                            text = if (needle.isEmpty()) "Nessun documento caricato." else "Nessun documento trovato."
                        )
                        documents.forEach { item ->
                            key(item.id) {
                                RowReveal(visible = matches(item.title), appear = item.id !in initialKeys) {
                                    ItemRow(item = item, onClick = { onOpen(item) })
                                }
                            }
                        }
                    }
                }
            }

            if (show(GitaFilter.LINKS)) {
                item(key = "links") {
                    GitaSection(title = "Link utili", count = linksShown, modifier = gitaMotion()) {
                        EmptyLine(
                            visible = linksShown == 0,
                            text = if (needle.isEmpty()) "Nessun link." else "Nessun link trovato."
                        )
                        links.forEach { item ->
                            key(item.id) {
                                RowReveal(visible = matches(item.title), appear = item.id !in initialKeys) {
                                    ItemRow(item = item, onClick = { onOpen(item) })
                                }
                            }
                        }
                    }
                }
            }

            if (show(GitaFilter.REPORTS) && feed.reports.isNotEmpty()) {
                item(key = "reports") {
                    GitaSection(
                        title = if (canEdit) "Segnalazioni" else "Le tue segnalazioni",
                        count = feed.reports.count { !it.isResolved },
                        countLabel = "da leggere",
                        modifier = gitaMotion()
                    ) {
                        feed.reports.forEach { report ->
                            key(report.id) {
                                RowReveal(visible = true, appear = "r${report.id}" !in initialKeys) {
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
        }
    }
}

/**
 * Sezione della Gita: un titolo con il conteggio (che si dissolve da un numero all'altro) e le
 * righe raccolte in una sola card. Il titolo è quello delle altre schermate ([AilaSectionTitle]).
 */
@Composable
private fun GitaSection(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = AppTheme.Space4)) {
            AilaSectionTitle(text = title, modifier = Modifier.weight(1f))
            AnimatedContent(
                targetState = if (countLabel != null) "$count $countLabel" else "$count",
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(ailaFadeSpec(150, delayMillis = 50)),
                        initialContentExit = fadeOut(ailaFadeSpec(80))
                    )
                },
                label = "gitaCount"
            ) { label ->
                Text(label, style = MaterialTheme.typography.labelLarge, color = AppTheme.TextMuted)
            }
        }
        AilaCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Una riga che si apre e si chiude (ricerca che la nasconde, voce appena arrivata) invece di
 * comparire e sparire di colpo. [appear]: la riga e' arrivata dopo la prima apertura e deve entrare.
 */
@Composable
private fun RowReveal(visible: Boolean, appear: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(!appear) }
    state.targetState = visible
    AnimatedVisibility(visibleState = state, enter = ailaExpandEnter(), exit = ailaCollapseExit()) {
        content()
    }
}

/** Riga di testo dentro una card quando non c'e' niente: si apre e si chiude come le righe vere. */
@Composable
private fun EmptyLine(visible: Boolean, text: String) {
    AnimatedVisibility(visible = visible, enter = ailaExpandEnter(), exit = ailaCollapseExit()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.TextMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
        )
    }
}

/** Avviso dopo un'operazione: card (vetro in Glass) con l'icona del suo esito. */
@Composable
private fun NoticeBanner(notice: GitaNotice, modifier: Modifier = Modifier) {
    val tint = if (notice.isError) AppTheme.TintRed else AppTheme.TintGreen
    val ink = if (notice.isError) AppTheme.TintRedInk else AppTheme.TintGreenInk
    AilaCard(modifier = modifier, containerColor = tint) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (notice.isError) AppIcons.Warning(modifier = Modifier.size(18.dp), color = ink)
            else AppIcons.Check(modifier = Modifier.size(18.dp), color = ink)
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Text(
                text = notice.text,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * "Aggiungi" / "Aggiunta" di una circolare: pillola che cambia colore e testo con una
 * dissolvenza, non di colpo, e si schiaccia al tocco come gli altri pulsanti.
 */
@Composable
private fun PinToggle(pinned: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (pinned) AppTheme.TintGreen else AppTheme.TintBlue,
        animationSpec = ailaColorSpec(),
        label = "gitaPinBg"
    )
    val ink by animateColorAsState(
        if (pinned) AppTheme.TintGreenInk else AppTheme.TintBlueInk,
        animationSpec = ailaColorSpec(),
        label = "gitaPinInk"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .ailaPressable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = AppTheme.Space12),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = pinned,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(ailaFadeSpec(150, delayMillis = 50)),
                    initialContentExit = fadeOut(ailaFadeSpec(80))
                )
            },
            label = "gitaPinLabel"
        ) { isPinned ->
            Text(
                text = if (isPinned) "Aggiunta" else "Aggiungi",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) ink else AppTheme.TextMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ItemRow(item: GitaItem, onClick: () -> Unit) {
    val look = item.look()
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

/** Una segnalazione dentro la card della sezione (niente card dentro la card: in Glass era vetro su vetro). */
@Composable
private fun ReportRow(report: GitaReport, showResolve: Boolean, busy: Boolean, onResolve: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)
    ) {
        Text(
            report.itemTitle,
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.TextDark,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(report.reason, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextDark)
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
        modifier = Modifier
            .height(44.dp)
            .ailaPressable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = AppTheme.Space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) AppTheme.PrimaryBlue else AppTheme.TextMuted,
            fontWeight = FontWeight.SemiBold
        )
    }
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
