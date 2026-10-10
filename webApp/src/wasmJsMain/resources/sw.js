// =============================================================================
// AILA — Service worker della PWA (JavaScript puro: niente build step, si pubblica cosi' com'e').
//
// Fa quattro cose:
//  1. offline della shell: index.html, aila.js, i .wasm, composeResources e icone restano in
//     cache e l'app riparte anche senza rete. Niente elenco di precache generato a build: la cache
//     si riempie a runtime (stale-while-revalidate) e all'installazione si scaricano solo i pochi
//     file fissi della shell piu' i .wasm che aila.js nomina;
//  2. Web Push: mostra SEMPRE la notifica (iOS revoca l'iscrizione se un push non ne mostra una),
//     la mette nella "casella" letta dall'app per la campanella e avvisa le finestre aperte;
//  3. tocco sulla notifica: porta avanti la finestra aperta (o ne apre una) sulla schermata giusta;
//  4. controllo periodico delle circolari nuove dove il browser lo permette (Periodic Background
//     Sync: Chrome/Edge con la PWA installata), come il refresh in background dell'app iOS.
//
// Il SW non vede il localStorage dell'app: quello che gli serve (token di sessione, URL del Worker,
// preferenze notifiche, segnalibro delle circolari) l'app lo copia nel database IndexedDB
// 'aila-sw' (vedi circolareplus.web.SwBridge in shared/src/wasmJsMain) e lo cancella al logout.
//
// Le chiamate al Worker NON passano dalla cache di questo file: hanno gia' la loro copia offline
// nell'app (OfflineStore), e una seconda cache qui servirebbe dati di un altro account o vecchi.
// =============================================================================

'use strict';

// Da aumentare solo se cambia il modo di usare la cache: la vecchia viene cancellata
// all'attivazione. Per un aggiornamento normale dell'app NON serve (vedi staleWhileRevalidate).
const SHELL_CACHE = 'aila-shell-v2';
const DB_NAME = 'aila-sw';
const DB_VERSION = 1;
const SYNC_TAG = 'aila-sync';
const INBOX_MAX = 50;

const scopeUrl = new URL(self.registration.scope);
const indexUrl = new URL('index.html', scopeUrl).href;

// File fissi della shell, scaricati all'installazione. I nomi con hash (.wasm) si ricavano da
// aila.js, cosi' non serve generare nulla a build.
const SHELL_FILES = ['./', 'index.html', 'aila.js', 'ios-keyboard.js', 'styles.css', 'manifest.webmanifest',
  'icons/icon-192.png', 'icons/favicon-32.png', 'icons/apple-touch-icon.png'];

