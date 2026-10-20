"""Excel-модель Van Riemsdijkweg 17.

python3 -I make_classic_xlsx.py WORK_CLASSIC_DIR OUT_DIR
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_xlsx_obj import EUR, NUM, PCT2, build  # noqa: E402

WORK = Path(sys.argv[1])
OUT = Path(sys.argv[2])
load = lambda n: json.loads((WORK / f"{n}.json").read_text())  # noqa: E731
M = load("riemsdijkweg")

CANDS = [("Van Riemsdijkweg 17, Роттердам", "riemsdijkweg", "отдельное, 1957"),
         ("Moezelhavenweg 57, Амстердам", "moezelhavenweg", "юнит, 2001–2010"),
         ("Televisieweg 141, Алмере", "televisieweg", "юнит, 1999"),
         ("Galvanistraat 122-126, Роттердам", "galvanistraat", "три юнита, 2000"),
         ("Willem Brocadesdreef 180, Хофддорп", "brocadesdreef", "юнит, 2008"),
         ("Theemsweg 62, Амстердам", "theemsweg", "юнит, 2002"),
         ("De Huchtstraat 39, Алмере", "huchtstraat", "отдельное, 2000"),
         ("Hooge Zijde 29, Эйндховен", "hoogezijde", "здание, 1998"),
         ("Ambachtsmark 67, Алмере", "ambachtsmark", "здание, 1981"),
         ("Vlambloem 163, Роттердам", "vlambloem", "юнит, 1981"),
         ("Bijlmermeerstraat 5, Хофддорп", "bijlmermeerstraat", "полуотдельное, 1960-е"),
         ("Industrieweg 99-B, Роттердам", "industrieweg", "юнит, 1998")]
rows = []
for label, key, kind in CANDS:
    C = load(key)
    b = C["bids"]["12"]
    rows.append([label, kind, C["ask"], C["rent"], C["spec"]["exit_bar"], C["at_ask"]["profit"],
                 b["breakeven"] / C["ask"] - 1, b["m10"] / C["ask"] - 1])

info = dict(
    title="Van Riemsdijkweg 17, Роттердам", date="20.10.2026",
    sub="Купить пустым за свои средства → отремонтировать → сдать → продать сданным инвестору. "
        "Все цифры — формулы от листов «Допущения» и «Аренда».",
    area_note="шоурум 159 м², зал 404 м², офисы 154 м²",
    capex_note="покраска, свет, мелкий ремонт ворот и кровли",
    hold_note="плата за землю (эрфпахт) € 8.666,40 в год, индексация раз в 5 лет",
    deduct_note="плата за землю: инвестор вычитает её из аренды при оценке",
    parts_basis={
        "Шоурум, первый этаж": "витрины на улицу; соседнее здание — € 155 за м²",
        "Производственный зал, первый этаж, высота 5,2 м": "залы в Ваалхавене — € 80–138 за м²",
        "Офисы, второй этаж": "служебные офисы при зале",
    },
    rents=load("riemsdijkweg-rents"),
    tables=[
        {"title": "Сданные здания на продаже", "columns": ["Объект", "м²", "Цена", "Аренда в год", "Доходность"],
         "fmts": [None, NUM, EUR, EUR, PCT2],
         "rows": [["Valkenierstraat 45, Ридеркерк (договор до 31.08.2027)", 610, 895_000, 67_500, 0.0754],
                  ["Tokyostraat 19-G, Лейнден", 148, 325_000, 22_585, 0.0695],
                  ["Sportlaan 8, Бест", 2966, 1_750_000, 137_000, 0.0783],
                  ["Roggeweg 8, Неймеген", 1133, 1_375_000, 120_588, 0.0877]]},
        {"title": "Пустые здания и юниты на продаже в Роттердаме", "columns": ["Объект", "м²", "Цена", "€ за м²", "Год"],
         "fmts": [None, NUM, EUR, EUR, None],
         "rows": [["Van Riemsdijkweg 17, Ваалхавен", 717, 895_000, 1248, "1957"],
                  ["Galvanistraat 122-126, Спаанс-Полдер", 696, 1_125_000, 1616, "2000"],
                  ["Industrieweg 99-B, Спаанс-Полдер", 306, 495_000, 1618, "1998"],
                  ["Vlambloem 163, Оммоорд", 325, 390_000, 1200, "1981"],
                  ["Otto van Tussenbroekstraat 137", 170, 350_000, 2059, "2001–2010"]]},
        {"title": "Существующие склады и здания в наших зонах: одна модель для всех",
         "columns": ["Объект", "Здание", "Цена продавца", "Аренда в год, оценка", "Доходность для инвестора",
                     "Итог по цене продавца", "Цена «в ноль» к цене продавца", "Цена для € 10 на € 100 затрат"],
         "fmts": [None, None, EUR, EUR, PCT2, EUR, "+0.0%;-0.0%", "+0.0%;-0.0%"], "rows": rows,
         "note": "Покупка с налогом 10,4 %, продажа сданным через 12 месяцев. У Moezelhavenweg 57 право на землю "
                 "заканчивается в 2056 году — доходность 8 %."}],
    sources=[
        ("Объект", "https://www.fundainbusiness.nl/bedrijfshal/rotterdam/object-44546024-van-riemsdijkweg-17/ — "
                   "€ 895.000 kosten koper, 1957, 717 м², энергетический класс A+, эрфпахт € 8.666,40 в год, "
                   "на продаже с 16.10.2026"),
        ("Налог на передачу", "Wet op belastingen van rechtsverkeer, ст. 14: 10,4 % для нежилой недвижимости"),
        ("Перепродажа до 6 месяцев", "Там же, ст. 13: при покупке в течение 6 месяцев после предыдущей покупки "
                                     "база уменьшается на сумму, с которой налог уже уплачен"),
        ("Налог на прибыль", "VPB 2026: 19 % до € 200.000, 25,8 % сверх (KVK)"),
        ("Сопоставимые объекты", "funda in business, выгрузка через Scrapling 20.10.2026: аренда в Ваалхавене, "
                                 "сданные объекты в Ридеркерке, Лейндене, Бесте и Неймегене"),
    ])
name = "Флип-модель Van Riemsdijkweg 17 Роттердам (20.10.26).xlsx"
build(M, info, OUT / name)
print("saved", OUT / name)
