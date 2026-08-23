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
    """Текст с необязательной разрядкой.

    Разрядка прописных из оформления убрана целиком, поэтому по умолчанию
    функция просто рисует строку; параметр оставлен на случай единичных
    исключений.
    """
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
    tracked(draw, (left, y + 4 * MM), kicker.upper(), kicker_font, PAPER_PURE)

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
    w = tracked_width(draw, meta_right.upper(), meta_font_r)
    tracked(draw, (right - w, y - 3.5 * MM), meta_right.upper(), meta_font_r,
            GOLD_BRIGHT)

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
    tracked(draw, (left, y + 4 * MM), city.upper(), font(SANS_MED, 7), PAPER_PURE)

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
                (196, 191, 181))
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
            GOLD_BRIGHT)

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
        tracked(draw, (left, y + 4 * MM), label.upper(), label_font, (150, 146, 138))
        draw.text((left, y + 9 * MM), value, font=value_font, fill=PAPER_PURE)
        w = draw.textlength(note, font=note_font)
        draw.text((right - w, y + 15 * MM), note, font=note_font, fill=(150, 146, 138))
        y += 26 * MM

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=92, subsampling=1)
    return dest


def rounded_photo(dest: Path, source: Path, *, width_mm: float, ratio: float,
                  radius_mm: float = 2.6, pad_mm: float = 4.0,
                  background=PAPER) -> Path:
    """Кадр со скруглёнными углами и мягкой тенью.

    Тень рисуется прямо в файле, на фоне цвета бумаги: DOCX не умеет ни
    скруглять углы встроенной картинки, ни давать ей тень, а вставка
    изображения с уже готовым фоном ложится на полосу бесшовно.
    """
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        return dest

    inner_w = int((width_mm - 2 * pad_mm) * MM)
    inner_h = int(inner_w / ratio)
    pad = int(pad_mm * MM)
    radius = int(radius_mm * MM)
    canvas = Image.new("RGB", (inner_w + 2 * pad, inner_h + 2 * pad), background)

    # тень: скруглённый прямоугольник, размытый и смещённый вниз
    shadow = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (pad, pad + int(0.9 * MM), pad + inner_w, pad + inner_h + int(0.9 * MM)),
        radius=radius, fill=104,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(1.5 * MM)))
    canvas.paste(Image.new("RGB", canvas.size, (58, 54, 48)), (0, 0), shadow)

    photo_img = grade(fill_crop(source, inner_w, inner_h), warmth=1.02)
    mask = Image.new("L", (inner_w, inner_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, inner_w - 1, inner_h - 1),
                                           radius=radius, fill=255)
    canvas.paste(photo_img, (pad, pad), mask)
    canvas.save(dest, quality=92, subsampling=1)
    return dest


