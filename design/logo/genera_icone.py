"""
Genera tutti gli asset del marchio AILA partendo da un'unica geometria.

Il marchio: AI + AULA. In alto la scintilla (l'AI, al posto della cattedra), sotto due file di
banchi collegati come i nodi di una rete neurale. Si legge sia come classe vista dall'alto sia
come rete.

Esistono due varianti:
  - COMPLETA: scintilla + due file di banchi + collegamenti. Per icona, login, caricamento.
  - PICCOLA: scintilla + una fila di banchi. Sotto i ~32dp la rete completa diventa un grumo di
    puntini, quindi si toglie la seconda fila e si ingrossano gli elementi.

La geometria è definita in un riquadro unitario (0..1). La stessa geometria è riportata in
shared/src/commonMain/kotlin/circolareplus/design/AilaLogo.kt (AilaGlyph): se la cambi qui,
cambiala anche lì.

Uso (dalla radice del repo):
    pip install cairosvg
    python3 design/logo/genera_icone.py
"""
import io
import os
import subprocess

import cairosvg

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# --- Colori del brand -------------------------------------------------------------------------
BG_A, BG_B = "#1E3C93", "#0A1330"          # fondo icona (come ic_launcher_background.xml)
DESK_A, DESK_B = "#6FA8FF", "#A78BFA"      # banchi: blu -> viola
LINE = "#B4C6FF"                           # collegamenti della rete
SPARK_A, SPARK_B = "#FFFFFF", "#DCE5FF"    # scintilla
HALO = "#C9B8FF"                           # alone della scintilla

# --- Geometria (riquadro unitario) ------------------------------------------------------------
FULL = dict(
    spark=(0.5, 0.1515), spark_rx=0.1212, spark_ry=0.1515, halo=0.2576,
    rows=[(0.5455, 1.0), (0.9091, 0.78)],          # (y centro fila, opacità)
    xs=[0.1818, 0.5, 0.8182], desk_w=0.2424, desk_h=0.1818, desk_r=0.0606,
    line_w=0.0273, line_a=0.42,
)
SMALL = dict(
    spark=(0.5, 0.2), spark_rx=0.14, spark_ry=0.19, halo=0.30,
    rows=[(0.78, 1.0)],
    xs=[0.17, 0.5, 0.83], desk_w=0.26, desk_h=0.2, desk_r=0.065,
    line_w=0.04, line_a=0.5,
)


def star(cx, cy, rx, ry, k=0.17):
    ix, iy = rx * k, ry * k
    return (f"M{cx:.3f},{cy - ry:.3f} Q{cx + ix:.3f},{cy - iy:.3f} {cx + rx:.3f},{cy:.3f} "
            f"Q{cx + ix:.3f},{cy + iy:.3f} {cx:.3f},{cy + ry:.3f} "
            f"Q{cx - ix:.3f},{cy + iy:.3f} {cx - rx:.3f},{cy:.3f} "
            f"Q{cx - ix:.3f},{cy - iy:.3f} {cx:.3f},{cy - ry:.3f} Z")


