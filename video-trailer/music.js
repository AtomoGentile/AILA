// Colonna sonora del trailer, sintetizzata da zero (nessun campione esterno).
// 128 BPM, la minore: Am – F – C – G, una battuta per accordo. Due drop (logo e dispositivi), un muro
// di suono sulle 24 combinazioni, finale che si apre in La maggiore.
// Gli effetti sonori arrivano da sfx.json (node render.js sfx), già nei tempi del video.
//   node music.js  -> build/music.wav
const fs = require('fs');
const TL = require('./timeline');
const { BAR, BEAT, S } = TL;
const B = n => n * BAR;
const SR = 44100, DUR = TL.DURATION, N = Math.ceil(SR * DUR);
const SFX = JSON.parse(fs.readFileSync('sfx.json', 'utf8'));

const L = new Float32Array(N), R = new Float32Array(N);       // mix asciutto
const VL = new Float32Array(N), VR = new Float32Array(N);     // mandata al riverbero
const DL = new Float32Array(N), DR = new Float32Array(N);     // mandata al delay (solo lead)
const PL = new Float32Array(N), PR = new Float32Array(N);     // pad grezzo (filtrato dopo)
const BS = new Float32Array(N);                               // basso (prima del sidechain)
const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
let seed = 1234567;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const has = (t, a, b) => t >= a - 1e-6 && t < b - 1e-6;
const clamp = (x, a = 0, b = 1) => x < a ? a : x > b ? b : x;

function put(i, v, pan = 0, send = 0, dsend = 0) {
  if (i < 0 || i >= N) return;
  const gl = Math.cos((pan + 1) * Math.PI / 4) * 1.414, gr = Math.sin((pan + 1) * Math.PI / 4) * 1.414;
  L[i] += v * gl; R[i] += v * gr;
  if (send) { VL[i] += v * gl * send; VR[i] += v * gr * send; }
  if (dsend) { DL[i] += v * gl * dsend; DR[i] += v * gr * dsend; }
}
// biquad RBJ, coefficienti aggiornabili
function biquad(type, f, Q = .707) {
  const q = { x1: 0, x2: 0, y1: 0, y2: 0 };
  q.set = (f2, Q2 = Q) => {
    const w = 2 * Math.PI * Math.min(f2, SR * .45) / SR, c = Math.cos(w), s = Math.sin(w), a = s / (2 * Q2);
    let b0, b1, b2;
    if (type === 'lp') { b0 = (1 - c) / 2; b1 = 1 - c; b2 = (1 - c) / 2; }
    else if (type === 'hp') { b0 = (1 + c) / 2; b1 = -(1 + c); b2 = (1 + c) / 2; }
    else { b0 = a; b1 = 0; b2 = -a; }
    const a0 = 1 + a;
    q.b0 = b0 / a0; q.b1 = b1 / a0; q.b2 = b2 / a0; q.a1 = -2 * c / a0; q.a2 = (1 - a) / a0;
  };
  q.p = x => { const y = q.b0 * x + q.b1 * q.x1 + q.b2 * q.x2 - q.a1 * q.y1 - q.a2 * q.y2; q.x2 = q.x1; q.x1 = x; q.y2 = q.y1; q.y1 = y; return y; };
  q.set(f); return q;
}

// ---------------- armonia ----------------
const CHORDS = [ // [pad, fondamentale del basso, arpeggio]
  { pad: [57, 60, 64, 69, 71], bass: 45, arp: [69, 72, 76, 81] },  // Am(add9)
  { pad: [53, 57, 60, 64, 67], bass: 41, arp: [65, 69, 72, 77] },  // Fmaj9
  { pad: [55, 60, 64, 67, 74], bass: 48, arp: [67, 72, 76, 79] },  // C(add9)
  { pad: [55, 59, 62, 67, 69], bass: 43, arp: [67, 71, 74, 79] },  // G(add9)
];
const A_MAJ = { pad: [57, 61, 64, 69, 71, 76], bass: 45, arp: [69, 73, 76, 81] }; // l'accordo che chiude
function chordAt(t) {
  const bar = Math.floor(t / BAR + 1e-6);
  if (bar >= 65) return A_MAJ;          // ultima battuta: La maggiore
  if (bar === 63) return CHORDS[1];     // F
  if (bar === 64) return CHORDS[3];     // G
  return CHORDS[((bar % 4) + 4) % 4];
}

