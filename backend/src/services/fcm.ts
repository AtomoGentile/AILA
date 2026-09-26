// =============================================================================
// AILA — Firebase Cloud Messaging Service (HTTP v1 API)
//
// Google ha dismesso la "Legacy HTTP API" di FCM (endpoint fcm.googleapis.com/fcm/send,
// autenticata con una singola "server key") il 20 giugno 2024. Qualsiasi progetto Firebase
// creato dopo quella data — incluso quello che Simone creerà per AILA — può inviare
// notifiche SOLO tramite la nuova HTTP v1 API, autenticata con un Service Account (OAuth2),
// non più con una stringa segreta statica. Questo file implementa quel flusso da zero con
// WebCrypto nativo (nessuna dipendenza esterna, come per auth.ts).
//
// Configurazione richiesta (wrangler secret put <NOME>):
//   FCM_PROJECT_ID          — Project ID del progetto Firebase (es. "circolare-plus-xxxxx")
//   FCM_SERVICE_ACCOUNT_KEY — contenuto JSON del Service Account scaricato da
//                             Firebase Console → Impostazioni progetto → Account di servizio →
//                             "Genera nuova chiave privata" (contiene client_email e private_key)
//
// Finché questi due secret non sono configurati, notifyClass/notifyUser non fanno nulla
// (loggano soltanto) invece di lanciare un errore: l'app resta utilizzabile senza notifiche
// push fino a quando Simone non crea il progetto Firebase.
// =============================================================================

import type { Env } from '../types';

interface FcmMessage {
  title: string;
  body: string;
  data?: Record<string, string>;
}

// Un destinatario: il token FCM e la piattaforma con cui e' stato registrato (colonna
// fcm_tokens.platform, "android" | "ios"). Serve a scegliere la forma del messaggio.
interface FcmRecipient {
  token: string;
  platform: string | null;
  // Preferenze del dispositivo (migrazione 009). Assenti se la migrazione non e' ancora applicata.
  muted_kinds?: string | null;
  system_notifications?: number | null;
}

/**
 * Stessa logica di NotificationCategoryMapper (app, codice condiviso Kotlin): dal payload `data`
 * alla categoria delle Impostazioni ("circulars", "board", "seatmap", "polls" o "" se nessuna).
 * "seatmap_preferences" dell'app qui e' gia' "seatmap", la voce dell'interruttore.
 */
function notificationKind(data: Record<string, string>): string {
  switch (data.action) {
    case 'open_preferences':
    case 'preferences_complete':
    case 'seat_map_updated':
      return 'seatmap';
    case 'poll_published':
    case 'poll_complete':
    case 'swap_request':
    case 'swap_accepted':
      return 'polls';
    case 'new_circular':
      return 'circulars';
    case 'new_proposal':
      return 'board';
  }
  if ('circular_number' in data) return 'circulars';
  if ('proposal_id' in data) return 'board';
  if ('poll_id' in data || 'swap_id' in data || 'grid_id' in data) return 'polls';
  return '';
}

/**
 * Legge i destinatari con le loro preferenze; se la migrazione 009 non e' ancora applicata le
 * colonne non esistono e la query fallisce: si ripiega sulla stessa query senza preferenze, cosi'
 * le notifiche continuano ad arrivare come prima.
 */
async function queryRecipients(env: Env, where: string, binds: unknown[]): Promise<FcmRecipient[]> {
  const run = (columns: string) =>
    env.DB.prepare(`SELECT DISTINCT ${columns} FROM fcm_tokens t ${where}`).bind(...binds).all<FcmRecipient>();
  try {
    return (await run('t.token, t.platform, t.muted_kinds, t.system_notifications')).results;
  } catch (e) {
    return (await run('t.token, t.platform')).results;
  }
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id?: string;
}

// Cache dell'access token OAuth2 in memoria del Worker isolate (evita di richiederne uno nuovo
// ad ogni notifica: i token durano 1h, li rinnoviamo 60s prima della scadenza per prudenza).
let cachedAccessToken: { token: string; expiresAt: number } | null = null;

const base64url = {
  encode: (buf: ArrayBuffer | Uint8Array): string => {
    const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
    return btoa(String.fromCharCode(...bytes))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  },
};

function parseServiceAccount(env: Env): ServiceAccount | null {
  if (!env.FCM_SERVICE_ACCOUNT_KEY) return null;
  try {
    const parsed = JSON.parse(env.FCM_SERVICE_ACCOUNT_KEY) as ServiceAccount;
    if (!parsed.client_email || !parsed.private_key) {
      console.error(
        `[FCM] FCM_SERVICE_ACCOUNT_KEY è un JSON valido ma incompleto (client_email presente: ${!!parsed.client_email}, private_key presente: ${!!parsed.private_key}) — assicurati che sia il file JSON completo scaricato da Firebase Console → Account di servizio`
      );
      return null;
    }
    return parsed;
  } catch (e) {
    console.error(
      `[FCM] FCM_SERVICE_ACCOUNT_KEY non è un JSON valido (lunghezza: ${env.FCM_SERVICE_ACCOUNT_KEY.length}): ${e instanceof Error ? e.message : e}`
    );
    return null;
  }
}

