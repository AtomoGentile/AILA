package circolareplus.ai.assistant

import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.daysInMonth
import circolareplus.util.parseIsoDate

/** Answers explicit two-activity schedule questions from the dated records visible to the user. */
internal object AssistantMeetingComparison {
    private val questionPattern = Regex(
        """quando (?:sono|si tengono) (?:gli incontri|le riunioni|le lezioni) (?:del |della |di |dell )?(.+?) si (?:intrecciano|sovrappongono|accavallano) con (?:il |la |l |i |le )?(.+)"""
    )
    private val simpleComparison = Regex(
        """(?:il |la |l |gli incontri del |le lezioni di )?(.+?) (?:si (?:sovrappone|sovrappongono|intreccia|intrecciano|accavalla|accavallano)|coincide|coincidono) (?:con |al |alla |all )(?:(?:il|lo|la|l|i|le) )?(.+)"""
    )
    private val aliases = listOf(
        setOf("giornalino", "giornalino scolastico", "rasoio di ockham", "riunione di redazione"),
        setOf("fce", "b2 first", "first certificate", "first for schools")
    )
    fun aliasesFor(label: String): Set<String> = aliases.firstOrNull { label in it } ?: setOf(label)

    private data class Meeting(val date: String, val time: String?, val title: String, val number: Int?)

    fun answer(question: String, knowledge: AssistantKnowledge): AssistantReply? {
        val normalized = AssistantContext.normalize(question).trim().replace(Regex("""\s+"""), " ")
        val match = questionPattern.matchEntire(normalized) ?: simpleComparison.matchEntire(normalized) ?: return null
        if (TimeScopeParser.parse(question, knowledge.todayIso) != null) return null
        val labels = match.groupValues.drop(1)
        if (labels.any { it.split(' ').size > 5 || it.contains(" e ") }) return null
        val circulars = knowledge.circulars.associateBy { it.number }
        val meetings = knowledge.calendarEvents.filter { event ->
            event.isForAll || event.visibleToUserIds.isNullOrEmpty() || knowledge.user?.id in event.visibleToUserIds.orEmpty()
        }.map {
            Meeting(it.date.take(10), it.time, it.title, it.circularNumber)
        } + knowledge.classifications.values.filter { !it.isFallback && it.circularNumber in circulars }.flatMap { analysis ->
            analysis.detectedDeadlines.map { Meeting(it.dueDate.take(10), it.time, it.title, analysis.circularNumber) }
        }
        fun group(label: String): List<Meeting> {
            val names = aliasesFor(label)
            fun matches(text: String): Boolean {
                val words = " " + AssistantContext.normalize(text).trim().replace(Regex("""\s+"""), " ") + " "
                return names.any { " $it " in words }
            }
            return meetings.filter { meeting ->
                val title = AssistantContext.normalize(meeting.title)
                val source = circulars[meeting.number]
                val session = Regex("""(?:incontro|lezione|riunione|sessione)(?: (?:n |numero )?\d+)?""").matches(title.trim())
                val deadline = Regex("""\b(scadenza|iscrizione|iscrizioni|pagamento|adesione)\b""").containsMatchIn(title)
                val date = parseIsoDate(meeting.date)
                date != null && date.toIso() == meeting.date && date.day <= daysInMonth(date.year, date.month) && !deadline && (matches(title) ||
                    session && source != null && matches(source.title) &&
                    aliases.none { other -> names != other && other.any { " $it " in " $title " } })
            }.distinct().sortedWith(compareBy({ it.date }, { it.time.orEmpty() }))
        }
        val first = group(labels[0])
        val second = group(labels[1])
        val firstLabel = labels[0].replaceFirstChar { it.uppercase() }
        val secondLabel = if (labels[1] == "fce") "FCE" else labels[1]
        val byDay = second.groupBy { it.date }
        val overlaps = mutableSetOf<String>()
        val uncertain = mutableSetOf<String>()
        for (a in first) for (b in byDay[a.date].orEmpty()) {
            val day = dayLabel(a.date)
            val x = AssistantReadTools.timeRange(a.time)
            val y = AssistantReadTools.timeRange(b.time)
            if (x == null || y == null || x.first != y.first || a == b) {
                uncertain += "$day ($firstLabel: ${a.time ?: "orario assente"}, $secondLabel: ${b.time ?: "orario assente"})"
                continue
            }
            val start = maxOf(x.second, y.second)
            val end = minOf(x.third, y.third)
            if (x.first == "lesson" && start <= end) overlaps += "$day, dalla ${start}ª alla ${end}ª ora"
            if (x.first == "clock" && start < end) overlaps += "$day, ${clock(start)}–${clock(end)}"
        }
        val text = buildString {
            appendLine("$firstLabel — incontri presenti nei dati di AILA:")
            first.map { dayLabel(it.date) + ", " + (it.time ?: "orario assente") }
                .distinct().forEach { appendLine("• $it") }
            if (first.isEmpty()) appendLine("Non ho trovato date per $firstLabel.")
            appendLine()
            if (second.isEmpty()) appendLine("Non ho trovato date per $secondLabel.")
            if (first.isEmpty() || second.isEmpty()) appendLine("Mi manca uno dei due calendari per verificare le sovrapposizioni.")
            else {
                appendLine("Confronto con $secondLabel (${second.distinctBy { it.date to it.time }.size} appuntamenti registrati):")
                if (overlaps.isNotEmpty()) appendLine("Sovrapposizioni verificate:\n" + overlaps.joinToString("\n") { "• $it" })
                if (uncertain.isNotEmpty()) appendLine("Stessa data, sovrapposizione da verificare:\n" + uncertain.joinToString("\n") { "• $it" })
                if (overlaps.isEmpty() && uncertain.isEmpty()) appendLine("Tra gli incontri registrati non risultano sovrapposizioni.")
            }
            knowledge.calendarWarning?.let { appendLine("Calendario potenzialmente incompleto: $it") }
        }.trim()
        val sources = (first + second).mapNotNull { circulars[it.number] }.distinctBy { it.number }.map {
            AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. ${it.number} — ${it.title}", it.number)
        }
        return AssistantReply(text, sources + AssistantSource(AssistantSourceKind.CALENDAR, "Calendario"), modelLabel = "AILA")
    }

    private fun dayLabel(date: String) = formatItalianDateWithWeekday(date) + " " + date.take(4)

    private fun clock(minutes: Int) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}
