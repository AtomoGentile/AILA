@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package circolareplus.platform

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy

actual fun createLocalContentStorage(): LocalContentStorage = object : LocalContentStorage {
    private val folder: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("Archivio del dispositivo non disponibile")
        "$base/aila-saved-content".also {
            check(NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)) { "Archivio del dispositivo non disponibile" }
        }
    }
    private fun path(key: String): String {
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,240}")))
        return "$folder/$key"
    }
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.Default) {
        val data = NSData.dataWithContentsOfFile(path(key)) ?: return@withContext null
        ByteArray(data.length.toInt()).also { out -> if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
    }
    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(data.writeToFile(path(key), true)) { "Spazio insufficiente o archivio non disponibile." }
    }
    override suspend fun remove(key: String) = withContext(Dispatchers.Default) {
        val target = path(key)
        check(!NSFileManager.defaultManager.fileExistsAtPath(target) || NSFileManager.defaultManager.removeItemAtPath(target, null)) { "Copia locale non eliminata: riprova." }
    }
    override suspend fun keys(): List<String> = withContext(Dispatchers.Default) {
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(folder, null)?.mapNotNull { it as? String }.orEmpty()
    }
}
