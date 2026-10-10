package circolareplus.domain.model

/** Categorie dei documenti della gita; i link non ne hanno una (vedi [GitaItem.kind]). */
enum class GitaCategory(val id: String, val label: String) {
    PROGRAMMA("PROGRAMMA", "Programma"),
    PREVENTIVO("PREVENTIVO", "Preventivo"),
    SCADENZA("SCADENZA", "Scadenza"),
    REGOLAMENTO("REGOLAMENTO", "Regolamento"),
    PAGAMENTO("PAGAMENTO", "Pagamento"),
    ALTRO("ALTRO", "Altro");

    companion object {
        fun fromId(id: String?): GitaCategory = entries.firstOrNull { it.id == id } ?: ALTRO
    }
}

/** Un documento o un link della gita, con la versione corrente. */
data class GitaItem(
    val id: String,
    val isLink: Boolean,
    val category: GitaCategory,
    val title: String,
    val createdAt: String,
    val current: GitaVersion?
)

/** Una versione: i documenti hanno [hasFile], i link hanno [url]. [uploadedAt] è la data da mostrare. */
data class GitaVersion(
    val id: String,
    val versionNo: Int,
    val uploadedAt: String,
    val hasFile: Boolean,
    val textChars: Int,
    val url: String?,
    val note: String?
)

data class GitaReport(
    val id: String,
    val itemId: String,
    val itemTitle: String,
    val reason: String,
    val isResolved: Boolean,
    val createdAt: String,
    val resolvedAt: String?
)

/** Tutto quello che la schermata Gita mostra. [canEdit] vale solo per il Rappresentante. */
data class GitaFeed(
    val items: List<GitaItem>,
    val reports: List<GitaReport>,
    val canEdit: Boolean,
    val circulars: List<GitaCircular> = emptyList()
)

/** Circolare che riguarda la gita di questa classe. [pinned]: aggiunta a mano dal Rappresentante. */
data class GitaCircular(val number: Int, val title: String, val publishDate: String, val pinned: Boolean)

/** Documento della gita come lo legge l'assistente: testo già estratto dal PDF. */
data class GitaSourceDoc(
    val itemId: String,
    val title: String,
    val category: GitaCategory,
    val uploadedAt: String,
    val versionNo: Int?,
    val url: String?,
    val text: String
)
