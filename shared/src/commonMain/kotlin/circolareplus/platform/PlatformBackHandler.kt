package circolareplus.platform

import androidx.compose.runtime.Composable

/**
 * Intercetta il pulsante/gesto "indietro" di sistema. Prima non c'era nulla: su Android, lo
 * swipe/tasto indietro chiudeva sempre l'app intera, anche quando si era dentro un flusso a
 * schermo intero (Mappa Posti, Sondaggi, Scheda Classe, Notifiche, dettaglio Circolare) invece di
 * tornare al menu/schermata precedente come ci si aspetterebbe.
 *
 * `BackHandler` di Jetpack (androidx.activity.compose) è un'API solo Android: non esiste un
 * equivalente diretto in Compose Multiplatform per iOS, quindi serve un `expect`/`actual` — su
 * iOS per ora non fa nulla (il gesto di swipe-indietro di sistema lì non chiude comunque l'app
 * allo stesso modo, essendo l'app un'unica vista Compose senza uno stack di navigazione UIKit).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
