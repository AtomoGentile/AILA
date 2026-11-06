package circolareplus.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.math.cos

/** Small living light source; the symbol itself stays legible and still. */
@Composable
internal fun AcaNeuralMark(thinking: Boolean, modifier: Modifier = Modifier) {
    val reduced = AppTheme.reduceMotion
    val phase = if(reduced) null else rememberInfiniteTransition(label="aca neural mark").animateFloat(
        0f, 6.2831855f, infiniteRepeatable(tween(if(thinking) 2400 else 4600,easing=LinearEasing)),label="aca neural orbit")
    Box(modifier,contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val angle=phase?.value?:0f
            listOf(Color(0xFF7088FF),Color(0xFFAF79E8),Color(0xFF59CEBB)).forEachIndexed { index,color ->
                val a=angle+index*2.0944f
                val center=Offset(size.width*(.5f+.13f*cos(a)),size.height*(.5f+.13f*sin(a)))
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha=.55f),color.copy(alpha=0f)),center=center,radius=size.minDimension*.55f),radius=size.minDimension*.55f,center=center)
            }
        }
        AilaAssistantMark(modifier = Modifier.fillMaxSize(.58f))
    }
}
