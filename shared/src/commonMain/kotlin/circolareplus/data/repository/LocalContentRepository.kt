package circolareplus.data.repository

import androidx.compose.runtime.*
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.dto.*
import circolareplus.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class LocalContentEntry(
    val id: String, val ownerId: String, val fileName: String, val mime: String,
    val bytes: Long, val savedAt: Long, val remoteIds: List<String> = emptyList(),
    val serverExpiresAt: String? = null, val extractedText: String? = null,
    val transcript: String? = null, val transcriptGeneratedAt: String? = null
)
@Serializable
private data class LocalDerivedContent(val extractedText:String?=null,val transcript:String?=null)
@Serializable
data class LocalContentPreferences(val automaticDownload: Boolean = false, val cursor: String = "", val classId: String = "")

/** Durable user copies. Server expiry, logout and temporary-cache cleanup do not delete them. */
class LocalContentRepository(
    private val storage: LocalContentStorage,
    private val currentUserId: () -> String
) {
    private val lock = Mutex()
    private val synchronization = Mutex()
    // Chat attachments live only in this process; explicit user saves still use storage.
    private val temporary = linkedMapOf<String, Pair<LocalContentEntry, IncomingContent>>()
    suspend fun saveTemporary(file: IncomingContent): LocalContentEntry = lock.withLock {
        val owner = owner()
        require(file.bytes.isNotEmpty() && file.bytes.size <= CONTENT_MAX_BYTES) { "File non valido: massimo 20 MB." }
        temporary.entries.removeAll { it.value.first.ownerId != owner }
        val id = file.localId?.takeIf { temporary[it]?.first?.ownerId == owner }
            ?: "temp-${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}"
        val entry = LocalContentEntry(id, owner, file.fileName, file.mime, file.bytes.size.toLong(), currentTimeMillis(),
            extractedText = file.extractedText.takeUnless { file.isTranscript },
            transcript = file.extractedText.takeIf { file.isTranscript })
        temporary.remove(id)
        temporary[id] = entry to file.copy(localId = id)
        // Keep at most five originals, bounded to 40 MB, without accumulating phone files.
        while (temporary.size > 5) temporary.remove(temporary.keys.first())
        while (temporary.values.sumOf { it.second.bytes.size.toLong() } > 40L * 1024 * 1024) {
            val oldest = temporary.entries.first { it.value.second.bytes.isNotEmpty() }
            temporary[oldest.key] = oldest.value.first to oldest.value.second.copy(bytes=byteArrayOf())
        }
        entry
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val metadataCache=mutableMapOf<String,List<LocalContentEntry>>()
    private val pendingPreferences=mutableMapOf<String,LocalContentPreferences>()
    var revision by mutableIntStateOf(0); private set
    var automaticOwner by mutableStateOf(""); private set
    var automaticDownload by mutableStateOf(false); private set

    private fun owner(): String = currentUserId().also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Accedi per aprire i tuoi file sul dispositivo." }
    }
    private fun key(owner: String, id: String, suffix: String): String {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        return "${owner.length}-${owner}_${id}.$suffix"
    }
    private suspend fun entries(owner: String): List<LocalContentEntry> = metadataCache[owner] ?: storage.keys()
        .filter { it.startsWith("${owner.length}-${owner}_") && it.endsWith(".meta") }
        .mapNotNull { name -> storage.read(name)?.let { bytes ->
            runCatching { json.decodeFromString<LocalContentEntry>(bytes.decodeToString()) }.getOrNull()?.takeIf { it.ownerId == owner }
        } }.also{metadataCache[owner]=it}
    suspend fun list(): List<LocalContentEntry> = lock.withLock { entries(owner()).sortedByDescending { it.savedAt } }
    suspend fun findByRemote(remoteId: String): LocalContentEntry? = lock.withLock { entries(owner()).firstOrNull { remoteId in it.remoteIds } }
    suspend fun entry(id: String): LocalContentEntry? {
        val owner = owner()
        temporary[id]?.takeIf { it.first.ownerId == owner }?.let { return it.first }
        return storage.read(key(owner, id, "meta"))?.let { json.decodeFromString<LocalContentEntry>(it.decodeToString()) }
            ?.takeIf { it.ownerId == owner }
    }
    suspend fun read(entry: LocalContentEntry): IncomingContent = lock.withLock {
        require(entry.ownerId == owner()) { "Questo file appartiene a un altro account." }
        temporary[entry.id]?.takeIf { it.first.ownerId == owner() }?.let {
            check(it.second.bytes.isNotEmpty()) { "L’originale temporaneo non è più disponibile. Allega nuovamente il file." }
            return@withLock it.second
        }
        val bytes = storage.read(key(entry.ownerId, entry.id, "data")) ?: error("La copia sul dispositivo non è disponibile.")
        val derived=storage.read(key(entry.ownerId,entry.id,"derived"))?.let{json.decodeFromString<LocalDerivedContent>(it.decodeToString())}
        val transcript=derived?.transcript?:entry.transcript
        require(entry.ownerId == owner())
        IncomingContent(entry.fileName, entry.mime, bytes, transcript ?: derived?.extractedText ?: entry.extractedText,
            localId = entry.id, isTranscript = transcript != null, generatedAt = entry.transcriptGeneratedAt)
    }
    suspend fun text(entry: LocalContentEntry): String? = lock.withLock {
        require(entry.ownerId == owner())
        temporary[entry.id]?.takeIf { it.first.ownerId == owner() }?.let { return@withLock it.second.extractedText }
        val derived=storage.read(key(entry.ownerId,entry.id,"derived"))?.let{json.decodeFromString<LocalDerivedContent>(it.decodeToString())}
        require(entry.ownerId == owner())
        derived?.transcript?:derived?.extractedText?:entry.transcript?:entry.extractedText
    }
    suspend fun save(file: IncomingContent, remote: ContentAttachmentDto? = null): LocalContentEntry = lock.withLock {
        val owner = owner()
        val previous = file.localId?.let { id ->
            storage.read(key(owner, id, "meta"))?.let { json.decodeFromString<LocalContentEntry>(it.decodeToString()) }
                ?.takeIf { it.ownerId == owner }
        } ?: remote?.let { r -> entries(owner).firstOrNull { r.id in it.remoteIds } }
        persist(owner, file, remote, previous, manual = true)
    }
    private suspend fun persist(owner: String, file: IncomingContent, remote: ContentAttachmentDto?, previous: LocalContentEntry?, manual: Boolean): LocalContentEntry = withContext(NonCancellable) {
        val previousEntries=entries(owner)
        require(file.bytes.isNotEmpty() && file.bytes.size <= CONTENT_MAX_BYTES) { "File non valido: massimo 20 MB." }
        val id = previous?.id ?: "${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}"
        val priorDerived=previous?.let{storage.read(key(owner,it.id,"derived"))}?.let{json.decodeFromString<LocalDerivedContent>(it.decodeToString())}
        val value = LocalContentEntry(id, owner, file.fileName, file.mime, file.bytes.size.toLong(), previous?.savedAt ?: currentTimeMillis(),
            (previous?.remoteIds.orEmpty() + listOfNotNull(remote?.id)).distinct(), remote?.expiresAt ?: previous?.serverExpiresAt,
            if (file.isTranscript) priorDerived?.extractedText?:previous?.extractedText else file.extractedText ?: priorDerived?.extractedText ?: previous?.extractedText,
            remote?.transcript ?: if (file.isTranscript) file.extractedText else priorDerived?.transcript ?: previous?.transcript,
            remote?.transcriptGeneratedAt ?: file.generatedAt ?: previous?.transcriptGeneratedAt)
        storage.write(key(owner, id, "data"), file.bytes)
        try {
            storage.write(key(owner,id,"derived"),json.encodeToString(LocalDerivedContent(value.extractedText,value.transcript)).encodeToByteArray())
            storage.write(key(owner, id, "meta"), json.encodeToString(value.copy(extractedText=null,transcript=null)).encodeToByteArray())
        }
        catch (e: Exception) { if (previous == null) { storage.remove(key(owner, id, "data"));storage.remove(key(owner,id,"derived")) }; throw e }
        if (manual) value.remoteIds.forEach { storage.remove(key(owner, it, "removed")) }
        metadataCache[owner]=(previousEntries.filterNot{it.id==id}+value.copy(extractedText=null,transcript=null))
        revision++
        value
    }
    suspend fun remove(entry: LocalContentEntry) = lock.withLock {
        val owner = owner(); require(entry.ownerId == owner)
        withContext(NonCancellable) {
            val before=entries(owner)
            // A confirmed deletion completes even when the user leaves the screen.
            entry.remoteIds.forEach { storage.write(key(owner, it, "removed"), byteArrayOf(1)) }
            storage.remove(key(owner, entry.id, "data"))
            storage.remove(key(owner, entry.id, "derived"))
            storage.remove(key(owner, entry.id, "meta"))
            metadataCache[owner]=before.filterNot{it.id==entry.id}
            revision++
        }
    }
    suspend fun removeAll() { list().forEach { remove(it) } }
    private suspend fun readPreferences(owner:String):LocalContentPreferences = pendingPreferences[owner]
        ?:storage.read(key(owner,"preferences","json"))?.let{json.decodeFromString<LocalContentPreferences>(it.decodeToString())}
        ?:LocalContentPreferences()
    private fun updatePreferenceState(owner:String,value:LocalContentPreferences){
        if(currentUserId()==owner){automaticOwner=owner;automaticDownload=value.automaticDownload}
    }
    suspend fun preferences():LocalContentPreferences=lock.withLock{
        val owner=owner();readPreferences(owner).also{updatePreferenceState(owner,it)}
    }
    suspend fun savePreferences(value:LocalContentPreferences){
        val owner=owner()
        updatePreferenceState(owner,value)
        lock.withLock{
            val next=readPreferences(owner).copy(automaticDownload=value.automaticDownload)
            pendingPreferences[owner]=next
            storage.write(key(owner,"preferences","json"),json.encodeToString(next).encodeToByteArray())
            pendingPreferences.remove(owner)
        }
    }
    private suspend fun advance(owner:String,cursor:String,classId:String):Boolean=lock.withLock{
        val current=readPreferences(owner)
        if(currentUserId()!=owner||!automaticDownload||!current.automaticDownload)return@withLock false
        val next=current.copy(cursor=cursor,classId=classId)
        storage.write(key(owner,"preferences","json"),json.encodeToString(next).encodeToByteArray())
        pendingPreferences.remove(owner)
        true
    }
    /** Bounded synchronization, no inference and no automatic upload. */
    suspend fun synchronize(
        inbox: suspend (String) -> ContentInboxDto,
        download: suspend (String) -> ByteArray
    ) {
        if(!synchronization.tryLock())return
        try{synchronizePass(inbox,download)}finally{synchronization.unlock()}
    }
    private suspend fun synchronizePass(inbox:suspend(String)->ContentInboxDto,download:suspend(String)->ByteArray){
        val owner = owner()
        var pref = preferences()
        if (!pref.automaticDownload) return
        var processed = 0
        while (processed < 10 && currentUserId() == owner && automaticDownload) {
            val page = inbox(pref.cursor)
            if (currentUserId() != owner || !automaticDownload) return
            if (pref.classId != page.classId) {
                pref = pref.copy(cursor = "", classId = page.classId)
                if(!advance(owner,pref.cursor,pref.classId))return
                continue
            }
            for (delivery in page.attachments) {
                if (currentUserId() != owner || !automaticDownload) return
                val item = delivery.attachment
                val removedKey = key(owner, item.id, "removed")
                if (storage.read(removedKey) == null && findByRemote(item.id) == null && !item.isExpired()) {
                    val bytes = try { download(item.id) }
                    catch (e: CancellationException) { throw e }
                    catch (e: ApiException) { if (e.statusCode == 404 || e.statusCode == 410) null else throw e }
                    if (bytes != null) lock.withLock {
                        if (currentUserId() == owner && automaticDownload && storage.read(removedKey) == null && entries(owner).none{item.id in it.remoteIds}) {
                            persist(owner, IncomingContent(item.fileName, item.mime, bytes), item, null, manual = false)
                        }
                    }
                }
                if (currentUserId() != owner || !automaticDownload) return
                pref = pref.copy(cursor = delivery.cursor)
                if(!advance(owner,pref.cursor,pref.classId))return
                if (++processed >= 10) return
            }
            if (!page.hasMore || page.attachments.isEmpty()) return
        }
    }
}
