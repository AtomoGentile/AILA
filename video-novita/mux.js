// Unisce video muto e musica nel file finale: node mux.js -> AILA_novita.mp4 (da build/)
const { execFileSync } = require('child_process');
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', 'build/video.mp4', '-i', 'build/music.wav',
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', 'AILA_novita.mp4'], { stdio: 'inherit' });
console.log('AILA_novita.mp4 pronto');
