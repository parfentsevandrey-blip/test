"""Сданные объекты на продаже (belegging/koop) в городах вокруг кандидата: python3 -I inv_scrape.py OUT_DIR slug ..."""
import json
import re
import sys
import time
from pathlib import Path

src = (Path(__file__).resolve().parent / "scrape.py").read_text()
head = src.split("ONLY = sys.argv[2:]")[0]
slugs = sys.argv[2:]
g = {"__file__": str(Path(__file__).resolve().parent / "scrape.py"), "__name__": "scr"}
exec(compile(head, "scrape_head", "exec"), g)
OUT = Path(sys.argv[1])
out = (OUT / "inv.jsonl").open("a", encoding="utf-8")
for slug in slugs:
    urls = g["search_urls"](slug, "belegging", "koop")
    print(slug, len(urls), flush=True)
    for u in urls:
        h = g["get"](u)
        time.sleep(1)
        if not h:
            continue
        rec = g["parse"](u, h)
        rec.update({"slug": slug, "kind": "belegging", "deal": "koop"})
        out.write(json.dumps(rec, ensure_ascii=False) + "\n")
        out.flush()
print("done")
