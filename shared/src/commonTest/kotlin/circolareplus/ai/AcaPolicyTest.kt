package circolareplus.ai

import circolareplus.data.remote.dto.AcaPreferencesDto
import circolareplus.platform.IncomingContentInbox
import circolareplus.platform.isExpired
import circolareplus.data.remote.dto.ContentAttachmentDto
import kotlin.test.*

class AcaPolicyTest {
 @Test fun attachmentPolicyFollowsEveryProviderSpecificPromptBuild(){
  val source=aiPromptBuilder(AiFeature.AGENT){room->AiPrompt("Sistema","Fonte".take(room))}
  val wrapped=source.withAttachmentServices(setOf("AUDIO")).withAttachmentServices(setOf("DOCUMENTS"))
  assertEquals(AiFeature.AGENT,wrapped.feature)
  assertEquals(setOf("AUDIO","DOCUMENTS"),wrapped.attachmentServices)
  assertEquals(source.build(300),wrapped.build(300))
 }
 @Test fun defaultsRequireExplicitActivation(){
  val defaults=AcaPreferencesDto()
  assertFalse(defaults.allows("CHAT"))
  assertFalse(defaults.allows("CALENDAR"))
  assertFalse(defaults.cloudConsent)
 }
 @Test fun chatServicesWorkIndependentlyOfContextualAca() {
  val native = listOf("CHAT", "CALENDAR", "DOCUMENTS", "IMAGES", "AUDIO", "STUDY_PLAN")
  val contextual = listOf("FORUM", "FORUM_SUGGESTIONS", "NOW_NUDGE")
  val basic = AcaPreferencesDto(aiEnabled = true, services = native + contextual)
  assertTrue(native.all { basic.allows(it) })
  assertTrue(contextual.none { basic.allows(it) })
  assertTrue(contextual.all { basic.copy(acaEnabled = true).allows(it) })
  assertFalse(basic.copy(services = listOf("CHAT")).allows("CALENDAR"))
  assertFalse(basic.copy(aiEnabled = false, acaEnabled = true).allows("CALENDAR"))
 }
 @Test fun remoteAttachmentMetadataExpiresAtTheExactServerDeadline(){
  val item=ContentAttachmentDto("file","Vocale.wav","audio/wav",100,"2026-11-12T10:00:00Z")
  val expiry=kotlin.time.Instant.parse(item.expiresAt).toEpochMilliseconds()
  assertFalse(item.isExpired(expiry-1));assertTrue(item.isExpired(expiry))
  assertTrue(item.copy(expired=true).isExpired(expiry-1))
  assertTrue(item.copy(expiresAt="invalid").isExpired(expiry-1))
 }
 @Test fun receivingContentCreatesOnlyAnInMemoryPreview(){
  IncomingContentInbox.clear()
  IncomingContentInbox.receive("../Appunti.txt","text/plain","Appunti".encodeToByteArray())
  assertEquals("Appunti.txt",IncomingContentInbox.pending?.fileName)
  IncomingContentInbox.clear();assertNull(IncomingContentInbox.pending)
  IncomingContentInbox.receive("Vuoto.txt","text/plain",byteArrayOf())
  assertNotNull(IncomingContentInbox.error);assertNull(IncomingContentInbox.pending)
  IncomingContentInbox.clear()
 }
}
