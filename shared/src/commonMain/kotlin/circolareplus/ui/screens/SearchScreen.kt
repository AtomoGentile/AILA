package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import circolareplus.design.AilaAssistantMark
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaPillTextField
import circolareplus.design.ailaAssistantAurora
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaListRow
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.ailaAppear
import circolareplus.design.ailaGlassPressable
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular
import circolareplus.domain.model.Proposal

/**
 * Ricerca globale (mockup "Ricerca"): una sola casella che cerca contemporaneamente tra circolari,
 * eventi di calendario e proposte della bacheca.
 *
 * Cerca solo tra i dati che l'app ha già scaricato — non interroga il server e non scarica i PDF:
 * per le circolari confronta numero e titolo, non il contenuto del documento. È una scelta, non una
 * dimenticanza: cercare dentro i PDF vorrebbe dire scaricarli e analizzarli tutti a ogni lettera
 * digitata. Se serve anche quello va costruito diversamente (indice costruito una volta sola
 * quando la circolare viene classificata).
 */
enum class SearchFilter(val label: String) {
    ALL("Tutto"),
    CIRCULARS("Circolari"),
    EVENTS("Calendario"),
    PROPOSALS("Bacheca")
}

private data class SearchHit(
    val kind: SearchFilter,
    val title: String,
    val subtitle: String,
    val onOpen: () -> Unit
)

