// Colonna sonora di "Fig. 1", sintetizzata da zero (nessun campione): un tappeto calmo che cambia accordo a ogni
// fase, più i piccoli suoni dell'interfaccia sugli eventi della scena (cambi di fase, circolari, notifiche, segnaposto…).
// I tempi vengono da timeline.js, gli stessi della pagina.
//   node audio.js  -> build/audio.wav
const fs = require('fs');
const TL = require('./timeline');
const { T, PHD, DURATION } = TL;

const SR = 44100, N = Math.ceil(SR * DURATION);
const L = new Float32Array(N), R = new Float32Array(N);        // mix asciutto
const VL = new Float32Array(N), VR = new Float32Array(N);      // mandata al riverbero
const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
let seed = 20261011;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const TAU = Math.PI * 2;
let K = 1;                                                     // ONLY=pad oppure ONLY=fx esporta una sola parte, per controllare i livelli
function put(i, v, pan = 0, send = 0) {
  if (i < 0 || i >= N) return;
  v *= K;
  const gl = Math.cos((pan + 1) * Math.PI / 4), gr = Math.sin((pan + 1) * Math.PI / 4);
  L[i] += v * gl; R[i] += v * gr; VL[i] += v * gl * send; VR[i] += v * gr * send;
}

// ---------------- strumenti ----------------
/* tappeto: ogni nota ha due voci leggermente scordate, una per lato; attacco e rilascio lenti si sovrappongono al cambio d'accordo */
function pad(t0, t1, notes, amp = 1) {
  const att = 1.9, rel = 2.4, i0 = Math.round((t0 - att * .5) * SR), i1 = Math.round((t1 + rel) * SR);
  notes.forEach((m, n) => [-1, 1].forEach(side => {
    const f = mtof(m) * Math.pow(2, side * 4 / 1200), lfo = .11 + n * .037, ph0 = n * 1.7 + side;
    for (let i = Math.max(0, i0); i < Math.min(N, i1); i++) {
      const t = i / SR, x = TAU * f * t;
      const env = Math.min(1, Math.max(0, (t - (t0 - att * .5)) / att)) * Math.min(1, Math.max(0, (t1 + rel - t) / rel));
      const v = (Math.sin(x) + .32 * Math.sin(2 * x + ph0) + .1 * Math.sin(3 * x)) * (.8 + .2 * Math.sin(TAU * lfo * t + ph0));
      put(i, v * env * env * .034 * amp, side * .7, .55);
    }
  }));
}
function sub(t0, t1, m, amp = 1) {
  const att = 1.2, rel = 2, f = mtof(m);
  for (let i = Math.max(0, Math.round((t0 - .4) * SR)); i < Math.min(N, Math.round((t1 + rel) * SR)); i++) {
    const t = i / SR, env = Math.min(1, Math.max(0, (t - (t0 - .4)) / att)) * Math.min(1, Math.max(0, (t1 + rel - t) / rel));
    put(i, Math.sin(TAU * f * t) * env * .085 * amp, 0, .1);
  }
}
function bell(t0, m, amp = 1, pan = 0, dec = 1.8) {
  const f = mtof(m), i0 = Math.round(t0 * SR), len = Math.round(dec * 2.6 * SR);
  for (let k = 0; k < len; k++) {
    const t = k / SR, a = Math.min(1, t / .004);
    const v = Math.sin(TAU * f * t) * Math.exp(-t * 3 / dec) + .34 * Math.sin(TAU * f * 2.76 * t) * Math.exp(-t * 9 / dec) + .14 * Math.sin(TAU * f * 5.4 * t) * Math.exp(-t * 20 / dec);
    put(i0 + k, v * a * .11 * amp, pan, .7);
  }
}
function pluck(t0, m, amp = 1, pan = 0) {
  const f = mtof(m), i0 = Math.round(t0 * SR), len = Math.round(.7 * SR);
  for (let k = 0; k < len; k++) {
    const t = k / SR, a = Math.min(1, t / .003);
    put(i0 + k, (Math.sin(TAU * f * t) + .28 * Math.sin(TAU * 2 * f * t) * Math.exp(-t * 20)) * a * Math.exp(-t * 11) * .1 * amp, pan, .5);
  }
}
/* un "tic" d'interfaccia: un soffio brevissimo più una nota acuta che si spegne subito */
function tick(t0, amp = 1, pan = 0, f = 1900) {
  const i0 = Math.round(t0 * SR), len = Math.round(.045 * SR); let prev = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR, n = noise(), hp = n - prev; prev = n;
    put(i0 + k, (hp * .5 * Math.exp(-t * 700) + Math.sin(TAU * f * t) * Math.exp(-t * 160)) * .07 * amp, pan, .2);
  }
}
/* fruscio per i movimenti di camera: rumore in un filtro passa-banda che sale e riscende */
function whoosh(t0, dur, amp = 1, f0 = 260, f1 = 1500) {
  const i0 = Math.round(t0 * SR), len = Math.round(dur * SR); let lo = 0, bd = 0;
  for (let k = 0; k < len; k++) {
    const p = k / len, env = Math.pow(Math.sin(Math.PI * p), 2), fc = f0 + (f1 - f0) * Math.sin(Math.PI * Math.pow(p, .8));
    const fq = 2 * Math.sin(Math.PI * fc / SR), hi = noise() - lo - .9 * bd; bd += fq * hi; lo += fq * bd;
    put(i0 + k, bd * env * .05 * amp, Math.sin(p * Math.PI) * .5 - .25, .45);
  }
}
/* velo acuto che si apre e si richiude (finestre, onda delle notifiche) */
function shimmer(t0, dur, notes, amp = 1) {
  const i0 = Math.round(t0 * SR), len = Math.round(dur * SR);
  notes.forEach((m, n) => {
    const f = mtof(m);
    for (let k = 0; k < len; k++) {
      const t = k / SR, p = k / len, env = Math.pow(Math.sin(Math.PI * Math.pow(p, .6)), 2);
      put(i0 + k, Math.sin(TAU * f * t + n) * (.7 + .3 * Math.sin(TAU * (5 + n) * t)) * env * .02 * amp, (n / (notes.length - 1 || 1)) * 1.4 - .7, .8);
    }
  });
}

