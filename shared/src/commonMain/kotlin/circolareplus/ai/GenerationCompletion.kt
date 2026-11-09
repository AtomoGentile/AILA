package circolareplus.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A 200 response may still contain a partial completion. Never treat it as a usable answer/plan. */
internal fun requireCompletedGeneration(root: JsonObject, format: CompatibleAiFormat) {
    val reason = when (format) {
        CompatibleAiFormat.OPENAI -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
        CompatibleAiFormat.ANTHROPIC -> root["stop_reason"]?.jsonPrimitive?.contentOrNull
        CompatibleAiFormat.GOOGLE -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("finishReason")?.jsonPrimitive?.contentOrNull
    } ?: return // Some compatible providers omit the metadata; validate the content separately.
    val complete = when (format) {
        CompatibleAiFormat.OPENAI -> reason == "stop"
        CompatibleAiFormat.ANTHROPIC -> reason in setOf("end_turn", "stop_sequence")
        CompatibleAiFormat.GOOGLE -> reason == "STOP"
    }
    check(complete) { "Il provider ha interrotto la risposta ($reason). Riprova o riduci la richiesta; nessuna azione è stata eseguita." }
}
