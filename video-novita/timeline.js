// Tempi del video: 96 BPM, una battuta (4/4) = 2,5 s. Ogni scena inizia su una battuta, così gli stacchi
// cadono a tempo con la musica. Usato sia dal browser (video.js) sia da Node (music.js).
(function (g) {
  const BPM = 96, BEAT = 60 / BPM, BAR = BEAT * 4;
  const S = { // inizio di ogni scena, in battute
    OPEN: 0, WEB: 3, BROWSER: 5, INSTALL: 10, BENTO: 15, GITA: 19, DOCS: 21, VERSIONS: 27, ASSISTANT: 31, FINALE: 36, END: 40,
  };
  const T = { BPM, BEAT, BAR, S, bar: n => n * BAR, beat: n => n * BEAT, DURATION: S.END * BAR };
  g.TIMELINE = T;
  if (typeof module !== 'undefined') module.exports = T;
})(typeof window !== 'undefined' ? window : globalThis);
