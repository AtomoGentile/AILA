'use strict';
/*
 * Intro "presentazione della squadra": energia da sigla di gara. Tagli su ogni quarto, cambi d'inquadratura
 * continui, strisce di luce in velocità, inquadratura che pompa sui colpi, grafica da diretta (cronometro).
 * Le sei funzioni entrano come piloti (numero, nome, statistiche), poi dispositivi, livree (temi), griglia di
 * partenza con le 24 combinazioni e il semaforo fatto con i sei banchi del logo.
 * Come il trailer: seek(t) disegna il fotogramma al secondo t senza stato. 144 BPM, tempi in intro-timeline.js.
 */
const { BAR, BEAT: Q, DURATION, S: SEC } = INTRO_TL;
const B = n => n * BAR;
const ACC_COL = k => ACC[k][2][0]; // colore dell'accento in tema scuro (più luminoso, sta bene sul nero)
const mixA = (a, b, p) => { const A = hex(a), C = hex(b); return A.map((v, i) => Math.round(lerp(v, C[i], p))); };
const inGroove = t => (t >= B(4) && t < B(33)) || (t >= B(35) && t < B(39));

// =====================================================================================
// ATMOSFERA: nebbia, fasci di luce, strisce in velocità (fuori dal "mondo" che pompa), poi il mondo
// =====================================================================================
const R0 = rng(77);
const STREAKS = [...Array(44)].map(() => ({ y: R0() * 1080, w: 200 + R0() * 900, h: 1 + R0() * 3.5, f: .5 + R0() * 1.4, x0: R0() * 3000, a: .12 + R0() * .4 }));
const atm = document.createElement('div');
atm.className = 'layer';
atm.innerHTML = `<div class="fog" id="f1"></div><div class="fog" id="f2"></div><div class="fog" id="f3"></div>
  ${[0, 1, 2].map(i => `<div class="beam" id="bm${i}"></div>`).join('')}
  ${STREAKS.map(s => `<div class="a stk" style="left:0;top:${s.y.toFixed(0)}px;width:${s.w.toFixed(0)}px;height:${s.h.toFixed(1)}px;border-radius:2px;
    background:linear-gradient(90deg,transparent,rgba(205,220,255,${s.a.toFixed(2)}) 70%,rgba(255,255,255,${Math.min(1, s.a * 2).toFixed(2)}));"></div>`).join('')}`;
stage.appendChild(atm);
const stk = [...atm.querySelectorAll('.stk')];
const world = document.createElement('div');
world.className = 'layer';
stage.appendChild(world);
const topLayer = document.createElement('div');
topLayer.className = 'layer';
topLayer.style.zIndex = 50;
topLayer.innerHTML = `<div class="flare" id="fl"></div><div id="vig"></div><div class="lbx" id="lb1" style="top:0"></div><div class="lbx" id="lb2" style="bottom:0"></div><div id="grain"></div>
  <div class="a tag" id="hud1" style="left:60px;top:44px;z-index:65">AILA &nbsp;·&nbsp; <b>Stagione 2026/27</b></div>
  <div class="a" id="hud2" style="right:60px;top:36px;z-index:65;display:flex;align-items:center;gap:14px;font-size:30px;font-weight:700;letter-spacing:.04em;font-variant-numeric:tabular-nums">
    <i id="hdot" style="width:12px;height:12px;border-radius:50%;background:#5A8CFF;box-shadow:0 0 14px #5A8CFF"></i><span id="clk">00:00.000</span></div>
  <div class="a tag" id="hud3" style="left:60px;bottom:44px;z-index:65"></div>
  <div class="layer" id="flash" style="background:radial-gradient(circle at 50% 45%,rgba(235,240,255,.95),rgba(150,170,255,.35) 45%,transparent 75%);z-index:80"></div>`;
