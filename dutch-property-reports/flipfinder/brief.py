"""Шаг 3: отчёт по одному объекту — 5 страниц о сделке и фотографии.

    python3 -I flipfinder/brief.py RUN_DIR [МЕСТО_В_СПИСКЕ] [--publish]

Берёт объект из RUN_DIR/shortlist.json (или candidates.json, если порог не прошёл никто),
собирает HTML по шаблону templates/brief.html и печатает PDF через Chromium: в папку отчёта,
а с --publish — в output/.
Подписи к фотографиям — RUN_DIR/brief-<slug>/captions.json: {"<url кадра>": "подпись"}.
"""
from __future__ import annotations

import html as H
import json
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

HERE = Path(__file__).resolve().parent
FONTS = C.ROOT / "assets" / "fonts"
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
POIS = C.ROOT / "data" / "flip" / "pois.json"

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
           "wognum": "Вогнюм", "alkmaar": "Алкмар", "obdam": "Обдам"}
FEATURES = {"lichtstraten": "световые фонари", "lichtstraat": "световые фонари", "elektrische overheaddeur": "электрические ворота",
            "overheaddeuren": "ворота", "overheaddeur": "ворота", "betonvloer": "бетонный пол", "heater": "обогреватель в зале",
            "toilet": "туалет", "airconditioning": "кондиционер", "mechanische ventilatie": "вентиляция",
            "te openen ramen": "открывающиеся окна", "pantry": "кухня", "krachtstroom": "силовая сеть",
            "kabelgoten": "кабельные лотки", "systeemplafond": "системный потолок", "sprinkler": "спринклеры",
            "goederenlift": "грузовой лифт", "alarminstallatie": "сигнализация", "topkoeling": "охлаждение",
            "zonnepanelen": "солнечные панели", "laadkuil": "погрузочная платформа", "loading dock": "погрузочные доки",
            "cv-ketel": "отопление", "verwarming": "отопление", "camerabewaking": "видеонаблюдение"}
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
    shots, plans = [], []
    for url in rec.get("photos") or []:
        try:
            path = media.fetch(url, cache)
        except Exception:                                      # noqa: BLE001 — кадр не загрузился
            continue
        (plans if is_plan(path) else shots).append({"url": url, "src": path.resolve().as_uri()})
    return shots, plans


# ------------------------------------------------------------------ графики: SVG, цвета из токенов страницы
def meter_svg(need: float, threshold: float) -> str:
    top = max(0.20, need + 0.04)
    w, h, y = 600, 56, 20
    x = lambda v: 10 + (w - 40) * max(0.0, v) / top          # noqa: E731
    fill = "var(--gain)" if need <= threshold else "var(--loss)"
    ticks = "".join(f'<text x="{x(t / 100):.1f}" y="{y + 30}" font-size="10" fill="var(--muted)" text-anchor="middle">{t} %</text>'
                    for t in range(0, int(top * 100) + 1, 5))
    return (f'<svg viewBox="0 0 {w} {h}">'
            f'<rect x="10" y="{y}" width="{w - 40}" height="12" rx="4" fill="var(--rule)"/>'
            f'<rect x="10" y="{y}" width="{max(0.0, x(need) - 10):.1f}" height="12" rx="4" fill="{fill}"/>'
            f'<line x1="{x(threshold):.1f}" y1="{y - 6}" x2="{x(threshold):.1f}" y2="{y + 16}" stroke="var(--ink)" stroke-width="2"/>'
            f'<text x="{x(threshold) + 5:.1f}" y="{y - 8}" font-size="11" fill="var(--ink)">порог {pct(threshold, 0)}</text>'
            f'<text x="{max(x(need) - 4, 70):.1f}" y="{y - 8}" font-size="11" font-weight="600" fill="var(--ink)" '
            f'text-anchor="end">нужно {pct(need)}</text>{ticks}</svg>')


