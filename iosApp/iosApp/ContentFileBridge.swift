import UIKit
import PhotosUI
import ImageIO
import AVFoundation
import Speech
import UniformTypeIdentifiers
import shared

/// Files remain local until the user explicitly starts a cloud operation in the preview.
final class ContentFileBridgeImpl: NSObject, ContentFileBridge, UIDocumentPickerDelegate, PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate, AVAudioRecorderDelegate {
    private var pickCallback: ((String?) -> Void)?
    private var photoCallback: ((String?) -> Void)?
    private var photoError: ((String) -> Void)?
    private var cameraCallback: ((String?) -> Void)?
    private var cameraError: ((String) -> Void)?
    private var recorder: AVAudioRecorder?
    private var recordCallback: ((String?) -> Void)?
    private var recordError: ((String) -> Void)?
    private var permissionRequest: UUID?

    func pickFile(onResult: @escaping (String?) -> Void) {
        guard let presenter = presenter() else { onResult(nil); return }
        pickCallback = onResult
        let docx = UTType(filenameExtension: "docx") ?? .data
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.pdf, .plainText, .image, .audio, docx], asCopy: true)
        picker.delegate = self; picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let callback = pickCallback; pickCallback = nil
        guard let source = urls.first else { callback?(nil); return }
        callback?(Self.localCopy(source)?.path)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { pickCallback?(nil); pickCallback = nil }
    func pickPhoto(onResult: @escaping (String?) -> Void, onError: @escaping (String) -> Void) {
        guard let presenter = presenter() else { onError("Selettore foto non disponibile"); return }
        photoCallback = onResult
        photoError = onError
        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1
        configuration.preferredAssetRepresentationMode = .current
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        presenter.present(picker, animated: true)
    }
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        let callback = photoCallback
        let failure = photoError
        photoCallback = nil
        photoError = nil
        guard let provider = results.first?.itemProvider else { callback?(nil); return }
        guard let imageType = provider.registeredTypeIdentifiers.first(where: { UTType($0)?.conforms(to: .image) == true }) else {
            failure?("Formato foto non leggibile.")
            return
        }
        provider.loadFileRepresentation(forTypeIdentifier: imageType) { url, _ in
            let path = url.flatMap { Self.preparePhoto($0) }
            DispatchQueue.main.async {
                if let path { callback?(path) }
                else { failure?("Impossibile leggere la foto selezionata.") }
            }
        }
    }
    func takePhoto(onResult: @escaping (String?) -> Void, onError: @escaping (String) -> Void) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            onError("Fotocamera non disponibile su questo dispositivo.")
            return
        }
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                guard granted else {
                    onError("Consenti l’accesso alla fotocamera nelle impostazioni per scattare una foto.")
                    return
                }
                guard let presenter = self.presenter() else {
                    onError("Fotocamera non disponibile.")
                    return
                }
                self.cameraCallback = onResult
                self.cameraError = onError
                let picker = UIImagePickerController()
                picker.sourceType = .camera
                picker.mediaTypes = [UTType.image.identifier]
                picker.delegate = self
                presenter.present(picker, animated: true)
            }
        }
    }
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        let callback = cameraCallback
        let failure = cameraError
        cameraCallback = nil
        cameraError = nil
        guard let image = info[.originalImage] as? UIImage else {
            failure?("Impossibile acquisire la foto.")
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let path = Self.prepareCapturedImage(image)
            DispatchQueue.main.async {
                if let path { callback?(path) }
                else { failure?("Impossibile salvare la foto scattata.") }
            }
        }
    }
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        let callback = cameraCallback
        cameraCallback = nil
        cameraError = nil
        callback?(nil)
    }
    private static func prepareCapturedImage(_ image: UIImage) -> String? {
        guard let rawData = image.jpegData(compressionQuality: 0.92),
              let source = CGImageSourceCreateWithData(rawData as CFData, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: 3000
              ] as CFDictionary) else { return nil }
        let target = FileManager.default.temporaryDirectory
            .appendingPathComponent("Camera-\(UUID().uuidString).jpg")
        guard let destination = CGImageDestinationCreateWithURL(target as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(destination, thumbnail, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
        if CGImageDestinationFinalize(destination) { return target.path }
        try? FileManager.default.removeItem(at: target)
        return nil
    }
    private static func preparePhoto(_ source: URL) -> String? {
        guard let image = CGImageSourceCreateWithURL(source as CFURL, nil),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(image, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: 3000
              ] as CFDictionary) else { return nil }
        let target = FileManager.default.temporaryDirectory
            .appendingPathComponent("Foto-\(UUID().uuidString).jpg")
        guard let destination = CGImageDestinationCreateWithURL(target as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(destination, thumbnail, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
        if CGImageDestinationFinalize(destination) { return target.path }
        try? FileManager.default.removeItem(at: target)
        return nil
    }
    func openFile(path: String) -> String? {
        guard let presenter = presenter() else { return "Nessuna schermata disponibile" }
        let sheet = UIActivityViewController(activityItems: [URL(fileURLWithPath: path)], applicationActivities: nil)
        if let popover = sheet.popoverPresentationController { popover.sourceView = presenter.view; popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 1, height: 1) }
        presenter.present(sheet, animated: true); return nil
    }
    func startRecording(onStarted: @escaping () -> Void, onResult: @escaping (String?) -> Void, onError: @escaping (String) -> Void) {
        guard recorder == nil, permissionRequest == nil else { return }
        let request = UUID(); permissionRequest = request
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self, self.permissionRequest == request else { return }
                self.permissionRequest = nil
                guard granted else { onError("Consenti il microfono per registrare, oppure scegli un file audio."); return }
                do {
                    let audio = AVAudioSession.sharedInstance()
                    try audio.setCategory(.record, mode: .default); try audio.setActive(true)
                    let url = FileManager.default.temporaryDirectory.appendingPathComponent("Vocale-\(UUID().uuidString).m4a")
                    let recorder = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 64000])
                    recorder.delegate = self; self.recordCallback = onResult; self.recordError = onError; self.recorder = recorder
                    guard recorder.record(forDuration: 599) else { throw NSError(domain: "AILA", code: 1) }
                    onStarted()
                } catch { self.cancelRecording(); onError("Registrazione non disponibile sul dispositivo.") }
            }
        }
    }
    func stopRecording() { recorder?.stop() }
    func cancelRecording() {
        permissionRequest = nil; recordCallback = nil; recordError = nil
        let file = recorder?.url; recorder?.stop(); recorder = nil
        if let file { try? FileManager.default.removeItem(at: file) }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        guard self.recorder === recorder else { return }
        let callback = recordCallback; let failure = recordError
        self.recorder = nil; recordCallback = nil; recordError = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        if flag { callback?(recorder.url.path) } else { try? FileManager.default.removeItem(at: recorder.url); failure?("Registrazione interrotta.") }
    }
    private var localSpeechRecognizers: [String: SFSpeechRecognizer] = [:]
    private var localSpeechTasks: [String: SFSpeechRecognitionTask] = [:]
    private var localSpeechRequests = Set<String>()
    private lazy var localSpeechProbe = SFSpeechRecognizer(locale: Locale(identifier: "it-IT"))
    func isLocalSpeechAvailable() -> Bool {
        if !Thread.isMainThread { return DispatchQueue.main.sync { self.isLocalSpeechAvailable() } }
        guard let recognizer = localSpeechProbe else { return false }
        return recognizer.supportsOnDeviceRecognition
    }
    func transcribeLocalAudio(path: String, onResult: @escaping (String?) -> Void) {
        guard isLocalSpeechAvailable() else { onResult(nil); return }
        localSpeechRequests.insert(path)
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            DispatchQueue.main.async {
                guard let self, self.localSpeechRequests.contains(path) else { return }
                guard status == .authorized, let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "it-IT")), recognizer.supportsOnDeviceRecognition else {
                    self.localSpeechRequests.remove(path); onResult(nil); return
                }
                self.localSpeechRecognizers[path] = recognizer
                let request = SFSpeechURLRecognitionRequest(url: URL(fileURLWithPath: path))
                request.requiresOnDeviceRecognition = true
                request.shouldReportPartialResults = false
                request.taskHint = .dictation
                self.localSpeechTasks[path] = recognizer.recognitionTask(with: request) { [weak self] result, error in
                    DispatchQueue.main.async {
                        guard let self, self.localSpeechRequests.contains(path) else { return }
                        if let result, result.isFinal {
                            self.localSpeechRequests.remove(path); self.localSpeechTasks.removeValue(forKey: path); self.localSpeechRecognizers.removeValue(forKey: path)
                            onResult(result.bestTranscription.formattedString)
                        } else if error != nil {
                            self.localSpeechRequests.remove(path); self.localSpeechTasks.removeValue(forKey: path); self.localSpeechRecognizers.removeValue(forKey: path)
                            onResult(nil)
                        }
                    }
                }
            }
        }
    }
    func cancelLocalAudio(path: String) {
        localSpeechRequests.remove(path)
        localSpeechTasks.removeValue(forKey: path)?.cancel()
        localSpeechRecognizers.removeValue(forKey: path)
    }
    static func receiveIncomingURL(_ url: URL) {
        guard url.isFileURL, let local = localCopy(url) else { return }
        IosContentIngress.shared.importFile(path: local.path)
    }
    private static func localCopy(_ source: URL) -> URL? {
        let access = source.startAccessingSecurityScopedResource(); defer { if access { source.stopAccessingSecurityScopedResource() } }
        guard let values = try? source.resourceValues(forKeys: [.fileSizeKey]), let size = values.fileSize, size > 0, size <= 20 * 1024 * 1024 else { return nil }
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("aila-import-\(UUID().uuidString)")
        do { try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true); let copy = folder.appendingPathComponent(source.lastPathComponent); try FileManager.default.copyItem(at: source, to: copy); return copy }
        catch { return nil }
    }
    private func presenter() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
        guard var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return nil }
        while let presented = top.presentedViewController { top = presented }; return top
    }
}
