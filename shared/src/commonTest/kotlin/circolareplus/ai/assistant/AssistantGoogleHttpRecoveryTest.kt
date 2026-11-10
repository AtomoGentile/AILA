package circolareplus.ai.assistant

import circolareplus.ai.ClientSideAiClassifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Uses the production Google adapter, HTTP serialization and finish-reason parsing. No API key/network. */
class AssistantGoogleHttpRecoveryTest {
    private fun response(text: String) = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${Json.encodeToString(text)}}]}}]}"""

    @Test fun googleReplyWithoutTheExtraFlagRemainsUsable() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            val answer = if (calls == 1) """{"answer":"Giornalino il 20 novembre."}"""
                else """{"answer":"Giornalino il 20 novembre; per FCE manca la durata."}"""
            respond(response(answer), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(HttpRequestRetry) { maxRetries = 0 }
        }
        try {
            val google = ClientSideAiClassifier("test-only", "recovery-test-model", http)
            val assistant = AilaAssistant({ google }, { error("Unexpected PDF") }, { error("Unexpected extraction") })
            val reply = assistant.ask("Quando sono gli incontri del giornalino? Si intrecciano con FCE?", emptyList(), AssistantKnowledge("2026-11-10", null, null))
            assertFalse(reply.isError, reply.text)
            assertTrue("per FCE manca la durata" in reply.text)
            assertEquals(2, calls)
        } finally { http.close() }
    }
    @Test fun googleCanRecoverTheComparisonAfterAnInitialCalendarRead() = runTest {
        val replies = listOf(
            """{"answer":"","calendarQueries":[{"circularNumber":11}]}""",
            """{"answer":"Giornalino il 20 novembre dalle 14:15 alle 15:45."}""",
            """{"answer":"","calendarQueries":[{"circularNumber":11,"compareWith":"FCE"}],"verified":false}""",
            """{"answer":"Giornalino e FCE coincidono il 20 novembre alle 14:30; manca la durata FCE."}"""
        )
        var calls = 0
        val http = HttpClient(MockEngine {
            respond(response(replies[calls++]), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
            install(HttpRequestRetry) { maxRetries = 0 }
        }
        try {
            val google = ClientSideAiClassifier("test-only", "recovery-test-model", http)
            val assistant = AilaAssistant({ google }, { error("Unexpected PDF") }, { error("Unexpected extraction") })
            val events = listOf(
                circolareplus.domain.model.CalendarEvent("g", "Riunione di redazione", "2026-11-20", "14:15–15:45", circolareplus.domain.model.CalendarEventCategory.ALTRO, circularNumber = 11),
                circolareplus.domain.model.CalendarEvent("f", "FCE", "2026-11-20", "14:30", circolareplus.domain.model.CalendarEventCategory.ALTRO)
            )
            val reply = assistant.ask("Quando sono gli incontri del giornalino? Si intrecciano con FCE?", emptyList(),
                AssistantKnowledge("2026-11-10", null, null, calendarEvents = events))
            assertFalse(reply.isError, reply.text)
            assertEquals(4, calls)
            assertTrue("Giornalino e FCE" in reply.text)
            assertTrue("STESSA_DATA_ORARIO_INSUFFICIENTE" in reply.readContext)
        } finally { http.close() }
    }

}