// ---------------- arrangiamento (in battute) ----------------
const GROOVE = t => has(t, B(8), B(36)) || has(t, B(38), B(56)) || has(t, B(59), B(63));
const HALF = t => has(t, B(56), B(58.5));                                     // fiducia: metà tempo
const STOP = t => has(t, B(5.75), B(6)) || has(t, B(7.75), B(8)) || has(t, B(37.75), B(38)); // silenzio prima dei drop
const kickOn = t => (GROOVE(t) || (HALF(t) && Math.round(t / BEAT) % 2 === 0)) && !STOP(t);
const pulseOn = t => has(t, B(2), B(5.75));                                  // battito sordo nel caos
const clapOn = t => GROOVE(t) && !STOP(t);
const hatOn = t => (GROOVE(t) || has(t, B(0), B(5.75)) || HALF(t)) && !STOP(t);
const arpOn = t => (has(t, B(8), B(36)) || has(t, B(38), B(58.5)) || has(t, B(59), B(64))) && !STOP(t);
const arpHi = t => has(t, B(28), B(36)) || has(t, B(46), B(56)) || has(t, B(59), B(63));
const leadOn = t => has(t, B(8), B(12)) || has(t, B(28), B(36)) || has(t, B(38), B(46)) || has(t, B(52), B(56)) || has(t, B(59), B(63));
function bassMode(t) {
  if (STOP(t)) return null;
  if (GROOVE(t)) return 'pump';
  if (has(t, B(6), B(7.75)) || has(t, B(36), B(37.75)) || HALF(t) || has(t, B(63), DUR - .5)) return 'long';
  return null;
}
function padCut(t) {
  if (t < B(6)) return 380 + 500 * t / B(6);
  if (t < B(8)) return 900 + 5000 * Math.pow((t - B(6)) / B(2), 2.2);
  if (has(t, B(36), B(38))) return 700 + 4500 * Math.pow((t - B(36)) / B(2), 2);
  if (has(t, B(56), B(59))) return 1200 + 4000 * Math.pow((t - B(56)) / B(3), 2);
  if (has(t, B(52), B(56))) return 4200;
  if (t >= B(63)) return 3200;
  return 3000;
}
function padLvl(t) {
  if (STOP(t)) return 0;
  if (t < B(6)) return .6;
  if (has(t, B(52), B(56))) return 1.05;
  return .85;
}

