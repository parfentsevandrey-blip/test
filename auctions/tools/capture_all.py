#!/usr/bin/env python3
"""Capture grids and the map for several objects in one paced run.

The reader throttles hard, so this walks the work serially with the retry and
back-off already built into capture_grids/capture_map rather than firing
requests in parallel.

    python3 tools/capture_all.py <branch> obj2 obj3 obj4 obj5
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import capture_grids                                        # noqa: E402
import capture_map                                          # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main(branch, obj_ids):
    for obj_id in obj_ids:
        print('==== %s' % obj_id, flush=True)
        lot = json.load(open(os.path.join(ROOT, 'assets', obj_id, 'lot.json'), encoding='utf-8'))
        try:
            capture_map.main(lot['lat'], lot['lon'], lot['metro'],
                             os.path.join(ROOT, 'assets', obj_id, 'map.png'))
        except SystemExit as exc:
            print('  map failed:', exc, flush=True)
        time.sleep(20)
        capture_grids.main(obj_id, branch)
    print('ALL DONE', flush=True)


if __name__ == '__main__':
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2:])