stage.appendChild(topLayer);
// grana: quattro fotogrammi di rumore generati una volta sola (stesso seme, stesse immagini a ogni esportazione)
const GRAIN = [0, 1, 2, 3].map(k => {
  const c = document.createElement('canvas'); c.width = 480; c.height = 270;
  const g = c.getContext('2d'), img = g.createImageData(480, 270), R = rng(31 + k);
  for (let i = 0; i < img.data.length; i += 4) { const v = R() * 255; img.data[i] = img.data[i + 1] = img.data[i + 2] = v; img.data[i + 3] = 255; }
  g.putImageData(img, 0, 0); return `url(${c.toDataURL()})`;
});
// palette della nebbia per sezione: [inizio, tre colori, intensità]
const FOG = [[0, ['#1E293B', '#312E81', '#0F172A'], .35], [B(4), ['#3B82F6', '#8B5CF6', '#06B6D4'], .95]];
// velocità delle strisce (px/s) a tratti: [inizio, fine, velocità]
const SPEED = [[B(4), B(6), 3400], [B(6), B(24), 1700], [B(24), B(27), 2400], [B(27), B(30), 2000], [B(35), B(39), 1500], [B(39), B(44), 500]];
const dist = t => SPEED.reduce((s, [a, b, v]) => s + v * clamp(t - a, 0, b - a), 0);
const speedAt = t => (SPEED.find(([a, b]) => t >= a && t < b) || [0, 0, 0])[2];
const FLASHES = [], FLARES = [];
const hit = (t, a = 1, y = 540) => { FLASHES.push([t, a]); FLARES.push([t, y]); };
let fogOverride = null, hudLabel = '';
function renderAtmo(t) {
  let k = 0; while (k < FOG.length - 1 && t >= FOG[k + 1][0]) k++;
  const prev = FOG[Math.max(0, k - 1)], cur = FOG[k], p = k === 0 ? 1 : P(t, cur[0], Q, E.inOut);
  const dark = (t >= B(33) && t < B(35)) ? 0 : 1; // semaforo: buio
  [['f1', 980 + 260 * Math.sin(t * .23), -380 + 120 * Math.cos(t * .19)], ['f2', -420 + 200 * Math.cos(t * .17), 420 + 120 * Math.sin(t * .21)],
    ['f3', 360 + 260 * Math.sin(t * .11 + 1), 640 + 100 * Math.sin(t * .14)]].forEach(([id, x, y], i) => {
    const c = fogOverride && i < 2 ? hex(fogOverride) : mixA(prev[1][i], cur[1][i], p);
    const el = document.getElementById(id), bgs = `radial-gradient(ellipse at center,rgba(${c.join(',')},.42),transparent 64%)`;
    if (el._bg !== bgs) { el.style.background = bgs; el._bg = bgs; }
    pose(el, { x, y, o: lerp(prev[2], cur[2], p) * dark });
  });
  [0, 1, 2].forEach(i => pose(document.getElementById('bm' + i), { x: 300 + i * 560 + Math.sin(t * .6 + i) * 120, r: -18 + i * 18 + Math.sin(t * .8 + i * 2) * 10,
    o: (.5 + .4 * Math.sin(t * 2.6 + i * 2.1)) * dark * (t < B(4) ? .4 : 1) }));
  // strisce di luce: la distanza percorsa è l'integrale della velocità, così non saltano tra un tratto e l'altro
  const d = dist(t), v = speedAt(t), so = clamp(v / 1500) * (1 - P(t, B(39), B(3)));
  stk.forEach((el, i) => { const s = STREAKS[i], span = 1920 + s.w * 2; pose(el, { x: span - ((s.x0 + d * s.f) % span) - s.w, sx: 1 + v / 4000, o: so }); });
  // bande del formato cinema: all'inizio e durante il semaforo
  const lb = (t < B(4) ? 1 - P(t, B(4), .3, E.inOut) : 0) + (t >= B(33) - .3 && t < B(35) ? P(t, B(33) - .3, .3, E.inOut) : 0);
  const h = 140 * clamp(lb);
  document.getElementById('lb1').style.height = h + 'px'; document.getElementById('lb2').style.height = h + 'px';
  document.getElementById('grain').style.backgroundImage = GRAIN[Math.floor(t * 24) % 4];
  let f = 0, shake = 0;
  for (const [tf, a] of FLASHES) if (t >= tf) { f = Math.max(f, a * (1 - P(t, tf, .35, E.out))); shake = Math.max(shake, a * Math.exp(-(t - tf) * 10)); }
  const fe = document.getElementById('flash'); fe.style.opacity = f.toFixed(3); fe.style.display = f > .002 ? '' : 'none';
  let fl = 0, fy = 540; for (const [tf, y] of FLARES) if (t >= tf && t < tf + .7) { const v2 = 1 - P(t, tf, .7, E.out); if (v2 > fl) { fl = v2; fy = y; } }
  pose(document.getElementById('fl'), { y: fy - 540 + 537, sx: lerp(.2, 1.2, 1 - fl), o: fl });
  // il mondo pompa sulla cassa e trema sugli impatti
  let pump = 1;
  if (inGroove(t)) { const b = t / Q, ph = (b - Math.floor(b)) * Q, down = Math.floor(b) % 4 === 0; pump = 1 + (down ? .03 : .016) * Math.exp(-ph * 14); }
  pose(world, { s: pump, x: Math.sin(t * 91) * 14 * shake, y: Math.cos(t * 77) * 10 * shake });
  // grafica da diretta
  const hp = (t >= B(4) && t < B(33) ? 1 : 0) * P(t, B(4) + .2, .3) * (1 - P(t, B(33) - .3, .3));
  ['hud1', 'hud2', 'hud3'].forEach(id => pose(document.getElementById(id), { o: hp }));
  const el = Math.max(0, t - B(4)), mm = Math.floor(el / 60), ss = el % 60;
  setText(document.getElementById('clk'), `${String(mm).padStart(2, '0')}:${ss.toFixed(3).padStart(6, '0')}`);
  document.getElementById('hdot').style.opacity = Math.floor(t * 2.4) % 2 ? .35 : 1;
  const h3 = document.getElementById('hud3'); if (h3._t !== hudLabel) { h3.innerHTML = hudLabel; h3._t = hudLabel; }
}
/* aberrazione cromatica che si spegne dopo l'impatto */
function chroma(el, t, t0, k = 9) {
  const v = t >= t0 ? Math.exp(-(t - t0) * 7) * k : 0;
  el.style.textShadow = v > .3 ? `${-v}px 0 rgba(255,40,90,.75),${v}px 0 rgba(40,220,255,.75)` : '';
}
/* testo che si adatta alla larghezza disponibile (rimisurato quando cambia il testo, a font caricati) */
function fitText(el, maxW) {
  const key = el.textContent;
  if (el._fit === key || document.fonts.status !== 'loaded') return;
  el.style.fontSize = '';
  const fs = parseFloat(getComputedStyle(el).fontSize), w = el.scrollWidth;
  if (w > maxW) el.style.fontSize = (fs * maxW / w).toFixed(1) + 'px';
  el._fit = key;
}
/* riflesso che attraversa il vetro di un dispositivo */
const SPEC = `<div class="a spec" style="left:0;top:-10%;width:45%;height:120%;z-index:40;pointer-events:none;
  background:linear-gradient(90deg,transparent,rgba(255,255,255,.28),transparent);transform:skewX(-18deg)"></div>`;
