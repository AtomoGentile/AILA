package circolareplus.ai

import android.os.Build
import circolareplus.platform.AndroidAppContext
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerateContentResponse
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * Tier 1 Android: Gemini Nano di sistema via **AICore** (`com.google.ai.edge.aicore`).
 *
 * Scritta contro l'API reale documentata su
 * developer.android.com/ai/reference/com/google/ai/edge/aicore (classi/metodi verificati con una
 * ricerca web il 20/9/2026: `GenerativeModel`, `GenerationConfig.Builder`,
 * `DownloadConfig(DownloadCallback)`, `DownloadCallback` con `onDownloadStarted/Progress/
 * Completed/Failed/DidNotStart/Pending`, `GenerateContentResponse.getText()`). **Non è mai stata
 * compilata né eseguita**: questo ambiente non ha un dispositivo Android con AICore, e la
 * dipendenza Gradle è stata attivata solo ora (coordinate confermate su mvnrepository.com, non
 * più una supposizione) — la prima verifica reale resta `:androidApp:assembleDebug` su una
 * macchina vera.
 *
 * **Non esiste un metodo di stato** ("è disponibile?") separato in `GenerativeModel`: la
 * disponibilità si scopre chiamando [prepareInferenceEngine], che scarica il modello di sistema
 * se serve (da cui il collegamento con [LocalModelStore.download] — "scaricare" AICore è
 * letteralmente prepararlo la prima volta, non un file gestito da questa app).
 *
 * **AICore è stateless**: a differenza di Apple Intelligence (che mantiene una sessione con
 * storico lato Swift) non c'è conversazione — [generate] concatena system+user prompt a ogni
 * chiamata, esattamente come indicato nel piano di partenza.
 *
 * **Risposta vuota su un prompt che chiede JSON puro** (segnalato in campo: `response.text` torna
 * `""`, non `null`, quindi nessuna eccezione — il fallimento emergeva solo piu' avanti, in
 * [LocalAiClassifier], come "non ha risposto in JSON" senza alcun dettaglio dopo i due punti).
 * [generate] ora lancia un errore che riporta `finishReason` e la lunghezza del prompt. Prima
 * ritentava con un promemoria sul formato, ma rimandare lo stesso prompt non cambia niente se la
 * causa e' la lunghezza o un filtro: il ritentativo utile (meno testo di PDF) sta in
 * [LocalAiClassifier], che riconosce "testo vuoto" nel messaggio.
 */
internal object AiCoreEngine {

    @Volatile
    private var model: GenerativeModel? = null

    @Volatile
    private var lastFailureReason: String? = null

    // Richiesti (non-null) da GenerationConfig.Builder. Un solo thread per i callback di
    // download (eventi rari e in ordine), un pool piccolo per il lavoro dell'engine.
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val workerExecutor = Executors.newFixedThreadPool(2)

    /** Tempo concesso alla generazione di prova durante "Attiva". */
    private const val PROBE_TIMEOUT_MS = 30_000L

    private const val PREFS = "aicore"
    private const val KEY_PREPARED = "prepared"

    /**
     * `true` se AICore e' pronto **o lo e' stato in un avvio precedente**.
     *
     * Prima valeva solo `model != null`, cioe' lo stato in memoria del processo: dopo ogni
     * riavvio dell'app AICore risultava "non installato" anche se il modello di sistema era
     * pronto da tempo, `LocalModelStore.installedPath` restituiva `null`, il classificatore
     * dichiarava "nessun modello locale" e la catena passava al cloud (Gemini Flash) senza mai
     * provare AICore. Il flag persistente dice che la preparazione e' gia' andata a buon fine;
     * [generate] ricrea il `GenerativeModel` da solo alla prima chiamata.
     */
    fun isAvailable(): Boolean = model != null || wasPreparedBefore()

    private fun wasPreparedBefore(): Boolean = try {
        AndroidAppContext.getOrNull()
            ?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            ?.getBoolean(KEY_PREPARED, false) == true
    } catch (e: Exception) {
        false
    }

    private fun rememberPrepared(prepared: Boolean) {
        try {
            AndroidAppContext.getOrNull()
                ?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                ?.edit()?.putBoolean(KEY_PREPARED, prepared)?.apply()
        } catch (e: Exception) {
            // Solo un'ottimizzazione: senza flag si torna al comportamento di prima.
        }
    }

