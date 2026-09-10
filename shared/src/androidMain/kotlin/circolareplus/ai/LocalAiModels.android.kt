package circolareplus.ai

/**
 * Catalogo Android: cinque modelli scaricabili.
 *
 * I Qwen sono tornati dopo il passaggio a LiteRT-LM: il runtime precedente (MediaPipe tasks-genai)
 * leggeva solo tokenizzatori SentencePiece, cioè in pratica la sola famiglia Gemma. Con LiteRT-LM
 * quel limite se n'è andato.
 *
 * Le due assenze rimanenti sono definitive, provate sul campo:
 * - Le varianti `-gpu` non si avviano: contengono solo pesi preconfezionati per GPU, e l'alternativa
 *   su CPU non trova nulla da caricare.
 * - **Gemma 4 12B non ha mai funzionato**, coerente con il suo model card: è l'unico della famiglia
 *   senza una sola riga di misure su Android, perché Google lo presenta come modello da computer.
 */
actual object LocalAiCatalog {
    private const val HF = "https://huggingface.co"

    // --- Gemma 4 ---
    val GEMMA4_E2B = LocalAiModel(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "$HF/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approxSizeBytes = 2_588_000_000,
        tier = DeviceTier.LOW,
        recommendedRamMb = 3_900,
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "La taglia più piccola di Gemma 4. Riassunti in italiano già buoni."
    )

    val GEMMA4_E4B = LocalAiModel(
        id = "gemma-4-e4b",
        displayName = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadUrl = "$HF/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        approxSizeBytes = 3_660_000_000,
        tier = DeviceTier.MID,
        recommendedRamMb = 5_500,
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Taglia intermedia. Coglie meglio destinatari e scadenze implicite."
    )

    // --- Qwen3.5 ---
    val QWEN35_08B = LocalAiModel(
        id = "qwen3.5-0.8b",
        displayName = "Qwen3.5 0.8B",
        fileName = "Qwen3.5-0.8B_int8.litertlm",
        downloadUrl = "$HF/litert-community/Qwen3.5-0.8B/resolve/main/Qwen3.5-0.8B_int8.litertlm",
        approxSizeBytes = 963_000_000,
        tier = DeviceTier.LOW,
        recommendedRamMb = 3_200,
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Meno di 1 GB e il download più rapido: il più adatto a un telefono modesto."
    )

    val QWEN35_2B = LocalAiModel(
        id = "qwen3.5-2b",
        displayName = "Qwen3.5 2B",
        fileName = "Qwen3.5-2B_int8.litertlm",
        downloadUrl = "$HF/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B_int8.litertlm",
        approxSizeBytes = 2_117_000_000,
        tier = DeviceTier.MID,
        recommendedRamMb = 5_000,
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Fascia media: pesa 1,5 GB meno di Gemma 4 E4B e sa produrre azioni strutturate."
    )

    val QWEN35_4B = LocalAiModel(
        id = "qwen3.5-4b",
        displayName = "Qwen3.5 4B",
        fileName = "Qwen3.5-4B_mixed_int4.litertlm",
        downloadUrl = "$HF/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm",
        approxSizeBytes = 2_754_000_000,
        tier = DeviceTier.HIGH,
        recommendedRamMb = 6_000,
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Il più capace del catalogo: 4 miliardi di parametri in 2,8 GB."
    )

    actual val all: List<LocalAiModel> = listOf(
        QWEN35_08B, QWEN35_2B, QWEN35_4B, GEMMA4_E2B, GEMMA4_E4B
    )

    actual fun byId(id: String?): LocalAiModel? = all.firstOrNull { it.id == id }

    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = when (tier) {
        DeviceTier.HIGH -> GEMMA4_E4B
        DeviceTier.MID -> GEMMA4_E2B
        DeviceTier.LOW, DeviceTier.UNKNOWN -> QWEN35_08B
    }

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> {
        val recommended = recommendedFor(deviceTierForRam(totalRamMb))
        return listOf(recommended) + all.filter { it.id != recommended.id }
    }

    actual fun knownFileNames(): Set<String> = all.map { it.fileName }.toSet()
}
