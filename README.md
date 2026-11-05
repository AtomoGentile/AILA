# AILA

**AILA** (ex "Circolare+") è l'app per la vita di classe: circolari spiegate dall'AI, calendario condiviso, bacheca
delle proposte, sondaggi, mappa dei posti in aula e notifiche in tempo reale. Per Android e iPhone/iPad.

> **Versione 2.0.0-beta.1**

---

## Cosa puoi fare

| | |
|---|---|
| **Circolari** | Ogni circolare nuova della scuola arriva con un riassunto, una categoria per la tua classe (*Ti riguarda / Potenziale interesse / Non ti riguarda*) e le scadenze già estratte. Sei avvisato subito con una notifica. |
| **AILA Assistant e Ricerca** | Fai una domanda in italiano e ricevi una risposta con le fonti di AILA da aprire. Con ACA attiva puoi creare, modificare o eliminare impegni nel calendario interno: ogni azione richiede anteprima e conferma. Le conversazioni restano in cronologia. |
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
- **iPhone / iPad**: con [SideStore](https://sidestore.io) installato, apri dal telefono
  [aila-scuola.pages.dev/sidestore](https://aila-scuola.pages.dev/sidestore) e tocca **Apri in SideStore**: si aggiunge la
  source di AILA e da lì installi l'app e ricevi gli aggiornamenti. In alternativa scarica `AILA.ipa` e aprila con SideStore,
  che la firma con il tuo Apple ID.

Per registrarti serve il codice della tua classe; i Rappresentanti ricevono un codice personale.

### Aggiornamenti

- **Android**: l'app controlla da sola le release di GitHub (a ogni apertura, e al ritorno in primo piano al massimo una volta l'ora; a mano da
  *Impostazioni → Aggiornamenti*). Se ce n'è una più recente propone di scaricarla e installarla senza uscire
  dall'app. La prima volta Android chiede di consentire ad AILA di installare app.
- **iPhone / iPad**: gli aggiornamenti arrivano da SideStore (vedi sopra).

**Per pubblicare un aggiornamento**: crea su GitHub una release con un tag di versione più alto di quello
installato, nel formato `vX.Y.Z` (es. `v1.0.1`), non come bozza né pre-release. La CI compila l'APK con quella
versione e lo allega alla release come `AILA.apk` dopo qualche minuto: da quel momento l'app lo propone.
Le note della release compaiono nella finestra di aggiornamento (la sezione «Installazione» viene omessa).

Le installazioni **stabili** ricevono solo stabili. Chi installa volontariamente una **beta
Android** riceve le beta successive e può passare alla stabile definitiva della stessa
versione: `1.6.3-beta.2 → 1.6.3`. Le beta non sono proposte agli utenti stabili e non si
propongono versioni più vecchie di quella installata. Il canale è indicato nelle Impostazioni.
Su iOS il feed SideStore resta stabile; le IPA beta si installano e aggiornano manualmente
dalla relativa pre-release. I tag beta usano il formato `vX.Y.Z-beta.N` con `N >= 1`.


---

## Servizi usati

AILA usa **Firebase** (notifiche) e **Cloudflare** (server e dati).

---

## Privacy

L’AI elabora le circolari e i contenuti che scegli per i servizi attivati. L’elaborazione cloud richiede
il relativo consenso; i motori locali restano disponibili sui dispositivi supportati. Le preferenze sociali
e le valutazioni dei compagni sono escluse dal contesto ACA.
L'informativa completa è in [PRIVACY.md](PRIVACY.md).

### ACA, Educazione tra pere e nuova home

AI e ACA sono opzionali: **Impostazioni → AI** raccoglie attivazione, singoli servizi, provider e informativa. Educazione tra pere aggiunge discussioni di classe con pseudonimi, risposte, allegati e assistenza AI esplicita. Puoi allegare documenti o vocali anche dal menu della chat classica, scegliere il contesto e preparare eventi da confermare. Le proposte automatiche dalle conversazioni sono un servizio opzionale: la scheda è condivisa e la prima conferma aggiorna lo stato per tutti. ACA si apre anche tenendo premuto per 2 secondi in un’area libera di qualsiasi schermata; il gesto si può disattivare nelle impostazioni AI. Le copie dei file sul dispositivo restano fino alla cancellazione manuale, mentre il download automatico si sceglie nelle impostazioni offline. La home presenta la nuova AILA e prevede un video opzionale; l’indicatore animato delle circolari in analisi compare soltanto in Circolari.

Configurazione, migrazione, conservazione 90/7 giorni e verifiche: [ACA_IMPLEMENTATION.md](docs/ACA_IMPLEMENTATION.md).
