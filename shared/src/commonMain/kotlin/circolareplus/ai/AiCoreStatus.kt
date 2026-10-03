package circolareplus.ai

/**
 * Stato di Gemini Nano come lo dà `GenerativeModel.checkStatus()` di ML Kit GenAI, tradotto
 * fuori dall'SDK.
 *
 * La traduzione sta qui in commonMain e non in AiCoreEngine perche' cosi' si prova con i test
 * comuni sulla CI: le costanti di ML Kit esistono solo nel codice Android, che lassu' si compila
 * ma non si esegue. AiCoreEngine si limita a convertire le costanti dell'SDK in questi valori.
 */
enum class AiCoreFeatureStatus { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, UNKNOWN }

/**
 * Codici d'errore di AICore/ML Kit che l'app sa distinguere. I nomi sono quelli dell'SDK
 * (`GenAiException.ErrorCode`) e dei messaggi di AICore ("error code 606-FEATURE_NOT_FOUND"):
 * finiscono nel testo dell'errore, e [AiCoreCooldown] e [LocalAiClassifier] li leggono da li'.
 */
enum class AiCoreErrorCode(val kind: AiCoreFailureKind) {
    BUSY(AiCoreFailureKind.TRANSIENT),
    IPC_ERROR(AiCoreFailureKind.TRANSIENT),
    INTERNAL_ERROR(AiCoreFailureKind.TRANSIENT),
    REQUEST_PROCESSING_ERROR(AiCoreFailureKind.TRANSIENT),
    RESPONSE_GENERATION_ERROR(AiCoreFailureKind.TRANSIENT),
    RESPONSE_PROCESSING_ERROR(AiCoreFailureKind.TRANSIENT),
    REQUEST_TOO_LARGE(AiCoreFailureKind.PROMPT),
    REQUEST_TOO_SMALL(AiCoreFailureKind.PROMPT),
    NOT_AVAILABLE(AiCoreFailureKind.PERMANENT),
    FEATURE_NOT_FOUND(AiCoreFailureKind.PERMANENT),
    NEEDS_SYSTEM_UPDATE(AiCoreFailureKind.PERMANENT),
    NOT_ENOUGH_DISK_SPACE(AiCoreFailureKind.PERMANENT),
    PER_APP_BATTERY_USE_QUOTA_EXCEEDED(AiCoreFailureKind.PERMANENT),
    BACKGROUND_USE_BLOCKED(AiCoreFailureKind.PERMANENT),
    PRIVATE_MODE(AiCoreFailureKind.PERMANENT),
    CANCELLED(AiCoreFailureKind.PERMANENT)
}

/**
 * - [TRANSIENT]: non dipende dal prompt e di solito sparisce da solo (servizio occupato,
 *   collegamento caduto, errore interno): si ritenta con una pausa.
 * - [PROMPT]: il prompt non va bene cosi' (troppo lungo o troppo corto): ritentare lo stesso
 *   prompt e' inutile, [LocalAiClassifier] riprova con meno testo.
 * - [PERMANENT]: Gemini Nano non e' utilizzabile adesso (assente, da aggiornare, bloccato dal
 *   sistema per batteria o app in background): ritentare subito da' lo stesso errore.
 */
enum class AiCoreFailureKind { TRANSIENT, PROMPT, PERMANENT }

/** Stato comune di AICore, per chi decide se usarlo, prepararlo o lasciarlo stare. */
sealed class AiCoreState {
    /** Gemini Nano e' scaricato e pronto. */
    data object Available : AiCoreState()

    /** Il telefono lo supporta ma il modello non e' ancora sul telefono: "Attiva" lo scarica. */
    data object Downloadable : AiCoreState()

    /** Il sistema lo sta gia' scaricando. */
    data object Downloading : AiCoreState()

    /** Questo telefono non offre Gemini Nano. */
    data class Unavailable(val message: String) : AiCoreState()

    /** Un errore del servizio: [failure] dice se ha senso riprovare. */
    data class Failed(val failure: AiCoreFailure) : AiCoreState()
}

