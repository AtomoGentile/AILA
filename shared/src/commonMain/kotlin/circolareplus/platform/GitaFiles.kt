package circolareplus.platform

import androidx.compose.runtime.Composable

/**
 * Apre la condivisione di sistema con un PDF in memoria (scaricato dal server con login): su
 * Android il FileProvider, su iOS lo share sheet via bridge Swift (vedi GitaFileBridge.swift).
 * Lancia in caso di errore: chi chiama mostra il messaggio.
 */
expect suspend fun sharePdfFile(bytes: ByteArray, fileName: String)

/** Dimensione massima di un PDF della Gita: la stessa che accetta il server. */
const val GITA_MAX_PDF_BYTES: Long = 100L * 1024 * 1024

/** Messaggio mostrato quando il file scelto supera [GITA_MAX_PDF_BYTES]. */
const val GITA_PDF_TOO_BIG_MESSAGE = "Il file è troppo grande (massimo 100 MB)."

/** Messaggio mostrato quando il file scelto non si riesce a leggere. */
const val GITA_PDF_UNREADABLE_MESSAGE = "Non riesco a leggere il file. Riprova o scegline un altro."

/**
 * Restituisce la funzione che apre il selettore dei PDF del telefono. [onPicked] riceve il nome del
 * file e il suo contenuto quando la scelta va a buon fine; [onError] il messaggio da mostrare se il
 * file è troppo grande o non si legge (mai un'eccezione che chiuda l'app).
 */
@Composable
expect fun rememberPdfFilePicker(
    onError: (message: String) -> Unit = {},
    onPicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit
