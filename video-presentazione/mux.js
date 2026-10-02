// Unisce video muto e musica nel file finale.
const ffmpeg = require('ffmpeg-static');
const { execFileSync } = require('child_process');
execFileSync(ffmpeg, ['-y', '-loglevel', 'error', '-i', 'build/video.mp4', '-i', 'build/music.wav',
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', 'AILA_presentazione.mp4'], { stdio: 'inherit' });
console.log('AILA_presentazione.mp4 pronto');
