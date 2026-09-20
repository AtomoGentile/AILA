package circolareplus.ai

/**
 * Catalogo Android: cinque modelli scaricabili.
 *
 * **I Qwen sono usciti**, sostituiti da Phi: riportati lenti sul campo rispetto a Gemma 4 a
 * parità di fascia. Restano le due assenze definitive, provate sul campo:
 * - Le varianti `-gpu` non si avviano: contengono solo pesi preconfezionati per GPU, e l'alternativa
 *   su CPU non trova nulla da caricare.
 * - **Gemma 4 12B non ha mai funzionato**, coerente con il suo model card: è l'unico della famiglia
 *   senza una sola riga di misure su Android, perché Google lo presenta come modello da computer.
 *
 * **ATTENZIONE — voci Phi da ricontrollare al primo download reale.** A differenza di ogni altra
 * voce di questo catalogo (tutte verificate una per una: HTTP 200 anonimo, `gated: false`,
 * dimensione byte-esatta — vedi TODO.md), i repo/file Phi qui sotto sono stati scelti in base alla
 * convenzione già in uso da `litert-community` (stesso schema di Gemma/Qwen) ma **non con una
 * richiesta HTTP reale**: questo ambiente di sviluppo non ha accesso di rete verso
 * huggingface.co. La prima volta che qualcuno prova a scaricarne uno va controllato che risponda
 * 200 e che `approxSizeBytes` combaci col byte — se un nome fosse leggermente diverso (succede,
 * `litert-community` non è sempre coerente fra un modello e l'altro), è un fix di una riga qui.
 * Nota anche: Phi non ha una taglia paragonabile a Qwen3.5 0.8B (il più piccolo della famiglia è
 * ~3,8B parametri): la fascia LOW perde un modello davvero "leggero".
 */
actual object LocalAiCatalog {
    private const val HF = "https://huggingface.co"

    /**
     * Tier 1: Gemini Nano di sistema via AICore. Nessun download (come Apple Intelligence su
     * iOS): `downloadUrl` vuoto e `approxSizeBytes = 0` sono il segnale che [LocalModelStore]
     * legge per trattarla come modello "di sistema" invece che come file da scaricare.
     *
     * **Non è ancora collegata all'SDK reale** — vedi `AiCoreEngine.kt`. Resta in catalogo (in
     * fondo alla lista, mai proposta come consigliata) perché la UI Impostazioni la mostri già
     * pronta il giorno in cui l'integrazione sarà completata, senza dover toccare la schermata.
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
        description = "Motore di sistema Android. Nessun download, ma non ancora attivo in " +
            "questa build (integrazione da completare, vedi AiCoreEngine.kt)."
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

    // --- Phi (sostituiscono Qwen3.5: repo/dimensioni da ricontrollare, vedi avviso sopra) ---
    val PHI_35_MINI_INT4 = LocalAiModel(
        id = "phi-3.5-mini-int4",
        displayName = "Phi-3.5 mini (int4)",
        fileName = "Phi-3.5-mini-instruct_int4.litertlm",
        downloadUrl = "$HF/litert-community/Phi-3.5-mini-instruct/resolve/main/Phi-3.5-mini-instruct_int4.litertlm",
        approxSizeBytes = 2_200_000_000, // stima
        tier = DeviceTier.LOW,
        recommendedRamMb = 4_200, // stima: Phi non ha una taglia "leggera" come Qwen 0.8B
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Quantizzazione più aggressiva di Phi-3.5 mini. Non esiste un Phi paragonabile " +
            "a Qwen3.5 0.8B per peso: questa è la fascia più leggera disponibile nella famiglia."
    )

    val PHI_35_MINI_INT8 = LocalAiModel(
        id = "phi-3.5-mini-int8",
        displayName = "Phi-3.5 mini (int8)",
        fileName = "Phi-3.5-mini-instruct_int8.litertlm",
        downloadUrl = "$HF/litert-community/Phi-3.5-mini-instruct/resolve/main/Phi-3.5-mini-instruct_int8.litertlm",
        approxSizeBytes = 4_000_000_000, // stima
        tier = DeviceTier.MID,
        recommendedRamMb = 5_800, // stima
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Fascia media: stessa famiglia di Phi-3.5 mini con quantizzazione meno aggressiva."
    )

    val PHI_4_MINI = LocalAiModel(
        id = "phi-4-mini",
        displayName = "Phi-4 mini",
        fileName = "Phi-4-mini-instruct_mixed_int4.litertlm",
        downloadUrl = "$HF/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_mixed_int4.litertlm",
        approxSizeBytes = 3_800_000_000, // stima
        tier = DeviceTier.HIGH,
        recommendedRamMb = 6_200, // stima
        preferGpu = true,
        supportsActions = true,
        maxOutputTokens = 900,
        description = "Il più capace della famiglia Phi nel catalogo."
    )

    actual val all: List<LocalAiModel> = listOf(
        AICORE, PHI_35_MINI_INT4, PHI_35_MINI_INT8, PHI_4_MINI, GEMMA4_E2B, GEMMA4_E4B
    )

    actual fun byId(id: String?): LocalAiModel? = all.firstOrNull { it.id == id }

    actual fun recommendedFor(tier: DeviceTier): LocalAiModel = when (tier) {
        DeviceTier.HIGH -> GEMMA4_E4B
        DeviceTier.MID -> GEMMA4_E2B
        DeviceTier.LOW, DeviceTier.UNKNOWN -> PHI_35_MINI_INT4
    }

    actual fun selectableFor(totalRamMb: Int): List<LocalAiModel> {
        val recommended = recommendedFor(deviceTierForRam(totalRamMb))
        return listOf(recommended) + all.filter { it.id != recommended.id }
    }

    actual fun knownFileNames(): Set<String> = all.map { it.fileName }.toSet()
}
