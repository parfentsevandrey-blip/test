"""Сбор выдачи funda in business по выбранным территориям через Scrapling.

python3 scrape.py OUT_DIR
Пишет OUT_DIR/listings.jsonl — по строке на объявление.
"""
import html as H
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
sys.path.append("/root/.local/lib/python3.11/site-packages")
from reportgen import funda_fetch as F  # noqa: E402
from scrapling.fetchers import Fetcher  # noqa: E402

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
RAW = OUT / "raw"
RAW.mkdir(exist_ok=True)

# города выбранных территорий (05.10.2026) — слаги funda in business
AREAS = {
    "eindhoven": ["eindhoven", "veldhoven", "son-en-breugel", "best"],
    "ai-schiphol": ["hoofddorp", "nieuw-vennep", "schiphol-rijk", "schiphol", "badhoevedorp",
                    "lijnden", "oude-meer", "rijsenhout", "aalsmeer"],
    "ai-amsterdam": ["amsterdam", "diemen", "almere"],
    "agriport": ["middenmeer", "hoorn-nh", "wieringerwerf"],
    "nijmegen": ["nijmegen", "wijchen"],
    "rotterdam": ["rotterdam"],
    # окрестности Хорна (Западная Фрисландия): аренда больших залов и доходность сданных
    "westfriesland": ["enkhuizen", "zwaag", "wognum", "medemblik", "purmerend", "heerhugowaard", "alkmaar"],
    # шоурумы и офисы в Хорне — ставки для частей Kernweg 1
    "hoorn-extra": ["hoorn-nh"],
}
SEARCHES_BY = {
    "westfriesland": [("bedrijfshal", "huur"), ("belegging", "koop"), ("bedrijfshal", "koop")],
    "hoorn-extra": [("kantoor", "huur"), ("winkel", "huur"), ("belegging", "koop")],
}
SEARCHES = [("bedrijfshal", "koop"), ("bedrijfshal", "huur"), ("belegging", "koop")]
BASE = "https://www.fundainbusiness.nl"


def get(url):
    for attempt in range(3):
        try:
            r = Fetcher.get(url, timeout=60)
            if r.status == 200 and F.WALL not in r.html_content:
                return r.html_content
            print("  status", r.status, url, flush=True)
            if r.status == 404:
                return None
        except Exception as exc:
            print("  err", type(exc).__name__, url, flush=True)
        time.sleep(3 * (attempt + 1))
    return None


def search_urls(slug, kind, deal):
    urls, page = [], 1
    while page <= 12:
        url = f"{BASE}/{kind}/{slug}/{deal}/" + (f"p{page}/" if page > 1 else "")
        h = get(url)
        if not h:
            break
        found = [u for u in re.findall(r'"url":\s*"(https://www\.fundainbusiness\.nl/[^"]+/object-\d+-[^"]+/)"', h)]
        total = re.search(r'"results_total":(\d+)', h)
        new = [u for u in found if u not in urls]
        urls += new
        if not new or (total and len(urls) >= int(total.group(1))):
            break
        page += 1
        time.sleep(1)
    return urls


MONEY = r"€\s*([\d.]+)(?:,\d+)?"


def num(s):
    digits = s.replace(".", "") if s else ""
    return int(digits) if digits.isdigit() else None


def parse(url, h):
    feat = F.features(h)
    desc = F.full_description(url, h)
    lines = [l for l in feat.split("\n")]

    def after(label):
        for i, l in enumerate(lines):
            if l.strip() == label and i + 1 < len(lines):
                return lines[i + 1].strip()
        return None

    title = re.search(r"<title>(.*?)</title>", h, re.S)
    title = H.unescape(title.group(1)).strip() if title else ""
    rec = {"url": url, "title": title}
    vp = after("Vraagprijs") or ""
    hp = after("Huurprijs") or ""
    m = re.search(MONEY, vp)
    rec["price"] = num(m.group(1)) if m else None
    rec["price_text"] = vp
    m = re.search(MONEY, hp)
    rec["rent_text"] = hp
    rent = num(m.group(1)) if m else None
    if rent and "per jaar" in hp and "m²" not in hp:
        rec["rent_year"] = rent
    elif rent and "per maand" in hp and "m²" not in hp:
        rec["rent_year"] = rent * 12
    elif rent and "m²" in hp:
        rec["rent_m2"] = rent
    area = after("Oppervlakte") or ""
    m = re.search(r"([\d.]+)\s*m²", area)
    rec["area"] = num(m.group(1)) if m else None
    for key, label in (("status", "Status"), ("year", "Bouwjaar"), ("period", "Bouwperiode"),
                       ("plot", "Perceel"), ("label", "Energielabel"), ("acceptance", "Aanvaarding"),
                       ("function", "Hoofdfunctie"), ("height", "Vrije hoogte")):
        rec[key] = after(label)
    low = desc.lower()
    rec["let"] = bool(re.search(r"verhuurd aan|huurder|huurovereenkomst loopt|huurinkomsten|huuropbrengst|"
                                r"aanvangsrendement|\bbar\b|belegging", low))
    rec["vacant"] = bool(re.search(r"vrij van huur|leeg en ontruimd|ontruimd|direct beschikbaar|"
                                   r"per direct|leegstaand|vrij op te leveren", low))
    m = re.search(r"(?:huuropbrengst|huurinkomsten|huurstroom|jaarhuur|huurprijs)[^€]{0,80}€\s*([\d.]+)", low)
    rec["rent_in_text"] = num(m.group(1)) if m else None
    m = re.search(r"(?:bruto aanvangsrendement|\bbar\b)[^\d]{0,40}(\d+[.,]\d+)\s*%", low)
    rec["bar_text"] = float(m.group(1).replace(",", ".")) if m else None
    rec["desc_len"] = len(desc)
    rec["desc"] = desc[:6000]
    rec["photos"] = len(F.business_gallery(h))
    return rec


ONLY = sys.argv[2:]
seen = set()
TAG = "-".join(ONLY) if ONLY else "all"
path = OUT / f"listings-{TAG}.jsonl"
out = path.open("a", encoding="utf-8")
done = {json.loads(l)["url"] for l in path.read_text().splitlines() if l.strip()} if path.exists() else set()
for territory, slugs in AREAS.items():
    if ONLY and territory not in ONLY:
        continue
    for slug in slugs:
        for kind, deal in SEARCHES_BY.get(territory, SEARCHES):
            urls = search_urls(slug, kind, deal)
            print(f"{territory}/{slug} {kind}/{deal}: {len(urls)}", flush=True)
            for u in urls:
                if u in seen or u in done:
                    seen.add(u)
                    continue
                seen.add(u)
                h = get(u)
                time.sleep(1)
                if not h:
                    continue
                name = re.sub(r"\W+", "-", u)[-90:]
                (RAW / f"{name}.html").write_text(h, encoding="utf-8")
                rec = parse(u, h)
                rec.update({"territory": territory, "slug": slug, "kind": kind, "deal": deal})
                out.write(json.dumps(rec, ensure_ascii=False) + "\n")
                out.flush()
print("done", len(seen))
