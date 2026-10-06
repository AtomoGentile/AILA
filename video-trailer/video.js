'use strict';
/*
 * Trailer di AILA.
 * Tutto è funzione del tempo: seek(t) disegna il fotogramma al secondo t, senza stato tra un fotogramma e
 * l'altro, così l'anteprima nel browser e l'esportazione (render.js) producono le stesse immagini.
 * 128 BPM: B(n) è l'inizio della battuta n, Q la durata di un quarto. Scaletta in timeline.js.
 */
const { BAR, BEAT: Q, DURATION } = TIMELINE;
const B = n => n * BAR;
const stage = document.getElementById('stage');
const SFX = []; // {t, type}: letti da music.js (via render.js sfx) per gli effetti sonori

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
const homePhone = (name = 'Giulia') => `<div class="hero">${SB}${heroTop(name)}
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

// =====================================================================================
// SFONDO (sempre presente): macchie di colore che cambiano tinta con le sezioni
// =====================================================================================
const bg = document.createElement('div');
bg.className = 'layer';
bg.innerHTML = `<div class="blob" id="b1"></div><div class="blob" id="b2"></div><div class="blob" id="b3"></div>
  <div class="layer" id="rays" style="inset:auto;left:-440px;top:-1000px;width:2800px;height:2800px;border-radius:50%;
    background:repeating-conic-gradient(from 0deg,rgba(150,170,255,.10) 0deg 5deg,transparent 5deg 15deg);
    -webkit-mask-image:radial-gradient(circle,#000 0,transparent 55%);mask-image:radial-gradient(circle,#000 0,transparent 55%)"></div>
  <div class="dots"></div><div class="layer" id="bgdim" style="background:#020409"></div>`;
stage.appendChild(bg);
const FEAT_COL = [['#3B82F6', '#6366F1', '#06B6D4'], ['#0EA5E9', '#14B8A6', '#6366F1'], ['#8B5CF6', '#A855F7', '#3B82F6'],
  ['#22C55E', '#10B981', '#0EA5E9'], ['#14B8A6', '#06B6D4', '#8B5CF6'], ['#F97316', '#EC4899', '#8B5CF6']];
// [inizio, colori] (sfumati fra una tappa e l'altra in mezza battuta)
const PAL = [[0, ['#7F1D1D', '#312E81', '#1E1B4B']], [B(6), ['#1E1B4B', '#0F172A', '#111827']], [B(8), ['#3B82F6', '#8B5CF6', '#06B6D4']],
  ...FEAT_COL.map((c, i) => [B(12 + i * 4), c]), [B(36), ['#312E81', '#1E3A8A', '#0F172A']], [B(38), ['#3B82F6', '#8B5CF6', '#06B6D4']],
  [B(56), ['#1E3A8A', '#4C1D95', '#0F766E']], [B(59), ['#3B82F6', '#8B5CF6', '#06B6D4']]];
let themeGlow = null; // [r,g,b] dell'accento nella scena dei temi (lo imposta la scena, prima dello sfondo)
const mixA = (a, b, p) => { const A = hex(a), C = hex(b); return A.map((v, i) => Math.round(lerp(v, C[i], p))); };
function renderBg(t) {
  let k = 0; while (k < PAL.length - 1 && t >= PAL[k + 1][0]) k++;
  const prev = PAL[Math.max(0, k - 1)][1], cur = PAL[k][1], p = k === 0 ? 1 : P(t, PAL[k][0], BAR / 2, E.inOut);
  const b = id => document.getElementById(id);
  [['b1', 1050 + 240 * Math.sin(t * .21), -420 + 160 * Math.cos(t * .17)], ['b2', -340 + 220 * Math.cos(t * .19), 330 + 140 * Math.sin(t * .23)],
    ['b3', 420 + 300 * Math.sin(t * .13 + 1), 380 + 160 * Math.sin(t * .16)]].forEach(([id, x, y], i) => {
    const c = themeGlow && i < 2 ? themeGlow : mixA(prev[i], cur[i], p);
    const el = b(id), bgs = `radial-gradient(circle,rgba(${c.join(',')},.5),transparent 62%)`;
    if (el._bg !== bgs) { el.style.background = bgs; el._bg = bgs; }
    pose(el, { x, y, o: t < B(8) ? .55 : .95 });
  });
  pose(b('rays'), { r: t * 6, o: (P(t, B(8), .3) * (1 - P(t, B(12) - .5, 1))) + P(t, B(59), .5) * .8 });
  // scuro all'inizio e nella pausa prima del drop
  const dim = (1 - P(t, B(1), BAR * 2)) * .85 + (t >= B(6) && t < B(8) ? .7 : 0);
  b('bgdim').style.opacity = clamp(dim).toFixed(3);
}

// lampo bianco degli impatti
const flash = document.createElement('div');
flash.className = 'layer';
flash.style.cssText = 'background:radial-gradient(circle at 50% 45%,rgba(230,236,255,.95),rgba(140,160,255,.35) 45%,transparent 75%);z-index:50;pointer-events:none';
stage.appendChild(flash);
const FLASHES = [];
const addFlash = (t, a = 1) => FLASHES.push([t, a]);

// =====================================================================================
// 1 · IL CAOS (battute 0–8)
// =====================================================================================
{
  const N = [
    ['r', 'Circolare n. 104 — Uscita didattica'], ['g', 'ma la verifica di mate è giovedì??'], ['r', 'Circolare n. 105 — Variazione orario'],
    ['g', 'qualcuno ha letto la circolare?'], ['g', 'chi si fa interrogare lunedì?'], ['r', "Circolare n. 106 — Assemblea d'istituto"],
    ['g', '52 messaggi non letti'], ['r', 'Circolare n. 107 — Contributo volontario'], ['g', 'da domani dove mi siedo?'],
    ['g', 'raga entro quando si paga la gita?'], ['r', 'Circolare n. 108 — Sciopero dei mezzi'], ['g', 'ma oggi ci sono le prime due ore?'],
    ['r', 'Circolare n. 109 — Corso sulla sicurezza'], ['g', 'chi ha i compiti di storia'], ['g', '128 messaggi non letti'],
    ['r', 'Circolare n. 110 — Open day'], ['g', 'NON HO CAPITO NIENTE'], ['r', 'Circolare n. 111 — Orientamento'],
    ['g', "nessuno l'aveva detto!!"], ['g', 'scusate ma la gita è confermata?'], ['r', 'Circolare n. 112 — Viaggio a Praga'],
    ['g', '256 messaggi non letti'], ['g', 'aiuto'], ['r', 'Circolare n. 113 — Variazione orario'],
  ];
  const R = rng(42);
  const POS = N.map((_, i) => [40 + R() * 1340, 30 + R() * 960, (R() - .5) * 16]);
  // arrivano sempre più fitte: due per battuta all'inizio, poi a raffica
  const TIMES = N.map((_, i) => B(1) + B(4.6) * Math.pow(i / (N.length - 1), .62));
  TIMES.forEach((tt, i) => sfx(tt, N[i][0] === 'r' ? 'ping' : 'ping2'));
  const SL = [ // [testo, battuta d'ingresso, classe]
    ['Circolari.', 2, ''], ['Scadenze.', 2.5, ''], ['Interrogazioni.', 3, ''], ['Chat infinite.', 3.5, ''],
    ['E le cose importanti', 4, 'small'], ['si perdono.', 4.5, 'red'],
  ];
  const S = addScene(0, B(8) + .1, `
    <div class="a center" id="clock" style="left:0;width:1920px;top:290px;font-size:250px;font-weight:300;letter-spacing:-.04em;line-height:1">7:42</div>
    <div class="a center" id="date" style="left:0;width:1920px;top:560px;font-size:38px;font-weight:500;color:var(--muted)">Lunedì 19 ottobre</div>
    ${N.map(([k, txt], i) => `<div class="a notif n" style="left:${POS[i][0]}px;top:${POS[i][1]}px">
      <div class="ic" style="background:${k === 'r' ? 'linear-gradient(140deg,#64748B,#334155)' : 'linear-gradient(140deg,#22C55E,#16A34A)'}">${I(k === 'r' ? 'file-text' : 'message-circle')}</div>
      <div style="flex:1;min-width:0"><div class="nap">${k === 'r' ? 'Registro elettronico' : 'Gruppo classe'}<span>ora</span></div><div class="tx">${txt}</div></div></div>`).join('')}
    <div class="a center" id="cnt" style="left:0;width:1920px;top:56px"><span class="chip" style="background:rgba(239,68,68,.18);border-color:rgba(248,113,113,.5);color:#FECACA">${I('bell-ring')}<span id="cntn">0</span>&nbsp;notifiche</span></div>
    <div class="layer" id="dim1" style="background:radial-gradient(ellipse at center,rgba(2,4,9,.94) 25%,rgba(2,4,9,.55) 85%)"></div>
    ${SL.map(([txt, , c], i) => `<div class="a center slam sl" style="left:0;width:1920px;top:${c === 'small' ? 430 : 455}px;${c === 'small' ? 'font-size:110px;font-weight:700' : ''}${c === 'red' ? ';top:560px' : ''}">${c === 'red' ? `<em class="grad" style="--g:linear-gradient(95deg,#FCA5A5,#F87171 50%,#FB923C)">${txt}</em>` : txt}</div>`).join('')}
    <div class="a center big" id="q1" style="left:0;width:1920px;top:330px">E se la tua classe avesse</div>
    <div class="a center" id="q2" style="left:0;width:1920px;top:470px;font-size:190px;font-weight:800;letter-spacing:-.045em;line-height:1"><em class="grad">un superpotere?</em></div>`);
  const cards = S.qa('.n'), sl = S.qa('.sl');
  sfx(Q, 'buzz'); sfx(Q * 3, 'buzz');
  SL.forEach(([, bt]) => sfx(B(bt), 'slam'));
  sfx(B(5.75), 'suck');
  S.render = t => {
    io(S.q('#clock'), t, .15, B(2) - .3, { dy: 20, b: 16, d: .9, e: E.out });
    io(S.q('#date'), t, .5, B(2) - .3, { dy: 16, d: .9, e: E.out });
    // le notifiche, poi il risucchio al centro
    const suck = t >= B(5.75);
    let shown = 0;
    cards.forEach((c, i) => {
      const t0 = TIMES[i], pin = P(t, t0, .35, E.back);
      if (t >= t0) shown++;
      const pc = P(t, B(5.75) + i * .012, .5, E.in);
      const cx = 960 - 280 - POS[i][0], cy = 540 - 45 - POS[i][1];
      const jit = t > B(5) && !suck ? Math.sin(t * 90 + i) * 6 : 0;
      pose(c, { o: clamp(pin * 1.6) * (1 - pc) * lerp(1, .55, clamp((t - t0 - 2) / 4)), x: cx * pc + jit,
        y: (1 - pin) * -50 + (t - t0) * 6 * (1 - pc) + cy * pc, s: lerp(.6, 1, pin) * lerp(1, .1, pc), r: POS[i][2] * (1 - pc), b: pc * 10 });
    });
    const total = Math.round(lerp(0, 256, clamp(shown / N.length)) * (shown ? 1 : 0));
    setText(S.q('#cntn'), String(total));
    io(S.q('#cnt'), t, TIMES[0], B(5.75), { dy: -30, d: .4 });
    S.q('#dim1').style.opacity = P(t, B(2) - .2, .3) * (1 - P(t, B(5), .3)) * .95;
    // parole sbattute a tempo; ognuna esce quando arriva la successiva
    sl.forEach((el, i) => {
      const tin = B(SL[i][1]), tout = i === 4 ? B(5) : i === 5 ? B(5) : B(SL[i + 1] ? SL[i + 1][1] : 5) - .08;
      slam(el, t, tin, tout);
      if (i < 4 && t > tin && t < tout && el._vis === 1) { // tremolio di macchina da presa sull'impatto
        const k = Math.exp(-(t - tin) * 10) * 14;
        el.style.transform += ` translate(${Math.sin(t * 97) * k}px,${Math.cos(t * 83) * k}px)`;
      }
    });
    // la domanda, sulla pausa prima del drop
    rw(S.q('#q1'), t, 0, B(7.75), { times: [B(6), B(6) + Q, B(6) + Q * 2, B(6) + Q * 3, B(6.5) + Q * .5], dy: 30, os: 1.4, od: .25 });
    const pz = P(t, B(7.75), B(.25), E.in);
    io(S.q('#q2'), t, B(7), null, { d: .9, dy: 0, s0: .7, b: 30, e: E.out });
    if (t >= B(7)) pose(S.q('#q2'), { o: P(t, B(7), .6) * (1 - pz), s: lerp(.85, 1.04, P(t, B(7), B(.75), E.out)) * lerp(1, 3.5, pz), b: pz * 30 + (1 - P(t, B(7), .5)) * 20 });
  };
}

// =====================================================================================
// 2 · DROP: IL LOGO (battute 8–12)
// =====================================================================================
{
  const MODS = [['file-text', 'Circolari'], ['sparkles', 'AILA Assistant'], ['calendar', 'Calendario'], ['chart-column', 'Sondaggi'], ['armchair', 'Mappa posti'], ['message-square', 'Bacheca']];
  const R = rng(7);
  const PARTS = [...Array(56)].map(() => [R() * Math.PI * 2, 300 + R() * 900, 3 + R() * 9, R()]);
  const S = addScene(B(8), B(12) + .1, `
    <div class="a" id="ring" style="left:760px;top:140px;width:400px;height:400px;border-radius:50%;border:4px solid rgba(190,205,255,.9)"></div>
    <div class="a" id="ring2" style="left:760px;top:140px;width:400px;height:400px;border-radius:50%;border:2px solid rgba(160,240,255,.7)"></div>
    ${PARTS.map(p => `<div class="a pt" style="left:958px;top:338px;width:${p[2]}px;height:${p[2]}px;border-radius:50%;background:${p[3] > .5 ? '#BFD0FF' : '#99F6E4'};box-shadow:0 0 12px currentColor"></div>`).join('')}
    <div id="grp" class="layer">
      <div class="a" id="logo" style="left:800px;top:160px;width:320px;height:320px">${logoSVG('A')}</div>
      <div class="a center" id="wm" style="left:0;width:1920px;top:500px;font-size:230px;font-weight:800;letter-spacing:.05em;line-height:1"></div>
      <div class="a center" id="tag" style="left:0;width:1920px;top:760px;font-size:48px;color:#DCE3FF;font-weight:500">La tua scuola, <em class="grad" style="font-weight:700">sincronizzata.</em></div>
      <div class="a" id="mods" style="left:0;width:1920px;top:880px;display:flex;justify-content:center;gap:16px">${MODS.map(([ic, l]) => `<span class="chip md">${I(ic, 'style="color:#A5B8FF"')}${l}</span>`).join('')}</div>
    </div>`);
  const wm = S.q('#wm'); wm.innerHTML = 'AILA'.split('').map(c => `<span class="w">${c}</span>`).join(''); wm._w = [...wm.querySelectorAll('.w')];
  const pts = S.qa('.pt'), mods = S.qa('.md');
  sfx(B(8), 'impact'); addFlash(B(8), 1);
  mods.forEach((_, i) => sfx(B(10) + i * Q / 2, 'pop'));
  sfx(B(11.5), 'riserS');
  S.render = t => {
    const t0 = B(8);
    [['#ring', 1.1, 5], ['#ring2', 1.6, 7]].forEach(([id, d, s]) => { const p = P(t, t0, d, E.out5); pose(S.q(id), { o: (1 - p) * .9, s: lerp(.3, s, p) }); });
    pts.forEach((el, i) => {
      const [a, sp, , ph] = PARTS[i], p = P(t, t0, 1.6 + ph, E.out5);
      pose(el, { x: Math.cos(a) * sp * p, y: Math.sin(a) * sp * p * .7, o: (1 - P(t, t0 + .4, 1.4 + ph, E.lin)) * (t >= t0 ? 1 : 0), s: 1 - p * .5 });
    });
    renderLogo(S.q('#logo'), t, t0 - .05, .6);
    pose(S.q('#logo'), { y: Math.sin(t * 1.6) * 6 });
    wm._w.forEach((w, i) => io(w, t, t0 + .25 + i * Q / 4, null, { dy: 0, s0: 1.8, b: 24, d: .3, e: E.out5 }));
    io(S.q('#tag'), t, B(9.5), null, { dy: 30 });
    mods.forEach((m, i) => io(m, t, B(10) + i * Q / 2, null, { dy: 26, s0: .6, e: E.back, d: .35, b: 6 }));
    // tutto vola verso lo spettatore: si entra nelle funzioni
    const pz = P(t, B(11.5), B(.5), E.in);
    pose(S.q('#grp'), { o: 1 - pz, s: lerp(1, 4, pz), b: pz * 26, y: pz * -200 });
  };
}

// =====================================================================================
// 3 · LE SEI FUNZIONI (battute 12–36), quattro battute ciascuna
// =====================================================================================
/* gruppo che entra da destra e esce a sinistra con una "frustata" (sfocatura di movimento) */
function whip(el, t, t0, t1) {
  const pi = P(t, t0, .42, E.out5), po = P(t, t1 - .32, .32, E.in);
  pose(el, { x: (1 - pi) * 700 - po * 900, o: clamp(pi * 3) * (1 - po * .9), b: (1 - pi) * 30 + po * 34, s: lerp(1.06, 1, pi) });
}
/* testo della funzione: etichetta, titolo a parole sulle battute, sottotitolo */
function featText(n, name, h1, sub, left, col) {
  return `<div class="a ftx" style="left:${left}px;top:300px;width:900px">
    <span class="label fl"><b>0${n}</b><span style="opacity:.5">/ 06</span>${name}</span>
    <div class="h1 fh" style="margin-top:34px;--g:linear-gradient(95deg,${col[0]},${col[1]} 60%,${col[2]})">${h1}</div>
    <div class="sub fs" style="margin-top:30px;width:780px">${sub}</div></div>`;
}
function featTextRender(S, t, t0, wordTimes) {
  io(S.q('.fl'), t, t0 + .05, null, { dx: -40, dy: 0, b: 8 });
  rw(S.q('.fh'), t, t0, null, { times: wordTimes, d: .28, s0: 1.35, dy: 0, b: 18 });
  io(S.q('.fs'), t, t0 + Q * 4, null, { dy: 24, d: .5 });
}
const phoneIdle = (t, side) => ({ ry: side * -9 + Math.sin(t * .9) * 2.5, rx: 3 + Math.cos(t * .7) * 1.5, y: Math.sin(t * 1.1) * 8 });
function phoneIn(el, t, t0, side, s = .94) {
  const p = P(t, t0, .7, E.out5), idle = phoneIdle(t, side);
  pose(el, { x: (1 - p) * 220 * side, y: idle.y + (1 - p) * 120, ry: idle.ry + (1 - p) * side * -30, rx: idle.rx, s: s * lerp(.85, 1, p) });
}
const FT = i => B(12 + i * 4); // inizio della funzione i
[0, 1, 2, 3, 4, 5].forEach(i => { sfx(FT(i) - .25, 'whoosh'); if (i) sfx(FT(i), 'crash'); });

// ---------- 01 Circolari (iPhone, Liquid Glass chiaro) ----------
{
  const t0 = FT(0), s = st('glass', false, 'blue');
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
  const S = addScene(t0 - .05, FT(1) + .05, `<div class="layer" id="g">
    ${featText(1, 'Circolari', 'Circolari?<br><em>Riassunte</em> <em>dall\'AI.</em>', 'Ti avvisa subito, ti dice se ti riguarda e salva le scadenze nel calendario.', 150, FEAT_COL[0])}
    <div class="a" id="ph" style="left:1230px;top:90px">${devHTML('iphone', s, inner)}</div></div>`);
  const cc = S.qa('.cc'), bl = S.qa('.bl');
  const tBan = t0 + Q * 2, tNew = B(13), tAn = B(13) + Q * 2, tDet = B(14);
  sfx(tBan, 'notify'); sfx(tAn, 'success'); cc.slice(1).forEach((_, i) => sfx(tAn + (i + 1) * Q / 4, 'blip'));
  sfx(tDet, 'swoosh'); bl.forEach((_, i) => sfx(tDet + (i + 1) * Q, 'blip')); sfx(B(15) + Q * 2, 'success');
  S.render = t => {
    whip(S.q('#g'), t, t0, FT(1));
    featTextRender(S, t, t0, [t0, t0 + Q * 2, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, 1);
    // banner: scende e risale
    const pb = P(t, tBan, .4, E.back), pbo = P(t, tNew - .15, .3, E.in);
    pose(S.q('.bn'), { y: lerp(-140, 0, pb) - pbo * 150, o: t >= tBan ? 1 : 0 });
    // la nuova circolare entra in cima e spinge giù le altre
    const pn = P(t, tNew, .45, E.out5);
    cc.forEach((c, i) => {
      const y = 170 + (i - 1) * 134 + pn * 134;
      if (i === 0) pose(c, { y: 170, o: pn, s: lerp(.85, 1, pn) });
      else pose(c, { y });
      const ta = i === 0 ? tAn : tAn + i * Q / 4, pa = P(t, ta, .3, E.back);
      pose(c.querySelector('.scan'), { o: i === 0 ? (1 - P(t, ta, .15)) * (0.65 + .35 * Math.sin(t * 14)) : 0 });
      pose(c.querySelector('.fin'), { o: i === 0 ? pa : 1, s: i === 0 ? lerp(.5, 1, pa) : 1 });
    });
    // dettaglio con il riassunto
    const pd = P(t, tDet, .5, E.out5);
    pose(S.q('#det'), { x: (1 - pd) * 420, o: t >= tDet ? 1 : 0 });
    pose(S.q('#lst'), { x: -pd * 120, o: 1 - pd * .6 });
    bl.forEach((b, i) => io(b, t, tDet + (i + 1) * Q, null, { dy: 14, d: .35, b: 4 }));
    io(S.q('#ddl'), t, B(15), null, { dy: 30, d: .4 });
    io(S.q('#added'), t, B(15) + Q * 2, null, { dy: 0, s0: .6, e: E.back, d: .35, b: 0 });
  };
}

// ---------- 02 AILA Assistant (Android, Material scuro) ----------
{
  const t0 = FT(1), s = st('mat', true, 'blue');
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
  const S = addScene(t0 - .05, FT(2) + .05, `<div class="layer" id="g">
    ${featText(2, 'AILA Assistant', 'Chiedi.<br><em>AILA</em> <em>risponde.</em>', 'Risposte basate solo sui dati della tua classe, con le fonti da aprire.', 820, FEAT_COL[1])}
    <div class="a" id="ph" style="left:250px;top:90px">${devHTML('droid', s, inner)}</div></div>`);
  const tType = t0 + Q, tSend = B(17) + Q * 2, tAns = B(18), tSrc = B(19) + Q;
  const cps = QTXT.length / (tSend - .15 - tType);
  for (let k = 0; k < QTXT.length; k += 2) sfx(tType + k / cps, 'key');
  sfx(tSend, 'send'); sfx(tAns, 'blip'); sfx(tSrc, 'pop'); sfx(tSrc + Q / 2, 'pop');
  const srcs = S.qa('.src'), dots = S.qa('.td');
  S.render = t => {
    whip(S.q('#g'), t, t0, FT(2));
    featTextRender(S, t, t0, [t0, t0 + Q * 2, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, -1);
    const sent = t >= tSend;
    typeText(S.q('#qt'), sent ? 0 : t, tType, cps, QTXT, S.q('#qc'));
    if (sent) { setText(S.q('#qt'), ''); S.q('#qc').style.opacity = 0; }
    S.q('#ph2').style.display = (t < tType || sent) ? '' : 'none';
    pose(S.q('#send'), { s: 1 + .25 * Math.exp(-Math.max(0, t - tSend) * 10) * (sent ? 1 : 0) });
    pose(S.q('#sugg'), { o: 1 - P(t, tSend - .2, .2) });
    io(S.q('#ub'), t, tSend, null, { dy: 60, s0: .8, d: .35, e: E.back, b: 0 });
    pose(S.q('#think'), { o: P(t, tSend + .2, .2) * (1 - P(t, tAns, .1)) });
    dots.forEach((d, i) => pose(d, { y: -5 * Math.max(0, Math.sin(t * 10 - i * .8)) }));
    io(S.q('#ab'), t, tAns, null, { dy: 20, d: .3, b: 0 });
    typeText(S.q('#at'), t, tAns + .05, 50, ATXT, S.q('#ac'));
    srcs.forEach((el, i) => io(el, t, tSrc + i * Q / 2, null, { dy: 0, s0: .5, e: E.back, d: .3, b: 0 }));
  };
}

// ---------- 03 Calendario (iPhone, Liquid Glass scuro, viola) ----------
{
  const t0 = FT(2), s = st('glass', true, 'violet');
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
  const S = addScene(t0 - .05, FT(3) + .05, `<div class="layer" id="g">
    ${featText(3, 'Calendario', 'Ogni scadenza.<br><em>Al</em> <em>suo</em> <em>posto.</em>', 'Eventi della scuola e della classe, anche presi dalle circolari. In ore di lezione, dalla 1ª alla 6ª.', 150, FEAT_COL[2])}
    <div class="a" id="ph" style="left:1230px;top:90px">${devHTML('iphone', s, inner)}</div></div>`);
  const evr = S.qa('.evr'), dc = S.qa('.dc');
  const TE = EV.map((_, i) => B(21) + i * Q * 2); // dalla battuta 21, un evento ogni due quarti
  TE.forEach(tt => sfx(tt, 'drop'));
  S.render = t => {
    whip(S.q('#g'), t, t0, FT(3));
    featTextRender(S, t, t0, [t0, t0 + Q, t0 + Q * 2, t0 + Q * 2.5, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, 1);
    evr.forEach((el, i) => io(el, t, TE[i], null, { dy: -40, s0: 1.1, d: .35, e: E.back, b: 6 }));
    dc.forEach(el => {
      const d = +el.dataset.d, k = EV.findIndex(e => e[0] === d);
      if (k < 0) return;
      const p = P(t, TE[k], .5, E.out), on = t >= TE[k];
      el.querySelector('.edot').style.background = on ? EV[k][3] : 'transparent';
      el.style.boxShadow = on ? `0 0 0 ${2 + 10 * (1 - p)}px ${EV[k][3]}${Math.round((1 - p) * 200 + 40).toString(16).padStart(2, '0')}` : '';
    });
  };
}

// ---------- 04 Sondaggi interrogazioni (Android, Material chiaro, verde) ----------
{
  const t0 = FT(3), s = st('mat', false, 'green');
  const DATES = [['Lun 2 nov', [0, 3, 5]], ['Mer 4 nov', ['me', 7, 9]], ['Ven 6 nov', [11, 13, 15]]];
  const inner = `${SB}
    <div class="a" style="left:20px;top:58px;display:flex;align-items:center;gap:6px;color:var(--acc);font-size:15px;font-weight:700">${I('arrow-left', 'style="width:19px;height:19px"')}Sondaggi</div>
    <div class="a" style="left:22px;right:22px;top:92px;font-size:25px;font-weight:800;line-height:1.2">Interrogazioni di storia</div>
    <div class="a" style="left:22px;top:132px;display:flex;gap:8px"><span class="bdg" style="background:var(--cont);color:var(--onCont)">${I('vote', 'style="width:14px;height:14px"')}Hai 3 voti</span><span class="bdg s"><i></i>Chiude venerdì</span></div>
    ${DATES.map((d, i) => `<div class="a card" style="left:14px;right:14px;top:${182 + i * 168}px;height:152px;padding:16px 18px">
      <div style="display:flex;justify-content:space-between;align-items:center"><div style="font-size:19px;font-weight:800">${d[0]}</div><span class="bdg g ok" style="opacity:0"><i></i>Confermata</span></div>
      <div style="font-size:12.5px;color:var(--faint);margin-top:2px">3 posti · ${i === 1 ? '2ª' : i ? '1ª' : '3ª'} ora</div>
      ${[0, 1, 2].map(k => `<div class="a" style="left:${18 + k * 66}px;top:78px;width:54px;height:54px;border-radius:50%;border:2px dashed color-mix(in srgb,var(--ink) 18%,transparent)"></div>`).join('')}
      ${d[1].map((k, j) => `<div class="a pav${k === 'me' ? ' me' : ''}" style="left:${18 + j * 66}px;top:78px;width:54px;height:54px;border-radius:50%;display:grid;place-items:center;font-size:17px;font-weight:800;color:#fff;background:${k === 'me' ? 'linear-gradient(140deg,#F59E0B,#EC4899)' : avBg(k)};${k === 'me' ? 'box-shadow:0 0 0 3px var(--bg),0 0 0 6px var(--acc)' : ''}">${k === 'me' ? 'G' : KIDS[k]}</div>`).join('')}
    </div>`).join('')}
    <div class="a" id="closed" style="left:14px;right:14px;bottom:110px;padding:15px 18px;border-radius:22px;background:var(--acc);color:#fff;display:flex;align-items:center;gap:10px;font-size:15px;font-weight:700">${I('shield-check', 'style="width:22px;height:22px"')}Date assegnate: equo per tutti</div>
    ${navHTML(3)}`;
  const S = addScene(t0 - .05, FT(4) + .05, `<div class="layer" id="g">
    ${featText(4, 'Sondaggi', 'Interrogazioni?<br><em>Decidete</em> <em>voi.</em>', 'Prenoti le date con un sistema di voti equo, pensato per non poter essere aggirato.', 820, FEAT_COL[3])}
    <div class="a" id="ph" style="left:250px;top:90px">${devHTML('droid', s, inner)}</div></div>`);
  const av = S.qa('.pav'), ok = S.qa('.ok');
  const order = [0, 3, 6, 1, 4, 7, 2, 5, 8]; // ordine d'arrivo (riga per riga, mescolate)
  const TA = av.map((_, i) => B(25) + order.indexOf(i) * Q / 2);
  TA.forEach((tt, i) => sfx(tt, i === 3 ? 'success' : 'tick'));
  sfx(B(27), 'stamp'); ok.forEach((_, i) => sfx(B(26) + Q * 2 + i * Q / 2, 'pop'));
  S.render = t => {
    whip(S.q('#g'), t, t0, FT(4));
    featTextRender(S, t, t0, [t0, t0 + Q * 2, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, -1);
    av.forEach((el, i) => { const p = P(t, TA[i], .4, E.back); pose(el, { o: clamp(p * 2), s: lerp(.2, 1, p), y: (1 - p) * -120, x: (1 - p) * ((i % 3) - 1) * 60 }); });
    ok.forEach((el, i) => { const p = P(t, B(26) + Q * 2 + i * Q / 2, .3, E.back); el.style.opacity = clamp(p); el.style.transform = `scale(${lerp(.4, 1, p)})`; });
    io(S.q('#closed'), t, B(27), null, { dy: 0, s0: 1.4, d: .25, b: 10 });
  };
}

// ---------- 05 Mappa posti (iPhone, Liquid Glass chiaro, petrolio) ----------
{
  const t0 = FT(4), s = st('glass', false, 'teal');
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
  const S = addScene(t0 - .05, FT(5) + .05, `<div class="layer" id="g">
    ${featText(5, 'Mappa posti', 'Il posto giusto.<br><em>Per</em> <em>tutti.</em>', 'Il Rappresentante sceglie tra 3 disposizioni dei banchi: contano affinità, aiuto tra compagni, rumore e altezza.', 150, FEAT_COL[4])}
    <div class="a" id="ph" style="left:1230px;top:90px">${devHTML('iphone', s, inner)}</div></div>`);
  const seats = S.qa('.seat'), fc = S.qa('.fc'), pp = S.qa('.pp');
  const TS = [t0, B(30), B(31)]; // cambio di proposta
  sfx(TS[1], 'shuffle'); sfx(TS[2], 'shuffle');
  fc.forEach((_, i) => sfx(B(29) + Q + i * Q / 2, 'pop'));
  const where = KIDS.map((_, kid) => LAY.map(L => seatXY(L.indexOf(kid))));
  S.render = t => {
    whip(S.q('#g'), t, t0, FT(5));
    featTextRender(S, t, t0, [t0, t0 + Q, t0 + Q * 2, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, 1);
    let k = 0; while (k < 2 && t >= TS[k + 1]) k++;
    const p = k ? P(t, TS[k], .55, E.inOut) : 1;
    seats.forEach((el, i) => {
      const a = where[i][Math.max(0, k - 1)], b = where[i][k], pi = k ? P(t, TS[k] + (i % 6) * .02, .5, E.inOut) : 1;
      const arc = Math.sin(pi * Math.PI) * -26;
      const pin = P(t, t0 + .2 + i * .02, .4, E.back);
      pose(el, { x: lerp(a[0], b[0], pi), y: lerp(a[1], b[1], pi) + arc, s: (1 + Math.sin(pi * Math.PI) * .15) * pin, o: clamp(pin * 2) });
    });
    const kp = k ? lerp(k - 1, k, p) : 0;
    pose(S.q('#knob'), { x: kp * 120 });
    pp.forEach((el, i) => el.style.color = Math.round(kp) === i ? '#fff' : 'var(--muted)');
    fc.forEach((el, i) => io(el, t, B(29) + Q + i * Q / 2, null, { dy: 0, s0: .5, e: E.back, d: .3, b: 0 }));
  };
}

// ---------- 06 Bacheca e sondaggi a classifica (Android, Material scuro, arancio) ----------
{
  const t0 = FT(5), s = st('mat', true, 'orange');
  const PR = [["Distributore d'acqua al secondo piano", 12, 31, true], ['Torneo di pallavolo a fine anno', 18, 24, false], ['Musica durante i lavori di gruppo', 9, 17, true]];
  const RK = [['Praga', 34, 0], ['Vienna', 20, 2], ['Berlino', 26, 1], ['Barcellona', 12, 3]]; // [nome, punti finali, posizione finale]
  const inner = `${SB}<div class="layer" id="brd">
      <div class="sh"><div class="t">Classe</div><div class="hb av">G</div></div>
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
  const S = addScene(t0 - .05, B(36) + .05, `<div class="layer" id="g">
    ${featText(6, 'Bacheca', 'La tua voce.<br><em>Conta.</em>', 'Proponi, vota e commenta, anche in anonimo. E i sondaggi a classifica si aggiornano da soli.', 820, FEAT_COL[5])}
    <div class="a" id="ph" style="left:250px;top:90px">${devHTML('droid', s, inner)}</div></div>`);
  const vn = S.qa('.vn'), up = S.qa('.up'), rkr = S.qa('.rkr'), rb = S.qa('.rb'), rp = S.qa('.rp'), rn = S.qa('.rn');
  const tV = t0 + Q * 2, tRk = B(34);
  for (let k = 0; k < 10; k++) sfx(tV + k * Q / 2, 'tick');
  sfx(tRk, 'swoosh'); sfx(B(35) + Q, 'success');
  S.render = t => {
    whip(S.q('#g'), t, t0, B(36));
    featTextRender(S, t, t0, [t0, t0 + Q, t0 + Q * 2, t0 + Q * 3]);
    phoneIn(S.q('#ph'), t, t0, -1);
    PR.forEach((p, i) => {
      const pv = P(t, tV, B(1.25), E.inOut), v = Math.round(lerp(p[1], p[2], pv));
      setText(vn[i], String(v));
      const beatPulse = t >= tV && t < tV + B(1.25) ? Math.exp(-((t - tV) % (Q / 2)) * 14) * .12 : 0;
      pose(up[i], { s: 1 + beatPulse });
    });
    const pr = P(t, tRk, .5, E.out5);
    pose(S.q('#rk'), { x: (1 - pr) * 420, o: t >= tRk ? 1 : 0 });
    pose(S.q('#brd'), { x: -pr * 120, o: 1 - pr * .6 });
    // la classifica: i punti crescono, poi le righe si riordinano
    const pp = P(t, tRk + .2, B(1) + Q, E.inOut), po = P(t, B(35), .55, E.inOut);
    RK.forEach((r, i) => {
      const pts = Math.round(r[1] * pp);
      setText(rp[i], pts + ' pt');
      rb[i].style.width = (pts / 34 * 100).toFixed(1) + '%';
      const y = 172 + lerp(i, r[2], po) * 86;
      pose(rkr[i], { y, s: 1 + Math.sin(po * Math.PI) * (r[2] < i ? .04 : 0) });
      setText(rn[i], String(po > .5 ? r[2] + 1 : i + 1));
    });
    io(S.q('#win'), t, B(35) + Q, null, { dy: 0, s0: 1.3, d: .25, b: 8 });
  };
}

