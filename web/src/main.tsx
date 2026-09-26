import { render } from 'preact';
import { useEffect, useState } from 'preact/hooks';
import '@fontsource/sora/400.css';
import '@fontsource/sora/600.css';
import '@fontsource/sora/700.css';
import './styles.css';
import { restoreSession } from './lib/session';
import { App } from './ui/App';

let waitingWorker: ServiceWorker | null = null;
const updateListeners = new Set<() => void>();

// Registrazione del service worker (solo in produzione) e avviso quando c'è una versione nuova.
async function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  const reg = await navigator.serviceWorker.register('/sw.js', { scope: '/' });
  const track = (worker: ServiceWorker | null) => {
    worker?.addEventListener('statechange', () => {
      if (worker.state === 'installed' && navigator.serviceWorker.controller) {
        waitingWorker = worker;
        updateListeners.forEach((l) => l());
      }
    });
  };
  if (reg.waiting && navigator.serviceWorker.controller) {
    waitingWorker = reg.waiting;
  }
  reg.addEventListener('updatefound', () => track(reg.installing));

  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading) return;
    reloading = true;
    location.reload();
  });
  // Tocco su una notifica con l'app già aperta.
  navigator.serviceWorker.addEventListener('message', (event) => {
    const data = event.data as { type?: string; url?: string } | null;
    if (data?.type === 'navigate' && data.url) location.hash = new URL(data.url, location.origin).hash;
  });
}

function Root() {
  const [updateReady, setUpdateReady] = useState(!!waitingWorker);
  useEffect(() => {
    const l = () => setUpdateReady(true);
    updateListeners.add(l);
    if (waitingWorker) l();
    return () => {
      updateListeners.delete(l);
    };
  }, []);
  return <App updateReady={updateReady} onUpdate={() => waitingWorker?.postMessage('SKIP_WAITING')} />;
}

// Chiede al browser di non cancellare i dati offline sotto pressione di spazio.
navigator.storage?.persist?.().catch(() => {});

void registerServiceWorker().catch(() => {});
void restoreSession();
render(<Root />, document.getElementById('app')!);
