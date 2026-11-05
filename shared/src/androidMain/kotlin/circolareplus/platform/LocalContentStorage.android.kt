package circolareplus.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun createLocalContentStorage(): LocalContentStorage = object : LocalContentStorage {
    private fun folder() = File(AndroidAppContext.require().filesDir, "saved-content").apply { mkdirs() }
    private fun file(key: String): File {
        require(key.matches(Regex("[A-Za-z0-9_.-]{1,240}")))
        return File(folder(), key)
    }
    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        file(key).takeIf { it.isFile }?.readBytes()
    }
    override suspend fun write(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(key)
        val temporary = File.createTempFile("saved-", ".tmp", folder())
        try {
            temporary.writeBytes(bytes)
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            Unit
        } finally { temporary.delete() }
    }
    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        val target = file(key)
        check(!target.exists() || target.delete()) { "Non riesco a eliminare la copia dal dispositivo." }
    }
    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        folder().list().orEmpty().filterNot { it.endsWith(".tmp") }
    }
}
