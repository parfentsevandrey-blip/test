"""Из объявления — в модель: аренда по сопоставимым, доходность для инвестора,
расходы владельца, ремонт, а также причины, по которым объект не подходит.
"""
from __future__ import annotations

import re
import statistics as st
from datetime import date

from flipfinder import config as C

LET = re.compile(r"verhuurd aan|is verhuurd|volledig verhuurd|lopende huurovereenkomst|huurovereenkomst (?:loopt|tot|met)|"
                 r"huurinkomsten|huuropbrengst|aanvangsrendement|huurder betaalt|zittende huurder|huurstroom|"
                 r"in verhuurde staat")
STATUS_BAD = re.compile(r"onder bod|onder optie|verkocht|verhuurd", re.I)
BUILDING = re.compile(r"momenteel in aanbouw|in aanbouw|start bouw|bouw start|bouw (?:is )?gestart|nog te realiseren|"
                      r"op tekening|\bverrijst\b|sloop van de (?:bestaande )?pand|nieuwbouw van \d+ ", re.I)
NOT_BUSINESS = re.compile(r"woning te bouwen|woonbestemming|transformatie naar wonen|ontwikkellocatie|(?<!geen )bedrijfswoning|"
                          r"onderdeel (?:uit )?van een (?:buurt)?winkelcentrum|horecapand|restaurant te koop", re.I)
TENDER = re.compile(r"\binschrijving|\bveiling\b|biedingsprocedure|bieden vanaf|openbare verkoopprocedure|"
                    r"verkoop via (?:een )?(?:openbare )?inschrijving", re.I)
SHELL = re.compile(r"\bcasco\b", re.I)
# антресоль: «178 m² entresol» или «Tussenverdieping: ca. 178 m²»
MEZZ = re.compile(r"(?:ca\.?|circa)?\s*([\d.]+)\s*m²?\s*(?:entresol|insteekvloer|mezzanine|tussenverdieping|tussenvloer)|"
                  r"(?:entresol|insteekvloer|mezzanine|tussenverdieping|tussenvloer)\W{0,6}(?:ca\.?|circa)?\s*([\d.]+)\s*m", re.I)
# юнит в комплексе, а не отдельное здание
UNIT = re.compile(r"bedrijfsverzamel|verzamelgebouw|verzamelcomplex|(?<!geen )(?<!geen sprake van een )appartementsrecht|"
                  r"\bvve\b|vereniging van eigenaren|\b(?:deze|de|betreft een|een) (?:bedrijfs)?unit\b|bedrijfsunit", re.I)
# проект из многих юнитов: цена «от», площадь в карточке — другого юнита
PROJECT = re.compile(r"(?:te koop|prijzen|verkoopprijzen|units?) vanaf €|vanaf € ?[\d.]+,?-? (?:v\.o\.n|excl)|"
                     r"units variëren|nog (?:enkele|slechts \w+|\d+) units|realiseert [^.]{0,40}\d+ [^.]{0,30}units|"
                     r"\d+ [^.]{0,40}units (?:worden )?gerealiseerd|vloeroppervlakten van (?:ca\. )?\d+ tot \d+", re.I)
# общая площадь из текста: «in totaal een BVO van 308 m²», «totale vloeroppervlakte van ca. 228 m²»
AREA_TEXT = re.compile(r"(?:in totaal|totaal|totale (?:netto |bruto |verhuurbare )?(?:vloer)?oppervlakte|het geheel betreft)"
                       r"(?!\s*groot)[^\d]{0,30}?(\d{1,3}(?:\.\d{3})+|\d{2,5})(?:,\d+)?\s*m", re.I)


def year_of(rec: dict) -> int | None:
    for key in ("year", "period"):
        m = re.search(r"(1[89]\d\d|20\d\d)", str(rec.get(key) or ""))
        if m:
            return int(m.group(1))
    return None


def _per_year(amount: float, period: str) -> float:
    return amount * (12 if "maand" in period else 4 if "kwartaal" in period else 1)