def contents_cover(dest: Path, *, kicker: str, title: str, subtitle: str,
                   items: list[tuple[str, str, str, Path]], meta: str,
                   page: tuple[float, float] = (200.0, 265.0),
                   margin: tuple[float, float] = (17.0, 13.0)) -> Path:
    """Обложка: шапка и список объектов с ценами — и больше ничего.

    Каждая строка списка — номер, миниатюра со скруглением, адрес, город и
    цена: содержания ровно столько, сколько просил заказчик, а плотность
    держится на типографике и кадрах, а не на дополнительных сведениях.
    """
    width_px, height_px = int(page[0] * MM), int(page[1] * MM)
    left, right = margin[0] * MM, page[0] * MM - margin[1] * MM
    canvas = Image.new("RGB", (width_px, height_px), PAPER)
    draw = ImageDraw.Draw(canvas)

    y = 22 * MM
    hairline(draw, left, y, right, y, GOLD, 0.6)
    tracked(draw, (left, y + 4.6 * MM), kicker.upper(), font(SANS_MED, 7), GOLD)

    title_font = font(DISPLAY, 47)
    y = 40 * MM
    for line in wrap(draw, title, title_font, right - left):
        draw.text((left, y), line, font=title_font, fill=INK)
        y += title_font.size * 1.03

    sub_font = font(SERIF, 11.5)
    y += 9 * MM
    for line in wrap(draw, subtitle, sub_font, (right - left) * 0.86):
        draw.text((left, y), line, font=sub_font, fill=(88, 88, 84))
        y += sub_font.size * 1.34
    subtitle_end = y

    # список объектов: низ списка привязан к подвалу, высота строки
    # подбирается так, чтобы список не наехал на подзаголовок
    bottom = height_px - 32 * MM
    row_h = min(46 * MM, (bottom - subtitle_end - 16 * MM) / len(items))
    # миниатюра выводится из высоты строки, иначе при сжатой строке она
    # перекрывает линейку, отделяющую следующий объект
    inset = 5.5 * MM
    thumb_h = row_h - 2 * inset
    thumb_w = thumb_h * 1.52
    top = bottom - len(items) * row_h
    tracked(draw, (left, top - 8 * MM), "В подборку входят".upper(),
            font(SANS_MED, 6.5), GOLD)
    ordinal_font = font(DISPLAY, 21)
    name_font = font(DISPLAY, 17)
    city_font = font(SANS, 7.6)
    price_font = font(SANS_LIGHT, 17)

    for index, (name, city, price, photo_path) in enumerate(items, start=1):
        y = top + (index - 1) * row_h
        hairline(draw, left, y, right, y, GREY_SOFT, 0.25)

        draw.text((left, y + row_h / 2 - 5 * MM), f"{index:02d}",
                  font=ordinal_font, fill=GOLD)

        thumb_x = left + 16 * MM
        thumb = grade(fill_crop(photo_path, int(thumb_w), int(thumb_h)), warmth=1.02)
        mask = Image.new("L", thumb.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            (0, 0, thumb.size[0] - 1, thumb.size[1] - 1), radius=int(2.6 * MM), fill=255)
        shadow = Image.new("L", canvas.size, 0)
        ImageDraw.Draw(shadow).rounded_rectangle(
            (thumb_x, y + inset + int(0.8 * MM), thumb_x + thumb_w,
             y + inset + thumb_h + int(0.8 * MM)), radius=int(2.6 * MM), fill=90)
        shadow = shadow.filter(ImageFilter.GaussianBlur(int(1.2 * MM)))
        canvas.paste(Image.new("RGB", canvas.size, (58, 54, 48)), (0, 0), shadow)
        canvas.paste(thumb, (int(thumb_x), int(y + inset)), mask)

        text_x = thumb_x + thumb_w + 9 * MM
        price_w = draw.textlength(price, font=price_font)
        # длинное название не должно наезжать на цену — кегль подбирается
        room = right - price_w - 7 * MM - text_x
        fitted = name_font
        for size in (17, 15.5, 14, 12.5, 11):
            fitted = font(DISPLAY, size)
            if draw.textlength(name, font=fitted) <= room:
                break
        middle = y + row_h / 2
        draw.text((text_x, middle - 6 * MM), name, font=fitted, fill=INK)
        draw.text((text_x, middle + 2 * MM), city, font=city_font, fill=GREY)
        draw.text((right - price_w, middle - 5.5 * MM), price, font=price_font, fill=INK)

    y = height_px - 20 * MM
    hairline(draw, left, y, right, y, GREY_SOFT, 0.25)
    draw.text((left, y + 3.4 * MM), meta, font=font(SANS, 7), fill=GREY)
    count = f"{len(items)} объекта"
    w = tracked_width(draw, count.upper(), font(SANS_MED, 7))
    tracked(draw, (right - w, y + 3.4 * MM), count.upper(), font(SANS_MED, 7), GOLD)

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=94, subsampling=1)
    return dest