    /**
     * Messaggio leggibile per un errore di AICore.
     *
     * "AICore failed with error type 2-INFERENCE_ERROR and error code 8-NOT_AVAILABLE: Required
     * LLM feature not found" (visto sul campo con AICore installato e attivo) non vuol dire che
     * AICore manchi: vuol dire che su quel telefono il servizio non offre Gemini Nano alle app
     * esterne. Mostrato cosi' com'era sembrava un guasto da riprovare; non lo e'.
     */
    private fun describe(e: Throwable): String {
        val raw = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
        return if (isFeatureMissing(raw)) {
            "Su questo telefono AICore non mette Gemini Nano a disposizione delle app " +
                "(errore NOT_AVAILABLE). Scegli un altro modello, per esempio Gemma 4 E2B, " +
                "oppure usa Google AI Studio. Dettaglio: $raw"
        } else {
            raw
        }
    }

    private fun isFeatureMissing(message: String): Boolean =
        message.contains("NOT_AVAILABLE") || message.contains("feature not found", ignoreCase = true)

    /**
     * Dopo un errore che dice "non supportato" AICore non va piu' considerato pronto: prima il
     * flag persistente restava vero e l'app continuava a sceglierlo e a fallire a ogni analisi.
     */
    private fun forgetIfUnsupported(message: String) {
        if (!isFeatureMissing(message)) return
        model = null
        rememberPrepared(false)
    }

    fun unavailableReason(): String =
        lastFailureReason ?: "AICore non ancora preparato: scaricalo dalle Impostazioni."

    /**
     * Prepara il motore AICore, scaricando il modello di sistema se necessario. Chiamata da
     * [LocalModelStore.download] quando l'utente tocca "Scarica" sulla voce AICore delle
     * Impostazioni — con AICore quel tasto non scarica un file nostro, avvia proprio questa
     * preparazione.
     */
    suspend fun prepare(maxOutputTokens: Int, onProgress: (downloaded: Long, total: Long) -> Unit): Boolean {
        model?.let { return true }
        // La libreria richiede API 31 (l'app parte da 26, con override nel manifest): sotto, non
        // va nemmeno toccata, o le sue classi lancerebbero al primo uso.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            lastFailureReason = "AICore richiede Android 12 o successivo."
            return false
        }
        val context = AndroidAppContext.getOrNull() ?: run {
            lastFailureReason = "AI locale non ancora inizializzata."
            return false
        }

        val outcome = CompletableDeferred<Boolean>()
        val callback = object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {
                onProgress(0L, bytesToDownload)
            }

            override fun onDownloadProgress(totalBytesDownloaded: Long) {
                // Il totale non è noto ad ogni progress: -1 segnala "totale sconosciuto in
                // questo aggiornamento", chi osserva onProgress tiene l'ultimo totale valido.
                onProgress(totalBytesDownloaded, -1L)
            }

            override fun onDownloadCompleted() {
                onProgress(1L, 1L)
            }

