package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.SeatMapProposal
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaPressable
import circolareplus.domain.model.User

/**
 * Confronto delle proposte calcolate dall'algoritmo.
 *
 * Prima erano tre righe in una finestra, ognuna con il solo "Scegli": non c'era modo di guardare
 * le mappe, e scegliere una proposta scartava le altre — quindi si sceglieva senza aver visto
 * niente. Ora ogni proposta ha l'occhio, che mostra la sua mappa sotto le schede senza
 * selezionarla, e un selettore 1/2/3 per passare da una mappa all'altra. Le proposte restano
 * tutte disponibili finché non se ne sceglie una (o si esce).
 */
@Composable
fun SeatMapProposalsScreen(
    proposals: List<SeatMapProposal>,
    studentsMap: Map<String, User>,
    onSelect: (SeatMapProposal) -> Unit,
    isBusy: Boolean = false
) {
    // Indice della proposta di cui si sta guardando la mappa; null = nessuna anteprima aperta.
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val safePreview = previewIndex?.takeIf { it in proposals.indices }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
        contentPadding = PaddingValues(
            start = AppTheme.Space16,
            end = AppTheme.Space16,
            top = AppTheme.Space16,
            bottom = AppTheme.Space32
        ),
        modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)
    ) {
        fullRow {
            Column {
                Text(
                    text = "Scegli la disposizione",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tocca l'occhio per guardare la mappa di una proposta senza sceglierla: " +
                        "le altre restano disponibili. Dopo la scelta potrai ancora modificare i posti.",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 17.sp
                )
            }
        }

        proposals.forEachIndexed { index, proposal ->
            fullRow {
                ProposalCard(
                    number = index + 1,
                    proposal = proposal,
                    isBest = index == 0,
                    isPreviewed = safePreview == index,
                    isBusy = isBusy,
                    onToggleView = { previewIndex = if (safePreview == index) null else index },
                    onSelect = { onSelect(proposal) }
                )
            }
        }

        if (safePreview != null) {
            val proposal = proposals[safePreview]
            val hasTrio = proposal.assignments.any { it.studentCId != null }

            fullRow {
                Column {
                    Text(
                        text = "Anteprima",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    // Il menu per passare da una mappa all'altra: 1, 2, 3.
                    AilaSegmentedTabs(
                        labels = proposals.indices.map { (it + 1).toString() },
                        selectedIndex = safePreview,
                        onSelect = { previewIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                        key = safePreview
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    Text(
                        text = "Proposta ${safePreview + 1} • ${proposal.satisfactionPercentage.toInt()}% • " +
                            if (hasTrio) "banchi da tre" else "banchi da due",
                        fontSize = 12.sp,
                        color = AppTheme.TextMuted
                    )
                }
            }

            fullRow {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                        .background(AppTheme.HeroGradient)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LAVAGNA & CATTEDRA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            itemsIndexed(
                items = proposal.assignments,
                key = { index, _ -> "$safePreview-$index" }
            ) { _, desk ->
                SeatMapDeskCard(desk = desk, studentsMap = studentsMap, showThirdSeat = hasTrio)
            }

            fullRow {
                AilaPrimaryButton(
                    text = "Scegli la proposta ${safePreview + 1}",
                    onClick = { onSelect(proposal) },
                    fillMaxWidth = true,
                    enabled = !isBusy
                )
            }
        }
    }
}

@Composable
private fun ProposalCard(
    number: Int,
    proposal: SeatMapProposal,
    isBest: Boolean,
    isPreviewed: Boolean,
    isBusy: Boolean,
    onToggleView: () -> Unit,
    onSelect: () -> Unit
) {
    val percent = proposal.satisfactionPercentage.toInt().coerceIn(0, 100)

    AilaCard {
        Column(modifier = Modifier.padding(AppTheme.Space12)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Proposta $number",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextDark
                        )
                        if (isBest) {
                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                            Text(
                                text = "MIGLIORE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TintGreenInk,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AppTheme.TintGreen)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Soddisfazione stimata $percent%",
                        fontSize = 12.sp,
                        color = AppTheme.TextMuted
                    )
                }

                // L'occhio: guarda la mappa senza scegliere. Pieno quando l'anteprima è aperta.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                        .background(if (isPreviewed) AppTheme.PrimaryBlue else AppTheme.TintSlate)
                        .ailaPressable(pressedScale = 0.92f) { onToggleView() },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcons.Eye(
                        modifier = Modifier.size(20.dp),
                        color = if (isPreviewed) Color.White else AppTheme.TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                AilaPrimaryButton(text = "Scegli", onClick = onSelect, compact = true, enabled = !isBusy)
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            // Barra della soddisfazione: a colpo d'occhio, senza leggere le percentuali.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppTheme.TintSlate)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(AppTheme.PrimaryBlue)
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                StatChip("Sociale", proposal.socialScore.toInt(), Modifier.weight(1f))
                StatChip("Didattica", proposal.didacticScore.toInt(), Modifier.weight(1f))
                StatChip("Disciplina", proposal.disciplinePenalty.toInt(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintSlate)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (value > 0) "+$value" else value.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark
        )
        Text(text = label, fontSize = 10.sp, color = AppTheme.TextMuted)
    }
}

private fun LazyGridScope.fullRow(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}