/** Un errore di AICore gia' classificato, con il messaggio da mostrare. */
data class AiCoreFailure(
    val code: AiCoreErrorCode?,
    val kind: AiCoreFailureKind,
    /** Frase in italiano, con il nome del codice e il messaggio grezzo in coda. */
    val message: String
) {
    val isTransient: Boolean get() = kind == AiCoreFailureKind.TRANSIENT
}

object AiCoreStatusMapper {

    /** Tentativi in tutto per un errore passeggero (vedi [shouldRetry]). */
    const val TRANSIENT_ATTEMPTS = 3

    /** Pausa prima del ritentativo: cresce a ogni giro (1,5 s, 3 s). */
    const val TRANSIENT_RETRY_DELAY_MS = 1_500L

    /** Segnale con cui [LocalAiClassifier] riconosce una risposta vuota e riprova il prompt. */
    const val EMPTY_ANSWER_MARKER = "testo vuoto"

    private val ERROR_CODE_PATTERN = Regex("ERROR CODE \\d+-([A-Z_]+)")

    fun fromFeatureStatus(status: AiCoreFeatureStatus): AiCoreState = when (status) {
        AiCoreFeatureStatus.AVAILABLE -> AiCoreState.Available
        AiCoreFeatureStatus.DOWNLOADABLE -> AiCoreState.Downloadable
        AiCoreFeatureStatus.DOWNLOADING -> AiCoreState.Downloading
        AiCoreFeatureStatus.UNAVAILABLE -> AiCoreState.Unavailable(
            "Gemini Nano non e' disponibile su questo telefono (NOT_AVAILABLE): scegli un altro " +
                "modello di AI locale."
        )
        // Un valore che questa versione dell'app non conosce (SDK piu' nuovo): si tratta come
        // un errore passeggero, cosi' alla prossima richiesta si richiede lo stato.
        AiCoreFeatureStatus.UNKNOWN -> AiCoreState.Failed(
            AiCoreFailure(
                code = null,
                kind = AiCoreFailureKind.TRANSIENT,
                message = "AICore ha restituito uno stato sconosciuto: riprova fra poco."
            )
        )
    }

    /** `true` se lo stato permette di generare subito. */
    fun canGenerate(state: AiCoreState): Boolean = state == AiCoreState.Available

    /** `true` se "Attiva" deve avviare (o seguire) il download di Gemini Nano. */
    fun needsDownload(state: AiCoreState): Boolean =
        state == AiCoreState.Downloadable || state == AiCoreState.Downloading

    /**
     * Il codice d'errore dal testo, quando l'SDK non l'ha gia' dato come numero noto. AICore
     * scrive "error code 606-FEATURE_NOT_FOUND"; altre volte c'e' solo il nome.
     */
    fun errorCodeFromMessage(message: String): AiCoreErrorCode? {
        val upper = message.uppercase()
        // Prima il codice vero: il messaggio ha anche "error type 2-INFERENCE_ERROR", che non
        // deve contare.
        ERROR_CODE_PATTERN.find(upper)?.groupValues?.get(1)?.let { name ->
            AiCoreErrorCode.entries.firstOrNull { it.name == name }?.let { return it }
        }
        // Poi i nomi sparsi, il piu' lungo per primo: "NOT_AVAILABLE" non deve vincere su un nome
        // che lo contiene, e "QUOTA" da solo (vecchia libreria) resta riconosciuto sotto.
        AiCoreErrorCode.entries.sortedByDescending { it.name.length }
            .firstOrNull { upper.contains(it.name) }
            ?.let { return it }
        return when {
            upper.contains("QUOTA") -> AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED
            upper.contains("BACKGROUND") -> AiCoreErrorCode.BACKGROUND_USE_BLOCKED
            upper.contains("IPC") -> AiCoreErrorCode.IPC_ERROR
            message.contains("feature not found", ignoreCase = true) -> AiCoreErrorCode.FEATURE_NOT_FOUND
            else -> null
        }
    }

