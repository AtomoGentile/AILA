// =============================================================================
// AILA — Pagina di amministrazione (GET /admin)
//
// Per emettere i codici Rappresentante dal browser, anche dal telefono, invece che dal terminale.
// La pagina non contiene niente di segreto: chi la apre deve scrivere ADMIN_SECRET, che il browser
// manda alle rotte /api/admin (stessa origine, header X-Admin-Secret) e non salva da nessuna parte
// (il gestore di password puo' ricordarlo, se lo si vuole). I tentativi sbagliati sono limitati
// dalle rotte stesse.
// =============================================================================

import type { Context } from 'hono';
import type { Env } from '../types';

export function adminPage(c: Context<{ Bindings: Env }>) {
  const nonce = crypto.randomUUID().replace(/-/g, '');
  c.header(
    'Content-Security-Policy',
    `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}'; connect-src 'self'; ` +
      "img-src 'none'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'"
  );
  c.header('Cache-Control', 'no-store');
  c.header('Referrer-Policy', 'no-referrer');
  c.header('X-Robots-Tag', 'noindex, nofollow');
  c.header('X-Content-Type-Options', 'nosniff');
  return c.html(PAGE.replace(/__NONCE__/g, nonce));
}

const PAGE = `<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>AILA · Codici Rappresentante</title>
<style nonce="__NONCE__">
  :root {
    --bg: #f5f6fa; --card: #ffffff; --text: #16182b; --muted: #5d6178; --line: #dfe2ec;
    --accent: #4f46e5; --accent-ink: #ffffff; --danger: #b42318; --ok: #067647; --code-bg: #eef0fb;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0f1020; --card: #1a1c30; --text: #eceefa; --muted: #a3a7c2; --line: #2d3050;
      --accent: #8b85ff; --accent-ink: #0f1020; --danger: #ff8a80; --ok: #6ee7a8; --code-bg: #24274a;
    }
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; background: var(--bg); color: var(--text);
    font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  main { max-width: 520px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { font-size: 1.4rem; margin: 0 0 4px; }
  h2 { font-size: 1.05rem; margin: 0 0 12px; }
  p.lead { color: var(--muted); margin: 0 0 20px; }
  .card { background: var(--card); border: 1px solid var(--line); border-radius: 14px; padding: 18px; margin-bottom: 16px; }
  label { display: block; font-weight: 600; font-size: .9rem; margin: 14px 0 6px; }
  label:first-of-type { margin-top: 0; }
  input, select {
    width: 100%; min-height: 44px; padding: 10px 12px; font: inherit; color: var(--text);
    background: var(--bg); border: 1px solid var(--line); border-radius: 10px;
  }
  input:focus-visible, select:focus-visible, button:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; }
  .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .hint { color: var(--muted); font-size: .85rem; margin: 6px 0 0; }
  button {
    min-height: 44px; padding: 10px 16px; font: inherit; font-weight: 600; border-radius: 10px;
    border: 1px solid var(--accent); background: var(--accent); color: var(--accent-ink); cursor: pointer;
  }
  button.secondary { background: transparent; color: var(--accent); }
  button.danger { background: transparent; border-color: var(--danger); color: var(--danger); min-height: 36px; padding: 6px 12px; }
  button:disabled { opacity: .55; cursor: default; }
  .actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
  .msg { margin: 12px 0 0; font-weight: 600; }
  .msg.error { color: var(--danger); }
  .code {
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    background: var(--code-bg); border-radius: 10px; padding: 12px 14px; margin-top: 10px;
  }
  .code strong { font: 700 1.35rem/1.2 ui-monospace, "SF Mono", Menlo, Consolas, monospace; letter-spacing: .06em; }
  .code small { display: block; color: var(--muted); font-size: .8rem; }
  ul.list { list-style: none; padding: 0; margin: 0; }
  ul.list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0; border-top: 1px solid var(--line); }
  ul.list li:first-child { border-top: 0; }
  .state { font-size: .85rem; color: var(--muted); }
  .state.used { color: var(--ok); }
  .sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  @media (prefers-reduced-motion: no-preference) { button { transition: opacity .15s; } }
</style>
</head>
<body>
<main>
  <h1>Codici Rappresentante</h1>
  <p class="lead">Ogni codice vale per una classe sola e una volta sola. Dai a ogni Rappresentante il suo codice, di persona o in privato.</p>

  <form class="card" id="issue" autocomplete="on">
    <h2>Nuovi codici</h2>
    <input class="sr" type="text" name="username" autocomplete="username" value="aila-admin" tabindex="-1" aria-hidden="true">
    <label for="secret">Segreto di amministrazione</label>
    <input id="secret" name="password" type="password" autocomplete="current-password" required>
    <p class="hint">È <code>ADMIN_SECRET</code> del Worker. Il gestore di password del browser può ricordarlo.</p>

    <label for="cls">Classe</label>
    <input id="cls" list="classes" placeholder="Per esempio 4 CSA" required autocapitalize="characters" autocomplete="off">
    <datalist id="classes"></datalist>

    <div class="row">
      <div>
        <label for="count">Quanti codici</label>
        <select id="count" aria-describedby="count-hint">
          <option value="1">1</option>
          <option value="2" selected>2</option>
        </select>
        <p class="hint" id="count-hint">Uno per Rappresentante.</p>
      </div>
      <div>
        <label for="days">Validi per</label>
        <select id="days">
          <option value="1">1 giorno</option>
          <option value="3">3 giorni</option>
          <option value="7" selected>7 giorni</option>
          <option value="14">14 giorni</option>
          <option value="30">30 giorni</option>
        </select>
      </div>
    </div>

    <div class="actions">
      <button type="submit" id="go">Crea codici</button>
      <button type="button" class="secondary" id="show">Codici già emessi</button>
    </div>
    <p class="msg" id="msg" role="status" aria-live="polite"></p>
  </form>

  <section class="card" id="result" hidden>
    <h2 id="result-title">Codici creati</h2>
    <div id="codes"></div>
    <p class="hint">Li vedi solo adesso: sul server resta solo un'impronta. Se li perdi, creane di nuovi e ritira questi.</p>
  </section>

  <section class="card" id="history" hidden>
    <h2 id="history-title">Codici emessi</h2>
    <ul class="list" id="list"></ul>
  </section>
</main>

<script nonce="__NONCE__">
(() => {
  const API = '/api/admin/representative-invites';
  const $ = (id) => document.getElementById(id);
  const msg = $('msg');

  function say(text, isError) {
    msg.textContent = text || '';
    msg.className = 'msg' + (isError ? ' error' : '');
  }

  function el(tag, attrs, children) {
    const node = document.createElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (k === 'text') node.textContent = v; else node.setAttribute(k, v);
    });
    (children || []).forEach((c) => node.appendChild(c));
    return node;
  }

  function when(iso) {
    return new Date(iso).toLocaleString('it-IT', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
  }

  async function call(method, url, body) {
    const secret = $('secret').value;
    if (!secret) { say('Scrivi il segreto di amministrazione.', true); $('secret').focus(); return null; }
    const res = await fetch(url, {
      method,
      headers: Object.assign({ 'X-Admin-Secret': secret }, body ? { 'Content-Type': 'application/json' } : {}),
      body: body ? JSON.stringify(body) : undefined,
    }).catch(() => null);
    if (!res) { say('Server non raggiungibile. Controlla la connessione.', true); return null; }
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      say(res.status === 401 ? 'Segreto sbagliato.' : (json.error || 'Errore ' + res.status), true);
      return null;
    }
    return json;
  }

  // Classi esistenti, per sceglierle invece di riscriverle (rotta pubblica della registrazione).
  fetch('/api/auth/classes').then((r) => r.json()).then((j) => {
    (j.classes || []).forEach((c) => $('classes').appendChild(el('option', { value: c.label })));
  }).catch(() => {});

  $('issue').addEventListener('submit', async (e) => {
    e.preventDefault();
    say('');
    const go = $('go');
    go.disabled = true;
    try {
      const json = await call('POST', API, {
        classLabel: $('cls').value,
        count: Number($('count').value),
        ttlDays: Number($('days').value),
      });
      if (!json) return;
      const box = $('codes');
      box.replaceChildren();
      json.codes.forEach((c, i) => {
        const copy = el('button', { type: 'button', class: 'secondary', text: 'Copia' });
        copy.addEventListener('click', async () => {
          try { await navigator.clipboard.writeText(c.code); copy.textContent = 'Copiato'; }
          catch { copy.textContent = 'Copia a mano'; }
          setTimeout(() => { copy.textContent = 'Copia'; }, 2000);
        });
        box.appendChild(el('div', { class: 'code' }, [
          el('div', {}, [
            el('small', { text: json.codes.length > 1 ? 'Rappresentante ' + (i + 1) : 'Rappresentante' }),
            el('strong', { text: c.code }),
          ]),
          copy,
        ]));
      });
      $('result-title').textContent = 'Codici per la ' + json.classLabel + ' · validi fino al ' + when(json.expiresAt);
      $('result').hidden = false;
      const free = 2 - json.representatives;
      say(json.representatives > 0
        ? 'La classe ha già ' + json.representatives + (json.representatives === 1 ? ' Rappresentante' : ' Rappresentanti') +
          (free <= 0 ? ': i codici non serviranno finché uno non lascia il posto.' : ': resta ' + free + ' posto.')
        : '', free <= 0);
      $('result').scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
    } finally {
      go.disabled = false;
    }
  });

  async function showHistory() {
    say('');
    const cls = $('cls').value.trim();
    const json = await call('GET', API + (cls ? '?classLabel=' + encodeURIComponent(cls) : ''));
    if (!json) return;
    const list = $('list');
    list.replaceChildren();
    const now = Date.now();
    if (!json.invites.length) list.appendChild(el('li', { text: 'Nessun codice emesso.' }));
    json.invites.forEach((inv) => {
      let state, used = false;
      if (inv.usedAt) { state = 'Usato da ' + (inv.usedBy || '?') + ' il ' + when(inv.usedAt); used = true; }
      else if (new Date(inv.expiresAt).getTime() <= now) state = 'Scaduto il ' + when(inv.expiresAt);
      else state = 'Libero fino al ' + when(inv.expiresAt);
      const children = [el('div', {}, [
        el('div', { text: inv.classLabel + (inv.createdAt ? ' · creato il ' + when(inv.createdAt) : '') }),
        el('div', { class: 'state' + (used ? ' used' : ''), text: state }),
      ])];
      if (!inv.usedAt) {
        const del = el('button', { type: 'button', class: 'danger', text: 'Ritira' });
        del.addEventListener('click', async () => {
          if (!confirm('Ritirare questo codice? Chi lo ha ricevuto non potrà più usarlo.')) return;
          if (await call('DELETE', API + '/' + encodeURIComponent(inv.id))) showHistory();
        });
        children.push(del);
      }
      list.appendChild(el('li', {}, children));
    });
    $('history-title').textContent = cls ? 'Codici emessi per la classe' : 'Codici emessi (tutte le classi)';
    $('history').hidden = false;
  }
  $('show').addEventListener('click', showHistory);
})();
</script>
</body>
</html>
`;
