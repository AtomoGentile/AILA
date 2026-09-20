package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icone vettoriali personalizzate per AILA, fedeli al concept grafico:
 * linee minimali, outline raffinato e proporzioni esatte.
 */
object AppIcons {

    @Composable
    fun Home(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.42f)
                lineTo(w * 0.5f, h * 0.15f)
                lineTo(w * 0.85f, h * 0.42f)
                lineTo(w * 0.85f, h * 0.85f)
                lineTo(w * 0.58f, h * 0.85f)
                lineTo(w * 0.58f, h * 0.58f)
                lineTo(w * 0.42f, h * 0.58f)
                lineTo(w * 0.42f, h * 0.85f)
                lineTo(w * 0.15f, h * 0.85f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Calendar(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            // Rettangolo calendario - Usiamo Path manuale per evitare drawRoundRect/SkiaBackedPath
            val rectPath = Path().apply {
                val rx = w * 0.14f
                val ry = w * 0.14f
                val left = w * 0.12f
                val top = h * 0.22f
                val right = w * 0.88f
                val bottom = h * 0.88f
                
                moveTo(left + rx, top)
                lineTo(right - rx, top)
                quadraticBezierTo(right, top, right, top + ry)
                lineTo(right, bottom - ry)
                quadraticBezierTo(right, bottom, right - rx, bottom)
                lineTo(left + rx, bottom)
                quadraticBezierTo(left, bottom, left, bottom - ry)
                lineTo(left, top + ry)
                quadraticBezierTo(left, top, left + rx, top)
            }
            drawPath(rectPath, color = color, style = Stroke(width = stroke))

            // Barra orizzontale
            drawLine(
                color = color,
                start = Offset(w * 0.12f, h * 0.42f),
                end = Offset(w * 0.88f, h * 0.42f),
                strokeWidth = stroke
            )
            // Ganci superiori
            drawLine(
                color = color,
                start = Offset(w * 0.32f, h * 0.12f),
                end = Offset(w * 0.32f, h * 0.26f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.68f, h * 0.12f),
                end = Offset(w * 0.68f, h * 0.26f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Document(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.12f)
                lineTo(w * 0.6f, h * 0.12f)
                lineTo(w * 0.82f, h * 0.34f)
                lineTo(w * 0.82f, h * 0.88f)
                lineTo(w * 0.2f, h * 0.88f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Angolo piegato
            val fold = Path().apply {
                moveTo(w * 0.6f, h * 0.12f)
                lineTo(w * 0.6f, h * 0.34f)
                lineTo(w * 0.82f, h * 0.34f)
            }
            drawPath(fold, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Righe di testo sul foglio
            drawLine(color, Offset(w * 0.35f, h * 0.52f), Offset(w * 0.67f, h * 0.52f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.35f, h * 0.68f), Offset(w * 0.67f, h * 0.68f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun ChatBubble(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.2f)
                lineTo(w * 0.85f, h * 0.2f)
                quadraticBezierTo(w * 0.92f, h * 0.2f, w * 0.92f, h * 0.28f)
                lineTo(w * 0.92f, h * 0.62f)
                quadraticBezierTo(w * 0.92f, h * 0.7f, w * 0.85f, h * 0.7f)
                lineTo(w * 0.45f, h * 0.7f)
                lineTo(w * 0.25f, h * 0.88f)
                lineTo(w * 0.25f, h * 0.7f)
                lineTo(w * 0.15f, h * 0.7f)
                quadraticBezierTo(w * 0.08f, h * 0.7f, w * 0.08f, h * 0.62f)
                lineTo(w * 0.08f, h * 0.28f)
                quadraticBezierTo(w * 0.08f, h * 0.2f, w * 0.15f, h * 0.2f)
                close()
            }
            drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // 3 puntini conversazione - Usiamo drawPath per i cerchi per evitare drawCircle
            val dotRadius = stroke * 0.7f
            val dot1 = Path().apply {
                val cx = w * 0.35f
                val cy = h * 0.45f
                moveTo(cx + dotRadius, cy)
                quadraticBezierTo(cx + dotRadius, cy + dotRadius, cx, cy + dotRadius)
                quadraticBezierTo(cx - dotRadius, cy + dotRadius, cx - dotRadius, cy)
                quadraticBezierTo(cx - dotRadius, cy - dotRadius, cx, cy - dotRadius)
                quadraticBezierTo(cx + dotRadius, cy - dotRadius, cx + dotRadius, cy)
                close()
            }
            drawPath(dot1, color = color)
            
            val dot2 = Path().apply {
                val cx = w * 0.5f
                val cy = h * 0.45f
                moveTo(cx + dotRadius, cy)
                quadraticBezierTo(cx + dotRadius, cy + dotRadius, cx, cy + dotRadius)
                quadraticBezierTo(cx - dotRadius, cy + dotRadius, cx - dotRadius, cy)
                quadraticBezierTo(cx - dotRadius, cy - dotRadius, cx, cy - dotRadius)
                quadraticBezierTo(cx + dotRadius, cy - dotRadius, cx + dotRadius, cy)
                close()
            }
            drawPath(dot2, color = color)

            val dot3 = Path().apply {
                val cx = w * 0.65f
                val cy = h * 0.45f
                moveTo(cx + dotRadius, cy)
                quadraticBezierTo(cx + dotRadius, cy + dotRadius, cx, cy + dotRadius)
                quadraticBezierTo(cx - dotRadius, cy + dotRadius, cx - dotRadius, cy)
                quadraticBezierTo(cx - dotRadius, cy - dotRadius, cx, cy - dotRadius)
                quadraticBezierTo(cx + dotRadius, cy - dotRadius, cx + dotRadius, cy)
                close()
            }
            drawPath(dot3, color = color)
        }
    }

    @Composable
    fun Profile(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            // Testa - Usiamo drawPath per disegnare un cerchio per evitare drawCircle/SkiaBackedPath
            val headPath = Path().apply {
                val cx = w * 0.5f
                val cy = h * 0.32f
                val r = w * 0.22f
                moveTo(cx + r, cy)
                quadraticBezierTo(cx + r, cy + r, cx, cy + r)
                quadraticBezierTo(cx - r, cy + r, cx - r, cy)
                quadraticBezierTo(cx - r, cy - r, cx, cy - r)
                quadraticBezierTo(cx + r, cy - r, cx + r, cy)
                close()
            }
            drawPath(headPath, color = color, style = Stroke(width = stroke))

            // Busto
            val torso = Path().apply {
                moveTo(w * 0.16f, h * 0.86f)
                quadraticBezierTo(w * 0.2f, h * 0.62f, w * 0.5f, h * 0.62f)
                quadraticBezierTo(w * 0.8f, h * 0.62f, w * 0.84f, h * 0.86f)
            }
            drawPath(torso, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Bell(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B), hasBadge: Boolean = false) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            val bellPath = Path().apply {
                moveTo(w * 0.5f, h * 0.15f)
                quadraticBezierTo(w * 0.28f, h * 0.18f, w * 0.28f, h * 0.52f)
                lineTo(w * 0.18f, h * 0.72f)
                lineTo(w * 0.82f, h * 0.72f)
                lineTo(w * 0.72f, h * 0.52f)
                quadraticBezierTo(w * 0.72f, h * 0.18f, w * 0.5f, h * 0.15f)
                close()
            }
            drawPath(bellPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Batacchio - Usiamo quadraticBezierTo per un semicerchio manuale.
            // NON usare addArc o drawArc qui, causano crash (ClassNotFoundException SkiaBackedPath_skikoKt) su Android in KMP.
            val clapperPath = Path().apply {
                moveTo(w * 0.4f, h * 0.72f)
                quadraticBezierTo(w * 0.5f, h * 0.88f, w * 0.6f, h * 0.72f)
            }
            drawPath(clapperPath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))

            if (hasBadge) {
                val badgeRadius = w * 0.14f
                val badgePath = Path().apply {
                    val cx = w * 0.82f
                    val cy = h * 0.18f
                    moveTo(cx + badgeRadius, cy)
                    quadraticBezierTo(cx + badgeRadius, cy + badgeRadius, cx, cy + badgeRadius)
                    quadraticBezierTo(cx - badgeRadius, cy + badgeRadius, cx - badgeRadius, cy)
                    quadraticBezierTo(cx - badgeRadius, cy - badgeRadius, cx, cy - badgeRadius)
                    quadraticBezierTo(cx + badgeRadius, cy - badgeRadius, cx + badgeRadius, cy)
                    close()
                }
                drawPath(badgePath, Color(0xFFEF4444))
            }
        }
    }

    /**
     * Lente d'ingrandimento per la ricerca globale. Il cerchio è disegnato con quattro curve
     * quadratiche invece che con drawArc/addArc: quelle, come già annotato per la campanella,
     * fanno crashare Android in KMP (ClassNotFoundException SkiaBackedPath).
     */
    @Composable
    fun Search(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f

            val cx = w * 0.44f
            val cy = h * 0.44f
            val r = w * 0.26f
            val circlePath = Path().apply {
                moveTo(cx + r, cy)
                quadraticBezierTo(cx + r, cy + r, cx, cy + r)
                quadraticBezierTo(cx - r, cy + r, cx - r, cy)
                quadraticBezierTo(cx - r, cy - r, cx, cy - r)
                quadraticBezierTo(cx + r, cy - r, cx + r, cy)
                close()
            }
            drawPath(circlePath, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

            drawLine(
                color,
                Offset(cx + r * 0.72f, cy + r * 0.72f),
                Offset(w * 0.86f, h * 0.86f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun Chair(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF2563EB)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.09f

            // Schienale
            drawLine(color, Offset(w * 0.28f, h * 0.15f), Offset(w * 0.72f, h * 0.15f), strokeWidth = stroke * 1.3f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.35f, h * 0.15f), Offset(w * 0.35f, h * 0.5f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.65f, h * 0.15f), Offset(w * 0.65f, h * 0.5f), strokeWidth = stroke)

            // Sedile - Usiamo Path manuale per evitare drawRoundRect
            val seatPath = Path().apply {
                val rx = w * 0.04f
                val ry = w * 0.04f
                val left = w * 0.2f
                val top = h * 0.48f
                val right = w * 0.8f
                val bottom = h * 0.60f
                
                moveTo(left + rx, top)
                lineTo(right - rx, top)
                quadraticBezierTo(right, top, right, top + ry)
                lineTo(right, bottom - ry)
                quadraticBezierTo(right, bottom, right - rx, bottom)
                lineTo(left + rx, bottom)
                quadraticBezierTo(left, bottom, left, bottom - ry)
                lineTo(left, top + ry)
                quadraticBezierTo(left, top, left + rx, top)
                close()
            }
            drawPath(seatPath, color = color)

            // Gambe sedia
            drawLine(color, Offset(w * 0.26f, h * 0.6f), Offset(w * 0.22f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.74f, h * 0.6f), Offset(w * 0.78f, h * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

    // ======================================================================================
    // Sostituti vettoriali delle emoji.
    //
    // Le emoji sparse nell'interfaccia (👍 👎 🗑 👑 ⭐ 📝 💳 ⚡ 🤖 …) le disegna il sistema
    // operativo: cambiano forma tra Android e iOS e tra una versione e l'altra, non seguono la
    // palette e non si possono animare. Da qui in avanti l'app usa queste, che sono disegnate da
    // noi, prendono il colore che passiamo e si possono scalare e animare come qualsiasi altro
    // elemento.
    //
    // Nota tecnica valida per tutte: niente drawArc/addArc/drawRoundRect — su Android in KMP
    // fanno crashare (ClassNotFoundException SkiaBackedPath), come già annotato per la campanella.
    // I cerchi si costruiscono con quattro curve quadratiche.
    // ======================================================================================

    /** Cerchio come Path, sostituto sicuro di addArc/drawArc. */
    private fun circlePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
        moveTo(cx + r, cy)
        quadraticBezierTo(cx + r, cy + r, cx, cy + r)
        quadraticBezierTo(cx - r, cy + r, cx - r, cy)
        quadraticBezierTo(cx - r, cy - r, cx, cy - r)
        quadraticBezierTo(cx + r, cy - r, cx + r, cy)
        close()
    }

    @Composable
    fun ThumbUp(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B), filled: Boolean = false) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val hand = Path().apply {
                moveTo(w * 0.38f, h * 0.46f)
                lineTo(w * 0.56f, h * 0.12f)
                quadraticBezierTo(w * 0.72f, h * 0.14f, w * 0.66f, h * 0.34f)
                lineTo(w * 0.62f, h * 0.44f)
                lineTo(w * 0.86f, h * 0.44f)
                quadraticBezierTo(w * 0.94f, h * 0.52f, w * 0.86f, h * 0.86f)
                lineTo(w * 0.38f, h * 0.86f)
                close()
            }
            val cuff = Path().apply {
                moveTo(w * 0.12f, h * 0.46f)
                lineTo(w * 0.34f, h * 0.46f)
                lineTo(w * 0.34f, h * 0.88f)
                lineTo(w * 0.12f, h * 0.88f)
                close()
            }
            if (filled) {
                drawPath(hand, color); drawPath(cuff, color)
            } else {
                drawPath(hand, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(cuff, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }

    @Composable
    fun ThumbDown(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B), filled: Boolean = false) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val hand = Path().apply {
                moveTo(w * 0.38f, h * 0.54f)
                lineTo(w * 0.56f, h * 0.88f)
                quadraticBezierTo(w * 0.72f, h * 0.86f, w * 0.66f, h * 0.66f)
                lineTo(w * 0.62f, h * 0.56f)
                lineTo(w * 0.86f, h * 0.56f)
                quadraticBezierTo(w * 0.94f, h * 0.48f, w * 0.86f, h * 0.14f)
                lineTo(w * 0.38f, h * 0.14f)
                close()
            }
            val cuff = Path().apply {
                moveTo(w * 0.12f, h * 0.12f)
                lineTo(w * 0.34f, h * 0.12f)
                lineTo(w * 0.34f, h * 0.54f)
                lineTo(w * 0.12f, h * 0.54f)
                close()
            }
            if (filled) {
                drawPath(hand, color); drawPath(cuff, color)
            } else {
                drawPath(hand, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(cuff, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }

    @Composable
    fun Trash(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            drawLine(color, Offset(w * 0.16f, h * 0.28f), Offset(w * 0.84f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.4f, h * 0.28f), Offset(w * 0.4f, h * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.6f, h * 0.28f), Offset(w * 0.6f, h * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.4f, h * 0.16f), Offset(w * 0.6f, h * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            val body = Path().apply {
                moveTo(w * 0.24f, h * 0.28f)
                lineTo(w * 0.3f, h * 0.86f)
                lineTo(w * 0.7f, h * 0.86f)
                lineTo(w * 0.76f, h * 0.28f)
            }
            drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.44f, h * 0.72f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.56f, h * 0.72f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
    }

    /** Occhio: "guarda senza scegliere" (anteprima, mostra/nascondi). */
    @Composable
    fun Eye(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val outline = Path().apply {
                moveTo(w * 0.08f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.06f, w * 0.92f, h * 0.5f)
                quadraticBezierTo(w * 0.5f, h * 0.94f, w * 0.08f, h * 0.5f)
                close()
            }
            drawPath(outline, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }

    @Composable
    fun Crown(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            val crown = Path().apply {
                moveTo(w * 0.12f, h * 0.74f)
                lineTo(w * 0.2f, h * 0.3f)
                lineTo(w * 0.36f, h * 0.52f)
                lineTo(w * 0.5f, h * 0.22f)
                lineTo(w * 0.64f, h * 0.52f)
                lineTo(w * 0.8f, h * 0.3f)
                lineTo(w * 0.88f, h * 0.74f)
                close()
            }
            drawPath(crown, color)
            drawLine(color, Offset(w * 0.16f, h * 0.84f), Offset(w * 0.84f, h * 0.84f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Star(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B), filled: Boolean = true) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            val star = Path().apply {
                moveTo(w * 0.5f, h * 0.1f)
                lineTo(w * 0.62f, h * 0.38f)
                lineTo(w * 0.92f, h * 0.4f)
                lineTo(w * 0.69f, h * 0.6f)
                lineTo(w * 0.76f, h * 0.9f)
                lineTo(w * 0.5f, h * 0.73f)
                lineTo(w * 0.24f, h * 0.9f)
                lineTo(w * 0.31f, h * 0.6f)
                lineTo(w * 0.08f, h * 0.4f)
                lineTo(w * 0.38f, h * 0.38f)
                close()
            }
            if (filled) drawPath(star, color)
            else drawPath(star, color, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Check(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B), progress: Float = 1f) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.11f
            val p = progress.coerceIn(0f, 1f)
            // Il segno si "disegna" in due tratti: usato per l'animazione di conferma.
            val firstEnd = (p / 0.45f).coerceAtMost(1f)
            drawLine(
                color,
                Offset(w * 0.2f, h * 0.52f),
                Offset(w * 0.2f + (w * 0.22f) * firstEnd, h * 0.52f + (h * 0.22f) * firstEnd),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
            if (p > 0.45f) {
                val secondEnd = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                drawLine(
                    color,
                    Offset(w * 0.42f, h * 0.74f),
                    Offset(w * 0.42f + (w * 0.38f) * secondEnd, h * 0.74f - (h * 0.48f) * secondEnd),
                    strokeWidth = stroke, cap = StrokeCap.Round
                )
            }
        }
    }

    @Composable
    fun Warning(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.09f
            val tri = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.94f, h * 0.84f)
                lineTo(w * 0.06f, h * 0.84f)
                close()
            }
            drawPath(tri, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.6f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawPath(circlePath(w * 0.5f, h * 0.72f, w * 0.045f), color)
        }
    }

    /** Scintilla: marca tutto ciò che è generato o analizzato dall'AI (al posto di ✨ e 🤖). */
    @Composable
    fun Sparkle(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            fun star4(cx: Float, cy: Float, r: Float) = Path().apply {
                moveTo(cx, cy - r)
                quadraticBezierTo(cx + r * 0.22f, cy - r * 0.22f, cx + r, cy)
                quadraticBezierTo(cx + r * 0.22f, cy + r * 0.22f, cx, cy + r)
                quadraticBezierTo(cx - r * 0.22f, cy + r * 0.22f, cx - r, cy)
                quadraticBezierTo(cx - r * 0.22f, cy - r * 0.22f, cx, cy - r)
                close()
            }
            drawPath(star4(w * 0.42f, h * 0.44f, w * 0.34f), color)
            drawPath(star4(w * 0.78f, h * 0.76f, w * 0.17f), color)
        }
    }

    @Composable
    fun Bolt(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height
            val bolt = Path().apply {
                moveTo(w * 0.56f, h * 0.08f)
                lineTo(w * 0.24f, h * 0.54f)
                lineTo(w * 0.46f, h * 0.54f)
                lineTo(w * 0.42f, h * 0.92f)
                lineTo(w * 0.76f, h * 0.44f)
                lineTo(w * 0.53f, h * 0.44f)
                close()
            }
            drawPath(bolt, color)
        }
    }

    /** Carta di pagamento: categoria "Pagamento" del calendario (al posto di 💳). */
    @Composable
    fun Card(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val r = w * 0.08f
            val body = Path().apply {
                moveTo(w * 0.1f + r, h * 0.24f)
                lineTo(w * 0.9f - r, h * 0.24f)
                quadraticBezierTo(w * 0.9f, h * 0.24f, w * 0.9f, h * 0.24f + r)
                lineTo(w * 0.9f, h * 0.76f - r)
                quadraticBezierTo(w * 0.9f, h * 0.76f, w * 0.9f - r, h * 0.76f)
                lineTo(w * 0.1f + r, h * 0.76f)
                quadraticBezierTo(w * 0.1f, h * 0.76f, w * 0.1f, h * 0.76f - r)
                lineTo(w * 0.1f, h * 0.24f + r)
                quadraticBezierTo(w * 0.1f, h * 0.24f, w * 0.1f + r, h * 0.24f)
                close()
            }
            drawPath(body, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.1f, h * 0.4f), Offset(w * 0.9f, h * 0.4f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.22f, h * 0.62f), Offset(w * 0.4f, h * 0.62f), strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
        }
    }

    /** Matita: categoria "Verifica" e azioni di modifica (al posto di 📝). */
    @Composable
    fun Pencil(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val body = Path().apply {
                moveTo(w * 0.18f, h * 0.82f)
                lineTo(w * 0.26f, h * 0.58f)
                lineTo(w * 0.68f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.32f)
                lineTo(w * 0.42f, h * 0.74f)
                close()
            }
            drawPath(body, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.6f, h * 0.24f), Offset(w * 0.76f, h * 0.4f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
    }

    /** Pullman: categoria "Uscita didattica" (al posto di 🚌). */
    @Composable
    fun Bus(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val r = w * 0.1f
            val body = Path().apply {
                moveTo(w * 0.16f + r, h * 0.18f)
                lineTo(w * 0.84f - r, h * 0.18f)
                quadraticBezierTo(w * 0.84f, h * 0.18f, w * 0.84f, h * 0.18f + r)
                lineTo(w * 0.84f, h * 0.74f)
                lineTo(w * 0.16f, h * 0.74f)
                lineTo(w * 0.16f, h * 0.18f + r)
                quadraticBezierTo(w * 0.16f, h * 0.18f, w * 0.16f + r, h * 0.18f)
                close()
            }
            drawPath(body, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.16f, h * 0.46f), Offset(w * 0.84f, h * 0.46f), strokeWidth = stroke)
            drawPath(circlePath(w * 0.32f, h * 0.82f, w * 0.075f), color)
            drawPath(circlePath(w * 0.68f, h * 0.82f, w * 0.075f), color)
        }
    }

    /** Scudo: ruolo di garanzia (al posto di 🛡️). */
    @Composable
    fun Shield(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.1f)
                lineTo(w * 0.86f, h * 0.26f)
                lineTo(w * 0.86f, h * 0.54f)
                quadraticBezierTo(w * 0.86f, h * 0.82f, w * 0.5f, h * 0.92f)
                quadraticBezierTo(w * 0.14f, h * 0.82f, w * 0.14f, h * 0.54f)
                lineTo(w * 0.14f, h * 0.26f)
                close()
            }
            drawPath(shield, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
    }

    /** Lampadina: una proposta della bacheca (al posto di 💡). */
    @Composable
    fun Bulb(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val glass = Path().apply {
                moveTo(w * 0.34f, h * 0.62f)
                quadraticBezierTo(w * 0.14f, h * 0.4f, w * 0.32f, h * 0.2f)
                quadraticBezierTo(w * 0.5f, h * 0.04f, w * 0.68f, h * 0.2f)
                quadraticBezierTo(w * 0.86f, h * 0.4f, w * 0.66f, h * 0.62f)
                close()
            }
            drawPath(glass, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.38f, h * 0.74f), Offset(w * 0.62f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.42f, h * 0.87f), Offset(w * 0.58f, h * 0.87f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

    /** Lucchetto: dati che restano sul dispositivo (al posto di 🔒). */
    @Composable
    fun Lock(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            val shackle = Path().apply {
                moveTo(w * 0.32f, h * 0.46f)
                lineTo(w * 0.32f, h * 0.32f)
                quadraticBezierTo(w * 0.32f, h * 0.12f, w * 0.5f, h * 0.12f)
                quadraticBezierTo(w * 0.68f, h * 0.12f, w * 0.68f, h * 0.32f)
                lineTo(w * 0.68f, h * 0.46f)
            }
            drawPath(shackle, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val body = Path().apply {
                moveTo(w * 0.2f, h * 0.46f)
                lineTo(w * 0.8f, h * 0.46f)
                lineTo(w * 0.8f, h * 0.88f)
                lineTo(w * 0.2f, h * 0.88f)
                close()
            }
            drawPath(body, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
        }
    }

    /** Cursori: pannello pesi del rappresentante (al posto di 🛠️). */
    @Composable
    fun Sliders(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.08f
            drawLine(color, Offset(w * 0.12f, h * 0.28f), Offset(w * 0.88f, h * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.12f, h * 0.72f), Offset(w * 0.88f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawPath(circlePath(w * 0.34f, h * 0.28f, w * 0.1f), color)
            drawPath(circlePath(w * 0.66f, h * 0.5f, w * 0.1f), color)
            drawPath(circlePath(w * 0.46f, h * 0.72f, w * 0.1f), color)
        }
    }

    @Composable
    fun Plus(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.1f
            drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun ChevronRight(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.1f
            val path = Path().apply {
                moveTo(w * 0.38f, h * 0.24f)
                lineTo(w * 0.64f, h * 0.5f)
                lineTo(w * 0.38f, h * 0.76f)
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronLeft(modifier: Modifier = Modifier.size(24.dp), color: Color = Color(0xFF1E293B)) {
        Canvas(modifier = modifier) {
            val w = size.width; val h = size.height; val stroke = w * 0.1f
            val path = Path().apply {
                moveTo(w * 0.62f, h * 0.24f)
                lineTo(w * 0.36f, h * 0.5f)
                lineTo(w * 0.62f, h * 0.76f)
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
