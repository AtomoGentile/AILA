# AILA Assistant: da dove prende le risposte

Documento che spiega come l'assistente sceglie il materiale da mandare al modello, e perché.

## Fonti

Le fonti sono quattro, tutte dentro l'app:

1. **Circolari** già classificate, con il testo integrale estratto dal PDF quando serve
   (`AssistantContext`, `PassageSelector`).
2. **Calendario, bacheca, sondaggi, mappa posti, dati di classe** (`AssistantContext`).
3. **Gita**: documenti e link caricati dal Rappresentante. Per ogni documento si manda il testo
   estratto dal PDF sul telefono al caricamento, con titolo, categoria e data di caricamento
   (`AssistantContext.renderGita`).
4. **Lo stato dell'app** per le domande su scadenze, proposte e capacità (`AssistantAgenda`,
   `AssistantBoard`, `AssistantCapabilities`): sono risposte esatte prodotte dal codice.

## Due modalità

- **Generale** (`AssistantMode.GENERAL`): l'assistente di sempre, dalla Ricerca. Per domande su
  scuola, classe e app risponde solo con i dati del contesto; per il resto usa le sue conoscenze.
- **Solo materiale** (`AssistantMode.GITA_ONLY`): la chat aperta dal pulsante AILA Assistant
  della sezione Gita. Non usa conoscenze generali: l'unica fonte consentita sono il materiale
  della gita e le circolari. Le risposte pronte del codice (scadenze, bacheca) sono disattivate
  in questa modalità, perché rispondono sull'app intera.

In entrambe le modalità, se l'informazione non c'è nelle fonti l'assistente scrive
«Non lo trovo nel materiale disponibile» e suggerisce di chiedere ai rappresentanti. Se due fonti
si contraddicono, lo segnala e privilegia il documento caricato più di recente.

## Perché niente database vettoriale

Il materiale della gita è poco: pochi documenti, ciascuno di qualche pagina. Il contesto del
modello basta per contenerlo. Un database vettoriale aggiungerebbe un servizio da mantenere,
un'indicizzazione da rifare a ogni caricamento e una fonte in più di errore, senza un guadagno
misurabile su questa scala.

Quando il materiale supera il budget del contesto, `PassageSelector` sceglie i passaggi che
rispondono alla domanda, come già accade per le circolari. Il budget è condiviso: in modalità
«solo materiale» la gita prende fino al 70% del contesto, nella modalità generale fino al 40%.

## Dove vive il testo

- Il PDF e il testo estratto stanno su R2 (`gita/{classe}/{voce}/{versione}.pdf` e `.txt`), non in
  D1: un testo può arrivare a 100 MB, troppo per una riga di database.
- Il testo viene estratto sul telefono del Rappresentante (`PdfTextExtractor`) prima dell'invio.
  Il server non chiama nessun modello AI: la chiave resta sul telefono di chi usa l'assistente.

## Chiavi

Le chiamate al modello restano client-side, con la chiave personale dell'utente
(Google AI Studio). Il provider «AI locale» usa lo stesso prompt, ma con la versione compatta
delle regole sulla gita, perché il modello sul telefono ha una finestra più stretta.
