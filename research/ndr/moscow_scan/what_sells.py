"""What sells best and most steadily inside MKAD (12 months, Oct 2025 - Sep 2026).

Builds on scan_export (buildings inside MKAD, grouped into ЖК) and adds:
  - cleaned monthly sales: in bulk months (one contract for many flats) the month counts deals, not flats;
  - steadiness: P25 of monthly sales = a level the ЖК reaches in 9 months of 12; CV = spread of months;
  - absorption: share of the unsold stock sold per month (size-neutral speed);
  - flat mix from the object page ("Квартиры и помещения": studios, 1-, 2-, 3-, 4+ rooms) and the average
    deal area (sold m² / flats sold), which shows whether small or large flats go first.
Writes what_sells.json next to this file.
"""
import contextlib
import io
import json
import math
import re
import statistics as stt
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
with contextlib.redirect_stdout(io.StringIO()):
    import scan_export as E  # noqa: E402  (inside_b, complexes, A = analyze2)
A = E.A
M = A.MONTHS
st = E.st
KREMLIN = (55.7520, 37.6175)
EXCLUDE_DEV = ("ФОНД РЕНОВАЦИИ",)                 # resettlement flats, not market sales

OKRUG = {
    "ЦАО": "Арбат Басманный Замоскворечье Красносельский Мещанский Пресненский Таганский Тверской Хамовники Якиманка",
    "САО": "Аэропорт Беговой Бескудниковский Войковский Восточное_Дегунино Головинский Дмитровский Западное_Дегунино "
           "Коптево Левобережный Молжаниновский Савёловский Сокол Тимирязевский Ховрино Хорошёвский",
    "СВАО": "Алексеевский Алтуфьевский Бабушкинский Бибирево Бутырский Лианозово Лосиноостровский Марфино Марьина_Роща "
            "Останкинский Отрадное Ростокино Свиблово Северное_Медведково Южное_Медведково Ярославский",
    "ВАО": "Богородское Вешняки Восточное_Измайлово Гольяново Ивановское Измайлово Метрогородок Новогиреево Перово "
           "Преображенское Северное_Измайлово Соколиная_Гора Сокольники",
    "ЮВАО": "Выхино-Жулебино Капотня Кузьминки Лефортово Люблино Марьино Нижегородский Печатники Рязанский Текстильщики Южнопортовый",
    "ЮАО": "Бирюлёво_Восточное Бирюлёво_Западное Братеево Даниловский Донской Зябликово Москворечье-Сабурово "
           "Нагатино-Садовники Нагатинский_Затон Нагорный Орехово-Борисово_Северное Орехово-Борисово_Южное Царицыно "
           "Чертаново_Северное Чертаново_Центральное Чертаново_Южное",
    "ЮЗАО": "Академический Гагаринский Зюзино Коньково Котловка Ломоносовский Обручевский Тёплый_Стан Черёмушки Ясенево",
    "ЗАО": "Дорогомилово Крылатское Кунцево Можайский Очаково-Матвеевское Проспект_Вернадского Раменки "
           "Тропарёво-Никулино Филёвский_Парк Фили-Давыдково",
    "СЗАО": "Покровское-Стрешнево Северное_Тушино Строгино Хорошёво-Мнёвники Щукино Южное_Тушино",
}
DISTRICT_OKRUG = {d.replace("_", " "): ok for ok, ds in OKRUG.items() for d in ds.split()}
STREET_DISTRICT = {"Улица Обручева": "Обручевский", "Улица Вавилова": "Гагаринский", "Проспект Вернадского": "Проспект Вернадского",
                   "Улица Амурская": "Гольяново", "Набережная Фрунзенская": "Хамовники"}


def km(a, b):
    return math.hypot((a[0] - b[0]) * 111.2, (a[1] - b[1]) * 111.2 * math.cos(math.radians(55.75)))


def district_of(addr):
    a = addr or ""
    first = a.split(",")[0].strip()
    if first in STREET_DISTRICT:
        return STREET_DISTRICT[first]
    m = re.search(r"(?:Муниципальный округ|Район)\s+([^,]+)", a)
    name = m.group(1).strip() if m else re.sub(r"\s*район$", "", first).strip()
    return name if name in DISTRICT_OKRUG else None


