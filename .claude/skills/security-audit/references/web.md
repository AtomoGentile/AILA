# Frontend web, PWA, estensioni

## XSS
- Sink: `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`,
  `dangerouslySetInnerHTML`, `v-html`, `[innerHTML]`, `{@html}`, `eval`, `new Function`,
  `setTimeout(stringa)`, `srcdoc`, `jQuery.html()`/`$(stringa)`.
- Markdown e HTML generati da utenti o da un LLM: renderizzati con sanitizzazione (es. DOMPurify)
  o come testo.
- Attributi URL da dati (`href`, `src`, `action`, `formaction`): bloccare `javascript:` e
  `data:` dove non servono. `target="_blank"` con `rel="noopener noreferrer"`.
- `postMessage`: controllo di `event.origin` in ricezione, origine esplicita in invio.

## Header e configurazione
- Content-Security-Policy: senza `unsafe-inline`/`unsafe-eval` se possibile, `connect-src`
  limitato ai backend reali, `frame-ancestors`, `object-src 'none'`, `base-uri`.
- `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`, HSTS.
- Sourcemap di produzione pubbliche: accettabili solo se il codice è comunque pubblico.

## Segreti nel client
- Tutto ciò che sta nel bundle è pubblico: cerca chiavi di servizi (variabili `VITE_`,
  `NEXT_PUBLIC_`, `REACT_APP_`) che non dovrebbero esserlo. Chiavi "pubbliche per design"
  (Firebase web config, chiavi publishable) vanno bene solo se le regole lato server le limitano.
- Le chiavi personali che l'utente inserisce restano nel suo browser e vanno solo al servizio
  per cui servono.

## Sessione e storage
- Token in `localStorage`/IndexedDB sono leggibili da qualsiasi XSS: accettabile solo con CSP
  stretta e nessun sink XSS; altrimenti cookie `HttpOnly`.
- Logout: cancella token, dati utente, cache (anche del service worker), registrazioni push.
- Nessun dato sensibile in URL (finisce in cronologia, log e `Referer`).

## Service worker e cache
- Le risposte autenticate non finiscono in cache condivise tra utenti dello stesso dispositivo
  dopo il logout; attenzione a cache HTTP `public` su risposte personali.
- Lo scope del service worker non è più ampio del necessario.

## Controlli solo lato client
Qualsiasi controllo fatto solo nel frontend (pulsante nascosto, ruolo letto dal token decodificato,
validazione) va verificato anche sul server: altrimenti è un problema del backend.
