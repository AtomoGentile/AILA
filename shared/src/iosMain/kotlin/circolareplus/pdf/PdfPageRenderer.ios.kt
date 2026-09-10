package circolareplus.pdf

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * iOS: non ancora implementato.
 *
 * La strada è CoreGraphics (`CGPDFDocumentCreateWithProvider` + disegno in un contesto bitmap) e
 * poi la conversione del buffer in `ImageBitmap` di Skia. È fattibile ma non è una riga di codice,
 * e qui non c'è modo di provarlo: preferisco lasciarlo dichiarato come mancante piuttosto che
 * scrivere codice iOS mai eseguito e farlo passare per funzionante.
 *
 * Restituendo un Flow vuoto la schermata del dettaglio circolare si comporta come prima su iOS:
 * mostra il tasto per aprire il PDF nel visualizzatore di sistema.
 */
actual fun renderPdfPages(
    bytes: ByteArray,
    maxPages: Int,
    targetWidthPx: Int
): Flow<ImageBitmap> = emptyFlow()
