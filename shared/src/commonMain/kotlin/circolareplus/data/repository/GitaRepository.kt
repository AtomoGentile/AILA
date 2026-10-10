package circolareplus.data.repository

import circolareplus.ai.PdfTextExtractor
import circolareplus.data.remote.ApiClient
import circolareplus.data.remote.dto.GitaCorpusResponseDto
import circolareplus.data.remote.dto.GitaCreatedDto
import circolareplus.data.remote.dto.GitaFeedDto
import circolareplus.data.remote.dto.GitaItemDto
import circolareplus.data.remote.dto.GitaOkDto
import circolareplus.data.remote.dto.GitaReportCreatedDto
import circolareplus.data.remote.dto.GitaReportDto
import circolareplus.data.remote.dto.GitaVersionCreatedDto
import circolareplus.data.remote.dto.GitaVersionDto
import circolareplus.data.remote.dto.GitaVersionsDto
import circolareplus.domain.model.GitaCategory
import circolareplus.domain.model.GitaFeed
import circolareplus.domain.model.GitaItem
import circolareplus.domain.model.GitaReport
import circolareplus.domain.model.GitaSourceDoc
import circolareplus.domain.model.GitaVersion
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

/**
 * Gita: documenti e link della classe. La lettura va a tutta la classe; le scritture le accetta
 * il server solo dal Rappresentante. Il testo del PDF si estrae qui, sul telefono, prima
 * dell'invio: il server lo salva cosi' com'e' e l'assistente non deve rileggere ogni volta il file.
 */
class GitaRepository(
    private val api: ApiClient,
    private val pdfTextExtractor: PdfTextExtractor
) {

    suspend fun feed(): GitaFeed {
        val dto: GitaFeedDto = api.get("/api/gita")
        return GitaFeed(
            items = dto.items.map { it.toDomain() },
            reports = dto.reports.map { it.toDomain() },
            canEdit = dto.canEdit
        )
    }

    /** Storico completo di una voce, dalla versione più recente alla prima. */
    suspend fun versions(itemId: String): Pair<GitaItem, List<GitaVersion>> {
        val dto: GitaVersionsDto = api.get("/api/gita/items/$itemId/versions")
        return dto.item.toDomain() to dto.versions.map { it.toDomain() }
    }

    /** Bytes del PDF di una versione, per aprirlo nel visualizzatore del telefono. */
    suspend fun downloadPdf(versionId: String): ByteArray = api.getBytes("/api/gita/versions/$versionId/file")

    /** Versione corrente di ogni voce, con il testo estratto: il materiale che legge l'assistente. */
    suspend fun sourceDocuments(): List<GitaSourceDoc> {
        val dto: GitaCorpusResponseDto = api.get("/api/gita/corpus")
        return dto.documents.map { d ->
            GitaSourceDoc(
                itemId = d.itemId,
                title = d.title,
                category = GitaCategory.fromId(d.category),
                uploadedAt = d.uploadedAt,
                versionNo = d.versionNo,
                url = d.url,
                text = d.text
            )
        }
    }

    /** Nuovo documento: estrae il testo dal PDF e lo invia insieme al file. */
    suspend fun addDocument(title: String, category: GitaCategory, fileName: String, pdf: ByteArray, note: String?) {
        val text = pdfTextExtractor.extractText(pdf)
        val parts = documentParts(title, category, fileName, pdf, text, note)
        api.postMultipart<GitaCreatedDto>("/api/gita/items", parts)
    }

    /** Sostituisce un documento: la versione precedente resta nello storico. */
    suspend fun replaceDocument(itemId: String, fileName: String, pdf: ByteArray, note: String?) {
        val text = pdfTextExtractor.extractText(pdf)
        val parts = formData {
            appendFile(fileName, pdf)
            append("text", text)
            if (!note.isNullOrBlank()) append("note", note)
        }
        api.postMultipart<GitaVersionCreatedDto>("/api/gita/items/$itemId/versions", parts)
    }

    suspend fun addLink(title: String, url: String, note: String?) {
        api.post<Map<String, String?>, GitaCreatedDto>(
            "/api/gita/items",
            mapOf("title" to title, "url" to url, "note" to note?.takeIf { it.isNotBlank() })
        )
    }

    /** Nuovo indirizzo per un link: diventa una versione nuova, la vecchia resta nello storico. */
    suspend fun replaceLink(itemId: String, url: String, note: String?) {
        api.post<Map<String, String?>, GitaVersionCreatedDto>(
            "/api/gita/items/$itemId/versions",
            mapOf("url" to url, "note" to note?.takeIf { it.isNotBlank() })
        )
    }

    /** Titolo e categoria, senza nuova versione. */
    suspend fun updateItem(itemId: String, title: String, category: GitaCategory) {
        api.patch<Map<String, String>, GitaOkDto>(
            "/api/gita/items/$itemId",
            mapOf("title" to title, "category" to category.id)
        )
    }

    /** Ritiro logico: la voce non si vede più, lo storico resta. */
    suspend fun withdraw(itemId: String) {
        api.delete<GitaOkDto>("/api/gita/items/$itemId")
    }

    suspend fun report(itemId: String, reason: String, versionId: String? = null) {
        val body = buildMap<String, String> {
            put("reason", reason)
            if (versionId != null) put("versionId", versionId)
        }
        api.post<Map<String, String>, GitaReportCreatedDto>("/api/gita/items/$itemId/reports", body)
    }

    /** Il Rappresentante segna la segnalazione come risolta (o la riapre). */
    suspend fun setReportResolved(reportId: String, resolved: Boolean) {
        api.patch<Map<String, String>, GitaOkDto>(
            "/api/gita/reports/$reportId",
            mapOf("status" to if (resolved) "RESOLVED" else "OPEN")
        )
    }

    private fun documentParts(
        title: String,
        category: GitaCategory,
        fileName: String,
        pdf: ByteArray,
        text: String,
        note: String?
    ) = formData {
        append("title", title)
        append("category", category.id)
        appendFile(fileName, pdf)
        append("text", text)
        if (!note.isNullOrBlank()) append("note", note)
    }

    private fun io.ktor.client.request.forms.FormBuilder.appendFile(fileName: String, pdf: ByteArray) {
        append(
            "file",
            pdf,
            Headers.build {
                append(HttpHeaders.ContentType, "application/pdf")
                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
            }
        )
    }
}

private fun GitaItemDto.toDomain() = GitaItem(
    id = id,
    isLink = kind == "LINK",
    category = GitaCategory.fromId(category),
    title = title,
    createdAt = createdAt,
    current = current?.toDomain()
)

private fun GitaVersionDto.toDomain() = GitaVersion(
    id = id,
    versionNo = versionNo,
    uploadedAt = uploadedAt,
    hasFile = hasFile,
    textChars = textChars,
    url = url,
    note = note
)

private fun GitaReportDto.toDomain() = GitaReport(
    id = id,
    itemId = itemId,
    itemTitle = itemTitle,
    reason = reason,
    isResolved = status == "RESOLVED",
    createdAt = createdAt,
    resolvedAt = resolvedAt
)
