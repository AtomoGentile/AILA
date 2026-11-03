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

Il rappresentante può aprire **Sondaggi → Storico → Interrogazioni → Confronta con il
nuovo algoritmo**, direttamente dalla card del sondaggio. L'anteprima mostra i conteggi prima/dopo per ogni colore, i voti
mancanti e, per ogni studente, data attuale, nuova data e voto su entrambe. Si possono
vedere solo gli spostamenti oppure tutti gli studenti, inclusi eventuali peggioramenti.

L'endpoint `GET /api/polls/:id/assignments/preview` è riservato al rappresentante della
classe. Esegue solo letture e usa la stessa funzione di pianificazione del calcolo reale.
Non pubblica assegnazioni, non aggiorna il calendario e non accredita o consuma bonus.

Se la fotografia originale completa è disponibile, il confronto usa i voti e i
bonus iniziali (`basis=ORIGINAL`). Sono disponibili anche una simulazione con i bonus
attuali (`basis=CURRENT_BONUSES`) e il confronto dei soli voti (`basis=VOTES_ONLY`).
Ogni studente mostra il bonus usato. Per i sondaggi precedenti alla fotografia, come
Biologia, l'app usa i bonus attuali con un avviso esplicito: non sono i bonus storici.

L'app richiede `basis=AUTO`: riceve tutti i confronti calcolati sulla stessa lettura
coerente e mantiene quei bonus fissi mentre si cambia modalità. Solo Aggiorna o la
riapertura del confronto genera una nuova anteprima. AUTO preferisce i bonus iniziali
quando disponibili, altrimenti quelli attuali. Le richieste senza basis dei client
precedenti mantengono il comportamento compatibile: iniziali se presenti, altrimenti
solo voti. L'anteprima non salva una nuova fotografia permanente.

Il nuovo algoritmo è già il default per ogni nuovo calcolo. Lista e dettaglio dei
sondaggi espongono la versione attiva e quella del calcolo salvato; l'app indica quali
calendari sono precedenti all'aggiornamento. Non esiste un interruttore da attivare e
l'anteprima non applica automaticamente modifiche ai calendari già pubblicati; dalla beta 4 è possibile pubblicarle con conferma esplicita.

Il confronto riguarda soltanto gli studenti ancora assegnati al sondaggio: non aggiunge
nuovi iscritti. Quando la cancellazione di un account rende incompleta la fotografia,
la modalità con bonus iniziali viene disabilitata. Gli scambi già accettati sono indicati;
a parità di punteggio ottimo si mantiene il maggior numero possibile di date attuali.

## Pubblicazione e ripristino di una revisione

Dalla beta 4 il rappresentante può scegliere il confronto e premere **Pubblica calendario
rivisto**. La conferma mostra conteggi, peggioramenti, elenco degli spostamenti e politica
dei bonus. Il confronto rimane in sola lettura fino alla conferma esplicita.

`POST /api/polls/:id/assignments/revisions` richiede `basis` e `revisionToken` restituiti
dall'anteprima. Il token identifica gli input, i bonus, le assegnazioni, gli scambi,
i calcoli successivi della materia e gli eventi coinvolti. Una nuova lettura e un
confronto SQL dentro la transazione impediscono di pubblicare un risultato superato.
Non vengono accettate assegnazioni arbitrarie inviate dal client.

La transazione salva il prima/dopo in `interrogation_revisions`, aggiorna i posti,
crea le date pubbliche del calendario collegate alla griglia e rifiuta gli scambi
ancora in attesa. Gli eventi generati dalle vecchie app senza collegamento vengono
sostituiti soltanto se titolo, data, nomi e caratteristiche corrispondono esattamente;
eventi manuali, circolari e note diverse restano intatti. Una corrispondenza ambigua con
un'altra griglia della materia ferma la pubblicazione. Le operazioni sono collettive
su JSON, senza una query separata per ogni studente.

Politiche dei bonus, visibili prima della conferma:

