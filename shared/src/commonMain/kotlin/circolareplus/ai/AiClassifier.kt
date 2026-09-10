package circolareplus.ai

import circolareplus.domain.model.CircularAiClassification

/**
 * Interfaccia comune per i classificatori AI. Consente di usare provider diversi
 * (Google AI Studio in rete, modello locale sul telefono) con la stessa logica di
 * classificazione, e di incatenarli fra loro (vedi [ChainedAiClassifier]).
 */
interface AiClassifier {
    /**
     * Classifica il testo di una circolare e ritorna badge, riassunto e deadline.
     * Se la classificazione fallisce, ritorna una classificazione euristica di fallback.
     */
    suspend fun classifyCircularText(
        circularNumber: Int,
        circularTitle: String,
        pdfText: String,
        studentContext: String = "Studente di scuola superiore, classe 4^ CSA"
    ): CircularAiClassification

    /**
     * Parsa il testo libero dell'utente e genera una bozza di evento calendario.
     * Se il parsing fallisce, ritorna una bozza vuota con categoria ALTRO.
     */
    suspend fun parseEventPrompt(userPrompt: String): EventDraft

    /**
     * Testa la configurazione (API key, autenticazione, ecc.) e ritorna un messaggio
     * leggibile su come è andata. Usato dal pulsante "Prova la chiave" nelle Impostazioni.
     */
    suspend fun testConfiguration(): String
}
