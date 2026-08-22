"""Графика данных отчёта.

Рисуется вручную в PIL, а не берётся из библиотеки построения графиков: нужен
именно тот минимализм, который держат FT и Bloomberg — ни рамки, ни сетки, ни
легенды. Подпись стоит рядом со своим объектом, значение — у конца столбика,
единственная линия на полосе это базовая ось. Фон совпадает с бумагой полосы,
поэтому график не выглядит вклеенной картинкой.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

from .visuals import (
    GOLD,
    GREY,
    GREY_SOFT,
    INK,
    MM,
    PAPER,
    font,
    hairline,
    tracked,
    tracked_width,
    SANS,
    SANS_LIGHT,
    SANS_MED,
)

BAR_INK = (58, 58, 56)
BAR_SOFT = (206, 201, 190)


def _canvas(width_mm: float, height_mm: float):
    image = Image.new("RGB", (int(width_mm * MM), int(height_mm * MM)), PAPER)
    return image, ImageDraw.Draw(image)


def _caption(draw, x, y, text: str, width: float):
    tracked(draw, (x, y), text.upper(), font(SANS_MED, 6.2), GOLD, 0.10)
    hairline(draw, x, y + 4.4 * MM, x + width, y + 4.4 * MM, GREY_SOFT, 0.25)


def yield_bars(dest: Path, rows: list[tuple[str, float, str]], *,
               highlight: str | None = None, average: float | None = None,
               title: str = "Доходность объектов подборки",
               width_mm: float = 170.0, row_mm: float = 13.0) -> Path:
    """Горизонтальные столбики доходности с линией средней по подборке."""
    height_mm = 12 + len(rows) * row_mm + 10
    image, draw = _canvas(width_mm, height_mm)
    _caption(draw, 0, 0, title, width_mm * MM)

    label_w = 46 * MM
    value_w = 18 * MM
    plot_x0 = label_w
    plot_x1 = (width_mm * MM) - value_w
    top = 12 * MM
    peak = max(value for _, value, _ in rows) * 1.12

    if average is not None:
        x = plot_x0 + (plot_x1 - plot_x0) * average / peak
        for y in range(int(top - 2 * MM), int(top + len(rows) * row_mm * MM), int(2.2 * MM)):
            draw.line((x, y, x, y + 1.1 * MM), fill=GOLD, width=max(1, int(0.3 * MM)))
        note = f"средняя {average:.1f} %".replace(".", ",")
        draw.text((x + 1.6 * MM, top - 5.4 * MM), note, font=font(SANS, 6.4), fill=GOLD)

    label_font = font(SANS_MED, 7.6)
    note_font = font(SANS, 6.4)
    value_font = font(SANS_LIGHT, 12)
    for index, (name, value, note) in enumerate(rows):
        y = top + index * row_mm * MM
        colour = GOLD if name == highlight else BAR_INK
        draw.text((0, y + 0.4 * MM), name, font=label_font, fill=INK)
        if note:
            draw.text((0, y + 4.6 * MM), note, font=note_font, fill=GREY)
        bar = (plot_x1 - plot_x0) * value / peak
        draw.rectangle((plot_x0, y + 0.6 * MM, plot_x0 + bar,
                        y + row_mm * 0.52 * MM), fill=colour)
        text = f"{value:.1f} %".replace(".", ",")
        w = draw.textlength(text, font=value_font)
        draw.text((width_mm * MM - w - 1.5 * MM, y - 0.4 * MM), text,
                  font=value_font, fill=INK)

    base = top + len(rows) * row_mm * MM - 4 * MM
    hairline(draw, plot_x0, base, plot_x1, base, GREY_SOFT, 0.25)
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, quality=95)
    return dest


def position(dest: Path, points: list[tuple[str, float, float, float]], *,
             title: str = "Цена за квадратный метр и доходность",
             x_label: str = "Цена за м², €",
             y_label: str = "Доходность, %",
             width_mm: float = 170.0, height_mm: float = 92.0) -> Path:
    """Положение объектов в координатах «цена за метр — доходность».

    Площадь кружка пропорциональна площади объекта: три числа на одной полосе
    без легенды и без таблицы.
    """
    image, draw = _canvas(width_mm, height_mm)
    _caption(draw, 0, 0, title, width_mm * MM)

    x0, x1 = 20 * MM, (width_mm - 6) * MM
    y0, y1 = 24 * MM, (height_mm - 16) * MM
    xs = [p[1] for p in points]
    ys = [p[2] for p in points]
    xmin, xmax = min(xs) * 0.75, max(xs) * 1.12
    ymin, ymax = min(ys) * 0.88, max(ys) * 1.08
    areas = [p[3] for p in points]
    amax = max(areas)

    hairline(draw, x0, y1, x1, y1, GREY_SOFT, 0.25)
    hairline(draw, x0, y0, x0, y1, GREY_SOFT, 0.25)
    tick_font = font(SANS, 6.2)
    for value in (ymin + (ymax - ymin) * t for t in (0.0, 0.5, 1.0)):
        y = y1 - (y1 - y0) * (value - ymin) / (ymax - ymin)
        draw.text((0, y - 1.4 * MM), f"{value:.1f}".replace(".", ","),
                  font=tick_font, fill=GREY)
        if value > ymin:
            for x in range(int(x0), int(x1), int(2.4 * MM)):
                draw.line((x, y, x + 1.2 * MM, y), fill=(224, 219, 208),
                          width=max(1, int(0.25 * MM)))
    for value in (xmin + (xmax - xmin) * t for t in (0.15, 0.5, 0.85)):
        x = x0 + (x1 - x0) * (value - xmin) / (xmax - xmin)
        draw.text((x - 4 * MM, y1 + 2.2 * MM), f"{int(round(value / 50) * 50):,}".replace(",", "."),
                  font=tick_font, fill=GREY)

    label_font = font(SANS_MED, 7.2)
    note_font = font(SANS, 6.2)
    for name, px, py, area in points:
        x = x0 + (x1 - x0) * (px - xmin) / (xmax - xmin)
        y = y1 - (y1 - y0) * (py - ymin) / (ymax - ymin)
        radius = (3.0 + 6.0 * (area / amax) ** 0.5) * MM
        draw.ellipse((x - radius, y - radius, x + radius, y + radius),
                     fill=(228, 222, 210), outline=BAR_INK, width=max(1, int(0.3 * MM)))
        draw.ellipse((x - 1.1 * MM, y - 1.1 * MM, x + 1.1 * MM, y + 1.1 * MM), fill=GOLD)
        area_text = f"{int(area):,} м²".replace(",", ".")
        name_w = draw.textlength(name, font=label_font)
        area_w = draw.textlength(area_text, font=note_font)
        text_x = min(max(x - name_w / 2, 0.0), width_mm * MM - name_w)
        draw.text((text_x, y - radius - 8.4 * MM), name, font=label_font, fill=INK)
        draw.text((text_x + (name_w - area_w) / 2, y - radius - 4.4 * MM),
                  area_text, font=note_font, fill=GREY)

    tracked(draw, (x0, height_mm * MM - 6 * MM), x_label.upper(),
            font(SANS_MED, 6.0), GREY, 0.10)
    tracked(draw, (0, 9.5 * MM), y_label.upper(), font(SANS_MED, 6.0), GREY, 0.10)
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, quality=95)
    return dest


def split_bar(dest: Path, parts: list[tuple[str, float]], *, title: str,
              unit: str = "", total_note: str = "",
              width_mm: float = 170.0) -> Path:
    """Одна составная полоса: из чего складывается доход или площадь."""
    height_mm = 12 + 14 + 7 * len(parts) + 6
    image, draw = _canvas(width_mm, height_mm)
    _caption(draw, 0, 0, title, width_mm * MM)

    total = sum(value for _, value in parts) or 1.0
    x0, x1 = 0.0, width_mm * MM
    share_w = 11 * MM
    top = 12 * MM
    bar_h = 9 * MM
    shades = [BAR_INK, GOLD, (146, 142, 134), (196, 191, 180)]
    x = x0
    for index, (_, value) in enumerate(parts):
        span = (x1 - x0) * value / total
        draw.rectangle((x, top, x + span, top + bar_h), fill=shades[index % len(shades)])
        x += span

    y = top + bar_h + 5 * MM
    label_font = font(SANS_MED, 7.2)
    value_font = font(SANS, 7.2)
    for index, (name, value) in enumerate(parts):
        colour = shades[index % len(shades)]
        draw.rectangle((0, y + 1.0 * MM, 2.4 * MM, y + 3.4 * MM), fill=colour)
        draw.text((4 * MM, y), name, font=label_font, fill=INK)
        share = f"{value / total * 100:.0f} %"
        text = f"{value:,.0f}".replace(",", ".") + (f" {unit}" if unit else "")
        w = draw.textlength(share, font=value_font)
        draw.text((x1 - w - 1.5 * MM, y), share, font=value_font, fill=GREY)
        w2 = draw.textlength(text, font=value_font)
        draw.text((x1 - share_w - 4.5 * MM - w2, y), text, font=value_font, fill=INK)
        y += 7 * MM

    if total_note:
        draw.text((0, y + 0.5 * MM), total_note, font=font(SANS, 6.4), fill=GREY)
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, quality=95)
    return dest
