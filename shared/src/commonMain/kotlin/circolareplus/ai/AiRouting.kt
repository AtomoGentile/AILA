package circolareplus.ai

/**
 * Decide se, per una circolare troppo lunga per il modello locale selezionato, conviene
 * anteporre il cloud invece di lasciar troncare il testo in locale.
 *
 * Non introduce una nuova soglia: riusa [LocalAiModel.maxPromptChars], che è già calibrato
 * per modello (dipende da [LocalAiModel.maxInputTokens], che varia da motore a motore) e più
 * prudente di un numero fisso di token per tutti. Sopra quella soglia [LocalAiClassifier]
 * troncherebbe comunque il testo e proverebbe a classificare solo la parte iniziale — utile
 * come ultima rete, ma silenzioso: se il cloud è configurato e può leggere il documento
 * intero, va provato per primo.
 *
 * `false` quando non c'è un modello locale selezionato (niente da evitare) o quando non è
 * configurata una chiave cloud (anteporre un provider che fallirebbe comunque non aiuta:
 * l'ordine attuale, con l'euristica come ultima rete, resta la scelta migliore).
 */
fun shouldPreferCloudForLength(
    textLength: Int,
    localModel: LocalAiModel?,
    hasCloudKey: Boolean
): Boolean {
    if (localModel == null || !hasCloudKey) return false
    return textLength > localModel.maxPromptChars
}
