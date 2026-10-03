package circolareplus.ai

import android.os.Build
import android.os.Looper
import circolareplus.platform.AndroidAppContext
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentResponse
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tier 1 Android: Gemini Nano di sistema via **ML Kit GenAI Prompt API**
 * (`com.google.mlkit:genai-prompt`), che parla con AICore.
 *
 * Prima si usava `com.google.ai.edge.aicore:aicore:0.0.1-exp02`, sperimentale e ferma da marzo
 * 2025, che funzionava su pochi telefoni e non aveva un modo di chiedere "Gemini Nano c'e'?":
 * lo si scopriva preparando il motore e facendo una generazione di prova ("Rispondi solo: OK"),
 * si teneva un flag `prepared` nelle SharedPreferences per non perderlo al riavvio e si
 * riconosceva NOT_AVAILABLE dal testo dell'errore. ML Kit ha `checkStatus()` (disponibile, da
 * scaricare, in scaricamento, non disponibile) e `download()`: niente flag, niente prova.
 *
 * Classi e metodi usati sono quelli della documentazione ufficiale e del sample
 * googlesamples/mlkit (android/genai, OpenPromptActivity) per la 1.0.0-beta4:
 * `Generation.getClient()`, `checkStatus()`, `download()` con `DownloadStatus`,
 * `generateContentRequest(TextPart(...)) { temperature; topK; maxOutputTokens; candidateCount }`,
 * `generateContent`, `generateContentStream`, `Candidate.text`/`finishReason`. **Compilata
 * solo dalla CI, mai provata su un telefono**: vedi TODO.md.
 *
 * La traduzione di stati ed errori in [AiCoreState]/[AiCoreFailure] sta in commonMain
 * ([AiCoreStatusMapper]), cosi' si prova con i test comuni; qui si convertono solo le costanti
 * dell'SDK.
 *
 * **Prompt senza stato**: system e user prompt concatenati a ogni chiamata, come con la
 * libreria di prima (verificato in campo su un S26 Ultra). Le istruzioni di sistema separate
 * esistono solo dalla beta3, e la beta3 chiedeva ad AICore una funzione che i telefoni non
 * avevano (googlesamples/mlkit#1061): meglio non dipenderne.
 *
 * **Risposta vuota su un prompt che chiede JSON puro** (segnalato in campo: testo `""`, nessuna
 * eccezione). [generate] lancia un errore che riporta `finishReason` e la lunghezza del prompt e
 * comincia con "testo vuoto": il ritentativo utile (meno testo di PDF) sta in
 * [LocalAiClassifier], che lo riconosce.
 */
internal object AiCoreEngine {

    @Volatile
    private var model: GenerativeModel? = null

    /** Ultimo stato letto da `checkStatus()`, `null` finche' non lo si e' chiesto. */
    @Volatile
    private var lastState: AiCoreState? = null

    @Volatile
    private var lastFailureReason: String? = null

    /** Per rileggere lo stato senza bloccare chi chiama [isAvailable] dal thread principale. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var refreshing = false

    @Volatile
    private var lastStateAt = 0L

    /**
     * Ogni quanto rileggere lo stato in sottofondo: [isAvailable] la chiedono anche le
     * Impostazioni a ogni ridisegno, e una chiamata ad AICore per ognuno sarebbe sprecata.
     */
    private const val STATE_MAX_AGE_MS = 30_000L

    /** Tetto a una singola chiamata di `checkStatus()`. */
    private const val CHECK_STATUS_TIMEOUT_MS = 10_000L

    /** Ogni quanto controllare se il download di Gemini Nano si e' fermato. */
    private const val DOWNLOAD_WATCH_INTERVAL_MS = 1_000L

    /** Tetto alla lettura dello stato quando si puo' aspettare (fuori dal thread principale). */
    private const val STATUS_TIMEOUT_MS = 2_000L

    /**
     * `true` se Gemini Nano e' scaricato e pronto, secondo l'ultimo `checkStatus()`.
     *
     * Sincrona perche' la chiede [LocalModelStore.isInstalled]. La prima volta nel processo lo
     * stato non c'e' ancora: fuori dal thread principale (analisi, Worker) lo si legge subito,
     * con un tetto di [STATUS_TIMEOUT_MS]; sul thread principale (Impostazioni) si risponde con
     * quello che si sa e lo si rilegge in sottofondo, per non bloccare l'interfaccia.
     */
    fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        lastState?.let {
            refreshAsync()
            return AiCoreStatusMapper.canGenerate(it)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshAsync()
            return false
        }
        val state = runBlocking { withTimeoutOrNull(STATUS_TIMEOUT_MS) { readState() } }
        return state != null && AiCoreStatusMapper.canGenerate(state)
    }

    private fun refreshAsync() {
        if (refreshing) return
        if (lastState != null && System.currentTimeMillis() - lastStateAt < STATE_MAX_AGE_MS) return
        refreshing = true
        scope.launch {
            try {
                readState()
            } finally {
                refreshing = false
            }
        }
    }

    fun unavailableReason(): String =
        lastFailureReason ?: "AICore non ancora preparato: attivalo dalle Impostazioni."

    // Configurazione vuota come nel sample ufficiale: temperatura e tetto ai token vanno per
    // richiesta (vedi generateOnce).
    private fun client(): GenerativeModel =
        model ?: Generation.getClient(generationConfig {}).also { model = it }

    /** Chiede lo stato ad AICore e lo ricorda. Non lancia: un errore diventa [AiCoreState.Failed]. */
    private suspend fun readState(): AiCoreState {
        val state = try {
            // Con un tetto: anche checkStatus puo' restare senza risposta se il servizio di
            // sistema e' bloccato, e chi aspetta qui e' l'onboarding o un'analisi.
            withTimeoutOrNull(CHECK_STATUS_TIMEOUT_MS) { client().checkStatus() }
                ?.let { AiCoreStatusMapper.fromFeatureStatus(featureStatusOf(it)) }
                ?: AiCoreState.Failed(
                    AiCoreStatusMapper.classifyError(
                        null,
                        "AICore non ha detto lo stato di Gemini Nano entro " +
                            "${CHECK_STATUS_TIMEOUT_MS / 1000} secondi."
                    )
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // checkStatus stesso puo' lanciare (visto in campo: "606-FEATURE_NOT_FOUND" sui
            // telefoni senza la funzione richiesta).
            AiCoreState.Failed(failureOf(e))
        }
        lastState = state
        lastStateAt = System.currentTimeMillis()
        when (state) {
            is AiCoreState.Unavailable -> lastFailureReason = state.message
            is AiCoreState.Failed -> lastFailureReason = state.failure.message
            AiCoreState.Available -> lastFailureReason = null
            else -> Unit
        }
        return state
    }

    /**
     * Prepara Gemini Nano, scaricandolo se serve. Chiamata da [LocalModelStore.download] quando
     * l'utente tocca "Attiva" sulla voce AICore delle Impostazioni: con AICore quel tasto non
     * scarica un file nostro, chiede al sistema il modello.
     */
    suspend fun prepare(maxOutputTokens: Int, onProgress: (downloaded: Long, total: Long) -> Unit): Boolean {
        // AICore esiste solo da Android 12 in su (l'app parte da 26, con override nel manifest):
        // sotto, le classi di ML Kit non vanno nemmeno toccate.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            lastFailureReason = "AICore richiede Android 12 o successivo."
            return false
        }
        if (AndroidAppContext.getOrNull() == null) {
            lastFailureReason = "AI locale non ancora inizializzata."
            return false
        }
        val state = readState()
        if (AiCoreStatusMapper.canGenerate(state)) return true
        if (!AiCoreStatusMapper.needsDownload(state)) return false
        return download(state, onProgress)
    }

    private suspend fun download(
        state: AiCoreState,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        val watch = AiCoreDownloadWatch(now = System::currentTimeMillis)
        var completed = false
        var failed = false
        var stalled = false
        try {
            coroutineScope {
                val collector = launch {
                    // transformWhile: il Flow si chiude da solo dopo l'esito, anche se ML Kit non
                    // lo chiudesse (prima un DownloadFailed lasciava l'attesa aperta per sempre).
                    client().download()
                        .transformWhile { status ->
                            emit(status)
                            status !is DownloadStatus.DownloadCompleted &&
                                status !is DownloadStatus.DownloadFailed
                        }
                        .collect { status ->
                            when (status) {
                                is DownloadStatus.DownloadStarted -> {
                                    watch.onStarted(status.bytesToDownload)
                                    onProgress(0L, watch.totalBytes)
                                }
                                // Il totale arriva solo all'inizio: si passa sempre l'ultimo noto.
                                is DownloadStatus.DownloadProgress -> {
                                    watch.onProgress(status.totalBytesDownloaded)
                                    onProgress(watch.downloadedBytes, watch.totalBytes)
                                }
                                is DownloadStatus.DownloadCompleted -> {
                                    completed = true
                                    onProgress(watch.totalBytes.coerceAtLeast(1L), watch.totalBytes.coerceAtLeast(1L))
                                }
                                is DownloadStatus.DownloadFailed -> {
                                    failed = true
                                    lastFailureReason = failureOf(status.e).message
                                }
                            }
                        }
                }
                // Il sistema puo' non far partire mai il download: senza questo controllo "Attiva"
                // restava su "0 MB di 0 MB" finche' l'utente non annullava.
                while (collector.isActive) {
                    delay(DOWNLOAD_WATCH_INTERVAL_MS)
                    if (watch.isStalled()) {
                        stalled = true
                        collector.cancel()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastFailureReason = failureOf(e).message
            return false
        }
        if (stalled) {
            lastFailureReason = watch.stalledMessage(state.statusName())
            return false
        }
        if (failed) return false
        if (!completed) {
            lastFailureReason = "AICore ha chiuso il download di Gemini Nano senza un esito " +
                "(stato: ${state.statusName()}). Riprova piu' tardi da Impostazioni."
            return false
        }
        val after = readState()
        if (!AiCoreStatusMapper.canGenerate(after)) {
            lastFailureReason = "Download di Gemini Nano finito, ma AICore lo dice ancora " +
                "${after.statusName()}. Riprova fra qualche minuto."
            return false
        }
        return true
    }

    /**
     * @throws IllegalStateException se Gemini Nano non e' pronto o la generazione fallisce:
     * [LocalAiClassifier] lo trasforma nel ripiego, e il messaggio contiene il nome del codice
     * (lo usa [AiCoreCooldown]).
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        maxOutputTokens: Int
    ): String {
        // Lo stato si richiede a ogni generazione: costa poco, e dice subito se nel frattempo
        // Gemini Nano e' sparito (aggiornamento di sistema) invece di scoprirlo da un errore.
        val state = readState()
        if (!AiCoreStatusMapper.canGenerate(state)) {
            throw IllegalStateException(
                when (state) {
                    is AiCoreState.Unavailable -> state.message
                    is AiCoreState.Failed -> state.failure.message
                    else -> "Gemini Nano non e' ancora sul telefono: attivalo dalle Impostazioni."
                }
            )
        }

        // Si ritenta, fino a TRANSIENT_ATTEMPTS volte e con una pausa, solo un errore del servizio
        // che non dipende dal prompt (BUSY, RESPONSE_PROCESSING_ERROR, collegamento caduto):
        // compare ogni tanto e la stessa richiesta subito dopo va. Con il collegamento caduto si
        // ricrea il client prima di riprovare.
        var attempt = 1
        while (true) {
            try {
                return generateOnce(client(), "$systemPrompt\n\n$userPrompt", timeoutMillis, maxOutputTokens)
            } catch (e: AiCoreServiceException) {
                val failure = e.failure
                lastFailureReason = failure.message
                if (!AiCoreStatusMapper.shouldRetry(failure, attempt)) {
                    throw IllegalStateException(failure.message, e)
                }
                delay(AiCoreStatusMapper.retryDelayMillis(attempt))
                if (AiCoreStatusMapper.needsReconnect(failure)) {
                    runCatching { model?.close() }
                    model = null
                }
                attempt++
            }
        }
    }

    /** Errore del servizio AICore durante una generazione, gia' classificato. */
    private class AiCoreServiceException(val failure: AiCoreFailure, cause: Throwable) :
        Exception(failure.message, cause)

    private suspend fun generateOnce(
        activeModel: GenerativeModel,
        prompt: String,
        timeoutMillis: Long,
        maxOutputTokens: Int
    ): String {
        // Senza questi il campionamento e' quello di default, pensato per la conversazione: qui
        // serve quasi sempre un JSON, e la temperatura alta lo rompe. Valori gia' usati con la
        // libreria di prima. maxOutputTokens ora va per richiesta, non piu' fissato alla
        // preparazione.
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0.2f
            topK = 16
            candidateCount = 1
            this.maxOutputTokens = maxOutputTokens
        }
        val response = withTimeoutOrNull(timeoutMillis) {
            try {
                activeModel.generateContent(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw AiCoreServiceException(failureOf(e), e)
            }
        } ?: throw IllegalStateException("AICore non ha risposto entro ${timeoutMillis / 1000} secondi.")

        val direct = textOf(response)
        if (direct.isNotBlank()) return direct

        // Ultimo tentativo: la stessa richiesta in streaming, che passa da un altro percorso del
        // servizio di sistema. Costa una seconda generazione solo quando la prima e' vuota.
        val streamed = withTimeoutOrNull(timeoutMillis) {
            try {
                activeModel.generateContentStream(request).toList().joinToString("") { textOf(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ""
            }
        }.orEmpty()
        if (streamed.isNotBlank()) return streamed

        val reason = response.candidates.firstOrNull()?.finishReason
        val finish = when (reason) {
            null -> "nessun candidato"
            Candidate.FinishReason.MAX_TOKENS -> "MAX_TOKENS"
            else -> reason.toString()
        }
        throw IllegalStateException(
            AiCoreStatusMapper.emptyAnswerMessage(
                finishReason = finish,
                parts = response.candidates.size,
                promptChars = prompt.length
            )
        )
    }

    private fun textOf(response: GenerateContentResponse): String =
        response.candidates.joinToString("") { it.text }

    private fun featureStatusOf(status: Int): AiCoreFeatureStatus = when (status) {
        FeatureStatus.AVAILABLE -> AiCoreFeatureStatus.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> AiCoreFeatureStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> AiCoreFeatureStatus.DOWNLOADING
        FeatureStatus.UNAVAILABLE -> AiCoreFeatureStatus.UNAVAILABLE
        else -> AiCoreFeatureStatus.UNKNOWN
    }

    private fun failureOf(e: Throwable): AiCoreFailure {
        val raw = "${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
        val code = (e as? GenAiException)?.let { errorCodeOf(it.errorCode) }
        return AiCoreStatusMapper.classifyError(code, raw)
    }

    /**
     * Codici numerici dell'SDK tradotti nei nostri. Solo quelli documentati in
     * `GenAiException.ErrorCode`; gli altri (606-FEATURE_NOT_FOUND, che arriva da AICore) li
     * riconosce [AiCoreStatusMapper.errorCodeFromMessage] dal testo.
     */
    private fun errorCodeOf(code: Int): AiCoreErrorCode? = when (code) {
        GenAiException.ErrorCode.BUSY -> AiCoreErrorCode.BUSY
        GenAiException.ErrorCode.CANCELLED -> AiCoreErrorCode.CANCELLED
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> AiCoreErrorCode.NEEDS_SYSTEM_UPDATE
        GenAiException.ErrorCode.NOT_AVAILABLE -> AiCoreErrorCode.NOT_AVAILABLE
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AiCoreErrorCode.NOT_ENOUGH_DISK_SPACE
        GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR -> AiCoreErrorCode.REQUEST_PROCESSING_ERROR
        GenAiException.ErrorCode.REQUEST_TOO_LARGE -> AiCoreErrorCode.REQUEST_TOO_LARGE
        GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR -> AiCoreErrorCode.RESPONSE_GENERATION_ERROR
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED ->
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> AiCoreErrorCode.BACKGROUND_USE_BLOCKED
        else -> null
    }
}
