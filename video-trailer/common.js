'use strict';
/*
 * Parti comuni ai video di AILA (trailer e intro): matematica e tempi, logo, temi dell'app, dispositivi e
 * schermate. Ogni pagina definisce poi le sue scene e seek(t).
 */
const stage = document.getElementById('stage');
const SFX = []; // {t, type}: letti da render.js (sfx) e dalla musica per gli effetti sonori
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
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16));

// ---------- DOM ----------
const I = (name, extra = '') => `<span class="ico" ${extra}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg></span>`;
function pose(el, { o = 1, x = 0, y = 0, s = 1, sx = null, sy = null, r = 0, b = 0, rx = 0, ry = 0 } = {}) {
  if (!el) return;
  if (o <= 0.002) { if (el._vis !== 0) { el.style.visibility = 'hidden'; el._vis = 0; } return; }
  if (el._vis !== 1) { el.style.visibility = ''; el._vis = 1; }
  el.style.opacity = o >= .999 ? '' : o.toFixed(3);
  const p3 = (rx || ry) ? `perspective(2200px) rotateX(${rx.toFixed(2)}deg) rotateY(${ry.toFixed(2)}deg) ` : '';
  el.style.transform = `translate3d(${x.toFixed(2)}px,${y.toFixed(2)}px,0) ${p3}scale(${(sx ?? s).toFixed(4)},${(sy ?? s).toFixed(4)}) rotate(${r.toFixed(2)}deg)`;
  el.style.filter = b > 0.05 ? `blur(${b.toFixed(2)}px)` : '';
}
/* entra a tin, esce a tout (null = resta) */
function io(el, t, tin, tout, opt = {}) {
  const { d = .5, od = .35, dy = 36, dx = 0, s0 = 1, b = 10, e = E.out5, ody = -16, odx = 0, os = 1, r0 = 0 } = opt;
  const pi = P(t, tin, d, e), po = tout == null ? 0 : P(t, tout, od, E.in);
  pose(el, { o: clamp(pi * 1.4) * (1 - po), x: dx * (1 - pi) + odx * po, y: dy * (1 - pi) + ody * po,
    s: lerp(s0, 1, pi) * lerp(1, os, po), r: r0 * (1 - pi), b: b * (1 - clamp(pi)) + b * po });
}
/* "sbattuta": la parola arriva grande e sfocata e si ferma di colpo */
const slam = (el, t, tin, tout, opt = {}) => io(el, t, tin, tout, { d: .22, dy: 0, s0: 1.55, b: 22, e: E.out5, od: .2, os: .92, ody: 0, ...opt });
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
/* parole una dopo l'altra (times: un istante per parola, oppure passo fisso) */
function rw(el, t, tin, tout, opt = {}) {
  const ws = splitWords(el), step = opt.step ?? .07;
  ws.forEach((w, i) => io(w, t, opt.times ? opt.times[Math.min(i, opt.times.length - 1)] : tin + i * step,
    tout == null ? null : tout + i * .02, { dy: 50, b: 14, d: .45, ...opt }));
}
function typeText(el, t, t0, cps, text, caret) {
  const n = Math.floor(clamp((t - t0) * cps, 0, text.length));
  if (el._n !== n) { el.textContent = text.slice(0, n); el._n = n; }
  if (caret) caret.style.opacity = (t >= t0 - .3 && (n < text.length && t >= t0 || Math.floor(t * 2.4) % 2 === 0)) ? 1 : 0;
  return n >= text.length;
}
const setText = (el, s) => { if (el._t !== s) { el.textContent = s; el._t = s; } };