function spec(root, t, t0, w, d = .5) { const p = P(t, t0, d, E.inOut); pose(root.querySelector('.spec'), { x: lerp(-.6 * w, 1.4 * w, p), o: t >= t0 && p < 1 ? 1 : 0 }); }
/* uscita a frustata: scivola via sfocandosi nell'ultimo mezzo quarto */
function whipOut(t, t1, dir = -1) { const p = P(t, t1 - Q / 2, Q / 2, E.in); return { x: dir * 700 * p, b: p * 34, o: 1 - p * p }; }

// ---------- le sei schermate nel loro stato finale (nel trailer sono animate, qui posano ferme) ----------
const SCR = [scrCircolari, scrAssistant, scrCalendario, scrSondaggi, scrMappaPosti, scrBacheca];
const FIN = [
  r => { r.querySelector('#lst').style.display = 'none'; },
  (r, d) => { ['#sugg', '#think', '#qc', '#ac'].forEach(s => r.querySelector(s).style.display = 'none'); r.querySelector('#at').textContent = d.ATXT; },
  (r, d) => r.querySelectorAll('.dc').forEach(el => { const e = d.EV.find(e => e[0] === +el.dataset.d); if (e) el.querySelector('.edot').style.background = e[3]; }),
  r => { r.querySelectorAll('.ok').forEach(el => el.style.opacity = 1); },
  (r, d) => {
    r.querySelectorAll('.seat').forEach((el, i) => { const [x, y] = d.seatXY(d.LAY[0].indexOf(i)); el.style.transform = `translate(${x}px,${y}px)`; });
    r.querySelectorAll('.pp').forEach((el, i) => el.style.color = i ? 'var(--muted)' : '#fff');
  },
  (r, d) => { r.querySelector('#rk').style.display = 'none'; r.querySelectorAll('.vn').forEach((el, i) => el.textContent = d.PR[i][2]); },
];
const CARDS = [
  { name: 'Circolari', desc: "Spiegate dall'AI", kind: 'iphone', s: st('glass', false, 'blue'), col: '#5A8CFF', foc: [[150, 300], [200, 610]],
    stats: [["15'", 'controllo del registro'], ['3', 'categorie per la tua classe'], ['AI', 'riassunto e scadenze']] },
  { name: 'Assistant', desc: 'AILA', kind: 'droid', s: st('mat', true, 'blue'), col: '#22D3EE', foc: [[200, 200], [150, 330]],
    stats: [['IT', 'chiedi in italiano'], ['FONTI', 'sempre da aprire'], ['STORICO', 'conversazioni salvate']] },
  { name: 'Calendario', desc: 'Ogni scadenza', kind: 'iphone', s: st('glass', true, 'violet'), col: '#A78BFA', foc: [[120, 300], [180, 560]],
    stats: [['1ª–6ª', 'ore di lezione'], ['AUTO', 'scadenze dalle circolari'], ['CLASSE', 'eventi condivisi']] },
  { name: 'Sondaggi', desc: 'Interrogazioni', kind: 'droid', s: st('mat', false, 'green'), col: '#34D399', foc: [[120, 450], [170, 290]],
    stats: [['EQUO', 'sistema di voti'], ['ANTI-FURBI', 'pensato per non essere aggirato'], ['10', 'opzioni nei sondaggi a classifica']] },
  { name: 'Mappa posti', desc: 'Il posto giusto', kind: 'iphone', s: st('glass', false, 'teal'), col: '#2DD4BF', foc: [[190, 320], [150, 640]],
    stats: [['3', 'disposizioni dei banchi'], ['4', 'affinità, aiuto, rumore, altezza'], ['SU MISURA', 'file e posti per fila']] },
  { name: 'Bacheca', desc: 'La tua voce', kind: 'droid', s: st('mat', true, 'orange'), col: '#FB923C', foc: [[200, 240], [150, 420]],
    stats: [['ANONIMA', 'se vuoi'], ['VOTI', 'e commenti'], ['TUTELA', 'anonimato svelato solo se c\'è un abuso']] },
];

// =====================================================================================
// DETTAGLI "MACRO": sei schermate ingrandite, condivise da tutte le scene (copia nitida al centro, sfocata ai bordi)
// =====================================================================================
const macroLayer = document.createElement('div');
macroLayer.className = 'layer';
const MAC = CARDS.map((c, k) => {
  const d = SCR[k](), shot = `<div class="shot" style="width:406px;height:876px"><div class="${appCls(c.s)}" style="${appVars(c.s)}">${d.inner}</div></div>`;
  const m = document.createElement('div');
  m.className = 'macro'; m.innerHTML = `<div class="layer soft">${shot}</div><div class="layer sharp">${shot}</div>`;
  m.querySelectorAll('.shot').forEach(sh => FIN[k](sh, d));
  macroLayer.appendChild(m); m._sh = [...m.querySelectorAll('.shot')];
  return m;
});
let macroShown = -1;
/* mostra la schermata k ingrandita sul punto (fx, fy), con scala e rotazione; p (0..1) muove l'inquadratura */
function macro(k, fx, fy, sc, p, rot = 0, pan = 0) {
  macroShown = k;
  const s = sc * (1 + .12 * (1 - p));
  MAC[k]._sh.forEach(sh => { sh.style.transform = `translate(${960 - fx * s + pan * (1 - p)}px,${540 - fy * s}px) scale(${s}) rotate(${rot}deg)`; });
}

