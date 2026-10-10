package circolareplus.platform

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.DeskAssignment
import circolareplus.domain.model.User
import kotlinx.coroutines.await
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.js.Promise

// Stesso foglio A4 in punti a 72 dpi dell'esportazione Android (PdfDocument).
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 36f

// Il browser non ha un'API per scrivere PDF: la pagina si disegna con lo stesso disegno
// dell'app Android (stessa griglia, colori, ombre) su una bitmap tre volte piu' grande del foglio
// e la si incolla in un PDF di una pagina. Il testo non e' selezionabile, ma a 216 dpi si legge e
// si stampa bene, e la mappa e' pensata per essere appesa o inoltrata, non copiata.
private const val RENDER_SCALE = 3

actual suspend fun exportSeatMapPdf(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>
) {
    val bitmap = renderSeatMapPage(assignments, studentsMap)
    val jpeg = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, 92)?.bytes
        ?: return
    val pdf = wrapJpegInPdf(jpeg, bitmap.width, bitmap.height)
    val name = "mappa_posti_${currentTimeMillis()}.pdf"
    shareOrDownload(Base64.encode(pdf), name).await<JsAny?>()
}

private fun renderSeatMapPage(assignments: List<DeskAssignment>, studentsMap: Map<String, User>): ImageBitmap {
    val bitmap = ImageBitmap(PAGE_WIDTH * RENDER_SCALE, PAGE_HEIGHT * RENDER_SCALE)
    // Densita' 1: 1 sp = 1 px nello spazio del foglio, poi tutto il disegno si ingrandisce di
    // RENDER_SCALE, cosi' i valori sono quelli (in punti) del codice Android.
    val density = Density(1f)
    val measurer = TextMeasurer(createFontFamilyResolver(), density, LayoutDirection.Ltr)
    CanvasDrawScope().draw(
        density, LayoutDirection.Ltr, Canvas(bitmap),
        Size(bitmap.width.toFloat(), bitmap.height.toFloat())
    ) {
        drawRect(Color.White)
        scale(RENDER_SCALE.toFloat(), pivot = Offset.Zero) {
            val y = drawHeader(measurer)
            val columns = ((assignments.maxOfOrNull { it.column } ?: 0) + 1).coerceAtLeast(1)
            val rows = ((assignments.maxOfOrNull { it.row } ?: 0) + 1).coerceAtLeast(1)
            drawDesks(measurer, assignments, studentsMap, columns, rows, top = y)
            drawFooter(measurer)
        }
    }
    return bitmap
}

// Palette del design system AILA, la stessa dell'esportazione Android.
private val COLOR_TEXT_DARK = Color(0xFF0F172A)
private val COLOR_TEXT_MUTED = Color(0xFF64748B)
private val COLOR_HAIRLINE = Color(0xFFE8EDF5)
private val COLOR_GRADIENT_TOP = Color(0xFF1B2E7A)
private val COLOR_GRADIENT_BOTTOM = Color(0xFF7B4FE3)
private val COLOR_PRIMARY_BLUE = Color(0xFF3B82F6)
private val COLOR_BADGE_TINT = Color(0xFFEFF3FF)
private val COLOR_ZEBRA_TINT = Color(0xFFF8FAFF)
private val COLOR_SHADOW = Color(0x14000000)

