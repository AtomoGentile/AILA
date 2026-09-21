package circolareplus.platform

/**
 * Un banco già "appiattito" in dati semplici (niente UIKit lato Kotlin): l'etichetta ("F1 · C1"),
 * la posizione nella griglia ([row] da davanti a dietro, [column] da sinistra, entrambe da 0, come
 * `DeskAssignment`) e i nomi degli occupanti (0 a 3 stringhe, "Vuoto" per un posto libero), pronti
 * da disegnare. Row/column servono a Swift per impaginare come Android: colonne = massima colonna + 1,
 * celle senza banco lasciate vuote.
 */
data class SeatMapPdfDesk(val label: String, val row: Int, val column: Int, val names: List<String>)

/**
 * Implementata in Swift (SeatMapPdfShareBridge.swift) sopra UIGraphicsPDFRenderer +
 * UIActivityViewController. Stesso pattern di [circolareplus.push.PushTokenBridge]: un'interfaccia
 * Kotlin, implementata da una classe Swift che il framework `shared` esporta come protocollo
 * Objective-C, iniettata da iOSApp.swift all'avvio.
 */
interface SeatMapPdfShareBridge {
    /**
     * Genera il PDF e apre subito lo share sheet di sistema. Va chiamata dal thread principale
     * (UIActivityViewController lo richiede); l'implementazione Swift se ne occupa da sé.
     *
     * Ritorna null se lo share sheet è stato presentato, altrimenti un messaggio d'errore leggibile
     * (scrittura del file fallita, nessun view controller su cui presentare): il chiamante Kotlin
     * lo trasforma in eccezione, cosi' l'utente vede "Impossibile generare il PDF" come su Android
     * invece di un pulsante che non fa nulla.
     */
    fun presentSeatMapPdf(className: String, generatedOnLabel: String, desks: List<SeatMapPdfDesk>): String?
}

/** Un solo bridge per tutta l'app, iniettato da Swift all'avvio. */
object SeatMapPdfShareBridgeHolder {
    var bridge: SeatMapPdfShareBridge? = null
}