// =====================================================================================
// 1 · APERTURA (battute 0–4): linea di luce, poi lampi dell'app sempre più fitti, il motore che sale
// =====================================================================================
const FR = []; // [istante, schermata (-1 = parola), fuoco, scala, rotazione, parola]
{
  const WORDS = ['Circolari', 'Scadenze', 'Voti', 'Posti', 'AI', 'Classe'];
  let k = 0;
  const add = tt => { const kind = k % 6, foc = CARDS[kind].foc[Math.floor(k / 6) % 2]; FR.push([tt, kind, foc, 2.3 + (k % 3) * .45, (k % 2 ? -4 : 4), null]); k++; };
  for (let i = 0; i < 4; i++) add(B(1) + i * Q);
  for (let i = 0; i < 8; i++) add(B(2) + i * Q / 2);
  for (let i = 0; i < 12; i++) { const tt = B(3) + i * Q / 4; if (i % 2) FR.push([tt, -1, null, 0, 0, WORDS[(i >> 1) % 6]]); else add(tt); }
}
{
  const S = addScene(0, B(4) + .02, `
    <div class="a" id="line" style="left:260px;top:538px;width:1400px;height:4px;border-radius:2px;background:linear-gradient(90deg,transparent,#DCE6FF 30%,#fff 50%,#DCE6FF 70%,transparent);box-shadow:0 0 30px 6px rgba(120,160,255,.55)"></div>
    <div class="a center cap" id="pres" style="left:0;width:1920px;top:470px">AILA presenta</div>
    <div class="a center slam" id="wd" style="left:0;width:1920px;top:430px;font-size:230px"></div>`, world);
  sfx(0, 'swell');
  FR.forEach(([tt, kind]) => sfx(tt, kind < 0 ? 'word' : 'cut'));
  FR.filter(f => f[0] < B(2)).forEach(f => hit(f[0], .35, 200 + f[1] * 120));
  S.render = t => {
    const pl = P(t, .1, B(.8), E.inOut);
    pose(S.q('#line'), { sx: pl, o: clamp(pl * 3) * (1 - P(t, B(1) - .2, .2)) * (.8 + .2 * Math.sin(t * 9)) });
    io(S.q('#pres'), t, B(.4), B(1) - .1, { dy: 0, b: 12, d: .5, e: E.out });
    let i = -1; while (i < FR.length - 1 && t >= FR[i + 1][0]) i++;
    const wd = S.q('#wd');
    if (i >= 0 && t < B(3.75)) {
      const [tt, kind, foc, sc, rot, word] = FR[i], next = i < FR.length - 1 ? FR[i + 1][0] : B(3.75), p = clamp((t - tt) / (next - tt));
      if (kind >= 0) { macro(kind, foc[0], foc[1], sc, p, rot, 160 * (i % 2 ? 1 : -1)); pose(wd, { o: 0 }); }
      else { setText(wd, word); pose(wd, { o: 1, s: lerp(1.25, 1, p) }); chroma(wd, t, tt, 16); }
    } else pose(wd, { o: 0 });
  };
}

// =====================================================================================
// 2 · DROP (battute 4–6): logo e titolo della stagione
// =====================================================================================
{
  const S = addScene(B(4), B(6) + .02, `<div class="layer" id="g">
    <div class="a" id="logo" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('I')}</div>
    <div class="a center" id="wm" style="left:0;width:1920px;top:480px;font-size:250px;font-weight:800;letter-spacing:.04em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
    <div class="a center cap" id="ss" style="left:0;width:1920px;top:780px">La squadra · Stagione 2026/27</div></div>`, world);
  const wm = S.q('#wm'); wm._w = [...wm.querySelectorAll('.w')];
  sfx(B(4), 'impact'); hit(B(4), 1);
  sfx(B(5), 'slam'); hit(B(5), .4, 820);
  S.render = t => {
    renderLogo(S.q('#logo'), t, B(4) - .05, .45);
    wm._w.forEach((w, i) => io(w, t, B(4) + .12 + i * Q / 4, null, { dy: 0, s0: 2, b: 26, d: .22 }));
    chroma(wm, t, B(4) + .12, 12);
    slam(S.q('#ss'), t, B(5), null, { s0: 1.3 }); chroma(S.q('#ss'), t, B(5), 8);
    pose(S.q('#g'), { ...whipOut(t, B(6)), s: 1 + (t - B(4)) * .03 });
  };
}