@Composable
fun SearchScreen(
    circulars: List<Circular>,
    calendarEvents: List<CalendarEvent>,
    proposals: List<Proposal>,
    recentSearches: List<String>,
    onBackClick: () -> Unit,
    onSubmitQuery: (String) -> Unit = {},
    onOpenAssistant: (String) -> Unit = {},
    onOpenCircular: (Circular) -> Unit = {},
    onOpenCirculars: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenBoard: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    val trimmed = query.trim()

    val hits: List<SearchHit> = remember(trimmed, filter, circulars, calendarEvents, proposals) {
        if (trimmed.length < 2) {
            emptyList()
        } else {
            buildList {
                if (filter == SearchFilter.ALL || filter == SearchFilter.CIRCULARS) {
                    circulars.filter {
                        it.title.contains(trimmed, ignoreCase = true) ||
                            it.number.toString().contains(trimmed)
                    }.forEach { circ ->
                        add(
                            SearchHit(
                                kind = SearchFilter.CIRCULARS,
                                title = "Circolare n. ${circ.number} — ${circ.title}",
                                subtitle = "Pubblicata il ${circ.publishDate}",
                                onOpen = { onOpenCircular(circ) }
                            )
                        )
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.EVENTS) {
                    calendarEvents.filter { it.title.contains(trimmed, ignoreCase = true) }
                        .forEach { event ->
                            add(
                                SearchHit(
                                    kind = SearchFilter.EVENTS,
                                    title = event.title,
                                    subtitle = "${event.date}${event.time?.let { " • $it" } ?: ""}",
                                    onOpen = onOpenCalendar
                                )
                            )
                        }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.PROPOSALS) {
                    proposals.filter {
                        it.title.contains(trimmed, ignoreCase = true) ||
                            it.description.contains(trimmed, ignoreCase = true)
                    }.forEach { proposal ->
                        add(
                            SearchHit(
                                kind = SearchFilter.PROPOSALS,
                                title = proposal.title,
                                subtitle = "Proposta di ${proposal.authorName}",
                                onOpen = onOpenBoard
                            )
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaBackBar(title = "Cerca in AILA", onBackClick = onBackClick)

        Column(modifier = Modifier.padding(horizontal = AppTheme.Space16)) {
            Spacer(modifier = Modifier.height(AppTheme.Space16))

            // Capsula con l'anello dell'Assistant al focus: stessa della chat.
            AilaPillTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.trim().length >= 2) onSubmitQuery(it)
                },
                placeholder = "Circolari, eventi, proposte…",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        // Area di tocco di 44dp (il minimo consigliato). Il nome serve al lettore
                        // di schermo, che altrimenti annunciava solo "pulsante".
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

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            circolareplus.design.AilaSlidingChipRow(
                selectedIndex = SearchFilter.entries.indexOf(filter),
                itemCount = SearchFilter.entries.size,
                modifier = Modifier.fillMaxWidth()
            ) { chipModifier ->
                SearchFilter.entries.forEachIndexed { index, f ->
                    AnimatedFilterChip(
                        label = f.label,
                        isSelected = filter == f,
                        onClick = { filter = f },
                        drawSelectionBackground = false,
                        modifier = chipModifier(index)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            AskAilaButton(query = trimmed, onClick = { onOpenAssistant(trimmed) })

            Spacer(modifier = Modifier.height(AppTheme.Space16))
        }

        when {
            // Meno di due lettere: si mostrano le ricerche recenti e dove si può guardare.
            trimmed.length < 2 -> SearchIdleContent(
                recentSearches = recentSearches,
                circularCount = circulars.size,
                eventCount = calendarEvents.size,
                proposalCount = proposals.size,
                onPickRecent = { query = it },
                onOpenCirculars = onOpenCirculars,
                onOpenCalendar = onOpenCalendar,
                onOpenBoard = onOpenBoard
            )

            hits.isEmpty() -> AilaEmptyState(
                title = "Nessun risultato",
                message = "Niente che corrisponda a \"$trimmed\" tra circolari, calendario e bacheca. " +
                    "L'Assistant può provare a rispondere alla domanda.",
                actionLabel = "Chiedi all'Assistant",
                onAction = { onOpenAssistant(trimmed) },
                icon = { AppIcons.Search(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
            )

            else -> circolareplus.design.AilaAdaptiveCardList(
                items = hits,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    bottom = AppTheme.Space24
                ),
                header = {
                    Text(
                        text = if (hits.size == 1) "1 risultato" else "${hits.size} risultati",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextFaint
                    )
                }
            ) { index, hit ->
                AilaCard(onClick = hit.onOpen, modifier = Modifier.ailaAppear(index)) {
                    AilaListRow(
                        title = hit.title,
                        subtitle = hit.subtitle,
                        tint = hit.kind.tint(),
                        onClick = hit.onOpen,
                        icon = { hit.kind.Icon() }
                    )
                }
            }
        }
    }
}

/** Contenuto quando non si sta ancora cercando nulla: ricerche recenti e scorciatoie. */
@Composable
private fun SearchIdleContent(
    recentSearches: List<String>,
    circularCount: Int,
    eventCount: Int,
    proposalCount: Int,
    onPickRecent: (String) -> Unit,
    onOpenCirculars: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenBoard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space16)
    ) {
        if (recentSearches.isNotEmpty()) {
            AilaSectionTitle(text = "Ricerche recenti")
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            AilaCard {
                recentSearches.forEach { recent ->
                    AilaListRow(
                        title = recent,
                        tint = AppTheme.TintSlate,
                        onClick = { onPickRecent(recent) },
                        icon = {
                            AppIcons.History(modifier = Modifier.size(19.dp), color = AppTheme.TintSlateInk)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppTheme.Space24))
        }

        AilaSectionTitle(text = "Dove posso cercare")
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        // Tre tessere affiancate, ognuna col colore della sua sezione (lo stesso di fonti e
        // risultati) e quanti elementi ci sono da cercare: la lista di righe uguali non diceva
        // niente di piu' dei filtri sopra.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            SearchSectionTile(
                title = "Circolari",
                subtitle = "Per numero o titolo",
                count = circularCount,
                tint = AppTheme.TintBlue,
                ink = AppTheme.TintBlueInk,
                onClick = onOpenCirculars,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) }
            SearchSectionTile(
                title = "Calendario",
                subtitle = "Verifiche e scadenze",
                count = eventCount,
                tint = AppTheme.TintAmber,
                ink = AppTheme.TintAmberInk,
                onClick = onOpenCalendar,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { AppIcons.Calendar(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk) }
            SearchSectionTile(
                title = "Bacheca",
                subtitle = "Proposte della classe",
                count = proposalCount,
                tint = AppTheme.TintViolet,
                ink = AppTheme.TintVioletInk,
                onClick = onOpenBoard,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) { AppIcons.ChatBubble(modifier = Modifier.size(20.dp), color = AppTheme.TintVioletInk) }
        }
    }
}

@Composable
private fun SearchSectionTile(
    title: String,
    subtitle: String,
    count: Int,
    tint: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    AilaCard(onClick = onClick, containerColor = tint, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppTheme.Space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Il riquadro dell'icona e' del colore dell'inchiostro, molto chiaro: sopra una
                // tessera gia' colorata il tono pieno della lista non si leggeva come un rilievo.
                AilaIconTile(tint = ink.copy(alpha = 0.14f), size = 40.dp, icon = icon)
                Spacer(modifier = Modifier.weight(1f))
                if (count > 0) {
                    Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, color = ink)
                }
            }
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.TextDark,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextMuted
            )
        }
    }
}

private fun SearchFilter.tint(): Color = when (this) {
    SearchFilter.CIRCULARS -> AppTheme.TintBlue
    SearchFilter.EVENTS -> AppTheme.TintAmber
    SearchFilter.PROPOSALS -> AppTheme.TintViolet
    SearchFilter.ALL -> AppTheme.TintSlate
}

@Composable
private fun SearchFilter.Icon() {
    when (this) {
        SearchFilter.CIRCULARS ->
            AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk)
        SearchFilter.EVENTS ->
            AppIcons.Calendar(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk)
        SearchFilter.PROPOSALS ->
            AppIcons.ChatBubble(modifier = Modifier.size(20.dp), color = AppTheme.TintVioletInk)
        SearchFilter.ALL ->
            AppIcons.Search(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk)
    }
}

/**
 * Il pulsante che porta all'assistente, sotto i filtri della ricerca.
 *
 * Sta qui e non fra i risultati di proposito. La ricerca normale confronta parole: trova la
 * circolare che ha "gita" nel titolo, non risponde a "quanto costa la gita e entro quando devo
 * pagare". Le due cose convivono nella stessa schermata perche' la domanda nasce quasi sempre da
 * una ricerca che non ha dato quello che serviva — e in quel momento il pulsante e' gia' li',
 * con dentro la frase appena digitata, invece di richiedere di riscriverla da un'altra parte.
 *
 * Il gradiente e' lo stesso dei pulsanti primari: in una schermata fatta di righe bianche questo
 * e' l'unico elemento che deve saltare all'occhio, perche' e' l'unico che fa una cosa diversa.
 */
@Composable
private fun AskAilaButton(query: String, onClick: () -> Unit) {
    val hasQuery = query.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.CardCornerRadius))
            // Aurora: l'unico elemento animato della schermata, e l'unico che fa una cosa diversa.
            .ailaAssistantAurora(RoundedCornerShape(AppTheme.CardCornerRadius))
            .ailaGlassPressable { onClick() }
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                // Il pulsante e' blu pieno in entrambi gli stili (PrimaryGradient), quindi i colori
                // "su gradiente" fissi e non gli OnHero*, che in Glass diventano scuri.
                .background(AppTheme.OnGradientSurface),
            contentAlignment = Alignment.Center
        ) {
            AilaAssistantMark(
                size = 22.dp,
                brush = SolidColor(Color.White)
            )
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hasQuery) "Chiedi ad AILA Assistant: «$query»" else "Chiedi ad AILA Assistant",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = if (hasQuery) {
                    "Risposta di AILA Assistant, cercando in tutta l'app"
                } else {
                    "Circolari, calendario, bacheca e altro"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.OnGradientSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        AppIcons.ChevronRight(modifier = Modifier.size(17.dp), color = Color.White)
    }
}
