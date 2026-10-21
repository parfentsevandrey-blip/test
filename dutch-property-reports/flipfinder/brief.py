"""Шаг 3: отчёт по одному объекту — 5 страниц о сделке и фотографии.

    python3 -I flipfinder/brief.py RUN_DIR [МЕСТО_В_СПИСКЕ] [--publish] [--url=часть_ссылки]

Берёт объект из RUN_DIR/shortlist.json (или candidates.json, если порог не прошёл никто),
собирает HTML по шаблону templates/brief.html и печатает PDF через Chromium: в папку отчёта,
а с --publish — в output/.
Подписи к фотографиям — RUN_DIR/brief-<slug>/captions.json: {"<url кадра>": "подпись"}.
"""
from __future__ import annotations

import html as H
import json
import math
import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.append("/root/.local/lib/python3.11/site-packages")
import jinja2  # noqa: E402
from markupsafe import Markup  # noqa: E402
from PIL import Image  # noqa: E402

from flipfinder import config as C  # noqa: E402
from flipfinder import fetch, locate  # noqa: E402
from flipfinder.model import Deal, capex, evaluate, run  # noqa: E402
from reportgen import maps, media  # noqa: E402
from reportgen import selection as S  # noqa: E402

HERE = Path(__file__).resolve().parent
FONTS = C.ROOT / "assets" / "fonts"
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
POIS = C.ROOT / "data" / "flip" / "pois.json"
NEAR_MIN = 35                  # компании и площадки дальше 35 минут в отчёт не идут
# узлы логистики — для объектов, у которых текст о месте пишет аналитик (why.md): три ближайших по дороге
# крупные города: ближайший попадает в кадр карты на стр. 3 (центр — по ратуше или главной площади)
BIG_CITIES = [("Амстердам", (52.3731, 4.8922)), ("Роттердам", (51.9225, 4.4792)), ("Гаага", (52.0705, 4.3007)),
              ("Утрехт", (52.0907, 5.1214)), ("Эйндховен", (51.4416, 5.4697)), ("Неймеген", (51.8426, 5.8546)),
              ("Арнем", (51.9851, 5.8987)), ("Харлем", (52.3812, 4.6369)), ("Алмере", (52.3727, 5.2213)),
              ("Тилбург", (51.5555, 5.0913)), ("Бреда", (51.5890, 4.7760)), ("Хертогенбос", (51.6898, 5.3037)),
              ("Амерсфорт", (52.1561, 5.3878)), ("Зволле", (52.5168, 6.0830)), ("Апелдорн", (52.2112, 5.9699)),
              ("Гронинген", (53.2194, 6.5665)), ("Энсхеде", (52.2215, 6.8937)), ("Маастрихт", (50.8514, 5.6910)),
              ("Алкмар", (52.6324, 4.7534))]
HUBS = [("Схипхол", (52.3105, 4.7683)), ("Порт Роттердама, Ваалхавен", (51.8869, 4.4361)), ("Роттердам, центр", (51.9225, 4.4792)),
        ("Утрехт, центр", (52.0907, 5.1214)), ("Амстердам, центр", (52.3731, 4.8922)), ("Гаага, центр", (52.0705, 4.3007)),
        ("Эйндховен, центр", (51.4416, 5.4697)), ("Неймеген, центр", (51.8426, 5.8546))]

CITY_RU = {"rotterdam": "Роттердам", "amsterdam": "Амстердам", "almere": "Алмере", "diemen": "Димен",
           "hoofddorp": "Хофддорп", "nieuw-vennep": "Ниу-Веннеп", "schiphol-rijk": "Схипхол-Рейк",
           "badhoevedorp": "Бадхуведорп", "lijnden": "Лейнден", "aalsmeer": "Алсмер", "rijsenhout": "Рейсенхаут",
           "eindhoven": "Эйндховен", "veldhoven": "Велдховен", "best": "Бест", "son-en-breugel": "Сон-эн-Брёгел",
           "nijmegen": "Неймеген", "wijchen": "Вейхен", "hoorn-nh": "Хорн", "middenmeer": "Мидденмер",
           "wieringerwerf": "Вирингерверф", "schiedam": "Схидам", "vlaardingen": "Влардинген",
           "capelle-aan-den-ijssel": "Капелле-ан-ден-Эйссел", "ridderkerk": "Ридеркерк", "barendrecht": "Барендрехт",
           "rhoon": "Рон", "poortugaal": "Портюгал", "spijkenisse": "Спейкениссе", "zwijndrecht": "Звейндрехт",
           "maassluis": "Масслёйс", "krimpen-aan-den-ijssel": "Кримпен-ан-ден-Эйссел", "bleiswijk": "Блейсвейк",
           "berkel-en-rodenrijs": "Беркел-эн-Роденрейс", "bergschenhoek": "Бергсхенхук",
           "nieuwerkerk-aan-den-ijssel": "Ниуверкерк-ан-ден-Эйссел", "hendrik-ido-ambacht": "Хендрик-Идо-Амбахт",
           "amstelveen": "Амстелвен", "uithoorn": "Ойтхорн", "haarlem": "Харлем", "zwanenburg": "Званенбург",
           "halfweg": "Халфвег", "kudelstaart": "Кюделстарт", "de-kwakel": "Де-Квакел", "cruquius": "Крукиус",
           "vijfhuizen": "Вейфхёйзен", "hillegom": "Хиллегом", "lisse": "Лиссе", "zaandam": "Зандам",
           "weesp": "Весп", "duivendrecht": "Дёйвендрехт", "purmerend": "Пюрмеренд", "lelystad": "Лелистад",
           "landsmeer": "Ландсмер", "wormerveer": "Вормервер", "krommenie": "Кромменье", "oostzaan": "Остзан",
           "muiden": "Мёйден", "abcoude": "Абкауде", "helmond": "Хелмонд", "nuenen": "Нюэнен", "geldrop": "Гелдроп",
           "waalre": "Валре", "oirschot": "Ойршот", "valkenswaard": "Валкенсвард", "eersel": "Эрсел",
           "arnhem": "Арнем", "elst-ge": "Элст", "beuningen-ge": "Бёнинген", "cuijk": "Кёйк", "malden": "Малден",
           "groesbeek": "Хрусбек", "bemmel": "Беммел", "druten": "Друтен", "heerhugowaard": "Херхюговард",
           "schagen": "Схаген", "enkhuizen": "Энкхёйзен", "zwaag": "Зваг", "medemblik": "Медемблик",
           "wognum": "Вогнюм", "alkmaar": "Алкмар", "obdam": "Обдам", "zoetermeer": "Зутермер", "nieuwegein": "Ниувегейн",
           "utrecht": "Утрехт", "houten": "Хаутен", "ijsselstein": "Эйсселстейн", "vianen": "Вианен", "dordrecht": "Дордрехт",
           "papendrecht": "Папендрехт", "alblasserdam": "Алблассердам", "sliedrecht": "Слидрехт",
           "oud-beijerland": "Ауд-Бейерланд", "delft": "Делфт", "gouda": "Гауда", "waddinxveen": "Ваддинксвен",
           "pijnacker": "Пейнакер", "naaldwijk": "Нальдвейк", "assendelft": "Ассенделфт", "wormer": "Вормер",
           "huissen": "Хёйссен", "duiven": "Дёйвен", "westervoort": "Вестерфорт", "zevenaar": "Зевенар"}