// ---------------- strumenti ----------------
function kick(t0, amp = 1, soft = false) {
  const i0 = Math.round(t0 * SR), len = Math.round(.45 * SR);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const f = (soft ? 40 : 48) + (soft ? 70 : 130) * Math.exp(-t * (soft ? 25 : 32));
    ph += 2 * Math.PI * f / SR;
    let v = Math.sin(ph) * Math.exp(-t * (soft ? 8 : 9));
    if (!soft) v += noise() * .3 * Math.exp(-t * 400);
    put(i0 + k, Math.tanh(v * 1.8) * .68 * amp);
  }
}
function clap(t0, amp = 1) {
  const i0 = Math.round(t0 * SR), len = Math.round(.3 * SR), bp = biquad('bp', 1400, .8), hp = biquad('hp', 700);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let env = Math.exp(-t * 16);
    for (const o of [0, .009, .019]) if (t >= o) env += .7 * Math.exp(-(t - o) * 170);
    ph += 2 * Math.PI * 190 / SR;
    const body = Math.sin(ph) * Math.exp(-t * 30) * .5; // corpo del rullante
    put(i0 + k, (hp.p(bp.p(noise())) * env + body) * .85 * amp, 0, .3);
  }
}
function hat(t0, amp = 1, open = false) {
  const i0 = Math.round(t0 * SR), len = Math.round((open ? .24 : .055) * SR), hp = biquad('hp', 8000, .8);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    put(i0 + k, hp.p(noise()) * Math.exp(-t * (open ? 16 : 75)) * .42 * amp, .25, .08);
  }
}
function crash(t0, amp = 1) {
  const i0 = Math.round(t0 * SR), len = Math.round(2.4 * SR), hp = biquad('hp', 5000, .6);
  for (let k = 0; k < len; k++) { const t = k / SR; put(i0 + k, hp.p(noise()) * Math.exp(-t * 1.9) * .32 * amp, -.2, .4); }
}
function pluck(t0, midi, amp, pan) {
  const i0 = Math.round(t0 * SR), len = Math.round(.36 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let v = 0;
    for (let h = 1; h <= 6; h++) v += Math.sin(2 * Math.PI * f * h * t) / h * Math.exp(-t * (8 + 8 * h));
    v *= Math.min(1, t / .003);
    put(i0 + k, v * amp, pan, .35);
  }
}
function bassNote(t0, dur, midi, amp) {
  const i0 = Math.round(t0 * SR), len = Math.round((dur + .06) * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const env = Math.min(1, t / .005) * (t < dur ? 1 : Math.exp(-(t - dur) * 70)) * (dur > .5 ? Math.exp(-t * .4) : Math.exp(-t * 4));
    // sega morbida + sub un'ottava sotto
    let saw = 0; for (let h = 1; h <= 7; h++) saw += Math.sin(2 * Math.PI * f * h * t) / h * (h > 3 ? .5 : 1);
    const v = Math.tanh(saw * 1.0 + Math.sin(Math.PI * f * t) * .7);
    const i = i0 + k; if (i < N) BS[i] += v * env * amp;
  }
}
// lead: tre seghe scordate con filtro che si apre a ogni nota, mandata al delay
function leadNote(t0, dur, midi, amp) {
  const i0 = Math.round(t0 * SR), len = Math.round((dur + .12) * SR), f = mtof(midi);
  const det = [-9, 0, 9].map(c => f * Math.pow(2, c / 1200));
  const lp = biquad('lp', 5000, 1.1), ph = [0, .33, .66];
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    if (k % 32 === 0) lp.set(1400 + 5200 * Math.exp(-t * 7), 1.1);
    let v = 0;
    for (let j = 0; j < 3; j++) { ph[j] += det[j] / SR; if (ph[j] >= 1) ph[j] -= 1; v += 2 * ph[j] - 1; }
    v += .5 * Math.sin(2 * Math.PI * f / 2 * t);
    const env = Math.min(1, t / .006) * (t < dur ? (.75 + .25 * Math.exp(-t * 6)) : .75 * Math.exp(-(t - dur) * 30));
    put(i0 + k, lp.p(v) * env * amp, 0, .3, .55);
  }
}
function bell(t0, midi, amp, pan = 0, decay = 4) {
  const i0 = Math.round(t0 * SR), len = Math.round(1.4 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const v = (Math.sin(2 * Math.PI * f * t + 1.2 * Math.sin(2 * Math.PI * f * 2.0 * t) * Math.exp(-t * 6)) + .3 * Math.sin(2 * Math.PI * f * 3 * t) * Math.exp(-t * 9))
      * Math.exp(-t * decay) * Math.min(1, t / .002);
    put(i0 + k, v * amp, pan, .45);
  }
}
function piano(t0, midi, amp, pan) {
  const i0 = Math.round(t0 * SR), len = Math.round(2.2 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let v = 0;
    for (let h = 1; h <= 5; h++) v += Math.sin(2 * Math.PI * f * h * t) / (h * h) * Math.exp(-t * (1.2 + 1.6 * h));
    put(i0 + k, v * Math.min(1, t / .004) * amp, pan, .5);
  }
}
function sweep(t0, t1, f0, f1, amp, shape = 'up', Q = 2) {
  // rumore filtrato: sale (riser), sale e scende (whoosh) o scende (risucchio)
  const i0 = Math.round(t0 * SR), len = Math.round((t1 - t0) * SR), bp = biquad('bp', f0, Q);
  for (let k = 0; k < len; k++) {
    const p = k / len;
    if (k % 32 === 0) bp.set(shape === 'arc' ? f0 * Math.pow(f1 / f0, Math.sin(p * Math.PI)) : f0 * Math.pow(f1 / f0, p), Q);
    const env = shape === 'arc' ? Math.pow(Math.sin(p * Math.PI), 2) : Math.pow(p, 2.2);
    put(i0 + k, bp.p(noise()) * env * amp, shape === 'arc' ? (p - .5) * 1.2 : 0, .5);
  }
}
function boom(t0, amp = 1, len = 3) {
  const i0 = Math.round(t0 * SR), n = Math.round(len * SR), hp = biquad('hp', 2500);
  let ph = 0;
  for (let k = 0; k < n; k++) {
    const t = k / SR;
    ph += 2 * Math.PI * (30 + 60 * Math.exp(-t * 7)) / SR;
    put(i0 + k, Math.tanh(Math.sin(ph) * Math.exp(-t * 1.7) * 1.5) * .95 * amp, 0, .2);
    put(i0 + k, hp.p(noise()) * Math.exp(-t * 1.5) * .45 * amp, 0, .6);
  }
}
function impact(t0, amp = 1) {
  boom(t0, amp, 3.4);
  const pre = Math.round(1.4 * SR), hp2 = biquad('hp', 4000), i0 = Math.round(t0 * SR);
  for (let k = 0; k < pre; k++) put(i0 - pre + k, hp2.p(noise()) * Math.pow(k / pre, 3) * .35 * amp, 0, .3); // piatto al contrario
}
function click(t0, f, amp, len = .012) {
  const i0 = Math.round(t0 * SR), n = Math.round(len * SR);
  for (let k = 0; k < n; k++) { const t = k / SR; put(i0 + k, (Math.sin(2 * Math.PI * f * t) * .6 + noise() * .4) * Math.exp(-t / len * 5) * amp, .1, .15); }
}
function blip(t0, amp = .2, f0 = 900, f1 = 1600) {
  const i0 = Math.round(t0 * SR), n = Math.round(.16 * SR);
  let ph = 0;
  for (let k = 0; k < n; k++) { const t = k / SR; ph += 2 * Math.PI * (f0 + (f1 - f0) * Math.min(1, t / .03)) / SR;
    put(i0 + k, Math.sin(ph) * Math.exp(-t * 30) * amp, 0, .35); }
}
function buzz(t0) { // vibrazione del telefono
  const i0 = Math.round(t0 * SR), n = Math.round(.42 * SR), lp = biquad('lp', 600);
  for (let k = 0; k < n; k++) { const t = k / SR; const v = Math.sign(Math.sin(2 * Math.PI * 165 * t)) * (t < .38 ? 1 : 0) * (.6 + .4 * Math.sin(2 * Math.PI * 22 * t));
    put(i0 + k, lp.p(v) * .22 * Math.min(1, t / .01), 0, .05); }
}

