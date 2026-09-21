package circolareplus.ai

import android.os.Build
import circolareplus.platform.AndroidAppContext
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import kotlinx.coroutines.CompletableDeferred
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
 * [generate] ora ritenta una volta con un promemoria piu' esplicito prima di arrendersi: non e'
 * mai stato verificato su un dispositivo reale se questo basti (nessun dispositivo Android con
 * AICore disponibile qui), ma non ha effetti collaterali quando la causa e' un'altra.
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
                this.callbackExecutor = this@AiCoreEngine.callbackExecutor
                this.workerExecutor = this@AiCoreEngine.workerExecutor
            }.build()
            val candidate = GenerativeModel(generationConfig, DownloadConfig(callback))
            candidate.prepareInferenceEngine()
            model = candidate
            if (!outcome.isCompleted) outcome.complete(true)
            outcome.await().also { rememberPrepared(it) }
        } catch (e: GenerativeAIException) {
            lastFailureReason = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            rememberPrepared(false)
            false
        } catch (e: Exception) {
            lastFailureReason = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
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

        val firstAttempt = generateOnce(activeModel, systemPrompt, userPrompt, timeoutMillis)
        if (firstAttempt.isNotBlank()) return firstAttempt

        // Il sintomo segnalato non e' un'eccezione ne' un JSON scritto male: e' `response.text`
        // vuoto ("" non null), che generateOnce restituisce senza errori — arrivava cosi'
        // com'e' fino a CircularClassificationPrompt.extractJsonObject, che ovviamente non trova
        // nessuna '{' e fallisce con un messaggio senza alcun dettaglio ("non ha risposto in
        // JSON: ", niente dopo i due punti). Prima di arrendersi si ritenta una volta sola con un
        // promemoria piu' esplicito: un secondo giro che non cambia nulla quando la risposta vuota
        // ha un'altra causa (filtro di sicurezza, modello non ancora pronto) non costa quasi
        // niente in piu' di un errore comunque gia' scritto, e recupera i casi in cui basta
        // insistere sul formato perche' il modello risponda.
        val reinforcedPrompt = "$userPrompt\n\nRispondi SOLO con l'oggetto JSON richiesto qui sopra: non lasciare la risposta vuota."
        val secondAttempt = generateOnce(activeModel, systemPrompt, reinforcedPrompt, timeoutMillis)
        if (secondAttempt.isNotBlank()) return secondAttempt

        throw IllegalStateException(
            "AICore ha risposto con testo vuoto per due volte: possibile filtro di sicurezza o " +
                "un prompt che il modello di sistema non riesce a completare."
        )
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
                throw IllegalStateException("${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}", e)
            }
        } ?: throw IllegalStateException("AICore non ha risposto entro ${timeoutMillis / 1000} secondi.")
        return response.text ?: throw IllegalStateException("AICore non ha prodotto testo.")
    }
}