FEATURES = {"lichtstraten": "световые фонари", "lichtstraat": "световые фонари", "elektrische overheaddeur": "электрические ворота",
            "overheaddeuren": "ворота", "overheaddeur": "ворота", "betonvloer": "бетонный пол", "heater": "обогреватель в зале",
            "toilet": "туалет", "airconditioning": "кондиционер", "mechanische ventilatie": "вентиляция",
            "te openen ramen": "открывающиеся окна", "pantry": "кухня", "krachtstroom": "силовая сеть",
            "kabelgoten": "кабельные лотки", "systeemplafond": "системный потолок", "sprinkler": "спринклеры",
            "goederenlift": "грузовой лифт", "alarminstallatie": "сигнализация", "topkoeling": "охлаждение",
            "zonnepanelen": "солнечные панели", "laadkuil": "погрузочная платформа", "loading dock": "погрузочные доки",
            "cv-ketel": "отопление", "verwarming": "отопление", "camerabewaking": "видеонаблюдение"}
FLOORS = re.compile(r"(begane grond|1e verdieping|eerste verdieping|2e verdieping|tweede verdieping|entresol|verdieping)\W{0,6}"
                    r"(?:ca\.?|circa)?\s*(\d{2,4})\s*m", re.I)
FLOOR_RU = {"begane grond": "первый этаж", "1e verdieping": "второй этаж", "eerste verdieping": "второй этаж",
            "2e verdieping": "третий этаж", "tweede verdieping": "третий этаж", "entresol": "антресоль",
            "verdieping": "второй этаж"}
MONTHS_NL = {"januari": "января", "februari": "февраля", "maart": "марта", "april": "апреля", "mei": "мая",
             "juni": "июня", "juli": "июля", "augustus": "августа", "september": "сентября", "oktober": "октября",
             "november": "ноября", "december": "декабря"}


# ------------------------------------------------------------------ формат
def eur(v: float, step: int = 100) -> str:
    v = round(v / step) * step
    return ("−" if v < 0 else "") + "€ " + f"{abs(v):,.0f}".replace(",", ".")


def signed(v: float) -> str:
    return ("+" if v >= 0 else "") + eur(v)


def pct(v: float, digits: int = 1) -> str:
    return f"{v * 100:.{digits}f} %".replace(".", ",")


def num(v: float) -> str:
    return f"{v:,.0f}".replace(",", ".")


def km(v: float) -> str:
    return f"{v:.1f}".replace(".", ",") + " км"


def md(text: str) -> str:
    """**жирный** → <b>; остальное экранируется."""
    return re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", H.escape(text or ""))


def plural(n: int, one: str, few: str, many: str) -> str:
    form = one if n % 10 == 1 and n % 100 != 11 else (few if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14) else many)
    return f"{n} {form}"


def months(n: int) -> str:
    return plural(n, "месяц", "месяца", "месяцев")


def date_ru(text: str | None) -> str | None:
    """«16 oktober 2026» → «16 октября 2026»; «6+ maanden» → «больше 6 месяцев»."""
    if not text:
        return None
    m = re.match(r"(\d+)(\+?) maanden", text)
    if m:
        return ("больше " if m.group(2) else "") + months(int(m.group(1)))
    for nl, ru in MONTHS_NL.items():
        text = text.replace(nl, ru)
    return text


# ------------------------------------------------------------------ фото
def is_plan(path: Path) -> bool:
    """Планировка: почти весь кадр белый и бесцветный."""
    raw = Image.open(path).convert("RGB").resize((120, 80)).tobytes()
    px = [raw[i:i + 3] for i in range(0, len(raw), 3)]
    white = sum(1 for r, g, b in px if min(r, g, b) > 228 and max(r, g, b) - min(r, g, b) < 12)
    return white / len(px) > 0.55


def photos(rec: dict, cache: Path) -> tuple[list[dict], list[dict]]:
    """Кадры объявления: планировки отдельно; в отчёт идут копии не больше 1600 пикселей по длинной стороне."""
    shots, plans = [], []
    small = cache.parent / "img-a4"
    small.mkdir(parents=True, exist_ok=True)
    for url in rec.get("photos") or []:
        try:
            path = media.fetch(url, cache)
        except Exception:                                      # noqa: BLE001 — кадр не загрузился
            continue
        out = small / (path.stem + "-q76.jpg")
        if not out.exists():
            im = Image.open(path).convert("RGB")
            im.thumbnail((1600, 1600))
            im.save(out, "JPEG", quality=76, optimize=True)
        (plans if is_plan(path) else shots).append({"url": url, "src": out.resolve().as_uri()})
    return shots, plans


