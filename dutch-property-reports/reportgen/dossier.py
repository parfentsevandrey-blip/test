"""Схема «dossier» — визуальная, а не текстовая.

Отличие от editorial в ритме: полосы, которые должны производить впечатление,
собираются как изображения (обложка, шмуцтитулы, тёмные полосы, вся графика),
а DOCX держит только текстовые полосы. Приёмы взяты у изданий, на которые
указал заказчик:

* NYT — надстрочная подпись капителью с разрядкой 0,1 em над каждым разделом,
  дисплейная антиква в заголовке, тесный интерлиньяж;
* Vogue — Didot (здесь Playfair Display) в очень крупном кегле поверх кадра,
  золото как единственный акцент, чернила вместо чёрного;
* Bloomberg — тёмные полосы, крупные числа и график как герой полосы.
"""

from __future__ import annotations

import logging
from pathlib import Path

from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Mm, Pt

from . import charts, style_editorial as S, visuals
from .docx_render import cell_margins, cell_shading, table_borders
from .editorial import (
    Flow,
    panel,
    _clean,
    _opener_image,
    bleed_image,
    crop_to_ratio,
    fixed_layout,
    grid,
    kpi_band,
    listing,
    micro,
    page_break,
    par,
    photo,
    rule,
    running_footer,
    setup_document,
    spec_sheet,
    txt,
    _page_setup,
)

log = logging.getLogger(__name__)

DISPLAY = "Playfair Display"
DISPLAY_MED = "Playfair Display Medium"


# --------------------------------------------------------------------------
# элементы полосы
# --------------------------------------------------------------------------
def full_bleed(doc, image: Path) -> None:
    """Полоса-изображение: кадр во весь лист, без полей и без текста поверх."""
    bleed_image(doc, image, x_mm=0, y_mm=0, w_mm=S.PAGE_W_MM, h_mm=S.PAGE_H_MM)


def display_title(doc, text: str, *, size: float = 30.0, after: float = 8.0):
    paragraph = par(doc, after=after, lead=size * 1.08)
    txt(paragraph, text, font=DISPLAY, size=size, color=S.INK, tracking=-6)
    return paragraph


def lead_paragraph(doc, text: str, *, after: float = 12.0):
    paragraph = par(doc, after=after, lead=S.LH_LEAD)
    txt(paragraph, text, font=S.SERIF_LIGHT, size=S.FS_LEAD, color=S.INK)
    return paragraph


def pull_quote(doc, text: str, *, before: float = 13.0, after: float = 13.0):
    """Врезка-цитата: главный тезис полосы, вынесенный крупно между линейками."""
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, before=before, after=7)
    paragraph = par(doc, after=7, lead=22, right=40)
    txt(paragraph, text, font=DISPLAY, size=17, color=S.INK, tracking=-4)
    rule(doc, color=S.RULE, size=S.SZ_HAIRLINE, after=after)


def figure(doc, image: Path, *, width_mm: float = S.CONTENT_W_MM, after: float = 0.0):
    """График во всю ширину полосы; фон рисунка совпадает с бумагой."""
    photo(doc, image, width_mm=width_mm, max_h=200.0)
    if after:
        par(doc, after=0, lead=after)


