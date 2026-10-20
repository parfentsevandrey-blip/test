"""Где стоит объект: адрес → координаты, наши районы рядом, въезд на магистраль A.

Районы — промзоны и площадки из файла с картами (data/selection-*.json) и Роттердам
целиком. «В районе» — внутри контура промзоны или не дальше 1,5 км от него;
«поблизости» — дальше, но не больше NEARBY_MIN минут на машине до промзоны.
Расстояние до магистрали — по маршруту OSRM: первый участок дороги с номером A.
"""
from __future__ import annotations

import json
import re
import time
from functools import lru_cache
from pathlib import Path

import requests
from shapely.geometry import Point

from flipfinder import config as C
from reportgen import selection as S

CACHE = C.ROOT / ".cache" / "geo.json"
OSRM = "https://router.project-osrm.org/route/v1/driving/{a};{b}?overview=false&steps={steps}"
# дальние точки в разные стороны: маршрут к ним выходит на ближайшую магистраль
FAR = [(52.0894, 5.1101), (52.0800, 4.3100), (52.5100, 6.0900), (51.5900, 4.7800), (52.6300, 4.7500),
       (51.4400, 5.4800), (51.8400, 5.8600)]


def _load() -> dict:
    return json.loads(CACHE.read_text()) if CACHE.exists() else {}


_GEO = _load()


def _save() -> None:
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(json.dumps(_GEO, ensure_ascii=False))


def geocode(address: str) -> tuple[float, float, str] | None:
    key = "g:" + address
    if key not in _GEO:
        try:
            lat, lon, name = S.geocode(address)
            _GEO[key] = [lat, lon, name]
        except Exception:                       # noqa: BLE001 — адрес не нашёлся
            _GEO[key] = None
        _save()
    return tuple(_GEO[key]) if _GEO[key] else None


def _route(a, b, steps=False):
    url = OSRM.format(a=f"{a[1]},{a[0]}", b=f"{b[1]},{b[0]}", steps="true" if steps else "false")
    for attempt in range(3):
        try:
            return requests.get(url, timeout=30).json()["routes"][0]
        except Exception:                       # noqa: BLE001
            time.sleep(2 * (attempt + 1))
    return None


def drive(a, b) -> tuple[float, float] | None:
    key = f"d:{a[0]:.5f},{a[1]:.5f}>{b[0]:.5f},{b[1]:.5f}"
    if key not in _GEO:
        r = _route(a, b)
        _GEO[key] = [r["distance"] / 1000, r["duration"] / 60] if r else None
        _save()
    return tuple(_GEO[key]) if _GEO[key] else None


def motorway(lat: float, lon: float) -> dict | None:
    """Ближайший въезд на магистраль A по дороге: км, минуты, номер."""
    key = f"m:{lat:.5f},{lon:.5f}"
    if key not in _GEO:
        best = None
        for far in FAR:
            r = _route((lat, lon), far, steps=True)
            if not r:
                continue
            d = t = 0.0
            for leg in r["legs"]:
                for s in leg["steps"]:
                    ref = s.get("ref") or ""
                    if re.search(r"\bA\s?\d+", ref):
                        hit = {"km": round(d / 1000, 1), "min": round(t / 60), "ref": re.search(r"A\s?\d+", ref).group(0)}
                        if best is None or hit["km"] < best["km"]:
                            best = hit
                        break
                    d += s["distance"]
                    t += s["duration"]
                else:
                    continue
                break
        _GEO[key] = best
        _save()
    return _GEO[key]


@lru_cache(maxsize=1)
def _zones():
    selection, spec, data = S.load()
    out = []
    for t in selection["territories"]:
        key = t["key"]
        geo = data["geo"][key]
        if t.get("whole"):
            for g in geo.values():
                out.append({"territory": key, "name": t["title"], "short": t["title"], "geom": g, "body": t.get("about", ""),
                            "whole": True})
            continue
        sheet = data["sheets"][key]
        for e in sheet["entries"]:
            if e["n"] in geo:
                out.append({"territory": key, "name": e["name"], "short": e["short"], "geom": geo[e["n"]],
                            "body": e["body"], "near": e.get("near", ""), "whole": False})
    return out


def km(a, b) -> float:
    """Расстояние по прямой, км."""
    import math
    dlat, dlon = math.radians(b[0] - a[0]), math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlon / 2) ** 2
    return 12742 * math.asin(math.sqrt(h))


def metres(here: Point, geom) -> float:
    return S._metres(here, geom)


def place(lat: float, lon: float) -> dict:
    """Ближайшая промзона нашего района: внутри / рядом / поблизости / далеко."""
    here = Point(lon, lat)
    best = None
    for z in _zones():
        inside = z["geom"].contains(here)
        m = 0 if inside else metres(here, z["geom"])
        if best is None or m < best["metres"]:
            best = {**{k: v for k, v in z.items() if k != "geom"}, "metres": round(m), "inside": inside,
                    "point": (z["geom"].representative_point().y, z["geom"].representative_point().x)}
    if best["inside"] or best["metres"] <= 1500:
        best["ring"] = "zone"
    else:
        route = drive((lat, lon), best["point"])
        best["drive_min"] = round(route[1]) if route else None
        best["ring"] = "nearby" if route and route[1] <= C.NEARBY_MIN else "far"
    return best


def anchors(lat: float, lon: float, territory: str, limit: int = 4) -> list[dict]:
    """Площадки и компании района рядом с объектом, с дорогой в км и минутах."""
    selection, spec, data = S.load()
    sheet = data["sheets"].get(territory)
    if not sheet:
        return []
    here = Point(lon, lat)
    found = sorted(((label, metres(here, p), p) for label, p in S._anchors(sheet, data["geo"][territory])),
                   key=lambda a: a[1])
    out, seen = [], set()
    for label, m, p in found:
        if label in seen:
            continue
        seen.add(label)
        r = drive((lat, lon), (p.y, p.x))
        out.append({"label": label, "km": round(r[0], 1) if r else None, "min": round(r[1]) if r else None})
        if len(out) == limit:
            break
    return out
