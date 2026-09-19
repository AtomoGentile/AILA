package circolareplus.ai

/**
 * Esito di una generazione di testo libero (non di una classificazione).
 *
 * Esiste perché l'assistente conversazionale ha bisogno di sapere *se* il modello ha risposto,
 * non solo *cosa* ha risposto: [AiClassifier.classifyCircularText] non può fallire — inghiotte
 * l'errore e torna l'euristica a parole chiave — ma per una domanda dell'utente non c'è nessuna
 * euristica che possa inventarsi una risposta sensata. Meglio un errore esplicito, con il motivo
 * vero, che una frase generica indistinguibile da una risposta reale.
 */
sealed class AiTextResult {
    /** [modelLabel] è quello mostrato sotto il messaggio in chat (es. "Google Gemini (gemini-flash-latest)"). */
    data class Success(val text: String, val modelLabel: String) : AiTextResult()

    /** [reason] è pensato per essere mostrato all'utente: deve dire cosa è andato storto. */
    data class Failure(val reason: String) : AiTextResult()
}
