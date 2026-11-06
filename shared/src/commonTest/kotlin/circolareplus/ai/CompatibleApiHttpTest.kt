package circolareplus.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

/** Exercise Ktor's actual conversion pipeline, not only construction of a JsonObject. */
class CompatibleApiHttpTest {
    private fun client(response: String, inspect: (JsonObject, Headers, String) -> Unit): HttpClient = HttpClient(MockEngine { request ->
        val content = assertIs<TextContent>(request.body, "Ktor must encode the request before reaching the engine")
        assertEquals(ContentType.Application.Json, content.contentType?.withoutParameters())
        inspect(Json.parseToJsonElement(content.text).jsonObject, request.headers, request.url.encodedPath)
        respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }) {
        // Same serializer configuration as GeminiHttp.client.
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val openAiResponse = """{"choices":[{"message":{"content":"Risposta di prova"}}]}"""

    @Test fun connectionProbeIsEncodedBeforeTransport() = runTest {
        var sent = false
        val http = client(openAiResponse) { body, headers, path ->
            sent = true
            assertEquals("/v1/chat/completions", path)
            assertEquals("Bearer test-only", headers[HttpHeaders.Authorization])
            assertEquals("gpt-6-luna", body["model"]!!.jsonPrimitive.content)
            assertEquals(1024, body["max_completion_tokens"]!!.jsonPrimitive.int)
            assertFalse("max_tokens" in body); assertFalse("temperature" in body)
            assertFalse("response_format" in body)
        }
        try {
            val result = CompatibleApiAiClassifier(CompatibleAiFormat.OPENAI, "https://provider.test/v1", "test-only", "gpt-6-luna", http).testConfiguration()
            assertTrue(result.startsWith("Connessione riuscita"), result)
            assertTrue(sent, "The serialized request must reach the engine")
        } finally { http.close() }
    }

    @Test fun chatAndAgentRequestsKeepTheirJsonTypeAndEscapedText() = runTest {
        val question = "Riassumi \"la verifica\".\nÈ per la mia classe?"
        for (feature in listOf(AiFeature.CHAT, AiFeature.AGENT)) {
            var sent = false
            val system = if(feature == AiFeature.AGENT) "Restituisci solo JSON valido." else "Sei AILA."
            val http = client(openAiResponse) { body, _, _ ->
                sent = true
                assertEquals(question, body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content)
                assertEquals(2048, body["max_completion_tokens"]!!.jsonPrimitive.int)
                assertEquals(feature == AiFeature.AGENT, "response_format" in body)
                if(feature == AiFeature.AGENT) assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            }
            try {
                val result = CompatibleApiAiClassifier(CompatibleAiFormat.OPENAI, "https://api.openai.com/v1", "test-only", "gpt-6-luna", http)
                    .generateAnswer(aiPromptBuilder(feature) { AiPrompt(system, question) })
                assertEquals("Risposta di prova", assertIs<AiTextResult.Success>(result).text)
                assertTrue(sent)
            } finally { http.close() }
        }
    }

    @Test fun legacyCompatibleModelStillUsesMaxTokens() = runTest {
        val http = client(openAiResponse) { body, _, _ ->
            assertEquals(2048, body["max_tokens"]!!.jsonPrimitive.int)
            assertFalse("max_completion_tokens" in body)
            assertEquals(0.2, body["temperature"]!!.jsonPrimitive.double)
        }
        try {
            assertIs<AiTextResult.Success>(CompatibleApiAiClassifier(CompatibleAiFormat.OPENAI, "https://provider.test/v1", "test-only", "custom-model", http)
                .generateAnswer(aiPromptBuilder(AiFeature.CHAT) { AiPrompt("Sistema", "Domanda") }))
        } finally { http.close() }
    }

    @Test fun anthropicAndGoogleBodiesRemainTyped() = runTest {
        for (format in listOf(CompatibleAiFormat.ANTHROPIC, CompatibleAiFormat.GOOGLE)) {
            var sent = false
            val response = if(format == CompatibleAiFormat.ANTHROPIC) """{"content":[{"text":"OK"}]}""" else """{"candidates":[{"content":{"parts":[{"text":"OK"}]}}]}"""
            val http = client(response) { body, headers, path ->
                sent = true
                if(format == CompatibleAiFormat.ANTHROPIC) {
                    assertEquals("/v1/messages", path)
                    assertEquals("test-only", headers["x-api-key"])
                    assertEquals(32, body["max_tokens"]!!.jsonPrimitive.int)
                    assertEquals("Verifica di connessione.", body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
                } else {
                    assertEquals("/v1beta/models/test-model:generateContent", path)
                    assertEquals("test-only", headers["x-goog-api-key"])
                    assertEquals(32, body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.int)
                    assertEquals("Verifica di connessione.", body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
                }
            }
            try {
                val result = CompatibleApiAiClassifier(format, "https://provider.test", "test-only", "test-model", http).testConfiguration()
                assertTrue(result.startsWith("Connessione riuscita"), result); assertTrue(sent)
            } finally { http.close() }
        }
    }
}
