package circolareplus.ai.assistant

import circolareplus.domain.model.Proposal
import circolareplus.domain.model.ProposalOutcome
import circolareplus.domain.model.ProposalStatus

/**
 * Le domande-elenco sulla bacheca ("quali proposte sono ancora aperte?", "le piu' votate",
 * "quelle accettate") risposte dal codice invece che dal modello.
 *
 * Stesso motivo di [AssistantAgenda]: con Gemini Nano la sezione della bacheca sta in coda al
 * contesto e con la finestra stretta veniva tagliata, e anche quando c'era il modello doveva
 * capire da solo che `NUOVA` e `IN_ANALISI` vogliono dire "aperta". Risultato in chat: "Non
 * risulta", alla domanda che l'app stessa suggerisce. Filtrare per stato e ordinare per voti
 * sono cose che il codice fa senza sbagliare.
 *
 * Come per l'agenda, scatta solo se ogni parola della domanda e' una parola "da bacheca": una
 * domanda su una proposta precisa ("cosa dice la proposta sulla gita?") resta al modello.
 */
internal object AssistantBoard {

    /** Oltre questo l'elenco in chat diventa illeggibile: il resto e' in bacheca. */
    private const val MAX_ITEMS = 10

    internal enum class Kind(val noun: String) {
        OPEN("aperte"),
        IN_ANALYSIS("in analisi"),
        CLOSED("chiuse"),
        ACCEPTED("accettate"),
        REJECTED("rifiutate"),
        ALL("")
    }

    internal data class Request(
        val kind: Kind,
        val mostVoted: Boolean,
        val mine: Boolean,
        val notVotedByMe: Boolean
    )

    /** La risposta gia' pronta, o `null` se la domanda non e' un elenco della bacheca. */
    fun answer(knowledge: AssistantKnowledge, question: String): AssistantReply? {
        val request = requestOf(question) ?: return null

        if (knowledge.proposals.isEmpty() && knowledge.dynamic.unavailable.any { it.startsWith(UNAVAILABLE_LABEL) }) {
            return AssistantReply(
                text = "Non sono riuscito a leggere la bacheca. Riprova tra poco o aprila dalla scheda Classe.",
                modelLabel = MODEL_LABEL
            )
        }

        val myId = knowledge.user?.id
        val filtered = knowledge.proposals
            .filter { matches(it, request.kind) }
            .filter { !request.mine || (myId != null && it.authorId == myId) }
            .filter { !request.notVotedByMe || it.myVote == 0 }
        val sorted = if (request.mostVoted || request.kind == Kind.OPEN || request.kind == Kind.IN_ANALYSIS) {
            filtered.sortedWith(compareByDescending<Proposal> { it.upvotes - it.downvotes }.thenByDescending { it.createdAt })
        } else {
            filtered.sortedByDescending { it.createdAt }
        }
        val shown = sorted.take(MAX_ITEMS)
        val what = description(request)

        val text = buildString {
            when {
                knowledge.proposals.isEmpty() -> append("In bacheca non c'e' nessuna proposta.")
                shown.isEmpty() -> {
                    append("Non ci sono $what.")
                    val open = knowledge.proposals.count { matches(it, Kind.OPEN) }
                    if (request.kind != Kind.OPEN && open > 0) {
                        append(" In bacheca ci sono ").append(open)
                        append(if (open == 1) " proposta aperta." else " proposte aperte.")
                    }
                }
                else -> {
                    append(what.replaceFirstChar { it.uppercase() }).append(" (").append(filtered.size).append("):")
                    shown.forEach { appendLine().append(line(it, knowledge.todayIso, request.kind)) }
                    val hidden = filtered.size - shown.size
                    if (hidden > 0) appendLine().append("…e altre $hidden: le trovi tutte in bacheca.")
                }
            }
        }

        return AssistantReply(
            text = text,
            sources = listOf(AssistantSource(AssistantSourceKind.BOARD, "Bacheca della classe")),
            modelLabel = MODEL_LABEL
        )
    }

