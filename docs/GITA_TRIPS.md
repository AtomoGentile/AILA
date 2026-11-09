# Gite multiple e aggiornamenti

Il rappresentante può creare e rinominare gite, caricare materiali e pubblicare
aggiornamenti con titolo e testo. Tutta la classe legge; i permessi di scrittura
sono verificati anche sul server. Gli aggiornamenti appartengono alla singola gita
e riportano autore e data. L'eliminazione richiede conferma nell'app.

Il selettore in cima alla schermata accetta un gesto verticale: verso l'alto per
la successiva, verso il basso per la precedente. Il gesto non supera gli estremi.
Toccando il selettore si apre l'elenco, utilizzabile anche senza gesti. Lo scroll
sulla lista continua a scorrere i materiali della gita corrente.

Gli aggiornamenti sono elementi lazy separati. Non vengono composte più schermate
Gita in un pager verticale: questo evita di sommare il costo di più liste durante
il movimento. Ogni richiesta viene associata all'ID della gita selezionata.

ACA aperta dalla Gita usa il corpus della selezione, inclusi gli aggiornamenti;
l'assistente generale può ricevere tutte le gite, con il nome nella fonte.

## Distribuzione

Prima del backend e del client aggiornati, applicare al database D1 la migrazione
`backend/migrations/032_gita_trips.sql`, una sola volta. Su un nuovo database usare
lo schema completo aggiornato. La migrazione assegna i materiali e le circolari
esistenti a «Gita 1», mantenendo ID, file, versioni e segnalazioni. La prima gita
può essere rinominata dal rappresentante. I vecchi client continuano a usare la
prima gita tramite le rotte senza `tripId`.

Il deploy remoto e l'applicazione della migrazione non vengono eseguiti da queste
modifiche al repository.
