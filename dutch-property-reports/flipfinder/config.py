"""Правила отбора и допущения флип-модели — всё, что можно поменять, в одном месте.

Флип: купить свободное здание, сдать его и ровно через 12 месяцев продать сданным
инвестору. Объект проходит, если без убытка его можно купить со скидкой не больше
порога (THRESHOLD) от цены продавца.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "data" / "registry.json"

# ------------------------------------------------------------------ отбор
PRICE_MAX = 2_000_000          # цена продавца, k.k.
AREA_MIN = 150                 # м²
THRESHOLD = 0.11               # скидка для выхода в ноль — не больше 11 %
HORIZON = 12                   # продажа через 12 месяцев
MOTORWAY_KM = 6.0              # до въезда на магистраль A — не дальше, км по дороге
NEARBY_MIN = 30                # «поблизости»: не дальше 30 минут на машине от наших районов
OFFICE_SHARE_MAX = 0.5         # офисов не больше половины площади
# ранжирование: сначала проходящие порог, затем наши районы, затем меньшая скидка.
# True — объект поблизости, прошедший порог, ставится выше объекта в наших районах,
# который тоже прошёл порог, если ему нужна меньшая скидка.
NEARBY_OVER_ZONE = False

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
                     "krommenie", "oostzaan", "muiden", "abcoude"],
    "eindhoven": ["helmond", "nuenen", "geldrop", "waalre", "oirschot", "valkenswaard", "eersel"],
    "nijmegen": ["arnhem", "elst-ge", "beuningen-ge", "cuijk", "malden", "groesbeek", "bemmel", "druten"],
    "agriport": ["heerhugowaard", "schagen", "enkhuizen", "zwaag", "medemblik", "wognum", "alkmaar", "obdam"],
    "rotterdam": ["schiedam", "vlaardingen", "capelle-aan-den-ijssel", "ridderkerk", "barendrecht", "rhoon",
                  "poortugaal", "spijkenisse", "krimpen-aan-den-ijssel", "bleiswijk", "berkel-en-rodenrijs",
                  "bergschenhoek", "nieuwerkerk-aan-den-ijssel", "hendrik-ido-ambacht", "zwijndrecht", "maassluis"],
}

# ------------------------------------------------------------------ рынок: доходность, по которой инвестор купит сданное
# Базовая доходность (аренда в год / цена k.k.) для склада с офисом и новым договором на 5 лет.
# Ориентиры — сданные объекты на funda in business 16–20.10.2026: Амстердам 6,6–8,2 %,
# Лейнден 6,95 %, Ридеркерк 7,54 %, Бест 7,83 %, Неймеген 8,77 %, Хорн 8,5–9,4 %.
EXIT_YIELD = {"ai-amsterdam": 0.070, "ai-schiphol": 0.0725, "rotterdam": 0.075, "eindhoven": 0.0775,
              "nijmegen": 0.085, "agriport": 0.0825}
EXIT_YIELD_CITY = {"almere": 0.0725, "lelystad": 0.0825, "purmerend": 0.0775, "zaandam": 0.0750,
                   "helmond": 0.0800, "arnhem": 0.0825, "alkmaar": 0.0800, "heerhugowaard": 0.0825}
EXIT_ADJ = {"poor": 0.0025, "after_2015": -0.0025, "large_2000m2": 0.0025,
            "lease_ends_2060": 0.005}          # земля в аренде до 2060 года и раньше

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
RENT_SAFETY = 0.92             # ставка — 92 % медианы предложений: в объявлениях ставки выше подписанных
RENT_POOR = 0.95               # здание в плохом состоянии (класс E–G или без класса, до 1990 года) — ещё −5 %
COMPS_KM = 12                  # сопоставимые — не дальше 12 км по прямой
COMPS_K = 15                   # и не больше 15 ближайших
COMPS_AREA = (0.5, 2.0)        # площадью от половины до двойной нашей
MEZZANINE_WEIGHT = 0.30        # антресоль считается как 30 % площади
COMPS_MIN = 4                  # минимум сопоставимых предложений для оценки
VACANT_CAP = 1.20              # цена продажи сданного здания за м² — не выше 120 % медианы цен свободных складов рядом