    /**
     * Classifica un errore di AICore. [code] e' il codice che l'SDK ha dato come numero, se
     * riconosciuto; altrimenti lo si cerca nel testo. Un errore senza codice riconoscibile conta
     * come passeggero: e' quello che faceva la vecchia integrazione, e i ritentativi sono pochi.
     */
    fun classifyError(code: AiCoreErrorCode?, rawMessage: String): AiCoreFailure {
        val resolved = code ?: errorCodeFromMessage(rawMessage)
        val kind = resolved?.kind ?: AiCoreFailureKind.TRANSIENT
        return AiCoreFailure(code = resolved, kind = kind, message = readable(resolved, rawMessage))
    }

    /**
     * Se ritentare dopo il tentativo numero [attempt] (da 1) fallito con [failure]. Solo gli
     * errori passeggeri, e al massimo [TRANSIENT_ATTEMPTS] tentativi in tutto.
     */
    fun shouldRetry(failure: AiCoreFailure, attempt: Int): Boolean =
        failure.isTransient && attempt < TRANSIENT_ATTEMPTS

    /** Attesa prima del tentativo successivo al numero [attempt]. */
    fun retryDelayMillis(attempt: Int): Long = TRANSIENT_RETRY_DELAY_MS * attempt

    /** `true` se dopo [failure] il modello va ricreato: il collegamento al servizio e' caduto. */
    fun needsReconnect(failure: AiCoreFailure): Boolean = failure.code == AiCoreErrorCode.IPC_ERROR

    /**
     * L'errore di una risposta vuota. Corto di proposito: il messaggio finisce in una riga di
     * 140 caratteri (vedi HeuristicClassification.shortenReason) e i valori utili sono in fondo.
     * Comincia con [EMPTY_ANSWER_MARKER], che [LocalAiClassifier] cerca per riprovare con un
     * prompt diverso.
     */
    fun emptyAnswerMessage(finishReason: String, parts: Int?, promptChars: Int): String =
        "$EMPTY_ANSWER_MARKER da AICore (finish=$finishReason, parti=$parts, $promptChars car.)"

    fun isEmptyAnswer(message: String): Boolean =
        message.contains(EMPTY_ANSWER_MARKER, ignoreCase = true)

    /**
     * Frase leggibile in italiano. Il nome del codice resta sempre nel testo: lo usano
     * [AiCoreCooldown] (pausa) e [LocalAiClassifier] (REQUEST_TOO_LARGE = meno testo).
     */
    fun readable(code: AiCoreErrorCode?, rawMessage: String): String {
        val hint = when (code) {
            AiCoreErrorCode.BUSY -> "Gemini Nano e' occupato da un'altra app."
            AiCoreErrorCode.IPC_ERROR -> "Il collegamento con AICore si e' interrotto."
            AiCoreErrorCode.INTERNAL_ERROR,
            AiCoreErrorCode.REQUEST_PROCESSING_ERROR -> "AICore ha avuto un errore interno."
            AiCoreErrorCode.RESPONSE_GENERATION_ERROR,
            AiCoreErrorCode.RESPONSE_PROCESSING_ERROR ->
                "Gemini Nano non e' riuscito a completare la risposta."
            AiCoreErrorCode.REQUEST_TOO_LARGE -> "Il testo e' troppo lungo per Gemini Nano."
            AiCoreErrorCode.REQUEST_TOO_SMALL -> "Il testo e' troppo corto per Gemini Nano."
            AiCoreErrorCode.NOT_AVAILABLE,
            AiCoreErrorCode.FEATURE_NOT_FOUND ->
                "Gemini Nano in questo momento non e' utilizzabile su questo telefono " +
                    "(puo' essere passeggero: modello in aggiornamento). Se persiste, scegli un " +
                    "altro modello."
            AiCoreErrorCode.NEEDS_SYSTEM_UPDATE ->
                "AICore va aggiornato: controlla gli aggiornamenti di sistema e di Google Play."
            AiCoreErrorCode.NOT_ENOUGH_DISK_SPACE ->
                "Spazio insufficiente per Gemini Nano: libera memoria sul telefono."
            AiCoreErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED ->
                "Android ha limitato l'uso di Gemini Nano per risparmiare batteria."
            AiCoreErrorCode.BACKGROUND_USE_BLOCKED ->
                "Gemini Nano funziona solo con l'app aperta in primo piano."
            AiCoreErrorCode.PRIVATE_MODE -> "Gemini Nano non e' disponibile in modalita' privata."
            AiCoreErrorCode.CANCELLED -> "Generazione annullata."
            null -> "AICore ha avuto un errore."
        }
        val retryHint = if ((code?.kind ?: AiCoreFailureKind.TRANSIENT) == AiCoreFailureKind.TRANSIENT) {
            " Riprova tra qualche secondo."
        } else {
            ""
        }
        val codeName = code?.name?.let { "$it: " }.orEmpty()
        return "$hint$retryHint ($codeName${rawMessage.ifBlank { "nessun dettaglio" }})"
    }
}

