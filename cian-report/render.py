"""Build build/report.html from data/*.json and the map screenshots.

    python render.py && node pdf.js
"""
import json
import os
import re
from statistics import median

from jinja2 import Environment, FileSystemLoader
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")

NBSP = "\u00a0"


def money(v):
    return f"{v:,.0f}".replace(",", NBSP) + NBSP + "₽"


def mln(v, digits=1):
    return f"{v / 1e6:.{digits}f}".replace(".", ",") + NBSP + "млн"


def area(v):
    if v is None:
        return "—"
    s = f"{v:.1f}".rstrip("0").rstrip(".").replace(".", ",")
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
    """Map screenshots are PNG; JPEG keeps the PDF light."""
    src = os.path.join(BUILD, "maps", f"{name}.png")
    dst = os.path.join(BUILD, "maps", f"{name}.jpg")
    if not os.path.exists(dst) or os.path.getmtime(dst) < os.path.getmtime(src):
        Image.open(src).convert("RGB").save(dst, "JPEG", quality=86, optimize=True, progressive=True)
    return f"maps/{name}.jpg"


def bar_chart(items):
    """Horizontal bars of price per m², single series, sorted high to low."""
    rows = sorted(items, key=lambda i: i["ppm2"], reverse=True)
    # Keep the plot the same height whatever the number of bars.
    row = min(47, 470 / len(rows))
    w, label_w, value_w, bar_h, gap = 620, 200, 80, round(row * 0.64), row - round(row * 0.64)
    plot_w = w - label_w - value_w
    vmax = max(i["ppm2"] for i in rows)
    h = len(rows) * (bar_h + gap)
    med = median(i["ppm2"] for i in rows)
    parts = [f'<svg viewBox="0 0 {w} {h + 26}" width="100%" role="img" '
             f'aria-label="Цена за квадратный метр по объектам">']
    mx = label_w + plot_w * med / vmax
    parts.append(f'<line x1="{mx:.1f}" y1="0" x2="{mx:.1f}" y2="{h - gap / 2}" class="median"/>')
    parts.append(f'<text x="{mx:.1f}" y="{h + 16}" class="median-label" text-anchor="middle">'
                 f'медиана {mln(med, 2)}{NBSP}₽/м²</text>')
    for k, it in enumerate(rows):
        y = k * (bar_h + gap)
        bw = max(plot_w * it["ppm2"] / vmax, 4)
        parts.append(f'<text x="0" y="{y + bar_h / 2 + 4}" class="lbl">'
                     f'<tspan class="num">{it["order"]:02d}</tspan>{NBSP}{NBSP}{it["jkShort"]}</text>')
        parts.append(f'<path d="M{label_w},{y} h{bw - 4:.1f} a4,4 0 0 1 4,4 v{bar_h - 8} a4,4 0 0 1 -4,4 '
                     f'h-{bw - 4:.1f} z" class="bar"/>')
        parts.append(f'<text x="{label_w + bw + 8:.1f}" y="{y + bar_h / 2 + 4}" class="val">'
                     f'{mln(it["ppm2"], 2)}</text>')
    parts.append("</svg>")
    return "".join(parts)


def cluster_markers(markers, radius=3.2):
    """Merge markers that would overlap (same or neighbouring buildings) into one pill."""
    out = []
    for m in sorted(markers, key=lambda m: m["order"]):
        near = next((c for c in out if abs(c["left"] - m["left"]) < radius and abs(c["top"] - m["top"]) < radius * 1.2), None)
        if near:
            near["orders"].append(m["order"])
            n = len(near["orders"])
            near["left"] = (near["left"] * (n - 1) + m["left"]) / n
            near["top"] = (near["top"] * (n - 1) + m["top"]) / n
        else:
            out.append({"left": m["left"], "top": m["top"], "orders": [m["order"]]})
    return out


def feature(it, label):
    return next((v for g in it["features"] for k, v in g["items"] if k == label), None)


