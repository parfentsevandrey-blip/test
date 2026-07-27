"""Рендер отчёта в .docx в оформлении эталонного шаблона."""

from __future__ import annotations

import logging
import os
import tempfile
from functools import lru_cache
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT
from docx.opc.constants import RELATIONSHIP_TYPE as RT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from PIL import Image, ImageFont

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


def cell_no_wrap(cell) -> None:
    """Запрещает перенос строки внутри ячейки (плашки статуса — всегда в одну строку)."""
    _insert_ordered(cell._tc.get_or_add_tcPr(), _el("noWrap", val="1"))


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
    if _has_keep_next(paragraph._p):
        return
    _insert_ordered(paragraph._p.get_or_add_pPr(), _el("keepNext", val="1"))


def keep_lines_together(paragraph) -> None:
    """Запрещает разрывать сам абзац между страницами (w:keepLines)."""
    pPr = paragraph._p.get_or_add_pPr()
    if pPr.find(qn("w:keepLines")) is None:
        _insert_ordered(pPr, _el("keepLines", val="1"))


def _has_keep_next(p_element) -> bool:
    pPr = p_element.find(qn("w:pPr"))
    if pPr is None:
        return False
    node = pPr.find(qn("w:keepNext"))
    return node is not None and node.get(qn("w:val")) not in ("0", "false")


def _keep_next_streak(p_element) -> int:
    """Сколько абзацев подряд непосредственно перед данным помечены keepNext.

    По этому счётчику список понимает, что стоит сразу за заголовком раздела
    (streak == 1), и удерживает при заголовке ровно первый свой пункт: заголовок
    держит первый пункт, первый пункт — второй, дальше цепочка обрывается.
    """
    count = 0
    node = p_element.getprevious()
    while node is not None and node.tag == qn("w:p") and _has_keep_next(node):
        count += 1
        node = node.getprevious()
    return count


_MEASURE_SCALE = 10  # меряем на десятикратном кегле — точность до 0.1 pt


@lru_cache(maxsize=16)
def _measure_font(size_pt: float, bold: bool):
    """Файл шрифта для замера ширины строки; None — если ничего не нашлось."""
    names = S.FONT_FILES_BOLD if bold else S.FONT_FILES_REGULAR
    for name in names:
        try:
            return ImageFont.truetype(name, round(size_pt * _MEASURE_SCALE))
        except OSError:
            continue
    log.warning("шрифт для замера не найден (%s), ширина считается оценкой", names[0])
    return None


def text_width_pt(text: str, size_pt: float, *, bold: bool = False) -> float:
    """Ширина строки в пунктах, измеренная по реальным метрикам шрифта."""
    font = _measure_font(size_pt, bold)
    if font is None:  # запасной вариант: грубая оценка по средней ширине символа
        return len(text) * S.BADGE_CHAR_W * size_pt / S.FS_BADGE
    return font.getlength(text) / _MEASURE_SCALE


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
    """Заголовок раздела с линейкой.

    Заголовок держит следующий абзац (keepNext), а первый пункт списка держит
    второй — см. bullet(): в итоге заголовок не отрывается от первых двух
    элементов списка и не остаётся один внизу страницы.
    """
    paragraph = add_paragraph(doc, before=16, after=8)
    run = add_run(paragraph, text.upper(), size=S.FS_SECTION, color=S.NAVY, bold=True)
    run_tracking(run, S.TRACKING_SECTION)
    paragraph_border(paragraph, "bottom", S.NAVY, S.RULE_NAVY_SZ, space=4)
    keep_with_next(paragraph)
    keep_lines_together(paragraph)
    return paragraph


def body_paragraph(doc, text: str, *, justify=True, after=6):
    align = WD_ALIGN_PARAGRAPH.JUSTIFY if justify else WD_ALIGN_PARAGRAPH.LEFT
    paragraph = add_paragraph(doc, after=after, align=align)
    add_run(paragraph, text, size=S.FS_BODY, color=S.BODY)
    return paragraph