// =====================================================================================
// 3 · LA SQUADRA (battute 6–24): sei funzioni, tre battute ciascuna, un'inquadratura nuova a ogni quarto
// =====================================================================================
CARDS.forEach((c, i) => {
  const t0 = B(SEC.CARDS + i * SEC.CARD_LEN), t1 = t0 + B(SEC.CARD_LEN), R = i % 2 === 0; // R: telefono a destra
  const d = SCR[i]();
  FOG.push([t0, [c.col, '#1E1B4B', c.col], .95]);
  const S = addScene(t0 - .02, t1 + .02, `
    <div class="layer" id="fbg" style="background:radial-gradient(ellipse at center,${c.col}66,transparent 70%)"></div>
    <div class="a center" id="fn" style="left:0;width:1920px;top:40px;font-size:1000px;font-weight:800;line-height:1;letter-spacing:-.07em;color:${c.col};-webkit-text-stroke:4px #fff"><span class="sk">0${i + 1}</span></div>
    <div class="layer" id="cam">
      <div class="a" id="glow" style="left:${R ? 900 : -60}px;top:-40px;width:1100px;height:1100px;border-radius:50%;background:radial-gradient(circle,${c.col}55,transparent 62%)"></div>
      <div class="a num" id="num" style="${R ? 'left:470px' : 'left:640px'};top:120px"><span class="sk">0${i + 1}</span></div>
      <div class="a" id="ph" style="left:${R ? 1250 : 240}px;top:90px">${devHTML(c.kind, c.s, d.inner + SPEC)}</div>
      <div class="a${R ? '' : ' right'}" style="${R ? 'left:110px' : 'left:800px;width:1010px;text-align:right'};top:470px">
        <div class="desc" id="ds">${c.desc}</div>
        <div class="name" id="nm" style="margin-top:20px"><span class="sk">${c.name}</span></div>
        <div class="stripe" id="sp" style="${R ? 'left:0' : 'right:0'};top:262px;width:620px;background:linear-gradient(90deg,${c.col},${c.col}00);transform-origin:${R ? '0' : '100%'} 50%"></div>
        <div class="stats" style="margin-top:104px">${c.stats.map(([v, l]) => `<div class="stat"><div class="v" style="color:${c.col}">${v}</div><div class="l">${l}</div></div>`).join('')}</div>
      </div></div>`, world);
  FIN[i](S.el, d);
  const st_ = S.qa('.stat');
  sfx(t0, 'num'); hit(t0, .7, R ? 300 : 800);
  sfx(t0 + Q, 'whoosh'); sfx(t0 + Q * 2, 'slam'); hit(t0 + Q * 2, .3, 760);
  st_.forEach((_, k) => sfx(t0 + Q * (3 + k), 'tick'));
  sfx(t0 + Q * 7, 'punch'); hit(t0 + Q * 7.5, .25, 300);
  sfx(t1 - Q / 2, 'whip');
  S.render = t => {
    hudLabel = `N° <b>0${i + 1}</b> &nbsp;·&nbsp; ${c.name}`;
    const b = (t - t0) / Q; // quarti dall'inizio della scheda
    // quarto 0: numero a tutto schermo
    const nOn = b < 1;
    pose(S.q('#fn'), { o: nOn ? 1 : 0, s: lerp(1.35, 1, P(t, t0, .22, E.out5)), x: -(t - t0) * 120 });
    pose(S.q('#fbg'), { o: nOn ? 1 : .35 * (1 - P(t, t0 + Q, Q * 2)) });
    // quarto 1 e mezzo quarto a 7: dettaglio dello schermo
    if (b >= 1 && b < 2) macro(i, c.foc[0][0], c.foc[0][1], 2.5, b - 1, R ? 3 : -3, R ? 300 : -300);
    else if (b >= 7 && b < 7.5) macro(i, c.foc[1][0], c.foc[1][1], 3.3, (b - 7) * 2, R ? -2 : 2, 0);
    // dal quarto 2: composizione principale, che scorre veloce
    const mainOn = b >= 2 && !(b >= 7 && b < 7.5);
    pose(S.q('#cam'), mainOn ? { ...whipOut(t, t1, R ? -1 : 1), s: 1 + .05 * (t - t0) / B(3) } : { o: 0 });
    pose(S.q('#glow'), { o: .85 + .15 * Math.sin(t * 6) });
    pose(S.q('#num'), { x: -(t - t0 - Q * 2) * 70 * (R ? 1 : -1), s: lerp(1.2, 1, P(t, t0 + Q * 2, .25, E.out5)) });
    const pin = P(t, t0 + Q * 2, .35, E.out5);
    pose(S.q('#ph'), { x: (1 - pin) * 260 * (R ? 1 : -1) - (t - t0) * 12 * (R ? 1 : -1), y: Math.sin((t - t0) * 2.2) * 6, ry: (R ? -1 : 1) * lerp(34, 10, pin), rx: 3, s: lerp(.9, .96, pin) });
    spec(S.q('#ph'), t, t0 + Q * 2.2, 430); if (b > 8) spec(S.q('#ph'), t, t0 + Q * 8, 430);
    const fd = t >= t0 + Q * 2.5 ? (t < t0 + Q * 3 ? (Math.floor(t * 30) % 2 ? .25 : 1) : 1) : 0;
    pose(S.q('#ds'), { o: fd });
    const nm = S.q('#nm'); fitText(nm, 1000);
    io(nm, t, t0 + Q * 2, null, { dx: R ? 320 : -320, dy: 0, s0: 1.2, b: 30, d: .22, e: E.out5 });
    chroma(nm, t, t0 + Q * 2, 16);
    pose(S.q('#sp'), { sx: P(t, t0 + Q * 2, .3, E.out5), o: t >= t0 + Q * 2 ? 1 : 0 });
    st_.forEach((el, k) => { slam(el, t, t0 + Q * (3 + k), null, { s0: 1.5, d: .16 }); chroma(el.querySelector('.v'), t, t0 + Q * (3 + k), 10); });
  };
});

