"""Regenerate the embedded PDF font and metrics; requires fonttools. Run from the repository root."""
from pathlib import Path
from fontTools.ttLib import TTFont
import json,base64
src=Path('webApp/src/wasmJsMain/resources/pdfjs/standard_fonts/LiberationSans-Regular.ttf');f=TTFont(src);em=f['head'].unitsPerEm
chars={cp:[f.getGlyphID(name),round(f['hmtx'][name][0]*1000/em)] for cp,name in f.getBestCmap().items()}
box=[round(getattr(f['head'],key)*1000/em) for key in ['xMin','yMin','xMax','yMax']]
s='// Generated from the PDF.js Liberation Sans font already bundled with AILA.\n// License: LICENSE_LIBERATION.txt. Metrics use PDF units (1000/em).\n'
s+='export const POLL_PDF_FONT_BASE64 = "'+base64.b64encode(src.read_bytes()).decode()+'";\n'
s+='export const POLL_PDF_GLYPHS: Record<number, [number, number]> = '+json.dumps(chars,separators=(',',':'))+';\n'
s+='export const POLL_PDF_FONT_BOX = '+json.dumps(box)+';\n'
s+=f"export const POLL_PDF_ASCENT = {round(f['hhea'].ascent*1000/em)};\nexport const POLL_PDF_DESCENT = {round(f['hhea'].descent*1000/em)};\n"
Path('backend/src/assets/pollPdfFont.ts').write_text(s)
