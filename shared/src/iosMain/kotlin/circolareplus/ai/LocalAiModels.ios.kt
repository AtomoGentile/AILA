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
 * **Repo MLX confermati esistenti** con una ricerca web il 20/9/2026 (non un accesso diretto a
 * huggingface.co, bloccato da questo ambiente): sia `mlx-community/Phi-3.5-mini-instruct-4bit`
 * sia `mlx-community/gemma-2-2b-it-4bit` sono repo reali. Le dimensioni restano una stima (la
 * ricerca non ha restituito il peso esatto). **L'integrazione resta comunque non compilabile/
 * testabile da qui** — serve un Mac con Xcode (vedi `MLXLocalEngine.swift`).
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
        // La finestra di Apple e' di 4096 token in TOTALE: istruzioni + richiesta + risposta.
        // Con il default (4096 solo in ingresso) piu' i 900 in uscita si sforava. 4096 - 900 di
        // risposta, meno un margine, fa 2800: il prompt dell'assistente si restringe di
        // conseguenza tramite AiPromptBuilder (maxPromptChars = maxInputTokens * 2).
        maxInputTokens = 2_800,
        description = "Il modello di sistema di Apple Intelligence. Nessun download: gira già " +
            "sul telefono se il dispositivo lo supporta (iPhone 15 Pro o successivo, iOS 26+)."
    )

    val PHI_35_MINI_MLX = LocalAiModel(
        id = "phi-3.5-mini-mlx",
        displayName = "Phi-3.5 mini (MLX)",
        fileName = "phi-3.5-mini-mlx",
        downloadUrl = "mlx-community/Phi-3.5-mini-instruct-4bit",
        approxSizeBytes = 2_200_000_000, // stima
        tier = DeviceTier.MID,
        recommendedRamMb = 4_500, // stima
        preferGpu = false, // MLX su iOS usa sempre la GPU via Metal, non c'è un ripiego CPU da scegliere
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Per iPhone senza Apple Intelligence (11-14, 15 base): scaricato una volta, " +
            "gira via MLX Swift. Scritta ma mai compilata in questa build (serve un Mac)."
    )

    val GEMMA_2B_MLX = LocalAiModel(
        id = "gemma-2b-mlx",
        displayName = "Gemma 2B (MLX)",
        fileName = "gemma-2b-mlx",
        downloadUrl = "mlx-community/gemma-2-2b-it-4bit",
        approxSizeBytes = 1_600_000_000, // stima
        tier = DeviceTier.LOW,
        recommendedRamMb = 3_600, // stima
        preferGpu = false,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Alternativa più leggera a Phi-3.5 mini per gli iPhone più datati fra quelli " +
            "senza Apple Intelligence. Scritta ma mai compilata in questa build (serve un Mac)."
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
