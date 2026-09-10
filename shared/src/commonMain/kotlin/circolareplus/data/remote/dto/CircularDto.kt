package circolareplus.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CircularDto(
    val number: Int,
    val title: String,
    val publishDate: String,
    val pdfKey: String,
    val hasLocalPdf: Boolean? = null,
    val originalUrl: String? = null,
    val attachments: List<CircularAttachmentDto> = emptyList(),
    val createdAt: String? = null
)

/**
 * Un allegato della colonna "Allegati" di Spaggiari (0, 1 o più per circolare, oltre al PDF
 * principale). Esattamente uno fra [pdfKey] e [url] è valorizzato: [pdfKey] quando il documento
 * è stato scaricato e messo in cache su R2 come il PDF principale (si mostra dentro l'app allo
 * stesso modo), [url] quando punta altrove — es. un'altra pagina del sito, non un PDF diretto —
 * e va aperto nel browser di sistema.
 */
@Serializable
data class CircularAttachmentDto(
    val label: String,
    val pdfKey: String? = null,
    val url: String? = null
)

@Serializable
data class CircularsListResponseDto(
    val circulars: List<CircularDto>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

@Serializable
data class ExtractedDeadlineDto(
    val title: String,
    val dueDate: String,
    val time: String? = null,
    val category: String = "AVVISO"
)

/** Analisi AI di una circolare, salvata sul server come cache condivisa fra utenti. */
@Serializable
data class CircularAnalysisDto(
    val circularNumber: Int,
    val badge: String,
    val summary: String,
    val deadlines: List<ExtractedDeadlineDto> = emptyList(),
    val isFallback: Boolean = false,
    val modelLabel: String = "Sconosciuto",
    val updatedAt: String? = null
)

/** Corpo della PUT che salva/sovrascrive l'analisi in cache. */
@Serializable
data class SaveCircularAnalysisRequestDto(
    val badge: String,
    val summary: String,
    val deadlines: List<ExtractedDeadlineDto> = emptyList(),
    val isFallback: Boolean = false,
    val modelLabel: String = "Sconosciuto"
)