// =====================================================================================
// 4 · DISPOSITIVI (battute 24–27): due parole, quattro dispositivi a mezza battuta, poi tutti in fila
// =====================================================================================
{
  const DEV = [ // [tipo, tema, schermata, nome, scala da protagonista, posizione in fila, scala in fila]
    ['droid', st('mat', false, 'blue'), homePhone('Chiara'), 'Android', .9, [800, 640], .56],
    ['iphone', st('glass', false, 'violet'), homePhone('Marco'), 'iPhone', .9, [1120, 640], .56],
    ['tab', st('mat', true, 'green'), homeWide('Luca', false), 'Tablet', .78, [500, 440], .6],
    ['ipad', st('glass', true, 'blue'), homeWide('Sara', true), 'iPadOS', .8, [1420, 430], .6],
  ];
  const W = { droid: [420, 900], iphone: [430, 900], ipad: [1180, 830], tab: [1240, 790] };
  FOG.push([B(24), ['#3B82F6', '#8B5CF6', '#06B6D4'], .85]);
  const TIN = DEV.map((_, i) => B(24.5) + i * BAR / 2);
  const S = addScene(B(24), B(27) + .02, `
    <div class="a center slam" id="s1" style="left:0;width:1920px;top:400px">Su ogni</div>
    <div class="a center slam" id="s2" style="left:0;width:1920px;top:400px"><em class="grad">schermo.</em></div>
    ${DEV.map(d => `<div class="a num dn" style="-webkit-text-stroke-color:rgba(255,255,255,.26);left:0;width:1920px;text-align:center;top:${d[0] === 'droid' || d[0] === 'iphone' ? 250 : 120}px;font-size:${d[0] === 'droid' || d[0] === 'iphone' ? 420 : 380}px"><span class="sk">${d[3]}</span></div>`).join('')}
    ${DEV.map(d => `<div class="a dv" style="left:${960 - W[d[0]][0] / 2}px;top:${540 - W[d[0]][1] / 2}px">${devHTML(d[0], d[1], d[2] + SPEC)}</div>`).join('')}
    <div class="a center" id="ttl" style="left:0;width:1920px;top:70px;font-size:66px;font-weight:800;letter-spacing:-.03em;text-transform:uppercase">Stessa app. <em class="grad">Ovunque.</em></div>`, world);
  const dn = S.qa('.dn'), dv = S.qa('.dv');
  sfx(B(24), 'slam'); sfx(B(24) + Q, 'slam'); hit(B(24), .5);
  TIN.forEach((tt, i) => { sfx(tt, 'hit'); hit(tt, .45, 200 + i * 220); });
  sfx(B(26.5), 'impactS'); hit(B(26.5), .6); DEV.forEach((_, i) => sfx(B(26.5) + i * Q / 4, 'pop'));
  S.render = t => {
    hudLabel = 'Dispositivi';
    slam(S.q('#s1'), t, B(24), B(24) + Q - .04); chroma(S.q('#s1'), t, B(24), 14);
    slam(S.q('#s2'), t, B(24) + Q, B(24.5) - .04); chroma(S.q('#s2'), t, B(24) + Q, 14);
    DEV.forEach((d, i) => {
      const t0 = TIN[i], hero = t >= t0 && t < t0 + BAR / 2;
      const pin = P(t, t0, .3, E.out5);
      if (hero) {
        pose(dv[i], { s: d[4] * lerp(.82, 1, pin), ry: lerp(80, -8, pin) + (t - t0) * 6, rx: lerp(10, 3, pin), o: clamp(pin * 3), x: -(t - t0) * 60 });
        spec(dv[i], t, t0 + .1, W[d[0]][0], .4);
      }
      pose(dn[i], { o: hero ? clamp(pin * 3) : 0, s: lerp(1.3, 1, pin), x: -(t - t0) * 140 });
      chroma(dn[i], t, t0, 10);
      if (t >= B(26.5)) {
        const pl = P(t, B(26.5) + i * Q / 4, .3, E.back);
        pose(dv[i], { x: d[5][0] - 960, y: d[5][1] - 540 + Math.sin(t * 2 + i) * 6, s: d[6] * pl, o: clamp(pl * 2) * (1 - P(t, B(27) - .15, .15)), ry: (i % 2 ? -1 : 1) * 6 });
        dv[i].style.zIndex = d[0] === 'droid' || d[0] === 'iphone' ? 3 : 1;
      } else if (!hero) pose(dv[i], { o: 0 });
    });
    slam(S.q('#ttl'), t, B(26.5) + Q / 2, B(27) - .1, { s0: 1.3 });
  };
}

