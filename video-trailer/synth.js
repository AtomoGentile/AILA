// Sintetizzatore condiviso dalle colonne sonore dei video (trailer e intro): buffer di mix, filtri,
// strumenti, ritardo, riverbero e mastering. Tutto è generato da zero, senza campioni esterni.
//   const S = require('./synth')(durataInSecondi);  poi S.kick(t), S.bell(t, nota, ...), S.finish('file.wav', ...)
const fs = require('fs');
module.exports = function createSynth(DUR) {
  const SR = 44100, N = Math.ceil(SR * DUR);
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


  // ritardo ping-pong sulla mandata DL/DR (d = ritardo in secondi)
  function pingPong(dsec, fb = .38, wet = .45, rev = .2) {
    const d = Math.round(dsec * SR);
    const bl = new Float32Array(d), br = new Float32Array(d); let p = 0;
    for (let i = 0; i < N; i++) {
      const ol = bl[p], or = br[p];
      bl[p] = DR[i] + or * fb; br[p] = DL[i] + ol * fb; p = (p + 1) % d;
      L[i] += ol * wet; R[i] += or * wet; VL[i] += ol * rev; VR[i] += or * rev;
    }
  }
  // riverbero (Freeverb semplificato) della mandata VL/VR
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
  // riverbero, dissolvenze, saturazione morbida, normalizzazione e scrittura del WAV a 16 bit
  function finish(file, { revWet = .3, fadeIn = .3, fadeOut = 1.6, drive = 1.6 } = {}) {
    const RL = reverb(VL, 0), RRv = reverb(VR, 23);
    for (let i = 0; i < N; i++) { L[i] += RL[i] * revWet; R[i] += RRv[i] * revWet; }
    let peak = 0;
    for (let i = 0; i < N; i++) {
      const t = i / SR;
      const g = Math.min(1, t / fadeIn) * (t > DUR - fadeOut ? Math.max(0, 1 - (t - DUR + fadeOut) / (fadeOut - .05)) : 1);
      L[i] *= g; R[i] *= g;
      peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
    }
    const pre = drive / peak;
    const tmp = new Float32Array(N * 2); let p2 = 0;
    for (let i = 0; i < N; i++) { tmp[2 * i] = Math.tanh(L[i] * pre); tmp[2 * i + 1] = Math.tanh(R[i] * pre); p2 = Math.max(p2, Math.abs(tmp[2 * i]), Math.abs(tmp[2 * i + 1])); }
    const norm = .94 / p2;
    const buf = Buffer.alloc(44 + N * 4);
    buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8); buf.write('fmt ', 12);
    buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22); buf.writeUInt32LE(SR, 24);
    buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34); buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
    for (let i = 0; i < N * 2; i++) buf.writeInt16LE(Math.round(clamp(tmp[i] * norm, -1, 1) * 32767), 44 + i * 2);
    fs.mkdirSync(require('path').dirname(file), { recursive: true });
    fs.writeFileSync(file, buf);
    console.log(file, 'scritto, picco pre-saturazione', peak.toFixed(2));
  }

  return { SR, N, L, R, VL, VR, DL, DR, PL, PR, BS, mtof, noise, has, clamp, put, biquad,
    kick, clap, hat, crash, pluck, bassNote, leadNote, bell, piano, sweep, boom, impact, click, blip, buzz, pingPong, finish };
};
