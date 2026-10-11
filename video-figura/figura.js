'use strict';
/*
 * AILA — "Fig. 1". L'aula disegnata come una tavola tecnica: solidi scuri con il contorno chiaro, etichette
 * monospaziate, un numero grande in alto a sinistra e didascalie numerate collegate a un punto della scena.
 * La scena 3D è disegnata su un canvas da un piccolo proiettore prospettico (niente librerie); le scritte sono DOM.
 * Tutto dipende solo dal tempo: seek(t) ridisegna il fotogramma al secondo t, ed è quello che usa render.js.
 */
const FR = { x: 40, y: 40, w: 1840, h: 1000 };               // il riquadro della figura, in pixel sullo stage
const ZOOM = 1.1;                                            // ingrandimento della scena dentro il riquadro
const CX = FR.x + FR.w / 2, CY = FR.y + FR.h / 2;
const { DURATION, PH0, PHD, T } = TL;                        // i tempi stanno in timeline.js, condivisi con l'audio
window.DURATION = DURATION;

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
};
const P = (t, a, d, e = E.out) => e(clamp((t - a) / d));
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const lerp3 = (a, b, p) => [lerp(a[0], b[0], p), lerp(a[1], b[1], p), lerp(a[2], b[2], p)];
function rng(seed) {
  return () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
}

