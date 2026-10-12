package circolareplus.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Cartella privata nella cache dell'app, esposta al FileProvider da res/xml/file_paths.xml
// ("updates"): l'installatore di sistema deve poter leggere l'APK, ma nient'altro.
private const val UPDATES_DIR = "updates"
private const val APK_FILE = "AILA-update.apk"
private const val APK_MIME = "application/vnd.android.package-archive"

private fun updatesDir(): File = File(AndroidAppContext.require().cacheDir, UPDATES_DIR).apply { mkdirs() }

private fun apkFile(): File = File(updatesDir(), APK_FILE)

actual fun isInAppUpdateSupported(): Boolean = AndroidAppContext.getOrNull() != null

actual fun canInstallPackages(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        AndroidAppContext.require().packageManager.canRequestPackageInstalls()

actual fun openInstallPermissionSettings() {
    val context = AndroidAppContext.require()
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        // Context dell'applicazione, non di un'Activity: senza NEW_TASK startActivity lancia.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

actual suspend fun downloadUpdateApk(
    url: String,
    expectedSize: Long,
    sha256: String?,
    onProgress: (downloaded: Long, total: Long) -> Unit
): Unit = withContext(Dispatchers.IO) {
    val target = apkFile()
    val partial = File(target.parentFile, "$APK_FILE.part")
    target.delete()
    partial.delete()

    var connection: HttpURLConnection? = null
    try {
        // GitHub risponde con un redirect verso il suo CDN (https -> https): HttpURLConnection lo
        // segue da solo.
        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AILA-Android")
        }
        val code = connection.responseCode
        if (code !in 200..299) error("Il server ha risposto $code")
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
        val digest = if (sha256 != null) MessageDigest.getInstance("SHA-256") else null

        var downloaded = 0L
        var lastReported = 0L
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    downloaded += read
                    // Non a ogni blocco da 64 KB: per un APK da un centinaio di MB sarebbero
                    // migliaia di ridisegni. Un aggiornamento ogni ~256 KB basta a una barra fluida.
                    if (downloaded - lastReported >= 256 * 1024) {
                        lastReported = downloaded
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        onProgress(downloaded, total)

        if (expectedSize > 0 && downloaded != expectedSize) {
            error("Download incompleto ($downloaded di $expectedSize byte)")
        }
        if (digest != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(sha256, ignoreCase = true)) error("Il file scaricato non corrisponde a quello pubblicato")
        }
        if (!partial.renameTo(target)) error("Impossibile salvare il file scaricato")
    } catch (e: Exception) {
        partial.delete()
        target.delete()
        throw e
    } finally {
        connection?.disconnect()
    }
}

actual fun isUpdateApkDownloaded(expectedSize: Long): Boolean {
    val file = apkFile()
    return file.isFile && file.length() > 0 && (expectedSize <= 0 || file.length() == expectedSize)
}

actual fun installDownloadedApk(): Boolean {
    val context = AndroidAppContext.require()
    val file = apkFile()
    if (!file.isFile) return false
    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

actual fun clearDownloadedUpdate() {
    try {
        File(AndroidAppContext.require().cacheDir, UPDATES_DIR).deleteRecursively()
    } catch (e: Exception) {
        // Cache gia' sparita o non scrivibile: nessuna conseguenza.
    }
}