def parse_date(s):
    try:
        d, m, y = (int(x) for x in s.split("."))
        return date(y, m, d)
    except Exception:
        return None


ROOMS = {"Студии": "студии", "1-комнатные": "1к", "2-комнатные": "2к", "3-комнатные": "3к", "4+ комнатные": "4к+"}


def flat_mix(href):
    """{'1к': (count, mid_area), ...} from the object page block 'Квартиры и помещения'."""
    t = (st["objects"].get(href) or {}).get("text") or ""
    i = t.find("Квартиры и помещения")
    if i < 0:
        return {}
    seg = t[i:i + 2000]
    mix = {}
    for m in re.finditer(r"\n([^\n]{2,30}?) от ([\d,]+) до ([\d,]+) м²\s*\n+\s*(\d[\d\s]*)\s", seg):
        label = ROOMS.get(m.group(1).strip())
        if label:
            lo, hi = (float(x.replace(",", ".")) for x in (m.group(2), m.group(3)))
            mix[label] = (int(m.group(4).replace(" ", "")), (lo + hi) / 2)
    return mix


# ---------- buildings (inside MKAD, market developers) ----------
B = {}
NO_FLATS = []
known = []
for bid, b in E.inside_b.items():
    if any(x in (b["dev"] or "") for x in EXCLUDE_DEV) or len(b["real"]) != 12:
        continue
    if not b["total"]:                                  # no flats at all: apartments, offices, parking - not this market
        NO_FLATS.append(bid)
        continue
    href = A.OBJECT_PATH.format(id=bid) if hasattr(A, "OBJECT_PATH") else f"/сервисы/каталог-новостроек/объект/{bid}"
    clean = [c if (c and r / c >= 5 and r >= 10) else r for r, c in zip(b["real"], b["contr"])]
    d = district_of(b["district"])
    B[bid] = dict(b, clean=clean, dist=d, mix=flat_mix(href), pubd=parse_date(b["pub"] or ""), endd=parse_date(b["end"] or ""),
                  km=round(km(b["xy"], KREMLIN), 1), bulk=sum(r - c for r, c in zip(b["real"], clean)))
    if d:
        known.append(bid)
for b in B.values():                                   # unknown district -> nearest building with a known one
    if not b["dist"] and known:
        b["dist"] = B[min(known, key=lambda k: km(B[k]["xy"], b["xy"]))]["dist"]
    b["okrug"] = DISTRICT_OKRUG.get(b["dist"], "?")


def stock_path(bs):
    """Unsold flats at the start of each month, reconstructed backwards from today's counter."""
    unsold_now = sum(max(b["total"] - b["sold"], 0) for b in bs)
    real = [sum(b["real"][i] for b in bs) for i in range(12)]
    path, s = [], unsold_now
    for i in range(11, -1, -1):
        s += real[i]
        path.append(s)
    return path[::-1]


def series_stats(sales):
    q = sorted(sales)
    mean = sum(sales) / 12
    return {"sum": sum(sales), "mean": round(mean, 1), "median": stt.median(sales), "p25": q[2] + (q[3] - q[2]) * 0.75,
            "min": q[0], "months_with_sales": sum(1 for x in sales if x > 0),
            "cv": round(stt.pstdev(sales) / mean, 2) if mean else None,
            "h1": sum(sales[:6]), "h2": sum(sales[6:])}


def like_for_like(bs):
    """Price change Q4-2025 -> Q3-2026 on the same buildings (sold-area weighted), so a change of mix is not read as a price move."""
    num = den = 0.0
    for b in bs:
        a0, a1 = sum(b["area"][:3]), sum(b["area"][9:])
        if a0 > 0 and a1 > 0:
            p0 = sum(a * p for a, p in zip(b["area"][:3], b["price"][:3])) / a0
            p1 = sum(a * p for a, p in zip(b["area"][9:], b["price"][9:])) / a1
            w = min(a0, a1)
            num += w * (p1 / p0 - 1); den += w
    return round(100 * num / den, 1) if den else None


