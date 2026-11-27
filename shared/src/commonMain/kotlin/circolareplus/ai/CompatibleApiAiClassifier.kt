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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class CompatibleAiFormat(val id: String, val label: String) {
    OPENAI("OPENAI", "OpenAI compatibile"),
    ANTHROPIC("ANTHROPIC", "Anthropic compatibile"),
    GOOGLE("GOOGLE", "Google compatibile");

    companion object {
        fun fromId(id: String?): CompatibleAiFormat = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

@Serializable
private data class CompatibleMessage(val role: String, val content: String)

private fun isOpenAiReasoningModel(model:String)=Regex("^(gpt-[5-9](?:[.-]|$)|o[1-9](?:[.-]|$))",RegexOption.IGNORE_CASE).containsMatchIn(model)

/** Explicit JSON omits unsupported fields entirely, regardless of HttpClient serializer defaults. */
internal fun openAiCompatibleBody(
    endpoint:String,
    model:String,
    system:String,
    user:String,
    maxTokens:Int,
    reasoningEffort:String?=null
):JsonObject {
    val official = io.ktor.http.Url(endpoint).host.equals("api.openai.com", ignoreCase = true)
    val supportedEffort = reasoningEffort?.takeIf { !official || it in openAiReasoningLevels(model) }
    val reasoning = supportedEffort != null || isOpenAiReasoningModel(model)
    return buildJsonObject {
        put("model",JsonPrimitive(model))
        put("messages",buildJsonArray {
            listOf("system" to system,"user" to user).forEach{(role,text)->add(buildJsonObject {
                put("role",JsonPrimitive(role));put("content",JsonPrimitive(text))
            })}
        })
        put(if(official||reasoning)"max_completion_tokens"else"max_tokens",JsonPrimitive(maxTokens))
        if(!reasoning)put("temperature",JsonPrimitive(0.2))
        supportedEffort?.let { put("reasoning_effort", JsonPrimitive(it)) }
        if(system.contains("JSON",ignoreCase=true))put("response_format",buildJsonObject {
            put("type",JsonPrimitive("json_object"))
        })
    }
}

@Serializable
private data class AnthropicCompatibleRequest(
    val model: String,
    val system: String,
    val messages: List<CompatibleMessage>,
    val max_tokens: Int = 2048,
    val temperature: Double? = null,
    val output_config: AnthropicOutputConfig? = null
)

@Serializable
private data class AnthropicOutputConfig(val effort: String)

@Serializable
private data class GooglePart(val text: String)

@Serializable
private data class GoogleContent(val role: String? = null, val parts: List<GooglePart>)

@Serializable
private data class GoogleSystemInstruction(val parts: List<GooglePart>)

@Serializable
private data class GoogleGenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 2048,
    val responseMimeType: String? = null,
    val thinkingConfig: GeminiThinkingConfig? = null
)

@Serializable
private data class GoogleRequest(
    val systemInstruction: GoogleSystemInstruction? = null,
    val contents: List<GoogleContent>,
    val generationConfig: GoogleGenerationConfig? = null
)

/** Calls user-configured OpenAI-compatible, Anthropic-compatible, or Google Gemini endpoints from the device. */
class CompatibleApiAiClassifier(
    private val format: CompatibleAiFormat,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val httpClient: HttpClient = GeminiHttp.client,
    private val assistantReasoning: String? = null
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
        val maxTokens = if (assistantReasoning == null) 2048 else 8192
        val (answer, actualModel) = requestWithModel(
            built.systemPrompt, built.userPrompt, maxTokens, assistantReasoning
        )
        AiTextResult.Success(answer, "${format.providerName(baseUrl)} ($actualModel)")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AiTextResult.Failure("${format.label}: ${e.message ?: "errore di connessione"}")
    }

    override suspend fun testConfiguration(): String = try {
        if (model.isBlank() && format != CompatibleAiFormat.GOOGLE) {
            val models = loadAssistantProviderModels(format, baseUrl, apiKey, httpClient)
            "Connessione riuscita con ${format.providerName(baseUrl)}. Modelli disponibili: ${models.size}."
        } else {
            // Reasoning tokens share the completion budget: 32 can yield no visible text.
            val probeTokens = if (format == CompatibleAiFormat.OPENAI && isOpenAiReasoningModel(model.trim())) 1024 else 32
            request("Rispondi solo con OK.", "Verifica di connessione.", probeTokens)
            "Connessione riuscita con ${format.providerName(baseUrl)} (${model.ifBlank { "gemini-4-hydrogen" }})."
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        "Connessione non riuscita: ${e.message ?: "errore sconosciuto"}"
    }

    private suspend fun request(system: String, user: String, maxTokens: Int): String =
        requestWithModel(system, user, maxTokens).first

    private suspend fun requestWithModel(
        system: String,
        user: String,
        maxTokens: Int,
        reasoning: String? = null
    ): Pair<String, String> {
        val effectiveModel = model.trim().ifBlank { if (format == CompatibleAiFormat.GOOGLE) "gemini-4-hydrogen" else "" }
        require(apiKey.isNotBlank()) { "Inserisci la chiave API." }
        require(effectiveModel.isNotBlank()) { "Seleziona un modello nel menu dell’Assistant." }
        val endpoint = endpointUrl(effectiveModel)
        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            when (format) {
                CompatibleAiFormat.OPENAI -> header("Authorization", "Bearer ${apiKey.trim()}")
                CompatibleAiFormat.ANTHROPIC -> {
                    header("x-api-key", apiKey.trim())
                    header("anthropic-version", "2023-06-01")
                }
                CompatibleAiFormat.GOOGLE -> {
                    header("x-goog-api-key", apiKey.trim())
                }
            }
            // A mixed `when` passed to setBody is inferred as Any. That makes Ktor
            // guess serializers for JsonObject's values, including internal JsonLiteral.
            // Preserve the concrete reified type at each call instead.
            when (format) {
                CompatibleAiFormat.OPENAI -> setBody<JsonObject>(
                    openAiCompatibleBody(endpoint, effectiveModel, system, user, maxTokens, reasoning)
                )
                CompatibleAiFormat.ANTHROPIC -> setBody<AnthropicCompatibleRequest>(
                    AnthropicCompatibleRequest(
                        model = effectiveModel, system = system,
                        messages = listOf(CompatibleMessage("user", user)), max_tokens = maxTokens,
                        temperature = 0.2.takeUnless { reasoning != null },
                        output_config = reasoning?.let(::AnthropicOutputConfig)
                    )
                )
                CompatibleAiFormat.GOOGLE -> setBody<GoogleRequest>(
                    GoogleRequest(
                        systemInstruction = if (system.isNotBlank()) {
                            GoogleSystemInstruction(listOf(GooglePart(system)))
                        } else null,
                        contents = listOf(GoogleContent(role = "user", parts = listOf(GooglePart(user)))),
                        generationConfig = GoogleGenerationConfig(
                            temperature = 0.2,
                            maxOutputTokens = maxTokens,
                            responseMimeType = if (system.contains("JSON", ignoreCase = true)) "application/json" else null,
                            thinkingConfig = reasoning?.let { GeminiThinkingConfig(it.uppercase()) }
                        )
                    )
                )
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: ${body.take(240)}")
        val root = json.parseToJsonElement(body).jsonObject
        requireCompletedGeneration(root, format)
        val text = when (format) {
            CompatibleAiFormat.OPENAI -> {
                val content = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject?.get("content")
                (content as? JsonPrimitive)?.contentOrNull ?: (content as? JsonArray)?.mapNotNull {
                    (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                }?.joinToString("")
            }
            CompatibleAiFormat.ANTHROPIC -> root["content"]?.jsonArray?.mapNotNull {
                (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }?.joinToString("")
            CompatibleAiFormat.GOOGLE -> {
                val parts = root["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                parts?.mapNotNull {
                    val part = it as? JsonObject ?: return@mapNotNull null
                    if (part["thought"]?.jsonPrimitive?.booleanOrNull == true) null
                    else part["text"]?.jsonPrimitive?.contentOrNull
                }?.joinToString("")
            }
        }
        val answer = text?.takeIf { it.isNotBlank() } ?: error("Il provider ha restituito una risposta vuota.")
        val responseModelField = if (format == CompatibleAiFormat.GOOGLE) "modelVersion" else "model"
        return answer to (root[responseModelField]?.jsonPrimitive?.contentOrNull ?: effectiveModel)
    }

    private fun endpointUrl(effectiveModel: String): String {
        val base = format.effectiveBaseUrl(baseUrl)
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Inserisci un endpoint HTTP o HTTPS valido."
        }
        return when (format) {
            CompatibleAiFormat.OPENAI -> {
                val suffix = "/chat/completions"
                if (base.endsWith(suffix)) base
                else if (base.endsWith("/v1")) "$base$suffix"
                else "$base/v1$suffix"
            }
            CompatibleAiFormat.ANTHROPIC -> {
                val suffix = "/messages"
                if (base.endsWith(suffix)) base
                else if (base.endsWith("/v1")) "$base$suffix"
                else "$base/v1$suffix"
            }
            CompatibleAiFormat.GOOGLE -> {
                if (base.contains(":generateContent")) base
                else {
                    val prefix = if (base.contains("/v1") || base.contains("/v1beta")) base else "$base/v1beta"
                    "$prefix/models/$effectiveModel:generateContent"
                }
            }
        }
    }
}
