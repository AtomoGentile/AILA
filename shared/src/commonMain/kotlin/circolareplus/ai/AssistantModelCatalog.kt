package circolareplus.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class AssistantProviderModel(
    val id: String,
    val name: String = id,
    val reasoning: List<String> = emptyList()
)

@Serializable
internal data class GeminiThinkingConfig(val thinkingLevel: String)

internal fun isAssistantEffort(value: String): Boolean =
    value.isNotBlank() && value !in setOf("default", "none", "off", "disabled")

private val standardEffort = listOf("low", "medium", "high")
private val effortValues = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

internal fun geminiReasoningLevels(id: String): List<String> = when {
    id == "gemini-3.1-flash-lite-image" -> listOf("minimal", "high")
    id.matches(Regex("gemini-4-(hydrogen|neon)(?:-.*)?")) -> standardEffort + "xhigh"
    id.matches(Regex("gemini-(4-(carbon|argon)|3\\.[78]-flash|3\\.1-pro-preview)(?:-.*)?")) -> standardEffort
    id.matches(Regex("gemini-(3\\.6-flash|3\\.5-flash(?:-lite)?|3-flash-preview|3\\.1-flash-lite)(?:-.*)?")) ->
        listOf("minimal") + standardEffort
    id == "gemini-3-pro-preview" -> listOf("low", "high")
    else -> emptyList()
}

// The standard OpenAI Models endpoint does not return supported effort values.
// Use only documented Chat Completions model IDs, including their dated snapshots.
internal fun openAiReasoningLevels(id: String): List<String> {
    val model = id.replace(Regex("-\\d{4}-\\d{2}-\\d{2}$"), "")
    return when (model) {
        "gpt-5", "gpt-5-mini", "gpt-5-nano" -> listOf("minimal") + standardEffort
        "gpt-5.1" -> listOf("none") + standardEffort
        "gpt-5.2", "gpt-5.4-mini", "gpt-5.5" -> listOf("none") + standardEffort + "xhigh"
        "gpt-6.2-astra", "gpt-6.2-sol" -> standardEffort + listOf("xhigh", "max")
        "gpt-6.1-terra", "gpt-6-luna" -> listOf("none") + standardEffort + listOf("xhigh", "max")
        "o1", "o3", "o3-mini", "o4-mini" -> standardEffort
        else -> emptyList()
    }
}

internal fun CompatibleAiFormat.defaultBaseUrl(): String = when (this) {
    CompatibleAiFormat.OPENAI -> "https://api.openai.com"
    CompatibleAiFormat.ANTHROPIC -> "https://api.anthropic.com"
    CompatibleAiFormat.GOOGLE -> "https://generativelanguage.googleapis.com"
}

internal fun CompatibleAiFormat.effectiveBaseUrl(baseUrl: String): String =
    baseUrl.trim().ifBlank { defaultBaseUrl() }.trimEnd('/')

internal fun CompatibleAiFormat.providerName(baseUrl: String): String {
    val host = runCatching { Url(effectiveBaseUrl(baseUrl)).host }.getOrDefault("")
    return when (host) {
        "api.openai.com" -> "OpenAI"
        "api.anthropic.com" -> "Anthropic"
        "generativelanguage.googleapis.com" -> "Google Gemini"
        else -> host.ifBlank { label }
    }
}

internal fun CompatibleAiFormat.catalogUrl(baseUrl: String): String {
    val base = effectiveBaseUrl(baseUrl)
    require(base.startsWith("https://") || base.startsWith("http://")) { "Endpoint non valido" }
    return when (this) {
        CompatibleAiFormat.OPENAI -> when {
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
            base.endsWith("/v1") -> "$base/models"
            else -> "$base/v1/models"
        }
        CompatibleAiFormat.ANTHROPIC -> when {
            base.endsWith("/messages") -> base.removeSuffix("/messages") + "/models"
            base.endsWith("/v1") -> "$base/models"
            else -> "$base/v1/models"
        }
        CompatibleAiFormat.GOOGLE -> {
            val root = base.substringBefore("/models/")
            val version = if (root.endsWith("/v1") || root.endsWith("/v1beta")) "" else "/v1beta"
            "$root$version/models"
        }
    }
}

