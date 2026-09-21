package circolareplus.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Canale con cui la parte di piattaforma dice all'app "ricarica i dati adesso".
 *
 * Serve a due momenti che il controllo periodico non copre bene: un push ricevuto ad app aperta
 * (la notifica di sistema compariva ma la lista restava quella di prima finche' non si usciva e
 * rientrava) e il ritorno in primo piano dopo un periodo in background.
 *
 * Una richiesta senza nessuno in ascolto si perde, ed e' giusto cosi': se l'app non e' composta,
 * i dati si caricano comunque al prossimo avvio. Le richieste ravvicinate si fondono in una.
 */
object DataRefreshEvents {
    private val flow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val requests: SharedFlow<Unit> = flow

    fun request() {
        flow.tryEmit(Unit)
    }
}
