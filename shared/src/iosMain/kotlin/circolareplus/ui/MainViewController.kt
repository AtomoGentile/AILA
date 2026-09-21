package circolareplus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import circolareplus.data.AppContainer
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.ui.screens.MainAppShell
import kotlinx.coroutines.delay
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Punto d'ingresso iOS. Passava a MainAppShell due parametri (`currentUserId`, `isRepresentative`)
 * che nella firma attuale non esistono più — residuo della versione con l'utente fittizio: questo
 * file non compilava da quando sono stati aggiunti i target iOS alla build. Ora chiama la shell
 * senza argomenti, come fa MainActivity: la sessione la ripristina da sé.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    // Come su Android: tema riletto prima della prima composizione, per non far lampeggiare
    // il bianco a chi usa il tema scuro.
    AppTheme.isDarkMode = AppContainer.settings.isDarkMode
    SyncWindowInterfaceStyle()
    AilaTheme {
        MainAppShell()
    }
}

/**
 * Fa seguire alla finestra iOS il tema scelto DENTRO l'app (toggle nelle Impostazioni), non quello
 * di sistema: senza questo la status bar restava con le icone scure su sfondo scuro (o viceversa)
 * quando il tema dell'app e quello del telefono non coincidono, e alert/share sheet nativi
 * comparivano nel tema sbagliato.
 *
 * E' un composable a parte, e non due righe dentro la lambda di [MainViewController], perche'
 * quella lambda scrive `AppTheme.isDarkMode` a ogni ricomposizione: leggerlo li' la farebbe
 * ricomporre a ogni cambio di tema e rileggere il valore dalle impostazioni, annullando il toggle.
 * [AppTheme.isDarkMode] e' stato di Compose, quindi l'effetto riparte da solo a ogni cambio.
 */
@Composable
private fun SyncWindowInterfaceStyle() {
    val isDark = AppTheme.isDarkMode
    LaunchedEffect(isDark) {
        applyWindowInterfaceStyle(isDark)
        // Al primo avvio la finestra creata da SwiftUI puo' non esistere ancora quando parte
        // questo effetto: un secondo passaggio poco dopo copre quel caso senza ascoltare eventi
        // di scena. Su un cambio tema a runtime e' solo un'assegnazione ripetuta, innocua.
        delay(500)
        applyWindowInterfaceStyle(isDark)
    }
}

/**
 * Imposta `overrideUserInterfaceStyle` su tutte le finestre delle scene collegate: sulla finestra
 * (e non sul solo view controller di Compose) perche' la status bar e i controller nativi
 * presentati sopra ereditano lo stile dalla finestra, non dal figlio ospitato in SwiftUI.
 */
private fun applyWindowInterfaceStyle(isDark: Boolean) {
    val style = if (isDark) {
        UIUserInterfaceStyle.UIUserInterfaceStyleDark
    } else {
        UIUserInterfaceStyle.UIUserInterfaceStyleLight
    }
    UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .forEach { scene ->
            scene.windows.filterIsInstance<UIWindow>().forEach { window ->
                window.overrideUserInterfaceStyle = style
            }
        }
}
