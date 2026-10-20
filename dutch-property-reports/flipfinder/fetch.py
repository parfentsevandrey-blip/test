"""Объявления funda in business: выдача поиска и страницы объектов, с кэшем на диске.

Страница выдачи уже содержит адрес, цену или ставку и площадь каждого объявления,
поэтому сопоставимые объекты (аренда) берутся прямо из выдачи. Страница объекта
загружается только для кандидатов на покупку и для сданных объектов на продаже.
Загруженные страницы — чужие данные: они лишь разбираются, ничего из них не выполняется.
"""
from __future__ import annotations

import hashlib
import html as H
import json
import os
import re
import sys
import time
from pathlib import Path

sys.path.append("/root/.local/lib/python3.11/site-packages")
from reportgen import funda_fetch as F  # noqa: E402

BASE = "https://www.fundainbusiness.nl"
CACHE = Path(os.environ.get("FLIP_CACHE", Path(__file__).resolve().parent.parent / ".cache" / "funda"))
MONEY = r"€\s*([\d.]+)(?:,\d+)?"


def num(s):
    digits = (s or "").replace(".", "")
    return int(digits) if digits.isdigit() else None


def _cache_path(url: str) -> Path:
    return CACHE / (hashlib.sha1(url.encode()).hexdigest()[:20] + ".html")


def get(url: str, *, max_age_h: float = 24, pause: float = 1.0) -> str | None:
    """HTML страницы; свежая копия из кэша, иначе загрузка через Scrapling."""
    path = _cache_path(url)
    if path.exists() and time.time() - path.stat().st_mtime < max_age_h * 3600:
        return path.read_text(encoding="utf-8")
    from scrapling.fetchers import Fetcher
    for attempt in range(3):
        try:
            r = Fetcher.get(url, timeout=60)
            if r.status == 404:
                return None
            if r.status == 200 and F.WALL not in r.html_content:
                CACHE.mkdir(parents=True, exist_ok=True)
                path.write_text(r.html_content, encoding="utf-8")
                time.sleep(pause)
                return r.html_content
        except Exception as exc:                      # noqa: BLE001 — сеть, повторяем
            print("  ошибка загрузки", type(exc).__name__, url, flush=True)
        time.sleep(3 * (attempt + 1))
    return None


# ------------------------------------------------------------------ выдача поиска
def _money_year(text: str, area: int | None) -> float | None:
    """Аренда в год из подписи выдачи: «€ 1.875 /mnd», «€ 145 /m²/jaar», «€ 42.000 /jaar»."""
    m = re.search(MONEY, text or "")
    if not m:
        return None
    v = num(m.group(1))
    if v is None:
        return None
    if "/m²" in text:
        return v * area if area else None
    if "/mnd" in text or "per maand" in text:
        return v * 12
    return v


def cards(html: str) -> list[dict]:
    out = []
    for block in re.split(r'<li class="search-result', html)[1:]:
        href = re.search(r'href="(/[^"]+/object-\d+-[^"]+/)"', block)
        if not href:
            continue

        def field(cls):
            m = re.search(rf'class="{cls}[^"]*"[^>]*>(.*?)</', block, re.S)
            return H.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", m.group(1)))).strip() if m else ""

        kenmerken = re.search(r'class="search-result-kenmerken[^"]*"[^>]*>(.*?)</ul>', block, re.S)
        ken = H.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", kenmerken.group(1)))) if kenmerken else ""
        area = re.search(r"([\d.]+)\s*m²", ken)
        out.append({"url": BASE + href.group(1), "title": field("search-result__header-title"),
                    "subtitle": field("search-result__header-subtitle"), "price_text": field("search-result-price"),
                    "area": num(area.group(1)) if area else None, "kenmerken": ken.strip()})
    return out


def search(slug: str, kind: str, deal: str, *, pages: int = 15) -> list[dict]:
    """Все карточки выдачи «kind/slug/deal» (kind: bedrijfshal | belegging, deal: koop | huur)."""
    found, seen = [], set()
    for page in range(1, pages + 1):
        url = f"{BASE}/{kind}/{slug}/{deal}/" + (f"p{page}/" if page > 1 else "")
        h = get(url)
        if not h:
            break
        new = [c for c in cards(h) if c["url"] not in seen]
        for c in new:
            seen.add(c["url"])
            c.update(slug=slug, kind=kind, deal=deal)
            if deal == "huur":
                c["rent_year"] = _money_year(c["price_text"], c["area"])
            else:
                m = re.search(MONEY, c["price_text"])
                c["price"] = num(m.group(1)) if m else None
        found += new
        total = re.search(r'"results_total":(\d+)', h)
        if not new or (total and len(found) >= int(total.group(1))):
            break
    return found


# ------------------------------------------------------------------ страница объекта
def listing(url: str) -> dict | None:
    h = get(url, max_age_h=72)
    if not h:
        return None
    feat = F.features(h)
    lines = [l.strip() for l in feat.split("\n")]

    def after(label):
        for i, l in enumerate(lines):
            if l == label and i + 1 < len(lines):
                return lines[i + 1]
        return None

    title = re.search(r"<title>(.*?)</title>", h, re.S)
    rec = {"url": url, "title": H.unescape(title.group(1)).strip() if title else ""}
    vp, hp = after("Vraagprijs") or "", after("Huurprijs") or ""
    m = re.search(MONEY, vp)
    rec["price"] = num(m.group(1)) if m else None
    rec["price_text"] = vp
    m = re.search(MONEY, hp)
    rent = num(m.group(1)) if m else None
    rec["rent_text"] = hp
    rec["ask_rent"] = (rent if "per jaar" in hp and "m²" not in hp else
                       rent * 12 if rent and "per maand" in hp and "m²" not in hp else None)
    rec["ask_rent_m2"] = rent if rent and "m²" in hp else None
    for key, label in (("area", "Oppervlakte"), ("hall", "Bedrijfshal oppervlak"), ("office", "Kantoor oppervlak"),
                       ("shop", "Winkel oppervlak"), ("yard", "Terrein oppervlak"), ("plot", "Perceel")):
        m = re.search(r"([\d.]+)\s*m²", after(label) or "")
        rec[key] = num(m.group(1)) if m else None
    for key, label in (("status", "Status"), ("year", "Bouwjaar"), ("period", "Bouwperiode"),
                       ("label", "Energielabel"), ("acceptance", "Aanvaarding"), ("function", "Hoofdfunctie"),
                       ("height", "Vrije hoogte"), ("since", "Aangeboden sinds"), ("access", "Bereikbaarheid"),
                       ("orig_price", "Oorspronkelijke vraagprijs"), ("build", "Soort bouw"),
                       ("floors", "Aantal bouwlagen"), ("load", "Draagvermogen"), ("parking", "Parkeerplaatsen")):
        rec[key] = after(label)
    sinds = re.search(r"sinds' : '([^']+)'", h)
    rec["since"] = sinds.group(1) if sinds else rec["since"]
    rec["desc"] = F.full_description(url, h)[:9000]
    if not rec["height"]:
        m = re.search(r"vrije hoogte[^\d]{0,40}(\d+[.,]?\d*)\s*m", rec["desc"], re.I)
        rec["height"] = f"{m.group(1).replace('.', ',')} м" if m else None
    rec["photos"] = F.business_gallery(h)
    return rec


def save_jsonl(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")


def load_jsonl(path: Path) -> list[dict]:
    return [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()] if path.exists() else []
