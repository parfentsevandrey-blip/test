"""Download the full pages of the duplicate offers listed in data/duplicates.json.

    .venv/bin/python fetch_duplicates.py
"""
import json
import os
import random
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scrape import fetch  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "build", "raw_dups")


def main():
    os.makedirs(OUT, exist_ok=True)
    dups = json.load(open(os.path.join(HERE, "data", "duplicates.json"), encoding="utf-8"))
    for key, ids in dups.items():
        if key.startswith("_"):
            continue
        for cid in ids:
            path = os.path.join(OUT, f"{cid}.html")
            if os.path.exists(path):
                continue
            body = fetch(f"https://www.cian.ru/sale/commercial/{cid}/")
            if body:
                open(path, "wb").write(body)
                print(key, cid, "ok")
            else:
                print(key, cid, "FAILED")
            time.sleep(random.uniform(2, 4))


if __name__ == "__main__":
    main()
