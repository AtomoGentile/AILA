package circolareplus.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AudioTranscriptionTest {
 @Test fun localResultDoesNotRequireOrCallCloud()=runTest {
  var calls=0
  val result=transcribeVoice(true,false,{true},{false},{"Un vocale italiano"},{calls++;AudioTranscription("cloud","cloud")})
  assertEquals("Un vocale italiano",result.text);assertEquals("Trascritto sul dispositivo",result.label);assertEquals(0,calls)
 }
 @Test fun unavailableLocalEngineNeedsCloudConsent()=runTest {
  var calls=0
  assertFailsWith<IllegalStateException>{transcribeVoice(true,false,{true},{false},{null},{calls++;AudioTranscription("cloud","cloud")})}
  assertEquals(0,calls)
 }
 @Test fun localOnlyPreventsRemoteFallback()=runTest {
  var calls=0
  assertFailsWith<IllegalStateException>{transcribeVoice(false,true,{true},{true},{null},{calls++;AudioTranscription("cloud","cloud")})}
  assertEquals(0,calls)
 }
 @Test fun consentRevocationDuringRecognitionStopsProcessing()=runTest {
  var allowed=true;var calls=0
  assertFailsWith<IllegalStateException>{transcribeVoice(true,false,{allowed},{true},{allowed=false;"testo"},{calls++;AudioTranscription("cloud","cloud")})}
  assertEquals(0,calls)
 }
 @Test fun authorizedFallbackLabelsTheRemoteProvider()=runTest {
  var calls=0
  val result=transcribeVoice(true,false,{true},{true},{null},{calls++;AudioTranscription("testo remoto","Whisper · AILA")})
  assertEquals(1,calls);assertEquals("Whisper · AILA",result.label)
 }
 @Test fun cancellationDoesNotSendAudioToCloud()=runTest {
  var calls=0
  assertFailsWith<CancellationException>{transcribeVoice(true,false,{true},{true},{throw CancellationException()},{calls++;AudioTranscription("cloud","cloud")})}
  assertEquals(0,calls)
 }
}
