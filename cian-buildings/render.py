"""Build build/report.html for the Garden Ring buildings report.

    python render.py && node pdf.js
"""
import json
import math
import os
import re
from statistics import median

from jinja2 import Environment, FileSystemLoader
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")
DATE = "22 октября 2026"

NBSP = " "


def money(v):
    return f"{v:,.0f}".replace(",", NBSP) + NBSP + "₽"


def mln(v, digits=1):
    return f"{v / 1e6:,.{digits}f}".replace(",", NBSP).replace(".", ",") + NBSP + "млн"


def mln_short(v):
    """3360000 -> "3,36 млн", 7000000 -> "7 млн"."""
    return f"{v / 1e6:.2f}".rstrip("0").rstrip(".").replace(".", ",") + NBSP + "млн"


def thousands(v):
    return f"{v / 1e3:,.0f}".replace(",", NBSP) + NBSP + "тыс."


def area(v):
    if v is None:
        return "—"
    s = f"{v:,.1f}".replace(",", NBSP).rstrip("0").rstrip(".").replace(".", ",")
    return s + NBSP + "м²"


def plural(n, one, few, many):
    if n % 10 == 1 and n % 100 != 11:
        return one
    if 2 <= n % 10 <= 4 and not 12 <= n % 100 <= 14:
        return few
    return many


def clean_description(text, limit):
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n", text).strip()
    if len(text) <= limit:
        return text
    cut = text[:limit]
    end = max(cut.rfind(". "), cut.rfind(".\n"), cut.rfind("!"), cut.rfind(";"))
    return (cut[:end + 1] if end > limit * 0.5 else cut.rsplit(" ", 1)[0]) + " …"


def map_jpeg(name):
    src = os.path.join(BUILD, "maps", f"{name}.png")
    dst = os.path.join(BUILD, "maps", f"{name}.jpg")
    if not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src):
        Image.open(src).convert("RGB").save(dst, "JPEG", quality=86, optimize=True, progressive=True)
    return f"maps/{name}.jpg"


# Yandex tiles use elliptical Mercator (EPSG:3395); same maths as maps.js.
E = 0.0818191908426


def project(lat, lng, z):
    size = 256 * 2 ** z
    phi = math.radians(lat)
    esin = E * math.sin(phi)
    y = math.log(math.tan(math.pi / 4 + phi / 2) * ((1 - esin) / (1 + esin)) ** (E / 2))
    return (lng + 180) / 360 * size, (0.5 - y / (2 * math.pi)) * size


