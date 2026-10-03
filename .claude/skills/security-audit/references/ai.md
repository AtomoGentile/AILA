# Funzioni con LLM/AI

- **Prompt injection**: il testo di documenti, pagine web, email, messaggi di altri utenti che
  entra nel prompt può contenere istruzioni. Conta solo se il modello può *fare* qualcosa
  (strumenti, azioni, accesso a dati di altri) o se la sua risposta viene usata come fidata.
  Verifica che strumenti e azioni abbiano i permessi dell'utente, non del sistema.
- **Output del modello come input non fidato**: renderizzato senza sanitizzazione (XSS),
  eseguito come codice/SQL/comandi, usato per URL scaricati dal server (SSRF), JSON accettato
  senza validazione dello schema.
- **Dati inviati al fornitore**: quali dati personali o riservati finiscono nei prompt; devono
  corrispondere a quanto dichiarato nell'informativa privacy. Dati di altri utenti non entrano
  nel contesto di chi non può vederli (attenzione a RAG e ricerca su dati condivisi tra tenant).
- **Chiavi API**: le chiavi del progetto stanno sul server; le chiavi personali degli utenti
  restano sul loro dispositivo e non transitano o vengono loggate dal backend.
- **Costi e abuso**: endpoint che chiamano un LLM con la chiave del progetto hanno
  autenticazione, rate limit e limiti alla lunghezza dell'input.
- **Log**: prompt e risposte con dati personali non finiscono nei log in chiaro.
