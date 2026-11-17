package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Original seven-dot symbols: shared by launcher shortcuts and navigation. */
enum class NothingSymbol(val rows: List<String>) {
    HOME(listOf("0001000","0011100","0111110","1101011","0100010","0101010","0111110")),
    CALENDAR(listOf("0100010","1111111","1000001","1111111","1010101","1000001","1111111")),
    DOCUMENT(listOf("1111100","1000110","1010101","1000001","1011101","1000001","1111111")),
    CHAT(listOf("0111110","1100011","1010101","1100011","0111110","0011000","0010000")),
    SEAT(listOf("0111110","0100010","0111110","0100010","1111111","1000001","1000001")),
    POLL(listOf("0000010","0000010","0010010","0010010","1010010","1010010","1111111"))
}

@Composable
fun NothingDotIcon(symbol: NothingSymbol, modifier: Modifier, color: Color = AppTheme.TextDark) {
    Canvas(modifier) {
        val cell = size.minDimension / 9f
        val origin = Offset((size.width-cell*6)/2,(size.height-cell*6)/2)
        symbol.rows.forEachIndexed { y,row -> row.forEachIndexed { x,c ->
            if(c=='1') drawCircle(color,cell*.31f,origin+Offset(x*cell,y*cell))
        } }
    }
}

/** Instrument dial, expressed in dots instead of another outlined card. */
@Composable
fun NothingDial(modifier: Modifier, progress: Float = 1f, ink: Color = AppTheme.TextDark,
    track: Color = AppTheme.FieldOutline, accent: Color = AppTheme.PrimaryBlue) {
    Canvas(modifier) {
        val radius=size.minDimension*.46f
        val center=Offset(size.width/2,size.height/2)
        repeat(60) { i ->
            val a=(i*6-90)*PI.toFloat()/180
            val point=center+Offset(cos(a),sin(a))*radius
            drawCircle(if(i/60f < progress) ink else track,if(i%5==0) size.minDimension*.010f else size.minDimension*.0045f,point)
        }
        val angle=(-90+360*progress)*PI.toFloat()/180
        val dir=Offset(cos(angle),sin(angle))
        drawLine(accent,center+dir*(radius*.82f),center+dir*(radius*1.09f),size.minDimension*.022f,StrokeCap.Round)
    }
}
