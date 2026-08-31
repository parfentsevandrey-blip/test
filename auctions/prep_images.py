#!/usr/bin/env python3
"""Trim the browser letterboxing off captured listing photos and emit a manifest.

The photos are captured as full-viewport screenshots of the image URL, so each
PNG may carry uniform bars where the picture did not fill the 1024x768 frame.
This crops those bars away, re-encodes to JPEG and records the final pixel size
so the document builder can lay the photos out without distorting them.
"""
import json
import os
import sys

from PIL import Image

TOL = 12          # per-channel tolerance when deciding a row/column is a bar
QUALITY = 88


def uniform(pixels, coords, ref):
    for c in coords:
        p = pixels[c]
        if abs(p[0] - ref[0]) > TOL or abs(p[1] - ref[1]) > TOL or abs(p[2] - ref[2]) > TOL:
            return False
    return True


def trim(im):
    w, h = im.size
    px = im.load()
    ref = px[0, 0]
    top, bottom, left, right = 0, h - 1, 0, w - 1
    step = max(1, w // 60)
    while top < bottom and uniform(px, [(x, top) for x in range(0, w, step)], ref):
        top += 1
    while bottom > top and uniform(px, [(x, bottom) for x in range(0, w, step)], ref):
        bottom -= 1
    step = max(1, h // 60)
    while left < right and uniform(px, [(left, y) for y in range(0, h, step)], ref):
        left += 1
    while right > left and uniform(px, [(right, y) for y in range(0, h, step)], ref):
        right -= 1
    if right - left < 80 or bottom - top < 80:
        return im                       # refuse to trim away the whole picture
    return im.crop((left, top, right + 1, bottom + 1))


def main(src_dir, dst_dir):
    os.makedirs(dst_dir, exist_ok=True)
    manifest = []
    for name in sorted(os.listdir(src_dir)):
        if not name.lower().endswith('.png'):
            continue
        im = Image.open(os.path.join(src_dir, name)).convert('RGB')
        im = trim(im)
        out = os.path.splitext(name)[0] + '.jpg'
        im.save(os.path.join(dst_dir, out), 'JPEG', quality=QUALITY, optimize=True)
        manifest.append({'file': out, 'width': im.size[0], 'height': im.size[1]})
        print(name, '->', out, im.size)
    with open(os.path.join(dst_dir, 'manifest.json'), 'w') as fh:
        json.dump(manifest, fh, indent=1)
    print('photos:', len(manifest))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
