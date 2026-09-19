package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
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

// ---------------------------------------------------------------------------
// Google AI Studio (Gemini API) — DTO minimi per l'endpoint generateContent
// ---------------------------------------------------------------------------

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val responseMimeType: String = "application/json"
)

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

/**
 * Motore di classificazione AI client-side per l'app dello studente.
 *
 * Usa l'API Key personale dell'utente (Google AI Studio — https://aistudio.google.com/apikey),
 * scelta perché offre un piano gratuito senza carta di credito richiesta, a differenza di
 * OpenAI/Anthropic. La chiamata parte DAL DISPOSITIVO dello studente con la SUA chiave: nessun
 * testo della circolare o riassunto passa mai dal server di AILA (privacy by design,
 * vedi Specifica Tecnica Master v3.0 sez. 7 e Riepilogo Moduli v1.2 sez. 1.3).
 *
 * Se la chiave non è impostata, o la chiamata fallisce (rete assente, quota esaurita, chiave
 * non valida), si ricade su una classificazione euristica locale a parole chiave: l'app resta
 * utilizzabile anche senza configurare nulla.
 */
class ClientSideAiClassifier(
    private val userApiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        // Senza questo blocco valgono i tempi di default del motore HTTP di Android (OkHttp):
        // dieci secondi di lettura. Una generateContent con il testo di una circolare intera ne
        // impiega regolarmente di piu', e la chiamata moriva con
        // "SocketTimeoutException: Socket timeout has expired" — che l'app riportava
        // correttamente come fallimento, ma il fallimento non c'entrava con la chiave o con la
        // quota: era l'app a riattaccare troppo presto. ApiClient un blocco simile ce l'aveva
        // gia', questo client era rimasto indietro.
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
        }
        // Un 429 (quota esaurita al minuto) su un PDF lungo era un fallimento immediato: nessun
        // tentativo, dritti al fallback (locale o euristico). La quota di Google si libera in
        // pochi secondi, quindi vale la pena aspettare e riprovare prima di arrendersi — la
        // stessa cosa che ApiClient fa gia' per gli errori 5xx verso il backend proprio.
        install(HttpRequestRetry) {
            maxRetries = 3
            retryIf { _, response -> response.status.value == 429 || response.status.value in 500..599 }
            exponentialDelay(base = 2.0, maxDelayMs = 20_000)
        }
    }
) : AiClassifier {
    companion object {
        /**
         * Modello predefinito: l'alias "flash più recente" invece di un numero di versione.
         * **Era `gemini-2.0-flash`, che Google ha messo in dismissione**: è la causa più probabile
         * del "riassunto AI non funziona" — la chiamata tornava con un errore di modello non
         * trovato e l'app ricadeva silenziosamente sull'euristica a parole chiave.
         *
         * Con l'alias il problema non si ripresenta al prossimo giro di versioni: punta sempre al
         * flash corrente. Elenco: https://ai.google.dev/gemini-api/docs/models
         */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        /**
         * Scaletta di ripiego, provata in ordine se il modello predefinito viene rifiutato — utile
         * se un giorno anche l'alias cambiasse nome. L'app prova le alternative da sola invece di
         * restare muta finché qualcuno non tocca il codice.
         */
        private val MODEL_LADDER = listOf(
            "gemini-flash-latest",
            "gemini-flash-lite-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro"
        )

        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Quanti caratteri di PDF mandare a Gemini nel prompt.
         *
         * Prima il testo passava per intero, qualunque fosse la lunghezza: una circolare con
         * allegati o tabelle poteva arrivare a decine di migliaia di caratteri, superando la
         * quota di token-al-minuto del piano gratuito e tornando 429 molto piu' spesso del
         * necessario. Il modello locale tronca molto meno (vedi
         * [CircularClassificationPrompt.MAX_PDF_CHARS]) perche' deve anche essere veloce su CPU;
         * Gemini non ha quel vincolo di velocita', quindi il tetto qui e' molto piu' alto — solo
         * abbastanza basso da restare sotto la quota anche per un PDF lungo. Alzato da 12.000: da
         * quando il testo include anche gli allegati PDF (vedi [truncatePdfTextForAi], che divide
         * questo budget fra documento principale e allegati), 12.000 bastava a malapena per la
         * sola circolare principale e non lasciava nulla agli allegati.
         */
        private const val MAX_PDF_CHARS = 24_000

        /**
         * Quanto puo' essere lungo, in caratteri, un prompt dell'assistente verso Gemini.
         *
         * La finestra del modello e' molto piu' grande: il limite qui non e' tecnico ma di
         * quota. Sul piano gratuito la quota e' al minuto, e una chat e' fatta di domande
         * una dietro l'altra — con un contesto da centomila caratteri bastano due o tre
         * domande per esaurirla e ritrovarsi con i 429 al posto delle risposte.
         */
        private const val MAX_PROMPT_CHARS = 30_000

        /**
         * Modello che ha funzionato in questa sessione: una volta trovato, le circolari successive
         * lo usano subito senza rifare tutta la scaletta. Sta nel companion perché
         * `AppContainer.newAiClassifier()` costruisce una nuova istanza a ogni classificazione.
         */
        private var resolvedModel: String? = null
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): CircularAiClassification {
        if (userApiKey.isBlank()) {
            return fallbackHeuristicClassification(circularNumber, circularTitle, pdfText, failureReason = null)
        }

        // Prima un fallimento qui (rete, chiave rifiutata, risposta malformata...) veniva
        // silenziosamente inghiottito e sostituito dal fallback euristico, che mostrava SEMPRE lo
        // stesso messaggio generico "Nessuna API Key AI configurata..." — falso quando la chiave
        // C'ERA ma la chiamata falliva per un altro motivo, e impossibile da diagnosticare da
        // remoto perché il motivo reale del fallimento non arrivava mai da nessuna parte. Ora il
        // motivo esatto (codice HTTP, corpo della risposta, o l'eccezione) finisce nel riassunto
        // mostrato in app, cosi il fallback si distingue da un vero funzionamento e il problema è
        // descrivibile con precisione invece di un generico "non funziona".
        return try {
            val result = callGemini(circularNumber, circularTitle, pdfText, studentContext)
            when (result) {
                is GeminiCallResult.Success -> result.classification
                is GeminiCallResult.Failure -> fallbackHeuristicClassification(
                    circularNumber, circularTitle, pdfText, failureReason = result.reason
                )
            }
        } catch (e: Exception) {
            fallbackHeuristicClassification(
                circularNumber, circularTitle, pdfText,
                failureReason = "eccezione ${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            )
        }
    }

    private sealed class GeminiCallResult {
        data class Success(val classification: CircularAiClassification) : GeminiCallResult()
        data class Failure(val reason: String) : GeminiCallResult()
    }

    private suspend fun callGemini(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): GeminiCallResult {
        val truncatedText = truncatePdfTextForAi(pdfText, MAX_PDF_CHARS)
        val prompt = """
            Sei AILA Assistant, l'assistente scolastico dell'app "AILA".
            Analizza il seguente testo estratto da una circolare scolastica ufficiale per determinare se e quanto riguarda il seguente studente: "$studentContext".

            CIRCOLARE N. $circularNumber: $circularTitle
            TESTO DOCUMENTO:
            $truncatedText

            Rispondi rigorosamente in formato JSON con questa struttura, senza testo aggiuntivo:
            {
              "badge": "RELEVANT" | "POTENTIAL" | "NOT_RELEVANT",
              "summary": "5-6 righe in italiano",
              "deadlines": [
                {
                  "title": "Titolo scadenza",
                  "dueDate": "YYYY-MM-DD",
                  "time": "HH:MM oppure null",
                  "category": "PAGAMENTO" | "USCITA_DIDATTICA" | "AVVISO"
                }
              ]
            }

            Regole sui badge:
            - RELEVANT (Ti riguarda): indicazioni dirette e vincolanti, uscite o pagamenti per la classe o l'intero istituto.
            - POTENTIAL (Potenziale interesse): corsi facoltativi pomeridiani, borse di studio, gare, open day.
            - NOT_RELEVANT (Non sembra riguardarti): circolari riservate ad altre classi specifiche, docenti o personale ATA.

            Regole su "summary": deve avere 5-6 righe, non una o due frasi. Riporta sempre, se
            presenti nel testo: il destinatario esatto, tutte le date citate (giorno e mese),
            nomi di persone o enti coinvolti (relatori, associazioni, uffici), e l'obiettivo
            concreto della circolare (cosa deve fare lo studente, entro quando, con quali
            modalita'). Non generalizzare se il documento contiene questi dettagli: riportali
            per esteso invece di ometterli.
        """.trimIndent()

        // Si prova il modello risolto in precedenza, poi quello configurato, poi la scaletta.
        // Un modello ritirato risponde 404/NOT_FOUND: in quel caso si passa al successivo invece
        // di dichiarare fallita l'intera classificazione.
        val candidates = buildList {
            resolvedModel?.let { add(it) }
            add(model)
            addAll(MODEL_LADDER)
        }.distinct()

        var lastFailure = "nessun modello disponibile"
        for (candidate in candidates) {
            val response = postGenerate(candidate, prompt)
            if (response.status.isSuccess()) {
                resolvedModel = candidate
                return parseGeminiResponse(circularNumber, response.bodyAsText(), candidate)
            }
            val body = try { response.bodyAsText() } catch (e: Exception) { "" }
            lastFailure = "HTTP ${response.status.value} con il modello $candidate: ${body.take(200)}"
            // Chiave non valida, quota esaurita, rete: inutile provare altri modelli.
            if (!isModelUnavailable(response.status.value, body)) return GeminiCallResult.Failure(lastFailure)
        }

        // Nessun nome noto accettato: si chiede direttamente a Google quali modelli sono
        // disponibili per questa chiave. Così l'app si ripara da sola anche fra un anno.
        val discovered = discoverUsableModel()
        if (discovered != null) {
            val response = postGenerate(discovered, prompt)
            if (response.status.isSuccess()) {
                resolvedModel = discovered
                return parseGeminiResponse(circularNumber, response.bodyAsText(), discovered)
            }
            val body = try { response.bodyAsText() } catch (e: Exception) { "" }
            lastFailure = "HTTP ${response.status.value} con il modello $discovered (rilevato automaticamente): ${body.take(200)}"
        }

        return GeminiCallResult.Failure(lastFailure)
    }

    /** Implementazione di [AiClassifier.parseEventPrompt]: genera una bozza di evento dal testo. */
    override suspend fun parseEventPrompt(userPrompt: String): EventDraft {
        return try {
            val prompt = EventGenerationPrompt.buildUserPrompt(userPrompt)
            val candidates = buildList {
                resolvedModel?.let { add(it) }
                add(model)
                addAll(MODEL_LADDER)
            }.distinct()

            for (candidate in candidates) {
                val response = postGenerate(candidate, prompt)
                if (response.status.isSuccess()) {
                    resolvedModel = candidate
                    val body = response.bodyAsText()
                    val jsonString = EventGenerationPrompt.extractJsonObject(body)
                    if (jsonString != null) {
                        EventGenerationPrompt.parse(jsonString)?.let { return it }
                    }
                }
                if (!isModelUnavailable(response.status.value, "")) break
            }

            EventDraft("", "", "ALTRO", null, null, null)
        } catch (e: Exception) {
            EventDraft("", "", "ALTRO", null, null, null)
        }
    }

    /**
     * Implementazione di [AiClassifier.generateAnswer]: una generateContent secca, con la stessa
     * scaletta di modelli usata dalla classificazione.
     *
     * Qui non c'e' nessun ripiego: se Google rifiuta la chiave, la quota e' finita o la rete non
     * c'e', torna [AiTextResult.Failure] con la risposta letterale del server. L'assistente
     * mostra quel motivo in chat invece di una frase generica — e' l'unico modo per capire da
     * fuori perche' una domanda non ha ottenuto risposta.
     */
    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
        if (userApiKey.isBlank()) {
            return AiTextResult.Failure(
                "Nessuna chiave Google AI Studio configurata: aprila dalle Impostazioni, " +
                    "oppure scarica un modello per l'AI locale."
            )
        }

        val built = prompt.build(MAX_PROMPT_CHARS)
        val text = built.systemPrompt + "\n\n" + built.userPrompt
        val candidates = buildList {
            resolvedModel?.let { add(it) }
            add(model)
            addAll(MODEL_LADDER)
        }.distinct()

        var lastFailure = "nessun modello disponibile"
        for (candidate in candidates) {
            val response = try {
                postGenerate(candidate, text)
            } catch (e: Exception) {
                return AiTextResult.Failure(
                    "non sono riuscito a raggiungere Google: ${e::class.simpleName}: " +
                        "${e.message ?: "nessun dettaglio"}"
                )
            }
            if (response.status.isSuccess()) {
                resolvedModel = candidate
                val answer = extractGeneratedText(response.bodyAsText())
                    ?: return AiTextResult.Failure("Google ha risposto senza contenuto utilizzabile.")
                return AiTextResult.Success(answer, "Google Gemini ($candidate)")
            }
            val body = try { response.bodyAsText() } catch (e: Exception) { "" }
            lastFailure = "HTTP ${response.status.value} con il modello $candidate: ${body.take(200)}"
            if (!isModelUnavailable(response.status.value, body)) return AiTextResult.Failure(lastFailure)
            // Un modello che ha appena risposto 503 non va piu' tenuto come "risolto": la
            // prossima domanda deve ripartire dalla scaletta invece di ributtarsi sullo stesso.
            if (resolvedModel == candidate) resolvedModel = null
        }

        val discovered = discoverUsableModel()
        if (discovered != null) {
            val response = postGenerate(discovered, text)
            if (response.status.isSuccess()) {
                resolvedModel = discovered
                val answer = extractGeneratedText(response.bodyAsText())
                if (answer != null) return AiTextResult.Success(answer, "Google Gemini ($discovered)")
            }
        }
        return AiTextResult.Failure(lastFailure)
    }

    /** Il testo del primo candidato di una risposta generateContent, o `null` se non c'e'. */
    private fun extractGeneratedText(raw: String): String? = try {
        json.parseToJsonElement(raw).jsonObject["candidates"]?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
    } catch (e: Exception) {
        null
    }

    /**
     * Prova la chiave con una chiamata minima e racconta com'è andata, per esteso.
     *
     * Serve al pulsante "Prova la chiave" delle Impostazioni. Nasce da un problema concreto: se
     * l'analisi AI non funzionava, l'app ricadeva in silenzio sull'euristica a parole chiave e
     * l'unico sintomo era un riassunto scadente. Non c'era modo di sapere se la colpa fosse
     * della chiave, della quota, del modello ritirato o della rete — e Simone non poteva
     * verificarlo. Qui la risposta di Google viene riportata così com'è.
     */
    /** Implementazione di [AiClassifier.testConfiguration]: qui coincide con [testKey]. */
    override suspend fun testConfiguration(): String = testKey()

    suspend fun testKey(): String {
        if (userApiKey.isBlank()) {
            return "Nessuna chiave inserita."
        }

        val candidates = buildList {
            resolvedModel?.let { add(it) }
            add(model)
            addAll(MODEL_LADDER)
        }.distinct()

        var lastFailure: String? = null
        for (candidate in candidates) {
            val response = try {
                postGenerate(candidate, "Rispondi solo con: ok")
            } catch (e: Exception) {
                return "Non sono riuscito a raggiungere Google: ${e::class.simpleName}: " +
                    "${e.message ?: "nessun dettaglio"}. Controlla la connessione."
            }
            if (response.status.isSuccess()) {
                resolvedModel = candidate
                return "Chiave valida. Modello in uso: $candidate."
            }
            val body = try { response.bodyAsText() } catch (e: Exception) { "" }
            lastFailure = "HTTP ${response.status.value} ($candidate): ${body.take(240)}"
            if (!isModelUnavailable(response.status.value, body)) {
                // Chiave sbagliata, quota esaurita, API non abilitata: provare altri modelli
                // non cambierebbe nulla, meglio riportare subito cosa ha detto Google.
                return "La chiave è stata rifiutata. Risposta di Google: $lastFailure"
            }
        }

        val discovered = discoverUsableModel()
        if (discovered != null) {
            return "Chiave valida, ma nessuno dei modelli previsti è disponibile. " +
                "Ne userò uno rilevato automaticamente: $discovered."
        }
        return "Nessun modello utilizzabile con questa chiave. Ultimo errore: " +
            "${lastFailure ?: "sconosciuto"}"
    }

    /** Una singola chiamata generateContent al modello indicato. */
    private suspend fun postGenerate(modelName: String, prompt: String): HttpResponse =
        httpClient.post("$API_BASE/$modelName:generateContent") {
            // Chiave sia in header (forma documentata da Google) sia come parametro: se una delle
            // due venisse ignorata l'altra regge, e non costa nulla mandarle entrambe.
            header("x-goog-api-key", userApiKey)
            parameter("key", userApiKey)
            contentType(ContentType.Application.Json)
            setBody(GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt))))))
        }

    /**
     * Chiede a Google l'elenco dei modelli utilizzabili con questa chiave e ne sceglie uno adatto:
     * deve supportare `generateContent` e si preferisce un "flash" stabile (più veloce e con
     * quota gratuita più generosa), scartando anteprime e modelli specializzati.
     */
    private suspend fun discoverUsableModel(): String? {
        return try {
            val response = httpClient.get(API_BASE) {
                header("x-goog-api-key", userApiKey)
                parameter("key", userApiKey)
                parameter("pageSize", "200")
            }
            if (!response.status.isSuccess()) return null
            val models = json.parseToJsonElement(response.bodyAsText())
                .jsonObject["models"]?.jsonArray.orEmpty()
                .mapNotNull { element ->
                    val obj = element as? JsonObject ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                        ?.removePrefix("models/") ?: return@mapNotNull null
                    val methods = obj["supportedGenerationMethods"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonPrimitive.contentOrNull }
                    if ("generateContent" !in methods) null else name
                }
                .filter { name ->
                    val lower = name.lowercase()
                    listOf("embedding", "vision", "tts", "live", "image", "aqa")
                        .none { lower.contains(it) }
                }
            models.firstOrNull { it.contains("flash") && !it.contains("preview") }
                ?: models.firstOrNull { !it.contains("preview") }
                ?: models.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * `true` quando vale la pena provare il modello successivo della scaletta, `false` quando
     * cambiare modello non cambierebbe niente (chiave sbagliata, quota finita, rete assente).
     *
     * Copre due casi diversi che portano alla stessa decisione. Il primo e' il modello ritirato,
     * che risponde 404. Il secondo e' il **sovraccarico temporaneo**: Google risponde 503
     * `UNAVAILABLE` — "This model is currently experiencing high demand" — e prima di questa
     * modifica l'app si fermava li', perche' 503 non e' 404. Ma un sovraccarico riguarda *quel*
     * modello, non la chiave: `gemini-flash-lite-latest` gira su una capacita' diversa e
     * risponde mentre `gemini-flash-latest` e' pieno. Fermarsi al primo 503 significava dire
     * "non sono riuscito a rispondere" avendo in tasca quattro alternative non provate.
     */
    private fun isModelUnavailable(statusCode: Int, body: String): Boolean {
        if (statusCode == 404 || statusCode == 503) return true
        val lower = body.lowercase()
        return lower.contains("not found") ||
            lower.contains("is not supported") ||
            lower.contains("does not exist") ||
            lower.contains("has been deprecated") ||
            lower.contains("unavailable") ||
            lower.contains("overloaded") ||
            lower.contains("high demand")
    }

    /** Estrae badge, riassunto e scadenze dalla risposta di Gemini. */
    private fun parseGeminiResponse(circularNumber: Int, raw: String, modelUsed: String): GeminiCallResult {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            return GeminiCallResult.Failure("risposta non JSON valida da Google AI Studio: ${raw.take(200)}")
        }
        val text = root["candidates"]?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.getOrNull(0)
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: return GeminiCallResult.Failure("risposta di Google AI Studio senza contenuto utilizzabile: ${raw.take(200)}")

        val parsed = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return GeminiCallResult.Failure("il modello non ha risposto in JSON come richiesto: ${text.take(200)}")
        }
        // Come nel prompt locale (vedi CircularClassificationPrompt.parse): un badge che non e'
        // uno dei tre valori validi non va sostituito in silenzio con POTENTIAL. Sarebbe mostrare
        // in app una classificazione che Gemini non ha mai dato, indistinguibile da una vera.
        // Meglio dichiarare la risposta malformata: il chiamante mostra il fallback con il
        // motivo, invece di un giudizio inventato.
        val rawBadge = parsed["badge"]?.jsonPrimitive?.content
        val badge = rawBadge?.let {
            try {
                CircularRelevanceBadge.valueOf(it)
            } catch (e: Exception) {
                null
            }
        } ?: return GeminiCallResult.Failure(
            "Google AI Studio ha risposto con un badge non valido (\"${rawBadge ?: "assente"}\"): ${text.take(200)}"
        )

        val summary = parsed["summary"]?.jsonPrimitive?.content
            ?: "Analisi AI disponibile senza riassunto dettagliato."

        val deadlines = parsed["deadlines"]?.jsonArray.orEmpty().mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val dueDate = obj["dueDate"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ExtractedDeadline(
                title = title,
                dueDate = dueDate,
                time = obj["time"]?.jsonPrimitive?.contentOrNull,
                category = obj["category"]?.jsonPrimitive?.content ?: "AVVISO"
            )
        }

        return GeminiCallResult.Success(
            CircularAiClassification(
                circularNumber = circularNumber,
                badge = badge,
                personalSummary = summary,
                detectedDeadlines = deadlines,
                modelLabel = "Google Gemini ($modelUsed)"
            )
        )
    }

    /**
     * Ripiego a parole chiave, ora condiviso con [LocalAiClassifier] tramite
     * [HeuristicClassification]. La copia che stava qui aveva regole sbagliate — marcava come
     * "solo per docenti" qualunque circolare citasse il Consiglio di Istituto, studenti compresi.
     */
    private fun fallbackHeuristicClassification(
        circularNumber: Int,
        title: String,
        text: String,
        failureReason: String?
    ): CircularAiClassification = HeuristicClassification.classify(
        circularNumber = circularNumber,
        title = title,
        text = text,
        failureReason = failureReason,
        notConfiguredMessage = "Nessuna API Key AI configurata: aprila dalle Impostazioni, " +
            "oppure scarica un modello per l'AI locale."
    )
}
