package circolareplus.ai

/**
 * Marcatore che introduce il testo di un allegato PDF nel testo passato ai classificatori (vedi
 * `MainAppShell.classifyCircularIfNeeded`, che lo usa per concatenare il testo del documento
 * principale con quello di ogni allegato PDF).
 */
internal const val ATTACHMENT_TEXT_MARKER = "\n\n--- Allegato: "

/**
 * Taglia [text] a [maxChars] caratteri ripartendo il budget fra il documento principale e i suoi
 * allegati (individuati da [ATTACHMENT_TEXT_MARKER]) invece di prendere semplicemente i primi
 * [maxChars] caratteri.
 *
 * Un `.take(maxChars)` semplice taglia sempre dalla coda: con un documento principale che da solo
 * supera già il budget (comune, es. una circolare di più pagine), gli allegati — che stanno in
 * fondo al testo concatenato — sparirebbero SEMPRE, esattamente il caso che li rende invisibili
 * all'AI e che questa funzione esiste per evitare.
 *
 * Il documento principale riceve metà del budget (o tutto il suo testo se più corto, lasciando il
 * resto agli allegati); quel che resta è diviso in parti uguali fra gli allegati, nell'ordine in
 * cui compaiono.
 */
internal fun truncatePdfTextForAi(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text

    val markerIndex = text.indexOf(ATTACHMENT_TEXT_MARKER)
    if (markerIndex < 0) return text.take(maxChars)

    val mainText = text.substring(0, markerIndex)
    val attachmentsText = text.substring(markerIndex)

    // Ogni allegato inizia con lo stesso marcatore: si spezza mantenendolo come prefisso di ogni
    // pezzo, così il modello vede comunque "--- Allegato: <nome> ---" come intestazione.
    val attachmentParts = attachmentsText
        .split(ATTACHMENT_TEXT_MARKER)
        .drop(1) // il primo pezzo è vuoto: attachmentsText inizia proprio col marcatore
        .map { ATTACHMENT_TEXT_MARKER + it }

    val mainBudget = (maxChars / 2).coerceAtMost(mainText.length)
    val remaining = maxChars - mainBudget
    val perAttachment = if (attachmentParts.isNotEmpty()) remaining / attachmentParts.size else 0

    val truncatedMain = mainText.take(mainBudget)
    val truncatedAttachments = attachmentParts.joinToString("") { it.take(perAttachment) }

    return truncatedMain + truncatedAttachments
}
