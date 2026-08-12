"""Генератор СИНТЕТИЧЕСКОЙ выборки аналогов и оперативных данных для демо-режима.

Прототип должен запускаться и показывать все ветки вердикта до того, как подключены
платные источники. Поэтому здесь строится правдоподобная, но выдуманная выборка:
разброс цен, сроки экспозиции, снижения цен у конкурентов, пара закрытых сделок.

ЭТИ ЧИСЛА НЕЛЬЗЯ ИСПОЛЬЗОВАТЬ ДЛЯ РЕШЕНИЙ ПО ЦЕНЕ. Их задача — прогнать механику.
Реальные аналоги приходят из agent/providers/cian.py и других источников.

    python scripts/make_demo_comps.py
"""

from __future__ import annotations

import json
import random
import sys
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from agent.models import Finish  # noqa: E402
from agent.registry import load_registry  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent

# На лот: (сколько аналогов в том же ЖК, сколько рядом, сдвиг медианы рынка к нашей цене,
# медианный срок экспозиции у аналогов). Сдвиг задаёт, окажемся ли мы дорогими или дешёвыми.
PROFILES: dict[str, tuple[int, int, float, int]] = {
    "kutuzovskiy-xii-1": (2, 4, +0.01, 70),
    "level-akademiceskaa-2": (4, 5, +0.14, 40),   # мы заметно дешевле рынка
    "zolotoy-jiloy-kvartal-3": (1, 1, 0.00, 120),  # уникальный лот, аналогов почти нет
    "high-life-4": (3, 4, +0.02, 55),
    "sky-house-5": (4, 3, -0.06, 60),
    "sky-house-6": (4, 3, -0.05, 60),
    "sky-house-7": (4, 3, -0.11, 60),             # мы дороже рынка
}

# Оперативные данные, которых нет в xlsx: сколько лот в экспозиции и как идёт спрос.
OPS: dict[str, dict] = {
    "kutuzovskiy-xii-1": {"days_listed": 96, "views_7d": 210, "calls_7d": 3, "viewings_30d": 2},
    "level-akademiceskaa-2": {"days_listed": 34, "views_7d": 480, "calls_7d": 11, "viewings_30d": 6},
    "zolotoy-jiloy-kvartal-3": {"days_listed": 210, "views_7d": 95, "calls_7d": 1, "viewings_30d": 1},
    "high-life-4": {"days_listed": 51, "views_7d": 260, "calls_7d": 5, "viewings_30d": 3},
    "sky-house-5": {"days_listed": 62, "views_7d": 190, "calls_7d": 2, "viewings_30d": 2},
    "sky-house-6": {"days_listed": 74, "views_7d": 150, "calls_7d": 2, "viewings_30d": 1},
    "sky-house-7": {"days_listed": 118, "views_7d": 120, "calls_7d": 1, "viewings_30d": 0},
}

NEARBY = {
    "kutuzovskiy-xii-1": [("Дом на Кутузовском", "Кутузовский проспект 16", 0.6)],
    "level-akademiceskaa-2": [("Обручева 30", "Профсоюзная 16", 1.1)],
    "zolotoy-jiloy-kvartал-3": [],
    "high-life-4": [("Павелецкая Сити", "Летниковская 6", 0.5)],
    "sky-house-5": [("Донской Олимп", "Мытная 42", 0.4)],
    "sky-house-6": [("Донской Олимп", "Мытная 42", 0.4)],
    "sky-house-7": [("Донской Олимп", "Мытная 42", 0.4)],
}

FINISH_POOL = [Finish.WHITEBOX, Finish.DEVELOPER, Finish.DESIGNER]


