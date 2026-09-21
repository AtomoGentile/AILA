import Foundation
import shared

#if canImport(MLXLLM)
import MLXLLM
import MLXLMCommon

/// Implementazione del bridge MLXLocalBridge in Swift — Tier 2 iOS, per gli iPhone senza Apple
/// Intelligence (11-14, 15 base).
///
/// Scritta contro l'API reale di `mlx-swift-lm` (dove sono confluite le librerie riusabili di
/// `mlx-swift-examples`), verificata con una ricerca web il 20/9/2026 leggendo i sorgenti su
/// GitHub: `LLMModelFactory.shared.loadContainer(configuration:progressHandler:)`,
/// `ModelConfiguration(id:)` per caricare un repo HuggingFace arbitrario (non serve un
/// `ModelRegistry` predefinito), `ChatSession(container, instructions:generateParameters:)` con
/// `respond(to:) async throws -> String`.
///
/// **Mai compilata né eseguita**: questo ambiente non ha un Mac per `xcodegen generate`/
/// `xcodebuild`. Due limiti noti, onesti, non nascosti:
/// - `respond(to:)` ritorna il testo completo in un colpo solo: `stopWhen` (fermarsi appena il
///   JSON è completo, come fa `LocalAiClassifier` con LiteRT-LM/Apple Intelligence) non è
///   applicato qui. `ChatSession` espone anche una variante in streaming nei sorgenti trovati,
///   ma non ne ho confermato la firma esatta — usarla è il primo miglioramento da fare una volta
///   che questo file compila davvero.
/// - `localSizeBytes`/la cancellazione reale dei pesi non sono implementate: `MLXLMCommon`
///   scarica e mette in cache i modelli tramite la sua integrazione con Hugging Face (non un
///   percorso file gestito da questa app come su Android), e non ho verificato l'API per
///   ispezionare/cancellare quella cache dall'esterno.
class MLXLocalEngine: MLXLocalBridge {

    private var containers: [String: ModelContainer] = [:]
    private var sessions: [String: ChatSession] = [:]

    func isDownloaded(modelId: String) -> Bool {
        containers[modelId] != nil
    }

    func localSizeBytes(modelId: String) -> Int64 {
        // TODO(MLX): non verificato come leggere la dimensione della cache di MLXLMCommon.
        return 0
    }

    func download(
        modelId: String,
        modelRepoId: String,
        onProgress: @escaping (Int64, Int64) -> Void
    ) async throws -> Bool {
        let configuration = ModelConfiguration(id: modelRepoId)
        let container = try await LLMModelFactory.shared.loadContainer(
            configuration: configuration
        ) { progress in
            // fractionCompleted è un Double 0...1 (Foundation.Progress): lo si riporta come
            // millesimi per restare nell'Int64 di ModelDownloadState senza perdere precisione.
            onProgress(Int64(progress.fractionCompleted * 1000), 1000)
        }
        containers[modelId] = container
        sessions[modelId] = nil // ricreata al primo generate, con le istruzioni di quella chiamata
        return true
    }

    func delete(modelId: String) -> Bool {
        let existed = containers[modelId] != nil
        containers[modelId] = nil
        sessions[modelId] = nil
        return existed
    }

    func unavailableReason(modelId: String) -> String? {
        containers[modelId] == nil ? "Modello non ancora scaricato." : nil
    }

    func generate(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int32,
        timeoutMillis: Int64,
        stopWhen: @escaping (String) -> KotlinBoolean
    ) async throws -> String {
        guard let container = containers[modelId] else {
            throw NSError(
                domain: "MLXLocalEngine",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: unavailableReason(modelId: modelId) ?? "Modello non disponibile."]
            )
        }

        let session = sessions[modelId] ?? ChatSession(
            container,
            instructions: systemPrompt,
            generateParameters: GenerateParameters(maxTokens: Int(maxOutputTokens))
        )
        sessions[modelId] = session

        let timeoutSeconds = Double(timeoutMillis) / 1000.0
        return try await withThrowingTaskGroup(of: String.self) { group in
            group.addTask {
                try await session.respond(to: userPrompt)
            }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(timeoutSeconds * 1_000_000_000))
                throw NSError(
                    domain: "MLXLocalEngine",
                    code: -2,
                    userInfo: [NSLocalizedDescriptionKey: "Timeout superato dopo \(timeoutMillis) ms"]
                )
            }
            guard let result = try await group.next() else {
                throw NSError(
                    domain: "MLXLocalEngine",
                    code: -3,
                    userInfo: [NSLocalizedDescriptionKey: "Errore sconosciuto nella generazione"]
                )
            }
            group.cancelAll()
            return result
        }
    }
}

#else

// Senza il pacchetto MLX (oggi tolto da project.yml: `mlx-swift-lm` su `main` richiede Swift
// tools 6.3 e non si risolveva con l'Xcode dei runner, quindi la CI iOS non compilava nulla)
// il bridge resta iniettato ma dichiara sempre il Tier 2 non disponibile. Il catalogo iOS non
// espone comunque i modelli MLX (vedi LocalAiModels.ios.kt). Per riattivare: rimettere il pacchetto
// in project.yml e la classe vera sotto si compila da sola grazie a `canImport`.
class MLXLocalEngine: MLXLocalBridge {
    func isDownloaded(modelId: String) -> Bool { false }
    func localSizeBytes(modelId: String) -> Int64 { 0 }
    func download(
        modelId: String,
        modelRepoId: String,
        onProgress: @escaping (Int64, Int64) -> Void
    ) async throws -> Bool {
        throw NSError(
            domain: "MLXLocalEngine",
            code: -10,
            userInfo: [NSLocalizedDescriptionKey: "MLX non è incluso in questa build."]
        )
    }
    func delete(modelId: String) -> Bool { false }
    func unavailableReason(modelId: String) -> String? { "MLX non è incluso in questa build." }
    func generate(
        modelId: String,
        systemPrompt: String,
        userPrompt: String,
        maxOutputTokens: Int32,
        timeoutMillis: Int64,
        stopWhen: @escaping (String) -> KotlinBoolean
    ) async throws -> String {
        throw NSError(
            domain: "MLXLocalEngine",
            code: -10,
            userInfo: [NSLocalizedDescriptionKey: "MLX non è incluso in questa build."]
        )
    }
}
#endif