/**
 * Avanzamento del download di Gemini Nano e momento di smettere di aspettarlo.
 *
 * Serve perche' `download()` di ML Kit puo' non emettere mai niente: segnalato in campo, il
 * tasto "Attiva" restava su "0 MB di 0 MB" senza fine. Il sistema decide da se' quando scaricare
 * (rete, batteria, altri download di AICore), e un Flow che non finisce bloccava l'onboarding.
 * Qui si tiene l'ultimo totale noto (ML Kit lo da' solo all'inizio) e si dichiara fermo il
 * download dopo [startTimeoutMillis] senza partenza o [stallTimeoutMillis] senza avanzamento.
 */
class AiCoreDownloadWatch(
    private val now: () -> Long,
    private val startTimeoutMillis: Long = DEFAULT_START_TIMEOUT_MILLIS,
    private val stallTimeoutMillis: Long = DEFAULT_STALL_TIMEOUT_MILLIS
) {
    private var lastEventAt = now()

    var started = false
        private set

    var totalBytes = 0L
        private set

    var downloadedBytes = 0L
        private set

    fun onStarted(bytesToDownload: Long) {
        started = true
        lastEventAt = now()
        if (bytesToDownload > 0) totalBytes = bytesToDownload
    }

    fun onProgress(totalBytesDownloaded: Long) {
        started = true
        lastEventAt = now()
        downloadedBytes = totalBytesDownloaded
        // Un totale mai arrivato non deve dare percentuali oltre il 100%.
        if (totalBytesDownloaded > totalBytes) totalBytes = totalBytesDownloaded
    }

    fun isStalled(): Boolean =
        now() - lastEventAt > if (started) stallTimeoutMillis else startTimeoutMillis

    /** Il motivo da mostrare quando si smette di aspettare, con lo stato letto da AICore. */
    fun stalledMessage(statusName: String): String = if (!started) {
        "AICore non ha avviato il download di Gemini Nano entro ${startTimeoutMillis / 1000} " +
            "secondi (stato: $statusName). Il sistema puo' rimandarlo (rete, batteria, altri " +
            "aggiornamenti): riprova piu' tardi da Impostazioni. Intanto AILA usa un altro motore."
    } else {
        "Il download di Gemini Nano non avanza da ${stallTimeoutMillis / 1000} secondi " +
            "(stato: $statusName). Riprova piu' tardi da Impostazioni."
    }

    companion object {
        const val DEFAULT_START_TIMEOUT_MILLIS = 60_000L
        const val DEFAULT_STALL_TIMEOUT_MILLIS = 120_000L
    }
}

/** Nome breve di uno stato, per i messaggi di diagnosi. */
fun AiCoreState.statusName(): String = when (this) {
    AiCoreState.Available -> "AVAILABLE"
    AiCoreState.Downloadable -> "DOWNLOADABLE"
    AiCoreState.Downloading -> "DOWNLOADING"
    is AiCoreState.Unavailable -> "UNAVAILABLE"
    is AiCoreState.Failed -> failure.code?.name ?: "ERROR"
}
