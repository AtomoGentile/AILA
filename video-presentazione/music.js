// Colonna sonora del video, sintetizzata da zero (nessun campione esterno).
// 120 BPM, una battuta = 2 s, progressione Bm7 – Gmaj7 – D – A (vi–IV–I–V in Re maggiore).
// Struttura legata alle scene di video.js; gli effetti sonori arrivano da sfx.json (node render.js sfx).
//   node music.js  -> build/music.wav
const fs = require('fs');
const TL = require('./timeline');
const SR = 44100, DUR = TL.DURATION, N = Math.ceil(SR * DUR);
// momenti chiave nel tempo del video (vedi timeline.js)
const W = TL.unwarp;
const DROP = W(16), CIRC = W(20), ASSIST = W(38), CAL = W(50), SEATS = W(76), BUILD = W(106), FINALE = W(113);
const SFX = JSON.parse(fs.readFileSync('sfx.json', 'utf8'));

const L = new Float32Array(N), R = new Float32Array(N);       // mix asciutto
const VL = new Float32Array(N), VR = new Float32Array(N);     // mandata al riverbero
const PL = new Float32Array(N), PR = new Float32Array(N);     // pad grezzo (filtrato dopo)
const BS = new Float32Array(N);                               // basso (prima del sidechain)
const mtof = m => 440 * Math.pow(2, (m - 69) / 12);
let seed = 1234567;
const noise = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 2147483648 - 1; };
const has = (t, a, b) => t >= a && t < b;
const clamp = (x, a = 0, b = 1) => x < a ? a : x > b ? b : x;

