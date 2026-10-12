package circolareplus.ai

/** Ore di lezione di un evento: da [from] a [to] (stessa ora se e' una sola), entrambe tra 1 e 6. */
internal data class LessonHourRange(val from: Int, val to: Int)

/**
 * Legge le ore di lezione (1-6, il selettore "Da / A" del foglio evento) dal testo scritto
 * dall'utente: "3ª ora", "terza ora", "dalla 2 alla 4 ora", "2ª-4ª ora", "prime due ore",
 * "ultima ora".
 *
 * Il modello di AI le riceve nel prompt, ma uno piccolo sbaglia o le ignora: queste frasi hanno
 * una forma fissa e si leggono meglio con delle regole, che quindi hanno la precedenza.
 */
internal object LessonHours {
    const val MIN = 1
    const val MAX = 6

    private val WORDS = mapOf(
        "prim" to 1, "second" to 2, "terz" to 3, "quart" to 4, "quint" to 5, "sest" to 6
    )
    private val COUNTS = mapOf("due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6)

    private const val NUM = "([1-6]|prim[ao]|second[ao]|terz[ao]|quart[ao]|quint[ao]|sest[ao])"
    // Suffisso ordinale: "3ª", "3º", "3°", "3a".
    private const val SUF = "[ªº°a]?"

    private val range = Regex("\\b(?:dalla|dal|da)\\s+(?:la\\s+)?$NUM\\s*$SUF\\s*(?:ora\\s+)?(?:alla|al|a)\\s+(?:la\\s+)?$NUM\\s*$SUF\\s*ora\\b")
    private val dashed = Regex("\\b$NUM\\s*$SUF\\s*(?:-|–|/|\\se\\s)\\s*$NUM\\s*$SUF\\s*ora\\b")
    private val firstN = Regex("\\bprime\\s+(due|tre|quattro|cinque|sei|[2-6])\\s+ore\\b")
    private val single = Regex("\\b$NUM\\s*$SUF\\s*ora\\b")
    private val last = Regex("\\bultima\\s+ora\\b")

    private fun number(token: String): Int? =
        token.toIntOrNull() ?: WORDS.entries.firstOrNull { token.startsWith(it.key) }?.value

    /**
     * Le ore dette nel testo. `null` se non ce ne sono o se qualcuna supera [maxHours]: una classe
     * da 5 ore non ha la 6ª. "Ultima ora" e' l'ultima della classe, cioe' [maxHours].
     */
    fun parse(text: String, maxHours: Int = MAX): LessonHourRange? =
        parseAny(text, maxHours)?.takeIf { it.to <= maxHours }

    private fun parseAny(text: String, maxHours: Int): LessonHourRange? {
        val lower = text.lowercase()
        (range.find(lower) ?: dashed.find(lower))?.let { m ->
            val a = number(m.groupValues[1]) ?: return@let
            val b = number(m.groupValues[2]) ?: return@let
            return LessonHourRange(minOf(a, b), maxOf(a, b))
        }
        firstN.find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: COUNTS[m.groupValues[1]] ?: return@let
            return LessonHourRange(1, n)
        }
        single.find(lower)?.let { m ->
            val n = number(m.groupValues[1]) ?: return@let
            return LessonHourRange(n, n)
        }
        if (last.containsMatchIn(lower)) return LessonHourRange(maxHours, maxHours)
        return null
    }

    // Griglia oraria usata per tradurre un orario dell'orologio in ora di lezione: la 1ª ora
    // comincia alle 8:00 e ogni ora dura un'ora, quindi la 6ª finisce alle 14:00. E' l'unico posto
    // da cambiare se la scuola ha altri orari del suono.
    private const val FIRST_HOUR_START_MINUTES = 8 * 60
    private const val LESSON_MINUTES = 60

    /**
     * L'ora di lezione in corso all'orario "HH:MM" dell'orologio ("09:00" -> 2, "10:30" -> 3), o
     * `null` se cade fuori dalle ore di lezione della classe (prima delle 8:00, dopo la fine della
     * [maxHours]-esima ora: 13:00 con 5 ore, 14:00 con 6) o non e' leggibile.
     */
    fun fromClock(timeHm: String?, maxHours: Int = MAX): Int? {
        val parts = timeHm?.split(':') ?: return null
        if (parts.size != 2) return null
        val minutes = (parts[0].toIntOrNull() ?: return null) * 60 + (parts[1].toIntOrNull() ?: return null)
        if (minutes < FIRST_HOUR_START_MINUTES) return null
        return ((minutes - FIRST_HOUR_START_MINUTES) / LESSON_MINUTES + 1).takeIf { it <= maxHours }
    }

    /** Un numero letto dal JSON del modello, se e' un'ora di lezione valida. */
    fun valid(hour: Int?): Int? = hour?.takeIf { it in MIN..MAX }
}