- `RECONCILE`: soltanto con modalità ORIGINAL, fotografia e regole complete, saldi
  ancora uguali ai risultati iniziali e nessun calcolo/revisione successivo della
  materia. Sostituisce il saldo risultante con quello corretto, senza aggiungere
  nuovamente gli accrediti. La fotografia iniziale resta immutata.
- `PRESERVE`: vecchie griglie senza fotografia, simulazioni con bonus attuali o soli
  voti, oppure storico successivo incompatibile. Pubblica le date e mantiene i saldi
  attuali. Non inventa bonus iniziali e non attribuisce nuovi accrediti.

Dopo la pubblicazione compare **Ripristina calendario precedente**, con l'elenco degli
spostamenti di ritorno. `POST /api/polls/:id/assignments/revisions/:revisionId/restore`
richiede il token aggiornato. Ripristina posti, date precedenti ed eventuali saldi
riconciliati solo se lo stato è ancora esattamente quello post-pubblicazione. Nuovi
scambi, bonus, eventi modificati o dati eliminati bloccano il ripristino automatico.
Gli scambi rifiutati non vengono riaperti. Una sola revisione può essere attiva per
griglia; per pubblicare un confronto diverso occorre prima ripristinare.

Applicare **una sola volta** `backend/migrations/027_poll_revisions.sql` prima del
Worker: contiene un ALTER TABLE non ripetibile. Non pubblica revisioni né cambia
risultati, bonus o eventi esistenti. La cancellazione di un account cancella anche
le fotografie delle revisioni della classe, che possono contenere nomi negli eventi.
I test coprono migrazione, autorizzazioni, conferme duplicate, conflitti prima della
transazione, riconciliazione, ripristino e rollback totale in caso di errore.

## Sincronizzazione esplicita del calendario e PDF (beta 5)

**Sondaggi → Storico → Interrogazioni → Aggiorna date nel calendario** legge i risultati
pubblicati e mostra le nuove date con i nomi. Gli eventi già collegati alla griglia sono
aggiornati senza duplicarli. Le vecchie voci senza collegamento sono selezionabili dal
rappresentante: la conferma sostituisce solo quelle selezionate. Eventi di circolari,
di altre griglie, privati o non selezionati restano intatti. Le corrispondenze complete
riconosciute vengono preselezionate soltanto se la data non è ambigua con altri sondaggi.

`GET /api/polls/:id/calendar/preview` e `POST /api/polls/:id/calendar/sync` sono riservati
al rappresentante della classe. La POST richiede `syncToken` e `replaceEventIds`; un
confronto SQL nello stesso batch protegge da modifiche tra anteprima e conferma. Le date
usano ID stabili per griglia/slot. Assegnazioni e bonus non vengono modificati. La
migrazione additiva e ripetibile `028_poll_calendar_sync.sql` va applicata prima del Worker.

Se il ripristino di una revisione era ancora sicuro, la sincronizzazione aggiorna la
fotografia post-pubblicazione e registra le vecchie voci selezionate da ripristinare.
Non riabilita un ripristino già bloccato e non può riscrivere fotografie cancellate
nel frattempo dalla rimozione di un account.

**Esporta PDF date e studenti** è disponibile sulle card dello Storico a chi può leggere
i risultati. `GET /api/polls/:id/assignments/pdf` applica gli stessi controlli di classe
e destinatari delle assegnazioni; restituisce `application/pdf`, `private, no-store`.
L'app disabilita la copia offline per questa richiesta e usa la condivisione PDF già
presente sulle tre piattaforme. Sono esportati solo i risultati realmente pubblicati,
con tutte le date (comprese quelle senza assegnati), ordinati per data e cognome.

Il PDF A4 contiene testo selezionabile, nomi su più righe, intestazioni ripetute e numeri
di pagina. Incorpora Liberation Sans, già incluso in PDF.js nell'app, con relativa
licenza in `backend/src/assets`. Font e metriche sono generati offline da
`scripts/generate_poll_pdf_font.py` (richiede fonttools); il Worker non analizza font né
usa un motore PDF pesante a ogni richiesta. `pdf-lib` serve solo nei test come lettore
indipendente del documento prodotto.
