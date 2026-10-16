"""Size-neutral ranking: which ЖК sell the largest share of their own flats, steadily (inside MKAD, 12 months).

stable_pct = P25 of monthly sales / flats in the project x 100 - the share of the project the ЖК sold in at least
             9 months of 12. A big project cannot win on volume: everything is per its own size.
Eligible: on sale the whole year, sales in >= 10 months, >= 40 flats, at least 25% of flats unsold at the start
(so a sold-out project does not look slow and a nearly sold-out one does not look fast). Writes efficiency.json.
"""
import contextlib
import io
import json
import statistics as stt
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
with contextlib.redirect_stdout(io.StringIO()):
    import what_sells as W  # noqa: E402

A, B, ACTIVE = W.A, W.B, W.ACTIVE


def small_share(bs):
    n = s = 0
    for b in bs:
        for k, (c, _) in b["mix"].items():
            n += c
            s += c if k in ("студии", "1к") else 0
    return round(100 * s / n) if n else None


ROWS = []
for key, bs0 in A.complexes.items():
    bs = [B[b["id"]] for b in bs0 if b["id"] in ACTIVE]
    if not bs:
        continue
    g = W.group_metrics(bs)
    b0 = max(bs, key=lambda b: b["total"])
    flats = g["flats"]
    start_unsold = g["remaining"] + sum(sum(b["real"]) for b in bs)
    area = sum(sum(b["area"]) for b in bs)
    money = sum(a * p for b in bs for a, p in zip(b["area"], b["price"]))
    raw = sum(sum(b["real"]) for b in bs)
    end = max((b["endd"] for b in bs if b["endd"]), default=None)
    r = {"name": b0["name"] or b0["dev"], "dev": b0["dev"], "class": "/".join(sorted({b["cls"] or "?" for b in bs})),
         "district": b0["dist"], "okrug": b0["okrug"], "km": b0["km"], "buildings": len(bs), "flats": flats,
         "sales_12m": g["sum"], "p25": g["p25"], "median": g["median"], "cv": g["cv"], "months_with_sales": g["months_with_sales"],
         "monthly": g["monthly"], "stable_pct": round(100 * g["p25"] / flats, 2) if flats else None,
         "year_pct": round(100 * g["sum"] / flats, 1) if flats else None,
         "start_unsold_pct": round(100 * start_unsold / flats) if flats else None, "remaining": g["remaining"], "sold_pct": g["sold_pct"],
         "price_m2": g["price_m2"], "price_chg_pct": g["price_chg_pct"], "deal_m2": g["deal_m2"],
         "ticket_mln": round(money / raw / 1e6, 1) if raw else None, "small_pct": small_share(bs),
         "completion": max((b["end"] or "") for b in bs), "months_to_end": round((end - W.date(2026, 10, 1)).days / 30.4) if end else None,
         "long_run": W.long_run(bs)}
    r["eligible"] = bool(r["long_run"] and r["months_with_sales"] >= 10 and flats >= 40 and r["start_unsold_pct"] >= 25)
    ROWS.append(r)

EL = sorted([r for r in ROWS if r["eligible"]], key=lambda r: (-r["stable_pct"], -r["year_pct"]))


def band(x, edges, labels):
    for e, l in zip(edges, labels):
        if x < e:
            return l
    return labels[-1]


def profile(keyf, order=None):
    g = defaultdict(list)
    for r in EL:
        g[keyf(r)].append(r)
    res = {}
    for k, rs in g.items():
        top = sum(1 for r in rs if r in EL[:30])
        res[k] = {"zk": len(rs), "median_stable_pct": round(stt.median(r["stable_pct"] for r in rs), 2),
                  "median_year_pct": round(stt.median(r["year_pct"] for r in rs), 1), "in_top30": top}
    return {k: res[k] for k in (order or sorted(res))if k in res}


