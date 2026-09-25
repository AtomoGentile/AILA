import Foundation
import FoundationModels
import shared

/**
 * Implementazione del bridge AppleIntelligenceBridge in Swift.
 *
 * Usa FoundationModels (iOS 26+) per accedere ad Apple Intelligence tramite il modello di
 * sistema. Non necessita download, API key, o configurazione: gira tutto sul dispositivo.
 *
 * Ogni metodo della classe SwiftUI implementa il corrispondente metodo Kotlin dell'interfaccia
 * AppleIntelligenceBridge, esportata come protocollo Objective-C dal framework `shared`.
 */
class AppleIntelligenceEngine: AppleIntelligenceBridge {

    /// Generazione in corso, per [cancelGeneration] (il tasto Stop). Protetta da `lock`: la
    /// imposta la chiamata di Kotlin e la legge lo stop, da thread diversi.
    private var currentTask: Task<String, Error>?
    private let lock = NSLock()

    /**
     * Ferma la generazione in corso. La cancellazione arriva allo stream di FoundationModels,
     * che si interrompe subito invece di continuare fino alla fine o al timeout.
     */
    func cancelGeneration() {
        lock.lock()
        let task = currentTask
        lock.unlock()
        task?.cancel()
    }

    /**
     * Verifica se Apple Intelligence è disponibile e pronto.
     *
     * Controlla `SystemLanguageModel.default.availability`:
     * - `.available` → dispositivo supportato, attivato, modello pronto
     * - `.unavailable(let reason)` → motivo specifico
     */
    func isAvailable() -> Bool {
        return SystemLanguageModel.default.availability == .available
    }

    /**
     * Motivo leggibile se Apple Intelligence non è disponibile.
     *
     * Mappa i casi di `SystemLanguageModel.Availability.Reason` a messaggi in italiano
     * comprensibili per un utente non tecnico.
     */
    func unavailableReason() -> String? {
        guard !isAvailable() else { return nil }

        let reason = SystemLanguageModel.default.availability
        if case .unavailable(let reason) = reason {
            switch reason {
            case .deviceNotEligible:
                return "Questo iPhone non supporta Apple Intelligence. Serve un iPhone 15 Pro o successivo."
            case .appleIntelligenceNotEnabled:
                return "Attiva Apple Intelligence nelle Impostazioni di sistema per usare l'AI locale."
            case .modelNotReady:
                return "Il modello si sta preparando. Riprova tra poco."
            @unknown default:
                return "Apple Intelligence non disponibile. Controlla le Impostazioni di sistema."
            }
        }

        return "Apple Intelligence non disponibile."
    }

    /**
     * Genera una risposta in streaming fermandosi appena stopWhen ritorna true.
     *
     * Crea una sessione di linguaggio con systemPrompt come istruzioni, poi chiama
     * session.streamResponse(to: userPrompt, options: options) e itera lo stream. Ad ogni chunk accumulato,
     * chiama la closure Kotlin stopWhen(testoAccumulato): se ritorna true, interrompe.
     *
     * Racchiude tutto in un timeout: se scade prima che stopWhen diventi true o la
     * generazione finisca, lancia un'eccezione descrittiva.
     *
     * @param systemPrompt Istruzioni di sistema per il modello
     * @param userPrompt Prompt dell'utente
     * @param timeoutMillis Timeout massimo in millisecondi
     * @param stopWhen Lambda Kotlin che decide quando interrompere
     * @return Testo generato accumulato
     * @throws NSError Se la generazione fallisce o scade il timeout
     */
    func generate(
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Int64,
        maxOutputTokens: Int32,
        temperature: Double,
        stopWhen: @escaping (String) -> KotlinBoolean
    ) async throws -> String {
        // La generazione vera gira in un Task a parte, cosi' cancelGeneration() la puo' fermare
        // anche se chi aspetta (la chiamata da Kotlin) non propaga la cancellazione.
        let task = Task<String, Error> {
            try await self.runGeneration(
                systemPrompt: systemPrompt,
                userPrompt: userPrompt,
                timeoutMillis: timeoutMillis,
                maxOutputTokens: maxOutputTokens,
                temperature: temperature,
                stopWhen: stopWhen
            )
        }
        lock.lock()
        currentTask = task
        lock.unlock()
        defer {
            lock.lock()
            if currentTask == task { currentTask = nil }
            lock.unlock()
        }
        return try await withTaskCancellationHandler {
            try await task.value
        } onCancel: {
            task.cancel()
        }
    }

