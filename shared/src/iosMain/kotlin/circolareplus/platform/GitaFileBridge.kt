package circolareplus.platform

/**
 * Condivisione e scelta dei PDF su iOS, implementate in Swift (GitaFileBridge.swift): stesso
 * pattern di [SeatMapPdfShareBridge]. Il file passa per percorso su disco, non come array di byte,
 * così il bridge non deve convertire dati binari.
 */
interface GitaFileBridge {
    /** Apre lo share sheet per un file già su disco. Null se è partito, altrimenti il motivo. */
    fun shareFile(path: String): String?

    /**
     * Apre il selettore dei PDF. [onResult] riceve il percorso di una copia temporanea del file
     * scelto, oppure null se l'utente annulla o il selettore non si apre.
     */
    fun pickPdf(onResult: (String?) -> Unit)
}

/** Un solo bridge per l'app, iniettato da Swift all'avvio. */
object GitaFileBridgeHolder {
    var bridge: GitaFileBridge? = null
}