internal suspend fun loadAssistantProviderModels(
    format: CompatibleAiFormat,
    baseUrl: String,
    key: String,
    httpClient: HttpClient = GeminiHttp.client
): List<AssistantProviderModel> {
    require(key.isNotBlank()) { "Chiave API mancante" }
    val endpoint = format.catalogUrl(baseUrl)
    val models = mutableListOf<AssistantProviderModel>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null
    do {
        val response = httpClient.get(endpoint) {
            retry { noRetry() }
            timeout { requestTimeoutMillis = 12_000L }
            when (format) {
                CompatibleAiFormat.GOOGLE -> {
                    header("x-goog-api-key", cleanGeminiApiKey(key))
                    cursor?.let { parameter("pageToken", it) }
                }
                CompatibleAiFormat.OPENAI -> header("Authorization", "Bearer ${key.trim()}")
                CompatibleAiFormat.ANTHROPIC -> {
                    header("x-api-key", key.trim())
                    header("anthropic-version", "2023-06-01")
                    cursor?.let { parameter("after_id", it) }
                }
            }
        }
        check(response.status.isSuccess()) { "Catalogo non disponibile (HTTP ${response.status.value})" }
        val page = Json.parseToJsonElement(response.bodyAsText()) as JsonObject
        val entries = page[if (format == CompatibleAiFormat.GOOGLE) "models" else "data"] as? JsonArray
            ?: error("Catalogo non disponibile")
        models += entries.mapNotNull { (it as? JsonObject)?.let { model -> providerModel(format, endpoint, model) } }
        cursor = when (format) {
            CompatibleAiFormat.GOOGLE -> page.text("nextPageToken")
            CompatibleAiFormat.ANTHROPIC -> page.text("last_id").takeIf { page.flag("has_more") == true }
            CompatibleAiFormat.OPENAI -> null
        }?.takeIf { it.isNotBlank() && seenCursors.add(it) }
    } while (cursor != null)
    return models.distinctBy { it.id }.sortedBy { it.name.lowercase() }
}

private fun providerModel(format: CompatibleAiFormat, endpoint: String, model: JsonObject): AssistantProviderModel? {
    val id = model.text(if (format == CompatibleAiFormat.GOOGLE) "name" else "id")
        ?.removePrefix("models/")?.takeIf { it.isNotBlank() } ?: return null
    val capabilities = model["capabilities"] as? JsonObject
    return when (format) {
        CompatibleAiFormat.GOOGLE -> {
            val methods = model["supportedGenerationMethods"] as? JsonArray ?: return null
            if (methods.none { (it as? JsonPrimitive)?.contentOrNull == "generateContent" } ||
                listOf("-image", "-tts", "-native-audio", "-robotics").any { id.contains(it) }) return null
            AssistantProviderModel(
                id, model.text("displayName") ?: id,
                if (model.flag("thinking") == false) emptyList() else geminiReasoningLevels(id)
            )
        }
        CompatibleAiFormat.ANTHROPIC -> {
            val effort = capabilities?.get("effort") as? JsonObject
            val levels = standardEffort + listOf("xhigh", "max")
            AssistantProviderModel(id, model.text("display_name") ?: id, levels.filter {
                (effort?.get(it) as? JsonObject)?.flag("supported") == true
            })
        }
        CompatibleAiFormat.OPENAI -> {
            val official = Url(endpoint).host == "api.openai.com"
            if (official && (!id.startsWith("gpt-") && !id.matches(Regex("o[1-9](-.*)?")) ||
                    listOf("-audio", "-realtime", "-transcribe", "-tts", "-search", "-image", "-codex", "-pro")
                        .any { id.contains(it) })) return null
            val advertised = model["supported_reasoning_efforts"] as? JsonArray
            AssistantProviderModel(id, reasoning = advertised?.let { values ->
                effortValues.filter { level -> values.any { (it as? JsonPrimitive)?.contentOrNull == level } }
            } ?: if (official) openAiReasoningLevels(id) else emptyList())
        }
    }
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.flag(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
