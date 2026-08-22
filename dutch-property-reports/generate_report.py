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

from reportgen import docx_render, funda_parse, maps, media, pdf

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".cache"

log = logging.getLogger("report")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def object_images(obj: dict, *, skip_map: bool = False) -> list[Path]:
    """Карта-обзор + фотографии объекта (всё кэшируется на диске)."""
    images: list[Path] = []
    if not skip_map and obj.get("map"):
        conf = obj["map"]
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
        images.append(
            maps.render(
                lat,
                lon,
                CACHE / "maps" / f"{name}.png",
                city=city_point,
                zoom=conf.get("zoom"),
            )
        )
    images.extend(media.fetch_all(obj.get("photos", []), CACHE / "photos"))
    return images


SEEN_OBJECTS = "seen-objects.json"


def warn_repeated_objects(
    report_path: Path, report: dict, objects: list[dict]
) -> list[str]:
    """Объекты, которые заказчик уже видел.

    Проверяются два источника: другие отчёты этого каталога (по имени файла
    карточки) и список seen-objects.json — объекты, ушедшие заказчику любым
    другим путём (по slug). Один и тот же объект в двух подборках — почти
    всегда ошибка, поэтому сборка не падает, но повторы печатаются заметно.
    """
    refs = report.get("objects", [])
    used_files = {ref.get("file") for ref in refs if ref.get("file")}
    used_slugs = {obj.get("slug") for obj in objects if obj.get("slug")}
    repeats: list[str] = []

    # тот же набор объектов в другом оформлении — не повтор, а вариант вёрстки
    variant_of = report.get("variant_of")
    for other in sorted(report_path.parent.glob("report-*.json")):
        if other.resolve() == report_path.resolve() or other.name == variant_of:
            continue
        try:
            data = json.loads(other.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if data.get("variant_of") == report_path.name:
            continue                       # связь варианта работает в обе стороны
        for ref in data.get("objects", []):
            name = ref.get("file")
            if name in used_files:
                repeats.append(f"{name} — уже в отчёте {other.name}")

    seen_path = report_path.parent / SEEN_OBJECTS
    try:
        seen = json.loads(seen_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return repeats
    for entry in seen.get("objects", []):
        if entry.get("slug") in used_slugs:
            source = entry.get("source", SEEN_OBJECTS)
            repeats.append(f"{entry['slug']} — {source}")
    return repeats


def cmd_build(args: argparse.Namespace) -> int:
    report_path = Path(args.report).resolve()
    report = load_json(report_path)
    base = report_path.parent

    cards = [
        load_json(base / ref["file"]) if "file" in ref else ref
        for ref in report["objects"]
    ]
    repeats = warn_repeated_objects(report_path, report, cards)
    for line in repeats:
        print(f"ВНИМАНИЕ: повтор объекта — {line}", file=sys.stderr)

    objects: list[tuple[dict, list[Path]]] = []
    for ref, obj in zip(report["objects"], cards):
        ref.setdefault("street", obj.get("street", obj["title"].split(",")[0]))
        ref.setdefault("city", obj.get("city", ""))
        ref.setdefault("price_short", obj.get("price_short", ""))
        images = object_images(obj, skip_map=args.no_map)
        log.info("%s: %d изображений", obj["slug"], len(images))
        objects.append((obj, images))

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
