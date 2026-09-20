package circolareplus.ai

/**
 * Catalogo Android: tre modelli scaricabili.
 *
 * **I Qwen sono usciti**, sostituiti da Phi: riportati lenti sul campo rispetto a Gemma 4 a
 * parità di fascia. Restano le due assenze definitive, provate sul campo:
 * - Le varianti `-gpu` non si avviano: contengono solo pesi preconfezionati per GPU, e l'alternativa
 *   su CPU non trova nulla da caricare.
 * - **Gemma 4 12B non ha mai funzionato**, coerente con il suo model card: è l'unico della famiglia
 *   senza una sola riga di misure su Android, perché Google lo presenta come modello da computer.
 *
 * **Su Phi: un solo modello confermato, non tre.** Una ricerca web (20/9/2026, non un accesso
 * diretto a huggingface.co — bloccato da questo ambiente) ha confermato che esiste un solo
 * modello Phi in formato LiteRT-LM: `litert-community/Phi-4-mini-instruct`, file
 * `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm` (il nome del file è confermato
 * comparendo in un URL di download indicizzato; `approxSizeBytes` resta una stima — Phi-4-mini è
 * ~3,8 miliardi di parametri, int8 quantizzato). Non ho trovato conferma di varianti Phi più
 * leggere (int4) nel repo ufficiale — solo conversioni comunitarie non hostate lì. Niente Phi per
 * la fascia LOW/MID quindi: restano coperte da Gemma 4, come prima dell'introduzione di Phi.
 */
actual object LocalAiCatalog {
    private const val HF = "https://huggingface.co"

    /**
     * Tier 1: Gemini Nano di sistema via AICore. Nessun download (come Apple Intelligence su
     * iOS): `downloadUrl` vuoto e `approxSizeBytes = 0` sono il segnale che [LocalModelStore]
     * legge per trattarla come modello "di sistema" invece che come file da scaricare.
     *
     * Scritta contro l'API reale di AICore (verificata via ricerca web, vedi `AiCoreEngine.kt`)
     * ma mai compilata: la dipendenza Gradle non è mai stata risolta da questo ambiente. Non è
     * mai proposta come "consigliata" (serve una prima build reale prima di fidarsene).
     */
    val AICORE = LocalAiModel(
        id = "aicore",
        displayName = "Android AICore (Gemini Nano)",
        fileName = "aicore",
        downloadUrl = "",
        approxSizeBytes = 0L,
        tier = DeviceTier.HIGH,
        recommendedRamMb = 0,
        preferGpu = false,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Motore di sistema Android. Nessun download, ma integrazione mai " +
            "compilata in questa build (vedi AiCoreEngine.kt)."
    )

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

    // --- Phi (sostituisce Qwen3.5: unico repo/file confermato, vedi avviso sopra) ---
    val PHI_4_MINI = LocalAiModel(
        id = "phi-4-mini",
        displayName = "Phi-4 mini",
        fileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        downloadUrl = "$HF/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        approxSizeBytes = 4_200_000_000, // stima: 3,8B parametri int8, dimensione byte-esatta da confermare
        tier = DeviceTier.HIGH,
        recommendedRamMb = 5_800, // stima, stesso ordine di grandezza di Gemma 4 E4B
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Alternativa a Gemma 4 E4B per la fascia alta: stesso ordine di grandezza, " +
            "famiglia diversa. Unico Phi confermato in formato LiteRT-LM."
    )

    actual val all: List<LocalAiModel> = listOf(AICORE, GEMMA4_E2B, GEMMA4_E4B, PHI_4_MINI)

    actual fun byId(id: String?): LocalAiModel? = all.firstOrNull { it.id == id }

    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = when (tier) {
        DeviceTier.HIGH -> GEMMA4_E4B
        DeviceTier.MID -> GEMMA4_E2B
        DeviceTier.LOW, DeviceTier.UNKNOWN -> GEMMA4_E2B
    }

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> {
        val recommended = recommendedFor(deviceTierForRam(totalRamMb))
        return listOf(recommended) + all.filter { it.id != recommended.id }
    }

    actual fun knownFileNames(): Set<String> = all.map { it.fileName }.toSet()
}