def owner_costs(desc: str) -> dict:
    """Плата за землю и взносы ассоциации из текста объявления."""
    low = desc.lower()
    out = {"canon": 0.0, "vve": 0.0, "lease_end": None, "bought_off_until": None, "perpetual": False}
    m = re.search(r"canon[^€]{0,80}€\s*([\d.]+)(?:,(\d+))?[^.]{0,40}?(per jaar|per maand|per kwartaal|p\.j\.|/jaar)?", low)
    if m and "afgekocht" not in low[max(0, m.start() - 60):m.end() + 20]:
        out["canon"] = _per_year(float(m.group(1).replace(".", "")), m.group(3) or "jaar")
    m = re.search(r"(?:vve|vereniging van eigenaren|servicekosten)[^€]{0,80}€\s*([\d.]+)(?:,\d+)?\s*(?:,-)?\s*"
                  r"(per maand|per kwartaal|per jaar|p\.m\.|p\.j\.)", low)
    if m:
        out["vve"] = _per_year(float(m.group(1).replace(".", "")), m.group(2))
    m = re.search(r"afgekocht tot(?: en met)?\s*(?:\d{1,2}[- ][\w-]+[- ])?(20\d\d|21\d\d)", low)
    if m:
        out["bought_off_until"] = int(m.group(1))
    m = re.search(r"erfpacht[^.]{0,120}?(?:eindigt|einddatum|loopt tot|tot en met|tot)(?:\s+op)?\s*"
                  r"(?:\d{1,2}[- ][a-z]+[- ])?\D{0,20}?(20\d\d|21\d\d)", low)
    if m and "eeuwigdurend" not in low:
        out["lease_end"] = int(m.group(1))
    if out["lease_end"] and re.search(r"tot (?:deze|die|die datum|het einde)[^.]{0,20}afgekocht|afgekocht tot het einde", low):
        out["bought_off_until"] = out["lease_end"]           # «De erfpacht is tot deze datum afgekocht»
    out["perpetual"] = "eeuwigdurend" in low
    return out


def checked_area(rec: dict) -> tuple[int | None, str | None]:
    """Площадь из карточки, сверенная с текстом: если описание называет меньшую общую площадь, берём её."""
    area = rec.get("area")
    if not area:
        return area, None
    found = [int(m.group(1).replace(".", "")) for m in AREA_TEXT.finditer(re.sub(r"\s+", " ", rec.get("desc") or ""))]
    found = [v for v in found if 0.3 * area <= v <= 1.5 * area]
    if not found or max(found) >= area:
        return area, None
    # описание называет меньшую общую площадь — берём её: в карточке бывает площадь с навесом, участком или другим юнитом
    return max(found), f"в карточке {area} м², в описании {max(found)} м²"


def mezzanine(rec: dict) -> float:
    m = MEZZ.search(re.sub(r"\s+", " ", rec.get("desc") or ""))
    return float((m.group(1) or m.group(2)).replace(".", "")) if m else 0.0


def is_unit(rec: dict) -> bool:
    """Юнит в комплексе (общий участок, ассоциация владельцев) — в отличие от отдельного здания."""
    return bool(UNIT.search(re.sub(r"\s+", " ", rec.get("desc") or "")))


def condition(rec: dict) -> str:
    """good — класс A–B или здание после 2015 года; poor — класс E–G или без класса и до 1990 года."""
    label = (rec.get("label") or "").strip().upper()
    y = year_of(rec)
    if label[:1] in ("A", "B") or (y and y >= 2015):
        return "good"
    if label[:1] in ("E", "F", "G") or ((not label or "NIET" in label) and y and y < 1990):
        return "poor"
    return "mid"


def _nearest(pool: list[dict], here: tuple[float, float]) -> list[dict]:
    """COMPS_K ближайших предложений не дальше COMPS_KM по прямой."""
    from flipfinder import locate
    near = []
    for c in pool:
        g = locate.geocode(c["title"])
        if g and locate.km(here, g[:2]) <= C.COMPS_KM:
            near.append({**c, "km": round(locate.km(here, g[:2]), 1)})
    return sorted(near, key=lambda c: c["km"])[:C.COMPS_K]


def _similar(cards: list[dict], rec: dict, deal: str) -> list[dict]:
    """Похожие по площади предложения, каждое один раз: одна карточка бывает в выдаче нескольких городов."""
    area = rec.get("area") or 0
    out, seen = [], {rec["url"]}
    for c in cards:
        key = (c["title"], c["area"], c.get("price_text"))
        if (c["deal"] == deal and c["kind"] == "bedrijfshal" and c.get("area") and c["url"] not in seen
                and key not in seen and C.COMPS_AREA[0] * area <= c["area"] <= C.COMPS_AREA[1] * area):
            seen |= {c["url"], key}
            out.append(c)
    return out


def rent_comps(cards: list[dict], rec: dict, here: tuple[float, float] | None = None) -> list[dict]:
    """Предложения аренды похожей площади: ближайшие к объекту, без адреса — тот же город или район."""
    pool = [{**c, "rent_m2": c["rent_year"] / c["area"]} for c in _similar(cards, rec, "huur") if c.get("rent_year")]
    pool = [c for c in pool if 25 <= c["rent_m2"] <= 400]
    if here:
        near = _nearest(pool, here)
        if len(near) >= C.COMPS_MIN:
            return near
    same = [c for c in pool if c["slug"] == rec["slug"]]
    return same if len(same) >= C.COMPS_MIN else [c for c in pool if c["territory"] == rec["territory"]]


