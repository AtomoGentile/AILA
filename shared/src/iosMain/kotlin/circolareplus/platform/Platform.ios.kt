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
