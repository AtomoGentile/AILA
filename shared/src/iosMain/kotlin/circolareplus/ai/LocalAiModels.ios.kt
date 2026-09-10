package circolareplus.ai

/**
 * Catalogo iOS: solo Apple Intelligence.
 *
 * Non ci sono modelli scaricabili. Il modello è quello di sistema, disponibile su iPhone 15 Pro+
 * con iOS 26+. Se il dispositivo non lo supporta, non c'è alternativa: l'app ricade su Google AI
 * Studio (implementato su tutte le piattaforme).
 *
 * LocalModelStore.download() su iOS restituisce immediatamente "Installed" se Apple Intelligence
 * è disponibile, oppure "Failed" con il motivo. In questo modo il pulsante "Scarica" della
 * schermata Impostazioni (pensato per Android) funziona anche su iOS come "Attiva" istantaneo,
 * senza dover toccare la UI che mostra il catalogo.
 */
actual object LocalAiCatalog {
    val APPLE_INTELLIGENCE = LocalAiModel(
        id = "apple-intelligence",
        displayName = "Apple Intelligence",
        fileName = "apple-intelligence",
        downloadUrl = "",
        approxSizeBytes = 0L,
        tier = DeviceTier.UNKNOWN,
        recommendedRamMb = 0,
        preferGpu = false,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Il modello di sistema di Apple Intelligence. Nessun download: gira già " +
            "sul telefono se il dispositivo lo supporta (iPhone 15 Pro o successivo, iOS 26+)."
    )

    actual val all: List<LocalAiModel> = listOf(APPLE_INTELLIGENCE)

    actual fun byId(id: String?): LocalAiModel? = all.firstOrNull { it.id == id }

    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = APPLE_INTELLIGENCE

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> = all

    actual fun knownFileNames(): Set<String> = setOf("apple-intelligence")
}