// ---------- colori ----------
const BG = [8, 9, 11], LINE = [226, 232, 245], ACC = [147, 164, 255], CREAM = [238, 226, 196];
const C_LO = [10, 11, 14], C_HI = [29, 32, 39];
const GREEN = [52, 211, 153], YELLOW = [251, 191, 36], GREY = [128, 136, 154], RED = [248, 113, 113];
const BLUE = [111, 168, 255], VIOLET = [167, 139, 250];
const rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a.toFixed(3)})`;
const mix = (a, b, p) => [lerp(a[0], b[0], p), lerp(a[1], b[1], p), lerp(a[2], b[2], p)];

// =====================================================================================
// Camera e proiezione
// =====================================================================================
// Ogni inquadratura parte a t, ci arriva in d secondi dalla precedente e poi scivola piano di dr metri al secondo.
// cut: stacco netto (con un battito di buio) invece del movimento, per i controcampi.
const CAM = [
  { t: 0, d: 0, pos: [8.6, 7.4, 18.6], tgt: [-0.4, 0.7, 3.4], fov: 30, dr: [-0.3, -0.2, -0.5] },
  { t: T[0], d: 1.9, pos: [6.3, 4.6, 12.6], tgt: [0.9, 1.35, 2.6], fov: 31, dr: [-0.07, 0, -0.04] },
  { t: T[1], d: 1.9, pos: [3.9, 2.3, 7.5], tgt: [0.85, 1.42, 1.5], fov: 37, dr: [-0.05, 0.01, -0.03] },
  { t: T[2], d: 1.9, pos: [-3.3, 1.8, 5.1], tgt: [-0.1, 1.52, 0], fov: 33, dr: [0.03, 0, -0.03] },
  { t: T[3], d: 0, cut: 1, pos: [6.4, 3.7, -4.1], tgt: [0.2, 1.25, 4.4], fov: 32, dr: [-0.08, 0, 0.02] },
  { t: T[4], d: 2.1, pos: [2.2, 14.6, 9.9], tgt: [1.55, 0.4, 4.75], fov: 33, dr: [-0.04, 0, 0] },
  { t: T[5], d: 2.1, pos: [-0.7, 2.0, 5.7], tgt: [2.25, 1.32, 0.3], fov: 34, dr: [0.04, 0, -0.03] },
  { t: T[6], d: 2.8, pos: [0, 21, 5.5], tgt: [0, 0, 4.85], fov: 30, dr: [0, 0.12, 0] },
];
const NEAR = 0.25;
let cam;
const poseAt = (k, t) => ({ pos: k.pos.map((v, i) => v + k.dr[i] * (t - k.t)), tgt: k.tgt, fov: k.fov });
function setCam(t) {
  let i = 0; while (i + 1 < CAM.length && t >= CAM[i + 1].t) i++;
  const k = CAM[i]; let p = poseAt(k, t);
  if (i > 0 && k.d > 0 && t < k.t + k.d) {
    const q = poseAt(CAM[i - 1], t), e = E.inOut((t - k.t) / k.d);
    p = { pos: lerp3(q.pos, p.pos, e), tgt: lerp3(q.tgt, p.tgt, e), fov: lerp(q.fov, p.fov, e) };
  }
  const f = norm(sub(p.tgt, p.pos)), r = norm(cross(f, [0, 1, 0])), u = cross(r, f);
  cam = { pos: p.pos, f, r, u, F: ZOOM * 410 / Math.tan(p.fov * Math.PI / 360) };
}
/* nei controcampi la scena si spegne un attimo prima dello stacco e si riaccende subito dopo */
const cutFade = t => CAM.reduce((a, k) => k.cut ? a * (t < k.t ? 1 - P(t, k.t - .2, .2, E.in) : P(t, k.t, .4)) : a, 1);

const cv = document.getElementById('cv'), g = cv.getContext('2d');
const toCam = p => { const d = sub(p, cam.pos); return [dot(d, cam.r), dot(d, cam.u), dot(d, cam.f)]; };
const toScr = q => [CX + q[0] / q[2] * cam.F, CY - q[1] / q[2] * cam.F, q[2]];
const project = p => { const q = toCam(p); return q[2] < NEAR ? null : toScr(q); };
/* poligono nel mondo -> punti sullo schermo, tagliato sul piano vicino */
function polyScr(pts) {
  const qs = pts.map(toCam), out = [];
  for (let i = 0; i < qs.length; i++) {
    const a = qs[i], b = qs[(i + 1) % qs.length], ia = a[2] >= NEAR, ib = b[2] >= NEAR;
    if (ia) out.push(toScr(a));
    if (ia !== ib) { const k = (NEAR - a[2]) / (b[2] - a[2]); out.push(toScr([a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, NEAR])); }
  }
  return out.length < 3 ? null : out;
}
function path(s, close = true) {
  g.beginPath(); g.moveTo(s[0][0], s[0][1]);
  for (let i = 1; i < s.length; i++) g.lineTo(s[i][0], s[i][1]);
  if (close) g.closePath();
}
/* aggiunge al percorso corrente il segmento a-b (o solo la sua prima parte, per disegnarlo progressivamente) */
function seg(a, b, prog = 1) {
  let qa = toCam(a), qb = toCam(b);
  if (prog < 1) qb = lerp3(qa, qb, prog);
  if (qa[2] < NEAR && qb[2] < NEAR) return;
  if (qa[2] < NEAR) qa = lerp3(qa, qb, (NEAR - qa[2]) / (qb[2] - qa[2]));
  else if (qb[2] < NEAR) qb = lerp3(qb, qa, (NEAR - qb[2]) / (qa[2] - qb[2]));
  g.moveTo(CX + qa[0] / qa[2] * cam.F, CY - qa[1] / qa[2] * cam.F);
  g.lineTo(CX + qb[0] / qb[2] * cam.F, CY - qb[1] / qb[2] * cam.F);
}
function strokeSegs(segs, col, a, w = 1.2, prog = 1) {
  if (a <= .004 || prog <= 0) return;
  g.beginPath(); for (const s of segs) seg(s[0], s[1], prog);
  g.lineWidth = w; g.strokeStyle = rgb(col, a); g.stroke();
}
const dist = p => Math.hypot(p[0] - cam.pos[0], p[1] - cam.pos[1], p[2] - cam.pos[2]);
const fogOf = p => clamp((dist(p) - 7) / 20) * .7;

/* testo appoggiato su un piano della scena: o = punto di partenza, ux/uy = direzioni di scrittura e verso l'alto */
function text3D(str, o, ux, uy, h, font, col, align = 'left') {
  const p0 = project(o), px = project(add(o, ux)), py = project(add(o, uy));
  if (!p0 || !px || !py) return;
  const k = h / 100;
  g.save();
  g.setTransform((px[0] - p0[0]) * k, (px[1] - p0[1]) * k, -(py[0] - p0[0]) * k, -(py[1] - p0[1]) * k, p0[0], p0[1]);
  g.font = font.replace('$', '100px'); g.fillStyle = col; g.textAlign = align; g.textBaseline = 'alphabetic';
  g.fillText(str, 0, 0);
  g.restore();
}
function glow(p, r, col, a) {
  if (!p || a <= .004 || r <= .5) return;
  const gr = g.createRadialGradient(p[0], p[1], 0, p[0], p[1], r);
  gr.addColorStop(0, rgb(col, a)); gr.addColorStop(1, rgb(col, 0));
  g.globalCompositeOperation = 'lighter'; g.fillStyle = gr;
  g.beginPath(); g.arc(p[0], p[1], r, 0, 7); g.fill();
  g.globalCompositeOperation = 'source-over';
}

// ---------- parallelepipedi ----------
const BV = i => [(i & 1) ? 1 : -1, (i & 2) ? 1 : -1, (i & 4) ? 1 : -1];
// facce: vertici, asse della normale, verso. Ordine: -x +x -y +y -z +z
const BF = [[[0, 4, 6, 2], 0, -1], [[1, 3, 7, 5], 0, 1], [[0, 1, 5, 4], 1, -1], [[2, 6, 7, 3], 1, 1], [[0, 2, 3, 1], 2, -1], [[4, 5, 7, 6], 2, 1]];
const BE = [];
for (let i = 0; i < 8; i++) for (const bit of [1, 2, 4]) if (!(i & bit)) {
  const j = i | bit; BE.push([i, j, BF.map((f, k) => f[0].includes(i) && f[0].includes(j) ? k : -1).filter(k => k >= 0)]);
}
const SHADE = [.26, .5, 0, 1, .16, .4];
const box = (c, s) => ({ c, h: [s[0] / 2, s[1] / 2, s[2] / 2] });
/* o.rev: 0..1 comparsa (prima i contorni, poi il riempimento); o.hl: 0..1 evidenziato; o.top: [colore, alpha] sulla faccia alta */
function drawBox(b, o = {}) {
  const rev = o.rev ?? 1; if (rev <= .001) return;
  const c = b.c, h = b.h, vis = BF.map(f => (cam.pos[f[1]] - c[f[1]]) * f[2] > h[f[1]]);
  const vs = []; for (let i = 0; i < 8; i++) { const s = BV(i); vs.push([c[0] + s[0] * h[0], c[1] + s[1] * h[1], c[2] + s[2] * h[2]]); }
  const fog = fogOf(c), fa = clamp((rev - .35) / .5) * (o.fillA ?? 1), hl = o.hl || 0;
  if (fa > 0) for (let f = 0; f < 6; f++) if (vis[f]) {
    const s = polyScr(BF[f][0].map(i => vs[i])); if (!s) continue;
    let col = mix(mix(C_LO, C_HI, SHADE[f]), BG, fog);
    if (hl) col = mix(col, ACC, .14 * hl);
    path(s); g.fillStyle = rgb(col, fa); g.fill();
    if (f === 3 && o.top && o.top[1] > .004) { g.fillStyle = rgb(o.top[0], o.top[1] * fa); g.fill(); }
  }
  g.beginPath(); const er = clamp(rev / .7);
  for (const e of BE) if (vis[e[2][0]] || vis[e[2][1]]) seg(vs[e[0]], vs[e[1]], er);
  g.lineWidth = lerp(o.w ?? 1.25, 2.2, hl);
  g.strokeStyle = rgb(mix(LINE, ACC, hl), lerp((o.ea ?? .62) * (1 - fog * .75), .98, hl) * clamp(rev * 3));
  if (hl > .05) { g.shadowColor = rgb(ACC, .85 * hl); g.shadowBlur = 14; }
  g.stroke(); g.shadowBlur = 0;
}

// =====================================================================================
// L'aula: misure in metri. La lavagna è sulla parete di fondo (z = 0), la classe guarda verso -z.
// =====================================================================================
const RX = 4.6, RZ = 9.3, RH = 3.1;                          // mezza larghezza, profondità, altezza delle pareti
const ROWS = [3.3, 4.65, 6.0, 7.35], COLS = [-2.7, 0, 2.7];  // 4 file da 3 banchi doppi: 24 posti
const seatXZ = k => { const r = Math.floor(k / 6), c = Math.floor((k % 6) / 2), s = k % 2 ? 1 : -1; return [COLS[c] + s * .33, ROWS[r] + .52]; };
const phoneXZ = k => { const [x, z] = seatXZ(k); return [x + .13, z - .5]; };
const SPARK = [0, 1.92, 1.5];                                // la scintilla sopra la cattedra
const sparkPos = t => [SPARK[0], SPARK[1] + .035 * Math.sin(t * 1.4), SPARK[2]];

// ---- fase 1: circolari ----
const SH = [['110', YELLOW], ['111', GREEN], ['112', GREEN], ['109', GREY], ['108', YELLOW]]; // [numero, categoria]
const SH_W = .5, SH_H = .66, shHome = k => [(k - 2) * .86, 2.72, 1.5];
const shArr = TL.shArr;
const { PULSE0, PULSE_V } = TL;                             // l'onda delle notifiche: partenza e metri al secondo
// ---- fase 2: assistant ----
const QK = 3;                                                // il posto di chi fa la domanda
const QTXT = '«Entro quando consegno l’autorizzazione?»', ATXT = 'Entro venerdì 23 ottobre.';
// ---- fase 3: calendario (ottobre 2026 inizia di giovedì) ----
const CAL = { x0: -2.92, cw: .52, yTop: 1.9, rh: .172, z: .05 };
const cellOf = d => { const i = d + 2; return [i % 7, Math.floor(i / 7)]; };
const cellAt = (d, fx = .5, fy = .5) => { const [c, r] = cellOf(d); return [CAL.x0 + (c + fx) * CAL.cw, CAL.yTop - (r + fy) * CAL.rh, CAL.z]; };
const EVT = [[22, RED, 'VERIFICA'], [27, YELLOW, 'INTERR.'], [29, GREEN, 'ASSEMBLEA']].map((e, i) => [...e, TL.EVT[i] - T[2]]);
const LAND = TL.LAND;                                        // la scadenza della circolare arriva sul 23
// ---- fase 4: interrogazioni ----
const GROUPS = [[2, 9, 19], [5, 12, 22], [0, 15, 17]], GLAB = ['LUN 2', 'MER 4', 'VEN 6'];
const pinT = TL.pinT;
const pinH = (gi, j) => [[1.6, 2.0, 1.75], [1.9, 1.55, 2.1], [2.2, 1.7, 1.95]][gi][j];
// ---- fase 5: mappa posti ----
const KIDS = ['GB', 'LM', 'SR', 'AF', 'MC', 'EP', 'DR', 'FT', 'CL', 'BN', 'AV', 'RG', 'MS', 'PL', 'ED', 'TC', 'NA', 'LF', 'IB', 'VM', 'OS', 'KR', 'EZ', 'GC'];
const R5 = rng(41);
const perm = () => { const a = [...Array(24).keys()]; for (let i = 23; i > 0; i--) { const j = Math.floor(R5() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
const SEAT_OF = [perm(), perm(), perm()];                    // per ogni proposta: studente -> posto
const MOVE = TL.MOVE.map(x => x - T[4]);                     // quando si passa da A a B e da B a C
const PAIRS = [[0, 4, 7, 10], [2, 3, 8, 11], [1, 5, 6, 9]];   // banchi con una coppia affiatata, per proposta
const propAt = t => t < T[4] + MOVE[0] + .5 ? 0 : t < T[4] + MOVE[1] + .5 ? 1 : 2;
// ---- fase 6: bacheca ----
const BRD = { x0: 1.86, w: .6, gap: .12, y0: 1.12, y1: 2.08, z: .05 };
const VOTES = [[12, 31], [24, 24], [17, 17]];
const votesAt = t => Math.round(lerp(12, 31, P(t, TL.VOTE[0], TL.VOTE[1], E.inOut)));
// ---- chiusura ----
const NET0 = TL.NET0;

function tokenState(i, t) {
  const u = t - T[4], seatP = k => { const [x, z] = seatXZ(k); return [x, 1.2, z]; };
  const m1 = P(u, MOVE[0] + i * .014, 1, E.inOut), m2 = P(u, MOVE[1] + i * .014, 1, E.inOut);
  const p = lerp3(lerp3(seatP(SEAT_OF[0][i]), seatP(SEAT_OF[1][i]), m1), seatP(SEAT_OF[2][i]), m2);
  p[1] += .75 * (Math.sin(Math.PI * m1) + Math.sin(Math.PI * m2));
  return { p, o: P(t, TL.TOKENS + SEAT_OF[0][i] * .022, .45, E.back) * (1 - P(u, PHD - .45, .4)) };
}
function sheetState(k, t) {
  const pin = P(t, shArr(k), .85, E.out5), home = shHome(k);
  let o = clamp(pin * 1.6), p = [home[0], home[1] + (1 - pin) * 1.7, home[2]], sc = 1;
  if (k !== 2) o *= 1 - P(t, T[2] + .2 + Math.abs(k - 2) * .1, .5);
  else {
    const fl = P(t, TL.FLY, LAND - TL.FLY, E.inOut);
    p = lerp3(p, cellAt(23), fl); p[2] += .5 * Math.sin(Math.PI * fl); sc = lerp(1, .1, fl);
    o *= 1 - P(t, LAND - .12, .12);
  }
  return { o, p, sc, scan: clamp((t - TL.shScan(k)) / .5), tag: P(t, TL.shScan(k) + .5, .3),
    hl: k === 2 ? P(t, T[1] + 3.1, .3) * (1 - P(t, T[1] + 6.7, .4)) + P(t, T[2] + 2.3, .3) : 0 };
}
function phoneState(k, t) {
  const [x, z] = phoneXZ(k), d = Math.hypot(x - SPARK[0], z - SPARK[2]);
  let a = P(t, PULSE0 + d / PULSE_V, .3) * (1 - P(t, T[2] + .1, .7)) * .9, col = ACC;
  if (k === QK) {  // il telefono di chi chiede: si accende quando scrive e lampeggia quando arriva la risposta
    a = Math.max(a, P(t, T[1] + .7, .3) * (1 - P(t, T[2] + .1, .7)));
    const fl = P(t, T[1] + 5.0, .12) * (1 - P(t, T[1] + 5.1, .9));
    col = mix(ACC, [255, 255, 255], .35 + .65 * fl);
  }
  return { a, col };
}
const deskGlow = (t, r, c) => [mix(BLUE, VIOLET, c / 2), P(t, NET0 - .5 + r * .16, .7) * (r < 2 ? .5 : .34)];

// ---------- oggetti ordinati per distanza (i più lontani per primi) ----------
const items = [];
// cattedra
{
  const body = box([0, .37, 1.5], [1.6, .74, .62]), top = box([0, .765, 1.5], [1.78, .05, .78]);
  items.push({ c: [0, .5, 1.5], draw(t) { const rev = P(t, 1.5, .9, E.inOut); drawBox(body, { rev }); drawBox(top, { rev }); } });
}
// banchi con i telefoni, e sedie
ROWS.forEach((z, r) => COLS.forEach((x, c) => {
  const t0 = TL.deskIn(r) + c * .07;
  const top = box([x, .725, z], [1.32, .05, .56]);
  const legs = [[-.6, -.22], [.6, -.22], [-.6, .22], [.6, .22]].map(([dx, dz]) => [[x + dx, 0, z + dz], [x + dx, .7, z + dz]]);
  const shelf = [[[x - .6, .55, z - .22], [x + .6, .55, z - .22]]];
  const phones = [0, 1].map(s => { const k = r * 6 + c * 2 + s, [px, pz] = phoneXZ(k); return { k, b: box([px, .758, pz], [.085, .014, .17]) }; });
  items.push({ c: top.c, draw(t) {
    const rev = P(t, t0, .9, E.inOut), fog = fogOf(top.c);
    strokeSegs(legs.concat(shelf), LINE, .36 * (1 - fog) * clamp(rev * 2), 1.1, rev);
    drawBox(top, { rev, top: deskGlow(t, r, c) });
    for (const ph of phones) {
      const st = phoneState(ph.k, t);
      drawBox(ph.b, { rev: P(t, 3.1 + ph.k * .025, .5), ea: .5, w: 1, top: [st.col, st.a], hl: ph.k === QK ? P(t, T[1] + .7, .3) * (1 - P(t, T[2] - .5, .4)) : 0 });
      if (st.a > .01) { const p = project([ph.b.c[0], .77, ph.b.c[2]]); if (p) glow(p, .2 * cam.F / p[2], st.col, .5 * st.a); }
    }
  } });
  [0, 1].forEach(s => {
    const k = r * 6 + c * 2 + s, [sx, sz] = seatXZ(k);
    const seat = box([sx, .45, sz], [.4, .035, .4]), back = box([sx, .72, sz + .2], [.4, .34, .035]);
    const lg = [[-.18, -.18], [.18, -.18], [-.18, .18], [.18, .18]].map(([dx, dz]) => [[sx + dx, 0, sz + dz], [sx + dx, dz > 0 ? .55 : .43, sz + dz]]);
    const gi = GROUPS.findIndex(gr => gr.includes(k)), gj = gi < 0 ? 0 : GROUPS[gi].indexOf(k);
    items.push({ c: [sx, .55, sz + .08], draw(t) {
      const rev = P(t, t0 + .22 + s * .05, .9, E.inOut), fog = fogOf(seat.c);
      const hl = gi < 0 ? 0 : P(t, pinT(gi, gj), .35) * (1 - P(t, T[4] - .25, .25));
      strokeSegs(lg, LINE, .3 * (1 - fog) * clamp(rev * 2), 1, rev);
      const parts = cam.pos[2] > sz + .2 ? [seat, back] : [back, seat];
      for (const b of parts) drawBox(b, { rev, hl, ea: .55, w: 1.1 });
    } });
  });
}));
// la scintilla
items.push({ c: SPARK, draw(t) {
  const on = P(t, T[0] + .2, .8, E.back); if (on <= .01) return;
  const pos = sparkPos(t), p = project(pos); if (!p) return;
  const think = P(t, T[1] + 2.8, .3) * (1 - P(t, T[1] + 4.2, .5)) + P(t, T[0] + 2.4, .3) * (1 - P(t, T[0] + 4.1, .5));
  const s = .23 * cam.F / p[2] * on * (1 + .06 * Math.sin(t * 2.6) + .16 * think * (.5 + .5 * Math.sin(t * 13)));
  glow(p, s * 4.2, [196, 182, 255], .34 * on * (1 + .7 * think)); glow(p, s * 1.7, [226, 220, 255], .5 * on);
  g.save(); g.translate(p[0], p[1]); g.rotate((1 - on) * -2.4 + think * Math.sin(t * 9) * .12); g.scale(s, s);
  g.beginPath(); g.moveTo(0, -1); g.quadraticCurveTo(.136, -.17, .8, 0); g.quadraticCurveTo(.136, .17, 0, 1);
  g.quadraticCurveTo(-.136, .17, -.8, 0); g.quadraticCurveTo(-.136, -.17, 0, -1); g.closePath();
  g.fillStyle = '#fff'; g.fill(); g.restore();
  const ring = P(t, T[1] + 1, .5) * (1 - P(t, T[2] - .5, .4));   // evidenziata nella fase dell'assistant
  if (ring > .01) {
    const R = .5 * cam.F / p[2];
    g.beginPath(); g.arc(p[0], p[1], R, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * ring);
    g.lineWidth = 2; g.strokeStyle = rgb(ACC, .95 * ring); g.shadowColor = rgb(ACC, .8); g.shadowBlur = 12; g.stroke(); g.shadowBlur = 0;
    g.beginPath(); for (let i = 0; i < 4; i++) { const a = i * Math.PI / 2 + t * .25; g.moveTo(p[0] + Math.cos(a) * (R + 5), p[1] + Math.sin(a) * (R + 5)); g.lineTo(p[0] + Math.cos(a) * (R + 14), p[1] + Math.sin(a) * (R + 14)); }
    g.strokeStyle = rgb(ACC, .8 * ring); g.lineWidth = 1.5; g.stroke();
  }
} });
// le circolari
SH.forEach((sh, k) => {
  const it = { c: shHome(k), draw(t) {
    const st = sheetState(k, t); if (st.o <= .01) return;
    const w = SH_W * st.sc / 2, h = SH_H * st.sc / 2, p = st.p, col = sh[1];
    const q = [[p[0] - w, p[1] - h, p[2]], [p[0] + w, p[1] - h, p[2]], [p[0] + w, p[1] + h, p[2]], [p[0] - w, p[1] + h, p[2]]];
    const s = polyScr(q); if (!s) return;
    g.globalAlpha = st.o;
    path(s); g.fillStyle = rgb(mix([17, 19, 24], col, .07 * st.tag), .95); g.fill();
    g.lineWidth = lerp(1.3, 2.2, st.hl); g.strokeStyle = rgb(mix(mix(LINE, col, .55 * st.tag), ACC, st.hl), .9);
    if (st.hl > .05) { g.shadowColor = rgb(ACC, .85); g.shadowBlur = 14 * st.hl; }
    g.stroke(); g.shadowBlur = 0;
    const X = f => p[0] - w + 2 * w * f, Y = f => p[1] + h - 2 * h * f;      // frazioni del foglio, dall'angolo in alto a sinistra
    if (st.sc > .6) {
      text3D('n. ' + sh[0], [X(.12), Y(.2), p[2] + .002], [1, 0, 0], [0, 1, 0], .075, '500 $ "Geist Mono"', rgb(LINE, .85));
      strokeSegs([.36, .47, .58].map((f, i) => [[X(.12), Y(f), p[2]], [X(i === 2 ? .6 : .88), Y(f), p[2]]]), LINE, .3, 2);
      if (st.tag > .01) {
        const t2 = polyScr([[X(.12), Y(.9), p[2] + .002], [X(.12 + .76 * st.tag), Y(.9), p[2] + .002], [X(.12 + .76 * st.tag), Y(.76), p[2] + .002], [X(.12), Y(.76), p[2] + .002]]);
        if (t2) { path(t2); g.fillStyle = rgb(col, .9); g.fill(); }
      }
      if (st.scan > 0 && st.scan < 1) {
        g.beginPath(); seg([X(-.06), Y(st.scan), p[2] + .003], [X(1.06), Y(st.scan), p[2] + .003]);
        g.lineWidth = 2; g.strokeStyle = rgb(ACC, 1); g.shadowColor = rgb(ACC, 1); g.shadowBlur = 12; g.stroke(); g.shadowBlur = 0;
      }
    }
    g.globalAlpha = 1;
  } };
  it.upd = t => { it.c = sheetState(k, t).p; };
  items.push(it);
});
// i segnaposto delle interrogazioni
GROUPS.forEach((gr, gi) => gr.forEach((k, j) => {
  const [x, z] = seatXZ(k);
  items.push({ c: [x, 1.4, z], draw(t) {
    const a = P(t, pinT(gi, j), .45) * (1 - P(t, T[4] - .3, .3)); if (a <= .01) return;
    strokeSegs([[[x, .5, z], [x, .5 + (pinH(gi, j) - .5) * a, z]]], ACC, .9 * a, 1.5);
    const p = project([x, .5, z]); if (p) { g.beginPath(); g.arc(p[0], p[1], 3.5, 0, 7); g.fillStyle = rgb(ACC, a); g.fill(); }
  } });
}));
// le pedine della mappa posti
KIDS.forEach((name, i) => {
  const it = { c: [0, 1.2, 5], draw(t) {
    const st = tokenState(i, t); if (st.o <= .01) return;
    const p = project(st.p); if (!p) return;
    const r = .215 * cam.F / p[2] * st.o;
    g.beginPath(); g.arc(p[0], p[1], r, 0, 7); g.fillStyle = rgb([15, 17, 22], .96); g.fill();
    g.lineWidth = 1.5; g.strokeStyle = rgb(LINE, .85); g.stroke();
    g.font = `500 ${(r * .82).toFixed(1)}px Sora`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillStyle = rgb(LINE, .95);
    g.fillText(name, p[0], p[1] + r * .06);
  } };
  it.upd = t => { it.c = tokenState(i, t).p; };
  items.push(it);
});

// ---------- fondo: pavimento, pareti, finestre, lavagna e bacheca ----------
function drawRoom(t) {
  const rf = P(t, .5, 1.3, E.inOut), rw = P(t, .9, 1.3, E.inOut), win = P(t, TL.WINDOWS, .9);
  // pavimento
  const fl = [[-RX, 0, 0], [RX, 0, 0], [RX, 0, RZ], [-RX, 0, RZ]], sf = polyScr(fl);
  if (sf) { path(sf); g.fillStyle = rgb([11, 12, 15], clamp((rf - .3) / .6)); g.fill(); }
  const grid = []; for (let x = -4; x <= 4; x++) grid.push([[x, 0, 0], [x, 0, RZ]]); for (let z = 1; z <= 9; z++) grid.push([[-RX, 0, z], [RX, 0, z]]);
  strokeSegs(grid, LINE, .045 * rf, 1, rf);
  // luce delle finestre sul pavimento
  if (win > .01) for (const zc of [1.6, 3.6, 5.6, 7.6]) {
    for (const [x1, a] of [[-1.1, .05], [-2.6, .045]]) { const s = polyScr([[-RX, .002, zc - .62], [x1, .002, zc - .2], [x1, .002, zc + 1.1], [-RX, .002, zc + .62]]); if (s) { path(s); g.fillStyle = rgb(CREAM, a * win); g.fill(); } }
  }
  strokeSegs(fl.map((p, i) => [p, fl[(i + 1) % 4]]), LINE, .34 * rf, 1.2, rf);
  // parete di fondo (si vede solo da dentro: da fuori resta lo spaccato)
  if (cam.pos[2] > 0) {
    const bw = [[-RX, 0, 0], [RX, 0, 0], [RX, RH, 0], [-RX, RH, 0]], s = polyScr(bw);
    if (s) { path(s); g.fillStyle = rgb([12, 13, 17], clamp((rw - .3) / .6)); g.fill(); }
    strokeSegs(bw.map((p, i) => [p, bw[(i + 1) % 4]]).concat([[[-RX, .12, 0], [RX, .12, 0]]]), LINE, .32 * rw, 1.2, rw);
    drawBoard(t); drawNotice(t);
  }
  // parete delle finestre
  if (cam.pos[0] > -RX) {
    const lw = [[-RX, 0, 0], [-RX, 0, RZ], [-RX, RH, RZ], [-RX, RH, 0]], s = polyScr(lw);
    if (s) { path(s); g.fillStyle = rgb([12, 13, 17], clamp((rw - .3) / .6)); g.fill(); }
    strokeSegs(lw.map((p, i) => [p, lw[(i + 1) % 4]]).concat([[[-RX, .12, 0], [-RX, .12, RZ]]]), LINE, .32 * rw, 1.2, rw);
    for (const zc of [1.6, 3.6, 5.6, 7.6]) {
      const z0 = zc - .68, z1 = zc + .68, x = -RX + .01, wq = [[x, 1.15, z0], [x, 1.15, z1], [x, 2.5, z1], [x, 2.5, z0]], ws = polyScr(wq);
      if (!ws) continue;
      path(ws); g.fillStyle = rgb(mix([15, 17, 21], CREAM, win * .92), rw); g.fill();
      if (win > .01) { const c = project([x, 1.82, zc]); if (c) glow(c, 1.5 * cam.F / c[2], CREAM, .13 * win); }
      strokeSegs(wq.map((p, i) => [p, wq[(i + 1) % 4]]), LINE, .5 * rw, 1.2, rw);
      strokeSegs([[[x, 1.15, zc - .227], [x, 2.5, zc - .227]], [[x, 1.15, zc + .227], [x, 2.5, zc + .227]], [[x, 2.05, z0], [x, 2.05, z1]]], [20, 22, 27], .9 * rw, 2.4, rw);
    }
  }
}
function quadOnWall(x0, x1, y0, y1, z) { return [[x0, y0, z], [x1, y0, z], [x1, y1, z], [x0, y1, z]]; }
function drawBoard(t) {
  const rev = P(t, 1.3, .9, E.inOut), on = P(t, T[2] + .3, .7) * (1 - P(t, T[3] - .25, .25)), hl = P(t, T[2] + .9, .4) * (1 - P(t, T[3] - .5, .3));
  const q = quadOnWall(-3.2, 1.0, .95, 2.25, .04), s = polyScr(q); if (!s) return;
  path(s); g.fillStyle = rgb(mix([10, 13, 14], [16, 22, 23], on), clamp((rev - .3) / .6)); g.fill();
  g.beginPath(); q.forEach((p, i) => seg(p, q[(i + 1) % 4], rev));
  g.lineWidth = lerp(1.3, 2, hl); g.strokeStyle = rgb(mix(LINE, ACC, hl), lerp(.55, .95, hl) * rev); g.stroke();
  strokeSegs([[[-3.2, .9, .1], [1.0, .9, .1]]], LINE, .4 * rev, 1.2, rev);
  // la griglia del mese: sempre accennata, piena quando la lavagna è "accesa"
  const a = (.2 + .8 * on) * rev, grid = [];
  for (let c = 0; c <= 7; c++) grid.push([[CAL.x0 + c * CAL.cw, CAL.yTop, CAL.z], [CAL.x0 + c * CAL.cw, CAL.yTop - 5 * CAL.rh, CAL.z]]);
  for (let r = 0; r <= 5; r++) grid.push([[CAL.x0, CAL.yTop - r * CAL.rh, CAL.z], [CAL.x0 + 7 * CAL.cw, CAL.yTop - r * CAL.rh, CAL.z]]);
  strokeSegs(grid, LINE, .3 * a, 1, rev);
  if (on <= .01) return;
  const ux = [1, 0, 0], uy = [0, 1, 0];
  text3D('OTTOBRE 2026', [CAL.x0, 2.07, CAL.z], ux, uy, .085, '500 $ Sora', rgb(LINE, .9 * on));
  'LMMGVSD'.split('').forEach((d, c) => text3D(d, [CAL.x0 + (c + .5) * CAL.cw, 1.935, CAL.z], ux, uy, .055, '500 $ "Geist Mono"', rgb(LINE, .42 * on), 'center'));
  for (let d = 1; d <= 31; d++) text3D(String(d), cellAt(d, .08, .42), ux, uy, .062, '500 $ "Geist Mono"', rgb(LINE, .6 * on));
  const dotAt = (d, col, a, tx, hot) => {
    if (a <= .01) return;
    const c = cellAt(d, .84, .3), p = project(c); if (!p) return;
    const r = .028 * cam.F / p[2] * a;
    glow(p, r * 4, col, .4 * a); g.beginPath(); g.arc(p[0], p[1], r, 0, 7); g.fillStyle = rgb(col, 1); g.fill();
    text3D(tx, cellAt(d, .08, .86), ux, uy, .042, '500 $ "Geist Mono"', rgb(mix(LINE, col, .55), .95 * clamp(a)));
    if (hot) {
      const cq = [cellAt(d, 0, 1), cellAt(d, 1, 1), cellAt(d, 1, 0), cellAt(d, 0, 0)], cs = polyScr(cq);
      if (cs) { path(cs); g.fillStyle = rgb(ACC, .1 * a); g.fill(); g.lineWidth = 2; g.strokeStyle = rgb(ACC, .95 * clamp(a)); g.shadowColor = rgb(ACC, .8); g.shadowBlur = 12; g.stroke(); g.shadowBlur = 0; }
    }
  };
  for (const [d, col, tx, at] of EVT) dotAt(d, col, P(t, T[2] + at, .45, E.back) * on, tx);
  dotAt(23, ACC, P(t, LAND - .1, .5, E.back) * on, 'AUTORIZZ.', true);
}
function drawNotice(t) {
  const rev = P(t, 1.45, .9, E.inOut), hlB = P(t, T[5] + .9, .4) * (1 - P(t, T[6] - .5, .3));
  const q = quadOnWall(1.7, 4.0, .95, 2.25, .04), s = polyScr(q); if (!s) return;
  path(s); g.fillStyle = rgb([15, 14, 14], clamp((rev - .3) / .6)); g.fill();
  strokeSegs(q.map((p, i) => [p, q[(i + 1) % 4]]), LINE, .55 * rev, 1.3, rev);
  VOTES.forEach((v, i) => {
    // le due proposte già appese ci sono dall'inizio; la terza (anonima) arriva nella fase della bacheca
    const a = i === 0 ? P(t, TL.CARD, .5, E.back) : P(t, 1.9 + i * .2, .6), hl = i === 0 ? hlB : 0;
    if (a <= .01) return;
    const x0 = BRD.x0 + i * (BRD.w + BRD.gap), x1 = x0 + BRD.w, drop = (1 - clamp(a)) * .12, y0 = BRD.y0 + drop, y1 = BRD.y1 + drop, z = BRD.z;
    const cq = quadOnWall(x0, x1, y0, y1, z), cs = polyScr(cq); if (!cs) return;
    g.globalAlpha = clamp(a);
    path(cs); g.fillStyle = rgb(mix([19, 21, 26], ACC, .1 * hl), .96); g.fill();
    g.lineWidth = lerp(1.2, 2.1, hl); g.strokeStyle = rgb(mix(LINE, ACC, hl), lerp(.6, .98, hl));
    if (hl > .05) { g.shadowColor = rgb(ACC, .85); g.shadowBlur = 14 * hl; }
    g.stroke(); g.shadowBlur = 0;
    const X = f => x0 + BRD.w * f, Y = f => y1 - (y1 - y0) * f, live = i === 0 ? hl : P(t, T[5] + .6, .5) * (1 - P(t, T[6] - .4, .3));
    // autore: cerchio pieno con il nome, oppure tratteggiato se la proposta è anonima
    const pc = project([X(.2), Y(.14), z]);
    if (pc) {
      const r = .05 * cam.F / pc[2];
      g.beginPath(); g.arc(pc[0], pc[1], r, 0, 7); g.lineWidth = 1.4; g.strokeStyle = rgb(LINE, .7);
      if (i !== 1) g.setLineDash([r * .5, r * .42]); else { g.fillStyle = rgb(LINE, .25); g.fill(); }
      g.stroke(); g.setLineDash([]);
    }
    text3D(i === 1 ? 'Marco R.' : 'Anonima', [X(.34), Y(.165), z], [1, 0, 0], [0, 1, 0], .05, '400 $ "Geist Mono"', rgb(LINE, .62));
    strokeSegs([.34, .43, .52].map((f, j) => [[X(.12), Y(f), z], [X(j === 2 ? .55 : .88), Y(f), z]]), LINE, .34, 2.4);
    // voti a favore: barra e numero
    const n = i === 0 ? votesAt(t) : v[1], bw = .76 * n / 34;
    strokeSegs([[[X(.12), Y(.79), z], [X(.88), Y(.79), z]]], LINE, .16, 5);
    strokeSegs([[[X(.12), Y(.79), z], [X(.12 + bw), Y(.79), z]]], mix(LINE, ACC, .5 + .5 * live), .95, 5);
    text3D(String(n), [X(.12), Y(.7), z], [1, 0, 0], [0, 1, 0], .1, '500 $ Sora', rgb(mix(LINE, ACC, .4 * live), .96));
    g.globalAlpha = 1;
  });
}

// ---------- sopra la scena: onde, traiettorie, coppie, rete ----------
const bez = (a, b, c, p) => lerp3(lerp3(a, b, p), lerp3(b, c, p), p);
function arc3(a, c, lift, p0, p1, col, alpha, w = 1.6) {     // arco tra due punti, disegnato da p0 a p1
  if (alpha <= .01 || p1 <= p0) return;
  const b = add(lerp3(a, c, .5), [0, lift, 0]); g.beginPath();
  let first = true;
  for (let i = 0; i <= 40; i++) { const p = lerp(p0, p1, i / 40), s = project(bez(a, b, c, p)); if (!s) continue; first ? g.moveTo(s[0], s[1]) : g.lineTo(s[0], s[1]); first = false; }
  g.lineWidth = w; g.strokeStyle = rgb(col, alpha); g.stroke();
  return project(bez(a, b, c, p1));
}
function drawOver(t) {
  // fase 1: l'onda delle notifiche che parte dalla scintilla e accende i telefoni
  const pr = (t - PULSE0) * PULSE_V;
  if (pr > 0 && pr < 10) for (const [off, k] of [[0, 1], [.7, .45]]) {
    const R = pr - off; if (R <= 0) continue;
    g.beginPath(); let first = true;
    for (let i = 0; i <= 96; i++) { const a = i / 96 * Math.PI * 2, s = project([SPARK[0] + Math.cos(a) * R, .78, SPARK[2] + Math.sin(a) * R]); if (!s) { first = true; continue; } first ? g.moveTo(s[0], s[1]) : g.lineTo(s[0], s[1]); first = false; }
    g.lineWidth = 1.6; g.strokeStyle = rgb(ACC, .75 * k * (1 - R / 10)); g.stroke();
  }
  // fase 2: la domanda sale alla scintilla, la scintilla consulta le fonti, la risposta torna al telefono
  const u2 = t - T[1];
  if (u2 > 0 && u2 < PHD) {
    const [qx, qz] = phoneXZ(QK), ph = [qx, .78, qz], sp = sparkPos(t), fade = 1 - P(u2, PHD - .6, .4);
    const go = P(t, TL.ASK, .8, E.inOut), back = P(t, TL.ANSWER, .8, E.inOut);
    arc3(ph, sp, .9, 0, go, ACC, .55 * fade * (1 - back * .0));
    const d1 = go > 0 && go < 1 ? project(bez(ph, add(lerp3(ph, sp, .5), [0, .9, 0]), sp, go)) : null;
    const d2 = back > 0 && back < 1 ? project(bez(sp, add(lerp3(ph, sp, .5), [0, .9, 0]), ph, back)) : null;
    for (const d of [d1, d2]) if (d) { glow(d, 20, ACC, .8); g.beginPath(); g.arc(d[0], d[1], 4.5, 0, 7); g.fillStyle = '#fff'; g.fill(); }
    // fonti: la circolare n. 112 e il calendario sulla lavagna
    const s1 = P(t, TL.SRC[0], .5, E.inOut), s2 = P(t, TL.SRC[1], .5, E.inOut), sh = shHome(2);
    for (const [to, pp] of [[[sh[0], sh[1] - SH_H / 2, sh[2]], s1], [cellAt(23), s2]]) {
      if (pp <= 0) continue;
      g.beginPath(); seg(sp, to, pp); g.setLineDash([5, 6]); g.lineWidth = 1.5; g.strokeStyle = rgb(ACC, .85 * fade); g.stroke(); g.setLineDash([]);
      if (pp >= 1) { const e = project(to); if (e) { g.beginPath(); g.arc(e[0], e[1], 4, 0, 7); g.fillStyle = rgb(ACC, fade); g.fill(); } }
    }
  }
  // fase 5: le coppie affiatate di ogni proposta
  const u5 = t - T[4];
  if (u5 > 0 && u5 < PHD) [[1.4, 2.55], [3.9, 4.6], [5.95, 6.95]].forEach(([a0, a1], pi) => {
    const a = P(u5, a0, .4) * (1 - P(u5, a1, .3)); if (a <= .01) return;
    for (const dk of PAIRS[pi]) {
      const A = SEAT_OF[pi].indexOf(dk * 2), B = SEAT_OF[pi].indexOf(dk * 2 + 1);
      const pa = project(tokenState(A, t).p), pb = project(tokenState(B, t).p); if (!pa || !pb) continue;
      const r = .215 * cam.F / pa[2] + 7, ang = Math.atan2(pb[1] - pa[1], pb[0] - pa[0]);
      g.beginPath(); g.arc(pa[0], pa[1], r, ang + Math.PI / 2, ang - Math.PI / 2); g.arc(pb[0], pb[1], r, ang - Math.PI / 2, ang + Math.PI / 2); g.closePath();
      g.lineWidth = 2; g.strokeStyle = rgb(ACC, .95 * a); g.shadowColor = rgb(ACC, .8); g.shadowBlur = 10; g.stroke(); g.shadowBlur = 0;
    }
  });
  // chiusura: scintilla e banchi collegati come una rete, cioè il logo
  if (t > NET0) {
    const y = .79, segs = [], sp = [SPARK[0], y, SPARK[2]];
    COLS.forEach(x => segs.push([sp, [x, y, ROWS[0]], 0]));
    for (let r = 0; r < 3; r++) COLS.forEach(xa => COLS.forEach(xb => segs.push([[xa, y, ROWS[r]], [xb, y, ROWS[r + 1]], r + 1])));
    segs.forEach(([a, b, lvl], i) => {
      const p = P(t, NET0 + lvl * .34 + (i % 9) * .02, .6, E.inOut); if (p <= 0) return;
      g.beginPath(); seg(a, b, p); g.lineWidth = lvl < 2 ? 2.4 : 1.6; g.strokeStyle = rgb([180, 198, 255], lvl < 2 ? .6 : .32); g.stroke();
    });
  }
}

// =====================================================================================
// Scritte sopra la figura
// =====================================================================================
const $ = id => document.getElementById(id);
const frame = $('frame');
const setHTML = (el, s) => { if (el._h !== s) { el.innerHTML = s; el._h = s; } };
const show = (el, o, y = 0) => {
  if (o <= .003) { if (el._v !== 0) { el.style.visibility = 'hidden'; el._v = 0; } return; }
  if (el._v !== 1) { el.style.visibility = ''; el._v = 1; }
  el.style.opacity = o.toFixed(3); if (y !== null) el.style.transform = `translateY(${y.toFixed(2)}px)`;
};
const dotH = c => `<i style="background:rgb(${c.join(',')})"></i>`;
const seatHead = k => { const [x, z] = seatXZ(k); return [x, 1.2, z]; };
/* fasi: -1 apertura, 0..5 le sei funzioni, 6 chiusura */
const INFO = {
  '-1': { name: 'Aula', st: 'Aula · 12 banchi · 24 posti', lab: 'L’APP PER LA VITA DI CLASSE', val: () => 'AILA', unit: '' },
  0: { name: 'Circolari', st: 'Circolari · riassunto · categoria per la classe · notifica',
    lab: 'CIRCOLARI · OGGI', val: t => SH.filter((_, k) => t > shArr(k) + .35).length, unit: 'nuove',
    num: '01', ttl: 'Circolari', at: [1236, 96], anchor: () => add(shHome(4), [SH_W / 2, SH_H / 2, 0]),
    desc: 'Ogni circolare nuova arriva con un riassunto, una categoria per la tua classe e le scadenze già estratte.',
    data: `<span>${dotH(GREEN)}TI RIGUARDA 2</span><span>${dotH(YELLOW)}POTENZIALE 2</span><span>${dotH(GREY)}NO 1</span>` },
  1: { name: 'Assistant', st: 'Assistant · domande in italiano · solo dati di AILA',
    lab: 'ASSISTANT · FONTI', val: t => TL.SRC.filter(x => t > x + .5).length, unit: 'citate',
    num: '02', ttl: 'AILA Assistant', at: [1250, 638], anchor: t => add(sparkPos(t), [.36, -.36, 0]),
    desc: 'Fai una domanda in italiano: risponde solo con i dati di AILA e ti mostra le fonti da aprire.',
    data: 'FONTI · CIRCOLARE N. 112 · CALENDARIO' },
  2: { name: 'Calendario', st: 'Calendario · scuola e classe · ore dalla 1ª alla 6ª',
    lab: 'OTTOBRE 2026 · EVENTI', val: t => EVT.filter(e => t > T[2] + e[3] + .2).length + (t > LAND + .1 ? 1 : 0), unit: 'in arrivo',
    num: '03', ttl: 'Calendario', at: [1246, 590], anchor: () => cellAt(23, 1, .5),
    desc: 'Eventi della scuola e della classe, anche creati dalle scadenze delle circolari.',
    data: '23 OTT · AUTORIZZAZIONE · DA CIRCOLARE N. 112' },
  3: { name: 'Interrogazioni', st: 'Interrogazioni · voti equi · storico visibile alla classe',
    lab: 'INTERROGAZIONI · POSTI ASSEGNATI', val: t => GROUPS.reduce((n, gr, gi) => n + gr.filter((_, j) => t > pinT(gi, j) + .2).length, 0), unit: 'di 9',
    num: '04', ttl: 'Sondaggi interrogazioni', at: [96, 656], side: 'L', anchor: () => { const [x, z] = seatXZ(2); return [x, .9, z + .2]; },
    desc: 'Prenoti le date con un sistema di voti equo, pensato per non poter essere aggirato.',
    data: 'LUN 2 · MER 4 · VEN 6 NOV · 3 POSTI PER DATA' },
  4: { name: 'Mappa posti', st: 'Mappa posti · 3 disposizioni · sceglie il Rappresentante',
    lab: 'MAPPA POSTI · PROPOSTA', val: t => 'ABC'[propAt(t)], unit: 'di 3',
    num: '05', ttl: 'Mappa posti', at: [1250, 420], anchor: t => add(tokenState(SEAT_OF[propAt(t)].indexOf(5), t).p, [.2, 0, 0]),
    desc: 'Tre disposizioni dei banchi che tengono conto di affinità, aiuto tra compagni, rumore e altezza.',
    data: 'AFFINITÀ · AIUTO · RUMORE · ALTEZZA' },
  5: { name: 'Bacheca', st: 'Bacheca · proponi, vota e commenta',
    lab: 'BACHECA · VOTI A FAVORE', val: t => votesAt(t), unit: 'voti',
    num: '06', ttl: 'Bacheca proposte', at: [1150, 84], anchor: () => [BRD.x0 + BRD.w / 2, BRD.y1, BRD.z],
    desc: 'Proponi, vota e commenta, anche in forma anonima.',
    data: 'DISTRIBUTORE D’ACQUA AL 2º PIANO · ANONIMA' },
  6: { name: 'Dall’alto', st: 'AILA · Android · iPhone · iPad', lab: 'AULA · VISTA DALL’ALTO', val: () => 24, unit: 'posti' },
};
const phaseOf = t => t < T[0] ? -1 : t >= T[6] ? 6 : Math.floor((t - T[0]) / PHD);
const phaseT0 = k => k < 0 ? 0 : T[k];
const CO_IN = TL.CO_IN, CO_OUT = PHD - .65;

const tags = {};
function tag(id, html, p, o, cls = '') {
  let el = tags[id];
  if (!el) { el = tags[id] = document.createElement('div'); el.className = 'tag ' + cls; el.innerHTML = '<div></div>'; frame.appendChild(el); }
  el._used = true;
  if (!p || o <= .003) { el.style.visibility = 'hidden'; return; }
  setHTML(el.firstChild, html);
  el.style.visibility = ''; el.style.opacity = o.toFixed(3);
  el.style.transform = `translate(${(p[0] - FR.x).toFixed(1)}px,${(p[1] - FR.y).toFixed(1)}px)`;
}
const typed = (s, t, t0, cps) => s.slice(0, Math.floor(clamp((t - t0) * cps, 0, s.length)));

function drawHUD(t) {
  const k = phaseOf(t), info = INFO[k], u = t - phaseT0(k);
  // numero grande e righe di stato
  const span = k < 0 ? [1.3, 4.5] : k === 6 ? [1.2, 4.7] : [.35, PHD - .3];
  const ro = $('ro'), a = P(u, span[0], .4) * (1 - P(u, span[1], .28, E.in));
  setHTML(ro.children[0], info.lab); setHTML(ro.querySelector('.n'), String(info.val(t))); setHTML(ro.querySelector('.u'), info.unit);
  show(ro, a, (1 - P(u, span[0], .5)) * 14);
  const sa = k < 0 ? P(t, 1.6, .5) : Math.min(1, P(u, .12, .3) + (1 - P(u, 0, .12)) * 0);
  setHTML($('stl'), `FASE 0${k + 1} / 07 · <b>${info.name}</b>`); show($('stl'), sa, null);
  setHTML($('str'), info.st); show($('str'), sa, null);
  show($('fig'), P(t, .7, .5), null); show($('ver'), P(t, .9, .5), null);

  // didascalia con la linea di richiamo
  const co = $('co');
  if (info.num && u > CO_IN - .1) {
    const out = 1 - P(u, CO_OUT, .3, E.in), [bx, by] = info.at, an = project(info.anchor(t));
    const lp = P(u, CO_IN, .55, E.inOut), ty = by + 22;
    // velo scuro sotto il testo, per leggerlo anche sopra le linee della scena
    g.save(); g.filter = 'blur(34px)'; g.fillStyle = rgb(BG, .95 * out * P(u, CO_IN + .15, .5));
    g.fillRect(FR.x + bx - 26, FR.y + by - 8, info.sw || 600, info.sh || 232); g.restore();
    if (an && lp > 0) {
      let ex = FR.x + bx - 18, kx = ex - 96; const ey = FR.y + ty;             // fine della linea e gomito
      if (info.side === 'L') {                                                 // testo a sinistra del punto: la linea arriva in fondo al titolo
        if (!info.tw) { g.font = '500 19px "Geist Mono"'; const a = g.measureText(info.num).width; g.font = '500 40px Sora'; info.tw = a + 16 + g.measureText(info.ttl).width - .88 * info.ttl.length; }
        ex = FR.x + bx + info.tw + 20; kx = ex + 96;
      }
      const pts = [[an[0], an[1]], [kx, ey], [ex, ey]], l1 = Math.hypot(kx - an[0], ey - an[1]), L = l1 + 96, d = L * lp;
      g.beginPath(); g.moveTo(an[0], an[1]);
      if (d <= l1) g.lineTo(lerp(an[0], kx, d / l1), lerp(an[1], ey, d / l1)); else { g.lineTo(kx, ey); g.lineTo(kx + (d - l1) * Math.sign(ex - kx), ey); }
      g.lineWidth = 1.4; g.strokeStyle = rgb(LINE, .9 * out); g.stroke();
      const ap = P(u, CO_IN - .1, .35, E.back);
      g.beginPath(); g.arc(an[0], an[1], 8.5 * ap, 0, 7); g.fillStyle = rgb(BG, .8 * out); g.fill(); g.lineWidth = 1.6; g.strokeStyle = rgb(LINE, .95 * out); g.stroke();
      g.beginPath(); g.arc(an[0], an[1], 3.2 * ap, 0, 7); g.fillStyle = rgb([255, 255, 255], out); g.fill();
      if (lp >= 1) { g.beginPath(); g.arc(pts[2][0], pts[2][1], 3, 0, 7); g.fillStyle = rgb([255, 255, 255], out); g.fill(); }
    }
    setHTML(co.querySelector('.num'), info.num); setHTML(co.querySelector('.ttl'), info.ttl);
    setHTML(co.querySelector('.desc'), info.desc); setHTML(co.querySelector('.data'), info.data);
    co.style.left = bx + 'px'; co.style.top = by + 'px';
    show(co, out, null);
    const h = co.querySelector('.h'), ds = co.querySelector('.desc'), da = co.querySelector('.data');
    h.style.opacity = P(u, CO_IN + .4, .4).toFixed(3); h.style.transform = `translateX(${((1 - P(u, CO_IN + .4, .5)) * -14).toFixed(2)}px)`;
    ds.style.opacity = P(u, CO_IN + .65, .5).toFixed(3);
    da.style.clipPath = `inset(0 ${((1 - P(u, CO_IN + 1.0, .9, E.lin)) * 100).toFixed(2)}% 0 0)`;
  } else show(co, 0, null);

  // etichette agganciate alla scena
  for (const id in tags) tags[id]._used = false;
  const u2 = t - T[1];
  if (u2 > 0 && u2 < PHD) {
    const [qx, qz] = phoneXZ(QK), p = project([qx, .98, qz]), fade = 1 - P(u2, PHD - .7, .4);
    const qa = P(u2, .8, .3) * (1 - P(u2, 4.75, .25)), aa = P(u2, 5.0, .3) * fade;
    if (qa > .003) tag('q', typed(QTXT, t, TL.TYPE_Q, 38) || '&nbsp;', p, qa);
    if (aa > .003) tag('a', `<em>✦</em> ${typed(ATXT, t, TL.TYPE_A, 34)}`, p, aa, 'acc');
  }
  const u4 = t - T[3];
  if (u4 > 0 && u4 < PHD) GROUPS.forEach((gr, gi) => gr.forEach((sk, j) => {
    const [x, z] = seatXZ(sk), a = P(t, pinT(gi, j) + .2, .3) * (1 - P(u4, PHD - .45, .3));
    if (a > .003) tag(`p${gi}${j}`, GLAB[gi], project([x, pinH(gi, j) + .02, z]), a, 'sm acc');
  }));
  const u5 = t - T[4];
  if (u5 > 0 && u5 < PHD) {
    const a = P(u5, 1.3, .4) * (1 - P(u5, PHD - .6, .3));
    if (a > .003) tag('cat', '<s>CATTEDRA</s>', project([0, .8, 2.3]), a, 'sm');
  }
  for (const id in tags) if (!tags[id]._used) tags[id].style.visibility = 'hidden';
}

// =====================================================================================
// Chiusura: il logo vero, che è la stessa aula vista dall'alto
// =====================================================================================
const LOGO_T = TL.LOGO;
(function buildLogo() {
  const apex = [256, 77.568], mid = [93.082, 256, 418.918].map(x => [x, 279.296]), bot = [93.082, 256, 418.918].map(x => [x, 465.459]);
  const lines = []; mid.forEach(m => lines.push([apex, m])); mid.forEach(m => bot.forEach(b => lines.push([m, b])));
  $('logo').innerHTML = `<svg viewBox="0 0 512 512" width="100%" height="100%" style="overflow:visible">
  <defs>
    <linearGradient id="lg-desk" x1="0" y1="512" x2="512" y2="0" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#6FA8FF"/><stop offset="1" stop-color="#A78BFA"/></linearGradient>
    <linearGradient id="lg-spk" x1="0" y1="0" x2="0" y2="155" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#DCE5FF"/></linearGradient>
    <radialGradient id="lg-halo" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#C9B8FF" stop-opacity=".6"/><stop offset="1" stop-color="#C9B8FF" stop-opacity="0"/></radialGradient>
  </defs>
  ${lines.map(([a, b]) => `<line class="ln" x1="${a[0]}" y1="${a[1]}" x2="${b[0]}" y2="${b[1]}" stroke="#B4C6FF" stroke-opacity=".45" stroke-width="14" stroke-linecap="round"/>`).join('')}
  ${[232.755, 418.918].map((y, r) => [31.027, 193.946, 356.864].map(x => `<rect class="dk" x="${x}" y="${y}" width="124.109" height="93.082" rx="31.027" fill="url(#lg-desk)" style="transform-box:fill-box;transform-origin:center"/>`).join('')).join('')}
  <circle class="halo" cx="256" cy="77.568" r="131.891" fill="url(#lg-halo)" style="transform-box:fill-box;transform-origin:center"/>
  <path class="spk" d="M256,0 Q266.549,64.381 318.054,77.568 Q266.549,90.755 256,155.136 Q245.451,90.755 193.946,77.568 Q245.451,64.381 256,0 Z" fill="url(#lg-spk)" style="transform-box:fill-box;transform-origin:center"/>
