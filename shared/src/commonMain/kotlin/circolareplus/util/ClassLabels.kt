package circolareplus.util

private val SPACED_CLASS_LABEL = Regex("""(\d)\s*([\^°ª])\s+([A-Z]{1,4}\b)""")

/**
 * "4^ CSA" -> "4^CSA": l'etichetta della classe si scrive attaccata. Le circolari (e quindi i
 * riassunti di Gemini) la scrivono spesso con lo spazio; si normalizza in lettura, così anche le
 * analisi e gli eventi già salvati si vedono giusti.
 */
fun compactClassLabels(text: String): String = SPACED_CLASS_LABEL.replace(text, "$1$2$3")