// =====================================================================================
// 5 · LIVREE (battute 27–30): un cambio di stile, tema o colore a ogni quarto
// =====================================================================================
{
  const SEQ = [ // [categoria, nome, stato]
    ['Stile', 'Liquid Glass', st('glass', false, 'blue')], ['Stile', 'Material', st('mat', false, 'blue')],
    ['Tema', 'Chiaro', st('glass', false, 'blue')], ['Tema', 'Scuro', st('glass', true, 'blue')],
    ...ACC_KEYS.map((k, i) => ['Colore', ACC[k][0], st(i % 2 ? 'mat' : 'glass', i % 3 !== 2, k)]),
  ];
  const TT = SEQ.map((_, i) => B(27) + i * Q);
  const S = addScene(B(27), B(30) + .02, `
    <div class="a" id="ph" style="left:1180px;top:90px">${devHTML('iphone', SEQ[0][2], `<div class="app" id="la">${homePhone('Chiara')}</div><div class="app" id="lb">${homePhone('Chiara')}</div>${SPEC}`)}</div>
    <div class="a" style="left:120px;top:400px;width:1000px">
      <div class="desc" id="cat"></div>
      <div class="name" id="val" style="margin-top:22px;font-size:180px"><span class="sk" id="vt"></span></div>
      <div class="stripe" id="sp" style="left:0;top:250px;width:560px;transform-origin:0 50%"></div></div>
    <div class="a center slam" id="l24" style="left:0;width:1920px;top:400px">24 livree.</div>`, world);
  const la = S.q('#la'), lb = S.q('#lb');
  TT.forEach(tt => { sfx(tt, 'switch'); hit(tt, .22, 760); });
  sfx(B(29.5), 'slam'); hit(B(29.5), .5);
  S.render = t => {
    hudLabel = 'Livree';
    let k = 0; while (k < SEQ.length - 1 && t >= TT[k + 1]) k++;
    const [cat, name, cur] = SEQ[k], prev = SEQ[Math.max(0, k - 1)][2], p = k ? P(t, TT[k], .08) : 1;
    fogOverride = t < B(29.5) && k >= 4 ? ACC_COL(cur.acc) : null;
    applyState(la, prev); applyState(lb, cur);
    la.style.display = p < 1 ? '' : 'none'; lb.style.opacity = p < 1 ? p.toFixed(3) : '';
    const pin = P(t, B(27), .35, E.out5), pout = P(t, B(29.5) - .1, .2, E.in);
    pose(S.q('#ph'), { y: (1 - pin) * 260 + Math.sin(t * 2) * 6, ry: -14 + Math.sin(t * 1.6) * 10, rx: 3, s: .96 * (1 + Math.exp(-(t - TT[k]) * 12) * .04), o: clamp(pin * 2) * (1 - pout), b: pout * 14 });
    spec(S.q('#ph'), t, TT[k], 430, .35);
    setText(S.q('#cat'), cat); setText(S.q('#vt'), name);
    const v = S.q('#val'); fitText(v, 960); slam(v, t, TT[k], B(29.5) - .08, { d: .12, s0: 1.3 }); chroma(v, t, TT[k], 12);
    pose(S.q('#cat'), { o: 1 - pout });
    const sp = S.q('#sp'); sp.style.background = `linear-gradient(90deg,${ACC_COL(cur.acc)},${ACC_COL(cur.acc)}00)`;
    pose(sp, { sx: P(t, TT[k], .2, E.out5), o: 1 - pout });
    slam(S.q('#l24'), t, B(29.5), B(30) - .05, { os: 2.4 }); chroma(S.q('#l24'), t, B(29.5), 14);
  };
}

// =====================================================================================
// 6 · GRIGLIA DI PARTENZA (battute 30–33): le 24 combinazioni schierate, la macchina da presa ci vola sopra
// =====================================================================================
{
  const ROWS = [st('glass', false), st('glass', true), st('mat', false), st('mat', true)];
  const combos = [];
  ACC_KEYS.forEach((a, ci) => ROWS.forEach((r, ri) => combos.push([ri, st(r.style, r.dark, a), ci])));
  const NM = ['Chiara', 'Marco', 'Sara', 'Luca', 'Anna', 'Leo'];
  FOG.push([B(30), ['#3B82F6', '#8B5CF6', '#06B6D4'], .85]);
  const S = addScene(B(30), B(33) + .02, `<div class="a" id="floor" style="left:960px;top:560px;width:0;height:0">
    ${combos.map(([ri, s, ci], g) => { const col = g % 2, row = Math.floor(g / 2), x = col ? 170 : -470, y = row * 760 + (col ? 380 : 0);
      return `<div class="a gc" style="left:${x}px;top:${y}px">
        <div class="slot" style="left:0;top:660px"></div><div class="slotn" style="left:${col ? 320 : -120}px;top:560px">${String(g + 1).padStart(2, '0')}</div>
        <div class="a" style="left:0;top:0;transform:scale(.7);transform-origin:0 0">${devHTML(ri < 2 ? 'iphone' : 'droid', s, homePhone(NM[ci]))}</div></div>`; }).join('')}</div>
    <div class="layer" style="background:linear-gradient(180deg,rgba(4,5,8,.92) 0%,rgba(4,5,8,.4) 32%,transparent 55%)"></div>
    <div class="a center slam" id="g1" style="left:0;width:1920px;top:80px;font-size:120px">24 combinazioni.</div>
    <div class="a center slam" id="g2" style="left:0;width:1920px;top:80px;font-size:120px"><em class="grad">La griglia è completa.</em></div>`, world);
  const gc = S.qa('.gc');
  sfx(B(30), 'impactS'); hit(B(30), .6, 300); sfx(B(30.5), 'slam'); sfx(B(31.75), 'slam');
  S.render = t => {
    hudLabel = 'Griglia di partenza';
    const p = (t - B(30)) / B(3);
    S.q('#floor').style.transform = `perspective(1300px) rotateX(${lerp(62, 54, p)}deg) translateY(${lerp(-7800, -2600, E.inOut(clamp(p)))}px) rotateZ(${lerp(-7, 5, p)}deg) scale(.82)`;
    gc.forEach((el, g) => { const pg = P(t, B(30) + (23 - g) * .025, .3, E.back); pose(el, { o: clamp(pg * 2), s: lerp(.7, 1, pg) }); });
    slam(S.q('#g1'), t, B(30.5), B(31.75) - .06); chroma(S.q('#g1'), t, B(30.5), 12);
    slam(S.q('#g2'), t, B(31.75), B(33) - .25); chroma(S.q('#g2'), t, B(31.75), 12);
    S.el.style.opacity = (1 - P(t, B(33) - .3, .28)).toFixed(3);
  };
}

