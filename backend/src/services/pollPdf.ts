import type { Env } from '../types';
import { PollCalculationError } from './pollAssignments';
import { fullName } from '../names';
import { POLL_PDF_FONT_BASE64, POLL_PDF_GLYPHS, POLL_PDF_FONT_BOX, POLL_PDF_ASCENT, POLL_PDF_DESCENT } from '../assets/pollPdfFont';

export interface PollPdfData {
  subject: string;
  classLabel: string;
  audience: string | null;
  slots: Array<{ date: string; students: Array<{ firstName: string; lastName: string }> }>;
}

export async function readPollPdfData(env: Env, gridId: string, classId: string): Promise<PollPdfData | null> {
  const row = await env.DB.prepare(`SELECT json_object(
    'subject', g.subject, 'classLabel', c.label, 'audience', g.audience_json,
    'slots', json((SELECT json_group_array(json(s.row)) FROM (
      SELECT json_object('date', s.slot_date, 'students', json((
        SELECT json_group_array(json_object('firstName', u.first_name, 'lastName', u.last_name)) FROM (
          SELECT u.first_name, u.last_name FROM interrogation_assignments a JOIN users u ON u.id=a.student_id
          WHERE a.slot_id=s.id AND u.class_id=g.class_id ORDER BY lower(u.last_name), lower(u.first_name), u.id
        ) u
      ))) AS row FROM interrogation_slots s WHERE s.grid_id=g.id ORDER BY s.slot_date,s.id
    ) s))
  ) AS data FROM interrogation_grids g JOIN classes c ON c.id=g.class_id WHERE g.id=? AND g.class_id=?`)
    .bind(gridId, classId).first<{ data: string }>();
  return row ? JSON.parse(row.data) : null;
}

// Font e metriche sono preparati in fase di sviluppo: l'export non analizza o ricomprime
// font durante la richiesta HTTP. Resta leggero anche sui Worker con un budget CPU ridotto.
const encoder = new TextEncoder();
const encode = (text: string) => encoder.encode(text);
const glyphs = Object.entries(POLL_PDF_GLYPHS);
const unicodeByGlyph = new Map<number, number>();
for (const [code, [gid]] of glyphs) if (!unicodeByGlyph.has(gid)) unicodeByGlyph.set(gid, Number(code));
const hex = (n: number) => n.toString(16).padStart(4, '0');
const unicodeMap = `/CIDInit /ProcSet findresource begin 12 dict begin begincmap
/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
/CMapName /AILAUnicode def /CMapType 2 def
1 begincodespacerange <0000> <FFFF> endcodespacerange
${Array.from(unicodeByGlyph).reduce<string[][]>((chunks, [gid, cp], i) => {
  const group = Math.floor(i / 100);
  (chunks[group] ??= []).push(`<${hex(gid)}> <${hex(cp)}>`);
  return chunks;
}, [] as string[][]).map(chunk => `${chunk.length} beginbfchar\n${chunk.join('\n')}\nendbfchar`).join('\n')}
endcmap CMapName currentdict /CMap defineresource pop end end`;
let fontBytes: Uint8Array | undefined;
function fontData() {
  if (!fontBytes) {
    const raw = atob(POLL_PDF_FONT_BASE64);
    fontBytes = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) fontBytes[i] = raw.charCodeAt(i);
  }
  return fontBytes;
}
function glyph(char: string) {
  const g = POLL_PDF_GLYPHS[char.codePointAt(0)!];
  if (!g) throw new PollCalculationError('Uno dei nomi o il titolo contiene un carattere non supportato dal PDF.');
  return g;
}
const width = (text: string, size: number) => Array.from(text).reduce((sum, c) => sum + glyph(c)[1] * size / 1000, 0);
const textHex = (text: string) => Array.from(text).map(c => hex(glyph(c)[0])).join('');
function wrap(text: string, size: number, maxWidth: number): string[] {
  const lines: string[] = [];
  let line = '';
  for (const word of text.replace(/\s+/gu, ' ').trim().split(' ')) {
    if (line && width(`${line} ${word}`, size) <= maxWidth) { line += ` ${word}`; continue; }
    if (line) { lines.push(line); line = ''; }
    for (const letter of word) {
      if (line && width(line + letter, size) > maxWidth) { lines.push(line); line = ''; }
      line += letter;
    }
  }
  if (line) lines.push(line);
  return lines.length ? lines : [''];
}
const dateLabel = (date: string) => { const [y, m, d] = date.split('-'); return `${d}/${m}/${y}`; };
const joinBytes = (parts: Uint8Array[]) => {
  const output = new Uint8Array(parts.reduce((sum, p) => sum + p.length, 0));
  let offset = 0; for (const part of parts) { output.set(part, offset); offset += part.length; }
  return output;
};
const stream = (bytes: Uint8Array, extra = '') => joinBytes([encode(`<< /Length ${bytes.length} ${extra} >>\nstream\n`), bytes, encode('\nendstream')]);
const metadata = (text: string) => 'FEFF' + Array.from(text).map(c => {
  const cp = c.codePointAt(0)!;
  if (cp <= 0xffff) return hex(cp);
  return hex(0xd800 + ((cp - 0x10000) >> 10)) + hex(0xdc00 + ((cp - 0x10000) & 1023));
}).join('');

