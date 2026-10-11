package circolareplus.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

actual suspend fun sharePdfFile(bytes: ByteArray, fileName: String) {
    val context = AndroidAppContext.require()
    val file = withContext(Dispatchers.IO) {
        File(File(context.cacheDir, "gita").apply { mkdirs() }, fileName).apply { writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Come per la mappa posti: il contesto è dell'applicazione, non di un'Activity.
    val chooser = Intent.createChooser(intent, "Apri il documento").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

@Composable
actual fun rememberPdfFilePicker(
    onError: (message: String) -> Unit,
    onPicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Un PDF può arrivare a 100 MB: la lettura non blocca la schermata. Ogni errore (file
            // non più raggiungibile, permesso negato, memoria finita) diventa un messaggio: lasciato
            // uscire dalla coroutine chiuderebbe l'app.
            val result = withContext(Dispatchers.IO) {
                try {
                    readPickedPdf(context, uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    PickResult.Failed(
                        if (e is OutOfMemoryError) GITA_PDF_TOO_BIG_MESSAGE else GITA_PDF_UNREADABLE_MESSAGE
                    )
                }
            }
            when (result) {
                is PickResult.Picked -> currentOnPicked(result.name, result.bytes)
                is PickResult.Failed -> currentOnError(result.message)
            }
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}

private sealed interface PickResult {
    class Picked(val name: String, val bytes: ByteArray) : PickResult
    class Failed(val message: String) : PickResult
}

private fun readPickedPdf(context: Context, uri: Uri): PickResult {
    val resolver = context.contentResolver
    var name: String? = null
    var size: Long? = null
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    // Il controllo sulla dimensione dichiarata evita di caricare in memoria un file che il server
    // rifiuterebbe comunque.
    if ((size ?: 0L) > GITA_MAX_PDF_BYTES) return PickResult.Failed(GITA_PDF_TOO_BIG_MESSAGE)
    val input = resolver.openInputStream(uri) ?: return PickResult.Failed(GITA_PDF_UNREADABLE_MESSAGE)
    val bytes = input.use { it.readBounded(GITA_MAX_PDF_BYTES) } ?: return PickResult.Failed(GITA_PDF_TOO_BIG_MESSAGE)
    if (bytes.isEmpty()) return PickResult.Failed(GITA_PDF_UNREADABLE_MESSAGE)
    return PickResult.Picked(name ?: "documento.pdf", bytes)
}

/** Legge tutto il flusso; se supera [limit] byte si ferma e restituisce null, senza riempire la memoria. */
private fun InputStream.readBounded(limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
