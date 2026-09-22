package circolareplus.ai

import circolareplus.platform.AndroidAppContext
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Inferenza on-device tramite **LiteRT-LM**, il runtime di Google per far girare modelli
 * linguistici su Android.
 *
 * **Perché non più MediaPipe `tasks-genai`.** Ci si era partiti perché entrava nel progetto senza
 * toccare la build: era Java puro, mentre LiteRT-LM è Kotlin con metadata 2.4, illeggibile dal
 * compilatore 2.0.20 di allora. Quel compromesso è costato caro e alla fine non ha retto:
 *
 * - `tasks-genai` carica solo modelli con tokenizer **SentencePiece**, in pratica la sola famiglia
 *   Gemma. Qwen, Llama e Mistral fallivano con `SentencePiece tokenizer is not found in the
 *   model` dopo che l'utente aveva già scaricato qualche giga.
 * - Non espone alcuna API di tool calling: la parte agentica si poteva fare solo chiedendo JSON
 *   nel prompt e sperando che il modello lo rispettasse.
 * - È in manutenzione, ferma alla 0.10.35, quindi nessuno di questi limiti sarebbe mai stato
 *   tolto.
 *
 * Passare qui ha richiesto di portare Kotlin a 2.4 e Compose Multiplatform a 1.12: è il motivo di
 * quell'aggiornamento, non una modernizzazione fine a sé stessa.
 */
