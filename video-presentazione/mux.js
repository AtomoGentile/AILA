// Unisce video muto e musica nel file finale.
const ffmpeg = require('ffmpeg-static');
const { execFileSync } = require('child_process');
const D = process.env.OUT || 'build';
const OUT = process.env.HYPE ? 'AILA_presentazione_hype.mp4' : 'AILA_presentazione.mp4';
execFileSync(ffmpeg, ['-y', '-loglevel', 'error', '-i', `${D}/video.mp4`, '-i', `${D}/music.wav`,
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', OUT], { stdio: 'inherit' });
console.log(OUT, 'pronto');