    /**
     * Che elenco chiede la domanda, o `null` se non riguarda la bacheca o se contiene un
     * argomento (allora la risposta va cercata nei testi, ed e' lavoro del modello).
     */
    internal fun requestOf(question: String): Request? {
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        val terms = AssistantContext.tokenize(question)
        fun has(vararg stems: String) = words.any { word -> stems.any { word.startsWith(it) } }

        if (!has("propost", "bacheca")) return null
        if (terms.any { term -> BOARD_WORDS.none { term.startsWith(it) } }) return null

        val kind = when {
            has("accettat", "approvat", "passat") -> Kind.ACCEPTED
            has("rifiutat", "bocciat", "respint") -> Kind.REJECTED
            has("analisi", "valutazion", "esame", "discussion") -> Kind.IN_ANALYSIS
            has("chius", "conclus", "finit") -> Kind.CLOSED
            has("apert", "attiv", "corso", "pendent", "sospes", "nuov", "votar") -> Kind.OPEN
            else -> Kind.ALL
        }
        val phrase = " " + words.joinToString(" ") + " "
        return Request(
            kind = kind,
            mostVoted = has("votat", "popolar", "piaciut", "gradit", "sostenut", "classifica") &&
                !phrase.contains(" non ho votato ") && !phrase.contains(" non votat"),
            mine = words.any { it == "mie" || it == "mia" || it == "miei" } || phrase.contains(" ho proposto ") ||
                phrase.contains(" ho fatto ") || phrase.contains(" ho scritto "),
            notVotedByMe = phrase.contains(" da votare ") || phrase.contains(" non ho votato ") ||
                phrase.contains(" non ho ancora votato ")
        )
    }

    private fun matches(proposal: Proposal, kind: Kind): Boolean = when (kind) {
        Kind.OPEN -> proposal.status != ProposalStatus.CHIUSA
        Kind.IN_ANALYSIS -> proposal.status == ProposalStatus.IN_ANALISI
        Kind.CLOSED -> proposal.status == ProposalStatus.CHIUSA
        Kind.ACCEPTED -> proposal.status == ProposalStatus.CHIUSA && proposal.outcome == ProposalOutcome.ACCETTATA
        Kind.REJECTED -> proposal.status == ProposalStatus.CHIUSA && proposal.outcome == ProposalOutcome.RIFIUTATA
        Kind.ALL -> true
    }

    private fun description(request: Request): String = buildString {
        append(if (request.mostVoted) "proposte piu' votate" else "proposte")
        if (request.kind != Kind.ALL) append(' ').append(request.kind.noun)
        if (request.mine) append(" che hai fatto tu")
        if (request.notVotedByMe) append(" che non hai ancora votato")
        if (request.kind == Kind.ALL && !request.mine && !request.notVotedByMe && !request.mostVoted) {
            append(" in bacheca")
        }
    }

    private fun line(proposal: Proposal, todayIso: String, kind: Kind): String = buildString {
        append("• ").append(proposal.title.trim())
        // Lo stato si ripete solo quando l'elenco ne mescola piu' d'uno.
        if (kind == Kind.OPEN || kind == Kind.CLOSED || kind == Kind.ALL) append(" — ").append(statusOf(proposal))
        append(" · ").append(proposal.upvotes).append(" a favore, ").append(proposal.downvotes)
        append(if (proposal.downvotes == 1) " contrario" else " contrari")
        when (proposal.myVote) {
            1 -> append(" (tu a favore)")
            -1 -> append(" (tu contrario)")
        }
        if (proposal.commentsCount > 0) {
            append(" · ").append(proposal.commentsCount).append(if (proposal.commentsCount == 1) " commento" else " commenti")
        }
        if (parseableDate(proposal.createdAt)) {
            append(" · del ").append(AssistantContext.readableDate(proposal.createdAt, todayIso))
        }
    }

    internal fun statusOf(proposal: Proposal): String = when (proposal.status) {
        ProposalStatus.NUOVA -> "aperta, nuova"
        ProposalStatus.IN_ANALISI -> "aperta, in analisi dal Rappresentante"
        ProposalStatus.CHIUSA -> when (proposal.outcome) {
            ProposalOutcome.ACCETTATA -> "chiusa, accettata"
            ProposalOutcome.RIFIUTATA -> "chiusa, rifiutata"
            null -> "chiusa"
        }
    }

    private fun parseableDate(value: String): Boolean = circolareplus.util.parseIsoDate(value.take(10)) != null

    internal const val UNAVAILABLE_LABEL = "Bacheca"
    private const val MODEL_LABEL = "Dalla bacheca di AILA"

    /**
     * Le radici delle parole ammesse in una domanda-elenco sulla bacheca, oltre alle stopword.
     * Tutto quello che non comincia con una di queste e' un argomento, e la domanda va al modello.
     */
    private val BOARD_WORDS = listOf(
        // cosa
        "propost", "bacheca", "classe",
        // stato
        "apert", "attiv", "corso", "pendent", "sospes", "nuov", "recent", "ultim", "analisi",
        "valutazion", "esame", "discussion", "chius", "conclus", "finit", "accettat", "approvat",
        "passat", "rifiutat", "bocciat", "respint", "stat", "esito",
        // voti
        "votat", "votar", "voti", "popolar", "piaciut", "gradit", "sostenut", "classifica",
        // come lo si chiede
        "elenc", "mostr", "ricord", "ci", "abbiamo", "avete", "hanno", "fatto", "scritto",
        "proposto", "nessun", "qualche", "altre", "tutt", "adesso", "ora", "momento", "attualment"
    )
}
