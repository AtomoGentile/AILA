---
name: security-audit
description: Audit di sicurezza di un progetto qualsiasi (backend/API, frontend web, app mobile, CLI, librerie, infrastruttura e CI). Usala quando l'utente chiede un audit o una revisione di sicurezza, "controlla se ci sono falle", prima di un rilascio, o dopo modifiche ad autenticazione, permessi, dati personali, pagamenti, upload, segreti o pipeline. Ricostruisce la superficie d'attacco, cerca falle reali, le conferma sul codice, corregge quelle gravi e scrive un report.
---

# Security audit

Obiettivo: trovare falle **reali e sfruttabili** in questo progetto, non un elenco di buone
pratiche generiche. Ogni problema va confermato seguendo il dato nel codice (e, quando si può,
con un test che lo riproduce) prima di finire nel report o di essere corretto.

Argomenti facoltativi: un'area (`backend`, `web`, `mobile`, `ci`, `deps`), un percorso, oppure
`diff` (solo le modifiche del branch rispetto al ramo principale) o `report` (nessuna modifica
al codice). Senza argomenti: tutto il progetto.

## 1. Contesto del progetto

Prima di tutto leggi, se esistono:
- `.claude/security-audit.md`: note di sicurezza specifiche del progetto (ruoli, dati sensibili,
  punti delicati, comandi di test). **Hanno la precedenza** sulle indicazioni generiche qui sotto.
- `CLAUDE.md`, `README.md`, `SECURITY.md`, documentazione su privacy e deploy.
- Le voci già note su sicurezza in TODO/issue, per non riportare come nuovo ciò che è tracciato.

## 2. Ricognizione

Ricostruisci la mappa prima di cercare problemi. Scrivila brevemente (ti serve per il report).

- **Stack**: guarda i manifest (`package.json`, `pyproject.toml`/`requirements*.txt`, `go.mod`,
  `Cargo.toml`, `pom.xml`/`build.gradle*`, `Gemfile`, `composer.json`, `*.csproj`, `Podfile`,
  `Package.swift`), Dockerfile, IaC (`*.tf`, `wrangler.toml`, `serverless.yml`, k8s, `fly.toml`,
  `vercel.json`), cartelle di workflow CI.
- **Punti d'ingresso**: rotte HTTP/GraphQL/RPC, handler di code e cron, webhook, comandi CLI,
  deep link, componenti esportati delle app, file caricati, input da servizi esterni (scraping,
  API di terzi, risposte di LLM).
- **Confini di fiducia**: cosa arriva dall'utente, cosa da altri utenti (contenuti condivisi),
  cosa da terzi. Tutto ciò che attraversa un confine è non fidato.
- **Modello di identità e permessi**: come si autentica (sessione, JWT, OAuth, chiavi API),
  quali ruoli esistono, chi possiede cosa, se ci sono più tenant/organizzazioni/gruppi i cui dati
  devono restare separati.
- **Dati sensibili**: credenziali, dati personali (in particolare di minori), dati sanitari o di
  pagamento, contenuti privati o anonimi, chiavi di terzi.
- **Segreti**: dove sono configurati e come arrivano al codice.

Le modifiche recenti (`git log --oneline -30`, `git diff <ramo-principale>...HEAD`) sono le
prime da guardare.

## 3. Ricerca

Per ogni area presente nel progetto apri il riferimento corrispondente e seguilo:

| Area | Riferimento |
|---|---|
| Backend, API, database, job | [references/backend.md](references/backend.md) |
| Frontend web, PWA, estensioni | [references/web.md](references/web.md) |
| App mobile e desktop | [references/mobile.md](references/mobile.md) |
| Segreti, CI/CD, infrastruttura, dipendenze | [references/supply-chain.md](references/supply-chain.md) |
| Funzioni con LLM/AI | [references/ai.md](references/ai.md) |

Le falle che contano di più, quasi in ogni progetto, in quest'ordine:
1. **Controllo degli accessi rotto**: rotte senza autenticazione, ruoli non verificati sul server,
   oggetti letti o modificati per id senza controllare proprietario o tenant (IDOR), dati di un
   tenant visibili a un altro.
2. **Iniezioni**: SQL/NoSQL, comandi di shell, template, path traversal, SSRF, XSS,
   deserializzazione non sicura, prompt injection con strumenti.
3. **Autenticazione e sessioni**: password, token, reset, rate limit, confronti non a tempo
   costante, sessioni che sopravvivono a logout o cambio password.
4. **Esposizione di dati**: campi in più nelle risposte, log, messaggi d'errore, cache, notifiche.
5. **Segreti** nel codice, nella cronologia git, nei log o nel bundle del client.
6. **Pipeline e dipendenze**.

Metodo: parti da ogni punto d'ingresso e segui il dato fino a dove viene usato (query, file,
comando, risposta, altro utente). Usa `grep`/`rg` per trovare i "sink" pericolosi elencati nei
riferimenti, poi risali a ritroso fino all'input.

## 4. Verifica

- Un problema è **confermato** solo se sai dire: chi è l'attaccante (anonimo, utente normale,
  utente di un altro tenant, ruolo basso), quale richiesta fa, cosa ottiene. Altrimenti va tra i
  "da verificare", non tra i problemi.
- Dove c'è una suite di test, scrivi un test che riproduce il problema (fallisce prima della
  correzione, passa dopo), nello stile dei test esistenti.
- Scarta: problemi solo teorici, protezioni già presenti più in alto (middleware, framework,
  proxy), codice morto o solo di sviluppo, consigli generici senza file e riga.

## 5. Correzione

Salvo l'argomento `report`:
- Correggi i problemi **Critici** e **Alti** confermati, con la modifica minima, nello stile e
  nella lingua dei commenti del codice intorno. Spiega nel commento il perché, non il cosa.
- Preferisci la correzione strutturale (un controllo centralizzato, una query parametrizzata,
  un helper già esistente) a toppe sparse.
- Se serve una migrazione di schema, crea un file nuovo e idempotente nel formato del progetto e
  segnala nel report che va applicato.
- Non cambiare comportamento visibile oltre il necessario: se una correzione rompe un flusso
  (per esempio richiede un nuovo login a tutti), dillo prima di farla.
- Fai girare i controlli del progetto (test, typecheck, lint, build) prima di committare. Se un
  controllo non si può eseguire nell'ambiente, scrivilo.

## 6. Report

In chat, e se il progetto tiene un diario dei lavori (TODO, CHANGELOG, note) aggiungi lì una
sezione datata. Formato:

**Superficie analizzata**: stack, punti d'ingresso, ruoli, dati sensibili (3-6 righe).

| Gravità | Area | File:riga | Problema | Scenario d'attacco | Stato |
|---|---|---|---|---|---|

- **Critica**: accesso ai dati di altri utenti/tenant, presa di controllo di account, esecuzione
  di codice, segreti di produzione esposti.
- **Alta**: escalation di privilegi, XSS memorizzato, SSRF verso rete interna, bypass del rate
  limit sul login.
- **Media**: fughe di informazioni limitate, protezioni mancanti con sfruttabilità ridotta.
- **Bassa**: difese in profondità, hardening.

Stato: corretto (commit), da correggere, da verificare. Chiudi con cosa **non** è stato
controllato e perché (ambiente, accesso, tempo).
