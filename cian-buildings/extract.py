"""Parse saved CIAN commercial offers into data/listings.json and prepare images.

Same source as the apartment report: the full offer state embedded as JSON
in window._cianConfig['frontend-offer-card'] (key "defaultState").
"""
import json
import os
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "build", "raw")
IMG_SRC = os.path.join(HERE, "build", "img_src")
IMG = os.path.join(HERE, "build", "img")
OUT = os.path.join(HERE, "data", "listings.json")

BUILDING_TYPES = {"officeCenter": "Офисный центр", "shoppingCenter": "Торговый центр", "free": "Свободного назначения",
                  "standaloneBuilding": "Отдельно стоящее здание", "businessCenter": "Бизнес-центр",
                  "administrativeBuilding": "Административное здание", "mansion": "Особняк"}
CONDITIONS = {"design": "Дизайнерский ремонт", "office": "Офисная отделка", "majorRepairsRequired": "Нужен капремонт",
              "cosmeticRepairsRequired": "Нужен косметический ремонт", "typical": "Типовой ремонт"}
LINES = {"first": "Первая", "second": "Вторая"}
CURRENCY = {"usd": "$", "eur": "€", "rur": "₽"}


def page_state(path):
    html = open(path, encoding="utf-8").read()
    m = re.search(r"_cianConfig\['frontend-offer-card'\]\s*=\s*\(?window\._cianConfig\['frontend-offer-card'\]"
                  r"\s*\|\|\s*\[\]\)?\.concat\((\[.*?\])\);\s*</script>", html, re.S)
    for item in json.loads(m.group(1)):
        if item.get("key") == "defaultState":
            return item["value"]
    raise ValueError(f"no defaultState in {path}")


def src_name(url):
    return re.sub(r"^https?://images\.cdn-cian\.ru/", "", url).replace("/", "_")


def ensure_src(url):
    path = os.path.join(IMG_SRC, src_name(url))
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        subprocess.run(["curl", "-sS", "-m", "60", "--retry", "6", "--retry-all-errors", "--retry-delay", "2",
                        "-o", path, url], check=False)
    return path


def trim_margins(im, threshold=170, min_dark=3, pad=0.03):
    """Crop the white margins around a floor plan so it can be shown larger.

    Only rows/columns with a few genuinely dark pixels count, so light dotted
    backgrounds and stray specks don't stretch the crop box."""
    gray = im.convert("L")
    w, h = gray.size
    px = gray.load()
    cols = [x for x in range(w) if sum(px[x, y] < threshold for y in range(0, h, 2)) >= min_dark]
    rows = [y for y in range(h) if sum(px[x, y] < threshold for x in range(0, w, 2)) >= min_dark]
    if not cols or not rows:
        return im
    px_, py_ = int(w * pad), int(h * pad)
    return im.crop((max(cols[0] - px_, 0), max(rows[0] - py_, 0),
                    min(cols[-1] + px_, w), min(rows[-1] + py_, h)))


def optimise(url, max_side=1700, quality=84, trim=False):
    """Download (if needed), downscale and re-encode as JPEG; return path relative to build/."""
    src = ensure_src(url)
    name = os.path.splitext(src_name(url))[0] + ("-plan" if trim else "") + ".jpg"
    dst = os.path.join(IMG, name)
    if not os.path.exists(dst):
        im = Image.open(src).convert("RGB")
        if trim:
            im = trim_margins(im)
        im.thumbnail((max_side, max_side), Image.LANCZOS)
        im.save(dst, "JPEG", quality=quality, optimize=True, progressive=True)
    return "img/" + name


def num(v):
    try:
        return float(str(v).replace(",", "."))
    except (TypeError, ValueError):
        return None


