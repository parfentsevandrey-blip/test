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
from .docx_render import (
    _el,
    _insert_ordered,
    cell_margins,
    cell_shading,
    paragraph_border,
    table_borders,
)
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
    paragraph = par(doc, after=after, lead=size * 1.06)
    txt(paragraph, text, font=DISPLAY, size=size, color=S.INK, tracking=-8)
    return paragraph


def body_paragraphs(doc, texts: list[str], *, after: float = 8.0) -> None:
    """Проза антиквой: гротеск на всю полосу читается серо."""
    for position, text in enumerate(texts):
        # выключка влево, а не по формату: без переносов русский текст
        # при выключке по формату разваливается на разреженные строки
        paragraph = par(doc, after=0 if position == len(texts) - 1 else after,
                        lead=S.LH_BODY)
        txt(paragraph, text, font=S.BODY_FONT, size=S.FS_BODY, color=S.BODY)


def lead_paragraph(doc, text: str, *, after: float = 12.0):
    paragraph = par(doc, after=after, lead=S.LH_LEAD)
    txt(paragraph, text, font=S.SERIF_LIGHT, size=S.FS_LEAD, color=S.INK)
    return paragraph


def pull_quote(doc, text: str, *, before: float = 13.0, after: float = 13.0):
    """Врезка-цитата курсивом дисплейной антиквы — главный тезис полосы."""
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, before=before, after=8)
    paragraph = par(doc, after=8, lead=23, right=30)
    txt(paragraph, text, font=DISPLAY, size=17, color=S.INK, tracking=-6, italic=True)
    rule(doc, color=S.RULE, size=S.SZ_HAIRLINE, after=after)


def figures_band(doc, items: list[list[str]]):
    """Полоса главных цифр: цена, доход, доходность, площадь.

    Одна плашка на четыре показателя, а не четыре отдельные карточки: полоса
    задаёт иерархию, с которой начинается чтение, и не спорит с перечнем ниже.
    """
    table = doc.add_table(rows=1, cols=len(items))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {
        "top": (S.BRASS, S.SZ_ACCENT),
        "bottom": (S.RULE, S.SZ_HAIRLINE),
        "insideV": (S.RULE, S.SZ_HAIRLINE),
    })
    width = S.CONTENT_W_MM / len(items)
    fixed_layout(table, [width] * len(items))
    for cell, entry in zip(table.rows[0].cells, items):
        label, value, note = (list(entry) + ["", "", ""])[:3]
        cell.width = Mm(width)
        cell_shading(cell, S.PANEL)
        cell_margins(cell, top=15, bottom=16, left=8, right=6)

        head = cell.paragraphs[0]
        head.paragraph_format.space_after = Pt(4)
        head.paragraph_format.line_spacing = Pt(S.FS_MICRO + 2)
        txt(head, label, font=S.SANS_MEDIUM, size=S.FS_MICRO, color=S.BRASS, caps=True)

        big = cell.add_paragraph()
        big.paragraph_format.space_after = Pt(3)
        big.paragraph_format.line_spacing = Pt(21)
        # кегль подобран под самое длинное значение полосы («€ 1.900.000»):
        # при большем оно переносилось на вторую строку
        txt(big, value, font=S.SANS_LIGHT, size=17, color=S.INK, tracking=-6)

        if note:
            caption = cell.add_paragraph()
            caption.paragraph_format.space_after = Pt(0)
            caption.paragraph_format.line_spacing = Pt(9.6)
            txt(caption, note, size=7.4, color=S.MUTED)
    return table


LABEL_INDENT_PT = 96.0          # 34 мм под подпись строки


def fact_row(cell, label: str, value: str) -> None:
    """Строка перечня: подпись слева, значение с висячим отступом справа."""
    paragraph = par(cell, before=5.6, after=5.6, lead=12.8,
                    left=LABEL_INDENT_PT, hanging=LABEL_INDENT_PT)
    tabs = _el("tabs")
    tabs.append(_el("tab", val="left", pos=int(LABEL_INDENT_PT * 20)))
    _insert_ordered(paragraph._p.get_or_add_pPr(), tabs)
    paragraph_border(paragraph, "bottom", S.RULE_SOFT, S.SZ_HAIRLINE, space=3)
    txt(paragraph, label, font=S.SANS_MEDIUM, size=8.8, color=S.INK)
    txt(paragraph, "\t", size=8.8, color=S.BODY)
    txt(paragraph, value, size=8.8, color=S.BODY)


