# -*- coding: utf-8 -*-
"""
Модель маржи на конкретной квартире 72 m2 в Утрехте.
Все параметры привязаны к публичным источникам (см. слайд «Методология»).
"""
import json

M2 = 72

# --- Индекс цен Утрехта: Utrecht Monitor, средняя цена сделки (2015=100) ---
IDX = {2015: 100.0, 2019: 152.9, 2021: 191.1, 2024: 214.7, 2026: 237.8}
BASE_2015 = 215_000  # так, что 2026 ≈ 510 000 (= 7 100 €/m2, реализованная цена Утрехт 2026)

PRICE = {y: round(BASE_2015 * IDX[y] / 100, -3) for y in IDX}

# WOZ отстаёт от рынка ~1,5 года (дата оценки = 1 января года-1)
WOZ = {2015: 175_000, 2019: 285_000, 2021: 360_000, 2024: 440_000, 2026: 480_000}

# Налог на переход права (overdrachtsbelasting) для арендодателя
OVB = {2015: 0.02, 2019: 0.02, 2021: 0.08, 2024: 0.104, 2026: 0.08}

# Прочие расходы на покупку (нотариус, оценка, брокер, ипотечный советник)
BUYCOST = {2015: 3_500, 2019: 4_500, 2021: 5_500, 2024: 6_500, 2026: 7_000}

# Ипотека: 70% LTV, только проценты (aflossingsvrij), ставка verhuurhypotheek
LTV = 0.70
RATE = {2015: 0.043, 2019: 0.033, 2021: 0.029, 2024: 0.056, 2026: 0.059}

# Арендная плата (kale huur, €/мес)
RENT_MARKET = {2015: 1040, 2019: 1120, 2021: 1245, 2024: 1368, 2026: 1620}
# Юридический потолок при НОВОМ договоре (WWS 186 баллов -> средний сегмент)
RENT_LEGAL = {2015: None, 2019: None, 2021: None, 2024: 1157.95, 2026: 1228.07}

# Операционные расходы, € / год
OPEX = {
    2015: {"VvE": 1450, "onderhoud": 900, "belastingen": 420, "verzekering": 190},
    2019: {"VvE": 1620, "onderhoud": 1020, "belastingen": 500, "verzekering": 230},
    2021: {"VvE": 1760, "onderhoud": 1120, "belastingen": 560, "verzekering": 265},
    2024: {"VvE": 2150, "onderhoud": 1380, "belastingen": 700, "verzekering": 330},
    2026: {"VvE": 2350, "onderhoud": 1500, "belastingen": 780, "verzekering": 360},
}
MGMT_PCT = 0.05  # управление + простой + смена арендатора

# --- Box 3 ---
# до 2023: leegwaarderatio 45–85%; с 2023: 73–100%
LWR_OLD = [(0.01, .45), (0.02, .51), (0.03, .56), (0.04, .62), (0.05, .67), (0.06, .73), (0.07, .78), (9, .85)]
LWR_NEW = [(0.01, .73), (0.02, .79), (0.03, .84), (0.04, .90), (0.05, .95), (9, 1.00)]

def lwr(rent_year, woz, year):
    r = rent_year / woz
    table = LWR_OLD if year < 2023 else LWR_NEW
    for hi, pct in table:
        if r < hi:
            return pct, r
    return table[-1][1], r

# 2015/2019: налог на ЧИСТЫЕ активы. 2021+: раздельные форфейты активов и долгов.
BOX3 = {
    2015: {"mode": "net", "forfait": 0.04, "tarief": 0.30},
    2019: {"mode": "net", "forfait": 0.04451, "tarief": 0.30},
    2021: {"mode": "split", "assets": 0.0569, "debt": 0.0246, "tarief": 0.31},
    2024: {"mode": "split", "assets": 0.0604, "debt": 0.0247, "tarief": 0.36},
    2026: {"mode": "split", "assets": 0.0600, "debt": 0.0270, "tarief": 0.36},
}

def box3(woz, debt, rent_year, year):
    pct, ratio = lwr(rent_year, woz, year)
    value = woz * pct
    p = BOX3[year]
    if p["mode"] == "net":
        tax = (value - debt) * p["forfait"] * p["tarief"]
    else:
        tax = (value * p["assets"] - debt * p["debt"]) * p["tarief"]
    return round(tax), round(value), pct, ratio