def waterfall_svg(rows: list[tuple[str, float]]) -> str:
    """Горизонтальный «мост»: каждая полоса начинается там, где кончилась предыдущая; последняя — прибыль от нуля."""
    lo, hi, cum, spans = 0.0, 0.0, 0.0, []
    for i, (label, v) in enumerate(rows):
        last = i == len(rows) - 1
        a, b = (0.0, v) if last else (cum, cum + v)
        spans.append((label, v, min(a, b), max(a, b), last))
        if not last:
            cum += v
        lo, hi = min(lo, a, b), max(hi, a, b)
    W, row_h, left, right = 292, 40, 106, 46
    plot = W - left - right
    x = lambda v: left + plot * (v - lo) / (hi - lo)        # noqa: E731
    out = [f'<svg viewBox="0 0 {W} {row_h * len(rows) + 4}">',
           f'<line x1="{x(0):.1f}" y1="2" x2="{x(0):.1f}" y2="{row_h * len(rows)}" stroke="var(--axis)" stroke-width="0.8"/>']
    for i, (label, v, a, b, last) in enumerate(spans):
        y = i * row_h + 12
        color = "var(--gain)" if v >= 0 else "var(--loss)"
        weight = "600" if last else "400"
        value = ("+" if v >= 0 else "−") + f"{abs(v) / 1000:,.0f}".replace(",", ".") + " тыс."
        out.append(f'<text x="0" y="{y + 10}" font-size="9" font-weight="{weight}" fill="var(--ink)">{H.escape(label)}</text>'
                   f'<rect x="{x(a):.1f}" y="{y - 1}" width="{max(1.5, x(b) - x(a)):.1f}" height="16" rx="2" fill="{color}"/>'
                   f'<text x="{W}" y="{y + 10}" font-size="9" font-weight="{weight}" fill="var(--ink)" text-anchor="end">{value}</text>')
    return "".join(out) + "</svg>"


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


def investor_comps(listings: list[dict], territory: str, limit: int = 3) -> list[dict]:
    """Сданные склады и мастерские на продаже в том же районе, где в объявлении есть аренда."""
    out = []
    for r in listings:
        if r.get("kind") != "belegging" or r.get("territory") != territory or not r.get("price"):
            continue
        d = (r.get("desc") or "").lower()
        if not re.search(r"bedrijfsruimte|bedrijfshal|bedrijfspand|loods|magazijn|werkplaats", d):
            continue
        m = re.search(r"(?:huursom|huuropbrengst|huurinkomsten|jaarhuur|huurstroom|huurprijs)[^€]{0,90}€\s*([\d.]+)", d)
        if not m:
            continue
        rent = int(m.group(1).replace(".", ""))
        if "per maand" in d[m.end():m.end() + 25]:
            rent *= 12
        y = rent / r["price"]
        if 0.045 <= y <= 0.12:
            name = re.search(r"te koop: (.+?) \d{4} ?[A-Z]{2} (.+?) \[", r.get("title") or "")
            out.append({"name": f"{name.group(1)}, {name.group(2)}" if name else r["title"], "price": r["price"],
                        "rent": rent, "yield": y})
    return sorted(out, key=lambda c: c["price"])[:limit]


def neighbours(pick: dict, limit: int = 4) -> list[dict]:
    """Ради чего мы выбрали район: площадки и компании рядом, с дорогой."""
    lat, lon = pick["lat"], pick["lon"]
    if pick["place"].get("whole"):
        pois = json.loads(POIS.read_text(encoding="utf-8")).get(pick["territory"], []) if POIS.exists() else []
        rows = []
        for p in pois:
            r = locate.drive((lat, lon), tuple(p["at"]))
            if r:
                rows.append({"label": p["label"], "km": r[0], "min": round(r[1]), "about": p["about"]})
        return sorted(rows, key=lambda r: r["min"])[:limit]
    return [{"label": a["label"], "km": a["km"], "min": a["min"], "about": ""}
            for a in locate.anchors(lat, lon, pick["territory"], limit) if a["km"] is not None]


def why_here(pick: dict, near: list[dict]) -> list[str]:
    place = pick["place"]
    if place.get("whole"):
        return [n["about"] for n in near[:3] if n["about"]]
    body = place.get("body") or ""
    lead = (f"Здание расположено в районе **{place['short']}**." if place["ring"] == "zone" else
            f"Здание расположено в {place.get('drive_min')} минутах от района **{place['short']}**.")
    return [f"{lead} {body}"]


