'use strict';
/*
 * Video di presentazione di AILA.
 * Tutto è funzione del tempo: seek(t) disegna il fotogramma al secondo t, senza stato tra un
 * fotogramma e l'altro. Così l'anteprima nel browser e l'esportazione fotogramma per fotogramma
 * (render.js) producono esattamente le stesse immagini.
 * Tempo: 120 BPM, una battuta = 2 s; i cambi scena cadono sempre su una battuta (vedi music.js).
 */
const DURATION = TIMELINE.DURATION; // tempo del video; le scene usano il tempo interno (timeline.js)
const stage = document.getElementById('stage');
const SFX = []; // {t, type} — letti da render.js per sincronizzare gli effetti sonori

// ---------- matematica ----------
const clamp = (x, a = 0, b = 1) => x < a ? a : x > b ? b : x;
const lerp = (a, b, p) => a + (b - a) * p;
const E = {
  lin: p => p,
  out: p => 1 - Math.pow(1 - p, 3),
  out5: p => 1 - Math.pow(1 - p, 5),
  in: p => p * p * p,
  inOut: p => p < .5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2,
  back: p => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2); },
  expo: p => p >= 1 ? 1 : 1 - Math.pow(2, -10 * p),
};
const P = (t, a, d, e = E.out) => e(clamp((t - a) / d));
function rng(seed) {
  return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
}
const fmt = n => Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');

// ---------- DOM ----------
const I = (name, extra = '') => `<span class="ico" ${extra}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg></span>`;
function pose(el, { o = 1, x = 0, y = 0, s = 1, sx = null, sy = null, r = 0, b = 0 } = {}) {
  if (!el) return;
  if (o <= 0.002) { el.style.visibility = 'hidden'; return; }
  el.style.visibility = '';
  el.style.opacity = o.toFixed(3);
  el.style.transform = `translate3d(${x.toFixed(2)}px,${y.toFixed(2)}px,0) scale(${(sx ?? s).toFixed(4)},${(sy ?? s).toFixed(4)}) rotate(${r.toFixed(2)}deg)`;
  el.style.filter = b > 0.05 ? `blur(${b.toFixed(2)}px)` : '';
}
/* entra a tin, esce a tout (null = resta) */
function io(el, t, tin, tout, opt = {}) {
  const { d = .7, od = .45, dy = 36, dx = 0, s0 = 1, b = 10, e = E.out, ody = -16, odx = 0, os = 1, r0 = 0 } = opt;
  const pi = P(t, tin, d, e), po = tout == null ? 0 : P(t, tout, od, E.in);
  pose(el, { o: clamp(pi) * (1 - po), x: dx * (1 - pi) + odx * po, y: dy * (1 - pi) + ody * po,
    s: lerp(s0, 1, pi) * lerp(1, os, po), r: r0 * (1 - pi), b: b * (1 - clamp(pi)) + b * po });
}
function splitWords(el) {
  if (el._w) return el._w;
  const out = [], nodes = [...el.childNodes];
  el.innerHTML = '';
  const add = (text, cls) => text.split(/(\s+)/).forEach(part => {
    if (!part) return;
    if (/^\s+$/.test(part)) { el.appendChild(document.createTextNode(' ')); return; }
    const s = document.createElement('span'); s.className = 'w' + (cls ? ' ' + cls : ''); s.textContent = part;
    el.appendChild(s); out.push(s);
  });
  nodes.forEach(n => {
    if (n.nodeType === 3) add(n.textContent);
    else if (n.tagName === 'BR') el.appendChild(document.createElement('br'));
    else add(n.textContent, n.tagName.toLowerCase());
  });
  return (el._w = out);
}
/* parole una dopo l'altra */
function rw(el, t, tin, tout, opt = {}) {
  const step = opt.step ?? .065;
  splitWords(el).forEach((w, i) => io(w, t, tin + i * step, tout == null ? null : tout + i * step * .35,
    { dy: 46, b: 12, d: .75, ...opt }));
}
function typeText(el, t, t0, cps, text, caret) {
  const n = Math.floor(clamp((t - t0) * cps, 0, text.length));
  if (el._n !== n) { el.textContent = text.slice(0, n); el._n = n; }
  if (caret) {
    const typing = t >= t0 && n < text.length;
    caret.style.opacity = (t >= t0 - .6 && (typing || Math.floor(t * 2.2) % 2 === 0)) ? 1 : 0;
  }
  return n >= text.length;
}

const scenes = [];
function addScene(start, end, html) {
  const el = document.createElement('div');
  el.className = 'scene';
  el.innerHTML = html;
  stage.appendChild(el);
  const sc = { start, end, el, render: () => {} };
  sc.q = s => el.querySelector(s);
  sc.qa = s => [...el.querySelectorAll(s)];
  scenes.push(sc);
  return sc;
}
const sfx = (t, type) => SFX.push({ t: +t.toFixed(3), type });

// ---------- logo ----------
function logoSVG(id) {
  const apex = [256, 77.568], mid = [[93.082, 279.296], [256, 279.296], [418.918, 279.296]];
  const bot = [[93.082, 465.459], [256, 465.459], [418.918, 465.459]];
  const lines = [];
  mid.forEach(m => lines.push([apex, m]));
  mid.forEach(m => bot.forEach(b => lines.push([m, b])));
  const deskX = [31.027, 193.946, 356.864];
  return `<svg viewBox="0 0 512 512" width="100%" height="100%" style="overflow:visible">
  <defs>
    <linearGradient id="desk${id}" x1="0" y1="512" x2="512" y2="0" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#6FA8FF"/><stop offset="1" stop-color="#A78BFA"/></linearGradient>
    <linearGradient id="spk${id}" x1="0" y1="0" x2="0" y2="155" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#DCE5FF"/></linearGradient>
    <radialGradient id="halo${id}" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#C9B8FF" stop-opacity=".6"/><stop offset="1" stop-color="#C9B8FF" stop-opacity="0"/></radialGradient>
  </defs>
  ${lines.map(([a, b]) => `<line class="ln" x1="${a[0]}" y1="${a[1]}" x2="${b[0]}" y2="${b[1]}" stroke="#B4C6FF" stroke-opacity=".45" stroke-width="14" stroke-linecap="round"/>`).join('')}
  ${[232.755, 418.918].map((y, r) => deskX.map(x => `<rect class="dk" x="${x}" y="${y}" width="124.109" height="93.082" rx="31.027" fill="url(#desk${id})" opacity="${r ? .78 : 1}" style="transform-box:fill-box;transform-origin:center"/>`).join('')).join('')}
  <circle class="halo" cx="256" cy="77.568" r="131.891" fill="url(#halo${id})" style="transform-box:fill-box;transform-origin:center"/>
  <path class="spk" d="M256,0 Q266.549,64.381 318.054,77.568 Q266.549,90.755 256,155.136 Q245.451,90.755 193.946,77.568 Q245.451,64.381 256,0 Z" fill="url(#spk${id})" style="transform-box:fill-box;transform-origin:center"/>
</svg>`;
}
/* costruzione del logo a partire da t0 */
function renderLogo(root, t, t0) {
  if (!root._parts) {
    root._parts = { dk: [...root.querySelectorAll('.dk')], ln: [...root.querySelectorAll('.ln')], halo: root.querySelector('.halo'), spk: root.querySelector('.spk') };
    root._parts.ln.forEach(l => { const L = Math.hypot(l.x2.baseVal.value - l.x1.baseVal.value, l.y2.baseVal.value - l.y1.baseVal.value); l._L = L; l.style.strokeDasharray = L; });
  }
  const { dk, ln, halo, spk } = root._parts;
  dk.forEach((d, i) => {
    const p = P(t, t0 + i * .06, .55, E.back);
    d.style.opacity = clamp(p * 1.5) * (i >= 3 ? .78 : 1);
    d.style.transform = `translateY(${(1 - p) * 90}px) scale(${lerp(.3, 1, p)})`;
  });
  ln.forEach((l, i) => { const p = P(t, t0 + .3 + i * .03, .5, E.inOut); l.style.strokeDashoffset = (1 - p) * l._L; });
  const ph = P(t, t0 + .65, .9, E.out);
  halo.style.opacity = ph; halo.style.transform = `scale(${lerp(.2, 1, ph) * (1 + .05 * Math.sin(t * 2.2))})`;
  const ps = P(t, t0 + .75, .75, E.back);
  spk.style.opacity = clamp(ps * 2);
  spk.style.transform = `scale(${ps * (1 + .045 * Math.sin(t * 3.1))}) rotate(${(1 - ps) * -120}deg)`;
}
const AVC = ['#3B82F6', '#8B5CF6', '#06B6D4', '#14B8A6', '#F59E0B', '#EC4899', '#22C55E', '#6366F1', '#F97316', '#0EA5E9', '#A855F7', '#10B981'];
const avBg = i => { const c = AVC[i % AVC.length]; return `linear-gradient(140deg, ${c}, ${AVC[(i + 5) % AVC.length]})`; };

// =====================================================================================
// SFONDO (sempre presente)
// =====================================================================================
const bg = document.createElement('div');
bg.className = 'layer';
bg.innerHTML = `
  <div class="blob" id="b1" style="background:radial-gradient(circle,rgba(59,130,246,.55),transparent 62%)"></div>
  <div class="blob" id="b2" style="background:radial-gradient(circle,rgba(139,92,246,.5),transparent 62%)"></div>
  <div class="blob" id="b3" style="background:radial-gradient(circle,rgba(6,182,212,.32),transparent 62%)"></div>
  <div class="blob" id="b4" style="background:radial-gradient(circle,rgba(239,68,68,.30),transparent 62%)"></div>
  <div class="dots"></div>
  <div class="layer" id="bgdim" style="background:#03060F"></div>`;