def photo_pages(shots: list[dict]) -> list[dict]:
    """Кадры по страницам без пустых хвостов: 14 → 6 + 4 + 4; на странице с меньшим числом кадров они крупнее."""
    pages = -(-len(shots) // 6)
    counts = [6] * pages
    i = pages - 1
    while sum(counts) > len(shots):
        counts[i] -= 2 if sum(counts) - len(shots) >= 2 else 1
        i = i - 1 if i > 0 else pages - 1
    out, start = [], 0
    for n in counts:
        out.append({"cls": f"g{n}", "shots": shots[start:start + n]})
        start += n
    return out


# ------------------------------------------------------------------ графики: SVG, цвета из токенов страницы
def discount_svg(cut: float) -> str:
    """Скидка базового сценария на шкале от 0 до 20 % (или дальше, если скидка больше).
    Жёлтая полоса под шкалой — скидка, на которую продавцы обычно соглашаются."""
    top = max(0.20, math.ceil((cut + 0.03) * 20) / 20)
    w, h, y = 600, 50, 18
    x = lambda v: 10 + (w - 40) * max(0.0, v) / top          # noqa: E731
    ticks = "".join(f'<text x="{x(t / 100):.1f}" y="{y + 30}" font-size="10" fill="var(--muted)" text-anchor="middle">{t} %</text>'
                    for t in range(0, int(round(top * 100)) + 1, 5))
    a, b = x(C.OFFER_DISCOUNT_MIN), x(C.OFFER_DISCOUNT_MAX)
    return (f'<svg viewBox="0 0 {w} {h}">'
            f'<rect x="{a:.1f}" y="{y - 4}" width="{b - a:.1f}" height="20" fill="var(--signal)"/>'
            f'<rect x="10" y="{y}" width="{w - 40}" height="12" fill="var(--concrete)"/>'
            f'<rect x="10" y="{y}" width="{max(0.0, x(cut) - 10):.1f}" height="12" fill="var(--ink)"/>'
            f'<text x="{max(x(cut), 60):.1f}" y="{y - 6}" font-size="12" font-weight="600" fill="var(--ink)" '
            f'text-anchor="end">скидка {pct(cut, 0)}</text>{ticks}</svg>')


def waterfall(rows: list[tuple[str, float]]) -> dict:
    """«Мост» от денег к прибыли: каждая полоса начинается там, где кончилась предыдущая; последняя — итог от нуля.
    Строки — HTML, чтобы график растягивался по высоте страницы, а подписи не искажались."""
    lo, hi, cum, spans = 0.0, 0.0, 0.0, []
    for i, (label, v) in enumerate(rows):
        last = i == len(rows) - 1
        a, b = (0.0, v) if last else (cum, cum + v)
        spans.append((label, v, min(a, b), max(a, b), last))
        if not last:
            cum += v
        lo, hi = min(lo, a, b), max(hi, a, b)
    x = lambda v: 100 * (v - lo) / (hi - lo)               # noqa: E731
    return {"zero": f"{x(0):.2f}", "rows": [{"label": label, "left": f"{x(a):.2f}", "width": f"{max(0.6, x(b) - x(a)):.2f}",
             "cls": "gain" if v >= 0 else "loss", "last": last,
             "value": ("+" if v >= 0 else "−") + f"{abs(v) / 1000:,.0f}".replace(",", ".") + "\u00a0тыс."}
            for label, v, a, b, last in spans]}


def cash_chart(rows: list[dict]) -> dict:
    """Сколько денег вложено в сделку на конец каждого месяца: ступенчатая линия с подписями на самом графике.
    После продажи вложенное возвращается, ниже нуля — прибыль. Координаты — проценты блока графика."""
    inv = [-r["cum"] for r in rows]
    n = len(inv)
    peak, final = max(inv), inv[-1]
    peak_m = inv.index(peak)
    top, bottom = peak * 1.8, min(final * 1.9, -peak * 0.16)
    X = lambda m: 100 * m / n                                  # noqa: E731
    Y = lambda v: 100 * (top - v) / (top - bottom)             # noqa: E731
    line = " ".join(f"{X(m):.2f},{Y(v):.2f} {X(m + 1):.2f},{Y(v):.2f}" for m, v in enumerate(inv))
    pos = [m for m, v in enumerate(inv) if v > 0]
    area = (f"{X(0):.2f},{Y(0):.2f} " + " ".join(f"{X(m):.2f},{Y(inv[m]):.2f} {X(m + 1):.2f},{Y(inv[m]):.2f}" for m in pos)
            + f" {X(pos[-1] + 1):.2f},{Y(0):.2f}") if pos else ""
    labels = [{"style": f"left: 0; top: {Y(inv[0]):.2f}%", "cls": "up", "title": "В день покупки", "value": eur(inv[0])}]
    if peak_m > 0 and peak > inv[0] * 1.01:
        labels.append({"style": f"left: {X(peak_m + 0.5):.2f}%; top: {Y(peak):.2f}%", "cls": "up mid",
                       "title": f"Максимум, месяц {peak_m}", "value": eur(peak)})
    if final < 0:
        labels.append({"style": f"right: {100 - X(n - 1) + 1.5:.2f}%; top: {Y(0):.2f}%", "cls": "down end",
                       "title": "Прибыль после продажи", "value": "+" + eur(-final)})
    return {"line": line, "area": area, "zero": f"{Y(0):.2f}", "labels": labels,
            "profit": {"x": f"{X(n - 1):.2f}", "w": f"{X(1):.2f}", "y": f"{Y(0):.2f}", "h": f"{Y(final) - Y(0):.2f}"}
            if final < 0 else None,
            "ticks": [{"x": f"{X(m + 0.5):.2f}", "m": m} for m in range(0, n, 3)]}


def bar_cell(v: float, top: float) -> str:
    half = 50 * min(1.0, abs(v) / top) if top else 0
    color = "var(--gain)" if v >= 0 else "var(--loss)"
    x = 50 if v >= 0 else 50 - half
    return (f'<svg viewBox="0 0 100 10" preserveAspectRatio="none" style="width:100%;height:3.4mm;display:block">'
            f'<line x1="50" y1="0" x2="50" y2="10" stroke="var(--axis)" stroke-width="0.6"/>'
            f'<rect x="{x:.2f}" y="1.5" width="{max(half, 0.6):.2f}" height="7" fill="{color}"/></svg>')


# ------------------------------------------------------------------ факты из объявления
def features_ru(rec: dict) -> list[str]:
    feat = fetch.F.features(fetch.get(rec["url"], max_age_h=1e6) or "")
    m = re.search(r"Voorzieningen\n(.+)", feat)
    raw = (m.group(1) if m else (rec.get("desc") or "")).lower()
    found = []
    for nl, ru in FEATURES.items():
        if nl in raw and ru not in found and not (ru == "ворота" and "электрические ворота" in found):
            found.append(ru)
    return found[:9]


BELEGGING_ADDRESS = re.compile(r"te koop: (.+?) (\d{4} ?[A-Z]{2}) (.+?) \[")


def investor_comps(listings: list[dict], here: tuple[float, float], limit: int = 2) -> list[dict]:
    """Сданные склады и мастерские на продаже не дальше 15 км, где в объявлении названа аренда; ближайшие первыми."""
    out = []
    for r in listings:
        if r.get("kind") != "belegging" or not r.get("price") or r.get("function") not in ("Belegging", "Bedrijfshal"):
            continue
        d = re.sub(r"\s+", " ", r.get("desc") or "").lower()
        if not re.search(r"bedrijfsruimte|bedrijfshal|bedrijfspand|bedrijfsunit|loods|magazijn|werkplaats", d) \
                or re.search(r"\b(?:woning|woningen|appartement|appartementen|winkelruimte|winkelpand)\b", d):
            continue
        m = re.search(r"(?:huursom|huuropbrengst|huurinkomsten|jaarhuur|huurstroom|huurprijs)[^€]{0,90}€\s*([\d.]+)", d)
        a = BELEGGING_ADDRESS.search(r.get("title") or "")
        if not m or not a:
            continue
        rent = int(m.group(1).replace(".", "")) * (12 if "per maand" in d[m.end():m.end() + 25] else 1)
        y = rent / r["price"]
        g = locate.geocode(f"{a.group(1)}, {a.group(2)} {a.group(3)}")
        if 0.045 <= y <= 0.12 and g and locate.km(here, g[:2]) <= 15:
            out.append({"name": f"{a.group(1)}, {a.group(3)}", "price": r["price"], "rent": rent, "yield": y,
                        "km": locate.km(here, g[:2])})
    return sorted(out, key=lambda c: c["km"])[:limit]


def neighbours(pick: dict, limit: int = 4) -> list[dict]:
    """Ради чего мы выбрали район: площадки и компании рядом, с дорогой."""
    lat, lon = pick["lat"], pick["lon"]
    if pick["place"].get("whole"):
        pois = json.loads(POIS.read_text(encoding="utf-8")).get(pick["territory"], []) if POIS.exists() else []
        rows = []
        for p in pois:
            r = locate.drive((lat, lon), tuple(p["at"]))
            if r and r[1] <= NEAR_MIN:
                rows.append({"label": p["label"], "km": r[0], "min": round(r[1]), "about": p["about"]})
        return sorted(rows, key=lambda r: r["min"])[:limit]
    # в таблицу дорог — только то, что объяснено в описании района: каждая названная компания с пояснением
    body = pick["place"].get("body") or ""
    rows = [{"label": a["label"], "km": a["km"], "min": a["min"], "about": about(a["label"], pick["territory"])}
            for a in locate.anchors(lat, lon, pick["territory"], 8) if a["km"] is not None and a["min"] <= NEAR_MIN]
    return [r for r in rows if r["about"] or f"**{r['label']}**" in body][:limit]


def hubs(pick: dict, limit: int = 3) -> list[dict]:
    rows = []
    for label, at in HUBS:
        r = locate.drive((pick["lat"], pick["lon"]), at)
        if r:
            rows.append({"label": label, "km": r[0], "min": round(r[1]), "about": ""})
    return sorted(rows, key=lambda r: r["min"])[:limit]


def plot_area(rec: dict, desc: str) -> float | None:
    """Участок по кадастру из описания, если он там есть: в карточке funda площадь бывает с опечаткой."""
    m = re.search(r"(?:perceel(?:grootte)?:?\s+(?:van\s+)?|groot\s*\(gezamenlijk\):?\s*)(?:ca\.?\s*|circa\s*)?"
                  r"(\d{1,3}(?:\.\d{3})+|\d+)\s*m(?:²|2)", desc, re.I)
    return fetch.num(m.group(1)) if m else rec.get("plot")


def about(label: str, territory: str) -> str:
    """Пояснение к компании или площадке: предложение из описания района, где она названа именно так."""
    for e in S.load()[2]["sheets"].get(territory, {}).get("entries", []):
        for sentence in re.split(r"(?<=[.!?])\s+", e.get("body") or ""):
            if f"**{label}**" in sentence:
                sentence = re.sub(r"^(?:Здесь же|Там же|Рядом)\s+", "", sentence)
                return sentence[0].upper() + sentence[1:]
    return ""


def why_here(pick: dict, near: list[dict]) -> list[str]:
    place = pick["place"]
    if place.get("whole"):
        return [n["about"] for n in near[:3] if n["about"]]
    body = place.get("body") or ""
    lead = (f"Здание расположено в районе **{place['short']}**." if place["ring"] == "zone" else
            f"От здания до промзоны **{place['short']}** — {place.get('drive_min')}\u00a0минут на машине.")
    out = [f"{lead} {body}"]
    for n in near:
        if n["about"] and n["about"] not in out and f"**{n['label']}**" not in body:
            out.append(n["about"])
    return out


def checks(pick: dict, rec: dict) -> list[str]:
    info, out = pick["info"], []
    oc = info["owner"]
    if oc["canon"]:
        out.append(f"**Земля в аренде:** кому платится €\u00a0{num(oc['canon'])} в год, до какого года право и когда индексация.")
    if info.get("year") and info["year"] < 1994:
        out.append(f"**Асбест и кровля:** обследование здания {info['year']} года.")
    label = (rec.get("label") or "").strip()
    if not label or label[0] in "DEFG" or "Niet" in label:
        out.append("**Энергетический класс:** что нужно, чтобы офисная часть получила класс C.")
    out.append(f"**Спрос:** брокер по аренде подтверждает ставку €\u00a0{num(pick['deal']['rent'] / pick['area'])} за м² "
               f"для {num(pick['area'])} м².")
    out.append("**План застройки:** разрешён ли вид деятельности будущего арендатора.")
    since = date_ru(rec.get("since"))
    if since:
        prefix = "с " if re.match(r"\d+ [а-я]", since) else ""
        out.append(f"**Продавец:** объект на продаже {prefix}{since} — насколько он готов уступить.")
    out.append("**Налоговый консультант:** аренда с НДС и продажа сданного здания как передача бизнеса.")
    return out[:6]


# ------------------------------------------------------------------ сборка
def context(pick: dict, rec: dict, cands: list[dict], listings: list[dict], work: Path) -> dict:
    ev = evaluate(Deal(**pick["deal"]))
    d = ev["deal"]                           # доходность инвестора может быть пересчитана (см. model.evaluate)
    b, ask, area = ev["base"], d.ask, pick["area"]
    offer, rent, need = b["price"], d.rent, ev["breakeven_discount"]
    city = CITY_RU.get(pick["slug"], pick["address"]["city"])
    street = pick["address"]["street"]
    year, oc, road = pick["info"].get("year"), pick["info"]["owner"], pick["road"]
    shots, plans = photos(rec, work / "img")
    caps = json.loads((work / "captions.json").read_text(encoding="utf-8")) if (work / "captions.json").exists() else {}
    if caps:
        # подписанные кадры — отобранные: только они и в порядке подписей (первый — обложка)
        by_url = {s["url"]: s for s in shots}
        shots = [by_url[u] for u in caps if u in by_url]
    for i, s in enumerate(shots, 1):
        s["cap"] = caps.get(s["url"], f"Кадр {i}")
    for p in plans:
        p["cap"] = caps.get(p["url"], "Планировка")
    why_md = work / "why.md"                 # текст о месте от аналитика — для объектов вне наших районов
    near = hubs(pick) if why_md.exists() else neighbours(pick)
    first_rent = d.lease_month + d.rent_free
    rent_months = max(0, d.horizon - first_rent + 1)
    since = date_ru(rec.get("since"))

    # стр. 1
    cut = pct(1 - offer / ask, 0)
    usual = f"{C.OFFER_DISCOUNT_MIN * 100:.0f}–{pct(C.OFFER_DISCOUNT_MAX, 0)}"
    verdict = (f"План сделки: покупка свободного склада за **{eur(offer)}** — на {cut} дешевле цены продавца, косметический "
               f"ремонт, сдача в аренду на 5 лет и через 12 месяцев продажа вместе с арендатором инвестору "
               f"за **{eur(b['sale'])}**.")
    tag = {"label": "Чистая прибыль за 12 месяцев", "value": eur(b["net"]),
           "hint": f"после налога на прибыль; стоимость владения за год — {eur(b['costs'])}"}
    kpis = [{"label": "Цена продавца", "value": eur(ask), "hint": f"{eur(ask / area, 1)} за м²"},
            {"label": "Предложение продавцу", "value": eur(offer), "hint": f"на {cut} меньше"},
            {"label": "Продажа инвестору", "value": eur(b["sale"]), "hint": "через 12 месяцев, с арендатором"}]
    meter = {"title": (f"Базовый сценарий: покупка со скидкой **{cut}** от цены продавца — {eur(offer)} "
                       f"вместо {eur(ask)}. Жёлтым — скидка, на которую продавцы обычно соглашаются: {usual}."),
             "svg": discount_svg(1 - offer / ask)}
    med, n_comps = pick["info"]["rent"].get("comps_m2"), pick["info"]["rent"]["n"]
    by_owner = pick["info"]["rent"].get("basis") == "ставка продавца"
    vac = pick["info"].get("vacant") or {}
    comps_txt = plural(n_comps, "предложения", "предложений", "предложений")
    rent_reason = (f"**Аренда по ставке владельца.** €\u00a0{num(rent / area)} за м² в год — столько владелец просит за аренду "
                   f"здания" + (f"; средняя ставка {comps_txt} рядом — €\u00a0{num(med)}." if med else ".")
                   if by_owner else
                   f"**Аренда по рынку.** €\u00a0{num(rent / area)} за м² в год"
                   + (f" — средняя ставка {comps_txt} рядом" if med else "")
                   + f"; первые {months(d.rent_free)} арендатор не платит.")
    first = (f"**Цена ниже рынка.** {eur(ask / area, 1)} за м² по цене продавца; свободные склады такой площади рядом "
             f"выставлены в среднем по {eur(vac['m2'], 1)}." if vac.get("m2") and vac["m2"] > ask / area else
             "**Здание свободно.** Ремонт и поиск арендатора можно начать сразу после покупки.")
    reasons = [
        first, rent_reason,
        f"**Магистраль рядом.** До {road['ref']} — {km(road['km'])}, {road['min']}\u00a0мин"
        + (f"; {near[0]['label']} — {near[0]['min']}\u00a0мин." if near else "."),
    ]

    # стр. 2
    hall, office = rec.get("hall"), rec.get("office")
    desc = re.sub(r"\s+", " ", rec.get("desc") or "")
    floors = [(FLOOR_RU[m.group(1).lower()], int(m.group(2))) for m in FLOORS.finditer(desc)]
    # этажи — только если в карточке нет разбивки на зал и офисы: она нагляднее
    if (not office and len(floors) >= 2 and len({n for n, _ in floors}) == len(floors)
            and abs(sum(a for _, a in floors) - area) <= 0.1 * area):
        parts = ", ".join(f"{name} {num(a)} м²" for name, a in floors)
    else:
        parts = "; ".join(x for x in [f"зал {num(hall)} м²" if hall and (office or hall != area) else "",
                                      f"офисы {num(office)} м²" if office else ""] if x)
    height = re.sub(r"\s*m$", "\u00a0м", rec.get("height") or "") or "не указана"
    orig_m = re.search(r"€\s*([\d.]+)", rec.get("orig_price") or "")
    orig = fetch.num(orig_m.group(1)) if orig_m else None
    facts = [
        [("Площадь", f"{num(area)} м²" + (f": {parts}" if parts else "")
          + (f" (по описанию; {pick['area_note'].split(', ')[0]})" if pick.get("area_note") else "")),
         ("Высота зала", height), ("Год постройки", str(year) if year else "не указан"),
         ("Энергетический класс", label if (label := (rec.get("label") or "").strip()) and "Niet" not in label else "не указан"),
         ("Участок", f"{num(plot)} м²" if (plot := plot_area(rec, desc)) else "не указан")],
        [("Цена продавца", f"{eur(ask)}, налог 10,4\u00a0% за покупателем"), ("Цена за м²", eur(ask / area, 1)),
         ("Арендатор", "нет, здание свободно"), ("Земля", f"в аренде, €\u00a0{num(oc['canon'])} в год" if oc["canon"] else "в аренде" if oc["lease_end"]
          else "в собственности, без платы" if re.search(r"eigen grond|voll?e? eigendom", desc, re.I) else "не указано"),
         ("На продаже", (f"с {since}" if since and re.match(r"\d+ [а-я]", since) else (since or "не указано"))
          + (f"; цена снижена с {eur(orig)}" if orig and orig > ask else ""))],
    ]
    feats = features_ru(rec)
    blocks = []
    note = work / "note.md"                  # заметка аналитика: то, чего нет в таблицах объявления
    if note.exists():
        blocks.append(("Важно", [p.strip() for p in note.read_text(encoding="utf-8").split("\n\n") if p.strip()]))
    if feats:
        blocks.append(("Оснащение", [(feats[0][0].upper() + feats[0][1:] + (", " + ", ".join(feats[1:]) if len(feats) > 1 else "") + ".")]))
    cat = re.search(r"(?:milieu)?categorie (\d[.,]\d|\d)", desc, re.I)
    if cat:
        use = (f"По плану застройки здесь разрешены предприятия до категории {cat.group(1).replace('.', ',')} "
               "по шкале воздействия на соседей (от 1 до 6): склад, мастерская, производство.")
        if re.search(r"ten hoogste 50\s?%[^.]{0,80}kantoor", desc, re.I):
            use += " Офисы — не больше половины площади."
        blocks.append(("Назначение", [use]))
    if oc["canon"] or oc["lease_end"]:
        land = "Участок в эрфпахте — долгосрочной аренде земли"
        land += f": владелец платит **€\u00a0{num(oc['canon'])} в год**." if oc["canon"] else "."
        if oc["lease_end"]:
            land += f" Право на землю действует до {oc['lease_end']} года."
        if oc["bought_off_until"]:
            land += f" Плата внесена вперёд до {oc['bought_off_until']} года."
        blocks.append(("Земля", [land]))
    todo = [f"**Ремонт:** покраска, свет, мелкий ремонт — {eur(b['works'])} без НДС.",
            f"**Арендатор:** ищет брокер за 15\u00a0% годовой аренды; первые {months(d.rent_free)} арендатор не платит.",
            "**Договор аренды:** на 5 лет, плата растёт вместе с инфляцией.",
            "**Договор покупки:** с правом отказаться, если проверка здания найдёт серьёзные дефекты."]
    images = ([{"src": plans[0]["src"], "cls": "plan"}] if plans else []) + \
             [{"src": shots[i]["src"], "cls": ""} for i in (1, 2) if len(shots) > i][: 2 - (1 if plans else 0)]

    # стр. 3
    # в кадре — объект и центр ближайшего крупного города, но не крупнее 13-го масштаба
    city_name, city_at = min(BIG_CITIES, key=lambda c: locate.km((pick["lat"], pick["lon"]), c[1]))
    zoom = min(13, maps.fit_zoom([(pick["lat"], pick["lon"]), city_at], 1400, 760, padding=0.7))
    map_png = maps.render(pick["lat"], pick["lon"], work / f"map-z{zoom}-{city_at[0]:.3f}.png", city=city_at, zoom=zoom,
                          width=1400, height=760, city_label=city_name, font=str(FONTS / "GolosText-600.ttf"))
    map_jpg = work / "map.jpg"
    Image.open(map_png).convert("RGB").save(map_jpg, "JPEG", quality=85, optimize=True)
    roads = [{"shield": road["ref"].replace(" ", ""), "label": "въезд на магистраль", "value": f"{km(road['km'])}, {road['min']}\u00a0мин"}]
    roads += [{"label": n["label"], "value": f"{km(n['km'])}, {n['min']}\u00a0мин"} for n in near]
    comps = sorted(pick["info"]["comps"], key=lambda c: abs(c["area"] - area))[:6]
    comp_rows = [{"name": c["title"], "area": num(c["area"]), "rate": c["rent_m2"]} for c in comps]
    comp_rows.append({"name": f"{street}, оценка", "area": num(area), "rate": rent / area, "mine": True})
    comp_rows.sort(key=lambda c: -c["rate"])
    for c in comp_rows:
        c["rate"] = f"€\u00a0{num(c['rate'])}"
    mezz = pick["info"]["rent"].get("mezzanine")
    rent_box = {"month": eur(rent / 12),
                "text": (f"Это **{eur(rent)} в год**, €\u00a0{num(rent / area)} за м²"
                         + (f": столько владелец просит за аренду этого здания. Средняя ставка {comps_txt} рядом — "
                            f"€\u00a0{num(med)}." if by_owner and med else
                            f": столько владелец просит за аренду этого здания." if by_owner else
                            f" — средняя ставка {comps_txt} рядом." if med else ".")
                         + f" Первые {months(d.rent_free)} арендатор не платит — это уступка за новый договор."),
                "note": ("В таблице — ближайшие предложения аренды на funda in business площадью от половины до двойной площади этого здания."
                         + (" Антресоль учтена как 30\u00a0% площади." if mezz else ""))}

    # стр. 4
    tax = b["buy"]["Налог на передачу 10,4 %"]
    upkeep = -b["tot"]["upkeep"]
    letting = -b["tot"]["letting"]
    steps = [{"what": "Покупка", "when": "месяц 0", "more": f"{eur(offer)} и налог 10,4\u00a0%"},
             {"what": "Ремонт", "when": "месяцы 1–2", "more": eur(b["works"])},
             {"what": "Сдача в аренду", "when": f"месяц {d.lease_month}", "more": f"договор на 5 лет, первые {months(d.rent_free)} бесплатно"},
             {"what": "Первая аренда", "when": f"с месяца {first_rent}", "more": f"{eur(rent / 12)} в месяц"},
             {"what": "Продажа", "when": "месяц 12", "more": f"инвестору за {eur(b['sale'])}"}]
    spend = [("Цена здания", eur(offer)), ("Налог при покупке, 10,4\u00a0%", eur(tax)),
             ("Нотариус, проверка здания, консультант", eur(b["buy_total"] - offer - tax)),
             ("Ремонт", eur(b["works"])), ("Поиск арендатора: брокер и реклама", eur(letting)),
             ("Налоги и страховка" + (", плата за землю" if oc["canon"] else "")
              + (", взнос ассоциации владельцев" if oc.get("vve") else "") + " на время владения", eur(upkeep)),
             ("Продажа: брокер и юрист", eur(b["sale_cost"]))]
    get = [(f"Аренда за {months(rent_months)}", eur(b["rent_collected"])), ("Продажа инвестору", eur(b["sale"]))]
    result_lines = [("Прибыль до налога", eur(b["profit"])), ("Налог на прибыль, 19\u00a0%" + (" и 25,8\u00a0%" if b["profit"] > C.VPB[0][0] else ""), eur(-b["vpb"])),
                    ("Чистая прибыль", "")]
    # доход на € 100 — от итоговой стоимости владения за год (все расходы с покупкой), как в таблице расходов
    per100 = f"{b['net'] / b['costs'] * 100:.2f}".replace(".", ",")
    result_hint = f"Итоговая стоимость владения за год — {eur(b['costs'])}. Каждые €\u00a0100 приносят €\u00a0{per100}."
    bridge = waterfall([("Продажа", b["sale"]), (f"Аренда, {rent_months} мес.", b["rent_collected"]),
                               ("Покупка и налог", -b["buy_total"]), ("Ремонт и сдача", -(b["works"] + letting)),
                               ("Содержание", -upkeep), ("Расходы на продажу", -b["sale_cost"]),
                               ("Прибыль до налога", b["profit"])])
    cash = cash_chart(b["rows"])
    cash_note = "В 12-м месяце инвестор платит за здание: вложенное возвращается вместе с прибылью."
    chart_title = f"Доходы {eur(b['income'])}, расходы {eur(b['costs'])}, прибыль до налога {eur(b['profit'])}"
    deduct = d.owner_costs
    eq = (f"({eur(rent)} − {eur(deduct)}) ÷ {pct(d.exit_yield, 2)} = {eur(b['sale'])}" if deduct
          else f"{eur(rent)} ÷ {pct(d.exit_yield, 2)} = {eur(b['sale'])}")
    intro = (f"Инвестор покупает здание ради аренды, как вклад с процентами: аренда за год — {pct(d.exit_yield, 2)} от цены. "
             f"Арендатор этого здания платит {eur(rent)} в год"
             + (f", из них {eur(deduct)} уходит на " + ("плату за землю" if oc["canon"] else "взнос ассоциации владельцев")
                if deduct else "") + ".")
    inv = investor_comps(listings, (pick["lat"], pick["lon"]))
    notes = ["Налог 10,4\u00a0% инвестор платит сверху."]
    if inv:
        notes.append("Сданные склады на продаже рядом и их доходность: " + "; ".join(
            f"{c['name']}, {eur(c['price'])}, {pct(c['yield'], 2)}" for c in inv) + ".")
    if pick["info"]["exit_notes"] and not ev["yield_set"]:
        notes.append("Поправка к доходности: " + ", ".join(pick["info"]["exit_notes"]) + ".")
    # с чем инвестор сравнивает: вклад, ставка ЕЦБ, инфляция
    bench = [("Это здание", d.exit_yield, "плюс рост аренды вместе с инфляцией", "own")] + \
            [(label, v, src, "") for _, v, label, src in C.BENCHMARKS]
    top_rate = max(v for _, v, _, _ in bench)
    compare = [{"label": label, "value": pct(v, 2 if k == "own" else 1), "hint": hint, "cls": k,
                "w": f"{100 * v / top_rate:.1f}"} for label, v, hint, k in bench]
    cpi = next(v for k, v, _, _ in C.BENCHMARKS if k == "cpi")
    dep = next(v for k, v, _, _ in C.BENCHMARKS if k == "deposit")
    compare_note = (f"Аренда в договоре растёт вместе с инфляцией, поэтому доход здания не обесценивается. Вклад даёт "
                    f"{pct(dep, 1)} при инфляции {pct(cpi, 1)}" + (" — деньги на нём почти не растут." if dep - cpi < 0.005 else "."))
    investor = {"title": f"Почему инвестор заплатит {eur(b['sale'])}", "intro": intro, "eq": eq, "note": " ".join(notes),
                "compare": compare, "compare_note": compare_note}

    # стр. 5
    prices = sorted({ask, offer, round(ev["breakeven"], -2)} | {round(ask * k / 5000) * 5000 for k in (0.95, 0.9, 0.85)},
                    reverse=True)
    lad = [(p, run(d.at(price=p))["profit"]) for p in prices]
    top = max(abs(v) for _, v in lad) or 1
    ladder = [{"price": eur(p), "discount": pct(1 - p / ask, 0), "bar": bar_cell(v, top),
               "profit": eur(0) if abs(p - ev["breakeven"]) < 100 and p != ask and abs(p - offer) >= 1 else signed(v),
               "mine": abs(p - offer) < 1,
               "tag": ("цена продавца" if p == ask else "предложение продавцу" if abs(p - offer) < 1 else
                       "без прибыли и убытка" if abs(p - ev["breakeven"]) < 100 else "")} for p, v in lad]
    wi = ev["what_if"]
    what_if = [("Всё по плану", signed(b["profit"])), ("Аренда на 10\u00a0% ниже", signed(wi["Аренда на 10 % ниже"])),
               (f"Инвестор заплатит меньше: захочет {pct(d.exit_yield + 0.005, 2)} в год вместо {pct(d.exit_yield, 2)}",
                signed(wi["Инвестор хочет на 0,5 % больше"])),
               ("Арендатор найдётся на 3 месяца позже", signed(wi["Арендатор на 3 месяца позже"])),
               ("Ремонт обойдётся вдвое дороже", signed(wi["Ремонт в 2 раза дороже"])),
               ("Арендатор найдётся сразу после покупки", signed(wi["Арендатор уже в 2-м месяце"]))]
    vat_saving = capex(d.at(vat_lease=False)) - capex(d.at(vat_lease=True))
    resale = C.OVB * offer                   # налог, который инвестор не заплатит при перепродаже в первые 6 месяцев
    legal = [
        ("НДС с ремонта вернётся",
         f"Аренда облагается НДС, если арендатор не меньше чем на 90\u00a0% работает с НДС; это пишется в договоре аренды. "
         f"Тогда НДС 21\u00a0% за ремонт и услуги возвращается: {eur(vat_saving)} за ремонт в этом расчёте. С банком, "
         f"страховой, клиникой или школой так нельзя."),
        ("Продажа без НДС",
         "Сданное здание с договором аренды продаётся как действующий бизнес (ст. 37d закона об НДС): НДС на цену нет, "
         "вернувшийся НДС за ремонт отдавать не нужно. Условие: инвестор продолжает сдавать здание, договор продажи "
         "подписан после начала аренды."),
        ("Налог на передачу — только со здания",
         f"10,4\u00a0% с цены здания: {eur(tax)}. Для нежилых зданий ставка в 2026 году та же, 8\u00a0% — только для жилья. "
         "Стеллажи и оборудование указываются в договоре отдельной ценой по рынку: с них этот налог не платится."
         if not d.new_build else
         "Новое здание (до 2 лет после начала использования) покупается без налога на передачу, с НДС 21\u00a0%, который "
         "возвращается, если здание сдаётся с НДС."),
        ("Перепродажа в первые 6 месяцев",
         f"Если инвестор купит здание в первые 6 месяцев, он платит 10,4\u00a0% только с разницы цен (ст. 13 закона о налогах "
         f"на сделки) и экономит до {eur(resale)} — на эту сумму можно поднять цену. В расчёте продажа через 12 месяцев, "
         f"без этой экономии."),
        ("Налог на прибыль — после всех расходов",
         f"19\u00a0% с прибыли до €\u00a0200.000 и 25,8\u00a0% сверх. Налог на передачу, нотариус, ремонт, брокеры и "
         f"содержание уменьшают облагаемую прибыль: с {eur(b['profit'])} налог {eur(b['vpb'])}"
         + (f" ({pct(b['vpb'] / b['profit'], 1)})." if b["profit"] > 0 else ".")),
    ]
    tax_sum = [("Налог на передачу, покупка", eur(tax)), ("НДС за ремонт", f"вернётся {eur(vat_saving)}"),
               ("НДС при продаже", "нет"), ("Налог на прибыль", eur(b["vpb"])),
               ("Налог на передачу, продажа", f"инвестор, {eur(b['investor_tax'])}")]

    return {
        "title": f"Флип-модель {street} {city}", "date": date.today().strftime("%d.%m.%Y"), "name": f"{street}, {city}",
        "street": street, "city": city,
        "subline": ("Новое здание" if d.new_build else "Склад с офисами") + (f", {year} год" if year else "")
        + f", {num(area)} м², {pick['address']['postcode']} {pick['address']['city']}",
        "hero": shots[0]["src"] if shots else "", "verdict": verdict, "tag": tag, "kpis": kpis, "meter": meter,
        "reasons": reasons,
        "facts": facts, "blocks": blocks, "todo": todo, "images": images,
        "fill_image": shots[3]["src"] if len(shots) > 3 else None, "source_url": pick["url"],
        "map": map_jpg.resolve().as_uri(), "roads": roads, "why": ([p.strip() for p in why_md.read_text(encoding="utf-8").split("\n\n") if p.strip()]
                if why_md.exists() else why_here(pick, near)), "comps": comp_rows,
        "rent": rent_box,
        "steps": steps, "spend": spend, "spend_total": eur(b["costs"]), "get": get, "get_total": eur(b["income"]),
        "result_lines": result_lines[:2], "result_total": eur(b["net"]), "result_hint": result_hint,
        "bridge": bridge, "cash": cash, "cash_note": cash_note, "chart_title": chart_title, "investor": investor,
        "ladder": ladder, "what_if": what_if, "checks": checks(pick, rec), "legal": legal, "tax_sum": tax_sum,
        "photo_pages": photo_pages(shots), "plans": plans[:2],
        "passes": ev["passes"],
        # строка для страницы сравнения в общем файле (pack.py)
        "summary": {"street": street, "city": city, "hero": shots[0]["src"] if shots else "",
                    "yield": pct(d.exit_yield, 2), "yield_set": ev["yield_set"],
                    "kind": ("Новое здание" if d.new_build else "Склад с офисами"),
                    "area": f"{num(area)} м²" + (f", {year} год" if year else ""),
                    "ask": eur(ask), "offer": eur(offer), "cut": pct(1 - offer / ask, 0), "sale": eur(b["sale"]),
                    "net": eur(b["net"]), "own": eur(b["costs"]), "per100": f"€\u00a0{per100}",
                    "breakeven": eur(ev["breakeven"]), "be_cut": pct(need), "rent": eur(rent / 12),
                    "road": f"{road['ref']}, {km(road['km'])}"},
    }


def render(ctx: dict, out_pdf: Path, work: Path, template: str = "brief.html") -> list[dict]:
    env = jinja2.Environment(loader=jinja2.FileSystemLoader(HERE / "templates"), autoescape=True)
    env.filters["md"] = lambda t: Markup(md(t))
    env.filters["nowrap"] = lambda t: Markup(" ".join(f'<span style="white-space: nowrap">{H.escape(w)}</span>' if "-" in w
                                                      else H.escape(w) for w in str(t).split(" ")))
    css = env.from_string((HERE / "templates" / "brief.css").read_text(encoding="utf-8")).render(fonts=FONTS.resolve().as_uri())
    page = work / template
    page.write_text(env.get_template(template).render(**ctx, css=Markup(css)), encoding="utf-8")
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME, headless=True)
        tab = browser.new_page()
        tab.goto(page.resolve().as_uri(), wait_until="networkidle")
        # блок аренды — туда, где колонки стр. 3 выходят ровнее всего: справа, слева или на всю ширину
        tab.evaluate("""() => {
            const box = document.getElementById('rentbox');
            if (!box) return;
            const L = document.getElementById('place-left'), R = document.getElementById('place-right');
            const tall = el => { const k = [...el.children].filter(c => c.getBoundingClientRect().height > 0);
                                 return k.length ? k[k.length - 1].getBoundingClientRect().bottom - k[0].getBoundingClientRect().top : 0; };
            let best = null;
            for (const slot of document.querySelectorAll('.slot')) {
                slot.appendChild(box);
                box.classList.toggle('wide', slot.dataset.slot === 'full');
                const diff = Math.abs(tall(L) - tall(R));
                if (!best || diff < best.diff) best = {slot, diff};
            }
            best.slot.appendChild(box);
            box.classList.toggle('wide', best.slot.dataset.slot === 'full');
        }""")
        # кадр-заполнитель не нужен, если ему досталось меньше 3 см
        tab.evaluate("""() => document.querySelectorAll('img.fill').forEach(i => {
            if (i.getBoundingClientRect().height < 113) i.style.display = 'none'; })""")
        overflow = tab.evaluate("""() => [...document.querySelectorAll('.page .body')].map((b, i) =>
            ({page: i + 1, over: Math.round(b.scrollHeight - b.clientHeight)})).filter(x => x.over > 1)""")
        out_pdf.parent.mkdir(parents=True, exist_ok=True)
        tab.pdf(path=str(out_pdf), prefer_css_page_size=True, print_background=True)
        browser.close()
    return overflow


