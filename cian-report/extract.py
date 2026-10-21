"""Parse saved CIAN pages into data/listings.json and prepare images.

Every offer page embeds its full state as JSON in
window._cianConfig['frontend-offer-card'] (key "defaultState"); that is
the only source used here. Photos are downloaded from the CIAN CDN and
downscaled so the PDF stays a sensible size.
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

ROOMS = {1: "1-комнатная", 2: "2-комнатная", 3: "3-комнатная", 4: "4-комнатная", 5: "5-комнатная"}
SALE_TYPES = {"free": "Свободная продажа", "alternative": "Альтернатива",
              "dupt": "Переуступка", "fz214": "ДДУ (214-ФЗ)", "pdkp": "Предварительный ДКП"}
MATERIALS = {"brick": "Кирпичный", "monolith": "Монолитный", "monolithBrick": "Монолитно-кирпичный",
             "panel": "Панельный", "block": "Блочный"}


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
        subprocess.run(["curl", "-sS", "-m", "60", "--retry", "6", "--retry-all-errors", "--retry-delay", "2", "-o", path, url], check=False)
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


def listing(state, order):
    od = state["offerData"]
    o = od["offer"]
    geo = o["geo"]
    addr = {a["type"]: a for a in geo["address"]}
    b = o.get("building") or {}
    nb = o.get("newbuilding") or {}
    jk_geo = geo.get("jk") or {}
    no = od.get("newObject")

    if o.get("flatType") == "openPlan":
        kind = "Свободная планировка"
    elif o.get("flatType") == "studio":
        kind = "Студия"
    else:
        kind = ROOMS.get(o.get("roomsCount"), f"{o.get('roomsCount')}-комнатная")

    price = o["bargainTerms"]["price"]
    total = num(o.get("totalArea"))
    photos = [p for p in o["photos"] if not p.get("isLayout")]
    layouts = [p for p in o["photos"] if p.get("isLayout")]

    jk = None
    if jk_geo.get("id"):
        jk = {
            "id": jk_geo["id"],
            "name": nb.get("name") or jk_geo.get("name"),
            "developer": (jk_geo.get("developer") or {}).get("name"),
            "url": jk_geo.get("fullUrl"),
            "site": jk_geo.get("webSiteUrl"),
            "house": (nb.get("house") or {}).get("name"),
            "finish": (nb.get("house") or {}).get("finishDate"),
            "rating": od.get("newbuildingRating"),
            "cover": (nb.get("newbuildingFeatures") or {}).get("firstImageUrl"),
            "newObject": None,
        }
        if no:
            jk["newObject"] = {
                "status": no.get("status"),
                "class": no.get("newbuildingClass"),
                "specs": no.get("specifications", []),
                "advantages": [{"title": a.get("title"), "type": a.get("advantageType"),
                                "description": a.get("description"),
                                "images": [i["url"] for i in a.get("images", [])]}
                               for a in no.get("advantages", [])],
                "developer": (no.get("developer") or {}).get("name"),
                "promos": [p.get("name") for p in no.get("promoInfos", [])],
            }

    bti = (od.get("bti") or {}).get("houseData") or {}
    agent = od.get("agent") or {}
    return {
        "order": order,
        "id": o["cianId"],
        "url": f"https://www.cian.ru/sale/flat/{o['cianId']}/",
        "kind": kind,
        "rooms": o.get("roomsCount"),
        "isNew": "newBuilding" in (o.get("category") or "") or any(
            f["value"] == "Новостройка" for g in od["features"] for f in g["features"]),
        "area": {"total": total, "living": num(o.get("livingArea")), "kitchen": num(o.get("kitchenArea"))},
        "floor": o.get("floorNumber"),
        "floors": b.get("floorsCount"),
        "price": price,
        "ppm2": round(price / total) if total else None,
        "mortgage": o["bargainTerms"].get("mortgageAllowed"),
        "saleType": SALE_TYPES.get(o["bargainTerms"].get("saleType"), o["bargainTerms"].get("saleType")),
        "address": ", ".join(x for x in [(addr.get("street") or {}).get("fullName"),
                                         (addr.get("house") or {}).get("fullName")] if x),
        "district": (addr.get("raion") or {}).get("fullName"),
        "okrug": (addr.get("okrug") or {}).get("fullName"),
        "coords": geo["coordinates"],
        "metro": [{"name": u["name"], "minutes": u.get("travelTime"), "by": u.get("travelType"),
                   "color": "#" + (u.get("lineColor") or "999999")}
                  for u in geo.get("undergrounds", [])][:3],
        "building": {"year": b.get("buildYear") or bti.get("yearRelease"),
                     "material": MATERIALS.get(b.get("materialType"), b.get("materialType")),
                     "ceiling": num(b.get("ceilingHeight")),
                     "flats": bti.get("flatCount"), "entrances": bti.get("entrances")},
        "features": [{"title": g["title"], "items": [[f["label"], f["value"]] for f in g["features"]]}
                     for g in od["features"]],
        "description": (o.get("description") or "").strip(),
        "photos": [p["fullUrl"] for p in photos],
        "layouts": [p["fullUrl"] for p in layouts],
        "jk": jk,
        "seller": {"name": agent.get("companyName") or agent.get("name"), "type": agent.get("accountType")},
        "published": o.get("humanizedEditDate"),
        "views": (od.get("stats") or {}).get("totalViewsFormattedString"),
        "priceChanges": [{"date": c["changeTime"][:10], "price": c["priceData"]["price"]}
                         for c in od.get("priceChanges") or []],
    }


def warn_duplicates(items):
    """One flat is often listed by several agents under different ids: flag
    offers in the same building on the same floor with near-equal area."""
    for i, a in enumerate(items):
        for b in items[i + 1:]:
            if (a["address"] == b["address"] and a["floor"] == b["floor"]
                    and abs((a["area"]["total"] or 0) - (b["area"]["total"] or 0)) < 1.5):
                print(f"WARNING: #{a['order']} ({a['id']}) and #{b['order']} ({b['id']}) may be the same flat "
                      "- compare their layouts")


def main():
    os.makedirs(IMG, exist_ok=True)
    os.makedirs(IMG_SRC, exist_ok=True)
    # The same offer pasted twice is kept once, in its first position.
    order = list(dict.fromkeys(re.findall(r"/flat/(\d+)", open(os.path.join(HERE, "listings.txt")).read())))
    items = [listing(page_state(os.path.join(RAW, f"{cid}.html")), i + 1) for i, cid in enumerate(order)]
    warn_duplicates(items)

    # Some offers omit the newObject block; borrow it from another offer in the same complex.
    by_jk = {it["jk"]["id"]: it["jk"]["newObject"] for it in items if it["jk"] and it["jk"]["newObject"]}
    for it in items:
        if it["jk"] and not it["jk"]["newObject"]:
            it["jk"]["newObject"] = by_jk.get(it["jk"]["id"])

    # Localise images (originals stay in the JSON for reference).
    urls = set()
    for it in items:
        urls.update(it["photos"])
        if it["jk"]:
            urls.update(filter(None, [it["jk"]["cover"]]))
            for a in (it["jk"]["newObject"] or {}).get("advantages", []):
                urls.update(a["images"][:3])
    with ThreadPoolExecutor(6) as pool:
        local = dict(zip(sorted(urls), pool.map(optimise, sorted(urls))))
    for it in items:
        it["photosLocal"] = [local[u] for u in it["photos"]]
        it["layoutsLocal"] = [optimise(u, max_side=2000, quality=90, trim=True) for u in it["layouts"]]
        if it["jk"]:
            it["jk"]["coverLocal"] = local.get(it["jk"]["cover"])
            for a in (it["jk"]["newObject"] or {}).get("advantages", []):
                a["imagesLocal"] = [local[u] for u in a["images"][:3]]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    print(f"{len(items)} listings, {len(urls)} images -> {OUT}")


if __name__ == "__main__":
    main()
