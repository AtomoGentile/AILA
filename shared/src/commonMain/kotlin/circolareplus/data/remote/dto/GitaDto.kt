package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

/** Risposta di GET /api/gita: documenti e link correnti, più le segnalazioni visibili a chi legge. */
@Serializable
data class GitaFeedDto(
    val items: List<GitaItemDto> = emptyList(),
    val circulars: List<GitaCircularDto> = emptyList(),
    val reports: List<GitaReportDto> = emptyList(),
    val canEdit: Boolean = false
)

@Serializable
data class GitaItemDto(
    val id: String,
    val kind: String,
    val category: String = "ALTRO",
    val title: String,
    val createdBy: String = "",
    val createdAt: String = "",
    val withdrawn: Boolean = false,
    val current: GitaVersionDto? = null
)

/** Una versione di un documento o di un link. [uploadedAt] è la data mostrata nell'app. */
@Serializable
data class GitaVersionDto(
    val id: String,
    val versionNo: Int,
    val uploadedBy: String = "",
    val uploadedAt: String = "",
    val hasFile: Boolean = false,
    val fileMime: String? = null,
    val fileSize: Long? = null,
    val textChars: Int = 0,
    val url: String? = null,
    val note: String? = null
)

@Serializable
data class GitaReportDto(
    val id: String,
    val itemId: String,
    val itemTitle: String = "",
    val versionId: String? = null,
    val reportedBy: String = "",
    val reason: String = "",
    val status: String = "OPEN",
    val createdAt: String = "",
    val resolvedAt: String? = null
)

@Serializable
data class GitaVersionsDto(
    val item: GitaItemDto,
    val versions: List<GitaVersionDto> = emptyList()
)

@Serializable
data class GitaCorpusResponseDto(val documents: List<GitaCorpusDocDto> = emptyList())

/** Materiale per l'assistente: versione corrente di ogni voce, con il testo estratto. */
@Serializable
data class GitaCorpusDocDto(
    val itemId: String,
    val kind: String,
    val category: String = "ALTRO",
    val title: String,
    val uploadedAt: String = "",
    val versionNo: Int? = null,
    val url: String? = null,
    val text: String = ""
)

@Serializable
data class GitaCreatedDto(val id: String = "", val version: GitaVersionDto? = null)

@Serializable
data class GitaVersionCreatedDto(val version: GitaVersionDto? = null)

@Serializable
data class GitaOkDto(val ok: Boolean = false)

@Serializable
data class GitaReportCreatedDto(val id: String = "", val status: String = "OPEN")

/** Circolare della gita per questa classe: aggiunta dal Rappresentante o col titolo che ne parla. */
@Serializable
data class GitaCircularDto(
    val number: Int,
    val title: String = "",
    val publishDate: String = "",
    val pinned: Boolean = false
)
