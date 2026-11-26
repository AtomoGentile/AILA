package circolareplus.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AssistantProviderModelTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun selectedGeminiUsesTheNativeLevelAndReportsItsResolvedVersion() = runTest {
        val modelId = "gemini-4-hydrogen"
        val http = client(MockEngine { request ->
            assertEquals("/v1beta/models/$modelId:generateContent", request.url.encodedPath)
            val body = Json.parseToJsonElement(
                assertIs<TextContent>(request.body).text
            ).jsonObject
            val thinking = body.getValue("generationConfig").jsonObject.getValue("thinkingConfig").jsonObject
            assertEquals("XHIGH", thinking.getValue("thinkingLevel").jsonPrimitive.content)
            assertNull(thinking["thinkingBudget"])
            respond(
                """{"modelVersion":"gemini-4-hydrogen-001","candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"OK"}]}}]}""",
                headers = jsonHeaders
            )
        })
        try {
            val classifier = ClientSideAiClassifier(
                "test-only", httpClient = http, assistantModel = modelId, assistantReasoning = "xhigh"
            )
            val result = assertIs<AiTextResult.Success>(classifier.generateAnswer { AiPrompt("Rispondi.", "Ciao") })
            assertEquals("Google Gemini (gemini-4-hydrogen-001)", result.modelLabel)
        } finally {
            http.close()
        }
    }

    @Test
    fun explicitGeminiModelDoesNotFallBackToAnotherModel() = runTest {
        var requests = 0
        val http = client(MockEngine {
            requests++
            respond("""{"error":{"message":"Modello non disponibile"}}""", HttpStatusCode.NotFound, jsonHeaders)
        })
        try {
            val classifier = ClientSideAiClassifier(
                "test-only", httpClient = http, assistantModel = "gemini-unavailable"
            )
            assertIs<AiTextResult.Failure>(classifier.generateAnswer { AiPrompt("Rispondi.", "Ciao") })
            assertEquals(1, requests)
        } finally {
            http.close()
        }
    }

    @Test
    fun claudeUsesAdvertisedEffortAndTheOfficialEndpointByDefault() = runTest {
        val http = client(MockEngine { request ->
            assertEquals("api.anthropic.com", request.url.host)
            when (request.url.encodedPath) {
                "/v1/models" -> respond(
                    """{"data":[{"id":"claude-test","display_name":"Claude Test","capabilities":{"effort":{"supported":true,"low":{"supported":true},"high":{"supported":true},"max":{"supported":false}}}}]}""",
                    headers = jsonHeaders
                )
                "/v1/messages" -> {
                    val body = Json.parseToJsonElement(
                        assertIs<TextContent>(request.body).text
                    ).jsonObject
                    assertEquals("high", body.getValue("output_config").jsonObject.getValue("effort").jsonPrimitive.content)
                    respond(
                        """{"model":"claude-test-001","stop_reason":"end_turn","content":[{"type":"text","text":"OK"}]}""",
                        headers = jsonHeaders
                    )
                }
                else -> error("Endpoint inatteso: ${request.url.encodedPath}")
            }
        })
        try {
            val model = loadAssistantProviderModels(CompatibleAiFormat.ANTHROPIC, "", "test-only", http).single()
            assertEquals(listOf("low", "high"), model.reasoning)
            val classifier = CompatibleApiAiClassifier(
                CompatibleAiFormat.ANTHROPIC, "", "test-only", model.id, http, "high"
            )
            val result = assertIs<AiTextResult.Success>(classifier.generateAnswer { AiPrompt("Rispondi.", "Ciao") })
            assertEquals("Anthropic (claude-test-001)", result.modelLabel)
        } finally {
            http.close()
        }
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpRequestRetry) { maxRetries = 0 }
        install(HttpTimeout)
    }
}