// ---------------- armonia: un accordo per fase (la minore → fa → do → sol), si chiude in do ----------------
const CH = {
  Am: { root: 33, pad: [57, 60, 64, 71] },
  F: { root: 29, pad: [53, 57, 60, 64] },
  C: { root: 36, pad: [55, 60, 62, 64] },
  G: { root: 31, pad: [55, 59, 62, 69] },
  Cend: { root: 36, pad: [48, 55, 60, 64, 67, 74] },
};
const SEQ = [[0, T[0], 'Am'], [T[0], T[1], 'F'], [T[1], T[2], 'C'], [T[2], T[3], 'G'], [T[3], T[4], 'Am'], [T[4], T[5], 'F'], [T[5], T[6], 'C'], [T[6], TL.LOGO, 'G'], [TL.LOGO, DURATION, 'Cend']];
K = process.env.ONLY === 'fx' ? 0 : .42;                        // il tappeto resta sotto
for (const [a, b, c] of SEQ) { pad(a, b, CH[c].pad, c === 'Cend' ? 1.25 : 1); sub(a, b, CH[c].root, c === 'Cend' ? 1.2 : 1); }

K = process.env.ONLY === 'pad' ? 0 : 1.7;

// ---------------- apertura ----------------
whoosh(.1, 1.3, .7, 200, 900);                                              // la cornice si traccia
bell(1.3, 69, .55, -.3); bell(1.42, 76, .4, .3);                            // "AILA"
[57, 60, 64, 67].forEach((m, r) => pluck(TL.deskIn(r) + .1, m, .5, -.4 + r * .25));   // le file di banchi
tick(TL.WINDOWS, 1, -.5); shimmer(TL.WINDOWS, 1.8, [76, 81, 84], .8);       // finestre

// ---------------- le sei fasi ----------------
const CALL = [76, 79, 81, 76, 79, 84];
for (let k = 0; k < 6; k++) {
  tick(T[k], 1.3, -.55 + k * .12);                                          // cambio di fase
  if (k === 3) { tick(T[k] + .02, .9, 0, 300); } else whoosh(T[k], 1.9, .85);  // movimento di camera (alla quarta c'è lo stacco)
  bell(T[k] + TL.CO_IN + .1, CALL[k], .6, .4);                              // didascalia
}
// 1 · circolari
[72, 74, 76, 79, 81].forEach((m, k) => { pluck(TL.shArr(k) + .15, m, .75, -.5 + k * .25); tick(TL.shScan(k) + .5, .55, -.5 + k * .25, 2600); });
tick(TL.PULSE0, 1, 0); shimmer(TL.PULSE0, 2.3, [81, 84, 88, 93], 1.1);
// 2 · assistant
for (let i = 0; i < 13; i++) tick(TL.TYPE_Q + i * .083, .3, -.3, 2300 + (i % 3) * 200);
pluck(TL.ASK, 76, .8, -.3); bell(TL.ASK + .8, 81, .5, 0, 1.2);
TL.SRC.forEach((t, i) => { tick(t, .9, .2); pluck(t + .5, 74 + i * 5, .7, .2 + i * .3); });
pluck(TL.ANSWER, 84, .7, .2); bell(TL.ANSWER + .8, 88, .55, -.3, 1.4);
for (let i = 0; i < 8; i++) tick(TL.TYPE_A + i * .09, .3, -.3, 2500 + (i % 3) * 200);
// 3 · calendario
TL.EVT.forEach((t, i) => pluck(t + .1, [76, 79, 81][i], .7, -.2 + i * .15));
whoosh(TL.FLY, TL.LAND - TL.FLY, .5, 500, 2200); tick(TL.LAND, 1.1, 0); bell(TL.LAND, 86, .7, 0, 1.6);
// 4 · interrogazioni
const PINS = [[69, 72, 76], [67, 71, 74], [72, 76, 79]];
for (let gi = 0; gi < 3; gi++) for (let j = 0; j < 3; j++) pluck(TL.pinT(gi, j) + .1, PINS[gi][j], .75, -.5 + j * .5);
// 5 · mappa posti
for (let i = 0; i < 24; i++) tick(TL.TOKENS + i * .022, .28, -.7 + i * .06, 1700 + i * 35);
TL.MOVE.forEach((t, i) => { tick(t, .9, .3); whoosh(t, 1.1, .7, 400, 1900); pluck(t + 1.15, 72 + i * 2, .6, -.2); pluck(t + 1.27, 77 + i * 2, .6, .2); });
pluck(T[4] + 1.4, 69, .6, -.2); pluck(T[4] + 1.52, 76, .6, .2);
// 6 · bacheca
tick(TL.CARD, 1, .1); pluck(TL.CARD + .1, 72, .8, .1);
for (let v = 13; v <= 31; v++) {                                            // un tic per ogni voto che si aggiunge
  const p = (v - 12) / 19, e = p < .5 ? Math.cbrt(p / 4) : 1 - Math.cbrt((1 - p) / 4);
  tick(TL.VOTE[0] + e * TL.VOTE[1], .32, .1, 2000 + v * 30);
}
bell(TL.VOTE[0] + TL.VOTE[1], 84, .55, .2, 1.4);

