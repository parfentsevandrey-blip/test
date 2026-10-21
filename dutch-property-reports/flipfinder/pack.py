"""Один файл с несколькими объектами: страница сравнения, затем по каждому — 5 страниц и фото.

    python3 -I flipfinder/pack.py RUN_DIR ЧАСТЬ_ССЫЛКИ [ЧАСТЬ_ССЫЛКИ ...]

Объекты берутся из RUN_DIR/candidates.json и manual.json (см. manual.py), подписи к фото и
заметки — из RUN_DIR/brief-<slug>/, как у brief.py. Файл кладётся в output/, объекты
записываются в реестр показанных.
"""
from __future__ import annotations

import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.append("/root/.local/lib/python3.11/site-packages")
import pymupdf  # noqa: E402

from flipfinder import brief, fetch  # noqa: E402
from flipfinder import config as C  # noqa: E402

ROWS = [("Цена продавца", "ask", ""), ("Предложение продавцу", "offer", ""), ("Скидка", "cut", ""),
        ("Продажа через 12 месяцев", "sale", ""), ("Чистая прибыль", "net", "key"), ("Итоговая стоимость владения за год", "own", ""),
        ("Доход на € 100 за год", "per100", ""), ("Аренда в месяц", "rent", ""),
        ("Доходность для инвестора", "yield", ""), ("До магистрали", "road", "")]


def intro(ctxs: list[dict]) -> str:
    plan = ("По каждому объекту — один и тот же план: покупка свободного здания со скидкой, косметический ремонт, "
            "сдача в аренду на 5 лет и через 12 месяцев продажа вместе с арендатором инвестору. ")
    scen = ("аренда на 10 % ниже, инвестор просит доходность на 0,5 % выше, арендатор находится на 3 месяца позже, "
            "ремонт обходится вдвое дороже.")
    cap = f"Скидка — не больше {C.OFFER_DISCOUNT_MAX * 100:.0f} %: больше продавцы обычно не уступают. "
    if all(c["summary"]["yield_set"] for c in ctxs):
        return plan + cap + "Доходность для инвестора — та, при которой сделка не уходит в убыток ни в одном сценарии: " + scen
    if not any(c["summary"]["yield_set"] for c in ctxs):
        return plan + cap + "Предложение продавцу — самая высокая цена, при которой ни один сценарий не даёт убытка: " + scen
    return (plan + cap + "Предложение продавцу — самая высокая цена, при которой ни один сценарий не даёт убытка; если "
            "для этого нужна скидка больше, доходность для инвестора снижена до уровня без убытка. Сценарии: " + scen)


def main(run_dir: Path, parts: list[str]) -> None:
    cands = brief.load_candidates(run_dir)
    listings = fetch.load_jsonl(run_dir / "listings.jsonl")
    stamp = date.today().strftime("%d.%m.%y")
    pdfs, ctxs, picks = [], [], []
    for part in parts:
        pick = next(c for c in cands if part in c["url"])
        rec = next(r for r in listings if r["url"] == pick["url"])
        slug = re.search(r"object-\d+-([a-z0-9-]+)/?$", pick["url"]).group(1)
        work = run_dir / f"brief-{slug}"
        work.mkdir(parents=True, exist_ok=True)
        ctx = brief.context(pick, rec, cands, listings, work)
        out = work / f"{ctx['title']} ({stamp}).pdf"
        overflow = brief.render(ctx, out, work)
        print(ctx["name"], "— переполнение:", overflow or "нет")
        pdfs.append(out)
        ctxs.append(ctx)
        picks.append(pick)

    n = len(ctxs)
    names = ", ".join(f"{c['summary']['street']} {c['summary']['city']}" for c in ctxs)
    title = f"Флип-модель {names}"
    work = run_dir / "pack"
    work.mkdir(exist_ok=True)
    cover = work / "compare.pdf"
    overflow = brief.render({"title": title, "heading": f"{n} {'объекта' if n in (2, 3, 4) else 'объектов'}: флип за 12 месяцев",
                             "date": date.today().strftime("%d.%m.%Y"), "objects": [c["summary"] for c in ctxs],
                             "rows": ROWS, "intro": intro(ctxs)},
                            cover, work, template="pack.html")
    print("сравнение — переполнение:", overflow or "нет")

    out = C.ROOT / "output" / f"{title} ({stamp}).pdf"
    merged = pymupdf.open()
    for path in [cover, *pdfs]:
        with pymupdf.open(path) as part:
            merged.insert_pdf(part)
    merged.save(out, garbage=3, deflate=True)
    for pick in picks:
        brief.register(pick, out.name)
    print("PDF:", out, merged.page_count, "стр.")


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2:])
