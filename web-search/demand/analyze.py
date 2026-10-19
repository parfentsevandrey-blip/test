"""Индекс спроса по лотам первички Циана: просмотры с поправкой на цену, сравнение с соседями, сегменты.

Вход: каталог demand/collect.py (snap/*.json -- лоты, stats/*.json -- статистика просмотров).
Выход: OUT/demand.xlsx (листы «Лоты», «Сегменты», «Модель», «Методика») и печать главных выводов.

Метрики лота
  v7      -- просмотры за 7 полных дней (без сегодняшнего неполного дня), vpd = v7 / 7
  idx_jk  -- vpd / медиана vpd по ЖК: во сколько раз лот интереснее типичного лота своего ЖК
  excess  -- «избыточный интерес»: остаток модели log(vpd+1) ~ ЖК + комнаты + log(площадь) + цена м² к ЖК×комнатности
             + этаж + отделка + продвижение; exp(остаток)-1 = на сколько % больше просмотров, чем ждёт модель
Сегменты
  attention = доля сегмента в просмотрах / доля сегмента в лотах (как в рабочем проекте: >1 -- сегмент собирает
  больше внимания, чем занимает места в выдаче)
usage: python demand/analyze.py DATA_DIR
"""
import collections, datetime as dt, glob, json, math, os, re, statistics, sys

import numpy as np
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill

d = sys.argv[1]
offers = {}
for f in sorted(glob.glob(f"{d}/snap/*.json")):
    for o in json.load(open(f))["offersSerialized"]:
        offers[o["id"]] = o
rows = []
for oid, o in offers.items():
    p = f"{d}/stats/{oid}.json"
    if not os.path.exists(p):
        continue
    s = json.load(open(p))
    daily = sorted((x["date"], x["views"]) for x in (s.get("daily") or {}).get("dailyViews") or [])
    if len(daily) < 8:
        continue
    full = daily[:-1][-7:]  # 7 полных дней, сегодняшний неполный не берём
    total = int(re.sub(r"\D", "", (s.get("totalViews") or "0").split("просм")[0]) or 0)
    bt, b, nb = o.get("bargainTerms") or {}, o.get("building") or {}, o.get("newbuilding") or {}
    area, price = float(o.get("totalArea") or 0), bt.get("priceRur")
    if not area or not price:
        continue
    rooms = o.get("roomsCount")
    rk = "студия" if o.get("flatType") == "studio" or rooms == 0 else ("своб." if rooms is None else f"{min(rooms, 4)}{'+' if rooms >= 4 else ''}к")
    floors = b.get("floorsCount") or 0
    fl = o.get("floorNumber") or 0
    created = dt.date.fromisoformat(o["creationDate"][:10])
    rows.append({
        "id": oid, "url": o["fullUrl"].split("?")[0], "jk": nb.get("name"), "jk_id": nb.get("id"),
        "house": (nb.get("house") or {}).get("name"), "rooms": rk, "area": area, "price": price, "pm2": price / area,
        "floor": fl, "floors": floors,
        "floor_band": "1-2" if fl <= 2 else ("верх 3" if floors and fl > floors - 3 else "середина"),
        "finish": {"without": "без отделки", "preFine": "предчистовая", "fine": "чистовая", "rough": "черновая",
                   "design": "дизайнерская"}.get(o.get("decoration"), o.get("decoration") or "н/д"),
        "promo": bool(o.get("isTop3") or o.get("isPremium") or o.get("isColorized") or o.get("isAuction") or o.get("brandingLevel")),
        "seller": "застройщик" if o.get("fromDeveloper") else "агент/собственник",
        "deadline": "сдан" if (b.get("deadline") or {}).get("isComplete") else str((b.get("deadline") or {}).get("year") or "н/д"),
        "created": created.isoformat(), "total": total, "v7": sum(v for _, v in full), "today": daily[-1][1],
        "daily": [v for _, v in daily],
    })
if not rows:
    sys.exit("нет данных")
for r in rows:
    r["vpd"] = r["v7"] / 7

# цена м² относительно медианы своего ЖК и комнатности; интерес относительно ЖК
g = collections.defaultdict(list)
for r in rows:
    g[(r["jk_id"], r["rooms"])].append(r["pm2"])
for r in rows:
    r["rel_price"] = r["pm2"] / statistics.median(g[(r["jk_id"], r["rooms"])])
gj = collections.defaultdict(list)
for r in rows:
    gj[(r["jk_id"], r["seller"])].append(r["vpd"])
for r in rows:
    m = statistics.median(gj[(r["jk_id"], r["seller"])])
    r["idx_jk"] = r["vpd"] / m if m else None

# модель: log(vpd+1) ~ признаки (МНК с фиктивными переменными)
cats = {k: sorted({r[k] for r in rows}) for k in ("seller", "jk", "rooms", "floor_band", "finish")}
names, X = ["const", "log(площадь)", "цена м² к ЖК×комн., +10%", "брендирование/продвижение"], []
for k, vals in cats.items():
    names += [f"{k}={v}" for v in vals[1:]]
for r in rows:
    x = [1.0, math.log(r["area"]), (r["rel_price"] - 1) * 10, float(r["promo"])]
    for k, vals in cats.items():
        x += [1.0 if r[k] == v else 0.0 for v in vals[1:]]
    X.append(x)
