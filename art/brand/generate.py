"""Torrento brand, direction A «Раздача»: ring of 12 pieces (9 done) + the app's pictogram.

Writes art/brand/*.svg with all text as outlines (Space Grotesk, art/brand/SpaceGrotesk-Variable.ttf).
Run from the repo root:  python3 -m venv /tmp/v && /tmp/v/bin/pip install fonttools && /tmp/v/bin/python art/brand/generate.py .
App PNGs: magick -background none -density 600 art/brand/torrento-drive-icon.svg -resize 192x192 <out>.png
"""
import math, sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

ROOT = sys.argv[1]
FONT = os.path.join(ROOT, 'art/brand/SpaceGrotesk-Variable.ttf')  # OFL 1.1
OUT = os.path.join(ROOT, 'art/brand')

C = dict(bg='#0B0C10', fg='#F2F3F5', dim='#2A2E38', dimLight='#C9CDD4', ink='#0B0C10',
         drive='#FF6A3D', play='#2FC8B4', music='#FF4F86', books='#FFB547')

_fonts = {}
def font(w):
    if w not in _fonts:
        f = TTFont(FONT)
        _fonts[w] = instantiateVariableFont(f, {'wght': w}, inplace=False)
    return _fonts[w]

def text_path(s, w, size, x=0.0, y=0.0, tracking=0.0):
    """Outline of string s at font-size `size`, baseline at (x, y). Returns (svg path d, advance width)."""
    f = font(w); gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f['head'].unitsPerEm
    k = size / upm; ds = []; cx = 0.0
    for ch in s:
        g = cmap[ord(ch)]
        pen = SVGPathPen(gs, lambda v: f'{v:.2f}')
        from fontTools.pens.transformPen import TransformPen
        tp = TransformPen(pen, (k, 0, 0, -k, x + cx, y))
        gs[g].draw(tp)
        ds.append(pen.getCommands())
        cx += gs[g].width * k + tracking
    return ' '.join(ds), cx - tracking

def glyph_t_box(box):
    """Lowercase t from Space Grotesk Bold, fitted to a box of height `box` centred on (0,0)."""
    f = font(700); gs = f.getGlyphSet(); g = f.getBestCmap()[ord('t')]
    bp = BoundsPen(gs); gs[g].draw(bp); x0, y0, x1, y1 = bp.bounds
    k = box / (y1 - y0)
    from fontTools.pens.transformPen import TransformPen
    pen = SVGPathPen(gs, lambda v: f'{v:.2f}')
    # centre: x mid, y mid; flip y
    tx = -(x0 + x1) / 2 * k; ty = (y0 + y1) / 2 * k
    gs[g].draw(TransformPen(pen, (k, 0, 0, -k, tx, ty)))
    return pen.getCommands()

# Pictograms in a ±20 box centred on (0,0); optical centring included.
def picto(k, col):
    if k == 't':
        return f'<path d="{glyph_t_box(36)}" fill="{col}"/>'
    if k == 'drive':  # rounded play triangle, shifted right by 1/6 of its width
        return (f'<path d="M-9.5 -14.5 Q-9.5 -17.5 -6.9 -16 L15.3 -1.9 Q17.8 0 15.3 1.9 L-6.9 16 '
                f'Q-9.5 17.5 -9.5 14.5 Z" fill="{col}"/>')
    if k == 'play':
        return (f'<rect x="-16" y="-5" width="32" height="10" rx="3" fill="{col}"/>'
                f'<rect x="-5" y="-16" width="10" height="32" rx="3" fill="{col}"/>')
    if k == 'music':
        return f'<path d="M-1 -17h14v7h-7v17a8 8 0 1 1-7-7.9z" fill="{col}"/>'
    if k == 'books':
        return (f'<path d="M-16 -12c6-2.5 11-1.5 15 2v23c-4-3.5-9-4.5-15-2z '
                f'M16 -12c-6-2.5-11-1.5-15 2v23c4-3.5 9-4.5 15-2z" fill="{col}"/>')

