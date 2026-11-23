import UIKit
import WebKit

// A disposable simulator app. No AILA account or device data is needed.
class App: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    var web: WKWebView!
    var attempts = 0
    let refuseGradient = ProcessInfo.processInfo.environment["AILA_TEST_CASE"] == "refuse-gradient"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let root = UIViewController()
        web = WKWebView(frame: .zero)
        root.view = web
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = root
        window?.makeKeyAndVisible()
        let url = ProcessInfo.processInfo.environment["AILA_URL"]!
        web.configuration.userContentController.addUserScript(WKUserScript(source: """
            window.addEventListener('error', e => { if (e.error) window.testStack = String(e.error.stack || e.message); });
            window.addEventListener('unhandledrejection', e => { window.testStack = String(e.reason?.stack || e.reason); });
            """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        if refuseGradient {
            // Install after startup.js so the real recovery adapter receives the failing Skia binding.
            web.configuration.userContentController.addUserScript(WKUserScript(source: """
                document.addEventListener('load', event => {
                 if (!(event.target instanceof HTMLScriptElement) || !event.target.src.includes('/startup.js')) return;
                 const original = WebAssembly.instantiateStreaming;
                 WebAssembly.instantiateStreaming = function(source, imports, options) {
                   const key = './skiko.mjs';
                   if (!imports[key]) return original.call(this, source, imports, options);
                   const bindings = {...imports[key]};
                   Object.defineProperty(bindings, 'org_jetbrains_skia_Shader__1nMakeRadialGradient', {
                     enumerable: true, value() { window.testSkiaRefusedGradient = true; return 0; }
                   });
                   return original.call(this, source, {...imports, [key]: bindings}, options);
                 };
                }, true);
                """, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        web.load(URLRequest(url: URL(string: url)!))
        poll()
        return true
    }

    func poll() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            self.attempts += 1
            self.web.evaluateJavaScript("""
                (() => {
                    const shadow = document.querySelector('compose-component > div')?.shadowRoot;
                    const canvas = shadow?.querySelector('canvas');
                    const recovered = window.testSkiaRefusedGradient === true &&
                        new URL(location.href).searchParams.get('grafica') === 'ridotta';
                    if (canvas?.width > 0 && !document.getElementById('aila-splash') &&
                        (!\(self.refuseGradient) || recovered)) return 'PASS';
                    return [document.getElementById('diagnosi-avvio')?.textContent,
                        document.getElementById('aila-startup-error')?.textContent, window.testStack].filter(Boolean).join('\\n');
                })()
                """) { value, error in
                let result = value as? String ?? error?.localizedDescription ?? ""
                if result == "PASS" || self.attempts >= 70 {
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
