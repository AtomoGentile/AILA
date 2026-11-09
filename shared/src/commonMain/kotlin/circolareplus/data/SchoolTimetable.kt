package circolareplus.data

import circolareplus.util.parseIsoDate
import circolareplus.util.weekdayOf

data class TimetableLesson(val subject: String, val fromHour: Int, val toHour: Int) {
    val timeLabel: String get() = if (fromHour == toHour) "${fromHour}ª ora" else "Dalla ${fromHour}ª alla ${toHour}ª ora"
}

/** Published weekly baseline. Holidays, substitutions and closures are separate exceptions. */
object SchoolTimetable {
    const val SCHOOL_YEAR = "2026/2027"
    const val SOURCE_URL = "https://cspace.spaggiari.eu/pub/TVII0007/Orario/as_2627/All%201%20-%20Orario%20Definitivo%20Classi%202026-27.pdf"
    const val MAX_HOURS = 9

    fun normalizeClass(label: String): String = label.uppercase().filter { it.isLetterOrDigit() }

    fun week(classLabel: String?): List<List<String>>? = classLabel?.let { SCHOOL_TIMETABLE_DATA[normalizeClass(it)] }

    fun lessons(classLabel: String?, dateIso: String): List<TimetableLesson> {
        val date = parseIsoDate(dateIso) ?: return emptyList()
        if (dateIso !in "2026-09-01".."2027-08-31" || date.toIso() != dateIso ||
            date.day > circolareplus.util.daysInMonth(date.year, date.month)) return emptyList()
        return day(classLabel, weekdayOf(date))
    }

    fun day(classLabel: String?, weekday: Int): List<TimetableLesson> {
        val hours = week(classLabel)?.getOrNull(weekday) ?: return emptyList()
        val blocks = mutableListOf<TimetableLesson>()
        hours.forEachIndexed { index, subject ->
            if (subject.isNotBlank()) {
                val last = blocks.lastOrNull()
                if (last?.subject == subject && last.toHour == index) {
                    blocks[blocks.lastIndex] = last.copy(toHour = index + 1)
                } else blocks += TimetableLesson(subject, index + 1, index + 1)
            }
        }
        return blocks
    }

    fun suggestions(classLabel: String?, dateIso: String, prefix: String): List<TimetableLesson> =
        lessons(classLabel, dateIso).filter { prefix.isBlank() || it.subject.contains(prefix.trim(), ignoreCase = true) }

    fun maxHours(classLabel: String?): Int? = week(classLabel)?.maxOfOrNull { it.size }

    fun describe(classLabel: String?): String = buildString {
        val week = week(classLabel) ?: return@buildString
        appendLine("ORARIO SETTIMANALE $classLabel — A.S. $SCHOOL_YEAR; fonte: $SOURCE_URL")
        appendLine("Ore di lezione, non fasce dell'orologio. Schema base: non verifica festività, chiusure o sostituzioni.")
        week.indices.forEach { weekday ->
            appendLine("${circolareplus.util.ITALIAN_WEEKDAYS[weekday]}: " +
                day(classLabel, weekday).joinToString("; ") { "${it.subject} ${it.timeLabel}" }.ifBlank { "nessuna lezione in orario" })
        }
        appendLine("Puoi proporre l'ora della materia nella data richiesta, dichiarando che proviene dall'orario. Se ci sono più blocchi chiedi quale; l'ora esplicita dell'utente prevale. Fuori dall'A.S. non applicare questo schema.")
    }
}
