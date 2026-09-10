package circolareplus.ai

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

/**
 * Estrazione testo via PDFKit nativo di Apple (framework di sistema, nessuna libreria terza
 * necessaria su iOS/iPadOS a differenza di Android).
 */
@OptIn(ExperimentalForeignApi::class)
actual class PdfTextExtractor actual constructor() {
    actual suspend fun extractText(pdfBytes: ByteArray): String {
        return try {
            val nsData = pdfBytes.toNSData()
            val document = PDFDocument(data = nsData) ?: return ""
            val pageCount = document.pageCount.toInt()
            buildString {
                for (i in 0 until pageCount) {
                    val page = document.pageAtIndex(i.convert()) ?: continue
                    append(page.string ?: "")
                    append("\n")
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}
