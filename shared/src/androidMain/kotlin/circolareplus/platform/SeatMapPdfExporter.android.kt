package circolareplus.platform

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

// Formato A4 in punti a 72dpi: stessa unità usata da PdfDocument/Canvas.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f

/**
 * Disegna la mappa posti (stessa griglia di [circolareplus.ui.screens.SeatMapScreen]: banchi con
 * 2 o 3 nomi, orientati rispetto a "Lavagna & Cattedra") su un'unica pagina PDF con
 * `android.graphics.pdf.PdfDocument` — già una dipendenza di piattaforma, nessuna libreria
 * aggiuntiva — e apre subito lo share sheet di sistema tramite FileProvider (vedi
 * androidApp/src/androidMain/AndroidManifest.xml e res/xml/file_paths.xml).
 */
actual suspend fun exportSeatMapPdf(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>
) {
    val context = AndroidAppContext.require()

    val file = withContext(Dispatchers.IO) {
        renderSeatMapPdf(assignments, studentsMap)
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // FLAG_ACTIVITY_NEW_TASK: il Context usato qui è l'application Context (vedi
    // AndroidAppContext), non un'Activity — senza questo flag Android lancerebbe
    // un'eccezione ("calling startActivity() from outside of an Activity context").
    val chooser = Intent.createChooser(shareIntent, "Condividi Mappa Posti").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

private fun renderSeatMapPdf(assignments: List<DeskAssignment>, studentsMap: Map<String, User>): File {
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    var y = drawHeader(canvas)

    val columns = ((assignments.maxOfOrNull { it.column } ?: 0) + 1).coerceAtLeast(1)
    val rows = ((assignments.maxOfOrNull { it.row } ?: 0) + 1).coerceAtLeast(1)
    drawDesks(canvas, assignments, studentsMap, columns, rows, top = y)
    drawFooter(canvas)

    document.finishPage(page)

    val exportsDir = File(AndroidAppContext.require().cacheDir, "exports").apply { mkdirs() }
    val file = File(exportsDir, "mappa_posti_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { document.writeTo(it) }
    document.close()
    return file
}

// Palette allineata al design system AILA (vedi AppTheme.kt: PrimaryBlue, HeroGradient*, TextDark/Muted, Hairline).
private const val COLOR_TEXT_DARK = "#0F172A"
private const val COLOR_TEXT_MUTED = "#64748B"
private const val COLOR_HAIRLINE = "#E8EDF5"
private const val COLOR_GRADIENT_TOP = "#1B2E7A"
private const val COLOR_GRADIENT_BOTTOM = "#7B4FE3"
private const val COLOR_PRIMARY_BLUE = "#3B82F6"
private const val COLOR_BADGE_TINT = "#EFF3FF"
private const val COLOR_ZEBRA_TINT = "#F8FAFF"
private const val COLOR_SHADOW = "#14000000" // nero all'8% di alpha, per un'ombra morbida

/** Titolo + data + barra "Lavagna & Cattedra". Restituisce la coordinata Y da cui continuare a disegnare. */
private fun drawHeader(canvas: Canvas): Float {
    val titlePaint = Paint().apply {
        color = Color.parseColor(COLOR_TEXT_DARK)
        textSize = 21f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val subtitlePaint = Paint().apply {
        color = Color.parseColor(COLOR_TEXT_MUTED)
        textSize = 10f
        isAntiAlias = true
    }

    var y = MARGIN + 6f
    canvas.drawText("Mappa Posti", MARGIN, y + 15f, titlePaint)
    y += 24f
    canvas.drawText("Generata il ${currentDateLabel()}", MARGIN, y, subtitlePaint)
    y += 14f

    // Sottile linea d'accento sfumata sotto al titolo, per legare la testata al resto del
    // design system senza appesantire (la stessa coppia di colori del gradiente hero dell'app).
    val rulePaint = Paint().apply {
        isAntiAlias = true
        shader = LinearGradient(
            MARGIN, 0f, PAGE_WIDTH - MARGIN, 0f,
            Color.parseColor(COLOR_GRADIENT_TOP), Color.parseColor(COLOR_GRADIENT_BOTTOM),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 2f, rulePaint)
    y += 18f

    // Barra "Lavagna & Cattedra": stesso gradiente blu-indaco dell'header dell'app (HeroGradient),
    // con un'ombra morbida sotto per staccarla dalla pagina invece del semplice riempimento piatto
    // di prima.
    val boardHeight = 26f
    val boardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + boardHeight)
    val shadowPaint = Paint().apply { color = Color.parseColor(COLOR_SHADOW); isAntiAlias = true }
    canvas.drawRoundRect(
        RectF(boardRect.left, boardRect.top + 2f, boardRect.right, boardRect.bottom + 2f),
        8f, 8f, shadowPaint
    )
    val boardPaint = Paint().apply {
        isAntiAlias = true
        shader = LinearGradient(
            boardRect.left, 0f, boardRect.right, 0f,
            Color.parseColor(COLOR_GRADIENT_TOP), Color.parseColor(COLOR_GRADIENT_BOTTOM),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRoundRect(boardRect, 8f, 8f, boardPaint)
    val boardTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 11f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
    }
    canvas.drawText("LAVAGNA & CATTEDRA", PAGE_WIDTH / 2f, boardRect.centerY() + 4f, boardTextPaint)
    y += boardHeight + 20f

    return y
}

private fun drawDesks(
    canvas: Canvas,
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    columns: Int,
    rows: Int,
    top: Float
) {
    val gutter = 8f
    val gridWidth = PAGE_WIDTH - 2 * MARGIN
    val deskWidth = (gridWidth - gutter * (columns - 1)) / columns

    // L'altezza del banco si adatta allo spazio verticale rimasto sulla pagina, invece di essere
    // fissa: con poche righe resta comoda da leggere, con una classe numerosa si stringe quanto
    // basta a restare su un'unica pagina (niente paginazione: fuori scopo per questa funzione).
    val availableHeight = PAGE_HEIGHT - top - MARGIN
    val deskHeight = ((availableHeight - gutter * (rows - 1)) / rows).coerceIn(26f, 54f)

    val byPosition = assignments.associateBy { it.row to it.column }

    val shadowPaint = Paint().apply { color = Color.parseColor(COLOR_SHADOW); isAntiAlias = true }
    val boxPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    val zebraPaint = Paint().apply { color = Color.parseColor(COLOR_ZEBRA_TINT); style = Paint.Style.FILL; isAntiAlias = true }
    val borderPaint = Paint().apply {
        color = Color.parseColor(COLOR_HAIRLINE)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }
    val badgePaint = Paint().apply { color = Color.parseColor(COLOR_BADGE_TINT); isAntiAlias = true }
    val labelPaint = Paint().apply {
        color = Color.parseColor(COLOR_PRIMARY_BLUE)
        textSize = 6.5f
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val namePaint = Paint().apply {
        color = Color.parseColor(COLOR_TEXT_DARK)
        textSize = (deskHeight / 5.2f).coerceIn(7f, 9.5f)
        isFakeBoldText = true
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    for (row in 0 until rows) {
        for (col in 0 until columns) {
            val desk = byPosition[row to col] ?: continue
            val left = MARGIN + col * (deskWidth + gutter)
            val top2 = top + row * (deskHeight + gutter)
            val rect = RectF(left, top2, left + deskWidth, top2 + deskHeight)

            // Ombra morbida sotto la card, per staccarla dalla pagina invece di un bordo secco.
            canvas.drawRoundRect(
                RectF(rect.left, rect.top + 1.5f, rect.right, rect.bottom + 1.5f),
                6f, 6f, shadowPaint
            )
            canvas.drawRoundRect(rect, 6f, 6f, if (row % 2 == 0) boxPaint else zebraPaint)
            canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

            // Etichetta di posizione come piccola pill in alto, invece del testo grigio nudo:
            // più leggibile e coerente con i badge del resto dell'app.
            val label = "F${row + 1} · C${col + 1}"
            val badgeWidth = (labelPaint.measureText(label) + 8f).coerceAtMost(rect.width() - 6f)
            val badgeRect = RectF(
                rect.centerX() - badgeWidth / 2f, rect.top + 3f,
                rect.centerX() + badgeWidth / 2f, rect.top + 11f
            )
            canvas.drawRoundRect(badgeRect, 4f, 4f, badgePaint)
            canvas.drawText(label, rect.centerX(), badgeRect.bottom - 2f, labelPaint)

            val centerX = rect.centerX()
            val names = listOfNotNull(desk.studentAId, desk.studentBId, desk.studentCId)
                .map { id -> studentsMap[id]?.let { "${it.firstName} ${it.lastName}" } ?: "Vuoto" }
            val lineHeight = namePaint.textSize + 3f
            // Blocco nomi centrato verticalmente nello spazio rimasto sotto il badge, non più
            // incollato in alto: con un solo nome in un banco alto restava scentrato e "vuoto".
            val namesHeight = lineHeight * names.size
            var nameY = ((badgeRect.bottom + rect.bottom - namesHeight) / 2f + namePaint.textSize)
                .coerceAtLeast(badgeRect.bottom + namePaint.textSize)
            for (name in names) {
                if (nameY > rect.bottom - 2f) break
                canvas.drawText(truncateName(name, 22), centerX, nameY, namePaint)
                nameY += lineHeight
            }
        }
    }
}

/** Riga di firma in fondo alla pagina, per chiudere il documento in modo un po' più curato. */
private fun drawFooter(canvas: Canvas) {
    val linePaint = Paint().apply { color = Color.parseColor(COLOR_HAIRLINE); strokeWidth = 1f }
    val y = PAGE_HEIGHT - MARGIN + 4f
    canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)

    val footerPaint = Paint().apply {
        color = Color.parseColor(COLOR_TEXT_MUTED)
        textSize = 8f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("Generato con AILA", PAGE_WIDTH - MARGIN, y + 12f, footerPaint)
}

private fun truncateName(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"

private fun currentDateLabel(): String {
    val cal = Calendar.getInstance()
    return "%02d/%02d/%04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR)
    )
}
