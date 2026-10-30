package circolareplus.domain.model

import circolareplus.algorithms.DeskAssignment

/** Una disposizione registrata dal server esclusivamente al momento della pubblicazione. */
data class PublishedSeatMap(
    val id: String,
    val mapIndex: Int,
    val publishedAt: String,
    val assignments: List<DeskAssignment>
)