</svg>`;
  $('pl').innerHTML = 'ANDROID · IPHONE · IPAD &nbsp;<b>—</b>&nbsp; GITHUB.COM/ATOMOGENTILE/AILA';
})();
function drawEnd(t) {
  const root = $('logo'), end = $('end');
  if (t < LOGO_T - .1) { show(end, 0, null); return; }
  show(end, 1, null);
  if (!root._p) {
    root._p = { dk: [...root.querySelectorAll('.dk')], ln: [...root.querySelectorAll('.ln')], halo: root.querySelector('.halo'), spk: root.querySelector('.spk') };
    root._p.ln.forEach(l => { l._L = Math.hypot(l.x2.baseVal.value - l.x1.baseVal.value, l.y2.baseVal.value - l.y1.baseVal.value); l.style.strokeDasharray = l._L; });
  }
  const { dk, ln, halo, spk } = root._p, t0 = LOGO_T;
  dk.forEach((d, i) => { const p = P(t, t0 + i * .06, .6, E.back); d.style.opacity = (clamp(p * 1.5) * (i >= 3 ? .78 : 1)).toFixed(3); d.style.transform = `translateY(${((1 - p) * 60).toFixed(2)}px) scale(${lerp(.4, 1, p).toFixed(4)})`; });
  ln.forEach((l, i) => { l.style.strokeDashoffset = ((1 - P(t, t0 + .3 + i * .03, .55, E.inOut)) * l._L).toFixed(2); });
  const ph = P(t, t0 + .6, .9); halo.style.opacity = ph.toFixed(3); halo.style.transform = `scale(${(lerp(.2, 1, ph) * (1 + .05 * Math.sin(t * 2.2))).toFixed(4)})`;
  const ps = P(t, t0 + .65, .8, E.back); spk.style.opacity = clamp(ps * 2).toFixed(3); spk.style.transform = `scale(${ps.toFixed(4)}) rotate(${((1 - ps) * -140).toFixed(2)}deg)`;
  const w = (el, a, d) => { const p = P(t, a, d, E.out5); el.style.opacity = clamp(p * 1.3).toFixed(3); el.style.transform = `translateY(${((1 - p) * 26).toFixed(2)}px)`; };
  w($('wm'), t0 + .45, .8); w($('tg'), t0 + .95, .8); w($('pl'), t0 + 1.45, .8);
}

// =====================================================================================
// Un fotogramma
// =====================================================================================
function seek(t) {
  t = clamp(t, 0, DURATION);
  setCam(t);
  g.setTransform(1, 0, 0, 1, 0, 0);
  g.fillStyle = rgb(BG); g.fillRect(0, 0, 1920, 1080);
  g.save(); g.beginPath(); g.rect(FR.x, FR.y, FR.w, FR.h); g.clip();
  // un chiarore largo dietro la scena, perché il nero non sia piatto
  const bgl = g.createRadialGradient(CX, CY + 60, 0, CX, CY + 60, 1100);
  bgl.addColorStop(0, 'rgba(26,29,38,.5)'); bgl.addColorStop(1, 'rgba(26,29,38,0)'); g.fillStyle = bgl; g.fillRect(FR.x, FR.y, FR.w, FR.h);
  drawRoom(t);
  for (const it of items) { if (it.upd) it.upd(t); it.d = dist(it.c); }
  for (const it of [...items].sort((a, b) => b.d - a.d)) it.draw(t);
  drawOver(t);
  // bordi più scuri, stacchi e buio di chiusura
  const vg = g.createRadialGradient(CX, CY, 420, CX, CY, 1120); vg.addColorStop(0, rgb(BG, 0)); vg.addColorStop(1, rgb(BG, .72));
  g.fillStyle = vg; g.fillRect(FR.x, FR.y, FR.w, FR.h);
  const bs = g.createLinearGradient(0, FR.y + FR.h - 120, 0, FR.y + FR.h); bs.addColorStop(0, rgb(BG, 0)); bs.addColorStop(1, rgb(BG, .78));
  g.fillStyle = bs; g.fillRect(FR.x, FR.y + FR.h - 120, FR.w, 120);
  const dim = Math.max(1 - cutFade(t), P(t, LOGO_T - .5, 1.1, E.inOut) * .9, 1 - P(t, .1, .5));
  if (dim > .003) { g.fillStyle = rgb(BG, dim); g.fillRect(FR.x, FR.y, FR.w, FR.h); }
  drawHUD(t);
  g.restore();
  // la cornice della figura si traccia nei primi istanti
  const fp = P(t, .15, 1.1, E.inOut), per = 2 * (FR.w + FR.h);
  g.beginPath(); g.rect(FR.x + .5, FR.y + .5, FR.w - 1, FR.h - 1); g.setLineDash([per * fp, per]); g.lineWidth = 1; g.strokeStyle = 'rgba(255,255,255,.15)'; g.stroke(); g.setLineDash([]);
  drawEnd(t);
}
window.seek = seek;

// ---------- anteprima nel browser ----------
(function preview() {
  const stage = $('stage'), ui = $('ui'), capture = /[?&]capture/.test(location.search);
  if (capture) { ui.style.display = 'none'; seek(0); return; }
  const fit = () => { const s = Math.min(innerWidth / 1920, (innerHeight - 44) / 1080); stage.style.transform = `scale(${s})`; };
  addEventListener('resize', fit); fit();
  let t = +(location.hash.match(/t=([\d.]+)/) || [0, 0])[1], playing = true, last = performance.now();
  const sk = $('seek'), tm = $('tm'), btn = $('play');
  sk.max = DURATION;
  sk.oninput = () => { t = +sk.value; };
  btn.onclick = () => { playing = !playing; btn.textContent = playing ? 'Pausa' : 'Play'; };
  (function loop(now) {
    if (playing) { t += (now - last) / 1000; if (t > DURATION) t = 0; sk.value = t; }
    last = now; tm.textContent = t.toFixed(1) + ' s'; seek(t); requestAnimationFrame(loop);
  })(last);
  document.fonts.ready.then(() => seek(t));
})();