def prose_with_initial(doc, texts: list[str], heading: str) -> None:
    """Проза с крупной буквицей в боковой колонке.

    Настоящая буквица в DOCX требует обтекания через w:framePr и ведёт себя в
    LibreOffice непредсказуемо, поэтому инициал вынесен на поля — приём,
    который в журнальной вёрстке используется не реже.
    """
    rail, main = grid(doc)
    _clean(rail)
    _clean(main)
    micro(rail, heading, after=6)
    if texts:
        initial = par(rail, after=0, lead=40)
        txt(initial, texts[0][:1], font=DISPLAY, size=38, color=S.BRASS, tracking=-20)
    for index, text in enumerate(texts):
        body = par(main, after=0 if index == len(texts) - 1 else 7,
                   lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
        txt(body, text, size=S.FS_BODY, color=S.BODY)


def stat_block(doc, value: str, label: str, note: str) -> None:
    """Крупное число как самостоятельный элемент полосы — приём Bloomberg."""
    micro(doc, label, after=2)
    paragraph = par(doc, after=2, lead=52)
    txt(paragraph, value, font=DISPLAY, size=46, color=S.INK, tracking=-14)
    caption = par(doc, after=0, lead=S.LH_SMALL)
    txt(caption, note, size=S.FS_CAPTION, color=S.MUTED)


# --------------------------------------------------------------------------
# полосы отчёта
# --------------------------------------------------------------------------
def overview(doc, report: dict, objects: list, assets: Path) -> None:
    micro(doc, "Обзор подборки", after=7)
    display_title(doc, report.get("overview_title", "Три объекта"), size=34, after=10)
    if report.get("intro"):
        lead_paragraph(doc, report["intro"], after=14)

    rows, points = [], []
    for obj, _ in objects:
        summary = obj.get("summary", {})
        rows.append((
            obj["street"],
            _percent(summary.get("yield")),
            f"{obj.get('city', '')} · {obj.get('price_short', '')}",
        ))
        if summary.get("price_per_m2") and summary.get("area_value"):
            points.append((obj["street"], float(summary["price_per_m2"]),
                           _percent(summary.get("yield")), float(summary["area_value"])))

    average = report.get("average_yield")
    figure(doc, charts.yield_bars(assets / "yield.png", rows, average=average,
                                  highlight=rows[0][0] if rows else None,
                                  row_mm=19.0), after=15)
    if report.get("criteria"):
        rail, main = grid(doc)
        _clean(rail)
        _clean(main)
        micro(rail, "Что искали", after=0)
        listing(main, report["criteria"])
    if report.get("rejected"):
        par(doc, after=0, lead=12)
        rail, main = grid(doc)
        _clean(rail)
        _clean(main)
        micro(rail, "Что не прошло", after=0)
        body = par(main, after=0, lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
        txt(body, report["rejected"], size=S.FS_BODY, color=S.BODY)
    return points


def _percent(value: str | None) -> float:
    if not value:
        return 0.0
    return float(str(value).replace("%", "").replace(",", ".").strip())


def comparison(doc, objects: list) -> None:
    micro(doc, "Сравнение", after=5)
    header = ("", "Объект", "Цена", "Доход / год", "BAR", "Площадь", "Трасса")
    widths = (10.0, 47.0, 24.0, 25.0, 13.0, 21.0, 30.0)
    table = doc.add_table(rows=len(objects) + 1, cols=len(header))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {"top": (S.INK, S.SZ_RULE), "bottom": (S.INK, S.SZ_RULE),
                          "insideH": (S.RULE, S.SZ_HAIRLINE)})
    fixed_layout(table, list(widths))
    for cell, label, width in zip(table.rows[0].cells, header, widths):
        cell.width = Mm(width)
        cell_margins(cell, top=5, bottom=5, left=0, right=4)
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        paragraph.paragraph_format.line_spacing = Pt(S.FS_MICRO + 2)
        txt(paragraph, label, font=S.SANS_MEDIUM, size=S.FS_MICRO, color=S.MUTED,
            tracking=S.TR_H3, caps=True)

    for index, (obj, _) in enumerate(objects, start=1):
        row = table.rows[index]
        summary = obj.get("summary", {})
        values = (f"{index:02d}", obj["street"], obj.get("price_short", ""),
                  summary.get("rent", "—"), summary.get("yield", "—"),
                  summary.get("area", "—"), summary.get("road", "—"))
        for position_index, (cell, value, width) in enumerate(zip(row.cells, values, widths)):
            cell.width = Mm(width)
            cell_margins(cell, top=6, bottom=6, left=0, right=4)
            if index % 2 == 0:
                cell_shading(cell, S.PANEL_SOFT)
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            paragraph.paragraph_format.line_spacing = Pt(S.LH_SMALL)
            if position_index == 0:
                txt(paragraph, value, font=DISPLAY, size=S.FS_SMALL + 3, color=S.BRASS)
            elif position_index == 1:
                txt(paragraph, value, font=S.SANS_MEDIUM, size=S.FS_SMALL, color=S.INK)
                city = cell.add_paragraph()
                city.paragraph_format.space_after = Pt(0)
                city.paragraph_format.line_spacing = Pt(S.LH_SMALL)
                txt(city, obj.get("city", ""), size=S.FS_CAPTION, color=S.MUTED)
            else:
                txt(paragraph, value, size=S.FS_SMALL, color=S.BODY)


def object_analysis(doc, index: int, obj: dict, assets: Path) -> None:
    micro(doc, f"Объект {index:02d} · {obj.get('city', '')}", after=7)
    display_title(doc, obj["title"], size=30, after=9)
    if obj.get("lead"):
        lead_paragraph(doc, obj["lead"], after=13)
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, after=12)

    summary = obj.get("summary", {})
    rail, main = grid(doc, rail_mm=46.0)
    _clean(rail)
    _clean(main)
    micro(rail, "Доходность", after=2)
    value = par(rail, after=2, lead=46)
    txt(value, summary.get("yield", "—"), font=DISPLAY, size=40, color=S.INK, tracking=-12)
    note = par(rail, after=0, lead=S.LH_SMALL)
    txt(note, "валовая начальная,\nк запрашиваемой цене", size=S.FS_CAPTION, color=S.MUTED)

    for block in obj["sections"]:
        if block.get("type") == "paragraphs":
            for position_index, text in enumerate(block["paragraphs"]):
                body = par(main, after=0 if position_index == len(block["paragraphs"]) - 1 else 7,
                           lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
                txt(body, text, size=S.FS_BODY, color=S.BODY)
            break

    if obj.get("pull"):
        pull_quote(doc, obj["pull"])

    drawn = obj.get("charts") or {}
    slug = obj["slug"]
    if drawn.get("income"):
        parts = [(name, float(value)) for name, value in drawn["income"]]
        figure(doc, charts.split_bar(assets / f"{slug}-income.png", parts,
                                     title="Структура арендного дохода", unit="€/год",
                                     total_note=drawn.get("income_note", "")), after=11)
    if drawn.get("areas"):
        parts = [(name, float(value)) for name, value in drawn["areas"]]
        figure(doc, charts.split_bar(assets / f"{slug}-areas.png", parts,
                                     title=drawn.get("areas_title", "Состав площадей"),
                                     unit="м²", total_note=drawn.get("areas_note", "")))


def object_data(doc, obj: dict, flow: Flow, assets: Path) -> None:
    flow.new_page()
    micro(doc, "Данные объекта", after=6)
    display_title(doc, obj["street"], size=19, after=7)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=10)

    for block in obj["sections"]:
        if block.get("type") == "callout":
            texts = block["paragraphs"]
            flow.take(flow.estimate(texts, extra_mm=26) * 0.80 + 20)
            panel(doc, block.get("heading", "О городе и районе"), block["title"], texts)
            par(doc, after=0, lead=12)
            break

    spec_sheet(doc, obj["specs"], flow)


