"""Графики флип-модели в стиле отчёта: без рамок и сетки, подписи у объектов.

python3 -I charts_flip.py KERNWEG_JSON COMPS_JSON OUT_DIR
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
sys.path.append("/root/.local/lib/python3.11/site-packages")
from PIL import Image, ImageDraw  # noqa: E402

from reportgen.visuals import (GOLD, GREY, GREY_SOFT, INK, MM, PAPER, SANS, SANS_LIGHT, SANS_MED,  # noqa: E402
                               font, hairline, tracked)

K = json.loads(Path(sys.argv[1]).read_text())
C = json.loads(Path(sys.argv[2]).read_text())
OUT = Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)
BAR_INK = (58, 58, 56)
SOFT = (206, 201, 190)
RED = (150, 62, 50)
W = 170.0


def canvas(h):
    img = Image.new("RGB", (int(W * MM), int(h * MM)), PAPER)
    return img, ImageDraw.Draw(img)


def caption(d, text):
    tracked(d, (0, 0), text.upper(), font(SANS_MED, 6.2), GOLD)
    hairline(d, 0, 4.4 * MM, W * MM, 4.4 * MM, GREY_SOFT, 0.25)


def eur(v, k=False):
    if k:
        s = f"{v / 1000:,.0f}".replace(",", ".")
        return ("−" if v < 0 else "") + "€ " + s.lstrip("-") + " тыс."
    s = f"{abs(v):,.0f}".replace(",", ".")
    return ("−" if v < 0 else "") + "€ " + s


def dashed_h(d, x0, x1, y, colour):
    x = x0
    while x < x1:
        d.line((x, y, min(x + 1.2 * MM, x1), y), fill=colour, width=max(1, int(0.25 * MM)))
        x += 2.4 * MM


# ------------------------------------------------------------------ накопленный поток
def cash():
    h = 100
    img, d = canvas(h)
    caption(d, "Накопленный денежный поток собственника по месяцам")
    series = [("Продажа через 12 месяцев", K["base"]["rows"], BAR_INK),
              ("Продажа через 6 месяцев", K["fast"]["rows"], GOLD)]
    curves = []
    for label, rows, colour in series:
        cum, pts = 0.0, []
        for r in rows:
            cum += r["equity"]
            pts.append((r["m"], cum))
        curves.append((label, pts, colour))
    lo = min(v for _, pts, _ in curves for _, v in pts)
    hi = max(v for _, pts, _ in curves for _, v in pts)
    lo, hi = lo * 1.08, max(hi, 0) + (hi - lo) * 0.12
    x0, x1 = 22 * MM, (W - 40) * MM
    y0, y1 = 12 * MM, (h - 10) * MM

    def X(m):
        return x0 + (x1 - x0) * m / 12

    def Y(v):
        return y1 - (y1 - y0) * (v - lo) / (hi - lo)
    hairline(d, x0, Y(0), x1, Y(0), INK, 0.3)
    tick = font(SANS, 6.2)
    for v in (-1_200_000, -800_000, -400_000):
        if v > lo:
            dashed_h(d, x0, x1, Y(v), (224, 219, 208))
            d.text((0, Y(v) - 1.6 * MM), eur(v, True), font=tick, fill=GREY)
    d.text((0, Y(0) - 1.6 * MM), "€ 0", font=tick, fill=GREY)
    for m in range(0, 13, 2):
        t = str(m)
        d.text((X(m) - d.textlength(t, font=tick) / 2, y1 + 2.2 * MM), t, font=tick, fill=GREY)
    tracked(d, (x0, h * MM - 4.5 * MM), "МЕСЯЦ ПОСЛЕ ПОКУПКИ", font(SANS_MED, 5.8), GREY)
    lab = font(SANS_MED, 7.0)
    val = font(SANS_LIGHT, 11)
    for i, (label, pts, colour) in enumerate(curves):
        xy = [(X(m), Y(v)) for m, v in pts]
        # ступенчатая линия: поток приходит в конце месяца
        step = []
        for j, (x, y) in enumerate(xy):
            if j:
                step.append((x, step[-1][1]))
            step.append((x, y))
        d.line(step, fill=colour, width=int(0.55 * MM))
        ex, ey = xy[-1]
        d.ellipse((ex - 1.2 * MM, ey - 1.2 * MM, ex + 1.2 * MM, ey + 1.2 * MM), fill=colour)
        profit = pts[-1][1]
        tx = ex + 3 * MM
        ty = ey - 5.5 * MM - (6 * MM if i == 1 else 0) + (0 if i == 1 else 2 * MM)
        d.text((tx, ty), "+" + eur(profit, True).replace("€", "€"), font=val, fill=colour)
        d.text((tx, ty + 5.0 * MM), label.lower(), font=font(SANS, 6.2), fill=GREY)
    # пик вложений
    pts = curves[0][1]
    pm, pv = min(pts, key=lambda t: t[1])
    for k, line in enumerate((f"больше всего своих денег — {eur(-pv, True)}",
                              f"на {pm}-м месяце: цена, налог 10,4 %,",
                              "расходы на сделку, ремонт, содержание")):
        d.text((X(6.5), Y(pv) - (20 - 3.6 * k) * MM), line, font=font(SANS, 6.2), fill=GREY)
    img.save(OUT / "cash.png")


# ------------------------------------------------------------------ график работ
def timeline():
    rows = [
        ("Покупка у нотариуса, налог 10,4 %", [(0, 0.35, BAR_INK)], [(0, 0.35, GOLD)]),
        ("Ремонт: покраска, свет, два входа", [(0.35, 2, SOFT)], [(0.35, 2, SOFT)]),
        ("Поиск арендатора и договор", [(0, 4, SOFT)], [(-1, 2, SOFT)]),
        ("Арендатор въехал, каникулы", [(4, 7, (176, 170, 158))], [(2, 4, (176, 170, 158))]),
        ("Аренда платится", [(7, 12, BAR_INK)], [(4, 6, GOLD)]),
        ("Продажа: реклама, предложения", [(7, 10, SOFT)], [(3, 4.2, SOFT)]),
        ("Проверка покупателем и нотариус", [(10, 12, BAR_INK)], [(4.2, 6, GOLD)]),
    ]
    h = 14 + len(rows) * 12.0 + 14
    img, d = canvas(h)
    caption(d, "Сроки: продажа через 12 месяцев и ускоренная через 6")
    x0, x1 = 58 * MM, (W - 2) * MM

    def X(m):
        return x0 + (x1 - x0) * (m + 1) / 13
    top = 14 * MM
    lab = font(SANS_MED, 6.8)
    sub = font(SANS, 6.0)
    for m in range(-1, 13):
        x = X(m)
        t = str(m) if m >= 0 else "до"
        d.text((x - d.textlength(t, font=sub) / 2, top - 5.2 * MM), t, font=sub, fill=GREY)
    six = X(6)
    for y in range(int(top - 1 * MM), int(top + len(rows) * 12 * MM), int(2.2 * MM)):
        d.line((six, y, six, y + 1.1 * MM), fill=GOLD, width=max(1, int(0.3 * MM)))
    for i, (label, a, b) in enumerate(rows):
        y = top + i * 12 * MM
        d.text((0, y + 1.6 * MM), label, font=lab, fill=INK)
        for (s, e, col) in a:
            d.rectangle((X(s), y + 0.8 * MM, X(e), y + 4.6 * MM), fill=col)
        for (s, e, col) in b:
            d.rectangle((X(s), y + 6.2 * MM, X(e), y + 9.2 * MM), fill=col if col != BAR_INK else GOLD)
        hairline(d, 0, y + 11 * MM, x1, y + 11 * MM, (230, 226, 218), 0.2)
    y = top + len(rows) * 12 * MM + 2 * MM
    d.rectangle((0, y + 0.6 * MM, 4 * MM, y + 3.6 * MM), fill=BAR_INK)
    d.text((5.5 * MM, y), "верхняя полоса — продажа через 12 месяцев", font=sub, fill=GREY)
    d.rectangle((75 * MM, y + 0.9 * MM, 79 * MM, y + 3.3 * MM), fill=GOLD)
    d.text((80.5 * MM, y), "нижняя — через 6 месяцев", font=sub, fill=GREY)
    d.text((0, y + 5 * MM), "Пунктир — 6 месяцев после нашей покупки: до этой даты покупатель платит налог 10,4 % только с прироста цены",
           font=sub, fill=GREY)
    img.save(OUT / "timeline.png")


# ------------------------------------------------------------------ откуда прибыль
def bridge():
    b = K["base"]
    acq = b["acq"]
    other = sum(v for k, v in acq.items() if k not in ("Цена покупки", "Налог на передачу 10,4 %"))
    t = b["tot"]
    items = [
        ("Продажа сданного здания", b["sale"], "in"),
        ("Аренда до продажи", t["rent"], "in"),
        ("Цена покупки", -acq["Цена покупки"], "out"),
        ("Налог на передачу 10,4 %", -acq["Налог на передачу 10,4 %"], "out"),
        ("Нотариус, проверка, консультант", -other, "out"),
        ("Ремонт с НДС", t["capex"], "out"),
        ("Брокер по аренде и реклама", t["letting"], "out"),
        ("Содержание и простой", t["hold"] + t["void"], "out"),
        ("Брокер и юрист на продаже", t["sale_cost"], "out"),
        ("Прибыль до налога", b["profit"], "res"),
        ("Налог на прибыль 19 %", -b["vpb"], "out"),
        ("Прибыль после налога", b["profit_after"], "res"),
    ]
    row_mm = 7.4
    h = 12 + len(items) * row_mm + 6
    img, d = canvas(h)
    caption(d, "Из чего складывается прибыль: базовый вариант, продажа через 12 месяцев")
    x0, x1 = 62 * MM, (W - 26) * MM
    top_val = b["sale"] + t["rent"]

    def X(v):
        return x0 + (x1 - x0) * v / top_val
    lab = font(SANS_MED, 6.8)
    valf = font(SANS, 6.8)
    level = 0.0
    for i, (label, v, kind) in enumerate(items):
        y = 12 * MM + i * row_mm * MM
        d.text((0, y + 0.6 * MM), label, font=lab if kind != "out" else font(SANS, 6.8), fill=INK)
        if kind == "in":
            a, e = level, level + v
            level = e
            col = BAR_INK
        elif kind == "out":
            a, e = level + v, level
            level = a
            col = SOFT if label != "Налог на передачу 10,4 %" else RED
        else:
            a, e = 0, v
            col = GOLD
            level = v
        d.rectangle((X(a), y + 0.5 * MM, max(X(e), X(a) + 0.4 * MM), y + 4.6 * MM), fill=col)
        text = ("+" if v > 0 and kind != "res" else "") + eur(v)
        d.text((max(X(e), X(a)) + 1.6 * MM, y + 0.6 * MM), text, font=valf if kind != "res" else font(SANS_MED, 6.8),
               fill=INK if kind != "out" else GREY)
    img.save(OUT / "bridge.png")


# ------------------------------------------------------------------ лестница цены
def ladder():
    """Столбики: сколько заработаем при разной цене покупки, через 12 и через 6 месяцев."""
    rows = K["price_steps"]
    row_mm = 10.5
    h = 20 + len(rows) * row_mm + 4
    img, d = canvas(h)
    caption(d, "Сколько заработаем до налога при разной цене покупки")
    lo = min(min(r["p12"], r["p6"]) for r in rows)
    hi = max(max(r["p12"], r["p6"]) for r in rows)
    x0, x1 = 58 * MM, (W - 2) * MM
    span = hi - lo
    left, right = lo - span * 0.22, hi + span * 0.22

    def X(v):
        return x0 + (x1 - x0) * (v - left) / (right - left)
    top = 19 * MM
    # подпись цветов
    d.rectangle((x0, 9.0 * MM, x0 + 3.5 * MM, 11.4 * MM), fill=BAR_INK)
    d.text((x0 + 5 * MM, 8.6 * MM), "продажа через 12 месяцев", font=font(SANS, 6.4), fill=GREY)
    d.rectangle((x0 + 45 * MM, 9.0 * MM, x0 + 48.5 * MM, 11.4 * MM), fill=GOLD)
    d.text((x0 + 50 * MM, 8.6 * MM), "через 6 месяцев", font=font(SANS, 6.4), fill=GREY)
    zx = X(0)
    d.line((zx, top - 2 * MM, zx, top + len(rows) * row_mm * MM - 1 * MM), fill=INK, width=max(1, int(0.3 * MM)))
    d.text((zx - 9 * MM, top - 5.6 * MM), "← убыток", font=font(SANS, 6.0), fill=GREY)
    d.text((zx + 1.5 * MM, top - 5.6 * MM), "прибыль →", font=font(SANS, 6.0), fill=GREY)
    for i, r in enumerate(rows):
        y = top + i * row_mm * MM
        mine = r["tag"] == "наше предложение"
        d.text((0, y + 0.2 * MM), eur(round(r["price"] / 100) * 100), font=font(SANS_MED, 7.4),
               fill=GOLD if mine else INK)
        note = r["tag"] or (f"на {abs(r['disc']) * 100:.0f} % ниже цены продавца")
        if r["tag"] == "в ноль":
            note = "прибыль 0 через 12 месяцев"
        if r["tag"] and r["tag"] != "цена продавца":
            note += f", на {abs(r['disc']) * 100:.0f} % ниже"
        d.text((0, y + 4.2 * MM), note, font=font(SANS, 6.0), fill=GOLD if mine else GREY)
        for j, (key, col) in enumerate((("p12", BAR_INK), ("p6", GOLD))):
            v = r[key]
            by = y + (0.4 + j * 4.4) * MM
            a_, b_ = sorted((X(0), X(v)))
            colour = col if v >= 0 else (RED if key == "p12" else (196, 140, 128))
            d.rectangle((a_, by, max(b_, a_ + 0.4 * MM), by + 3.4 * MM), fill=colour)
            if abs(v) < 500:
                text = "0"
            else:
                text = ("+" if v > 0 else "−") + f"€ {abs(v) / 1000:,.0f} тыс.".replace(",", ".")
            f = font(SANS_MED if key == "p12" else SANS, 6.4)
            w = d.textlength(text, font=f)
            tx = X(v) + 1.4 * MM if v >= 0 else X(v) - 1.4 * MM - w
            d.text((tx, by - 0.3 * MM), text, font=f, fill=INK if key == "p12" else GREY)
        hairline(d, 0, y + row_mm * MM - 1.2 * MM, x1, y + row_mm * MM - 1.2 * MM, (230, 226, 218), 0.2)
    img.save(OUT / "ladder.png")


# ------------------------------------------------------------------ ставки аренды
def rents():
    big = [c for c in C["rent"] if 1400 <= c["area"] <= 6000]
    big = sorted(big, key=lambda c: c["rent_m2"])
    rows = [(f"{c['addr']}, {c['town']}", c["rent_m2"], f"{c['area']:,}".replace(",", ".") + f" м², {c['year']}")
            for c in big]
    rows.append(("Kernweg 1 — наша оценка", K["rent"] / K["area"], "2.016 м², средняя по шести частям"))
    rows = sorted(rows, key=lambda r: r[1])
    row_mm = 8.6
    h = 12 + len(rows) * row_mm + 2
    img, d = canvas(h)
    caption(d, "Аренда больших залов в Хорне и окрестностях, € за м² в год")
    x0, x1 = 70 * MM, (W - 18) * MM
    peak = max(r[1] for r in rows) * 1.1
    for i, (name, v, note) in enumerate(rows):
        y = 12 * MM + i * row_mm * MM
        mine = name.startswith("Kernweg")
        d.text((0, y), name, font=font(SANS_MED, 7.0), fill=GOLD if mine else INK)
        d.text((0, y + 4.0 * MM), note, font=font(SANS, 6.0), fill=GREY)
        d.rectangle((x0, y + 0.8 * MM, x0 + (x1 - x0) * v / peak, y + 5.0 * MM), fill=GOLD if mine else BAR_INK)
        d.text((x0 + (x1 - x0) * v / peak + 2 * MM, y + 0.2 * MM), f"€ {v:.0f}", font=font(SANS_LIGHT, 10),
               fill=GOLD if mine else INK)
    img.save(OUT / "rents.png")


cash()
timeline()
bridge()
ladder()
rents()
print("charts:", sorted(p.name for p in OUT.glob("*.png")))
