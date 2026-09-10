package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaListRow
import circolareplus.design.AilaSectionTitle
import circolareplus.design.ailaFieldColors
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.ailaAppear
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
    onOpenCircular: (Circular) -> Unit = {},
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

            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.trim().length >= 2) onSubmitQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Circolari, eventi, proposte…", fontSize = 14.sp) },
                leadingIcon = {
                    AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text(
                            text = "✕",
                            fontSize = 15.sp,
                            color = AppTheme.TextFaint,
                            modifier = Modifier
                                .padding(end = AppTheme.Space12)
                                .clickable { query = "" }
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                colors = ailaFieldColors()
            )

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
            ) {
                SearchFilter.entries.forEach { f ->
                    AnimatedFilterChip(
                        label = f.label,
                        isSelected = filter == f,
                        onClick = { filter = f }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space16))
        }

        when {
            // Meno di due lettere: si mostrano le ricerche recenti e dove si può guardare.
            trimmed.length < 2 -> SearchIdleContent(
                recentSearches = recentSearches,
                onPickRecent = { query = it },
                onOpenCalendar = onOpenCalendar,
                onOpenBoard = onOpenBoard
            )

            hits.isEmpty() -> AilaEmptyState(
                title = "Nessun risultato",
                message = "Niente che corrisponda a \"$trimmed\" tra circolari, calendario e bacheca.",
                icon = { AppIcons.Search(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
                contentPadding = PaddingValues(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    bottom = AppTheme.Space24
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = if (hits.size == 1) "1 risultato" else "${hits.size} risultati",
                        fontSize = 12.sp,
                        color = AppTheme.TextFaint
                    )
                }
                itemsIndexed(hits) { index, hit ->
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
}

/** Contenuto quando non si sta ancora cercando nulla: ricerche recenti e scorciatoie. */
@Composable
private fun SearchIdleContent(
    recentSearches: List<String>,
    onPickRecent: (String) -> Unit,
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
                            AppIcons.Search(modifier = Modifier.size(19.dp), color = AppTheme.TintSlateInk)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppTheme.Space24))
        }

        AilaSectionTitle(text = "Dove posso cercare")
        Spacer(modifier = Modifier.height(AppTheme.Space12))
        AilaCard {
            AilaListRow(
                title = "Circolari",
                subtitle = "Per numero o titolo",
                tint = AppTheme.TintBlue,
                icon = { AppIcons.Document(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk) }
            )
            AilaListRow(
                title = "Calendario",
                subtitle = "Verifiche, pagamenti, uscite",
                tint = AppTheme.TintAmber,
                onClick = onOpenCalendar,
                icon = { AppIcons.Calendar(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk) }
            )
            AilaListRow(
                title = "Bacheca",
                subtitle = "Proposte della classe",
                tint = AppTheme.TintViolet,
                onClick = onOpenBoard,
                icon = { AppIcons.ChatBubble(modifier = Modifier.size(20.dp), color = AppTheme.TintVioletInk) }
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