def cards_panel(dest: Path, cards: list[list[str]], *, width_mm: float,
                columns: int = 3, gap_mm: float = 4.5,
                max_height_mm: float | None = None) -> tuple[Path, float]:
    """Сетка карточек с характеристиками — одним изображением.

    Ячейка таблицы DOCX не умеет скругляться и не умеет отбрасывать тень,
    поэтому карточки рисуются так же, как фотографии: скруглённый прямоугольник
    с мягкой тенью на бумажном фоне. Возвращается путь и фактическая высота в
    миллиметрах, чтобы вызывающий код знал, сколько места занял блок.
    """
    card_w = (width_mm - gap_mm * (columns - 1)) / columns
    rows = -(-len(cards) // columns)

    pad = 6.6
    label_font = font(SANS_MED, 7.0)
    note_font = font(SANS, 7.0)
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    inner_px = (card_w - 2 * pad) * MM

    def value_font_for(text: str):
        for size in (19.0, 16.5, 14.0, 12.0, 10.5):
            candidate = font(DISPLAY, size)
            if probe.textlength(text, font=candidate) <= inner_px:
                return candidate, size
        return font(DISPLAY, 10.0), 10.0

    prepared = []
    note_lines_max = 1
    for entry in cards:
        label, value, note = (list(entry) + ["", "", ""])[:3]
        value_fnt, value_size = value_font_for(value)
        lines = wrap(probe, note, note_font, inner_px) if note else []
        note_lines_max = max(note_lines_max, len(lines))
        prepared.append((label, value, value_fnt, value_size, lines))

    label_h, value_gap, note_gap, note_lh = 4.0, 2.6, 2.4, 3.7
    value_h = max(size for _, _, _, size, _ in prepared) * 25.4 / 72 * 1.02

    def height_for(padding: float) -> float:
        return (padding * 2 + label_h + value_gap + value_h
                + note_gap + note_lines_max * note_lh)

    # высота карточки подгоняется полями, а не обрезкой: при жёстком лимите
    # уточнение вылезало из плашки и печаталось прямо на бумаге
    card_h = height_for(pad)
    if max_height_mm:
        available = (max_height_mm - gap_mm * (rows - 1)) / rows
        while card_h > available and pad > 3.4:
            pad -= 0.3
            card_h = height_for(pad)
        card_h = max(card_h, height_for(3.4))
    total_h = rows * card_h + (rows - 1) * gap_mm

    canvas = Image.new("RGB", (int(width_mm * MM), int(total_h * MM) + 2), PAPER)
    radius = int(2.6 * MM)

    shadow = Image.new("L", canvas.size, 0)
    shadow_draw = ImageDraw.Draw(shadow)
    boxes = []
    for index in range(len(prepared)):
        x = (index % columns) * (card_w + gap_mm) * MM
        y = (index // columns) * (card_h + gap_mm) * MM
        boxes.append((x, y))
        shadow_draw.rounded_rectangle(
            (x, y + 0.7 * MM, x + card_w * MM, y + (card_h + 0.7) * MM),
            radius=radius, fill=76)
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(1.2 * MM)))
    canvas.paste(Image.new("RGB", canvas.size, (72, 68, 60)), (0, 0), shadow)

    draw = ImageDraw.Draw(canvas)
    for (label, value, value_fnt, value_size, lines), (x, y) in zip(prepared, boxes):
        draw.rounded_rectangle((x, y, x + card_w * MM, y + card_h * MM),
                               radius=radius, fill=(233, 229, 218))
        text_x = x + pad * MM
        cursor = y + pad * MM
        draw.text((text_x, cursor), label.upper(), font=label_font, fill=GOLD)
        cursor += (label_h + value_gap) * MM
        draw.text((text_x, cursor), value, font=value_fnt, fill=INK)
        # величина кегля берётся в пунктах: у шрифта PIL .size хранится в
        # пикселях, и подстановка его сюда уводила уточнение за низ карточки
        cursor += (value_size * 25.4 / 72 * 1.02 + note_gap) * MM
        for line in lines:
            draw.text((text_x, cursor), line, font=note_font, fill=GREY)
            cursor += note_lh * MM

    dest.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(dest, quality=95, subsampling=1)
    return dest, total_h
