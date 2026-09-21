package circolareplus.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

// Con Compose Multiplatform 1.11 lo swipe dal bordo sinistro su iOS arriva al BackHandler comune
// (c'e' un riconoscitore di gesti del bordo nel ComposeUIViewController, vedi
// UIKitNavigationEventInput nei sorgenti di compose-ui): prima qui non c'era niente e su iOS
// si tornava indietro solo con la freccia disegnata. Il commento precedente ("nessun
// equivalente") era vero per le versioni vecchie. L'API e' marcata deprecata a favore dei
// NavigationEventHandler, ma e' quella che Compose stesso usa per Android/Desktop: si usa con
// l'opt-in finche' non conviene migrare tutto il progetto a navigationevent.
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