// =====================================================================================
// 4 · DISPOSITIVI (battute 36–46)
// =====================================================================================
{
  const DEV = [ // [tipo, tema, schermata, nome, scala da protagonista, scala in fila, centro in fila]
    ['droid', st('mat', false, 'blue'), homePhone('Giulia'), 'Android', .9, .58, [790, 650]],
    ['iphone', st('glass', false, 'violet'), homePhone('Marco'), 'iPhone', .9, .58, [1130, 650]],
    ['ipad', st('glass', true, 'blue'), homeWide('Sara', true), 'iPad', .82, .62, [520, 450]],
    ['tab', st('mat', true, 'green'), homeWide('Luca', false), 'Tablet Android', .8, .62, [1400, 460]],
  ];
  const W = { droid: [420, 900], iphone: [430, 900], ipad: [1180, 830], tab: [1240, 790] };
  const S = addScene(B(36), B(46) + .05, `
    <div class="a" id="outl" style="left:0;top:0;border:5px solid rgba(190,205,255,.85);box-shadow:0 0 40px rgba(120,150,255,.5),inset 0 0 40px rgba(120,150,255,.25)"></div>
    <div class="a center big" id="b1" style="left:0;width:1920px;top:440px">Un'app.</div>
    <div class="a center big" id="b2" style="left:0;width:1920px;top:440px"><em class="grad">Ogni schermo.</em></div>
    ${DEV.map((d, i) => `<div class="a dv" style="left:${960 - W[d[0]][0] / 2}px;top:${540 - W[d[0]][1] / 2}px;z-index:${d[0] === 'droid' || d[0] === 'iphone' ? 3 : 1}">
      ${devHTML(d[0], d[1], d[2] + banner('Nuova circolare', "n. 113 — Variazione d'orario", d[0] !== 'iphone' && d[0] !== 'ipad', 'sync'))}</div>`).join('')}
    ${DEV.map((d, i) => d[0] === 'ipad' || d[0] === 'tab'
      ? `<div class="a dl slam center" style="left:0;width:1920px;top:46px;font-size:120px">${d[3]}</div>`
      : `<div class="a dl slam" style="${i % 2 ? 'left:1200px' : 'left:0;width:720px;text-align:right'};top:470px;font-size:130px">${d[3]}</div>`).join('')}
    <div class="a center" id="ttl" style="left:0;width:1920px;top:56px;font-size:64px;font-weight:800;letter-spacing:-.03em">Stessa app. <em class="grad">Ovunque.</em></div>
    <div class="a center" id="cap" style="left:0;width:1920px;top:972px;font-size:36px;font-weight:600;color:#DCE3FF">${I('bell-ring', 'style="width:36px;height:36px;vertical-align:-6px;color:#9DB6FF"')}&nbsp; Tutta la classe, nello stesso istante.</div>
    ${DEV.map(() => `<div class="a rip" style="left:0;top:0;width:300px;height:300px;margin:-150px 0 0 -150px;border-radius:50%;border:3px solid rgba(160,200,255,.8)"></div>`).join('')}`);
  const dv = S.qa('.dv'), dl = S.qa('.dl'), rip = S.qa('.rip'), syncB = S.qa('.sync');
  const TIN = DEV.map((_, i) => B(38 + i)); // ognuno arriva su una battuta
  TIN.forEach(tt => sfx(tt, 'hit'));
  sfx(B(36), 'slam'); sfx(B(37), 'slam'); sfx(B(38) - .3, 'whoosh');
  sfx(B(44), 'notify'); sfx(B(44), 'impactS');
  S.render = t => {
    // pausa: contorno che passa da telefono a tablet e "Un'app. Ogni schermo."
    const ph = (t - B(36)) / Q, k = Math.floor(ph), pk = E.inOut(clamp((ph - k) / .6));
    const shapes = [[330, 680, 56], [880, 600, 44], [600, 600, 40], [1040, 640, 40]];
    const a = shapes[k % 4], b = shapes[(k + 1) % 4], sh = a.map((v, i) => lerp(v, b[i], pk));
    const ol = S.q('#outl');
    Object.assign(ol.style, { width: sh[0] + 'px', height: sh[1] + 'px', borderRadius: sh[2] + 'px', left: (960 - sh[0] / 2) + 'px', top: (540 - sh[1] / 2) + 'px' });
    pose(ol, { o: P(t, B(36), .4) * (1 - P(t, B(38) - .2, .2)) * .9 });
    slam(S.q('#b1'), t, B(36), B(37) - .1);
    slam(S.q('#b2'), t, B(37), B(38) - .15);
    // i quattro dispositivi: protagonista al centro per una battuta, poi in fila
    let hero = -1; TIN.forEach((tt, i) => { if (t >= tt && t < tt + BAR) hero = i; });
    DEV.forEach((d, i) => {
      const el = dv[i], [w, h] = W[d[0]];
      const pin = P(t, TIN[i], .5, E.out5), pmv = P(t, TIN[i] + BAR, .6, E.inOut);
      const heroX = 960, heroY = d[0] === 'ipad' || d[0] === 'tab' ? 580 : 560;
      const cx = lerp(heroX, d[6][0], pmv), cy = lerp(heroY, d[6][1], pmv);
      const sc = lerp(d[4], d[5], pmv) * lerp(.6, 1, pin);
      const dimmed = hero >= 0 && hero !== i && t >= TIN[i] + BAR ? .35 : 1;
      const fl = t > B(42) ? Math.sin(t * 1.2 + i * 1.7) * 7 : 0;
      const pout = P(t, B(45.5), B(.5), E.in);
      pose(el, { x: cx - 960, y: cy - 540 + (1 - pin) * 300 + fl, s: sc * lerp(1, 1.4, pout), ry: (1 - pin) * (i % 2 ? -40 : 40), rx: (1 - pin) * 20,
        o: clamp(pin * 2) * lerp(1, dimmed, P(t, TIN[i] + BAR, .3)) * (1 - pout), b: pout * 16 });
      el.style.zIndex = hero === i ? 10 : (d[0] === 'droid' || d[0] === 'iphone' ? 3 : 1);
      slam(dl[i], t, TIN[i] + .05, TIN[i] + BAR - .15, { dy: 0 });
      // la stessa notifica arriva su tutti insieme
      const pb = P(t, B(44), .4, E.back);
      pose(syncB[i], { y: lerp(-150, 0, pb), o: t >= B(44) ? 1 : 0 });
      const pr = P(t, B(44), 1.2, E.out);
      pose(rip[i], { x: d[6][0], y: d[6][1] - (d[0] === 'ipad' || d[0] === 'tab' ? 230 : 240), s: lerp(.2, 3, pr), o: (1 - pr) * (t >= B(44) ? 1 : 0) });
    });
    io(S.q('#ttl'), t, B(42), B(45.5), { dy: -30 });
    io(S.q('#cap'), t, B(44) + Q, B(45.5), { dy: 30 });
  };
}

