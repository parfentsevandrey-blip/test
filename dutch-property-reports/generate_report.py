#!/usr/bin/env python3
"""CLI генератора отчётов по объектам голландской коммерческой недвижимости.

Сборка отчёта:
    python generate_report.py build data/report-2026-07-27.json

Черновик карточки объекта из сохранённой страницы funda:
    python generate_report.py parse raw/object.html -o data/objects/draft.json
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from pathlib import Path
from typing import NamedTuple

from reportgen import docx_render, finance, funda_parse, maps, media, pdf

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".cache"

NBSP = "\u00A0"  # неразрывный пробел между числом и единицей измерения
NBHY = "‑"  # неразрывный дефис в адресах, номерах домов и телефонах
NA = "н/д"       # значение, которого нет в карточке

PROFILE_WORDS = 14  # предел строки «Профиль» в сводной таблице

# Дисклеймер сводной страницы, если в файле отчёта нет поля «disclaimer»
DEFAULT_DISCLAIMER = (
    "Показатели рассчитаны по данным листингов и носят ориентировочный характер: "
    f"цена входа включает overdrachtsbelasting {finance.fmt_pct(finance.OVB_RATE)} "
    f"и сопутствующие расходы по сделке {finance.fmt_pct(finance.CLOSING_EXTRA_RATE)}, "
    "NAR к цене входа посчитан при доле эксплуатационных расходов, указанной "
    "в карточке объекта. Перед сделкой цифры подлежат проверке по документам продавца."
)

# Дисклеймер закрывающей страницы; перекрывается полем «closing_note» отчёта
DEFAULT_CLOSING_NOTE = (
    "Все расчёты в отчёте ориентировочные и выполнены по данным публикаций листингов. "
    "Отчёт не является инвестиционной рекомендацией, оценкой или офертой. Показатели, "
    "договоры аренды и технические характеристики подлежат проверке по документам "
    "продавца до принятия решения о сделке."
)

# Источник данных о населении городов; перекрывается полем «population_source»
DEFAULT_POPULATION_SOURCE = (
    "открытые статистические данные по муниципалитетам "
    "(Centraal Bureau voor de Statistiek, CBS StatLine), на 1 января 2026 года"
)

# Глоссарий закрывающей страницы: «термин — расшифровка по-русски»
GLOSSARY: list[tuple[str, str]] = [
    (
        "k.k. (kosten koper)",
        "признак цены: налог на переход права и расходы по сделке покупатель платит "
        "сверх запрашиваемой цены",
    ),
    (
        "v.o.n. (vrij op naam)",
        "признак цены: расходы по сделке уже включены в цену и остаются на продавце",
    ),
    (
        "v.v.o. (verhuurbaar vloeroppervlak)",
        "арендуемая площадь пола — база для расчёта арендной платы",
    ),
    (
        "BAR (bruto aanvangsrendement)",
        "валовая начальная доходность: годовая арендная плата к цене или к цене входа",
    ),
    (
        "NAR (netto aanvangsrendement)",
        "чистая начальная доходность: арендная плата за вычетом эксплуатационных "
        "расходов собственника к цене входа",
    ),
    (
        "WALT (weighted average lease term)",
        "средневзвешенный оставшийся срок действующих договоров аренды",
    ),
    (
        "overdrachtsbelasting",
        "налог на переход права собственности; для нежилой недвижимости ставка "
        f"2026 года — {finance.fmt_pct(finance.OVB_RATE)}",
    ),
    (
        "bestemmingsplan",
        "муниципальный план назначения территории: задаёт разрешённое использование "
        "участка и здания",
    ),
    (
        "WBB (Wet bodembescherming)",
        "закон об охране почв: определяет порядок исследования грунта и обязанность "
        "по его санации",
    ),
    (
        "ROZ (Raad voor Onroerende Zaken)",
        "совет по недвижимости; его типовые формы договоров аренды приняты на рынке "
        "за стандарт",
    ),
    (
        "servicekosten",
        "плата за коммунальные и сервисные услуги, которую арендатор вносит сверх "
        "арендной ставки",
    ),
    (
        "herzieningstermijn",
        "срок пересмотра вычета НДС по недвижимости — десять лет с года ввода "
        "объекта в эксплуатацию",
    ),
    (
        "entresol",
        "антресоль: промежуточный ярус внутри производственного или складского "
        "помещения",
    ),
    (
        "bedrijfswoning",
        "служебное жильё при предприятии: жилая единица на участке производственного "
        "назначения",
    ),
]

log = logging.getLogger("report")


class Lot(NamedTuple):
    """Объект отчёта: карточка, её показатели и подписи для обложки и сводки."""

    obj: dict
    metrics: dict
    street: str   # полный состав лота: «Archimedesbaan 11 и 7‑9»
    city: str


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------
# помощники по данным карточки
# --------------------------------------------------------------------------
def spec_value(obj: dict, label: str) -> str | None:
    """Значение описательной строки «specs» по началу подписи."""
    for row in obj.get("specs", []):
        if isinstance(row, (list, tuple)) and len(row) >= 2:
            if str(row[0]).lower().startswith(label.lower()):
                return str(row[1])
    return None


def short_value(value: str | None) -> str:
    """Короткая версия значения для сводной таблицы — без пояснения в скобках."""
    if not value:
        return NA
    return value.split(" (")[0].strip() or NA


def na(value: object) -> str:
    """«н/д» вместо пустого значения: finance возвращает «—», карточка — пустую строку."""
    text = "" if value is None else str(value).strip()
    return text if text and text != finance.EMPTY else NA


def nb_hyphen(text: str) -> str:
    """Неразрывный дефис в адресе или номере: «Steenovenweg 11‑13», «030‑2331116»."""
    return text.replace("-", NBHY) if text else text


YEAR_RE = re.compile(r"\b(1[5-9]\d{2}|2[01]\d{2})\b")

# Пробельные символы, по которым можно резать текст: U+00A0 сюда намеренно не входит
ASCII_WS_RE = re.compile(r"[ \t\n\r\f\v]+")


def build_year(obj: dict) -> str:
    """Год постройки одной короткой строкой: «1979 / 2019» вместо пояснений."""
    raw = spec_value(obj, "Год постройки")
    if not raw:
        return NA
    years = list(dict.fromkeys(YEAR_RE.findall(raw)))  # порядок как в карточке, без повторов
    return " / ".join(years) if years else short_value(raw)


def short_profile(text: str | None, limit: int = PROFILE_WORDS) -> str:
    """Строка «Профиль» для сводки: не длиннее limit слов, ничего не дописывая.

    Делим только по обычным пробельным символам: str.split() без аргумента режет
    и по U+00A0, а обратная склейка ставит обычный пробел — «1965 года» теряет
    неразрывный пробел и может разорваться по строкам в узкой ячейке сводки.
    """
    words = [w for w in ASCII_WS_RE.split(text or "") if w]
    if not words:
        return NA
    return " ".join(words[:limit])


def lot_street(obj: dict, ref: dict | None = None) -> str:
    """Полный состав лота для обложки и шапки сводки.

    Приоритет: «street_full» отчёта → «street_full» карточки → «street» отчёта →
    «street» карточки → часть заголовка до запятой.
    """
    ref = ref or {}
    title = str(obj.get("title") or "")
    for value in (
        ref.get("street_full"),
        obj.get("street_full"),
        ref.get("street"),
        obj.get("street"),
        title.split(",")[0],
    ):
        text = str(value).strip() if value else ""
        if text:
            return nb_hyphen(text)
    return NA


BROKER_RE = re.compile(r"Брокер\w*\s*[:—-]\s*(.+)", re.IGNORECASE)


def broker_contact(obj: dict) -> str:
    """Брокер объекта: поле «broker» карточки либо строка «Брокер: …» из разделов."""
    broker = obj.get("broker")
    if isinstance(broker, dict):
        name = str(broker.get("name") or "").strip()
        phone = str(broker.get("phone") or "").strip()
        parts = [part for part in (name, f"тел. {nb_hyphen(phone)}" if phone else "") if part]
        if parts:
            return ", ".join(parts)
    elif isinstance(broker, str) and broker.strip():
        return nb_hyphen(broker.strip())

    for block in obj.get("sections") or []:
        if not isinstance(block, dict):
            continue
        for item in block.get("bullets") or []:
            if isinstance(item, dict):
                text = f"{item.get('lead', '')}: {item.get('text', '')}"
            else:
                text = str(item)
            match = BROKER_RE.search(text)
            contact = match.group(1).strip(" .;") if match else ""
            if contact:
                return nb_hyphen(contact)
    return NA


def listing_url(obj: dict) -> str:
    """Ссылка на публикацию объекта: поле карточки либо строка «Ссылка» из specs."""
    for value in (obj.get("source_url"), obj.get("url"), spec_value(obj, "Ссылка")):
        text = str(value).strip() if value else ""
        if text.startswith(("http://", "https://")):
            return text
    return NA


def listing_bar(obj: dict, m: dict) -> float | None:
    """«BAR по листингу»: рыночная арендная стоимость ÷ запрашиваемая цена.

    Если брокер указал доходность прямо в листинге, она берётся из поля
    «market_bar_listing» карточки; иначе считается от рыночной аренды.
    """
    fin = obj.get("financials") or {}
    published = fin.get("market_bar_listing")
    if isinstance(published, (int, float)) and not isinstance(published, bool):
        return float(published)
    market_rent = fin.get("market_rent_eur_year")
    price = m.get("price")
    if isinstance(market_rent, bool) or not isinstance(market_rent, (int, float)):
        return None
    return market_rent / price if price else None


DATE_RE = re.compile(r"(\d{4})-(\d{2})-(\d{2})")


def report_date(report: dict, report_path: Path) -> str:
    """Дата выгрузки: поле «as_of» отчёта, иначе дата из имени файла отчёта."""
    raw = str(report.get("as_of") or "").strip()
    match = DATE_RE.search(raw) or DATE_RE.search(report_path.stem)
    if match:
        year, month, day = match.groups()
        return f"{day}.{month}.{year}"
    return raw or NA


def fmt_per_sqm(value: object) -> str:
    """Цена или ставка за квадратный метр: «€ 1.583/м²»."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return NA
    return f"{finance.fmt_eur(value)}/м²"


