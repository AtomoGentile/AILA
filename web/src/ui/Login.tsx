import { useState } from 'preact/hooks';
import { login } from '../lib/session';

export function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: Event) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main class="login">
      <img src="/icons/icon-192.png" alt="" width={88} height={88} class="login-logo" />
      <h1>AILA</h1>
      <p class="muted">Accedi con lo stesso account dell'app.</p>
      <form onSubmit={submit} class="card form">
        <label>
          Username
          <input
            value={username}
            onInput={(e) => setUsername(e.currentTarget.value)}
            autocomplete="username"
            autocapitalize="none"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onInput={(e) => setPassword(e.currentTarget.value)}
            autocomplete="current-password"
            required
          />
        </label>
        {error && <p class="form-error">{error}</p>}
        <button class="btn btn-primary" disabled={busy}>
          {busy ? 'Accesso…' : 'Accedi'}
        </button>
      </form>
      <p class="muted small">Non hai un account? Registrati dall'app AILA su Android o iOS.</p>
    </main>
  );
}
