#!/usr/bin/env python3
"""Regenerate the Play Store feature graphic.

    python3 store/make-feature-graphic.py     # run from the repo root

Play requires 1024x500, JPEG or 24-bit PNG with NO alpha (the opposite of the
512x512 listing icon, which must have one). Google crops the graphic from the
edges depending on the surface it is shown on, so the whole lockup is centred;
and if a promo video is ever added, a play button is overlaid in the middle,
which would land on top of this composition.

Requires Pillow.
"""
from PIL import Image, ImageDraw, ImageFont
import pathlib

W, H = 1024, 500
ROOT = pathlib.Path(__file__).resolve().parent.parent
MARK = ROOT / 'app/src/main/res/drawable-nodpi/hadron_mark.png'
DST = ROOT / 'store/play-feature-graphic-1024x500.png'

# Brand blue (#219EBC, shared with hadron-ios' AccentColor) deepened toward a
# darker teal so the gradient stays calm behind white type.
TOP, BOT = (0x2A, 0xB2, 0xD2), (0x12, 0x63, 0x7A)
TITLE, TAGLINE = 'Hadron', 'Knowledge lego-style'
MARK_SIZE, GAP = 232, 48

img = Image.new('RGB', (W, H))
draw = ImageDraw.Draw(img)
for y in range(H):
    t = y / (H - 1)
    draw.line([(0, y), (W, y)],
              fill=tuple(round(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)))

# The mark is a two-tone opaque disc — black glyph, white counter-space,
# transparent only outside the circle. Composite it; never tint it, or it
# flattens into a filled blob.
mark = Image.open(MARK).convert('RGBA').resize((MARK_SIZE, MARK_SIZE), Image.LANCZOS)


def font(size, weight):
    f = ImageFont.truetype('/System/Library/Fonts/SFNS.ttf', size)
    try:
        f.set_variation_by_name(weight)   # SF ships weights as named instances
    except Exception:
        pass
    return f


f_title, f_tag = font(118, 'Bold'), font(40, 'Regular')
tb = draw.textbbox((0, 0), TITLE, font=f_title)
gb = draw.textbbox((0, 0), TAGLINE, font=f_tag)

lockup_w = MARK_SIZE + GAP + max(tb[2] - tb[0], gb[2] - gb[0])
x0, y_mid = (W - lockup_w) // 2, H // 2

img.paste(mark, (x0, y_mid - MARK_SIZE // 2), mark)
tx = x0 + MARK_SIZE + GAP
draw.text((tx - tb[0], y_mid - 74 - tb[1]), TITLE, font=f_title, fill=(255, 255, 255))
draw.text((tx - gb[0], y_mid + 26 - gb[1]), TAGLINE, font=f_tag, fill=(226, 244, 250))

img.save(DST, 'PNG')                      # RGB mode => 24-bit, no alpha
print(f'wrote {DST.relative_to(ROOT)}')