// ---------------- chiusura ----------------
tick(T[6], 1.3, .3); whoosh(T[6], 2.8, 1, 220, 1300);
tick(TL.NET0, 1, .4);
[[60, 64, 67], [67, 72, 76], [72, 76, 79], [79, 84, 88]].forEach((ns, lvl) => ns.forEach((m, i) => pluck(TL.NET0 + lvl * .34 + i * .07, m, .6, -.5 + i * .5)));
whoosh(TL.LOGO - .7, 1.2, .8, 300, 2400);
[60, 67, 72, 76].forEach((m, i) => bell(TL.LOGO + .05 + i * .07, m, .65, -.4 + i * .27, 2.6));
bell(TL.LOGO + .65, 84, .6, .1, 2.4); bell(TL.LOGO + 1.0, 79, .35, -.3, 2); bell(TL.LOGO + 1.5, 86, .3, .3, 2);
shimmer(TL.LOGO + .3, 3.2, [84, 88, 91], .7);

// ---------------- riverbero (pettini smorzati in parallelo, poi due passa-tutto) e mastering ----------------
function reverb(inp, out, spread) {
  const combs = [1557, 1617, 1491, 1422, 1277, 1356].map(d => ({ buf: new Float32Array(d + spread), i: 0, lp: 0 }));
  const aps = [556, 441].map(d => ({ buf: new Float32Array(d + spread), i: 0 }));
  const fb = .86, damp = .32;
  for (let n = 0; n < N; n++) {
    const x = inp[n]; let y = 0;
    for (const c of combs) { const o = c.buf[c.i]; c.lp = o * (1 - damp) + c.lp * damp; c.buf[c.i] = x + c.lp * fb; c.i = (c.i + 1) % c.buf.length; y += o; }
    y *= .16;
    for (const a of aps) { const o = a.buf[a.i]; a.buf[a.i] = y + o * .5; a.i = (a.i + 1) % a.buf.length; y = o - y * .5; }
    out[n] += y;
  }
}
const WL = new Float32Array(N), WR = new Float32Array(N);
reverb(VL, WL, 0); reverb(VR, WR, 23);
let peak = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR, fade = Math.min(1, t / .3) * Math.min(1, (DURATION - t) / 1.3);
  L[i] = (L[i] + WL[i] * .9) * fade; R[i] = (R[i] + WR[i] * .9) * fade;
  peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
}
const gain = process.env.ONLY ? 1.2 : .82 / peak, pcm = Buffer.alloc(44 + N * 4);
pcm.write('RIFF', 0); pcm.writeUInt32LE(36 + N * 4, 4); pcm.write('WAVEfmt ', 8); pcm.writeUInt32LE(16, 16); pcm.writeUInt16LE(1, 20);
pcm.writeUInt16LE(2, 22); pcm.writeUInt32LE(SR, 24); pcm.writeUInt32LE(SR * 4, 28); pcm.writeUInt16LE(4, 32); pcm.writeUInt16LE(16, 34);
pcm.write('data', 36); pcm.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N; i++) {
  pcm.writeInt16LE(Math.round(Math.tanh(L[i] * gain * 1.15) / Math.tanh(1.15) * 32000), 44 + i * 4);
  pcm.writeInt16LE(Math.round(Math.tanh(R[i] * gain * 1.15) / Math.tanh(1.15) * 32000), 46 + i * 4);
}
fs.mkdirSync('build', { recursive: true });
fs.writeFileSync(`build/audio${process.env.ONLY ? '-' + process.env.ONLY : ''}.wav`, pcm);
console.log('build/audio.wav pronto, picco', peak.toFixed(3));
