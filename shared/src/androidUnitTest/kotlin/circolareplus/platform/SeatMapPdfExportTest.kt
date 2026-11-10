package circolareplus.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import java.io.File
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatMapPdfExportTest {
    @Test fun printedPageKeepsDateClearOfTitleDescenders() {
        val students = (1..12).associate { id ->
            "$id" to User("$id", "Studente $id", "Esempio", "student$id")
        }
        val desks = (0 until 6).map { index ->
            DeskAssignment(index / 3, index % 3, "${index * 2 + 1}", "${index * 2 + 2}")
        }
        val bitmap = Bitmap.createBitmap(1684, 1190, Bitmap.Config.ARGB_8888)
        var titleBottom: Float? = null
        var subtitleTop: Float? = null
        val canvas = object : Canvas(bitmap) {
            override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                val bounds = Rect()
                paint.getTextBounds(text, 0, text.length, bounds)
                if (text == "Mappa Posti") titleBottom = y + bounds.bottom
                if (text.startsWith("Generata il")) subtitleTop = y + bounds.top
                super.drawText(text, x, y, paint)
            }
        }
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(2f, 2f)
        // Exactly the drawing commands used on PdfDocument.Page.canvas in production.
        drawSeatMapPage(canvas, desks, students)
        val directory = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
        File(directory, "seat-map-print-preview.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        assertTrue((subtitleTop ?: error("Missing date")) - (titleBottom ?: error("Missing title")) >= 6f,
            "The printed date must have clear space below the title")
    }
}
