package circolareplus.ai.assistant

import circolareplus.platform.OfflineStore
import circolareplus.platform.readText
import circolareplus.platform.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Il testo delle circolari sul telefono, con l'indice di ricerca in memoria.
 *
 * Ogni testo sta in un file offline `t_<numero>`, scritto dalla sincronizzazione (vedi
 * OfflineSync). L'indice si costruisce alla prima ricerca leggendo quei file, e da li' in poi
 * resta aggiornato a ogni salvataggio. Sincronizzazione e assistente girano su coroutine diverse,
 * quindi ogni accesso all'indice passa dal mutex.
 */
internal object CircularTextStore {

    private const val PREFIX = "t_"

    private val mutex = Mutex()
    private var index: CircularTextIndex? = null

    private fun name(number: Int) = "$PREFIX$number"

    /** Se il testo della circolare e' gia' sul telefono (anche se vuoto, per una scansione). */
    suspend fun has(number: Int): Boolean = withContext(Dispatchers.Default) {
        OfflineStore.exists(name(number))
    }

    /** Salva il testo di una circolare, sul disco e nell'indice. */
    suspend fun save(number: Int, text: String) {
        withContext(Dispatchers.Default) { OfflineStore.writeText(name(number), text) }
        mutex.withLock { index?.put(number, text) }
    }

    /** Toglie dal telefono e dall'indice i testi delle circolari che non sono in [numbers]. */
    suspend fun keepOnly(numbers: Set<Int>) {
        withContext(Dispatchers.Default) {
            try {
                OfflineStore.list()
                    .filter { it.startsWith(PREFIX) && it.removePrefix(PREFIX).toIntOrNull()?.let { n -> n !in numbers } == true }
                    .forEach { OfflineStore.delete(it) }
            } catch (e: Exception) {
                // Pulizia facoltativa: meglio un file in piu' che un errore.
            }
        }
        mutex.withLock {
            val current = index ?: return@withLock
            current.numbers().filter { it !in numbers }.forEach { current.remove(it) }
        }
    }

    /** Le circolari che contengono almeno una parola di [terms], la piu' pertinente per prima. */
    suspend fun search(terms: List<String>): List<CircularTextIndex.Hit> = mutex.withLock {
        ensureLoaded().search(terms)
    }

    /** Quante circolari hanno il testo sul telefono. */
    suspend fun count(): Int = mutex.withLock { ensureLoaded().size }

    /** Il testo integrale di una circolare, se e' sul telefono. */
    suspend fun text(number: Int): String? = mutex.withLock { ensureLoaded().text(number) }

    /** I passaggi di una circolare che contengono le [terms], entro [maxChars]. */
    suspend fun passages(number: Int, terms: List<String>, maxChars: Int): String = mutex.withLock {
        ensureLoaded().passages(number, terms, maxChars)
    }

    /** Da chiamare con il mutex tenuto: costruisce l'indice la prima volta. */
    private suspend fun ensureLoaded(): CircularTextIndex {
        index?.let { return it }
        val loaded = CircularTextIndex()
        val names = withContext(Dispatchers.Default) { OfflineStore.list().filter { it.startsWith(PREFIX) } }
        for (fileName in names) {
            val number = fileName.removePrefix(PREFIX).toIntOrNull() ?: continue
            val text = withContext(Dispatchers.Default) { OfflineStore.readText(fileName) } ?: continue
            if (text.isNotBlank()) loaded.put(number, text)
        }
        index = loaded
        return loaded
    }
}
