package circolareplus.platform

import java.io.File

actual object OfflineStore {
    private fun dir(): File? {
        val context = AndroidAppContext.getOrNull() ?: return null
        return File(context.filesDir, "offline").apply { mkdirs() }
    }

    actual fun readBytes(name: String): ByteArray? = try {
        dir()?.let { File(it, name) }?.takeIf { it.isFile }?.readBytes()
    } catch (e: Exception) {
        null
    }

    actual fun writeBytes(name: String, bytes: ByteArray) {
        try {
            val folder = dir() ?: return
            // Prima su un file temporaneo e poi rinomina: un'interruzione a meta' non lascia
            // mai una copia troncata al posto di quella buona.
            val tmp = File(folder, "$name.tmp")
            tmp.writeBytes(bytes)
            val target = File(folder, name)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (e: Exception) {
            // Disco pieno o simili: la copia offline e' un di piu', non deve rompere nulla.
        }
    }

    actual fun exists(name: String): Boolean = dir()?.let { File(it, name).isFile } ?: false

    actual fun totalBytes(): Long = dir()?.listFiles()?.sumOf { it.length() } ?: 0L

    actual fun clear() {
        dir()?.listFiles()?.forEach { it.delete() }
    }
}