/** Converte la chiave privata PEM (formato PKCS#8, quello dei Service Account Google) in un CryptoKey RSA. */
async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const pemBody = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  const binaryDer = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));

  return crypto.subtle.importKey(
    'pkcs8',
    binaryDer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
}

/** Firma un JWT RS256 di richiesta token OAuth2 ("self-signed JWT" per i Service Account Google). */
async function signServiceAccountJwt(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = {
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  };

  const encHeader = base64url.encode(new TextEncoder().encode(JSON.stringify(header)));
  const encClaims = base64url.encode(new TextEncoder().encode(JSON.stringify(claims)));
  const signingInput = `${encHeader}.${encClaims}`;

  const key = await importPrivateKey(sa.private_key);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput));

  return `${signingInput}.${base64url.encode(signature)}`;
}

/** Scambia il JWT firmato per un access token OAuth2 valido ~1h, con cache in memoria. */
async function getAccessToken(sa: ServiceAccount): Promise<string | null> {
  if (cachedAccessToken && cachedAccessToken.expiresAt > Date.now() + 60_000) {
    return cachedAccessToken.token;
  }

  try {
    const assertion = await signServiceAccountJwt(sa);
    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion,
      }),
    });

    if (!res.ok) {
      console.error('[FCM] Scambio token OAuth2 fallito:', await res.text());
      return null;
    }

    const json = await res.json<{ access_token: string; expires_in: number }>();
    cachedAccessToken = { token: json.access_token, expiresAt: Date.now() + json.expires_in * 1000 };
    return json.access_token;
  } catch (e) {
    console.error('[FCM] Errore durante l\'ottenimento dell\'access token', e);
    return null;
  }
}

/**
 * Costruisce il messaggio nella forma giusta per la piattaforma.
 *
 * Android: SOLO `data`, senza blocco `notification`. Con `notification` e app in background FCM
 * mostra la notifica da solo e `onMessageReceived` non parte mai, quindi la campanella in-app
 * (che vive solo sul telefono, vedi LocalSettingsManager.onPushReceived) restava vuota. Con un
 * messaggio data-only il servizio gira sempre, in primo piano e in background, applica gli
 * interruttori dell'utente, scrive la cronologia e mostra lui la notifica. Priorita' HIGH:
 * senza, in Doze i messaggi data-only vengono ritardati.
 *
 * iOS (e piattaforma sconosciuta): `notification` + `data`, come prima; iOS non ha un servizio
 * equivalente e la notifica la mostra il sistema. Il suono va chiesto esplicitamente.
 */
function buildMessage(
  target: { token: string } | { topic: string },
  recipient: FcmRecipient,
  message: FcmMessage
): Record<string, unknown> | null {
  const data = message.data ?? {};

  if (recipient.platform === 'android') {
    return {
      ...target,
      // title/body dopo i data: quelli "veri" non devono poter essere sovrascritti da chiavi omonime.
      data: { ...data, title: message.title, body: message.body },
      android: { priority: 'HIGH' },
    };
  }

  // iOS: le preferenze le applica il server, perche' con l'app in background il banner lo
  // mostra il sistema senza passare dall'app. Categoria silenziata: non si manda nulla (come su
  // Android, dove onPushReceived la scarta senza scriverla in campanella).
  const kind = notificationKind(data);
  const muted = (recipient.muted_kinds ?? '').split(',').filter(Boolean);
  if (kind && muted.includes(kind)) return null;

  // "Notifiche di sistema" spento: push silenzioso (content-available), niente banner. L'app,
  // se iOS la sveglia, lo scrive solo nella campanella (AppDelegate, didReceiveRemoteNotification).
  if (recipient.system_notifications === 0) {
    return {
      ...target,
      data: { ...data, title: message.title, body: message.body },
      apns: {
        headers: { 'apns-push-type': 'background', 'apns-priority': '5' },
        payload: { aps: { 'content-available': 1 } },
      },
    };
  }

  return {
    ...target,
    notification: { title: message.title, body: message.body },
    data,
    // content-available: con l'app sospesa iOS la sveglia qualche secondo, cosi' la notifica
    // finisce in campanella anche se l'utente non la tocca (vedi AppDelegate).
    apns: { payload: { aps: { sound: 'default', 'content-available': 1 } } },
  };
}

async function sendV1(
  env: Env,
  accessToken: string,
  projectId: string,
  recipient: FcmRecipient,
  message: FcmMessage
): Promise<boolean> {
  const built = buildMessage({ token: recipient.token }, recipient, message);
  if (!built) return true; // Categoria silenziata su questo dispositivo: niente da inviare.
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message: built }),
  });

  if (!res.ok) {
    console.error('[FCM] Invio fallito:', await res.text());
  }
  return res.ok;
}

