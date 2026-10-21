"""Шаг 1: собрать выдачу по нашим районам и окрестностям и страницы кандидатов.

    python3 -I flipfinder/harvest.py RUN_DIR

Пишет RUN_DIR/cards.jsonl (вся выдача: продажа, аренда, сданные объекты) и
RUN_DIR/listings.jsonl (страницы кандидатов на покупку и сданных объектов).
С --only=город,город дособирает только эти города и дописывает их к готовому прогону.
С --fresh=Ч выдача поиска берётся заново, если копии в кэше больше Ч часов (по умолчанию 24).
С --no-invest сданные объекты (belegging) не собираются — они нужны только для справки инвестору.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from flipfinder import config as C  # noqa: E402
from flipfinder import fetch  # noqa: E402

SEARCHES = (("bedrijfshal", "koop"), ("bedrijfshal", "huur"), ("belegging", "koop"))


def towns(only: set[str] | None = None):
    for ring, table in (("zone", C.ZONES), ("nearby", C.NEARBY)):
        for terr, slugs in table.items():
            for s in slugs:
                if not only or s in only:
                    yield terr, s, ring


def main(run_dir: Path, only: set[str] | None = None, fresh: float = 24, invest: bool = True) -> None:
    old_cards = fetch.load_jsonl(run_dir / "cards.jsonl") if only else []
    old_rows = fetch.load_jsonl(run_dir / "listings.jsonl") if only else []
    all_cards = []
    for terr, slug, ring in towns(only):
        for kind, deal in SEARCHES if invest else SEARCHES[:2]:
            found = fetch.search(slug, kind, deal, max_age_h=fresh)
            for c in found:
                c.update(territory=terr, ring=ring)
            all_cards += found
            print(f"{terr:12} {ring:6} {slug:26} {kind}/{deal}: {len(found)}", flush=True)
    fetch.save_jsonl(old_cards + all_cards, run_dir / "cards.jsonl")

    known = {r["url"] for r in old_rows}
    wanted = {}
    for c in all_cards:
        if c["url"] in known:
            continue
        if c["deal"] != "koop":
            continue
        price, area = c.get("price"), c.get("area")
        if c["kind"] == "bedrijfshal" and (price is None or price <= C.PRICE_MAX) and (area is None or area >= C.AREA_MIN):
            wanted.setdefault(c["url"], c)
        if c["kind"] == "belegging" and price and price <= 4_000_000:
            wanted.setdefault(c["url"], c)
    rows = old_rows
    for i, (url, c) in enumerate(wanted.items(), 1):
        rec = fetch.listing(url)
        if rec:
            rec.update({k: c[k] for k in ("territory", "ring", "slug", "kind", "deal")})
            rows.append(rec)
        if i % 25 == 0:
            print(f"  страницы: {i}/{len(wanted)}", flush=True)
            fetch.save_jsonl(rows, run_dir / "listings.jsonl")
    fetch.save_jsonl(rows, run_dir / "listings.jsonl")
    print("готово:", len(old_cards) + len(all_cards), "карточек,", len(rows), "страниц", flush=True)


if __name__ == "__main__":
    only = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--only=")), None)
    fresh = next((float(a.split("=", 1)[1]) for a in sys.argv if a.startswith("--fresh=")), 24)
    main(Path(sys.argv[1]), set(only.split(",")) if only else None, fresh, "--no-invest" not in sys.argv)
