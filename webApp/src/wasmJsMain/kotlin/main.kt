import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import circolareplus.data.AppContainer
import circolareplus.design.AilaAccent
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.design.UiStyle
import circolareplus.platform.OfflineStore
import circolareplus.ui.screens.MainAppShell
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Punto d'ingresso della PWA: l'equivalente di MainActivity (Android) e MainViewController (iOS).
 * La sessione la ripristina MainAppShell da sé, dal token salvato nel localStorage.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Tema riletto prima della prima composizione, come sulle altre piattaforme: senza, chi usa il
    // tema scuro vedrebbe un lampo chiaro a ogni avvio.
    AppTheme.isDarkMode = AppContainer.settings.isDarkMode
    AppTheme.uiStyle = UiStyle.fromKey(AppContainer.settings.uiStyleKey)
    AppTheme.accent = AilaAccent.fromKey(AppContainer.settings.accentKey)

    // Service worker, notifiche push, rientro in primo piano (come onStart/onResume su Android).
    circolareplus.web.installWebLifecycle()

    // La copia offline sta in IndexedDB (asincrona) ma l'app la legge in modo sincrono: va caricata
    // in memoria prima che parta la UI. Nel frattempo si vede la schermata di avvio di index.html.
    MainScope().launch {
        OfflineStore.init()
        ComposeViewport {
            // Il primo fotogramma di Compose e' pronto: via la schermata di avvio HTML.
            LaunchedEffect(Unit) { hideSplash() }
            AilaTheme {
                MainAppShell()
            }
        }
    }
}

private fun hideSplash() {
    js("var s = document.getElementById('aila-splash'); if (s) { s.classList.add('done'); setTimeout(function () { s.remove(); }, 400); }")
}