stage.appendChild(bg);
function renderBg(t) {
  // nella scena del caos (0–12 s) lo sfondo è cupo e rossastro, poi si accende col logo
  const calm = P(t, 15.6, 1.4, E.out);
  const b = id => document.getElementById(id);
  pose(b('b1'), { x: 1000 + 260 * Math.sin(t * .21), y: -320 + 160 * Math.cos(t * .17), o: lerp(.35, 1, calm) });
  pose(b('b2'), { x: -260 + 220 * Math.cos(t * .19), y: 380 + 140 * Math.sin(t * .23), o: lerp(.35, 1, calm) });
  pose(b('b3'), { x: 520 + 300 * Math.sin(t * .13 + 1), y: 420 + 160 * Math.sin(t * .16), o: lerp(.1, .9, calm) });
  pose(b('b4'), { x: 300 + 240 * Math.sin(t * .3), y: -200 + 120 * Math.cos(t * .25), o: lerp(.9, 0, calm) });
  const dim = Math.max(1 - P(t, 0, 1.4), .0) * .9 + (t > 120.4 ? P(t, 120.4, 1.5, E.inOut) : 0);
  b('bgdim').style.opacity = clamp(dim);
}

// =====================================================================================
// 1 · IL CAOS (0–12)
// =====================================================================================
{
  const N = [
    ['r', 'Circolare n. 104 — Uscita didattica a Venezia'], ['g', 'ma la verifica di mate è giovedì??'],
    ['r', 'Circolare n. 105 — Variazione orario'], ['g', 'qualcuno ha letto la circolare della gita?'],
    ['r', 'Circolare n. 106 — Assemblea d\'istituto'], ['g', 'chi si fa interrogare lunedì?'],
    ['r', 'Circolare n. 107 — Contributo volontario'], ['g', '52 messaggi non letti'],
    ['r', 'Circolare n. 108 — Sciopero dei mezzi'], ['g', 'da domani dove mi siedo?'],
    ['r', 'Circolare n. 109 — Corso sulla sicurezza'], ['g', 'raga entro quando si paga la gita??'],
    ['r', 'Circolare n. 110 — Open day'], ['g', '128 messaggi non letti'],
  ];
  const POS = [[120, 90, -4], [1190, 150, 5], [650, 430, -2], [190, 650, 6], [1250, 580, -6], [700, 110, 3], [70, 370, -7],
    [1290, 870, 4], [620, 780, -3], [1070, 340, 7], [250, 905, -5], [890, 610, 2], [1380, 30, -3], [470, 245, 6]];
  const times = N.map((_, i) => .6 + 8.4 * (1 - Math.pow(1 - i / (N.length - 1), 1.7)));
  const S = addScene(0, 12.2, `
    ${N.map(([k, txt], i) => `<div class="a notif n" style="left:${POS[i][0]}px;top:${POS[i][1]}px">
      <div class="ic" style="background:${k === 'r' ? 'linear-gradient(140deg,#3B82F6,#6366F1)' : 'linear-gradient(140deg,#22C55E,#16A34A)'}">${I(k === 'r' ? 'file-text' : 'message-circle')}</div>
      <div style="flex:1;min-width:0"><div class="app">${k === 'r' ? 'Registro elettronico' : 'Gruppo classe'}<span>ora</span></div><div class="tx">${txt}</div></div></div>`).join('')}
    <div class="layer" id="dim1" style="background:radial-gradient(ellipse at center,rgba(3,6,15,.92) 20%,rgba(3,6,15,.6) 80%)"></div>
    <div class="a hxl center" id="h1a" style="left:0;width:1920px;top:330px;font-size:92px">Circolari. Scadenze.<br>Interrogazioni. Chat infinite.</div>
    <div class="a hxl center" id="h1b" style="left:0;width:1920px;top:360px">E le cose importanti<br><em>si perdono.</em></div>`);
  times.forEach((tt, i) => sfx(tt, N[i][0] === 'r' ? 'ping' : 'ping2'));
  const cards = S.qa('.n');
  S.render = t => {
    cards.forEach((c, i) => {
      const t0 = times[i];
      const pin = P(t, t0, .5, E.back);
      const age = Math.max(0, t - t0);
      const pc = P(t, 10.2 + i * .035, .9, E.in); // risucchio al centro
      const cx = 960 - 300 - POS[i][0], cy = 540 - 50 - POS[i][1];
      pose(c, { o: clamp(pin * 1.6) * (1 - pc) * lerp(1, .55, clamp((t - t0 - 2.5) / 4)),
        x: cx * pc, y: (1 - pin) * -40 + age * 7 * (1 - pc) + cy * pc, s: lerp(.6, 1, pin) * lerp(1, .15, pc),
        r: POS[i][2] * (1 - pc) + (1 - pin) * 8, b: pc * 8 });
    });
    S.q('#dim1').style.opacity = P(t, 3.5, 1.2) * (1 - P(t, 11.2, .8));
    // le quattro parole entrano una per volta
    splitWords(S.q('#h1a')).forEach((w, i) => io(w, t, [3.9, 4.45, 5.0, 5.6, 5.6][i], 7.9,
      { dy: 0, s0: 1.25, b: 16, d: .45, od: .4 }));
    rw(S.q('#h1b'), t, 8.4, 10.9, { step: .1, od: .6, os: .92, b: 14 });
  };
}

// =====================================================================================
// 2 · LA DOMANDA + IL LOGO (12–20)
// =====================================================================================
{
  const S = addScene(12, 20.2, `
    <div class="a hxl center" id="q" style="left:0;width:1920px;top:380px">E se la tua classe avesse<br><em>un'assistente?</em></div>
    <div class="a" id="flash" style="left:0;top:0;width:1920px;height:1080px;background:radial-gradient(circle at 50% 27%,rgba(200,215,255,.9),rgba(120,140,255,.25) 40%,transparent 70%)"></div>
    <div class="a" id="ring" style="left:760px;top:90px;width:400px;height:400px;border-radius:50%;border:3px solid rgba(180,200,255,.8)"></div>
    <div id="grp" class="layer">
      <div class="a" id="logo" style="left:790px;top:120px;width:340px;height:340px">${logoSVG('A')}</div>
      <div class="a center" id="wm" style="left:0;width:1920px;top:470px;font-size:210px;font-weight:700;letter-spacing:.06em;line-height:1">AILA</div>
      <div class="a center sub" id="tag" style="left:0;width:1920px;top:720px;font-size:46px;color:#DCE3FF;font-weight:500">La vita di classe, in un'app.</div>
      <div class="a center" id="mods" style="left:0;width:1920px;top:840px;font-size:27px;color:var(--muted);letter-spacing:.04em">Circolari · AILA Assistant · Calendario · Sondaggi · Mappa posti · Bacheca</div>
    </div>`);
  const wm = S.q('#wm'); wm.innerHTML = 'AILA'.split('').map(c => `<span class="w">${c}</span>`).join('');
  wm._w = [...wm.querySelectorAll('.w')];
  sfx(16, 'impact');
  S.render = t => {
    rw(S.q('#q'), t, 12.25, 15.15, { step: .13, od: .55, os: 1.06, b: 16 });
    S.q('#flash').style.opacity = t < 16 ? 0 : .55 * (1 - P(t, 16, .7, E.out));
    const pr = P(t, 16, 1.3, E.out5);
    pose(S.q('#ring'), { o: t < 16 ? 0 : (1 - pr) * .9, s: lerp(.2, 4.5, pr) });
    renderLogo(S.q('#logo'), t, 16.0);
    pose(S.q('#logo'), { y: Math.sin(t * 1.3) * 6 });
    wm._w.forEach((w, i) => io(w, t, 16.9 + i * .09, null, { dy: 80, b: 18, d: .8, e: E.out5 }));
    io(S.q('#tag'), t, 17.6, null, { dy: 30 });
    io(S.q('#mods'), t, 18.2, null, { dy: 20, b: 6 });
    const pg = P(t, 19.35, .7, E.in);
    pose(S.q('#grp'), { o: 1 - pg, s: lerp(1, .9, pg), b: pg * 14 });
  };
}

// ---------- marchio piccolo in alto a destra durante le funzioni ----------
{
  const S = addScene(20, 113, `<div class="a" id="wmk" style="left:1660px;top:56px;display:flex;align-items:center;gap:14px">
    <div style="width:48px;height:48px">${logoSVG('W')}</div><div style="font-size:28px;font-weight:700;letter-spacing:.08em">AILA</div></div>`);
  S.render = t => { renderLogo(S.q('#wmk'), t, 20.1); pose(S.q('#wmk'), { o: .8 * P(t, 20.1, .6) * (1 - P(t, 112.3, .6)) }); };
}

// helper per le intestazioni di sezione: etichetta + titolo (+ sottotitolo)
function header(S, t, tin, tout) {
  io(S.q('.label'), t, tin, tout, { dx: -30, dy: 0, b: 6 });
  rw(S.q('.h1'), t, tin + .2, tout);
  const sub = S.q('.sub.hs'); if (sub) io(sub, t, tin + .9, tout, { dy: 24 });
}