def register(pick: dict, report: str) -> None:
    """Записать объект в реестр показанных заказчику, чтобы следующий отбор его пропускал."""
    from reportgen import registry
    a = pick["address"]
    slug = re.sub(r"[^a-z0-9]+", "-", f"{a['street']} {a['city']}".lower()).strip("-")
    data = registry.load(C.ROOT / "data")
    entry = next((e for e in data["objects"] if e.get("slug") == slug), None)
    if entry is None:
        entry = {"slug": slug, "title": f"{a['street']}, {a['city']}", "city": a["city"],
                 "price": f"€ {num(pick['price'])}", "url": pick["url"], "reports": []}
        data["objects"].append(entry)
    if report not in entry["reports"]:
        entry["reports"].append(report)
    registry.save(C.ROOT / "data", data)


def load_candidates(run_dir: Path) -> list[dict]:
    """Посчитанные объекты прогона: из отбора и присланные вручную (manual.json)."""
    out = []
    for name in ("candidates.json", "manual.json"):
        path = run_dir / name
        if path.exists():
            out += json.loads(path.read_text(encoding="utf-8"))
    return out


def main(run_dir: Path, rank: int = 1, publish: bool = False, url: str | None = None) -> None:
    short = json.loads((run_dir / "shortlist.json").read_text(encoding="utf-8"))
    cands = load_candidates(run_dir)
    pick = next(c for c in cands if url in c["url"]) if url else (short or cands)[rank - 1]
    listings = fetch.load_jsonl(run_dir / "listings.jsonl")
    rec = next(r for r in listings if r["url"] == pick["url"])
    slug = re.search(r"object-\d+-([a-z0-9-]+)/?$", pick["url"]).group(1)
    work = run_dir / f"brief-{slug}"
    work.mkdir(parents=True, exist_ok=True)
    ctx = context(pick, rec, cands, listings, work)
    name = f"{ctx['title']} ({date.today().strftime('%d.%m.%y')}).pdf"
    out = (C.ROOT / "output" if publish else work) / name
    overflow = render(ctx, out, work)
    (work / "photos.json").write_text(json.dumps([{"url": s["url"], "cap": s["cap"]} for c in ctx["photo_pages"] for s in c["shots"]]
                                                 + [{"url": p["url"], "cap": p["cap"]} for p in ctx["plans"]],
                                                 ensure_ascii=False, indent=1), encoding="utf-8")
    if publish:
        register(pick, out.name)
    print("PDF:", out)
    print("переполнение страниц:", overflow or "нет")
    print("фото:", sum(len(c["shots"]) for c in ctx["photo_pages"]), "планировок:", len(ctx["plans"]), "| подписи:", work / "captions.json")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    url = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--url=")), None)
    main(Path(args[0]), int(args[1]) if len(args) > 1 else 1, publish="--publish" in sys.argv, url=url)
