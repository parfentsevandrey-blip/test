"""Флип-модель Kernweg 1, Хорн: все цифры отчёта и таблицы.

python3 -I kernweg.py OUT_JSON
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import flipmodel as F  # noqa: E402

ASK = 1_495_000
PRICE = 1_150_000                     # предложение: −23,1 % к цене продавца
AREA = 2016                           # VVO по объявлению
PARTS = [                             # рыночная аренда по частям здания, €/м²/год без НДС
    ("Шоурум, первый этаж", 621, 80),
    ("Шоурум и витрины, антресоль", 228, 45),
    ("Офисы, два этажа", 121, 110),
    ("Кантина и подсобные", 52, 50),
    ("Мастерские и склады, первый этаж", 555, 62),
    ("Склады и мастерские, второй этаж", 439, 35),
]
RENT = sum(a * r for _, a, r in PARTS)
EURIBOR = 0.02628                     # 3M, 14.10.2026
MARGIN = 0.0275

BASE = dict(price=PRICE, area=AREA, rent_m2=RENT / AREA, exit_bar=0.0825, capex_m2=20,
            lease_month=5, rent_free=3, ltv=0.0, rate=EURIBOR + MARGIN, sale_month=12)   # покупка за свои средства
FAST = dict(lease_month=3, rent_free=2, sale_month=6)       # ускоренный вариант по ст. 13

SCEN = {
    "Пессимистичный": dict(rent_m2=RENT * 0.9 / AREA, exit_bar=0.09, lease_month=8, rent_free=4, sale_month=15),
    "Базовый": {},
    "Оптимистичный": dict(rent_m2=RENT * 1.1 / AREA, exit_bar=0.0775, lease_month=4, rent_free=2, sale_month=10),
    "Ускоренный, 6 месяцев": FAST,
}


def pack(r):
    keys = ("rent", "value_let", "free_left", "uplift", "sale", "sale_cost", "loan", "cost_total", "income",
            "profit", "margin", "vpb", "profit_after", "equity", "roe", "roe_after", "multiple", "irr",
            "irr_after", "buyer_ovb", "buyer_total", "buyer_giy", "giy_on_cost", "yoc_price", "capex",
            "first_paid", "art13")
    out = {k: r[k] for k in keys}
    out["acq"] = r["acq"]
    out["tot"] = r["tot"]
    out["rows"] = r["rows"]
    out["p"] = r["p"]
    return out


def main():
    out = {"ask": ASK, "price": PRICE, "area": AREA, "parts": PARTS, "rent": RENT,
           "euribor": EURIBOR, "margin": MARGIN, "base_params": BASE}
    base = F.run(BASE)
    fast = F.run(BASE, **FAST)
    out["base"] = pack(base)
    out["fast"] = pack(fast)
    out["at_ask"] = pack(F.run(BASE, price=ASK))
    out["at_ask_fast"] = pack(F.run(BASE, price=ASK, **FAST))
    out["unlevered"] = pack(F.run(BASE, ltv=0.0))
    out["unlevered_fast"] = pack(F.run(BASE, ltv=0.0, **FAST))
    out["scenarios"] = {name: pack(F.run(BASE, **kw)) for name, kw in SCEN.items()}

    # предельная цена покупки и точки безубыточности
    bids = {}
    for label, kw in (("12", {}), ("6", FAST)):
        bids[label] = {
            "breakeven": F.solve("price", 0.0, "profit", 8e5, 1.7e6, BASE, **kw),
            "m10": F.solve("price", 0.10, "margin", 8e5, 1.7e6, BASE, **kw),
            "m15": F.solve("price", 0.15, "margin", 8e5, 1.7e6, BASE, **kw),
            "bar_be": F.solve("exit_bar", 0.0, "profit", 0.05, 0.14, BASE, **kw),
            "rent_be": F.solve("rent_m2", 0.0, "profit", 20, 120, BASE, **kw),
            "lease_be": None,
        }
    out["bids"] = bids

    # чувствительность: цена × доходность выхода; аренда × доходность; месяц аренды × месяц продажи
    prices = [1_100_000, 1_150_000, 1_200_000, 1_250_000, 1_300_000, 1_400_000, ASK]
    bars = [0.075, 0.0775, 0.08, 0.0825, 0.085, 0.0875, 0.09]
    out["grid_price_bar"] = {"prices": prices, "bars": bars,
                             "profit": [[F.run(BASE, price=p, exit_bar=b)["profit"] for b in bars] for p in prices]}
    rents = [0.85, 0.9, 0.95, 1.0, 1.05, 1.1, 1.15]
    out["grid_rent_bar"] = {"rents": rents, "bars": bars,
                            "profit": [[F.run(BASE, rent_m2=RENT * k / AREA, exit_bar=b)["profit"] for b in bars]
                                       for k in rents]}
    leases = [3, 5, 7, 9]
    sales = [6, 9, 12, 15, 18]
    grid = []
    for lm in leases:
        row = []
        for sm in sales:
            if lm + 1 > sm:
                row.append(None)
                continue
            r = F.run(BASE, lease_month=lm, sale_month=sm, rent_free=2 if sm <= 6 else 3)
            row.append({"profit": r["profit"], "irr": r["irr"]})
        grid.append(row)
    out["grid_lease_sale"] = {"leases": leases, "sales": sales, "cells": grid}

    # доля экономии покупателя по ст. 13, которую удаётся забрать в цене
    out["art13_share"] = [(s, F.run(BASE, art13_share=s, **FAST)["profit"]) for s in (0, 0.25, 0.5, 0.75, 1.0)]

    # план Б
    out["hold"] = F.hold(base, refi_ltv=0.0)     # держим без кредита
    out["split"] = F.split_sale(base)
    out["split_low"] = F.split_sale(base, price_m2=750)
    # продать пустым обратно, если не сдали: по цене покупки через 12 месяцев
    p = base["p"]
    acq = sum(base["acq"].values())
    months = 12
    carry = (p["price"] * p["holding_pct"] + p["void_m2"] * AREA) * months / 12 + base["loan"] * p["rate"] * months / 12
    cost = acq + base["capex"] + carry + PRICE * p["sale_fee"] + p["sale_fixed"]
    out["resale_vacant"] = {"profit": PRICE - cost, "cost": cost}
    # лестница цены покупки
    ladder = []
    for price in (ASK, 1_400_000, 1_350_000, 1_300_000, 1_250_000, 1_200_000, PRICE, 1_100_000):
        a, b = F.run(BASE, price=price), F.run(BASE, price=price, **FAST)
        ladder.append({"price": price, "disc": price / ASK - 1, "pm2": price / AREA,
                       "p12": a["profit"], "m12": a["margin"], "p6": b["profit"], "m6": b["margin"],
                       "irr12": a["irr"], "irr6": b["irr"]})
    out["ladder"] = ladder
    # цена покупки → прибыль: ступени для графика, с точкой «в ноль» и ценой для € 10 на € 100 затрат
    steps = []
    for price, tag in ((ASK, "цена продавца"), (1_400_000, ""), (bids["12"]["breakeven"], "в ноль"),
                       (1_250_000, ""), (1_200_000, ""), (bids["12"]["m10"], "€ 10 на каждые € 100 затрат"),
                       (PRICE, "наше предложение"), (1_100_000, "")):
        a, b = F.run(BASE, price=price), F.run(BASE, price=price, **FAST)
        steps.append({"price": price, "tag": tag, "disc": price / ASK - 1, "p12": a["profit"], "p6": b["profit"]})
    out["price_steps"] = steps
    # что если: инвестор хочет другую доходность, аренда другая
    out["sens_bar"] = [{"bar": bar, "sale": F.run(BASE, exit_bar=bar)["sale"], "profit": F.run(BASE, exit_bar=bar)["profit"]}
                       for bar in bars]
    out["sens_rent"] = [{"k": k, "rent": RENT * k, "sale": F.run(BASE, rent_m2=RENT * k / AREA)["sale"],
                         "profit": F.run(BASE, rent_m2=RENT * k / AREA)["profit"]} for k in rents]
    Path(sys.argv[1]).write_text(json.dumps(out, ensure_ascii=False, indent=1, default=float))

    def pct(x):
        return "—" if x is None else f"{x * 100:.1f}%"
    print(f"аренда {RENT:,} = €{RENT / AREA:.1f}/м²; к цене продавца {RENT / ASK * 100:.2f}%, к предложению {RENT / PRICE * 100:.2f}%")
    for name, r in [("по цене продавца 12м", out["at_ask"]), ("по цене продавца 6м", out["at_ask_fast"]),
                    ("база 12м", out["base"]), ("ускоренный 6м", out["fast"]),
                    ("без кредита 12м", out["unlevered"]), ("без кредита 6м", out["unlevered_fast"])] + \
            [(k, v) for k, v in out["scenarios"].items()]:
        print(f"{name:24} продажа {r['sale']:>10,.0f} затраты {r['cost_total']:>10,.0f} прибыль {r['profit']:>9,.0f} "
              f"после налога {r['profit_after']:>9,.0f} маржа {pct(r['margin']):>6} капитал {r['equity']:>9,.0f} "
              f"ROE {pct(r['roe']):>6} IRR {pct(r['irr']):>7} IRR п/н {pct(r['irr_after']):>7} x{r['multiple']:.2f}")
    for k, b in bids.items():
        print(k, {kk: (round(v, 4) if isinstance(v, float) and v < 1 else round(v) if v else v) for kk, v in b.items()})
    print("hold", {k: round(v, 3) for k, v in out["hold"].items()})
    print("split", {k: round(v, 3) for k, v in out["split"].items()})
    print("split750", {k: round(v, 3) for k, v in out["split_low"].items()})
    print("resale vacant", out["resale_vacant"])
    print("art13 share", out["art13_share"])
    for l in out["ladder"]:
        print(f"  {l['price']:>9,} {l['disc']*100:6.1f}% €{l['pm2']:.0f}/м² 12м {l['p12']:>9,.0f} {pct(l['m12']):>6} {pct(l['irr12']):>7} | 6м {l['p6']:>9,.0f} {pct(l['m6']):>6} {pct(l['irr6']):>7}")


if __name__ == "__main__":
    main()
