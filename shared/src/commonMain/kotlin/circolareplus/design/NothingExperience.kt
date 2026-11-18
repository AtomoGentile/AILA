package circolareplus.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NothingPreferences(
    val dotDisplay: Boolean = true,
    @SerialName("showFocusWidget") val showCalendarGlyph: Boolean = true,
    val conflictLights: Boolean = false,
    val systemNotifications: Boolean = false
)

@Serializable
private data class NothingSavedState(val preferences: NothingPreferences = NothingPreferences())

object NothingExperience {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    var preferences by mutableStateOf(NothingPreferences())
        private set
    private var writer: ((String) -> Unit)? = null

    fun readPreferences(json: String): NothingPreferences = runCatching {
        codec.decodeFromString<NothingSavedState>(json).preferences
    }.getOrDefault(NothingPreferences())

    fun restore(json: String, write: (String) -> Unit) {
        preferences = readPreferences(json)
        writer = write
    }

    fun configure(value: NothingPreferences) {
        preferences = value
        writer?.invoke(codec.encodeToString(NothingSavedState(value)))
    }
}
