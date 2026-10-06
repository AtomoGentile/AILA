// Genera qr.js con il QR code della pagina di download (Releases di GitHub), disegnato come SVG nel finale.
const QR = require('qrcode');
const fs = require('fs');
const URL = 'https://github.com/AtomoGentile/AILA/releases/latest';
const qr = QR.create(URL, { errorCorrectionLevel: 'M' });
const n = qr.modules.size, d = qr.modules.data;
const cells = [];
for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (d[y * n + x]) cells.push([x, y]);
fs.writeFileSync('qr.js', `window.QR=${JSON.stringify({ url: URL, n, cells })};\n`);
console.log('QR', n + 'x' + n, URL);
