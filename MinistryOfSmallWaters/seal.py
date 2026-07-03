"""
The Great Seal of the Ministry of Small Waters: a crowned crab on the navy
field. PIL-only (no Tkinter) so it can be baked into a tray icon, a window
icon, or an .ico file for the packaged .exe.
"""

from __future__ import annotations

from PIL import Image, ImageDraw

import pixelart
from config import PALETTE


def _rgb(name):
    h = PALETTE[name].lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def build_seal(size=64):
    """Return an RGBA PIL image of the crowned-crab seal at `size`x`size`."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    navy = _rgb("navy") + (255,)
    teal = _rgb("teal") + (255,)
    r = max(3, size // 6)
    d.rounded_rectangle([1, 1, size - 2, size - 2], radius=r, fill=navy)
    d.rounded_rectangle([1, 1, size - 2, size - 2], radius=r,
                        outline=teal, width=max(1, size // 32))

    crab = pixelart.make_crab(crown=True)[1]          # claws-up, most regal frame
    scale = max(1, int(size * 0.74) // crab.width)
    crab = pixelart.scale(crab, scale)
    img.alpha_composite(crab, ((size - crab.width) // 2,
                               (size - crab.height) // 2 + size // 12))

    # one teal bubble, top-right, to keep it unmistakably aquatic
    bx, by = int(size * 0.72), int(size * 0.15)
    bd = max(2, size // 9)
    d.ellipse([bx, by, bx + bd, by + bd], outline=teal, width=max(1, size // 40))
    return img


def write_ico(path, sizes=(16, 24, 32, 48, 64, 128, 256)):
    """Render the seal natively at each resolution into one Windows .ico file."""
    imgs = [build_seal(s) for s in sizes]     # crisp pixel art per size
    largest = imgs[-1]
    largest.save(path, format="ICO", append_images=imgs[:-1])
    return path


if __name__ == "__main__":
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "icon.ico")
    write_ico(out)
    # also dump a big PNG preview
    build_seal(256).save(os.path.join(here, "seal_preview.png"))
    print(f"wrote {out} and seal_preview.png")
    # ascii peek so we can sanity-check the seal headlessly
    print(pixelart.ascii_preview(build_seal(32)))
