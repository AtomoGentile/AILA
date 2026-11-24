package circolareplus.platform

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.io.File

@Composable
actual fun rememberAudioPlayer(bytes: ByteArray?, mime: String): AudioPlayerState {
    val context = LocalContext.current
    var isPlaying by remember(bytes) { mutableStateOf(false) }
    var positionMs by remember(bytes) { mutableStateOf(0) }
    var durationMs by remember(bytes) { mutableStateOf(0) }
    var error by remember(bytes) { mutableStateOf<String?>(null) }
    var player by remember(bytes) { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember(bytes) { mutableStateOf<File?>(null) }

    fun releasePlayer() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        isPlaying = false
        positionMs = 0
        tempFile?.delete()
        tempFile = null
    }

    DisposableEffect(bytes) {
        onDispose { releasePlayer() }
    }

    LaunchedEffect(isPlaying, player) {
        while (isPlaying) {
            val mp = player ?: break
            val playingNow = runCatching { mp.isPlaying }.getOrDefault(false)
            if (!playingNow) {
                isPlaying = false
                break
            }
            positionMs = runCatching { mp.currentPosition }.getOrDefault(positionMs)
            durationMs = runCatching { mp.duration }.getOrDefault(durationMs).coerceAtLeast(0)
            delay(200)
        }
    }

    fun ensurePrepared(): MediaPlayer? {
        player?.let { return it }
        val data = bytes ?: return null
        return try {
            val ext = when {
                mime.contains("wav") -> ".wav"
                mime.contains("ogg") -> ".ogg"
                mime.contains("mpeg") || mime.contains("mp3") -> ".mp3"
                else -> ".m4a"
            }
            val file = File.createTempFile("aila-play-", ext, context.cacheDir).apply { writeBytes(data) }
            tempFile = file
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                durationMs = duration.coerceAtLeast(0)
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                    runCatching { seekTo(0) }
                }
                setOnErrorListener { _, _, _ ->
                    error = "Impossibile riprodurre l'audio."
                    releasePlayer()
                    true
                }
                player = this
            }
        } catch (e: Exception) {
            error = "Audio non riproducibile."
            releasePlayer()
            null
        }
    }

    val toggle: () -> Unit = {
        error = null
        val mp = ensurePrepared()
        if (mp != null) {
            if (isPlaying) {
                runCatching { mp.pause() }
                isPlaying = false
            } else {
                runCatching {
                    mp.start()
                    isPlaying = true
                }.onFailure {
                    error = "Impossibile avviare la riproduzione."
                    releasePlayer()
                }
            }
        }
    }

    val stop: () -> Unit = {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.pause(); mp.seekTo(0) }
        }
        isPlaying = false
        positionMs = 0
    }

    return AudioPlayerState(
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        error = error,
        togglePlayPause = toggle,
        stop = stop
    )
}