def bullet(doc, item: str | dict, *, size: float = S.FS_BODY, keep_next: bool | None = None):
    """Пункт списка: строка либо {'lead': 'Почва', 'text': '…'} — lead жирным.

    keep_next=None — решать самому: пункт, стоящий сразу за заголовком раздела,
    прижимается к нему, чтобы заголовок не отрывался от первых двух пунктов.
    """
    paragraph = add_paragraph(doc, after=S.BULLET_GAP)
    fmt = paragraph.paragraph_format
    fmt.left_indent = Pt(S.BULLET_INDENT)
    fmt.first_line_indent = Pt(-S.BULLET_HANGING)
    tabs = _el("tabs")
    tabs.append(_el("tab", val="left", pos=int(S.BULLET_INDENT * 20)))
    _insert_ordered(paragraph._p.get_or_add_pPr(), tabs)
    mark_size = size + (S.FS_BULLET_MARK - S.FS_BODY)
    add_run(paragraph, "•", size=mark_size, color=S.GOLD)
    add_run(paragraph, "\t", size=size, color=S.BODY)
    if isinstance(item, dict):
        add_run(paragraph, item.get("lead", ""), size=size, color=S.NAVY, bold=True)
        add_run(paragraph, " — ", size=size, color=S.BODY)
        add_run(paragraph, item.get("text", ""), size=size, color=S.BODY)
    else:
        add_run(paragraph, item, size=size, color=S.BODY)

    keep_lines_together(paragraph)  # сам пункт по страницам не разрывается
    if keep_next is None:
        # ровно один keepNext перед нами — это заголовок раздела, держимся за него
        keep_next = _keep_next_streak(paragraph._p) == 1
    if keep_next:
        keep_with_next(paragraph)
    return paragraph


def badges_paragraph(doc, items: list[str]):
    """Плашки статуса у заголовка: светло-золотые подложки, прижатые влево."""
    if not items:
        return None
    # Ширина плашки = измеренная ширина строки плюс фиксированные поля, поэтому
    # внутренние отступы у всех плашек одинаковы и не «плавают» от длины текста.
    cells: list[tuple[str | None, float]] = []
    for index, text in enumerate(items):
        if index:
            cells.append((None, S.BADGE_GAP))
        width = text_width_pt(text, S.FS_BADGE, bold=True)
        cells.append((text, width + 2 * S.BADGE_PAD_X + S.BADGE_SAFETY))
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
        cell_margins(
            cell,
            top=S.BADGE_PAD_Y,
            bottom=S.BADGE_PAD_Y,
            left=S.BADGE_PAD_X,
            right=S.BADGE_PAD_X,
        )
        cell_no_wrap(cell)
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
    """Строка-подзаголовок таблицы характеристик на всю её ширину.

    keepNext у абзацев ячейки не даёт строке-группе («ФИНАНСЫ», «ОБЪЕКТ»)
    остаться последней на странице: она уходит на следующую вместе с первой
    строкой своей группы.
    """
    cell = row.cells[0].merge(row.cells[1])
    cell.width = Pt(S.TABLE_WIDTH)
    cell_shading(cell, S.TABLE_GROUP_BG)
    cell_margins(cell, top=6, bottom=5, left=8, right=6)
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_after = Pt(0)
    run = add_run(paragraph, title.upper(), size=S.FS_TABLE_GROUP, color=S.NAVY, bold=True)
    run_tracking(run, S.TRACKING_TABLE_GROUP)
    for cell_paragraph in cell.paragraphs:
        keep_with_next(cell_paragraph)
        keep_lines_together(cell_paragraph)


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
    since_group = None  # сколько обычных строк прошло после строки-группы
    for row, item in zip(table.rows, rows):
        row_cant_split(row)  # строка целиком остаётся на одной странице
        if isinstance(item, dict):
            _spec_group_row(row, item.get("group", ""))
            since_group = 0
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

        # группа тянет за собой две первые строки: иначе подзаголовок остаётся
        # внизу полосы с одной-двумя строками, а остальные уезжают на следующую
        if since_group is not None and since_group < S.TABLE_GROUP_KEEP_ROWS:
            for cell in (label_cell, value_cell):
                for cell_paragraph in cell.paragraphs:
                    keep_with_next(cell_paragraph)
            since_group += 1
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


def card_variant(block: dict) -> str:
    """Оформление карточки по смыслу раздела: город, риски или обычная."""
    if block.get("variant"):
        return block["variant"]
    if block.get("type") == "callout":
        return "city"
    if "риск" in (block.get("heading") or "").lower():
        return "risk"
    return "plain"


