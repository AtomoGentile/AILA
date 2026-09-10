package circolareplus.ai

/**
 * Provider AI selezionabili per la classificazione delle circolari.
 *
 * Sostituisce il vecchio elenco che aveva GitHub Models come alternativa: quel provider era di
 * fatto morto in app — il suo classificatore veniva costruito soltanto dal pulsante "Prova
 * token" delle Impostazioni e non è mai stato collegato alla classificazione vera, che passava
 * sempre e comunque da [ClientSideAiClassifier]. Al suo posto c'è [ON_DEVICE], che gira davvero
 * sul telefono con un modello scaricato una volta sola.
 */
enum class AiProvider(val id: String, val label: String, val description: String) {
    GOOGLE_AI_STUDIO(
        id = "GOOGLE_AI_STUDIO",
        label = "Google AI Studio",
        description = "Gemini via API key personale — gratuito, ma serve rete a ogni circolare"
    ),
    ON_DEVICE(
        id = "ON_DEVICE",
        label = "AI locale",
        description = "Modello scaricato sul telefono — funziona offline, nessun dato esce dal dispositivo"
    );

    companion object {
        /**
         * Legge il provider salvato nelle impostazioni.
         *
         * Tollerante ai valori sconosciuti di proposito: le installazioni che avevano salvato
         * `"GITHUB_MODELS"` ricadono su Google AI Studio invece di far crashare l'app con una
         * `IllegalArgumentException` di `valueOf` — non serve una migrazione dello storage.
         */
        fun fromId(id: String?): AiProvider =
            values().firstOrNull { it.id == id } ?: GOOGLE_AI_STUDIO
    }
}
