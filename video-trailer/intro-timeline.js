// Tempi dell'intro "presentazione della squadra": 144 BPM, una battuta = 1,667 s; dura quanto il trailer (~2:03).
// Usato sia dal browser (intro.js) sia da Node (intro-music.js).
(function (g) {
  const BPM = 144, BEAT = 60 / BPM, BAR = BEAT * 4;
  const S = { // inizio di ogni sezione, in battute
    OPEN: 0, DROP: 6, CARDS: 9, CARD_LEN: 6, DEVICES: 45, THEMES: 51, WALL: 57, TRUST: 61, OUT: 64, OUTRO: 69, END: 74,
  };
  const T = { BPM, BEAT, BAR, S, bar: n => n * BAR, DURATION: S.END * BAR };
  g.INTRO_TL = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