/** Scrive [text] con la linea di base a [baseline] (come Canvas.drawText di Android), allineato come richiesto. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    color: Color,
    bold: Boolean = false,
    align: Int = 0, // -1 destra, 0 sinistra, 1 centro
    letterSpacingEm: Float = 0f
) {
    val layout = measurer.measure(
        text,
        TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = letterSpacingEm.em
        )
    )
    val left = when (align) {
        1 -> x - layout.size.width / 2f
        -1 -> x - layout.size.width
        else -> x
    }
    drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))
}

private fun textWidth(measurer: TextMeasurer, text: String, size: Float, bold: Boolean): Float =
    measurer.measure(
        text,
        TextStyle(fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    ).size.width.toFloat()

private fun DrawScope.drawHeader(measurer: TextMeasurer): Float {
    var y = MARGIN + 6f
    drawLabel(measurer, "Mappa Posti", MARGIN, y + 15f, 21f, COLOR_TEXT_DARK, bold = true)
    // 4 punti in piu' dell'esportazione Android: li' le discendenti del titolo ("pp") sfiorano la
    // riga della data, sul web con questo font si toccavano.
    y += 28f
    drawLabel(measurer, "Generata il ${currentDateLabel()}", MARGIN, y, 10f, COLOR_TEXT_MUTED)
    y += 10f

    val gradient = Brush.horizontalGradient(
        listOf(COLOR_GRADIENT_TOP, COLOR_GRADIENT_BOTTOM), startX = MARGIN, endX = PAGE_WIDTH - MARGIN
    )
    drawRect(gradient, topLeft = Offset(MARGIN, y), size = Size(PAGE_WIDTH - 2 * MARGIN, 2f))
    y += 18f

    val boardHeight = 26f
    val boardWidth = PAGE_WIDTH - 2 * MARGIN
    drawRoundRect(COLOR_SHADOW, Offset(MARGIN, y + 2f), Size(boardWidth, boardHeight), CornerRadius(8f))
    drawRoundRect(gradient, Offset(MARGIN, y), Size(boardWidth, boardHeight), CornerRadius(8f))
    drawLabel(
        measurer, "LAVAGNA & CATTEDRA", PAGE_WIDTH / 2f, y + boardHeight / 2f + 4f, 11f,
        Color.White, bold = true, align = 1, letterSpacingEm = 0.05f
    )
    y += boardHeight + 20f
    return y
}

private fun DrawScope.drawDesks(
    measurer: TextMeasurer,
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    columns: Int,
    rows: Int,
    top: Float
) {
    val gutter = 8f
    val gridWidth = PAGE_WIDTH - 2 * MARGIN
    val deskWidth = (gridWidth - gutter * (columns - 1)) / columns
    // L'altezza del banco si adatta allo spazio rimasto, come su Android: tutta la mappa su una pagina.
    val availableHeight = PAGE_HEIGHT - top - MARGIN
    val deskHeight = ((availableHeight - gutter * (rows - 1)) / rows).coerceIn(26f, 54f)
    val byPosition = assignments.associateBy { it.row to it.column }
    val nameSize = (deskHeight / 5.2f).coerceIn(7f, 9.5f)

    for (row in 0 until rows) {
        for (col in 0 until columns) {
            val desk = byPosition[row to col] ?: continue
            val left = MARGIN + col * (deskWidth + gutter)
            val deskTop = top + row * (deskHeight + gutter)
            val corner = CornerRadius(6f)
            val size = Size(deskWidth, deskHeight)

            drawRoundRect(COLOR_SHADOW, Offset(left, deskTop + 1.5f), size, corner)
            drawRoundRect(if (row % 2 == 0) Color.White else COLOR_ZEBRA_TINT, Offset(left, deskTop), size, corner)
            drawRoundRect(COLOR_HAIRLINE, Offset(left, deskTop), size, corner, style = Stroke(1f))

            val centerX = left + deskWidth / 2f
            val label = "F${row + 1} · C${col + 1}"
            val badgeWidth = (textWidth(measurer, label, 6.5f, true) + 8f).coerceAtMost(deskWidth - 6f)
            val badgeTop = deskTop + 3f
            val badgeBottom = deskTop + 11f
            drawRoundRect(
                COLOR_BADGE_TINT, Offset(centerX - badgeWidth / 2f, badgeTop),
                Size(badgeWidth, badgeBottom - badgeTop), CornerRadius(4f)
            )
            drawLabel(measurer, label, centerX, badgeBottom - 2f, 6.5f, COLOR_PRIMARY_BLUE, bold = true, align = 1)

            val names = listOfNotNull(desk.studentAId, desk.studentBId, desk.studentCId)
                .map { id -> studentsMap[id]?.let { "${it.firstName} ${it.lastName}" } ?: "Vuoto" }
            val lineHeight = nameSize + 3f
            val namesHeight = lineHeight * names.size
            var nameY = ((badgeBottom + deskTop + deskHeight - namesHeight) / 2f + nameSize)
                .coerceAtLeast(badgeBottom + nameSize)
            for (name in names) {
                if (nameY > deskTop + deskHeight - 2f) break
                drawLabel(measurer, truncateName(name, 22), centerX, nameY, nameSize, COLOR_TEXT_DARK, bold = true, align = 1)
                nameY += lineHeight
            }
        }
    }
}

private fun DrawScope.drawFooter(measurer: TextMeasurer) {
    val y = PAGE_HEIGHT - MARGIN + 4f
    drawLine(COLOR_HAIRLINE, Offset(MARGIN, y), Offset(PAGE_WIDTH - MARGIN, y), strokeWidth = 1f)
    drawLabel(measurer, "Generato con AILA", PAGE_WIDTH - MARGIN, y + 12f, 8f, COLOR_TEXT_MUTED, align = -1)
}

private fun truncateName(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max - 1) + "…"

private fun currentDateLabel(): String {
    val now = currentTimeMillis().toDouble()
    val day = jsDatePart(now, 0)
    val month = jsDatePart(now, 1)
    val year = jsDatePart(now, 2)
    return "${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/$year"
}

private fun jsDatePart(millis: Double, part: Int): Int =
    js("(function (d, p) { var x = new Date(d); return p === 0 ? x.getDate() : p === 1 ? x.getMonth() + 1 : x.getFullYear(); })(millis, part)")

/**
 * PDF a una pagina A4 che contiene solo l'immagine JPEG a tutta pagina. Scritto a mano (sono
 * quattro oggetti e una tabella): per questo non serve nessuna libreria.
 */