def ring(col, dim, cx=50, cy=50, R=46, r=31, n=12, done=9, gap_deg=4.5):
    out = []
    for i in range(n):
        a0 = math.radians(i * 360 / n - 90 + gap_deg / 2)
        a1 = math.radians((i + 1) * 360 / n - 90 - gap_deg / 2)
        p = lambda rad, a: f'{cx + rad*math.cos(a):.2f} {cy + rad*math.sin(a):.2f}'
        fill = col if i < done else dim
        out.append(f'<path d="M{p(R,a0)} A{R} {R} 0 0 1 {p(R,a1)} L{p(r,a1)} A{r} {r} 0 0 0 {p(r,a0)}Z" fill="{fill}"/>')
    return ''.join(out)

def mark(k, on_light=False):
    """Group drawn in a 100×100 box."""
    col = C['drive'] if k == 't' else C[k]
    dim = C['dimLight'] if on_light else C['dim']
    fg = C['ink'] if on_light else C['fg']
    return ring(col, dim) + f'<g transform="translate(50 50)">{picto(k, fg)}</g>'

def svg(w, h, body, title):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">'
            f'<title>{title}</title>{body}</svg>\n')

def write(name, s):
    open(os.path.join(OUT, name), 'w').write(s)

NAMES = dict(t='Torrento', drive='Torrento Drive', play='Torrento Play', music='Torrento Music', books='Torrento Books')

for k in ['t', 'drive', 'play', 'music', 'books']:
    base = 'torrento' if k == 't' else f'torrento-{k}'
    write(f'{base}-mark.svg', svg(100, 100, mark(k), NAMES[k]))
    write(f'{base}-mark-light.svg', svg(100, 100, mark(k, True), NAMES[k]))
    # launcher tile: dark rounded square, mark at 78 %
    write(f'{base}-icon.svg', svg(512, 512,
          f'<rect width="512" height="512" rx="112" fill="{C["bg"]}"/>'
          f'<g transform="translate(56 56) scale(4)">{mark(k)}</g>', NAMES[k]))

def lockup(k, on_light=False, h=100):
    """Horizontal lockup: mark h×h, then 'torrento' Bold + app name Regular in app colour."""
    fg = C['ink'] if on_light else C['fg']
    size = h * 0.56
    gap = h * 0.22
    base_y = h * 0.5 + size * 0.36  # visually centred on the ring (x-height ~0.72 of size... lowercase)
    d1, w1 = text_path('torrento', 700, size, x=h + gap, y=base_y, tracking=-size*0.01)
    body = f'<g transform="scale({h/100})">{mark(k, on_light)}</g><path d="{d1}" fill="{fg}"/>'
    width = h + gap + w1
    if k != 't':
        sp = size * 0.26
        d2, w2 = text_path(NAMES[k].split()[1].lower(), 400, size, x=width + sp, y=base_y)
        body += f'<path d="{d2}" fill="{C[k]}"/>'
        width += sp + w2
    return svg(round(width + 2), h, body, NAMES[k])

for k in ['t', 'drive', 'play', 'music', 'books']:
    base = 'torrento' if k == 't' else f'torrento-{k}'
    write(f'{base}-lockup.svg', lockup(k))
    write(f'{base}-lockup-light.svg', lockup(k, True))

# Android TV banner 320×180: mark left, stacked wordmark.
def banner(k='drive'):
    m = 92; x0 = 30; y0 = (180 - m) / 2
    d1, _ = text_path('torrento', 700, 40, x=x0 + m + 20, y=86, tracking=-0.4)
    d2, _ = text_path(NAMES[k].split()[1].lower(), 400, 32, x=x0 + m + 21, y=124)
    body = (f'<rect width="320" height="180" fill="{C["bg"]}"/>'
            f'<defs><radialGradient id="g" cx=".22" cy=".5" r=".6"><stop offset="0" stop-color="{C[k]}" stop-opacity=".20"/>'
            f'<stop offset="1" stop-color="{C[k]}" stop-opacity="0"/></radialGradient></defs>'
            f'<rect width="320" height="180" fill="url(#g)"/>'
            f'<g transform="translate({x0} {y0}) scale({m/100})">{mark(k)}</g>'
            f'<path d="{d1}" fill="{C["fg"]}"/><path d="{d2}" fill="{C[k]}"/>')
    return svg(320, 180, body, NAMES[k])
write('torrento-drive-banner.svg', banner())
print('ok', sorted(os.listdir(OUT)))
