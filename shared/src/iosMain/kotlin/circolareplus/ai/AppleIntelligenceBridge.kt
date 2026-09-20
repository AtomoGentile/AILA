package circolareplus.ai

/**
 * Implementata in Swift (AppleIntelligenceEngine.swift) sopra FoundationModels.
 * Iniettata da iOSApp.swift in AppleIntelligenceBridgeHolder.bridge prima che qualunque
 * schermata possa chiamare isOnDeviceAiAvailable()/LocalLlm().
 *
 * Kotlin/Native espone questa interfaccia come protocollo Objective-C alle classi Swift,
 * permettendo a Swift di implementarla e a Kotlin di chiamarla tramite il bridge holder.
 */
interface AppleIntelligenceBridge {
    /**
     * true se il dispositivo supporta Apple Intelligence, è attivo e il modello è pronto.
     */
    fun isAvailable(): Boolean

    /**
     * Motivo leggibile se isAvailable() è false: dispositivo non supportato, Apple
     * Intelligence disattivato nelle Impostazioni di sistema, o lingua non supportata.
     * Null se isAvailable() è true.
     */
    fun unavailableReason(): String?

    /**
     * Genera una risposta in streaming, fermandosi appena stopWhen(testo accumulato)
     * ritorna true, o dopo timeoutMillis. Lancia un'eccezione (che il chiamante Kotlin
     * già gestisce, vedi LocalAiPlatform.ios.kt) se la generazione fallisce.
     *
     * @param systemPrompt Istruzioni di sistema per il modello
     * @param userPrompt Prompt dell'utente
     * @param timeoutMillis Timeout massimo in millisecondi
     * @param maxOutputTokens Tetto ai token della risposta (`maximumResponseTokens` di Apple):
     *   senza, il modello puo' continuare fino a riempire la finestra di contesto.
     * @param temperature Temperatura di campionamento. Bassa (0.1) per produrre sempre lo stesso
     *   JSON ben formato, come sul motore Android.
     * @param stopWhen Lambda che decide quando interrompere la generazione basandosi sul testo accumulato
     * @return Stringa generata dal modello
     * @throws Exception Se la generazione fallisce o scade il timeout
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Long,
        maxOutputTokens: Int,
        temperature: Double,
        stopWhen: (String) -> Boolean
    ): String
}

/**
 * Holder singleton per il bridge Apple Intelligence.
 * Viene iniettato da Swift in iOSApp.swift.init() prima che MainViewController()
 * crei la UI. Non dovrebbe mai essere null in pratica (se lo è, il codice che lo
 * chiama lancerà un'eccezione esplicita invece di un NPE confuso).
 */
object AppleIntelligenceBridgeHolder {
    var bridge: AppleIntelligenceBridge? = null
}