private fun wrapJpegInPdf(jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int): ByteArray {
    val out = ByteArrayBuilder()
    val offsets = mutableListOf<Int>()
    fun obj(body: String, stream: ByteArray? = null) {
        offsets.add(out.size)
        out.append("${offsets.size} 0 obj\n$body\n")
        if (stream != null) {
            out.append("stream\n")
            out.append(stream)
            out.append("\nendstream\n")
        }
        out.append("endobj\n")
    }
    out.append("%PDF-1.4\n")
    obj("<< /Type /Catalog /Pages 2 0 R >>")
    obj("<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>")
    obj(
        "<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight /ColorSpace /DeviceRGB " +
            "/BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>",
        jpeg
    )
    val content = "q $PAGE_WIDTH 0 0 $PAGE_HEIGHT 0 0 cm /Im0 Do Q".encodeToByteArray()
    obj("<< /Length ${content.size} >>", content)
    val xref = out.size
    out.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
    for (offset in offsets) out.append(offset.toString().padStart(10, '0') + " 00000 n \n")
    out.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.toByteArray()
}

private class ByteArrayBuilder {
    private var buffer = ByteArray(1 shl 16)
    var size = 0
        private set

    fun append(bytes: ByteArray) {
        if (size + bytes.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun append(text: String) = append(text.encodeToByteArray())

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

// Su telefoni e tablet si apre il foglio di condivisione con il file, come fa l'app nativa; sui
// computer si scarica. Il foglio di condivisione non si usa sui computer anche se il browser lo
// offre: su Windows apre una finestra di sistema che a molti sembra "non succede niente", e in un
// browser senza interfaccia la promessa non si risolve mai e l'esportazione resta appesa. Se il
// foglio viene negato (Safari, se e' passato troppo tempo dal tocco) si ripiega sul download.
private fun shareOrDownload(base64: String, fileName: String): Promise<JsAny?> = js(
    "(async function () { " +
        "var bin = atob(base64); var bytes = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i); " +
        "var file = new File([bytes], fileName, { type: 'application/pdf' }); " +
        "var mobile = /Android|iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent)); " +
        "try { if (mobile && navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: 'Mappa Posti' }); return null; } } " +
        "catch (e) { if (e && e.name === 'AbortError') return null; } " +
        "var url = URL.createObjectURL(file); var a = document.createElement('a'); a.href = url; a.download = fileName; " +
        "document.body.appendChild(a); a.click(); a.remove(); setTimeout(function () { URL.revokeObjectURL(url); }, 60000); return null; })()"
)
