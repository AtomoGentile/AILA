import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Inietta i bridge nativi prima di creare la UI.
        // Gli holder sono esportati da Kotlin/Native come singleton (pattern standard: .shared).
        AppleIntelligenceBridgeHolder.shared.bridge = AppleIntelligenceEngine()
        // MLX (Tier 2, iPhone senza Apple Intelligence): iniettato comunque, ma MLXLocalEngine
        // dichiara isDownloaded()/generate() come non disponibili finché l'SDK MLX Swift reale
        // non è collegato — vedi il commento in cima a MLXLocalEngine.swift.
        MLXLocalBridgeHolder.shared.bridge = MLXLocalEngine()
        PushTokenBridgeHolder.shared.bridge = FirebasePushTokenBridge()
        SeatMapPdfShareBridgeHolder.shared.bridge = SeatMapPdfShareBridgeImpl()
        // Analisi sul telefono che continua con l'app in background (vedi BackgroundWorkBridge.swift).
        BackgroundWorkBridgeHolder.shared.bridge = BackgroundWorkBridgeImpl()
        IosBackgroundWork.shared.start()
        BackgroundRefreshBridgeHolder.shared.bridge = AilaBackground()

        // Refresh in background delle circolari: va registrato prima della fine del lancio. E'
        // l'unico punto che registra com.circolareplus.refresh (registrarlo due volte fa
        // terminare l'app).
        AilaBackground.register()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
        // Ogni volta che l'app va in background si (ri)programma il prossimo risveglio.
        .onChange(of: scenePhase) { _, phase in
            if phase == .background {
                AilaBackground.schedule()
            }
        }
    }
}