function put(i, v, pan = 0, send = 0) {
  if (i < 0 || i >= N) return;
  const gl = Math.cos((pan + 1) * Math.PI / 4) * 1.414, gr = Math.sin((pan + 1) * Math.PI / 4) * 1.414;
  L[i] += v * gl; R[i] += v * gr;
  if (send) { VL[i] += v * gl * send; VR[i] += v * gr * send; }
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
const CHORDS = [ // [note del pad, fondamentale del basso, note dell'arpeggio]
  { pad: [59, 62, 66, 69], bass: 47, arp: [71, 74, 78, 81] },  // Bm7
  { pad: [55, 59, 62, 66], bass: 43, arp: [67, 71, 74, 78] },  // Gmaj7
  { pad: [57, 62, 64, 66], bass: 50, arp: [69, 74, 76, 78] },  // Dadd9
  { pad: [57, 61, 64, 69], bass: 45, arp: [69, 73, 76, 81] },  // A
];
const chordAt = t => CHORDS[Math.floor(t / 2) % 4];
// finale: progressione più calma e che si risolve (Dmaj7 – Gmaj7 – Bm9 – A – Dmaj7), pianoforte e campanelli
const CLOSE = [
  { pad: [62, 66, 69, 73], bass: 38, arp: [62, 66, 69, 73, 74, 78] },
  { pad: [55, 59, 62, 66], bass: 43, arp: [67, 71, 74, 78, 79, 83] },
  { pad: [59, 62, 66, 69], bass: 47, arp: [66, 71, 74, 78, 81, 83] },
  { pad: [57, 61, 64, 69], bass: 45, arp: [64, 69, 73, 76, 81, 85] },
];
const CLOSE_SEQ = [0, 1, 2, 3, 0];
const closeAt = t => CLOSE[CLOSE_SEQ[Math.min(4, Math.floor((t - FINALE) / 2))]];
const chordFor = t => t >= FINALE ? closeAt(t) : chordAt(t);

// ---------------- arrangiamento ----------------
const GROOVE = t => has(t, CIRC, ASSIST) || has(t, CAL, FINALE - .25);
const kickOn = t => GROOVE(t);
const pulseOn = t => has(t, 4, 10.4);                 // battito sordo nel caos iniziale
const clapOn = t => GROOVE(t) || has(t, ASSIST + 4, CAL);
const hatOn = t => has(t, 2, 10.4) || GROOVE(t) || has(t, ASSIST + 2, CAL);
const arpOn = t => has(t, DROP, FINALE - .25);
const arpHi = t => has(t, SEATS, FINALE - .25);
const bassMode = t => GROOVE(t) ? 'pump' : (has(t, DROP, CIRC) || has(t, ASSIST, CAL) || has(t, FINALE, DUR - 2) ? 'long' : null);
function padCut(t) {
  if (t < 12) return 450 + 250 * t / 12;
  if (t < 16) return 700 + 2600 * Math.pow((t - 12) / 4, 2);
  if (has(t, ASSIST, CAL)) return 1800;
  if (has(t, BUILD, FINALE)) return 2200 + 2500 * (t - BUILD) / (FINALE - BUILD);
  return 2600;
}
function padLvl(t) {
  if (t < 12) return .55;
  if (has(t, 15.75, 16)) return 0;
  if (has(t, FINALE - .25, FINALE)) return 0;
  return .8;
}

// ---------------- strumenti ----------------
function kick(t0, amp = 1, soft = false) {
  const i0 = Math.round(t0 * SR), len = Math.round(.5 * SR);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const f = (soft ? 38 : 45) + (soft ? 60 : 110) * Math.exp(-t * 28);
    ph += 2 * Math.PI * f / SR;
    let v = Math.sin(ph) * Math.exp(-t * (soft ? 7 : 6.5));
    if (!soft) v += noise() * .25 * Math.exp(-t * 300);
    put(i0 + k, Math.tanh(v * 1.6) * .9 * amp);
  }
}
function clap(t0, amp = 1) {
  const i0 = Math.round(t0 * SR), len = Math.round(.35 * SR), bp = biquad('bp', 1300, .9), hp = biquad('hp', 600);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let env = Math.exp(-t * 18);
    for (const o of [0, .011, .022]) if (t >= o) env += .7 * Math.exp(-(t - o) * 160);
    const v = hp.p(bp.p(noise())) * env;
    put(i0 + k, v * .9 * amp, 0, .35);
  }
}
function hat(t0, amp = 1, open = false) {
  const i0 = Math.round(t0 * SR), len = Math.round((open ? .22 : .06) * SR), hp = biquad('hp', 7500, .8);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    put(i0 + k, hp.p(noise()) * Math.exp(-t * (open ? 18 : 70)) * .35 * amp, .25, .1);
  }
}
function pluck(t0, midi, amp, pan) {
  const i0 = Math.round(t0 * SR), len = Math.round(.42 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let v = 0;
    for (let h = 1; h <= 6; h++) v += Math.sin(2 * Math.PI * f * h * t) / h * Math.exp(-t * (7 + 7 * h));
    v *= Math.min(1, t / .003);
    put(i0 + k, v * amp, pan, .4);
  }
}
function bassNote(t0, dur, midi, amp) {
  const i0 = Math.round(t0 * SR), len = Math.round((dur + .08) * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const env = Math.min(1, t / .006) * (t < dur ? 1 : Math.exp(-(t - dur) * 60)) * (dur > .5 ? Math.exp(-t * .5) : Math.exp(-t * 5));
    const v = Math.tanh((Math.sin(2 * Math.PI * f * t) + .45 * Math.sin(4 * Math.PI * f * t) + .8 * Math.sin(Math.PI * f * t)) * 1.3);
    const i = i0 + k; if (i < N) BS[i] += v * env * amp;
  }
}
function bell(t0, midi, amp, pan = 0, decay = 4) {
  const i0 = Math.round(t0 * SR), len = Math.round(1.2 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    const v = (Math.sin(2 * Math.PI * f * t + 1.2 * Math.sin(2 * Math.PI * f * 2.0 * t) * Math.exp(-t * 6)) + .3 * Math.sin(2 * Math.PI * f * 3 * t) * Math.exp(-t * 9))
      * Math.exp(-t * decay) * Math.min(1, t / .002);
    put(i0 + k, v * amp, pan, .45);
  }
}
function sweep(t0, t1, f0, f1, amp, shape = 'up', Q = 2) {
  // rumore filtrato con frequenza che sale (riser) o sale e scende (whoosh)
  const i0 = Math.round(t0 * SR), len = Math.round((t1 - t0) * SR), bp = biquad('bp', f0, Q);
  for (let k = 0; k < len; k++) {
    const p = k / len;
    if (k % 32 === 0) bp.set(shape === 'up' ? f0 * Math.pow(f1 / f0, p) : f0 * Math.pow(f1 / f0, Math.sin(p * Math.PI)), Q);
    const env = shape === 'up' ? Math.pow(p, 2.2) : Math.pow(Math.sin(p * Math.PI), 2);
    put(i0 + k, bp.p(noise()) * env * amp, shape === 'up' ? 0 : (p - .5) * 1.2, .5);
  }
}
function impact(t0) {
  const i0 = Math.round(t0 * SR), len = Math.round(3.2 * SR), hp = biquad('hp', 2500);
  let ph = 0;
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    ph += 2 * Math.PI * (32 + 50 * Math.exp(-t * 6)) / SR;
    const boom = Math.sin(ph) * Math.exp(-t * 1.6);
    const crash = hp.p(noise()) * Math.exp(-t * 1.4) * .45;
    put(i0 + k, Math.tanh(boom * 1.4) * .95, 0, .2);
    put(i0 + k, crash, 0, .6);
  }
  // piatto al contrario prima dell'impatto
  const pre = Math.round(1.6 * SR), hp2 = biquad('hp', 4000);
  for (let k = 0; k < pre; k++) put(i0 - pre + k, hp2.p(noise()) * Math.pow(k / pre, 3) * .4, 0, .3);
}
function click(t0, f, amp, len = .012) {
  const i0 = Math.round(t0 * SR), n = Math.round(len * SR);
  for (let k = 0; k < n; k++) { const t = k / SR; put(i0 + k, (Math.sin(2 * Math.PI * f * t) * .6 + noise() * .4) * Math.exp(-t / len * 5) * amp, .1, .15); }
}
function blip(t0, amp = .22) {
  const i0 = Math.round(t0 * SR), n = Math.round(.16 * SR);
  for (let k = 0; k < n; k++) { const t = k / SR; const f = 900 + 700 * Math.min(1, t / .03);
    put(i0 + k, Math.sin(2 * Math.PI * f * t) * Math.exp(-t * 32) * amp, 0, .35); }
}