def fact_columns(doc, groups: list) -> None:
    """Характеристики группами в две колонки — перечень, а не сетка плашек.

    Волосяная линейка под каждой строкой держит ритм лучше, чем рамка вокруг
    каждого показателя: глаз читает столбец, а не пятнадцать отдельных блоков.
    """
    counts = [len(rows) for _, rows in groups]
    total = sum(counts)
    # точка деления выбирается по минимальному перекосу, а не по первому
    # превышению половины: иначе в одной колонке оказывалось вдвое больше строк
    split = min(range(1, len(groups)),
                key=lambda index: abs(sum(counts[:index]) - total / 2),
                default=1)
    halves = (groups[:split], groups[split:])

    column_mm = (S.CONTENT_W_MM - 6.0) / 2
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {})
    fixed_layout(table, [column_mm + 6.0, column_mm])

    for position, (cell, half) in enumerate(zip(table.rows[0].cells, halves)):
        cell.width = Mm(column_mm + (6.0 if position == 0 else 0.0))
        cell_margins(cell, top=0, bottom=0, left=0,
                     right=17 if position == 0 else 0)
        _clean(cell)
        for order, (title, rows) in enumerate(half):
            header = par(cell, before=0 if order == 0 else 12, after=5,
                         lead=S.FS_MICRO + 2)
            paragraph_border(header, "top", S.BRASS, S.SZ_RULE, space=5)
            txt(header, title, font=S.SANS_MEDIUM, size=S.FS_MICRO,
                color=S.BRASS, caps=True)
            for label, value in rows:
                fact_row(cell, label, value)


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
def object_facts(doc, index: int, obj: dict) -> None:
    micro(doc, f"Объект {index:02d} · {obj.get('city', '')}", after=6)
    # длинное название сажаем на кегль поменьше, иначе оно уходит на две строки
    display_title(doc, obj["title"], size=31 if len(obj["title"]) <= 26 else 25,
                  after=8)
    if obj.get("lead"):
        lead_paragraph(doc, obj["lead"], after=11)

    facts = obj.get("facts") or {}
    if facts.get("headline"):
        figures_band(doc, facts["headline"])
    if facts.get("groups"):
        par(doc, after=0, lead=15)
        fact_columns(doc, facts["groups"])

    # ссылка на публикацию закрывает полосу и заменяет строку в перечне
    link = next((value for label, value in obj.get("specs", [])
                 if str(value).startswith("http")), None)
    if link:
        par(doc, after=0, lead=14)
        rule(doc, color=S.RULE, size=S.SZ_HAIRLINE, after=5)
        source = par(doc, after=0, lead=S.LH_SMALL)
        txt(source, "Публикация объекта: ", font=S.SANS_MEDIUM,
            size=S.FS_CAPTION, color=S.MUTED)
        txt(source, link, size=S.FS_CAPTION, color=S.MUTED)


def object_description(doc, obj: dict, cache: Path, images: list[Path]) -> None:
    micro(doc, "Описание объекта", after=6)
    display_title(doc, obj["street"], size=26, after=8)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=11)

    for block in obj["sections"]:
        if block.get("type") != "paragraphs":
            continue
        body_paragraphs(doc, block["paragraphs"])
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
    micro(doc, "Локация", after=6)
    display_title(doc, block["title"], size=26, after=8)
    rule(doc, color=S.INK, size=S.SZ_RULE, after=11)
    body_paragraphs(doc, block["paragraphs"])

    if images:
        par(doc, after=0, lead=13)
        framed_photo(doc, images[0], cache, width_mm=S.CONTENT_W_MM, ratio=16 / 9)
        # подпись выключается по центру кадра, а не по левому краю набора
        caption = par(doc, before=3, after=0, lead=S.LH_SMALL,
                      align=WD_ALIGN_PARAGRAPH.CENTER)
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
        object_facts(doc, index, obj)
        flow.new_page()
        object_description(doc, obj, cache, images)
        flow.new_page()
        object_location(doc, obj, cache, images)
        photo_pages(doc, images, flow, cache)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX (dossier): %s", dest)
    return dest
