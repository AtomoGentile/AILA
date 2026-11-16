"""Build AILA Dot, an original circular 5x7 display face (fonttools required)."""
from pathlib import Path
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
import math
import unicodedata

# Rows encoded as five bits; lowercase intentionally uses the same display capitals.
ROWS = {
    'A': [14,17,17,31,17,17,17], 'B': [30,17,17,30,17,17,30],
    'C': [14,17,16,16,16,17,14], 'D': [30,17,17,17,17,17,30],
    'E': [31,16,16,30,16,16,31], 'F': [31,16,16,30,16,16,16],
    'G': [14,17,16,23,17,17,15], 'H': [17,17,17,31,17,17,17],
    'I': [14,4,4,4,4,4,14], 'J': [7,2,2,2,2,18,12],
    'K': [17,18,20,24,20,18,17], 'L': [16,16,16,16,16,16,31],
    'M': [17,27,21,21,17,17,17], 'N': [17,25,25,21,19,19,17],
    'O': [14,17,17,17,17,17,14], 'P': [30,17,17,30,16,16,16],
    'Q': [14,17,17,17,21,18,13], 'R': [30,17,17,30,20,18,17],
    'S': [15,16,16,14,1,1,30], 'T': [31,4,4,4,4,4,4],
    'U': [17,17,17,17,17,17,14], 'V': [17,17,17,17,17,10,4],
    'W': [17,17,17,21,21,27,17], 'X': [17,17,10,4,10,17,17],
    'Y': [17,17,10,4,4,4,4], 'Z': [31,1,2,4,8,16,31],
    '0': [14,17,19,21,25,17,14], '1': [4,12,4,4,4,4,14],
    '2': [14,17,1,2,4,8,31], '3': [30,1,1,14,1,1,30],
    '4': [2,6,10,18,31,2,2], '5': [31,16,16,30,1,1,30],
    '6': [14,16,16,30,17,17,14], '7': [31,1,2,4,8,8,8],
    '8': [14,17,17,14,17,17,14], '9': [14,17,17,15,1,1,14],
    ' ': [0]*7, '.': [0,0,0,0,0,0,4], ',': [0,0,0,0,0,4,8],
    ':': [0,4,4,0,4,4,0], ';': [0,4,4,0,4,4,8],
    '-': [0,0,0,31,0,0,0], '/': [1,2,2,4,8,8,16],
    '(': [2,4,8,8,8,4,2], ')': [8,4,2,2,2,4,8],
    '+': [0,4,4,31,4,4,0], '!': [4,4,4,4,4,0,4],
    '?': [14,17,1,2,4,0,4], "'": [4,4,8,0,0,0,0],
    '%': [25,25,2,4,8,19,19], '&': [12,18,20,8,21,18,13],
    '_': [0,0,0,0,0,0,31], '=': [0,0,31,0,31,0,0],
    '[': [14,8,8,8,8,8,14], ']': [14,2,2,2,2,2,14],
}

def glyph(rows, accent=False):
    pen = TTGlyphPen(None)
    for row, bits in enumerate(rows):
        for col in range(5):
            if bits & (1 << (4-col)):
                cx, cy = 80+col*120, 800-row*120
                for i in range(16):
                    a = -2*math.pi*i/16
                    point = (round(cx+43*math.cos(a)), round(cy+43*math.sin(a)))
                    (pen.moveTo if i == 0 else pen.lineTo)(point)
                pen.closePath()
    if accent:
        # Two circular dots make a grave/acute accent, rather than the old ambiguous bars.
        dots = [(260, 980), (380, 900)] if accent in 'àèìòùÀÈÌÒÙ' else [(260, 900), (380, 980)]
        for cx, cy in dots:
            for i in range(16):
                a = -2*math.pi*i/16
                point = (round(cx+30*math.cos(a)), round(cy+30*math.sin(a)))
                (pen.moveTo if i == 0 else pen.lineTo)(point)
            pen.closePath()
    return pen.glyph()

def main():
    cmap, glyphs, metrics = {}, {'.notdef': glyph(ROWS['?'])}, {'.notdef': (640,37)}
    for code in list(range(32,127)) + list(range(160,256)) + [0x2018,0x2019,0x2013,0x2014,0x2026]:
        ch = chr(code)
        base = unicodedata.normalize('NFD', ch)[0].upper()
        base = {'’': "'", '‘': "'", '–': '-', '—': '-', '…': '.', '\xa0': ' '}.get(base,base)
        rows = ROWS.get(base, ROWS['?'])
        name = f'uni{code:04X}'
        cmap[code] = name
        glyphs[name] = glyph(rows, accent=ch if ch in 'àèéìòùÀÈÉÌÒÙ' else None)
        metrics[name] = (640,37)
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(list(glyphs)); fb.setupCharacterMap(cmap); fb.setupGlyf(glyphs)
    fb.setupHorizontalMetrics(metrics); fb.setupHorizontalHeader(ascent=1000, descent=-140)
    fb.setupNameTable({'familyName':'AILA Dot','styleName':'Regular','uniqueFontIdentifier':'AILA-Dot-1.0',
                       'fullName':'AILA Dot Regular','psName':'AILADot-Regular',
                       'licenseDescription':'Original AILA artwork. Licensed under the MIT license.'})
    fb.setupOS2(sTypoAscender=1000,sTypoDescender=-140,usWinAscent=1000,usWinDescent=140)
    fb.setupPost(); fb.setupMaxp()
    target = Path(__file__).resolve().parents[2]/'shared/src/commonMain/composeResources/font/aila_dot.ttf'
    target.parent.mkdir(parents=True, exist_ok=True)
    fb.save(target)
    print(target, target.stat().st_size)

if __name__ == '__main__': main()
