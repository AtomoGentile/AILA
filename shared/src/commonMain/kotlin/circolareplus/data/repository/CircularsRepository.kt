package circolareplus.data.repository

import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.dto.CircularAnalysesDto
import circolareplus.data.remote.dto.CircularAnalysisDto
import circolareplus.data.remote.dto.CircularAttachmentDto
import circolareplus.data.remote.dto.CircularDto
import circolareplus.data.remote.dto.CircularsListResponseDto
import circolareplus.data.remote.dto.ExtractedDeadlineDto
import circolareplus.data.remote.dto.SaveCircularAnalysisRequestDto
import circolareplus.data.remote.dto.SaveCircularAnalysisResponseDto
import circolareplus.data.remote.dto.SuccessDto
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularAttachment
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.domain.model.ExtractedDeadline
import circolareplus.util.decodeHtmlEntities

/**
 * Circolari: la rilevazione/deduplicazione/cache PDF avviene lato server (Cron Trigger su
 * Spaggiari + D1 + R2, vedi services/spaggiari.ts). Il client si limita a leggere l'elenco
 * già pronto e a scaricare il PDF dalla cache R2 tramite /api/circulars/pdf/:key.
 */
class CircularsRepository(private val api: ApiClient) {

    suspend fun listCirculars(limit: Int = 50, offset: Int = 0): List<Circular> {
        val response: CircularsListResponseDto = api.get("/api/circulars?limit=$limit&offset=$offset")
        return response.circulars.map { it.toDomain(api.baseUrl) }
    }

    suspend fun getCircular(number: Int): Circular {
        val dto: CircularDto = api.get("/api/circulars/$number")
        return dto.toDomain(api.baseUrl)
    }

    /** URL assoluto da cui scaricare il PDF originale (proxato da R2 dal Worker). */
    fun pdfDownloadUrl(baseUrl: String, pdfKey: String): String = "$baseUrl/api/circulars/pdf/$pdfKey"

    /** Byte grezzi del PDF, usati per l'estrazione testo lato client (classificazione AI). */
    suspend fun downloadPdfBytes(pdfKey: String): ByteArray = api.getBytes("/api/circulars/pdf/$pdfKey")

    /**
     * Analisi AI già in cache sul server, o `null` se nessuno l'ha ancora prodotta per questa
     * circolare. Chiave di cache: solo il numero circolare (vedi commento sulla tabella
     * `circular_ai_analysis` in schema.sql) — condivisa da tutti gli utenti, non rifatta da zero
     * a ogni apertura.
     */
    suspend fun getCachedAnalysis(circularNumber: Int): CircularAiClassification? {
        return try {
            val dto: CircularAnalysisDto = api.get("/api/circulars/$circularNumber/analysis")
            dto.toDomain()
        } catch (e: ApiException) {
            if (e.statusCode == 404) null else throw e
        }
    }

    /**
     * Salva sul server l'analisi appena prodotta sul telefono, così diventa visibile a tutti
     * senza che ciascuno la rifaccia. Non manda mai il testo del PDF, solo l'esito (badge,
     * riassunto, scadenze) — vedi la nota di privacy sulla tabella `circulars`.
     *
     * Il server tiene l'analisi di livello più alto (vedi [circolareplus.ai.tier]): se ne ha già
     * una migliore rifiuta questa e la restituisce, e qui torna quella. `null` se è stata salvata.
     */
    suspend fun saveAnalysis(classification: CircularAiClassification): CircularAiClassification? {
        val response = api.put<SaveCircularAnalysisRequestDto, SaveCircularAnalysisResponseDto>(
            "/api/circulars/${classification.circularNumber}/analysis",
            SaveCircularAnalysisRequestDto(
                badge = classification.badge.name,
                summary = classification.personalSummary,
                deadlines = classification.detectedDeadlines.map {
                    ExtractedDeadlineDto(it.title, it.dueDate, it.time, it.category)
                },
                isFallback = classification.isFallback,
                modelLabel = classification.modelLabel
            )
        )
        return if (response.stored) null else response.current?.toDomain()
    }

    /**
     * Le analisi cambiate sul server dopo [since] (`null` = tutte), con il nuovo valore da
     * passare alla chiamata successiva. Una sola richiesta al posto di una per circolare, e
     * chi ha l'app aperta vede i riassunti fatti da altri senza dover riaprire l'app.
     */
    suspend fun getAnalysesSince(since: String?): Pair<List<CircularAiClassification>, String?> {
        val all = mutableListOf<CircularAiClassification>()
        var cursor = since
        // Il server ne restituisce al massimo 200 per volta, dalla più vecchia.
        repeat(MAX_ANALYSIS_PAGES) {
            val query = cursor?.let { "?since=" + it.replace(" ", "%20") } ?: ""
            val page: CircularAnalysesDto = api.get("/api/circulars/analyses$query")
            all += page.analyses.map { it.toDomain() }
            val last = page.analyses.lastOrNull()?.updatedAt
            if (last != null) cursor = last
            if (page.analyses.size < ANALYSES_PAGE_SIZE) return all to cursor
        }
        return all to cursor
    }

    private companion object {
        const val ANALYSES_PAGE_SIZE = 200
        const val MAX_ANALYSIS_PAGES = 5
    }
}

private fun CircularAnalysisDto.toDomain(): CircularAiClassification {
    // In condizioni normali qui il badge e' sempre uno dei tre valori validi: chi lo ha salvato
    // (saveAnalysis) parte da una CircularAiClassification gia' validata, sia essa una vera
    // classificazione o un fallback euristico. Se questo scatta comunque, la riga e' corrotta o
    // di una versione precedente: meglio marcarla isFallback cosi' la UI la mostra come tale
    // invece di spacciarla per un giudizio vero.
    val parsedBadge = try {
        CircularRelevanceBadge.valueOf(badge)
    } catch (e: IllegalArgumentException) {
        null
    }
    return CircularAiClassification(
        circularNumber = circularNumber,
        badge = parsedBadge ?: CircularRelevanceBadge.POTENTIAL,
        personalSummary = summary,
        detectedDeadlines = deadlines.map { ExtractedDeadline(it.title, it.dueDate, it.time, it.category) },
        isFallback = isFallback || parsedBadge == null,
        modelLabel = modelLabel
    )
}

private fun CircularDto.toDomain(baseUrl: String): Circular = Circular(
    number = number,
    title = decodeHtmlEntities(title),
    publishDate = publishDate,
    r2PdfKey = pdfKey,
    downloadUrl = "$baseUrl/api/circulars/pdf/$pdfKey",
    attachments = attachments.map { it.toDomain(baseUrl) }
)

private fun CircularAttachmentDto.toDomain(baseUrl: String): CircularAttachment =
    if (pdfKey != null) {
        CircularAttachment(label = decodeHtmlEntities(label), downloadUrl = "$baseUrl/api/circulars/pdf/$pdfKey", isPdf = true, pdfKey = pdfKey)
    } else {
        CircularAttachment(label = decodeHtmlEntities(label), downloadUrl = url ?: "", isPdf = false)
    }