// =====================================================================================
// 5 · TEMI (battute 46–52) e IL MURO DELLE 24 COMBINAZIONI (52–56)
// =====================================================================================
{
  // [stato, istante, transizione: wipe | circle | fade]
  const SEQ = [
    [st('glass', false, 'blue'), B(46), 'fade'], [st('mat', false, 'blue'), B(47), 'wipe'], [st('mat', true, 'blue'), B(47) + Q * 2, 'circle'],
    [st('glass', true, 'blue'), B(48), 'wipe'], [st('glass', false, 'blue'), B(48) + Q * 2, 'circle'],
    [st('glass', false, 'violet'), B(49), 'fade'], [st('glass', false, 'green'), B(49) + Q, 'fade'], [st('glass', true, 'teal'), B(49) + Q * 2, 'fade'],
    [st('mat', true, 'orange'), B(49) + Q * 3, 'fade'], [st('mat', false, 'pink'), B(50), 'fade'], [st('glass', false, 'blue'), B(50) + Q, 'fade'],
  ];
  const SW = ACC_KEYS.map(k => ACC[k][1][0]);
  const S = addScene(B(46), B(56) + .05, `
    <div class="a center" id="tt" style="left:0;width:1920px;top:44px;font-size:76px;font-weight:800;letter-spacing:-.03em">Fatta <em class="grad">a modo tuo.</em></div>
    <div class="a" id="ph" style="left:470px;top:150px">${devHTML('iphone', SEQ[0][0], `<div class="app" id="la" style="position:absolute;inset:0">${homePhone('Giulia')}</div><div class="app" id="lb" style="position:absolute;inset:0">${homePhone('Giulia')}</div>`)}</div>
    <div class="a panel" id="pn" style="left:1010px;top:250px;width:640px;padding:34px 36px">
      <div style="font-size:20px;font-weight:700;letter-spacing:.16em;color:#9FB0E8">STILE</div>
      <div class="opt" style="margin-top:14px"><div class="knob" id="k1" style="width:calc(50% - 6px)"></div><span>${I('droplet')}Liquid Glass</span><span>${I('layers')}Material</span></div>
      <div style="font-size:20px;font-weight:700;letter-spacing:.16em;color:#9FB0E8;margin-top:34px">TEMA</div>
      <div class="opt" style="margin-top:14px"><div class="knob" id="k2" style="width:calc(50% - 6px)"></div><span>${I('sun')}Chiaro</span><span>${I('moon')}Scuro</span></div>
      <div style="display:flex;justify-content:space-between;margin-top:34px"><span style="font-size:20px;font-weight:700;letter-spacing:.16em;color:#9FB0E8">COLORE</span><span id="cn" style="font-size:22px;font-weight:700"></span></div>
      <div style="display:flex;justify-content:space-between;margin-top:20px;padding:0 10px">${SW.map(c => `<div class="sw" style="background:${c}"><div class="ring"></div></div>`).join('')}</div>
    </div>
    <div class="a center" id="eq" style="left:1010px;width:640px;top:770px;font-size:31px;font-weight:600;color:#DCE3FF">2 stili <span style="color:var(--faint)">×</span> chiaro e scuro <span style="color:var(--faint)">×</span> 6 colori</div>
    <div class="layer" id="wall"></div>
    <div class="layer" id="wdim" style="background:radial-gradient(ellipse at center,rgba(2,4,9,.88) 22%,rgba(2,4,9,.2) 70%)"></div>
    <div class="a center" id="n24" style="left:0;width:1920px;top:250px;font-size:330px;font-weight:800;letter-spacing:-.06em;line-height:1"><em class="grad">24</em></div>
    <div class="a center big" id="w1" style="left:0;width:1920px;top:590px;font-size:84px">combinazioni.</div>
    <div class="a center" id="w2" style="left:0;width:1920px;top:710px;font-size:60px;font-weight:600;color:#DCE3FF">Una è la tua.</div>`);
  // il muro: righe = stile e tema, colonne = colore
  const ROWS = [st('glass', false), st('glass', true), st('mat', false), st('mat', true)];
  const wall = S.q('#wall');
  wall.innerHTML = `<div id="wg" class="a" style="left:0;top:0;width:1920px;height:1080px">${ROWS.map((r, ri) => ACC_KEYS.map((a, ci) =>
    `<div class="a wc" style="left:${960 - 215 + (ci - 2.5) * 262}px;top:${540 - 450 + (ri - 1.5) * 262}px" data-r="${ri}" data-c="${ci}">
      ${devHTML(ri === 0 || ri === 1 ? 'iphone' : 'droid', st(r.style, r.dark, a), homePhone(['Giulia', 'Marco', 'Sara', 'Luca', 'Anna', 'Leo'][ci]))}</div>`).join('')).join('')}</div>`;
  const wc = S.qa('.wc');
  const la = S.q('#la'), lb = S.q('#lb'), sws = S.qa('.sw');
  SEQ.slice(1).forEach(([, tt, k]) => sfx(tt, k === 'fade' ? 'switch' : 'switchBig'));
  for (let d = 0; d < 9; d++) sfx(B(52) + d * Q / 2, 'pop');
  sfx(B(52), 'impactS'); sfx(B(53), 'slam'); sfx(B(54), 'slam');
  S.render = t => {
    // telefono e pannello delle impostazioni
    let k = 0; while (k < SEQ.length - 1 && t >= SEQ[k + 1][1]) k++;
    const [cur, tk, kind] = SEQ[k], prev = SEQ[Math.max(0, k - 1)][0];
    const p = k ? P(t, tk, kind === 'fade' ? .16 : .42, kind === 'fade' ? E.out : E.inOut) : 1;
    themeGlow = t < B(52) ? mixA(ACC[prev.acc][2][0], ACC[cur.acc][2][0], p) : null;
    applyState(la, prev); applyState(lb, cur);
    la.style.display = p < 1 ? '' : 'none';
    if (kind === 'wipe') { lb.style.clipPath = `inset(0 ${(100 - p * 100).toFixed(2)}% 0 0)`; lb.style.opacity = ''; }
    else if (kind === 'circle') { lb.style.clipPath = `circle(${(p * 130).toFixed(2)}% at 88% 9%)`; lb.style.opacity = ''; }
    else { lb.style.clipPath = ''; lb.style.opacity = p < 1 ? p.toFixed(3) : ''; }
    const pin = P(t, B(46), .6, E.out5), pw = P(t, B(52) - .1, .5, E.in);
    const bump = k ? Math.exp(-(t - tk) * 9) * .025 : 0;
    pose(S.q('#ph'), { y: (1 - pin) * 200 + Math.sin(t * 1.1) * 6, s: (.92 + bump) * lerp(1, .3, pw), ry: 8 + Math.sin(t * .8) * 2, o: clamp(pin * 2) * (1 - pw), b: pw * 8 });
    pose(S.q('#pn'), { x: (1 - pin) * 200 + pw * 300, o: clamp(pin * 2) * (1 - pw), b: pw * 10 });
    io(S.q('#tt'), t, B(46), B(52) - .2, { dy: -30 });
    io(S.q('#eq'), t, B(50) + Q * 2, B(52) - .2, { dy: 30 });
    const kmove = (from, to) => lerp(from, to, p);
    const sIdx = s => s.style === 'glass' ? 0 : 1, mIdx = s => s.dark ? 1 : 0;
    const kw = 278; // larghezza di metà selettore
    pose(S.q('#k1'), { x: kmove(sIdx(prev), sIdx(cur)) * kw });
    pose(S.q('#k2'), { x: kmove(mIdx(prev), mIdx(cur)) * kw });
    sws.forEach((el, i) => pose(el.firstElementChild, { o: (ACC_KEYS[i] === cur.acc ? 1 : 0), s: ACC_KEYS[i] === cur.acc ? lerp(1.4, 1, P(t, tk, .3, E.back)) : 1 }));
    setText(S.q('#cn'), ACC[cur.acc][0]);
    // il muro: le celle arrivano a onda in diagonale, poi tutto si inclina in 3D
    const pz = P(t, B(52), B(4), E.lin);
    pose(S.q('#wg'), { rx: 14 - 6 * pz, ry: -10 + 8 * pz, s: lerp(.92, 1.02, pz), y: -20 * pz, o: 1 - P(t, B(56) - .3, .3) });
    wc.forEach(el => {
      const r = +el.dataset.r, c = +el.dataset.c, ti = B(52) + (r + c) * Q / 2, pi = P(t, ti, .45, E.back);
      pose(el, { s: .3 * pi, o: clamp(pi * 3) });
    });
    pose(S.q('#wdim'), { o: P(t, B(53) - .2, .3) * (1 - P(t, B(55.5), .5)) });
    const n = Math.round(lerp(1, 24, P(t, B(53), Q * 1.5, E.out)));
    if (t >= B(53)) setText(S.q('#n24 em'), String(n));
    slam(S.q('#n24'), t, B(53), B(55.5));
    io(S.q('#w1'), t, B(53) + Q * 2, B(55.5), { dy: 30, d: .35 });
    slam(S.q('#w2'), t, B(54), B(55.5));
  };
}

