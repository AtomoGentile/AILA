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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.SeatMapProposal
import circolareplus.design.*
import circolareplus.domain.model.User

/** One map at a time; proposal navigation and analysis never hide the map behind large cards. */
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
    generationSummary: String? = null,
    canRewind: Boolean = false,
    onRewind: () -> Unit = {}
) {
    var previewId by remember { mutableStateOf<String?>(null) }
    var showRewindConfirm by remember { mutableStateOf(false) }
    var showAnalysis by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(proposals) { previewId = proposals.firstOrNull()?.id }
    val selected = proposals.firstOrNull { it.id == previewId } ?: proposals.firstOrNull()
    val cells = remember(selected?.assignments) { deskGridCells(selected?.assignments.orEmpty()) }
    val bestId = proposals.maxByOrNull { it.totalScore }?.id

    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${proposals.size} proposte · ${retainedProposalIds.size} conservate",
                style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRegenerate, enabled = !isBusy) {
                    AppIcons.Refresh(modifier = Modifier.size(18.dp), color = AppTheme.PrimaryBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Migliora", color = AppTheme.PrimaryBlue)
                }
                if (canRewind) Box {
                    TextButton(onClick = { showMenu = true }, enabled = !isBusy,
                        modifier = Modifier.semantics { contentDescription = "Altre azioni" }) { Text("•••", color = AppTheme.TextMuted) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Scarta le proposte non conservate") },
                            onClick = { showMenu = false; showRewindConfirm = true })
                    }
                }
            }
        }
        if (isBusy) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SeatMapGenerationProgress(generationCompleted)
            }
        }
        if (!isBusy && generationSummary != null) Text(generationSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            proposals.forEachIndexed { index, proposal ->
                val active = selected?.id == proposal.id
                AilaCard(
                    modifier = Modifier.width(116.dp).semantics { this.selected = active },
                    containerColor = if (active) AppTheme.TintBlue else AppTheme.CardSurface,
                    onClick = { previewId = proposal.id }
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Proposta ${index + 1}", style = MaterialTheme.typography.labelMedium, color = AppTheme.TextDark)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(proposal.satisfaction?.let { "${kotlin.math.round(it.percentage * 10) / 10}%" } ?: "N/D",
                                style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                            val retained = proposal.id in retainedProposalIds
                            if (retained || proposal.id == bestId) Text(if (retained) "★" else "✓",
                                style = MaterialTheme.typography.labelLarge, color = AppTheme.PrimaryBlue,
                                modifier = Modifier.semantics { contentDescription = if (retained) "Conservata" else "Consigliata" })
                        }
                    }
                }
            }
        }
        if (selected != null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cells.first.coerceAtLeast(1)),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fullRow {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                            .background(AppTheme.HeroGradient).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("LAVAGNA E CATTEDRA", style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.OnHeroPrimary)
                    }
                }
                itemsIndexed(cells.second, key = { index, _ -> "${selected.id}-$index" }) { _, cell ->
                    if (cell == null) Spacer(Modifier)
                    else if (cell.value.isVacantDesk) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
                            Text("F${cell.value.row + 1}C${cell.value.column + 1}\nLibero",
                                style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                        }
                    } else SeatMapDeskCard(cell.value, studentsMap, showThirdSeat = cell.value.hasThirdSeat)
                }
                fullRow {
                    AilaCard {
                        TextButton(onClick = { showAnalysis = !showAnalysis }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAnalysis) "Chiudi analisi" else "Analisi della proposta",
                                color = AppTheme.PrimaryBlue)
                        }
                        AnimatedVisibility(showAnalysis) {
                            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(satisfactionLabel(selected.satisfaction, selected.assignments),
                                    style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                                Text("Punteggio complessivo · ${selected.totalScore.toInt()}",
                                    style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
                                Row(Modifier.fillMaxWidth()) {
                                    StatChip("Sociale", selected.socialScore.toInt(), Modifier.weight(1f))
                                    StatChip("Didattica", selected.didacticScore.toInt(), Modifier.weight(1f))
                                    StatChip("Disciplina", selected.disciplinePenalty.toInt(), Modifier.weight(1f))
                                }
                                Text("La soddisfazione considera i voti utili. La proposta consigliata bilancia tutti i criteri scelti.",
                                    style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                            }
                        }
                    }
                }

            }
            // Shared AILA surface follows Expressive/Glass and keeps the actions reachable.
            AilaCard(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val retained = selected.id in retainedProposalIds
                    FilterChip(selected = retained, enabled = !isBusy, onClick = { onToggleRetain(selected) },
                        label = { Text(if (retained) "Conservata" else "Conserva") })
                    Box(Modifier.weight(1f)) {
                        AilaPrimaryButton(text = "Modifica", onClick = { onSelect(selected) },
                            fillMaxWidth = true, compact = true, enabled = !isBusy)
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nessuna proposta disponibile", color = AppTheme.TextMuted)
            }
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
