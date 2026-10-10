package circolareplus.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual suspend fun sharePdfFile(bytes: ByteArray, fileName: String) {
    val bridge = GitaFileBridgeHolder.bridge ?: error("Condivisione non disponibile su questo dispositivo")
    val path = NSTemporaryDirectory() + fileName
    bytes.toNSData().writeToFile(path, atomically = true)
    val error = withContext(Dispatchers.Main) { bridge.shareFile(path) }
    if (error != null) throw IllegalStateException(error)
}

@Composable
actual fun rememberPdfFilePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    return remember {
        {
            GitaFileBridgeHolder.bridge?.pickPdf { path ->
                if (path != null) {
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) { readFile(path) }
                        if (bytes != null) currentOnPicked(path.substringAfterLast('/'), bytes)
                    }
                }
            }
        }
    }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun readFile(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return ByteArray(data.length.toInt()).also { out ->
        out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
}
