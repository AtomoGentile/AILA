# Backend, API, database, job

## Per ogni rotta / handler
Compila questa riga e annota quelle che non tornano:

1. **Autenticazione**: la rotta passa dal middleware di autenticazione? Elenca le rotte pubbliche
   e chiediti se ognuna deve esserlo davvero (login, registrazione, reset, health, webhook firmati).
2. **Autorizzazione per ruolo**: verificata sul server, con il ruolo letto da una fonte fidata
   (DB o token firmato), mai da un campo del body/query/header scelto dal client.
3. **Proprietà dell'oggetto** (IDOR): ogni accesso per id (`/items/:id`, `WHERE id = ?`) controlla
   che l'oggetto appartenga all'utente o al suo tenant. Controlla anche JOIN, sottoquery,
   operazioni in blocco, export, conteggi e rotte "secondarie" (commenti, allegati, voti).
4. **Isolamento tra tenant**: se esistono organizzazioni/classi/team/workspace, *ogni* query su
   dati di tenant filtra sul tenant dell'utente, preso dalla sessione e non dalla richiesta.
5. **Mass assignment**: il body non può impostare campi come `role`, `owner_id`, `tenant_id`,
   `is_admin`, `price`, `status` se non previsto. Diffida di `{...body}` / `**data` passati al DB.
6. **Validazione**: tipi, lunghezze massime, enum, range numerici, formati. Limiti alla
   dimensione del body e degli upload, alla paginazione (`limit` massimo).
7. **Risposta**: niente hash di password, token, dati di altri utenti, campi interni, autori di
   contenuti anonimi; errori senza stack trace, SQL o percorsi.

## Sink da cercare
- SQL: concatenazione o interpolazione in query (`${`, `+ "`, f-string, `%` , `format(`,
  `.raw(`, `execute(` con stringhe costruite). Ogni valore della richiesta deve essere un
  parametro legato. I nomi di colonna/ordinamento dinamici vanno da una whitelist.
- NoSQL: oggetti della richiesta passati come filtro (`$ne`, `$gt`, `$where`).
- Comandi: `exec`, `spawn` con `shell: true`, `os.system`, `subprocess(..., shell=True)`,
  backtick, `Runtime.exec`.
- File: percorsi costruiti da input (`../`), estrazione di archivi (zip slip), upload salvati
  con il nome del client, tipo MIME fidato dal client, file serviti dalla stessa origine dell'app.
- Rete (SSRF): URL forniti dall'utente scaricati dal server (anteprime, webhook, import, immagini,
  push endpoint). Serve whitelist di schema e host, blocco di IP privati/loopback/metadata cloud
  (`169.254.169.254`), attenzione ai redirect.
- Deserializzazione: `pickle`, `yaml.load` non safe, `ObjectInputStream`, `unserialize`,
  `eval`/`new Function` su dati.
- Template lato server con input dell'utente nel template stesso.
- Redirect aperti: `redirect(req.query.next)`.
- Regex costruite da input o vulnerabili a backtracking (ReDoS) su input lunghi.

## Autenticazione e sessioni
- Password: hash lento con sale (argon2, bcrypt, scrypt, PBKDF2 con molte iterazioni), lunghezza
  minima e massima, nessuna password in log o risposte.
- Confronti di segreti, token e codici a tempo costante.
- JWT: algoritmo fissato sul server (niente `alg: none` né scelta dal token), `exp` obbligatorio,
  segreto robusto e non di default, invalidazione dopo logout/cambio password/eliminazione account
  o cambio ruolo (ruolo e permessi riletti dal DB, o token di breve durata).
- Reset password, codici d'invito, link magici: casuali con CSPRNG, monouso, con scadenza,
  salvati come hash, rate limit, nessuna enumerazione degli utenti nelle risposte.
- Rate limit su login, registrazione, reset, invio codici, endpoint costosi. Verifica cosa succede
  se il meccanismo di rate limit fallisce (aperto o chiuso?).
- Cookie: `HttpOnly`, `Secure`, `SameSite`; con cookie di sessione serve protezione CSRF.
- CORS: niente origine riflessa senza whitelist, niente `*` con credenziali.

## Logica di dominio
Le falle più gravi spesso sono di logica, non tecniche. Per ogni regola del dominio
(voti, budget, quote, stati di un flusso, approvazioni a quorum, anonimato, prezzi, inviti)
chiediti come un utente potrebbe aggirarla: ripetere la richiesta, farla in parallelo (race
condition: due richieste che passano lo stesso controllo), saltare un passaggio, agire per conto
di un altro, contare due volte lo stesso voto, vedere un risultato prima del tempo.

## Job, cron, webhook, code
- Webhook in ingresso: firma verificata, a tempo costante, con protezione dai replay.
- Job che elaborano contenuti esterni li trattano come non fidati.
- Notifiche e email non includono dati che il destinatario non dovrebbe vedere.

## Schema e dati
- Vincoli di unicità che impediscono doppi voti/iscrizioni a livello DB, non solo nel codice.
- Colonne di tenant presenti e indicizzate su tutte le tabelle di dati di tenant.
- Eliminazione account: cosa resta e se resta collegato alla persona.
