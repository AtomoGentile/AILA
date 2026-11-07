package circolareplus.ai.assistant

import circolareplus.domain.model.UserRole

/** Exact calendar/name matches can be planned from app data without model-generated copies. */
internal object AssistantAudienceEdit {
    private val audience = Regex("\\bsolo\\s+(?:a|per)\\s+([^.!?]+)", RegexOption.IGNORE_CASE)
    private val mutation = Regex("\\b(?:cambia|imposta|modifica|rendi|rendile|rendili)\\b", RegexOption.IGNORE_CASE)
    private val scopeWords = setOf(
        "trova", "cerca", "prendi", "tutte", "tutti", "tutto", "ogni", "le", "i", "gli", "la", "il",
        "date", "data", "eventi", "evento", "appuntamenti", "calendario", "agenda", "a", "nel", "in", "cui",
        "si", "parla", "di", "del", "della", "delle", "dei", "degli", "e", "corso", "percorso"
    )

    fun plan(question: String, history: List<AssistantMessage>, knowledge: AssistantKnowledge): AssistantActionPlan? {
        if (!AssistantActionIntent.matches(question, history)) return null
        if (knowledge.attachedSources.isNotEmpty()) return null
        val pending = history.lastOrNull()?.awaitingActionInput == true
        val source = if (audience.containsMatchIn(question)) question else if (pending) {
            history.lastOrNull { it.author == AssistantAuthor.USER && audience.containsMatchIn(it.text) }?.text
                ?: return null
        } else return null
        // This shortcut changes only recipients. Mixed edits still use the general planner.
        val normalized = AssistantContext.normalize(source)
        if (normalized.split(' ').any { it in setOf("note", "nota", "aggiungi", "sposta", "elimina", "cancella", "rimuovi", "titolo", "categoria", "orario", "ora", "anticipa", "posticipa") }) return null
        val requested = audience.find(source)?.groupValues?.get(1) ?: return null
        val names = requested.split(Regex("\\s*(?:,|\\be\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { AssistantContext.normalize(it).trim() }.filter { it.isNotBlank() }
        if (names.isEmpty()) return null
        val people = (knowledge.classmates + listOfNotNull(knowledge.user)).distinctBy { it.id }
        val recipientIds = mutableListOf<String>()
        for (name in names) {
            val matches = if (name in setOf("me", "io")) listOfNotNull(knowledge.user) else people.filter {
                name == AssistantContext.normalize(it.firstName) ||
                    name == AssistantContext.normalize("${it.firstName} ${it.lastName}")
            }
            if (matches.size != 1) return AssistantActionPlan(clarification =
                if (matches.isEmpty()) "Non trovo «$name» tra i compagni. Qual è il nome completo?"
                else "Ci sono più compagni chiamati «$name». Qual è il cognome?")
            recipientIds += matches.single().id
        }
        // A short follow-up refines the event title while retaining the pending recipients.
        val selector = if (source != question) question else {
            val withoutAside = source.replace(Regex("\\([^)]*\\)"), " ")
            val change = mutation.find(withoutAside) ?: return null
            withoutAside.take(change.range.first)
        }
        if (TimeScopeParser.parse(selector, knowledge.todayIso) != null) return null
        val words = titleWords(selector).filterNot { it in scopeWords }.distinct()
        if (words.isEmpty() || words.any { word -> word.any(Char::isDigit) }) return null
        val all = normalized.split(' ').any { it in setOf("tutte", "tutti", "ogni") }
        if (!all) return null
        val matching = knowledge.calendarEvents.filter { event ->
            val title = titleWords(event.title)
            words.all { it in title }
        }
        if (matching.isEmpty()) return AssistantActionPlan(clarification =
            "Non trovo eventi con «${selector.trim()}» nel calendario visibile. Qual è il titolo esatto?")
        if (matching.any { !it.isAiGenerated && it.createdByUserId != knowledge.user?.id && knowledge.role != UserRole.REPRESENTATIVE }) {
            return AssistantActionPlan(clarification = "Ho trovato ${matching.size} eventi, ma non hai i permessi per modificarli tutti. Quali eventi creati da te vuoi modificare?")
        }
        if (matching.size > AssistantActionPrompt.MAX_ACTIONS) return AssistantActionPlan(clarification =
            "Ho trovato ${matching.size} eventi. Posso prepararne al massimo ${AssistantActionPrompt.MAX_ACTIONS} alla volta: quale intervallo di date vuoi modificare per primo?")
        return AssistantActionPlan(
            requestedByUserId = knowledge.user?.id,
            actions = matching.sortedWith(compareBy({ it.date }, { it.id })).map { event ->
                AssistantCalendarAction(
                    tool = "calendar.update", eventId = event.id, title = event.title,
                    date = event.date, time = event.time, category = event.category, notes = event.notes,
                    fields = listOf("visibility"),
                    visibility = "SPECIFIC", visibleToUserIds = recipientIds.distinct()
                )
            }
        )
    }

    private fun titleWords(text: String): List<String> = AssistantContext.normalize(text)
        .replace(Regex("\\bfirst\\s+fce\\b"), "fce")
        .split(' ').filter(String::isNotBlank).map { if (it == "lezioni") "lezione" else it }
}