// =====================================================================================
// 3 · CIRCOLARI (20–38)
// =====================================================================================
{
  const STEPS = [
    ['server', 'Controlla il registro della scuola', 'Il server di AILA lo fa ogni 15 minuti, da solo'],
    ['bell-ring', 'Ti avvisa subito', 'Notifica appena esce una nuova circolare'],
    ['sparkles', 'La legge e la riassume', 'E ti dice se ti riguarda davvero'],
    ['calendar-plus', 'Salva le scadenze', 'Finiscono nel calendario di classe'],
  ];
  const CARDS = [ // [numero, titolo, badge]
    ['112', 'Viaggio d\'istruzione a Praga — classi quarte', 'green'],
    ['111', 'Corso di preparazione ai test universitari', 'amber'],
    ['110', 'Uscita anticipata delle classi prime', 'gray'],
    ['109', 'Assemblea d\'istituto di ottobre', 'green'],
  ];
  const BTXT = { green: 'Ti riguarda', amber: 'Potenziale interesse', gray: 'Non ti riguarda' };
  const BULLETS = [
    'Viaggio a Praga dal 18 al 22 novembre per le classi quarte.',
    'Costo totale 380 €: acconto di 150 €, poi il saldo.',
    'Autorizzazione firmata da consegnare al coordinatore.',
  ];
  const S = addScene(20, 38.2, `
    <div class="a" style="left:140px;top:120px"><span class="label"><b>01</b> Circolari</span></div>
    <div class="a h1" style="left:140px;top:195px;width:900px">Ogni circolare,<br><em>riassunta per te.</em></div>
    <div class="a" id="line" style="left:175px;top:500px;width:4px;height:0;border-radius:2px;background:linear-gradient(#5A8CFF,#A78BFA)"></div>
    ${STEPS.map((s, i) => `<div class="a step" style="left:140px;top:${465 + i * 126}px;width:860px;display:flex;gap:28px;align-items:center">
      <div class="si" style="width:74px;height:74px;border-radius:50%;display:grid;place-items:center;flex:none;background:#141B38;border:1px solid rgba(165,180,252,.35)">${I(s[0], 'style="width:34px;height:34px;color:#B5C3FF"')}</div>
      <div><div style="font-size:31px;font-weight:600">${s[1]}</div><div style="font-size:23px;color:var(--muted);margin-top:4px">${s[2]}</div></div></div>`).join('')}
    <div class="a phone" id="ph" style="left:1240px;top:90px"><div class="screen">
      <div class="island"></div><div class="sbar"><span>9:41</span><span>●●● 5G</span></div>
      <div id="list" class="layer">
        <div class="a" style="left:24px;top:70px;font-size:34px;font-weight:700">Circolari</div>
        <div class="a" style="left:24px;top:116px;font-size:15px;color:#74777F">Aggiornate ora · analizzate dall'AI</div>
        <div class="a" style="left:22px;top:152px;display:flex;gap:8px;font-size:14px;font-weight:600">
          <span style="padding:8px 14px;border-radius:99px;background:#3B82F6;color:#fff">Tutte</span>
          <span style="padding:8px 14px;border-radius:99px;background:#fff;color:#44474F">Ti riguarda</span>
          <span style="padding:8px 14px;border-radius:99px;background:#fff;color:#44474F">Potenziale</span></div>
        ${CARDS.map(c => `<div class="lt-card cc" style="top:0;height:128px">
          <div class="lt-meta">n. ${c[0]} · 30 set</div><div class="lt-title" style="padding-right:4px">${c[1]}</div>
          <span class="badge b-scan scan">${I('sparkles')}Analisi…</span><span class="badge b-${c[2]} fin"><i></i>${BTXT[c[2]]}</span></div>`).join('')}
      </div>
      <div id="det" class="layer">
        <div class="a" style="left:22px;top:66px;display:flex;align-items:center;gap:8px;color:#3B82F6;font-size:16px;font-weight:600">${I('arrow-left', 'style="width:20px;height:20px"')}Circolare n. 112</div>
        <div class="a" style="left:24px;top:104px;right:24px;font-size:25px;font-weight:700;line-height:1.25">Viaggio d'istruzione a Praga — classi quarte</div>
        <span class="badge b-green" style="left:24px;right:auto;top:180px"><i></i>Ti riguarda</span>
        <div class="a" style="left:18px;right:18px;top:226px;background:#fff;border-radius:22px;padding:18px;box-shadow:0 6px 18px rgba(30,40,90,.08)">
          <div style="display:flex;align-items:center;gap:8px;font-size:16px;font-weight:700;color:#6D28D9">${I('sparkles', 'style="width:20px;height:20px"')}Riassunto AI</div>
          ${BULLETS.map(() => `<div style="display:flex;gap:10px;margin-top:12px;font-size:16.5px;line-height:1.45;min-height:48px"><span style="width:7px;height:7px;border-radius:50%;background:#8B5CF6;margin-top:9px;flex:none"></span><span class="bl"></span></div>`).join('')}
        </div>
        <div class="a" id="dl" style="left:18px;right:18px;top:534px;background:#FFFBEB;border:1px solid #FDE68A;border-radius:20px;padding:16px 18px;display:flex;gap:14px;align-items:center">
          <div style="width:46px;height:46px;border-radius:14px;background:#F59E0B;display:grid;place-items:center;color:#fff;flex:none">${I('calendar', 'style="width:24px;height:24px"')}</div>
          <div><div style="font-size:13px;font-weight:700;color:#B45309;letter-spacing:.05em;text-transform:uppercase">Scadenza trovata</div>
          <div style="font-size:17px;font-weight:600;margin-top:2px">Acconto 150 € · entro il 15 ottobre</div></div></div>
        <div class="a" id="btn1" style="left:18px;right:18px;top:640px;height:58px;border-radius:18px;background:#3B82F6;color:#fff;display:grid;place-items:center;font-size:17px;font-weight:600">Aggiungi al calendario</div>
        <div class="a" id="btn2" style="left:18px;right:18px;top:640px;height:58px;border-radius:18px;background:#10B981;color:#fff;display:flex;justify-content:center;align-items:center;gap:10px;font-size:17px;font-weight:600">${I('check', 'style="width:22px;height:22px"')}Aggiunto al calendario</div>
        <div class="a" id="aitag" style="left:0;right:0;top:712px;text-align:center;font-size:14px;color:#6D28D9;font-weight:600">Visibile a tutta la classe · Inserito da AI</div>
      </div>
      <div class="a" id="push" style="left:12px;right:12px;top:56px;background:rgba(255,255,255,.92);border-radius:24px;padding:14px 16px;display:flex;gap:12px;box-shadow:0 14px 40px rgba(20,30,80,.25);z-index:6">
        <div style="width:42px;height:42px;border-radius:11px;overflow:hidden;flex:none;background:linear-gradient(140deg,#1E3C93,#0A1330);padding:4px">${logoSVG('P')}</div>
        <div style="flex:1"><div style="display:flex;justify-content:space-between;font-size:14px;font-weight:700">AILA<span style="font-weight:400;color:#74777F">ora</span></div>
        <div style="font-size:15px;line-height:1.35;margin-top:2px">Nuova circolare n. 112: Viaggio d'istruzione a Praga</div></div></div>
      <div class="tabbar"><span>${I('house')}</span><span class="on">${I('file-text')}</span><span>${I('calendar')}</span><span>${I('layout-list')}</span><span>${I('user')}</span></div>
    </div></div>`);
  const steps = S.qa('.step'), cards = S.qa('.cc'), bls = S.qa('.bl');
  const ST = [21.2, 22.4, 24.0, 31.6]; // quando si attiva ogni passo
  const BADGE_T = [25.7, 24.3, 24.8, 25.25];
  BADGE_T.forEach(tt => sfx(tt, 'blip'));
  sfx(22.6, 'notify'); sfx(34.2, 'success');
  const pl = S.q('#push').querySelector('svg'); // logo nella notifica: già costruito
  S.render = t => {
    header(S, t, 20.15, 37.3);
    const out = P(t, 37.3, .5, E.in);
    // passi a sinistra con linea che scorre
    steps.forEach((s, i) => {
      io(s, t, ST[i], 37.3 + i * .05, { dx: -40, dy: 0, b: 8 });
      const on = t >= ST[i] && (i === 3 || t < ST[i + 1]);
      const si = s.querySelector('.si');
      const g = P(t, ST[i], .4) * (on ? 1 : (t >= ST[i] ? .35 : 0));
      si.style.background = `linear-gradient(140deg, rgba(59,130,246,${.95 * g}), rgba(139,92,246,${.95 * g})), #141B38`;
      si.style.boxShadow = `0 0 ${40 * g}px rgba(120,140,255,${.6 * g})`;
    });
    const lh = lerp(0, 3 * 126, clamp(P(t, 21.4, 1.2) * .34 + P(t, 22.6, 1.2) * .33 + P(t, 24.2, 1.2) * .33 + P(t, 31.6, .1) * 0));
    S.q('#line').style.height = lh + 'px'; S.q('#line').style.opacity = 1 - out;
    // telefono
    io(S.q('#ph'), t, 20.5, 37.3, { dy: 140, b: 0, d: 1.1, e: E.out5, ody: 60 });
    pose(S.q('#push'), { y: lerp(-170, 0, P(t, 22.6, .55, E.back)) + lerp(0, -170, P(t, 24.4, .45, E.in)), o: t > 22.5 && t < 25 ? 1 : 0 });
    renderLogo(S.q('#push'), t, 0);
    // lista: 112 entra in cima a 23.1, le altre scendono
    const ins = P(t, 23.1, .6, E.inOut);
    cards.forEach((c, i) => {
      const slot = i === 0 ? 0 : (i - 1) + ins;
      const top = 206 + slot * 142;
      c.style.top = top + 'px';
      const ci = i === 0 ? P(t, 23.25, .5) : P(t, 21.0 + i * .12, .5);
      pose(c, { o: ci, y: (1 - ci) * 30, x: i === 0 ? (1 - ci) * 40 : 0 });
      const sc = c.querySelector('.scan'), fn = c.querySelector('.fin');
      const pb = P(t, BADGE_T[i], .45, E.back);
      pose(sc, { o: (t > 21 ? 1 : 0) * (1 - clamp(pb * 2)) * (.6 + .4 * Math.sin(t * 9 + i)) });
      pose(fn, { o: clamp(pb * 1.5), s: lerp(.6, 1, pb) });
    });
    // dettaglio della circolare
    const dIn = P(t, 28.3, .6, E.inOut);
    pose(S.q('#list'), { o: 1 - dIn, x: -60 * dIn });
    pose(S.q('#det'), { o: dIn, x: 60 * (1 - dIn) });
    BULLETS.forEach((b, i) => typeText(bls[i], t, 29.0 + i * .95, 60, b));
    io(S.q('#dl'), t, 32.0, null, { dy: 20, s0: .9, e: E.back, b: 0 });
    pose(S.q('#btn1'), { o: P(t, 32.8, .4) * (1 - P(t, 34.15, .2)), s: 1 - .05 * Math.sin(clamp((t - 33.9) / .25) * Math.PI) });
    pose(S.q('#btn2'), { o: P(t, 34.15, .25), s: lerp(.92, 1, P(t, 34.15, .4, E.back)) });
    io(S.q('#aitag'), t, 34.5, null, { dy: 10, b: 0 });
  };
}

