"""Рендер отчёта в .docx в оформлении эталонного шаблона."""

from __future__ import annotations

import logging
import os
import tempfile
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from PIL import Image

from . import style as S

log = logging.getLogger(__name__)


# --------------------------------------------------------------------------
# низкоуровневые помощники OOXML
# --------------------------------------------------------------------------
def _el(tag: str, **attrs) -> OxmlElement:
    node = OxmlElement(tag if ":" in tag else f"w:{tag}")
    for key, value in attrs.items():
        node.set(qn(f"w:{key}"), str(value))
    return node


# Порядок дочерних элементов в OOXML жёстко задан схемой: если вставить узел
# «не туда», Word откроет файл, а LibreOffice откажется его читать.
_ORDER = {
    "pPr": (
        "pStyle keepNext keepLines pageBreakBefore framePr widowControl numPr "
        "suppressLineNumbers pBdr shd tabs suppressAutoHyphens kinsoku wordWrap "
        "overflowPunct topLinePunct autoSpaceDE autoSpaceDN bidi adjustRightInd "
        "snapToGrid spacing ind contextualSpacing mirrorIndents suppressOverlap jc "
        "textDirection textAlignment textboxTightWrap outlineLvl divId cnfStyle rPr "
        "sectPr pPrChange"
    ),
    "rPr": (
        "rStyle rFonts b bCs i iCs caps smallCaps strike dstrike outline shadow emboss "
        "imprint noProof snapToGrid vanish webHidden color spacing w kern position sz "
        "szCs highlight u effect bdr shd fitText vertAlign rtl cs em lang "
        "eastAsianLayout specVanish oMath"
    ),
    "tblPr": (
        "tblStyle tblpPr tblOverlap bidiVisual tblStyleRowBandSize tblStyleColBandSize "
        "tblW jc tblCellSpacing tblInd tblBorders shd tblLayout tblCellMar tblLook "
        "tblCaption tblDescription"
    ),
    "tcPr": (
        "cnfStyle tcW gridSpan hMerge vMerge tcBorders shd noWrap tcMar textDirection "
        "tcFitText vAlign hideMark"
    ),
    "trPr": (
        "cnfStyle divId gridBefore gridAfter wBefore wAfter cantSplit trHeight tblHeader "
        "tblCellSpacing jc hidden ins del trPrChange"
    ),
}


def _insert_ordered(parent, element) -> None:
    """Вставляет узел в родителя с соблюдением порядка, заданного схемой OOXML."""
    order = _ORDER[parent.tag.split("}")[-1]].split()
    tag = element.tag.split("}")[-1]
    index = order.index(tag)
    for child in parent:
        child_tag = child.tag.split("}")[-1]
        if child_tag in order and order.index(child_tag) > index:
            child.addprevious(element)
            return
    parent.append(element)


def _get_or_add(parent, tag: str):
    existing = parent.find(qn(f"w:{tag}"))
    if existing is not None:
        return existing
    element = _el(tag)
    _insert_ordered(parent, element)
    return element


def _append_content(paragraph, element) -> None:
    """Добавляет узел уровня содержимого (w:hyperlink, w:fldSimple) в конец абзаца.

    Свойства абзаца (w:pPr) по схеме идут первыми и создаются раньше прогонов,
    поэтому добавление в конец списка детей порядок не нарушает.
    """
    paragraph._p.append(element)


def _apply_fonts(rPr) -> None:
    """Проставляет наш шрифт для всех наборов символов (латиница, кириллица)."""
    fonts = _get_or_add(rPr, "rFonts")
    for attr in ("ascii", "hAnsi", "cs", "eastAsia"):
        fonts.set(qn(f"w:{attr}"), S.FONT)
    # темы Word перебивают явное имя шрифта — убираем их
    for attr in ("asciiTheme", "hAnsiTheme", "cstheme", "eastAsiaTheme"):
        if fonts.get(qn(f"w:{attr}")) is not None:
            del fonts.attrib[qn(f"w:{attr}")]


def paragraph_border(paragraph, edge: str, color: str, size: int, space: int = 1) -> None:
    """Добавляет линейку (границу абзаца) — используется для линеек и подчёркиваний."""
    borders = _get_or_add(paragraph._p.get_or_add_pPr(), "pBdr")
    borders.append(_el(edge, val="single", sz=size, space=space, color=color))