def mark_svg(g, ox, oy, s, mono=None):
    """Il marchio dentro il riquadro di lato s con angolo in alto a sinistra in (ox, oy)."""
    P = lambda u, v: (ox + u * s, oy + v * s)
    out = []
    defs = ""
    if mono is None:
        x0, y1 = P(0, 1)
        x1, y0 = P(1, 0)
        defs = (f'<linearGradient id="desk" x1="{x0}" y1="{y1}" x2="{x1}" y2="{y0}" gradientUnits="userSpaceOnUse">'
                f'<stop offset="0" stop-color="{DESK_A}"/><stop offset="1" stop-color="{DESK_B}"/></linearGradient>'
                f'<linearGradient id="spk" x1="0" y1="{y0}" x2="0" y2="{P(0, g["spark"][1] + g["spark_ry"])[1]}" gradientUnits="userSpaceOnUse">'
                f'<stop offset="0" stop-color="{SPARK_A}"/><stop offset="1" stop-color="{SPARK_B}"/></linearGradient>'
                f'<radialGradient id="halo" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{HALO}" stop-opacity=".55"/>'
                f'<stop offset="1" stop-color="{HALO}" stop-opacity="0"/></radialGradient>')
    desk_fill = "url(#desk)" if mono is None else mono
    line_col = LINE if mono is None else mono
    line_a = g["line_a"] if mono is None else 1
    sx, sy = P(*g["spark"])
    # collegamenti: scintilla -> prima fila, poi ogni banco di una fila -> tutti quelli della successiva
    levels = [[g["spark"]]] + [[(x, y) for x in g["xs"]] for y, _ in g["rows"]]
    for a_lvl, b_lvl in zip(levels, levels[1:]):
        for a in a_lvl:
            for b in b_lvl:
                (x1, y1), (x2, y2) = P(*a), P(*b)
                out.append(f'<line x1="{x1:.3f}" y1="{y1:.3f}" x2="{x2:.3f}" y2="{y2:.3f}" stroke="{line_col}" '
                           f'stroke-opacity="{line_a}" stroke-width="{g["line_w"] * s:.3f}" stroke-linecap="round"/>')
    for y, alpha in g["rows"]:
        for x in g["xs"]:
            cx, cy = P(x, y)
            w, h, r = g["desk_w"] * s, g["desk_h"] * s, g["desk_r"] * s
            op = alpha if mono is None else 1
            out.append(f'<rect x="{cx - w / 2:.3f}" y="{cy - h / 2:.3f}" width="{w:.3f}" height="{h:.3f}" rx="{r:.3f}" '
                       f'fill="{desk_fill}" opacity="{op}"/>')
    if mono is None:
        hr = g["halo"] * s
        out.append(f'<circle cx="{sx:.3f}" cy="{sy:.3f}" r="{hr:.3f}" fill="url(#halo)"/>')
    out.append(f'<path d="{star(sx, sy, g["spark_rx"] * s, g["spark_ry"] * s)}" fill="{"url(#spk)" if mono is None else mono}"/>')
    return defs, "".join(out)


def icon_svg(g, size=108, rounded=False):
    """Icona completa: fondo + riflesso + marchio. Il marchio occupa 66/108 del lato, come nel mockup."""
    s = size * 66 / 108
    ox, oy = (size - s) / 2, size * 17 / 108
    defs, body = mark_svg(g, ox, oy, s)
    clip = f'<clipPath id="m"><rect width="{size}" height="{size}" rx="{size * 0.2222}"/></clipPath>' if rounded else ""
    clip_attr = ' clip-path="url(#m)"' if rounded else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}"><defs>{defs}{clip}'
            f'<linearGradient id="bg" x1="0" y1="{size}" x2="{size}" y2="0" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{BG_A}"/><stop offset="1" stop-color="{BG_B}"/></linearGradient>'
            f'<radialGradient id="sheen" cx="0" cy="0" r="{size * 0.74}" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="#fff" stop-opacity=".14"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient></defs>'
            f'<g{clip_attr}><rect width="{size}" height="{size}" fill="url(#bg)"/>'
            f'<rect width="{size}" height="{size}" fill="url(#sheen)"/>{body}</g></svg>')


def mark_only_svg(g, size=512, pad=0.0, mono=None):
    s = size * (1 - 2 * pad)
    defs, body = mark_svg(g, size * pad, size * pad, s, mono)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}"><defs>{defs}</defs>{body}</svg>'


def png(svg, path, px, flatten=False):
    data = cairosvg.svg2png(bytestring=svg.encode(), output_width=px, output_height=px)
    if flatten:  # l'icona da 1024 dell'App Store non deve avere il canale alpha
        subprocess.run(["convert", "png:-", "-background", BG_B, "-alpha", "remove", "-alpha", "off", path],
                       input=data, check=True)
    else:
        with open(path, "wb") as f:
            f.write(data)


