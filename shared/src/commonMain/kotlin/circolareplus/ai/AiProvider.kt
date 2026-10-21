package circolareplus.ai

/**
 * Provider AI selezionabili in Impostazioni.
 *
 * Dalla v1.5.0 l'assistente non chiede per forza una chiave personale: il predefinito è
 * [AILA_CLOUD], il servizio incluso dell'app, che vale per tutti. Storia di questo elenco:
 * GitHub Models era un tempo un'alternativa dichiarata ma di fatto morta in app — il suo
 * classificatore veniva costruito soltanto dal pulsante "Prova token" delle Impostazioni e
 * non è mai stato collegato alla classificazione vera, che passava sempre e comunque da
 * [ClientSideAiClassifier]. Fu tolto e al suo posto arrivò [ON_DEVICE], che gira davvero sul
 * telefono con un modello scaricato una volta sola.
 */
enum class AiProvider(val id: String, val label: String, val description: String) {
    AILA_CLOUD(
        id = "AILA_CLOUD",
        label = "AILA (incluso)",
        description = "Il chatbot incluso nell'app: la chat passa dal Worker di AILA su " +
            "Gemma 4 26B A4B (Cloudflare Workers AI). Nessuna chiave da configurare, " +
            "vale per tutti — la quota giornaliera la conta Cloudflare e si rinnova " +
            "tutti i giorni"
    ),
    GOOGLE_AI_STUDIO(
        id = "GOOGLE_AI_STUDIO",
        label = "Google AI Studio",
        description = "Gemini via API key personale — gratuito, ma serve rete a ogni circolare"
    ),
    COMPATIBLE_API(
        id = "COMPATIBLE_API",
        label = "API compatibile",
        description = "Endpoint compatibili con le API OpenAI o Anthropic"
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
         * `"GITHUB_MODELS"` (e chi non ha mai scelto niente, la variabile vuota/di default di
         * prima) ricadono su [AILA_CLOUD] — il servizio incluso, che da v1.5.0 vale per tutti e
         * non chiede configurazione — invece di far crashare l'app con una
         * `IllegalArgumentException` di `valueOf`. Chi ha esplicitamente salvato un altro
         * provider lo continua a usare.
         */
        fun fromId(id: String?): AiProvider =
            values().firstOrNull { it.id == id } ?: AILA_CLOUD
    }

    /** `true` quando la chat passa dal servizio incluso e non da chiavi o modelli personali. */
    val isAilaChatIncluded: Boolean get() = this == AILA_CLOUD
}