// =====================================================================================
// 4 · AILA ASSISTANT (38–50)
// =====================================================================================
const ASSIST = `<svg viewBox="0 0 512 512" width="100%" height="100%"><defs><linearGradient id="asg" x1="0" y1="0" x2="512" y2="512" gradientUnits="userSpaceOnUse">
  <stop offset="0" stop-color="#8B5CF6"/><stop offset=".33" stop-color="#3B82F6"/><stop offset=".67" stop-color="#3B82F6"/><stop offset="1" stop-color="#14B8A6"/></linearGradient></defs>
  <g fill="url(#asg)"><rect class="ab" x="31" y="128" width="76.8" height="256" rx="38.4"/><rect class="ab" x="154.8" y="20.5" width="76.8" height="471" rx="38.4"/>
  <rect class="ab" x="278.6" y="169" width="76.8" height="174.1" rx="38.4"/><rect class="ab" x="402.4" y="92.2" width="76.8" height="327.7" rx="38.4"/></g></svg>`;
function renderBars(root, t, active) {
  root.querySelectorAll('.ab').forEach((b, i) => {
    const k = active ? .55 + .45 * Math.abs(Math.sin(t * 5.5 + i * 1.3)) : 1;
    b.style.transformBox = 'fill-box'; b.style.transformOrigin = 'center'; b.style.transform = `scaleY(${k})`;
  });
}
{
  const Q = 'Quanto costa la gita a Praga e quando si paga l\'acconto?';
  const A = 'La gita a Praga costa 380 € in totale. L\'acconto di 150 € va versato entro il 15 ottobre, il saldo entro il 30 ottobre.';
  const S = addScene(38, 50.2, `
    <div class="a" style="left:140px;top:120px"><span class="label"><b>02</b> AILA Assistant</span></div>
    <div class="a h1" style="left:140px;top:195px;width:800px">Chiedi qualcosa.<br><em>AILA risponde.</em></div>
    <div class="a sub hs" style="left:140px;top:420px;width:740px">Risponde solo con quello che sa davvero: circolari, calendario, sondaggi, bacheca, mappa posti.</div>
    <div class="a" id="f1" style="left:140px;top:640px;display:flex;gap:18px;align-items:center;font-size:28px;font-weight:600">
      <div style="width:64px;height:64px;border-radius:18px;display:grid;place-items:center;background:rgba(59,130,246,.18);border:1px solid rgba(125,160,255,.35)">${I('file-text', 'style="width:32px;height:32px;color:#9DB6FF"')}</div>E ti mostra sempre la fonte.</div>
    <div class="a" id="f2" style="left:140px;top:740px;display:flex;gap:18px;align-items:center;font-size:28px;font-weight:600">
      <div style="width:64px;height:64px;border-radius:18px;display:grid;place-items:center;background:rgba(20,184,166,.16);border:1px solid rgba(94,234,212,.35)">${I('lock', 'style="width:30px;height:30px;color:#5EEAD4"')}</div>
      <div>Può usare anche l'AI sul telefono<div style="font-size:22px;color:var(--muted);font-weight:400;margin-top:2px">senza mandare nulla fuori dal dispositivo</div></div></div>
    <div class="a glass" id="chat" style="left:1000px;top:120px;width:780px;height:850px;overflow:hidden">
      <div style="position:absolute;left:0;right:0;top:0;height:100px;display:flex;align-items:center;gap:18px;padding:0 30px;border-bottom:1px solid rgba(255,255,255,.08)">
        <div id="am" style="width:50px;height:50px">${ASSIST}</div><div style="font-size:27px;font-weight:600">AILA Assistant</div></div>
      <div class="bubble bu" id="ub" style="top:140px"></div>
      <div class="a" id="think" style="left:34px;top:330px;display:flex;align-items:center;gap:16px;font-size:22px;color:var(--muted)">
        <div id="am2" style="width:44px;height:44px">${ASSIST}</div>Sto cercando nelle circolari…</div>
      <div class="bubble ba" id="ab" style="top:300px"><span id="at"></span><span class="caret" id="ac"></span></div>
      <div class="a" id="srcs" style="left:34px;top:530px;display:flex;gap:12px">
        <span class="src" id="s1">${I('file-text')}Circolare n. 112</span><span class="src" id="s2">${I('calendar')}15 ottobre · Acconto gita</span></div>
      <div class="a" id="sugg" style="left:34px;top:640px;right:34px">
        <div style="font-size:18px;color:var(--faint);margin-bottom:12px">Prova anche</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap"><span class="chip sg" style="font-size:19px;padding:10px 16px">Cosa c'è domani?</span>
        <span class="chip sg" style="font-size:19px;padding:10px 16px">Quando mi interrogano in storia?</span></div></div>
      <div style="position:absolute;left:24px;right:24px;bottom:24px;height:76px;border-radius:24px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);display:flex;align-items:center;padding:0 12px 0 26px;gap:12px">
        <div style="flex:1;font-size:22px;white-space:nowrap;overflow:hidden"><span id="ph2" style="color:var(--faint)">Chiedi ad AILA…</span><span id="it"></span><span class="caret" id="ic"></span></div>
        <div id="send" style="width:54px;height:54px;border-radius:17px;background:linear-gradient(135deg,#3B82F6,#8B5CF6);display:grid;place-items:center">${I('send', 'style="width:26px;height:26px;color:#fff"')}</div></div>
    </div>`);
  for (let k = 0; k < Q.length; k += 3) sfx(39.4 + k / 30, 'key');
  sfx(41.6, 'send'); sfx(46.1, 'blip'); sfx(46.4, 'blip');
  S.render = t => {
    header(S, t, 38.15, 49.3);
    io(S.q('#f1'), t, 46.1, 49.3, { dx: -30, dy: 0 });
    io(S.q('#f2'), t, 47.0, 49.35, { dx: -30, dy: 0 });
    io(S.q('#chat'), t, 38.4, 49.3, { dy: 80, b: 0, d: 1, e: E.out5 });
    renderBars(S.q('#am'), t, t > 42 && t < 46);
    // digitazione nella barra in basso, invio, bolla dell'utente
    const sent = t >= 41.65;
    S.q('#ph2').style.display = t < 39.4 ? '' : 'none';
    typeText(S.q('#it'), sent ? -1 : t, 39.4, 30, Q, S.q('#ic'));
    if (sent) { S.q('#it').textContent = ''; S.q('#it')._n = 0; S.q('#ic').style.opacity = 0; S.q('#ph2').style.display = ''; }
    pose(S.q('#send'), { s: 1 - .15 * Math.sin(clamp((t - 41.5) / .25) * Math.PI) });
    const ub = S.q('#ub'); ub.textContent = Q;
    io(ub, t, 41.7, null, { dy: 30, s0: .9, b: 0, d: .5, e: E.back });
    // "sta pensando"
    pose(S.q('#think'), { o: P(t, 42.0, .3) * (1 - P(t, 43.3, .2)) });
    renderBars(S.q('#am2'), t, true);
    const ab = S.q('#ab');
    io(ab, t, 43.4, null, { dy: 20, b: 0, d: .4 });
    typeText(S.q('#at'), t, 43.5, 52, A, S.q('#ac'));
    if (t > 46.3) S.q('#ac').style.opacity = 0;
    io(S.q('#s1'), t, 46.1, null, { dy: 16, s0: .8, e: E.back, b: 0, d: .5 });
    io(S.q('#s2'), t, 46.4, null, { dy: 16, s0: .8, e: E.back, b: 0, d: .5 });
    io(S.q('#sugg'), t, 47.3, null, { dy: 20, b: 0 });
  };
}

