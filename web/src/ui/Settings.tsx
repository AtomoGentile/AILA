// Impostazioni: API key personale, notifiche push per categoria, uscita.
import { useEffect, useState } from 'preact/hooks';
import type { NotificationKind } from '@worker/contracts';
import { testKey } from '../ai/gemini';
import { kv } from '../lib/db';
import { isIos, isStandalone, pushSupported } from '../lib/platform';
import { NOTIFICATION_KINDS, currentSubscription, disablePush, enablePush, getMutedKinds, setMutedKinds } from '../lib/push';
import { getSession, logout } from '../lib/session';
import { PageHeader } from './common';

export function Settings() {
  const user = getSession()?.user;
  return (
    <section>
      <PageHeader title="Impostazioni" />
      {user && (
        <div class="card">
          <strong>
            {user.firstName} {user.lastName}
          </strong>
          <p class="muted small">
            @{user.username} · {user.role === 'REPRESENTATIVE' ? 'Rappresentante' : 'Studente'}
            {user.classLabel ? ` · ${user.classLabel}` : ''}
          </p>
        </div>
      )}
      <ApiKeySettings />
      <NotificationSettings />
      <StorageInfo />
      <button class="btn btn-block danger" onClick={() => void logout()}>
        Esci
      </button>
    </section>
  );
}

function ApiKeySettings() {
  const [key, setKey] = useState('');
  const [saved, setSaved] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);

  useEffect(() => {
    kv.get<string>('geminiKey').then((k) => setKey(k ?? ''));
  }, []);

  async function save() {
    await kv.set('geminiKey', key.trim());
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  async function test() {
    setTesting(true);
    setResult(null);
    setResult(await testKey(key.trim()));
    setTesting(false);
  }

  return (
    <div class="card form">
      <h2>Analisi AI delle circolari</h2>
      <p class="muted small">
        Usa la tua chiave gratuita di Google AI Studio (aistudio.google.com/apikey). Resta su questo dispositivo: il testo delle
        circolari va direttamente a Google, non passa dal server di AILA.
      </p>
      <label>
        API key Google AI Studio
        <input type="password" value={key} autocomplete="off" onInput={(e) => setKey(e.currentTarget.value)} />
      </label>
      <div class="row gap">
        <button class="btn btn-primary" onClick={save}>
          {saved ? 'Salvata ✓' : 'Salva'}
        </button>
        <button class="btn" onClick={test} disabled={testing || !key.trim()}>
          {testing ? 'Provo…' : 'Prova la chiave'}
        </button>
      </div>
      {result && <p class="small">{result}</p>}
    </div>
  );
}

function NotificationSettings() {
  const [enabled, setEnabled] = useState(false);
  const [muted, setMuted] = useState<NotificationKind[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    currentSubscription().then((s) => setEnabled(!!s && Notification.permission === 'granted')).catch(() => {});
    getMutedKinds().then(setMuted);
  }, []);

  if (!pushSupported()) {
    // Su iOS il push web esiste solo per le PWA installate (iOS 16.4+).
    return (
      <div class="card">
        <h2>Notifiche</h2>
        <p class="muted small">
          {isIos() && !isStandalone()
            ? 'Per ricevere le notifiche aggiungi AILA alla schermata Home (Condividi → Aggiungi alla schermata Home) e aprila da lì.'
            : 'Questo browser non supporta le notifiche push.'}
        </p>
      </div>
    );
  }

  async function toggle() {
    setBusy(true);
    setError(null);
    try {
      if (enabled) {
        await disablePush();
        setEnabled(false);
      } else {
        await enablePush();
        setEnabled(true);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleKind(kind: NotificationKind, on: boolean) {
    const next = on ? muted.filter((k) => k !== kind) : [...muted, kind];
    setMuted(next);
    try {
      await setMutedKinds(next);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const iosNotInstalled = isIos() && !isStandalone();

  return (
    <div class="card form">
      <h2>Notifiche</h2>
      {iosNotInstalled ? (
        <p class="muted small">Su iPhone e iPad le notifiche funzionano solo aprendo AILA dalla schermata Home.</p>
      ) : (
        <button class={`btn ${enabled ? '' : 'btn-primary'}`} onClick={toggle} disabled={busy}>
          {busy ? 'Attendi…' : enabled ? 'Disattiva le notifiche' : 'Attiva le notifiche'}
        </button>
      )}
      {error && <p class="form-error">{error}</p>}
      <p class="muted small">Categorie</p>
      {NOTIFICATION_KINDS.map((k) => (
        <label class="switch" key={k.key}>
          <span>
            <strong>{k.label}</strong>
            <span class="muted small block">{k.description}</span>
          </span>
          <input type="checkbox" checked={!muted.includes(k.key)} onChange={(e) => toggleKind(k.key, e.currentTarget.checked)} />
        </label>
      ))}
    </div>
  );
}

function StorageInfo() {
  const [persisted, setPersisted] = useState<boolean | null>(null);
  useEffect(() => {
    navigator.storage?.persisted?.().then(setPersisted).catch(() => {});
  }, []);
  if (persisted === null) return null;
  return (
    <p class="muted small center">
      Dati offline {persisted ? 'protetti dalla pulizia automatica del browser' : 'soggetti alla pulizia automatica del browser'}.
    </p>
  );
}
