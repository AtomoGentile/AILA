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
        Text(title, style = MaterialTheme.typography.headlineMedium, color = AppTheme.TextDark,
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
    Column(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AILA (01)", style = MaterialTheme.typography.labelMedium,
                color = AppTheme.TextMuted, modifier = Modifier.weight(1f))
            AilaIconButton("Cerca", onSearch) { AppIcons.Search(Modifier.size(20.dp), AppTheme.TextDark) }
            AilaIconButton("Gita", onGita) { AppIcons.Bus(Modifier.size(20.dp), AppTheme.TextDark) }
            LocalProfileEntry.current?.let { AilaProfileButton(it) }
        }
        Column {
            Text("Ciao,", style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextMuted)
            Text(studentFirstName, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 38.sp, lineHeight = 44.sp),
                color = AppTheme.TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val date = today()
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                .background(AppTheme.SurfaceWhite).border(1.dp, AppTheme.Hairline, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClickLabel = "Apri calendario", onClick = onCalendar)
                .padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("OGGI", style = MaterialTheme.typography.labelMedium, color = AppTheme.TextMuted)
                Text(date.day.toString().padStart(2, '0'), style = MaterialTheme.typography.displaySmall.copy(fontSize = 68.sp, lineHeight = 76.sp),
                    color = AppTheme.TextDark)
                Text(ITALIAN_MONTHS[date.month].uppercase(), style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
            }
            NothingGlyphWidget(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NothingQuickTile("Circolari", "01", onCirculars, Modifier.weight(1f)) { AppIcons.Document(Modifier.size(24.dp), AppTheme.TextDark) }
            NothingQuickTile("Bacheca", "02", onBoard, Modifier.weight(1f)) { AppIcons.ChatBubble(Modifier.size(24.dp), AppTheme.TextDark) }
            NothingQuickTile("Posti", "03", onSeatMap, Modifier.weight(1f)) { AppIcons.Chair(Modifier.size(24.dp), AppTheme.TextDark) }
        }
    }
}

@Composable
private fun NothingQuickTile(label: String, index: String, onClick: () -> Unit, modifier: Modifier, icon: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(AppTheme.TrackFill)
        .clickable(role = Role.Button, onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(index, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = AppTheme.TextMuted)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextDark, maxLines = 1)
    }
}

@Composable
fun NothingGlyphWidget(modifier: Modifier = Modifier) {
    val hardware = circolareplus.platform.rememberNothingGlyph()
    val haptic = LocalHapticFeedback.current
    val light = remember { Animatable(1f) }
    var pulses by remember { mutableIntStateOf(0) }
    LaunchedEffect(pulses) {
        if (pulses > 0) {
            hardware.stop()
            hardware.pulse()
            if (!AppTheme.reduceMotion) { light.snapTo(0f); light.animateTo(1f, tween(700)) }
            kotlinx.coroutines.delay(1500)
            hardware.stop()
        }
    }
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF181818))
        .nothingGrid(Color.White.copy(alpha = .09f))
        .clickable(role = Role.Button, onClickLabel = if (hardware.ready) "Accendi le luci posteriori" else "Anima il Glyph sullo schermo") {
            pulses++
            if (hardware.ownerLabel != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (hardware.ownerLabel != null) "INSIDER" else "GLYPH", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBEBEB8))
            Box(Modifier.size(5.dp).background(AppTheme.PrimaryBlue, CircleShape))
        }
        NothingGlyphMark(size = 120.dp, progress = light.value)
        Text(if (pulses > 0) "HELLO, NOTHING." else "TOCCA IL GLYPH", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = Color(0xFFBEBEB8), maxLines = 1)
        if (hardware.ownerLabel != null) {
            Spacer(Modifier.height(6.dp))
            Text(hardware.ownerLabel.orEmpty(), style = MaterialTheme.typography.labelSmall,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hardware.status, style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBEBEB8), maxLines = 3)
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
