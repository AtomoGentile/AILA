package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification

/**
 * Prova il provider scelto e, se quello non ce la fa, l'altro.
 *
 * Serve perché i due provider falliscono in situazioni opposte e complementari: Google AI Studio
 * cade senza rete, con la quota gratuita esaurita o con una chiave scaduta; l'AI locale cade se
 * il modello non è stato scaricato o se il telefono è troppo carico. Prima l'unica riserva era
 * l'euristica a parole chiave, che mostra sempre la stessa frase generica; con la catena, chi ha
 * configurato entrambi vede un riassunto vero in tutti i casi tranne quello in cui falliscono
 * tutti e due.
 *
 * Il segnale di fallimento è [CircularAiClassification.isFallback]: i classificatori non lanciano
 * eccezioni — inghiottono l'errore e restituiscono l'euristica — quindi senza quel flag da qui
 * non ci sarebbe modo di distinguere una classificazione riuscita da una di ripiego.
 */
class ChainedAiClassifier(
    private val primary: AiClassifier,
    private val secondary: AiClassifier,
    /**
     * Se `false`, un fallimento del primario NON prova il secondario: si tiene subito il
     * risultato di ripiego del primario (euristica a parole chiave, istantanea).
     *
     * Serve a chi chiama in batch (il ciclo di classificazione in sottofondo di
     * `MainAppShell`): quando il primario e' il cloud e il secondario e' il modello locale,
     * ogni fallimento del cloud (tipicamente un 429 a raffica) scaricava il lavoro sul motore
     * on-device — decine di secondi di CPU/GPU a pieno regime, ripetuti per ogni circolare
     * rimasta da classificare, ed e' la causa piu' diretta del telefono che scalda anche quando
     * l'utente ha scelto il provider cloud. Chi chiama puo' disattivare l'escalation dopo che
     * si e' vista qualche volta di fila, e lasciarla per l'apertura manuale di una singola
     * circolare (dove l'attesa e' accettata perche' e' l'utente a chiederla).
     */
    private val escalateToSecondary: Boolean = true
) : AiClassifier {

    private companion object {
        /**
         * Sotto questa soglia il testo estratto dal PDF è troppo poco per contare come "letto
         * davvero" — tipicamente una circolare scansionata o firmata a mano, senza livello di
         * testo. Prima si mandava comunque a Gemini/al modello locale: nel migliore dei casi
         * rispondevano con un errore poco chiaro, nel peggiore inventavano un riassunto plausibile
         * dal solo titolo, indistinguibile in app da un'analisi vera. Qui si salta subito la
         * chiamata e si spiega il motivo reale, senza sprecare quota/tempo su un testo che non
         * può produrre nulla di utile.
         */
        const val MIN_EXTRACTABLE_TEXT_LENGTH = 40
    }

    override suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String
    ): CircularAiClassification {
        if (pdfText.trim().length < MIN_EXTRACTABLE_TEXT_LENGTH) {
            return HeuristicClassification.classify(
                circularNumber = circularNumber,
                title = circularTitle,
                text = pdfText,
                failureReason = "il PDF non contiene testo estraibile, probabile scansione o " +
                    "documento firmato senza livello di testo",
                notConfiguredMessage = "Nessun testo estratto dal PDF."
            )
        }

        val first = primary.classifyCircularText(
            circularNumber, circularTitle, pdfText, studentContext
        )
        if (!first.isFallback || !escalateToSecondary) return first

        val second = secondary.classifyCircularText(
            circularNumber, circularTitle, pdfText, studentContext
        )
        // Se anche il secondo ripiega si tiene il risultato del primo: il suo messaggio spiega
        // il motivo del provider che l'utente ha effettivamente scelto, che è quello su cui può
        // intervenire.
        return if (second.isFallback) first else second
    }

    override suspend fun parseEventPrompt(userPrompt: String): EventDraft {
        val first = primary.parseEventPrompt(userPrompt)
        if (first.title.isNotBlank()) return first

        val second = secondary.parseEventPrompt(userPrompt)
        return if (second.title.isNotBlank()) second else first
    }

    /**
     * Implementazione di [AiClassifier.generateAnswer]: si prova il primario e, se fallisce, il
     * secondario — esattamente come per la classificazione, ma con un segnale di fallimento
     * molto piu' netto, perche' qui il fallimento e' un tipo ([AiTextResult.Failure]) e non un
     * flag su un risultato plausibile.
     *
     * A differenza della classificazione l'escalation non e' disattivabile: una domanda
     * dell'assistente la fa sempre l'utente di persona, una alla volta, quindi non esiste il
     * caso del ciclo in sottofondo che scarica decine di generazioni sul motore on-device.
     *
     * Se falliscono entrambi si riportano tutti e due i motivi: quasi sempre sono diversi
     * (quota finita da una parte, modello non scaricato dall'altra) e sapere quale dei due
     * sistemare e' l'unica informazione utile.
     */
    override suspend fun generateAnswer(prompt: AiPromptBuilder): AiTextResult {
        val first = primary.generateAnswer(prompt)
        if (first is AiTextResult.Success) return first

        // Lo stesso builder, non lo stesso prompt: ognuno dei due se lo fa costruire della
        // misura che regge. E' quello che rende la riserva una riserva vera anche quando il
        // primario e' il cloud con il suo contesto largo.
        val second = secondary.generateAnswer(prompt)
        if (second is AiTextResult.Success) return second

        val firstReason = (first as AiTextResult.Failure).reason
        val secondReason = (second as AiTextResult.Failure).reason
        return AiTextResult.Failure("$firstReason\nRiserva: $secondReason")
    }

    /** Riporta l'esito di entrambi: il pulsante di prova deve dire cosa funziona e cosa no. */
    override suspend fun testConfiguration(): String {
        val primaryResult = primary.testConfiguration()
        val secondaryResult = secondary.testConfiguration()
        return "$primaryResult\nRiserva: $secondaryResult"
    }
}