def checks(pick: dict, rec: dict) -> list[str]:
    info, out = pick["info"], []
    oc = info["owner"]
    if oc["canon"]:
        out.append(f"**Земля в аренде:** кому платится € {num(oc['canon'])} в год, до какого года право и когда индексация.")
    if info.get("year") and info["year"] < 1994:
        out.append(f"**Асбест и кровля:** обследование здания {info['year']} года.")
    label = (rec.get("label") or "").strip()
    if not label or label[0] in "DEFG" or "Niet" in label:
        out.append("**Энергетический класс:** что нужно, чтобы офисная часть получила класс C.")
    out.append(f"**Спрос:** брокер по аренде подтверждает ставку € {num(pick['deal']['rent'] / pick['area'])} за м² "
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
    d = Deal(**pick["deal"])
    ev = evaluate(d)
    b, ask, area = ev["base"], d.ask, pick["area"]
    offer, rent, need = b["price"], d.rent, ev["breakeven_discount"]
    city = CITY_RU.get(pick["slug"], pick["address"]["city"])
    street = pick["address"]["street"]
    year, oc, road = pick["info"].get("year"), pick["info"]["owner"], pick["road"]
    shots, plans = photos(rec, work / "img")
    caps = json.loads((work / "captions.json").read_text(encoding="utf-8")) if (work / "captions.json").exists() else {}
    for i, s in enumerate(shots, 1):
        s["cap"] = caps.get(s["url"], f"Кадр {i}")
    for p in plans:
        p["cap"] = caps.get(p["url"], "Планировка")
    near = neighbours(pick)
    first_rent = d.lease_month + d.rent_free
    rent_months = max(0, d.horizon - first_rent + 1)
    since = date_ru(rec.get("since"))

    # стр. 1
    verdict = (f"Предлагаем продавцу **{eur(offer)}**, на {pct(1 - offer / ask, 0)} меньше его цены. После ремонта "
               f"сдаём здание на 5 лет и через 12 месяцев продаём его сданным инвестору за **{eur(b['sale'])}**. "
               f"Чистая прибыль — **{eur(b['net'])}**.")
    if not ev["passes"]:
        verdict += (f" Порог не пройден: без убытка здание можно купить только со скидкой {pct(need)}; "
                    "отчёт собран в тестовом режиме.")
    kpis = [{"label": "Цена продавца", "value": eur(ask), "hint": f"{eur(ask / area, 1)} за м², налог за покупателем"},
            {"label": "Покупаем за", "value": eur(offer), "hint": f"скидка {pct(1 - offer / ask, 0)}"},
            {"label": "Продаём через 12 месяцев", "value": eur(b["sale"]), "hint": "сданным, инвестору"},
            {"label": "Чистая прибыль", "value": eur(b["net"]), "hint": f"{pct(b['return'])} на вложенные {eur(b['peak'])}",
             "key": True}]
    meter = {"title": (f"Чтобы не уйти в минус, продавец должен уступить **{pct(need)}**. "
                       f"Мы берём объекты, где это не больше {pct(C.THRESHOLD, 0)}."), "svg": meter_svg(need, C.THRESHOLD)}
    med, n_comps = pick["info"]["rent"].get("comps_m2"), pick["info"]["rent"]["n"]
    nxt = next((c for c in cands if c["url"] != pick["url"]), None)
    reasons = [
        ("**Скидка в пределах порога.**" if ev["passes"] else "**Ближе всех к порогу.**")
        + f" В ноль при цене {eur(ev['breakeven'])}"
        + (f"; следующему кандидату нужно {pct(nxt['breakeven_discount'])}." if nxt else "."),
        f"**Аренда по рынку.** В модели € {num(rent / area)} за м² в год"
        + (f": {pct(C.RENT_SAFETY, 0)} от средней ставки {plural(n_comps, 'предложения', 'предложений', 'предложений')} "
           f"рядом (€ {num(med)})." if med else "."),
        f"**Дорога.** До магистрали {road['ref']} — {km(road['km'])}, {road['min']} мин"
        + (f"; до {near[0]['label']} — {near[0]['min']} мин." if near else "."),
    ]
    passed = sum(1 for c in cands if c["passes"])

    # стр. 2
    hall, office = rec.get("hall"), rec.get("office")
    parts = "; ".join(x for x in [f"зал {num(hall)} м²" if hall else "", f"офисы {num(office)} м²" if office else ""] if x)
    facts = [
        [("Площадь", f"{num(area)} м²" + (f": {parts}" if parts else "")
          + (f" (по описанию; {pick['area_note'].split(', ')[0]})" if pick.get("area_note") else "")),
         ("Высота зала", rec.get("height") or "не указана"), ("Год постройки", str(year) if year else "не указан"),
         ("Энергетический класс", label if (label := (rec.get("label") or "").strip()) and "Niet" not in label else "не указан"),
         ("Участок", (f"{num(rec['plot'])} м²" if rec.get("plot") else "не указан")
          + (", земля в аренде" if oc["canon"] or oc["lease_end"] else ""))],
        [("Цена продавца", f"{eur(ask)}, налог 10,4 % за покупателем"), ("Цена за м²", eur(ask / area, 1)),
         ("Арендатор", "нет, здание свободно"), ("Плата за землю", f"€ {num(oc['canon'])} в год" if oc["canon"] else "нет"),
         ("На продаже", f"с {since}" if since and re.match(r"\d+ [а-я]", since) else (since or "не указано"))],
    ]
    feats = features_ru(rec)
    blocks = []
    if feats:
        blocks.append(("Оснащение", [(feats[0][0].upper() + feats[0][1:] + (", " + ", ".join(feats[1:]) if len(feats) > 1 else "") + ".")]))
    if oc["canon"] or oc["lease_end"]:
        land = "Участок в эрфпахте — долгосрочной аренде земли"
        land += f": владелец платит **€ {num(oc['canon'])} в год**." if oc["canon"] else "."
        if oc["lease_end"]:
            land += f" Право на землю действует до {oc['lease_end']} года."
        if oc["bought_off_until"]:
            land += f" Плата внесена вперёд до {oc['bought_off_until']} года."
        blocks.append(("Земля", [land]))
    blocks.append(("Что сделать перед сдачей", [
        f"**Ремонт:** € {num(C.CAPEX_M2_SHELL if d.shell else C.CAPEX_M2)} за м² и резерв — {eur(b['works'])} без НДС.",
        f"**Арендатор:** брокер берёт 15 % годовой аренды, арендатор получает {months(d.rent_free)} без платы.",
        "**Договор:** 5 лет, индексация по инфляции, аренда с НДС.",
        "**Покупка:** договор с условием проверки здания и документов."]))
    images = ([{"src": plans[0]["src"], "cls": "plan"}] if plans else []) + \
             ([{"src": shots[i]["src"], "cls": ""} for i in (3, 4) if len(shots) > i][: 2 - (1 if plans else 0)])

    # стр. 3
    map_png = maps.render(pick["lat"], pick["lon"], work / "map.png", zoom=13, width=1400, height=760)
    roads = [{"shield": road["ref"].replace(" ", ""), "label": "въезд на магистраль", "value": f"{km(road['km'])}, {road['min']} мин"}]
    roads += [{"label": n["label"], "value": f"{km(n['km'])}, {n['min']} мин"} for n in near]
    comps = sorted(pick["info"]["comps"], key=lambda c: abs(c["area"] - area))[:6]
    comp_rows = [{"name": c["title"], "area": num(c["area"]), "rate": c["rent_m2"]} for c in comps]
    comp_rows.append({"name": f"{street}, наша оценка", "area": num(area), "rate": rent / area, "mine": True})
    comp_rows.sort(key=lambda c: -c["rate"])
    for c in comp_rows:
        c["rate"] = f"€ {num(c['rate'])}"
    mezz = pick["info"]["rent"].get("mezzanine")

    # стр. 4
    tax = b["buy"]["Налог на передачу 10,4 %"]
    upkeep = -b["tot"]["upkeep"]
    letting = -b["tot"]["letting"]
    money_out = [("Цена покупки", eur(offer)), ("Налог на передачу 10,4 %", eur(tax)),
                 ("Нотариус, проверка здания, консультант", eur(b["buy_total"] - offer - tax)),
                 ("Ремонт", eur(b["works"])), ("Брокер по аренде и реклама", eur(letting)),
                 ("Содержание за год: налог OZB, страховка" + (", земля" if oc["canon"] else ""), eur(upkeep)),
                 ("Брокер и юрист на продаже", eur(b["sale_cost"]))]
    money_in = [(f"Аренда за {months(rent_months)}", eur(b["rent_collected"])), ("Продажа инвестору", eur(b["sale"]))]
    result = [("Прибыль до налога", eur(b["profit"])), ("Налог на прибыль 19 %", eur(-b["vpb"])),
              ("Чистая прибыль", eur(b["net"])), ("Больше всего денег в деле", eur(b["peak"])),
              ("Чистая прибыль на вложенное", pct(b["return"]))]
    steps = [{"when": "Месяц 0", "what": "Покупка", "more": f"{eur(offer)} и налог"},
             {"when": "Месяцы 1–2", "what": "Ремонт", "more": eur(b["works"])},
             {"when": f"Месяц {d.lease_month}", "what": "Въезд арендатора", "more": f"договор на 5 лет, {months(d.rent_free)} без платы"},
             {"when": f"Месяц {first_rent}", "what": "Первая аренда", "more": f"{eur(rent / 12)} в месяц"},
             {"when": "Месяц 12", "what": "Продажа инвестору", "more": eur(b["sale"])}]
    waterfall = waterfall_svg([("Покупка и налог", -b["buy_total"]), ("Ремонт и сдача", -(b["works"] + letting)),
                               ("Содержание", -upkeep), ("Расходы на продажу", -b["sale_cost"]),
                               (f"Аренда, {rent_months} мес.", b["rent_collected"]), ("Продажа", b["sale"]),
                               ("Прибыль", b["profit"])])
    deduct = d.owner_costs
    eq = (f"(€ {num(rent)} аренды − € {num(deduct)} за землю) ÷ {pct(d.exit_yield, 2)} = {eur(b['sale'])}" if deduct
          else f"€ {num(rent)} аренды ÷ {pct(d.exit_yield, 2)} = {eur(b['sale'])}")
    inv = investor_comps(listings, pick["territory"])
    inv_text = ("Сданные склады на продаже в этом районе: " + "; ".join(
        f"{c['name']} — {eur(c['price'])} при аренде {eur(c['rent'])}, это {pct(c['yield'], 2)}" for c in inv) + "."
                if inv else "Сданных складов с указанной арендой в районе сейчас нет; доходность — по ориентирам региона.")
    if pick["info"]["exit_notes"]:
        inv_text += " Поправка: " + ", ".join(pick["info"]["exit_notes"]) + "."
    vac = pick["info"].get("vacant") or {}
    if vac.get("m2"):
        inv_text += (f" Для сравнения: свободные склады похожей площади рядом выставлены в среднем по € {num(vac['m2'])} "
                     f"за м², мы продаём сданное здание по € {num(b['sale'] / area)} за м².")
    investor = {"intro": (f"Инвестор платит столько, чтобы аренда за год составила {pct(d.exit_yield, 2)} от цены; "
                          "налог 10,4 % он платит сверху. Чем выше аренда и длиннее договор, тем выше цена."),
                "eq": eq, "comps": inv_text}

    # стр. 5
    prices = sorted({round(ask * k / 5000) * 5000 for k in (1.0, 0.95, 0.9, 0.85, 0.8)} | {offer, round(ev["breakeven"], -2)},
                    reverse=True)
    lad = [(p, run(d.at(price=p))["profit"]) for p in prices]
    top = max(abs(v) for _, v in lad) or 1
    ladder = [{"price": eur(p), "discount": pct(1 - p / ask, 0), "profit": signed(v), "bar": bar_cell(v, top),
               "mine": abs(p - offer) < 1,
               "tag": ("цена продавца" if p == ask else "наша цена" if abs(p - offer) < 1 else
                       "в ноль" if abs(p - ev["breakeven"]) < 100 else "")} for p, v in lad]
    wi = ev["what_if"]
    what_if = [("Базовый расчёт", signed(b["profit"])), ("Аренда на 10 % ниже", signed(wi["Аренда на 10 % ниже"])),
               (f"Инвестору нужно {pct(d.exit_yield + 0.005, 2)} вместо {pct(d.exit_yield, 2)}",
                signed(wi["Инвестор хочет на 0,5 % больше"])),
               ("Арендатор въезжает на 3 месяца позже", signed(wi["Арендатор на 3 месяца позже"])),
               ("Ремонт в 2 раза дороже", signed(wi["Ремонт в 2 раза дороже"])),
               ("Арендатор найден ещё до передачи", signed(wi["Арендатор уже в 2-м месяце"]))]
    vat_saving = capex(d.at(vat_lease=False)) - capex(d.at(vat_lease=True))
    legal = [("Аренда с НДС", f"Если арендатор платит НДС, НДС на ремонт и услуги возвращается: {eur(vat_saving)} в этом расчёте."),
             ("Продажа сданного здания", "Это передача действующего бизнеса: на цену нет НДС, обязательства по НДС переходят к инвестору."),
             ("Налог 10,4 %", "Через 12 месяцев инвестор платит его со всей цены; только при продаже в первые 6 месяцев — с прироста."
              if not d.new_build else "Новое здание покупается без налога 10,4 %; через 12 месяцев инвестор платит его как обычно.")]

    return {
        "title": f"Флип-модель {street} {city}", "date": date.today().strftime("%d.%m.%Y"), "name": f"{street}, {city}",
        "subline": ("Новое здание" if d.new_build else "Склад с офисами") + (f", {year} год" if year else "")
        + f", {num(area)} м², {pick['address']['postcode']} {pick['address']['city']}",
        "hero": shots[0]["src"] if shots else "", "verdict": verdict, "kpis": kpis, "meter": meter, "reasons": reasons,
        "picked_note": (f"Посчитано {plural(len(cands), 'здание', 'здания', 'зданий')} на продаже в наших районах и рядом; "
                        f"порог {pct(C.THRESHOLD, 0)} {'прошло' if passed == 1 else 'прошли'} {passed}."),
        "facts": facts, "blocks": blocks, "images": images, "source_url": pick["url"],
        "map": map_png.resolve().as_uri(), "roads": roads, "why": why_here(pick, near), "comps": comp_rows,
        "comps_note": (f"Ближайшие предложения аренды на funda in business площадью от половины до двойной нашей. Наша ставка — "
                       f"{pct(C.RENT_SAFETY, 0)} от средней" + (", антресоль считается как 30 % площади." if mezz else ".")),
        "steps": steps, "money_out": money_out, "out_total": eur(b["costs"]), "money_in": money_in,
        "in_total": eur(b["income"]), "result": result, "waterfall": waterfall, "investor": investor,
        "ladder": ladder, "what_if": what_if, "checks": checks(pick, rec), "legal": legal,
        "photo_pages": [shots[i:i + 6] for i in range(0, len(shots), 6)], "plans": plans[:2],
        "passes": ev["passes"],
    }


def render(ctx: dict, out_pdf: Path, work: Path) -> list[dict]:
    env = jinja2.Environment(loader=jinja2.FileSystemLoader(HERE / "templates"), autoescape=True)
    env.filters["md"] = lambda t: Markup(md(t))
    css = env.from_string((HERE / "templates" / "brief.css").read_text(encoding="utf-8")).render(fonts=FONTS.resolve().as_uri())
    page = work / "brief.html"
    page.write_text(env.get_template("brief.html").render(**ctx, css=Markup(css)), encoding="utf-8")
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=CHROME, headless=True)
        tab = browser.new_page()
        tab.goto(page.resolve().as_uri(), wait_until="networkidle")
        overflow = tab.evaluate("""() => [...document.querySelectorAll('.page .body')].map((b, i) =>
            ({page: i + 1, over: Math.round(b.scrollHeight - b.clientHeight)})).filter(x => x.over > 1)""")
        out_pdf.parent.mkdir(parents=True, exist_ok=True)
        tab.pdf(path=str(out_pdf), prefer_css_page_size=True, print_background=True)
        browser.close()
    return overflow


