package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.retry
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// Il servizio AI incluso (v1.5.0): la chat di AILA Assistant passa dal Worker di AILA.
//
// Niente chiavi da configurare per chi usa l'app: la richiesta arriva al backend
// (routes/ai.ts, rotta /api/ai/chat, protetta dal token di sessione) e li' gira su
// Workers AI con Gemma 4 26B A4B. La quota giornaliera la conta Cloudflare con precisione
// esatta e, sul piano Free, la ferma da sola (errore 4006): il backend la traduce in un
// 429 con ragione QUOTA_EXHAUSTED, e qui la si usa per passare alla riserva — la stessa che
// esisteva prima della v1.5.0, chiave Google o AI locale.
// ---------------------------------------------------------------------------

@Serializable
internal data class AilaChatMessage(val role: String, val content: String)

@Serializable
internal data class AilaChatRequest(
    val messages: List<AilaChatMessage>,
    /** Atteso un oggetto JSON (stesure evento): attiva response_format lato Worker. */
    val json: Boolean = false,
    /** Il ragionamento di Gemma sulla domanda, come lo accetta l'assistente sui modelli locali. */
    val thinking: Boolean = false,
    val maxTokens: Int = DEFAULT_CHAT_MAX_TOKENS
)

/** Esito di una chiamata verso il Worker: fine quota e' un flag esplicito, non da indovinare. */
internal sealed class AilaChatResult {
    data class Success(val text: String) : AilaChatResult()

    /**
     * [isDailyQuota] separa la fine della quota (riserva sensata, messaggio dedicato) da
     * qualunque altro guasto (Worker irraggiungibile, servizio spento, modello sovraccarico).
     */
    data class Failure(val reason: String, val isDailyQuota: Boolean) : AilaChatResult()
}

/**
 * Un classificatore per il provider "AILA (incluso)".
 *
 * Rimappa i metodi di [AiClassifier] sul servizio incluso con un perimetro preciso:
 * - [generateAnswer] (la chat) e [parseEventPrompt] (stesure evento) passano dal Worker;
 * - [classifyCircularText] NON lo passa: la lettura delle circolari resta dov'era — i
 *   riassunti dal server con la sua chiave dedicata (GEMINI_API_KEY, services/summarizer.ts)
 *   e, per l'analisi personale, [personal]: la chiave Google o l'AI locale, le strade
 *   che esistevano gia' prima della v1.5.0.
 *
 * Alla riserva arriva anche tutto cio' che il servizio incluso non riesce a fare: quota
 * esaurita, Worker irraggiungibile, risposta vuota. Chi ha configurato la propria AI non
 * resta a secco la sera in cui la quota e' finita con la classe; chi non ha configurato
 * niente riceve un messaggio chiaro e una via d'uscita al posto di una terna di errori
 * tecnici.
 *
 * Come col client Gemini qui non c'e' nessun ripiego che inventa: un fallimento resta un
 * [AiTextResult.Failure] col motivo vero, che la chat mostra all'utente.
 *
 * [apiBaseUrl] e [authToken] sono funzioni e non stringhe perche' l'istanza nasce nuova a
 * ogni domanda dell'assistente (vedi AppContainer.newAssistant): rileggere URL e token a
 * ogni chiamata fa valere subito un override sviluppatore o un cambio di sessione — la
 * stessa ragione per cui la factory esiste.
 */