// =====================================================================================
// 5 · CALENDARIO (50–58)
// =====================================================================================
{
  const COLORS = { red: '#F87171', violet: '#A78BFA', cyan: '#22D3EE', amber: '#FBBF24', blue: '#60A5FA', gray: '#94A3B8' };
  const EVS = [ // [giorno di ottobre, testo, colore, inserito dall'AI]
    [6, 'Verifica di matematica', 'red'], [8, 'Assemblea d\'istituto', 'cyan', 1], [12, 'Interrogazioni storia', 'violet'],
    [13, 'Interrogazioni storia', 'violet'], [15, 'Acconto gita Praga', 'amber', 1], [21, 'Relazione di fisica', 'blue'],
    [23, 'Sciopero dei mezzi', 'gray', 1], [27, 'Verifica di inglese', 'red'], [30, 'Saldo gita Praga', 'amber', 1],
  ];
  const X0 = 30, Y0 = 150, CW = 191, CH = 98, G = 8;
  let cells = '';
  for (let r = 0; r < 5; r++) for (let c = 0; c < 7; c++) {
    const n = r * 7 + c - 3; // il 28 settembre è lunedì; il 1° ottobre è giovedì
    const day = n < 0 ? 28 + n + 3 : n + 1;
    const out = n < 0 || day > 31;
    cells += `<div class="cell ${out ? 'out' : ''} ${(!out && day === 1) ? 'today' : ''}" style="left:${X0 + c * (CW + G)}px;top:${Y0 + r * (CH + G)}px;width:${CW}px;height:${CH}px"><div class="d">${out && n >= 0 ? day - 31 : day}</div></div>`;
  }
  const evHtml = EVS.map(([d, txt, col, ai]) => {
    const n = d - 1 + 3, r = Math.floor(n / 7), c = n % 7, cc = COLORS[col];
    return `<div class="ev" style="left:${X0 + c * (CW + G) + 10}px;width:${CW - 20}px;top:${Y0 + r * (CH + G) + 42}px;background:${cc}22;border-color:${cc};color:#F1F4FF">${ai ? I('sparkles', `style="color:${cc}"`) : ''}${txt}</div>`;
  }).join('');
  const S = addScene(50, 58.2, `
    <div class="a center" style="left:0;width:1920px;top:70px"><span class="label"><b>03</b> Calendario</span></div>
    <div class="a h1 center" style="left:0;width:1920px;top:140px">Un calendario. <em>Tutta la classe.</em></div>
    <div class="a sub hs center" style="left:0;width:1920px;top:250px">Lo aggiornano i compagni. E l'AI lo riempie leggendo le circolari.</div>
    <div class="a glass" id="cal" style="left:225px;top:340px;width:1470px;height:690px">
      <div class="a" style="left:36px;top:34px;font-size:36px;font-weight:700">Ottobre 2026</div>
      <div class="a" style="right:36px;top:38px;display:flex;gap:22px;font-size:19px;color:var(--muted)">
        ${[['Verifiche', 'red'], ['Interrogazioni', 'violet'], ['Scuola', 'cyan'], ['Scadenze', 'amber']].map(([l, c]) => `<span style="display:flex;align-items:center;gap:8px"><i style="width:12px;height:12px;border-radius:4px;background:${COLORS[c]}"></i>${l}</span>`).join('')}
        <span style="display:flex;align-items:center;gap:8px;color:#C4B5FD">${I('sparkles', 'style="width:18px;height:18px"')}inserito da AI</span></div>
      ${['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'].map((d, i) => `<div class="a" style="left:${X0 + i * (CW + G) + 14}px;top:108px;font-size:18px;font-weight:600;color:var(--faint);letter-spacing:.08em;text-transform:uppercase">${d}</div>`).join('')}
      <div class="layer cells">${cells}</div>
      <div class="layer evs">${evHtml}</div>
    </div>
    <div class="a glass" id="toast" style="left:1350px;top:968px;width:520px;padding:20px 24px;border-radius:22px;display:flex;gap:16px;align-items:center;background:rgba(18,26,52,.95)">
      <div style="width:50px;height:50px;border-radius:15px;background:rgba(16,185,129,.2);display:grid;place-items:center;flex:none">${I('check', 'style="width:28px;height:28px;color:#34D399"')}</div>
      <div><div style="font-size:21px;font-weight:600">Doppione evitato</div><div style="font-size:18px;color:var(--muted);margin-top:2px">"Assemblea d'istituto" c'era già</div></div></div>`);
  const cellEls = S.qa('.cell'), evEls = S.qa('.ev');
  const ET = EVS.map((_, i) => 51.3 + i * .36);
  ET.forEach(tt => sfx(tt, 'blip')); sfx(55.6, 'success');
  S.render = t => {
    header(S, t, 50.1, 57.3);
    io(S.q('#cal'), t, 50.3, 57.3, { dy: 60, b: 0, d: .9, e: E.out5 });
    cellEls.forEach((c, i) => pose(c, { o: P(t, 50.5 + (i % 7) * .03 + Math.floor(i / 7) * .06, .4), s: lerp(.85, 1, P(t, 50.5 + (i % 7) * .03 + Math.floor(i / 7) * .06, .5, E.back)) }));
    evEls.forEach((e, i) => {
      const p = P(t, ET[i], .45, E.back);
      pose(e, { o: clamp(p * 2), s: lerp(.5, 1, p), y: (1 - p) * 10 });
      if (EVS[i][3]) e.style.boxShadow = `0 0 ${30 * (1 - P(t, ET[i] + .2, 1.2))}px ${COLORS[EVS[i][2]]}`;
    });
    io(S.q('#toast'), t, 55.6, 57.2, { dy: 40, s0: .9, e: E.back, b: 0, d: .55 });
  };
}

