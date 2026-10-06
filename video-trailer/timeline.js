// Tempi del trailer: 128 BPM, una battuta (4/4) = 1,875 s. Ogni sezione inizia su una battuta, così i tagli
// cadono a tempo con la musica. Usato sia dal browser (video.js) sia da Node (music.js).
(function (g) {
  const BPM = 128, BEAT = 60 / BPM, BAR = BEAT * 4;
  const S = { // inizio di ogni sezione, in battute
    INTRO: 0, DROP: 8, FEAT: 12, BREAK: 36, DEVICES: 38, THEMES: 46, WALL: 52, TRUST: 56, FINALE: 59, END: 66,
  };
  const T = { BPM, BEAT, BAR, S, bar: n => n * BAR, beat: n => n * BEAT, DURATION: S.END * BAR };
  g.TIMELINE = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
