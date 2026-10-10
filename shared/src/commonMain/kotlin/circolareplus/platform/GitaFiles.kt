package circolareplus.platform

import androidx.compose.runtime.Composable

/**
 * Apre la condivisione di sistema con un PDF in memoria (scaricato dal server con login): su
 * Android il FileProvider, su iOS lo share sheet via bridge Swift (vedi GitaFileBridge.swift).
 * Lancia in caso di errore: chi chiama mostra il messaggio.
 */
expect suspend fun sharePdfFile(bytes: ByteArray, fileName: String)

/**
 * Restituisce la funzione che apre il selettore dei PDF del telefono. [onPicked] riceve il nome del
 * file e il suo contenuto quando la scelta va a buon fine.
 */
@Composable
expect fun rememberPdfFilePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit
