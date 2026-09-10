package circolareplus.pdf

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow

/**
 * Rendering delle pagine di un PDF dentro l'app.
 *
 * Finora il dettaglio di una circolare mostrava solo un segnaposto con il tasto "Apri il PDF", che
 * lanciava il visualizzatore di sistema: si usciva da AILA per leggere il documento. Qui le pagine
 * diventano immagini che si possono mostrare direttamente nella schermata.
 *
 * Restituisce un [Flow] invece di una `List` completa: prima la schermata aspettava che TUTTE le
 * pagine (fino a [maxPages]) fossero decodificate a piena risoluzione prima di mostrarne anche una
 * sola, il che per un PDF di 10-12 pagine significava uno spinner per parecchi secondi. Con il
 * Flow la prima pagina appare non appena pronta, e le altre arrivano una alla volta mentre
 * l'utente sta già leggendo.
 *
 * Implementazioni di piattaforma:
 * - **Android**: `android.graphics.pdf.PdfRenderer`, incluso nel sistema (API 21+, qui minSdk 26),
 *   quindi nessuna libreria in più nell'APK.
 * - **iOS**: non ancora implementato — vedi il file `.ios.kt`. Restituisce un Flow vuoto e la
 *   schermata ricade sul tasto "Apri esternamente", come prima.
 *
 * [maxPages] a 12 tagliava a metà i documenti più lunghi (es. una circolare di 44 pagine si
 * fermava all'anteprima della dodicesima, senza alcun indizio che ne mancassero altre). Il
 * limite resta per non tentare di tenere in memoria centinaia di bitmap su un documento
 * anomalo, ma è alzato a una soglia che copre le circolari reali.
 */
expect fun renderPdfPages(
    bytes: ByteArray,
    maxPages: Int = 200,
    targetWidthPx: Int = 1240
): Flow<ImageBitmap>
