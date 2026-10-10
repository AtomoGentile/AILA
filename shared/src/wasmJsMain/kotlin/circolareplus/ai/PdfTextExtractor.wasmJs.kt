@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package circolareplus.ai

import circolareplus.pdf.PdfJs
import kotlinx.coroutines.CancellationException

/**
 * Estrazione testo nel browser con pdf.js (`getTextContent()`, vedi [PdfJs]).
 *
 * Il testo equivale a quello delle altre piattaforme: una riga per riga del documento, in ordine
 * di posizione sulla pagina (come PdfBox con `sortByPosition` su Android e PDFKit su iOS), e un
 * a-capo dopo ogni pagina. Cosi' la classificazione AI riceve lo stesso input su tutti i
 * dispositivi. Come sulle altre piattaforme, un PDF scansionato senza testo, protetto da password
 * o corrotto restituisce stringa vuota invece di lanciare: il chiamante ricade sulla
 * classificazione euristica basata sul solo titolo.
 *
 * Il parsing gira nel worker di pdf.js, quindi l'interfaccia resta fluida anche su PDF lunghi;
 * l'estrazione resta interamente sul dispositivo (nessun byte lascia il browser).
 */
actual class PdfTextExtractor actual constructor() {
    actual suspend fun extractText(pdfBytes: ByteArray): String {
        val document = PdfJs.open(pdfBytes) ?: return ""
        return try {
            buildString {
                for (number in 1..PdfJs.pageCount(document)) {
                    val page = PdfJs.page(document, number) ?: continue
                    append(PdfJs.pageText(page))
                    append("\n")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ""
        } finally {
            PdfJs.close(document)
        }
    }
}