            override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                lastFailureReason = "$failureStatus: ${e.message ?: e::class.simpleName}"
                outcome.complete(false)
            }

            override fun onDownloadDidNotStart(e: GenerativeAIException) {
                lastFailureReason = e.message ?: (e::class.simpleName ?: "download non avviato")
                outcome.complete(false)
            }
        }

        return try {
            // Il Builder di 0.0.1-exp02 espone proprietà (var), non setter concatenabili: i
            // metodi setX() restituiscono Unit.
            val generationConfig = GenerationConfig.Builder().apply {
                this.context = context
                this.maxOutputTokens = maxOutputTokens
                // Senza questi il campionamento e' quello di default, pensato per la
                // conversazione: qui serve quasi sempre un JSON, e la temperatura alta lo rompe.
                // Valori dell'esempio ufficiale di AICore.
                this.temperature = 0.2f
                this.topK = 16
                this.candidateCount = 1
                this.callbackExecutor = this@AiCoreEngine.callbackExecutor
                this.workerExecutor = this@AiCoreEngine.workerExecutor
            }.build()
            val candidate = GenerativeModel(generationConfig, DownloadConfig(callback))
            candidate.prepareInferenceEngine()
            if (!outcome.isCompleted) outcome.complete(true)
            if (!outcome.await()) {
                rememberPrepared(false)
                return false
            }
            // Motore pronto non vuol dire modello utilizzabile: su alcuni telefoni la
            // preparazione riesce e poi ogni generazione fallisce con NOT_AVAILABLE. Una prova
            // minuscola qui fa fallire subito "Attiva", con il motivo, invece di ogni analisi.
            withTimeoutOrNull(PROBE_TIMEOUT_MS) { candidate.generateContent("Rispondi solo: OK") }
                ?: throw IllegalStateException("AICore non ha risposto alla prova entro ${PROBE_TIMEOUT_MS / 1000} secondi.")
            model = candidate
            rememberPrepared(true)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastFailureReason = describe(e)
            model = null
            rememberPrepared(false)
            false
        }
    }

    /**
     * @throws IllegalStateException se AICore non è ancora stato preparato — [LocalLlm]
     * intercetta e ricade sul modello LiteRT-LM, esattamente come già fa per un fallimento GPU.
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        maxOutputTokens: Int
    ): String {
        // Dopo un riavvio `model` e' null anche se AICore era gia' pronto (vedi isAvailable):
        // si ricrea qui, senza far ripassare l'utente dalle Impostazioni. Se non riesce, il
        // motivo vero (dispositivo non supportato, Gemini Nano non ancora scaricato...) finisce
        // nell'eccezione invece di sparire dietro il ripiego sul cloud.
        if (model == null) prepare(maxOutputTokens) { _, _ -> }
        val activeModel = model ?: throw IllegalStateException(unavailableReason())

        // Un solo tentativo: rimandare lo STESSO prompt dopo una risposta vuota non cambia niente
        // quando la causa e' la lunghezza o un filtro sul contenuto (era il ritentativo di prima,
        // con un promemoria sul formato). Chi chiama sa riprovare con meno testo: vedi
        // LocalAiClassifier, che riconosce l'errore "testo vuoto" e ricostruisce il prompt.
        return generateOnce(activeModel, systemPrompt, userPrompt, timeoutMillis)
    }

    private suspend fun generateOnce(
        activeModel: GenerativeModel,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long
    ): String {
        val prompt = "$systemPrompt\n\n$userPrompt"
        val response = withTimeoutOrNull(timeoutMillis) {
            try {
                activeModel.generateContent(prompt)
            } catch (e: GenerativeAIException) {
                val reason = describe(e)
                forgetIfUnsupported(reason)
                lastFailureReason = reason
                throw IllegalStateException(reason, e)
            }
        } ?: throw IllegalStateException("AICore non ha risposto entro ${timeoutMillis / 1000} secondi.")

        // `response.text` e' solo la PRIMA parte di testo: se il modello risponde con piu' parti,
        // o con una prima parte vuota, sembra una risposta vuota anche quando non lo e'.
        val direct = textOf(response)
        if (direct.isNotBlank()) return direct

        // Ultimo tentativo: la stessa richiesta in streaming, che passa da un altro percorso del
        // servizio di sistema. Costa una seconda generazione solo quando la prima e' vuota.
        val streamed = withTimeoutOrNull(timeoutMillis) {
            try {
                activeModel.generateContentStream(prompt).toList().joinToString("") { textOf(it) }
            } catch (e: GenerativeAIException) {
                ""
            }
        }.orEmpty()
        if (streamed.isNotBlank()) return streamed

        // finishReason: 0 = STOP (il modello ha chiuso da solo, senza scrivere niente), 1 =
        // MAX_TOKENS. Verificato sulle costanti dell'SDK: un commento precedente le dava sbagliate
        // (1 e 2). Corto di proposito: il messaggio finisce in una riga di 140 caratteri (vedi
        // HeuristicClassification.shortenReason) e i valori che servono sono in fondo. "testo vuoto"
        // e' il segnale con cui LocalAiClassifier decide di riprovare con un prompt diverso.
        val candidate = response.candidates.firstOrNull()
        val finish = when (val reason = candidate?.finishReason) {
            0 -> "STOP"
            1 -> "MAX_TOKENS"
            else -> reason.toString()
        }
        throw IllegalStateException(
            "testo vuoto da AICore (finish=$finish, parti=${candidate?.content?.parts?.size}, ${prompt.length} car.)"
        )
    }

    private fun textOf(response: GenerateContentResponse): String =
        response.text?.takeIf { it.isNotBlank() }
            ?: response.candidates
                .flatMap { it.content.parts }
                .filterIsInstance<TextPart>()
                .joinToString("") { it.text }
}
