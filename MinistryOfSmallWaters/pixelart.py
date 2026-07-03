"""
Procedural pixel-art sprite factory.

Everything is drawn at a tiny base resolution (a couple dozen pixels wide) so
that when we scale it up with nearest-neighbour it looks crunchy and pixelated,
exactly the retro-aquarium vibe we want. No image assets on disk -- the whole
tank is generated in code, which keeps the app a single portable folder.

Each maker returns a list of RGBA `PIL.Image` *animation frames* at base
resolution. The renderer scales + caches them as it needs them.
"""

from __future__ import annotations

from PIL import Image, ImageDraw, ImageChops


# --------------------------------------------------------------------------- #
# low-level helpers
# --------------------------------------------------------------------------- #

def _blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def _clip_to_silhouette(base, deco):
    """Keep only the parts of `deco` that overlap opaque pixels of `base`."""
    mask = base.getchannel("A")
    deco_alpha = ImageChops.multiply(deco.getchannel("A"), mask)
    deco.putalpha(deco_alpha)
    base.alpha_composite(deco)


def _outline(img, color=(15, 20, 35, 255)):
    """Add a 1px dark outline around the opaque silhouette (that retro look)."""
    a = img.getchannel("A")
    w, h = img.size
    edge = _blank(w, h)
    ed = ImageDraw.Draw(edge)
    # sample the alpha's neighbours; a pixel that is transparent but touches an
    # opaque pixel becomes outline.
    px = a.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] > 40:
                continue
            touch = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and px[nx, ny] > 120:
                    touch = True
                    break
            if touch:
                ed.point((x, y), fill=color)
    img.alpha_composite(edge)


def scale(img, factor):
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def flip_h(img):
    return img.transpose(Image.FLIP_LEFT_RIGHT)


def ascii_preview(img, chars=" .:-=+*#%@"):
    """Render a sprite as text so we can sanity-check shape without a display."""
    out = []
    px = img.convert("RGBA").load()
    for y in range(img.height):
        row = []
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a < 40:
                row.append(" ")
            else:
                lum = (r * 0.3 + g * 0.59 + b * 0.11) / 255.0
                row.append(chars[min(len(chars) - 1, int(lum * (len(chars) - 1)) + 1)])
        out.append("".join(row))
    return "\n".join(out)


# --------------------------------------------------------------------------- #
# creatures
# --------------------------------------------------------------------------- #

def make_fish(body, belly, fin, eye_ring=None, stripes=None, w=26, h=17):
    """A round, friendly fish facing RIGHT. Returns 3 tail-wag frames."""
    frames = []
    for wag in (-2, 0, 2):
        img = _blank(w, h)
        d = ImageDraw.Draw(img)

        bx0, by0, bx1, by1 = 6, 2, w - 3, h - 3
        cy = (by0 + by1) // 2

        # tail (points left)
        tail_base_x = bx0 + 2
        d.polygon(
            [(tail_base_x, cy), (0, cy - 5 + wag), (2, cy), (0, cy + 5 + wag)],
            fill=body,
        )
        # top + bottom fins
        d.polygon([(bx0 + 6, by0 + 1), (bx0 + 12, by0 - 2), (bx0 + 13, by0 + 3)], fill=fin)
        d.polygon([(bx0 + 7, by1 - 1), (bx0 + 11, by1 + 3), (bx0 + 13, by1 - 2)], fill=fin)
        # body
        d.ellipse([bx0, by0, bx1, by1], fill=body)
        # belly shading (lower third)
        belly_layer = _blank(w, h)
        bd = ImageDraw.Draw(belly_layer)
        bd.ellipse([bx0 + 1, cy, bx1 - 1, by1], fill=belly)
        _clip_to_silhouette(img, belly_layer)

        # stripes
        if stripes:
            sl = _blank(w, h)
            sd = ImageDraw.Draw(sl)
            for sx in stripes:
                x = bx0 + sx
                sd.line([(x, by0), (x, by1)], fill=(245, 245, 250, 255), width=1)
                sd.line([(x + 1, by0), (x + 1, by1)], fill=(245, 245, 250, 255), width=1)
            _clip_to_silhouette(img, sl)

        _outline(img)

        # eye + mouth (drawn after outline so they stay crisp)
        ex, ey = bx1 - 6, cy - 2
        if eye_ring:
            d.ellipse([ex - 1, ey - 1, ex + 3, ey + 3], fill=eye_ring)
        d.ellipse([ex, ey, ex + 2, ey + 2], fill=(20, 22, 30, 255))
        d.point((ex + 2, ey), fill=(255, 255, 255, 255))
        d.line([(bx1 - 2, cy + 1), (bx1, cy + 1)], fill=(25, 25, 35, 255))

        frames.append(img)
    return frames


