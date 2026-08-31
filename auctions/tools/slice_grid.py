#!/usr/bin/env python3
"""Cut the captured grid screenshots back into individual photos.

Each capture page stacks photos in fixed 1024x800 slots on a magenta backdrop,
so slicing is a plain cut on the slot boundaries; prep_images.py then trims the
magenta away and writes the JPEGs the document builder uses.

    python3 tools/slice_grid.py assets/obj1/grids assets/obj1/photos_raw
"""
import os
import sys

from PIL import Image

SLOT_W, SLOT_H = 1024, 800


def main(grid_dir, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    index = 0
    for name in sorted(f for f in os.listdir(grid_dir) if f.endswith('.png')):
        im = Image.open(os.path.join(grid_dir, name)).convert('RGB')
        w, h = im.size
        for top in range(0, h, SLOT_H):
            if top + SLOT_H > h:
                break
            index += 1
            slot = im.crop((0, top, min(SLOT_W, w), top + SLOT_H))
            slot.save(os.path.join(out_dir, '%02d.png' % index))
        print(name, im.size, '->', index, 'slots so far')
    print('slots:', index)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
