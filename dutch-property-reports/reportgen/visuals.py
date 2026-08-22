"""Полосы и графика, которые собираются как изображения.

Средствами DOCX нельзя ни положить текст на фотографию, ни нарисовать
диаграмму. А именно это отличает издание от документа: у NYT и Vogue
заголовок живёт на кадре, у Bloomberg график — герой полосы, а не иллюстрация
к абзацу. Поэтому обложка, шмуцтитулы, тёмные полосы и вся графика данных
рисуются здесь в PIL и вставляются в документ навылет.

Ориентиры сняты с самих изданий (разбор CSS главных страниц):

* NYT — пара «дисплейная антиква + гротеск», чернила #121212, разрядка
  надстрочных подписей 0,1 em, интерлиньяж заголовка 1,15;
* Vogue — Didot в крупном кегле, золото #E0C04E, чернила #1A1A1A;
* Bloomberg — тёмный фон, крупные числа, график вместо картинки.

Свободные замены гарнитур: Playfair Display вместо Didot, Inter вместо
гротеска NYT, Spectral для прозы.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

DPI = 250
MM = DPI / 25.4                      # мм → пиксели

FONTS = Path(__file__).resolve().parent.parent / "assets" / "fonts"

# --- палитра ---------------------------------------------------------------
INK = (20, 20, 20)                   # чернила: нейтральный почти-чёрный, как у NYT
INK_DEEP = (12, 12, 12)
PAPER = (244, 241, 234)
PAPER_PURE = (250, 248, 244)
GOLD = (176, 141, 63)
GOLD_BRIGHT = (201, 169, 97)         # золото на тёмном
GREY = (122, 122, 118)
GREY_SOFT = (198, 193, 182)
WHITE = (255, 255, 255)


def font(name: str, size_pt: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / f"{name}.ttf"), int(round(size_pt * DPI / 72)))


DISPLAY = "PlayfairDisplay-Regular"
DISPLAY_MED = "PlayfairDisplay-Medium"
SANS = "Inter-Regular"
SANS_MED = "Inter-Medium"
SANS_LIGHT = "Inter-Light"
SERIF = "Spectral-Light"


# --------------------------------------------------------------------------
# примитивы
# --------------------------------------------------------------------------
def tracked(draw, xy, text, fnt, fill, tracking_em: float = 0.0):
    """Текст с разрядкой. У PIL её нет, поэтому знаки ставятся по одному."""
    x, y = xy
    extra = tracking_em * fnt.size
    for char in text:
        draw.text((x, y), char, font=fnt, fill=fill)
        x += draw.textlength(char, font=fnt) + extra
    return x


def tracked_width(draw, text, fnt, tracking_em: float = 0.0) -> float:
    extra = tracking_em * fnt.size
    return sum(draw.textlength(c, font=fnt) + extra for c in text) - extra


def wrap(draw, text: str, fnt, max_width: float) -> list[str]:
    words, lines, current = text.split(), [], ""
    for word in words:
        probe = f"{current} {word}".strip()
        if draw.textlength(probe, font=fnt) <= max_width or not current:
            current = probe
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def fill_crop(path: Path, width_px: int, height_px: int) -> Image.Image:
    """Кадр, заполняющий прямоугольник целиком, с центральной подрезкой."""
    img = Image.open(path).convert("RGB")
    scale = max(width_px / img.width, height_px / img.height)
    img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))),
                     Image.LANCZOS)
    left = (img.width - width_px) // 2
    top = (img.height - height_px) // 2
    return img.crop((left, top, left + width_px, top + height_px))


def grade(img: Image.Image, *, warmth: float = 1.0, contrast: float = 1.06,
          saturation: float = 0.88) -> Image.Image:
    """Единая обработка кадра: снятая насыщенность и лёгкий тёплый сдвиг.

    Без общей обработки фотографии из разных листингов выглядят как случайная
    подборка; в изданиях кадры всегда приведены к одному тону.
    """
    img = ImageEnhance.Color(img).enhance(saturation)
    img = ImageEnhance.Contrast(img).enhance(contrast)
    if warmth != 1.0:
        r, g, b = img.split()
        r = r.point(lambda v: min(255, int(v * warmth)))
        b = b.point(lambda v: int(v / warmth))
        img = Image.merge("RGB", (r, g, b))
    return img


def scrim(size: tuple[int, int], *, start: float, end: float,
          top_alpha: int = 0, bottom_alpha: int = 235,
          color: tuple[int, int, int] = INK_DEEP) -> Image.Image:
    """Вуаль: вертикальный градиент от прозрачного к плотному.

    Именно она позволяет положить светлый заголовок на любую фотографию и не
    зависеть от того, что на ней — небо или тёмный интерьер.
    """
    width, height = size
    mask = Image.new("L", (1, height), 0)
    pixels = mask.load()
    a, b = int(height * start), int(height * end)
    for y in range(height):
        if y <= a:
            value = top_alpha
        elif y >= b:
            value = bottom_alpha
        else:
            t = (y - a) / max(1, b - a)
            value = int(top_alpha + (bottom_alpha - top_alpha) * (t * t))
        pixels[0, y] = value
    mask = mask.resize((width, height))
    layer = Image.new("RGB", (width, height), color)
    layer.putalpha(mask)
    return layer


def page_canvas(width_mm: float, height_mm: float, color=PAPER) -> Image.Image:
    return Image.new("RGB", (int(width_mm * MM), int(height_mm * MM)), color)


def hairline(draw, x0, y0, x1, y1, color, width_mm: float = 0.25):
    draw.line((x0, y0, x1, y1), fill=color, width=max(1, int(width_mm * MM)))


# --------------------------------------------------------------------------
# полосы
# --------------------------------------------------------------------------
def cover(dest: Path, photo: Path, *, kicker: str, title: str, subtitle: str,
          meta_left: str, meta_right: str,
          page: tuple[float, float] = (200.0, 265.0),
          margin: tuple[float, float] = (17.0, 13.0)) -> Path:
    """Обложка: кадр навылет, вуаль, типографика поверх кадра."""
    width_px, height_px = int(page[0] * MM), int(page[1] * MM)
    left, right = margin[0] * MM, page[0] * MM - margin[1] * MM
    canvas = grade(fill_crop(photo, width_px, height_px), warmth=1.03)
    veil = scrim((width_px, height_px), start=0.05, end=0.74,
                 top_alpha=52, bottom_alpha=248)
    canvas.paste(veil, (0, 0), veil)
    draw = ImageDraw.Draw(canvas)

    # верх: волосяная линейка и надстрочная подпись — приём NYT
    y = 20 * MM
    hairline(draw, left, y, right, y, GOLD_BRIGHT, 0.5)
    kicker_font = font(SANS_MED, 7)
    tracked(draw, (left, y + 4 * MM), kicker.upper(), kicker_font, PAPER_PURE, 0.10)

    # низ: заголовок в дисплейной антикве
    title_font = font(DISPLAY, 44)
    lines = wrap(draw, title, title_font, right - left)
    leading = title_font.size * 1.06
    sub_font = font(SERIF, 12)
    sub_lines = wrap(draw, subtitle, sub_font, (right - left) * 0.82)
    sub_leading = sub_font.size * 1.35

    block = len(lines) * leading + 7 * MM + len(sub_lines) * sub_leading
    y = height_px - 26 * MM - block
    for line in lines:
        draw.text((left, y), line, font=title_font, fill=PAPER_PURE)
        y += leading
    y += 7 * MM
    for line in sub_lines:
        draw.text((left, y), line, font=sub_font, fill=(226, 222, 212))
        y += sub_leading

    # подвал
    y = height_px - 15 * MM
    hairline(draw, left, y - 6 * MM, right, y - 6 * MM, (150, 145, 135), 0.25)
    meta_font = font(SANS, 7)
    draw.text((left, y - 3.5 * MM), meta_left, font=meta_font, fill=(206, 201, 190))
    meta_font_r = font(SANS_MED, 7)
    w = tracked_width(draw, meta_right.upper(), meta_font_r, 0.10)
    tracked(draw, (right - w, y - 3.5 * MM), meta_right.upper(), meta_font_r,
            GOLD_BRIGHT, 0.10)

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=90, subsampling=1)
    return dest


def opener(dest: Path, photo: Path, *, ordinal: str, city: str, title: str,
           subtitle: str, kpi: list[tuple[str, str]],
           page: tuple[float, float] = (200.0, 265.0),
           margin: tuple[float, float] = (17.0, 13.0)) -> Path:
    """Шмуцтитул объекта: номер и название на кадре, полоса цифр по низу."""
    width_px, height_px = int(page[0] * MM), int(page[1] * MM)
    left, right = margin[0] * MM, page[0] * MM - margin[1] * MM
    canvas = grade(fill_crop(photo, width_px, height_px), warmth=1.02)
    veil = scrim((width_px, height_px), start=0.10, end=0.68,
                 top_alpha=70, bottom_alpha=243)
    canvas.paste(veil, (0, 0), veil)
    draw = ImageDraw.Draw(canvas)

    y = 20 * MM
    hairline(draw, left, y, right, y, GOLD_BRIGHT, 0.5)
    tracked(draw, (left, y + 4 * MM), city.upper(), font(SANS_MED, 7), PAPER_PURE, 0.10)

    ordinal_font = font(DISPLAY, 84)
    title_font = font(DISPLAY, 34)
    lines = wrap(draw, title, title_font, right - left - 4 * MM)
    leading = title_font.size * 1.08

    box = draw.textbbox((0, 0), ordinal, font=ordinal_font)
    ordinal_h = box[3] - box[1]
    strip_top = height_px - 40 * MM
    block = ordinal_h + 9 * MM + len(lines) * leading + 6 * MM
    y = strip_top - 14 * MM - block
    draw.text((left - 0.6 * MM - box[0], y - box[1]), ordinal,
              font=ordinal_font, fill=GOLD_BRIGHT)
    y += ordinal_h + 9 * MM
    for line in lines:
        draw.text((left, y), line, font=title_font, fill=PAPER_PURE)
        y += leading
    y += 3.5 * MM
    draw.text((left, y), subtitle, font=font(SANS_LIGHT, 8), fill=(212, 207, 197))

    # полоса ключевых цифр по нижнему краю
    hairline(draw, left, strip_top, right, strip_top, (150, 145, 135), 0.25)
    step = (right - left) / len(kpi)
    label_font, value_font = font(SANS_MED, 6.2), font(SANS_LIGHT, 17)
    for index, (label, value) in enumerate(kpi):
        x = left + index * step
        if index:
            hairline(draw, x - 4 * MM, strip_top + 5 * MM, x - 4 * MM,
                     strip_top + 22 * MM, (120, 116, 108), 0.25)
        tracked(draw, (x, strip_top + 6 * MM), label.upper(), label_font,
                (196, 191, 181), 0.10)
        draw.text((x, strip_top + 11 * MM), value, font=value_font, fill=PAPER_PURE)

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=90, subsampling=1)
    return dest


def statement(dest: Path, *, kicker: str, text: str, figures: list[tuple[str, str, str]],
              page: tuple[float, float] = (200.0, 265.0),
              margin: tuple[float, float] = (17.0, 13.0)) -> Path:
    """Тёмная полоса-манифест: короткое утверждение и крупные числа."""
    width_px, height_px = int(page[0] * MM), int(page[1] * MM)
    left, right = margin[0] * MM, page[0] * MM - margin[1] * MM
    canvas = Image.new("RGB", (width_px, height_px), INK_DEEP)
    draw = ImageDraw.Draw(canvas)

    y = 26 * MM
    hairline(draw, left, y, right, y, GOLD_BRIGHT, 0.5)
    tracked(draw, (left, y + 4 * MM), kicker.upper(), font(SANS_MED, 7),
            GOLD_BRIGHT, 0.10)

    text_font = font(DISPLAY, 27)
    lines = wrap(draw, text, text_font, right - left)
    y = 48 * MM
    for line in lines:
        draw.text((left, y), line, font=text_font, fill=PAPER_PURE)
        y += text_font.size * 1.22

    y = height_px - 52 * MM - len(figures) * 26 * MM
    label_font = font(SANS_MED, 6.4)
    value_font = font(SANS_LIGHT, 30)
    note_font = font(SANS, 7.5)
    for label, value, note in figures:
        hairline(draw, left, y, right, y, (70, 68, 64), 0.25)
        tracked(draw, (left, y + 4 * MM), label.upper(), label_font, (150, 146, 138), 0.10)
        draw.text((left, y + 9 * MM), value, font=value_font, fill=PAPER_PURE)
        w = draw.textlength(note, font=note_font)
        draw.text((right - w, y + 15 * MM), note, font=note_font, fill=(150, 146, 138))
        y += 26 * MM

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=92, subsampling=1)
    return dest
