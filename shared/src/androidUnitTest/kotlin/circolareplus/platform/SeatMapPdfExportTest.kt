package circolareplus.platform

import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatMapPdfExportTest {
    @Test fun exportedMapIsPrintableLandscapePdfWithHeader() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        AndroidAppContext.init(context)
        PDFBoxResourceLoader.init(context)
        val students = (1..12).associate { id ->
            "$id" to User("$id", "Studente $id", "Esempio", "student$id")
        }
        val desks = (0 until 6).map { index ->
            DeskAssignment(index / 3, index % 3, "${index * 2 + 1}", "${index * 2 + 2}")
        }
        val pdf = renderSeatMapPdf(desks, students)
        PDDocument.load(pdf).use { document ->
            assertEquals(1, document.numberOfPages)
            assertEquals(842f, document.getPage(0).mediaBox.width)
            assertEquals(595f, document.getPage(0).mediaBox.height)
            val text = PDFTextStripper().getText(document)
            assertTrue(text.contains("Mappa Posti"))
            assertTrue(text.contains("Generata il"))
            assertTrue(text.contains("Studente 12"))
        }
        val directory = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
        pdf.copyTo(File(directory, "seat-map-print-preview.pdf"), overwrite = true)
    }
}