def object_review(doc, obj: dict, flow: Flow) -> None:
    first = True
    for block in obj["sections"]:
        if block.get("type") not in (None, "bullets"):
            continue
        items = block.get("bullets") or []
        if not items:
            continue
        if first:
            flow.new_page()
            micro(doc, "Разбор", after=6)
            display_title(doc, "Что стоит за цифрами", size=19, after=7)
            rule(doc, color=S.INK, size=S.SZ_RULE, after=0)
            first = False
        flow.fit(flow.estimate(items, extra_mm=11))
        par(doc, after=0, lead=13)
        rail, main = grid(doc)
        _clean(rail)
        _clean(main)
        micro(rail, block.get("heading", ""), after=0)
        listing(main, items)


def photo_pages(doc, images: list[Path], flow: Flow, cache: Path) -> None:
    if not images:
        return
    flow.new_page()
    micro(doc, "Объект в кадре", after=6)
    budget = S.PAGE_H_MM - S.MARGIN_TOP_MM - S.MARGIN_BOTTOM_MM - 4.0
    height = S.CONTENT_W_MM / S.PHOTO_RATIO
    used = 8.0
    for index, path in enumerate(images):
        if index == 0:
            _, map_height = photo(doc, path)
            caption = par(doc, before=4, after=0, lead=S.LH_SMALL)
            txt(caption, "Расположение объекта · картографические данные © Google",
                size=S.FS_CAPTION, color=S.MUTED)
            used += map_height + 8
            continue
        if used + S.PHOTO_GAP_MM + height > budget:
            page_break(doc)
            used = 0.0
        else:
            par(doc, after=0, lead=S.PHOTO_GAP_MM * 72 / 25.4)
            used += S.PHOTO_GAP_MM
        photo(doc, crop_to_ratio(path, S.PHOTO_RATIO, cache), max_h=height)
        used += height
    flow.used = used


