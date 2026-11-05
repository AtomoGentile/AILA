# Correzioni dopo il primo collaudo 2.0

- Pere: accesso persistente dalla home e dalla sezione Classe.
- Home: un solo riquadro AILA brief, coerente con il tema; ripasso riferito all’impegno selezionato, non alla semplice frase generica.
- Destinatari: brief e studio escludono gli eventi creati dall’utente per destinatari diversi. L’autore non è automaticamente destinatario.
- Circolari: quelle ancora in analisi restano nella lista, insieme alla disclosure dei lavori in corso.
- ACA: pressione di 650 ms; popup dall’alto senza navigazione. Contesto visibile e selezionabile prima dell’invio, esclusione dei campi di credenziali. Conferme disponibili nel popup.
- Indicatore: perimetro acceso durante lettura, pianificazione, scrittura e trascrizione, anche nella chat; scomparsa prima della risposta. Nessun ritardo artificiale iniziale.
- Chat: compositore unico, più allineato, microfono quando il testo è vuoto, allegati e fonti dentro la conversazione. File, impostazioni e ripasso nel menu della chat, senza passare dall’hub ACA.
- Vocali: lettura dei box M4A estesi a 64 bit e dei metadati mdhd, con controllo dei limiti di struttura e durata.
- Preferenze: niente disabilitazione globale degli interruttori durante il salvataggio; sincronizzazione senza GET aggiuntiva.
- Provider: richieste testuali, trascrizioni e immagini con API personale OpenAI passano al provider configurato, senza consumare la quota di inferenza AILA. Gli altri provider mantengono le capacità effettivamente implementate. Le proposte pubbliche del forum rimangono un servizio condiviso distinto.

Il contesto della schermata viene allegato solo con scelta esplicita nel popup, è visibile prima dell’invio e rispetta i servizi e i consensi attivi. Le modifiche al calendario restano sottoposte a conferma.

Le prove automatiche non attestano registrazione fisica, permessi del microfono, resa grafica o Foundation Models su un dispositivo reale. Il collaudo su Android/iOS resta necessario prima di dichiarare la versione stabile.

L’inserimento AI dal calendario usa lo stesso pianificatore multi-evento dell’assistente: la richiesta viene presentata in ACA sopra il calendario, con elenco delle date e conferma del piano. Il vecchio riempimento automatico di una sola bozza non è più l’ingresso usato da quel menu. L’inserimento manuale e le sue ripetizioni restano disponibili. La cronologia resta condivisa con l’assistente, come richiesto.

La rilevanza personale è separata dalla visibilità: gli eventi dei calendari interrogazioni restano visibili alla classe, ma studio e brief usano gli studenti assegnati allo slot del sondaggio. Gli eventi storici senza collegamento usano il roster esplicito nelle note, con esclusione dei casi ambigui. L’API aggiunge l’indicatore personale `isRelevantToMe`, conservato anche nel client; i dati di calendario non vengono nascosti alla classe per ottenere questo filtro.
