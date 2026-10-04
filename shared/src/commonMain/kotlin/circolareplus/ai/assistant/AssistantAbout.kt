package circolareplus.ai.assistant

/**
 * "Che cos'e' AILA?", "e cosa fa?", "cosa sai fare?": domande sull'app e sull'assistente stessi.
 *
 * Il modello sul telefono (Gemini Nano) non ha nessun dato su AILA e rispondeva con una frase
 * vuota ("AILA e' l'app scolastica."), uguale anche al seguito "e cosa fa?". Le risposte vere le
 * sa il codice: cosa c'e' nell'app e cosa si puo' chiedere. Come [AssistantCapabilities], vale
 * solo per frasi brevi e inequivocabili: "cosa fa il Rappresentante?" resta al modello.
 */
internal object AssistantAbout {

    private const val MODEL_LABEL = "Guida di AILA"

    private val aboutPhrases = setOf(
        "cos e", "che cos e", "cosa e", "che cosa e", "chi e", "cosa fa", "che fa", "che cosa fa",
        "a cosa serve", "a che serve", "come funziona", "cos e aila", "che cos e aila",
        "cosa e aila", "che cosa e aila", "cosa fa aila", "a cosa serve aila", "come funziona aila",
        "spiegami aila", "parlami di aila", "presentati"
    )

    private val canDoPhrases = setOf(
        "cosa sai fare", "che cosa sai fare", "cosa puoi fare", "che cosa puoi fare",
        "cosa posso chiederti", "cosa posso chiedere", "cosa ti posso chiedere",
        "come puoi aiutarmi", "in cosa puoi aiutarmi", "come mi puoi aiutare",
        "in cosa mi puoi aiutare", "che funzioni hai", "cosa sai fare aila", "aiuto"
    )

    fun answer(question: String, history: List<AssistantMessage>): AssistantReply? {
        // Senza accenti e punteggiatura; un "e" iniziale ("e cosa fa?") e' solo un seguito.
        var normalized = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
            .joinToString(" ")
        if (normalized.startsWith("e ")) normalized = normalized.removePrefix("e ")
        // Anche "ma", "ok", "quindi", "allora" davanti a un seguito.
        for (lead in listOf("ma ", "quindi ", "allora ", "ok ")) normalized = normalized.removePrefix(lead)
        // "Aila" e "assistant" non cambiano la domanda.
        val bare = normalized.replace(" aila", "").replace("aila ", "").trim()

        val words = normalized.split(' ')
        return when {
            normalized in canDoPhrases || bare in canDoPhrases -> reply(CAN_DO)
            normalized == "presentati" -> reply(ABOUT)
            // "Che cos'e' AILA?", "cosa fa AILA?": nominata, quindi inequivocabile.
            "aila" in words && (bare in aboutPhrases || normalized in aboutPhrases) -> reply(ABOUT)
            // "e cosa fa?", "come funziona?": senza AILA nella frase hanno senso solo come
            // seguito di una risposta su AILA, non nel vuoto.
            bare in aboutPhrases && lastAnswerWasAbout(history) -> reply(ABOUT)
            else -> null
        }
    }

    private fun lastAnswerWasAbout(history: List<AssistantMessage>): Boolean {
        val last = history.lastOrNull { it.author != AssistantAuthor.USER && !it.isError } ?: return false
        return last.modelLabel == MODEL_LABEL || "AILA" in last.text
    }

    private fun reply(text: String) = AssistantReply(text = text, modelLabel = MODEL_LABEL)

    private val ABOUT = """
        AILA è l'app di classe della tua scuola: tiene in un posto solo quello che serve.
        • Circolari: le legge, le riassume e ti dice quali ti riguardano.
        • Calendario: verifiche, scadenze, pagamenti e uscite della classe.
        • Bacheca: le proposte della classe, su cui votare e commentare.
        • Sondaggi: per organizzare verifiche e interrogazioni.
        • Mappa posti: dove sei seduto in aula e con chi.
        Io sono AILA Assistant: rispondo alle domande su tutto questo cercando nei dati dell'app, e per il resto uso le mie conoscenze.
    """.trimIndent()

    private val CAN_DO = """
        Posso cercare per te nei dati di AILA e spiegarti le cose con parole semplici. Per esempio:
        • "Cosa devo fare questa settimana?" o "Ci sono scadenze in arrivo?"
        • "Riassumimi le ultime circolari" o "Cosa dice la circolare 12?"
        • "Quali proposte della bacheca sono aperte?"
        • "Dove sono seduto in aula?"
        Per lo studio e le domande generali rispondo con le mie conoscenze. Non posso creare eventi o sondaggi dalla chat, ma ti dico dove si fanno nell'app.
    """.trimIndent()
}