// =====================================================================================
// 6 · SONDAGGI INTERROGAZIONI (58–76)
// =====================================================================================
{
  const VT = [
    ['Prima scelta', '+50', 'max 3', '#22C55E'], ['Disponibile', '0', 'quanti vuoi', '#EAB308'],
    ['Sconsigliato', '−80', 'max 3', '#F87171'], ['Blocco grave', '−300', 'max 2', '#DC2626'],
  ];
  const DATES = [['Lun 12', 0], ['Mar 13', 1], ['Mer 14', 2], ['Gio 15', 0], ['Ven 16', 3], ['Lun 19', 1]]; // [giorno, voto dato]
  const DX = k => 140 + k * 280, DY = 660;
  const SLOT = (k, j) => [DX(k) + 26 + (j % 2) * 110, DY + 110 + Math.floor(j / 2) * 100];
  const NAMES = ['An', 'Bi', 'Ca', 'Da', 'El', 'Fi', 'Gi', 'Ha', 'Ir', 'Ja', 'Ki', 'Lu', 'Ma', 'Na', 'Om', 'Pa', 'Re', 'Sa', 'To', 'Vi', 'Zo', 'Mt', 'Ch', 'Le'];
  // ordine di assegnazione: Marco (indice 12) va per primo nella sua data verde, il giovedì
  const order = [12, ...NAMES.map((_, i) => i).filter(i => i !== 12)];
  const slotOf = {}; let fill = 0;
  order.forEach((s, k) => {
    if (k === 0) { slotOf[s] = [3, 0]; return; }
    let col, j; do { col = Math.floor(fill / 4); j = fill % 4; fill++; } while (col === 3 && j === 0);
    slotOf[s] = [col, j];
  });
  const S = addScene(58, 76.2, `
    <div class="a" style="left:140px;top:110px"><span class="label"><b>04</b> Sondaggi interrogazioni</span></div>
    <div class="a h1" style="left:140px;top:180px;width:1300px">Interrogazioni:<br><em>niente più corsa alla data.</em></div>
    <div class="a sub" id="sA" style="left:140px;top:378px;width:1500px">Ognuno vota i giorni con un budget di voti limitato. Nessuno può fare il furbo.</div>
    <div class="a sub" id="sB" style="left:140px;top:378px;width:1500px">Chi ha rinunciato prima, <b style="color:#fff">ha la precedenza dopo</b>.</div>
    <div class="a glass" id="bud" style="left:1350px;top:150px;width:430px;padding:22px 26px;border-radius:24px">
      <div style="font-size:18px;color:var(--muted);font-weight:600;letter-spacing:.08em;text-transform:uppercase">Il tuo budget</div>
      ${[['Verdi', '#22C55E', 3, 'bg'], ['Rossi chiari', '#F87171', 3, 'bl'], ['Rossi scuri', '#DC2626', 2, 'bd']].map(([n, c, m, id]) => `
        <div style="display:flex;align-items:center;gap:12px;margin-top:12px;font-size:22px"><i style="width:16px;height:16px;border-radius:50%;background:${c}"></i>${n}
        <span style="margin-left:auto;font-weight:700"><span id="${id}">0</span> / ${m}</span></div>`).join('')}
    </div>
    ${VT.map((v, k) => `<div class="glass vote" style="left:${140 + k * 420}px;top:470px"><div class="dot" style="background:${v[3]};color:${v[3]}"></div>
      <div><div class="nm">${v[0]}</div><div class="pt">${v[2]}</div></div></div>`).join('')}
    <div class="a glass" id="marco" style="left:140px;top:470px;width:1640px;height:138px;border-radius:24px;display:flex;align-items:center;gap:26px;padding:0 30px">
      <div class="av" id="mav" style="position:relative;background:${avBg(12)};flex:none">Ma</div>
      <div style="font-size:25px;line-height:1.35"><b>Marco</b> <span style="color:var(--muted)">l'ultima volta è stato interrogato in un giorno rosso.</span></div>
      <div id="bonus" style="margin-left:auto;font-size:26px;font-weight:800;color:#FDE68A;background:rgba(245,158,11,.16);border:1px solid rgba(251,191,36,.45);padding:12px 20px;border-radius:16px;white-space:nowrap">Bonus sacrificio</div>
      <div id="eq" style="font-size:28px;font-weight:700;white-space:nowrap;color:#4ADE80">Ora ha la precedenza</div>
    </div>
    ${DATES.map(([d], k) => `<div class="glass dcol" style="left:${DX(k)}px;top:${DY}px"><div class="dn">${d}</div><div class="dm">ottobre</div>
      <div class="stamp" style="border:2px dashed rgba(255,255,255,.22)"></div><div class="stamp vs"></div>
      ${[0, 1, 2, 3].map(j => `<div class="slot" style="left:${26 + (j % 2) * 110}px;top:${110 + Math.floor(j / 2) * 100}px"></div>`).join('')}</div>`).join('')}
    ${NAMES.map((n, i) => `<div class="av sv" style="left:0;top:0;background:${avBg(i)}">${n}</div>`).join('')}
    <div class="a" id="glow" style="width:120px;height:120px;border-radius:50%;background:radial-gradient(circle,rgba(250,204,21,.7),transparent 65%)"></div>`);
  const votes = S.qa('.vote'), cols = S.qa('.dcol'), stamps = S.qa('.vs'), avs = S.qa('.sv');
  const STAMP_T = DATES.map((_, k) => 61.4 + k * .5);
  STAMP_T.forEach(tt => sfx(tt, 'stamp'));
  sfx(67.9, 'success');
  const AV_T = {}; order.forEach((s, k) => { AV_T[s] = k === 0 ? 70.9 : 71.9 + (k - 1) * .11; });
  sfx(70.9, 'whooshS');
  S.render = t => {
    header(S, t, 58.15, 75.3);
    io(S.q('#sA'), t, 59.0, 66.4, { dy: 24 });
    io(S.q('#sB'), t, 66.9, 75.3, { dy: 24 });
    io(S.q('#bud'), t, 60.4, 75.3, { dx: 40, dy: 0 });
    votes.forEach((v, k) => io(v, t, 59.7 + k * .22, 66.2 + k * .05, { dy: 40, b: 6 }));
    cols.forEach((c, k) => io(c, t, 60.1 + k * .1, 75.3 + k * .03, { dy: 60, b: 0, d: .8, e: E.out5 }));
    let cnt = [0, 0, 0];
    DATES.forEach(([, v], k) => {
      const p = P(t, STAMP_T[k], .4, E.back);
      const st = stamps[k];
      st.style.background = VT[v][3]; st.style.boxShadow = `0 0 26px ${VT[v][3]}`;
      pose(st, { o: clamp(p * 2), s: lerp(2.2, 1, p) });
      if (t >= STAMP_T[k]) { if (v === 0) cnt[0]++; if (v === 2) cnt[1]++; if (v === 3) cnt[2]++; }
    });
    S.q('#bg').textContent = cnt[0]; S.q('#bl').textContent = cnt[1]; S.q('#bd').textContent = cnt[2];
    // bonus sacrificio
    io(S.q('#marco'), t, 66.6, 75.3, { dy: 40, b: 6 });
    pose(S.q('#bonus'), { o: P(t, 67.9, .4), s: lerp(.5, 1, P(t, 67.9, .5, E.back)) });
    pose(S.q('#eq'), { o: P(t, 68.8, .4), x: 20 * (1 - P(t, 68.8, .5)) });
    // assegnazione: gli avatar volano nei posti
    avs.forEach((a, i) => {
      const [col, j] = slotOf[i]; const [sx, sy] = SLOT(col, j);
      const t0 = AV_T[i], p = P(t, t0, .8, E.inOut);
      const fromX = i === 12 ? 170 : 960 - 38 + (i - 12) * 18, fromY = i === 12 ? 501 : 1180;
      const x = lerp(fromX, sx, p), y = lerp(fromY, sy, p) - Math.sin(p * Math.PI) * 120;
      pose(a, { x, y, o: i === 12 ? (t > 70.85 ? 1 : 0) : clamp(P(t, t0, .25)), s: lerp(.6, 1, p) });
      if (t > 75.3) pose(a, { x, y, o: 1 - P(t, 75.3 + (i % 6) * .03, .45, E.in) });
    });
    S.q('#mav').style.visibility = t > 70.9 ? 'hidden' : '';
    const [gx, gy] = SLOT(3, 0);
    pose(S.q('#glow'), { x: gx - 22, y: gy - 22, o: P(t, 71.6, .5) * (1 - P(t, 75.2, .4)) * (.75 + .25 * Math.sin(t * 6)) });
  };
}

