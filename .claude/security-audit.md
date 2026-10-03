# Note di sicurezza di AILA

> Letto per primo dalla skill generica `/security-audit` (`.claude/skills/security-audit/`): qui ci sono
> le parti specifiche di AILA, che hanno la precedenza sulle indicazioni generiche.

## Mappa delle superfici

| Area | Dove | Cosa conta di più |
|---|---|---|
| Worker | `backend/src/index.ts`, `auth.ts`, `rateLimit.ts`, `routes/*.ts`, `services/*.ts` | Autorizzazione per ruolo e per classe, anonimato, SQL, segreti |
| Schema | `backend/schema.sql`, `backend/migrations/*.sql` | Vincoli, colonne `class_id`, indici di unicità |
| Contratto | `backend/src/contracts.ts` (condiviso con la PWA) | Campi esposti al client |
| PWA | `web/src/**`, `web/public/_headers`, `web/src/sw.ts` | XSS, CSP, token in IndexedDB, cache del service worker |
| App KMP | `shared/src/commonMain/kotlin/circolareplus/**`, `androidApp/`, `iosApp/` | Dove stanno token e chiave Gemini, backup, deep link, log |
| CI | `.github/workflows/*.yml` | Input non fidati nei `run:`, permessi, segreti nei log |

Ruoli (`contracts.ts`): `STUDENT`, `REPRESENTATIVE`, `SECURITY_GUARD`. La classe dell'utente
arriva da `resolveClassId(c)`; ruolo e classe vengono riletti dal DB a ogni richiesta in
`authMiddleware`.

## Procedura

### 1. Preparazione
- `git log --oneline -20` e `git diff origin/main...HEAD` per sapere cosa è cambiato di recente:
  le modifiche nuove sono le prime da guardare.
- Cerca in `TODO.md` le voci su sicurezza già note (`grep -n -i "sicurezza\|security" TODO.md`),
  per non riportare come nuovo ciò che è già tracciato o già corretto.

### 2. Worker — una rotta alla volta
Per **ogni** handler in `backend/src/routes/*.ts` compila mentalmente questa riga e segnati quelle
che non tornano:

1. **Autenticazione**: la rotta passa da `authMiddleware()`? Le uniche rotte pubbliche ammesse
   sono login, registrazione, reset password, health. Qualsiasi altra rotta senza middleware è
   un problema.
2. **Ruolo**: le azioni da Rappresentante/Guardia usano `requireRole(...)` o un controllo
   equivalente sul ruolo letto dal DB, non un campo del body.
3. **Isolamento tra classi** (il rischio principale dell'app): ogni `SELECT`, `UPDATE`, `DELETE`
   su dati di classe filtra su `class_id = ?` con il valore di `resolveClassId(c)`. Attenzione
   alle query per id (`WHERE id = ?`): un id di un'altra classe non deve essere leggibile né
   modificabile. Controlla anche le JOIN e le sottoquery, non solo la tabella principale.
4. **Proprietà**: modificare/cancellare un evento, una proposta, un commento o un voto richiede
   di esserne l'autore (o un ruolo che lo consente esplicitamente).
5. **Input**: tutte le query sono `prepare(...).bind(...)`. Cerca interpolazioni:
   `grep -n '\${' backend/src/routes/*.ts backend/src/services/*.ts` e verifica che ogni
   `${...}` dentro una stringa SQL sia una costante del codice, mai un valore della richiesta.
   Controlla tipi, lunghezze massime e valori ammessi (enum, range dei voti -2…+2, budget voti).
6. **Risposta**: non restituisce `password_hash`, token di altri, le valutazioni riservate del
   Rappresentante (`ratings`) a chi non è Rappresentante, né l'autore di contenuti anonimi.

### 3. Punti sensibili specifici di AILA
- **Anonimato della bacheca** (`routes/proposals.ts`, tabelle `anonymity_unlock_*`): l'identità
  di proposte e commenti anonimi si sblocca solo con il quorum (2 Rappresentanti + 1 Guardia
  scelta dal Rappresentante, ognuno dal proprio account). Verifica che: lo stesso account non
  conti due volte; la Guardia sia quella designata per *quella* classe; l'autore non trapeli da
  altri campi (liste, conteggi, notifiche push, ordinamenti, `author_id` nel JSON, errori).
- **Preferenze sociali e valutazioni**: il Rappresentante vede aggregati, non chi ha votato cosa
  (`routes/preferences.ts`, `routes/ratings.ts`). Le valutazioni non devono finire in prompt AI
  (`services/summarizer.ts`, `services/classAnalysis.ts`, assistente in `shared/.../ai`).
- **Sondaggi**: un utente non può votare oltre il budget, votare per un altro, o vedere le scelte
  altrui prima del momento previsto (`routes/polls.ts`, `routes/rankingPolls.ts`, `audience.ts`).
- **Registrazione e ruoli**: `REPRESENTATIVE_SIGNUP_CODE` confrontato a tempo costante
  (`timingSafeEqual`), codici invito di classe (`services/classInvites.ts`) monouso, salvati solo
  come hash, con scadenza e rate limit.
- **JWT** (`auth.ts`): HS256 con `JWT_SECRET`; `verifyJWT` deve rifiutare `alg` diversi, token
  senza `exp`, firma assente. Il `pv` invalida i token dopo cambio password: verifica che tutte le
  vie di cambio/reset password lo aggiornino.
- **Rate limit** (`rateLimit.ts`): login, registrazione, reset, codici invito. Nota che fallisce
  "aperto" se manca la tabella: va bene solo se la migrazione 013 è applicata in produzione.
- **Push** (`services/fcm.ts`, `webpush.ts`, `routes/fcm.ts`, `routes/webpush.ts`): un utente
  non può iscrivere un token/endpoint a topic di un'altra classe; il testo delle notifiche non
  rivela autori anonimi; l'endpoint web push è validato (solo https, niente SSRF verso host
  interni).
