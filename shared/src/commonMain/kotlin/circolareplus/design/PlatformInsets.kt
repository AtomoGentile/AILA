package circolareplus.design

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import circolareplus.platform.isIos

/**
 * Margini di sicurezza (notch, Dynamic Island, barra home, tastiera) da applicare solo su iOS.
 *
 * Su iOS l'app disegna a tutto schermo (iosApp/iOSApp.swift usa `.ignoresSafeArea`), quindi
 * fuori dallo Scaffold nessuno teneva il contenuto lontano dalle aree di sistema, e la tastiera
 * copriva i campi di testo. Su Android la finestra NON è edge-to-edge (targetSdk 34, nessun
 * `enableEdgeToEdge`): è già il sistema a tenere il contenuto sotto la status bar e a
 * ridimensionare la finestra per la tastiera, e applicare qui lo stesso margine rischierebbe di
 * contarlo due volte. Per questo il ramo Android resta un no-op esplicito.
 */
fun Modifier.iosSafeDrawingPadding(): Modifier =
    if (isIos()) safeDrawingPadding() else this

/** Solo la tastiera (senza status bar/home indicator), per schermate già dentro uno Scaffold. */
fun Modifier.iosImePadding(): Modifier =
    if (isIos()) imePadding() else this
