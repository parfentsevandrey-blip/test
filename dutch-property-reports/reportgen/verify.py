"""Проверка готового PDF на соответствие формату.

Формат держится не на договорённости, а на проверке: правки в вёрстке легко
ломают то, что чинили неделю назад — полоса перестаёт доходить до нижнего
поля, между разделами появляется пустая страница, в набор пролезает чужая
гарнитура. Здесь собраны те требования, которые можно проверить машинно.

    python generate_report.py check "output/Отчёт.pdf"
"""

from __future__ import annotations

from pathlib import Path

import fitz

from . import style_editorial as S

# Гарнитуры формата: всё остальное в наборе — ошибка
FAMILIES = ("SourceSerif4", "SourceSans3")

TOLERANCE_MM = 1.0      # допуск на формат листа
BOTTOM_FREE_MM = 26.0   # сколько белого поля внизу полосы считается пробелом
CAPTION = "Расположение объекта"


def _mm(points: float) -> float:
    return points / 72 * 25.4


def _check_page(page, number: int) -> list[str]:
    problems = []
    width, height = _mm(page.rect.width), _mm(page.rect.height)
    if (abs(width - S.PAGE_W_MM) > TOLERANCE_MM
            or abs(height - S.PAGE_H_MM) > TOLERANCE_MM):
        problems.append(f"полоса {number}: формат {width:.0f}×{height:.0f} мм, "
                        f"а не {S.PAGE_W_MM:.0f}×{S.PAGE_H_MM:.0f}")

    for font in page.get_fonts(full=True):
        name = font[3].split("+")[-1]
        if not name.startswith(FAMILIES):
            problems.append(f"полоса {number}: чужая гарнитура {name}")

    limit = S.PAGE_H_MM - S.MARGIN_BOTTOM_MM
    blocks = [b for b in page.get_text("blocks")
              if b[4].strip() and _mm(b[1]) < limit]
    frames = [r for r in (page.get_image_bbox(image)
                          for image in page.get_images(full=True))
              if r.y1 > r.y0]
    bleed = any(_mm(r.y1 - r.y0) > S.PAGE_H_MM - 2 for r in frames)

    if not blocks and not frames:
        problems.append(f"полоса {number}: пустая")
        return problems
    if bleed:
        return problems                      # шмуцтитул и обложка идут навылет

    bottom = max([_mm(b[3]) for b in blocks] + [_mm(r.y1) for r in frames])
    if limit - bottom > BOTTOM_FREE_MM:
        problems.append(f"полоса {number}: снизу пусто {limit - bottom:.0f} мм")
    return problems


def check(path: Path) -> list[str]:
    """Список несоответствий формату; пустой список — всё в порядке."""
    document = fitz.open(str(path))
    problems: list[str] = []
    maps = 0
    for number, page in enumerate(document, start=1):
        problems.extend(_check_page(page, number))
        if CAPTION in page.get_text():
            maps += 1
    if not maps:
        problems.append("ни на одной полосе нет карты расположения")
    return problems
