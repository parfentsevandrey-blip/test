"""Excel-модели двух новых юнитов.

python3 -I make_two_xlsx.py WORK_TWO_DIR OUT_DIR
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_xlsx_obj import EUR, NUM, PCT, PCT2, build  # noqa: E402

TWO = Path(sys.argv[1])
OUT = Path(sys.argv[2])
load = lambda n: json.loads((TWO / f"{n}.json").read_text())  # noqa: E731
S, B = load("sijsjesbergweg"), load("boeingavenue")

CANDS = [("Sijsjesbergweg 4-C2, Амстердам", "sijsjesbergweg", "новый, с НДС"),
         ("Boeingavenue 307-N, Схипхол-Рейк", "boeingavenue", "новый, с НДС"),
         ("Van Riemsdijkweg 17, Роттердам", "cand-riemsdijkweg", "1957, налог 10,4 %"),
         ("Visserstraat 14-B, Алсмер", "cand-visserstraat", "новый, с НДС"),
         ("Televisieweg 141, Алмере", "cand-televisieweg", "1999, налог 10,4 %"),
         ("Palmpolstraat 99-G26, Алмере", "cand-palmpolstraat", "новый, с НДС"),
         ("Willem Brocadesdreef 180, Хофддорп", "cand-brocadesdreef", "2008, налог 10,4 %"),
         ("Theemsweg 62, Амстердам", "cand-theemsweg", "2002, налог 10,4 %")]
cand_rows = []
for label, n, kind in CANDS:
    M = load(n)
    b = M["bids"]["12"]
    cand_rows.append([label, kind, M["ask"], M["rent"], M["spec"]["exit_bar"], M["at_ask"]["profit"],
                      b["breakeven"] / M["ask"] - 1, b["m10"] / M["ask"] - 1])
cand_table = {"title": "Склады и юниты на продаже в наших зонах: одна модель для всех",
              "columns": ["Объект", "Здание", "Цена продавца", "Аренда в год, оценка", "Доходность для инвестора",
                          "Итог по цене продавца", "Цена «в ноль» к цене продавца", "Цена для € 10 на € 100 затрат"],
              "fmts": [None, None, EUR, EUR, PCT2, EUR, "+0.0%;-0.0%", "+0.0%;-0.0%"],
              "rows": cand_rows,
              "note": "Новые помещения — схема с НДС и продажа на 7-м месяце; старые здания — налог 10,4 % и продажа "
                      "через 12 месяцев. У Van Riemsdijkweg 17 учтена плата за эрфпахт € 8.666 в год."}
TAX = [("Налог на передачу", "Wet op belastingen van rechtsverkeer, ст. 14: 10,4 % для нежилой недвижимости"),
       ("Схема с НДС", "Там же, ст. 15, часть 1, пункт a: покупка нового здания, облагаемая НДС, освобождена от налога "
                       "на передачу; если помещение уже использовалось как рабочий актив, освобождение сохраняется "
                       "при покупке в течение 6 месяцев после начала использования — для сданного помещения это "
                       "начало аренды"),
       ("НДС", "21 %; продажа здания обязательно с НДС до 2 лет после начала использования; аренда с НДС — только "
               "арендатору с правом на вычет"),
       ("Налог на прибыль", "VPB 2026: 19 % до € 200.000, 25,8 % сверх (KVK)")]

infos = {
    "sijsjesbergweg": dict(
        title="Sijsjesbergweg 4-C2, Амстердам", date="20.10.2026",
        sub="Купить новый юнит за свои средства → отделать → сдать → продать сданным инвестору до конца 6 месяцев "
            "после начала аренды. Все цифры — формулы от листов «Допущения» и «Аренда».",
        area_note="162 м² на трёх уровнях: 60 + 60 + 42 м², терраса 17 м²",
        capex_note="голая коробка: тепло и охлаждение, свет, туалет, кухня, пожарная защита",
        hold_note="эрфпахт € 3.191 и ассоциация собственников € 1.200",
        parts_basis={
            "Производственное помещение, первый этаж": "высота 3,7 м, стеклянные складные двери",
            "Помещение под мастерскую или офис, второй этаж": "новые помещения в Зёйдосте — € 209–221",
            "Офис на третьем уровне с террасой 17 м²": "своя терраса на крыше",
        },
        rents=load("sijsjesbergweg-rents"),
        tables=[
            {"title": "Сданные помещения на продаже в Амстердаме", "columns": ["Объект", "м²", "Цена", "Аренда в год",
                                                                               "Доходность"],
             "fmts": [None, NUM, EUR, EUR, PCT2],
             "rows": [["Luchtvaartstraat 4", 295, 1_075_000, 71_291, 0.0663],
                      ["Amsteldijk 128-A", 120, 395_000, 29_000, 0.0734],
                      ["Doblijn 17", 210, 525_000, 42_850, 0.0816]]},
            {"title": "Пустые новые юниты на продаже рядом", "columns": ["Объект", "м²", "Цена без НДС", "€ за м²", "Год"],
             "fmts": [None, NUM, EUR, EUR, None],
             "rows": [["Sijsjesbergweg 4-B2, тот же проект", 162, 380_000, 2346, "2026"],
                      ["Sijsjesbergweg 4-C2, наш юнит", 162, 380_000, 2346, "2026"],
                      ["Donauweg 11-A", 287, 625_000, 2178, "2026"],
                      ["Donauweg 11-A", 449, 915_000, 2038, "2026"],
                      ["Disketteweg 9-A21", 198, 495_000, 2500, "после 2020"]]},
            cand_table],
        sources=[("Объект", "https://www.fundainbusiness.nl/bedrijfshal/amsterdam/object-44558505-sijsjesbergweg-4-c2/ — "
                            "€ 380.000 vrij op naam плюс НДС, было € 410.000; аренда € 36.000 в год плюс НДС; "
                            "эрфпахт до 15.03.2046, € 3.191,45 в год")] + TAX),
    "boeingavenue": dict(
        title="Boeingavenue 307-N, Схипхол-Рейк", date="20.10.2026",
        sub="Купить новый юнит за свои средства → отделать → сдать → продать сданным инвестору до конца 6 месяцев "
            "после начала аренды. Все цифры — формулы от листов «Допущения» и «Аренда».",
        area_note="193 м² на двух этажах по 96,5 м²",
        capex_note="голая коробка: электрическое тепло, свет, туалет, кухня, пожарная защита",
        hold_note="взносы за общий двор и парковку",
        parts_basis={
            "Производственное помещение, первый этаж, высота 3,7 м": "ворота, пол до 2.000 кг/м²",
            "Офис или мастерская, второй этаж, высота 3 м": "юниты 307-P и 307-R — € 159 и € 176",
        },
        rents=load("boeingavenue-rents"),
        tables=[
            {"title": "Сданные помещения на продаже у Схипхола", "columns": ["Объект", "м²", "Цена", "Аренда в год",
                                                                             "Доходность"],
             "fmts": [None, NUM, EUR, EUR, PCT2],
             "rows": [["Tokyostraat 19-G, Лейнден", 148, 325_000, 22_585, 0.0695],
                      ["F.A. Wentstraat 2-A, Алсмер", 381, 495_000, 28_818, 0.0582],
                      ["Boeingavenue 305-F, ставка продавца", 200, 425_000, 33_000, 0.0776]]},
            {"title": "Пустые юниты на продаже рядом", "columns": ["Объект", "м²", "Цена", "€ за м²", "Год"],
             "fmts": [None, NUM, EUR, EUR, None],
             "rows": [["Boeingavenue 307-N, наш юнит", 193, 360_000, 1865, "2025"],
                      ["Boeingavenue 303-N", 125, 335_000, 2680, "2025"],
                      ["Boeingavenue 303-E, с отделкой", 108, 305_000, 2824, "2026"],
                      ["Boeingavenue 305-E", 200, 400_000, 2000, "2020"],
                      ["Boeingavenue 305-F", 200, 425_000, 2125, "2020"],
                      ["Boeingavenue 309-F", 200, 585_000, 2925, "2020"],
                      ["Visserstraat 14-B, Алсмер", 177, 335_000, 1893, "2026"],
                      ["Lakenblekerstraat 21-A5, Алсмер", 212, 430_000, 2028, "2025"],
                      ["Burgemeester Hoffscholteweg 1-02, Алсмер", 228, 380_000, 1667, "2022"]]},
            cand_table],
        sources=[("Объект", "https://www.fundainbusiness.nl/bedrijfshal/schiphol-rijk/object-43009847-boeingavenue-307-n/ — "
                            "€ 360.000 vrij op naam без НДС, налога на передачу нет; сдан в 2025 году; разрешение — "
                            "промышленность с подчинённым офисом")] + TAX),
}
files = {"sijsjesbergweg": "Флип-модель Sijsjesbergweg 4-C2 Амстердам (20.10.26).xlsx",
         "boeingavenue": "Флип-модель Boeingavenue 307-N Схипхол-Рейк (20.10.26).xlsx"}
for key, M in (("sijsjesbergweg", S), ("boeingavenue", B)):
    build(M, infos[key], OUT / files[key])
    print("saved", OUT / files[key])