// =====================================================================================
// 7 · MAPPA POSTI (76–96)
// =====================================================================================
{
  const FACT = [
    ['users', 'Amici vicini', 'se vi scegliete a vicenda', '+90', 'pos'],
    ['graduation-cap', 'Chi è forte aiuta chi fa fatica', 'tutoring tra compagni', '+25', 'pos'],
    ['volume-2', 'Due chiacchieroni non vicini', 'stesso banco', '−180', 'neg'],
    ['ruler', 'Chi è alto non copre la lavagna', 'ogni 5 cm di differenza', '−30', 'neg'],
    ['refresh-cw', 'Si cambia compagno', 'ricorda le ultime 4 mappe', '−300', 'neg'],
    ['ban', 'Un rifiuto reciproco è un muro', 'mai nello stesso banco', '−1000', 'neg'],
  ];
  const PX = 860, PY = 370, PW = 920;
  const DESK = (r, c) => [45 + c * 290, 150 + r * 106];
  const SEAT = s => { const d = Math.floor(s / 2), r = Math.floor(d / 3), c = d % 3; const [x, y] = DESK(r, c); return [PX + x + (s % 2 ? 160 : 28), PY + y + 12]; };
  const NAMES = ['An', 'Bi', 'Ca', 'Da', 'El', 'Fi', 'Gi', 'Ha', 'Ir', 'Ja', 'Ki', 'Lu', 'Ma', 'Na', 'Om', 'Pa', 'Re', 'Sa', 'To', 'Vi', 'Zo', 'Mt', 'Ch', 'Le'];
  // permutazione iniziale casuale, poi scambi fino alla disposizione finale (studente i nel posto i)
  const R = rng(7); const perm = NAMES.map((_, i) => i);
  for (let i = perm.length - 1; i > 0; i--) { const j = Math.floor(R() * (i + 1)); [perm[i], perm[j]] = [perm[j], perm[i]]; }
  const start = perm.slice(); const swaps = []; const cur = perm.slice();
  const seatOrder = NAMES.map((_, i) => i).sort(() => R() - .5);
  seatOrder.forEach(i => { if (cur[i] !== i) { const j = cur.indexOf(i); swaps.push([i, j]); [cur[i], cur[j]] = [cur[j], cur[i]]; } });
  // tempi degli scambi: sempre più veloci
  const SW0 = 79.4, SW1 = 86.2; const SWT = []; let acc = 0;
  const durs = swaps.map((_, k) => lerp(.55, .14, Math.pow(k / Math.max(1, swaps.length - 1), .7)));
  const tot = durs.reduce((a, b) => a + b, 0);
  durs.forEach((d, k) => { const dd = d * (SW1 - SW0) / tot; SWT.push([SW0 + acc, dd]); acc += dd; });
  SWT.forEach(([tt]) => sfx(tt, 'tick'));
  const ME = 17; // "il tuo posto"
  const S = addScene(76, 96.2, `
    <div class="a" style="left:140px;top:110px"><span class="label"><b>05</b> Mappa posti</span></div>
    <div class="a h1" style="left:140px;top:180px;width:1640px">Il compagno di banco?<br><em>Lo sceglie un algoritmo.</em></div>
    ${FACT.map((f, i) => `<div class="factor a" style="left:140px;top:${390 + i * 104}px">
      <div class="fi">${I(f[0])}</div><div><div class="ft">${f[1]}</div><div class="fs">${f[2]}</div></div></div>`).join('')}
    <div class="a glass" id="priv" style="left:140px;top:420px;width:660px;padding:34px;border-radius:28px">
      <div style="width:76px;height:76px;border-radius:22px;background:rgba(20,184,166,.18);border:1px solid rgba(94,234,212,.4);display:grid;place-items:center">${I('lock', 'style="width:38px;height:38px;color:#5EEAD4"')}</div>
      <div style="font-size:36px;font-weight:700;margin-top:24px;line-height:1.2">Le preferenze restano segrete.</div>
      <div style="font-size:25px;color:var(--muted);margin-top:14px;line-height:1.45">Nessuno vede chi ha votato cosa, nemmeno il Rappresentante: le usa solo l'algoritmo.</div>
      <div style="font-size:25px;color:var(--muted);margin-top:18px;line-height:1.45">Chi ha bisogno di stare davanti, con il <b style="color:#fff">Priority Pass</b> sta davanti.</div></div>
    <div class="a glass" id="room" style="left:${PX}px;top:${PY}px;width:${PW}px;height:680px"></div>
    <div class="a" id="lav" style="left:${PX + 200}px;top:${PY + 34}px;width:520px;height:16px;border-radius:8px;background:linear-gradient(90deg,#334155,#475569)"></div>
    <div class="a" id="lavl" style="left:${PX + 200}px;top:${PY + 56}px;width:520px;text-align:center;font-size:16px;letter-spacing:.2em;color:var(--faint);font-weight:600">LAVAGNA</div>
    <div class="a" id="cat" style="left:${PX + 650}px;top:${PY + 86}px;width:180px;height:44px;border-radius:12px;border:2px solid rgba(255,255,255,.14);display:grid;place-items:center;font-size:15px;color:var(--faint);letter-spacing:.14em;font-weight:600">CATTEDRA</div>
    ${[0, 1, 2, 3].map(r => [0, 1, 2].map(c => { const [x, y] = DESK(r, c); return `<div class="desk" style="left:${PX + x}px;top:${PY + y}px"></div>`; }).join('')).join('')}
    <div class="a" id="dimr" style="left:${PX}px;top:${PY}px;width:${PW}px;height:680px;border-radius:30px;background:rgba(4,8,20,.72)"></div>
    ${NAMES.map((n, i) => `<div class="seat a" style="left:0;top:0;background:${avBg(i)}">${n}</div>`).join('')}
    <div class="a" id="ring" style="width:100px;height:100px;border-radius:50%;border:4px solid #FDE047;box-shadow:0 0 40px #FACC15"></div>
    <div class="a" id="you" style="padding:10px 18px;border-radius:14px;background:#FDE047;color:#1A1300;font-size:20px;font-weight:800;white-space:nowrap">Il tuo posto</div>
    <div class="a" id="stats" style="left:${PX + 40}px;top:${PY + 590}px;width:${PW - 80}px;display:flex;align-items:center;gap:30px">
      <div style="flex:1"><div style="font-size:21px;color:var(--muted)">L'algoritmo prova migliaia di combinazioni…</div>
      <div style="height:14px;border-radius:7px;background:rgba(255,255,255,.08);margin-top:10px;overflow:hidden"><div id="satb" style="height:100%;width:0;border-radius:7px;background:linear-gradient(90deg,#3B82F6,#22C55E)"></div></div></div></div>
    <div class="a" id="tabs" style="left:${PX + 40}px;top:${PY + 594}px;display:flex;gap:14px">
      ${[['A', 87], ['B', 84], ['C', 81]].map(([l, v], i) => `<div class="tb" style="padding:13px 20px;border-radius:18px;font-size:20px;font-weight:700;white-space:nowrap;${i === 0 ? 'background:linear-gradient(135deg,#3B82F6,#8B5CF6);' : 'background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.14);'}">Proposta ${l}</div>`).join('')}
      </div>
    <div class="a" id="where" style="left:${PX + PW - 330}px;top:${PY - 84}px;padding:14px 24px;border-radius:18px;font-size:22px;font-weight:700;background:#FDE047;color:#1A1300;display:flex;gap:10px;align-items:center;white-space:nowrap;box-shadow:0 0 40px rgba(250,204,21,.35)">${I('map-pin', 'style="width:24px;height:24px"')}Dov'è il mio posto?</div>`);
  const facts = S.qa('.factor'), desks = S.qa('.desk'), seats = S.qa('.seat'), tabs = S.qa('.tb');
  FACT.forEach((_, i) => sfx(80.1 + i * .95, 'blip'));
  sfx(87.0, 'success'); sfx(90.9, 'ping');
  const GOOD = '#22C55E';
  S.render = t => {
    header(S, t, 76.15, 95.3);
    facts.forEach((f, i) => io(f, t, 80.1 + i * .95, 90.0 + i * .05, { dx: -40, dy: 0, b: 8 }));
    io(S.q('#priv'), t, 90.6, 95.3, { dy: 30 });
    io(S.q('#room'), t, 76.6, 95.3, { dy: 60, b: 0, d: .9, e: E.out5 });
    ['#lav', '#lavl', '#cat'].forEach((s, i) => io(S.q(s), t, 77.0 + i * .1, 95.3, { dy: 16, b: 0 }));
    // stato della disposizione al tempo t
    const map = start.slice(); // map[seat] = studente
    let k = 0, partial = null;
    for (; k < swaps.length; k++) {
      const [ts, d] = SWT[k];
      if (t >= ts + d) { const [a, b] = swaps[k]; [map[a], map[b]] = [map[b], map[a]]; }
      else { if (t >= ts) partial = [k, (t - ts) / d]; break; }
    }
    const done = k;
    const posOf = {}; map.forEach((s, seat) => { posOf[s] = SEAT(seat); });
    if (partial) {
      const [a, b] = swaps[partial[0]]; const p = E.inOut(partial[1]);
      const sa = map[a], sb = map[b]; const A = SEAT(a), B = SEAT(b);
      posOf[sa] = [lerp(A[0], B[0], p), lerp(A[1], B[1], p) - Math.sin(p * Math.PI) * 60];
      posOf[sb] = [lerp(B[0], A[0], p), lerp(B[1], A[1], p) + Math.sin(p * Math.PI) * 60];
    }
    seats.forEach((el, i) => {
      const p = P(t, 78.0 + i * .035, .5, E.back);
      const [x, y] = posOf[i];
      const spot = P(t, 91.0, .5);
      pose(el, { x, y: y - (1 - p) * 60, o: clamp(p * 2) * (1 - P(t, 95.3 + (i % 3) * .03, .45, E.in)) * (i === ME ? 1 : lerp(1, .35, spot * (1 - P(t, 95.2, .4)))), s: lerp(.4, 1, p) * (i === ME ? 1 + .12 * spot : 1) });
      el.style.zIndex = i === ME ? 3 : 1;
    });
    desks.forEach((d, i) => {
      io(d, t, 77.2 + i * .05, 95.3, { dy: 20, b: 0, d: .5 });
      const good = map[2 * i] === 2 * i && map[2 * i + 1] === 2 * i + 1 && t > SW0;
      d.style.borderColor = good ? 'rgba(34,197,94,.75)' : 'rgba(255,255,255,.12)';
      d.style.background = good ? 'rgba(34,197,94,.10)' : 'rgba(255,255,255,.06)';
    });
    const prog = done / swaps.length;
    S.q('#satb').style.width = (t < SW0 ? 2 : lerp(2, 100, prog)) + '%';
    io(S.q('#stats'), t, 78.6, 86.9, { dy: 20, b: 0, od: .3 });
    tabs.forEach((b, i) => io(b, t, 87.0 + i * .15, 95.3, { dy: 20, s0: .9, b: 0, e: E.back }));
    io(S.q('#where'), t, 89.6, 95.3, { dy: 20, s0: .9, b: 0, e: E.back });
    pose(S.q('#where'), { o: P(t, 89.6, .4) * (1 - P(t, 95.3, .4)), s: 1 - .08 * Math.sin(clamp((t - 90.6) / .3) * Math.PI) });
    // riflettore sul tuo posto
    const sp = P(t, 91.0, .5) * (1 - P(t, 95.2, .4));
    S.q('#dimr').style.opacity = sp; S.q('#dimr').style.visibility = sp > 0 ? 'visible' : 'hidden';
    const [mx, my] = posOf[ME];
    pose(S.q('#ring'), { x: mx - 19, y: my - 19, o: sp, s: 1 + .1 * Math.sin(t * 5) });
    pose(S.q('#you'), { x: mx - 50, y: my - 66, o: sp, s: lerp(.6, 1, P(t, 91.2, .5, E.back)) });
  };
}