// ---------------- scrittura degli eventi ----------------
const kicks = [];
for (let b = 0; b * BEAT < DUR; b++) {
  const t = b * BEAT;
  if (kickOn(t)) { kick(t); kicks.push(t); }
  else if (pulseOn(t) && (b % 2 === 0 || t >= B(4))) kick(t, .25 + .5 * (t - B(2)) / B(3.75), true);
  if (clapOn(t) && b % 2 === 1) clap(t);
  if (HALF(t) && b % 4 === 2) clap(t, .9);
  if (hatOn(t)) {
    const intro = t < B(8);
    if (intro) { // ticchettio d'orologio, sempre più fitto
      hat(t, .3, false);
      if (t >= B(2)) hat(t + BEAT / 2, .35);
      if (t >= B(4)) { hat(t + BEAT / 4, .22); hat(t + 3 * BEAT / 4, .22); }
    } else {
      hat(t + BEAT / 2, 1, true);
      hat(t + BEAT / 4, .45); hat(t + 3 * BEAT / 4, .45);
    }
  }
}
// rullate: prima dei drop (sedicesimi che accelerano) e brevi stacchi a ogni cambio di funzione
function roll(t0, t1, a0, a1) {
  for (let t = t0, step = BEAT / 2; t < t1 - .02; t += step, step = Math.max(BEAT / 8, step * .88))
    clap(t, a0 + (a1 - a0) * (t - t0) / (t1 - t0));
}
roll(B(7), B(7.75), .25, .9);
roll(B(37), B(37.75), .25, .9);
roll(B(58.5), B(59) - .02, .4, 1);
for (let f = 1; f < 6; f++) { const tf = B(12 + f * 4); for (let k = 0; k < 4; k++) clap(tf - BEAT + k * BEAT / 4, .35 + k * .12); }
for (let k = 0; k < 4; k++) clap(B(46) - BEAT + k * BEAT / 4, .35 + k * .12);
for (let k = 0; k < 8; k++) clap(B(52) - BEAT * 2 + k * BEAT / 4, .3 + k * .08);
// piatti sugli attacchi delle sezioni
[B(8), B(38), B(46), B(52), B(59)].forEach(t => crash(t, 1.2));

