// Unisce video muto e musica nel file finale.
//   node mux.js          -> AILA_trailer.mp4 (da build/)
//   node mux.js intro    -> AILA_intro.mp4 (da build/intro/)
const { execFileSync } = require('child_process');
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
const page = process.argv[2] || 'index';
const dir = page === 'index' ? 'build' : `build/${page}`, out = page === 'index' ? 'AILA_trailer.mp4' : `AILA_${page}.mp4`;
execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', `${dir}/video.mp4`, '-i', `${dir}/music.wav`,
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', out], { stdio: 'inherit' });
console.log(out, 'pronto');
