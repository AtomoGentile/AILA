package circolareplus.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.platform.LocalNothingGlyph
import circolareplus.platform.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GlyphPaper = Color(0xFFF6F5EF)
private val GlyphMuted = Color(0xFFC2C2BA)
private val GlyphPanel = Color(0xFF181818)

@Composable
fun NothingFocusWidget(modifier: Modifier = Modifier, compact: Boolean = false) {
    val prefs = NothingExperience.preferences
    val focus = NothingExperience.focus
    val hardware = LocalNothingGlyph.current
    val haptic = LocalHapticFeedback.current
    val now = NothingExperience.nowMillis
    var studio by remember { mutableStateOf(false) }
    var signal by remember { mutableIntStateOf(0) }
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(GlyphPanel)
        .padding(NothingSpacing.Content), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FOCUS", style = MaterialTheme.typography.labelMedium, color = GlyphMuted)
                Text(if (focus.preparingForEvent) "Fino a 5 min prima dell’impegno" else prefs.signature.ifBlank { "Il tuo tempo" },
                    style = MaterialTheme.typography.labelSmall, color = GlyphMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                .clickable(role = Role.Button) { studio = true }
                .semantics { contentDescription = "Personalizza Glyph" }, contentAlignment = Alignment.Center) {
                AppIcons.Sliders(Modifier.size(18.dp), GlyphPaper)
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            NothingDial(Modifier.fillMaxSize(), progress = focus.fraction(now), ink = GlyphPaper,
                track = Color(0xFF393939), accent = AppTheme.PrimaryBlue)
            if(hardware.barSegments > 0) NothingBarPreview(Modifier.fillMaxSize().padding(vertical=12.dp), focus.fraction(now), hardware.barSegments)
            else NothingGlyphArt(Modifier.fillMaxSize().padding(32.dp),progress=focus.fraction(now),signal=signal+NothingExperience.completedSessions)
        }
        Text(formatFocusTime(focus.remaining(now)), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal),
            color = GlyphPaper, maxLines = 1)

        if (focus.completed) Text(if (focus.preparingForEvent) "È ora di prepararti" else "Sessione conclusa",
            style = MaterialTheme.typography.bodySmall, color = GlyphMuted)
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape).background(AppTheme.PrimaryBlue)
            .clickable(role = Role.Button) {
                hardware.stop()
                NothingExperience.toggleFocus(currentTimeMillis())
                signal++
                if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }, contentAlignment = Alignment.Center) {
            val actionLabel = when {
                focus.running && focus.preparingForEvent -> "Termina Focus"
                focus.running -> "Pausa"
                focus.completed && !focus.preparingForEvent -> "Ancora"
                focus.started && !focus.completed -> "Riprendi"
                else -> "Avvia"
            }
            Text(actionLabel,
                style = MaterialTheme.typography.labelLarge, color = if(AppTheme.isDarkMode) Color.Black else Color.White)
        }
    }
    if (studio) NothingGlyphStudio { studio = false }
}

internal fun formatFocusTime(seconds: Int): String = "${seconds.coerceAtLeast(0)/60}:${(seconds.coerceAtLeast(0)%60).toString().padStart(2,'0')}"
internal object NothingGlyphPreview { var active by mutableStateOf(false) }

