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
  assertFalse(defaults.allows("CHAT",false))
  assertFalse(defaults.allows("CALENDAR"))
  assertFalse(defaults.cloudConsent)
 }
 @Test fun servicesAndAcaRemainIndependent(){
  val basic=AcaPreferencesDto(aiEnabled=true,services=listOf("CHAT","CALENDAR"))
  assertTrue(basic.allows("CHAT",false))
  assertFalse(basic.allows("CALENDAR"))
  assertTrue(basic.copy(acaEnabled=true).allows("CALENDAR"))
  assertFalse(basic.copy(acaEnabled=true).allows("DOCUMENTS"))
  assertFalse(basic.copy(aiEnabled=false,acaEnabled=true).allows("CALENDAR"))
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
