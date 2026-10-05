package circolareplus.ui.screens

import circolareplus.algorithms.DeskAssignment

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