def group_metrics(bs):
    sales = [sum(b["clean"][i] for b in bs) for i in range(12)]
    s = series_stats(sales)
    stock = stock_path(bs)
    avg_stock = sum(stock) / 12
    area = sum(sum(b["area"]) for b in bs)
    money = sum(a * p for b in bs for a, p in zip(b["area"], b["price"]))
    chg = like_for_like(bs)
    raw = sum(sum(b["real"]) for b in bs)
    total = sum(b["total"] for b in bs); sold = sum(b["sold"] for b in bs)
    return {**s, "monthly": sales, "flats": total, "sold": sold, "remaining": max(total - sold, 0),
            "sold_pct": round(100 * sold / total, 1) if total else None,
            "absorb_pct": round(100 * s["mean"] / avg_stock, 2) if avg_stock else None,   # % of unsold stock sold per month
            "price_m2": round(money / area) if area else None,
            "price_chg_pct": chg, "h2_vs_h1_pct": round(100 * (s["h2"] / s["h1"] - 1), 1) if s["h1"] else None,
            "deal_m2": round(area / raw, 1) if raw else None, "bulk_flats": sum(b["bulk"] for b in bs),
            "buildings": len(bs)}


START = date(2025, 10, 1)
ACTIVE = {k: b for k, b in B.items() if sum(b["real"]) > 0}   # buildings with at least one sale in 12 months
long_run = lambda bs: any(b["pubd"] and b["pubd"] <= START for b in bs) and sum(b["clean"][0] + b["clean"][1] + b["clean"][2] for b in bs) > 0

# ---------- ЖК ----------
ZK = []
for key, bs0 in A.complexes.items():
    bs = [B[b["id"]] for b in bs0 if b["id"] in ACTIVE]
    if not bs:
        continue
    b0 = max(bs, key=lambda b: b["total"])
    g = group_metrics(bs)
    ZK.append({"name": b0["name"], "dev": b0["dev"], "class": "/".join(sorted({b["cls"] or "?" for b in bs})),
               "district": b0["dist"], "okrug": b0["okrug"], "km": b0["km"], "metro": b0["metro"],
               "long_run": long_run(bs), "completion": max((b["end"] or "") for b in bs), **g})

# ---------- class / district / okrug ----------


def by(keyf, pool=None):
    """Metrics over active buildings; buildings without a single sale are counted separately as 'idle'."""
    groups, idle = defaultdict(list), defaultdict(int)
    for b in (pool or B.values()):
        if b["id"] in ACTIVE:
            groups[keyf(b)].append(b)
        else:
            idle[keyf(b)] += 1
    return {k: group_metrics(v) | {"zk": len({(b["dev"], A.norm(b["name"])) for b in v}), "idle_buildings": idle.get(k, 0)}
            for k, v in groups.items()}


CLASSES = by(lambda b: b["cls"] or "?")
OKRUGS = by(lambda b: b["okrug"])
DISTRICTS = by(lambda b: b["dist"])


def band(x, edges, labels):
    if x < 0:
        return "нет сделок за 12 мес"
    for e, l in zip(edges, labels):
        if x < e:
            return l
    return labels[-1]


KM_BANDS = by(lambda b: band(b["km"], [3, 6, 10], ["до 3 км (≈Садовое)", "3–6 км (≈ТТК)", "6–10 км", "10+ км (к МКАД)"]))
STAGE = by(lambda b: band(max((b["endd"] - date(2026, 10, 1)).days / 30.4, 0) if b["endd"] else 99, [6, 18, 36],
                          ["сдача ≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"]))
PRICE = by(lambda b: band((sum(a * p for a, p in zip(b["area"], b["price"])) / sum(b["area"])) if sum(b["area"]) else -1,
                          [400_000, 600_000, 900_000, 1_500_000], ["до 400 тыс/м²", "400–600", "600–900", "900–1500", "1500+"]))

# ---------- flat types ----------
def mix_stats(pool):
    cnt = defaultdict(int); area_sum = 0; n = 0
    for b in pool:
        for k, (c, a) in b["mix"].items():
            cnt[k] += c; area_sum += c * a; n += c
    sold_area = sum(sum(x["area"]) for x in pool); sold_n = sum(sum(x["real"]) for x in pool)
    return {"mix_pct": {k: round(100 * cnt[k] / n, 1) for k in ROOMS.values() if n}, "stock_avg_m2": round(area_sum / n, 1) if n else None,
            "deal_avg_m2": round(sold_area / sold_n, 1) if sold_n else None, "buildings_with_mix": sum(1 for b in pool if b["mix"])}


