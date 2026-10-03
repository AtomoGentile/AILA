package circolareplus.platform

import platform.Foundation.valueForKey

actual fun isIos(): Boolean = true

actual fun isAndroid(): Boolean = false

// iOS non espone il raggio in un'API pubblica: "_displayCornerRadius" di UIScreen e' la chiave
// usata comunemente (esiste da iOS 13, vale 0 sugli schermi squadrati). In punti, cioe' in dp.
@androidx.compose.runtime.Composable
actual fun displayCornerRadius(): androidx.compose.ui.unit.Dp = androidx.compose.runtime.remember {
    val value = platform.UIKit.UIScreen.mainScreen.valueForKey("_displayCornerRadius") as? platform.Foundation.NSNumber
    androidx.compose.ui.unit.Dp((value?.doubleValue ?: 0.0).toFloat())
}

actual fun appVersionName(): String =
    (platform.Foundation.NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "?"

// "Riduci movimento" di iOS. Si legge all'apertura e poi si resta in ascolto della notifica di
// sistema, cosi' se lo si cambia dal Centro di controllo (o tornando dalle Impostazioni) l'app si
// adegua subito, senza riavvio. La notifica arriva sulla coda principale: si puo' scrivere lo
// stato di Compose direttamente.
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@androidx.compose.runtime.Composable
actual fun isReduceMotionEnabled(): Boolean {
    val reduce = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(platform.UIKit.UIAccessibilityIsReduceMotionEnabled())
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        // Argomenti posizionali (nome, oggetto, coda, blocco): e' la forma sicura per un metodo
        // Objective-C importato in Kotlin/Native.
        val token = center.addObserverForName(
            platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            platform.Foundation.NSOperationQueue.mainQueue
        ) { _ ->
            reduce.value = platform.UIKit.UIAccessibilityIsReduceMotionEnabled()
        }
        // Puo' essere cambiato fra la lettura iniziale e la registrazione: si rilegge una volta.
        reduce.value = platform.UIKit.UIAccessibilityIsReduceMotionEnabled()
        onDispose { center.removeObserver(token) }
    }
    return reduce.value
}
