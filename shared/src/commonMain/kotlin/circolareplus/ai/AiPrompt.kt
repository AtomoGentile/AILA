package circolareplus.ai

/** Le due parti di un prompt: le istruzioni fisse e la richiesta vera e propria. */
data class AiPrompt(val systemPrompt: String, val userPrompt: String) {
    val totalChars: Int get() = systemPrompt.length + userPrompt.length
}

/**
 * Un prompt che si costruisce su misura dello spazio che il motore gli lascia.
 *
 * Nasce da un fallimento concreto: l'assistente costruiva **un solo** prompt e lo passava alla
 * catena dei provider, cosi' com'era, a tutti e due. Per Gemini andava bene — ha una finestra da
 * centinaia di migliaia di token — ma quando il cloud cadeva e subentrava il modello locale, lo
 * stesso prompt arrivava a un motore che accetta 4096 token in ingresso e moriva prima ancora di
 * partire: `Input token ids are too long. Exceeding the maximum number of tokens allowed:
 * 4471 >= 4096`. La riserva non era una riserva: era un secondo fallimento garantito.
 *
 * Con questa interfaccia il prompt non e' piu' una stringa decisa da chi chiama, ma qualcosa che
 * ogni motore si fa dare della misura giusta per se'. La catena passa lo stesso builder a
 * entrambi: il cloud riceve il contesto largo, il modello locale la versione stretta, senza che
 * nessuno dei due debba sapere dell'altro.
 */
fun interface AiPromptBuilder {
    /**
     * [maxChars] e' lo spazio **totale** (istruzioni + richiesta) che questo motore puo'
     * accettare. Chi implementa deve stare sotto quel tetto, non limitarsi a puntarci.
     */
    fun build(maxChars: Int): AiPrompt
}
