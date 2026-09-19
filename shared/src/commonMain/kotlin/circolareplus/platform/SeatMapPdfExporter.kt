package circolareplus.platform

import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User

/**
 * Genera un PDF con il disegno della mappa posti — la stessa disposizione a griglia mostrata in
 * SeatMapScreen, banchi con 2 o 3 nomi orientati rispetto a "Lavagna & Cattedra" — e lo condivide
 * subito tramite il meccanismo nativo della piattaforma (share sheet), così l'utente può
 * salvarlo, stamparlo o inviarlo senza passaggi intermedi in-app.
 *
 * Implementazione nativa per piattaforma:
 * - Android: SeatMapPdfExporter.android.kt, android.graphics.pdf.PdfDocument + FileProvider.
 * - iOS: SeatMapPdfExporter.ios.kt, bridge Swift su UIGraphicsPDFRenderer +
 *   UIActivityViewController (stesso pattern di [circolareplus.push.PushTokenBridge]).
 *
 * Lancia in caso di errore — nessun degrado silenzioso come per le notifiche push: chi chiama
 * questa funzione (vedi MainAppShell) cattura l'eccezione e mostra il messaggio all'utente.
 */
expect suspend fun exportSeatMapPdf(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>
)
