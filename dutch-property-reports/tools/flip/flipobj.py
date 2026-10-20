"""Флип-модель любого объекта по описанию в JSON.

python3 -I flipobj.py SPEC_JSON OUT_JSON

Описание (spec): ask — цена продавца k.k., area — полезная площадь, parts — аренда по частям здания
[[подпись, м², € за м² в год], ...], exit_bar — доходность, при которой инвестор купит сданное здание,
capex_m2 — ремонт на м², offer — цена покупки (если не задана: цена для € 10 на каждые € 100 затрат,
вниз до € 5.000), split — продажа частями пользователям: {"price_m2", "low_m2", "cost"} или null.
Покупка за свои средства, без кредита.
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import flipmodel as F  # noqa: E402

FAST = dict(lease_month=3, rent_free=2, sale_month=6)       # продажа до 6 месяцев: ст. 13 WBR


def pack(r):
    keys = ("rent", "value_let", "free_left", "uplift", "sale", "sale_cost", "loan", "cost_total", "income",
            "profit", "margin", "vpb", "profit_after", "equity", "roe", "roe_after", "multiple", "irr",
            "irr_after", "buyer_ovb", "buyer_total", "buyer_giy", "giy_on_cost", "yoc_price", "capex",
            "first_paid", "art13")
    out = {k: r[k] for k in keys}
    out.update(acq=r["acq"], tot=r["tot"], rows=r["rows"], p=r["p"])
    return out


def model(spec: dict) -> dict:
    ask, area, bar = spec["ask"], spec["area"], spec["exit_bar"]
    parts = spec["parts"]
    rent = sum(a * r for _, a, r in parts)
    vat = spec.get("route") == "vat"
    base0 = dict(price=ask, area=area, rent_m2=rent / area, exit_bar=bar, capex_m2=spec.get("capex_m2", 20),
                 capex_fixed=spec.get("capex_fixed", 2500), lease_month=5, rent_free=3, ltv=0.0, sale_month=12,
                 hold_fixed=spec.get("hold_fixed", 0), exit_deduct=spec.get("exit_deduct", 0), dd=spec.get("dd", 7500))
    if vat:
        # новое помещение: НДС вместо налога на передачу; продажа до 6 месяцев после начала аренды
        base0.update(vat_route=True, ovb=0.0, capex_vat=0.0, notary=0, lease_month=3, rent_free=2, sale_month=7)
        alt = dict(sale_month=12)
        labels = {"main": "продажа на 7-м месяце", "alt": "на 12-м месяце, инвестор платит 10,4 %",
                  "main_short": "7 месяцев", "alt_short": "12 месяцев"}
    else:
        alt = dict(FAST)
        labels = {"main": "продажа через 12 месяцев", "alt": "через 6 месяцев", "main_short": "12 месяцев",
                  "alt_short": "6 месяцев"}
    lo, hi = 0.3 * ask, 2.0 * ask
    m10 = F.solve("price", 0.10, "margin", lo, hi, base0)
    offer = spec.get("offer") or min(ask, math.floor(m10 / 5000) * 5000)
    BASE = dict(base0, price=offer)
    if vat:
        scen = {
            "Пессимистичный": dict(rent_m2=rent * 0.9 / area, exit_bar=bar + 0.0075, lease_month=5, rent_free=3,
                                   sale_month=10),
            "Базовый": {},
            "Оптимистичный": dict(rent_m2=spec.get("ask_rent", rent * 1.1) / area, exit_bar=bar - 0.005,
                                  lease_month=2, rent_free=2, sale_month=6),
            "Поздняя продажа": alt,
        }
    else:
        scen = {
            "Пессимистичный": dict(rent_m2=rent * 0.9 / area, exit_bar=bar + 0.0075, lease_month=8, rent_free=4,
                                   sale_month=15),
            "Базовый": {},
            "Оптимистичный": dict(rent_m2=rent * 1.1 / area, exit_bar=bar - 0.005, lease_month=4, rent_free=2,
                                  sale_month=10),
            "Ускоренный, 6 месяцев": alt,
        }
    out = {"spec": spec, "route": "vat" if vat else "ovb", "labels": labels, "ask": ask, "price": offer,
           "area": area, "parts": parts, "rent": rent, "base_params": BASE}
    base = F.run(BASE)
    out["base"], out["fast"] = pack(base), pack(F.run(BASE, **alt))
    out["at_ask"], out["at_ask_fast"] = pack(F.run(BASE, price=ask)), pack(F.run(BASE, price=ask, **alt))
    out["scenarios"] = {name: pack(F.run(BASE, **kw)) for name, kw in scen.items()}
    out["bids"] = {label: {
        "breakeven": F.solve("price", 0.0, "profit", lo, hi, BASE, **kw),
        "m10": F.solve("price", 0.10, "margin", lo, hi, BASE, **kw),
        "m15": F.solve("price", 0.15, "margin", lo, hi, BASE, **kw),
        "bar_be": F.solve("exit_bar", 0.0, "profit", 0.03, 0.2, BASE, **kw),
        "rent_be": F.solve("rent_m2", 0.0, "profit", 5, 600, BASE, **kw),
    } for label, kw in (("12", {}), ("6", alt))}
    bids = out["bids"]

    bars = [round(bar + d, 4) for d in (-0.0075, -0.005, -0.0025, 0, 0.0025, 0.005, 0.0075)]
    rents = [0.85, 0.9, 0.95, 1.0, 1.05, 1.1, 1.15]
    prices = sorted({round(ask * k / 5000) * 5000 for k in (1.1, 1.0, 0.95, 0.9, 0.85, 0.8)} | {offer})
    out["grid_price_bar"] = {"prices": prices, "bars": bars,
                             "profit": [[F.run(BASE, price=p, exit_bar=b)["profit"] for b in bars] for p in prices]}
    out["grid_rent_bar"] = {"rents": rents, "bars": bars,
                            "profit": [[F.run(BASE, rent_m2=rent * k / area, exit_bar=b)["profit"] for b in bars]
                                       for k in rents]}
    leases, sales = ([2, 3, 5, 7], [6, 7, 8, 10, 12]) if vat else ([3, 5, 7, 9], [6, 9, 12, 15, 18])
    out["grid_lease_sale"] = {"leases": leases, "sales": sales, "cells": [
        [None if lm + 1 > sm else {"profit": (r := F.run(BASE, lease_month=lm, sale_month=sm,
                                                         rent_free=2 if (vat or sm <= 6) else 3))["profit"],
                                   "irr": r["irr"], "late": r["late"]}
         for sm in sales] for lm in leases]}
    out["art13_share"] = [] if vat else [(s, F.run(BASE, art13_share=s, **alt)["profit"])
                                         for s in (0, 0.25, 0.5, 0.75, 1.0)]
    out["sens_bar"] = [{"bar": b, "sale": (r := F.run(BASE, exit_bar=b))["sale"], "profit": r["profit"]} for b in bars]
    out["sens_rent"] = [{"k": k, "rent": rent * k, "sale": (r := F.run(BASE, rent_m2=rent * k / area))["sale"],
                         "profit": r["profit"]} for k in rents]

    out["hold"] = F.hold(base, refi_ltv=0.0)
    split = spec.get("split")
    if split:
        out["split"] = F.split_sale(base, price_m2=split["price_m2"], split_cost=split.get("cost", 60_000))
        out["split_low"] = F.split_sale(base, price_m2=split["low_m2"], split_cost=split.get("cost", 60_000))
    p = base["p"]
    carry = (p["price"] * p["holding_pct"] + p["hold_fixed"] + p["void_m2"] * area)
    cost = sum(base["acq"].values()) + base["capex"] + carry + offer * p["sale_fee"] + p["sale_fixed"]
    out["resale_vacant"] = {"profit": offer - cost, "cost": cost}

    steps = [(round(ask * 1.05 / 5000) * 5000, "")] if bids["12"]["breakeven"] > ask else []
    steps += [(ask, "цена продавца"), (round(ask * 0.95 / 5000) * 5000, ""),
              (bids["12"]["breakeven"], "в ноль"), (bids["12"]["m10"], "€ 10 на каждые € 100 затрат"),
              (round(ask * 0.9 / 5000) * 5000, ""), (round(ask * 0.85 / 5000) * 5000, "")]
    if offer != ask:
        steps.append((offer, "наше предложение"))
    # подписанные шаги остаются все; круглые цены — только если не ближе 1,5 % к уже взятым
    kept = []
    for price, tag in sorted(steps, key=lambda t: (t[1] == "", -t[0])):
        near = [k for k in kept if abs(k[0] - price) < (1000 if tag else 0.015 * ask)]
        if not near:
            kept.append((price, tag))
    out["price_steps"] = []
    for price, tag in sorted(kept, key=lambda t: -t[0]):
        a, b = F.run(BASE, price=price), F.run(BASE, price=price, **alt)
        out["price_steps"].append({"price": price, "tag": tag, "disc": price / ask - 1, "p12": a["profit"],
                                   "p6": b["profit"]})
    out["ladder"] = [{"price": s["price"], "disc": s["disc"], "pm2": s["price"] / area, "p12": s["p12"],
                      "p6": s["p6"], "m12": F.run(BASE, price=s["price"])["margin"],
                      "m6": F.run(BASE, price=s["price"], **alt)["margin"],
                      "irr12": F.run(BASE, price=s["price"])["irr"],
                      "irr6": F.run(BASE, price=s["price"], **alt)["irr"]} for s in out["price_steps"]]
    return out


def main():
    spec = json.loads(Path(sys.argv[1]).read_text())
    out = model(spec)
    Path(sys.argv[2]).write_text(json.dumps(out, ensure_ascii=False, indent=1, default=float))
    b, f, a = out["base"], out["fast"], out["at_ask"]
    print(f"{spec['name']}: аренда € {out['rent']:,.0f} ({out['rent'] / out['ask'] * 100:.2f} % к цене продавца), "
          f"BAR {spec['exit_bar'] * 100:.2f} %")
    print(f"  по цене продавца: прибыль € {a['profit']:,.0f}")
    print(f"  в ноль € {out['bids']['12']['breakeven']:,.0f} ({out['bids']['12']['breakeven'] / out['ask'] * 100 - 100:.1f} %), "
          f"10 % € {out['bids']['12']['m10']:,.0f} ({out['bids']['12']['m10'] / out['ask'] * 100 - 100:.1f} %)")
    print(f"  предложение € {out['price']:,.0f}: {out['labels']['main']} € {b['profit']:,.0f} / после налога "
          f"€ {b['profit_after']:,.0f}, {out['labels']['alt']} € {f['profit']:,.0f}; капитал € {b['equity']:,.0f}, "
          f"IRR п/н {b['irr_after'] * 100:.1f} %")
    for k, v in out["scenarios"].items():
        print(f"    {k:22} прибыль € {v['profit']:>9,.0f}  продажа € {v['sale']:>9,.0f}")


if __name__ == "__main__":
    main()
