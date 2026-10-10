package circolareplus.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

// Il tasto/gesto "indietro" del browser (e della PWA installata su Android) svuota la cronologia,
// non chiama l'app. Per intercettarlo si tiene nella cronologia una voce sentinella finche' c'e'
// almeno un gestore attivo: quando l'utente torna indietro, il browser consuma la sentinella e
// noi chiamiamo il gestore in cima alla pila (l'ultimo registrato, come su Android).
private class BackEntry(val onBack: () -> Unit)

private object WebBack {
    private val stack = mutableListOf<BackEntry>()
    private var sentinelPresent = false
    private var ignorePops = 0
    private var installed = false

    fun push(entry: BackEntry) {
        install()
        stack.add(entry)
        ensureSentinel()
    }

    fun remove(entry: BackEntry) {
        stack.remove(entry)
        if (stack.isEmpty()) scheduleCleanup()
    }

    private fun install() {
        if (installed) return
        installed = true
        onPopState {
            if (ignorePops > 0) {
                ignorePops--
                return@onPopState
            }
            // La sentinella e' stata consumata dal browser.
            sentinelPresent = false
            val top = stack.lastOrNull() ?: return@onPopState
            top.onBack()
            // Se dopo il gestore ce n'e' ancora uno attivo, serve una nuova sentinella per il
            // prossimo "indietro".
            if (stack.isNotEmpty()) ensureSentinel()
        }
    }

    private fun ensureSentinel() {
        if (sentinelPresent) return
        sentinelPresent = true
        pushSentinel()
    }

    // Quando l'ultimo gestore sparisce si toglie la sentinella, ma con un rinvio: durante un
    // cambio di schermata il vecchio gestore sparisce un attimo prima che arrivi il nuovo, e
    // togliere/rimettere la voce a ogni passaggio riempirebbe la cronologia di rumore.
    private fun scheduleCleanup() {
        later {
            if (stack.isEmpty() && sentinelPresent) {
                sentinelPresent = false
                ignorePops++
                historyBack()
            }
        }
    }
}

private fun onPopState(callback: () -> Unit) {
    js("window.addEventListener('popstate', function () { callback(); })")
}

private fun pushSentinel() {
    js("history.pushState({ aila: 'back' }, '')")
}

private fun historyBack() {
    js("history.back()")
}

private fun later(callback: () -> Unit) {
    js("setTimeout(function () { callback(); }, 0)")
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val entry = BackEntry { current.value() }
        WebBack.push(entry)
        onDispose { WebBack.remove(entry) }
    }
}