def snapshot(year, rent_mo=None, use_debt=True, label=None):
    price = PRICE[year]
    ovb = round(price * OVB[year])
    invest = price + ovb + BUYCOST[year]
    debt = round(price * LTV) if use_debt else 0
    equity = invest - debt
    interest = round(debt * RATE[year])

    if rent_mo is None:
        rent_mo = RENT_LEGAL[year] or RENT_MARKET[year]
    rent_y = round(rent_mo * 12)

    op = dict(OPEX[year])
    op["beheer"] = round(rent_y * MGMT_PCT)
    opex = sum(op.values())

    tax, b3val, pct, ratio = box3(WOZ[year], debt, rent_y, year)
    net = rent_y - interest - opex - tax

    return {
        "year": year, "label": label or str(year), "price": price, "ovb": ovb,
        "ovb_pct": OVB[year] * 100, "buycost": BUYCOST[year], "invest": invest,
        "debt": debt, "equity": equity, "rate": RATE[year] * 100, "interest": interest,
        "rent_mo": round(rent_mo, 2), "rent_y": rent_y, "opex": opex, "opex_detail": op,
        "woz": WOZ[year], "b3_value": b3val, "lwr": round(pct * 100),
        "rent_woz": round(ratio * 100, 2), "box3": tax, "net": net,
        "coc": round(net / equity * 100, 2) if equity else None,
        "gross_yield": round(rent_y / price * 100, 2),
        "net_op_yield": round((rent_y - opex) / invest * 100, 2),
        "spread": round((rent_y - opex) / invest * 100 - RATE[year] * 100, 2),
        "rent_m2": round(rent_mo / M2, 2),
        "price_m2": round(price / M2),
    }

rows = [snapshot(y) for y in (2015, 2019, 2021, 2024, 2026)]

print("=" * 118)
print("КЕЙС: 72 м², Утрехт — покупка по рынку соответствующего года, 70% LTV, только проценты")
print("=" * 118)
hdr = f"{'':<22}" + "".join(f"{r['label']:>19}" for r in rows)
print(hdr)
def line(name, key, fmt="{:,.0f}", suffix=""):
    s = f"{name:<22}"
    for r in rows:
        v = r[key]
        s += f"{(fmt.format(v) + suffix) if v is not None else '—':>19}"
    print(s.replace(",", " "))

line("Цена покупки, €", "price")
line("Цена, €/м²", "price_m2")
line("OVB, %", "ovb_pct", "{:.1f}", "%")
line("OVB, €", "ovb")
line("Всего вложено, €", "invest")
line("Ипотека 70%, €", "debt")
line("Свои деньги, €", "equity")
line("Ставка, %", "rate", "{:.1f}", "%")
print("-" * 118)
line("Аренда, €/мес", "rent_mo", "{:,.2f}")
line("Аренда, €/м²/мес", "rent_m2", "{:,.2f}")
line("Аренда, €/год", "rent_y")
print("-" * 118)
line("− Проценты, €", "interest")
line("− Операц. расходы, €", "opex")
line("− Box 3, €", "box3")
print("-" * 118)
line("= Чистый поток, €/год", "net")
line("Доходность на свои, %", "coc", "{:.2f}", "%")
line("Валовая дох-ть, %", "gross_yield", "{:.2f}", "%")
line("Чистая опер. дох-ть, %", "net_op_yield", "{:.2f}", "%")
line("Спред к ставке, п.п.", "spread", "{:+.2f}")
print("-" * 118)
line("WOZ, €", "woz")
line("Leegwaarderatio, %", "lwr", "{:.0f}", "%")
line("База box 3, €", "b3_value")

print()
print("=" * 118)
print("СЦЕНАРИИ 2026")
print("=" * 118)

scen = {
    "Факт 2026: закон + ипотека 70%": snapshot(2026),
    "Если бы аренда была рыночной (€1 620)": snapshot(2026, rent_mo=1620),
    "Покупка за наличные, аренда по закону": snapshot(2026, use_debt=False),
    "Наличные + рыночная аренда": snapshot(2026, rent_mo=1620, use_debt=False),
    "2027: смягчение WOZ-надбавка +€96/мес": snapshot(2026, rent_mo=1228.07 + 96),
}
for k, v in scen.items():
    coc = f"{v['coc']:+.2f}%" if v["coc"] is not None else "—"
    print(f"{k:<44} поток {v['net']:>9,.0f} €/год   на свои {coc:>9}   "
          f"(свои {v['equity']:>9,.0f} €)".replace(",", " "))

# сколько нужен рост цены, чтобы выйти в ноль
f = snapshot(2026)
need = -f["net"] / f["price"] * 100
print()
print(f"Чтобы компенсировать отрицательный поток, квартира должна дорожать на "
      f"{need:.2f}% в год ({-f['net']:,.0f} € / {f['price']:,.0f} €).".replace(",", " "))
print(f"Фактический рост Утрехта 2015→2026: {(IDX[2026]/IDX[2015])**(1/11)*100-100:.2f}% в год; "
      f"2021→2026: {(IDX[2026]/IDX[2021])**(1/5)*100-100:.2f}% в год.")

# «мост» 2015 -> 2026
a, b = rows[0], rows[-1]
print()
print("=" * 118)
print("МОСТ 2015 → 2026 (изменение годового потока, €)")
print("=" * 118)
bridge = [
    ("Поток 2015", a["net"]),
    ("Аренда", b["rent_y"] - a["rent_y"]),
    ("Проценты по ипотеке", -(b["interest"] - a["interest"])),
    ("Операционные расходы", -(b["opex"] - a["opex"])),
    ("Налог box 3", -(b["box3"] - a["box3"])),
    ("Поток 2026", b["net"]),
]
for n, v in bridge:
    print(f"  {n:<26}{v:>+10,.0f}".replace(",", " "))
