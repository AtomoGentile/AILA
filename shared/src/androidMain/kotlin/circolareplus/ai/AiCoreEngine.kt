package circolareplus.ai

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

    fun isAvailable(): Boolean = model != null

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
            val generationConfig = GenerationConfig.Builder()
                .setContext(context)
                .setMaxOutputTokens(maxOutputTokens)
                .setCallbackExecutor(callbackExecutor)
                .setWorkerExecutor(workerExecutor)
                .build()
            val candidate = GenerativeModel(generationConfig, DownloadConfig(callback))
            candidate.prepareInferenceEngine()
            model = candidate
            if (!outcome.isCompleted) outcome.complete(true)
            outcome.await()
        } catch (e: GenerativeAIException) {
            lastFailureReason = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            false
        } catch (e: Exception) {
            lastFailureReason = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            false
        }
    }

    /**
     * @throws IllegalStateException se AICore non è ancora stato preparato — [LocalLlm]
     * intercetta e ricade sul modello LiteRT-LM, esattamente come già fa per un fallimento GPU.
     */
    suspend fun generate(systemPrompt: String, userPrompt: String, timeoutMillis: Long): String {
        val activeModel = model ?: throw IllegalStateException(unavailableReason())
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
