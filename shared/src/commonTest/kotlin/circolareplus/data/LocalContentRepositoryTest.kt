package circolareplus.data

import circolareplus.data.remote.dto.*
import circolareplus.data.repository.*
import circolareplus.platform.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalContentRepositoryTest {
    private class MemoryStorage : LocalContentStorage {
        val data = mutableMapOf<String, ByteArray>()
        var failWrites = false
        override suspend fun read(key: String) = data[key]?.copyOf()
        override suspend fun write(key: String, bytes: ByteArray) { check(!failWrites) { "Disco pieno" }; data[key] = bytes.copyOf() }
        override suspend fun remove(key: String) { data.remove(key) }
        override suspend fun keys() = data.keys.toList()
    }
    private val file = IncomingContent("Appunti.txt", "text/plain", "Appunti conservati".encodeToByteArray(), "Appunti conservati")
    private fun remote(id: String = "r1", expired: Boolean = false) = ContentAttachmentDto(id, file.fileName, file.mime,
        file.bytes.size.toLong(), if (expired) "2000-01-01T00:00:00Z" else "2099-01-01T00:00:00Z", expired = expired)
    private fun page(item: ContentAttachmentDto = remote()) = ContentInboxDto(listOf(ContentDeliveryDto(item, "1:1")), false, "C1")

    @Test fun localCopiesSurviveServerExpiryRestartAndLogout() = runTest {
        val store = MemoryStorage(); var owner = "u1"
        val repo = LocalContentRepository(store) { owner }
        val entry = repo.save(file, remote(expired = true))
        owner = ""
        assertFailsWith<IllegalArgumentException> { repo.list() }
        owner = "u1"
        val restored = LocalContentRepository(store) { owner }
        assertContentEquals(file.bytes, restored.read(restored.list().single()).bytes)
        assertEquals("Appunti conservati", restored.read(entry).extractedText)
        assertTrue(remote(expired = true).isExpired())
    }
    @Test fun anotherAccountCannotListOrReadTheSavedCopies() = runTest {
        val store = MemoryStorage(); var owner = "u1"
        val repo = LocalContentRepository(store) { owner }; val entry = repo.save(file)
        owner = "u2"
        assertTrue(repo.list().isEmpty())
        assertFailsWith<IllegalArgumentException> { repo.read(entry) }
        owner = "u1"; assertEquals(1, repo.list().size)
    }
    @Test fun accountScopeCannotCollideWithAnIdentifierPrefix() = runTest {
        val store=MemoryStorage();var owner="u_a";val repo=LocalContentRepository(store){owner}
        val first=repo.save(file)
        owner="u"
        assertFailsWith<IllegalStateException>{repo.read(first.copy(ownerId="u",id="a_${first.id}"))}
        assertTrue(repo.list().isEmpty())
    }
    @Test fun manualDeletionRemovesTheOriginalAndDerivedTextOnlyFromTheDevice() = runTest {
        val store = MemoryStorage(); val repo = LocalContentRepository(store) { "u1" }
        val entry = repo.save(file, remote())
        repo.remove(entry)
        assertTrue(repo.list().isEmpty()); assertNull(repo.findByRemote("r1"))
        assertFalse(store.data.keys.any { it.endsWith(".data") || it.endsWith(".derived") || it.endsWith(".meta") })
        assertTrue(store.data.keys.any { it.endsWith(".removed") })
    }
    @Test fun automaticDownloadIsOffUntilChosenInSettings() = runTest {
        val repo = LocalContentRepository(MemoryStorage()) { "u1" }
        var requests = 0
        repo.synchronize({ requests++; page() }, { requests++; file.bytes })
        assertEquals(0, requests); assertFalse(repo.preferences().automaticDownload)
    }
    @Test fun enabledDownloadsPersistAndManualDeletionIsNeverUndoneByAReplay() = runTest {
        val store = MemoryStorage(); val repo = LocalContentRepository(store) { "u1" }
        repo.savePreferences(LocalContentPreferences(automaticDownload = true))
        var downloads = 0
        repo.synchronize({ page() }, { downloads++; file.bytes })
        assertEquals(1, downloads); val saved = repo.list().single()
        assertEquals("1:1", repo.preferences().cursor)
        repo.remove(saved)
        repo.synchronize({ page() }, { downloads++; file.bytes })
        assertEquals(1, downloads); assertTrue(repo.list().isEmpty())
    }
    @Test fun turningOffAutomaticDownloadWhileARequestRunsPreventsSavingAndReactivation() = runTest {
        val repo = LocalContentRepository(MemoryStorage()) { "u1" }
        repo.savePreferences(LocalContentPreferences(automaticDownload = true))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<ByteArray>()
        val job = async { repo.synchronize({ page() }, { entered.complete(Unit); release.await() }) }
        entered.await(); repo.savePreferences(LocalContentPreferences(automaticDownload = false)); release.complete(file.bytes); job.await()
        assertTrue(repo.list().isEmpty()); assertFalse(repo.preferences().automaticDownload)
    }
    @Test fun deletingAManuallySavedCopyDuringAnAutomaticRequestPreventsResurrection() = runTest {
        val repo = LocalContentRepository(MemoryStorage()) { "u1" }
        repo.savePreferences(LocalContentPreferences(automaticDownload = true))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<ByteArray>()
        val job = async { repo.synchronize({ page() }, { entered.complete(Unit); release.await() }) }
        entered.await(); repo.remove(repo.save(file, remote())); release.complete(file.bytes); job.await()
        assertTrue(repo.list().isEmpty())
    }
    @Test fun diskFailureIsReportedAndAnUnsavedOffChoiceStaysOffInMemory() = runTest {
        val store = MemoryStorage(); val repo = LocalContentRepository(store) { "u1" }
        repo.savePreferences(LocalContentPreferences(automaticDownload = true)); store.failWrites = true
        assertFailsWith<IllegalStateException> { repo.save(file) }
        assertFailsWith<IllegalStateException> { repo.savePreferences(LocalContentPreferences(automaticDownload = false)) }
        assertFalse(repo.preferences().automaticDownload)
        var calls = 0; repo.synchronize({ calls++; page() }, { calls++; file.bytes }); assertEquals(0, calls)
    }
    @Test fun reuploadingKeepsOldForumLinksAndTheOriginalTranscript() = runTest {
        val store = MemoryStorage(); val repo = LocalContentRepository(store) { "u1" }
        val r1 = remote().copy(transcript = "Trascrizione originale", transcriptGeneratedAt = "2026-11-05T10:00:00Z")
        val first = repo.save(file, r1)
        repo.save(file.copy(localId = first.id), remote("r2"))
        assertEquals(1, repo.list().size); assertNotNull(repo.findByRemote("r1")); assertNotNull(repo.findByRemote("r2"))
        val restored = LocalContentRepository(store) { "u1" }
        val reopened = restored.read(restored.list().single())
        assertTrue(reopened.isTranscript); assertEquals("Trascrizione originale", reopened.extractedText)
    }
    @Test fun concurrentForegroundAndBackgroundSynchronizationDoNotDuplicateDownloads() = runTest {
        val repo = LocalContentRepository(MemoryStorage()) { "u1" }
        repo.savePreferences(LocalContentPreferences(automaticDownload = true))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<ByteArray>(); var downloads = 0
        val job = async { repo.synchronize({ page() }, { downloads++; entered.complete(Unit); release.await() }) }
        entered.await(); repo.synchronize({ page() }, { downloads++; file.bytes }); release.complete(file.bytes); job.await()
        assertEquals(1, downloads); assertEquals(1, repo.list().size)
    }
}