chk = a["net"] + (b["rent_y"] - a["rent_y"]) - (b["interest"] - a["interest"]) \
      - (b["opex"] - a["opex"]) - (b["box3"] - a["box3"])
print(f"  {'контроль':<26}{chk:>+10,.0f}   (должно совпасть с 2026)".replace(",", " "))

json.dump({"rows": rows, "scen": {k: v for k, v in scen.items()}},
          open("model.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("\n→ model.json записан")


print()
print("=" * 118)
print("РАЗЛОЖЕНИЕ МОСТА 2015 → 2026 ПО ФАКТОРАМ")
print("=" * 118)
a, b = rows[0], rows[-1]
mkt26 = round(RENT_MARKET[2026] * 12)
vol_eff = round((b["debt"] - a["debt"]) * RATE[2015])          # больше долга при старой ставке
rate_eff = round(b["debt"] * (RATE[2026] - RATE[2015]))        # та же сумма долга, новая ставка
steps = [
    ("Чистый поток 2015", a["net"], "base"),
    ("Рост рыночной аренды", mkt26 - a["rent_y"], "up"),
    ("Потолок аренды (Wet betaalbare huur)", -(mkt26 - b["rent_y"]), "down"),
    ("Цена выросла → больше долга", -vol_eff, "down"),
    ("Ставка по ипотеке выросла", -rate_eff, "down"),
    ("Операционные расходы", -(b["opex"] - a["opex"]), "down"),
    ("Налог box 3", -(b["box3"] - a["box3"]), "down"),
    ("Чистый поток 2026", b["net"], "base"),
]
run = 0
for n, v, k in steps:
    if k == "base":
        run = v
        print(f"  {n:<40}{v:>+10,.0f}".replace(",", " "))
    else:
        run += v
        print(f"  {n:<40}{v:>+10,.0f}   → {run:>+10,.0f}".replace(",", " "))
print(f"  {'разовый OVB: 4 300 € → 40 880 €':<40}{-(b['ovb']-a['ovb']):>+10,.0f}   (единовременно, не в потоке)".replace(",", " "))

print()
print("=" * 118)
print("КОГОРТА 2015 ГОДА: ЧТО У НЕЁ ПРОИСХОДИТ В 2026")
print("=" * 118)
old_debt, old_rate = a["debt"], RATE[2015]
old_rent_mo = 1450          # старый договор в свободном секторе, ежегодная индексация
old_rent_y = old_rent_mo * 12
old_int = round(old_debt * old_rate)
old_opex = sum(OPEX[2026].values()) + round(old_rent_y * MGMT_PCT)
old_b3, old_val, old_pct, _ = box3(WOZ[2026], old_debt, old_rent_y, 2026)
old_net = old_rent_y - old_int - old_opex - old_b3
print(f"  Аренда по старому договору (свободный сектор) {old_rent_y:>10,.0f} €/год".replace(",", " "))
print(f"  − проценты ({old_debt:,.0f} € @ {old_rate*100:.1f}%)          {-old_int:>10,.0f}".replace(",", " "))
print(f"  − операционные расходы                        {-old_opex:>10,.0f}".replace(",", " "))
print(f"  − box 3 (база {old_val:,.0f} €, LWR {old_pct*100:.0f}%)        {-old_b3:>10,.0f}".replace(",", " "))
print(f"  = ЧИСТЫЙ ПОТОК                                {old_net:>+10,.0f} €/год".replace(",", " "))
new_rent_y = round(RENT_LEGAL[2026] * 12)
new_opex = sum(OPEX[2026].values()) + round(new_rent_y * MGMT_PCT)
new_b3, nv, np_, _ = box3(WOZ[2026], old_debt, new_rent_y, 2026)
new_net = new_rent_y - old_int - new_opex - new_b3
print(f"  ...а если арендатор съедет и нужен НОВЫЙ договор (потолок {RENT_LEGAL[2026]:,.2f} €/мес):".replace(",", " "))
print(f"  = ЧИСТЫЙ ПОТОК                                {new_net:>+10,.0f} €/год".replace(",", " "))
equity_now = PRICE[2026] - old_debt
print(f"\n  Вложено своих в 2015: {a['equity']:,.0f} €".replace(",", " "))
print(f"  Чистый капитал в 2026: {PRICE[2026]:,.0f} − {old_debt:,.0f} = {equity_now:,.0f} €  "
      f"(×{equity_now/a['equity']:.1f} за 11 лет)".replace(",", " "))
print(f"  Прирост стоимости: {PRICE[2026]-PRICE[2015]:+,.0f} € — налога на прирост капитала в NL нет".replace(",", " "))
tenanted = round(PRICE[2026] * 0.80)
print(f"  Продажа с арендатором ≈ 80% рынка: {tenanted:,.0f} €;  пустой: {PRICE[2026]:,.0f} €;  "
      f"премия за пустую: {PRICE[2026]-tenanted:+,.0f} €".replace(",", " "))
print(f"  Премия за пустую квартиру = {(PRICE[2026]-tenanted)/abs(old_net):.0f} лет текущего денежного потока")
