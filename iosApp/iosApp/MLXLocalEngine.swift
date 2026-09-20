import Foundation
import shared

/// Implementazione del bridge MLXLocalBridge in Swift — Tier 2 iOS, per gli iPhone senza Apple
/// Intelligence (11-14, 15 base).
///
/// **Stato: scheletro non collegato all'SDK reale.** Questo ambiente di sviluppo non ha un Mac:
/// non è possibile eseguire `xcodegen generate`/`xcodebuild`, quindi non è possibile verificare la
/// superficie esatta del pacchetto `mlx-swift-examples` (nomi di classi/metodi per caricare un
/// modello e generare testo) né aggiungere il pacchetto SPM con certezza che risolva. Aggiungere
/// import/chiamate indovinate avrebbe reso questo file non compilabile per costruzione — peggio
/// che dichiararlo esplicitamente non ancora pronto. Ogni metodo qui sotto è quindi "onesto": non
/// finge di funzionare, cosicché [MLXLocalBridgeHolder] può restare iniettato (vedi `iOSApp.swift`)
/// senza rischiare comportamenti silenziosamente sbagliati — la catena Kotlin ricade sul cloud
/// esattamente come farebbe se il bridge non fosse mai stato iniettato.
///
/// **Per completare l'integrazione vera** (da fare su un Mac con Xcode):
/// 1. In `iosApp/project.yml`, aggiungere ai `packages:` il pacchetto
///    `https://github.com/ml-explore/mlx-swift-examples` e, come dipendenza del target `iosApp`,
///    i prodotti `MLXLLM`/`MLXLMCommon` (verificare i nomi esatti nel `Package.swift` del repo al
///    momento della build: sono cambiati più volte durante lo sviluppo della libreria).
/// 2. `import MLXLLM` / `import MLXLMCommon` qui sopra.
/// 3. In `download`, usare l'API di download del modello della libreria (tipicamente basata su
///    `Hub`/`HubApi` di `swift-transformers`, con un `progressHandler`) per scaricare lo snapshot
///    del repo `modelRepoId` (org `mlx-community`) in `Documents/ai-models/<modelId>/`.
/// 4. In `generate`, caricare il modello scaricato (`LLMModelFactory` o equivalente al momento
///    della build) e generare in streaming, chiamando `stopWhen` ad ogni chunk come fa già
///    `AppleIntelligenceEngine.generate` per il testo accumulato.
class MLXLocalEngine: MLXLocalBridge {

    func isDownloaded(modelId: String) -> Bool {
        // TODO(MLX): controllare se `Documents/ai-models/<modelId>/` esiste ed è completo.
        return false
    }

    func localSizeBytes(modelId: String) -> Int64 {
        return 0
    }

    func download(
        modelId: String,
        modelRepoId: String,
        onProgress: @escaping (Int64, Int64) -> Void
    ) async throws -> Bool {
        throw NSError(
            domain: "MLXLocalEngine",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: unavailableReason(modelId: modelId) ?? "MLX non ancora collegato."]
        )
    }

    func delete(modelId: String) -> Bool {
        return false
    }

    func unavailableReason(modelId: String) -> String? {
        return "AI locale MLX non è ancora collegata in questa build: integrazione da completare " +
            "(vedi commento in cima a MLXLocalEngine.swift)."
    }

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
            code: -2,
            userInfo: [NSLocalizedDescriptionKey: unavailableReason(modelId: modelId) ?? "MLX non ancora collegato."]
        )
    }
}