def listing(state, order, notes):
    od = state["offerData"]
    o = od["offer"]
    geo = o["geo"]
    addr = {a["type"]: a for a in geo["address"]}
    b = o.get("building") or {}
    bt = o["bargainTerms"]
    feats = {f["label"]: f["value"] for g in od["features"] for f in g["features"]}

    price = bt["prices"]["rur"]  # CIAN's own conversion when the offer is priced in $ or €
    area = num(o.get("totalArea"))
    tax = bt.get("tax") or {}
    if bt.get("vatType") == "included":
        vat = "НДС включён"
    elif tax.get("type") == "noTax":
        vat = "Без НДС (УСН)"
    else:
        vat = "НДС не включён"
    note = notes.get(str(o["cianId"]), {})
    # Floor plans are often uploaded as ordinary photos; notes.json lists them by index.
    plan_idx = set(note.get("plans", []))
    layouts = [p for k, p in enumerate(o["photos"]) if p.get("isLayout") or k in plan_idx]
    photos = [p for k, p in enumerate(o["photos"]) if not p.get("isLayout") and k not in plan_idx]
    hero = note.get("hero")
    if hero is not None:
        photos.insert(0, photos.pop([p["fullUrl"] for p in photos].index(o["photos"][hero]["fullUrl"])))
    agent = od.get("agent") or {}
    income = note.get("income")

    return {
        "order": order,
        "id": o["cianId"],
        "url": f"https://www.cian.ru/sale/commercial/{o['cianId']}/",
        "title": note.get("title") or "Здание",
        "purpose": feats.get("Возможное назначение") or BUILDING_TYPES.get(b.get("type"), "Свободного назначения"),
        "area": area,
        "floors": b.get("floorsCount"),
        "klass": (b.get("classType") or "").upper() or None,
        "ceiling": num(b.get("ceilingHeight")),
        "line": LINES.get(b.get("houseLineType")),
        "condition": CONDITIONS.get(o.get("conditionType")) or feats.get("Состояние"),
        "parking": feats.get("Парковка"),
        "price": price,
        "priceOrig": None if bt["currency"] == "rur" else f"{CURRENCY.get(bt['currency'], bt['currency'])}{bt['price'] / 1e6:.1f}".replace(".", ",") + " млн",
        "ppm2": round(price / area) if area else None,
        "vat": vat,
        "income": income,
        "yield": round(income["monthly"] * 12 / price * 100, 1) if income else None,
        "address": ", ".join(x for x in [(addr.get("street") or {}).get("fullName"),
                                         (addr.get("house") or {}).get("fullName")] if x),
        "district": (addr.get("raion") or {}).get("fullName"),
        "okrug": (addr.get("okrug") or {}).get("fullName"),
        "coords": geo["coordinates"],
        "metro": [{"name": u["name"], "minutes": u.get("travelTime"), "by": u.get("travelType"),
                   "color": "#" + (u.get("lineColor") or "999999")}
                  for u in geo.get("undergrounds", [])][:3],
        "features": [{"title": g["title"], "items": [[f["label"], f["value"]] for f in g["features"]]}
                     for g in od["features"]],
        "facts": note.get("facts", []),
        "tenants": note.get("tenants"),
        "flags": note.get("flags", []),
        "renders": note.get("renders"),
        "verdict": note.get("verdict"),
        "verdictText": note.get("verdictText"),
        "description": (o.get("description") or "").strip(),
        "photos": [p["fullUrl"] for p in photos],
        "layouts": [p["fullUrl"] for p in layouts],
        "seller": {"name": agent.get("companyName") or agent.get("name"), "type": agent.get("accountType")},
        "published": o.get("humanizedEditDate"),
        "views": (od.get("stats") or {}).get("totalViewsFormattedString"),
        "priceChanges": len(od.get("priceChanges") or []),
    }


def warn_duplicates(items):
    """The same building is often listed by several brokers: flag offers at
    the same address or within ~40 m with near-equal area."""
    for i, a in enumerate(items):
        for b in items[i + 1:]:
            close = (abs(a["coords"]["lat"] - b["coords"]["lat"]) < 0.0004
                     and abs(a["coords"]["lng"] - b["coords"]["lng"]) < 0.0006)
            if (a["address"] == b["address"] or close) and abs((a["area"] or 0) - (b["area"] or 0)) < 0.1 * (a["area"] or 1):
                print(f"WARNING: #{a['order']} ({a['id']}) and #{b['order']} ({b['id']}) may be the same building")


def main():
    os.makedirs(IMG, exist_ok=True)
    os.makedirs(IMG_SRC, exist_ok=True)
    notes = json.load(open(os.path.join(HERE, "data", "notes.json"), encoding="utf-8"))
    order = list(dict.fromkeys(re.findall(r"/commercial/(\d+)", open(os.path.join(HERE, "listings.txt")).read())))
    items = [listing(page_state(os.path.join(RAW, f"{cid}.html")), i + 1, notes) for i, cid in enumerate(order)]
    warn_duplicates(items)

    urls = sorted({u for it in items for u in it["photos"]})
    with ThreadPoolExecutor(6) as pool:
        local = dict(zip(urls, pool.map(optimise, urls)))
    for it in items:
        it["photosLocal"] = [local[u] for u in it["photos"]]
        it["layoutsLocal"] = [optimise(u, max_side=2200, quality=90, trim=True) for u in it["layouts"]]

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    print(f"{len(items)} buildings, {len(urls)} images -> {OUT}")


if __name__ == "__main__":
    main()
