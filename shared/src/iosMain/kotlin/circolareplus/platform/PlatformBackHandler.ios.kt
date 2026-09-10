package circolareplus.platform

import androidx.compose.runtime.Composable

// Nessun equivalente diretto su iOS: l'app è un'unica vista Compose, non c'è uno stack di
// navigazione UIKit da cui intercettare uno swipe-indietro che chiuderebbe l'app. No-op per ora.
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
