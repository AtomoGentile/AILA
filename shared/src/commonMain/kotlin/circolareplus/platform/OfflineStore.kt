package circolareplus.platform

/**
 * Copia su disco delle ultime risposte del server (JSON) e dei PDF delle circolari, per poter
 * consultare l'app anche senza rete (a scuola, in aereo...). Sta nella cartella dei file
 * dell'app e non nella cache di sistema, che il sistema puo' svuotare quando vuole.
 *
 * I nomi dei file li decide [circolareplus.data.remote.ApiClient]; qui solo lettura/scrittura.
 */
expect object OfflineStore {
    fun readBytes(name: String): ByteArray?
    fun writeBytes(name: String, bytes: ByteArray)
    fun exists(name: String): Boolean
    /** Spazio occupato da tutti i dati offline, in byte. */
    fun totalBytes(): Long
    /** Cancella tutto (logout, o "Svuota dati offline" dalle Impostazioni). */
    fun clear()
}

fun OfflineStore.readText(name: String): String? = readBytes(name)?.decodeToString()

fun OfflineStore.writeText(name: String, text: String) = writeBytes(name, text.encodeToByteArray())
