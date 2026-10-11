// Genera icons.js con le icone Lucide usate nel video (inline, nessuna richiesta di rete).
// Contiene anche quelle che servono a ../video-trailer/common.js, che questa pagina riusa.
const fs = require('fs');
const names = [
  // usate da common.js
  'bell', 'bell-ring', 'file-text', 'sparkles', 'calendar', 'calendar-plus', 'message-circle', 'users', 'search',
  'send', 'check', 'lock', 'shield-check', 'eye-off', 'smartphone', 'tablet', 'palette', 'moon', 'sun', 'zap', 'thumbs-up',
  'message-square', 'house', 'armchair', 'chart-column', 'trophy', 'arrow-left', 'user', 'download', 'qr-code', 'layers',
  'droplet', 'square', 'heart', 'graduation-cap', 'clock', 'circle-check', 'flame', 'vote', 'scan-line', 'wand-sparkles',
  // nuove
  'bus', 'globe', 'share', 'plus', 'square-plus', 'wifi-off', 'link', 'history', 'triangle-alert', 'external-link',
  'chevron-left', 'chevron-right', 'x', 'monitor', 'copy', 'bookmark', 'book-open', 'ellipsis', 'star', 'rotate-cw',
  'upload', 'map', 'mail', 'camera', 'music', 'cloud-sun', 'settings', 'notebook-pen', 'phone', 'image', 'calculator',
  'compass', 'arrow-up', 'file-up', 'badge-check',
];
const out = {};
for (const n of names) {
  const svg = fs.readFileSync(`node_modules/lucide-static/icons/${n}.svg`, 'utf8');
  out[n] = svg.replace(/<!--[\s\S]*?-->/g, '').match(/<svg[^>]*>([\s\S]*)<\/svg>/)[1].replace(/\s+/g, ' ').trim();
}
fs.writeFileSync('icons.js', 'window.ICONS=' + JSON.stringify(out) + ';\n');
console.log('icone:', names.length);