def main():
    listings = json.load(open(os.path.join(HERE, "data", "listings.json"), encoding="utf-8"))
    jk_info = json.load(open(os.path.join(HERE, "data", "jk.json"), encoding="utf-8"))
    maps = json.load(open(os.path.join(BUILD, "maps", "maps.json"), encoding="utf-8"))

    for it in listings:
        jk = it["jk"]
        it["jkShort"] = re.sub(r"\s*\(.*\)", "", jk["name"]) if jk else it["address"]
        it["map"] = map_jpeg(str(it["id"]))
        it["descShort"] = clean_description(it["description"], 900)
        it["hero"] = it["photosLocal"][0] if it["photosLocal"] else None
        it["grid"] = it["photosLocal"][1:5]
        it["more"] = it["photosLocal"][5:11]
        # "сегодня"/"вчера" are relative to the scrape date.
        it["published"] = (it["published"] or "").replace("сегодня", "21 окт").replace("вчера", "20 окт")
        # CIAN shows the BTI year ("Год постройки") when it has one; prefer it.
        year = feature(it, "Год постройки")
        it["year"] = int(year) if year and year.isdigit() else (
            it["building"]["year"] or (it["jk"]["finish"] or {}).get("year") if it["jk"] else it["building"]["year"])
    maps["overview"]["clusters"] = cluster_markers(maps["overview"]["markers"])

    # Group listings by complex, in the order complexes first appear.
    groups = []
    for it in listings:
        key = str(it["jk"]["id"]) if it["jk"] else f"addr-{it['id']}"
        g = next((g for g in groups if g["key"] == key), None)
        if not g:
            jk = it["jk"] or {}
            no = jk.get("newObject") or {}
            advantages = [a for a in no.get("advantages", []) if a.get("imagesLocal")]
            hero = next((a["imagesLocal"][0] for a in advantages if a["type"] in ("Architecture", "Facade")),
                        jk.get("coverLocal") or (advantages[0]["imagesLocal"][0] if advantages else it["hero"]))
            strip = [(a["title"], a["imagesLocal"][0]) for a in advantages if a["imagesLocal"][0] != hero][:3]
            g = {"key": key, "jk": jk, "info": jk_info.get(key, {}), "specs": no.get("specs", []),
                 "status": no.get("status"), "promos": no.get("promos", []), "hero": hero, "strip": strip,
                 "flats": []}
            groups.append(g)
        g["flats"].append(it)

    prices = [i["price"] for i in listings]
    ppm2 = [i["ppm2"] for i in listings]
    areas = [i["area"]["total"] for i in listings]
    stats = {
        "count": len(listings), "jk": len(groups),
        "price": (min(prices), max(prices)), "area": (min(areas), max(areas)),
        "ppm2": (min(ppm2), max(ppm2), median(ppm2)),
        "cheapest": min(listings, key=lambda i: i["ppm2"]),
        "priciest": max(listings, key=lambda i: i["ppm2"]),
        "biggest": max(listings, key=lambda i: i["area"]["total"]),
        "kitchen": max(listings, key=lambda i: i["area"]["kitchen"] or 0),
        "ceiling": max(listings, key=lambda i: i["building"]["ceiling"] or 0),
        "metro": min(listings, key=lambda i: min((m["minutes"] or 99) for m in i["metro"])),
        "districts": sorted({i["district"].replace("район ", "") for i in listings}),
        "addresses": len({i["address"] for i in listings}),
    }

    env = Environment(loader=FileSystemLoader(HERE), autoescape=True, trim_blocks=True, lstrip_blocks=True)
    env.filters.update(money=money, mln=mln, area=area)
    env.globals.update(plural=plural)
    html = env.get_template("template.html").render(
        listings=listings, groups=groups, stats=stats, maps=maps,
        overview_map=map_jpeg("overview"), chart=bar_chart(listings),
        cover=next(g["hero"] for g in groups if g["jk"].get("id") == 3401216) if any(
            g["jk"].get("id") == 3401216 for g in groups) else groups[0]["hero"],
        date="21 октября 2026",
    )
    with open(os.path.join(BUILD, "report.html"), "w", encoding="utf-8") as f:
        f.write(html)
    print("build/report.html written")


if __name__ == "__main__":
    main()
