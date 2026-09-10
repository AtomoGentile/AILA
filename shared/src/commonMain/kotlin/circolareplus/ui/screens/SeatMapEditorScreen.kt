package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppTheme
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User

/** Riferimento a un posto (metà banco) selezionato nell'editor: indice del banco + slot A/B. */
private data class EditableSeatRef(val deskIndex: Int, val isSeatA: Boolean)

/**
 * Editor manuale della disposizione scelta dal Rappresentante: tap-to-swap (si seleziona un posto,
 * poi un secondo per scambiarli) con ricalcolo live del punteggio passato da fuori, pulsante di
 * ripristino alla proposta originale dell'algoritmo e pulsante di pubblicazione finale.
 */
@Composable
fun SeatMapEditorScreen(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
    totalScore: Double,
    satisfactionPercentage: Double,
    isPublishing: Boolean,
    onSwapSeats: (deskIndex1: Int, isSeatA1: Boolean, deskIndex2: Int, isSeatA2: Boolean) -> Unit,
    onRestore: () -> Unit,
    onPublish: () -> Unit
) {
    var selectedSeat by remember { mutableStateOf<EditableSeatRef?>(null) }

    // Uno scambio può cambiare la composizione dei banchi: se la selezione punta a un indice
    // ormai fuori range, la si azzera invece di lasciarla puntare a un posto inesistente.
    LaunchedEffect(assignments.size) {
        val seat = selectedSeat
        if (seat != null && seat.deskIndex >= assignments.size) {
            selectedSeat = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        AilaCard(
            containerColor = AppTheme.SurfaceWhite,
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16)
        ) {
            Column(modifier = Modifier.padding(AppTheme.Space12)) {
                Text(
                    text = "Punteggio: ${totalScore.toInt()} pt",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
                Text(
                    text = "Soddisfazione stimata: ${satisfactionPercentage.toInt()}%",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
                Text(
                    text = if (selectedSeat == null)
                        "Tocca un nome per selezionarlo, poi un secondo per scambiarli."
                    else
                        "Ora tocca un secondo nome per completare lo scambio.",
                    fontSize = 11.sp,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.padding(top = AppTheme.Space8)
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                AilaSecondaryButton(
                    text = "Ripristina Proposta Algoritmo",
                    onClick = {
                        selectedSeat = null
                        onRestore()
                    },
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                AilaPrimaryButton(
                    text = "Pubblica questa disposizione",
                    onClick = onPublish,
                    fillMaxWidth = true,
                    enabled = !isPublishing
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                bottom = AppTheme.Space32
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = assignments.size) { deskIndex ->
                val desk = assignments[deskIndex]
                val sA = desk.studentAId?.let { studentsMap[it] }
                val sB = desk.studentBId?.let { studentsMap[it] }
                val isForbidden = desk.studentAId != null && desk.studentBId != null &&
                    SeatMapOptimizer.isForbiddenPair(desk.studentAId, desk.studentBId, socialPreferences)

                Card(
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.SurfaceWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
                    modifier = Modifier.border(
                        width = if (isForbidden) 2.dp else 1.dp,
                        color = if (isForbidden) AppTheme.PollDarkRed else AppTheme.Hairline,
                        shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Banco F${desk.row + 1}C${desk.column + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextMuted
                        )
                        if (isForbidden) {
                            Text(
                                text = "Coppia vietata",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.PollDarkRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SeatSlotLabel(
                            name = sA?.firstName,
                            isSelected = selectedSeat == EditableSeatRef(deskIndex, true),
                            onClick = {
                                selectedSeat = handleSeatTap(
                                    tapped = EditableSeatRef(deskIndex, true),
                                    current = selectedSeat,
                                    onSwap = onSwapSeats
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = AppTheme.Hairline)
                        SeatSlotLabel(
                            name = sB?.firstName,
                            isSelected = selectedSeat == EditableSeatRef(deskIndex, false),
                            onClick = {
                                selectedSeat = handleSeatTap(
                                    tapped = EditableSeatRef(deskIndex, false),
                                    current = selectedSeat,
                                    onSwap = onSwapSeats
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gestisce il tap su un posto: se non c'era nulla di selezionato, seleziona; se il tap ricade
 * sullo stesso posto già selezionato, deseleziona; altrimenti scambia i due posti e azzera la
 * selezione.
 */
private fun handleSeatTap(
    tapped: EditableSeatRef,
    current: EditableSeatRef?,
    onSwap: (Int, Boolean, Int, Boolean) -> Unit
): EditableSeatRef? {
    return when {
        current == null -> tapped
        current == tapped -> null
        else -> {
            onSwap(current.deskIndex, current.isSeatA, tapped.deskIndex, tapped.isSeatA)
            null
        }
    }
}

@Composable
private fun SeatSlotLabel(name: String?, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = name ?: "Vuoto",
        fontSize = 12.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) AppTheme.PrimaryBlue else AppTheme.TextDark,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(1.dp, AppTheme.PrimaryBlue, RoundedCornerShape(4.dp)) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}
