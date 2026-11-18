package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.today

/** Static texture: no timer or infinite invalidation while the user scrolls. */
fun Modifier.nothingGrid(ink: Color = AppTheme.TextFaint.copy(alpha = .16f)): Modifier = drawWithCache {
    val gap = 8.dp.toPx()
    val radius = .65.dp.toPx()
    onDrawBehind {
        var x = gap
        while (x < size.width) {
            var y = gap
            while (y < size.height) {
                drawCircle(ink, radius, Offset(x, y))
                y += gap
            }
            x += gap
        }
    }
}

@Composable
fun NothingScreenHeader(
    title: String, subtitle: String?, modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().background(AppTheme.BackgroundLight).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AILA / NOTHING", style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted,
                modifier = Modifier.weight(1f))
            action?.invoke()
            LocalProfileEntry.current?.let { Spacer(Modifier.width(8.dp)); AilaProfileButton(it) }
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = nothingDisplayStyle(32, 42), color = AppTheme.TextDark,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(AppTheme.PrimaryBlue, CircleShape))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(AppTheme.Hairline))
        }
    }
}

/** Bounded, static matrix when motion is disabled; only loaders animate repeatedly. */
@Composable
fun NothingMatrixLoader(modifier: Modifier = Modifier, color: Color = AppTheme.TextDark) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(AppTheme.reduceMotion) {
        if (!AppTheme.reduceMotion) while (true) { kotlinx.coroutines.delay(110); frame = (frame + 1) % 9 }
    }
    Canvas(modifier) {
        val cell = size.minDimension / 3f
        repeat(9) { i ->
            drawCircle(color.copy(alpha = if (AppTheme.reduceMotion || i == frame) 1f else .18f),
                cell*.23f, Offset(cell*(i%3+.5f), cell*(i/3+.5f)))
        }
    }
}
