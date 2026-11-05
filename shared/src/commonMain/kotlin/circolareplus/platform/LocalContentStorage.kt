package circolareplus.platform

/** User-kept files, separate from disposable/offline caches. No expiration or automatic eviction. */
interface LocalContentStorage {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun remove(key: String)
    suspend fun keys(): List<String>
}
expect fun createLocalContentStorage(): LocalContentStorage
