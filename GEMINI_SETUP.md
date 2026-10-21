# Credenziali Gemini per AILA

Da maggio 2026 Google crea solo chiavi di **autorizzazione** (iniziano con `AQ.`): ogni chiave è
legata a un *service account* di Google Cloud e le richieste girano con l'identità di quel
service account. Le vecchie chiavi `AIzaSy…` non restrette hanno smesso di funzionare il 19
giugno 2026, le altre a settembre 2026.

AILA non chiede un login OAuth: vuole **una chiave `AQ.`** e la manda nell'header
`x-goog-api-key`, l'unica forma che le chiavi nuove accettano. Questa guida spiega come
ottenerne una che funzioni davvero, dove metterla e come verificarla prima di incollarla
nell'app.

## 1. Strada veloce: Google AI Studio

1. Apri [aistudio.google.com/api-keys](https://aistudio.google.com/api-keys) ed entra con il tuo
   account Google.
2. **Create API key** → scegli **Create in new project**. Un progetto nuovo evita le
   restrizioni e i service account rimasti dai progetti vecchi.
3. Copia la chiave (`AQ.…`). AI Studio crea da solo il service account e la limita all'API
   Gemini.
4. Verificala (punto 4) prima di usarla.

Se la chiave risponde 401, **cancellala** in AI Studio e creane un'altra: è il primo rimedio che
suggerisce lo staff Google. Se il 401 resta, passa alla strada 2, che fa gli stessi passaggi a
mano e permette di vedere quale manca.

## 2. Strada completa: Google Cloud console

### 2.1 Progetto e API

1. [console.cloud.google.com](https://console.cloud.google.com/) → selettore progetti →
   **Nuovo progetto** (es. `aila-gemini`).
2. **API e servizi → Libreria** → cerca **Generative Language API** → **Abilita**.
3. Nella stessa libreria abilita anche **API Keys API**.

### 2.2 Permessi del tuo account

Su un progetto tuo sei *Proprietario* e hai già tutto. Se il progetto è di un altro account o
di un'organizzazione (es. l'account della scuola), in **IAM e amministrazione → IAM** al tuo
utente servono questi ruoli:

| Ruolo | A cosa serve |
| --- | --- |
| `API Keys Admin` (`roles/serviceusage.apiKeysAdmin`) | creare la chiave |
| `Service Usage Viewer` (`roles/serviceusage.serviceUsageViewer`) | vedere le API abilitate |
| `Service Account User` (`roles/iam.serviceAccountUser`) | usare il service account |
| `Service Account API Key Binding Admin` (`roles/iam.serviceAccountApiKeyBindingAdmin`) | legare la chiave al service account |

Con un account Google Workspace la policy di organizzazione
`constraints/iam.managed.disableServiceAccountApiKeyCreation` può bloccare il legame chiave ↔
service account. L'API Gemini è nell'elenco consentito per default, ma se la creazione fallisce
chiedi all'amministratore di controllare quella policy, oppure usa un account Gmail personale.

### 2.3 Service account

1. **IAM e amministrazione → Service account → Crea service account**.
2. Nome `aila-gemini`. Non servono ruoli: premi **Fine**.

### 2.4 Chiave di autorizzazione

1. **API e servizi → Credenziali → Crea credenziali → Chiave API**.
2. Spunta **Authenticate API calls through a service account** e scegli `aila-gemini`.
3. In **Restrizioni API** seleziona solo **Generative Language API**.
4. **Crea** e copia la chiave `AQ.…`.

### 2.5 Gli stessi passaggi con `gcloud`

```bash
gcloud projects create aila-gemini-123 --set-as-default
gcloud services enable generativelanguage.googleapis.com apikeys.googleapis.com
gcloud iam service-accounts create aila-gemini --display-name="AILA Gemini"
gcloud beta services api-keys create --display-name=aila \
  --api-target=service=generativelanguage.googleapis.com \
  --service-account=aila-gemini@aila-gemini-123.iam.gserviceaccount.com
gcloud services api-keys get-key-string KEY_ID
```

`KEY_ID` è l'ultima parte del `name` stampato dal comando di creazione. Il flag
`--service-account` esiste solo in `gcloud beta`.

## 3. Dove mettere la chiave

| Dove | Come |
| --- | --- |
| App Android/iOS | Primo avvio → **Chiave Google gratuita**, oppure **Impostazioni → Chiave AI di AILA Assistant**. Incolla e premi **Prova la chiave**. |
| Versione web (PWA) | **Impostazioni** → campo chiave → **Salva la chiave** e **Prova la chiave**. Resta nel browser. |
| Backend (riassunti sul server) | `cd backend` e `npx wrangler secret put GEMINI_API_KEY`, poi incolla la chiave. Facoltativo: senza, il server non riassume. |

La chiave non va mai messa in `wrangler.toml`, nel codice o in un commit.

## 4. Verificare la chiave

Dalla cartella del progetto:

```bash
powershell -ExecutionPolicy Bypass -File scripts/prova-chiave-gemini.ps1
```

Lo script chiede la chiave senza mostrarla e prova l'elenco modelli e sei modelli (gli alias
`-latest` usati da AILA e quelli con la versione). Ogni riga riporta il codice HTTP e l'inizio
della risposta di Google.

Per capire se il problema è della chiave o del progetto si può provare lo stesso progetto con
un token OAuth vero, senza chiave:

```bash
gcloud auth application-default login --scopes=https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/generative-language
curl -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" -H "x-goog-user-project: aila-gemini-123" https://generativelanguage.googleapis.com/v1beta/models
```

Se con OAuth risponde 200 e con la chiave `AQ.` 401, il progetto è a posto e il difetto è nella
chiave: cancellala e ricreala. L'app oggi non accetta token OAuth, solo chiavi.

## 5. Errori comuni

| Risposta di Google | Causa | Cosa fare |
| --- | --- | --- |
| `401 UNAUTHENTICATED` · `ACCESS_TOKEN_TYPE_UNSUPPORTED` · "Expected OAuth 2 access token" | Chiave `AQ.` non riconosciuta: service account mancante o cancellato, chiave creata male, oppure modello ritirato che Google segnala così | Ricrea la chiave in un progetto nuovo (strada 1 o 2). Se con lo script solo gli alias `-latest` danno 401, il problema è il modello, non la chiave. |
| `403 PERMISSION_DENIED` · "API has not been used" | Generative Language API non abilitata | Punto 2.1 |
| `403` · "API key not valid" / "blocked" | Restrizione API sbagliata o chiave vecchia `AIzaSy…` non restretta | Restrizione solo su Generative Language API, oppure chiave nuova |
| `400 API_KEY_INVALID` | Chiave copiata male (spazi, a capo, due chiavi incollate) | Ricopiala: AILA tiene solo la prima parola, ma deve essere intera |
| `404` sull'URL con `?key=` | Le chiavi `AQ.` funzionano solo nell'header | AILA usa già l'header; riguarda solo prove fatte a mano |
| `429 RESOURCE_EXHAUSTED` | Quota gratuita finita (al minuto o al giorno) | Aspetta: l'app riprova da sola e passa al modello successivo |

Riferimenti: [Using Gemini API keys](https://ai.google.dev/gemini-api/docs/api-key),
[Manage API keys](https://docs.cloud.google.com/docs/authentication/api-keys),
[gcloud services api-keys create](https://docs.cloud.google.com/sdk/gcloud/reference/services/api-keys/create).
