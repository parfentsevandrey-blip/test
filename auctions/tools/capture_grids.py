#!/usr/bin/env python3
"""Screenshot the capture pages through the Jina reader.

The pages must already be pushed to the repository: they are loaded via
htmlpreview.github.io, which serves raw GitHub HTML to a browser (raw.github-
usercontent.com sends text/plain and githack shows an interstitial, so neither
renders). The reader returns a full-page PNG — 800 px per photo slot — which
slice_grid.py then cuts apart.

    python3 tools/capture_grids.py obj1 <branch>
"""
import os
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = 'parfentsevandrey-blip/test'
SLOT_H = 800


def sh(cmd, t=260):
    try:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=t).stdout.strip()
    except Exception as exc:                     # noqa: BLE001
        return 'EXC:' + str(exc)[:90]


def capture(page_url, dst, expect_h, tries=6):
    from PIL import Image
    if os.path.exists(dst):
        try:
            if Image.open(dst).size[1] >= expect_h:
                print('  cached', dst)
                return True
        except Exception:
            pass
    for attempt in range(tries):
        shot = sh('curl -sS -m 240 -H "x-timeout: 90" -H "x-respond-with: pageshot" "https://r.jina.ai/%s"'
                  % page_url)
        if shot.startswith('https'):
            sh('curl -sSL -o "%s" -m 250 -w "%%{http_code}" "%s"' % (dst, shot))
            try:
                size = Image.open(dst).size
            except Exception:
                size = (0, 0)
            if size[1] >= expect_h:
                print('  ok', size)
                return True
            print('  wrong size', size)
            os.path.exists(dst) and os.remove(dst)
        else:
            print('  api-fail', shot[:80].replace('\n', ' '))
        time.sleep(60 + 45 * attempt)
    return False


def main(obj_id, branch):
    grid_dir = os.path.join(ROOT, 'tools', 'grids', obj_id)
    out_dir = os.path.join(ROOT, 'assets', obj_id, 'grids')
    os.makedirs(out_dir, exist_ok=True)
    pages = sorted(f for f in os.listdir(grid_dir) if f.endswith('.html'))
    for page in pages:
        slots = open(os.path.join(grid_dir, page), encoding='utf-8').read().count('class="slot"')
        raw = ('https://raw.githubusercontent.com/%s/%s/auctions/tools/grids/%s/%s'
               % (REPO, branch, obj_id, page))
        url = 'https://htmlpreview.github.io/?' + raw
        dst = os.path.join(out_dir, page.replace('.html', '.png'))
        print(page, '->', dst, '(%d slots)' % slots)
        if not capture(url, dst, slots * SLOT_H):
            print('  GIVEUP', page)
        time.sleep(20)
    print('DONE')


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
