# AILA

**AILA** (ex "Circolare+") è l'app per la vita di classe: circolari spiegate dall'AI, calendario condiviso, bacheca
delle proposte, sondaggi, mappa dei posti in aula e notifiche in tempo reale. Per Android e iPhone/iPad.

> **Versione beta 0.0.1** — l'app è in fase di prova: qualcosa potrebbe non funzionare come previsto.

---

## Cosa puoi fare

| | |
|---|---|
| **Circolari** | Ogni circolare nuova della scuola arriva con un riassunto, una categoria per la tua classe (*Ti riguarda / Potenziale interesse / Non ti riguarda*) e le scadenze già estratte. Sei avvisato subito con una notifica. |
| **AILA Assistant e Ricerca** | Fai una domanda in italiano e ricevi una risposta basata solo sui dati di AILA (circolari, calendario, bacheca, sondaggi, mappa posti), con le fonti da aprire. Le conversazioni restano in cronologia. |
| **Calendario** | Eventi della scuola e della classe, anche creati dalle scadenze delle circolari. L'orario si sceglie in ore di lezione (dalla 1ª alla 6ª). |
| **Bacheca proposte** | Proponi, vota e commenta, anche in forma anonima. L'identità si svela solo con l'approvazione di più persone della classe, in caso di abuso. |
| **Sondaggi interrogazioni** | Prenota le date delle interrogazioni con un sistema di voti equo, pensato per non poter essere aggirato. Lo storico lo vede tutta la classe. |
| **Sondaggi a classifica** | Il Rappresentante propone fino a 10 opzioni, ognuno le mette in ordine e la classifica si aggiorna da sola. |
| **Mappa posti** | Il Rappresentante sceglie tra 3 disposizioni dei banchi che tengono conto di affinità, aiuto tra compagni, rumore e altezza. Può anche personalizzare file e posti per fila. |
| **Preferenze sociali** | Il gradimento reciproco tra compagni si raccoglie solo quando il Rappresentante apre la votazione, mai di nascosto. |
| **Più classi** | I dati di ogni classe sono separati da quelli delle altre. |

Due stili grafici a scelta, **Glass** e **Material**, e rispetto dell'impostazione "Riduci movimento".

---

## Installazione

Scarica l'ultima versione dalla pagina **Releases** del repository.

- **Android**: scarica `AILA.apk` dal telefono e aprilo. Se richiesto, consenti l'installazione da fonti sconosciute.
- **iPhone / iPad**: scarica `AILA.ipa` e aprila con [SideStore](https://sidestore.io), che la firma con il tuo Apple ID.

Per registrarti serve il codice della tua classe; i Rappresentanti ricevono un codice personale.

---

## Servizi usati

AILA usa **Firebase** (notifiche) e **Cloudflare** (server e dati).

---

## Privacy

L'AI riceve solo i PDF pubblici delle circolari, mai i tuoi dati personali. Le preferenze sociali e le valutazioni
dei compagni non vengono mai inviate all'AI. Se preferisci, puoi usare l'AI sul tuo telefono.
L'informativa completa è in [PRIVACY.md](PRIVACY.md).