/** A4, testo selezionabile, font incorporato e intestazioni ripetute su ogni pagina. */
export async function buildPollPdf(data: PollPdfData): Promise<Uint8Array> {
  const pages: string[][] = [];
  let page: string[], y = 0, pageTop = 0, row = 0;
  const text = (value: string, x: number, top: number, size: number, color = '0.06 0.09 0.16') =>
    page.push(`BT /F1 ${size} Tf ${color} rg 1 0 0 1 ${x} ${top} Tm <${textHex(value)}> Tj ET`);
  const rect = (x: number, top: number, w: number, h: number, color: string) => page.push(`${color} rg ${x} ${top} ${w} ${h} re f`);
  function newPage() {
    page = []; pages.push(page);
    text('AILA · Calendario interrogazioni', 40, 800, 10, '0.15 0.29 0.65');
    y = 771;
    for (const line of wrap(data.subject, 18, 515)) { text(line, 40, y, 18); y -= 23; }
    for (const line of wrap(`Classe ${data.classLabel} · Calendario pubblicato`, 10, 515)) { text(line, 40, y - 2, 10, '0.39 0.45 0.55'); y -= 14; }
    y -= 19; rect(40, y - 8, 515, 25, '0.93 0.95 0.99');
    text('Data', 49, y, 11, '0.15 0.29 0.65'); text('Studenti', 165, y, 11, '0.15 0.29 0.65'); y -= 30; pageTop = y;
  }
  newPage();
  // Una riga per data: unisce anche eventuali slot distinti dello stesso giorno.
  const byDate = new Map<string, PollPdfData['slots'][number]['students']>();
  for (const slot of data.slots) byDate.set(slot.date, [...(byDate.get(slot.date) ?? []), ...slot.students]);
  for (const [date, students] of [...byDate].sort(([a], [b]) => a.localeCompare(b))) {
    const names = students.length ? [...students].sort((a, b) => a.lastName.localeCompare(b.lastName, 'it') || a.firstName.localeCompare(b.firstName, 'it'))
      .map(s => fullName(s.firstName, s.lastName)) : ['Nessuno studente assegnato'];
    const lines = names.flatMap(name => wrap(name, 11, 370).map((value, index) => ({ value, x: index === 0 ? 165 : 175 })));
    const groupHeight = Math.max(30, lines.length * 16 + 14);
    // Mantiene insieme i gruppi che possono stare interamente su una pagina.
    if (groupHeight <= pageTop - 55 && y - groupHeight < 55) newPage();
    let offset = 0;
    while (offset < lines.length) {
      let available = Math.floor((y - 55 - 14) / 16);
      if (available < 1 || (offset > 0 && y - 55 < 40)) { newPage(); available = Math.floor((y - 55 - 14) / 16); }
      const chunk = lines.slice(offset, offset + available);
      const height = Math.max(offset > 0 ? 40 : 30, chunk.length * 16 + 14);
      if (row % 2 === 1) rect(40, y - height + 10, 515, height, '0.98 0.985 0.995');
      text(dateLabel(date), 49, y, 11);
      if (offset > 0) text('(continua)', 49, y - 15, 9, '0.39 0.45 0.55');
      chunk.forEach((line, i) => text(line.value, line.x, y - i * 16, 11));
      y -= height; offset += chunk.length;
      if (offset < lines.length) newPage();
    }
    row++;
  }
  const exportedAt = new Date().toISOString().slice(0, 10);
  pages.forEach((p, i) => {
    page = p; rect(40, 42, 515, 0.5, '0.88 0.9 0.94');
    text(`Esportato il ${dateLabel(exportedAt)}`, 40, 28, 9, '0.39 0.45 0.55');
    text(`${i + 1} / ${pages.length}`, 520, 28, 9, '0.39 0.45 0.55');
  });
  const widths = new Map<number, number>(glyphs.map(([, [gid, w]]) => [gid, w]));
  const objects: Uint8Array[] = [
    encode('<< /Type /Catalog /Pages 2 0 R >>'),
    encode(`<< /Type /Pages /Count ${pages.length} /Kids [${pages.map((_, i) => `${9 + i * 2} 0 R`).join(' ')}] >>`),
    encode('<< /Type /Font /Subtype /Type0 /BaseFont /LiberationSans /Encoding /Identity-H /DescendantFonts [4 0 R] /ToUnicode 7 0 R >>'),
    encode(`<< /Type /Font /Subtype /CIDFontType2 /BaseFont /LiberationSans /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /FontDescriptor 5 0 R /CIDToGIDMap /Identity /DW 1000 /W [${[...widths].map(([gid, w]) => `${gid} [${w}]`).join(' ')}] >>`),
    encode(`<< /Type /FontDescriptor /FontName /LiberationSans /Flags 32 /FontBBox [${POLL_PDF_FONT_BOX.join(' ')}] /ItalicAngle 0 /Ascent ${POLL_PDF_ASCENT} /Descent ${POLL_PDF_DESCENT} /CapHeight ${POLL_PDF_ASCENT} /StemV 80 /FontFile2 6 0 R >>`),
    stream(fontData(), `/Length1 ${fontData().length}`), stream(encode(unicodeMap)),
    encode(`<< /Title <${metadata(`Calendario interrogazioni - ${data.subject}`)}> /Author (AILA) /Subject <${metadata(`Classe ${data.classLabel}`)}> >>`),
  ];
  pages.forEach((p, i) => {
    objects.push(encode(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${10 + i * 2} 0 R >>`));
    objects.push(stream(encode(p.join('\n'))));
  });
  const parts = [encode('%PDF-1.7\n'), new Uint8Array([37, 226, 227, 207, 211, 10])], offsets = [0];
  let position = parts.reduce((sum, p) => sum + p.length, 0);
  objects.forEach((object, i) => {
    offsets.push(position);
    const chunk = joinBytes([encode(`${i + 1} 0 obj\n`), object, encode('\nendobj\n')]); parts.push(chunk); position += chunk.length;
  });
  parts.push(encode(`xref\n0 ${offsets.length}\n0000000000 65535 f \n${offsets.slice(1).map(n => `${n.toString().padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${offsets.length} /Root 1 0 R /Info 8 0 R >>\nstartxref\n${position}\n%%EOF\n`));
  return joinBytes(parts);
}
