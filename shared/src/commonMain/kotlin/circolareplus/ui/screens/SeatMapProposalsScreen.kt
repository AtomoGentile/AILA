package circolareplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.SeatMapProposal
import circolareplus.design.*
import circolareplus.domain.model.User

/** Map-first comparison; controls follow the map instead of leaving an empty action dock. */
@Composable
fun SeatMapProposalsScreen(
    proposals: List<SeatMapProposal>,
    studentsMap: Map<String, User>,
    onSelect: (SeatMapProposal) -> Unit,
    retainedProposalIds: Set<String> = emptySet(),
    onToggleRetain: (SeatMapProposal) -> Unit = {},
    onRegenerate: () -> Unit = {},
    seatsPerDesk: Int = SeatMapOptimizer.SEATS_PER_DESK_PAIR,
    isBusy: Boolean = false,
    generationCompleted: Int = 0,
    onUseCurrentResult: () -> Unit = {},
    generationSummary: String? = null,
    canRewind: Boolean = false,
    onRewind: () -> Unit = {}
) {
    val rankedProposals = remember(proposals) {
        proposals.sortedWith(circolareplus.algorithms.SeatMapRanking.bestFirst)
    }
    var previewId by remember { mutableStateOf<String?>(null) }
    var showRewindConfirm by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(rankedProposals) { previewId = rankedProposals.firstOrNull()?.id }
    val selected = rankedProposals.firstOrNull { it.id == previewId } ?: rankedProposals.firstOrNull()
    val cells = remember(selected?.assignments) { deskGridCells(selected?.assignments.orEmpty()) }
    val bestId = rankedProposals.firstOrNull()?.id
    val retainedCount = rankedProposals.count { it.id in retainedProposalIds }

    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (rankedProposals.size == 1) "1 proposta" else "${rankedProposals.size} proposte",
                style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                circolareplus.design.AilaTextButton(onClick = onRegenerate, enabled = !isBusy) {
                    AppIcons.Refresh(modifier = Modifier.size(18.dp), color = AppTheme.PrimaryBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Migliora", color = AppTheme.PrimaryBlue)
                }
                if (canRewind) Box {
                    circolareplus.design.AilaIconControl(onClick = { showMenu = true }, enabled = !isBusy,
                        modifier = Modifier.semantics { contentDescription = "Altre azioni" }) {
                        Text("•••", color = AppTheme.TextMuted)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Scarta le proposte non conservate") },
                            onClick = { showMenu = false; showRewindConfirm = true })
                    }
                }
            }
        }
        if (isBusy) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column {
                SeatMapGenerationProgress(generationCompleted)
                circolareplus.design.AilaTextButton(onClick = onUseCurrentResult) { Text("Usa questo risultato") }
            }
        }
        // A single proposal needs no navigation tile or empty horizontal carousel.
        if (rankedProposals.size > 1) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rankedProposals.forEachIndexed { index, proposal ->
                val active = selected?.id == proposal.id
                AilaCard(
                    modifier = Modifier.widthIn(min = 90.dp).semantics {
                        this.selected = active
                        contentDescription = "Proposta ${index + 1}"
                    },
                    containerColor = if (active) AppTheme.TintBlue else AppTheme.CardSurface,
                    onClick = { previewId = proposal.id }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1} · " + proposal.satisfaction?.let { percentLabel(it.percentage) }.orEmpty(),
                            style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
                        if (proposal.id in retainedProposalIds) AppIcons.Star(
                            modifier = Modifier.size(14.dp).semantics { contentDescription = "Conservata" },
                            color = AppTheme.PrimaryBlue)
                    }
                }
            }
        }
        if (selected != null) {
            val retained = selected.id in retainedProposalIds
            val studentCount = selected.assignments.flatMap {
                listOfNotNull(it.studentAId, it.studentBId, it.studentCId)
            }.toSet().size
            LazyVerticalGrid(
                columns = GridCells.Fixed(cells.first.coerceAtLeast(1)),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fullRow {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(if (rankedProposals.size == 1) "Disposizione" else "Disposizione ${rankedProposals.indexOf(selected) + 1}",
                                style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                            Text("$studentCount studenti" + when {
                                retained -> " · Conservata"
                                selected.id == bestId -> " · Consigliata"
                                else -> ""
                            }, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(selected.satisfaction?.let { percentLabel(it.percentage) } ?: "—",
                                style = MaterialTheme.typography.headlineMedium, color = AppTheme.TextDark)
                            Text("Soddisfazione", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                        }
                    }
                }
                if (!isBusy && generationSummary != null) fullRow {
                    Text(generationSummary, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                fullRow {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(Modifier.weight(1f), color = AppTheme.Hairline)
                        Text("Lavagna e cattedra", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                        HorizontalDivider(Modifier.weight(1f), color = AppTheme.Hairline)
                    }
                }
                itemsIndexed(cells.second, key = { index, _ -> "${selected.id}-$index" }) { _, cell ->
                    val itemModifier = if (AppTheme.reduceMotion) Modifier else Modifier.animateItem()
                    if (cell == null) Spacer(Modifier)
                    else if (cell.value.isVacantDesk) {
                        Box(itemModifier.fillMaxWidth().heightIn(min = 62.dp), contentAlignment = Alignment.Center) {
                            Text("F${cell.value.row + 1}C${cell.value.column + 1}\nSpazio libero",
                                style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                        }
                    } else SeatMapDeskCard(cell.value, studentsMap, showThirdSeat = cell.value.hasThirdSeat,
                        modifier = itemModifier, showSeatNumbers = false)
                }
                fullRow {
                    // Actions stay adjacent to the map, including on tall phone screens.
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        circolareplus.design.AilaTextButton(onClick = { onToggleRetain(selected) }, enabled = !isBusy) {
                            AppIcons.Star(modifier = Modifier.size(19.dp), color = AppTheme.PrimaryBlue, filled = retained)
                            Spacer(Modifier.width(6.dp))
                            Text(if (retained) "Conservata" else "Conserva", color = AppTheme.PrimaryBlue)
                        }
                        Box(Modifier.weight(1f)) {
                            AilaPrimaryButton(text = "Modifica", onClick = { onSelect(selected) },
                                fillMaxWidth = true, compact = true, enabled = !isBusy)
                        }
                    }
                }
                fullRow {
                    Column {
                        circolareplus.design.AilaTextButton(onClick = { showAnalysis = !showAnalysis }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAnalysis) "Chiudi analisi" else "Analisi della proposta", color = AppTheme.TextMuted)
                            Spacer(Modifier.width(6.dp))
                            AppIcons.ChevronRight(modifier = Modifier.size(16.dp).rotate(if (showAnalysis) 270f else 90f),
                                color = AppTheme.TextMuted)
                        }
                        AnimatedVisibility(showAnalysis) {
                            AilaCard {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(satisfactionLabel(selected.satisfaction, selected.assignments),
                                        style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                                    Text("Punteggio complessivo · ${selected.totalScore.toInt()}",
                                        style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
                                    Row(Modifier.fillMaxWidth()) {
                                        StatChip("Sociale", selected.socialScore.toInt(), Modifier.weight(1f))
                                        StatChip("Didattica", selected.didacticScore.toInt(), Modifier.weight(1f))
                                        StatChip("Disciplina", selected.disciplinePenalty.toInt(), Modifier.weight(1f))
                                    }
                                    Text("La soddisfazione considera i voti utili. A pari punteggio: prima meno studenti a disagio, poi soddisfazione più alta.",
                                        style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                                    if (retainedCount > 0) Text(
                                        if (retainedCount == 1) "1 proposta conservata fino alla pubblicazione."
                                        else "$retainedCount proposte conservate fino alla pubblicazione.",
                                        style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Nessuna proposta disponibile", color = AppTheme.TextMuted)
        }
    }
    if (showRewindConfirm) AilaConfirmDialog(
        title = "Scartare le proposte?",
        message = "Le mappe conservate resteranno disponibili fino alla pubblicazione.",
        onDismiss = { showRewindConfirm = false },
        onConfirm = { showRewindConfirm = false; onRewind() },
        confirmLabel = "Scarta"
    )
}

private fun percentLabel(value: Double): String =
    "${kotlin.math.round(value * 10) / 10}%".replace('.', ',')

/** Testo della soddisfazione: media dei singoli studenti in base ai compagni di banco che hanno
 * davvero, con quanti siedono con qualcuno che hanno votato in negativo; oppure l'avviso che nessuno
 * ha votato e quindi non c'è nulla da misurare. */
internal fun satisfactionLabel(satisfaction: SeatMapOptimizer.VoteSatisfaction?, assignments: List<DeskAssignment>): String {
    val total = assignments.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }.toSet().size
    val occupants = "$total studenti in mappa"
    if (satisfaction == null) return "$occupants\nSoddisfazione non calcolabile: nessun voto utile"
    val base = "$occupants\nSoddisfazione media: ${satisfaction.percentage.toInt()}% (${satisfaction.evaluatedStudents} valutati)"
    return if (satisfaction.uncomfortableStudents > 0) {
        "$base • ${satisfaction.uncomfortableStudents} con un compagno sgradito"
    } else base
}

@Composable
internal fun StatChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (value > 0) "+$value" else value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = AppTheme.TextDark
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
    }
}

private fun LazyGridScope.fullRow(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
