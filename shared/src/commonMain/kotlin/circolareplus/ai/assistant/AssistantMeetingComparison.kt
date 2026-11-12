package circolareplus.ai.assistant

import circolareplus.util.formatItalianDateWithWeekday
import circolareplus.util.CivilDate
import circolareplus.util.ITALIAN_MONTHS
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

    suspend fun answer(
        question: String,
        knowledge: AssistantKnowledge,
        readCirculars: suspend (List<Int>) -> Map<Int, String>
    ): AssistantReply? {
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
            return meetings.filter { meeting ->
                val title = AssistantContext.normalize(meeting.title)
                val source = circulars[meeting.number]
                val session = Regex("""(?:incontro|lezione|riunione|sessione)(?: (?:n |numero )?\d+)?""").matches(title.trim())
                val deadline = Regex("""\b(scadenza|iscrizione|iscrizioni|pagamento|adesione)\b""").containsMatchIn(title)
                val date = parseIsoDate(meeting.date)
                date != null && date.toIso() == meeting.date && date.day <= daysInMonth(date.year, date.month) && !deadline && (mentions(title, names) ||
                    session && source != null && mentions(source.title, names) &&
                    aliases.none { other -> names != other && other.any { " $it " in " $title " } })
            }.distinct().sortedWith(compareBy({ it.date }, { it.time.orEmpty() }))
        }
        val groups = labels.associateWith(::group).toMutableMap()
        val missing = labels.distinct().filter { groups[it].isNullOrEmpty() }
        val documents = missing.associateWith { label ->
            knowledge.circulars.filter { mentions(it.title, aliasesFor(label)) }
                .sortedByDescending { it.publishDate }.take(2)
        }
        val numbers = documents.values.flatten().map { it.number }.distinct()
        val texts = if (numbers.isEmpty()) emptyMap() else readCirculars(numbers)
        for ((label, docs) in documents) {
            groups[label] = docs.flatMap { doc ->
                texts[doc.number].orEmpty().lineSequence().mapNotNull { line ->
                    meetingFromLine(line, doc.number, label)
                }.toList()
            }.distinct().sortedWith(compareBy({ it.date }, { it.time.orEmpty() }))
        }
        val first = groups.getValue(labels[0])
        val second = groups.getValue(labels[1])
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
        val sources = ((first + second).mapNotNull { circulars[it.number] } + documents.values.flatten()).distinctBy { it.number }.map {
            AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. ${it.number} — ${it.title}", it.number)
        }
        return AssistantReply(text, sources + AssistantSource(AssistantSourceKind.CALENDAR, "Calendario"), modelLabel = "AILA")
    }

    private fun mentions(text: String, names: Set<String>): Boolean {
        val words = " " + AssistantContext.normalize(text).trim().replace(Regex("""\s+"""), " ") + " "
        return names.any { " $it " in words }
    }

    // A PDF row needs its own date with an explicit year. Do not infer a recurrence,
    // propagate a time across rows, or parse publication dates from document headers.
    private val dateInRow = Regex("""\b(?:(\d{4})-(\d{2})-(\d{2})|(\d{1,2})[/.](\d{1,2})[/.](\d{4})|(\d{1,2})\s+([a-z]+)\s+(\d{4}))\b""")
    private val clockInRow = Regex("""(?<!\d)(?:[01]?\d|2[0-3])[:.][0-5]\d(?:\s*(?:[-–—]|alle)\s*(?:[01]?\d|2[0-3])[:.][0-5]\d)?(?!\d)""")
    private val nonMeeting = Regex("""\b(scadenza|adesione|pagamento|iscrizioni?|iscrizione|annullat[oaie]|rinviat[oaie]|sospes[oaie]|entro|protocollo)\b""")
    private val sessionWord = Regex("""\b(lezione|incontro|riunione|sessione)\b""")

    private fun meetingFromLine(line: String, number: Int, label: String): Meeting? {
        val row = line.lowercase().trim()
        if (nonMeeting.containsMatchIn(row) || !sessionWord.containsMatchIn(row)) return null
        // Mixed-activity circulars are frequent. Only rows identifying the activity count.
        if (!mentions(row, aliasesFor(label))) return null
        val match = dateInRow.findAll(row).singleOrNull() ?: return null
        val g = match.groupValues
        val date = when {
            g[1].isNotEmpty() -> CivilDate(g[1].toInt(), g[2].toInt(), g[3].toInt())
            g[4].isNotEmpty() -> CivilDate(g[6].toInt(), g[5].toInt(), g[4].toInt())
            else -> CivilDate(g[9].toInt(), ITALIAN_MONTHS.indexOfFirst { it.equals(g[8], true) }, g[7].toInt())
        }
        if (date.month !in 1..12 || date.day !in 1..daysInMonth(date.year, date.month)) return null
        val time = clockInRow.findAll(row.removeRange(match.range)).singleOrNull()?.value
        return Meeting(date.toIso(), time, label, number)
    }

    private fun dayLabel(date: String) = formatItalianDateWithWeekday(date) + " " + date.take(4)

    private fun clock(minutes: Int) = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}