def fmt_years(value: object) -> str:
    """Срок в годах: «2,4 года»."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return NA
    return f"{value:.1f}".replace(".", ",") + f"{NBSP}года"


def photo_items(obj: dict) -> list[tuple[str, str]]:
    """Пары «ссылка — подпись». Старый формат (просто строки) даёт пустые подписи."""
    items: list[tuple[str, str]] = []
    for photo in obj.get("photos", []):
        if isinstance(photo, dict):
            url, caption = photo.get("url", ""), photo.get("caption", "")
        else:
            url, caption = str(photo), ""
        if url:
            items.append((url, caption))
    return items


# --------------------------------------------------------------------------
# изображения объекта
# --------------------------------------------------------------------------
def object_map(obj: dict) -> Path | None:
    """Обзорная карта объекта (кэшируется на диске)."""
    conf = obj.get("map")
    if not isinstance(conf, dict) or not conf:
        return None

    slug = obj.get("slug") or "object"
    lat, lon = conf.get("lat"), conf.get("lon")
    if lat is None or lon is None:
        if not conf.get("query"):  # без координат и адреса карту строить не из чего
            log.warning("%s: в блоке «map» нет ни координат, ни адреса", slug)
            return None
        lat, lon = maps.geocode(conf["query"])
        log.info("%s: геокодирование → %.6f, %.6f", slug, lat, lon)

    # ближайший крупный город, который должен попасть в кадр
    city = conf.get("city")
    city_point = None
    if isinstance(city, dict) and city:
        if city.get("lat") is None or city.get("lon") is None:
            city_point = maps.geocode(city["query"]) if city.get("query") else None
        else:
            city_point = (city["lat"], city["lon"])

    name = f"{slug}-google" + (f"-z{conf['zoom']}" if conf.get("zoom") else "")
    return maps.render(
        lat,
        lon,
        CACHE / "maps" / f"{name}.png",
        city=city_point,
        zoom=conf.get("zoom"),
    )


def object_images(obj: dict, *, skip_map: bool = False) -> tuple[list[Path], list[str]]:
    """Карта-обзор и фотографии объекта вместе с подписями к ним.

    Списки идут строго параллельно, поэтому фотографии загружаются по одной:
    недоступная картинка выпадает вместе со своей подписью.
    """
    images: list[Path] = []
    captions: list[str] = []

    map_path = None if skip_map else object_map(obj)
    if map_path is not None:
        images.append(map_path)
        city = obj.get("city", "")
        captions.append(f"Расположение объекта: {city}" if city else "Расположение объекта")

    for url, caption in photo_items(obj):
        try:
            images.append(media.fetch(url, CACHE / "photos"))
            captions.append(caption)
        except Exception as exc:  # одна недоступная картинка не должна ронять отчёт
            log.warning("не удалось загрузить %s: %s", url, exc)
    return images, captions


def hero_image(obj: dict) -> Path | None:
    """Фото фасада, которое ставится под заголовком объекта."""
    url = obj.get("hero_photo")
    if not url:
        return None
    try:
        return media.fetch(url, CACHE / "photos")
    except Exception as exc:  # без фасадного фото отчёт всё равно собирается
        log.warning("не удалось загрузить фото фасада %s: %s", url, exc)
        return None


# --------------------------------------------------------------------------
# таблица характеристик и плитки KPI
# --------------------------------------------------------------------------
def finance_rows(obj: dict, m: dict) -> list[list[str]]:
    """Строки группы «Финансы»: в карточке их нет, всё считается из «financials»."""
    fin = obj.get("financials") or {}
    if not fin:
        return []

    rent = fin.get("rent_eur_year")
    market_rent = fin.get("market_rent_eur_year")
    basis = fin.get("price_basis") or "k.k."

    price = na(finance.fmt_eur(m.get("price")))
    rows: list[list[str]] = [["Цена", price if price == NA else f"{price} {basis}"]]
    if m.get("acquisition") is not None:
        rows.append(
            [
                "Цена входа (с k.k.)",
                f"≈ {finance.fmt_eur(m.get('acquisition'))} — включая "
                f"overdrachtsbelasting {finance.fmt_pct(finance.OVB_RATE)} "
                f"и расходы по сделке {finance.fmt_pct(finance.CLOSING_EXTRA_RATE)}",
            ]
        )
    if rent is not None:
        rows.append(["Доход от аренды", f"{finance.fmt_eur(rent)} в год"])
    if market_rent is not None:
        # рыночная аренда — это потенциал объекта, поэтому доходность к ней
        # называется «BAR по листингу» и считается от запрашиваемой цены
        value = f"{finance.fmt_eur(market_rent)} в год"
        listing = listing_bar(obj, m)
        if listing is not None:
            value += (
                f" — BAR по листингу (от рыночной аренды) ≈ {finance.fmt_pct(listing)}; "
                "это потенциал, а не текущий доход"
            )
        rows.append(["Рыночная арендная стоимость", value])
    if m.get("rent_per_sqm") is not None:
        rows.append(["Ставка аренды", f"≈ {finance.fmt_eur(m.get('rent_per_sqm'))}/м² в год"])
    if m.get("price_per_sqm") is not None:
        rows.append(["Цена за м²", f"≈ {finance.fmt_eur(m.get('price_per_sqm'))}/м²"])
    if m.get("bar_price") is not None:
        rows.append(["BAR к цене", finance.fmt_pct(m.get("bar_price"))])
    if m.get("bar_acquisition") is not None:
        rows.append(["BAR к цене входа", finance.fmt_pct(m.get("bar_acquisition"))])
    if m.get("nar_acquisition") is not None:
        value = finance.fmt_pct(m.get("nar_acquisition"))
        opex = fin.get("opex_ratio")
        if isinstance(opex, (int, float)) and not isinstance(opex, bool):
            value += f" (при эксплуатационных расходах {finance.fmt_pct(opex)} от аренды)"
        rows.append(["NAR к цене входа", value])

    for scenario in m.get("scenarios") or []:
        value = f"доход {na(finance.fmt_eur(scenario.get('rent')))} в год"
        bar = scenario.get("bar_acquisition")
        if bar is not None:
            # в сценарии обязательно называется база доходности
            value += f" — BAR к цене входа ≈ {finance.fmt_pct(bar)}"
        rows.append([f"Сценарий: {scenario.get('label') or 'альтернативный расчёт'}", value])
    return rows


def object_spec_rows(obj: dict, m: dict) -> list:
    """Таблица характеристик: рассчитанные «Финансы» плюс описательный «Объект»."""
    rows: list = []
    money = finance_rows(obj, m)
    if money:
        rows.append({"group": "Финансы"})
        rows.extend(money)
    # описательные строки берутся как есть; пустое значение показывается как «н/д»
    specs = [
        [str(row[0]), na(row[1] if len(row) > 1 else None)]
        for row in obj.get("specs") or []
        if isinstance(row, (list, tuple)) and row
    ]
    if specs:
        rows.append({"group": "Объект"})
        rows.extend(specs)
    return rows


def object_kpi(obj: dict, m: dict) -> list[tuple[str, str]]:
    """Четыре плитки под заголовком объекта."""
    fin = obj.get("financials") or {}
    return [
        ("Цена", na(finance.fmt_eur(m.get("price")))),
        ("Доход в год", na(finance.fmt_eur(fin.get("rent_eur_year")))),
        ("BAR к цене входа", na(finance.fmt_pct(m.get("bar_acquisition")))),
        ("Площадь", na(finance.fmt_sqm(fin.get("lettable_sqm")))),
    ]


# --------------------------------------------------------------------------
# сводная страница сравнения
# --------------------------------------------------------------------------
def summary_page(report: dict, lots: list[Lot]) -> dict:
    """Таблица сравнения: показатели по строкам, объекты по колонкам."""

    def fin(obj: dict) -> dict:
        return obj.get("financials") or {}

    # подпись строки и то, как берётся значение по конкретному объекту
    fields = [
        ("Цена", lambda obj, m: na(finance.fmt_eur(m.get("price")))),
        ("Цена входа (с k.k.)", lambda obj, m: na(finance.fmt_eur(m.get("acquisition")))),
        ("Площадь", lambda obj, m: na(finance.fmt_sqm(fin(obj).get("lettable_sqm")))),
        ("Цена за м²", lambda obj, m: fmt_per_sqm(m.get("price_per_sqm"))),
        ("Доход в год", lambda obj, m: na(finance.fmt_eur(fin(obj).get("rent_eur_year")))),
        ("Ставка €/м²", lambda obj, m: fmt_per_sqm(m.get("rent_per_sqm"))),
        ("BAR к цене", lambda obj, m: na(finance.fmt_pct(m.get("bar_price")))),
        ("BAR к цене входа", lambda obj, m: na(finance.fmt_pct(m.get("bar_acquisition")))),
        ("NAR к цене входа", lambda obj, m: na(finance.fmt_pct(m.get("nar_acquisition")))),
        ("Арендаторы", lambda obj, m: na(finance.fmt_int(fin(obj).get("tenants")))),
        ("WALT", lambda obj, m: fmt_years(fin(obj).get("walt_years"))),
        ("Год постройки", lambda obj, m: build_year(obj)),
        ("Энергокласс", lambda obj, m: short_value(spec_value(obj, "Энерг"))),
        ("Профиль", lambda obj, m: short_profile(obj.get("positioning"))),
    ]

    headers = ["Показатель"] + [lot.street for lot in lots]
    rows = [
        [label] + [value(lot.obj, lot.metrics) for lot in lots] for label, value in fields
    ]
    return {
        "headers": headers,
        "rows": rows,
        "note": report.get("disclaimer") or DEFAULT_DISCLAIMER,
    }


# --------------------------------------------------------------------------
# закрывающая страница: методика, источники, глоссарий, контакты
# --------------------------------------------------------------------------
def closing_data(report: dict, report_path: Path, lots: list[Lot]) -> dict:
    """Данные закрывающей страницы для docx_render.closing_page(doc, data).

    Формат повторяет разделы карточки объекта, поэтому рендерятся они теми же
    средствами:

        {
          "heading": "Методика, источники и глоссарий",
          "sections": [
            {"heading": "…", "type": "bullets",
             "bullets": [{"lead": "…", "text": "…"} | "…"]},
          ],
          "note": "дисклеймер под страницей",
        }
    """
    ovb = finance.fmt_pct(finance.OVB_RATE)
    closing_extra = finance.fmt_pct(finance.CLOSING_EXTRA_RATE)

    # доля эксплуатационных расходов задана по каждому объекту отдельно
    shares: list[str] = []
    for lot in lots:
        opex = (lot.obj.get("financials") or {}).get("opex_ratio")
        if isinstance(opex, (int, float)) and not isinstance(opex, bool):
            shares.append(f"{lot.street} — {finance.fmt_pct(opex)}")
        else:
            shares.append(
                f"{lot.street} — {finance.fmt_pct(finance.DEFAULT_OPEX_RATIO)} "
                "(значение по умолчанию)"
            )

    method = [
        {"lead": "BAR к цене", "text": "валовой доход от аренды за год ÷ запрашиваемая цена"},
        {"lead": "BAR к цене входа", "text": "валовой доход от аренды за год ÷ цена входа"},
        {
            "lead": "NAR к цене входа",
            "text": "(валовой доход от аренды за год − эксплуатационные расходы "
            "собственника) ÷ цена входа",
        },
        {
            "lead": "BAR по листингу",
            "text": "рыночная арендная стоимость по листингу ÷ запрашиваемая цена; "
            "показывает потенциал объекта, а не текущий доход",
        },
        {
            "lead": "Цена входа (с k.k.)",
            "text": f"запрашиваемая цена + overdrachtsbelasting {ovb} + расходы "
            f"по сделке {closing_extra} (нотариус, кадастр, проверка объекта)",
        },
    ]
    if shares:
        method.append(
            {
                "lead": "Эксплуатационные расходы",
                "text": "доля от валовой аренды задана по каждому объекту отдельно: "
                + "; ".join(shares),
            }
        )
    method.append(
        {
            "lead": "Ставка overdrachtsbelasting",
            "text": f"{ovb} — ставка для нежилой недвижимости, 2026 год "
            "(Belastingdienst); объявленное снижение до 8 % касается только жилья",
        }
    )
    method.append(
        {
            "lead": "Форматы дат",
            "text": "даты договоров, решений и энергетических сертификатов приведены "
            "в нидерландском формате ДД-ММ-ГГГГ, дата подготовки отчёта — ДД.ММ.ГГГГ",
        }
    )
    method.append(
        {
            "lead": "Округление",
            "text": "денежные величины — до целых евро, доходности — до одного знака "
            "после запятой; копейки остаются только в разбивке по арендаторам",
        }
    )

    sources: list[dict] = [
        {
            "lead": "Дата выгрузки данных",
            "text": f"{report_date(report, report_path)} — листинги и расчёты приведены "
            "по состоянию на эту дату",
        }
    ]
    for lot in lots:
        where = f"{lot.street}, {lot.city}" if lot.city else lot.street
        sources.append({"lead": where, "text": f"funda in business — {listing_url(lot.obj)}"})
    sources.append(
        {
            "lead": "Население городов",
            "text": report.get("population_source") or DEFAULT_POPULATION_SOURCE,
        }
    )

    glossary = [{"lead": term, "text": meaning} for term, meaning in GLOSSARY]

    brokers = []
    for lot in lots:
        where = f"{lot.street}, {lot.city}" if lot.city else lot.street
        brokers.append({"lead": where, "text": broker_contact(lot.obj)})

    sections = [
        {"heading": "Как считались показатели", "type": "bullets", "bullets": method},
        {"heading": "Источники и дата данных", "type": "bullets", "bullets": sources},
        {"heading": "Глоссарий", "type": "bullets", "bullets": glossary,
         "layout": "columns"},
    ]
    if brokers:
        sections.append(
            {"heading": "Контакты брокеров", "type": "bullets", "bullets": brokers}
        )

    return {
        "heading": report.get("closing_heading") or "Методика, источники и глоссарий",
        "sections": sections,
        "note": report.get("closing_note") or DEFAULT_CLOSING_NOTE,
    }


# --------------------------------------------------------------------------
# команды
# --------------------------------------------------------------------------
def cmd_build(args: argparse.Namespace) -> int:
    report_path = Path(args.report).resolve()
    report = load_json(report_path)
    base = report_path.parent

    objects: list[tuple[dict, list[Path], dict]] = []
    lots: list[Lot] = []
    for ref in report.get("objects") or []:
        if not isinstance(ref, dict):
            continue
        obj = load_json(base / ref["file"]) if "file" in ref else ref
        metrics = finance.metrics(obj.get("financials"))

        # на обложке и в шапке сводки — полный состав лота
        street = lot_street(obj, ref)
        city = str(ref.get("city") or obj.get("city") or "")
        ref["street"] = street
        ref["street_full"] = street
        ref["city"] = city
        # цена на обложке: из карточки, иначе из расчёта по «financials»
        ref["price_short"] = na(
            ref.get("price_short")
            or obj.get("price_short")
            or finance.fmt_eur(metrics.get("price"))
        )

        images, captions = object_images(obj, skip_map=args.no_map)
        log.info("%s: %d изображений", obj.get("slug") or street, len(images))
        extras = {
            "spec_rows": object_spec_rows(obj, metrics),
            "kpi": object_kpi(obj, metrics),
            "hero": hero_image(obj),
            "captions": captions,
        }
        objects.append((obj, images, extras))
        lots.append(Lot(obj=obj, metrics=metrics, street=street, city=city))

    if lots:
        report["summary"] = summary_page(report, lots)
        # закрывающая страница; готовый блок в файле отчёта имеет приоритет
        report.setdefault("closing", closing_data(report, report_path, lots))

    out_dir = Path(args.out or report.get("output_dir", "output"))
    if not out_dir.is_absolute():
        out_dir = ROOT / out_dir
    name = report.get("filename", report_path.stem)
    docx_path = docx_render.build(report, objects, out_dir / f"{name}.docx")

    if args.pdf:
        try:
            pdf.convert(docx_path, out_dir)
        except Exception as exc:
            log.error("PDF не собран: %s", exc)
            return 1
    print(f"Готово: {docx_path}")
    return 0


def cmd_parse(args: argparse.Namespace) -> int:
    dest = Path(args.out)
    funda_parse.parse_to_file(Path(args.html), dest, args.url)
    data = load_json(dest)
    print(f"Черновик: {dest}")
    print(f"  характеристик: {len(data['kenmerken'])}, фотографий: {data['photo_count']}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("-v", "--verbose", action="store_true", help="подробный лог")
    sub = parser.add_subparsers(dest="command", required=True)

    build = sub.add_parser("build", help="собрать отчёт (DOCX + PDF)")
    build.add_argument("report", help="JSON описания отчёта")
    build.add_argument("-o", "--out", help="каталог для результата")
    build.add_argument("--no-pdf", dest="pdf", action="store_false", help="только DOCX")
    build.add_argument("--no-map", action="store_true", help="без обзорных карт")
    build.set_defaults(func=cmd_build, pdf=True)

    parse = sub.add_parser("parse", help="черновик карточки из сохранённой HTML funda")
    parse.add_argument("html", help="сохранённая страница объекта")
    parse.add_argument("-o", "--out", required=True, help="куда положить JSON")
    parse.add_argument("--url", help="исходный URL объекта")
    parse.set_defaults(func=cmd_parse)

    args = parser.parse_args(argv)
    logging.basicConfig(
        level=logging.INFO if args.verbose else logging.WARNING,
        format="%(levelname)s %(message)s",
    )
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
