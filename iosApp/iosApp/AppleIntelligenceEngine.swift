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
            case .systemLanguageNotSupported:
                return "La lingua del tuo dispositivo non è supportata da Apple Intelligence. Prova l'inglese nelle Impostazioni."
            case .notInstalled:
                return "Apple Intelligence non è ancora installato. Riprova tra poco."
            default:
                return "Apple Intelligence non disponibile. Controlla le Impostazioni di sistema."
            }
        }

        return "Apple Intelligence non disponibile."
    }

    /**
     * Genera una risposta in streaming fermandosi appena stopWhen ritorna true.
     *
     * Crea una sessione di linguaggio con systemPrompt come istruzioni, poi chiama
     * session.streamResponse(to: userPrompt) e itera lo stream. Ad ogni chunk accumulato,
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
        timeoutMillis: Long,
        stopWhen: (String) -> KotlinBoolean
    ) async throws -> String {
        // Timeout in secondi
        let timeoutSeconds = Double(timeoutMillis) / 1000.0

        // Crea la sessione con systemPrompt come istruzioni
        let session = LanguageModelSession(instructions: systemPrompt)

        // Accumula il testo generato
        var accumulatedText = ""

        // Race fra la generazione e il timeout
        return try await withThrowingTaskGroup(of: String.self) { group in
            // Task 1: Generazione vera
            group.addTask {
                do {
                    // Genera in streaming
                    for try await chunk in session.streamResponse(to: userPrompt) {
                        accumulatedText += chunk

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
                        userInfo: [NSLocalizedDescriptionKey: "Generazione fallita: \(error.localizedDescription)"]
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
    