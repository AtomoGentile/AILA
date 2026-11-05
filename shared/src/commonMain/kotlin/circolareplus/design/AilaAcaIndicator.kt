package circolareplus.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import circolareplus.ai.assistant.AssistantAgentPhase

@Composable fun AilaAcaIndicator(phase:AssistantAgentPhase?,awaitingConfirmation:Boolean,showPopup:Boolean,operationKey:String?,onOpenAssistant:()->Unit){
 val opacity by animateFloatAsState(if(phase!=null)1f else 0f,tween(if(phase!=null)0 else 120),label="aca perimeter")
 if(opacity==0f)return
 val colors=listOf(Color(0xFF9666FF),Color(0xFF568BFF),Color(0xFF40DAD0),Color(0xFFF7DD8A),Color(0xFFFFAA76),Color(0xFFF77EB5),Color(0xFF9666FF))
 val transition=rememberInfiniteTransition(label="aca light")
 val angle by transition.animateFloat(0f,360f,infiniteRepeatable(tween(5000,easing=LinearEasing)),label="aca rotation")
 Canvas(Modifier.fillMaxSize()){
  val inset=3.dp.toPx()
  val brush=Brush.sweepGradient(colors,Offset(size.width/2,size.height/2))
  // The entire perimeter stays lit for the actual duration, including inside chat.
  val alpha=opacity*(if(AppTheme.reduceMotion)1f else .85f+.15f*kotlin.math.sin(angle*0.0174533f))
  for(width in if(AppTheme.liteEffects)listOf(4f)else listOf(22f,12f,4f))drawRoundRect(brush,Offset(inset,inset),Size(size.width-2*inset,size.height-2*inset),CornerRadius(26.dp.toPx()),alpha=alpha*(if(width==4f)1f else .14f),style=Stroke(width.dp.toPx()))
 }
}
