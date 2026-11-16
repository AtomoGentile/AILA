package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import circolareplus.generated.resources.Res
import circolareplus.generated.resources.aila_dot
import org.jetbrains.compose.resources.Font
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dot text is opt-in display artwork, never the default font of event titles or controls. */
@Composable
fun nothingDisplayStyle(size: Int, lineHeight: Int = size + 8): TextStyle = TextStyle(
    fontFamily = if (NothingExperience.preferences.dotDisplay) FontFamily(Font(Res.font.aila_dot)) else FontFamily.SansSerif,
    fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = 0.sp
)

@Composable
fun NothingGlyphArt(
    modifier: Modifier,
    face: GlyphFace = NothingExperience.preferences.face,
    progress: Float = 1f,
    brightness: Float = NothingExperience.preferences.previewBrightness,
    signal: Int = 0
) {
    val rhythm = NothingExperience.preferences.rhythm
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(signal, rhythm) {
        if (signal > 0 && !AppTheme.reduceMotion) {
            pulse.snapTo(.2f)
            pulse.animateTo(1f, tween(rhythm.periodMillis / 2))
        } else pulse.snapTo(1f)
    }
    val displayed by animateFloatAsState(progress.coerceIn(0f,1f),
        animationSpec = tween(if (AppTheme.reduceMotion) 0 else 260), label = "glyphTime")
    Canvas(modifier.semantics { contentDescription = "Anteprima Glyph" }) {
        val s = size.minDimension
        val ox = (size.width-s)/2
        val oy = (size.height-s)/2
        fun p(x: Float,y: Float) = Offset(ox+x*s,oy+y*s)
        val off = Color(0xFF383838)
        val on = Color(0xFFF6F5EF).copy(alpha = (brightness * pulse.value).coerceIn(.2f,1f))
        fun line(x1:Float,y1:Float,x2:Float,y2:Float,lit:Boolean = true,width:Float = .035f) =
            drawLine(if(lit) on else off,p(x1,y1),p(x2,y2),s*width,StrokeCap.Round)
        fun arc(cx:Float,cy:Float,r:Float,start:Float,sweep:Float,fraction:Float = displayed,width:Float = .035f) {
            fun path(t:Float) = Path().apply {
                val steps = (72*t).toInt().coerceAtLeast(1)
                for(i in 0..steps) {
                    val a = (start+sweep*t*i/steps)*PI.toFloat()/180f
                    val q=p(cx+r*cos(a),cy+r*sin(a))
                    if(i==0) moveTo(q.x,q.y) else lineTo(q.x,q.y)
                }
            }
            drawPath(path(1f),off,style=Stroke(s*width,cap=StrokeCap.Round))
            if(fraction>0f) drawPath(path(fraction),on,style=Stroke(s*width,cap=StrokeCap.Round))
        }
        when(face) {
            GlyphFace.CLASSIC -> {
                arc(.23f,.18f,.11f,45f,270f)
                arc(.52f,.49f,.25f,48f,264f)
                line(.76f,.15f,.87f,.26f,displayed>.5f)
                line(.52f,.80f,.52f,.94f,displayed>.1f)
                drawCircle(on,s*.022f,p(.52f,.99f))
            }
            GlyphFace.ORBIT -> {
                arc(.5f,.5f,.40f,-90f,348f,width=.024f)
                arc(.5f,.5f,.28f,90f,348f,width=.034f)
                arc(.5f,.5f,.13f,-90f,348f,width=.045f)
                drawCircle(AppTheme.PrimaryBlue,s*.035f,p(.5f,.08f))
            }
            GlyphFace.MATRIX -> {
                val a = intArrayOf(14,17,17,31,17,17,17)
                for(y in 0..8) for(x in 0..8) {
                    val inside=(x-4)*(x-4)+(y-4)*(y-4)<=20
                    if(inside) {
                        val letter=x in 2..6 && y in 1..7 && (a[y-1] and (1 shl (6-x)))!=0
                        val lit=letter && (8-y)/8f<=displayed
                        drawCircle(if(lit)on else off,s*.025f,p(.10f+x*.10f,.10f+y*.10f))
                    }
                }
            }
            GlyphFace.BARS -> {
                val heights=floatArrayOf(.44f,.80f,.58f,.96f)
                heights.forEachIndexed { x,h ->
                    repeat(12) { y ->
                        val pos=.5f-h*.5f+y*h/12
                        val lit=(12-y)/12f<=displayed
                        line(.18f+x*.21f,pos,.18f+x*.21f,pos+h*.037f,lit,.065f)
                    }
                }
            }
        }
    }
}
