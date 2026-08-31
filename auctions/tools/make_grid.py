#!/usr/bin/env python3
"""Generate the capture pages used to pull listing photos off torgi.mos.ru.

torgi.mos.ru refuses connections from this network; the Jina reader reaches it
but rate-limits hard, so grabbing 18 photos one screenshot at a time does not
finish. These pages stack several photos in fixed-height slots on a magenta
backdrop; one screenshot of a page then yields several photos, and slice_grid.py
cuts them apart on the slot boundaries.

The pages are served from the repository over raw.githack.com so that the
reader's browser — not this container — is the one fetching the pictures.

    python3 tools/make_grid.py assets/obj1/images.txt tools/grids/obj1 6
"""
import os
import sys

SLOT_W, SLOT_H = 1024, 800
BG = '#ff00ff'          # sentinel colour, trimmed away when slicing

PAGE = """<!doctype html>
<meta charset="utf-8">
<title>capture</title>
<style>
 html,body{{margin:0;padding:0;background:{bg};}}
 .slot{{width:{w}px;height:{h}px;display:flex;align-items:center;
        justify-content:center;background:{bg};overflow:hidden;}}
 .slot img{{max-width:{w}px;max-height:{h}px;display:block;}}
</style>
{slots}
"""
SLOT = '<div class="slot"><img referrerpolicy="no-referrer" src="{src}"></div>\n'


def main(list_file, out_dir, per_page):
    per_page = int(per_page)
    urls = [l.strip() for l in open(list_file) if l.strip()]
    os.makedirs(out_dir, exist_ok=True)
    pages = []
    for start in range(0, len(urls), per_page):
        chunk = urls[start:start + per_page]
        name = 'grid%02d.html' % (start // per_page + 1)
        slots = ''.join(SLOT.format(src=u) for u in chunk)
        with open(os.path.join(out_dir, name), 'w', encoding='utf-8') as fh:
            fh.write(PAGE.format(bg=BG, w=SLOT_W, h=SLOT_H, slots=slots))
        pages.append((name, start + 1, start + len(chunk)))
        print(name, 'photos', start + 1, '-', start + len(chunk))
    return pages


if __name__ == '__main__':
    if len(sys.argv) < 4:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
