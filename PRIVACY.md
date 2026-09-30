# AILA — Informativa sulla privacy

Ultimo aggiornamento: 30 settembre 2026

AILA è un'app per le classi di una scuola superiore: circolari con riassunto, calendario di
classe, bacheca delle proposte, sondaggi e mappa dei posti. È un progetto scolastico non
commerciale, senza pubblicità e senza profilazione.

**Titolare del trattamento:** Simone Bianchin, sviluppatore di AILA.
**Contatto per qualsiasi richiesta:** di persona (sono un tuo compagno di classe) oppure dalla
pagina del progetto su GitHub (github.com/AtomoGentile/AILA).

> Questa informativa descrive ciò che l'app fa davvero, ricavato dal codice. Se qualcosa cambia,
> viene aggiornata qui prima del rilascio.

## Quali dati raccogliamo

| Dato | A cosa serve |
|---|---|
| Nome, cognome, nome utente, classe | Riconoscerti e mostrarti ai compagni della tua classe |
| Password | Accedere. Sul server resta solo un'impronta (hash) della password, mai la password |
| Altezza (a scaglioni di 5 cm) | Calcolare la mappa dei posti |
| Preferenze sui compagni (da −2 a +2) | Mappa dei posti; se il Rappresentante non le apre, non vengono raccolte. Il Rappresentante non vede chi ha votato cosa |
| Voti nei sondaggi e nelle proposte | Mostrare i risultati; le proposte possono essere anonime |
| Eventi di calendario, proposte, commenti | Le funzioni di classe |
| Valutazioni riservate del Rappresentante (didattica, comportamento) | Mappa dei posti. Le vede solo il Rappresentante e **non vengono mai inviate a servizi di intelligenza artificiale** |
| Token del dispositivo per le notifiche | Inviarti le notifiche push |
| Tentativi di accesso (contatore temporaneo) | Bloccare i tentativi ripetuti di indovinare una password |

Non raccogliamo posizione, contatti, foto, microfono, e-mail o numero di telefono. Non usiamo
strumenti di statistica o pubblicità.

## Dove sono i dati e chi li tratta

- **Cloudflare** (Workers, D1, R2): ospita il server e il database. I dati di tutte le classi
  stanno lì, separati per classe.
- **Google Firebase Cloud Messaging**: recapita le notifiche push. Riceve il token del dispositivo
  e il testo della notifica.
- **Google Gemini**: riassume le circolari della scuola. Al servizio si invia il PDF della
  circolare (documento pubblico della scuola), non dati personali degli studenti. Se inserisci la tua
  chiave personale di Google AI Studio, le richieste dal telefono partono con la tua chiave e sono
  soggette alle condizioni di Google. L'assistente dell'app manda a Gemini le tue domande e il testo
  delle circolari, non le valutazioni dei compagni. In alternativa puoi usare l'AI locale, che
  funziona sul telefono senza inviare nulla.
- **Registro elettronico della scuola**: l'app legge le circolari pubblicate dalla scuola. Non
  accede ai dati di nessuno studente.

Alcuni di questi fornitori possono trattare dati fuori dall'Unione Europea, con le garanzie
previste dalle rispettive condizioni.

## Cosa resta sul tuo telefono

La chiave AI personale, la cronologia dell'assistente, la campanella delle notifiche e la copia
offline delle circolari restano **solo sul telefono**. Uscendo dall'account (“Esci”) vengono
cancellati. L'app esclude questi dati dai backup del telefono.

## Quanto conserviamo i dati

Finché il tuo account esiste. Quando lo elimini (Profilo → elimina account) spariscono account,
profilo, preferenze, voti e token. Gli eventi di calendario e le analisi delle circolari restano,
senza il tuo nome come autore. I contatori dei tentativi di accesso durano pochi minuti.

## I tuoi diritti

Puoi chiedere accesso, correzione, cancellazione, limitazione e portabilità dei tuoi dati, e
opporti al trattamento (artt. 15–22 GDPR). Puoi eliminare l'account da solo dall'app; per il resto
usa il contatto qui sopra. Hai anche diritto di reclamo al Garante per la protezione dei dati
personali (garanteprivacy.it).

## A chi è rivolta

AILA è pensata per gli studenti di scuola superiore, che hanno almeno 14 anni. Non è destinata
a chi ha meno di 14 anni.

## Sicurezza

Connessioni cifrate (HTTPS), password conservate come hash, token di accesso che scadono, limite ai
tentativi di accesso, dati di ogni classe visibili solo a quella classe.

## Modifiche

Ogni modifica sostanziale viene scritta qui, con la nuova data in cima.
