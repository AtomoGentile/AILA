package circolareplus.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * L'app e' in primo piano? Lo imposta la piattaforma (MainActivity.onStart/onStop su Android).
 *
 * Serve ai cicli periodici di MainAppShell (circolari ogni minuto, analisi, novita', copia
 * offline): su Android la composizione resta viva anche con l'app in background, e prima quei
 * cicli continuavano a interrogare il server e a consumare batteria e dati in tasca. Il lavoro in
 * background vero lo fanno WorkManager e BGTaskScheduler, non questi cicli.
 */
object AppForeground {
    var isForeground by mutableStateOf(true)
}

/** Sospende finche' l'app non e' in primo piano (ritorna subito se lo e' gia'). */
suspend fun awaitForeground() {
    if (AppForeground.isForeground) return
    snapshotFlow { AppForeground.isForeground }.first { it }
}