def vacant(cards: list[dict], rec: dict, here: tuple[float, float]) -> dict:
    """Свободные склады похожей площади на продаже рядом: медиана цены за м²."""
    near = _nearest([c for c in _similar(cards, rec, "koop") if c.get("price")], here)
    return {"n": len(near), "m2": st.median(c["price"] / c["area"] for c in near) if len(near) >= C.COMPS_MIN else None}


def rent(cards: list[dict], rec: dict, here: tuple[float, float] | None = None) -> dict:
    comps = rent_comps(cards, rec, here)
    area = rec.get("area") or 0
    mezz = min(mezzanine(rec), 0.6 * area)
    effective = area - mezz + C.MEZZANINE_WEIGHT * mezz
    out = {"comps": comps, "n": len(comps), "effective_area": effective, "mezzanine": mezz}
    if len(comps) >= C.COMPS_MIN:
        med = st.median(c["rent_m2"] for c in comps)
        out["comps_m2"] = med
        estimate = med * C.RENT_SAFETY * (C.RENT_POOR if condition(rec) == "poor" else 1.0) * effective
    else:
        estimate = None
    ask = rec.get("ask_rent") or ((rec.get("ask_rent_m2") or 0) * area or None)
    out["ask"] = ask
    if estimate and ask:
        out["rent"], out["basis"] = (ask, "ставка продавца") if ask < estimate else (estimate, "сопоставимые")
    elif estimate:
        out["rent"], out["basis"] = estimate, "сопоставимые"
    elif ask:
        out["rent"], out["basis"] = ask * C.RENT_SAFETY, "ставка продавца"
    else:
        out["rent"], out["basis"] = None, "нет данных"
    return out


def exit_yield(rec: dict, oc: dict, ring: str = "zone") -> tuple[float, list[str]]:
    base = C.EXIT_YIELD_CITY.get(rec["slug"], C.EXIT_YIELD[rec["territory"]])
    notes = []
    y = year_of(rec)
    if condition(rec) == "poor":
        base += C.EXIT_ADJ["poor"]
        notes.append("здание требует вложений: старое и с низким энергетическим классом")
    if y and y >= 2015:
        base += C.EXIT_ADJ["after_2015"]
        notes.append("здание после 2015 года")
    if (rec.get("area") or 0) >= 2000:
        base += C.EXIT_ADJ["large_2000m2"]
        notes.append("больше 2.000 м²")
    if ring != "zone":
        base += C.EXIT_ADJ["nearby"]
        notes.append("меньше спроса инвесторов, чем в крупных промзонах")
    if oc["lease_end"] and oc["lease_end"] <= 2060:
        base += C.EXIT_ADJ["lease_ends_2060"]
        notes.append(f"право на землю до {oc['lease_end']} года")
    return round(base, 4), notes


def reject(rec: dict, shown: set[str]) -> list[str]:
    """Причины, по которым объект не подходит под флип. Пустой список — подходит."""
    why = []
    desc = re.sub(r"\s+", " ", rec.get("desc") or "").lower()
    price, area = rec.get("price"), rec.get("area")
    if not price:
        why.append("цена не указана")
    elif price > C.PRICE_MAX:
        why.append("дороже € 2 млн")
    if not area or area < C.AREA_MIN:
        why.append("меньше 150 м²")
    if STATUS_BAD.search(rec.get("status") or ""):
        why.append("уже в сделке")
    if LET.search(desc):
        why.append("уже сдан")
    y = year_of(rec)
    if BUILDING.search(desc) or (y and y > date.today().year):
        why.append("ещё строится")
    if PROJECT.search(desc):
        why.append("проект из многих юнитов: цена «от», площадь другого юнита")
    if NOT_BUSINESS.search(desc):
        why.append("не складское или производственное назначение")
    if TENDER.search(desc):
        why.append("продажа через торги")
    if area and (rec.get("office") or 0) > C.OFFICE_SHARE_MAX * area:
        why.append("офисов больше половины")
    if rec.get("kind") != "bedrijfshal":
        why.append("не склад")
    slug = re.search(r"object-\d+-([a-z0-9-]+)/?$", rec["url"])
    if slug and any(s.startswith(slug.group(1).replace("-", "")) for s in shown):
        why.append("уже был в отчёте")
    return why


def new_build(rec: dict) -> bool:
    y = year_of(rec)
    text = (rec.get("price_text") or "") + " " + (rec.get("desc") or "")
    return bool(y and y >= 2024 and re.search(r"v\.o\.n\.|vrij op naam|btw|geen overdrachtsbelasting", text, re.I))