def run_tracking(run, twentieths: int) -> None:
    """Разрядка символов (w:spacing) в 1/20 pt."""
    _insert_ordered(run._r.get_or_add_rPr(), _el("spacing", val=twentieths))


def cell_shading(cell, color: str) -> None:
    _insert_ordered(
        cell._tc.get_or_add_tcPr(), _el("shd", val="clear", color="auto", fill=color)
    )


def cell_margins(cell, top=0, bottom=0, left=0, right=0) -> None:
    """Внутренние поля ячейки в pt."""
    margins = _el("tcMar")
    for name, value in (("top", top), ("start", left), ("bottom", bottom), ("end", right)):
        margins.append(_el(name, w=int(value * 20), type="dxa"))
    _insert_ordered(cell._tc.get_or_add_tcPr(), margins)


def table_borders(table, spec: dict[str, tuple[str, int]]) -> None:
    """spec: {'top': (color, sz), 'insideH': (...), ...}; отсутствующие рёбра — none."""
    borders = _el("tblBorders")
    for edge in ("top", "start", "bottom", "end", "insideH", "insideV"):
        if edge in spec:
            color, size = spec[edge]
            borders.append(_el(edge, val="single", sz=size, space=0, color=color))
        else:
            borders.append(_el(edge, val="none", sz=0, space=0, color="auto"))
    _insert_ordered(table._tbl.tblPr, borders)


def row_cant_split(row) -> None:
    """Запрещает разрыв строки таблицы между страницами."""
    _insert_ordered(row._tr.get_or_add_trPr(), _el("cantSplit", val="1"))


def row_repeat_header(row) -> None:
    """Повторяет строку-шапку таблицы на каждой следующей странице."""
    _insert_ordered(row._tr.get_or_add_trPr(), _el("tblHeader", val="1"))


def keep_with_next(paragraph) -> None:
    _insert_ordered(paragraph._p.get_or_add_pPr(), _el("keepNext", val="1"))


def column_widths(table, widths: list[float]) -> None:
    """Ширины колонок таблицы в pt (сетка w:gridCol)."""
    for column, width in zip(table.columns, widths):
        column.width = Pt(width)


# --------------------------------------------------------------------------
# строительные блоки страницы
# --------------------------------------------------------------------------
def add_run(paragraph, text: str, *, size: float, color: str, bold=False, italic=False):
    run = paragraph.add_run(text)
    run.font.name = S.FONT
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = RGBColor.from_string(color)
    # шрифт для кириллицы/восточных наборов
    _apply_fonts(run._r.get_or_add_rPr())
    return run


def add_paragraph(doc, *, before=0, after=0, align=None, line=None, style=None):
    paragraph = doc.add_paragraph(style=style) if style else doc.add_paragraph()
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(before)
    fmt.space_after = Pt(after)
    if align is not None:
        paragraph.alignment = align
    if line is not None:
        fmt.line_spacing = line
    return paragraph


def hyperlink(paragraph, text: str, url: str, *, size=S.FS_TABLE, color=S.NAVY):
    """Внешняя ссылка: w:hyperlink со связью, записанной в часть документа."""
    rel_id = paragraph.part.relate_to(url, RT.HYPERLINK, is_external=True)
    link = _el("hyperlink")
    link.set(qn("r:id"), rel_id)
    run = add_run(paragraph, text, size=size, color=color)
    run.font.underline = True
    link.append(run._r)  # прогон переезжает из абзаца внутрь ссылки
    _append_content(paragraph, link)
    return link


def page_number_field(paragraph, *, size: float, color: str):
    """Номер страницы полем PAGE; текст прогона — закэшированное значение поля."""
    field = _el("fldSimple", instr=" PAGE ")
    run = add_run(paragraph, "1", size=size, color=color)
    field.append(run._r)
    _append_content(paragraph, field)
    return field


def gold_rule(doc, *, before=0, after=6, size=S.RULE_GOLD_SZ):
    paragraph = add_paragraph(doc, before=before, after=after)
    paragraph.paragraph_format.line_spacing = 1
    paragraph_border(paragraph, "bottom", S.GOLD, size, space=1)
    return paragraph


