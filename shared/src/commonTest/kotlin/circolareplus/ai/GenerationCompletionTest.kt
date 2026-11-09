package circolareplus.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class GenerationCompletionTest {
    @Test fun aSuccessfulHttpResponseIsNotProofOfACompleteAnswer() {
        val bodies = listOf(
            CompatibleAiFormat.GOOGLE to """{"candidates":[{"finishReason":"MAX_TOKENS"}]}""",
            CompatibleAiFormat.OPENAI to """{"choices":[{"finish_reason":"length"}]}""",
            CompatibleAiFormat.ANTHROPIC to """{"stop_reason":"max_tokens"}"""
        )
        bodies.forEach { (format, body) -> assertFailsWith<IllegalStateException> { requireCompletedGeneration(Json.parseToJsonElement(body).jsonObject, format) } }
        requireCompletedGeneration(Json.parseToJsonElement("""{"candidates":[{"finishReason":"STOP"}]}""").jsonObject, CompatibleAiFormat.GOOGLE)
    }
}
