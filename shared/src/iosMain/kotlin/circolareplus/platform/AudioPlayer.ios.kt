package circolareplus.platform

import androidx.compose.runtime.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioPlayer(bytes: ByteArray?, mime: String): AudioPlayerState {
    var isPlaying by remember(bytes) { mutableStateOf(false) }
    var positionMs by remember(bytes) { mutableStateOf(0) }
    var durationMs by remember(bytes) { mutableStateOf(0) }
    var error by remember(bytes) { mutableStateOf<String?>(null) }
    var player by remember(bytes) { mutableStateOf<AVAudioPlayer?>(null) }

    fun releasePlayer() {
        player?.stop()
        player = null
        isPlaying = false
        positionMs = 0
    }

    DisposableEffect(bytes) {
        onDispose { releasePlayer() }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            val p = player ?: break
            if (!p.playing) {
                isPlaying = false
                positionMs = 0
                break
            }
            positionMs = (p.currentTime * 1000.0).toInt().coerceAtLeast(0)
            durationMs = (p.duration * 1000.0).toInt().coerceAtLeast(0)
            delay(200)
        }
    }

    fun ensurePrepared(): AVAudioPlayer? {
        player?.let { return it }
        val data = bytes ?: return null
        return runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
            val nsData = data.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
            }
            val av = AVAudioPlayer(data = nsData, error = null)
            av.prepareToPlay()
            durationMs = (av.duration * 1000.0).toInt().coerceAtLeast(0)
            player = av
            av
        }.getOrElse {
            error = "Audio non riproducibile."
            null
        }
    }

    return AudioPlayerState(
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        error = error,
        togglePlayPause = {
            error = null
            val p = ensurePrepared()
            if (p != null) {
                if (isPlaying) {
                    p.pause()
                    isPlaying = false
                } else {
                    if (p.play()) isPlaying = true else error = "Impossibile avviare la riproduzione."
                }
            }
        },
        stop = {
            player?.stop()
            player?.currentTime = 0.0
            isPlaying = false
            positionMs = 0
        }
    )
}