def section_heading(doc, text: str):
    paragraph = add_paragraph(doc, before=16, after=8)
    run = add_run(paragraph, text.upper(), size=S.FS_SECTION, color=S.NAVY, bold=True)
    run_tracking(run, S.TRACKING_SECTION)
    paragraph_border(paragraph, "bottom", S.NAVY, S.RULE_NAVY_SZ, space=4)
    keep_with_next(paragraph)
    return paragraph


def body_paragraph(doc, text: str, *, justify=True, after=6):
    align = WD_ALIGN_PARAGRAPH.JUSTIFY if justify else WD_ALIGN_PARAGRAPH.LEFT
    paragraph = add_paragraph(doc, after=after, align=align)
    add_run(paragraph, text, size=S.FS_BODY, color=S.BODY)
    return paragraph


def bullet(doc, item: str | dict):
    """Пункт списка: строка либо {'lead': 'Почва', 'text': '…'} — lead жирным."""
    paragraph = add_paragraph(doc, after=3)
    fmt = paragraph.paragraph_format
    fmt.left_indent = Pt(S.BULLET_INDENT)
    fmt.first_line_indent = Pt(-S.BULLET_HANGING)
    tabs = _el("tabs")
    tabs.append(_el("tab", val="left", pos=int(S.BULLET_INDENT * 20)))
    _insert_ordered(paragraph._p.get_or_add_pPr(), tabs)
    add_run(paragraph, "•", size=S.FS_BULLET_MARK, color=S.GOLD)
    add_run(paragraph, "\t", size=S.FS_BODY, color=S.BODY)
    if isinstance(item, dict):
        add_run(paragraph, item.get("lead", ""), size=S.FS_BODY, color=S.NAVY, bold=True)
        add_run(paragraph, " — ", size=S.FS_BODY, color=S.BODY)
        add_run(paragraph, item.get("text", ""), size=S.FS_BODY, color=S.BODY)
    else:
        add_run(paragraph, item, size=S.FS_BODY, color=S.BODY)
    return paragraph


def badges_paragraph(doc, items: list[str]):
    """Плашки статуса у заголовка: светло-золотые подложки, прижатые влево."""
    if not items:
        return None
    # ширина плашки — по длине текста; между плашками узкие пустые ячейки
    cells: list[tuple[str | None, float]] = []
    for index, text in enumerate(items):
        if index:
            cells.append((None, S.BADGE_GAP))
        cells.append((text, len(text) * S.BADGE_CHAR_W + S.BADGE_PADDING))
    used = sum(width for _, width in cells)
    cells.append((None, max(S.CONTENT_WIDTH - used, S.BADGE_GAP)))  # добор до ширины текста

    table = doc.add_table(rows=1, cols=len(cells))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {})
    column_widths(table, [width for _, width in cells])
    row = table.rows[0]
    row_cant_split(row)
    for cell, (text, width) in zip(row.cells, cells):
        cell.width = Pt(width)
        paragraph = cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        if text is None:  # разделитель между плашками, без заливки и текста
            cell_margins(cell)
            continue
        cell_shading(cell, S.BADGE_BG)
        cell_margins(cell, top=3, bottom=3, left=7, right=7)
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        add_run(paragraph, text, size=S.FS_BADGE, color=S.NAVY, bold=True)
    return table


def kpi_tiles(doc, tiles: list[tuple[str, str]]):
    """Строка из 3-4 плиток «подпись/значение» во всю ширину текстового блока."""
    if not tiles:
        return None
    table = doc.add_table(rows=1, cols=len(tiles))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    # внешних рамок нет; вертикальная линия цветом страницы = зазор между плитками
    table_borders(table, {"insideV": (S.PAGE_BG, S.KPI_GAP_SZ)})
    width = S.CONTENT_WIDTH / len(tiles)
    column_widths(table, [width] * len(tiles))
    row = table.rows[0]
    row_cant_split(row)
    for cell, (label, value) in zip(row.cells, tiles):
        cell.width = Pt(width)
        cell_shading(cell, S.KPI_TILE_BG)
        cell_margins(cell, top=8, bottom=8, left=10, right=8)

        head = cell.paragraphs[0]
        head.paragraph_format.space_after = Pt(2)
        run = add_run(head, str(label).upper(), size=S.FS_KPI_LABEL, color=S.MUTED)
        run_tracking(run, S.TRACKING_KPI_LABEL)

        body = cell.add_paragraph()
        body.paragraph_format.space_after = Pt(0)
        add_run(body, str(value), size=S.FS_KPI_VALUE, color=S.NAVY, bold=True)
    return table


