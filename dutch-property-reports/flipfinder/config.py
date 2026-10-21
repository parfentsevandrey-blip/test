"""Правила отбора и допущения флип-модели — всё, что можно поменять, в одном месте.

Флип: купить свободное здание, сдать его и ровно через 12 месяцев продать сданным
инвестору. Объект проходит, если без убытка его можно купить со скидкой не больше
порога (THRESHOLD) от цены продавца, а ни один сценарий не даёт убытка при скидке
не больше OFFER_DISCOUNT_MAX.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "data" / "registry.json"

# ------------------------------------------------------------------ отбор
PRICE_MAX = 2_000_000          # цена продавца, k.k.
AREA_MIN = 150                 # м²
THRESHOLD = 0.11               # скидка для выхода в ноль — не больше 11 %
# предложение продавцу — самая высокая цена, при которой ни один сценарий не даёт убытка
OFFER_DISCOUNT_MIN = 0.10      # скидка не меньше 10 %: столько продавцы уступают обычно
OFFER_DISCOUNT_MAX = 0.17      # и не больше 17 %: больше не уступают — объект с большей скидкой не подходит
HORIZON = 12                   # продажа через 12 месяцев
MOTORWAY_KM = 6.0              # до въезда на магистраль A — не дальше, км по дороге
NEARBY_MIN = 30                # «поблизости»: не дальше 30 минут на машине от наших районов
OFFICE_SHARE_MAX = 0.5         # офисов не больше половины площади
# ранжирование: сначала проходящие порог, затем наши районы, затем меньшая скидка.
# True — объект поблизости, прошедший порог, ставится выше объекта в наших районах,
# который тоже прошёл порог, если ему нужна меньшая скидка.
NEARBY_OVER_ZONE = False
BUILDINGS_FIRST = True         # отдельное здание выше юнита в комплексе (bedrijfsverzamelgebouw, appartementsrecht, VvE)

# ослабленный отбор (screen.py --relaxed): когда в строгих рамках нет отдельных зданий
RELAXED = {"THRESHOLD": 0.14, "MOTORWAY_KM": 8.0, "NEARBY_MIN": 40}

# ------------------------------------------------------------------ города: наши районы и окрестности
ZONES = {
    "ai-schiphol": ["hoofddorp", "nieuw-vennep", "schiphol-rijk", "schiphol", "badhoevedorp", "lijnden",
                    "oude-meer", "rijsenhout", "aalsmeer"],
    "ai-amsterdam": ["amsterdam", "diemen", "almere"],
    "eindhoven": ["eindhoven", "veldhoven", "son-en-breugel", "best"],
    "nijmegen": ["nijmegen", "wijchen"],
    "agriport": ["middenmeer", "hoorn-nh", "wieringerwerf"],
    "rotterdam": ["rotterdam"],
}
NEARBY = {
    "ai-schiphol": ["amstelveen", "uithoorn", "haarlem", "zwanenburg", "halfweg", "kudelstaart", "de-kwakel",
                    "cruquius", "vijfhuizen", "hillegom", "lisse"],
    "ai-amsterdam": ["zaandam", "weesp", "duivendrecht", "purmerend", "lelystad", "landsmeer", "wormerveer",
                     "krommenie", "oostzaan", "muiden", "abcoude", "assendelft", "wormer"],
    "eindhoven": ["helmond", "nuenen", "geldrop", "waalre", "oirschot", "valkenswaard", "eersel"],
    "nijmegen": ["arnhem", "elst-ge", "beuningen-ge", "cuijk", "malden", "groesbeek", "bemmel", "druten",
                 "huissen", "duiven", "westervoort", "zevenaar"],
    "agriport": ["heerhugowaard", "schagen", "enkhuizen", "zwaag", "medemblik", "wognum", "alkmaar", "obdam"],
    "rotterdam": ["schiedam", "vlaardingen", "capelle-aan-den-ijssel", "ridderkerk", "barendrecht", "rhoon",
                  "poortugaal", "spijkenisse", "krimpen-aan-den-ijssel", "bleiswijk", "berkel-en-rodenrijs",
                  "bergschenhoek", "nieuwerkerk-aan-den-ijssel", "hendrik-ido-ambacht", "zwijndrecht", "maassluis",
                  "dordrecht", "papendrecht", "alblasserdam", "sliedrecht", "oud-beijerland", "delft", "zoetermeer",
                  "gouda", "waddinxveen", "pijnacker", "naaldwijk"],
}

# ------------------------------------------------------------------ рынок: доходность, по которой инвестор купит сданное
# Базовая доходность (аренда в год / цена k.k.) для склада с офисом и новым договором на 5 лет.
# Ориентиры — сданные объекты на funda in business 16–20.10.2026: Амстердам 6,6–8,2 %,
# Лейнден 6,95 %, Ридеркерк 7,54 %, Бест 7,83 %, Неймеген 8,77 %, Хорн 8,5–9,4 %.
EXIT_YIELD = {"ai-amsterdam": 0.070, "ai-schiphol": 0.0725, "rotterdam": 0.075, "eindhoven": 0.0775,
              "nijmegen": 0.085, "agriport": 0.0825}
EXIT_YIELD_CITY = {"almere": 0.0725, "lelystad": 0.0825, "purmerend": 0.0775, "zaandam": 0.0750,
                   "helmond": 0.0800, "arnhem": 0.0825, "alkmaar": 0.0800, "heerhugowaard": 0.0825,
                   # район Утрехта — по оценке: на 21.10.2026 сданных складов с указанной арендой там почти нет
                   "utrecht": 0.0700, "nieuwegein": 0.0725, "houten": 0.0725, "ijsselstein": 0.0750, "vianen": 0.0750}
EXIT_ADJ = {"poor": 0.0025, "after_2015": -0.0025, "large_2000m2": 0.0025,
            "lease_ends_2060": 0.005}          # земля в аренде до 2060 года и раньше

# ------------------------------------------------------------------ с чем инвестор сравнивает доходность здания
# (обновлять перед отчётом: значение, подпись, источник)
BENCHMARKS = [
    ("deposit", 0.034, "Вклад в банке на 1 год", "лучшие ставки, октябрь 2026"),  # Raisin, Spaarrente.nl
    ("ecb", 0.025, "Ставка ЕЦБ", "по депозитам, с 16.09.2026"),
    ("cpi", 0.034, "Инфляция", "Нидерланды, сентябрь 2026, CBS"),
]

# ------------------------------------------------------------------ сделка и содержание
OVB = 0.104                    # налог на передачу нежилой недвижимости (ст. 14 WBR), платит каждый покупатель
NOTARY = 3_500
DD = 10_000                    # техника, асбест, право
ADVISORY = 0.01                # консультант по покупке
CAPEX_M2 = 25                  # косметический ремонт существующего здания, € за м²
CAPEX_M2_SHELL = 200           # голая коробка: отделка под арендатора
CAPEX_FIXED = 2_500
CAPEX_FIXED_SHELL = 5_000
VAT = 0.21
VAT_LEASE = True               # аренда с НДС арендатору-плательщику: НДС на ремонт и услуги возвращается
LETTING_FEE = 0.15             # брокер по аренде, доля годовой аренды
MARKETING = 1_500
LEASE_MONTH = 5                # арендатор въезжает на 5-м месяце
RENT_FREE = 3                  # 3 месяца без платы
HOLDING = 0.006                # налог OZB владельца и страховка, доля цены в год
VOID_M2 = 3.0                  # энергия и охрана, пока здание пустое, € за м² в год
SALE_FEE = 0.0125              # брокер на продаже
SALE_FIXED = 2_500
VPB = ((200_000, 0.19), (None, 0.258))   # налог на прибыль 2026

# ------------------------------------------------------------------ аренда: как оцениваем
RENT_SAFETY = 1.0              # ставка — медиана предложений рядом: уступка арендатору уже в модели —
                               # 3 бесплатных месяца (RENT_FREE) и брокер (LETTING_FEE)
RENT_POOR = 0.95               # здание в плохом состоянии (класс E–G или без класса, до 1990 года) — ещё −5 %
COMPS_KM = 12                  # сопоставимые — не дальше 12 км по прямой
COMPS_K = 10                   # и не больше 10 ближайших: дальше начинаются другие рынки
COMPS_AREA = (0.5, 2.0)        # площадью от половины до двойной нашей
MEZZANINE_WEIGHT = 0.30        # антресоль считается как 30 % площади
COMPS_MIN = 4                  # минимум сопоставимых предложений для оценки
VACANT_CAP = 1.20              # цена продажи сданного здания за м² — не выше 120 % медианы цен свободных складов рядом
