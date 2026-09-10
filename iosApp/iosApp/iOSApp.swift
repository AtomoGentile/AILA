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

    init() {
        // Inietta il bridge Apple Intelligence prima di creare la UI.
        // AppleIntelligenceBridgeHolder è esportato da Kotlin/Native come holder singleton.
        // Il metodo di accesso dipende dalla configurazione Kotlin, però il pattern standard è:
        AppleIntelligenceBridgeHolder.shared.bridge = AppleIntelligenceEngine()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