cls1 = lambda r: r["class"].split("/")[-1] if r["class"].count("/") == 0 else ("Элитный" if "Элитный" in r["class"] else r["class"].split("/")[0])
PROFILES = {
    "Размер проекта": profile(lambda r: band(r["flats"], [150, 300, 600, 1000], ["40–150 квартир", "150–300", "300–600", "600–1000", "1000+"]),
                              ["40–150 квартир", "150–300", "300–600", "600–1000", "1000+"]),
    "Корпусов в ЖК": profile(lambda r: band(r["buildings"], [2, 4], ["1 корпус", "2–3 корпуса", "4+ корпусов"]), ["1 корпус", "2–3 корпуса", "4+ корпусов"]),
    "Средний чек": profile(lambda r: band(r["ticket_mln"] or 0, [20, 30, 45, 80], ["до 20 млн", "20–30 млн", "30–45 млн", "45–80 млн", "80 млн+"]),
                           ["до 20 млн", "20–30 млн", "30–45 млн", "45–80 млн", "80 млн+"]),
    "Класс": profile(lambda r: "Элитный" if "Элитный" in r["class"] else ("Бизнес" if "Бизнес" in r["class"] else r["class"].split("/")[0]),
                     ["Комфорт", "Типовой", "Бизнес", "Элитный"]),
    "Цена, ₽/м²": profile(lambda r: band(r["price_m2"] or 0, [400_000, 550_000, 700_000, 1_000_000], ["до 400 тыс", "400–550 тыс", "550–700 тыс", "700 тыс – 1 млн", "1 млн+"]),
                          ["до 400 тыс", "400–550 тыс", "550–700 тыс", "700 тыс – 1 млн", "1 млн+"]),
    "До сдачи": profile(lambda r: band(r["months_to_end"] if r["months_to_end"] is not None else 99, [6, 18, 36], ["≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"]),
                        ["≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"]),
    "Удалённость от Кремля": profile(lambda r: band(r["km"], [3, 6, 10], ["до 3 км", "3–6 км", "6–10 км", "10+ км"]), ["до 3 км", "3–6 км", "6–10 км", "10+ км"]),
    "Доля студий и 1к": profile(lambda r: band(r["small_pct"] if r["small_pct"] is not None else -1, [0, 25, 45, 60], ["нет данных", "до 25%", "25–45%", "45–60%", "60%+"]),
                                ["до 25%", "25–45%", "45–60%", "60%+", "нет данных"]),
}

out = {"eligible": len(EL), "all_zk": len(ROWS), "ranking": EL, "profiles": PROFILES,
       "median_stable_pct": round(stt.median(r["stable_pct"] for r in EL), 2), "median_year_pct": round(stt.median(r["year_pct"] for r in EL), 1)}
(HERE / "efficiency.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))

if __name__ == "__main__":
    print(f"eligible {len(EL)} of {len(ROWS)}; median stable {out['median_stable_pct']}%/mo, year {out['median_year_pct']}%")
    for i, r in enumerate(EL[:35], 1):
        print(f"{i:>2} {r['name'][:30]:<30} {r['class'][:14]:<14} {str(r['district'])[:16]:<16} fl {r['flats']:>5} b{r['buildings']} stable {r['stable_pct']:>5}% "
              f"year {r['year_pct']:>5}% p25 {r['p25']:>5} m+{r['months_with_sales']} cv {r['cv']} start {r['start_unsold_pct']}% price {r['price_m2']} "
              f"ticket {r['ticket_mln']} small {r['small_pct']} end {r['months_to_end']} km {r['km']}")
    for k, v in PROFILES.items():
        print("\n==", k)
        for g, x in v.items():
            print(f"   {g:<20} zk {x['zk']:>3} med stable {x['median_stable_pct']:>5}% med year {x['median_year_pct']:>5}% top30 {x['in_top30']}")
    print("\nELITE")
    for i, r in enumerate([r for r in EL if "Элитный" in r["class"]][:12], 1):
        print(f"{i:>2} {r['name'][:30]:<30} {str(r['district'])[:16]:<16} fl {r['flats']:>5} stable {r['stable_pct']}% year {r['year_pct']}% p25 {r['p25']} m+{r['months_with_sales']} price {r['price_m2']} ticket {r['ticket_mln']}")
    print("\nPRIME 1m+")
    for r in [r for r in EL if (r["price_m2"] or 0) >= 1_000_000][:10]:
        print(f"   {r['name'][:30]:<30} {str(r['district'])[:16]:<16} fl {r['flats']:>5} stable {r['stable_pct']}% year {r['year_pct']}% p25 {r['p25']} m+{r['months_with_sales']} price {r['price_m2']} ticket {r['ticket_mln']}")
    print("\nnot eligible but small & fast (flats<40 or months<10):")
    for r in sorted([r for r in ROWS if not r["eligible"] and r["long_run"] and r["flats"] < 40 and r["year_pct"]], key=lambda r: -r["year_pct"])[:8]:
        print(f"   {r['name'][:30]:<30} fl {r['flats']} year {r['year_pct']}% m+{r['months_with_sales']} price {r['price_m2']}")