// =====================================================================================
// 8 · BACHECA E SONDAGGI A ORDINAMENTO (96–106)
// =====================================================================================
{
  const OPTS = [['Pizzeria', 46, '#F59E0B'], ['Sushi', 50, '#EC4899'], ['Grigliata al parco', 30, '#22C55E'], ['Hamburgeria', 18, '#06B6D4']];
  const S = addScene(96, 106.2, `
    <div class="a" style="left:140px;top:110px"><span class="label"><b>06</b> Bacheca e sondaggi</span></div>
    <div class="a h1" style="left:140px;top:180px;width:1640px">La voce della classe,<br><em>finalmente organizzata.</em></div>
    <div class="glass prop" id="p1" style="left:140px;top:400px">
      <div class="pt1">${I('user', 'style="width:22px;height:22px"')}Giulia · Proposta</div>
      <div class="pt2">Distributore d'acqua al secondo piano</div>
      <div class="pf"><span style="color:#86EFAC">${I('thumbs-up')}<b id="v1">0</b></span><span>${I('thumbs-down')}2</span><span>${I('message-square')}5 commenti</span></div>
      <div class="status" id="st1" style="background:rgba(255,255,255,.08);color:var(--muted)">Aperta</div>
      <div class="status" id="st2" style="background:rgba(245,158,11,.18);color:#FCD34D">In analisi</div>
      <div class="status" id="st3" style="background:rgba(34,197,94,.18);color:#86EFAC">Accettata</div></div>
    <div class="glass prop" id="p2" style="left:140px;top:610px">
      <div class="pt1">${I('eye-off', 'style="width:22px;height:22px"')}Anonimo · Proposta</div>
      <div class="pt2">Mai due verifiche nello stesso giorno</div>
      <div class="pf"><span style="color:#86EFAC">${I('thumbs-up')}<b id="v2">0</b></span><span>${I('thumbs-down')}1</span><span>${I('message-square')}9 commenti</span></div></div>
    <div class="a" id="shield" style="left:140px;top:836px;width:860px;display:flex;gap:20px;align-items:center">
      <div style="width:70px;height:70px;border-radius:20px;background:linear-gradient(140deg,#3B82F6,#8B5CF6);display:grid;place-items:center;flex:none">${I('shield-check', 'style="width:36px;height:36px;color:#fff"')}</div>
      <div style="font-size:24px;line-height:1.4"><b>Anonimato protetto.</b> <span style="color:var(--muted)">Per svelare un autore servono 2 Rappresentanti e 1 Guardia, ognuno dal proprio account.</span></div></div>
    <div class="a glass" id="rank" style="left:1070px;top:400px;width:710px;height:540px">
      <div class="a" style="left:30px;top:28px;font-size:18px;color:var(--muted);font-weight:600;letter-spacing:.08em;text-transform:uppercase">Sondaggio a ordinamento</div>
      <div class="a" style="left:30px;top:62px;font-size:30px;font-weight:700">Cena di classe: dove andiamo?</div>
      ${OPTS.map(([n, , c]) => `<div class="rk"><div class="bar" style="background:linear-gradient(90deg,${c}cc,${c}55)"></div>
        <div class="nm"><i>0</i>${n}</div><div class="vl"><span>0</span>&nbsp;pt</div></div>`).join('')}
      <div class="a" style="left:30px;right:30px;bottom:26px;font-size:19px;color:var(--muted)">24 voti · ognuno mette in ordine: il 1° posto vale 3 punti, l'ultimo 0</div></div>`);
  const rks = S.qa('.rk');
  sfx(101.6, 'blip'); sfx(103.6, 'success');
  S.render = t => {
    header(S, t, 96.15, 105.3);
    io(S.q('#p1'), t, 96.7, 105.3, { dy: 40, b: 6 });
    io(S.q('#p2'), t, 97.2, 105.35, { dy: 40, b: 6 });
    io(S.q('#shield'), t, 99.6, 105.4, { dy: 30, b: 6 });
    io(S.q('#rank'), t, 97.6, 105.3, { dx: 60, dy: 0, b: 6 });
    S.q('#v1').textContent = Math.round(21 * P(t, 97.4, 2.6, E.out));
    S.q('#v2').textContent = Math.round(17 * P(t, 97.9, 2.6, E.out));
    const s2 = P(t, 101.6, .35), s3 = P(t, 103.6, .35);
    pose(S.q('#st1'), { o: 1 - s2 });
    pose(S.q('#st2'), { o: s2 * (1 - s3), s: lerp(.7, 1, P(t, 101.6, .4, E.back)) });
    pose(S.q('#st3'), { o: s3, s: lerp(.7, 1, P(t, 103.6, .4, E.back)) });
    // valori che crescono con ritmi diversi: il sushi supera la pizzeria verso la fine
    const curves = [P(t, 98.4, 2.2, E.out), P(t, 98.4, 4.0, E.inOut), P(t, 98.4, 3.0, E.out), P(t, 98.4, 2.6, E.out)];
    const vals = OPTS.map((o, i) => o[1] * curves[i]);
    const max = 50;
    rks.forEach((r, i) => {
      // posizione in classifica continua (niente stato tra fotogrammi)
      let rank = 0; vals.forEach((v, j) => { if (j !== i) rank += 1 / (1 + Math.exp(-(v - vals[i]) / .9)); });
      r.style.top = (130 + rank * 88) + 'px';
      r.querySelector('.bar').style.width = (14 + 86 * vals[i] / max) + '%';
      r.querySelector('.vl span').textContent = Math.round(vals[i]);
      r.querySelector('.nm i').textContent = Math.round(rank) + 1;
    });
  };
}

// =====================================================================================
// 9 · OVUNQUE, IN SICUREZZA (106–113)
// =====================================================================================
{
  const DEV = [['smartphone', 'Android'], ['smartphone', 'iPhone'], ['tablet', 'iPad']];
  const CH = [['bell-ring', 'Notifiche in tempo reale'], ['users', 'Ogni classe ha i suoi dati'], ['ban', 'Zero pubblicità'],
    ['eye-off', 'Zero profilazione'], ['lock', 'Password mai salvate in chiaro']];
  const S = addScene(106, 113.2, `
    <div class="a hxl center" style="left:0;width:1920px;top:130px" id="h9">Su ogni telefono.<br><em>Per ogni classe.</em></div>
    ${DEV.map(([ic, l], i) => `<div class="a dev" style="left:${540 + i * 300}px;top:440px;width:240px;text-align:center">
      <div class="glass" style="width:170px;height:170px;margin:0 auto;border-radius:40px;display:grid;place-items:center">${I(ic, 'style="width:80px;height:80px;color:#B5C3FF"')}</div>
      <div style="font-size:32px;font-weight:600;margin-top:20px">${l}</div></div>`).join('')}
    <div class="a" style="left:0;width:1920px;top:770px;display:flex;justify-content:center;gap:16px;flex-wrap:wrap;padding:0 200px" id="chips">
      ${CH.map(([ic, l]) => `<span class="chip cp">${I(ic)}${l}</span>`).join('')}</div>`);
  const devs = S.qa('.dev'), chips = S.qa('.cp');
  devs.forEach((_, i) => sfx(106.8 + i * .2, 'blip'));
  S.render = t => {
    rw(S.q('#h9'), t, 106.15, 112.4, { step: .09 });
    devs.forEach((d, i) => io(d, t, 106.8 + i * .2, 112.4 + i * .04, { dy: 60, s0: .8, e: E.back, b: 6, d: .6 }));
    chips.forEach((c, i) => io(c, t, 108.6 + i * .22, 112.45, { dy: 30, s0: .9, b: 4, d: .5 }));
  };
}

// =====================================================================================
// 10 · FINALE (113–122)
// =====================================================================================
{
  const S = addScene(112.9, 122, `
    <div class="a" id="flash2" style="left:0;top:0;width:1920px;height:1080px;background:radial-gradient(circle at 50% 30%,rgba(200,215,255,.85),rgba(120,140,255,.2) 40%,transparent 70%)"></div>
    <div class="a" id="logo2" style="left:820px;top:90px;width:280px;height:280px">${logoSVG('F')}</div>
    <div class="a center" id="wm2" style="left:0;width:1920px;top:400px;font-size:190px;font-weight:700;letter-spacing:.06em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
    <div class="a center" id="tag2" style="left:0;width:1920px;top:625px;font-size:46px;font-weight:500;color:#DCE3FF">La vita di classe, in un'app.</div>
    <div class="a center" id="cta" style="left:0;width:1920px;top:745px"><span class="chip" style="font-size:32px;padding:18px 32px;background:linear-gradient(135deg,rgba(59,130,246,.35),rgba(139,92,246,.35));border-color:rgba(165,180,252,.5)">${I('users', 'style="width:34px;height:34px;color:#fff"')}Provala con la tua classe</span></div>
    <div class="a center" id="plat" style="left:0;width:1920px;top:850px;font-size:26px;color:var(--muted);letter-spacing:.06em">Android · iPhone · iPad</div>
    <div class="a center" id="cred" style="left:0;width:1920px;top:960px;font-size:24px;color:var(--faint)">Un progetto di Simone Bianchin</div>`);
  const wm = S.q('#wm2'); wm._w = [...wm.querySelectorAll('.w')];
  sfx(113, 'impact');
  S.render = t => {
    S.q('#flash2').style.opacity = t < 113 ? 0 : .5 * (1 - P(t, 113, .8));
    renderLogo(S.q('#logo2'), t, 113.0);
    pose(S.q('#logo2'), { y: Math.sin(t * 1.3) * 6 });
    wm._w.forEach((w, i) => io(w, t, 113.7 + i * .09, null, { dy: 80, b: 18, d: .8, e: E.out5 }));
    io(S.q('#tag2'), t, 114.5, null, { dy: 30 });
    io(S.q('#cta'), t, 116.0, null, { dy: 30, s0: .9, e: E.back });
    io(S.q('#plat'), t, 116.6, null, { dy: 20 });
    io(S.q('#cred'), t, 117.8, null, { dy: 16 });
  };
}

// transizioni sonore tra le scene
[20, 38, 50, 58, 76, 96, 106].forEach(tt => sfx(tt - .45, 'whoosh'));

// ---------- motore ----------
function seek(T) {
  const t = TIMELINE.warp(T);
  renderBg(t);
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
}
window.seek = seek; window.DURATION = DURATION;
window.SFX = SFX.map(s => ({ t: +TIMELINE.unwarp(s.t).toFixed(3), type: s.type }));

// ---------- anteprima nel browser ----------
const capture = location.search.includes('capture');
if (capture) document.body.classList.add('capture');
function fit() {
  if (capture) return;
  const s = Math.min(innerWidth / 1920, (innerHeight - 56) / 1080);
  stage.style.transform = `scale(${s})`;
  stage.style.left = ((innerWidth - 1920 * s) / 2) + 'px';
}
addEventListener('resize', fit); fit();
seek(0);
if (!capture) {
  const m = location.hash.match(/t=([\d.]+)/);
  let t0 = m ? +m[1] : 0, playing = true, base = performance.now() - t0 * 1000, cur = t0;
  const playBtn = document.getElementById('play'), range = document.getElementById('seek'), tm = document.getElementById('tm');
  playBtn.onclick = () => { playing = !playing; playBtn.textContent = playing ? 'Pausa' : 'Play'; base = performance.now() - cur * 1000; };
  range.oninput = () => { cur = +range.value; base = performance.now() - cur * 1000; seek(cur); };
  document.fonts.ready.then(() => {
    const loop = () => {
      if (playing) { cur = (performance.now() - base) / 1000; if (cur > DURATION) { cur = 0; base = performance.now(); } seek(cur); range.value = cur; }
      tm.textContent = cur.toFixed(1) + ' s';
      requestAnimationFrame(loop);
    };
    loop();
  });
}
