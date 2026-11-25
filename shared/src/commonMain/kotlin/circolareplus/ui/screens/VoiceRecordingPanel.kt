package circolareplus.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import kotlinx.coroutines.delay

@Composable
internal fun VoiceRecordingPanel(
    onStop: () -> Unit,
    onCancel: () -> Unit,
    subtitle: String = "Il vocale verrà allegato al messaggio."
) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while(true) { delay(1000);seconds++ } }
    val pulse=if(AppTheme.reduceMotion) null else rememberInfiniteTransition(label="recording").animateFloat(
        .45f,1f,infiniteRepeatable(tween(800),RepeatMode.Reverse),label="recording light")
    Column(Modifier.fillMaxWidth().background(AppTheme.CardSurface,RoundedCornerShape(28.dp))
        .border(1.dp,AppTheme.TintRedInk.copy(alpha=.22f),RoundedCornerShape(28.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).alpha(pulse?.value?:1f).background(AppTheme.TintRedInk,RoundedCornerShape(5.dp)))
            Column(Modifier.weight(1f)) {
                Text("Sto registrando",style=MaterialTheme.typography.titleSmall,color=AppTheme.TextDark,fontWeight=FontWeight.SemiBold)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
            }
            Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.titleMedium,color=AppTheme.TintRedInk)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            circolareplus.design.AilaTextButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Annulla")}
            circolareplus.design.AilaActionButton(onClick=onStop,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp)) {
                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.onPrimary,RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp));Text("Termina")
            }
        }
    }
}

/** Barra di registrazione vocale compatta su una sola riga, in stile WhatsApp. */
@Composable
internal fun ChatVoiceRecordingBar(
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val pulse = if (AppTheme.reduceMotion) null else rememberInfiniteTransition(label = "chatRec").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "chatRecDot"
    )
    val wavePhase = if (AppTheme.reduceMotion) null else rememberInfiniteTransition(label = "chatWave").animateFloat(
        0f, 6.283f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "chatWavePhase"
    )
    val waveColor = AppTheme.TintRedInk.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.CardSurface, RoundedCornerShape(28.dp))
            .border(1.dp, AppTheme.TintRedInk.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.TintRed)
                .clickable(role = Role.Button, onClick = onCancel)
                .semantics { contentDescription = "Annulla registrazione vocale" },
            contentAlignment = Alignment.Center
        ) {
            AppIcons.Trash(Modifier.size(18.dp), AppTheme.TintRedInk)
        }
        Box(
            Modifier
                .size(9.dp)
                .alpha(pulse?.value ?: 1f)
                .clip(CircleShape)
                .background(AppTheme.TintRedInk)
        )
        Text(
            text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.TextDark
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
        ) {
            val count = 20
            val step = size.width / count.toFloat()
            val phase = wavePhase?.value ?: 0f
            val centerY = size.height / 2f
            for (i in 0 until count) {
                val amp = 0.25f + 0.55f * kotlin.math.abs(kotlin.math.sin(phase + i * 0.55f)).toFloat()
                val x = i * step + step / 2f
                val halfH = (size.height * amp * 0.45f).coerceAtLeast(2.dp.toPx())
                drawLine(
                    color = waveColor,
                    start = Offset(x, centerY - halfH),
                    end = Offset(x, centerY + halfH),
                    strokeWidth = (step * 0.45f).coerceIn(2.dp.toPx(), 3.dp.toPx()),
                    cap = StrokeCap.Round
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.PrimaryGradient)
                .clickable(role = Role.Button, onClick = onSend)
                .semantics { contentDescription = "Invia messaggio vocale" },
            contentAlignment = Alignment.Center
        ) {
            AppIcons.ArrowUp(
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = if (AppTheme.isGlass || AppTheme.isNothing) 0f else 90f },
                color = if (AppTheme.isNothing) AppTheme.BackgroundLight else Color.White
            )
        }
    }
}
