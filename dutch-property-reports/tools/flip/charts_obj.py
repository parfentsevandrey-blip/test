"""Графики флип-модели любого объекта в стиле отчёта.

python3 -I charts_obj.py MODEL_JSON RENTS_JSON OUT_DIR
RENTS_JSON: {"title": ..., "rows": [[подпись, € за м², примечание], ...], "mine": [подпись, € за м², примечание]}
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
sys.path.append("/root/.local/lib/python3.11/site-packages")
from PIL import Image, ImageDraw  # noqa: E402

from reportgen.visuals import (GOLD, GREY, GREY_SOFT, INK, MM, PAPER, SANS, SANS_LIGHT, SANS_MED,  # noqa: E402
                               font, hairline, tracked)

BAR_INK = (58, 58, 56)
SOFT = (206, 201, 190)
MID = (176, 170, 158)
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
        return ("−" if v < 0 else "") + "€ " + f"{abs(v) / 1000:,.0f}".replace(",", ".") + " тыс."
    return ("−" if v < 0 else "") + "€ " + f"{abs(v):,.0f}".replace(",", ".")


def dashed_h(d, x0, x1, y, colour):
    x = x0
    while x < x1:
        d.line((x, y, min(x + 1.2 * MM, x1), y), fill=colour, width=max(1, int(0.25 * MM)))
        x += 2.4 * MM


def cash(M, out):
    h = 92
    img, d = canvas(h)
    caption(d, "Накопленный денежный поток собственника по месяцам")
    L = M["labels"]
    series = [(L["main"], M["base"]["rows"], BAR_INK), (L["alt"], M["fast"]["rows"], GOLD)]
    curves = []
    for label, rows, colour in series:
        cum, pts = 0.0, []
        for r in rows:
            cum += r["equity"]
            pts.append((r["m"], cum))
        curves.append((label, pts, colour))
    lo = min(v for _, pts, _ in curves for _, v in pts)
    hi = max(v for _, pts, _ in curves for _, v in pts)
    span = hi - lo
    lo, hi = lo - span * 0.06, max(hi, 0) + span * 0.16
    months = max(p[-1][0] for _, p, _ in curves)
    x0, x1 = 22 * MM, (W - 44) * MM
    y0, y1 = 14 * MM, (h - 12) * MM

    def X(m):
        return x0 + (x1 - x0) * m / months

    def Y(v):
        return y1 - (y1 - y0) * (v - lo) / (hi - lo)
    hairline(d, x0, Y(0), x1, Y(0), INK, 0.3)
    tick = font(SANS, 6.2)
    step = 100_000 if span > 300_000 else 50_000
    v = -step
    while v > lo:
        dashed_h(d, x0, x1, Y(v), (224, 219, 208))
        d.text((0, Y(v) - 1.6 * MM), eur(v, True), font=tick, fill=GREY)
        v -= step
    d.text((0, Y(0) - 1.6 * MM), "€ 0", font=tick, fill=GREY)
    for m in range(0, months + 1, 2 if months > 8 else 1):
        t = str(m)
        d.text((X(m) - d.textlength(t, font=tick) / 2, y1 + 2.2 * MM), t, font=tick, fill=GREY)
    tracked(d, (x0, h * MM - 4.5 * MM), "МЕСЯЦ ПОСЛЕ ПОКУПКИ", font(SANS_MED, 5.8), GREY)
    val = font(SANS_LIGHT, 11)
    ends = []
    for label, pts, colour in curves:
        xy = [(X(m), Y(v)) for m, v in pts]
        line = []
        for j, (x, y) in enumerate(xy):
            if j:
                line.append((x, line[-1][1]))
            line.append((x, y))
        d.line(line, fill=colour, width=int(0.55 * MM))
        ex, ey = xy[-1]
        d.ellipse((ex - 1.2 * MM, ey - 1.2 * MM, ex + 1.2 * MM, ey + 1.2 * MM), fill=colour)
        ends.append((ex, ey, pts[-1][1], label, colour))
    ends.sort(key=lambda e: e[1])
    last_y = -1e9
    for ex, ey, profit, label, colour in ends:
        ty = max(ey - 5.5 * MM, last_y + 10 * MM)
        last_y = ty
        d.text((ex + 3 * MM, ty), ("+" if profit >= 0 else "") + eur(profit, True), font=val, fill=colour)
        d.text((ex + 3 * MM, ty + 5.0 * MM), label, font=font(SANS, 6.0), fill=GREY)
    pts = curves[0][1]
    pm, pv = min(pts, key=lambda t: t[1])
    note = ("цена, НДС до возврата, отделка" if M["route"] == "vat"
            else "цена, налог 10,4 %, расходы на сделку")
    for k, text in enumerate((f"больше всего денег в проекте — {eur(-pv, True)}", f"на {pm}-м месяце: {note}")):
        d.text((X(0.3), Y(pv) - (16 - 3.6 * k) * MM), text, font=font(SANS, 6.2), fill=GREY)
    img.save(out / "cash.png")


def timeline(M, out):
    p = M["base_params"]
    lm, rf, sm = p["lease_month"], p["rent_free"], p["sale_month"]
    vat = M["route"] == "vat"
    rows = [
        ("Покупка у нотариуса" + (", без налога 10,4 %" if vat else ", налог 10,4 %"), [(0, 0.35, BAR_INK)]),
        ("НДС: платим и возвращаем" if vat else "Ремонт", [(0, 2, SOFT)] if vat else [(0.35, 2, SOFT)]),
        ("Отделка помещения" if vat else "Поиск арендатора и договор", [(0.35, 2.5, SOFT)] if vat else [(0, lm - 1, SOFT)]),
        ("Поиск арендатора и договор", [(-1, lm - 1, SOFT)]) if vat else None,
        ("Арендатор въехал, каникулы", [(lm - 1, lm - 1 + rf, MID)]),
        ("Аренда платится", [(lm - 1 + rf, sm, BAR_INK)]),
        ("Продажа: реклама, предложения", [(lm - 1, sm - 2.2, SOFT)]),
        ("Проверка покупателем и нотариус", [(sm - 2.2, sm, BAR_INK)]),
    ]
    rows = [r for r in rows if r]
    end = 12
    h = 16 + len(rows) * 8.6 + 14
    img, d = canvas(h)
    caption(d, f"Сроки: {M['labels']['main']}")
    x0, x1 = 60 * MM, (W - 2) * MM

    def X(m):
        return x0 + (x1 - x0) * (m + 1) / (end + 1)
    top = 15 * MM
    sub = font(SANS, 6.0)
    for m in range(-1, end + 1):
        t = str(m) if m >= 0 else "до"
        d.text((X(m) - d.textlength(t, font=sub) / 2, top - 5.6 * MM), t, font=sub, fill=GREY)
    if vat:
        dead = lm - 1 + M["base"]["p"]["vat_window"]
        dashed_v(d, X(dead), top - 1 * MM, top + len(rows) * 8.6 * MM)
        d.text((X(dead) + 1.2 * MM, top + len(rows) * 8.6 * MM - 4 * MM), "срок для продажи без 10,4 %",
               font=sub, fill=GOLD)
    for i, (label, bars) in enumerate(rows):
        y = top + i * 8.6 * MM
        d.text((0, y + 0.6 * MM), label, font=font(SANS_MED, 6.8), fill=INK)
        for s, e, col in bars:
            d.rectangle((X(s), y + 0.6 * MM, X(e), y + 4.4 * MM), fill=col)
        hairline(d, 0, y + 7.6 * MM, x1, y + 7.6 * MM, (230, 226, 218), 0.2)
    y = top + len(rows) * 8.6 * MM + 2.5 * MM
    text = ("Пунктир — 6 месяцев после начала аренды: если акт у нотариуса подписан позже, инвестор платит налог 10,4 % "
            "и снижает цену" if vat else "Месяц 0 — передача у нотариуса")
    d.text((0, y), text, font=sub, fill=GREY)
    img.save(out / "timeline.png")


def dashed_v(d, x, y0, y1, colour=GOLD):
    y = y0
    while y < y1:
        d.line((x, y, x, min(y + 1.1 * MM, y1)), fill=colour, width=max(1, int(0.3 * MM)))
        y += 2.2 * MM


def ladder(M, out):
    rows = M["price_steps"]
    L = M["labels"]
    row_mm = 10.5
    h = 20 + len(rows) * row_mm + 4
    img, d = canvas(h)
    caption(d, "Сколько заработаем до налога при разной цене покупки")
    lo = min(min(r["p12"], r["p6"], 0) for r in rows)
    hi = max(max(r["p12"], r["p6"], 0) for r in rows)
    span = hi - lo
    left, right = lo - span * 0.25, hi + span * 0.25
    x0, x1 = 58 * MM, (W - 2) * MM

    def X(v):
        return x0 + (x1 - x0) * (v - left) / (right - left)
    top = 19 * MM
    d.rectangle((x0, 9.0 * MM, x0 + 3.5 * MM, 11.4 * MM), fill=BAR_INK)
    d.text((x0 + 5 * MM, 8.6 * MM), L["main"], font=font(SANS, 6.4), fill=GREY)
    lx = x0 + 5 * MM + d.textlength(L["main"], font=font(SANS, 6.4)) + 8 * MM
    d.rectangle((lx, 9.0 * MM, lx + 3.5 * MM, 11.4 * MM), fill=GOLD)
    d.text((lx + 5 * MM, 8.6 * MM), L["alt"], font=font(SANS, 6.4), fill=GREY)
    zx = X(0)
    d.line((zx, top - 2 * MM, zx, top + len(rows) * row_mm * MM - 1 * MM), fill=INK, width=max(1, int(0.3 * MM)))
    d.text((zx - 13 * MM, top - 5.6 * MM), "← убыток", font=font(SANS, 6.0), fill=GREY)
    d.text((zx + 1.5 * MM, top - 5.6 * MM), "прибыль →", font=font(SANS, 6.0), fill=GREY)
    for i, r in enumerate(rows):
        y = top + i * row_mm * MM
        mine = r["tag"] == "наше предложение"
        d.text((0, y + 0.2 * MM), eur(round(r["price"] / 100) * 100), font=font(SANS_MED, 7.4),
               fill=GOLD if mine else INK)
        disc = r["disc"]
        rel = ("цена продавца" if abs(disc) < 1e-9 else
               f"на {abs(disc) * 100:.0f} % " + ("ниже цены продавца" if disc < 0 else "выше цены продавца"))
        if r["tag"] == "в ноль":
            note = "прибыль 0, " + rel.replace(" цены продавца", "")
        elif r["tag"] and r["tag"] != "цена продавца":
            note = r["tag"] + ", " + rel.replace(" цены продавца", "")
        else:
            note = rel
        d.text((0, y + 4.2 * MM), note, font=font(SANS, 6.0), fill=GOLD if mine else GREY)
        for j, (key, col) in enumerate((("p12", BAR_INK), ("p6", GOLD))):
            v = r[key]
            by = y + (0.4 + j * 4.4) * MM
            a_, b_ = sorted((X(0), X(v)))
            colour = col if v >= 0 else (RED if key == "p12" else (196, 140, 128))
            d.rectangle((a_, by, max(b_, a_ + 0.4 * MM), by + 3.4 * MM), fill=colour)
            text = "0" if abs(v) < 500 else ("+" if v > 0 else "−") + f"€ {abs(v) / 1000:,.0f} тыс.".replace(",", ".")
            f = font(SANS_MED if key == "p12" else SANS, 6.4)
            w = d.textlength(text, font=f)
            tx = X(v) + 1.4 * MM if v >= 0 else X(v) - 1.4 * MM - w
            d.text((tx, by - 0.3 * MM), text, font=f, fill=INK if key == "p12" else GREY)
        hairline(d, 0, y + row_mm * MM - 1.2 * MM, x1, y + row_mm * MM - 1.2 * MM, (230, 226, 218), 0.2)
    img.save(out / "ladder.png")


def rents(R, out):
    rows = sorted(R["rows"] + [R["mine"]], key=lambda r: r[1])
    row_mm = 8.6
    h = 12 + len(rows) * row_mm + 2
    img, d = canvas(h)
    caption(d, R["title"])
    x0, x1 = 72 * MM, (W - 18) * MM
    peak = max(r[1] for r in rows) * 1.1
    for i, (name, v, note) in enumerate(rows):
        y = 12 * MM + i * row_mm * MM
        mine = name == R["mine"][0]
        d.text((0, y), name, font=font(SANS_MED, 7.0), fill=GOLD if mine else INK)
        d.text((0, y + 4.0 * MM), note, font=font(SANS, 6.0), fill=GREY)
        d.rectangle((x0, y + 0.8 * MM, x0 + (x1 - x0) * v / peak, y + 5.0 * MM), fill=GOLD if mine else BAR_INK)
        d.text((x0 + (x1 - x0) * v / peak + 2 * MM, y + 0.2 * MM), f"€ {v:.0f}", font=font(SANS_LIGHT, 10),
               fill=GOLD if mine else INK)
    img.save(out / "rents.png")


def main():
    M = json.loads(Path(sys.argv[1]).read_text())
    R = json.loads(Path(sys.argv[2]).read_text())
    out = Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    cash(M, out)
    timeline(M, out)
    ladder(M, out)
    rents(R, out)
    print("charts:", sorted(p.name for p in out.glob("*.png")))


if __name__ == "__main__":
    main()