async function resolveCredentials(env: Env): Promise<{ accessToken: string; projectId: string } | null> {
  const sa = parseServiceAccount(env);
  if (!sa) {
    if (!env.FCM_SERVICE_ACCOUNT_KEY) {
      console.log('[FCM] FCM_SERVICE_ACCOUNT_KEY non configurata: notifica saltata (Firebase non ancora impostato)');
    } else {
      console.log('[FCM] FCM_SERVICE_ACCOUNT_KEY presente ma non valida: notifica saltata (vedi errore sopra)');
    }
    return null;
  }

  const projectId = env.FCM_PROJECT_ID || sa.project_id;
  if (!projectId) {
    console.error('[FCM] Impossibile determinare il project ID (imposta FCM_PROJECT_ID)');
    return null;
  }

  const accessToken = await getAccessToken(sa);
  if (!accessToken) return null;

  return { accessToken, projectId };
}

// ---------------------------------------------------------------------------
// Public API — invariata rispetto a prima, cambia solo l'implementazione interna
// ---------------------------------------------------------------------------

/**
 * Notify the entire class (or, without a classId, every registered device).
 *
 * Nota: si inviava un unico messaggio al topic FCM "class", ma nessun client si iscrive mai a
 * quel topic (né Android né iOS chiamano subscribeToTopic da nessuna parte) — quindi il topic
 * non ha mai avuto iscritti. FCM risponde comunque "ok" a un invio a un topic senza iscritti,
 * quindi l'errore passava inosservato: le circolari (uniche chiamanti senza classId) non
 * generavano mai una notifica push reale. Si manda direttamente token per token.
 */
export async function notifyClass(
  env: Env,
  title: string,
  body: string,
  data?: Record<string, string>,
  // Classe destinataria. Se assente la notifica va a tutti, com'era prima del multi-classe:
  // serve solo alle notifiche che riguardano davvero l'istituto intero (le circolari), non a
  // quelle di classe. Chi chiama e non la passa deve avere una ragione.
  classId?: string
): Promise<void> {
  const message: FcmMessage = { title, body, data };
  console.log(`[FCM] Notifica classe${classId ? ` ${classId}` : ' (tutte)'}: ${title} — ${body}`);

  const creds = await resolveCredentials(env);
  if (!creds) return;

  if (!classId) {
    const all = await queryRecipients(env, '', []);
    await Promise.allSettled(
      all.map((row) => sendV1(env, creds.accessToken, creds.projectId, row, message))
    );
    return;
  }

  // Con una classe indicata non si può usare il topic 'class': è unico e raggiungerebbe anche
  // gli iscritti delle altre classi. Si mandano i messaggi ai token di quella classe soltanto.
  // DISTINCT (in queryRecipients): lo stesso telefono può comparire sotto più account (vedi
  // routes/fcm.ts), e senza raggruppare per token la stessa notifica partiva una volta per account.
  const tokens = await queryRecipients(env, 'JOIN users u ON u.id = t.user_id WHERE u.class_id = ?', [classId]);

  await Promise.allSettled(
    tokens.map((row) => sendV1(env, creds.accessToken, creds.projectId, row, message))
  );
}

/**
 * Notify a single user by userId.
 */
export async function notifyUser(env: Env, userId: string, title: string, body: string, data?: Record<string, string>): Promise<void> {
  const creds = await resolveCredentials(env);
  if (!creds) return;

  const tokens = await queryRecipients(env, 'WHERE t.user_id = ?', [userId]);

  const message: FcmMessage = { title, body, data };
  await Promise.allSettled(
    tokens.map((row) => sendV1(env, creds.accessToken, creds.projectId, row, message))
  );
}

/**
 * Notifica un gruppo di utenti (es. i destinatari di un sondaggio rivolto solo ad alcuni).
 * Le credenziali si risolvono una volta sola; lo stesso telefono sotto più account riceve la
 * notifica una volta (DISTINCT in queryRecipients).
 */
export async function notifyUsers(
  env: Env,
  userIds: string[],
  title: string,
  body: string,
  data?: Record<string, string>
): Promise<void> {
  if (userIds.length === 0) return;
  const creds = await resolveCredentials(env);
  if (!creds) return;

  const message: FcmMessage = { title, body, data };
  // D1 limita i parametri per query: si procede a blocchi.
  const CHUNK = 50;
  for (let i = 0; i < userIds.length; i += CHUNK) {
    const chunk = userIds.slice(i, i + CHUNK);
    const tokens = await queryRecipients(
      env,
      `WHERE t.user_id IN (${chunk.map(() => '?').join(', ')})`,
      chunk
    );
    await Promise.allSettled(
      tokens.map((row) => sendV1(env, creds.accessToken, creds.projectId, row, message))
    );
  }
}
