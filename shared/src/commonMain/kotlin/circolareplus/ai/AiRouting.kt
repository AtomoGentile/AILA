package circolareplus.ai

/**
 * Decide se, per una circolare troppo lunga per il modello locale selezionato, conviene
 * anteporre il cloud invece di lasciar troncare il testo in locale.
 *
 * Non introduce una nuova soglia: riusa [LocalAiModel.maxPromptChars], che è già calibrato
 * per modello (dipende da [LocalAiModel.maxInputTokens], che varia da motore a motore) e più
 * prudente di un numero fisso di token per tutti. Sopra quella soglia [LocalAiClassifier]
 * troncherebbe comunque il testo e proverebbe a classificare solo la parte iniziale — utile
 * come ultima rete, ma silenzioso: se il cloud è configurato e può leggere il documento
 * intero, va provato per primo.
 *
 * `false` quando non c'è un modello locale selezionato (niente da evitare) o quando non è
 * configurata una chiave cloud (anteporre un provider che fallirebbe comunque non aiuta:
 * l'ordine attuale, con l'euristica come ultima rete, resta la scelta migliore).
 */
fun shouldPreferCloudForLength(
    textLength: Int,
    localModel: LocalAiModel?,
    hasCloudKey: Boolean
): Boolean {
    if (localModel == null || !hasCloudKey) return false
    return textLength > localModel.maxPromptChars
}

/** Id della voce AICore nel catalogo Android: e' anche il "percorso" che [LocalLlm] riconosce. */
const val AICORE_MODEL_ID = "aicore"

/**
 * Memoria a breve termine dei fallimenti di AICore che riprovare subito non risolve.
 *
 * Senza, ogni analisi ripartiva da AICore: con la quota di batteria esaurita o l'app in
 * background Gemini Nano rifiuta tutto per minuti, e ogni circolare in coda pagava i
 * ritentativi di `AiCoreEngine` (fino a qualche secondo) prima di passare al motore successivo
 * per poi fallire allo stesso modo alla circolare dopo. Con la pausa AICore si salta per
 * [pauseMillis] e si va subito su LiteRT-LM o sul cloud; alla scadenza si riprova da solo.
 *
 * Gli errori che dipendono dal prompt (troppo lungo, risposta vuota) o passeggeri (IPC,
 * RESPONSE_PROCESSING) non mettono in pausa: il primo lo risolve [LocalAiClassifier] con meno
 * testo, il secondo i ritentativi stessi di AICore.
 *
 * L'orologio e' iniettato e lo stato vive nell'istanza, non in un oggetto globale: chi la crea
 * (AppContainer) decide quanto dura, e nei test il tempo si sposta a mano.
 */
