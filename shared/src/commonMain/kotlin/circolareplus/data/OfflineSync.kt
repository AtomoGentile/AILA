package circolareplus.data

import circolareplus.data.remote.ConnectivityState
import circolareplus.platform.currentTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString

/**
 * "Scarica tutto per l'uso offline": chiama una volta ogni schermata dell'app con gli stessi
 * percorsi che usano le schermate, cosi' la copia offline di ApiClient si riempie anche per
 * quelle mai aperte. Parte da sola (all'avvio con rete, al ritorno della rete, al massimo una
 * volta ogni [AUTO_INTERVAL_MILLIS]) oppure dal pulsante nelle Impostazioni.
 *
 * Ogni passo e' indipendente: un endpoint riservato al Rappresentante che per uno studente
 * risponde 403 non ferma gli altri.
 */
object OfflineSync {
    // Ogni giro sono decine di richieste (dettagli dei sondaggi, commenti...): con molti telefoni
    // e giri frequenti si esauriva la quota giornaliera del Worker. I dati che contano davvero
    // (circolari, analisi, calendario) si aggiornano comunque da soli quando si aprono.
    private const val AUTO_INTERVAL_MILLIS = 2L * 60L * 60L * 1000L
    /** Ad app chiusa basta un giro ogni sei ore (il sistema sveglia l'app ogni 15+ minuti). */
    private const val BACKGROUND_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L
    private const val PDF_COUNT = 30
    /** PDF tenuti sul telefono: quelli scaricati in automatico piu' un margine per quelli aperti a mano. */
    private const val PDF_KEEP = 60
    private const val POLL_DETAILS = 30
    private const val PROPOSAL_COMMENTS = 30

    private val mutex = Mutex()

    /** Download in corso (per il pulsante e la sua barra). */
    val isRunning: Boolean get() = mutex.isLocked

    /** Automatico: solo con rete e se l'ultimo giro completo e' abbastanza vecchio. */
    suspend fun runIfDue(background: Boolean = false) {
        if (!background && ConnectivityState.isOffline) return
        // Senza sessione (logout) non c'e' niente da scaricare.
        if (AppContainer.settings.authToken.isBlank()) return
        val last = AppContainer.settings.lastFullOfflineSyncMillis
        val interval = if (background) BACKGROUND_INTERVAL_MILLIS else AUTO_INTERVAL_MILLIS
        if (currentTimeMillis() - last < interval) return
        run()
    }

    /**
     * Scarica tutto. [onProgress] riceve un valore 0..1. Restituisce false se non c'era rete
     * (nulla da fare) o se un altro download era gia' in corso.
     */
    suspend fun run(onProgress: (Float) -> Unit = {}): Boolean {
        if (!mutex.tryLock()) return false
        try {
            val c = AppContainer
            // Le rotte riservate al Rappresentante a uno studente rispondono 403: chiederle a
            // ogni giro erano solo richieste sprecate.
            val isRepresentative = isRepresentative()
            val steps = mutableListOf<suspend () -> Unit>(
                { c.usersRepository.me() },
                { c.calendarRepository.listEvents() },
                { c.proposalsRepository.listUnlockRequests() },
                { c.usersRepository.listStudents() },
                { c.seatMapRepository.getCurrentSeatMap() },
                { c.seatMapRepository.getHistoryForOptimizer() },
                { c.preferencesRepository.getConfig() },
                { c.preferencesRepository.progress() },
                { c.preferencesRepository.myVotes() },
                { c.rankingPollsRepository.listPolls() },
            )
            if (isRepresentative) {
                steps += { c.ratingsRepository.listRatings() }
                steps += { c.ratingsRepository.listDisciplinePairs() }
            }
            // Elenchi da cui dipendono i dettagli: prima questi, poi i singoli elementi.
            val circulars = attempt { c.circularsRepository.listCirculars() } ?: emptyList()
            val polls = attempt { c.pollsRepository.listPolls() } ?: emptyList()
            if (ConnectivityState.isOffline) return false
            val proposals = attempt { c.proposalsRepository.listProposals() } ?: emptyList()

            polls.take(POLL_DETAILS).forEach { poll ->
                steps += { c.pollsRepository.getPollWithProgress(poll.id) }
                if (isRepresentative) steps += { c.pollsRepository.getAssignments(poll.id) }
            }
            proposals.take(PROPOSAL_COMMENTS).forEach { proposal ->
                steps += { c.proposalsRepository.listComments(proposal.id) }
            }
            circulars.sortedByDescending { it.number }.take(PDF_COUNT).forEach { circular ->
                val keys = listOf(circular.r2PdfKey) + circular.attachments.mapNotNull { it.pdfKey }
                keys.filter { it.isNotBlank() }.forEach { key ->
                    steps += {
                        if (!c.circularsRepository.isPdfAvailableOffline(key)) {
                            c.circularsRepository.downloadPdfBytes(key)
                        }
                    }
                }
            }

            steps.forEachIndexed { index, step ->
                // Rete caduta a meta': inutile continuare, si riprova al prossimo giro.
                if (ConnectivityState.isOffline) return false
                attempt { step() }
                onProgress((index + 1).toFloat() / steps.size)
            }
            if (circulars.isNotEmpty()) prunePdfs(circulars)
            AppContainer.settings.lastFullOfflineSyncMillis = currentTimeMillis()
            return true
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Tiene sul telefono solo i PDF delle [PDF_KEEP] circolari piu' recenti (e dei loro allegati):
     * prima ogni PDF mai aperto restava per sempre, e in un anno di circolari erano centinaia di MB.
     */
    private fun prunePdfs(circulars: List<circolareplus.domain.model.Circular>) {
        val api = AppContainer.api
        val keep = circulars.sortedByDescending { it.number }.take(PDF_KEEP).flatMap { circular ->
            (listOf(circular.r2PdfKey) + circular.attachments.mapNotNull { it.pdfKey })
                .filter { it.isNotBlank() }
                .map { api.offlineName("/api/circulars/pdf/$it", "b") }
        }.toSet()
        try {
            circolareplus.platform.OfflineStore.list()
                .filter { it.startsWith("b_") && it !in keep }
                .forEach { circolareplus.platform.OfflineStore.delete(it) }
        } catch (e: Exception) {
            // Pulizia facoltativa: meglio un file in piu' che un errore.
        }
    }

    private fun isRepresentative(): Boolean = try {
        val raw = AppContainer.settings.cachedUserJson
        raw.isNotBlank() &&
            circolareplus.data.remote.apiJson.decodeFromString<circolareplus.data.remote.dto.UserDto>(raw).role == "REPRESENTATIVE"
    } catch (e: Exception) {
        false
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
