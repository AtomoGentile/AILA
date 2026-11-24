package circolareplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.ContentAttachmentDto
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Cache in memoria per evitare riscaricamenti di immagini/vocali durante lo scorrimento. */
private object AttachmentMediaCache {
    private val bytesMap = LinkedHashMap<String, ByteArray>()
    private val bitmapMap = LinkedHashMap<String, ImageBitmap>()
    private const val MAX_ITEMS = 24

    fun getBytes(id: String): ByteArray? = bytesMap[id]
    fun putBytes(id: String, bytes: ByteArray) {
        if (bytesMap.size >= MAX_ITEMS && id !in bytesMap) {
            bytesMap.keys.firstOrNull()?.let { bytesMap.remove(it) }
        }
        bytesMap[id] = bytes
    }

    fun getBitmap(id: String): ImageBitmap? = bitmapMap[id]
    fun putBitmap(id: String, bitmap: ImageBitmap) {
        if (bitmapMap.size >= MAX_ITEMS && id !in bitmapMap) {
            bitmapMap.keys.firstOrNull()?.let { bitmapMap.remove(it) }
        }
        bitmapMap[id] = bitmap
    }
}

private suspend fun loadAttachmentBytes(item: ContentAttachmentDto): ByteArray {
    AttachmentMediaCache.getBytes(item.id)?.let { return it }
    val local = AppContainer.localContents.findByRemote(item.id)
    val bytes = if (local != null) {
        AppContainer.localContents.read(local).bytes
    } else {
        check(!item.isExpired()) { "Allegato scaduto sul server." }
        AppContainer.acaRepository.downloadFromServer(item.id)
    }
    AttachmentMediaCache.putBytes(item.id, bytes)
    return bytes
}

private fun formatDurationMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

private fun formatByteSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${((bytes * 10L) / (1024L * 1024L)) / 10.0} MB"
}

