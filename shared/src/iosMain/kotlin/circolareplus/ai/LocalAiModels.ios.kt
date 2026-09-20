package circolareplus.ai

/**
 * Catalogo iOS: Apple Intelligence (Tier 1, di sistema) più due modelli MLX (Tier 2, da
 * scaricare) per gli iPhone senza Apple Intelligence (11-14, 15 base).
 *
 * Apple Intelligence: nessun download, disponibile su iPhone 15 Pro+ con iOS 26+.
 * `LocalModelStore.download()` su iOS ritorna immediatamente "Installed"/"Failed" per questa
 * voce, cosi' il pulsante "Scarica" della UI (pensata per Android) funziona come "Attiva"
 * istantaneo.
 *
 * Modelli MLX: **`downloadUrl` qui non è un URL HTTP diretto** come su Android, ma l'id del repo
 * HuggingFace in formato MLX (org `mlx-community`) — la libreria MLX Swift scarica uno snapshot
 * dell'intero repo (safetensors + config), non un singolo file `.litertlm`/`.gguf`. Vedi
 * `MLXLocalBridge.kt` per come viene consumato questo campo.
 *
 * **ATTENZIONE — repo MLX da ricontrollare al primo download reale.** Come per Phi su Android
 * (vedi `LocalAiModels.android.kt`), questo ambiente non ha accesso di rete a huggingface.co: i
 * repo id sotto sono quelli più plausibili per l'org `mlx-community` (stessa convenzione di
 * naming già in uso lì), non una verifica con una richiesta HTTP reale. **L'intera integrazione
 * MLX resta comunque non compilabile/testabile da qui** — serve un Mac con Xcode (vedi
 * `MLXLocalEngine.swift`).
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

    val PHI_35_MINI_MLX = LocalAiModel(
        id = "phi-3.5-mini-mlx",
        displayName = "Phi-3.5 mini (MLX)",
        fileName = "phi-3.5-mini-mlx",
        downloadUrl = "mlx-community/Phi-3.5-mini-instruct-4bit",
        approxSizeBytes = 2_200_000_000, // stima non verificata
        tier = DeviceTier.MID,
        recommendedRamMb = 4_500, // stima non verificata
        preferGpu = false, // MLX su iOS usa sempre la GPU via Metal, non c'è un ripiego CPU da scegliere
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Per iPhone senza Apple Intelligence (11-14, 15 base): scaricato una volta, " +
            "gira via MLX Swift. Integrazione non ancora attiva in questa build."
    )

    val GEMMA_2B_MLX = LocalAiModel(
        id = "gemma-2b-mlx",
        displayName = "Gemma 2B (MLX)",
        fileName = "gemma-2b-mlx",
        downloadUrl = "mlx-community/gemma-2-2b-it-4bit",
        approxSizeBytes = 1_600_000_000, // stima non verificata
        tier = DeviceTier.LOW,
        recommendedRamMb = 3_600, // stima non verificata
        preferGpu = false,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Alternativa più leggera a Phi-3.5 mini per gli iPhone più datati fra quelli " +
            "senza Apple Intelligence. Integrazione non ancora attiva in questa build."
    )

    actual val all: List<LocalAiModel> = listOf(APPLE_INTELLIGENCE, PHI_35_MINI_MLX, GEMMA_2B_MLX)

    actual fun byId(id: String?): LocalAiModel? = all.firstOrNull { it.id == id }

    // Apple Intelligence resta sempre la voce "consigliata": è di sistema, gratuita in termini di
    // spazio, e migliore in qualità quando disponibile. Le due voci MLX compaiono comunque in
    // selectableFor per chi non ce l'ha — la UI Impostazioni distingue "consigliato" da
    // "disponibile davvero" chiamando LocalModelStore, non recommendedFor.
    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = APPLE_INTELLIGENCE

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> = all

    actual fun knownFileNames(): Set<String> = all.map { it.fileName }.toSet()
}
