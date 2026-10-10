import UIKit
import UniformTypeIdentifiers
import shared

/// Implementazione in Swift del bridge GitaFileBridge (shared/iosMain/.../GitaFileBridge.kt):
/// condivisione di un PDF con lo share sheet di sistema, e scelta di un PDF dal selettore file.
/// Stesso pattern di SeatMapPdfShareBridge.swift. Il file passa per percorso su disco.
class GitaFileBridgeImpl: NSObject, GitaFileBridge, UIDocumentPickerDelegate {

    private var pickCallback: ((String?) -> KotlinUnit)?

    func shareFile(path: String) -> String? {
        guard let presenter = topViewController() else {
            return "nessuna schermata su cui aprire la condivisione"
        }
        let activity = UIActivityViewController(activityItems: [URL(fileURLWithPath: path)], applicationActivities: nil)
        if let popover = activity.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1)
        }
        presenter.present(activity, animated: true)
        return nil
    }

    func pickPdf(onResult: @escaping (String?) -> KotlinUnit) {
        guard let presenter = topViewController() else {
            _ = onResult(nil)
            return
        }
        pickCallback = onResult
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [UTType.pdf])
        picker.delegate = self
        picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let callback = pickCallback
        pickCallback = nil
        guard let source = urls.first else {
            _ = callback?(nil)
            return
        }
        // Il file scelto sta fuori dall'app: va letto dentro la sua security scope e copiato.
        let accessed = source.startAccessingSecurityScopedResource()
        defer {
            if accessed { source.stopAccessingSecurityScopedResource() }
        }
        let copy = FileManager.default.temporaryDirectory.appendingPathComponent(source.lastPathComponent)
        try? FileManager.default.removeItem(at: copy)
        do {
            try FileManager.default.copyItem(at: source, to: copy)
            _ = callback?(copy.path)
        } catch {
            print("GitaFileBridge: copia del PDF non riuscita: \(error)")
            _ = callback?(nil)
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        let callback = pickCallback
        pickCallback = nil
        _ = callback?(nil)
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
        guard var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return nil }
        while let presented = top.presentedViewController {
            top = presented
        }
        return top
    }
}