def rrect_path(x, y, w, h, r):
    return (f"M{x + r:.3f},{y:.3f} H{x + w - r:.3f} A{r:.3f},{r:.3f} 0 0 1 {x + w:.3f},{y + r:.3f} "
            f"V{y + h - r:.3f} A{r:.3f},{r:.3f} 0 0 1 {x + w - r:.3f},{y + h:.3f} H{x + r:.3f} "
            f"A{r:.3f},{r:.3f} 0 0 1 {x:.3f},{y + h - r:.3f} V{y + r:.3f} A{r:.3f},{r:.3f} 0 0 1 {x + r:.3f},{y:.3f} Z")


def circle_path(cx, cy, r):
    return (f"M{cx - r:.3f},{cy:.3f} A{r:.3f},{r:.3f} 0 1 1 {cx + r:.3f},{cy:.3f} "
            f"A{r:.3f},{r:.3f} 0 1 1 {cx - r:.3f},{cy:.3f} Z")


def argb(hex_rgb, alpha=1.0):
    return "#%02X%s" % (round(alpha * 255), hex_rgb.lstrip("#").upper())


# Nell'icona adattiva il livello è 108dp e il launcher mostra solo il cerchio di raggio 33dp al
# centro (la "safe zone"). Con il riquadro del marchio a 48dp l'angolo più lontano dei banchi sta
# a ~32dp dal centro: nessuna maschera lo taglia.
ANDROID_BOX = 48.0


def android_vector(mono=False):
    g = FULL
    s = ANDROID_BOX
    ox = oy = (108 - s) / 2
    P = lambda u, v: (ox + u * s, oy + v * s)
    parts = []
    levels = [[g["spark"]]] + [[(x, y) for x in g["xs"]] for y, _ in g["rows"]]
    lines = []
    for a_lvl, b_lvl in zip(levels, levels[1:]):
        for a in a_lvl:
            for b in b_lvl:
                (x1, y1), (x2, y2) = P(*a), P(*b)
                lines.append(f"M{x1:.3f},{y1:.3f} L{x2:.3f},{y2:.3f}")
    line_col = "#FF000000" if mono else argb(LINE, g["line_a"])
    parts.append(f'    <path\n        android:pathData="{" ".join(lines)}"\n        android:strokeColor="{line_col}"\n'
                 f'        android:strokeWidth="{g["line_w"] * s:.3f}"\n        android:strokeLineCap="round" />')
    x0, y1 = P(0, 1)
    x1, y0 = P(1, 0)
    for y, alpha in g["rows"]:
        d = " ".join(rrect_path(P(x, y)[0] - g["desk_w"] * s / 2, P(x, y)[1] - g["desk_h"] * s / 2,
                                g["desk_w"] * s, g["desk_h"] * s, g["desk_r"] * s) for x in g["xs"])
        if mono:
            parts.append(f'    <path\n        android:pathData="{d}"\n        android:fillColor="#FF000000" />')
        else:
            parts.append(f'    <path\n        android:pathData="{d}"\n        android:fillAlpha="{alpha}">\n'
                         f'        <aapt:attr name="android:fillColor">\n            <gradient\n                android:type="linear"\n'
                         f'                android:startX="{x0:.3f}" android:startY="{y1:.3f}"\n'
                         f'                android:endX="{x1:.3f}" android:endY="{y0:.3f}"\n'
                         f'                android:startColor="{argb(DESK_A)}"\n                android:endColor="{argb(DESK_B)}" />\n'
                         f'        </aapt:attr>\n    </path>')
    sx, sy = P(*g["spark"])
    if not mono:
        hr = g["halo"] * s
        parts.append(f'    <path\n        android:pathData="{circle_path(sx, sy, hr)}">\n'
                     f'        <aapt:attr name="android:fillColor">\n            <gradient\n                android:type="radial"\n'
                     f'                android:centerX="{sx:.3f}" android:centerY="{sy:.3f}"\n                android:gradientRadius="{hr:.3f}"\n'
                     f'                android:startColor="{argb(HALO, 0.55)}"\n                android:endColor="{argb(HALO, 0)}" />\n'
                     f'        </aapt:attr>\n    </path>')
    spark_fill = "#FF000000" if mono else argb(SPARK_A)
    parts.append(f'    <path\n        android:pathData="{star(sx, sy, g["spark_rx"] * s, g["spark_ry"] * s)}"\n'
                 f'        android:fillColor="{spark_fill}" />')
    if mono:
        head = ("<!--\n    Silhouette per le \"icone a tema\" di Android 13+: il sistema ricolora questo livello con i\n"
                "    colori del tema dell'utente, quindi qui conta solo la forma. Stessa geometria di\n"
                "    ic_launcher_foreground.xml. GENERATO da design/logo/genera_icone.py: non modificare a mano.\n-->\n")
        ns = ""
    else:
        head = ("<!--\n    Livello primo piano dell'icona adattiva AILA: il marchio \"AI + AULA\" (scintilla in cattedra,\n"
                "    banchi collegati come i nodi di una rete). Vettore e non PNG, così resta nitido anche\n"
                "    nell'animazione di avvio di sistema. Il marchio sta in un riquadro di 48dp al centro dei\n"
                "    108dp del livello, dentro la safe zone: nessuna maschera del launcher lo taglia.\n"
                "    GENERATO da design/logo/genera_icone.py: non modificare a mano.\n-->\n")
        ns = '\n    xmlns:aapt="http://schemas.android.com/aapt"'
    return ('<?xml version="1.0" encoding="utf-8"?>\n' + head +
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            '    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n' + "\n".join(parts) + "\n</vector>\n")


