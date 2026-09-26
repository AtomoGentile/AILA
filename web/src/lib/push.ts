// Web Push: iscrizione del browser, preferenze per categoria, disiscrizione al logout.
import type { NotificationKind } from '@worker/contracts';
import { api } from './api';
import { kv } from './db';
import { pushSupported } from './platform';

export const NOTIFICATION_KINDS: { key: NotificationKind; label: string; description: string }[] = [
  { key: 'circulars', label: 'Circolari', description: 'Quando la scuola pubblica una circolare nuova' },
  { key: 'calendar', label: 'Calendario', description: 'Nuove verifiche, scadenze e pagamenti' },
  { key: 'board', label: 'Bacheca', description: 'Nuove proposte della classe' },
  { key: 'seatmap', label: 'Mappa posti', description: 'Nuova disposizione o votazione aperta' },
  { key: 'polls', label: 'Sondaggi', description: 'Nuovi sondaggi, interrogazioni e a ordinamento' },
];

const MUTED_KEY = 'mutedKinds';

export async function getMutedKinds(): Promise<NotificationKind[]> {
  return (await kv.get<NotificationKind[]>(MUTED_KEY)) ?? [];
}

function toKeyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const b64 = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4);
  const raw = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null;
  const reg = await navigator.serviceWorker.getRegistration();
  return (await reg?.pushManager.getSubscription()) ?? null;
}

/**
 * Da chiamare direttamente dal click del pulsante: su iOS il permesso va chiesto con un gesto
 * esplicito, e solo con la PWA installata sulla Home.
 */
export async function enablePush(): Promise<void> {
  if (!pushSupported()) throw new Error('Questo browser non supporta le notifiche push.');
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') throw new Error('Permesso per le notifiche negato. Puoi riattivarlo dalle impostazioni del browser.');

  const { publicKey } = await api<{ publicKey: string }>('/api/webpush/vapid-public-key');
  const reg = await navigator.serviceWorker.ready;
  const sub =
    (await reg.pushManager.getSubscription()) ??
    (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toKeyBytes(publicKey) }));

  const json = sub.toJSON();
  await api('/api/webpush/subscription', {
    method: 'POST',
    body: { subscription: { endpoint: sub.endpoint, keys: json.keys }, mutedKinds: await getMutedKinds() },
  });
}

/** Toglie l'iscrizione dal server e dal browser. Non lancia: serve anche al logout offline. */
export async function disablePush(): Promise<void> {
  const sub = await currentSubscription().catch(() => null);
  if (!sub) return;
  await api('/api/webpush/subscription', { method: 'DELETE', body: { endpoint: sub.endpoint } }).catch(() => {});
  await sub.unsubscribe().catch(() => {});
}

export async function setMutedKinds(muted: NotificationKind[]): Promise<void> {
  await kv.set(MUTED_KEY, muted);
  const sub = await currentSubscription();
  if (sub) await api('/api/webpush/preferences', { method: 'PUT', body: { endpoint: sub.endpoint, mutedKinds: muted } });
}
