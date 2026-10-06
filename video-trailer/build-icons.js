// Genera icons.js con le icone Lucide usate nel trailer (inline, nessuna richiesta di rete).
const fs = require('fs');
const names = ['bell', 'bell-ring', 'file-text', 'sparkles', 'calendar', 'calendar-plus', 'message-circle', 'users', 'search',
  'send', 'check', 'lock', 'shield-check', 'eye-off', 'smartphone', 'tablet', 'palette', 'moon', 'sun', 'zap', 'thumbs-up',
  'message-square', 'house', 'armchair', 'chart-column', 'trophy', 'arrow-left', 'user', 'download', 'qr-code', 'layers',
  'droplet', 'square', 'heart', 'graduation-cap', 'clock', 'circle-check', 'flame', 'vote', 'scan-line', 'wand-sparkles'];
const out = {};
for (const n of names) {
  const svg = fs.readFileSync(`node_modules/lucide-static/icons/${n}.svg`, 'utf8');
  out[n] = svg.replace(/<!--[\s\S]*?-->/g, '').match(/<svg[^>]*>([\s\S]*)<\/svg>/)[1].replace(/\s+/g, ' ').trim();
}
fs.writeFileSync('icons.js', 'window.ICONS=' + JSON.stringify(out) + ';\n');
console.log('icone:', names.length);