// arpeggio a sedicesimi
const ARP_PATTERN = [0, 1, 2, 3, 2, 1, 3, 1];
for (let s = 0; s * BEAT / 4 < DUR; s++) {
  const t = s * BEAT / 4;
  if (!arpOn(t)) continue;
  const ch = chordAt(t), n = ch.arp[ARP_PATTERN[s % 8]];
  const outro = t >= B(63) ? Math.max(0, 1 - (t - B(63)) / B(1)) : 1;
  const half = HALF(t) ? .7 : 1;
  pluck(t, n, .18 * outro * half, s % 2 ? .35 : -.35);
  if (arpHi(t) && s % 2 === 0) pluck(t + BEAT / 8, n + 12, .05, s % 4 ? -.6 : .6);
}
// melodia: 8 ottavi per battuta, 0 = pausa, -1 = tiene la nota precedente. Frase A e frase B.
const MEL = [
  [76, -1, 76, -1, 74, 76, -1, 81], [79, -1, 77, -1, 76, -1, 72, -1], [76, -1, 76, -1, 74, 76, -1, 79], [74, -1, -1, 71, -1, -1, 74, 0],
  [81, -1, 79, -1, 76, -1, 74, 76], [72, -1, -1, -1, 69, -1, 72, -1], [76, -1, 79, -1, 81, -1, 79, 76], [74, -1, -1, -1, -1, -1, 0, 0],
];
for (let bar = 0; bar * BAR < DUR; bar++) {
  const t0 = B(bar);
  if (!leadOn(t0)) continue;
  const phr = MEL[(bar - 8 + 800) % 8];
  for (let e = 0; e < 8; e++) {
    const n = phr[e]; if (n <= 0) continue;
    let len = 1; while (e + len < 8 && phr[e + len] === -1) len++;
    const t = t0 + e * BEAT / 2;
    if (STOP(t)) continue;
    leadNote(t, len * BEAT / 2 - .03, n, has(t0, B(52), B(56)) ? .12 : .1);
  }
}
// basso
for (let e = 0; e * BEAT / 2 < DUR; e++) {
  const t = e * BEAT / 2, m = bassMode(t), root = chordAt(t).bass;
  if (m === 'pump') bassNote(t, BEAT / 2 - .04, root - 12 + (e % 2 ? 12 : 0), e % 2 ? .42 : .3);
  else if (m === 'long' && e % 8 === 0) bassNote(t, BAR - .1, root - 12, .3 * (t >= B(63) ? Math.max(.2, 1 - (t - B(63)) / B(3)) : 1));
}
// finale: pianoforte e campanelli sopra l'ultimo giro, chiusura in La maggiore
const UPDOWN = [0, 1, 2, 3, 2, 1, 3, 2];
for (let s = 0; B(63) + s * BEAT / 2 < DUR - 1.2; s++) {
  const t = B(63) + s * BEAT / 2, ch = chordAt(t);
  piano(t, ch.arp[UPDOWN[s % 8]] - 12, .24, s % 2 ? .3 : -.3);
}
piano(B(65), 57, .22, -.2); piano(B(65), 61, .2, 0); piano(B(65), 64, .2, .2); piano(B(65), 69, .18, 0);
[[0, 81], [1.5, 79], [3, 76], [4, 77], [5.5, 79], [7, 81], [7.5, 85]].forEach(([o, m]) => bell(B(63) + o * BEAT * 1, m, .11, 0, 2.2));
// intro: tre note di carillon sulla domanda, prima del drop
[[0, 76], [1, 72], [2, 69], [3, 71], [4, 72], [5, 74], [6, 76]].forEach(([o, m]) => bell(B(6) + o * BEAT / 2 * 1.6, m, .09, 0, 3));

