"""Exact MKAD ring from OSM relation 2094222 (polar model, 1-degree bins, outer carriageway)."""
import json, math
from pathlib import Path
HERE = Path(__file__).resolve().parent
C = (55.7558, 37.6176); K = math.cos(math.radians(55.75)); B = 360


def polar(lat, lon):
    dy = (lat - C[0]) * 111.2; dx = (lon - C[1]) * 111.2 * K
    return math.hypot(dx, dy), (math.degrees(math.atan2(dy, dx)) + 360) % 360


def build():
    bins = [[] for _ in range(B)]
    for la, lo in json.loads((HERE / "mkad_nodes.json").read_text()):
        d, a = polar(la, lo)
        if 12 < d < 21:
            bins[int(a) % B].append(d)
    rad = [max(b) if b else None for b in bins]
    known = [i for i, r in enumerate(rad) if r]
    for i in range(B):
        if rad[i] is None:
            lo = max((j for j in known if j < i), default=known[-1] - B)
            hi = min((j for j in known if j > i), default=known[0] + B)
            rad[i] = rad[lo % B] + (rad[hi % B] - rad[lo % B]) * (i - lo) / (hi - lo)
    return rad


RAD = build()


def inside(lat, lon, margin_km=0.0):
    d, a = polar(lat, lon)
    i0 = int(a) % B; f = a - int(a)
    return d <= RAD[i0] * (1 - f) + RAD[(i0 + 1) % B] * f + margin_km