def colophon(doc, report: dict) -> None:
    micro(doc, "Источники и оговорки", after=7)
    display_title(doc, "Как читать эту подборку", size=24, after=9)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=11)
    for label, text in report.get("colophon", []):
        par(doc, after=0, lead=11)
        rail, main = grid(doc)
        _clean(rail)
        _clean(main)
        micro(rail, label, after=0)
        body = par(main, after=0, lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
        txt(body, text, size=S.FS_BODY, color=S.BODY)


# --------------------------------------------------------------------------
# сборка
# --------------------------------------------------------------------------
def build(report: dict, objects: list[tuple[dict, list[Path]]], dest: Path) -> Path:
    doc = setup_document()
    root = dest.parent.parent
    assets = root / ".cache" / "visuals"
    crops = root / ".cache" / "crops"

    first_obj, first_images = objects[0]
    cover_photo = first_images[1] if len(first_images) > 1 else None
    if cover_photo:
        full_bleed(doc, visuals.cover(
            assets / "cover.jpg", cover_photo,
            kicker=report.get("eyebrow", "Инвестиционная подборка · Нидерланды"),
            title=report.get("cover_title", report["title"]),
            subtitle=report["subtitle"],
            meta_left=report.get("source_line", ""),
            meta_right=f"{len(objects)} объекта",
        ))

    body = doc.add_section(WD_SECTION.NEW_PAGE)
    _page_setup(body)
    running_footer(body, report.get("running_title", report["title"]))

    if report.get("statement"):
        full_bleed(doc, visuals.statement(
            assets / "statement.jpg",
            kicker=report["statement"].get("kicker", "Подборка в трёх числах"),
            text=report["statement"]["text"],
            figures=[tuple(row) for row in report["statement"]["figures"]],
        ))
        page_break(doc)

    points = overview(doc, report, objects, assets)
    page_break(doc)
    comparison(doc, objects)
    if len(points) >= 2:
        par(doc, after=0, lead=14)
        figure(doc, charts.position(assets / "position.png", points, height_mm=96), after=13)
    flow = Flow(doc)
    for index, (obj, images) in enumerate(objects, start=1):
        opener_photo = _opener_image(images, index)
        flow.new_page()
        if opener_photo:
            full_bleed(doc, visuals.opener(
                assets / f"opener-{index}.jpg", opener_photo,
                ordinal=f"{index:02d}", city=obj.get("city", ""),
                title=obj["title"], subtitle=obj["subtitle"],
                kpi=[(label, value) for label, value, _ in obj.get("kpi", [])[:4]],
            ))
        flow.new_page()
        object_analysis(doc, index, obj, assets)
        object_data(doc, obj, flow, assets)
        object_review(doc, obj, flow)
        photo_pages(doc, images, flow, crops)

    flow.new_page()
    colophon(doc, report)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX (dossier): %s", dest)
    return dest