actual class LocalLlm actual constructor() {

    /**
     * Il motore resta caricato fra una circolare e l'altra: `initialize()` legge centinaia di MB
     * dal disco e può prendersi una decina di secondi. Ricrearlo ogni volta renderebbe la
     * funzione inutilizzabile.
     */
    private var engine: Engine? = null
    private var loadedPath: String? = null

    /**
     * Il backend con cui il modello è stato davvero caricato.
     *
     * Va tenuto e mostrato perché è **la** cosa da sapere quando l'AI locale è lentissima: fra GPU
     * e CPU c'è quasi un fattore dieci in prefill (3.808 contro 557 token al secondo su un S26
     * Ultra, secondo i model card di Google).
     */
    @Volatile
    var lastBackendUsed: String? = null
        private set

    /**
     * Perché il tentativo su GPU è fallito, quando poi il modello è partito su CPU.
     *
     * Senza, si vedrebbe soltanto che gira su CPU, senza sapere se sia una scelta o un ripiego né
     * cosa non abbia funzionato.
     */
    @Volatile
    var lastGpuFailure: String? = null
        private set

    /**
     * Il motore nativo non è rientrante: due generazioni in parallelo lo fanno crashare a livello
     * nativo. Il mutex le mette in coda invece di far cadere l'app.
     */
    private val mutex = Mutex()

    actual suspend fun generate(
        modelPath: String,
        preferGpu: Boolean,
        maxOutputTokens: Int,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean,
        enableThinking: Boolean
    ): String {
        // Tier 1: AICore (Gemini Nano di sistema) invece di LiteRT-LM. Non condivide il motore
        // caricato in `engine`, ma passa comunque dallo stesso mutex: due generazioni AICore in
        // parallelo (due circolari aperte una dopo l'altra) si rubavano NPU e memoria a vicenda
        // e finivano entrambe piu' tardi di quanto avrebbero fatto una dopo l'altra.
        // `stopWhen`/streaming non si applicano (AICore genera in un colpo solo, per ora): vedi
        // AiCoreEngine.kt per lo stato reale dell'integrazione.
        if (modelPath == "aicore") {
            // maxOutputTokens serve solo se AICore va ricreato (dopo un riavvio dell'app):
            // è fissato una volta sola in AiCoreEngine.prepare, dentro GenerationConfig —
            // GenerativeModel non lo accetta per singola chiamata come fa ConversationConfig
            // con LiteRT-LM.
            return mutex.withLock {
                AiCoreEngine.generate(
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    timeoutMillis = timeoutMillis,
                    maxOutputTokens = maxOutputTokens
                )
            }
        }
        return generateWithLiteRtLm(
            modelPath, preferGpu, maxOutputTokens, systemPrompt, userPrompt, timeoutMillis, stopWhen,
            enableThinking
        )
    }

    private suspend fun generateWithLiteRtLm(
        modelPath: String,
        preferGpu: Boolean,
        maxOutputTokens: Int,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean,
        enableThinking: Boolean
    ): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val activeEngine = ensureEngine(modelPath, preferGpu)

            // Qwen3.5 ragiona "ad alta voce" in un blocco <think> prima di rispondere, di default:
            // con un tetto di 900 token il ragionamento si mangia da solo quasi tutto il budget, ed
            // e' la causa dell'attesa infinita sui Qwen. Gemma 4 invece ha il ragionamento spento
            // di default (lo accende il token `<|think|>` nel system prompt, aggiunto da
            // LocalAiClassifier). Qui serve un JSON o una risposta breve, non una dimostrazione:
            // si spegne, e solo l'assistente lo puo' riaccendere, su scelta dell'utente.
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                // Temperatura bassissima e topK stretto: qui non si vuole creatività, si vuole
                // che il modello produca lo stesso JSON ben formato tutte le volte.
                // Con il ragionamento acceso la temperatura bassissima fa entrare i modelli in
                // ripetizioni infinite dentro <think>: si usa quella consigliata per il thinking.
                samplerConfig = if (enableThinking) {
                    SamplerConfig(topK = 20, topP = 0.95, temperature = 0.6)
                } else {
                    SamplerConfig(topK = 20, topP = 0.9, temperature = 0.1)
                },
                // Rete di sicurezza oltre a stopWhen: se il modello sbaglia il formato e non
                // produce mai un JSON completo, senza questo continuerebbe a scrivere fino a
                // riempire il contesto.
                maxOutputToken = maxOutputTokens,
                thinkingConfig = ThinkingConfig(enableThinking = enableThinking)
            )

            activeEngine.createConversation(conversationConfig).use { conversation ->
                val accumulated = StringBuilder()

                // Generazione **in streaming**: si smette appena la risposta utile è arrivata,
                // invece di aspettare che il modello decida da sé di fermarsi. Con un modello
                // piccolo quel momento può non arrivare — dopo il JSON continua a commentare
                // finché non ha riempito la finestra di contesto — ed è la ragione per cui una
                // circolare da una pagina poteva andare avanti per minuti senza concludere.
                //
                // Chiudere il Flow ferma la generazione, quindi `takeWhile` basta da solo: non
                // serve la cancellazione esplicita che richiedeva il runtime precedente.
                val completed = withTimeoutOrNull(timeoutMillis) {
                    conversation.sendMessageAsync(userPrompt)
                        .takeWhile { !stopWhen(accumulated.toString()) }
                        .collect { accumulated.append(it.toString()) }
                    accumulated.toString()
                }

                completed ?: throw IllegalStateException(
                    "Il modello non ha finito entro ${timeoutMillis / 1000} secondi " +
                        "(backend ${lastBackendUsed ?: "sconosciuto"}). " +
                        "Su questo telefono conviene un modello più piccolo."
                )
            }
        }
    }

    /**
     * Carica il modello se serve, provando prima la GPU e poi la CPU.
     *
     * Il ripiego su CPU c'è perché su parecchi chip il driver OpenCL manca o va in errore, e senza
     * ripiego l'AI locale sarebbe semplicemente rotta su quei telefoni. Quando falliscono entrambi
     * l'eccezione riporta **tutti e due** i motivi: l'errore del backend di ripiego da solo fa
     * sembrare corrotto il file scaricato, quando il problema sta a monte.
     */
    private suspend fun ensureEngine(modelPath: String, preferGpu: Boolean): Engine {
        engine?.let { if (loadedPath == modelPath) return it }

        unload()

        val context = AndroidAppContext.require()

        suspend fun create(backend: Backend): Engine {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                // Riduce i tempi dal secondo caricamento in poi: il motore ci tiene le strutture
                // già compilate invece di rifarle a ogni avvio dell'app.
                cacheDir = context.cacheDir.path
            )
            return Engine(config).also { it.initialize() }
        }

        val created = if (preferGpu) {
            try {
                create(Backend.GPU()).also {
                    lastBackendUsed = "GPU"
                    lastGpuFailure = null
                }
            } catch (gpuFailure: Throwable) {
                // Throwable e non Exception: un driver OpenCL mancante arriva come
                // UnsatisfiedLinkError, che è un Error e non verrebbe intercettato.
                lastGpuFailure = firstLineOf(gpuFailure)
                try {
                    create(Backend.CPU()).also { lastBackendUsed = "CPU" }
                } catch (cpuFailure: Throwable) {
                    lastBackendUsed = null
                    throw IllegalStateException(
                        "GPU: ${firstLineOf(gpuFailure)} | CPU: ${firstLineOf(cpuFailure)}",
                        cpuFailure
                    )
                }
            }
        } else {
            create(Backend.CPU()).also { lastBackendUsed = "CPU" }
        }

        engine = created
        loadedPath = modelPath
        return created
    }

    /**
     * La prima riga utile di un errore del motore nativo.
     *
     * I messaggi veri sono lunghissimi: si portano dietro il trace del codice C++ e i byte grezzi
     * di uno `StatusList` protobuf. Concatenandone due interi verrebbe fuori un muro di testo, e
     * l'informazione che serve — quale backend non è partito e perché — sta all'inizio.
     */
    private fun firstLineOf(error: Throwable): String {
        val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val text = if (message.isBlank()) error::class.simpleName.orEmpty() else message
        return if (text.length <= 160) text else text.take(160).trimEnd() + "…"
    }

    actual fun backendLabel(): String {
        val backend = lastBackendUsed ?: return "backend sconosciuto"
        val gpuFailure = lastGpuFailure
        return if (backend == "CPU" && gpuFailure != null) {
            "CPU (la GPU non è partita: $gpuFailure)"
        } else {
            backend
        }
    }

    actual fun unload() {
        try {
            engine?.close()
        } catch (e: Throwable) {
            // Chiudere un motore già morto non deve impedire di caricarne un altro.
        }
        engine = null
        loadedPath = null
        lastBackendUsed = null
    }
}
