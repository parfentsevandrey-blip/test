"""
Барометр мобилизации — конфигурация.

Здесь живут:
  * список источников (RSS независимых СМИ, Telegram-каналы, X/Twitter, DeepState);
  * лексикон сигналов (ключевые фразы RU/EN с полярностью и весом);
  * веса категорий и параметры скоринга барометра 0–100.

Всё, что нужно менять для настройки чувствительности барометра, собрано тут.
"""

from __future__ import annotations

import os

# --------------------------------------------------------------------------- #
#  Общие параметры                                                            #
# --------------------------------------------------------------------------- #

# Как часто фоновый планировщик пересчитывает барометр (минуты).
REFRESH_MINUTES = int(os.environ.get("BAROMETER_REFRESH_MINUTES", "60"))

# Окно, за которое учитываются новости при расчёте (дни).
WINDOW_DAYS = int(os.environ.get("BAROMETER_WINDOW_DAYS", "10"))

# Период полураспада «свежести» новости (дни): чем старше — тем меньше вклад.
HALFLIFE_DAYS = 3.0

# Сетевые настройки.
HTTP_TIMEOUT = 20
USER_AGENT = "MobilizationBarometer/1.0 (OSINT news aggregator; +local)"

# Путь к БД и сэмплам.
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("BAROMETER_DB", os.path.join(BASE_DIR, "data", "barometer.db"))
SAMPLES_DIR = os.path.join(BASE_DIR, "data", "samples")

# --------------------------------------------------------------------------- #
#  Источники: RSS независимых российских / эмигрантских СМИ                    #
# --------------------------------------------------------------------------- #
# stream — одна из четырёх «опор» барометра (для группировки в интерфейсе):
#   media     — независимые СМИ (опора №1)
#   deepstate — карта фронта (опора №2)
#   analysts  — аналитики и соцсети, X/Telegram (опора №3)
#   raids     — облавы/бусификация (опора №4; обычно подмножество media)
#
# source_weight — доверие к источнику (множитель вклада сигналов).

RSS_SOURCES = [
    {"id": "meduza",      "name": "Meduza",                "url": "https://meduza.io/rss/all",            "lang": "ru", "stream": "media", "source_weight": 1.0},
    {"id": "mediazona",   "name": "Медиазона",             "url": "https://zona.media/rss",               "lang": "ru", "stream": "media", "source_weight": 1.0},
    {"id": "verstka",     "name": "Вёрстка",               "url": "https://verstka.media/feed",           "lang": "ru", "stream": "media", "source_weight": 1.0},
    {"id": "novaya_eu",   "name": "Новая газета Европа",    "url": "https://novayagazeta.eu/feed/rss",     "lang": "ru", "stream": "media", "source_weight": 1.0},
    {"id": "holod",       "name": "Холод",                 "url": "https://holod.media/feed/",            "lang": "ru", "stream": "media", "source_weight": 0.9},
    {"id": "mostimes",    "name": "The Moscow Times",       "url": "https://www.themoscowtimes.com/rss/news", "lang": "en", "stream": "media", "source_weight": 1.0},
    {"id": "bbc_ru",      "name": "BBC News Русская служба", "url": "https://feeds.bbci.co.uk/russian/rss.xml", "lang": "ru", "stream": "media", "source_weight": 0.9},
]

# --------------------------------------------------------------------------- #
#  Источники: Telegram (публичный веб-превью t.me/s/<channel>, без ключей)     #
# --------------------------------------------------------------------------- #
TELEGRAM_CHANNELS = [
    {"id": "tg_ostorozhno", "name": "Осторожно, новости", "channel": "ostorozhno_novosti", "stream": "raids",    "source_weight": 0.7},
    {"id": "tg_astra",      "name": "ASTRA",              "channel": "astrapress",          "stream": "raids",    "source_weight": 0.7},
    {"id": "tg_sota",       "name": "SOTA",               "channel": "sotaproject",         "stream": "raids",    "source_weight": 0.6},
    {"id": "tg_mozhem",     "name": "Можем объяснить",    "channel": "mozhemobyasnit",      "stream": "analysts", "source_weight": 0.6},
]

# --------------------------------------------------------------------------- #
#  Источники: X / Twitter — реальный путь через API v2, иначе сэмпл           #
# --------------------------------------------------------------------------- #
# Реальная выгрузка включается, если задан X_BEARER_TOKEN (Twitter API v2).
# Иначе адаптер берёт data/samples/twitter.json и помечает поток как «sample».
X_BEARER_TOKEN = os.environ.get("X_BEARER_TOKEN", "").strip()
X_QUERY = '(mobilization OR мобилизация OR "draft Russia" OR conscription) -is:retweet lang:en'
X_ACCOUNTS = [  # ведущие военные аналитики / OSINT (для справки и сэмплов)
    "TheStudyofWar",      # ISW
    "war_mapper",
    "RALee85",            # Rob Lee
    "DefMon3",
]

