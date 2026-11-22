import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import kotlinx.coroutines.await
import kotlin.js.JsBoolean
import kotlin.js.Promise

/**
 * Punto d'ingresso della PWA: l'equivalente di MainActivity (Android) e MainViewController (iOS).
 * La sessione la ripristina MainAppShell da sé, dal token salvato nel localStorage.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startupPhase("WebAssembly avviato. Carico le impostazioni.")
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
        startupPhase("Carico i dati offline.")
        OfflineStore.init()
        startupPhase("Dati offline pronti. Avvio l’interfaccia.")
        viewportReady().await()
        // isA11YEnabled: Compose affianca al canvas una copia DOM dell'albero di accessibilita'
        // (un elemento per ogni nodo) e la risincronizza a ogni cambio di layout, cioe' a ogni
        // fotogramma di un'animazione o di uno scorrimento, sullo stesso thread che disegna. Su
        // schermate lunghe e' il costo piu' alto dopo il disegno, quindi su Android e computer e'
        // spenta. Su iOS invece deve restare accesa: ios-keyboard.js riconosce da li' il campo di
        // testo toccato e gli apre la tastiera dentro il gesto (senza, si apre e si richiude
        // subito). ?a11y=1 la riaccende dove e' spenta, ?a11y=0 la spegne anche su iOS: vedi
        // perf-debug.js.
        // Compose svuota il suo contenitore quando crea il canvas. Lo splash resta fuori.
        ComposeViewport(viewportContainerId = "aila-viewport", configure = { isA11YEnabled = a11yRequested() }) {
            val firstFrame = remember { object { var drawn = false } }
            AilaTheme {
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        if (!firstFrame.drawn) {
                            firstFrame.drawn = true
                            startupPhase("Primo fotogramma disegnato.")
                            hideSplashAfterFrame()
                        }
                    }
                ) {
                    MainAppShell()
                }
            }
        }
    }
}

private fun viewportReady(): Promise<JsBoolean> =
    js("new Promise(resolve => { const start = () => resolve(true); if (window.__ailaStartViewport) window.__ailaStartViewport(start); else start(); })")

private fun a11yRequested(): Boolean = js("window.__ailaA11y === true")

private fun startupPhase(message: String) {
    js("if (window.__ailaStartupTrace) window.__ailaStartupTrace(message); var stage = document.getElementById('diagnosi-avvio'); if (stage) { stage.setAttribute('data-kotlin', ''); stage.textContent = message; }")
}

private fun hideSplashAfterFrame() {
    js("requestAnimationFrame(function () { document.getElementById('aila-splash')?.remove(); if (window.__ailaStartupReady) window.__ailaStartupReady(); });")
}
