# Correzione delle assegnazioni dei sondaggi

Il calcolo ora trova l'assegnazione globale con il punteggio più alto, rispettando le
capienze. Sostituisce il greedy, che poteva assegnare una data negativa anche quando
uno scambio avrebbe migliorato qualcuno senza penalizzare gli altri.

Restano gli stessi punteggi, l'attenuazione dei negativi oltre la quota del 40%, il tetto
del bonus a 300 e gli accrediti di 40/150 prima dell'attenuazione. I voti mancanti restano
neutri nel calcolo, ma sono distinti dai gialli espliciti nella fotografia salvata.
L'ottimo riguarda questo punteggio complessivo: i rossi restano penalità, non divieti.

## Chiusura e concorrenza

Una sola transazione acquisisce il calcolo, inserisce le assegnazioni, aggiorna i bonus
e salva la fotografia originale. Le altre richieste non possono ripetere gli effetti.
Non è presente un lock provvisorio che possa restare bloccato dopo un errore.

La lettura iniziale degli input è coerente e viene confrontata nuovamente dentro la
transazione finale. Se voti, destinatari, invii, scadenza o bonus cambiano nel frattempo,
il calcolo riparte dagli input aggiornati. Questo protegge anche due sondaggi diversi
della stessa materia che aggiornano contemporaneamente i bonus degli stessi studenti.

Le scritture dei voti e gli invii controllano la chiusura nella stessa istruzione SQL,
così una richiesta già partita non può cambiare i voti dopo la fotografia del risultato.

## Verifica dei risultati

`GET /api/polls/:id/calculation` è riservato ai rappresentanti della classe. Restituisce:

- versione dell'algoritmo e parametri;
- voti, date, destinatari e bonus iniziali;
- assegnazione originale, peso dei negativi e bonus finali;
- scambi successivi in ordine di registrazione;
- numero di partecipanti rimossi dalla fotografia per cancellazione dell'account.

Gli scambi non sovrascrivono il risultato originale. Una conferma duplicata non può
applicare due volte lo stesso scambio. La cancellazione dell'account elimina anche i
suoi voti e bonus dalla fotografia e rimuove gli eventi di scambio che lo riguardano.

## Rilascio

Applicare `backend/migrations/026_poll_calculations.sql` prima di distribuire il Worker.
La migrazione è additiva e ripetibile. Marca come già calcolate le griglie che hanno
risultati precedenti, senza modificarne assegnazioni o bonus e senza inventare dati
storici mancanti. Per quei sondaggi `snapshotAvailable` resta `false`.

Il calendario di Biologia già pubblicato non viene ricalcolato né corretto dal rilascio.
I tre scambi individuati nell'indagine restano una decisione separata, così come la
riconciliazione dei bonus già attribuiti.

## Verifica

I test includono il controesempio del greedy, le 4096 combinazioni 2x2 di colori e bonus,
capienze multiple e catene di spostamenti, chiusure simultanee manuali e automatiche,
aggiornamenti concorrenti tra griglie, rollback, voti in volo, autorizzazioni,
conferme duplicate degli scambi e cancellazione degli account.

Il nuovo ottimizzatore è stato eseguito anche sul caso anonimo di Biologia: con i voti
salvati e bonus iniziali posti a zero trova 20 verdi e nessun rosso. Questo è un controllo
sui voti reali, non una ricostruzione esatta dei bonus storici, che non erano registrati.

## Anteprima nell'app

Il rappresentante può aprire **Sondaggi → Storico → Vedi risultati → Confronta con il
nuovo algoritmo**. L'anteprima mostra i conteggi prima/dopo per ogni colore, i voti
mancanti e, per ogni studente, data attuale, nuova data e voto su entrambe. Si possono
vedere solo gli spostamenti oppure tutti gli studenti, inclusi eventuali peggioramenti.

L'endpoint `GET /api/polls/:id/assignments/preview` è riservato al rappresentante della
classe. Esegue solo letture e usa la stessa funzione di pianificazione del calcolo reale.
Non pubblica assegnazioni, non aggiorna il calendario e non accredita o consuma bonus.

Se la fotografia originale completa è disponibile, il confronto può usare i voti e i
bonus iniziali (`basis=ORIGINAL`). In alternativa si possono confrontare i soli voti
(`basis=VOTES_ONLY`). Per i sondaggi precedenti alla fotografia, come Biologia, la modalità
predefinita è solo voti: i bonus correnti, già modificati dai risultati, non vengono
spacciati per bonus storici. Il limite è visibile nell'anteprima.

Il confronto riguarda soltanto gli studenti ancora assegnati al sondaggio: non aggiunge
nuovi iscritti. Quando la cancellazione di un account rende incompleta la fotografia,
la modalità con bonus iniziali viene disabilitata. Gli scambi già accettati sono indicati;
a parità di punteggio ottimo si mantiene il maggior numero possibile di date attuali.
