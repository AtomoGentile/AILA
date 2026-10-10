package circolareplus.ai

// Catalogo vuoto: nessun modello gira nel browser. "recommendedFor" deve comunque restituire
// qualcosa; non viene mai mostrato perche' la sezione AI locale e' nascosta (vedi LocalAiPlatform).
actual object LocalAiCatalog {
    private val NONE = LocalAiModel(
        id = "none",
        displayName = "Nessuno",
        fileName = "none",
        downloadUrl = "",
        approxSizeBytes = 0L,
        tier = DeviceTier.UNKNOWN,
        recommendedRamMb = 0,
        preferGpu = false,
        supportsActions = false,
        maxOutputTokens = 0,
        description = "L'AI locale non e' disponibile nella versione web."
    )

    actual val all: List<LocalAiModel> = emptyList()

    actual fun byId(id: String?): LocalAiModel? = null

    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = NONE

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> = emptyList()

    actual fun knownFileNames(): Set<String> = emptySet()
}
