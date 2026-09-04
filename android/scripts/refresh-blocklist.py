#!/usr/bin/env python3
"""Rebuilds app/src/main/assets/blocklist/hosts.xz from a hosts-format file.

Usage: refresh-blocklist.py hosts.txt

The source is Steven Black's unified hosts list (MIT):
  curl -L https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts -o hosts.txt

Only the names are kept, one per line, lower-cased and de-duplicated; the
native tunnel answers "no such name" for each of them and for their
subdomains. The STAMP file carries the date, and the app re-unpacks the list
when the stamp changes.
"""
import datetime
import lzma
import pathlib
import re
import sys

LOCAL = {
    "localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0",
    "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
    "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
}


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    names = set()
    for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) < 2 or parts[0] not in ("0.0.0.0", "127.0.0.1"):
            continue
        for name in parts[1:]:
            if name.startswith("#"):
                break
            name = name.lower().rstrip(".")
            if name in LOCAL or "." not in name:
                continue
            if not re.fullmatch(r"[a-z0-9._-]+", name):
                continue
            names.add(name)
    if len(names) < 10_000:
        sys.exit(f"only {len(names)} names; refusing to ship a list that small")

    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/blocklist"
    out.mkdir(parents=True, exist_ok=True)
    body = ("\n".join(sorted(names)) + "\n").encode()
    (out / "hosts.xz").write_bytes(lzma.compress(body, preset=9 | lzma.PRESET_EXTREME))
    (out / "STAMP").write_text(datetime.date.today().isoformat() + "\n")
    print(f"{len(names)} names, {len(body) // 1024} KB raw, "
          f"{(out / 'hosts.xz').stat().st_size // 1024} KB packed")


if __name__ == "__main__":
    main()