// pad: supersaw (5 voci per nota), scritto grezzo e filtrato dopo con taglio variabile
for (let bar = 0; bar * BAR < DUR; bar++) {
  const t0 = B(bar), ch = chordAt(t0 + .01);
  const last = bar === S.END - 1;
  const i0 = Math.round(t0 * SR), len = Math.round((last ? BAR + 1 : BAR + 1.2) * SR);
  ch.pad.forEach((m, ni) => {
    [-14, -7, 0, 7, 14].forEach((c, vi) => {
      const f = mtof(m) * Math.pow(2, c / 1200); let ph = (ni * .37 + vi * .21) % 1;
      const pan = (vi - 2) / 2.5, dt = f / SR;
      for (let k = 0; k < len; k++) {
        const t = k / SR, i = i0 + k; if (i >= N) break;
        ph += dt; if (ph >= 1) ph -= 1;
        let v = 2 * ph - 1; // dente di sega polyBLEP
        if (ph < dt) { const x = ph / dt; v -= x + x - x * x - 1; } else if (ph > 1 - dt) { const x = (ph - 1) / dt; v -= x * x + x + x + 1; }
        const env = Math.min(1, t / .25) * (t < BAR ? 1 : Math.exp(-(t - BAR) * 4));
        const g = v * env * .027;
        PL[i] += g * (1 - pan) * .5; PR[i] += g * (1 + pan) * .5;
      }
    });
  });
}

// risers e effetti sincronizzati con le scene
sweep(B(6), B(7.75), 200, 9000, .55, 'up', 1.4);
sweep(B(36.5), B(37.75), 250, 8000, .5, 'up', 1.4);
sweep(B(50), B(52), 300, 7000, .3, 'up', 1.6);
for (const { t, type } of SFX) {
  switch (type) {
    case 'ping': bell(t, 88, .12, -.3, 6); bell(t + .07, 93, .09, -.3, 6); break;
    case 'ping2': bell(t, 81, .1, .3, 9); bell(t + .05, 76, .08, .3, 9); break;
    case 'buzz': buzz(t); break;
    case 'notify': bell(t, 84, .14, 0); bell(t + .11, 88, .14, 0); bell(t + .22, 91, .14, 0); break;
    case 'success': bell(t, 86, .12, 0); bell(t + .09, 93, .12, 0); break;
    case 'impact': impact(t, 1); break;
    case 'impactS': boom(t, .55, 1.8); break;
    case 'slam': kick(t, .7, true); boom(t, .3, .8); click(t, 300, .25, .05); break;
    case 'hit': kick(t, .6, true); boom(t, .35, 1.2); break;
    case 'suck': sweep(t - .45, t, 6000, 150, .5, 'up', 1.2); break;
    case 'riser': sweep(t - .05, t + BAR / 2 - .05, 400, 9000, .45, 'up', 1.3); break;
    case 'riserS': sweep(t - .05, t + BAR / 2 - .05, 400, 7000, .35, 'up', 1.3); break;
    case 'blip': blip(t); break;
    case 'pop': blip(t, .16, 700, 2100); break;
    case 'drop': blip(t, .2, 1800, 700); kick(t, .2, true); break;
    case 'key': click(t, 3400, .08, .007); break;
    case 'tick': click(t, 2000, .1, .01); break;
    case 'stamp': kick(t, .45, true); click(t, 900, .2, .03); break;
    case 'shuffle': for (let k = 0; k < 6; k++) click(t + k * .05, 1500 + k * 120, .08, .01); sweep(t - .1, t + .5, 500, 3500, .22, 'arc', 1.2); break;
    case 'send': sweep(t - .1, t + .3, 600, 4500, .22, 'arc', 1.2); break;
    case 'swoosh': sweep(t - .15, t + .4, 400, 3200, .26, 'arc', 1.2); break;
    case 'whoosh': sweep(t - .25, t + .45, 300, 6000, .36, 'arc', 1.1); break;
    case 'crash': crash(t, .8); break;
    case 'switch': click(t, 2600, .1, .008); bell(t, 98, .05, .2, 8); break;
    case 'switchBig': sweep(t - .12, t + .3, 800, 6000, .2, 'arc', 1.2); click(t, 2200, .12, .01); break;
  }
}

