#!/usr/bin/env python3
"""Refresh the directory seed shipped in the APK.

A first connect over a slow transport is mostly one thing: tor downloading the
network directory — the consensus and a microdescriptor for every relay — over a
path that manages tens of kilobytes a second. Measured on one user's Wi-Fi at
68 KB/s, that alone is minutes. This script gathers what tor would download and
puts it in the APK, in the exact files tor keeps in its DataDirectory, so a
fresh install starts with a verified consensus and every microdescriptor it
needs, and goes straight to building a circuit.

Sources are CollecTor, the Tor Project's public archive of directory documents,
over plain HTTPS. Three files come out, in tor's own on-disk format:

  cached-microdesc-consensus  the latest consensus, verbatim
  cached-microdescs           every microdescriptor the consensus lists, each
                              preceded by the @last-listed annotation tor writes
  cached-certs                the current authority key certificates, without
                              which tor cannot verify the consensus and ignores it

Verified locally: a tor started on these three files logs "Reloaded
microdescriptor cache. Found 9453 descriptors" and accepts the consensus with
seven good signatures, then proceeds directly to "Connecting to a relay".

The consensus goes stale within a day and tor replaces it (a diff, when the
directory still has one for this base; a full fetch otherwise). The
microdescriptors, which are the bulk, stay valid for weeks. Run this before
every release build.
"""

import base64
import hashlib
import io
import lzma
import os
import re
import sys
import time
import urllib.request
from html.parser import HTMLParser

BASE = "https://collector.torproject.org/recent/relay-descriptors/"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "seed")
MICRODESC_FILES = 72  # about three days of hourly files; enough to cover every listed relay


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.hrefs = []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            for name, value in attrs:
                if name == "href" and value and not value.startswith(("/", "?", "..")):
                    self.hrefs.append(value)


def fetch(url, retries=4):
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(url, timeout=120) as response:
                return response.read()
        except Exception as error:  # noqa: BLE001 - retry anything, it is a download
            if attempt == retries - 1:
                raise
            time.sleep(2 * (attempt + 1))
            print(f"  retry {url}: {error}", file=sys.stderr)


def listing(path, suffix):
    parser = Links()
    parser.feed(fetch(BASE + path).decode("utf-8", "replace"))
    return sorted(h for h in parser.hrefs if h.endswith(suffix))


def strip_type(text):
    return re.sub(r"(?m)^@type[^\n]*\n", "", text)


def main():
    print("consensus…")
    latest = listing("microdescs/consensus-microdesc/", "-consensus-microdesc")[-1]
    consensus = strip_type(fetch(BASE + "microdescs/consensus-microdesc/" + latest).decode("utf-8", "replace"))
    wanted = [line.split()[1] for line in consensus.splitlines() if line.startswith("m ")]
    print(f"  {latest}: {len(wanted)} relays listed")

    print("microdescriptors…")
    have = {}
    files = listing("microdescs/micro/", "-microdescs")[-MICRODESC_FILES:]
    for index, name in enumerate(files, 1):
        text = fetch(BASE + "microdescs/micro/" + name).decode("utf-8", "replace")
        for chunk in re.split(r"(?m)^(?=onion-key)", text):
            if not chunk.startswith("onion-key"):
                continue
            body = re.split(r"(?m)^@", chunk)[0]
            digest = base64.b64encode(hashlib.sha256(body.encode()).digest()).decode().rstrip("=")
            have[digest] = body
        if index % 12 == 0:
            print(f"  {index}/{len(files)} files, {len(have)} descriptors")
    covered = [d for d in wanted if d in have]
    print(f"  coverage {len(covered)}/{len(wanted)}")
    if len(covered) < 0.95 * len(wanted):
        sys.exit("coverage too low; widen MICRODESC_FILES")
    stamp = time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime())
    microdescs = "".join(f"@last-listed {stamp}\n{have[d]}" for d in covered)

    print("certificates…")
    docs = {}
    for name in listing("certs/", "-certs"):
        text = strip_type(fetch(BASE + "certs/" + name).decode("utf-8", "replace"))
        for doc in re.findall(r"(?s)(dir-key-certificate-version 3\n.*?-----END SIGNATURE-----\n)", text):
            fingerprint = re.search(r"^fingerprint (\S+)", doc, re.M).group(1)
            published = re.search(r"^dir-key-published (.+)$", doc, re.M).group(1)
            if fingerprint not in docs or published > docs[fingerprint][0]:
                docs[fingerprint] = (published, doc)
    print(f"  {len(docs)} authority certificate(s)")
    if len(docs) < 5:
        sys.exit("too few certificates; tor needs five recognised signatures")
    certs = "".join(doc for _, doc in docs.values())

    os.makedirs(OUT, exist_ok=True)
    for name, text in (
        ("cached-microdesc-consensus", consensus),
        ("cached-microdescs", microdescs),
        ("cached-certs", certs),
    ):
        path = os.path.join(OUT, name + ".xz")
        with lzma.open(path, "wb", preset=9 | lzma.PRESET_EXTREME) as out:
            out.write(text.encode())
        print(f"  {name}.xz  {os.path.getsize(path) / 1e6:.2f} MB  (raw {len(text) / 1e6:.1f} MB)")
    with open(os.path.join(OUT, "STAMP"), "w") as out:
        out.write(f"{latest}\n")
    print("done")


if __name__ == "__main__":
    main()