def map_overlay(center, z, w, h, ways, markers, r):
    """SVG drawn in the screenshot's own pixel space. preserveAspectRatio
    "slice" crops exactly like the <img>'s object-fit: cover, so the ring and
    the markers stay on their real positions whatever the frame's shape."""
    cx, cy = project(center["lat"], center["lng"], z)

    def xy(lat, lng):
        x, y = project(lat, lng, z)
        return w / 2 + x - cx, h / 2 + y - cy

    paths = []
    for way in ways:
        pts = [xy(lat, lng) for lat, lng in way]
        if all(x < -60 or x > w + 60 or y < -60 or y > h + 60 for x, y in pts):
            continue
        paths.append("M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts))
    d = " ".join(paths)
    out = [f'<svg class="overlay" viewBox="0 0 {w} {h}" preserveAspectRatio="xMidYMid slice">',
           f'<path d="{d}" class="ring-halo" style="stroke-width:{r * 0.9:.1f}px"/>',
           f'<path d="{d}" class="ring-line" style="stroke-width:{max(r * 0.12, 1.5):.1f}px"/>']
    # Merge markers that would overlap into one pill.
    placed = []
    for m in markers:
        x, y = xy(m["lat"], m["lng"])
        near = next((p for p in placed if math.hypot(p["x"] - x, p["y"] - y) < r * 2.1), None)
        if near:
            near["labels"].append(m["label"])
            n = len(near["labels"])
            near["x"] += (x - near["x"]) / n
            near["y"] += (y - near["y"]) / n
        else:
            placed.append({"x": x, "y": y, "labels": [m["label"]]})
    for p in placed:
        text = " · ".join(map(str, p["labels"]))
        half = max(r, r * 0.42 * len(text) + r * 0.55)
        out.append(f'<g class="pin"><rect x="{p["x"] - half:.1f}" y="{p["y"] - r:.1f}" width="{2 * half:.1f}" '
                   f'height="{2 * r:.1f}" rx="{r:.1f}" style="stroke-width:{r * 0.22:.1f}px"/>'
                   f'<text x="{p["x"]:.1f}" y="{p["y"] + r * 0.36:.1f}" style="font-size:{r * 0.95:.1f}px">'
                   f'{text}</text></g>')
    out.append("</svg>")
    return "".join(out)


def bar_chart(items):
    rows = sorted(items, key=lambda i: i["ppm2"], reverse=True)
    row = min(47, 470 / len(rows))
    w, label_w, value_w = 620, 250, 80
    bar_h, gap = round(row * 0.64), row - round(row * 0.64)
    plot_w = w - label_w - value_w
    vmax = max(i["ppm2"] for i in rows)
    h = len(rows) * (bar_h + gap)
    med = median(i["ppm2"] for i in rows)
    parts = [f'<svg viewBox="0 0 {w} {h + 26}" width="100%" role="img" aria-label="Цена за квадратный метр">']
    mx = label_w + plot_w * med / vmax
    parts.append(f'<line x1="{mx:.1f}" y1="0" x2="{mx:.1f}" y2="{h - gap / 2}" class="median"/>')
    parts.append(f'<text x="{mx:.1f}" y="{h + 16}" class="median-label" text-anchor="middle">'
                 f'медиана {thousands(med)}{NBSP}₽/м²</text>')
    for k, it in enumerate(rows):
        y = k * (bar_h + gap)
        bw = max(plot_w * it["ppm2"] / vmax, 4)
        parts.append(f'<text x="0" y="{y + bar_h / 2 + 4}" class="lbl"><tspan class="num">{it["order"]:02d}</tspan>'
                     f'{NBSP}{NBSP}{it["short"]}</text>')
        parts.append(f'<path d="M{label_w},{y} h{bw - 4:.1f} a4,4 0 0 1 4,4 v{bar_h - 8} a4,4 0 0 1 -4,4 '
                     f'h-{bw - 4:.1f} z" class="bar"/>')
        parts.append(f'<text x="{label_w + bw + 8:.1f}" y="{y + bar_h / 2 + 4}" class="val">{thousands(it["ppm2"])}</text>')
    parts.append("</svg>")
    return "".join(parts)


def plan_grid(sizes, box=(269, 176), gap=4):
    """Pick the column count that shows the floor plans largest."""
    if not sizes:
        return None
    n = len(sizes)
    aspect = sum(w / h for w, h in sizes) / n
    best = None
    for cols in range(1, n + 1):
        rows = math.ceil(n / cols)
        cw = (box[0] - (cols - 1) * gap) / cols
        ch = (box[1] - (rows - 1) * gap) / rows
        h = min(cw / aspect, ch)
        if not best or h * h * aspect > best[0]:
            best = (h * h * aspect, cols, rows)
    return {"cols": best[1], "rows": best[2]}


def short_address(addr):
    for a, b in [("улица", "ул."), ("площадь", "пл."), ("бульвар", "б-р"), ("переулок", "пер."), ("набережная", "наб.")]:
        addr = addr.replace(a, b)
    return addr


def main():
    items = json.load(open(os.path.join(HERE, "data", "listings.json"), encoding="utf-8"))
    ring = json.load(open(os.path.join(HERE, "data", "ring.json"), encoding="utf-8"))
    maps = json.load(open(os.path.join(BUILD, "maps", "maps.json"), encoding="utf-8"))

    for it in items:
        it["short"] = short_address(it["address"])
        it["ringDist"] = ring["distances"].get(str(it["id"]))
        it["descShort"] = clean_description(it["description"], 950)
        it["hero"] = it["photosLocal"][0]
        it["grid"] = it["photosLocal"][1:5]
        it["more"] = it["photosLocal"][5:10]
        it["planGrid"] = plan_grid([Image.open(os.path.join(BUILD, p)).size for p in it["layoutsLocal"][:6]])
        # A photo not shown anywhere else fills the free space of the facts card.
        used = 1 + len(it["grid"]) + (len(it["more"]) if len(it["more"]) >= 3 else 0)
        it["spare"] = it["photosLocal"][used] if len(it["photosLocal"]) > used else None
        it["published"] = (it["published"] or "").replace("сегодня", "22 окт").replace("вчера", "21 окт")
        m = maps["listings"][str(it["id"])]
        it["map"] = map_jpeg(str(it["id"]))
        it["mapSvg"] = map_overlay(it["coords"], m["z"], m["w"], m["h"], ring["ways"],
                                   [{"lat": it["coords"]["lat"], "lng": it["coords"]["lng"], "label": it["order"]}], 38)

    ov = maps["overview"]
    overview_svg = map_overlay(ov["center"], ov["z"], ov["w"], ov["h"], ring["ways"],
                               [{"lat": i["coords"]["lat"], "lng": i["coords"]["lng"], "label": i["order"]} for i in items], 21)

    prices = [i["price"] for i in items]
    ppm2 = [i["ppm2"] for i in items]
    areas = [i["area"] for i in items]
    with_income = [i for i in items if i["yield"]]
    stats = {
        "count": len(items),
        "price": (min(prices), max(prices)), "area": (min(areas), max(areas)), "areaTotal": sum(areas),
        "ppm2": (min(ppm2), max(ppm2), median(ppm2)),
        "cheapest": min(items, key=lambda i: i["ppm2"]),
        "priciest": max(items, key=lambda i: i["ppm2"]),
        "biggest": max(items, key=lambda i: i["area"]),
        "metro": min(items, key=lambda i: min((m["minutes"] or 99) for m in i["metro"])),
        "income": with_income,
        "districts": sorted({i["district"].replace("район ", "") for i in items}),
        "firstLine": sum(1 for i in items if i["verdict"] == "first"),
        "offers": sum(len(i["sellers"]) for i in items),
        "mostSellers": max(items, key=lambda i: len(i["sellers"])),
        "widest": max(items, key=lambda i: i["priceMax"] / i["priceMin"]),
        "doubt": [i for i in items if i["verdict"] == "doubt"],
        "notFirst": [i for i in items if i["verdict"] == "no"],
    }

    env = Environment(loader=FileSystemLoader(HERE), autoescape=True, trim_blocks=True, lstrip_blocks=True)
    env.filters.update(money=money, mln=mln, area=area, thousands=thousands, mln_short=mln_short)
    env.globals.update(plural=plural)
    html = env.get_template("template.html").render(
        items=items, stats=stats, overview_map=map_jpeg("overview"), overview_svg=overview_svg,
        chart=bar_chart(items), cover=next(i["hero"] for i in items if i["id"] == 334830734),
        date=DATE, ring_source=ring["source"],
    )
    with open(os.path.join(BUILD, "report.html"), "w", encoding="utf-8") as f:
        f.write(html)
    print("build/report.html written")


if __name__ == "__main__":
    main()
