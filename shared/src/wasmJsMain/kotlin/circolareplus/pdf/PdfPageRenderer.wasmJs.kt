@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package circolareplus.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.js.JsAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.skia.Image

/**
 * Rendering PDF nel browser con pdf.js (vedi [PdfJs]): ogni pagina viene disegnata su un canvas
 * alla larghezza richiesta (altezza dalle proporzioni della pagina, come su Android e iOS),
 * codificata in PNG e riletta da Skia come `ImageBitmap`.
 *
 * Emette una pagina alla volta, come le altre piattaforme (vedi [renderPdfPages] in commonMain):
 * la prima appare senza aspettare le altre. Il lavoro pesante (parsing, font, immagini) gira nel
 * worker di pdf.js; sul thread principale restano il disegno sul canvas e la codifica, che il
 * browser fa fuori dal thread. Non c'e' `flowOn(Dispatchers.Default)` perche' in wasm non esiste
 * un secondo thread: si cede il controllo tra una pagina e l'altra, a ogni `await`.
 *
 * Cancellazione: se chi raccoglie il Flow si ferma (l'utente esce dalla circolare), il disegno in
 * corso viene annullato e il documento chiuso, cosi' il worker non resta acceso a elaborare pagine
 * che nessuno vedra'.
 */
actual fun renderPdfPages(
    bytes: ByteArray,
    maxPages: Int,
    targetWidthPx: Int
): Flow<ImageBitmap> = flow {
    // Come su Android e iOS: PDF illeggibile o protetto da password → nessuna pagina, la schermata
    // ricade sull'apertura esterna invece di mostrare un errore bloccante.
    val document = PdfJs.open(bytes) ?: return@flow
    try {
        val pageCount = minOf(PdfJs.pageCount(document), maxPages)
        for (number in 1..pageCount) {
            // Una pagina che non si renderizza non blocca le successive.
            val bitmap = renderPage(document, number, targetWidthPx) ?: continue
            emit(bitmap)
        }
    } finally {
        PdfJs.close(document)
    }
}

private suspend fun renderPage(document: JsAny, number: Int, targetWidthPx: Int): ImageBitmap? = try {
    val page = PdfJs.page(document, number)
    val png = page?.let { PdfJs.renderToPng(it, targetWidthPx) }
    png?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    null
}
