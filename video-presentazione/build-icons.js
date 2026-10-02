// Genera icons.js con le icone Lucide usate nel video (inline, nessuna richiesta di rete).
const fs = require('fs');
const names = ['bell','bell-ring','file-text','server','sparkles','calendar-plus','calendar','message-circle','users',
  'graduation-cap','volume-2','ruler','refresh-cw','shield-check','lock','eye-off','thumbs-up','thumbs-down','smartphone',
  'tablet','globe','check','map-pin','send','ban','arrow-left','house','layout-list','user','message-square','search'];
const out = {};
for (const n of names) {
  const svg = fs.readFileSync(`node_modules/lucide-static/icons/${n}.svg`, 'utf8');
  out[n] = svg.replace(/<!--[\s\S]*?-->/g, '').match(/<svg[^>]*>([\s\S]*)<\/svg>/)[1].replace(/\s+/g, ' ').trim();
}
fs.writeFileSync('icons.js', 'window.ICONS=' + JSON.stringify(out) + ';\n');
console.log('icone:', names.length);
