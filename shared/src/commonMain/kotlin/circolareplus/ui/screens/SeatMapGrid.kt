package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.algorithms.DeskAssignment
import circolareplus.design.AppTheme
import circolareplus.design.ailaDeskLabel

/**
 * Cella della griglia dei banchi: il banco con il suo indice nella disposizione, oppure null per
 * una cella vuota che completa una fila piu' corta delle altre.
 */
typealias DeskCell = IndexedValue<DeskAssignment>?

/**
 * Banchi disposti per file: le colonne sono quelle della fila piu' lunga e le file piu' corte si
 * completano con celle vuote, cosi' una mappa con 4, 4 e 3 banchi per fila si vede com'e'. Per le
 * mappe automatiche (3 banchi per fila) il risultato e' quello di sempre.
 */
fun deskGridCells(assignments: List<DeskAssignment>): Pair<Int, List<DeskCell>> {
    if (assignments.isEmpty()) return 1 to emptyList()
    val rows = assignments.withIndex().groupBy { it.value.row }.toList().sortedBy { it.first }.toMap()
    val columns = rows.values.maxOf { it.size }.coerceAtLeast(1)
    val cells = mutableListOf<DeskCell>()
    for ((_, desks) in rows) {
        val ordered = desks.sortedBy { it.value.column }
        cells.addAll(ordered)
        repeat(columns - ordered.size) { cells.add(null) }
    }
    return columns to cells
}

/** Il banco ha un terzo posto (trio), anche se in questo momento e' vuoto. */
val DeskAssignment.hasThirdSeat: Boolean get() = seats >= 3 || studentCId != null

/**
 * Il banco non ha nessun occupante: e' uno slot "vuoto". Dal v1.1.6 nell'editor manuale esiste
 * davvero (lo aggiunge SeatMapOptimizer.withVacantDesks finche' la griglia scelta ha piu' posti
 * che studenti) per lasciare al Rappresentante la decisione di DOVE stare lo spazio libero: si
 * trascina come un banco pieno e spostandolo lo spazio vuoto cambia posto. Nessun campo nuovo in
 * DeskAssignment: i tre occupanti a null ("non ci e' seduto nessuno") fanno gia' da banco vacante
 * e la capienza 2/3 si legge dal campo `seats`.
 */
val DeskAssignment.isVacantDesk: Boolean
    get() = studentAId == null && studentBId == null && studentCId == null

/**
 * Slot visivo di un banco senza occupanti nell'editor: box "ghost" col bordo sottile, distinto
 * da un banco pieno (card con i nomi) e da un banco con un solo occupante (card con "Vuoto" in
 * uno dei due posti). Il bordo si accende quando e' il bersaglio del trascinamento in corso.
 */
@Composable
fun VacantDeskSlot(
    desk: DeskAssignment,
    modifier: Modifier = Modifier,
    isDragged: Boolean = false,
    isHoverTarget: Boolean = false,
    showGlass: Boolean = AppTheme.isGlass
) {
    val shape = RoundedCornerShape(if (showGlass) AppTheme.SmallElementRadius + 2.dp else 16.dp)
    val hoverHighlight = isHoverTarget && !isDragged
    Box(
        modifier = modifier
            .background(
                if (showGlass) Color.Transparent else AppTheme.DeskFloor.copy(alpha = 0.4f),
                shape
            )
            .border(
                if (hoverHighlight) 2.dp else 1.dp,
                if (hoverHighlight) AppTheme.PrimaryBlue else AppTheme.TextMuted.copy(alpha = 0.4f),
                shape
            )
            .padding(AppTheme.Space8),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Banco F${desk.row + 1}C${desk.column + 1}",
                style = MaterialTheme.typography.ailaDeskLabel,
                color = AppTheme.TextMuted
            )
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            Text(
                text = "Libero (trascinabile)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextMuted
            )
        }
    }
}

/**
 * Slot invisibile di un banco vuoto nelle superfici FUORI dall'editor (mappa pubblicata,
 * anteprima "occhio" delle proposte e, indirettamente, i PDF): dal v1.1.6 la disposizione
 * completa passa per withVacantDesks e contiene banchi senza occupanti, che pero' non devono
 * vedersi a valle. Qui lo spazio libero si nasconde disegnando la stessa card dei banchi
 * pieni con alpha 0 ("buco bianco"): occupa esattamente lo stesso spazio in griglia, quindi
 * colonne e file restano dove sono e le coppie NON slittano — un semplice Spacer a fissa
 * altezza non garantirebbe la misura della card della fila (che conta anche i trii).
 * clearAndSetSemantics: per lo screen reader il banco nascosto non esiste, esattamente come
 * non si vede allegate a vista.
 */
@Composable
fun VacantDeskBlankSlot(
    desk: DeskAssignment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .alpha(0f)
            .clearAndSetSemantics {}
    ) {
        SeatMapDeskCard(
            desk = desk,
            studentsMap = emptyMap(),
            showThirdSeat = desk.hasThirdSeat
        )
    }
}
