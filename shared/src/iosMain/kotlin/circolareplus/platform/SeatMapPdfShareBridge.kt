package circolareplus.platform

/**
 * Un banco già "appiattito" in dati semplici (niente UIKit lato Kotlin): l'etichetta ("F1C1")
 * e i nomi degli occupanti (0 a 3 stringhe, "Vuoto" per un posto libero), pronti da disegnare.
 */
data class SeatMapPdfDesk(val label: String, val names: List<String>)

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
     */
    fun presentSeatMapPdf(className: String, generatedOnLabel: String, desks: List<SeatMapPdfDesk>)
}

/** Un solo bridge per tutta l'app, iniettato da Swift all'avvio. */
object SeatMapPdfShareBridgeHolder {
    var bridge: SeatMapPdfShareBridge? = null
}
