"""Шаг 2: отбор и расчёт. Каждое здание на продаже проходит одни и те же фильтры и одну модель.

    python3 -I flipfinder/screen.py RUN_DIR

Порядок в списке: сначала объекты, которым для выхода в ноль нужна скидка не больше
порога (11 %), среди них — наши районы, затем окрестности; внутри — по размеру скидки.
Пишет RUN_DIR/candidates.json (все посчитанные), RUN_DIR/rejected.json (с причинами)
и RUN_DIR/shortlist.json (прошедшие порог, лучшие первыми).
"""
from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.append("/root/.local/lib/python3.11/site-packages")
from flipfinder import config as C  # noqa: E402
from flipfinder import estimate as E  # noqa: E402
from flipfinder import fetch, locate  # noqa: E402
from flipfinder.model import Deal, evaluate  # noqa: E402

ADDRESS = re.compile(r"te koop(?: en te huur)?: (.+?) (\d{4} ?[A-Z]{2}) (.+?) \[")
PREFILTER = 0.25          # точную привязку и маршруты считаем только тем, кому нужно не больше 25 % скидки


def shown_slugs() -> set[str]:
    reg = json.loads(C.REGISTRY.read_text(encoding="utf-8"))
    return {o["slug"].replace("-", "") for o in reg["objects"]}


def address(rec: dict) -> dict | None:
    m = ADDRESS.search(rec.get("title") or "")
    return {"street": m.group(1), "postcode": m.group(2), "city": m.group(3)} if m else None


def model_for(rec: dict, cards: list[dict], here=None) -> tuple[Deal | None, dict]:
    rent = E.rent(cards, rec, here)
    oc = E.owner_costs(rec.get("desc") or "")
    ey, notes = E.exit_yield(rec, oc)
    info = {"rent": rent, "owner": oc, "exit_yield": ey, "exit_notes": notes, "year": E.year_of(rec),
            "new_build": E.new_build(rec), "shell": bool(E.SHELL.search(rec.get("desc") or "")),
            "condition": E.condition(rec)}
    if not rent["rent"]:
        return None, info
    deal = Deal(ask=rec["price"], area=rec["area"], rent=round(rent["rent"]), exit_yield=ey,
                new_build=info["new_build"], shell=info["shell"], owner_costs=oc["canon"] + oc["vve"])
    return deal, info


def rank_key(c: dict):
    zone_first = 0 if c["place"]["ring"] == "zone" else 1
    return (0 if c["passes"] else 1, 0 if C.NEARBY_OVER_ZONE else zone_first, c["breakeven_discount"])


def main(run_dir: Path, include_shown: bool = False) -> None:
    cards = fetch.load_jsonl(run_dir / "cards.jsonl")
    listings = [r for r in fetch.load_jsonl(run_dir / "listings.jsonl") if r.get("kind") == "bedrijfshal"]
    shown = set() if include_shown else shown_slugs()
    seen, done, rejected = set(), [], []
    for rec in listings:
        if rec["url"] in seen:
            continue
        seen.add(rec["url"])
        rec["area"], area_note = E.checked_area(rec)
        why = E.reject(rec, shown)
        if why:
            rejected.append({"url": rec["url"], "title": rec["title"], "why": why})
            continue
        addr = address(rec)
        geo = locate.geocode(f"{addr['street']}, {addr['postcode']} {addr['city']}") if addr else None
        if not geo:
            rejected.append({"url": rec["url"], "title": rec["title"], "why": ["адрес не найден"]})
            continue
        lat, lon, _ = geo
        deal, info = model_for(rec, cards, (lat, lon))
        if not deal:
            rejected.append({"url": rec["url"], "title": rec["title"], "why": ["нет данных об аренде рядом"]})
            continue
        ev = evaluate(deal)
        if ev["breakeven_discount"] is None or ev["breakeven_discount"] > PREFILTER:
            rejected.append({"url": rec["url"], "title": rec["title"],
                             "why": [f"нужна скидка больше {PREFILTER:.0%}"],
                             "discount": ev["breakeven_discount"]})
            continue
        info["vacant"] = E.vacant(cards, rec, (lat, lon))
        cap = info["vacant"]["m2"] and C.VACANT_CAP * info["vacant"]["m2"] * rec["area"]
        if cap and ev["base"]["sale"] > cap:
            # инвестор не заплатит за сданное здание намного больше, чем стоят свободные рядом
            deal = deal.at(exit_yield=math.ceil((deal.rent - deal.owner_costs) / cap / 0.0005) * 0.0005)
            info["exit_yield"] = deal.exit_yield
            info["exit_notes"].append(f"цена продажи ограничена {C.VACANT_CAP:.0%} цен свободных складов рядом")
            ev = evaluate(deal)
            if ev["breakeven_discount"] is None or ev["breakeven_discount"] > PREFILTER:
                rejected.append({"url": rec["url"], "title": rec["title"], "why": [f"нужна скидка больше {PREFILTER:.0%}"],
                                 "discount": ev["breakeven_discount"]})
                continue
        place = locate.place(lat, lon)
        road = locate.motorway(lat, lon)
        if place["ring"] == "far":
            rejected.append({"url": rec["url"], "title": rec["title"], "why": ["далеко от наших районов"]})
            continue
        if not road or road["km"] > C.MOTORWAY_KM:
            rejected.append({"url": rec["url"], "title": rec["title"],
                             "why": [f"до магистрали больше {C.MOTORWAY_KM:.0f} км"], "road": road})
            continue
        done.append({
            "url": rec["url"], "title": rec["title"], "address": addr, "lat": lat, "lon": lon, "slug": rec["slug"],
            "territory": rec["territory"], "place": place, "road": road, "price": rec["price"], "area": rec["area"],
            "area_note": area_note,
            "info": {**info, "rent": {k: v for k, v in info["rent"].items() if k != "comps"},
                     "comps": [{k: c.get(k) for k in ("title", "area", "rent_m2", "url", "km")} for c in info["rent"]["comps"]]},
            "deal": ev["base"]["deal"], "breakeven": ev["breakeven"], "breakeven_discount": ev["breakeven_discount"],
            "offer": ev["offer"], "passes": ev["passes"], "profit_at_ask": ev["at_ask"]["profit"],
            "net_at_offer": ev["base"]["net"], "sale": ev["base"]["sale"],
        })
    done.sort(key=rank_key)
    (run_dir / "candidates.json").write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")
    (run_dir / "rejected.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=1), encoding="utf-8")
    short = [c for c in done if c["passes"]]
    (run_dir / "shortlist.json").write_text(json.dumps(short, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"страниц {len(seen)}, посчитано {len(done)}, прошли порог {len(short)}, отсеяно {len(rejected)}")
    for c in done[:25]:
        print(f"{'✓' if c['passes'] else ' '} {c['breakeven_discount'] * 100:5.1f}%  {c['place']['ring']:6} "
              f"{c['territory']:12} €{c['price']:>9,} {c['area']:>5} м² аренда €{c['deal']['rent']:>7,} "
              f"BAR {c['deal']['exit_yield'] * 100:.2f}  A {c['road']['km']} км  {c['title'][-70:]}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), include_shown="--include-shown" in sys.argv)
