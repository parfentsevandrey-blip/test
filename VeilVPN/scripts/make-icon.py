#!/usr/bin/env python3
"""Render the Veil app icon (macOS squircle, glass ring + snowflake) into the asset catalog.

Pure numpy + zlib, no Pillow required:  python3 scripts/make-icon.py
"""
import os
import struct
import sys
import zlib

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "Veil", "Resources", "Assets.xcassets", "AppIcon.appiconset")
SIZE = 1024


def write_png(path, rgba):
    h, w, _ = rgba.shape
    raw = b"".join(b"\x00" + rgba[y].tobytes() for y in range(h))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def sd_round_box(px, py, cx, cy, half, radius):
    qx = np.abs(px - cx) - half + radius
    qy = np.abs(py - cy) - half + radius
    outside = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - radius


def sd_segment(px, py, ax, ay, bx, by):
    pax, pay = px - ax, py - ay
    bax, bay = bx - ax, by - ay
    h = np.clip((pax * bax + pay * bay) / (bax * bax + bay * bay), 0.0, 1.0)
    return np.sqrt((pax - bax * h) ** 2 + (pay - bay * h) ** 2)


def over(dst, color, alpha):
    """Alpha-composite a flat colour with per-pixel alpha onto dst (premultiplied-free RGBA float)."""
    a = alpha[..., None]
    rgb = np.array(color, dtype=np.float64)[None, None, :]
    dst[..., :3] = rgb * a + dst[..., :3] * (1 - a)
    dst[..., 3] = a[..., 0] + dst[..., 3] * (1 - a[..., 0])


def render(size):
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float64)
    px = xs + 0.5
    py = ys + 0.5
    s = size / 1024.0
    img = np.zeros((size, size, 4), dtype=np.float64)

    cx = cy = size / 2
    half = 412 * s
    radius = 186 * s

    # Drop shadow
    shadow = sd_round_box(px, py - 18 * s, cx, cy, half, radius)
    over(img, (0.02, 0.03, 0.10), (1 - smoothstep(-10 * s, 40 * s, shadow)) * 0.35)

    # Squircle body with a diagonal gradient (violet -> teal)
    body = sd_round_box(px, py, cx, cy, half, radius)
    body_a = 1 - smoothstep(-1.0, 1.0, body)
    t = np.clip(((px - (cx - half)) * 0.55 + (py - (cy - half)) * 0.8) / (half * 2 * 1.35), 0, 1)
    top = np.array([0.43, 0.36, 1.00])
    bottom = np.array([0.08, 0.72, 0.78])
    grad = top[None, None, :] * (1 - t[..., None]) + bottom[None, None, :] * t[..., None]
    a = body_a[..., None]
    img[..., :3] = grad * a + img[..., :3] * (1 - a)
    img[..., 3] = body_a + img[..., 3] * (1 - body_a)

    # Soft highlight (top-left) and vignette
    hl = np.exp(-(((px - (cx - 260 * s)) ** 2 + (py - (cy - 300 * s)) ** 2) / (2 * (330 * s) ** 2)))
    over(img, (1, 1, 1), hl * 0.22 * body_a)
    vig = np.clip((np.sqrt((px - cx) ** 2 + (py - cy) ** 2) - 300 * s) / (300 * s), 0, 1)
    over(img, (0.02, 0.05, 0.16), vig * 0.28 * body_a)

    # Glass ring
    r = np.sqrt((px - cx) ** 2 + (py - cy) ** 2)
    ring = np.abs(r - 292 * s) - 24 * s
    ring_a = 1 - smoothstep(-1.0, 1.0, ring)
    over(img, (1, 1, 1), ring_a * 0.16)
    inner_glow = (1 - smoothstep(-1.0, 12 * s, np.abs(r - 268 * s))) * 0.45
    over(img, (1, 1, 1), inner_glow * body_a)
    outer_edge = (1 - smoothstep(-1.0, 6 * s, np.abs(r - 316 * s))) * 0.55
    over(img, (1, 1, 1), outer_edge * body_a)

    # Snowflake: fold the plane into a 30-degree sector, then draw one arm + one branch.
    ang = np.arctan2(py - cy, px - cx)
    sector = np.pi / 3
    folded = np.mod(ang, sector)
    folded = np.where(folded > sector / 2, sector - folded, folded)
    fx = cx + r * np.cos(folded)
    fy = cy + r * np.sin(folded)

    arm_len = 205 * s
    arm = sd_segment(fx, fy, cx + 34 * s, cy, cx + arm_len, cy) - 15 * s
    # branch: starts at 58% of the arm, goes out at +60 degrees
    bx0 = cx + arm_len * 0.58
    bl = 62 * s
    branch = sd_segment(fx, fy, bx0, cy, bx0 + bl * np.cos(sector), cy + bl * np.sin(sector)) - 11 * s
    tip = np.sqrt((fx - (cx + arm_len)) ** 2 + (fy - cy) ** 2) - 22 * s
    flake = np.minimum(np.minimum(arm, branch), tip)
    flake_a = 1 - smoothstep(-1.0, 1.0, flake)
    core = r - 40 * s
    core_a = 1 - smoothstep(-1.0, 1.0, core)
    glow = (1 - smoothstep(0, 26 * s, flake)) * 0.35
    over(img, (0.85, 0.95, 1.0), glow * body_a)
    over(img, (1, 1, 1), np.maximum(flake_a, core_a) * 0.97)

    out = np.clip(img * 255 + 0.5, 0, 255).astype(np.uint8)
    return out


def downsample(rgba, factor):
    h, w, c = rgba.shape
    f = rgba.astype(np.float64)
    # premultiply to avoid dark fringes
    f[..., :3] *= f[..., 3:4] / 255.0
    f = f.reshape(h // factor, factor, w // factor, factor, c).mean(axis=(1, 3))
    alpha = f[..., 3:4]
    rgb = np.where(alpha > 0, f[..., :3] / np.maximum(alpha / 255.0, 1e-6), 0)
    return np.clip(np.concatenate([rgb, alpha], axis=-1) + 0.5, 0, 255).astype(np.uint8)


def main():
    os.makedirs(OUT, exist_ok=True)
    base = render(SIZE)
    sizes = {1024: "icon_512x512@2x.png", 512: ["icon_512x512.png", "icon_256x256@2x.png"],
             256: ["icon_256x256.png", "icon_128x128@2x.png"], 128: "icon_128x128.png",
             64: "icon_32x32@2x.png", 32: ["icon_32x32.png", "icon_16x16@2x.png"], 16: "icon_16x16.png"}
    current = base
    current_size = SIZE
    for size in [1024, 512, 256, 128, 64, 32, 16]:
        if size != current_size:
            current = downsample(current, current_size // size)
            current_size = size
        names = sizes[size]
        for name in ([names] if isinstance(names, str) else names):
            write_png(os.path.join(OUT, name), current)
            print("wrote", name)
    write_png(os.path.join(ROOT, "docs", "icon-1024.png"), base) if os.path.isdir(os.path.join(ROOT, "docs")) else None


if __name__ == "__main__":
    sys.exit(main())
