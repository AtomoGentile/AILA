package circolareplus.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Rendering PDF su Android con `PdfRenderer` di sistema: niente librerie aggiuntive nell'APK.
 *
 * `PdfRenderer` vuole un `ParcelFileDescriptor` su un file vero, non un array di byte, quindi il
 * PDF scaricato viene scritto in un file temporaneo nella cache dell'app e cancellato subito dopo.
 * Tutto gira sul dispatcher IO: disegnare una pagina è lavoro pesante e bloccherebbe l'interfaccia.
 *
 * Emette una pagina alla volta (vedi il commento su [circolareplus.pdf.renderPdfPages]) invece di
 * costruire l'intera lista prima di restituirla: la prima pagina arriva a chi raccoglie il Flow
 * subito dopo essere stata decodificata, senza aspettare le altre.
 */
actual fun renderPdfPages(
    bytes: ByteArray,
    maxPages: Int,
    targetWidthPx: Int
): Flow<ImageBitmap> = flow {
    var tempFile: File? = null
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        val file = File.createTempFile("aila_circolare_", ".pdf")
        tempFile = file
        file.writeBytes(bytes)

        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(descriptor)

        for (index in 0 until minOf(renderer.pageCount, maxPages)) {
            renderer.openPage(index).use { page ->
                // Proporzioni della pagina rispettate: si fissa la larghezza e si ricava l'altezza.
                val width = targetWidthPx.coerceAtLeast(1)
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fondo bianco esplicito: PdfRenderer disegna solo il contenuto e senza questo le
                // zone vuote della pagina resterebbero trasparenti (nere su sfondo scuro).
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                emit(bitmap.asImageBitmap())
            }
        }
    } catch (e: Exception) {
        // PDF protetto da password, file corrotto, memoria insufficiente: la schermata ricade
        // sull'apertura esterna invece di mostrare un errore bloccante. Se qualche pagina era
        // gia' stata emessa, il collector le tiene comunque: meglio un'anteprima parziale che
        // niente.
    } finally {
        try { renderer?.close() } catch (e: Exception) { }
        try { descriptor?.close() } catch (e: Exception) { }
        try { tempFile?.delete() } catch (e: Exception) { }
    }
}.flowOn(Dispatchers.IO)
