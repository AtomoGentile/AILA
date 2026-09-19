package circolareplus.platform

import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Appiattisce gli assignment nella forma semplice attesa dal bridge Swift ([SeatMapPdfDesk]:
 * niente UIKit lato Kotlin) e delega a SeatMapPdfShareBridge.swift, sopra UIGraphicsPDFRenderer +
 * UIActivityViewController — stesso pattern di [circolareplus.push.PushTokenBridge].
 *
 * La data di generazione viene lasciata a Swift ("" qui): kotlinx.datetime non è garantita
 * disponibile su questo target, e Date() lato Swift è semplicissimo da formattare.
 *
 * presentSeatMapPdf deve girare sul thread principale (UIActivityViewController lo richiede),
 * da qui il withContext(Dispatchers.Main).
 */
actual suspend fun exportSeatMapPdf(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>
) {
    val bridge = SeatMapPdfShareBridgeHolder.bridge
        ?: error("Esportazione PDF non disponibile su questo dispositivo")

    val desks = assignments
        .sortedWith(compareBy({ it.row }, { it.column }))
        .map { assignment ->
            val names = listOfNotNull(assignment.studentAId, assignment.studentBId, assignment.studentCId)
                .map { id -> studentsMap[id]?.let { "${it.firstName} ${it.lastName}" } ?: "Vuoto" }
            SeatMapPdfDesk(
                label = "F${assignment.row + 1} · C${assignment.column + 1}",
                names = names
            )
        }

    withContext(Dispatchers.Main) {
        bridge.presentSeatMapPdf(className = "", generatedOnLabel = "", desks = desks)
    }
}