def main():
    here = os.path.join(ROOT, "design", "logo")
    drawable = os.path.join(ROOT, "androidApp", "src", "androidMain", "res", "drawable")
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w") as f:
        f.write(android_vector())
    with open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(android_vector(mono=True))
    # sorgenti vettoriali
    with open(os.path.join(here, "aila-mark.svg"), "w") as f:
        f.write(mark_only_svg(FULL))
    with open(os.path.join(here, "aila-mark-small.svg"), "w") as f:
        f.write(mark_only_svg(SMALL))
    with open(os.path.join(here, "aila-mark-mono.svg"), "w") as f:
        f.write(mark_only_svg(FULL, mono="currentColor"))
    with open(os.path.join(here, "aila-icon.svg"), "w") as f:
        f.write(icon_svg(FULL))
    with open(os.path.join(here, "aila-icon-rounded.svg"), "w") as f:
        f.write(icon_svg(FULL, rounded=True))

    # iOS: icone app (quadrate, la maschera la mette il sistema). Sotto i 64px variante piccola.
    ios = os.path.join(ROOT, "iosApp", "iosApp", "Assets.xcassets", "AppIcon.appiconset")
    for name in sorted(os.listdir(ios)):
        if not name.endswith(".png"):
            continue
        p = os.path.join(ios, name)
        px = int(subprocess.check_output(["identify", "-format", "%w", p]).decode())
        g = SMALL if px < 64 else FULL
        png(icon_svg(g), p, px, flatten=True)

    # iOS: logo della schermata di avvio (solo marchio, fondo trasparente su LaunchBackground)
    launch = os.path.join(ROOT, "iosApp", "iosApp", "Assets.xcassets", "LaunchLogo.imageset")
    for scale in (1, 2, 3):
        png(mark_only_svg(FULL, pad=0.08), os.path.join(launch, f"LaunchLogo@{scale}x.png"), 120 * scale)

    # Compose: logo raster di riserva (icona completa, senza alpha)
    png(icon_svg(FULL), os.path.join(ROOT, "shared", "src", "commonMain", "composeResources", "drawable", "aila_logo.png"),
        512, flatten=True)
    print("ok")


if __name__ == "__main__":
    main()