// ---------------- scrittura degli eventi ----------------
const beat = .5;
const kicks = [];
for (let b = 0; b * beat < DUR; b++) {
  const t = b * beat;
  if (kickOn(t)) { kick(t); kicks.push(t); }
  else if (pulseOn(t)) kick(t, .25 + .45 * (t - 4) / 6.4, true);
  if (clapOn(t) && b % 2 === 1) clap(t, has(t, ASSIST + 4, CAL) ? .55 : 1);
  if (hatOn(t)) {
    const intro = t < 12;
    hat(t + beat / 2, intro ? .35 : 1, !intro && b % 4 === 3);
    if (!intro || t > 6) { hat(t + beat / 4, intro ? .2 : .45); hat(t + 3 * beat / 4, intro ? .2 : .45); }
  }
}
// rullante di passaggio prima del calendario e crescendo prima del finale
for (let k = 0; k < 4; k++) clap(CAL - 1 + k * .25, .5 + k * .12);
for (let t = FINALE - 2.5, step = .25; t < FINALE - .3; t += step, step = Math.max(.0625, step * .9)) clap(t, .35 + .6 * (t - FINALE + 2.5) / 2.2);

// arpeggio a sedicesimi
const ARP_PATTERN = [0, 1, 2, 3, 2, 1, 3, 1];
for (let s = 0; s * .125 < DUR; s++) {
  const t = s * .125;
  if (!arpOn(t)) continue;
  const ch = chordAt(t), n = ch.arp[ARP_PATTERN[s % 8]];
  const fadeIn = has(t, DROP, CIRC) ? .6 + .4 * (t - DROP) / (CIRC - DROP) : 1;
  const outro = t >= FINALE ? Math.max(0, 1 - (t - FINALE) / (DUR - FINALE - 1)) : 1;
  const quiet = has(t, ASSIST, CAL) ? .8 : 1;
  pluck(t, n, .16 * fadeIn * outro * quiet, s % 2 ? .35 : -.35);
  if (arpHi(t) && s % 2 === 0) pluck(t + .0625, n + 12, .06, s % 4 ? -.6 : .6);
}
// finale: pianoforte che sale e scende a ottavi, con una melodia di campanelli sopra
function piano(t0, midi, amp, pan) {
  const i0 = Math.round(t0 * SR), len = Math.round(1.6 * SR), f = mtof(midi);
  for (let k = 0; k < len; k++) {
    const t = k / SR;
    let v = 0;
    for (let h = 1; h <= 5; h++) v += Math.sin(2 * Math.PI * f * h * t) / (h * h) * Math.exp(-t * (1.6 + 1.8 * h));
    v *= Math.min(1, t / .004);
    put(i0 + k, v * amp, pan, .5);
  }
}
const UPDOWN = [0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 3, 2];
for (let s = 0; FINALE + s * .25 < DUR - .5; s++) {
  const t = FINALE + .5 + s * .25 - .5, ch = closeAt(t), fade = Math.max(0, 1 - (t - FINALE) / (DUR - FINALE + 1));
  if (t < FINALE + .75) continue;
  piano(t, ch.arp[UPDOWN[s % UPDOWN.length]], .2 * (.35 + .65 * fade), (s % 2 ? .3 : -.3));
}
[[.9, 86], [2.9, 83], [4.4, 81], [5.4, 78], [6.9, 79], [8.0, 81], [9.0, 74]].forEach(([o, m]) => bell(FINALE + o, m, .13, 0, 2.2));
// basso
for (let e = 0; e * .25 < DUR; e++) {
  const t = e * .25, m = bassMode(t), root = chordFor(t).bass;
  if (m === 'pump') bassNote(t, .2, root, e % 2 ? .5 : .32);
  else if (m === 'long' && e % 8 === 0) bassNote(t, 1.9, root, .24 * (t >= FINALE ? Math.max(0, 1 - (t - FINALE) / (DUR - FINALE - 2)) : 1));
}
// pad: supersaw (5 voci per nota) — scritto grezzo, filtrato dopo con taglio variabile
for (let bar = 0; bar * 2 < DUR; bar++) {
  const t0 = bar * 2, ch = t0 >= FINALE ? closeAt(t0) : CHORDS[bar % 4];
  if (t0 >= DUR - 1) break;
  const i0 = Math.round(t0 * SR), len = Math.round(3.3 * SR);
  ch.pad.forEach((m, ni) => {
    const det = [-14, -7, 0, 7, 14];
    det.forEach((c, vi) => {
      const f = mtof(m) * Math.pow(2, c / 1200); let ph = (ni * .37 + vi * .21) % 1;
      const pan = (vi - 2) / 2.5;
      for (let k = 0; k < len; k++) {
        const t = k / SR, i = i0 + k; if (i >= N) break;
        ph += f / SR; if (ph >= 1) ph -= 1;
        // dente di sega polyBLEP
        const dt = f / SR; let v = 2 * ph - 1;
        if (ph < dt) { const x = ph / dt; v -= x + x - x * x - 1; } else if (ph > 1 - dt) { const x = (ph - 1) / dt; v -= x * x + x + x + 1; }
        const env = Math.min(1, t / .35) * (t < 2 ? 1 : Math.exp(-(t - 2) * 3.2));
        const g = v * env * .022;
        PL[i] += g * (1 - pan) * .5; PR[i] += g * (1 + pan) * .5;
      }
    });
  });
}