# --------------------------------------------------------------------------- #
#  Источник: DeepState (карта фронта)                                         #
# --------------------------------------------------------------------------- #
DEEPSTATE_LAST_URL = "https://deepstatemap.live/api/history/last"
# Масштаб для нормировки суточного изменения площади (км²/день).
DEEPSTATE_SCALE_KM2 = 120.0

# --------------------------------------------------------------------------- #
#  Категории сигналов и их веса в итоговом барометре (сумма = 1.0)            #
# --------------------------------------------------------------------------- #
SIGNAL_CATEGORIES = {
    "legislation":      {"label": "Законы и указы",            "weight": 0.28, "scale": 3.0},
    "mobilization_prep":{"label": "Подготовка, облавы, повестки","weight": 0.24, "scale": 4.0},
    "official_signal":  {"label": "Заявления и опровержения власти", "weight": 0.16, "scale": 3.0},
    "frontline_text":   {"label": "Фронт: потери и нехватка людей", "weight": 0.12, "scale": 4.0},
    "deepstate":        {"label": "DeepState: продвижение/отступление", "weight": 0.10, "scale": 1.0},
    "society_economy":  {"label": "Общество и экономика",      "weight": 0.10, "scale": 4.0},
}

# Параметры итогового сигмоида: barometer = 100 * sigmoid(K * X + B),
# где X = Σ weight[c] * norm[c], norm[c] ∈ [-1, 1].
# При X = 0 (фон) барометр ≈ 19; при максимальной эскалации ≈ 89.
SCORE_K = 3.6
SCORE_B = -1.45

# Порог, при достижении которого считаем мобилизацию «фактически объявленной».
ANNOUNCED_THRESHOLD = 92
# Порог для прогноза даты (к нему экстраполируем рост).
FORECAST_THRESHOLD = 90