    private func runGeneration(
        systemPrompt: String,
        userPrompt: String,
        timeoutMillis: Int64,
        maxOutputTokens: Int32,
        temperature: Double,
        stopWhen: @escaping (String) -> KotlinBoolean
    ) async throws -> String {
        // Timeout in secondi
        let timeoutSeconds = Double(timeoutMillis) / 1000.0

        // Crea la sessione con systemPrompt come istruzioni
        let session = LanguageModelSession(instructions: systemPrompt)

        // Temperatura bassa e tetto ai token, come sul motore Android: senza, il modello
        // campiona con i parametri di default (creativi) e non ha un limite di lunghezza.
        let options = GenerationOptions(
            temperature: temperature,
            maximumResponseTokens: Int(maxOutputTokens)
        )

        // Accumula il testo generato
        var accumulatedText = ""

        // Race fra la generazione e il timeout
        return try await withThrowingTaskGroup(of: String.self) { group in
            // Task 1: Generazione vera
            group.addTask {
                do {
                    // Genera in streaming: ogni elemento e' uno snapshot con il testo
                    // CUMULATIVO prodotto finora (non un delta), quindi si sostituisce
                    // accumulatedText invece di concatenarla.
                    for try await partial in session.streamResponse(to: userPrompt, options: options) {
                        accumulatedText = partial.content

                        // Chiama Kotlin per decidere se fermarsi
                        let shouldStop = stopWhen(accumulatedText).boolValue
                        if shouldStop {
                            break
                        }
                    }
                    return accumulatedText
                } catch {
                    throw NSError(
                        domain: "AppleIntelligenceEngine",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: AppleIntelligenceEngine.describe(error)]
                    )
                }
            }

            // Task 2: Timeout
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeoutSeconds * 1_000_000_000))
                throw NSError(
                    domain: "AppleIntelligenceEngine",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Timeout superato dopo \(timeoutMillis) ms"]
                )
            }

            // Ritorna il primo risultato disponibile (generazione o timeout)
            if let result = try await group.next() {
                group.cancelAll()
                return result
            }

            throw NSError(
                domain: "AppleIntelligenceEngine",
                code: -3,
                userInfo: [NSLocalizedDescriptionKey: "Errore sconosciuto nella generazione"]
            )
        }
    }

    /**
     * Messaggio leggibile per un errore di FoundationModels.
     *
     * Il caso "prompt troppo lungo" usa di proposito le parole "Exceeding the maximum number of
     * tokens allowed": e' la frase che LocalAiClassifier.isPromptTooLong (Kotlin) cerca per
     * ritentare con un prompt piu' corto. L'errore di Apple ("Exceeded model context window
     * size") non la contiene, quindi senza questa traduzione il ritentativo non scatterebbe mai.
     */
    static func describe(_ error: Error) -> String {
        if let generationError = error as? LanguageModelSession.GenerationError {
            switch generationError {
            case .exceededContextWindowSize:
                return "Exceeding the maximum number of tokens allowed: il prompt supera la finestra di contesto di Apple Intelligence."
            case .guardrailViolation:
                return "Apple Intelligence ha rifiutato la richiesta con i suoi filtri di sicurezza."
            case .unsupportedLanguageOrLocale:
                return "Apple Intelligence non supporta la lingua del dispositivo."
            default:
                break
            }
        }
        return "Generazione fallita: \(error.localizedDescription)"
    }
}
