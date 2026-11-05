# AILA ACA e calendario interno

Implementazione della specifica del 22 ottobre 2026, estesa dalle decisioni dell’utente: AI e ACA opzionali, scelta dei servizi in Impostazioni → AI, allegati piccoli conservati 90 giorni. Vedere [ACA_IMPLEMENTATION.md](ACA_IMPLEMENTATION.md) per forum, allegati, piattaforme, configurazione e verifiche.

## Percorso delle azioni

1. L’intento viene ricavato dal messaggio dell’utente, mai dalle istruzioni nei documenti. Le richieste informative, le negazioni e «Annulla» non autorizzano azioni. I chiarimenti restano nella cronologia.
2. Il pianificatore produce al massimo dodici `calendar.create`, `calendar.update` o `calendar.delete`. Non inventa date o orari; per gli aggiornamenti indica soltanto i campi richiesti e sceglie un identificatore dall’elenco degli eventi effettivamente modificabili. Valutazioni dei compagni, preferenze sociali e assegnazioni dei posti sono escluse dal contesto del pianificatore.
3. Prima di mostrare i pulsanti di conferma, il backend convalida l’intero piano, normalizza i dati e salva una **PREVIEW** personale valida 30 minuti. Non scrive eventi. Per modifiche ed eliminazioni recupera i valori reali e conserva i destinatari esistenti.
4. La chat mostra titolo, data, orario, categoria, note e destinatari; per le modifiche mostra anche i valori precedenti. La conferma invia identificatore dell’operazione e digest dell’esatta anteprima, senza una seconda generazione.
5. Il backend ricontrolla account, classe, attivazione AI/ACA/Calendario e permessi. Una transazione D1 esegue il piano con un token di esecuzione; i confronti con lo stato precedente impediscono di sovrascrivere eventi cambiati nel frattempo, inclusa la visibilità. I duplicati non vengono forzati e le conferme ripetute o concorrenti non ripetono le scritture.
6. Gli esiti persistenti distinguono azioni eseguite, eventi già presenti e conflitti. Il calendario viene aggiornato. Dopo un’interruzione «Verifica l’esito sul server» legge lo stato dell’operazione senza ripeterla. Le vecchie anteprime prive di identificatore server non sono eseguibili.

Un nuovo messaggio annulla l’anteprima precedente; «Annulla» la disabilita anche sul server. Dopo scadenza o annullamento serve un nuovo piano. Le operazioni conservate vengono eliminate dopo 30 giorni. Il modello non riceve URL arbitrari, credenziali, SQL o capacità di scrittura diretta.

## Attività e conferma

Il segnale personale ACA segue lettura, pianificazione, scrittura, trascrizione ed esecuzione. Il bordo arcobaleno compare brevemente, il popup mostra il simbolo ufficiale e tre puntini; la luce converge sull’icona e resta un’attività contenuta. Fine, errore, annullamento e richiesta di conferma fermano l’animazione. Riduci movimento elimina il trasferimento; nella PWA il bordo usa un solo tratto per contenere il costo di disegno. L’attività privata non viene comunicata al resto della classe.

## Limiti

Gli strumenti agiscono soltanto nel calendario interno. Non cambiano i destinatari di un evento esistente e non includono calendari esterni. La validazione delle date non garantisce che il modello abbia interpretato correttamente una fonte: questo resta uno dei motivi dell’anteprima obbligatoria. Un piano con più di dodici azioni va suddiviso.
