package circolareplus.ai

import platform.Foundation.NSProcessInfo

/**
 * AI locale su iOS: Apple Intelligence (Foundation Models, iOS 26+).
 *
 * Il modello è quello di sistema: nessun download, nessuna API key, nessun file da gestire.
 * L'implementazione usa il bridge AppleIntelligenceBridge (implementato in Swift da
 * AppleIntelligenceEngine.swift), che viene iniettato da iOSApp.swift.init() prima di creare
 * la UI.
 */

actual fun totalDeviceRamMb(): Int {
    // Memoria fisica totale in byte → MiB (1024*1024), non MB decimali: come su Android
    // (ActivityManager.totalMem / 1024 / 1024), perché le soglie di LocalAiModels.kt in commonMain
    // sono tarate su quella unità e con /1.000.000 un iPhone da 8 GiB risultava ~6% "più piccolo".
    val bytes = NSProcessInfo.processInfo.physicalMemory
    return (bytes.toLong() / 1024L / 1024L).toInt()
}

actual fun isOnDeviceAiAvailable(): Boolean {
    // Se il bridge è stato iniettato e dice che Apple Intelligence è disponibile
    return AppleIntelligenceBridgeHolder.bridge?.isAvailable() ?: false
}

actual fun onDeviceAiUnavailableReason(): String? {
    val bridge = AppleIntelligenceBridgeHolder.bridge
    if (bridge == null) {
        return "AI locale non ancora inizializzata."
    }
    return bridge.unavailableReason()
}

/**
 * Apple Intelligence (Tier 1) è sempre "installata" o mai: nessun file, solo verifica di
 * sistema. I modelli MLX (Tier 2) sono invece scaricati per davvero, tramite [MLXLocalBridge] —
 * vedi il commento su `LocalAiModels.ios.kt` per lo stato di quell'integrazione.
 */
actual class LocalModelStore actual constructor() {

    private fun isAppleIntelligence(model: LocalAiModel) = model.id == "apple-intelligence"

    actual fun isInstalled(model: LocalAiModel): Boolean = if (isAppleIntelligence(model)) {
        isOnDeviceAiAvailable()
    } else {
        MLXLocalBridgeHolder.bridge?.isDownloaded(model.id) ?: false
    }

    actual fun installedPath(model: LocalAiModel): String? {
        if (!isInstalled(model)) return null
        // Percorso finto per entrambi i tier: né il modello di sistema né MLXLocalEngine
        // caricano un file per path su iOS, LocalLlm.ios.kt sceglie il bridge dall'id.
        return model.id
    }

    actual fun partialBytes(model: LocalAiModel): Long = 0L
    // Nessun download parziale esposto da MLXLocalBridge per ora: l'integrazione è inerte.

    actual fun freeSpaceBytes(): Long = 0L
    // Non calcolato: nessun download reale avviene ancora su questa build.

    // Il download MLX gira nella coroutine del chiamante (nessun lavoro separato in background):
    // annullarla, come fa gia' la UI, e' sufficiente.
    actual fun cancelDownload(model: LocalAiModel) {}

    actual fun delete(model: LocalAiModel): Boolean {
        if (isAppleIntelligence(model)) return false // non si può cancellare il modello di sistema
        return MLXLocalBridgeHolder.bridge?.delete(model.id) ?: false
    }

    actual fun orphanBytes(): Long = 0L
    // Non ci sono file orfani gestiti da questa build.

    actual fun deleteOrphans(): Long = 0L
    // Non c'è nulla da cancellare.

    actual fun installedModels(): List<LocalAiModel> = LocalAiCatalog.all.filter { isInstalled(it) }

    actual suspend fun download(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState {
        if (isAppleIntelligence(model)) {
            // "Scaricare" il modello di sistema = verificare che sia disponibile: completamento
            // istantaneo, nessun byte reale da trasferire.
            onProgress(1, 1)
            return if (isOnDeviceAiAvailable()) {
                ModelDownloadState.Installed("apple-intelligence")
            } else {
                ModelDownloadState.Failed(
                    onDeviceAiUnavailableReason() ?: "Apple Intelligence non disponibile su questo dispositivo."
                )
            }
        }

        val bridge = MLXLocalBridgeHolder.bridge
            ?: return ModelDownloadState.Failed(
                "AI locale MLX non ancora disponibile in questa build."
            )
        return try {
            val ok = bridge.download(
                modelId = model.id,
                modelRepoId = model.downloadUrl,
                onProgress = onProgress
            )
            if (ok) ModelDownloadState.Installed(model.id)
            else ModelDownloadState.Failed(bridge.unavailableReason(model.id) ?: "Download non riuscito.")
        } catch (e: Exception) {
            ModelDownloadState.Failed(
                "Download interrotto: ${e::class.simpleName}: ${e.message ?: "nessun dettaglio"}"
            )
        }
    }
}

/**
 * Motore di inferenza: Apple Intelligence (Foundation Models).
 * Il modello vive nel sistema, non si carica/scarica come su Android.
 */
actual class LocalLlm actual constructor() {

    private companion object {
        /** Come su Android (LocalLlm.android.kt) con il ragionamento spento. */
        const val JSON_TEMPERATURE = 0.1
    }

    @Volatile
    private var lastEngineLabel: String = "Apple Intelligence (Neural Engine)"

    actual fun backendLabel(): String = lastEngineLabel

    /**
     * `modelPath` qui è sempre l'id del modello ([LocalAiModel.id], vedi
     * `LocalModelStore.installedPath`), non un path su disco: sceglie il bridge giusto in base
     * a quale motore serve questo modello — stesso ruolo del branch `modelPath == "aicore"`
     * aggiunto lato Android in `LocalLlm.android.kt`.
     */
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
        // enableThinking è ignorato: né il modello Apple né MLX hanno (per ora) un blocco di
        // ragionamento da accendere. Stesso tetto ai token e stessa temperatura bassa di Android:
        // per produrre JSON serve la stessa risposta ogni volta, non creatività.
        if (modelPath == "apple-intelligence") {
            lastEngineLabel = "Apple Intelligence (Neural Engine)"
            val bridge = AppleIntelligenceBridgeHolder.bridge
                ?: throw IllegalStateException("Apple Intelligence non disponibile.")
            return bridge.generate(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                timeoutMillis = timeoutMillis,
                maxOutputTokens = maxOutputTokens,
                temperature = JSON_TEMPERATURE,
                stopWhen = stopWhen
            )
        }

        // Tier 2: modello MLX. maxOutputTokens/preferGpu: preferGpu è ignorato (MLX su iOS usa
        // sempre Metal), maxOutputTokens passa al bridge come su Android.
        lastEngineLabel = "MLX (Metal)"
        val bridge = MLXLocalBridgeHolder.bridge
            ?: throw IllegalStateException("AI locale MLX non ancora disponibile in questa build.")
        return bridge.generate(
            modelId = modelPath,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxOutputTokens = maxOutputTokens,
            timeoutMillis = timeoutMillis,
            stopWhen = stopWhen
        )
    }

    actual fun unload() {
        // Non fare nulla: né il modello di sistema né (per ora) MLXLocalEngine tengono uno stato
        // da liberare esplicitamente lato Kotlin.
    }
}
