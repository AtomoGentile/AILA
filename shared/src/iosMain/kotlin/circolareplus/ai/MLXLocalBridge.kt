package circolareplus.ai

/**
 * Tier 2 iOS: modelli custom quantizzati via **MLX Swift**, per i dispositivi senza Apple
 * Intelligence (iPhone 11-14, 15 base). Implementata in Swift (`MLXLocalEngine.swift`),
 * iniettata da `iOSApp.swift` in [MLXLocalBridgeHolder], sullo stesso schema di
 * [AppleIntelligenceBridge]/[AppleIntelligenceBridgeHolder].
 *
 * A differenza di Apple Intelligence (modello di sistema, sempre "installato" o mai), qui serve
 * un vero download: [modelRepoId] è l'identificativo del repo HuggingFace nel formato MLX
 * (org `mlx-community`, es. `mlx-community/Phi-3.5-mini-instruct-4bit`), non un singolo file —
 * riusa il campo [LocalAiModel.downloadUrl] del catalogo comune per portare questo id, dato che
 * su iOS quel campo non è mai stato un URL HTTP diretto (vedi commento su `LocalAiModels.ios.kt`).
 *
 * **Stato: scheletro non collegato all'SDK reale.** Vedi `MLXLocalEngine.swift` per il motivo
 * (nessun Mac in questo ambiente per verificare la superficie esatta di `mlx-swift-examples`) e
 * per i passi che restano da fare.
 */
interface MLXLocalBridge {
    /** `true` se il modello (identificato da [modelId], lo stesso di [LocalAiModel.id]) è già stato scaricato. */
    fun isDownloaded(modelId: String): Boolean

    /** Byte occupati sul disco da questo modello, 0 se non scaricato. */
    fun localSizeBytes(modelId: String): Long

    /**
     * Scarica il modello dal repo HuggingFace [modelRepoId] (formato MLX), salvandolo in
     * `Documents/ai-models/<modelId>/`. Ritorna `true` a download completato.
     *
     * @throws Exception se il download fallisce (rete, spazio, repo non trovato).
     */
    suspend fun download(
        modelId: String,
        modelRepoId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Boolean

    /** Cancella il modello scaricato. `true` se qualcosa è stato effettivamente rimosso. */
    fun delete(modelId: String): Boolean

    /** Motivo leggibile se il modello non può girare su questo dispositivo (RAM insufficiente, non scaricato, MLX non disponibile). Null se pronto. */
    fun unavailableReason(modelId: String): String?

    /**
     * Genera una risposta in streaming, fermandosi appena `stopWhen` ritorna `true` o dopo
     * `timeoutMillis` — stesso contratto di [AppleIntelligenceBridge.generate].
     */
    suspend fun generate(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean
    ): String
}

/**
 * Holder singleton per il bridge MLX, iniettato da Swift in `iOSApp.swift.init()` — stesso
 * pattern di [AppleIntelligenceBridgeHolder]. Può restare `null` (nessuna build reale
 * dell'integrazione MLX ancora fatta): il codice Kotlin che lo consulta tratta `null` come
 * "Tier 2 non disponibile", ricadendo sul cloud, mai su un crash.
 */
object MLXLocalBridgeHolder {
    var bridge: MLXLocalBridge? = null
}
