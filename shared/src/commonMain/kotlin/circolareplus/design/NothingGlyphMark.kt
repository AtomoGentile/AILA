package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Piccolo motivo LED originale, ispirato ai circuiti luminosi dei telefoni Nothing. */
@Composable
fun NothingGlyphMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    active: Boolean = true,
    accent: Color = AppTheme.PrimaryBlue
) {
    Canvas(modifier.size(size).semantics { contentDescription = "Glyph AILA" }) {
        val s = this.size.minDimension
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val line = (s * .065f).coerceAtLeast(1.5.dp.toPx())
        val led = if (AppTheme.isDarkMode) Color(0xFF777777) else Color(0xFFB8B8B4)
        // Anello centrale interrotto, come un circuito inciso nel retro di un dispositivo.
        drawArc(led, 210f, 115f, false, Offset(s * .20f, s * .20f), androidx.compose.ui.geometry.Size(s * .60f, s * .60f), style = Stroke(line, cap = StrokeCap.Round))
        drawArc(led, 35f, 115f, false, Offset(s * .20f, s * .20f), androidx.compose.ui.geometry.Size(s * .60f, s * .60f), style = Stroke(line, cap = StrokeCap.Round))
        // Due piste simmetriche e terminali arrotondati.
        drawLine(led, Offset(s * .50f, s * .04f), Offset(s * .50f, s * .20f), line, StrokeCap.Round)
        drawLine(led, Offset(s * .50f, s * .80f), Offset(s * .50f, s * .96f), line, StrokeCap.Round)
        drawLine(led, Offset(s * .04f, s * .50f), Offset(s * .20f, s * .50f), line, StrokeCap.Round)
        drawLine(led, Offset(s * .80f, s * .50f), Offset(s * .96f, s * .50f), line, StrokeCap.Round)
        drawCircle(if (active) accent else led, radius = s * .075f, center = c)
        if (active) drawCircle(accent.copy(alpha = .16f), radius = s * .17f, center = c)
    }
}
