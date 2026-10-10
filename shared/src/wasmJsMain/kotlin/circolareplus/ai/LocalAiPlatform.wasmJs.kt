package circolareplus.ai

// Nel browser non c'e' AI locale: l'analisi la fa il server (o Gemini con la chiave personale).
// La sezione "AI locale" delle impostazioni resta nascosta perche' isOnDeviceAiOfferedHere e' false.

actual fun totalDeviceRamMb(): Int = 0

actual fun isOnDeviceAiAvailable(): Boolean = false

actual fun onDeviceAiUnavailableReason(): String? = "L'AI locale non e' disponibile nella versione web."

actual fun isOnDeviceAiOfferedHere(): Boolean = false

actual class LocalModelStore actual constructor() {
    actual fun isInstalled(model: LocalAiModel): Boolean = false
    actual fun installedPath(model: LocalAiModel): String? = null
    actual fun partialBytes(model: LocalAiModel): Long = 0L
    actual fun freeSpaceBytes(): Long = 0L
    actual fun delete(model: LocalAiModel): Boolean = false
    actual fun cancelDownload(model: LocalAiModel) {}
    actual fun orphanBytes(): Long = 0L
    actual fun deleteOrphans(): Long = 0L
    actual fun installedModels(): List<LocalAiModel> = emptyList()
    actual suspend fun download(
        model: LocalAiModel,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelDownloadState = ModelDownloadState.Failed("L'AI locale non e' disponibile nella versione web.")
}

actual class LocalLlm actual constructor() {
    actual suspend fun generate(
        modelPath: String,
        preferGpu: Boolean,
        maxOutputTokens: Int,
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        stopWhen: (String) -> Boolean,
        enableThinking: Boolean,
        assistantAnswer: Boolean
    ): String = error("L'AI locale non e' disponibile nella versione web.")

    actual fun backendLabel(): String = "Nessuno"

    actual fun unload() {}
}
