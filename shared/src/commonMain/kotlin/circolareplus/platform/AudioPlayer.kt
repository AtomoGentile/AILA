package circolareplus.platform

import androidx.compose.runtime.Composable

data class AudioPlayerState(
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val error: String?,
    val togglePlayPause: () -> Unit,
    val stop: () -> Unit
)

/** Riproduttore audio in-app multipiattaforma per i messaggi vocali. */
@Composable
expect fun rememberAudioPlayer(bytes: ByteArray?, mime: String): AudioPlayerState
