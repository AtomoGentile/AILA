package circolareplus.ai.assistant

/**
 * "Ciao", "buongiorno", "grazie": saluti e ringraziamenti senza nessuna domanda dentro.
 *
 * Mandati al modello sul telefono tornavano indietro come un elenco di eventi del calendario:
 * Apple Intelligence, con il contesto pieno di scadenze e nessuna domanda vera, rispondeva a
 * "ciao" con le verifiche della settimana. Un saluto non ha bisogno dei dati, quindi la risposta
 * la da' il codice. Vale solo per frasi fatte *solo* di saluti: "ciao, cosa ho domani?" resta
 * una domanda e va avanti.
 */
internal object AssistantGreeting {

    private const val MODEL_LABEL = "Guida di AILA"

    private val greetingWords = setOf(
        "ciao", "ciaoo", "ciaooo", "buongiorno", "buonasera", "buonanotte", "buon", "buona", "giorno", "notte", "pomeriggio", "sera",
        "salve", "hey", "ehi", "hei", "ehila", "hello", "hi", "yo", "saluti"
    )

    private val thanksWords = setOf("grazie", "grazi", "thanks", "thank", "you", "mille", "tante", "ti", "ringrazio")

    /** Parole che accompagnano un saluto senza trasformarlo in una domanda. */
    private val fillerWords = setOf(
        "aila", "assistant", "assistente", "ok", "okay", "perfetto", "ottimo", "va", "bene",
        "a", "tutti", "come", "stai", "e", "tu", "di", "nuovo", "allora", "molto", "gentile"
    )

    fun answer(question: String): AssistantReply? {
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 6) return null
        if (words.any { it !in greetingWords && it !in thanksWords && it !in fillerWords }) return null
        return when {
            words.any { it == "grazie" || it == "grazi" || it == "thanks" || it == "thank" || it == "ringrazio" } ->
                reply("Di niente! Se ti serve altro, chiedi pure.")
            words.any { it in greetingWords } -> reply(
                if ("stai" in words) {
                    "Ciao! Tutto bene, grazie. Cosa ti serve? Per esempio cosa hai questa settimana, " +
                        "cosa dice una circolare o dove sei seduto in aula."
                } else {
                    "Ciao! Cosa ti serve? Per esempio cosa hai questa settimana, cosa dice una " +
                        "circolare o dove sei seduto in aula."
                }
            )
            else -> null
        }
    }

    private fun reply(text: String) = AssistantReply(text = text, modelLabel = MODEL_LABEL)
}