- **Spaggiari e PDF** (`services/spaggiari.ts`, R2): URL scaricati solo dal dominio della scuola;
  le chiavi R2 non derivano da input dell'utente senza normalizzazione (path traversal); limiti di
  dimensione.
- **CORS** (`index.ts`): solo `WEB_ORIGINS` e localhost. Nessun `*` con credenziali.
- **Errori**: `app.onError` non deve rimandare stack o messaggi SQL al client.

### 4. PWA (`web/`)
- Sink XSS: `grep -rn "dangerouslySetInnerHTML\|innerHTML\|insertAdjacentHTML\|eval(\|new Function" web/src`.
  Il testo di circolari, proposte, commenti e risposte dell'AI è sempre non fidato.
- Link generati da dati: niente `javascript:`; `target="_blank"` con `rel="noopener"`.
- `web/public/_headers`: la CSP resta senza `unsafe-inline`/`unsafe-eval`; `connect-src`
  allineato all'URL reale del Worker e a Gemini e a nient'altro.
- `sw.ts`: le risposte API autenticate non vanno messe in una cache condivisa che sopravvive al
  logout; il logout (`lib/session.ts`) svuota token, utente e cache.
- La chiave Gemini personale non lascia il browser se non verso `generativelanguage.googleapis.com`.

### 5. App KMP
- Dove stanno token e chiave Gemini (`AuthRepository`, `LocalSettingsManager`, impostazioni):
  Android `allowBackup="false"` nel manifest; iOS esclusione dai backup. Nessun valore sensibile
  in `println`/`Log`/crash.
- Deep link e `PendingDeepLink.kt`: un link esterno non può eseguire azioni (votare, cancellare)
  senza conferma dell'utente.
- `AndroidManifest.xml`: componenti `exported` solo dove serve; niente `usesCleartextTraffic`.
- Nessuna chiave o URL privato hardcoded: `grep -rn -i "AIza\|BEGIN PRIVATE\|secret\|api_key" shared androidApp iosApp --include=*.kt --include=*.swift --include=*.plist --include=*.xml`.

### 6. Segreti e CI
- Segreti nel repo e nella cronologia:
  `git grep -n -I -E "AIza[0-9A-Za-z_-]{30,}|-----BEGIN (RSA |EC )?PRIVATE KEY|\"private_key\"|ghp_[0-9A-Za-z]{30,}"`
  e `git log -p -S "private_key" --all | head`. File che `.gitignore` esclude
  (`google-services.json`, `*firebase-adminsdk*.json`) non devono comparire in `git ls-files`.
- Workflow: nessun `${{ github.event.* }}` o `${{ inputs.* }}` dentro `run:` (passarli via `env:`
  e validarli come fa `worker-deploy.yml`); `permissions:` minimi; nessun `pull_request_target`
  che esegue codice della PR; segreti mascherati.
- Dipendenze: `cd backend && npm audit --omit=dev` e `cd web && npm audit --omit=dev` (se la rete
  lo consente). Riporta solo vulnerabilità raggiungibili dal codice.

## Verifica e correzione
- Per ogni sospetto, conferma con il codice: segui il dato dalla richiesta fino alla query.
  Se non riesci a costruire uno scenario concreto (chi, quale richiesta, cosa ottiene), non è
  un problema confermato: mettilo tra i "da verificare".
- Per i problemi del Worker scrivi un test in `backend/test/routes.test.ts` (usa lo shim D1 di
  `backend/test/d1shim.ts`) che fallisce prima della correzione e passa dopo.
- Correggi i problemi **Critici** e **Alti** confermati con la modifica minima, nello stile del
  codice intorno (commenti in italiano che spiegano il perché). I **Medi/Bassi** vanno nel
  report, salvo correzioni banali.
- Se una correzione richiede una migrazione D1, crea il file numerato successivo in
  `backend/migrations/` (solo `CREATE ... IF NOT EXISTS` o istruzioni idempotenti) e scrivi nel
  report che va applicata dal workflow **Worker deploy**.
- Controlli prima del commit:
  - `cd backend && npx tsc --noEmit -p . && npm test`
  - `cd web && npm test && npm run typecheck`
  - per modifiche a `shared/`: `./gradlew check` se Gradle è disponibile, altrimenti
    dillo nel report.

## Report
Alla fine scrivi un report (in chat, e aggiungi una sezione datata in `TODO.md` come per gli
altri lavori) con:

| Gravità | Area | File:riga | Problema | Scenario d'attacco | Stato |
|---|---|---|---|---|---|

Gravità: **Critica** (dati di altre classi, identità anonime, account altrui), **Alta**
(escalation di ruolo, segreti esposti), **Media**, **Bassa**. Stato: corretto (con commit),
da correggere, da verificare. Elenca anche cosa non è stato controllato e perché.
Non riportare problemi solo teorici né consigli generici senza un file e una riga.