def make_shrimp(shell=(255, 150, 165), pale=(255, 205, 210), w=22, h=14):
    """A little shrimp facing RIGHT with a curling tail. 2 frames (relaxed / flick)."""
    frames = []
    for flick in (0, 1):
        img = _blank(w, h)
        d = ImageDraw.Draw(img)
        cy = h // 2

        # segmented body arching along the top, head at right
        segs = [(6, 0), (8, -1), (10, -1), (12, 0), (14, 1), (16, 1)]
        for i, (sx, dy) in enumerate(segs):
            r = 3 if i in (2, 3) else 2
            d.ellipse([sx - r, cy + dy - r, sx + r, cy + dy + r], fill=shell)

        # curling tail fan on the left
        if flick:
            d.polygon([(6, cy), (1, cy - 4), (0, cy), (1, cy + 5)], fill=shell)
        else:
            d.polygon([(6, cy), (2, cy - 3), (0, cy + 1), (2, cy + 4)], fill=shell)

        # rostrum / antennae to the right
        d.line([(17, cy - 1), (21, cy - 4)], fill=shell, width=1)
        d.line([(17, cy), (21, cy + 4)], fill=shell, width=1)

        # legs underneath
        for lx in (9, 11, 13, 15):
            d.line([(lx, cy + 2), (lx - 1, cy + 4)], fill=shell, width=1)

        # pale belly highlight
        pl = _blank(w, h)
        pd = ImageDraw.Draw(pl)
        pd.line([(7, cy + 1), (16, cy + 1)], fill=pale + (255,) if len(pale) == 3 else pale, width=1)
        _clip_to_silhouette(img, pl)

        _outline(img)
        # eye
        d.point((16, cy - 2), fill=(20, 22, 30, 255))
        frames.append(img)
    return frames


def make_crab(shell=(214, 58, 47), claw=(240, 96, 82), crown=True, w=24, h=16):
    """The Head of State: a grumpy crowned crab. 2 frames: claws down / claws up."""
    frames = []
    for raise_claw in (0, 1):
        img = _blank(w, h)
        d = ImageDraw.Draw(img)
        cx, cy = w // 2, h - 6

        # legs (3 each side) -- animate opposite phase from claws for a scuttle feel
        legphase = 1 if raise_claw else -1
        for i, sign in enumerate((-1, 1)):
            for j in range(3):
                lx = cx + sign * (5 + j * 2)
                ly = cy + 1 + j
                d.line([(cx + sign * 4, cy + 1), (lx, ly + 2)], fill=shell, width=1)
                d.line([(lx, ly + 2), (lx + sign, ly + 4 + (legphase if j == 1 else 0))],
                       fill=shell, width=1)

        # body shell
        d.ellipse([cx - 7, cy - 4, cx + 7, cy + 4], fill=shell)
        d.rectangle([cx - 7, cy - 1, cx + 7, cy + 3], fill=shell)

        # claws on stalks
        for sign in (-1, 1):
            ax = cx + sign * 8
            ay = cy - (5 if raise_claw else 1)
            d.line([(cx + sign * 6, cy - 2), (ax, ay)], fill=shell, width=2)
            d.ellipse([ax - 3, ay - 3, ax + 3, ay + 2], fill=claw)
            # pincer gap
            d.line([(ax + sign * 1, ay - 2), (ax + sign * 3, ay - 3)], fill=shell, width=1)

        _outline(img)

        # two beady eyes on top
        for sign in (-1, 1):
            ex = cx + sign * 2
            d.line([(ex, cy - 4), (ex, cy - 6)], fill=(120, 30, 25, 255))
            d.ellipse([ex - 1, cy - 8, ex + 1, cy - 6], fill=(20, 22, 30, 255))
        # grumpy mouth
        d.line([(cx - 2, cy + 1), (cx + 2, cy + 1)], fill=(120, 30, 25, 255))

        # royal crown -- the whole reason he's Head of State
        if crown:
            gold, gdark = (242, 194, 48, 255), (200, 150, 30, 255)
            cty = cy - 6                       # crown band sits just above the shell
            d.rectangle([cx - 3, cty, cx + 3, cty + 1], fill=gold)      # band
            for pkx in (cx - 3, cx, cx + 3):                            # three points
                d.line([(pkx, cty), (pkx, cty - 3)], fill=gold, width=1)
                d.point((pkx, cty - 3), fill=(255, 240, 170, 255))      # jewel tip
            d.point((cx - 3, cty + 1), fill=gdark)
            d.point((cx + 3, cty + 1), fill=gdark)
        frames.append(img)
    return frames


def make_tetra(body=(70, 190, 180), spark=(240, 250, 255), w=11, h=7):
    """A teeny 'Intern' schooling fish. 2 tail frames, faces RIGHT."""
    frames = []
    for wag in (-1, 1):
        img = _blank(w, h)
        d = ImageDraw.Draw(img)
        cy = h // 2
        d.polygon([(3, cy), (0, cy - 2 + wag), (0, cy + 2 + wag)], fill=body)  # tail
        d.ellipse([2, cy - 2, w - 2, cy + 2], fill=body)                       # body
        d.point((w - 3, cy), fill=spark)                                       # neon fleck
        d.point((w - 3, cy - 1), fill=(20, 22, 30, 255))                       # eye
        frames.append(img)
    return frames


