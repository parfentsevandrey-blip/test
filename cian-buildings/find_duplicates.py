"""Find other CIAN offers for the same buildings.

One building is usually listed by several brokers, each with their own
photos, description and sometimes price. For every offer in listings.txt
this searches CIAN twice — by the house id and by a small polygon around the
coordinates (brokers spell addresses differently) — and keeps sale offers
whose area is close to the original's. The result, build/search/candidates.json,
is reviewed by hand; confirmed duplicates go to data/duplicates.json.

    .venv/bin/python find_duplicates.py
"""
import json
import math
import os
import random
import re
import sys
import time

from scrapling.fetchers import Fetcher

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "build", "search")
STATE_RE = re.compile(r"_cianConfig\['(legacy-commercial-serp-frontend|frontend-serp)'\]\s*=\s*\(?window\._cianConfig\[[^\]]+\]"
                      r"\s*\|\|\s*\[\]\)?\.concat\((\[.*?\])\);\s*</script>", re.S)

# Search settings per report type.
KINDS = {
    "commercial": {"offer_type": "offices", "radius_m": 250, "area_tol": 0.2},
    "flat": {"offer_type": "flat", "radius_m": 120, "area_tol": 0.06},
}


def get(url):
    for attempt in range(5):
        try:
            p = Fetcher.get(url, impersonate=random.choice(["chrome", "safari"]), stealthy_headers=True, timeout=40)
            if p.status == 200 and len(p.body) > 50_000:
                return p.body.decode("utf-8", "ignore")
            print("   status", p.status, "- retry")
        except Exception as e:
            print("   error", str(e)[:70])
        time.sleep(4 + attempt * 4)
    return None


def serp_offers(html):
    m = STATE_RE.search(html or "")
    if not m:
        return [], 0
    for item in json.loads(m.group(2)):
        if item.get("key") in ("initialState", "defaultState") and "results" in (item.get("value") or {}):
            r = item["value"]["results"]
            return r.get("offers") or [], r.get("totalOffers") or 0
    return [], 0


def search(base, kind, extra):
    found, page = {}, 1
    while True:
        url = f"https://www.cian.ru/cat.php?deal_type=sale&engine_version=2&offer_type={KINDS[kind]['offer_type']}&region=1&{extra}&p={page}"
        offers, total = serp_offers(get(url))
        for o in offers:
            found[o["cianId"]] = o
        time.sleep(random.uniform(2, 4))
        if not offers or len(found) >= total or page >= 5:
            return found
        page += 1


def dist_m(a, b):
    kx = 111320 * math.cos(math.radians(a["lat"]))
    return math.hypot((a["lat"] - b["lat"]) * 110540, (a["lng"] - b["lng"]) * kx)


def summary(o, target):
    bt = o.get("bargainTerms") or {}
    user = o.get("user") or {}
    return {
        "id": o["cianId"], "url": o.get("fullUrl"), "category": o.get("category"),
        "area": float(o.get("totalArea") or 0), "floor": o.get("floorNumber"),
        "price": bt.get("priceRur") or bt.get("price"), "currency": bt.get("currency"),
        "address": o["geo"].get("userInput"), "dist": round(dist_m(o["geo"]["coordinates"], target["coords"])),
        "seller": user.get("agencyName") or user.get("companyName") or user.get("name"),
        "photos": len(o.get("photos") or []), "added": o.get("added"),
        "description": (o.get("description") or "")[:400],
    }


def main(kind):
    cfg = KINDS[kind]
    os.makedirs(OUT, exist_ok=True)
    targets = json.load(open(os.path.join(HERE, "data", "listings.json"), encoding="utf-8"))
    raw_states = os.path.join(HERE, "build", "json")
    result = {}
    for t in targets:
        st = json.load(open(os.path.join(raw_states, f"{t['id']}.json"), encoding="utf-8"))
        house = next((a.get("id") for a in st["offerData"]["offer"]["geo"]["address"] if a["type"] == "house"), None)
        lat, lng = t["coords"]["lat"], t["coords"]["lng"]
        dlat = cfg["radius_m"] / 110540
        dlng = cfg["radius_m"] / (111320 * math.cos(math.radians(lat)))
        poly = ",".join(f"{x:.5f}_{y:.5f}" for x, y in
                        [(lng - dlng, lat - dlat), (lng + dlng, lat - dlat), (lng + dlng, lat + dlat), (lng - dlng, lat + dlat)])
        print(f"#{t['order']} {t['address']} (house {house})")
        found = {}
        if house:
            found.update(search(t, kind, f"house%5B0%5D={house}"))
        found.update(search(t, kind, "in_polygon%5B1%5D=" + poly.replace(",", "%2C")))
        area = t["area"]["total"] if isinstance(t["area"], dict) else t["area"]
        keep = [summary(o, t) for o in found.values()
                if o["cianId"] != t["id"] and abs(float(o.get("totalArea") or 0) - area) <= cfg["area_tol"] * area]
        keep.sort(key=lambda c: (abs(c["area"] - area), c["dist"]))
        print(f"   {len(found)} offers around, {len(keep)} with similar area")
        result[str(t["id"])] = {"target": {"id": t["id"], "address": t["address"], "area": area,
                                           "price": t["price"], "floor": t.get("floor")},
                                "candidates": keep}
        json.dump(result, open(os.path.join(OUT, "candidates.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "commercial")