// --- IndexedDB (ponte con l'app) ------------------------------------------------------------

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    // Stesso schema creato dall'app (SwBridge): chi arriva prima crea gli archivi.
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains('kv')) db.createObjectStore('kv');
      if (!db.objectStoreNames.contains('inbox')) db.createObjectStore('inbox', { keyPath: 'id' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function withStore(name, mode, work) {
  const db = await openDb();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(name, mode);
      let result;
      Promise.resolve(work(tx.objectStore(name))).then((r) => { result = r; }, reject);
      tx.oncomplete = () => resolve(result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally {
    db.close();
  }
}

function reqValue(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function kvGet(key) {
  return withStore('kv', 'readonly', (store) => reqValue(store.get(key)));
}

async function kvSet(key, value) {
  return withStore('kv', 'readwrite', (store) => { store.put(value, key); });
}

async function kvDelete(key) {
  return withStore('kv', 'readwrite', (store) => { store.delete(key); });
}

// La campanella dell'app legge da qui i push arrivati ad app chiusa (come fa Android dal suo
// servizio FCM). Si tengono solo gli ultimi INBOX_MAX: l'app svuota la casella a ogni apertura.
async function inboxAdd(entry) {
  return withStore('inbox', 'readwrite', async (store) => {
    store.put(entry);
    const keys = await reqValue(store.getAllKeys());
    if (keys.length > INBOX_MAX) {
      const all = await reqValue(store.getAll());
      all.sort((a, b) => a.at - b.at);
      for (const old of all.slice(0, all.length - INBOX_MAX)) store.delete(old.id);
    }
  });
}

// --- Shell offline ----------------------------------------------------------------------------

// Solo risposte complete e dello stesso sito: niente 206 (Range), opache, redirect o errori.
function isCacheable(response) {
  return response && response.status === 200 && response.type === 'basic' && !response.redirected;
}

// aila.js non ha hash nel nome ma punta ai .wasm che ce l'hanno: se si mettesse in cache la nuova
// aila.js senza i suoi .wasm, al prossimo avvio offline l'app cercherebbe file mai scaricati.
// Prima i .wasm, poi aila.js: la versione in cache resta sempre completa. Solo i nomi con hash:
// aila.js cita anche nomi che non sono file pubblicati (es. "skiko.wasm", nome interno del modulo).
async function cacheScriptWithWasm(cache, request, response) {
  const text = await response.clone().text();
  const names = new Set();
  for (const m of text.matchAll(/["'`]([0-9a-f]{16,}\.wasm)["'`]/g)) names.add(m[1]);
  for (const name of names) {
    const wasmUrl = new URL(name, request.url).href;
    if (await cache.match(wasmUrl)) continue;
    const wasm = await fetch(wasmUrl);
    if (!isCacheable(wasm)) throw new Error(`wasm non scaricato: ${name}`);
    await cache.put(wasmUrl, wasm);
  }
  await cache.put(request, response);
}

async function putInCache(request, response) {
  const cache = await caches.open(SHELL_CACHE);
  const url = new URL(request.url);
  if (url.pathname.endsWith('.js') && !url.pathname.endsWith('/sw.js')) {
    await cacheScriptWithWasm(cache, request, response);
  } else {
    await cache.put(request, response);
  }
}

// Copia in cache e, se aila.js e' cambiata, avvisa le finestre: la versione nuova dell'app vale
// dal prossimo avvio (la pagina aperta continua con quella che ha gia' caricato).
async function storeFresh(request, fresh, cached) {
  // Stesso ETag/Last-Modified di quella in cache: niente da riscrivere.
  if (cached && sameVersion(cached, fresh)) return;
  const changed = !!cached && isNewVersion(cached, fresh);
  await putInCache(request, fresh);
  if (changed && /\/aila\.js$/.test(new URL(request.url).pathname)) {
    await broadcast({ type: 'aila-app-updated' });
  }
}

function versionOf(response) {
  return response.headers.get('etag') || response.headers.get('last-modified');
}

function sameVersion(cached, fresh) {
  const a = versionOf(cached);
  return !!a && a === versionOf(fresh);
}

function isNewVersion(cached, fresh) {
  const a = versionOf(cached);
  const b = versionOf(fresh);
  return !!a && !!b && a !== b;
}

// Stale-while-revalidate: dalla cache subito (avvio istantaneo, anche offline), e intanto si
// riscarica la copia per la volta dopo. Senza copia si va in rete. La copia per la cache si fa
// appena arriva la risposta (prima che la pagina la legga) e si salva senza farla aspettare.
function staleWhileRevalidate(event) {
  const request = event.request;
  const cachedP = cacheLookup(request);
  const network = fetch(request).then((response) => ({
    response,
    copy: isCacheable(response) ? response.clone() : null,
  }));
  event.waitUntil(
    network
      .then(async ({ copy }) => { if (copy) await storeFresh(request, copy, await cachedP); })
      .catch(() => {})
  );
  return cachedP.then((cached) => cached || network.then((r) => r.response));
}

// Un errore della cache non deve mai diventare un errore di rete per la pagina: si va in rete.
function cacheLookup(request) {
  return caches.match(request).catch(() => undefined);
}

async function cacheFirst(event) {
  const request = event.request;
  const cached = await cacheLookup(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (isCacheable(response)) {
    // In parallelo: la pagina compila il .wasm mentre arriva, senza aspettare la copia in cache.
    const copy = response.clone();
    event.waitUntil(caches.open(SHELL_CACHE).then((cache) => cache.put(request, copy)).catch(() => {}));
  }
  return response;
}

// Navigazione: prima la rete (cosi' un index.html nuovo si vede subito), la copia in cache se
// offline o se la rete non risponde entro NAV_TIMEOUT_MS (Wi-Fi della scuola "connesso" ma
// fermo: senza tetto l'app restava sulla pagina bianca fino al timeout del browser). Offline
// qualunque indirizzo dentro l'app (anche ?push=... dal tocco su una notifica) riceve la stessa
// index.html: la PWA ha una sola pagina.
const NAV_TIMEOUT_MS = 4000;

async function navigation(event) {
  const network = fetch(event.request).then((response) => {
    if (isCacheable(response) && new URL(response.url).pathname.replace(/index\.html$/, '') === scopeUrl.pathname) {
      const copy = response.clone();
      event.waitUntil(caches.open(SHELL_CACHE).then((cache) => cache.put(indexUrl, copy)).catch(() => {}));
    }
    return response;
  });
  const fallback = async () => (await cacheLookup(indexUrl)) || (await cacheLookup(scopeUrl.href));
  let timer;
  const slow = new Promise((resolve) => { timer = setTimeout(resolve, NAV_TIMEOUT_MS); })
    .then(fallback)
    .then((cached) => cached || network);
  try {
    return await Promise.race([network, slow]);
  } catch (e) {
    const cached = await fallback();
    if (cached) return cached;
    throw e;
  } finally {
    clearTimeout(timer);
    // La risposta arrivata dopo il tetto aggiorna comunque la copia per la prossima volta.
    event.waitUntil(network.catch(() => {}));
  }
}

self.addEventListener('install', (event) => {
  // Niente skipWaiting qui: la versione nuova del SW subentra quando l'app lo chiede (vedi il
  // messaggio SKIP_WAITING), cosi' non cambia sotto i piedi di una pagina in uso.
  event.waitUntil((async () => {
    let cache;
    try {
      cache = await caches.open(SHELL_CACHE);
    } catch (e) {
      // Cache Storage non disponibile (disco pieno, profilo danneggiato): il SW si installa
      // comunque, perche' le notifiche non devono dipendere dalla cache della shell.
      return;
    }
    for (const file of SHELL_FILES) {
      const request = new Request(new URL(file, scopeUrl).href, { cache: 'reload' });
      try {
        const response = await fetch(request);
        if (isCacheable(response)) await putInCache(request, response);
      } catch (e) {
        // Un file mancante non deve impedire l'installazione: si riempira' a runtime.
      }
    }
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    try {
      const names = await caches.keys();
      await Promise.all(names.filter((n) => n.startsWith('aila-') && n !== SHELL_CACHE).map((n) => caches.delete(n)));
    } catch (e) {
      // Vedi install: senza Cache Storage si va avanti lo stesso.
    }
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  // Il Worker e Google AI Studio sono di un'altra origine: si lasciano alla rete (e all'app).
  if (url.origin !== scopeUrl.origin) return;
  // Un Worker servito dalla stessa origine (URL personalizzato nelle Impostazioni) resta escluso.
  if (url.pathname.startsWith('/api/')) return;
  if (request.headers.has('range')) return;
  if (url.pathname.endsWith('/sw.js')) return;

  if (request.mode === 'navigate') {
    event.respondWith(navigation(event));
    return;
  }
  // I .wasm hanno l'hash del contenuto nel nome (webpack): non cambiano mai, inutile
  // riscaricare decine di MB a ogni avvio per scoprirlo.
  if (/\/[0-9a-f]{16,}\.wasm$/.test(url.pathname)) {
    event.respondWith(cacheFirst(event));
    return;
  }
  event.respondWith(staleWhileRevalidate(event));
});

// --- Messaggi dall'app --------------------------------------------------------------------------

self.addEventListener('message', (event) => {
  const msg = event.data || {};
  if (msg.type === 'SKIP_WAITING') {
    self.skipWaiting();
  } else if (msg.type === 'aila-cache-urls' && Array.isArray(msg.urls)) {
    // File che la pagina ha caricato prima che questo SW la controllasse (primo avvio): senza,
    // fino al secondo avvio online mancherebbero alla shell offline (font, composeResources).
    event.waitUntil(cacheMissing(msg.urls));
  }
});

async function cacheMissing(urls) {
  const cache = await caches.open(SHELL_CACHE);
  for (const raw of urls.slice(0, 200)) {
    try {
      const url = new URL(raw, scopeUrl);
      if (url.origin !== scopeUrl.origin || url.pathname.endsWith('/sw.js')) continue;
      if (await cache.match(url.href)) continue;
      const response = await fetch(url.href);
      if (isCacheable(response)) await putInCache(new Request(url.href), response);
    } catch (e) {
      // Si riprova al prossimo avvio.
    }
  }
}

async function broadcast(message) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of windows) client.postMessage(message);
}

// --- Web Push -------------------------------------------------------------------------------------

function newId() {
  return (self.crypto && self.crypto.randomUUID) ? self.crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
}

// Contratto: WebPushPayload in backend/src/contracts.ts ({ title, body, kind, data }).
function parsePayload(event) {
  const payload = { title: 'AILA', body: '', kind: '', data: {} };
  if (!event.data) return payload;
  try {
    const json = event.data.json();
    if (typeof json.title === 'string' && json.title) payload.title = json.title;
    if (typeof json.body === 'string') payload.body = json.body;
    if (typeof json.kind === 'string') payload.kind = json.kind;
    if (json.data && typeof json.data === 'object') {
      for (const [k, v] of Object.entries(json.data)) payload.data[k] = String(v);
    }
  } catch (e) {
    payload.body = event.data.text();
  }
  return payload;
}

// Una notifica per elemento (stessa circolare, stessa proposta...): un secondo push sullo stesso
// argomento sostituisce il primo invece di accumularsi.
function tagOf(payload) {
  const d = payload.data;
  if (d.circular_number) return `circular-${d.circular_number}`;
  if (d.proposal_id) return `proposal-${d.proposal_id}`;
  if (d.poll_id) return `poll-${d.poll_id}`;
  return undefined;
}

async function handlePush(payload) {
  const id = newId();
  // Prima la notifica: e' l'unica cosa obbligatoria, il resto non deve poterla impedire. Se
  // fallisce (permesso revocato nel frattempo) campanella e rinfresco dei dati servono lo stesso.
  const shown = self.registration.showNotification(payload.title, {
    body: payload.body,
    icon: new URL('icons/icon-192.png', scopeUrl).href,
    badge: new URL('icons/icon-192.png', scopeUrl).href,
    tag: tagOf(payload),
    renotify: !!tagOf(payload),
    lang: 'it',
    data: { kind: payload.kind, data: payload.data },
  }).catch(() => {});
  try {
    await inboxAdd({ id, title: payload.title, body: payload.body, kind: payload.kind, data: payload.data, at: Date.now() });
    // Circolare gia' annunciata dal push: il controllo periodico non deve annunciarla di nuovo.
    const number = parseInt(payload.data.circular_number || '', 10);
    if (number > 0) {
      const current = (await kvGet('swLastCircular')) || 0;
      if (number > current) await kvSet('swLastCircular', number);
    }
  } catch (e) {
    // IndexedDB non disponibile: la notifica c'e' comunque, manca solo la voce in campanella.
  }
  // Le finestre aperte rileggono subito i dati (DataRefreshEvents) e svuotano la casella.
  await broadcast({ type: 'aila-push', id });
  await shown;
}

self.addEventListener('push', (event) => {
  event.waitUntil(handlePush(parsePayload(event)));
});

// --- Tocco sulla notifica ---------------------------------------------------------------------

async function openFromNotification(notification) {
  const info = notification.data || {};
  const message = { type: 'aila-open', kind: info.kind || '', data: info.data || {} };
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const mine = windows.filter((c) => c.url.startsWith(scopeUrl.href));
  const target = mine.find((c) => c.focused) || mine.find((c) => c.visibilityState === 'visible') || mine[0];
  if (target) {
    // La pagina e' gia' avviata: la schermata la sceglie l'app (NotificationCategoryMapper).
    target.postMessage(message);
    try {
      await target.focus();
    } catch (e) {
      // Alcuni browser rifiutano focus() se la finestra e' in un'altra app: il messaggio e' arrivato.
    }
    return;
  }
  // Avvio a freddo: l'app legge ?push=... all'avvio (installWebLifecycle) e lo toglie dall'indirizzo.
  const url = new URL(scopeUrl.href);
  url.searchParams.set('push', JSON.stringify({ kind: message.kind, data: message.data }));
  await self.clients.openWindow(url.href);
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(openFromNotification(event.notification));
});

// --- Iscrizione cambiata dal browser ----------------------------------------------------------

// Il browser puo' rinnovare (o invalidare) l'iscrizione da solo: senza questa ri-registrazione il
// server continuerebbe a mandare all'endpoint vecchio, che risponde 410 e viene cancellato.
async function resubscribe(event) {
  const token = await kvGet('token');
  const apiBase = await kvGet('apiBase');
  if (!token || !apiBase) return;
  let subscription = event.newSubscription;
  if (!subscription) {
    const options = event.oldSubscription && event.oldSubscription.options;
    let key = options && options.applicationServerKey;
    if (!key) {
      const res = await fetch(`${apiBase}/api/webpush/vapid-public-key`);
      if (!res.ok) return;
      key = base64UrlToBytes((await res.json()).publicKey);
    }
    subscription = await self.registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  }
  const json = subscription.toJSON();
  const res = await fetch(`${apiBase}/api/webpush/subscription`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ subscription: { endpoint: json.endpoint, keys: json.keys }, mutedKinds: (await kvGet('muted')) || [] }),
  });
  if (res.status === 401) await kvDelete('token');
}

function base64UrlToBytes(base64url) {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4);
  const raw = atob(b64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

self.addEventListener('pushsubscriptionchange', (event) => {
  event.waitUntil(resubscribe(event).catch(() => {}));
});

// --- Controllo periodico delle circolari --------------------------------------------------------
// Stessa logica del refresh in background iOS (iosMain BackgroundSync.kt + AilaBackground.swift):
// le circolari con numero maggiore del segnalibro, una notifica riassuntiva "N nuove circolari",
// rispettando gli interruttori delle Impostazioni. Il segnalibro e' il massimo fra quello del SW
// e l'ultimo numero visto dall'app (campanella): cosi' non si annuncia cio' che si e' gia' visto.

async function checkNewCirculars() {
  const token = await kvGet('token');
  const apiBase = await kvGet('apiBase');
  if (!token || !apiBase) return;
  const after = Math.max((await kvGet('swLastCircular')) || 0, (await kvGet('appLastCircular')) || 0);
  // Mai visto nulla: si aspetta che l'app fissi il punto di partenza, senza annunciare l'arretrato.
  if (after <= 0) return;

  const res = await fetch(`${apiBase}/api/circulars/newer?after=${after}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 401) {
    // Sessione non piu' valida: inutile riprovare finche' l'app non rientra.
    await kvDelete('token');
    return;
  }
  if (!res.ok) return;
  const circulars = ((await res.json()).circulars || []).filter((c) => typeof c.number === 'number');
  await kvSet('lastSyncAt', Date.now());
  if (circulars.length === 0) return;
  await kvSet('swLastCircular', Math.max(...circulars.map((c) => c.number)));

  const muted = (await kvGet('muted')) || [];
  const system = (await kvGet('system')) !== false;
  if (!system || muted.includes('circulars') || Notification.permission !== 'granted') return;

  const count = circulars.length;
  await self.registration.showNotification('AILA', {
    body: count === 1 ? `Circolare n. ${circulars[0].number}: ${circulars[0].title}` : `${count} nuove circolari`,
    icon: new URL('icons/icon-192.png', scopeUrl).href,
    badge: new URL('icons/icon-192.png', scopeUrl).href,
    tag: 'aila-new-circulars',
    lang: 'it',
    // Il tocco apre le Circolari (NotificationCategoryMapper: action=new_circular).
    data: { kind: 'circulars', data: { action: 'new_circular' } },
  });
}

self.addEventListener('periodicsync', (event) => {
  if (event.tag === SYNC_TAG) event.waitUntil(checkNewCirculars().catch(() => {}));
});
