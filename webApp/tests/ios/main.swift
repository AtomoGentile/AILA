import UIKit
import WebKit

// A disposable simulator app. No AILA account or device data is needed.
class App: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Startup", sessionRole: session.role)
        config.delegateClass = StartupScene.self
        return config
    }
}

class StartupScene: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    var web: WKWebView!
    var attempts = 0

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let root = UIViewController()
        web = WKWebView(frame: .zero)
        root.view = web
        window = UIWindow(windowScene: windowScene)
        window?.rootViewController = root
        window?.makeKeyAndVisible()
        let url = ProcessInfo.processInfo.environment["AILA_URL"]!
        web.configuration.userContentController.addUserScript(WKUserScript(source: """
            window.addEventListener('error', e => { if (e.error) window.testStack = String(e.error.stack || e.message); });
            window.addEventListener('unhandledrejection', e => { window.testStack = String(e.reason?.stack || e.reason); });
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        web.load(URLRequest(url: URL(string: url)!))
        poll()
    }

    func poll() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            self.attempts += 1
            self.web.evaluateJavaScript("""
                (() => {
                    function findCanvas() {
                        let c = document.querySelector('canvas');
                        if (c) return c;
                        for (const el of document.querySelectorAll('*')) {
                            if (el.shadowRoot) {
                                c = el.shadowRoot.querySelector('canvas');
                                if (c) return c;
                            }
                        }
                        return null;
                    }
                    const canvas = findCanvas();
                    if (canvas?.width > 0 && !document.getElementById('aila-splash') && !window.testStack) return 'PASS';
                    return [location.href, document.readyState, document.getElementById('diagnosi-avvio')?.textContent,
                        document.getElementById('aila-startup-error')?.textContent, window.testStack].filter(Boolean).join('\\n');
                })()
                """) { value, error in
                let result = value as? String ?? error?.localizedDescription ?? ""
                if result == "PASS" || self.attempts >= 110 {
                    let path = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("result")
                    try! (result.isEmpty ? "No first frame" : result).write(to: path, atomically: true, encoding: .utf8)
                } else {
                    self.poll()
                }
            }
        }
    }
}

UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(App.self))
