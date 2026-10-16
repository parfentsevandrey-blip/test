"""Кандидаты на флип одной моделью: прибыль по цене продавца и цена для маржи 10 %.

python3 -I candidates.py OUT_JSON
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import flipmodel as F  # noqa: E402
from kernweg import BASE  # noqa: E402

# аренда — оценка по частям здания и местным ставкам, доходность выхода — по сданным объектам рядом
CANDS = [
    {"name": "Kernweg 1, Хорн", "terr": "Хорн 80, Agriport A7 и Хорн", "inside": True, "price": 1_495_000,
     "area": 2016, "rent": 125_625, "bar": 0.0825, "since": "6+ мес.", "cut": None, "year": "1970/2007",
     "note": "переезд владельца, продажа части возможна"},
    {"name": "Rijstenweg 4-6, Эйндховен", "terr": "Де Хюрк, Эйндховен", "inside": True, "price": 1_795_000,
     "area": 1995, "rent": 135_875, "bar": 0.0775, "since": "4 мес.", "cut": None, "year": "1966",
     "note": "здание 1966 года, два бывших объекта"},
    {"name": "Electronplein 1 (часть), Хорн", "terr": "Хорн 80", "inside": True, "price": 1_099_000,
     "area": 1100, "rent": 71_500, "bar": 0.0825, "since": "3 нед.", "cut": None, "year": "1999",
     "note": "часть большого комплекса"},
    {"name": "Galvanistraat 122-126, Роттердам", "terr": "Роттердам", "inside": True, "price": 1_125_000,
     "area": 696, "rent": 78_500, "bar": 0.075, "since": "4 мес.", "cut": None, "year": "2000",
     "note": "эрфпахт оплачен до 2048"},
    {"name": "Industrieweg 99-B, Роттердам", "terr": "Роттердам", "inside": True, "price": 495_000,
     "area": 306, "rent": 35_190, "bar": 0.075, "since": "2 мес.", "cut": None, "year": "1998",
     "note": "эрфпахт"},
    {"name": "Vlambloem 163, Роттердам", "terr": "Роттердам", "inside": True, "price": 390_000,
     "area": 325, "rent": 28_000, "bar": 0.0775, "since": "3 мес.", "cut": None, "year": "1981",
     "note": "эрфпахт оплачен только до 2031"},
    {"name": "Karwijstraat 29-C и 29-D, Ньиве-Веннеп", "terr": "рядом со Схипхолом", "inside": False,
     "price": 825_000, "area": 620, "rent": 65_100, "bar": 0.0725, "since": "6+ мес.", "cut": "−11 % и −6 %",
     "year": "2009", "note": "высота 3 м, до трассы 5 км и больше"},
    {"name": "Petroleumhavenweg 24, Амстердам", "terr": "Вестпорт, рядом со Слотердейком", "inside": False,
     "price": 550_000, "area": 278, "rent": 38_920, "bar": 0.0725, "since": "3 мес.", "cut": "−15 %",
     "year": "1997", "note": "эрфпахт, плата € 3.615 в год"},
]


def main():
    out = []
    for c in CANDS:
        p = dict(BASE, price=c["price"], area=c["area"], rent_m2=c["rent"] / c["area"], exit_bar=c["bar"])
        at_ask = F.run(p)
        m10 = F.solve("price", 0.10, "margin", 0.3 * c["price"], 1.5 * c["price"], p)
        be = F.solve("price", 0.0, "profit", 0.3 * c["price"], 1.5 * c["price"], p)
        out.append({**c, "giy_ask": c["rent"] / c["price"], "rent_m2": c["rent"] / c["area"],
                    "profit_ask": at_ask["profit"], "margin_ask": at_ask["margin"],
                    "price_be": be, "price_m10": m10, "disc_m10": m10 / c["price"] - 1,
                    "disc_be": be / c["price"] - 1})
        print(f"{c['name']:42} аренда {c['rent'] / c['price'] * 100:5.2f}% выход {c['bar'] * 100:.2f}% "
              f"прибыль {at_ask['profit']:>9,.0f} ({at_ask['margin'] * 100:5.1f}%) безубыток {be:>10,.0f} ({(be / c['price'] - 1) * 100:5.1f}%) "
              f"маржа 10% {m10:>10,.0f} ({(m10 / c['price'] - 1) * 100:5.1f}%)")
    Path(sys.argv[1]).write_text(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