class AiCoreCooldown(
    private val now: () -> Long,
    private val pauseMillis: Long = DEFAULT_PAUSE_MILLIS
) {
    private var pausedUntil: Long = 0L

    /** Il messaggio dell'errore che ha messo in pausa AICore, per spiegare il salto. */
    var lastReason: String? = null
        private set

    /**
     * Da chiamare con il messaggio di un fallimento di AICore. Restituisce `true` se il
     * fallimento mette in pausa il motore (codice in [PAUSING_CODES]).
     */
    fun recordFailure(message: String): Boolean {
        if (!isPausingFailure(message)) return false
        pausedUntil = now() + pauseMillis
        lastReason = message
        return true
    }

    /** Una generazione riuscita chiude subito la pausa: il problema e' passato prima del previsto. */
    fun recordSuccess() {
        pausedUntil = 0L
        lastReason = null
    }

    fun isPaused(): Boolean = now() < pausedUntil

    /** Millisecondi che mancano alla fine della pausa, 0 se AICore non e' in pausa. */
    fun remainingMillis(): Long = (pausedUntil - now()).coerceAtLeast(0L)

    /** Frase da mostrare quando AICore e' stato saltato e non c'era nient'altro da provare. */
    fun skipReason(): String {
        val minutes = ((remainingMillis() + 59_999L) / 60_000L).coerceAtLeast(1L)
        return "AICore in pausa per circa $minutes min dopo un errore che non si risolve " +
            "riprovando subito" + (lastReason?.let { ": ${it.take(160)}" } ?: ".")
    }

    companion object {
        const val DEFAULT_PAUSE_MILLIS = 5L * 60_000L

        /**
         * Codici di AICore che riprovare subito non risolve: limiti di sistema (quota di
         * batteria, app in background), servizio occupato anche dopo i ritentativi, Gemini Nano
         * assente o da aggiornare. Confrontati come sottostringhe del messaggio: coprono sia i nomi
         * della vecchia libreria ("8-NOT_AVAILABLE", "QUOTA") sia quelli di ML Kit
         * ("PER_APP_BATTERY_USE_QUOTA_EXCEEDED", "BACKGROUND_USE_BLOCKED", "FEATURE_NOT_FOUND").
         */
        val PAUSING_CODES = listOf(
            "QUOTA", "BACKGROUND", "BUSY", "NOT_AVAILABLE", "NEEDS_SYSTEM_UPDATE", "FEATURE_NOT_FOUND"
        )

        fun isPausingFailure(message: String): Boolean {
            val upper = message.uppercase()
            return PAUSING_CODES.any { upper.contains(it) }
        }
    }
}

/**
 * Dove mandare una richiesta di AI: il modello locale da usare (o nessuno) e se il cloud va
 * provato per primo.
 *
 * [aiCoreSkipped] dice che il modello scelto era AICore ed e' stato saltato per la pausa: serve
 * a spiegare il motivo quando poi non resta nessun motore.
 */
data class AiRoutePlan(
    val localModel: LocalAiModel?,
    val cloudFirst: Boolean,
    val aiCoreSkipped: Boolean
)

/**
 * Sceglie i motori per una richiesta, in ordine: AICore, LiteRT-LM, cloud.
 *
 * - Provider cloud: il cloud per primo, il modello scelto come riserva (come prima).
 * - Provider locale: il modello scelto. Se e' AICore ed e' in pausa ([aiCorePaused]) si usa il
 *   primo modello LiteRT-LM gia' scaricato ([installedModels], ordine del catalogo); se non ce
 *   n'e' nessuno si passa direttamente al cloud, purche' ci sia una chiave. Senza chiave resta
 *   solo l'euristica, con il motivo della pausa.
 * - [preferCloudForLongText] riusa [shouldPreferCloudForLength] per le circolari lunghe.
 *
 * [selectedModel] e' il modello scelto solo se installato: un modello non scaricato non conta.
 */
fun planAiRoute(
    provider: AiProvider,
    selectedModel: LocalAiModel?,
    installedModels: List<LocalAiModel>,
    hasCloudKey: Boolean,
    aiCorePaused: Boolean,
    textLength: Int? = null,
    preferCloudForLongText: Boolean = false
): AiRoutePlan {
    val skipAiCore = aiCorePaused && selectedModel?.id == AICORE_MODEL_ID
    val local = if (skipAiCore) {
        installedModels.firstOrNull { !it.isSystemModel }
    } else {
        selectedModel
    }
    val cloudFirst = when (provider) {
        AiProvider.GOOGLE_AI_STUDIO -> true
        AiProvider.ON_DEVICE ->
            // Solo quando e' stata la pausa a togliere il locale: senza nessun modello installato
            // l'ordine resta quello di prima (il locale ripiega subito e la catena passa al cloud).
            (skipAiCore && local == null && hasCloudKey) ||
                (preferCloudForLongText && textLength != null &&
                    shouldPreferCloudForLength(textLength, local, hasCloudKey))
    }
    return AiRoutePlan(localModel = local, cloudFirst = cloudFirst, aiCoreSkipped = skipAiCore)
}
