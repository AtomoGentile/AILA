package circolareplus.ui

import androidx.compose.ui.window.ComposeUIViewController
import circolareplus.data.AppContainer
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.ui.screens.MainAppShell
import platform.UIKit.UIViewController

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
    AilaTheme {
        MainAppShell()
    }
}