// effetti sincronizzati con le scene
sweep(12, 15.85, 250, 7000, .5, 'up', 1.5);
sweep(FINALE - 4.2, FINALE - .15, 250, 7000, .45, 'up', 1.5);
for (const { t, type } of SFX) {
  switch (type) {
    case 'ping': bell(t, 88, .16, -.3); bell(t + .08, 93, .12, -.3); break;
    case 'ping2': bell(t, 81, .13, .3, 9); bell(t + .06, 76, .1, .3, 9); break;
    case 'notify': bell(t, 84, .14, 0); bell(t + .12, 88, .14, 0); bell(t + .24, 91, .14, 0); break;
    case 'success': bell(t, 86, .12, 0); bell(t + .1, 93, .12, 0); break;
    case 'impact': impact(t); break;
    case 'blip': blip(t); break;
    case 'key': click(t, 3200, .09, .008); break;
    case 'tick': click(t, 1800, .1, .01); break;
    case 'stamp': kick(t, .35, true); click(t, 900, .18, .03); break;
    case 'send': sweep(t - .1, t + .35, 600, 4000, .25, 'arc', 1.2); break;
    case 'whooshS': sweep(t - .2, t + .5, 400, 3000, .3, 'arc', 1.2); break;
    case 'whoosh': sweep(t - .2, t + .7, 300, 5000, .42, 'arc', 1.1); break;
  }
}

