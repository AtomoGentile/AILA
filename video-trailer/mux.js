// Unisce video muto e musica nel file finale.
const { execFileSync } = require('child_process');
const FFMPEG = process.env.FFMPEG || 'ffmpeg';
execFileSync(FFMPEG, ['-y', '-loglevel', 'error', '-i', 'build/video.mp4', '-i', 'build/music.wav',
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', 'AILA_trailer.mp4'], { stdio: 'inherit' });
console.log('AILA_trailer.mp4 pronto');
