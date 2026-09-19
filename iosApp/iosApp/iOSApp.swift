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
        // Inietta i bridge nativi prima di creare la UI.
        // Gli holder sono esportati da Kotlin/Native come singleton (pattern standard: .shared).
        AppleIntelligenceBridgeHolder.shared.bridge = AppleIntelligenceEngine()
        PushTokenBridgeHolder.shared.bridge = FirebasePushTokenBridge()
        SeatMapPdfShareBridgeHolder.shared.bridge = SeatMapPdfShareBridgeImpl()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
