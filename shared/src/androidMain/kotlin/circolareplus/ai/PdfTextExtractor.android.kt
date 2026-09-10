package circolareplus.ai

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Inizializzazione una tantum richiesta da PdfBox-Android: carica le risorse (font/glifi) dagli
 * assets del modulo. Va chiamata UNA VOLTA all'avvio dell'app, prima di qualsiasi estrazione
 * (tipicamente in MainActivity.onCreate, PRIMA di setContent).
 */
object PdfBoxInit {
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }
}

actual class PdfTextExtractor actual constructor() {
    actual suspend fun extractText(pdfBytes: ByteArray): String = withContext(Dispatchers.Default) {
        try {
            PDDocument.load(pdfBytes).use { document ->
                PDFTextStripper().getText(document)
            }
        } catch (e: Exception) {
            // PDF scansionato senza testo, non ancora inizializzato PdfBoxInit, file corrotto, ecc.
            // Il chiamante ricade sulla classificazione euristica basata sul solo titolo.
            ""
        }
    }
}
