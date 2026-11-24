package circolareplus.platform

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberAudioPlayer(bytes: ByteArray?, mime: String): AudioPlayerState {
    var isPlaying by remember(bytes) { mutableStateOf(false) }
    var positionMs by remember(bytes) { mutableStateOf(0) }
    var durationMs by remember(bytes) { mutableStateOf(0) }
    var error by remember(bytes) { mutableStateOf<String?>(null) }
    var handle by remember(bytes) { mutableStateOf<JsAny?>(null) }

    DisposableEffect(bytes) {
        onDispose {
            handle?.let { webAudioDispose(it) }
            handle = null
        }
    }

    LaunchedEffect(isPlaying, handle) {
        while (isPlaying) {
            val h = handle ?: break
            val playingNow = webAudioIsPlaying(h)
            positionMs = webAudioPositionMs(h)
            durationMs = webAudioDurationMs(h)
            if (!playingNow) {
                isPlaying = false
                break
            }
            delay(200)
        }
    }

    fun ensureHandle(): JsAny? {
        handle?.let { return it }
        val data = bytes ?: return null
        val b64 = Base64.encode(data)
        return runCatching {
            webAudioCreate(b64, mime.ifBlank { "audio/mp4" }).also { handle = it }
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
            val h = ensureHandle()
            if (h != null) {
                if (isPlaying) {
                    webAudioPause(h)
                    isPlaying = false
                } else {
                    webAudioPlay(h)
                    isPlaying = true
                }
            }
        },
        stop = {
            handle?.let { webAudioStop(it) }
            isPlaying = false
            positionMs = 0
        }
    )
}

private fun webAudioCreate(base64: String, mime: String): JsAny = js(
    "(function(){ var bin = atob(base64); var arr = new Uint8Array(bin.length); " +
        "for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i); " +
        "var url = URL.createObjectURL(new Blob([arr], { type: mime })); " +
        "var audio = new Audio(url); return { audio: audio, url: url }; })()"
)

private fun webAudioPlay(h: JsAny): Unit = js("h.audio.play().catch(function(){})")
private fun webAudioPause(h: JsAny): Unit = js("h.audio.pause()")
private fun webAudioStop(h: JsAny): Unit = js("(function(){ h.audio.pause(); h.audio.currentTime = 0; })()")
private fun webAudioIsPlaying(h: JsAny): Boolean = js("(!h.audio.paused && !h.audio.ended)")
private fun webAudioPositionMs(h: JsAny): Int = js("Math.floor((h.audio.currentTime || 0) * 1000)")
private fun webAudioDurationMs(h: JsAny): Int = js("isFinite(h.audio.duration) ? Math.floor(h.audio.duration * 1000) : 0")
private fun webAudioDispose(h: JsAny): Unit = js("(function(){ h.audio.pause(); URL.revokeObjectURL(h.url); })()")
