package circolareplus.ai

import kotlin.test.*
import kotlinx.serialization.json.*

class OpenAiCompatibleBodyTest {
 @Test fun reportedModelUsesCompletionTokensWithoutUnsupportedSampling(){
  val body=openAiCompatibleBody("https://api.openai.com/v1/chat/completions","gpt-6-luna","Rispondi OK","Prova",32)
  assertEquals(32,body["max_completion_tokens"]?.jsonPrimitive?.int)
  assertFalse("max_tokens" in body);assertFalse("temperature" in body)
  assertEquals("Prova",body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
 }
 @Test fun reasoningModelsUseTheSameContractForChatAndJsonPlanning(){
  listOf("gpt-5","gpt-5.1","gpt-6-luna","o1","o3-mini","o4-mini").forEach{model->
   val body=openAiCompatibleBody("https://proxy.example/v1/chat/completions",model,"Solo JSON","Pianifica",2048)
   assertTrue("max_completion_tokens" in body,model);assertFalse("max_tokens" in body,model)
   assertFalse("temperature" in body,model)
   assertEquals("json_object",body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
  }
 }
 @Test fun legacyCompatibleEndpointsKeepTheirExistingTokenField(){
  val body=openAiCompatibleBody("https://provider.example/v1/chat/completions","custom-model","Sistema","Domanda",700)
  assertEquals(700,body["max_tokens"]!!.jsonPrimitive.int)
  assertFalse("max_completion_tokens" in body);assertTrue("temperature" in body)
 }
 @Test fun officialOlderModelsAlsoUseTheCurrentCompletionTokenField(){
  val body=openAiCompatibleBody("https://api.openai.com/v1/chat/completions","gpt-4o","Sistema","Domanda",700)
  assertTrue("max_completion_tokens" in body);assertFalse("max_tokens" in body)
  assertFalse("response_format" in body)
 }
}
