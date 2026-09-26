/// <reference lib="webworker" />
// Service worker della PWA: shell in precache, ultimi dati /api/* per l'offline, Web Push.
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching';
import { NavigationRoute, registerRoute } from 'workbox-routing';
import { CacheFirst, NetworkFirst } from 'workbox-strategies';
import { ExpirationPlugin } from 'workbox-expiration';
import { CacheableResponsePlugin } from 'workbox-cacheable-response';
import type { WebPushPayload } from '@worker/contracts';
import { API_BASE, API_CACHE, PDF_CACHE } from './config';

declare const self: ServiceWorkerGlobalScope & { __WB_MANIFEST: Array<{ url: string; revision: string | null }> };

// --- Shell -------------------------------------------------------------------
precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();
registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html')));

const apiOrigin = new URL(API_BASE).origin;

// --- PDF delle circolari (dal Worker/R2): restano leggibili offline -------------
registerRoute(
  ({ url, request }) => request.method === 'GET' && url.origin === apiOrigin && url.pathname.startsWith('/api/circulars/pdf/'),
  new CacheFirst({
    cacheName: PDF_CACHE,
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({ maxEntries: 40, maxAgeSeconds: 30 * 24 * 60 * 60 }),
    ],
  })
);

// --- Ultimi dati letti: rete prima, cache se offline. Mai login e push. ----------
registerRoute(
  ({ url, request }) =>
    request.method === 'GET' &&
    url.origin === apiOrigin &&
    url.pathname.startsWith('/api/') &&
    !url.pathname.startsWith('/api/auth/') &&
    !url.pathname.startsWith('/api/webpush/'),
  new NetworkFirst({
    cacheName: API_CACHE,
    networkTimeoutSeconds: 6,
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
      new ExpirationPlugin({ maxEntries: 120, maxAgeSeconds: 14 * 24 * 60 * 60 }),
    ],
  })
);

// Aggiornamento su richiesta della pagina ("Aggiorna" nel banner).
self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') void self.skipWaiting();
});

// --- Web Push ------------------------------------------------------------------
// Dove portare l'utente al tocco, secondo la categoria (come la campanella dell'app).
function targetUrl(p: WebPushPayload): string {
  switch (p.kind) {
    case 'circulars':
      return p.data.circular_number ? `/#/circolari/${encodeURIComponent(p.data.circular_number)}` : '/#/circolari';
    case 'board':
      return '/#/bacheca';
    case 'calendar':
      return '/#/calendario';
    case 'seatmap':
      return '/#/mappa';
    default:
      return '/';
  }
}

self.addEventListener('push', (event) => {
  let payload: WebPushPayload = { title: 'AILA', body: '', kind: '', data: {} };
  try {
    payload = { ...payload, ...(event.data?.json() as Partial<WebPushPayload>) };
  } catch {
    payload.body = event.data?.text() ?? '';
  }
  // iOS revoca l'iscrizione se un push non mostra una notifica: la si mostra sempre.
  event.waitUntil(
    self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: payload.data.circular_number ? `circular-${payload.data.circular_number}` : undefined,
      data: { url: targetUrl(payload) },
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data as { url?: string } | null)?.url ?? '/';
  event.waitUntil(
    (async () => {
      const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      const existing = windows[0];
      if (existing) {
        await existing.focus();
        existing.postMessage({ type: 'navigate', url });
        return;
      }
      await self.clients.openWindow(url);
    })()
  );
});