// =====================================================================================
// 7 · SEMAFORO (battute 33–35): i sei banchi del logo si accendono uno per quarto, poi si spengono tutti
// =====================================================================================
{
  const POS = [[0, 0], [0, 1], [1, 0], [1, 1], [2, 0], [2, 1]]; // ordine di accensione: colonna per colonna
  const S = addScene(B(33), B(35) + .02, `${POS.map(([c, r]) => `<div class="lamp" style="left:${960 - 95 + (c - 1) * 250}px;top:${200 + r * 200}px"><i></i></div>`).join('')}`, world);
  const lamps = S.qa('.lamp i');
  POS.forEach((_, k) => sfx(B(33) + k * Q, 'light' + k));
  S.render = t => {
    lamps.forEach((el, k) => { const tk = B(33) + k * Q, on = t >= tk && t < B(35); pose(el, { o: on ? 1 : 0, s: on ? lerp(1.12, 1, P(t, tk, .15)) : 1 }); });
  };
}

// =====================================================================================
// 8 · VIA! (battute 35–44): logo, QR code e piattaforme
// =====================================================================================
{
  const q = QR, cell = 12, size = q.n * cell;
  const qrSVG = `<svg width="${size}" height="${size}" viewBox="0 0 ${q.n} ${q.n}" shape-rendering="crispEdges"><path fill="#0A1330" d="${q.cells.map(([x, y]) => `M${x} ${y}h1v1h-1z`).join('')}"/></svg>`;
  FOG.push([B(35), ['#3B82F6', '#8B5CF6', '#06B6D4'], 1]);
  const S = addScene(B(35), DURATION + 1, `
    <div class="layer" id="lg">
      <div class="a" id="logo2" style="left:810px;top:150px;width:300px;height:300px">${logoSVG('F')}</div>
      <div class="a center" id="wm2" style="left:0;width:1920px;top:470px;font-size:210px;font-weight:800;letter-spacing:.05em;line-height:1"><span class="w">A</span><span class="w">I</span><span class="w">L</span><span class="w">A</span></div>
      <div class="a center" id="tag2" style="left:0;width:1920px;top:710px;font-size:46px;font-weight:500;color:#DCE3FF">La tua scuola, <em class="grad" style="font-weight:700">sincronizzata.</em></div>
      <div class="a" id="plat" style="left:0;width:1920px;top:820px;display:flex;justify-content:center;gap:16px">
        ${[['smartphone', 'Android'], ['smartphone', 'iPhone'], ['tablet', 'Tablet'], ['tablet', 'iPadOS']].map(([ic, l]) => `<span class="chip pl">${I(ic, 'style="color:#A5B8FF"')}${l}</span>`).join('')}</div>
    </div>
    <div class="a" id="qr" style="left:1210px;top:190px;width:520px">
      <div class="center" style="font-size:54px;font-weight:800;letter-spacing:-.02em;text-transform:uppercase">Installala <em class="grad">ora.</em></div>
      <div class="qrbox" style="margin:30px auto 0;width:${size + 80}px">${qrSVG}</div>
      <div class="center" style="font-size:27px;font-weight:600;color:#DCE3FF;margin-top:30px">${I('scan-line', 'style="width:30px;height:30px;vertical-align:-7px;color:#9DB6FF"')}&nbsp; Inquadra il codice</div>
      <div class="center" style="font-size:22px;color:#A9B5DA;margin-top:10px">Ti serve solo il codice della tua classe.</div>
    </div>
    <div class="a center" id="cred" style="left:0;width:1920px;top:1000px;font-size:23px;color:#6E7AA3">Un progetto di Simone Bianchin</div>`, world);
  const wm = S.q('#wm2'); wm._w = [...wm.querySelectorAll('.w')];
  const pl = S.qa('.pl');
  sfx(B(35), 'impact'); hit(B(35), 1);
  sfx(B(36.5), 'whoosh'); pl.forEach((_, i) => sfx(B(37) + Q * 2 + i * Q / 2, 'pop'));
  S.render = t => {
    const t0 = B(35);
    renderLogo(S.q('#logo2'), t, t0 - .05, .45);
    pose(S.q('#logo2'), { y: Math.sin(t * 1.4) * 6 });
    wm._w.forEach((w, i) => io(w, t, t0 + .12 + i * Q / 4, null, { dy: 0, s0: 2, b: 26, d: .22 }));
    chroma(wm, t, t0 + .12, 14);
    io(S.q('#tag2'), t, B(36), null, { dy: 30 });
    const pm = P(t, B(36.5), .7, E.inOut);
    pose(S.q('#lg'), { x: -380 * pm, y: 40 * pm, s: lerp(1, .86, pm) });
    io(S.q('#qr'), t, B(36.5) + .12, null, { dx: 260, dy: 0, d: .6, b: 16, s0: .92 });
    pl.forEach((el, i) => io(el, t, B(37) + Q * 2 + i * Q / 2, null, { dy: 24, s0: .6, e: E.back, d: .35, b: 4 }));
    io(S.q('#cred'), t, B(38.5), null, { dy: 14 });
  };
}
world.appendChild(macroLayer); // sopra a tutte le scene

// ---------- motore ----------
FOG.sort((a, b) => a[0] - b[0]);
function seek(t) {
  fogOverride = null; hudLabel = ''; macroShown = -1;
  for (const sc of scenes) {
    const on = t >= sc.start && t < sc.end;
    if (sc._on !== on) { sc.el.style.display = on ? 'block' : 'none'; sc._on = on; }
    if (on) sc.render(t);
  }
  MAC.forEach((m, k) => { const on = k === macroShown; if (m._on !== on) { m.style.display = on ? 'block' : 'none'; m._on = on; } });
  renderAtmo(t);
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
