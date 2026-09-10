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
    // Memoria fisica totale in byte → MB
    val bytes = NSProcessInfo.processInfo.physicalMemory
    return (bytes / 1_000_000L).toInt()
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
 * Su iOS non esiste il concetto di "modello da scaricare": Apple Intelligence è sempre
 * "installata" se il dispositivo la supporta (iPhone 15 Pro+, iOS 26+), altrimenti non
 * disponibile. Questo class rappresenta lo stato di disponibilità come se fosse "il modello
 * installato".
 */
actual class LocalModelStore actual constructor() {

    actual fun isInstalled(model: LocalAiModel): Boolean {
        // Il modello è "installato" se Apple Intelligence è disponibile
        return isOnDeviceAiAvailable()
    }

    actual fun installedPath(model: LocalAiModel): String? {
        // Percorso finto: il modello di sistema non ha un percorso su disco
        return if (isOnDeviceAiAvailable()) "apple-intelligence" else null
    }

    actual fun partialBytes(model: LocalAiModel): Long = 0L
    // Non esiste download parziale: il modello è di sistema

    actual fun freeSpaceBytes(): Long = 0L
    // Non occupa spazio nel dispositivo

    actual fun delete(model: LocalAiModel): Boolean = false
    // Non si può cancellare il modello di sistema

    actual fun orphanBytes(): Long = 0L
    // Non ci sono file orfani su iOS

    actual fun deleteOrphans(): Long = 0L
    // Non c'è nulla da cancellare

    actual fun installedModels(): List<LocalAiModel> {
        // Se Apple Intelligence è disponibile, è l'unico "modello installato"
        // Il catalogo iOS (LocalAiModels.ios.kt) ha una sola voce
        return if (isOnDeviceAiAvailable()) LocalAiCatalog.all else emptyList()
    }

    actual suspend fun download(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState {
        // Su iOS "scaricare" il modello = verificare che Apple Intelligence sia disponibile
        // Chiama onProgress(1, 1) per completamento istantaneo
        onProgress(1, 1)
        return if (isOnDeviceAiAvailable()) {
            ModelDownloadState.Installed("apple-intelligence")
        } else {
            ModelDownloadState.Failed(
                onDeviceAiUnavailableReason() ?: "Apple Intelligence non disponibile su questo dispositivo."
            )
        }
    }
}

/**
 * Motore di inferenza: Apple Intelligence (Foundation Models).
 * Il modello vive nel sistema, non si carica/scarica come su Android.
 */
actual class LocalLlm actual constructor() {

    actual fun backendLabel(): String = "Apple Intelligence (Neural Engine)"

    actual suspend fun generate(
        modelPath: String,
        preferGpu: Boolean,
        maxOutputTokens: Int,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean
    ): String {
        // Ignora modelPath (non c'è file), preferGpu (il backend lo sceglie il sistema),
        // maxOutputTokens (lo decide Apple Intelligence). Delega al bridge Swift.
        val bridge = AppleIntelligenceBridgeHolder.bridge
            ?: throw IllegalStateException("Apple Intelligence non disponibile.")
        return bridge.generate(systemPrompt, userPrompt, timeoutMillis, stopWhen)
    }

    actual fun unload() {
        // Non fare nulla: il modello di sistema non si scarica dall'app.
        // iOS lo gestisce automaticamente.
    }
}
