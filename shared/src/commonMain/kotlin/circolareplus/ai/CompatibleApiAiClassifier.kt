package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class CompatibleAiFormat(val id: String, val label: String) {
    OPENAI("OPENAI", "OpenAI compatibile"),
    ANTHROPIC("ANTHROPIC", "Anthropic compatibile");

    companion object {
        fun fromId(id: String?): CompatibleAiFormat = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

@Serializable
private data class CompatibleMessage(val role: String, val content: String)

@Serializable
private data class OpenAiCompatibleRequest(
    val model: String,
    val messages: List<CompatibleMessage>,
    val max_tokens: Int = 2048,
    val temperature: Double = 0.2,
    val response_format: Map<String, String>? = null
)

@Serializable
private data class AnthropicCompatibleRequest(
    val model: String,
    val system: String,
    val messages: List<CompatibleMessage>,
    val max_tokens: Int = 2048,
    val temperature: Double = 0.2
)

/** Calls user-configured OpenAI-compatible or Anthropic-compatible endpoints from the device. */
class CompatibleApiAiClassifier(
    private val format: CompatibleAiFormat,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val httpClient: HttpClient = GeminiHttp.client
) : AiClassifier {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): CircularAiClassification {
        val prompt = """Analizza la circolare scolastica per "$studentContext". Circolare n. $circularNumber: $circularTitle.
            Testo: ${pdfText.take(24_000)}
            Rispondi solo con JSON: {"badge":"RELEVANT|POTENTIAL|NOT_RELEVANT","summary":"riassunto in italiano","deadlines":[{"title":"","dueDate":"YYYY-MM-DD","time":null,"category":"PAGAMENTO|USCITA_DIDATTICA|AVVISO"}]}
            RELEVANT = indicazioni dirette per lo studente/classe; POTENTIAL = opportunità facoltative; NOT_RELEVANT = altre classi o personale.""".trimIndent()
        return try {
            val text = request("Sei AILA, assistente scolastico. Restituisci solo JSON valido.", prompt, 1800)
            val parsed = json.parseToJsonElement(EventGenerationPrompt.extractJsonObject(text) ?: text).jsonObject
            val badge = parsed["badge"]?.jsonPrimitive?.contentOrNull?.let { raw ->
                runCatching { CircularRelevanceBadge.valueOf(raw.uppercase()) }.getOrNull()
            } ?: error("badge non valido")
            val deadlines = parsed["deadlines"]?.jsonArray.orEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val dueDate = item["dueDate"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                ExtractedDeadline(
                    title = title,
                    dueDate = dueDate,
                    time = item["time"]?.jsonPrimitive?.contentOrNull,
                    category = item["category"]?.jsonPrimitive?.contentOrNull ?: "AVVISO"
                )
            }
            CircularAiClassification(
                circularNumber = circularNumber,
                badge = badge,
                personalSummary = parsed["summary"]?.jsonPrimitive?.contentOrNull
                    ?: "Analisi AI disponibile senza riassunto dettagliato.",
                detectedDeadlines = deadlines,
                modelLabel = "${format.label} ($model)"
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            HeuristicClassification.classify(
                circularNumber, circularTitle, pdfText,
                failureReason = "${format.label}: ${e.message ?: "risposta non valida"}",
                notConfiguredMessage = "Configura una API compatibile nelle Impostazioni."
            )
        }
    }

    override suspend fun parseEventPrompt(userPrompt: String): EventDraft = try {
        val text = request(EventGenerationPrompt.SYSTEM_PROMPT, EventGenerationPrompt.buildUserPrompt(userPrompt), 700)
        EventGenerationPrompt.extractJsonObject(text)?.let(EventGenerationPrompt::parse)
            ?: EventDraft("", "", "ALTRO", null, null, null)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        EventDraft("", "", "ALTRO", null, null, null)
    }

    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult = try {
        val built = prompt.build(60_000)
        val answer = request(built.systemPrompt, built.userPrompt, 2048)
        AiTextResult.Success(answer, "${format.label} ($model)")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AiTextResult.Failure("${format.label}: ${e.message ?: "errore di connessione"}")
    }

    override suspend fun testConfiguration(): String = try {
        request("Rispondi solo con OK.", "Verifica di connessione.", 32)
        "Connessione riuscita con ${format.label} ($model)."
    } catch (e: Exception) {
        "Connessione non riuscita: ${e.message ?: "errore sconosciuto"}"
    }

    private suspend fun request(system: String, user: String, maxTokens: Int): String {
        require(apiKey.isNotBlank()) { "Inserisci la chiave API." }
        require(model.isNotBlank()) { "Inserisci il modello." }
        val endpoint = endpointUrl()
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            when (format) {
                CompatibleAiFormat.OPENAI -> header("Authorization", "Bearer ${apiKey.trim()}")
                CompatibleAiFormat.ANTHROPIC -> {
                    header("x-api-key", apiKey.trim())
                    header("anthropic-version", "2023-06-01")
                }
            }
            setBody(
                when (format) {
                    CompatibleAiFormat.OPENAI -> OpenAiCompatibleRequest(
                        model = model.trim(),
                        messages = listOf(CompatibleMessage("system", system), CompatibleMessage("user", user)),
                        max_tokens = maxTokens,
                        response_format = if (system.contains("JSON", ignoreCase = true)) mapOf("type" to "json_object") else null
                    )
                    CompatibleAiFormat.ANTHROPIC -> AnthropicCompatibleRequest(
                        model = model.trim(), system = system,
                        messages = listOf(CompatibleMessage("user", user)), max_tokens = maxTokens
                    )
                }
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: ${body.take(240)}")
        val root = json.parseToJsonElement(body).jsonObject
        val text = when (format) {
            CompatibleAiFormat.OPENAI -> {
                val content = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject?.get("content")
                content?.jsonPrimitive?.contentOrNull ?: content?.jsonArray?.mapNotNull {
                    (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                }?.joinToString("")
            }
            CompatibleAiFormat.ANTHROPIC -> root["content"]?.jsonArray?.mapNotNull {
                (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }?.joinToString("")
        }
        return text?.takeIf { it.isNotBlank() } ?: error("Il provider ha restituito una risposta vuota.")
    }

    private fun endpointUrl(): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Inserisci un endpoint HTTP o HTTPS valido."
        }
        val suffix = when (format) {
            CompatibleAiFormat.OPENAI -> "/chat/completions"
            CompatibleAiFormat.ANTHROPIC -> "/messages"
        }
        if (base.endsWith(suffix)) return base
        if (format == CompatibleAiFormat.OPENAI && base.endsWith("/v1")) return "$base$suffix"
        if (format == CompatibleAiFormat.ANTHROPIC && base.endsWith("/v1")) return "$base$suffix"
        return "$base/v1$suffix"
    }
}