// =====================================================================================
// 6 · FIDUCIA (battute 56–59)
// =====================================================================================
{
  const ROWS = [['bell-ring', 'Notifiche in tempo reale.', '#60A5FA'], ['shield-check', 'Ogni classe ha i suoi dati, separati.', '#34D399'],
    ['lock', "L'AI non riceve mai i tuoi dati personali.", '#C4B5FD']];
  const S = addScene(B(56), B(59) + .05, `<div class="layer" id="tg">${ROWS.map(([ic, tx, c], i) => `<div class="a tr" style="left:0;width:1920px;top:${300 + i * 170}px;display:flex;justify-content:center">
    <div style="display:flex;align-items:center;gap:34px"><div style="width:110px;height:110px;border-radius:32px;display:grid;place-items:center;background:${c}22;border:2px solid ${c}66;color:${c}">${I(ic, 'style="width:54px;height:54px"')}</div>
    <div style="font-size:62px;font-weight:700;letter-spacing:-.02em">${tx}</div></div></div>`).join('')}</div>`);
  const tr = S.qa('.tr');
  ROWS.forEach((_, i) => sfx(B(56 + i), 'slam'));
  sfx(B(58.5), 'riser');
  S.render = t => {
    tr.forEach((el, i) => io(el, t, B(56 + i), null, { dx: i % 2 ? 160 : -160, dy: 0, d: .4, b: 18, s0: 1.1 }));
    const pz = P(t, B(58.5), B(.5), E.in);
    pose(S.q('#tg'), { s: lerp(1, .05, pz), o: 1 - pz * .8, b: pz * 8 });
  };
}

