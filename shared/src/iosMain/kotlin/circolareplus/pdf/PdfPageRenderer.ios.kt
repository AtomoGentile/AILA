package circolareplus.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxCropBox
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * Rendering PDF su iOS con PDFKit (stesso framework di sistema di [circolareplus.ai.PdfTextExtractor]):
 * niente librerie aggiuntive nell'app.
 *
 * Ogni pagina viene disegnata in un contesto bitmap CoreGraphics alla larghezza richiesta (altezza
 * ricavata dalle proporzioni della pagina, come su Android), codificata in PNG e riletta da Skia
 * come `ImageBitmap`: e' la strada piu' corta verso Compose senza toccare i buffer di pixel a mano.
 * Emette una pagina alla volta, come la versione Android (vedi [renderPdfPages] in commonMain):
 * la prima appare senza aspettare le altre.
 *
 * Tutto il lavoro pesante (disegno, codifica, decodifica) gira su Dispatchers.Default, mai sul
 * thread principale: UIGraphicsBeginImageContext puo' essere usato da thread secondari.
 */
actual fun renderPdfPages(
    bytes: ByteArray,
    maxPages: Int,
    targetWidthPx: Int
): Flow<ImageBitmap> = flow {
    // Come su Android: PDF illeggibile o protetto da password → nessuna pagina, la schermata
    // ricade sull'apertura esterna invece di mostrare un errore bloccante.
    val document = openDocument(bytes) ?: return@flow
    val pageCount = minOf(document.pageCount.toInt(), maxPages)

    for (index in 0 until pageCount) {
        // Una pagina che non si renderizza non blocca le successive.
        val bitmap = renderPage(document, index, targetWidthPx) ?: continue
        emit(bitmap)
    }
}.flowOn(Dispatchers.Default)

@OptIn(ExperimentalForeignApi::class)
private fun openDocument(bytes: ByteArray): PDFDocument? = try {
    if (bytes.isEmpty()) null else PDFDocument(data = bytes.toNSData())
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalForeignApi::class)
private fun renderPage(document: PDFDocument, index: Int, targetWidthPx: Int): ImageBitmap? = try {
    // autoreleasepool: UIImage/NSData creati per ogni pagina vanno rilasciati subito, non alla
    // fine del flusso, altrimenti un PDF di 200 pagine terrebbe in memoria tutti i PNG.
    val png: ByteArray? = autoreleasepool {
        val page = document.pageAtIndex(index.convert())
        if (page == null) {
            null
        } else {
            // CropBox = la zona che PDFKit mostra di norma; la rotazione della pagina e'
            // gia' applicata da boundsForBox/drawWithBox (larghezza e altezza sono quelle a video).
            val bounds = page.boundsForBox(kPDFDisplayBoxCropBox)
            val pageWidth = bounds.useContents { size.width }
            val pageHeight = bounds.useContents { size.height }
            if (pageWidth <= 0.0 || pageHeight <= 0.0) {
                null
            } else {
                // Proporzioni rispettate: si fissa la larghezza e si ricava l'altezza.
                val width = targetWidthPx.coerceAtLeast(1).toDouble()
                val height = (width * pageHeight / pageWidth).toInt().coerceAtLeast(1).toDouble()

                // Scala 1.0 e opaco: la bitmap ha esattamente width x height pixel, senza il
                // moltiplicatore dello schermo (3x sarebbe uno spreco di memoria per una pagina
                // che Compose ridimensiona comunque).
                UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 1.0)
                try {
                    val context = UIGraphicsGetCurrentContext()
                    if (context == null) {
                        null
                    } else {
                        // Fondo bianco esplicito: come su Android, le zone vuote non devono restare
                        // trasparenti (nere sul tema scuro).
                        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
                        CGContextFillRect(context, CGRectMake(0.0, 0.0, width, height))
                        // Il contesto UIKit ha l'origine in alto a sinistra, il PDF in basso a
                        // sinistra: si ribalta l'asse Y e si scala la pagina alla larghezza voluta.
                        CGContextTranslateCTM(context, 0.0, height)
                        CGContextScaleCTM(context, width / pageWidth, -height / pageHeight)
                        page.drawWithBox(kPDFDisplayBoxCropBox, toContext = context)

                        val image = UIGraphicsGetImageFromCurrentImageContext()
                        image?.let { UIImagePNGRepresentation(it)?.toByteArray() }
                    }
                } finally {
                    UIGraphicsEndImageContext()
                }
            }
        }
    }
    png?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val byteCount = length.toInt()
    if (byteCount == 0) return ByteArray(0)
    val result = ByteArray(byteCount)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return result
}