// ---------------- mix ----------------
// sidechain: pad e basso si abbassano a ogni cassa (effetto "pompa")
const duck = new Float32Array(N).fill(1);
for (const tk of kicks) {
  const i0 = Math.round(tk * SR), n = Math.round(.42 * SR);
  for (let k = 0; k < n && i0 + k < N; k++) duck[i0 + k] = Math.min(duck[i0 + k], 1 - .62 * Math.exp(-k / SR / .11));
}
{
  const fl = [biquad('lp', 800, .6), biquad('lp', 800, .6)], fr = [biquad('lp', 800, .6), biquad('lp', 800, .6)];
  for (let i = 0; i < N; i++) {
    const t = i / SR;
    if (i % 64 === 0) { const c = padCut(t); fl.forEach(f => f.set(c, .6)); fr.forEach(f => f.set(c, .6)); }
    const lv = padLvl(t) * duck[i];
    const l = fl[1].p(fl[0].p(PL[i])) * lv, r = fr[1].p(fr[0].p(PR[i])) * lv;
    L[i] += l; R[i] += r; VL[i] += l * .35; VR[i] += r * .35;
  }
  const hp = biquad('hp', 35);
  for (let i = 0; i < N; i++) { const v = hp.p(BS[i]) * duck[i] * .55; L[i] += v; R[i] += v; }
}
// riverbero (Schroeder/Freeverb semplificato)
function reverb(inp, offset) {
  const combs = [1557, 1617, 1491, 1422, 1277, 1356].map(d => ({ b: new Float32Array(d + offset), i: 0, s: 0 }));
  const aps = [556, 441, 341].map(d => ({ b: new Float32Array(d + offset), i: 0 }));
  const out = new Float32Array(N);
  for (let n = 0; n < N; n++) {
    let acc = 0; const x = inp[n] * .3;
    for (const c of combs) {
      const y = c.b[c.i]; c.s = y * .7 + c.s * .3; c.b[c.i] = x + c.s * .86; c.i = (c.i + 1) % c.b.length; acc += y;
    }
    for (const a of aps) { const bo = a.b[a.i]; const y = -acc + bo; a.b[a.i] = acc + bo * .5; a.i = (a.i + 1) % a.b.length; acc = y; }
    out[n] = acc;
  }
  return out;
}
const RL = reverb(VL, 0), RR = reverb(VR, 23);
for (let i = 0; i < N; i++) { L[i] += RL[i] * .32; R[i] += RR[i] * .32; }

// mastering: dissolvenze, saturazione morbida, normalizzazione
let peak = 0;
for (let i = 0; i < N; i++) {
  const t = i / SR;
  const g = Math.min(1, t / .8) * (t > DUR - 2.5 ? Math.max(0, 1 - (t - DUR + 2.5) / 2.4) : 1);
  L[i] *= g; R[i] *= g;
  peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
}
const pre = 1.4 / peak;
const buf = Buffer.alloc(44 + N * 4);
let p2 = 0; const tmp = new Float32Array(N * 2);
for (let i = 0; i < N; i++) { tmp[2 * i] = Math.tanh(L[i] * pre); tmp[2 * i + 1] = Math.tanh(R[i] * pre); p2 = Math.max(p2, Math.abs(tmp[2 * i]), Math.abs(tmp[2 * i + 1])); }
const norm = .93 / p2;
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let i = 0; i < N * 2; i++) buf.writeInt16LE(Math.round(clamp(tmp[i] * norm, -1, 1) * 32767), 44 + i * 2);
fs.mkdirSync('build', { recursive: true });
fs.writeFileSync('build/music.wav', buf);
console.log('build/music.wav scritto, picco pre-saturazione', peak.toFixed(2));
