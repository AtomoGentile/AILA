package circolareplus.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import circolareplus.platform.LocalNothingGlyph
import kotlinx.coroutines.delay

/** Entries and callbacks belong to this Home composition, never to a persisted singleton. */
enum class GlyphChannel(val label: String) { CALENDAR("Calendario"), CIRCULARS("Circolari"), CLASS("Classe") }
data class GlyphRadarEntry(val key: String, val channel: GlyphChannel, val title: String,
    val detail: String, val urgent: Boolean = false, val onOpen: () -> Unit)
data class GlyphRadarData(val entries: List<GlyphRadarEntry>, val loading: Boolean = false,
    val error: String? = null, val onCalendar: () -> Unit = {})
val LocalGlyphRadar = staticCompositionLocalOf<GlyphRadarData?> { null }
internal fun radarEntries(entries: List<GlyphRadarEntry>, prefs: NothingPreferences) = entries
    .distinctBy { it.key }.filter { when(it.channel) {
        GlyphChannel.CALENDAR -> prefs.radarEvents
        GlyphChannel.CIRCULARS -> prefs.radarCirculars
        GlyphChannel.CLASS -> prefs.radarClass
    } }.sortedWith(compareByDescending<GlyphRadarEntry> { it.urgent }.thenBy { it.channel.ordinal })

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NothingRadarWidget(data: GlyphRadarData, modifier: Modifier = Modifier) {
    val p = NothingExperience.preferences
    val entries = radarEntries(data.entries, p)
    var focus by remember { mutableStateOf(NothingExperience.focus.running) }
    var studio by remember { mutableStateOf(false) }
    var inbox by remember { mutableStateOf(false) }
    val hardware = LocalNothingGlyph.current
    val paper = Color(0xFFF6F5EF)
    val muted = Color(0xFFC2C2BA)
    // Prime on the first complete snapshot. Loading/refresh and preference changes do not
    // manufacture alerts; disposal cancels the bounded signal and turns off the hardware.
    var known by remember { mutableStateOf<Set<String>?>(null) }
    val allKeys = data.entries.map { it.key }.toSet()
    val focusRunning = NothingExperience.focus.running
    LaunchedEffect(allKeys, data.loading, data.error, p.rearLights, focusRunning) {
        if (data.loading || data.error != null) return@LaunchedEffect
        val previous = known
        known = allKeys
        val fresh = entries.firstOrNull { previous != null && it.key !in previous }
        if (fresh != null && p.rearLights && !focusRunning && !NothingGlyphPreview.active) {
            try {
                hardware.pulse(fresh.channel.ordinal + 1, p.rhythm.periodMillis)
                delay(p.rhythm.periodMillis + 100L)
            } finally { hardware.stop() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Radar", "Focus").forEachIndexed { index, label ->
                val selected = focus == (index == 1)
                Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                    .background(if(selected) AppTheme.TextDark else AppTheme.TrackFill)
                    .clickable(role = Role.Tab) { focus = index == 1 }
                    .semantics { this.selected = selected }, contentAlignment = Alignment.Center) {
                    Text(label, color = if(selected) AppTheme.BackgroundLight else AppTheme.TextDark,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        AnimatedContent(focus, transitionSpec = {
            fadeIn(tween(if(AppTheme.reduceMotion) 0 else 160)) togetherWith fadeOut(tween(if(AppTheme.reduceMotion) 0 else 100))
        }, label = "glyphInstrument") { timer ->
            if(timer) NothingFocusWidget(Modifier.fillMaxWidth()) else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF181818)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("GLYPH / 03", Modifier.weight(1f), color = muted, style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                            .clickable(role = Role.Button) { studio = true }
                            .semantics { contentDescription = "Personalizza Glyph" }, contentAlignment = Alignment.Center) {
                            AppIcons.Sliders(Modifier.size(18.dp), paper)
                        }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)
                        .clickable(role = Role.Button) { inbox = true }
                        .semantics { contentDescription = "Apri Radar: ${entries.size} voci, ${entries.count { it.urgent }} oggi" },
                        contentAlignment = Alignment.Center) {
                        NothingRadarTracks(entries, Modifier.fillMaxSize(), paper)
                        Text(if(data.loading) "…" else if(entries.size > 999) "999+" else entries.size.toString().padStart(2,'0'),
                            style = nothingDisplayStyle(if(entries.size < 100) 32 else if(entries.size < 1000) 24 else 18, 42), color = paper)
                    }
                    Text(when {
                        data.loading -> "Aggiornamento…"
                        data.error != null -> "Dati parziali"
                        entries.any { it.urgent } -> "${entries.count { it.urgent }} oggi"
                        entries.isEmpty() -> "Nessuna voce"
                        else -> "${entries.size} da vedere"
                    }, color = paper, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape).background(paper)
                        .clickable(role = Role.Button) { inbox = true }, contentAlignment = Alignment.Center) {
                        Text("Apri radar", color = Color(0xFF181818), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    if(studio) NothingGlyphStudio { studio = false }
    if(inbox) AilaBottomSheet(onDismissRequest = { inbox = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radar", Modifier.weight(1f), color = AppTheme.TextDark, style = nothingDisplayStyle(30, 40))
                AilaIconButton("Chiudi Radar", { inbox = false }) { AppIcons.Close(Modifier.size(18.dp), it) }
            }
            Text("Dall’esterno al centro: calendario, circolari e classe. Ogni segmento è una voce, fino a 12 per traccia. Il rosso indica gli eventi di oggi.", color = AppTheme.TextMuted, style = MaterialTheme.typography.bodyMedium)
            if(data.loading) Text("Aggiornamento in corso…", color = AppTheme.TextMuted)
            if(data.error != null) Text("Il Brief non è disponibile. Le voci del calendario restano consultabili.", color = AppTheme.TextMuted)
            GlyphChannel.entries.forEach { channel ->
                val group = entries.filter { it.channel == channel }
                val enabled = when(channel) { GlyphChannel.CALENDAR -> p.radarEvents; GlyphChannel.CIRCULARS -> p.radarCirculars; GlyphChannel.CLASS -> p.radarClass }
                Text("${channel.ordinal + 1} / ${channel.label.uppercase()} · ${if(enabled) group.size else "OFF"}",
                    color = AppTheme.TextMuted, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
                group.forEach { entry ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppTheme.TrackFill)
                        .clickable(role = Role.Button) { inbox = false; entry.onOpen() }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, color = AppTheme.TextDark, style = MaterialTheme.typography.titleMedium)
                        Text((if(entry.urgent) "OGGI · " else "") + entry.detail, color = AppTheme.TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(entries.isEmpty() && !data.loading) AilaSecondaryButton("Apri calendario", { inbox = false; data.onCalendar() })
            AilaSecondaryButton("Configura tracce", { inbox = false; studio = true })
        }
    }
}

@Composable
private fun NothingRadarTracks(entries: List<GlyphRadarEntry>, modifier: Modifier, paper: Color) {
    val red = AppTheme.PrimaryBlue
    Canvas(modifier) {
        GlyphChannel.entries.forEachIndexed { index, channel ->
            val inset = size.minDimension * (.08f + index * .105f)
            val radius = (size.minDimension - inset * 2) / 2
            val arcSize = Size(radius*2, radius*2)
            val origin = Offset(size.width/2-radius, size.height/2-radius)
            val count = entries.count { it.channel == channel }
            repeat(12) { segment ->
                drawArc(if(segment < count.coerceAtMost(12)) {
                    if(entries.any { it.channel == channel && it.urgent }) red else paper
                } else Color(0xFF424242), -80f + segment*28f, 19f, false, origin, arcSize,
                    style = Stroke(size.minDimension*.032f, cap = StrokeCap.Round))
            }
        }
    }
}