// ---------------- mix ----------------
// sidechain: pad e basso si abbassano a ogni cassa ("pompa")
const duck = new Float32Array(N).fill(1);
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.38 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .68 * Math.exp(-k / SR / .1));
}
{
  const fl = [biquad('lp', 800, .6), biquad('lp', 800, .6)], fr = [biquad('lp', 800, .6), biquad('lp', 800, .6)];
  for (let i = 0; i < N; i++) {
    const t = i / SR;
    if (i % 64 === 0) { const c = padCut(t); fl.forEach(f => f.set(c, .6)); fr.forEach(f => f.set(c, .6)); }
    const lv = padLvl(t) * duck[i];
    const l = fl[1].p(fl[0].p(PL[i])) * lv, r = fr[1].p(fr[0].p(PR[i])) * lv;
    L[i] += l; R[i] += r; VL[i] += l * .3; VR[i] += r * .3;
  }
  const hp = biquad('hp', 32);
  for (let i = 0; i < N; i++) { const v = hp.p(BS[i]) * duck[i] * .28 * (STOP(i / SR) ? 0 : 1); L[i] += v; R[i] += v; }
}
// delay ping-pong a 3/16 sul lead
{
  const d = Math.round(BEAT * .75 * SR), fb = .38;
  const bl = new Float32Array(d), br = new Float32Array(d); let p = 0;
  for (let i = 0; i < N; i++) {
    const ol = bl[p], or = br[p];
    bl[p] = DR[i] + or * fb; br[p] = DL[i] + ol * fb; p = (p + 1) % d;
    L[i] += ol * .45; R[i] += or * .45; VL[i] += ol * .2; VR[i] += or * .2;
  }
}
// riverbero (Freeverb semplificato)
function reverb(inp, offset) {
  const combs = [1557, 1617, 1491, 1422, 1277, 1356].map(d => ({ b: new Float32Array(d + offset), i: 0, s: 0 }));
  const aps = [556, 441, 341].map(d => ({ b: new Float32Array(d + offset), i: 0 }));
  const out = new Float32Array(N);
  for (let n = 0; n < N; n++) {
    let acc = 0; const x = inp[n] * .3;
    for (const c of combs) { const y = c.b[c.i]; c.s = y * .7 + c.s * .3; c.b[c.i] = x + c.s * .85; c.i = (c.i + 1) % c.b.length; acc += y; }
    for (const a of aps) { const bo = a.b[a.i]; const y = -acc + bo; a.b[a.i] = acc + bo * .5; a.i = (a.i + 1) % a.b.length; acc = y; }
    out[n] = acc;
  }
  return out;
}
const RL = reverb(VL, 0), RRv = reverb(VR, 23);
for (let i = 0; i < N; i++) { L[i] += RL[i] * .3; R[i] += RRv[i] * .3; }

// mastering: dissolvenze, saturazione morbida, normalizzazione
let peak = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR;
  const g = Math.min(1, t / .3) * (t > DUR - 1.6 ? Math.max(0, 1 - (t - DUR + 1.6) / 1.55) : 1);
  L[i] *= g; R[i] *= g;
  peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
}
const pre = 1.6 / peak;
const tmp = new Float32Array(N * 2); let p2 = 0;
for (let i = 0; i < N; i++) { tmp[2 * i] = Math.tanh(L[i] * pre); tmp[2 * i + 1] = Math.tanh(R[i] * pre); p2 = Math.max(p2, Math.abs(tmp[2 * i]), Math.abs(tmp[2 * i + 1])); }
const norm = .94 / p2;
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N * 2; i++) buf.writeInt16LE(Math.round(clamp(tmp[i] * norm, -1, 1) * 32767), 44 + i * 2);
fs.mkdirSync('build', { recursive: true });
fs.writeFileSync('build/music.wav', buf);
console.log('build/music.wav scritto, picco pre-saturazione', peak.toFixed(2));