def card(doc, title: str, *, variant: str = "plain"):
    """Тематическая карточка: рамка, акцентная полоса слева и заголовок внутри.

    Возвращает ячейку — содержимое добавляется в неё теми же помощниками
    (bullet, body_paragraph), что и обычный текст: они принимают любой
    контейнер с методом add_paragraph.
    """
    look = S.CARD_VARIANTS.get(variant, S.CARD_VARIANTS["plain"])
    table = doc.add_table(rows=1, cols=1)
    table.autofit = False
    table_borders(
        table,
        {
            "top": (look["border"], S.BORDER_CARD_SZ),
            "bottom": (look["border"], S.BORDER_CARD_SZ),
            "end": (look["border"], S.BORDER_CARD_SZ),
            "start": (look["accent"], S.BORDER_CARD_ACCENT_SZ),
        },
    )
    column_widths(table, [S.CARD_WIDTH])
    cell = table.cell(0, 0)
    cell.width = Pt(S.CARD_WIDTH)
    cell_shading(cell, look["bg"])
    cell_margins(cell, top=S.CARD_PAD_Y, bottom=S.CARD_PAD_Y, left=S.CARD_PAD_X, right=S.CARD_PAD_X)

    head = cell.paragraphs[0]
    head.paragraph_format.space_after = Pt(7)
    if title:
        run = add_run(head, title.upper(), size=S.FS_CARD_TITLE, color=S.NAVY, bold=True)
        run_tracking(run, S.TRACKING_SECTION)
        # keepNext здесь не ставится намеренно: LibreOffice в этом случае
        # переносит на следующую полосу всю карточку целиком, оставляя
        # до 40 % пустого листа. Карточка должна разрываться по строкам.
    return cell


def card_gap(doc, size: float = S.CARD_GAP):
    """Зазор между карточками: две таблицы подряд Word и LibreOffice склеивают в одну."""
    paragraph = add_paragraph(doc, after=0)
    paragraph.paragraph_format.line_spacing = 1
    add_run(paragraph, "", size=size, color=S.BODY)
    return paragraph


def card_body(cell, block: dict) -> None:
    """Наполнение карточки: абзацы, список или врезка «О городе и районе»."""
    kind = block.get("type", "bullets")
    if kind == "callout":
        head = cell.add_paragraph()
        head.paragraph_format.space_after = Pt(5)
        add_run(head, f"{S.CALLOUT_MARKER}  ", size=S.FS_BODY, color=S.GOLD, bold=True)
        add_run(head, block.get("title", ""), size=S.FS_BODY, color=S.NAVY, bold=True)
        keep_with_next(head)
        texts = block.get("paragraphs") or []
    elif kind == "paragraphs":
        texts = block.get("paragraphs") or []
    else:
        items = block.get("bullets") or []
        for index, item in enumerate(items):
            bullet(cell, item, keep_next=index == 0 and len(items) > 1)
        return

    for index, text in enumerate(texts):
        last = index == len(texts) - 1
        body_paragraph(cell, text, after=0 if last else 6)


def callout(doc, title: str, paragraphs: list[str], marker: str = S.CALLOUT_MARKER):
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
    # маркер — знак основного шрифта, кегль заголовка врезки, цвет золотой
    add_run(head, f"{marker}  ", size=S.FS_SECTION, color=S.GOLD, bold=True)
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


def _picture_paragraph(doc, before: float = 0):
    """Абзац под снимок: по центру текстовой колонки, без отступов и интерлиньяжа.

    Отступы обнуляются явно: любой унаследованный left_indent сдвинул бы центр
    картинки относительно центра текстовой колонки. Интерлиньяж 1.0 нужен, чтобы
    высота строки со снимком равнялась высоте самого снимка — на этом построен
    расчёт количества снимков на странице.
    """
    paragraph = add_paragraph(
        doc, before=before, after=0, align=WD_ALIGN_PARAGRAPH.CENTER, line=1
    )
    fmt = paragraph.paragraph_format
    fmt.left_indent = Pt(0)
    fmt.right_indent = Pt(0)
    fmt.first_line_indent = Pt(0)
    return paragraph


def hero_picture(doc, path: Path, *, width_cm=S.PHOTO_WIDTH_CM, max_height_cm=S.HERO_MAX_HEIGHT_CM):
    """Фото фасада под заголовком: во всю ширину текста, высокий кадр режется по центру."""
    temp: Path | None = None
    width_cm = min(width_cm, S.PHOTO_WIDTH_CM)
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

    paragraph = _picture_paragraph(doc)
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
    before: float = 0,
):
    with Image.open(path) as img:
        width_px, height_px = img.size
    # шире текстовой колонки (с поправкой на клемп LibreOffice) не растём:
    # такой снимок перестал бы центрироваться и вылез бы за правое поле
    width_cm = min(max_width_cm, S.PHOTO_WIDTH_CM)
    height_cm = width_cm * height_px / width_px
    if height_cm > max_height_cm:
        height_cm = max_height_cm
        width_cm = height_cm * width_px / height_px
    paragraph = _picture_paragraph(doc, before=before)
    paragraph.add_run().add_picture(str(path), width=Cm(width_cm), height=Cm(height_cm))
    if caption:
        keep_with_next(paragraph)
        signature = add_paragraph(doc, before=3, after=0, align=WD_ALIGN_PARAGRAPH.CENTER)
        add_run(signature, caption, size=S.FS_CAPTION, color=S.MUTED, italic=True)
    return paragraph