const scenes = [];
function addScene(start, end, html, parent = stage) {
  const el = document.createElement('div');
  el.className = 'scene';
  el.innerHTML = html;
  parent.appendChild(el);
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
/* costruzione del logo a partire da t0 (veloce: sta tutta in una battuta) */
function renderLogo(root, t, t0, k = .7) {
  if (!root._parts) {
    root._parts = { dk: [...root.querySelectorAll('.dk')], ln: [...root.querySelectorAll('.ln')], halo: root.querySelector('.halo'), spk: root.querySelector('.spk') };
    root._parts.ln.forEach(l => { const L = Math.hypot(l.x2.baseVal.value - l.x1.baseVal.value, l.y2.baseVal.value - l.y1.baseVal.value); l._L = L; l.style.strokeDasharray = L; });
  }
  const { dk, ln, halo, spk } = root._parts;
  dk.forEach((d, i) => {
    const p = P(t, t0 + i * .05 * k, .5 * k, E.back);
    d.style.opacity = clamp(p * 1.5) * (i >= 3 ? .78 : 1);
    d.style.transform = `translateY(${(1 - p) * 90}px) scale(${lerp(.3, 1, p)})`;
  });
  ln.forEach((l, i) => { const p = P(t, t0 + (.25 + i * .025) * k, .45 * k, E.inOut); l.style.strokeDashoffset = (1 - p) * l._L; });
  const ph = P(t, t0 + .55 * k, .8 * k, E.out);
  halo.style.opacity = ph; halo.style.transform = `scale(${lerp(.2, 1, ph) * (1 + .06 * Math.sin(t * Math.PI * 2 / BAR))})`;
  const ps = P(t, t0 + .6 * k, .7 * k, E.back);
  spk.style.opacity = clamp(ps * 2);
  spk.style.transform = `scale(${ps * (1 + .05 * Math.sin(t * Math.PI * 4 / BAR))}) rotate(${(1 - ps) * -140}deg)`;
}
const AVC = ['#3B82F6', '#8B5CF6', '#06B6D4', '#14B8A6', '#F59E0B', '#EC4899', '#22C55E', '#6366F1', '#F97316', '#0EA5E9', '#A855F7', '#10B981'];
const avBg = i => `linear-gradient(140deg, ${AVC[i % AVC.length]}, ${AVC[(i + 5) % AVC.length]})`;
const KIDS = ['GB', 'LM', 'SR', 'AF', 'MC', 'EP', 'DR', 'FT', 'CL', 'BN', 'AV', 'RG', 'MS', 'PL', 'ED', 'TC', 'NA', 'LF', 'IB', 'VM', 'OS', 'KR', 'EZ', 'GC'];

// =====================================================================================
// L'APP: temi, dispositivi e schermate (valori da shared/.../design/AppTheme.kt)
// =====================================================================================
// [nome, chiaro: primario, container, inchiostro del container, tono profondo; scuro: idem]
const ACC = {
  blue: ['Blu AILA', ['#2F5BD3', '#DCE3FF', '#0B1B45', '#1C3C9A'], ['#5A8CFF', '#223A7A', '#DCE3FF', '#14245A']],
  violet: ['Viola', ['#6B4FD8', '#E9DDFF', '#22005D', '#4A2FA8'], ['#9A7DFF', '#3E2A7A', '#E9DDFF', '#2A1B5E']],
  green: ['Verde', ['#1E8E5A', '#C8F2DC', '#002111', '#146B43'], ['#3DBE84', '#1F4D36', '#C8F2DC', '#103A27']],
  teal: ['Petrolio', ['#00838F', '#C7F1F5', '#002022', '#00606A'], ['#33B5C2', '#0E4A50', '#C7F1F5', '#073236']],
  orange: ['Arancio', ['#C2590C', '#FFDBC8', '#331200', '#8F3F05'], ['#F08A3E', '#5C2E0F', '#FFDBC8', '#3F1D05']],
  pink: ['Rosa', ['#C2185B', '#FFD9E3', '#3E001D', '#8E0E43'], ['#F0679A', '#5C1734', '#FFD9E3', '#3E0A22']],
};
const ACC_KEYS = Object.keys(ACC);
const st = (style, dark, acc) => ({ style, dark, acc });
function appVars(s) {
  const v = ACC[s.acc][s.dark ? 2 : 1];
  let acc = v[0];
  if (s.acc === 'blue' && s.style === 'glass') acc = s.dark ? '#0A84FF' : '#3B82F6'; // PrimaryBlue in Glass
  return `--acc:${acc};--cont:${v[1]};--onCont:${v[2]};--deep:${v[3]}`;
}
const appCls = (s, extra = '') => `app ${s.style} ${s.dark ? 'dark' : 'light'} ${extra}`;
function applyState(el, s, extra = '') {
  const key = s.style + s.dark + s.acc + extra;
  if (el._st === key) return;
  el._st = key; el.className = appCls(s, extra); el.style.cssText = appVars(s);
}
const SIG = `<svg width="18" height="12" viewBox="0 0 18 12"><rect x="0" y="8" width="3" height="4" rx="1" fill="currentColor"/><rect x="5" y="5" width="3" height="7" rx="1" fill="currentColor"/><rect x="10" y="2.5" width="3" height="9.5" rx="1" fill="currentColor"/><rect x="15" y="0" width="3" height="12" rx="1" fill="currentColor"/></svg>`;
const BAT = `<svg width="27" height="13" viewBox="0 0 27 13"><rect x=".5" y=".5" width="23" height="12" rx="3.5" fill="none" stroke="currentColor" opacity=".45"/><rect x="2.5" y="2.5" width="16" height="8" rx="2" fill="currentColor"/><rect x="24.5" y="4.5" width="2" height="4" rx="1" fill="currentColor" opacity=".45"/></svg>`;
const SB = `<div class="sb"><span>9:41</span><span style="display:flex;gap:7px;align-items:center">${SIG}<span style="font-size:13px">5G</span>${BAT}</span></div>`;
const NAVT = [['calendar', 'Calendario'], ['users', 'Classe'], ['house', 'Home'], ['chart-column', 'Sondaggi'], ['armchair', 'Mappa posti']];
const navHTML = (on = 2, style = '') => `<div class="nav" style="${style}">${NAVT.map(([ic, l], i) => `<div class="ni${i === on ? ' on' : ''}"><span class="pill"></span>${I(ic)}<span>${l}</span></div>`).join('')}</div>`;
const QUICK = [['file-text', 'Circolari'], ['calendar', 'Calendario'], ['message-square', 'Bacheca'], ['armchair', 'Mappa posti']];
const EVENTS = [['22', 'OTT', 'Verifica di matematica', '3ª ora · Classe'], ['23', 'OTT', 'Autorizzazione per Praga', 'Dalla circolare n. 112'], ['27', 'OTT', 'Interrogazione di storia', 'Date assegnate dal sondaggio']];
const erow = e => `<div class="erow"><div class="dt"><b>${e[0]}</b><s>${e[1]}</s></div><div><div class="et">${e[2]}</div><div class="es">${e[3]}</div></div></div>`;
const heroTop = (name, big = false) => `<div class="hi"><div class="nm"${big ? ' style="font-size:34px"' : ''}>Ciao, ${name}</div><div class="hb">${I('search')}</div><div class="hb">${I('bell')}<span class="dot"></span></div><div class="hb av">${name[0]}</div></div>`;
const featured = `<div class="card ccard"><div style="display:flex;justify-content:space-between;align-items:center"><span class="meta">Circolare n. 112 · oggi</span><span class="bdg g"><i></i>Ti riguarda</span></div>
  <div class="ttl">Viaggio d'istruzione a Praga — classi quarte</div><div class="ai">${I('sparkles')}<span>Dal 18 al 22 novembre. Autorizzazione entro venerdì 23.</span></div></div>`;
/* Home del telefono */
const homePhone = (name = 'Chiara') => `<div class="hero">${SB}${heroTop(name)}
  <div class="qrow">${QUICK.map(([ic, l]) => `<div class="qi"><div class="t">${I(ic)}</div>${l}</div>`).join('')}</div></div>
  <div class="body" style="top:256px"><div><div class="sect">In evidenza</div>${featured}</div>
  <div><div class="sect">Prossimi eventi</div><div class="card">${EVENTS.map(erow).join('')}</div></div></div>${navHTML(2)}`;
/* Home su tablet: due colonne */
const homeWide = (name, glass) => `<div class="hero" style="padding:58px 34px 26px">${SB}${heroTop(name, true)}
  <div class="qrow" style="width:640px">${QUICK.map(([ic, l]) => `<div class="qi" style="font-size:14px"><div class="t" style="height:64px">${I(ic)}</div>${l}</div>`).join('')}</div></div>
  <div style="position:absolute;left:28px;right:28px;top:290px;display:flex;gap:20px">
    <div style="flex:1;display:flex;flex-direction:column;gap:14px"><div><div class="sect">In evidenza</div>${featured}</div>
      <div><div class="sect">Ultime circolari</div><div class="card">
        <div class="erow"><div class="dt">${I('file-text', 'style="width:22px;height:22px"')}</div><div style="flex:1"><div class="et">Assemblea d'istituto di ottobre</div><div class="es">n. 111 · ieri</div></div><span class="bdg g"><i></i>Ti riguarda</span></div>
        <div class="erow"><div class="dt">${I('file-text', 'style="width:22px;height:22px"')}</div><div style="flex:1"><div class="et">Corso di preparazione ai test</div><div class="es">n. 110 · lun</div></div><span class="bdg y"><i></i>Potenziale</span></div>
      </div></div></div>
    <div style="flex:1"><div class="sect">Prossimi eventi</div><div class="card">${EVENTS.map(erow).join('')}${erow(['29', 'OTT', "Assemblea d'istituto", '4ª–6ª ora'])}</div></div>
  </div>${navHTML(2, glass ? 'left:50%;right:auto;width:600px;margin-left:-300px' : 'height:76px;padding-bottom:4px')}`;
/* un dispositivo con dentro l'app */
function devHTML(kind, s, inner, attrs = '') {
  return `<div class="dev ${kind}" ${attrs}><div class="frame"></div><div class="scr"><div class="${appCls(s, kind === 'droid' || kind === 'tab' ? 'droidsb' : '')}" style="${appVars(s)}">${inner}</div></div><div class="cam"></div></div>`;
}
/* banner di notifica nell'app (stile di sistema: chiaro su iOS, tonale su Android) */
const banner = (title, text, droid = false, cls = 'bn') => `<div class="a ${cls}" style="left:10px;right:10px;top:${droid ? 12 : 14}px;z-index:30;display:flex;gap:12px;align-items:center;padding:13px 15px;
  border-radius:${droid ? 26 : 28}px;background:${droid ? 'rgba(236,239,250,.97)' : 'rgba(245,246,252,.92)'};box-shadow:0 14px 34px rgba(0,0,0,.28);color:#111">
  <div style="width:42px;height:42px;border-radius:11px;flex:none;background:linear-gradient(150deg,#0B1330,#1B2E7A);padding:5px">${logoSVG('n' + Math.random().toString(36).slice(2, 7))}</div>
  <div style="min-width:0;flex:1"><div style="display:flex;justify-content:space-between;font-size:13px;font-weight:700"><span>AILA · ${title}</span><span style="font-weight:500;color:#777">ora</span></div>
  <div style="font-size:14px;font-weight:500;line-height:1.3;margin-top:2px;color:#222;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${text}</div></div></div>`;

// ---------- schermate delle sei funzioni (usate dal trailer e dall'intro) ----------
// Ogni funzione restituisce l'HTML della schermata (inner) e i dati che servono per animarla.
function scrCircolari() {
    const CARDS = [['112', "Viaggio d'istruzione a Praga — classi quarte", 'g', 'Ti riguarda'], ['111', "Assemblea d'istituto di ottobre", 'g', 'Ti riguarda'],
      ['110', 'Corso di preparazione ai test universitari', 'y', 'Potenziale interesse'], ['109', 'Uscita anticipata delle classi prime', 's', 'Non ti riguarda'], ['108', 'Sciopero dei mezzi pubblici', 'y', 'Potenziale interesse']];
    const BUL = ['Viaggio a Praga dal 18 al 22 novembre.', 'Costo 380 €: acconto di 150 €, poi il saldo.', 'Autorizzazione firmata al coordinatore.'];
    const inner = `${SB}<div class="layer" id="lst">
        <div class="sh"><div class="t">Circolari</div><div class="hb">${I('search')}</div></div>
        <div class="a seg" style="left:20px;top:118px"><span class="on">Tutte</span><span>Ti riguarda</span><span>Potenziale</span></div>
        ${CARDS.map((c, i) => `<div class="a card ccard cc" style="left:16px;right:16px;top:0;height:122px">
          <div class="meta">n. ${c[0]} · ${i ? (i === 1 ? 'ieri' : 'lun') : 'ora'}</div><div class="ttl" style="padding-right:4px">${c[1]}</div>
          <div style="margin-top:9px;position:relative;height:24px"><span class="a bdg aib scan" style="left:0;top:0">${I('sparkles')}Analisi AI…</span><span class="a bdg ${c[2]} fin" style="left:0;top:0"><i></i>${c[3]}</span></div></div>`).join('')}
        ${navHTML(1)}</div>
      <div class="layer" id="det" style="background:var(--bg)">
        <div class="a" style="left:20px;top:62px;display:flex;align-items:center;gap:6px;color:var(--acc);font-size:16px;font-weight:600">${I('arrow-left', 'style="width:20px;height:20px"')}Circolari</div>
        <div class="a" style="left:22px;right:22px;top:100px;font-size:24px;font-weight:800;line-height:1.25">Viaggio d'istruzione a Praga — classi quarte</div>
        <span class="a bdg g" style="left:22px;top:170px"><i></i>Ti riguarda</span>
        <div class="a card" style="left:16px;right:16px;top:212px;padding:18px">
          <div style="display:flex;align-items:center;gap:8px;font-size:15px;font-weight:800;color:#6D28D9">${I('sparkles', 'style="width:19px;height:19px"')}Riassunto AI</div>
          ${BUL.map(b => `<div class="bl" style="display:flex;gap:10px;margin-top:12px;font-size:15.5px;line-height:1.4"><span style="width:7px;height:7px;border-radius:50%;background:var(--acc);margin-top:8px;flex:none"></span>${b}</div>`).join('')}</div>
        <div class="a card" id="ddl" style="left:16px;right:16px;top:440px;padding:16px">
          <div style="font-size:13px;font-weight:700;color:var(--faint);letter-spacing:.08em">SCADENZE</div>
          <div style="display:flex;align-items:center;gap:12px;margin-top:10px"><div class="dt"><b>23</b><s>OTT</s></div><div style="flex:1"><div style="font-size:15px;font-weight:700">Consegna autorizzazione</div><div style="font-size:12.5px;color:var(--faint);margin-top:3px">Al coordinatore di classe</div></div></div>
          <div id="added" style="margin-top:12px;display:inline-flex;align-items:center;gap:8px;font-size:14px;font-weight:700;color:#047857;background:rgba(16,185,129,.16);padding:8px 14px;border-radius:999px">${I('circle-check', 'style="width:17px;height:17px"')}Aggiunta al calendario</div></div>
      </div>
      ${banner('Nuova circolare', "n. 112 — Viaggio d'istruzione a Praga")}`;
    return { CARDS, BUL, inner };
}

function scrAssistant() {
    const QTXT = "Entro quando consegno l'autorizzazione per Praga?";
    const ATXT = 'Entro venerdì 23 ottobre, firmata, al coordinatore di classe. Poi c\'è l\'acconto di 150 €.';
    const inner = `${SB}
      <div class="a" style="left:20px;right:20px;top:58px;display:flex;align-items:center;gap:12px">
        <div style="width:44px;height:44px;border-radius:50%;background:linear-gradient(140deg,#0E4C6E,#2F7FB8);display:grid;place-items:center;color:#fff">${I('sparkles', 'style="width:22px;height:22px"')}</div>
        <div><div style="font-size:21px;font-weight:800">AILA Assistant</div><div style="font-size:12.5px;color:var(--faint)">Risponde con i dati della tua classe</div></div></div>
      <div class="a" id="sugg" style="left:20px;right:20px;top:150px;display:flex;flex-wrap:wrap;gap:8px">
        ${["Cosa c'è domani?", 'Ultime circolari', 'Quando ho storia?'].map(x => `<span style="font-size:13.5px;font-weight:600;padding:10px 14px;border-radius:16px;background:var(--card);color:var(--muted)">${x}</span>`).join('')}</div>
      <div class="a" id="ub" style="right:16px;top:150px;max-width:300px;background:var(--acc);color:#fff;font-size:16px;line-height:1.4;padding:13px 16px;border-radius:22px 22px 6px 22px;font-weight:500">${QTXT}</div>
      <div class="a" id="think" style="left:16px;top:270px;display:flex;gap:7px;padding:16px 18px;border-radius:22px;background:var(--card)">${[0, 1, 2].map(() => '<i style="width:9px;height:9px;border-radius:50%;background:var(--muted);display:block" class="td"></i>').join('')}</div>
      <div class="a" id="ab" style="left:16px;right:40px;top:270px;background:var(--card);font-size:16px;line-height:1.5;padding:15px 17px;border-radius:22px 22px 22px 6px">
        <span id="at"></span><span id="ac" style="display:inline-block;width:2px;height:1em;background:var(--acc);vertical-align:-2px;margin-left:2px"></span>
        <div style="display:flex;gap:8px;margin-top:12px;flex-wrap:wrap">${[['file-text', 'Circolare n. 112'], ['calendar', 'Calendario']].map(([ic, l]) => `<span class="src" style="display:inline-flex;align-items:center;gap:7px;font-size:13px;font-weight:700;padding:7px 12px;border-radius:12px;background:var(--cont);color:var(--onCont)">${I(ic, 'style="width:15px;height:15px"')}${l}</span>`).join('')}</div></div>
      <div class="a" style="left:14px;right:14px;bottom:26px;display:flex;gap:10px;align-items:center">
        <div style="flex:1;min-height:54px;border-radius:27px;background:var(--card);padding:16px 18px;font-size:15px;color:var(--ink)"><span id="qt"></span><span id="qc" style="display:inline-block;width:2px;height:1.1em;background:var(--acc);vertical-align:-3px"></span><span id="ph2" style="color:var(--faint)">Chiedi qualcosa…</span></div>
        <div id="send" style="width:54px;height:54px;border-radius:50%;background:var(--acc);display:grid;place-items:center;color:#fff">${I('send', 'style="width:22px;height:22px"')}</div></div>`;
    return { QTXT, ATXT, inner };
}

function scrCalendario() {
    const EV = [[22, 'Verifica di matematica', '3ª ora', '#F87171'], [23, 'Autorizzazione per Praga', 'Dalla circolare n. 112', '#60A5FA'],
      [27, 'Interrogazione di storia', '2ª ora', '#FBBF24'], [29, "Assemblea d'istituto", '4ª–6ª ora', '#34D399']];
    const days = []; for (let i = 0; i < 35; i++) { const d = i - 2; days.push(d >= 1 && d <= 31 ? d : 0); }
    const cw = 48, x0 = 18;
    const inner = `${SB}<div class="sh"><div class="t">Calendario</div><div class="hb" style="background:var(--acc);color:#fff;border:0">${I('calendar-plus')}</div></div>
      <div class="a card" style="left:14px;right:14px;top:118px;height:336px;padding:16px">
        <div style="display:flex;justify-content:space-between;font-size:18px;font-weight:800"><span>Ottobre 2026</span><span style="color:var(--faint);font-weight:600">‹ ›</span></div>
        <div style="display:flex;margin-top:14px;font-size:12px;font-weight:700;color:var(--faint)">${'LMMGVSD'.split('').map(c => `<span style="width:${cw}px;text-align:center">${c}</span>`).join('')}</div>
        ${days.map((d, i) => d ? `<div class="a dc" data-d="${d}" style="left:${x0 + (i % 7) * cw}px;top:${76 + Math.floor(i / 7) * 50}px;width:${cw - 4}px;height:44px;border-radius:14px;text-align:center;font-size:16px;font-weight:600;padding-top:6px;${d === 19 ? 'background:var(--acc);color:#fff' : ''}">${d}<i style="position:absolute;left:50%;bottom:7px;width:6px;height:6px;margin-left:-3px;border-radius:50%;display:block" class="edot"></i></div>` : '').join('')}
      </div>
      <div class="a" style="left:16px;right:16px;top:468px;display:flex;flex-direction:column;gap:9px">
        ${EV.map(e => `<div class="card evr" style="display:flex;align-items:center;gap:12px;padding:12px 14px;border-left:4px solid ${e[3]}"><div style="font-size:20px;font-weight:800;width:34px;text-align:center">${e[0]}</div><div><div style="font-size:15px;font-weight:700">${e[1]}</div><div style="font-size:12.5px;color:var(--faint);margin-top:2px">${e[2]}</div></div></div>`).join('')}</div>
      ${navHTML(0)}`;
    return { EV, days, cw, inner };
}

function scrSondaggi() {
    const DATES = [['Lun 2 nov', [0, 3, 5]], ['Mer 4 nov', ['me', 7, 9]], ['Ven 6 nov', [11, 13, 15]]];
    const inner = `${SB}
      <div class="a" style="left:20px;top:58px;display:flex;align-items:center;gap:6px;color:var(--acc);font-size:15px;font-weight:700">${I('arrow-left', 'style="width:19px;height:19px"')}Sondaggi</div>
      <div class="a" style="left:22px;right:22px;top:92px;font-size:25px;font-weight:800;line-height:1.2">Interrogazioni di storia</div>
      <div class="a" style="left:22px;top:132px;display:flex;gap:8px"><span class="bdg" style="background:var(--cont);color:var(--onCont)">${I('vote', 'style="width:14px;height:14px"')}Hai 3 voti</span><span class="bdg s"><i></i>Chiude venerdì</span></div>
      ${DATES.map((d, i) => `<div class="a card" style="left:14px;right:14px;top:${182 + i * 168}px;height:152px;padding:16px 18px">
        <div style="display:flex;justify-content:space-between;align-items:center"><div style="font-size:19px;font-weight:800">${d[0]}</div><span class="bdg g ok" style="opacity:0"><i></i>Confermata</span></div>
        <div style="font-size:12.5px;color:var(--faint);margin-top:2px">3 posti · ${i === 1 ? '2ª' : i ? '1ª' : '3ª'} ora</div>
        ${[0, 1, 2].map(k => `<div class="a" style="left:${18 + k * 66}px;top:78px;width:54px;height:54px;border-radius:50%;border:2px dashed color-mix(in srgb,var(--ink) 18%,transparent)"></div>`).join('')}
        ${d[1].map((k, j) => `<div class="a pav${k === 'me' ? ' me' : ''}" style="left:${18 + j * 66}px;top:78px;width:54px;height:54px;border-radius:50%;display:grid;place-items:center;font-size:17px;font-weight:800;color:#fff;background:${k === 'me' ? 'linear-gradient(140deg,#F59E0B,#EC4899)' : avBg(k)};${k === 'me' ? 'box-shadow:0 0 0 3px var(--bg),0 0 0 6px var(--acc)' : ''}">${k === 'me' ? 'C' : KIDS[k]}</div>`).join('')}
      </div>`).join('')}
      <div class="a" id="closed" style="left:14px;right:14px;bottom:110px;padding:15px 18px;border-radius:22px;background:var(--acc);color:#fff;display:flex;align-items:center;gap:10px;font-size:15px;font-weight:700">${I('shield-check', 'style="width:22px;height:22px"')}Date assegnate: equo per tutti</div>
      ${navHTML(3)}`;
    return { DATES, inner };
}

function scrMappaPosti() {
    const R = rng(99);
    const perm = () => { const a = [...Array(24).keys()]; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(R() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
    const LAY = [perm(), perm(), perm()]; // per ogni proposta: posto -> studente
    const seatXY = k => { const r = Math.floor(k / 6), c = k % 6, desk = Math.floor(c / 2); return [26 + desk * 122 + (c % 2) * 52, 236 + r * 92]; };
    const inner = `${SB}<div class="sh"><div class="t">Mappa posti</div><div class="hb">${I('users')}</div></div>
      <div class="a" style="left:18px;right:18px;top:116px;height:46px;border-radius:23px;background:var(--card);border:1px solid var(--cardB);display:flex;padding:4px">
        <div id="knob" class="a" style="left:4px;top:4px;width:120px;height:36px;border-radius:18px;background:var(--acc)"></div>
        ${['Proposta A', 'Proposta B', 'Proposta C'].map(l => `<div class="pp" style="flex:1;position:relative;display:grid;place-items:center;font-size:13.5px;font-weight:700">${l}</div>`).join('')}</div>
      <div class="a" style="left:110px;right:110px;top:182px;height:30px;border-radius:10px;background:var(--card);border:1px solid var(--cardB);text-align:center;font-size:12px;font-weight:700;color:var(--faint);line-height:28px;letter-spacing:.12em">CATTEDRA</div>
      ${[0, 1, 2, 3].map(r => [0, 1, 2].map(d => `<div class="a" style="left:${18 + d * 122}px;top:${228 + r * 92}px;width:112px;height:62px;border-radius:18px;background:var(--card);border:1px solid var(--cardB)"></div>`).join('')).join('')}
      ${KIDS.map((k, i) => `<div class="a seat" style="left:0;top:0;width:46px;height:46px;border-radius:50%;display:grid;place-items:center;font-size:14px;font-weight:800;color:#fff;background:${avBg(i)};box-shadow:0 4px 10px rgba(0,0,0,.2),inset 0 0 0 2px rgba(255,255,255,.35)">${k}</div>`).join('')}
      <div class="a" id="fac" style="left:16px;right:16px;top:612px;display:flex;flex-wrap:wrap;gap:8px">
        ${[['heart', 'Affinità'], ['graduation-cap', 'Aiuto tra compagni'], ['zap', 'Rumore'], ['users', 'Altezza']].map(([ic, l]) => `<span class="fc card" style="display:inline-flex;align-items:center;gap:7px;padding:9px 13px;border-radius:999px;font-size:13.5px;font-weight:700;color:var(--ink)">${I(ic, 'style="width:16px;height:16px;color:var(--acc)"')}${l}</span>`).join('')}</div>
      ${navHTML(4)}`;
    return { R, perm, LAY, seatXY, inner };
}

function scrBacheca() {
    const PR = [["Distributore d'acqua al secondo piano", 12, 31, true], ['Torneo di pallavolo a fine anno', 18, 24, false], ['Musica durante i lavori di gruppo', 9, 17, true]];
    const RK = [['Praga', 34, 0], ['Vienna', 20, 2], ['Berlino', 26, 1], ['Barcellona', 12, 3]]; // [nome, punti finali, posizione finale]
    const inner = `${SB}<div class="layer" id="brd">
        <div class="sh"><div class="t">Classe</div><div class="hb av">C</div></div>
        <div class="a" style="left:18px;right:18px;top:116px;height:46px;border-radius:23px;background:var(--card);display:flex;padding:4px">
          <div style="flex:1;display:grid;place-items:center;font-size:14px;font-weight:700;color:var(--muted)">Circolari</div>
          <div style="flex:1;display:grid;place-items:center;font-size:14px;font-weight:700;border-radius:19px;background:var(--cont);color:var(--onCont)">Bacheca</div></div>
        ${PR.map((p, i) => `<div class="a card prp" style="left:14px;right:14px;top:${180 + i * 156}px;padding:16px 18px;height:142px">
          <div style="display:flex;align-items:center;gap:8px;font-size:12.5px;color:var(--faint);font-weight:600">${p[3] ? `${I('eye-off', 'style="width:15px;height:15px"')}Anonima` : `${I('user', 'style="width:15px;height:15px"')}Marco R.`} · ${i + 1} g fa</div>
          <div style="font-size:17px;font-weight:700;line-height:1.3;margin-top:8px">${p[0]}</div>
          <div style="display:flex;align-items:center;gap:16px;margin-top:12px;font-size:14px;color:var(--muted);font-weight:700">
            <span class="up" style="display:inline-flex;align-items:center;gap:7px;padding:6px 12px;border-radius:999px;background:var(--cont);color:var(--onCont)">${I('thumbs-up', 'style="width:16px;height:16px"')}<b class="vn">${p[1]}</b></span>
            <span style="display:inline-flex;align-items:center;gap:7px">${I('message-circle', 'style="width:16px;height:16px"')}${4 + i * 3}</span></div></div>`).join('')}
        ${navHTML(1)}</div>
      <div class="layer" id="rk" style="background:var(--bg)">
        <div class="a" style="left:20px;top:58px;display:flex;align-items:center;gap:6px;color:var(--acc);font-size:15px;font-weight:700">${I('arrow-left', 'style="width:19px;height:19px"')}Sondaggi</div>
        <div class="a" style="left:22px;right:22px;top:92px;font-size:25px;font-weight:800">Dove andiamo in gita?</div>
        <div class="a" style="left:22px;top:130px;font-size:13px;color:var(--faint);font-weight:600">Classifica · si aggiorna da sola</div>
        ${RK.map((r, i) => `<div class="a rkr" style="left:14px;right:14px;top:0;height:74px;border-radius:22px;background:var(--card);overflow:hidden">
          <div class="rb" style="position:absolute;left:0;top:0;bottom:0;background:color-mix(in srgb,var(--acc) 34%,transparent)"></div>
          <div style="position:absolute;left:16px;top:0;bottom:0;display:flex;align-items:center;gap:12px;font-size:17px;font-weight:700">
            <i class="rn" style="font-style:normal;width:32px;height:32px;border-radius:50%;background:var(--cont);color:var(--onCont);display:grid;place-items:center;font-size:14px;font-weight:800">${i + 1}</i>${r[0]}</div>
          <div class="rp" style="position:absolute;right:18px;top:0;bottom:0;display:flex;align-items:center;font-size:16px;font-weight:800">0</div></div>`).join('')}
        <div class="a" id="win" style="left:14px;right:14px;top:540px;display:flex;align-items:center;gap:10px;padding:15px 18px;border-radius:22px;background:var(--acc);color:#fff;font-size:15px;font-weight:700">${I('trophy', 'style="width:22px;height:22px"')}Vince Praga, con 34 punti</div>
      </div>`;
    return { PR, RK, inner };
}
