"""Схема «dossier» — визуальная, с коротким составом.

Состав задан заказчиком: простая обложка со списком объектов и ценами;
на каждый объект — шмуцтитул, полоса карточек с ключевыми характеристиками,
описание объекта, описание локации и фотографии. Никакого свода подборки,
никакого разбора и никакого колофона.

Полосы, которые должны производить впечатление, собираются как изображения
(модуль visuals): DOCX не умеет ни положить текст на кадр, ни скруглить углы
иллюстрации, ни дать ей тень. Приёмы взяты у изданий, на которые указал
заказчик: дисплейная антиква поверх кадра и разрядка надстрочных подписей
0,1 em (NYT), Didot в крупном кегле и золото как единственный акцент (Vogue).
"""

from __future__ import annotations

import logging
from pathlib import Path

from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Mm, Pt

from . import style_editorial as S, visuals
from .docx_render import _el, _insert_ordered, cell_margins, cell_shading, table_borders
from .editorial import (
    Flow,
    _clean,
    _opener_image,
    _page_setup,
    bleed_image,
    fixed_layout,
    grid,
    micro,
    page_break,
    par,
    photo,
    rule,
    running_footer,
    setup_document,
    txt,
)

log = logging.getLogger(__name__)

DISPLAY = "Playfair Display"
CARD_COLUMNS = 3


# --------------------------------------------------------------------------
# элементы полосы
# --------------------------------------------------------------------------
def full_bleed(doc, image: Path) -> None:
    bleed_image(doc, image, x_mm=0, y_mm=0, w_mm=S.PAGE_W_MM, h_mm=S.PAGE_H_MM)


def display_title(doc, text: str, *, size: float = 30.0, after: float = 8.0):
    paragraph = par(doc, after=after, lead=size * 1.08)
    txt(paragraph, text, font=DISPLAY, size=size, color=S.INK, tracking=-6)
    return paragraph


def lead_paragraph(doc, text: str, *, after: float = 12.0):
    paragraph = par(doc, after=after, lead=S.LH_LEAD)
    txt(paragraph, text, font=S.SERIF_LIGHT, size=S.FS_LEAD, color=S.INK)
    return paragraph


def pull_quote(doc, text: str, *, before: float = 12.0, after: float = 12.0):
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, before=before, after=7)
    paragraph = par(doc, after=7, lead=21, right=34)
    txt(paragraph, text, font=DISPLAY, size=16, color=S.INK, tracking=-4)
    rule(doc, color=S.RULE, size=S.SZ_HAIRLINE, after=after)


GAP_MM = 5.0
CARD_MM = (S.CONTENT_W_MM - GAP_MM * (CARD_COLUMNS - 1)) / CARD_COLUMNS


def card_row(doc, cards: list[list[str]]) -> None:
    """Одна строка карточек.

    Каждая строка — отдельная таблица с пустыми колонками-разделителями:
    w:tblCellSpacing в LibreOffice не даёт зазора, и плашки слипаются в
    сплошную таблицу вместо карточек.
    """
    widths: list[float] = []
    for position in range(CARD_COLUMNS):
        if position:
            widths.append(GAP_MM)
        widths.append(CARD_MM)
    table = doc.add_table(rows=1, cols=len(widths))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {})
    fixed_layout(table, widths)

    for position, cell in enumerate(table.rows[0].cells):
        cell.width = Mm(widths[position])
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        paragraph.paragraph_format.line_spacing = Pt(1)
        if position % 2:                       # колонка-разделитель
            cell_margins(cell, top=0, bottom=0, left=0, right=0)
            continue
        index = position // 2
        if index >= len(cards):
            continue
        label, value, note = (list(cards[index]) + ["", "", ""])[:3]
        cell_shading(cell, S.PANEL)
        cell_margins(cell, top=13, bottom=14, left=9, right=8)

        paragraph.paragraph_format.space_after = Pt(4)
        paragraph.paragraph_format.line_spacing = Pt(S.FS_MICRO + 2)
        txt(paragraph, label, font=S.SANS_MEDIUM, size=S.FS_MICRO, color=S.BRASS,
            tracking=S.TR_MICRO, caps=True)

        big = cell.add_paragraph()
        big.paragraph_format.space_after = Pt(3)
        big.paragraph_format.line_spacing = Pt(17)
        size = 15.5 if len(value) <= 14 else (12.5 if len(value) <= 22 else 11.0)
        txt(big, value, font=DISPLAY, size=size, color=S.INK, tracking=-4)

        if note:
            caption = cell.add_paragraph()
            caption.paragraph_format.space_after = Pt(0)
            caption.paragraph_format.line_spacing = Pt(9.6)
            txt(caption, note, size=7.0, color=S.MUTED)


def card_grid(doc, cards: list[list[str]]) -> None:
    """Ключевые характеристики карточками, а не строками таблицы.

    Карточка держит три уровня: подпись капителью, значение крупно и уточнение
    подписью — так на одной полосе умещается пятнадцать показателей и полоса
    при этом не выглядит выгрузкой из базы.
    """
    for start in range(0, len(cards), CARD_COLUMNS):
        if start:
            par(doc, after=0, lead=GAP_MM * 72 / 25.4)
        card_row(doc, cards[start:start + CARD_COLUMNS])


