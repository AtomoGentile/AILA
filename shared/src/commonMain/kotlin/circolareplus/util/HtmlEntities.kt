package circolareplus.util

private val NUMERIC_ENTITY = Regex("""&#(x[0-9a-fA-F]+|[0-9]+);""")

/**
 * Decodifica le entita' HTML che finiscono nei testi presi dal registro elettronico: `&#160;`,
 * `&nbsp;`, `&amp;`, `&quot;`, `&apos;`, `&lt;`, `&gt;` e qualunque riferimento numerico
 * (`&#39;`, `&#x27;`).
 *
 * Nasce dal titolo "Festa di sport - &#160;2026" visto in app: il server decodificava le entita'
 * nell'ordine sbagliato (`&amp;` prima del resto, quindi `&amp;#160;` diventava `&#160;` e li'
 * restava) e le righe gia' salvate non si correggono da sole. Qui si decodifica due volte per lo
 * stesso motivo — un testo gia' passato da un decoder puo' contenere ancora entita' — e `&amp;` si
 * tratta per ultimo, una volta per giro.
 */
fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    var current = text
    repeat(2) {
        val next = decodeOnce(current)
        if (next == current) return collapseSpaces(next)
        current = next
    }
    return collapseSpaces(current)
}

private fun decodeOnce(text: String): String {
    var result = NUMERIC_ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        val code = if (body.startsWith("x")) body.drop(1).toIntOrNull(16) else body.toIntOrNull()
        codePointToString(code) ?: match.value
    }
    result = result
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
    return result
}

/** Un solo carattere (o la coppia surrogata) per [code]; `null` se non e' un carattere valido. */
private fun codePointToString(code: Int?): String? {
    if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return null
    if (code == 0xA0) return " "
    if (code <= 0xFFFF) return code.toChar().toString()
    val offset = code - 0x10000
    val high = (0xD800 + (offset shr 10)).toChar()
    val low = (0xDC00 + (offset and 0x3FF)).toChar()
    return "$high$low"
}

private fun collapseSpaces(text: String): String =
    text.replace(Regex("""[ \t ]+"""), " ").trim()