@Composable
internal fun InlineAttachmentView(
    item: ContentAttachmentDto,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    val isImage = item.mime.startsWith("image/", ignoreCase = true)
    val isAudio = item.mime.startsWith("audio/", ignoreCase = true)
    when {
        isImage -> InlineImageAttachment(item = item, enabled = enabled, onOpenExternal = onOpen)
        isAudio -> InlineAudioAttachment(item = item, enabled = enabled)
        else -> {
            val revision = AppContainer.localContents.revision
            var local by remember(item.id) { mutableStateOf<circolareplus.data.repository.LocalContentEntry?>(null) }
            LaunchedEffect(item.id, revision) {
                try { local = AppContainer.localContents.findByRemote(item.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {}
            }
            val canOpen = enabled && (!item.isExpired() || local != null)
            val sizeLabel = formatByteSize(item.bytes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppTheme.BackgroundLight)
                    .border(1.dp, AppTheme.Hairline, RoundedCornerShape(16.dp))
                    .clickable(enabled = canOpen) { onOpen() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AilaIconTile(tint = AppTheme.TintViolet, size = 34.dp) {
                    AppIcons.Document(Modifier.size(18.dp), AppTheme.TintVioletInk)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = local?.fileName ?: item.fileName,
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            local != null -> "Sul dispositivo${if (sizeLabel.isNotEmpty()) " · $sizeLabel" else ""}"
                            item.isExpired() -> "Copia sul server scaduta"
                            sizeLabel.isNotEmpty() -> "Allegato · $sizeLabel"
                            else -> "Tocca per aprire"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextMuted
                    )
                }
                if (canOpen) {
                    AppIcons.ChevronRight(Modifier.size(16.dp), AppTheme.TextFaint)
                }
            }
        }
    }
}

@Composable
private fun InlineImageAttachment(
    item: ContentAttachmentDto,
    enabled: Boolean,
    onOpenExternal: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var minimized by rememberSaveable(item.id) { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var bitmap by remember(item.id) { mutableStateOf(AttachmentMediaCache.getBitmap(item.id)) }
    var loading by remember(item.id) { mutableStateOf(false) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id, minimized) {
        if (minimized || bitmap != null || item.isExpired()) return@LaunchedEffect
        loading = true
        error = null
        try {
            val bytes = loadAttachmentBytes(item)
            val decoded = decodeImageBitmap(bytes)
            if (decoded != null) {
                AttachmentMediaCache.putBitmap(item.id, decoded)
                bitmap = decoded
            } else {
                error = "Anteprima immagine non supportata."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Impossibile caricare l'immagine."
        } finally {
            loading = false
        }
    }

    val sizeLabel = remember(item.bytes) { formatByteSize(item.bytes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.BackgroundLight)
            .border(1.dp, AppTheme.Hairline, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { minimized = !minimized }
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AilaIconTile(tint = AppTheme.TintBlue, size = 34.dp) {
                AppIcons.Photo(Modifier.size(18.dp), AppTheme.TintBlueInk)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(if (minimized) "Immagine ridotta" else "Foto allegata")
                        if (sizeLabel.isNotEmpty()) append(" · $sizeLabel")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppTheme.TintBlue)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (minimized) "Mostra" else "Riduci",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.TintBlueInk
                )
            }
        }

        AnimatedVisibility(visible = !minimized) {
            when {
                item.isExpired() && bitmap == null -> {
                    Text(
                        "Immagine scaduta sul server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextMuted
                    )
                }
                loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AilaCircularProgress(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Caricamento immagine…", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                }
                error != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk, modifier = Modifier.weight(1f))
                        AilaTextButton(onClick = onOpenExternal, enabled = enabled) { Text("Apri") }
                    }
                }
                bitmap != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.08f))
                            .clickable(enabled = enabled) { fullscreen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 230.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Ingrandisci",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (fullscreen && bitmap != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                val bytes = loadAttachmentBytes(item)
                                openContentFile(bytes, item.fileName, item.mime)
                            }
                        }
                    }) {
                        Text("Condividi", color = Color.White)
                    }
                    TextButton(onClick = { fullscreen = false }) {
                        Text("Chiudi", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineAudioAttachment(
    item: ContentAttachmentDto,
    enabled: Boolean
) {
    val scope = rememberCoroutineScope()
    var audioBytes by remember(item.id) { mutableStateOf(AttachmentMediaCache.getBytes(item.id)) }
    var loading by remember(item.id) { mutableStateOf(false) }
    var loadError by remember(item.id) { mutableStateOf<String?>(null) }
    var pendingAutoPlay by remember(item.id) { mutableStateOf(false) }
    var showTranscript by remember(item.id) { mutableStateOf(false) }

    val player = rememberAudioPlayer(audioBytes, item.mime)

    LaunchedEffect(audioBytes, pendingAutoPlay) {
        if (pendingAutoPlay && audioBytes != null) {
            pendingAutoPlay = false
            player.togglePlayPause()
        }
    }

    val totalMs = when {
        player.durationMs > 0 -> player.durationMs
        item.durationSeconds != null && item.durationSeconds > 0 -> (item.durationSeconds * 1000.0).toInt()
        else -> 0
    }
    val progress = if (totalMs > 0) (player.positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.BackgroundLight)
            .border(1.dp, AppTheme.Hairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (enabled && !item.isExpired()) AppTheme.PrimaryBlue else AppTheme.Hairline)
                    .clickable(enabled = enabled && !loading && (!item.isExpired() || audioBytes != null)) {
                        if (audioBytes != null) {
                            player.togglePlayPause()
                        } else {
                            loading = true
                            loadError = null
                            scope.launch {
                                try {
                                    audioBytes = loadAttachmentBytes(item)
                                    pendingAutoPlay = true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    loadError = e.message ?: "Impossibile caricare il vocale."
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> AilaCircularProgress(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    player.isPlaying -> PauseGlyph(Modifier.size(16.dp), Color.White)
                    else -> PlayGlyph(Modifier.size(16.dp), Color.White)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (item.isExpired() && audioBytes == null) "Vocale scaduto" else "Messaggio vocale",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.TextDark
                    )
                    Text(
                        text = if (totalMs > 0) "${formatDurationMs(player.positionMs)} / ${formatDurationMs(totalMs)}"
                        else if (player.isPlaying) formatDurationMs(player.positionMs) else "Tocca per ascoltare",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppTheme.Hairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(AppTheme.PrimaryBlue)
                    )
                }
            }
        }

        (loadError ?: player.error)?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk)
        }

        if (!item.transcript.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppTheme.TintSlate)
                    .clickable { showTranscript = !showTranscript }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (showTranscript) "Nascondi trascrizione" else "Mostra trascrizione",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.TextDark
                )
            }
            AnimatedVisibility(visible = showTranscript) {
                Text(
                    text = item.transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
            }
        }
    }
}

@Composable
private fun PlayGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.84f, h * 0.50f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
private fun PauseGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.22f
        drawLine(color, Offset(w * 0.32f, h * 0.22f), Offset(w * 0.32f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.22f), Offset(w * 0.68f, h * 0.78f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
