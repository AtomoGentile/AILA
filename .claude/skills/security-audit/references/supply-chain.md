# Segreti, CI/CD, infrastruttura, dipendenze

## Segreti nel repository
```bash
# File tracciati che sembrano segreti
git ls-files | grep -i -E '\.env($|\.)|\.pem$|\.p12$|\.key$|id_rsa|credentials|service-?account|google-services\.json|GoogleService-Info\.plist|\.keystore$|\.jks$'

# Pattern di chiavi nel contenuto attuale
git grep -n -I -E 'AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|ghp_[0-9A-Za-z]{36}|github_pat_[0-9A-Za-z_]{40,}|xox[baprs]-[0-9A-Za-z-]+|sk_live_[0-9A-Za-z]{20,}|\bsk-(proj-|ant-)?[A-Za-z0-9_-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|"private_key":'

# Nella cronologia (anche se poi cancellati)
git log --all -p -G 'BEGIN [A-Z ]*PRIVATE KEY|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|sk_live_' --oneline | head -50
```
- Un segreto finito nella cronologia va considerato compromesso: la correzione è **ruotarlo**,
  non solo cancellarlo. Scrivilo nel report; riscrivere la cronologia lo decide il proprietario.
- `.gitignore` copre `.env*`, chiavi, file di configurazione con credenziali.
- Valori segnaposto di default (`changeme`, `secret`, `dev`) non usati in produzione.

## CI/CD (GitHub Actions e simili)
- Nessuna espressione non fidata dentro `run:`: `${{ github.event.issue.title }}`,
  `...pull_request.title/body`, `...head_ref`, `...comment.body`, `${{ inputs.* }}` vanno passati
  via `env:` e, se usati come argomenti, validati.
- `pull_request_target` e `workflow_run` che fanno checkout ed eseguono codice della PR: grave.
- `permissions:` minimi a livello di workflow o job (default `contents: read`).
- Azioni di terzi fissate a uno SHA quando hanno accesso a segreti.
- Segreti non stampati nei log; `::add-mask::` per valori derivati.
- Artefatti di build non contengono `.env` o chiavi.

## Infrastruttura
- Bucket/storage non pubblici se contengono dati privati; URL firmati con scadenza.
- Database non esposti su Internet; credenziali con privilegi minimi.
- Dockerfile: utente non root, nessun segreto in `ENV`/`ARG`/layer, immagine base aggiornata.
- Regole di sicurezza di servizi gestiti (Firebase/Firestore/Supabase RLS, policy IAM): spesso
  sono loro il vero controllo degli accessi, vanno lette come il codice del backend.

## Dipendenze
- Esegui l'audit dell'ecosistema, se la rete lo permette: `npm audit --omit=dev`,
  `pip-audit`, `cargo audit`, `govulncheck ./...`, `bundle audit`, `./gradlew dependencyCheck`
  (se configurato), `osv-scanner -r .` (se installato).
- Riporta solo vulnerabilità raggiungibili dal codice o in dipendenze di runtime; per le altre
  basta una riga riassuntiva.
- Lockfile presente e usato in CI (`npm ci`, non `npm install`) per build riproducibili.
- Pacchetti con nomi sospetti (typosquatting) o script di installazione inattesi.