def _spec_group_row(row, title: str) -> None:
    """Строка-подзаголовок таблицы характеристик на всю её ширину."""
    cell = row.cells[0].merge(row.cells[1])
    cell.width = Pt(S.TABLE_WIDTH)
    cell_shading(cell, S.TABLE_GROUP_BG)
    cell_margins(cell, top=6, bottom=5, left=8, right=6)
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_after = Pt(0)
    run = add_run(paragraph, title.upper(), size=S.FS_TABLE_GROUP, color=S.NAVY, bold=True)
    run_tracking(run, S.TRACKING_TABLE_GROUP)


def spec_table(doc, rows: list):
    """Таблица характеристик: пара ['подпись', 'значение'] либо {'group': 'Финансы'}."""
    if not rows:
        return None
    table = doc.add_table(rows=len(rows), cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(
        table,
        {
            "top": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
            "bottom": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
            "insideH": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
        },
    )
    column_widths(table, [S.TABLE_COL_LABEL, S.TABLE_COL_VALUE])
    for row, item in zip(table.rows, rows):
        if isinstance(item, dict):
            _spec_group_row(row, item.get("group", ""))
            continue

        label, value = item
        label_cell, value_cell = row.cells
        label_cell.width = Pt(S.TABLE_COL_LABEL)
        value_cell.width = Pt(S.TABLE_COL_VALUE)
        cell_shading(label_cell, S.TABLE_LABEL_BG)
        cell_margins(label_cell, top=5, bottom=5, left=8, right=6)
        cell_margins(value_cell, top=5, bottom=5, left=5, right=6)

        paragraph = label_cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        add_run(paragraph, label, size=S.FS_TABLE, color=S.NAVY, bold=True)

        paragraph = value_cell.paragraphs[0]
        paragraph.paragraph_format.space_after = Pt(0)
        text = "" if value is None else str(value)
        if text.startswith(("http://", "https://")):
            hyperlink(paragraph, text, text)
        else:
            add_run(paragraph, text, size=S.FS_TABLE, color=S.BODY)
    return table


def _summary_cell(cell, text: str, width: float, *, label: bool) -> None:
    """Ячейка сводной таблицы: подпись показателя или значение по объекту."""
    cell.width = Pt(width)
    cell_margins(cell, top=5, bottom=5, left=6, right=6)
    if label:
        cell_shading(cell, S.TABLE_LABEL_BG)
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.LEFT
    add_run(
        paragraph,
        text,
        size=S.FS_SUMMARY,
        color=S.NAVY if label else S.BODY,
        bold=label,
    )


def summary_table(doc, headers: list[str], rows: list[list[str]]):
    """Сводная таблица сравнения: колонка показателя плюс колонка на объект."""
    if not headers:
        return None
    table = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(
        table,
        {
            "top": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
            "bottom": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
            "insideH": (S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ),
        },
    )
    # колонки объектов делят остаток ширины текста поровну
    others = max(len(headers) - 1, 1)
    value_width = (S.CONTENT_WIDTH - S.SUMMARY_COL_LABEL) / others
    widths = ([S.SUMMARY_COL_LABEL] + [value_width] * others)[: len(headers)]
    column_widths(table, widths)

    for index, (cell, text) in enumerate(zip(table.rows[0].cells, headers)):
        _summary_cell(cell, str(text), widths[index], label=True)
    row_repeat_header(table.rows[0])

    for row, values in zip(table.rows[1:], rows):
        for index, cell in enumerate(row.cells):
            text = str(values[index]) if index < len(values) else ""
            _summary_cell(cell, text, widths[index], label=index == 0)
    return table


def callout(doc, title: str, paragraphs: list[str], marker: str = "\U0001F4CD"):
    table = doc.add_table(rows=1, cols=1)
    table.autofit = False
    table_borders(
        table,
        {
            edge: (S.GOLD, S.BORDER_CALLOUT_SZ)
            for edge in ("top", "start", "bottom", "end")
        },
    )
    cell = table.cell(0, 0)
    cell.width = Pt(S.CALLOUT_WIDTH)
    cell_shading(cell, S.CALLOUT_BG)
    cell_margins(cell, top=10, bottom=10, left=12, right=12)

    head = cell.paragraphs[0]
    head.paragraph_format.space_after = Pt(6)
    add_run(head, f"{marker}  ", size=S.FS_SECTION, color=S.BODY)
    add_run(head, title, size=S.FS_SECTION, color=S.NAVY, bold=True)

    for index, text in enumerate(paragraphs):
        paragraph = cell.add_paragraph()
        paragraph.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
        paragraph.paragraph_format.space_after = Pt(
            0 if index == len(paragraphs) - 1 else 6
        )
        add_run(paragraph, text, size=S.FS_BODY, color=S.BODY)
    return table


def _crop_to_temp(img: Image.Image, box: tuple[int, int, int, int]) -> Path:
    """Обрезанный кадр во временном файле; после вставки в документ удаляется."""
    handle, name = tempfile.mkstemp(prefix="hero-", suffix=".jpg")
    os.close(handle)
    path = Path(name)
    img.convert("RGB").crop(box).save(path, quality=92)
    return path


def hero_picture(doc, path: Path, *, width_cm=S.PHOTO_WIDTH_CM, max_height_cm=S.HERO_MAX_HEIGHT_CM):
    """Фото фасада под заголовком: во всю ширину текста, высокий кадр режется по центру."""
    temp: Path | None = None
    with Image.open(path) as img:
        width_px, height_px = img.size
        ratio = width_cm / max_height_cm
        if width_px / height_px < ratio:
            # кадр выше нужного — оставляем центральную полосу нужных пропорций
            keep = int(round(width_px / ratio))
            top = (height_px - keep) // 2
            temp = _crop_to_temp(img, (0, top, width_px, top + keep))
            height_cm = max_height_cm
        else:
            height_cm = width_cm * height_px / width_px

    paragraph = add_paragraph(doc, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
    try:
        source = temp if temp is not None else Path(path)
        paragraph.add_run().add_picture(str(source), width=Cm(width_cm), height=Cm(height_cm))
    finally:
        if temp is not None:  # изображение уже скопировано внутрь .docx
            temp.unlink(missing_ok=True)
    return paragraph


def picture(
    doc,
    path: Path,
    caption: str | None = None,
    *,
    max_width_cm=S.PHOTO_WIDTH_CM,
    max_height_cm=S.PHOTO_MAX_HEIGHT_CM,
):
    with Image.open(path) as img:
        width_px, height_px = img.size
    width_cm = max_width_cm
    height_cm = width_cm * height_px / width_px
    if height_cm > max_height_cm:
        height_cm = max_height_cm
        width_cm = height_cm * width_px / height_px
    paragraph = add_paragraph(doc, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
    paragraph.add_run().add_picture(str(path), width=Cm(width_cm), height=Cm(height_cm))
    if caption:
        keep_with_next(paragraph)
        signature = add_paragraph(doc, before=3, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
        add_run(signature, caption, size=S.FS_CAPTION, color=S.MUTED, italic=True)
    return paragraph


def page_break(doc):
    paragraph = add_paragraph(doc, after=0)
    paragraph.add_run().add_break(WD_BREAK.PAGE)
    return paragraph


# --------------------------------------------------------------------------
# колонтитулы
# --------------------------------------------------------------------------
def _unlink_hdrftr(section) -> None:
    """Отвязывает колонтитулы секции от предыдущей.

    python-docx переиспользует sectPr предыдущей секции, поэтому ссылка на её
    колонтитул уже стоит и одного присваивания False недостаточно: сначала
    связь снимается (True), затем заводится собственная часть (False).
    """
    for part in (section.header, section.footer):
        part.is_linked_to_previous = True
        part.is_linked_to_previous = False


def _header_line(section, left: str, right: str) -> None:
    """Верхний колонтитул: адрес слева, цена справа, тонкая линия снизу."""
    paragraph = section.header.paragraphs[0]
    paragraph.style = "Normal"  # у стиля «Header» свои табуляторы — они не нужны
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(0)
    fmt.space_after = Pt(0)
    fmt.tab_stops.add_tab_stop(Pt(S.CONTENT_WIDTH), WD_TAB_ALIGNMENT.RIGHT)
    add_run(paragraph, left, size=S.FS_HDRFTR, color=S.MUTED)
    if right:
        add_run(paragraph, "\t", size=S.FS_HDRFTR, color=S.MUTED)
        add_run(paragraph, right, size=S.FS_HDRFTR, color=S.MUTED)
    paragraph_border(paragraph, "bottom", S.TABLE_BORDER, S.BORDER_HAIRLINE_SZ, space=3)


def _footer_line(section) -> None:
    """Нижний колонтитул: номер страницы по центру."""
    paragraph = section.footer.paragraphs[0]
    paragraph.style = "Normal"
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(0)
    fmt.space_after = Pt(0)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    page_number_field(paragraph, size=S.FS_HDRFTR, color=S.MUTED)


def object_section(doc, obj: dict):
    """Новая секция под объект: с начала страницы и со своими колонтитулами."""
    section = doc.add_section(WD_SECTION.NEW_PAGE)
    section.header_distance = Cm(S.HEADER_DISTANCE)
    section.footer_distance = Cm(S.FOOTER_DISTANCE)
    _unlink_hdrftr(section)
    _header_line(section, obj.get("title", ""), obj.get("price_short", ""))
    _footer_line(section)
    return section


# --------------------------------------------------------------------------
# страницы отчёта
# --------------------------------------------------------------------------
def setup_document() -> Document:
    doc = Document()
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.top_margin = Cm(S.PAGE_MARGIN_TOP)
    section.bottom_margin = Cm(S.PAGE_MARGIN_BOTTOM)
    section.left_margin = Cm(S.PAGE_MARGIN_LEFT)
    section.right_margin = Cm(S.PAGE_MARGIN_RIGHT)

    normal = doc.styles["Normal"]
    normal.font.name = S.FONT
    normal.font.size = Pt(S.FS_BODY)
    normal.font.color.rgb = RGBColor.from_string(S.BODY)
    normal.paragraph_format.space_after = Pt(0)
    normal.paragraph_format.line_spacing = 1.15

    # заголовок объекта идёт стилем «Heading 1»: по нему LibreOffice строит
    # закладки в PDF. Оформление стиля повторяет прежнее прямое форматирование.
    heading = doc.styles["Heading 1"]
    heading.font.name = S.FONT
    heading.font.size = Pt(S.FS_OBJECT_TITLE)
    heading.font.bold = True
    heading.font.color.rgb = RGBColor.from_string(S.NAVY)
    heading.paragraph_format.space_before = Pt(0)
    heading.paragraph_format.space_after = Pt(0)
    heading.paragraph_format.line_spacing = 1.15
    _apply_fonts(heading.element.get_or_add_rPr())
    return doc


def set_metadata(doc, report: dict) -> None:
    """Свойства файла: видны в проводнике и переносятся в PDF."""
    props = doc.core_properties
    props.title = report.get("title", "")
    props.author = report.get("author", "")
    props.subject = report.get("subtitle", "")


def cover_page(doc, report: dict) -> None:
    for _ in range(6):
        add_paragraph(doc, after=0)

    gold_rule(doc, after=14)

    paragraph = add_paragraph(doc, after=10, align=WD_ALIGN_PARAGRAPH.CENTER)
    run = add_run(
        paragraph, report["title"], size=S.FS_COVER_TITLE, color=S.NAVY, bold=True
    )
    run_tracking(run, S.TRACKING_COVER_TITLE)

    paragraph = add_paragraph(doc, after=14, align=WD_ALIGN_PARAGRAPH.CENTER)
    add_run(
        paragraph,
        report["subtitle"],
        size=S.FS_COVER_SUBTITLE,
        color=S.MUTED,
        italic=True,
    )

    gold_rule(doc, after=22, size=S.RULE_NAVY_SZ + 4)

    paragraph = add_paragraph(doc, after=10, align=WD_ALIGN_PARAGRAPH.CENTER)
    add_run(
        paragraph,
        report.get("intro", "В подборку входят:"),
        size=S.FS_COVER_INTRO,
        color=S.MUTED,
    )

    for index, obj in enumerate(report["objects"], start=1):
        paragraph = add_paragraph(doc, after=6, align=WD_ALIGN_PARAGRAPH.CENTER)
        add_run(paragraph, f"{index}. ", size=S.FS_COVER_ITEM, color=S.GOLD, bold=True)
        add_run(
            paragraph,
            f"{obj['street']}, ",
            size=S.FS_COVER_ITEM,
            color=S.NAVY,
            bold=True,
        )
        add_run(
            paragraph,
            f"{obj['city']} — {obj['price_short']}",
            size=S.FS_COVER_ITEM,
            color=S.BODY,
        )

    if report.get("footnote"):
        add_paragraph(doc, after=0)
        paragraph = add_paragraph(doc, before=18, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
        add_run(paragraph, report["footnote"], size=9, color=S.MUTED, italic=True)


def summary_page(doc, summary: dict) -> None:
    """Сводная страница сравнения — сразу после обложки, без колонтитулов."""
    section_heading(doc, "Сравнение объектов")
    summary_table(doc, summary.get("headers", []), summary.get("rows", []))
    if summary.get("note"):
        paragraph = add_paragraph(doc, before=8, after=0)
        add_run(paragraph, summary["note"], size=S.FS_CAPTION, color=S.MUTED, italic=True)


def object_pages(doc, obj: dict, images: list[Path], extras: dict | None = None) -> None:
    extras = extras or {}
    captions = extras.get("captions") or []

    gold_rule(doc, after=10)

    paragraph = add_paragraph(doc, after=4, style="Heading 1")
    add_run(paragraph, obj["title"], size=S.FS_OBJECT_TITLE, color=S.NAVY, bold=True)

    badges = obj.get("badges") or []
    if badges:
        badges_paragraph(doc, badges)

    # без плашек подзаголовок стоит вплотную к заголовку, как в прежней вёрстке
    paragraph = add_paragraph(doc, before=10 if badges else 0, after=12)
    add_run(
        paragraph,
        obj["subtitle"],
        size=S.FS_OBJECT_SUBTITLE,
        color=S.MUTED,
        italic=True,
    )

    if extras.get("hero"):
        hero_picture(doc, extras["hero"]).paragraph_format.space_after = Pt(10)

    if extras.get("kpi"):
        kpi_tiles(doc, extras["kpi"])

    if obj.get("positioning"):
        # строка позиционирования заодно разделяет плитки и таблицу:
        # две таблицы подряд Word и LibreOffice склеивают в одну
        paragraph = add_paragraph(doc, before=10, after=10)
        add_run(paragraph, obj["positioning"], size=S.FS_BODY, color=S.NAVY, italic=True)
    elif extras.get("kpi"):
        add_paragraph(doc, before=6, after=0)  # разделитель между таблицами

    spec_table(doc, extras.get("spec_rows") or obj.get("specs") or [])
    add_paragraph(doc, after=0)

    for block in obj["sections"]:
        kind = block.get("type", "bullets")
        if block.get("heading"):
            section_heading(doc, block["heading"])
        if kind == "callout":
            callout(doc, block["title"], block["paragraphs"])
        elif kind == "paragraphs":
            for text in block["paragraphs"]:
                body_paragraph(doc, text)
        else:
            for item in block["bullets"]:
                bullet(doc, item)

    if images:
        page_break(doc)
        for index, path in enumerate(images):
            picture(doc, path, captions[index] if index < len(captions) else None)
            if index == len(images) - 1:
                break
            if (index + 1) % S.PHOTOS_PER_PAGE == 0:
                page_break(doc)
            else:
                add_paragraph(doc, after=0, before=14)


def _unpack(item: tuple) -> tuple[dict, list[Path], dict]:
    """Карточка объекта: (obj, images, extras); пара без extras тоже принимается."""
    if len(item) >= 3:
        return item[0], item[1], item[2] or {}
    return item[0], item[1], {}


def _summary(report: dict, objects: list[tuple]) -> dict | None:
    """Описание сводной таблицы: из отчёта либо из extras первого объекта."""
    if report.get("summary"):
        return report["summary"]
    for item in objects:
        extras = _unpack(item)[2]
        if extras.get("summary"):
            return extras["summary"]
    return None


def build(report: dict, objects: list[tuple], dest: Path) -> Path:
    doc = setup_document()
    set_metadata(doc, report)
    cover_page(doc, report)

    summary = _summary(report, objects)
    if summary:
        page_break(doc)
        summary_page(doc, summary)

    for item in objects:
        obj, images, extras = _unpack(item)
        object_section(doc, obj)  # секция начинает новую страницу сама
        object_pages(doc, obj, images, extras)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX: %s", dest)
    return dest
