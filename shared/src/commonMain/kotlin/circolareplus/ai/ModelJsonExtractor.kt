package circolareplus.ai

/**
 * Estrae un oggetto JSON dalla risposta grezza di un modello locale piccolo.
 *
 * Stava duplicata, identica, dentro [CircularClassificationPrompt] e [EventGenerationPrompt]. La
 * copia in comune non è solo ordine: **entrambe avevano lo stesso bug**, scoperto con Phi-4 mini
 * su "Festa di sport" — la circolare cita il nome della scuola fra virgolette ("Primo Levi") e il
 * modello lo riporta nel campo "summary" copiando le virgolette del testo originale così come
 * sono, senza scapparle (`\"`). Il conteggio delle parentesi qui sotto usava `"` per capire dove
 * inizia e finisce una stringa JSON: alla prima virgoletta non scappata dentro il testo copiato,
 * credeva che la stringa "summary" fosse già finita, e tutto quello che veniva dopo (compreso
 * l'eventuale `}` di chiusura vero) veniva letto come se fosse fuori dalla stringa — risultato,
 * un oggetto che sembrava troncato proprio lì, o un JSON che sembra completo ma non lo è più una
 * volta passato a un parser vero.
 */
internal object ModelJsonExtractor {

    fun extractJsonObject(raw: String): String? {
        var text = raw.trim()

        // I modelli piccoli sporcano quasi sempre la risposta: la incapsulano in un blocco
        // ```json, oppure — le varianti "thinking" — antepongono un blocco <think>…</think>.
        val thinkEnd = text.indexOf("</think>")
        if (thinkEnd >= 0) text = text.substring(thinkEnd + "</think>".length).trim()

        text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val start = text.indexOf('{')
        if (start < 0) return null

        // Si ricostruisce il testo da qui in poi invece di limitarsi a trovare gli indici di
        // inizio/fine: una virgoletta dentro il contenuto (non scappata dal modello) va scappata
        // qui, altrimenti il JSON restituito non è comunque leggibile da un parser vero anche
        // quando le parentesi risultano bilanciate per caso.
        //
        // Si decide se una '"' chiude davvero la stringa guardando cosa viene SUBITO DOPO
        // (ignorando gli spazi): solo `:`, `,`, `}`, `]` o la fine del testo sono seguiti
        // legittimamente da una '"' di chiusura in un JSON valido. Qualunque altro carattere (una
        // lettera, per dirne una — come nella "P" di "Primo Levi") significa che quella virgoletta
        // fa parte del contenuto copiato dal testo originale, non della sintassi JSON.
        val repaired = StringBuilder()
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> {
                    repaired.append(c)
                    escaped = false
                }
                c == '\\' && inString -> {
                    repaired.append(c)
                    escaped = true
                }
                c == '"' && inString -> {
                    var j = i + 1
                    while (j < text.length && text[j].isWhitespace()) j++
                    val next = text.getOrNull(j)
                    if (next == null || next == ':' || next == ',' || next == '}' || next == ']') {
                        inString = false
                        repaired.append(c)
                    } else {
                        repaired.append('\\').append(c)
                    }
                }
                c == '"' -> {
                    inString = true
                    repaired.append(c)
                }
                inString -> repaired.append(c)
                c == '{' -> {
                    depth++
                    repaired.append(c)
                }
                c == '}' -> {
                    depth--
                    repaired.append(c)
                    if (depth == 0) return repaired.toString()
                }
                else -> repaired.append(c)
            }
        }
        return null
    }
}
