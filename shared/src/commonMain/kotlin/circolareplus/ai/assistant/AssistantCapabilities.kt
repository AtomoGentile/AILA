package circolareplus.ai.assistant

import circolareplus.domain.model.UserRole

/**
 * "Puoi creare un sondaggio?", "come aggiungo un evento?": domande su cosa si puo' fare, a cui
 * risponde il codice spiegando dove farlo nell'app.
 *
 * Il modello le trattava come domande sui dati e rispondeva "Non risulta" (visto con Gemini
 * Nano): la regola "se il dato non c'e', dillo" e' giusta per le circolari e sbagliata qui.
 * L'assistente non esegue azioni, quindi la risposta utile e' sempre la stessa: dove si fa, e
 * chi puo' farlo.
 *
 * Scatta solo se nella domanda c'e' un verbo di creazione e uno degli oggetti qui sotto: il
 * resto ("come faccio a votare il sondaggio?") resta al modello.
 */
internal object AssistantCapabilities {

    private const val MODEL_LABEL = "Guida di AILA"

    private enum class Target { POLL, EVENT, PROPOSAL }

    private val createWords = setOf(
        "crea", "creare", "creo", "creami", "crei", "aggiungi", "aggiungere", "aggiungo",
        "aggiungimi", "organizza", "organizzare", "organizzo", "lancia", "lanciare",
        "inserisci", "inserire", "segna", "segnare", "segnami", "metti", "mettere", "mettimi"
    )

    /** "Fare" conta solo in "fare un/una ...": "cosa devo fare per l'evento?" non e' una richiesta. */
    private val doWords = setOf("fare", "fai", "fammi", "farmi")
    private val articles = setOf("un", "una", "uno")

    fun answer(knowledge: AssistantKnowledge, question: String): AssistantReply? {
        val words = AssistantContext.normalize(question).split(' ').filter { it.isNotBlank() }
        val asksToCreate = words.any { it in createWords } ||
            words.zipWithNext().any { (verb, next) -> verb in doWords && next in articles }
        if (!asksToCreate) return null
        val target = when {
            words.any { it.startsWith("sondagg") } -> Target.POLL
            words.any { it.startsWith("propost") } -> Target.PROPOSAL
            words.any { it == "evento" || it == "eventi" || it == "promemoria" || it == "calendario" } -> Target.EVENT
            else -> return null
        }
        val text = when (target) {
            Target.POLL -> if (knowledge.role == UserRole.REPRESENTATIVE) {
                "Dalla chat non posso crearlo io, ma puoi farlo tu: apri la tab Sondaggi e tocca " +
                    "\"+ Nuovo sondaggio\"."
            } else {
                "Dalla chat non posso crearlo. I sondaggi li crea il Rappresentante di classe " +
                    "dalla tab Sondaggi: quando ne apre uno lo trovi li'. Se ti serve un sondaggio, " +
                    "chiediglielo o proponilo in Bacheca."
            }
            Target.EVENT ->
                "Dalla chat non posso aggiungerlo io, ma puoi farlo dalla tab Calendario con " +
                    "\"Aggiungi evento\": puoi anche scriverlo a parole (es. \"verifica di " +
                    "matematica giovedi' alle 10\") e l'AI compila i campi."
            Target.PROPOSAL ->
                "Dalla chat non posso pubblicarla io, ma puoi farlo tu: apri la tab Classe, poi " +
                    "Bacheca, e tocca il pulsante \"+\" in basso a destra (Nuova proposta)."
        }
        return AssistantReply(text = text, modelLabel = MODEL_LABEL)
    }
}