def main(run_dir: Path, rank: int = 1, publish: bool = False) -> None:
    short = json.loads((run_dir / "shortlist.json").read_text(encoding="utf-8"))
    cands = json.loads((run_dir / "candidates.json").read_text(encoding="utf-8"))
    pick = (short or cands)[rank - 1]
    listings = fetch.load_jsonl(run_dir / "listings.jsonl")
    rec = next(r for r in listings if r["url"] == pick["url"])
    slug = re.search(r"object-\d+-([a-z0-9-]+)/?$", pick["url"]).group(1)
    work = run_dir / f"brief-{slug}"
    work.mkdir(parents=True, exist_ok=True)
    ctx = context(pick, rec, cands, listings, work)
    name = f"{ctx['title']} ({date.today().strftime('%d.%m.%y')}).pdf"
    out = (C.ROOT / "output" if publish else work) / name
    overflow = render(ctx, out, work)
    (work / "photos.json").write_text(json.dumps([{"url": s["url"], "cap": s["cap"]} for c in ctx["photo_pages"] for s in c]
                                                 + [{"url": p["url"], "cap": p["cap"]} for p in ctx["plans"]],
                                                 ensure_ascii=False, indent=1), encoding="utf-8")
    print("PDF:", out)
    print("переполнение страниц:", overflow or "нет")
    print("фото:", sum(len(c) for c in ctx["photo_pages"]), "планировок:", len(ctx["plans"]), "| подписи:", work / "captions.json")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    main(Path(args[0]), int(args[1]) if len(args) > 1 else 1, publish="--publish" in sys.argv)