def framed_photo(doc, source: Path, cache: Path, *, width_mm: float,
                 ratio: float = S.PHOTO_RATIO):
    """Кадр со скруглением и тенью — на полосу ложится уже готовым файлом."""
    prepared = visuals.rounded_photo(
        cache / f"{source.stem}-{int(width_mm)}.jpg", source,
        width_mm=width_mm, ratio=ratio)
    return photo(doc, prepared, width_mm=width_mm, max_h=260.0)


# --------------------------------------------------------------------------
# полосы объекта
# --------------------------------------------------------------------------
def object_cards(doc, index: int, obj: dict) -> None:
    micro(doc, f"Объект {index:02d} · {obj.get('city', '')}", after=7)
    display_title(doc, obj["title"], size=29, after=9)
    if obj.get("lead"):
        lead_paragraph(doc, obj["lead"], after=11)
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, after=12)
    cards = obj.get("cards") or [[label, value, ""] for label, value in obj["specs"][:15]]
    card_grid(doc, cards)


def object_description(doc, obj: dict, cache: Path, images: list[Path]) -> None:
    micro(doc, "Описание объекта", after=7)
    display_title(doc, obj["street"], size=21, after=8)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=10)

    for block in obj["sections"]:
        if block.get("type") != "paragraphs":
            continue
        texts = block["paragraphs"]
        for position, text in enumerate(texts):
            body = par(doc, after=0 if position == len(texts) - 1 else 7,
                       lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
            txt(body, text, size=S.FS_BODY, color=S.BODY)
        break

    if obj.get("pull"):
        pull_quote(doc, obj["pull"])
    if len(images) > 1:
        par(doc, after=0, lead=3)
        framed_photo(doc, images[1], cache, width_mm=S.CONTENT_W_MM, ratio=21 / 9)


def object_location(doc, obj: dict, cache: Path, images: list[Path]) -> None:
    block = next((b for b in obj["sections"] if b.get("type") == "callout"), None)
    if block is None:
        return
    micro(doc, "Локация", after=7)
    display_title(doc, block["title"], size=21, after=8)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=10)

    texts = block["paragraphs"]
    for position, text in enumerate(texts):
        body = par(doc, after=0 if position == len(texts) - 1 else 7,
                   lead=S.LH_BODY, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
        txt(body, text, size=S.FS_BODY, color=S.BODY)

    if images:
        par(doc, after=0, lead=13)
        framed_photo(doc, images[0], cache, width_mm=S.CONTENT_W_MM, ratio=16 / 9)
        caption = par(doc, before=3, after=0, lead=S.LH_SMALL)
        txt(caption, "Расположение объекта · картографические данные © Google",
            size=S.FS_CAPTION, color=S.MUTED)


def photo_pages(doc, images: list[Path], flow: Flow, cache: Path) -> None:
    gallery = images[2:] if len(images) > 2 else []
    if not gallery:
        return
    flow.new_page()
    micro(doc, "Объект в кадре", after=7)

    budget = S.PAGE_H_MM - S.MARGIN_TOP_MM - S.MARGIN_BOTTOM_MM - 4.0
    ratio = 16 / 9
    height = (S.CONTENT_W_MM - 8.0) / ratio + 8.0
    used = 9.0
    for path in gallery:
        if used + S.PHOTO_GAP_MM + height > budget:
            page_break(doc)
            used = 0.0
        else:
            par(doc, after=0, lead=S.PHOTO_GAP_MM * 72 / 25.4)
            used += S.PHOTO_GAP_MM
        framed_photo(doc, path, cache, width_mm=S.CONTENT_W_MM, ratio=ratio)
        used += height
    flow.used = used


# --------------------------------------------------------------------------
# сборка
# --------------------------------------------------------------------------
def build(report: dict, objects: list[tuple[dict, list[Path]]], dest: Path) -> Path:
    doc = setup_document()
    root = dest.parent.parent
    assets = root / ".cache" / "visuals"
    cache = root / ".cache" / "framed"

    items = []
    for obj, images in objects:
        thumb = images[1] if len(images) > 1 else images[0]
        items.append((obj["street"], obj.get("city", ""),
                      obj.get("price_short", ""), thumb))
    full_bleed(doc, visuals.contents_cover(
        assets / "cover.jpg",
        kicker=report.get("eyebrow", "Инвестиционная подборка · Нидерланды"),
        title=report.get("cover_title", report["title"]),
        subtitle=report["subtitle"],
        items=items,
        meta=report.get("source_line", ""),
    ))

    body = doc.add_section(WD_SECTION.NEW_PAGE)
    _page_setup(body)
    running_footer(body, report.get("running_title", report["title"]))

    flow = Flow(doc)
    for index, (obj, images) in enumerate(objects, start=1):
        opener_photo = _opener_image(images, index)
        if index > 1:
            flow.new_page()
        if opener_photo:
            full_bleed(doc, visuals.opener(
                assets / f"opener-{index}.jpg", opener_photo,
                ordinal=f"{index:02d}", city=obj.get("city", ""),
                title=obj["title"], subtitle=obj["subtitle"],
                kpi=[(label, value) for label, value, _ in obj.get("kpi", [])[:4]],
            ))
            flow.new_page()
        object_cards(doc, index, obj)
        flow.new_page()
        object_description(doc, obj, cache, images)
        flow.new_page()
        object_location(doc, obj, cache, images)
        photo_pages(doc, images, flow, cache)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX (dossier): %s", dest)
    return dest