class AilaCloudClassifier(
    private val apiBaseUrl: () -> String,
    private val authToken: () -> String,
    /** Riserva per le circolari e per tutto cio' che il servizio incluso non copre. */
    private val personal: AiClassifier,
    private val enableThinking: Boolean = false,
    /** Tetto per ciascun tentativo al Worker, come fa [ChainedAiClassifier] coi suoi provider. */
    private val attemptTimeoutMs: Long? = null,
    private val httpClient: HttpClient = GeminiHttp.client
) : AiClassifier {

    companion object {
        const val MODEL_LABEL = "AILA (Gemma 4 26B A4B)"

        /**
         * Quanto puo' essere lungo, in caratteri, un prompt dell'assistente verso il servizio
         * incluso. La stessa misura della chat verso Gemini (MAX_PROMPT_CHARS): il Worker
         * rifiuterebbe di piu', ma meglio fermarsi gia' al client che bruciare neuron del
         * giorno con un prompt a tonnellate.
         */
        private const val MAX_CHAT_PROMPT_CHARS = 60_000

        /**
         * Tetto di completamento per la chat; gli eventi ne chiedono di meno. Gemma potrebbe
         * scrivere pagina su pagina, ma l'assistente risponde in poche schermate e i neuron
         * gratis sono pochi: un tetto basso e' il modo piu' semplice di non sprecarli.
         */
        private const val DEFAULT_CHAT_MAX_TOKENS = 1500
        private const val MAX_EVENT_TOKENS = 500

        private val json = Json { ignoreUnknownKeys = true }
    }

    // -----------------------------------------------------------------------
    // Circolari: la lettura resta sulla configurazione di prima, non sul servizio.
    // -----------------------------------------------------------------------

    override suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): CircularAiClassification = personal.classifyCircularText(
        circularNumber, circularTitle, pdfText, studentContext
    )

    // -----------------------------------------------------------------------
    // Eventi calendario: dal servizio incluso, con la riserva se non arriva nulla.
    // -----------------------------------------------------------------------

    override suspend fun parseEventPrompt(userPrompt: String): EventDraft {
        try {
            val request = AilaChatRequest(
                messages = listOf(
                    AilaChatMessage("system", EventGenerationPrompt.SYSTEM_PROMPT),
                    AilaChatMessage("user", EventGenerationPrompt.buildUserPrompt(userPrompt))
                ),
                json = true,
                maxTokens = MAX_EVENT_TOKENS
            )
            val text = (chatOrTimeout(request) as? AilaChatResult.Success)?.text
            if (text != null) {
                val jsonString = EventGenerationPrompt.extractJsonObject(text)
                val draft = jsonString?.let { EventGenerationPrompt.parse(it) }
                if (draft != null) return draft
                // JSON assente o malformato: non e' fine quota, ma non e' neanche una bozza
                // utilizzabile. Vale la riserva, come con un guasto qualsiasi.
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Rete assente o endpoint irraggiungibile: sotto c'e' comunque la riserva.
        }
        return personal.parseEventPrompt(userPrompt)
    }

    /**
     * Implementazione di [AiClassifier.generateAnswer]: prima il servizio incluso, poi la
     * riserva [personal] — la catena chiave Google/modello locale costruita in
     * AppContainer.newAiClassifier. La combinazione dei motivi ricalca
     * [ChainedAiClassifier.generateAnswer]: chi legge la chat deve capire cosa ha impedito
     * la risposta e a chi serve l'intervento.
     */
    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
        val built = prompt.build(MAX_CHAT_PROMPT_CHARS)
        val first = chatOrTimeout(
            AilaChatRequest(
                messages = listOf(
                    AilaChatMessage("system", built.systemPrompt),
                    AilaChatMessage("user", built.userPrompt)
                ),
                thinking = enableThinking
            )
        )
        val firstReason = when (first) {
            is AilaChatResult.Success -> return AiTextResult.Success(first.text, MODEL_LABEL)
            is AilaChatResult.Failure -> first.reason
        }

        val fallback = personal.generateAnswer(prompt)
        if (fallback is AiTextResult.Success) return fallback
        val fallbackReason = (fallback as AiTextResult.Failure).reason

        return AiTextResult.Failure(
            when ((first as? AilaChatResult.Failure)?.isDailyQuota) {
                // Fine quota: il messaggio parte per l'utente cosi' com'e', e se la riserva ha
                // qualcosa da dire (chiave non configurata, modello assente) sta in coda,
                // troncato: e' un documentarsi in piu', non un secondo problema da risolvere.
                true -> "$firstReason Anche la riserva non ha risposto: ${fallbackReason.take(280)}"
                else -> "$firstReason\nRiserva: $fallbackReason"
            }
        )
    }

    /**
     * Implementazione di [AiClassifier.testConfiguration]: verifica il servizio incluso con
     * richieste gratuite (niente neuron bruciati per dire "funziona") e riporta la riserva
     * per esteso, come [ChainedAiClassifier.testConfiguration] fa coi due provider.
     */
    override suspend fun testConfiguration(): String {
        val serviceLine = try {
            val response = httpClient.get("${apiBaseUrl()}/api/ai/status") {
                header(HttpHeaders.Authorization, "Bearer ${authToken()}")
            }
            val body = response.bodyAsText()
            val parsed = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }
            when {
                response.status.value == 401 ->
                    "Servizio AI incluso: sessione non valida (rifai il login)."
                response.status.isSuccess() -> when (parsed?.get("configured")?.jsonPrimitive?.booleanOrNull) {
                    true -> "Servizio AI incluso attivo " +
                        "(${parsed?.get("model")?.jsonPrimitive?.contentOrNull ?: MODEL_LABEL}): " +
                        "nessuna chiave da configurare. La quota giornaliera la conta Cloudflare " +
                        "e si rinnova a mezzanotte UTC."
                    else -> "Il servizio AI incluso non risulta attivo: ora l'assistente ha solo " +
                        "la riserva. Errore: " +
                        "${parsed?.get("error")?.jsonPrimitive?.contentOrNull ?: "non specificato"}"
                }
                else -> "Il servizio AI ha risposto HTTP ${response.status.value}: ${body.take(200)}"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Il servizio AI incluso non risulta raggiungibile: ${e::class.simpleName}: " +
                "${e.message ?: "nessun dettaglio"}. Controlla la connessione."
        }
        return "$serviceLine\nRiserva: ${personal.testConfiguration()}"
    }

    /**
     * Una chiamata chat verso il Worker, col tetto di tempo dell'assistente se c'e'.
     * Allo scadere non e' un'eccezione da gestire a monte: e' un fallimento, col resto
     * dei casi che non risolvono riprovando subito.
     */
    private suspend fun chatOrTimeout(request: AilaChatRequest): AilaChatResult {
        val chatResult = try {
            withTimeoutOrNull(attemptTimeoutMs ?: Long.MAX_VALUE) { postChat(request) }
                ?: return AilaChatResult.Failure(
                    "Il servizio di AILA non ha risposto entro ${attemptTimeoutMs?.div(1000)} secondi.",
                    isDailyQuota = false
                )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            // Il timeout del socket a volte arriva come questo, a volte come una classe
            // omonima per piattaforma: qui basta il primo, il secondo lo smorza postChat.
            return AilaChatResult.Failure("Il servizio di AILA non ha risposto in tempo.", false)
        } catch (e: Exception) {
            return AilaChatResult.Failure(
                "Non riesco a raggiungere il servizio di AILA: ${e::class.simpleName}: " +
                    "${e.message ?: "nessun dettaglio"}",
                false
            )
        }
        return chatResult
    }

    /** Una sola richiesta HTTP, senza nessuna politica di ritentativo dietro alle spalle. */
    private suspend fun postChat(request: AilaChatRequest): AilaChatResult {
        val response = httpClient.post("${apiBaseUrl()}/api/ai/chat") {
            // Niente ritentativi del client condiviso: un 429 di fine quota e' definitivo,
            // e rimandarla da noi non rigenera neuron.
            retry { noRetry() }
            timeout {
                requestTimeoutMillis = 90_000
                socketTimeoutMillis = 90_000
            }
            header(HttpHeaders.Authorization, "Bearer ${authToken()}")
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body = try { response.bodyAsText() } catch (e: Exception) { "" }
        if (response.status.isSuccess()) {
            val text = try {
                json.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.contentOrNull
            } catch (e: Exception) {
                null
            }
            return if (text.isNullOrBlank()) {
                AilaChatResult.Failure("Il servizio di AILA ha risposto senza contenuti.", false)
            } else {
                AilaChatResult.Success(text)
            }
        }
        val parsed = try { json.parseToJsonElement(body).jsonObject } catch (e: Exception) { null }
        val reason = parsed?.get("reason")?.jsonPrimitive?.contentOrNull
        val message = parsed?.get("error")?.jsonPrimitive?.contentOrNull ?: body.take(240)
        return AilaChatResult.Failure(
            "Il servizio di AILA ha risposto HTTP ${response.status.value}: ${message.trim()}",
            isDailyQuota = response.status.value == 429 && reason == "QUOTA_EXHAUSTED"
        )
    }
}
