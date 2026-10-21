"""Присланные объекты: та же модель, что в отборе, но без отсева.

    python3 -I flipfinder/manual.py RUN_DIR URL [URL ...]

Ссылки можно давать на английскую версию funda in business (/en/). Страница объекта
загружается заново, считается так же, как в screen.py, и записывается в RUN_DIR/manual.json;
сама страница — в RUN_DIR/listings.jsonl. Какие наши условия объект не выполняет, скрипт
печатает — это для разговора с заказчиком, в отчёт не идёт.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.append("/root/.local/lib/python3.11/site-packages")
from flipfinder import config as C  # noqa: E402
from flipfinder import estimate as E  # noqa: E402
from flipfinder import fetch, screen  # noqa: E402


def dutch(url: str) -> str:
    return re.sub(r"fundainbusiness\.nl/en/", "fundainbusiness.nl/", url.strip())


def main(run_dir: Path, urls: list[str]) -> None:
    cards = fetch.load_jsonl(run_dir / "cards.jsonl")
    rows = {r["url"]: r for r in fetch.load_jsonl(run_dir / "listings.jsonl")}
    path = run_dir / "manual.json"
    done = {c["url"]: c for c in json.loads(path.read_text(encoding="utf-8"))} if path.exists() else {}
    shown = screen.shown_slugs()
    for url in map(dutch, urls):
        rec = fetch.listing(url, max_age_h=6)
        if not rec:
            print("не загрузилось:", url)
            continue
        kind = re.search(r"\.nl/([a-z]+)/", url).group(1)
        rec.update(kind=kind, deal="koop", slug=re.search(r"\.nl/[a-z]+/([a-z0-9-]+)/object-", url).group(1),
                   territory="extra", ring="extra")
        rows[url] = dict(rec)
        rec["area"], area_note = E.checked_area(rec)
        why = E.reject(rec, shown)
        cand, warn = screen.assess(rec, cards, area_note, strict=False)
        if cand is None:
            print("не посчитать:", url, warn)
            continue
        if cand["breakeven_discount"] is not None and cand["breakeven_discount"] > C.THRESHOLD:
            warn.append(f"для выхода в ноль нужна скидка {cand['breakeven_discount']:.1%}, больше {C.THRESHOLD:.0%}")
        if cand["safe_discount"] is None or cand["safe_discount"] > C.OFFER_DISCOUNT_MAX:
            warn.append(f"чтобы ни один сценарий не дал убытка, нужна скидка больше {C.OFFER_DISCOUNT_MAX:.0%}"
                        + (f" ({cand['safe_discount']:.1%})" if cand["safe_discount"] else ""))
        cand["checks"] = why + warn
        done[url] = cand
        print(f"{cand['title'][-60:]}\n  в ноль {cand['breakeven_discount']:.1%}, предложение {cand['offer']:,.0f} "
              f"(−{cand['offer_discount']:.1%}), продажа {cand['sale']:,.0f}, чистая прибыль {cand['net_at_offer']:,.0f}, "
              f"{'юнит' if cand['info']['unit'] else 'здание'}, {cand['place']['ring']}\n  не выполняет: {cand['checks'] or 'нет'}")
    path.write_text(json.dumps(list(done.values()), ensure_ascii=False, indent=1), encoding="utf-8")
    fetch.save_jsonl(list(rows.values()), run_dir / "listings.jsonl")


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2:])