def make_coral(w=26, h=20):
    """A little pixel reef silhouette for the seabed."""
    img = _blank(w, h)
    d = ImageDraw.Draw(img)
    branches = [(200, 90, 120), (230, 120, 150), (180, 110, 200)]
    for i, bx in enumerate((6, 13, 20)):
        col = branches[i % len(branches)] + (255,)
        d.line([(bx, h - 1), (bx, h - 8 - i)], fill=col, width=2)
        d.line([(bx, h - 6), (bx - 3, h - 11)], fill=col, width=2)
        d.line([(bx, h - 5), (bx + 3, h - 10)], fill=col, width=2)
        d.ellipse([bx - 2, h - 12 - i, bx + 2, h - 8 - i], fill=col)
    _outline(img)
    return img


# --------------------------------------------------------------------------- #
# props & particles
# --------------------------------------------------------------------------- #

def make_food(size=6):
    """A single crunchy fish-food pellet."""
    img = _blank(size, size)
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, size - 1, size - 1], fill=(140, 96, 40, 255))
    d.ellipse([1, 1, size - 3, size - 3], fill=(190, 140, 70, 255))
    d.point((1, 1), fill=(230, 200, 140, 255))
    return img


def make_bubble(size=7):
    img = _blank(size, size)
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, size - 1, size - 1], outline=(210, 235, 255, 220))
    d.arc([1, 1, size - 2, size - 2], 180, 250, fill=(255, 255, 255, 230))
    return img


def make_seaweed(height=34, width=10, sway=0):
    """A swaying frond of seaweed. `sway` in pixels shifts the top."""
    img = _blank(width, height)
    d = ImageDraw.Draw(img)
    greens = [(52, 140, 70), (66, 168, 84), (44, 120, 60)]
    for k, gx in enumerate((width // 2 - 2, width // 2 + 1)):
        col = greens[k % len(greens)]
        pts = []
        for i in range(height):
            t = i / height
            x = gx + int(sway * (1 - t)) + int(2 * ((i // 4) % 2))
            pts.append((x, height - 1 - i))
        for j in range(len(pts) - 1):
            d.line([pts[j], pts[j + 1]], fill=col, width=2)
    return img


def make_chest(w=22, h=16):
    """A tiny sunken treasure chest that occasionally burps a bubble."""
    img = _blank(w, h)
    d = ImageDraw.Draw(img)
    wood, dark, gold = (120, 78, 40, 255), (80, 50, 24, 255), (240, 205, 90, 255)
    d.rectangle([2, 6, w - 3, h - 2], fill=wood)
    d.rectangle([2, 6, w - 3, 8], fill=dark)
    d.arc([2, 1, w - 3, 11], 180, 360, fill=wood)
    d.rectangle([2, 5, w - 3, 7], fill=dark)
    # lock + gold peeking out
    d.rectangle([w // 2 - 1, 6, w // 2 + 1, 9], fill=gold)
    d.point((w // 2, 7), fill=dark)
    for gx in range(4, w - 4, 3):
        d.point((gx, 6), fill=gold)
    _outline(img)
    return img


# --------------------------------------------------------------------------- #
# fish species presets (so the tank has variety)
# --------------------------------------------------------------------------- #

FISH_SPECIES = {
    "clownfish": dict(body=(240, 130, 40), belly=(255, 170, 90), fin=(250, 155, 70),
                      eye_ring=(255, 255, 255, 255), stripes=(6, 12)),
    "bluetang":  dict(body=(48, 108, 208), belly=(90, 150, 230), fin=(250, 210, 60),
                      eye_ring=(20, 40, 90, 255), stripes=None),
    "goldfish":  dict(body=(245, 170, 40), belly=(255, 205, 110), fin=(255, 140, 40),
                      eye_ring=(255, 255, 255, 255), stripes=None),
    "guppy":     dict(body=(150, 90, 210), belly=(200, 150, 240), fin=(255, 120, 190),
                      eye_ring=(255, 255, 255, 255), stripes=None),
    "pufferfish": dict(body=(180, 200, 90), belly=(215, 225, 150), fin=(160, 180, 80),
                       eye_ring=(255, 255, 255, 255), stripes=None),
    # the slow, dignified 'Bureaucrat' -- broad and grey-teal
    "cod":       dict(body=(96, 132, 128), belly=(140, 170, 165), fin=(84, 116, 112),
                      eye_ring=(255, 255, 255, 255), stripes=None),
}


def build_fish_frames(species):
    kw = FISH_SPECIES[species]
    return make_fish(**kw)


if __name__ == "__main__":
    # headless self-check: print each sprite as ASCII so shapes are reviewable
    def show(title, frame):
        print(f"\n=== {title} ({frame.width}x{frame.height}) ===")
        print(ascii_preview(frame))

    show("clownfish", build_fish_frames("clownfish")[1])
    show("cod (bureaucrat)", build_fish_frames("cod")[1])
    show("tetra (intern)", make_tetra()[0])
    show("shrimp-flick", make_shrimp()[1])
    show("crab (head of state)", make_crab()[0])
    show("crab-claws-up", make_crab()[1])
    show("coral", make_coral())
    show("chest", make_chest())
