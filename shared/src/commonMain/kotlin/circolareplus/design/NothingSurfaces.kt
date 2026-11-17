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

/** A bespoke widget deck, used only by Nothing mode. */
@Composable
fun NothingHomePanel(
    studentFirstName: String,
    onSearch: () -> Unit, onGita: () -> Unit, onCirculars: () -> Unit,
    onCalendar: () -> Unit, onBoard: () -> Unit, onSeatMap: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val compactFocus = (maxWidth - 52.dp) / 2 - 28.dp < 124.dp
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AILA / NOTHING", style = MaterialTheme.typography.labelMedium,
                color = AppTheme.TextMuted, modifier = Modifier.weight(1f))
            AilaIconButton("Cerca", onSearch) { AppIcons.Search(Modifier.size(20.dp), AppTheme.TextDark) }
            AilaIconButton("Gita", onGita) { AppIcons.Bus(Modifier.size(20.dp), AppTheme.TextDark) }
            LocalProfileEntry.current?.let { AilaProfileButton(it) }
        }
        Column {
            Text("Ciao,", style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextMuted)
            Text(studentFirstName, style = nothingDisplayStyle(44, 54),
                color = AppTheme.TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val date = today()
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                .background(AppTheme.TextDark)
                .clickable(role = Role.Button, onClickLabel = "Apri calendario", onClick = onCalendar)
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween) {
                Text("OGGI", style = MaterialTheme.typography.labelMedium, color = AppTheme.BackgroundLight)
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    NothingDial(Modifier.fillMaxSize(), ink = AppTheme.BackgroundLight,
                        track = AppTheme.TextMuted, accent = AppTheme.PrimaryBlue)
                    Text(date.day.toString().padStart(2, '0'), style = nothingDisplayStyle(52, 66),
                        color = AppTheme.BackgroundLight)
                }
                Text(ITALIAN_MONTHS[date.month].uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.BackgroundLight)
            }
            if (NothingExperience.preferences.showFocusWidget) NothingGlyphWidget(Modifier.weight(1f), compactFocus)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NothingQuickTile("Circolari", NothingSymbol.DOCUMENT, onCirculars, Modifier.weight(1f))
            NothingQuickTile("Bacheca", NothingSymbol.CHAT, onBoard, Modifier.weight(1f))
            NothingQuickTile("Posti", NothingSymbol.SEAT, onSeatMap, Modifier.weight(1f))
        }
    }
    }
}

@Composable
private fun NothingQuickTile(label: String, symbol: NothingSymbol, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(AppTheme.TrackFill)
            .nothingGrid(AppTheme.TextFaint.copy(alpha = .14f)),contentAlignment = Alignment.Center) {
            NothingDotIcon(symbol,Modifier.fillMaxSize().padding(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.TextDark, maxLines = 1)
    }
}

@Composable
fun NothingGlyphWidget(modifier: Modifier = Modifier, compact: Boolean = false) {
    val radar = LocalGlyphRadar.current
    if (radar == null) NothingFocusWidget(modifier, compact) else NothingRadarWidget(radar, modifier)
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
