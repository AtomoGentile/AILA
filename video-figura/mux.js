// Unisce il video muto e l'audio nel file finale:  node mux.js  -> AILA_figura.mp4
const { execFileSync } = require('child_process');
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', 'build/video.mp4', '-i', 'build/audio.wav',
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', 'AILA_figura.mp4'], { stdio: 'inherit' });
console.log('AILA_figura.mp4 pronto');
