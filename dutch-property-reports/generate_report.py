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

from reportgen import docx_render, funda_parse, maps, media, pdf, registry

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / ".cache"

log = logging.getLogger("report")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def object_images(obj: dict, *, skip_map: bool = False,
                  aerial: bool = True) -> list[Path]:
    """Карта-обзор, аэрофотоснимок участка и фотографии (всё кэшируется).

    Аэрофотоснимок добавляется потому, что листингов с большой галереей мало:
    funda отдаёт в HTML пять кадров, остальное подгружает скриптом. Вид
    участка сверху — материал, который не зависит от щедрости брокера, и в
    отчёте он на своём месте: показывает пятно застройки, двор и подъезды.
    """
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
        if aerial and conf.get("aerial", True):
            images.append(
                maps.render(
                    lat,
                    lon,
                    CACHE / "maps" / f"{obj['slug']}-aerial.png",
                    zoom=conf.get("aerial_zoom", 18),
                    style="aerial",
                )
            )
    images.extend(media.fetch_all(obj.get("photos", []), CACHE / "photos"))
    return images


def cmd_build(args: argparse.Namespace) -> int:
    report_path = Path(args.report).resolve()
    report = load_json(report_path)
    base = report_path.parent

    cards = [
        load_json(base / ref["file"]) if "file" in ref else ref
        for ref in report["objects"]
    ]
    repeats = registry.repeats(base, report_path, cards)
    for line in repeats:
        print(f"ВНИМАНИЕ: повтор объекта — {line}", file=sys.stderr)

    objects: list[tuple[dict, list[Path]]] = []
    for ref, obj in zip(report["objects"], cards):
        ref.setdefault("street", obj.get("street", obj["title"].split(",")[0]))
        ref.setdefault("city", obj.get("city", ""))
        ref.setdefault("price_short", obj.get("price_short", ""))
        # аэрофотоснимок кладётся только в схему по умолчанию: ранние отчёты
        # собраны без него, и пересборка не должна их менять
        images = object_images(obj, skip_map=args.no_map,
                               aerial=report.get("layout", "dossier") == "dossier")
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

    # отчёт собран — объекты уходят в реестр, чтобы не попасть в следующую подборку
    registry.record(base, report_path, cards)
    print(f"Готово: {docx_path}")
    return 0


def cmd_registry(args: argparse.Namespace) -> int:
    data_dir = Path(args.data or ROOT / "data").resolve()
    if args.rebuild:
        destination, count = registry.rebuild(data_dir)
        print(f"Реестр пересобран: {destination} — объектов: {count}")
        return 0
    entries = registry.load(data_dir)["objects"]
    for entry in entries:
        where = ", ".join(entry.get("reports", [])) or entry.get("source", "—")
        print(f"{entry['slug']:44s} {entry.get('city', ''):16s} "
              f"{entry.get('price', ''):14s} {where}")
    print(f"Всего объектов: {len(entries)}")
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

    reg = sub.add_parser("registry", help="реестр показанных объектов")
    reg.add_argument("--rebuild", action="store_true",
                     help="пересобрать по отчётам каталога")
    reg.add_argument("--data", help="каталог данных (по умолчанию data/)")
    reg.set_defaults(func=cmd_registry)

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
