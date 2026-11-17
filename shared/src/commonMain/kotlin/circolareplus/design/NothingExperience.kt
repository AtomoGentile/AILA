package circolareplus.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class GlyphFace(val label: String) { CLASSIC("Circuito"), ORBIT("Orbita"), MATRIX("Matrice"), BARS("Tracce") }
@Serializable
enum class GlyphRhythm(val label: String, val periodMillis: Int) { CALM("Calmo", 1800), BALANCED("Bilanciato", 1200), QUICK("Rapido", 800) }
@Serializable
data class NothingPreferences(
    val face: GlyphFace = GlyphFace.CLASSIC,
    val rhythm: GlyphRhythm = GlyphRhythm.BALANCED,
    val previewBrightness: Float = .85f,
    val focusMinutes: Int = 25,
    val signature: String = "AILA",
    val dotDisplay: Boolean = true,
    val haptics: Boolean = true,
    val rearLights: Boolean = false,
    val showFocusWidget: Boolean = true,
    val radarEvents: Boolean = true,
    val radarCirculars: Boolean = true,
    val radarClass: Boolean = true,
    val systemNotifications: Boolean = false,
    val radarHome: Boolean = false
)

/** The deadline, rather than a decrementing counter, survives navigation and process death. */
@Serializable
data class NothingFocusSession(
    val totalSeconds: Int = 25 * 60,
    val pausedSeconds: Int = 25 * 60,
    val deadlineMillis: Long? = null,
    val started: Boolean = false,
    val completed: Boolean = false
) {
    val running: Boolean get() = deadlineMillis != null
    fun remaining(nowMillis: Long): Int = deadlineMillis?.let {
        ((it - nowMillis + 999L) / 1000L).coerceIn(0L, totalSeconds.toLong()).toInt()
    } ?: pausedSeconds.coerceIn(0, totalSeconds)
    fun fraction(nowMillis: Long): Float = remaining(nowMillis).toFloat() / totalSeconds.coerceAtLeast(1)
    fun start(nowMillis: Long): NothingFocusSession = copy(deadlineMillis = nowMillis + pausedSeconds * 1000L, started = true, completed = false)
    fun pause(nowMillis: Long): NothingFocusSession = copy(pausedSeconds = remaining(nowMillis), deadlineMillis = null)
    companion object {
        fun fresh(minutes: Int) = NothingFocusSession(totalSeconds = minutes.coerceIn(1, 90)*60, pausedSeconds = minutes.coerceIn(1, 90)*60)
    }
}

@Serializable
private data class NothingSavedState(
    val preferences: NothingPreferences = NothingPreferences(),
    val focus: NothingFocusSession = NothingFocusSession(),
    val completedSessions: Int = 0
)

object NothingExperience {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var preferences by mutableStateOf(NothingPreferences())
        private set
    var focus by mutableStateOf(NothingFocusSession())
        private set
    var completedSessions by mutableStateOf(0)
        private set
    var nowMillis by mutableLongStateOf(circolareplus.platform.currentTimeMillis())
        private set
    fun refreshClock() { nowMillis = circolareplus.platform.currentTimeMillis() }
    private var writer: ((String) -> Unit)? = null

    fun readPreferences(json: String): NothingPreferences = runCatching {
        codec.decodeFromString<NothingSavedState>(json).preferences
    }.getOrDefault(NothingPreferences())

    fun restore(json: String, write: (String) -> Unit) {
        val saved = runCatching { codec.decodeFromString<NothingSavedState>(json) }.getOrDefault(NothingSavedState())
        preferences = saved.preferences.copy(previewBrightness = saved.preferences.previewBrightness.coerceIn(.25f, 1f), focusMinutes = saved.preferences.focusMinutes.coerceIn(1, 90), signature = cleanSignature(saved.preferences.signature))
        val total = saved.focus.totalSeconds.coerceIn(60, 5400)
        focus = saved.focus.copy(totalSeconds = total, pausedSeconds = saved.focus.pausedSeconds.coerceIn(0,total))
        refreshClock()
        completedSessions = saved.completedSessions.coerceAtLeast(0)
        writer = write
    }
    private fun cleanSignature(value: String): String {
        val cut = value.filter { it >= ' ' && it != '\u007f' }.take(8)
        val last = cut.lastOrNull()
        return if(last != null && last in '\uD800'..'\uDBFF') cut.dropLast(1) else cut
    }
    private fun save() { writer?.invoke(codec.encodeToString(NothingSavedState(preferences, focus, completedSessions))) }
    fun configure(value: NothingPreferences) {
        val durationChanged = value.focusMinutes != preferences.focusMinutes
        preferences = value.copy(previewBrightness = value.previewBrightness.coerceIn(.25f, 1f), focusMinutes = value.focusMinutes.coerceIn(1, 90), signature = cleanSignature(value.signature))
        if (durationChanged && !focus.running) focus = NothingFocusSession.fresh(preferences.focusMinutes)
        save()
    }
    fun toggleFocus(nowMillis: Long) {
        this.nowMillis = nowMillis
        if (completeIfDue(nowMillis)) return
        focus = when {
            focus.running -> focus.pause(nowMillis)
            focus.completed || focus.pausedSeconds == 0 -> NothingFocusSession.fresh(preferences.focusMinutes).start(nowMillis)
            else -> focus.start(nowMillis)
        }
        save()
    }
    fun resetFocus() { focus = NothingFocusSession.fresh(preferences.focusMinutes); save() }
    /** True exactly once per completed session; callers can trigger one completion cue. */
    fun completeIfDue(nowMillis: Long): Boolean {
        if (!focus.running || focus.remaining(nowMillis) > 0) return false
        focus = focus.copy(deadlineMillis = null, pausedSeconds = 0, completed = true)
        completedSessions++
        save()
        return true
    }
}
