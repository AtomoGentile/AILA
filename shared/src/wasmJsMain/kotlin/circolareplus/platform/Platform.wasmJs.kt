package circolareplus.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Il browser non dice "sono un iPhone" in modo diretto: si legge lo user agent. Sugli iPad piu'
// recenti Safari si presenta come un Mac, ma un Mac vero non ha lo schermo tattile: si distingue
// da li'. Per la PWA l'obiettivo e' mostrare lo stile del telefono su cui gira (Liquid Glass su
// iPhone/iPad, Material su Android e computer), come fa l'app nativa.
private fun userAgent(): String = js("navigator.userAgent")

private fun touchPoints(): Int = js("navigator.maxTouchPoints || 0")

actual fun isIos(): Boolean {
    val ua = userAgent()
    return ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod") ||
        (ua.contains("Macintosh") && touchPoints() > 1)
}

actual fun isAndroid(): Boolean = userAgent().contains("Android")

private fun isStandalone(): Boolean =
    js("(window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) || navigator.standalone === true")

// Una PWA aggiunta alla Home su iPhone arriva fino al bordo dello schermo, curvo come nell'app
// nativa; nel browser normale (o su computer) c'e' la cornice del browser e gli angoli non contano.
@Composable
actual fun displayCornerRadius(): Dp = remember { if (isIos() && isStandalone() && touchPoints() > 1) 47.dp else 0.dp }

actual fun appVersionName(): String = "Web"

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches")

@Composable
actual fun isReduceMotionEnabled(): Boolean = remember { prefersReducedMotion() }