def _photo_groups(count: int, per_page: int = S.PHOTOS_PER_PAGE) -> list[int]:
    """Разбивка снимков по страницам.

    Одиночный снимок на последней странице (нечётное число фотографий) прижимает
    к ней ~400 pt пустоты, поэтому он уходит к предыдущей группе: последняя
    страница получает три снимка меньшей высоты.
    """
    if count <= 0:
        return []
    groups = [per_page] * (count // per_page)
    if count % per_page:
        groups.append(count % per_page)
    if len(groups) > 1 and groups[-1] == 1:
        orphan = groups.pop()
        groups[-1] += orphan
    return groups


def _photo_max_height_cm(per_page: int) -> float:
    """Предельная высота снимка, при которой per_page снимков заполняют страницу.

    Из высоты текстового блока вычитаются подписи, отбивки между снимками и
    запас на подписи в две строки; остаток делится поровну.
    """
    if per_page <= S.PHOTOS_PER_PAGE:
        free = S.CONTENT_HEIGHT_CM - S.PHOTO_PAGE_SLACK_CM
        free -= S.PHOTOS_PER_PAGE * S.PHOTO_CAPTION_BLOCK_CM
        free -= (S.PHOTOS_PER_PAGE - 1) * S.PHOTO_GAP_PT / S.PT_PER_CM
        return min(S.PHOTO_MAX_HEIGHT_CM, free / S.PHOTOS_PER_PAGE)
    free = S.CONTENT_HEIGHT_CM - S.PHOTO_PAGE_SLACK_CM
    free -= per_page * S.PHOTO_CAPTION_BLOCK_CM
    free -= (per_page - 1) * S.PHOTO_GAP_PT / S.PT_PER_CM
    return max(min(S.PHOTO_MAX_HEIGHT_CM, free / per_page), S.PHOTO_MIN_HEIGHT_CM)


def photo_pages(doc, images: list[Path], captions: list[str]) -> None:
    """Фотографии объекта: страницы заполняются целиком, подписи — под каждым снимком."""
    index = 0
    for page_index, per_page in enumerate(_photo_groups(len(images))):
        if page_index:
            page_break(doc)
        max_height_cm = _photo_max_height_cm(per_page)
        for position in range(per_page):
            caption = captions[index] if index < len(captions) else None
            picture(
                doc,
                images[index],
                caption,
                max_height_cm=max_height_cm,
                before=0 if position == 0 else S.PHOTO_GAP_PT,
            )
            index += 1


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


def new_section(doc, header_left: str, header_right: str = ""):
    """Новая секция с начала страницы и со своими колонтитулами: шапка и номер."""
    section = doc.add_section(WD_SECTION.NEW_PAGE)
    section.header_distance = Cm(S.HEADER_DISTANCE)
    section.footer_distance = Cm(S.FOOTER_DISTANCE)
    _unlink_hdrftr(section)
    _header_line(section, header_left, header_right)
    _footer_line(section)
    return section


def object_section(doc, obj: dict):
    """Секция объекта: адрес слева, цена справа."""
    return new_section(doc, obj.get("title", ""), obj.get("price_short", ""))


# --------------------------------------------------------------------------
# страницы отчёта
# --------------------------------------------------------------------------
def setup_document() -> Document:
    doc = Document()
    section = doc.sections[0]
    section.page_width = Cm(S.PAGE_WIDTH_CM)
    section.page_height = Cm(S.PAGE_HEIGHT_CM)
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
    """Сводная страница сравнения — сразу после обложки.

    Секцию с шапкой и номером страницы заводит build(): содержательных страниц
    без колонтитула в отчёте быть не должно, без них остаётся только обложка.
    """
    section_heading(doc, summary.get("heading") or S.SUMMARY_HEADING)
    summary_table(doc, summary.get("headers", []), summary.get("rows", []))
    if summary.get("note"):
        paragraph = add_paragraph(doc, before=8, after=0)
        add_run(paragraph, summary["note"], size=S.FS_CAPTION, color=S.MUTED, italic=True)


def _closing_blocks(data: dict) -> list[dict]:
    """Блоки закрывающей страницы, приведённые к виду {'title': …, 'items': […]}.

    Основной формат — «blocks»/«title»/«items». Дополнительно принимается запись
    блоками разделов объекта («sections»/«heading»/«bullets»), чтобы страница
    собиралась независимо от того, каким из двух словарей её описали.
    """
    blocks = data.get("blocks") or data.get("sections") or []
    return [
        {
            "title": block.get("title") or block.get("heading") or "",
            "items": block.get("items") or block.get("bullets") or [],
            "layout": block.get("layout", ""),
            "break_before": block.get("break_before", False),
        }
        for block in blocks
        if isinstance(block, dict)
    ]


def _closing_item(doc, item, *, keep_next: bool = False):
    """Пункт закрывающей страницы; адрес в конце текста становится ссылкой."""
    text = item.get("text", "") if isinstance(item, dict) else str(item)
    head, sep, url = text.partition("https://")
    if not sep:
        return bullet(doc, item, size=S.FS_CLOSING_ITEM, keep_next=keep_next)

    plain = dict(item, text=head) if isinstance(item, dict) else head
    paragraph = bullet(doc, plain, size=S.FS_CLOSING_ITEM, keep_next=keep_next)
    hyperlink(paragraph, sep + url, sep + url, size=S.FS_CLOSING_ITEM)
    return paragraph


def _closing_columns(doc, items: list, *, width: float | None = None) -> None:
    """Компактный двухколоночный список — для глоссария, чтобы не плодить страницы."""
    if not items:
        return
    half = (len(items) + 1) // 2
    columns = [items[:half], items[half:]]
    table = doc.add_table(rows=half, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.LEFT
    table.autofit = False
    table_borders(table, {})
    width = S.CONTENT_WIDTH / 2 if width is None else width
    column_widths(table, [width, width])
    for index in range(half):
        for column, cell in zip(columns, table.rows[index].cells):
            cell.width = Pt(width)
            cell_margins(cell, top=1, bottom=1, left=0, right=8)
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(2)
            if index >= len(column):
                continue
            item = column[index]
            if isinstance(item, dict):
                add_run(
                    paragraph, item.get("lead", ""),
                    size=S.FS_CLOSING_ITEM, color=S.NAVY, bold=True,
                )
                add_run(paragraph, " — ", size=S.FS_CLOSING_ITEM, color=S.BODY)
                add_run(paragraph, item.get("text", ""), size=S.FS_CLOSING_ITEM, color=S.BODY)
            else:
                add_run(paragraph, str(item), size=S.FS_CLOSING_ITEM, color=S.BODY)


def closing_page(doc, data: dict) -> None:
    """Закрывающая страница: заголовок с линейкой, блоки подзаголовков и пунктов.

    data: {'heading': '…',
           'blocks': [{'title': '…', 'items': ['…', {'lead': '…', 'text': '…'}]}],
           'note': '…'}
    """
    section_heading(doc, data.get("heading") or S.CLOSING_HEADING)

    for index, block in enumerate(_closing_blocks(data)):
        if block.get("break_before"):
            page_break(doc)
        elif index:
            card_gap(doc)
        cell = card(doc, block.get("title", ""))
        items = block["items"]
        if block.get("layout") == "columns":
            _closing_columns(cell, items, width=S.CARD_INNER_WIDTH / 2)
            continue
        for position, item in enumerate(items):
            # первый пункт держится за подзаголовком карточки, второй — за первым
            _closing_item(cell, item, keep_next=position == 0 and len(items) > 1)

    if data.get("note"):
        paragraph = add_paragraph(
            doc, before=14, after=0, align=WD_ALIGN_PARAGRAPH.JUSTIFY
        )
        add_run(paragraph, data["note"], size=S.FS_CAPTION, color=S.MUTED, italic=True)


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

    for index, block in enumerate(obj["sections"]):
        if index:
            card_gap(doc)
        card_body(card(doc, block.get("heading", ""), variant=card_variant(block)), block)

    if images:
        page_break(doc)
        photo_pages(doc, images, captions)


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
        # своя секция: сводная страница получает шапку и номер, как у объектов
        new_section(doc, summary.get("heading") or S.SUMMARY_HEADING)
        summary_page(doc, summary)

    for item in objects:
        obj, images, extras = _unpack(item)
        object_section(doc, obj)  # секция начинает новую страницу сама
        object_pages(doc, obj, images, extras)

    closing = report.get("closing")
    if closing:
        new_section(doc, closing.get("heading") or S.CLOSING_HEADING)
        closing_page(doc, closing)

    dest.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(dest))
    log.info("сохранён DOCX: %s", dest)
    return dest