// =====================================================================================
// 7 · FINALE (battute 59–66): logo e QR code per installarla
// =====================================================================================
{
  const q = QR, cell = 12, size = q.n * cell;
  const qrSVG = `<svg width="${size}" height="${size}" viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges"><path fill="#0A1330" d="${q.cells.map(([x, y]) => `M${x} ${y}h1v1h-1z`).join('')}"/></svg>`;
  const S = addScene(B(59), DURATION + 1, `
    <div class="a" id="ring" style="left:760px;top:140px;width:400px;height:400px;border-radius:50%;border:4px solid rgba(190,205,255,.9)"></div>
    <div class="layer" id="lg">
      <div class="a" id="logo2" style="left:820px;top:150px;width:280px;height:280px">${logoSVG('F')}</div>
      <div class="a center" id="wm2" style="left:0;width:1920px;top:450px;font-size:200px;font-weight:800;letter-spacing:.05em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
      <div class="a center" id="tag2" style="left:0;width:1920px;top:690px;font-size:46px;font-weight:500;color:#DCE3FF">La tua scuola, <em class="grad" style="font-weight:700">sincronizzata.</em></div>
      <div class="a" id="plat" style="left:0;width:1920px;top:800px;display:flex;justify-content:center;gap:16px">
        ${[['smartphone', 'Android'], ['smartphone', 'iPhone'], ['tablet', 'iPad'], ['tablet', 'Tablet']].map(([ic, l]) => `<span class="chip pl">${I(ic, 'style="color:#A5B8FF"')}${l}</span>`).join('')}</div>
    </div>
    <div class="a" id="qr" style="left:1210px;top:190px;width:520px">
      <div class="center" style="font-size:54px;font-weight:800;letter-spacing:-.02em">Installala <em class="grad">ora.</em></div>
      <div class="qrbox" style="padding:40px;margin:30px auto 0;width:${size + 80}px;position:relative;overflow:hidden">${qrSVG}
        <div id="scan" class="a" style="left:0;right:0;top:0;height:70px;background:linear-gradient(180deg,transparent,rgba(90,140,255,.28),transparent)"></div></div>
      <div class="center" style="font-size:27px;font-weight:600;color:#DCE3FF;margin-top:30px">${I('scan-line', 'style="width:30px;height:30px;vertical-align:-7px;color:#9DB6FF"')}&nbsp; Inquadra il codice</div>
      <div class="center" style="font-size:22px;color:var(--muted);margin-top:10px">Ti serve solo il codice della tua classe.</div>
    </div>
    <div class="a center" id="cred" style="left:0;width:1920px;top:1000px;font-size:23px;color:var(--faint)">Un progetto di Simone Bianchin</div>`);
  const wm = S.q('#wm2'); wm._w = [...wm.querySelectorAll('.w')];
  const pl = S.qa('.pl');
  sfx(B(59), 'impact'); addFlash(B(59), 1);
  sfx(B(60.5), 'whoosh'); pl.forEach((_, i) => sfx(B(61) + Q * 2 + i * Q / 2, 'pop'));
  S.render = t => {
    const t0 = B(59);
    const pr = P(t, t0, 1.2, E.out5); pose(S.q('#ring'), { o: (1 - pr) * .9, s: lerp(.3, 5, pr) });
    renderLogo(S.q('#logo2'), t, t0 - .05, .6);
    pose(S.q('#logo2'), { y: Math.sin(t * 1.4) * 6 });
    wm._w.forEach((w, i) => io(w, t, t0 + .25 + i * Q / 4, null, { dy: 0, s0: 1.8, b: 24, d: .3 }));
    io(S.q('#tag2'), t, B(60), null, { dy: 30 });
    // il blocco del logo si sposta a sinistra e arriva il QR
    const pm = P(t, B(60.5), .8, E.inOut);
    pose(S.q('#lg'), { x: -380 * pm, y: 50 * pm, s: lerp(1, .86, pm) });
    io(S.q('#qr'), t, B(60.5) + .15, null, { dx: 260, dy: 0, d: .7, b: 16, s0: .92 });
    pose(S.q('#scan'), { y: ((t - B(61)) % 2.2) / 2.2 * (size + 120) - 70, o: t > B(61) && t < B(64) ? 1 : 0 });
    pl.forEach((el, i) => io(el, t, B(61) + Q * 2 + i * Q / 2, null, { dy: 24, s0: .6, e: E.back, d: .35, b: 4 }));
    io(S.q('#cred'), t, B(62.5), null, { dy: 14 });
  };
}

// ---------- motore ----------
function seek(t) {
  themeGlow = null;
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
  renderBg(t);
  let f = 0; for (const [tf, a] of FLASHES) if (t >= tf) f = Math.max(f, a * (1 - P(t, tf, .7, E.out)));
  flash.style.opacity = f.toFixed(3);
  flash.style.display = f > .002 ? '' : 'none';
}
window.seek = seek; window.DURATION = DURATION;
window.SFX = SFX.slice().sort((a, b) => a.t - b.t);

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
  range.max = DURATION;
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