def build() -> dict:
    today = date.today()
    comps: dict[str, list[dict]] = {}

    for lot in load_registry():
        rng = random.Random(hash(lot.id) & 0xFFFF)
        n_same, n_near, shift, median_dom = PROFILES.get(lot.id, (3, 3, 0.0, 55))
        # Медиана рынка = наша цена, сдвинутая на profile shift.
        market_ppsm = lot.price_per_sqm * (1 + shift)
        items: list[dict] = []

        for i in range(n_same):
            items.append(
                _make(
                    rng,
                    today,
                    lot,
                    market_ppsm,
                    median_dom,
                    same_complex=True,
                    complex_name=lot.complex_name,
                    address=lot.address,
                    distance_km=0.0,
                    idx=i,
                )
            )

        for j in range(n_near):
            pool = NEARBY.get(lot.id) or [(lot.complex_name + " (рядом)", lot.address, 0.8)]
            name, addr, dist = pool[j % len(pool)]
            items.append(
                _make(
                    rng,
                    today,
                    lot,
                    market_ppsm * rng.uniform(0.94, 1.04),
                    median_dom,
                    same_complex=False,
                    complex_name=name,
                    address=addr,
                    distance_km=dist,
                    idx=100 + j,
                )
            )

        # Пара закрытых сделок там, где выборка достаточно велика: они весомее экспозиции.
        if len(items) >= 5:
            for k in range(2):
                deal = _make(
                    rng,
                    today,
                    lot,
                    market_ppsm * (1 - 0.08) * rng.uniform(0.97, 1.02),
                    median_dom,
                    same_complex=True,
                    complex_name=lot.complex_name,
                    address=lot.address,
                    distance_km=0.0,
                    idx=200 + k,
                )
                deal["is_closed_deal"] = True
                deal["days_on_market"] = None
                deal["price_cut_pct"] = None
                deal["source"] = "egrn"
                items.append(deal)

        comps[lot.id] = items

    return {
        "_disclaimer": (
            "СИНТЕТИЧЕСКИЕ ДАННЫЕ. Сгенерированы scripts/make_demo_comps.py для проверки "
            "механики вердикта. Не являются рыночной информацией и не пригодны для "
            "принятия решений по цене."
        ),
        "generated_at": today.isoformat(),
        "comps": comps,
    }


def _make(
    rng: random.Random,
    today: date,
    lot,
    center_ppsm: float,
    median_dom: int,
    *,
    same_complex: bool,
    complex_name: str,
    address: str,
    distance_km: float,
    idx: int,
) -> dict:
    area = round(lot.area * rng.uniform(0.82, 1.22), 1)
    floor = max(1, min(lot.floors_total, lot.floor + rng.randint(-5, 5)))
    finish = rng.choice(FINISH_POOL)
    ppsm = center_ppsm * rng.gauss(1.0, 0.07)
    has_parking = rng.random() < 0.4
    price = ppsm * area + (6_000_000 if has_parking else 0)
    dom = max(5, int(rng.gauss(median_dom, median_dom * 0.35)))
    cut = round(rng.uniform(0.03, 0.12), 3) if rng.random() < 0.45 else None

    return {
        "source": "demo",
        "external_id": f"{lot.id}-cmp-{idx}",
        "complex_name": complex_name,
        "address": address,
        "rooms": lot.rooms,
        "area": area,
        "floor": floor,
        "floors_total": lot.floors_total,
        "price": int(round(price / 100_000) * 100_000),
        "finish": finish.value,
        "has_parking": has_parking,
        "same_complex": same_complex,
        "distance_km": distance_km,
        "days_on_market": dom,
        "price_cut_pct": cut,
        "is_closed_deal": False,
        "observed_at": (today - timedelta(days=rng.randint(0, 25))).isoformat(),
        "url": "",
    }


def main() -> None:
    payload = build()
    out = ROOT / "data" / "comps_demo.json"
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    total = sum(len(v) for v in payload["comps"].values())
    print(f"Сгенерировано аналогов: {total} для {len(payload['comps'])} лотов → {out}")

    today = date.today()
    ops = {
        lot_id: {
            "listed_at": (today - timedelta(days=v["days_listed"])).isoformat(),
            "views_7d": v["views_7d"],
            "calls_7d": v["calls_7d"],
            "viewings_30d": v["viewings_30d"],
        }
        for lot_id, v in OPS.items()
    }
    ops_out = ROOT / "data" / "ops_demo.json"
    ops_out.write_text(
        json.dumps(
            {
                "_disclaimer": (
                    "СИНТЕТИЧЕСКИЕ оперативные данные (срок экспозиции, просмотры, звонки, "
                    "показы). В боевом контуре берутся из CRM и статистики площадок."
                ),
                "ops": ops,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"Оперативные данные → {ops_out}")


if __name__ == "__main__":
    main()