# --------------------------------------------------------------------------- #
#  Лексикон сигналов                                                           #
#  (category, polarity, weight, term)  — term ищется как подстрока в           #
#  нормализованном тексте (нижний регистр, ё→е).                              #
# --------------------------------------------------------------------------- #
LEXICON = [
    # --- legislation (законы/указы) ---------------------------------------- #
    ("legislation", +1, 2.0, "указ о мобилизац"),
    ("legislation", +1, 1.6, "закон о мобилизац"),
    ("legislation", +1, 2.0, "всеобщая мобилизац"),
    ("legislation", +1, 1.8, "вторая волна мобилизац"),
    ("legislation", +1, 1.8, "новая волна мобилизац"),
    ("legislation", +1, 1.0, "указ о призыв"),
    ("legislation", +1, 1.4, "военное положение"),
    ("legislation", +1, 1.5, "закрытие границ"),
    ("legislation", +1, 1.3, "запрет на выезд"),
    ("legislation", +1, 1.2, "ограничение выезда"),
    ("legislation", +1, 1.2, "электронн" + "ые повестки"),
    ("legislation", +1, 1.2, "электронная повестка"),
    ("legislation", +1, 1.3, "реестр повесток"),
    ("legislation", +1, 1.2, "реестр военнообязанных"),
    ("legislation", +1, 1.2, "повышение призывного возраста"),
    ("legislation", +1, 1.2, "отмена отсрочек"),
    ("legislation", +1, 1.2, "отмена отсрочки"),
    ("legislation", +1, 0.8, "мобилизационн"),
    ("legislation", +1, 2.0, "mobilization decree"),
    ("legislation", +1, 2.0, "general mobilization"),
    ("legislation", +1, 1.8, "second wave of mobilization"),
    ("legislation", +1, 1.3, "martial law"),
    ("legislation", +1, 1.2, "exit ban"),
    ("legislation", +1, 1.0, "border closure"),
    ("legislation", +1, 1.0, "electronic summons"),
    ("legislation", +1, 1.0, "draft law"),

    # --- mobilization_prep (подготовка, облавы, повестки) ------------------ #
    ("mobilization_prep", +1, 1.0, "повестк"),
    ("mobilization_prep", +1, 0.9, "военкомат"),
    ("mobilization_prep", +1, 1.6, "облав"),
    ("mobilization_prep", +1, 1.8, "бусификац"),
    ("mobilization_prep", +1, 1.5, "ловят на улиц"),
    ("mobilization_prep", +1, 1.3, "вручение повесток"),
    ("mobilization_prep", +1, 1.3, "раздают повестки"),
    ("mobilization_prep", +1, 1.3, "раздача повесток"),
    ("mobilization_prep", +1, 1.0, "именные повестки"),
    ("mobilization_prep", +1, 1.2, "сборный пункт"),
    ("mobilization_prep", +1, 0.8, "уклонист"),
    ("mobilization_prep", +1, 1.3, "розыск военнообязанных"),
    ("mobilization_prep", +1, 1.2, "квоты на призыв"),
    ("mobilization_prep", +1, 1.5, "мобилизационный план"),
    ("mobilization_prep", +1, 1.4, "силой забира"),
    ("mobilization_prep", +1, 1.0, "принудительн"),
    ("mobilization_prep", +1, 0.7, "вербовк"),
    ("mobilization_prep", +1, 1.5, "forced mobilization"),
    ("mobilization_prep", +1, 1.4, "press-gang"),
    ("mobilization_prep", +1, 1.5, "conscription raid"),
    ("mobilization_prep", +1, 1.0, "draft notice"),
    ("mobilization_prep", +1, 0.8, "recruitment drive"),

    # --- official_signal (заявления / опровержения) ------------------------ #
    ("official_signal", +1, 2.0, "объявил мобилизац"),
    ("official_signal", +1, 1.6, "готовится мобилизац"),
    ("official_signal", +1, 1.2, "не исключил мобилизац"),
    ("official_signal", +1, 1.2, "признаки мобилизац"),
    ("official_signal", +1, 2.0, "announced mobilization"),
    ("official_signal", +1, 1.5, "preparing mobilization"),
    ("official_signal", -1, 2.0, "мобилизации не будет"),
    ("official_signal", -1, 1.8, "никакой мобилизац"),
    ("official_signal", -1, 1.8, "опроверг мобилизац"),
    ("official_signal", -1, 1.8, "опровергли мобилизац"),
    ("official_signal", -1, 1.6, "не планируется мобилизац"),
    ("official_signal", -1, 1.6, "не планируют мобилизац"),
    ("official_signal", -1, 1.6, "нет планов по мобилизац"),
    ("official_signal", -1, 1.5, "обойдемся без мобилизац"),
    ("official_signal", -1, 1.3, "достаточно добровольцев"),
    ("official_signal", -1, 1.2, "хватает добровольцев"),
    ("official_signal", -1, 1.8, "ruled out mobilization"),
    ("official_signal", -1, 1.6, "no plans for mobilization"),
    ("official_signal", -1, 1.6, "denied mobilization"),
    ("official_signal", -1, 1.0, "enough volunteers"),

    # --- frontline_text (фронт: потери/нехватка) --------------------------- #
    ("frontline_text", +1, 1.2, "большие потери"),
    ("frontline_text", +1, 1.4, "огромные потери"),
    ("frontline_text", +1, 1.3, "тяжелые потери"),
    ("frontline_text", +1, 1.6, "нехватка личного состава"),
    ("frontline_text", +1, 1.6, "дефицит личного состава"),
    ("frontline_text", +1, 1.5, "нехватка солдат"),
    ("frontline_text", +1, 1.2, "мясные штурмы"),
    ("frontline_text", +1, 1.2, "мясной штурм"),
    ("frontline_text", +1, 1.0, "наступление застопорил"),
    ("frontline_text", +1, 0.9, "контрнаступление"),
    ("frontline_text", +1, 1.0, "прорыв обороны"),
    ("frontline_text", +1, 1.0, "отступление росси"),
    ("frontline_text", +1, 0.7, "окружение"),
    ("frontline_text", +1, 1.2, "manpower shortage"),
    ("frontline_text", +1, 1.5, "personnel shortage"),
    ("frontline_text", +1, 1.2, "heavy losses"),
    ("frontline_text", +1, 1.2, "high casualties"),
    ("frontline_text", +1, 1.0, "stalled offensive"),

    # --- society_economy (общество/экономика) ------------------------------ #
    ("society_economy", +1, 0.9, "повышение выплат"),
    ("society_economy", +1, 0.8, "единовременная выплата"),
    ("society_economy", +1, 0.8, "выплаты контрактникам"),
    ("society_economy", +1, 1.3, "очереди в военкоматы"),
    ("society_economy", +1, 1.2, "очереди на границе"),
    ("society_economy", +1, 1.2, "скупают билеты"),
    ("society_economy", +1, 1.0, "бронируют билеты"),
    ("society_economy", +1, 1.0, "массовый отъезд"),
    ("society_economy", +1, 1.0, "бегут из страны"),
    ("society_economy", +1, 0.8, "паника"),
    ("society_economy", +1, 0.7, "ажиотаж"),
    ("society_economy", +1, 1.2, "fleeing the draft"),
    ("society_economy", +1, 1.0, "border queues"),
]

# Подсказки релевантности: новость учитывается в скоринге, только если в ней
# есть тематические маркеры (иначе это просто фоновая новость).
RELEVANCE_HINTS = [
    "мобилизац", "mobiliz", "повестк", "военкомат", "призыв", "conscript",
    "облав", "бусификац", "военнообязан", "доброволь", "контрактник",
    "фронт", "наступлен", "оборон", "deepstate", "war ", "война", "draft",
    "военное положение", "martial law",
]
