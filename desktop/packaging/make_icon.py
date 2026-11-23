"""Draws the app icon (same design as the Android adaptive icon) as icon.png and SchoolFeeManager.ico."""
import sys
from PIL import Image, ImageDraw

def draw(size):
    s = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = int(size * 0.18)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=(0x1F, 0x4E, 0x79, 255))
    # content is drawn inside the adaptive-icon safe zone (18..90), enlarged slightly for desktop
    def p(x, y):
        c = 54
        k = 1.25
        return ((c + (x - c) * k) * s, (c + (y - c) * k) * s)
    def rect(x, y, w, h, col=(255, 255, 255, 255)):
        d.rectangle([p(x, y), p(x + w, y + h)], fill=col)
    d.polygon([p(54, 30), p(80, 44), p(28, 44)], fill=(255, 255, 255, 255))
    rect(32, 46, 44, 4)
    for x in (35, 51, 67):
        rect(x, 52, 6, 18)
    rect(30, 72, 48, 5)
    cx, cy = p(78, 70)
    rr = 9 * 1.25 * s
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(0xF2, 0xC9, 0x4C, 255))
    return img

out_png, out_ico = sys.argv[1], sys.argv[2]
big = draw(512)
big.resize((256, 256), Image.LANCZOS).save(out_png)
big.save(out_ico, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
