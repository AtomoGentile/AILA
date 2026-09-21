package circolareplus.platform

import androidx.compose.runtime.Composable

/**
 * Intercetta il pulsante/gesto "indietro" di sistema. Prima non c'era nulla: su Android, lo
 * swipe/tasto indietro chiudeva sempre l'app intera, anche quando si era dentro un flusso a
 * schermo intero (Mappa Posti, Sondaggi, Scheda Classe, Notifiche, dettaglio Circolare) invece di
 * tornare al menu/schermata precedente come ci si aspetterebbe.
 *
 * Android usa il `BackHandler` di Jetpack (androidx.activity.compose); iOS usa quello comune di
 * Compose Multiplatform, che riceve lo swipe dal bordo sinistro. Restano due `actual` separati
 * perché il primo dipende da androidx.activity, che non esiste fuori da Android.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
