# Neural Expressive in AILA

Proposta di evoluzione del linguaggio visivo già richiamato dalle note di rilascio
della Gita. Il repository non definisce una libreria o uno standard con questo nome.
Qui indica un linguaggio di prodotto: informazioni collegate, gerarchie leggibili,
feedback locali e movimento che chiarisce un cambiamento.

## Priorità

1. **Relazioni e provenienza.** Nelle card di eventi e Gita mostrare un collegamento
   compatto «Da circolare 123», apribile. Nelle risposte dell'assistente rendere
   immediatamente accessibili le fonti. Usare piccoli nodi e connessioni statiche
   solo dove rappresentano relazioni reali; evitare reti decorative su ogni card.
2. **Sezioni coerenti.** Estrarre il pattern titolo + contatore della Gita in un
   componente condiviso per Documenti, Circolari e Sondaggi. Riusare tipografia e
   token di selezione esistenti. Animare il contatore soltanto quando cambia il
   dato, mantenendo stabile lo spazio del titolo.
3. **Azioni riconoscibili.** Una forma più evidente per l'azione principale di una
   schermata, gruppi di azioni secondarie più compatti, forme coerenti con il tema.
   Mantenere etichette esplicite e aree di tocco accessibili. Non affidare lo stato
   al solo colore.
4. **Assistente con stati espliciti.** Mostrare «Cerco nelle circolari», «Leggo le
   fonti», «Preparo la risposta» solo se corrispondono allo stato reale del lavoro.
   Collegare il risultato alle fonti con lo stesso pattern delle card. Eventuali
   impulsi del logo devono fermarsi al completamento e fuori dalla schermata.
5. **Feedback sul punto cambiato.** Alla conferma di un voto, al salvataggio o
   all'arrivo di un documento, dare riscontro sulla relativa card o azione.
   Evitare di rianimare l'intera lista già letta.

## Movimento e costo di rendering

- Navigazione: il gesto controlla la posizione; la selezione e i caricamenti della
  destinazione si aggiornano a pagina ferma. La barra segue il gesto in continuo.
- Swipe: pagine opache, lieve scala nel livello grafico, nessuna scala aggiuntiva
  con Riduci movimento. Evitare dissolvenze simultanee di pagine intere.
- Microinterazioni: molle per selezioni e piccole forme; dissolvenze brevi per
  cambiamenti di contenuto. Riusare AilaMotion e i token AppTheme.
- Non aggiungere sfocature, shader, animazioni infinite o entrate a cascata alle
  pagine adiacenti mantenute composte dal pager.

## Verifica su Android prima del rilascio

Misurare una build release su dispositivo, distinguendo prima visita e pagine già
aperte. Confrontare frame lenti durante swipe, inversione del gesto, swipe annullato,
tocchi rapidi sulla barra e salto a una tab distante. Verificare anche Riduci
movimento e ritorno alla posizione delle liste. A 60 Hz il budget è circa 16,7 ms
per frame, a 120 Hz circa 8,3 ms. Le modifiche al codice riducono lavoro identificabile;
il miglioramento effettivo va misurato sul dispositivo.
