package circolareplus.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import kotlinx.coroutines.delay

@Composable
internal fun VoiceRecordingPanel(onStop: () -> Unit, onCancel: () -> Unit) {
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
                Text("Il vocale verrà allegato alla chat.",style=MaterialTheme.typography.bodySmall,color=AppTheme.TextMuted)
            }
            Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.titleMedium,color=AppTheme.TintRedInk)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TextButton(onClick=onCancel,modifier=Modifier.weight(1f)){Text("Annulla")}
            Button(onClick=onStop,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp)) {
                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.onPrimary,RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp));Text("Termina")
            }
        }
    }
}
