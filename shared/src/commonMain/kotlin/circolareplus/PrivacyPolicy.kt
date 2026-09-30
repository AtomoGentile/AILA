package circolareplus

/**
 * Testo dell'informativa mostrato in Impostazioni → Privacy. Riassume PRIVACY.md (radice del repo):
 * se cambia uno, va aggiornato anche l'altro.
 */
object PrivacyPolicy {
    /** Coppie (titolo, testo) nell'ordine in cui si mostrano. */
    val sections: List<Pair<String, String>> = listOf(
        "Cos'è AILA" to "Un\'app per la tua classe: circolari con riassunto, calendario, bacheca, sondaggi " +
            "e mappa dei posti. Progetto scolastico non commerciale, senza pubblicità né profilazione. " +
            "Titolare: Simone Bianchin. Per qualsiasi richiesta chiedimi di persona (sono un tuo compagno) " +
            "o scrivi sulla pagina del progetto: github.com/AtomoGentile/AILA.",
        "Quali dati raccogliamo" to "Nome, cognome, nome utente e classe; la password (sul server resta solo " +
            "un\'impronta, mai la password); l\'altezza a scaglioni di 5 cm e le preferenze sui compagni, per " +
            "la mappa dei posti; i voti di sondaggi e proposte; eventi di calendario, proposte e commenti; " +
            "il codice del telefono per le notifiche. Non raccogliamo posizione, contatti, foto, microfono, " +
            "e-mail o telefono, né usiamo statistiche o pubblicità.",
        "Le valutazioni del Rappresentante" to "Le valutazioni riservate (didattica e comportamento) le vede solo " +
            "il Rappresentante e non vengono mai inviate a servizi di intelligenza artificiale. Chi ha votato " +
            "cosa nelle preferenze non è visibile al Rappresentante.",
        "Chi tratta i dati" to "Cloudflare ospita server e database. Google Firebase recapita le notifiche. " +
            "Google Gemini riassume le circolari (gli si invia il PDF della circolare, non dati personali " +
            "degli studenti). Con la tua chiave personale di Google AI Studio le richieste partono dal tuo " +
            "telefono e valgono le condizioni di Google; l\'assistente invia a Gemini le tue domande e il " +
            "testo delle circolari. Con l\'AI locale non esce nulla dal telefono.",
        "Cosa resta sul telefono" to "Chiave AI personale, cronologia dell\'assistente, campanella delle " +
            "notifiche e copia offline restano solo sul telefono e si cancellano con \"Esci\". Sono esclusi " +
            "dai backup.",
        "Quanto li conserviamo" to "Finché esiste il tuo account. Dal Profilo puoi eliminarlo: spariscono " +
            "account, profilo, preferenze, voti e codice per le notifiche. Eventi di calendario e analisi " +
            "delle circolari restano, senza il tuo nome.",
        "I tuoi diritti" to "Puoi chiedere accesso, correzione, cancellazione, limitazione e portabilità dei " +
            "dati e opporti al trattamento (artt. 15-22 GDPR). Puoi rivolgerti al Garante per la protezione " +
            "dei dati personali (garanteprivacy.it).",
        "A chi è rivolta" to "Agli studenti di scuola superiore. Non è destinata ai minori di 14 anni."
    )
}