MIX = {cls: mix_stats([b for b in ACTIVE.values() if b["cls"] == cls and b["mix"]]) for cls in CLASSES}
MIX["Все"] = mix_stats([b for b in ACTIVE.values() if b["mix"]])

# small-flat share vs speed, within class (buildings on sale all 12 months)
def small_share(b):
    n = sum(c for c, _ in b["mix"].values())
    return (b["mix"].get("студии", (0, 0))[0] + b["mix"].get("1к", (0, 0))[0]) / n if n else None


SMALL = {}
for cls in ("Комфорт", "Бизнес", "Элитный"):
    pool = [b for b in ACTIVE.values() if b["cls"] == cls and b["mix"] and b["pubd"] and b["pubd"] <= START and small_share(b) is not None]
    pool.sort(key=small_share)
    if len(pool) < 8:
        continue
    k = len(pool) // 3
    parts = {"мало малогабаритных": pool[:k], "средне": pool[k:-k], "много малогабаритных": pool[-k:]}
    SMALL[cls] = {name: {"buildings": len(p), "small_share_pct": round(100 * stt.mean(small_share(b) for b in p)),
                         "absorb_pct": group_metrics(p)["absorb_pct"], "deal_m2": group_metrics(p)["deal_m2"]}
                  for name, p in parts.items()}

# deal size over time (all market)
DEAL_M2 = [round(sum(b["area"][i] for b in ACTIVE.values()) / max(sum(b["real"][i] for b in ACTIVE.values()), 1), 1) for i in range(12)]

DEVS = by(lambda b: b["dev"])

out = {"months": M, "note": "inside MKAD, without ФОНД РЕНОВАЦИИ; bulk months counted as deals",
       "market": group_metrics(list(ACTIVE.values())) | {"idle_buildings": len(B) - len(ACTIVE), "no_flats_objects": len(NO_FLATS),
                                                      "idle_not_started": sum(1 for k, b in B.items() if k not in ACTIVE and b["not_started"])}, "classes": CLASSES, "okrugs": OKRUGS, "districts": DISTRICTS,
       "distance": KM_BANDS, "stage": STAGE, "price_bands": PRICE, "mix": MIX, "small_share": SMALL, "deal_m2_by_month": DEAL_M2,
       "complexes": sorted(ZK, key=lambda z: -z["p25"]), "developers": DEVS}
(HERE / "what_sells.json").write_text(json.dumps(out, ensure_ascii=False, indent=1, default=str))

if __name__ == "__main__":
    mk = out["market"]
    print(f"buildings {len(B)}, ЖК {len(ZK)} (long-run {sum(z['long_run'] for z in ZK)}), sales 12m {mk['sum']}, bulk flats {mk['bulk_flats']}")
    print("market monthly", mk["monthly"], "absorb", mk["absorb_pct"])
    def tab(title, d, keys=("buildings", "zk", "sum", "mean", "p25", "cv", "absorb_pct", "price_m2", "price_chg_pct", "deal_m2", "sold_pct")):
        print("\n==", title); print(f"{'':<26}" + "".join(f"{k[:9]:>10}" for k in keys))
        for k, v in sorted(d.items(), key=lambda kv: -kv[1]["sum"]):
            print(f"{str(k)[:26]:<26}" + "".join(f"{str(v.get(x)):>10}" for x in keys))
    tab("class", CLASSES); tab("okrug", OKRUGS); tab("distance", KM_BANDS); tab("stage", STAGE); tab("price", PRICE)
    tab("districts top", dict(sorted(DISTRICTS.items(), key=lambda kv: -kv[1]["sum"])[:20]))
    print("\n== mix", json.dumps(MIX, ensure_ascii=False)); print("== small share", json.dumps(SMALL, ensure_ascii=False))
    print("== deal m2 by month", DEAL_M2)
    print("\n== ЖК long-run by P25")
    for z in [z for z in out["complexes"] if z["long_run"]][:30]:
        print(f"{z['name'][:34]:<34} {z['class'][:8]:<8} {z['district'] or '?':<20} p25 {z['p25']:>6.1f} med {z['median']:>5} cv {z['cv']} "
              f"m+ {z['months_with_sales']} sum {z['sum']:>4} abs {z['absorb_pct']}% sold {z['sold_pct']}% rem {z['remaining']} chg {z['price_chg_pct']}")
