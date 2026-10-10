package circolareplus.platform

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
actual fun rememberPdfFilePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Un PDF può arrivare a 100 MB: la lettura non blocca la schermata.
            val picked = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                bytes?.let { (name ?: "documento.pdf") to it }
            }
            picked?.let { (name, bytes) -> currentOnPicked(name, bytes) }
        }
    }
    return remember(launcher) { { launcher.launch(arrayOf("application/pdf")) } }
}
