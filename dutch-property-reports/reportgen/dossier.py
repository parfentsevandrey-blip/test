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


def card_grid(doc, obj: dict, assets: Path, cards: list[list[str]]) -> None:
    """Характеристики карточками со скруглением и тенью — как у фотографий.

    Ячейка таблицы DOCX не скругляется, поэтому вся сетка рисуется одним
    изображением (visuals.cards_panel) и ставится на полосу целиком.
    """
    image, height = visuals.cards_panel(
        assets / f"cards-{obj['slug']}.jpg", cards,
        width_mm=S.CONTENT_W_MM, max_height_mm=181.0)
    photo(doc, image, width_mm=S.CONTENT_W_MM, max_h=height + 2)


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
def object_cards(doc, index: int, obj: dict, assets: Path) -> None:
    micro(doc, f"Объект {index:02d} · {obj.get('city', '')}", after=6)
    # длинное название сажаем на кегль поменьше, иначе оно уходит на две
    # строки и сетка карточек не помещается на ту же полосу
    display_title(doc, obj["title"], size=31 if len(obj["title"]) <= 26 else 25,
                  after=8)
    if obj.get("lead"):
        lead_paragraph(doc, obj["lead"], after=9)
    rule(doc, color=S.BRASS, size=S.SZ_ACCENT, after=11)
    cards = obj.get("cards") or [[label, value, ""] for label, value in obj["specs"][:15]]
    card_grid(doc, obj, assets, cards)


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
        object_cards(doc, index, obj, assets)
        flow.new_page()
        object_description(doc, obj, cache, images)
        flow.new_page()
        object_location(doc, obj, cache, images)
        photo_pages(doc, images, flow, cache)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX (dossier): %s", dest)
    return dest