@Composable
fun NothingGlyphSettingsSection(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val p = NothingExperience.preferences
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(AppTheme.TrackFill)
        .clickable(role = Role.Button) { open = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(GlyphPanel).padding(6.dp)) {
            NothingGlyphArt(Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text("Glyph Studio", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
            Text("${p.face.label} · ${p.focusMinutes} minuti", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        }
        AppIcons.ChevronRight(Modifier.size(18.dp), AppTheme.TextMuted)
    }
    if (open) NothingGlyphStudio { open = false }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NothingGlyphStudio(onDismiss: () -> Unit) {
    val p = NothingExperience.preferences
    val hardware = LocalNothingGlyph.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var signal by remember { mutableIntStateOf(0) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun update(value: NothingPreferences) = NothingExperience.configure(value)
    DisposableEffect(Unit) {
        onDispose { previewJob?.cancel(); NothingGlyphPreview.active = false; hardware.stop() }
    }
    AilaBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()
            .padding(horizontal = NothingSpacing.Edge).padding(bottom = NothingSpacing.Edge), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Glyph Studio", style = MaterialTheme.typography.headlineSmall, color = AppTheme.TextDark)
                    Text("Scegli cosa merita la tua attenzione.", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                }
                AilaIconButton("Chiudi Glyph Studio", onDismiss) { AppIcons.Close(Modifier.size(18.dp), it) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GlyphPanel).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AnimatedContent(p.face, transitionSpec = { fadeIn(tween(if(AppTheme.reduceMotion) 0 else 180)) togetherWith fadeOut(tween(if(AppTheme.reduceMotion) 0 else 120)) }, label = "glyphFace") { face ->
                    if(hardware.barSegments > 0) NothingBarPreview(Modifier.size(84.dp), segments=hardware.barSegments)
                    else NothingGlyphArt(Modifier.size(84.dp), face = face, signal = signal)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                    Text(p.signature.ifBlank { "AILA" }, style = MaterialTheme.typography.titleMedium, color = GlyphPaper, maxLines = 2)
                    Text("${p.focusMinutes} minuti di concentrazione", style = MaterialTheme.typography.bodySmall, color = GlyphMuted)
                    Text(if (hardware.ownerLabel != null) "Nothing Insider" else "Anteprima", style = MaterialTheme.typography.labelSmall, color = GlyphMuted)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Content)) {
            AilaSectionTitle("Avvisi sul retro")
            Text("Le notifiche reali arrivano anche con AILA chiusa. Su Nothing OS scegli un suono Glyph per ogni categoria; per gli avvisi persistenti usa le notifiche essenziali del telefono.", style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
            if(hardware.notificationSettingsAvailable) {
                NothingPreferenceRow("Categorie Glyph", "Separa circolari, bacheca, pere, sondaggi, calendario e classe nelle impostazioni del telefono.", p.systemNotifications) { update(p.copy(systemNotifications = it)) }
                if(p.systemNotifications) NothingNotificationGroup.entries.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                        Text(group.label, style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Item), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                            AilaSecondaryButton("Configura ${group.label}", { if(!hardware.openNotificationSettings(group)) notice = "Impossibile aprire le impostazioni del telefono." })
                            AilaTextButton({ notice = if(hardware.testNotification(group)) "Avviso inviato. Blocca lo schermo per verificare il Glyph; il suono Nothing e le luci si scelgono nelle impostazioni del telefono." else "Avviso non inviato: controlla il permesso notifiche e che la categoria sia attiva." }) { Text("Prova ${group.label}") }
                        }
                    }
                }
            } else Text("La configurazione delle luci di notifica è disponibile sui telefoni Nothing. Le forme sotto sono anteprime sullo schermo.", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextDark) }
            if(hardware.barSegments == 0) {
            AilaSectionTitle("Disegno del Focus")
            GlyphFace.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
                    row.forEach { face ->
                        Row(Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
                            .background(if(p.face == face) AppTheme.TextDark else AppTheme.TrackFill)
                            .clickable(role = Role.RadioButton) { update(p.copy(face = face)); signal++ }
                            .semantics { selected = p.face == face }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
                            Box(Modifier.size(8.dp).background(if(p.face == face) AppTheme.PrimaryBlue else AppTheme.TextMuted,CircleShape))
                            Text(face.label, style = MaterialTheme.typography.labelLarge, color = if(p.face == face) AppTheme.BackgroundLight else AppTheme.TextDark)
                        }
                    }
                }
            }
            } else {
                AilaSectionTitle("Glyph Bar")
                Text("${hardware.barSegments} segmenti per il tempo rimanente. Le notifiche usano il pattern del suono scelto in Nothing OS.", style=MaterialTheme.typography.bodySmall, color=AppTheme.TextMuted)
            }
            Text("Ritmo", style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            AilaFilterBar(GlyphRhythm.entries.map { it.label }, GlyphRhythm.entries.indexOf(p.rhythm),
                { update(p.copy(rhythm = GlyphRhythm.entries[it])); signal++ })
            Text("Luminosità dell’anteprima", style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            AilaFilterBar(listOf("Bassa", "Media", "Alta"), when { p.previewBrightness < .5f -> 0; p.previewBrightness < .9f -> 1; else -> 2 },
                { update(p.copy(previewBrightness = listOf(.35f,.7f,1f)[it])); signal++ })
            Text(if(NothingExperience.focus.running) "Durata della prossima sessione" else "Durata della sessione", style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            val durations = listOf(5,15,25,45)
            AilaFilterBar(durations.map { "$it min" }, durations.indexOf(p.focusMinutes), { update(p.copy(focusMinutes = durations[it])) })
            OutlinedTextField(value = p.signature, onValueChange = { update(p.copy(signature = it.take(8))) },
                label = { Text("Firma del Glyph") }, supportingText = { Text("Fino a 8 caratteri, mostrati sotto il timer.") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ailaFieldColors())
            NothingPreferenceRow("Caratteri a punti", "Titoli e strumenti; il testo dei contenuti resta leggibile.", p.dotDisplay) { update(p.copy(dotDisplay = it)) }
            NothingPreferenceRow("Widget nella Home", "Mostra il prossimo impegno nel Glyph e i comandi di Focus.", p.showFocusWidget) { update(p.copy(showFocusWidget = it)) }
            NothingPreferenceRow("Feedback tattile", "Un segnale all’avvio e a fine sessione.", p.haptics) { update(p.copy(haptics = it)) }
            if (hardware.ownerLabel != null) {
                AilaSectionTitle("Nothing Insider")
                Text(hardware.status, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                NothingPreferenceRow("Luci del Focus", "Fine Focus e avanzamento sulla barra, con AILA aperta.", p.rearLights,
                    enabled = hardware.ready) { update(p.copy(rearLights = it)); if(!it) hardware.stop() }
                Text("Focus: controllo diretto con AILA in primo piano. Notifiche: gestione del telefono anche ad app chiusa.", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
                AilaSecondaryButton("Prova", {
                    signal++
                    previewJob?.cancel()
                    previewJob = scope.launch {
                        NothingGlyphPreview.active = true
                        try {
                            if(p.rearLights && hardware.ready) {
                                hardware.stop(); hardware.pulse(p.face.ordinal,p.rhythm.periodMillis)
                            }
                            delay(p.rhythm.periodMillis + 100L)
                        } finally { hardware.stop(); NothingGlyphPreview.active = false }
                    }
                }, modifier = Modifier.weight(1f))
                AilaSecondaryButton("Reimposta timer", { hardware.stop(); NothingExperience.resetFocus() }, modifier = Modifier.weight(1f))
            }
            Text("Sessioni concluse: ${NothingExperience.completedSessions}", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        }
    }
}

@Composable
private fun NothingPreferenceRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Content)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.TextDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        }
        AilaSwitch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun NothingFocusRuntime() {
    val prefs = NothingExperience.preferences
    val focus = NothingExperience.focus
    val hardware = LocalNothingGlyph.current
    val haptic = LocalHapticFeedback.current
    var previousCompletions by remember { mutableIntStateOf(NothingExperience.completedSessions) }
    LaunchedEffect(focus.deadlineMillis, prefs.rearLights) {
        try {
            NothingExperience.refreshClock()
            while (NothingExperience.focus.running) {
                NothingExperience.refreshClock()
                val now = NothingExperience.nowMillis
                if (NothingExperience.completeIfDue(now)) break
                if (prefs.rearLights && !NothingGlyphPreview.active) hardware.showProgress(NothingExperience.focus.fraction(now))
                delay(1000)
            }
        } finally { hardware.stop() }
    }
    LaunchedEffect(NothingExperience.completedSessions) {
        if (NothingExperience.completedSessions > previousCompletions) {
            previousCompletions = NothingExperience.completedSessions
            if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.yield()
            if (prefs.rearLights) {
                hardware.pulse(prefs.face.ordinal, prefs.rhythm.periodMillis)
                delay(prefs.rhythm.periodMillis + 100L)
                hardware.stop()
            }
        }
    }

}
