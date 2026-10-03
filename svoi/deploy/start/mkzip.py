#!/usr/bin/env python3
"""mkzip.py OUT.zip FOLDER — zip FOLDER (kept as the top-level directory) for Windows users.

Info-ZIP's `zip` stores a non-ASCII file name in the OEM code page, so Explorer shows «КАК ЗАПУСТИТЬ.txt»
as garbage. Python's zipfile marks such names as UTF-8 (general purpose flag bit 11), which Windows
Explorer, 7-Zip and macOS/Linux unzippers all understand."""
import os
import sys
import zipfile


def main(out: str, folder: str) -> None:
    folder = folder.rstrip("/")
    parent = os.path.dirname(os.path.abspath(folder))
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for root, dirs, files in os.walk(folder):
            dirs.sort()
            for f in sorted(files):
                full = os.path.join(root, f)
                info = zipfile.ZipInfo.from_file(full, os.path.relpath(full, parent))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(full, "rb") as fh:
                    z.writestr(info, fh.read(), compresslevel=9)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
