@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package circolareplus.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual object OfflineStore {
    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: NSTemporaryDirectory()
        val path = "$base/aila-offline"
        ensureDir(path)
        path
    }

    private fun ensureDir(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    actual fun readBytes(name: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile("$dir/$name") ?: return null
        val size = data.length.toInt()
        if (size == 0) return ByteArray(0)
        val out = ByteArray(size)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        return out
    }

    actual fun writeBytes(name: String, bytes: ByteArray) {
        ensureDir(dir)
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert()) }
        }
        // atomically = true: iOS scrive su un file temporaneo e poi lo sostituisce.
        data.writeToFile("$dir/$name", atomically = true)
    }

    actual fun exists(name: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath("$dir/$name")

    actual fun totalBytes(): Long {
        val manager = NSFileManager.defaultManager
        val names = manager.contentsOfDirectoryAtPath(dir, error = null) ?: return 0L
        return names.sumOf { name ->
            val attributes = manager.attributesOfItemAtPath("$dir/$name", error = null)
            (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        }
    }

    actual fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(dir, error = null)
        ensureDir(dir)
    }
}
