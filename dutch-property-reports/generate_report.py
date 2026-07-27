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
import sys
from pathlib import Path

from reportgen import docx_render, finance, funda_parse, maps, media, pdf

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".cache"

NBSP = "\u00A0"  # неразрывный пробел между числом и единицей измерения
NA = "н/д"       # значение, которого нет в карточке

# Дисклеймер сводной страницы, если в файле отчёта нет поля «disclaimer»
DEFAULT_DISCLAIMER = (
    "Показатели рассчитаны по данным листингов и носят ориентировочный характер: "
    f"цена входа включает overdrachtsbelasting {finance.fmt_pct(finance.OVB_RATE)} "
    f"и сопутствующие расходы по сделке {finance.fmt_pct(finance.CLOSING_EXTRA_RATE)}, "
    "NAR посчитан при доле эксплуатационных расходов, указанной в карточке объекта. "
    "Перед сделкой цифры подлежат проверке по документам продавца."
)

log = logging.getLogger("report")


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
    if not conf:
        return None

    lat, lon = conf.get("lat"), conf.get("lon")
    if lat is None or lon is None:
        lat, lon = maps.geocode(conf["query"])
        log.info("%s: геокодирование → %.6f, %.6f", obj["slug"], lat, lon)

    # ближайший крупный город, который должен попасть в кадр
    city = conf.get("city")
    city_point = None
    if city:
        if city.get("lat") is None or city.get("lon") is None:
            city_point = maps.geocode(city["query"])
        else:
            city_point = (city["lat"], city["lon"])

    name = f"{obj['slug']}-google" + (f"-z{conf['zoom']}" if conf.get("zoom") else "")
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

    rows: list[list[str]] = [["Цена", f"{finance.fmt_eur(m.get('price'))} {basis}"]]
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
        value = f"{finance.fmt_eur(market_rent)} в год"
        if m.get("market_bar_acquisition") is not None:
            value += (
                f" (BAR к цене входа ≈ {finance.fmt_pct(m.get('market_bar_acquisition'))})"
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
        value = f"доход {finance.fmt_eur(scenario.get('rent'))} в год"
        if scenario.get("bar_acquisition") is not None:
            value += f", BAR ≈ {finance.fmt_pct(scenario.get('bar_acquisition'))}"
        rows.append([f"Сценарий: {scenario.get('label') or 'альтернативный расчёт'}", value])
    return rows


def object_spec_rows(obj: dict, m: dict) -> list:
    """Таблица характеристик: рассчитанные «Финансы» плюс описательный «Объект»."""
    rows: list = []
    money = finance_rows(obj, m)
    if money:
        rows.append({"group": "Финансы"})
        rows.extend(money)
    specs = list(obj.get("specs", []))
    if specs:
        rows.append({"group": "Объект"})
        rows.extend(specs)
    return rows


def object_kpi(obj: dict, m: dict) -> list[tuple[str, str]]:
    """Четыре плитки под заголовком объекта."""
    fin = obj.get("financials") or {}
    return [
        ("Цена", finance.fmt_eur(m.get("price"))),
        ("Доход в год", finance.fmt_eur(fin.get("rent_eur_year"))),
        ("BAR к цене входа", finance.fmt_pct(m.get("bar_acquisition"))),
        ("Площадь", finance.fmt_sqm(fin.get("lettable_sqm"))),
    ]


# --------------------------------------------------------------------------
# сводная страница сравнения
# --------------------------------------------------------------------------
def summary_page(report: dict, cards: list[tuple[dict, dict]]) -> dict:
    """Таблица сравнения: показатели по строкам, объекты по колонкам."""

    def fin(obj: dict) -> dict:
        return obj.get("financials") or {}

    # подпись строки и то, как берётся значение по конкретному объекту
    fields = [
        ("Цена", lambda obj, m: finance.fmt_eur(m.get("price"))),
        ("Цена входа (k.k.)", lambda obj, m: finance.fmt_eur(m.get("acquisition"))),
        ("Площадь", lambda obj, m: finance.fmt_sqm(fin(obj).get("lettable_sqm"))),
        ("Цена за м²", lambda obj, m: fmt_per_sqm(m.get("price_per_sqm"))),
        ("Доход в год", lambda obj, m: finance.fmt_eur(fin(obj).get("rent_eur_year"))),
        ("Ставка €/м²", lambda obj, m: fmt_per_sqm(m.get("rent_per_sqm"))),
        ("BAR к цене входа", lambda obj, m: finance.fmt_pct(m.get("bar_acquisition"))),
        ("NAR", lambda obj, m: finance.fmt_pct(m.get("nar_acquisition"))),
        ("Арендаторы", lambda obj, m: finance.fmt_int(fin(obj).get("tenants"))),
        ("WALT", lambda obj, m: fmt_years(fin(obj).get("walt_years"))),
        ("Год постройки", lambda obj, m: short_value(spec_value(obj, "Год постройки"))),
        ("Энергокласс", lambda obj, m: short_value(spec_value(obj, "Энерг"))),
        ("Профиль", lambda obj, m: obj.get("positioning") or NA),
    ]

    headers = ["Показатель"] + [
        obj.get("street") or obj["title"].split(",")[0] for obj, _ in cards
    ]
    rows = [[label] + [value(obj, m) for obj, m in cards] for label, value in fields]
    return {
        "headers": headers,
        "rows": rows,
        "note": report.get("disclaimer") or DEFAULT_DISCLAIMER,
    }


# --------------------------------------------------------------------------
# команды
# --------------------------------------------------------------------------
def cmd_build(args: argparse.Namespace) -> int:
    report_path = Path(args.report).resolve()
    report = load_json(report_path)
    base = report_path.parent

    objects: list[tuple[dict, list[Path], dict]] = []
    cards: list[tuple[dict, dict]] = []
    for ref in report["objects"]:
        obj = load_json(base / ref["file"]) if "file" in ref else ref
        metrics = finance.metrics(obj.get("financials"))

        ref.setdefault("street", obj.get("street", obj["title"].split(",")[0]))
        ref.setdefault("city", obj.get("city", ""))
        # цена на обложке: из карточки, иначе из расчёта по «financials»
        ref.setdefault(
            "price_short", obj.get("price_short") or finance.fmt_eur(metrics.get("price"))
        )

        images, captions = object_images(obj, skip_map=args.no_map)
        log.info("%s: %d изображений", obj["slug"], len(images))
        extras = {
            "spec_rows": object_spec_rows(obj, metrics),
            "kpi": object_kpi(obj, metrics),
            "hero": hero_image(obj),
            "captions": captions,
        }
        objects.append((obj, images, extras))
        cards.append((obj, metrics))

    if cards:
        report["summary"] = summary_page(report, cards)

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