X, y = np.array(X), np.array([math.log(r["vpd"] + 1) for r in rows])
beta, *_ = np.linalg.lstsq(X, y, rcond=None)
pred = X @ beta
r2 = 1 - ((y - pred) ** 2).sum() / ((y - y.mean()) ** 2).sum()
for r, e in zip(rows, y - pred):
    r["excess"] = math.exp(e) - 1


def segments(key):
    tot_v, tot_n = sum(r["v7"] for r in rows), len(rows)
    s = collections.defaultdict(list)
    for r in rows:
        s[key(r)].append(r)
    out = []
    for k, rs in s.items():
        if len(rs) < 5:
            continue
        sv = sum(r["v7"] for r in rs)
        out.append((k, len(rs), round(statistics.median(r["vpd"] for r in rs), 1),
                    round((sv / tot_v) / (len(rs) / tot_n), 2) if tot_v else None,
                    round(statistics.median(r["excess"] for r in rs) * 100)))
    return sorted(out, key=lambda x: -(x[3] or 0))


SEG = {
    "Комнатность": lambda r: r["rooms"],
    "Площадь": lambda r: next(f"{a}-{b} м²" for a, b in ((0, 30), (30, 40), (40, 55), (55, 75), (75, 100), (100, 10**4)) if a <= r["area"] < b),
    "Цена м² к ЖК×комн.": lambda r: "дешевле на 5%+" if r["rel_price"] < .95 else ("дороже на 5%+" if r["rel_price"] > 1.05 else "±5%"),
    "Этаж": lambda r: r["floor_band"],
    "Отделка": lambda r: r["finish"],
    "Срок сдачи": lambda r: r["deadline"],
    "Продвижение": lambda r: "брендирование/продвижение" if r["promo"] else "нет",
    "Продавец": lambda r: r["seller"],
    "ЖК": lambda r: r["jk"],
}

# ---------- Excel ----------
wb = Workbook()
H, F = Font(bold=True, color="FFFFFF"), PatternFill("solid", fgColor="1F3864")


def sheet(ws, header, data):
    ws.append(header)
    for c in ws[1]:
        c.font, c.fill = H, F
    for row in data:
        ws.append(row)
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions
    for col in ws.columns:
        ws.column_dimensions[col[0].column_letter].width = min(40, max(10, max(len(str(c.value or "")) for c in col) + 2))


ws = wb.active
ws.title = "Лоты"
sheet(ws, ["Продавец", "ЖК", "Корпус", "Комн.", "Площадь", "Этаж", "Цена, ₽", "₽/м²", "Цена к ЖК×комн.", "Отделка", "Продвижение",
           "Просм./день (7 дн)", "Просм. сегодня", "Всего просм.", "Интерес к ЖК, раз", "Избыточный интерес, %", "Опубликовано", "Ссылка"],
      [[r["seller"], r["jk"], r["house"], r["rooms"], r["area"], f'{r["floor"]}/{r["floors"]}', r["price"], round(r["pm2"]), round(r["rel_price"], 3),
        r["finish"], "да" if r["promo"] else "", round(r["vpd"], 1), r["today"], r["total"],
        round(r["idx_jk"], 2) if r["idx_jk"] else None, round(r["excess"] * 100), r["created"], r["url"]]
       for r in sorted(rows, key=lambda r: -r["excess"])])
ws2 = wb.create_sheet("Сегменты")
seg_rows = []
for name, fn in SEG.items():
    for k, n, vpd, att, ex in segments(fn):
        seg_rows.append([name, k, n, vpd, att, ex])
sheet(ws2, ["Разрез", "Сегмент", "Лотов", "Медиана просм./день", "Индекс внимания (доля просм./доля лотов)", "Медиана избыточного интереса, %"], seg_rows)
ws3 = wb.create_sheet("Модель")
sheet(ws3, ["Признак", "Эффект на просмотры, %"], [[n, round((math.exp(b) - 1) * 100, 1)] for n, b in zip(names, beta) if n != "const"]
      + [["R² модели", round(r2, 3)], ["Лотов в модели", len(rows)]])
ws4 = wb.create_sheet("Методика")
for line in (__doc__ or "").strip().splitlines():
    ws4.append([line])
ws4.column_dimensions["A"].width = 140
path = f"{d}/demand.xlsx"
wb.save(path)

print(f"лотов с просмотрами: {len(rows)}, ЖК: {len({r['jk_id'] for r in rows})}, R² модели: {r2:.2f}")
print("эффекты признаков на просмотры (%):")
for n, b in [x for x in zip(names, beta) if x[0] != "const" and not x[0].startswith(("jk=", "rooms=", "floor_band=", "finish="))]:
    print(f"  {n}: {(math.exp(b) - 1) * 100:+.0f}%")
for name in ("Продавец", "Комнатность", "Площадь", "Цена м² к ЖК×комн.", "Этаж", "Отделка", "Продвижение"):
    print(name + ":", "; ".join(f"{k}: внимание {att}, изб. {ex:+d}%" for k, n, vpd, att, ex in segments(SEG[name])))
print("топ-5 по избыточному интересу:")
for r in sorted(rows, key=lambda r: -r["excess"])[:5]:
    print(f"  [{r['seller']}] {r['jk']} {r['rooms']} {r['area']}м² эт.{r['floor']} {r['price']:,}₽ ({r['rel_price']:.2f} к ЖК) "
          f"{r['vpd']:.0f}/день, +{r['excess'] * 100:.0f}% к модели {r['url']}")
print("excel:", path)
