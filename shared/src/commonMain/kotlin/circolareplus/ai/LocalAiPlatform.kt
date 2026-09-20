package circolareplus.ai

/**
 * Confine fra il codice condiviso e quello che solo la piattaforma può sapere o fare per l'AI
 * locale: quanta RAM ha il telefono, se esiste un motore di inferenza, dove finiscono i file dei
 * modelli e come si esegue una generazione.
 *
 * Oggi c'è un'implementazione vera solo su Android (motore nativo LiteRT-LM incluso in MediaPipe
 * `tasks-genai`). Su iOS le `actual` esistono ma dichiarano l'AI locale non disponibile: la
 * schermata Impostazioni lo dice esplicitamente e l'app resta su Google AI Studio. Il giorno in
 * cui si aggiunge iOS (Apple Foundation Models su iOS 26+, oppure lo stesso LiteRT-LM via
 * cinterop) basta riempire le `actual` in `iosMain`, senza toccare nulla di quanto sta qui.
 */

/** RAM totale del dispositivo in MB, oppure 0 se non è leggibile su questa piattaforma. */
expect fun totalDeviceRamMb(): Int

/** `true` se su questa piattaforma esiste un motore di inferenza on-device utilizzabile. */
expect fun isOnDeviceAiAvailable(): Boolean

/**
 * Perché l'AI locale non è utilizzabile su questo dispositivo, in una frase mostrabile
 * all'utente. `null` quando invece è disponibile.
 */
expect fun onDeviceAiUnavailableReason(): String?

/** Avanzamento del download di un modello, osservato dalla schermata Impostazioni. */
sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    /** [totalBytes] è 0 se il server non manda `Content-Length`. */
    data class InProgress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState() {
        /** 0f..1f, oppure -1f quando la dimensione totale è ignota (barra indeterminata). */
        val fraction: Float
            get() = if (totalBytes > 0) {
                (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            } else {
                -1f
            }
    }
    data class Installed(val path: String) : ModelDownloadState()
    data class Failed(val reason: String) : ModelDownloadState()
}

/**
 * Archivio dei modelli sul dispositivo: download ripartibile, verifica, cancellazione.
 *
 * Il download è ripartibile di proposito. Un modello va da 350 MB a 2 GB e la rete della scuola
 * cade: senza ripresa, ogni interruzione butterebbe via tutto quello che si era già scaricato e
 * l'utente ricomincerebbe da capo — su una connessione lenta significa non finire mai.
 */
expect class LocalModelStore() {

    /** Il file del modello esiste ed è completo. */
    fun isInstalled(model: LocalAiModel): Boolean

    /** Percorso assoluto del modello installato, `null` se non c'è. */
    fun installedPath(model: LocalAiModel): String?

    /** Byte già scaricati di un download interrotto (0 se non ce n'è uno). */
    fun partialBytes(model: LocalAiModel): Long

    /** Spazio libero in byte nella cartella dei modelli. */
    fun freeSpaceBytes(): Long

    /** Cancella modello e download parziale. Ritorna `true` se qualcosa è stato rimosso. */
    fun delete(model: LocalAiModel): Boolean

    /**
     * Byte occupati da file di modelli che non sono più in catalogo.
     *
     * Il catalogo cambia — sono già stati tolti i build solo-GPU, che non si avviavano — e i file
     * già scaricati di una voce sparita non sarebbero più raggiungibili da nessuna schermata:
     * resterebbero a occupare qualche giga per sempre, invisibili. Questo li conta perché le
     * Impostazioni possano proporre di liberarli.
     */
    fun orphanBytes(): Long

    /** Cancella i file di cui sopra. Ritorna i byte liberati. */
    fun deleteOrphans(): Long

    /** Tutti i modelli del catalogo attualmente installati. */
    fun installedModels(): List<LocalAiModel>

    /**
     * Scarica il modello, riprendendo un eventuale download parziale. Chiama [onProgress] a ogni
     * blocco ricevuto. Sospende fino alla fine: va lanciata in una coroutine cancellabile —
     * cancellarla lascia il parziale su disco, pronto per essere ripreso.
     */
    suspend fun download(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState
}

/**
 * Motore di inferenza locale. Una sola istanza per tutta l'app: caricare un modello costa
 * secondi e centinaia di MB, quindi va tenuto caldo fra una circolare e l'altra e le richieste
 * vanno serializzate (il motore nativo non è rientrante).
 */
expect class LocalLlm() {

    /**
     * Genera una risposta. [systemPrompt] e [userPrompt] vengono uniti secondo il formato che il
     * motore si aspetta. Lancia se il modello non si carica, se la generazione fallisce o se
     * supera [timeoutMillis]: il chiamante ([LocalAiClassifier]) trasforma l'eccezione in un
     * motivo leggibile.
     *
     * [stopWhen] viene chiamata sul testo accumulato mano a mano e ferma la generazione appena
     * restituisce `true`. Senza, si aspetterebbe che il modello decida da solo di fermarsi —
     * cosa che con un modello piccolo puo' non succedere: dopo aver dato la risposta continua a
     * commentare finche' non ha riempito la finestra di contesto, e intanto il telefono macina.
     */
    suspend fun generate(
        modelPath: String,
        preferGpu: Boolean,
        maxOutputTokens: Int,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean,
        /** Lascia ragionare il modello (blocco `<think>`) prima della risposta. Solo motori che lo supportano. */
        enableThinking: Boolean = false
    ): String

    /**
     * "GPU", "CPU" o "backend sconosciuto": con quale acceleratore gira il modello caricato.
     *
     * E' l'informazione decisiva quando l'AI locale e' lentissima — fra i due c'e' quasi un
     * fattore dieci — e prima non usciva da nessuna parte.
     */
    fun backendLabel(): String

    /** Scarica il modello dalla memoria. Da chiamare quando si cambia o cancella un modello. */
    fun unload()
}
