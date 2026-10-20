"""Полигон МКАД из OSM (Nominatim): все участки дороги «МКАД» (листание через exclude_place_ids),
точки сортируются по углу от центра Москвы; в каждом секторе 0,5° берётся самая дальняя точка (внешняя полоса).
МКАД звездообразен относительно центра, поэтому так получается корректный контур.
usage: python tests/mkad_polygon.py data/geo/mkad.json"""
import json, math, sys, time, urllib.parse, urllib.request
C = (55.7522, 37.6156)
pts, seen = set(), []
for page in range(40):
    url = ("https://nominatim.openstreetmap.org/search?q=%D0%9C%D0%9A%D0%90%D0%94&format=json&polygon_geojson=1&limit=50&dedupe=0"
           + (f"&exclude_place_ids={','.join(map(str, seen))}" if seen else ""))
    for attempt in range(5):
        try:
            res = json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "web-search-research/1.0"}), timeout=60))
            break
        except Exception as e:
            print(page, e); time.sleep(10 * (attempt + 1))
    new = [x for x in res if x["place_id"] not in seen]
    if not new:
        break
    for x in new:
        seen.append(x["place_id"])
        g = x["geojson"]
        if x.get("class") == "highway" and g["type"] == "LineString":
            pts.update((round(lat, 5), round(lon, 5)) for lon, lat in g["coordinates"])
    print(f"стр. {page}: участков {len(seen)}, точек {len(pts)}")
    time.sleep(2)
best = {}
k_lat = math.cos(math.radians(C[0]))
for lat, lon in pts:
    dx, dy = (lon - C[1]) * k_lat, lat - C[0]
    r = math.hypot(dx, dy)
    if not 0.12 < r < 0.25:  # МКАД ~ 13-20 км от центра; отсекаем вылетные магистрали с тем же именем
        continue
    k = round(math.degrees(math.atan2(dy, dx)) * 2) / 2
    if r > best.get(k, (0,))[0]:
        best[k] = (r, lat, lon)
ring = [[lon, lat] for _, (_, lat, lon) in sorted(best.items())]
ring.append(ring[0])
json.dump({"type": "Polygon", "coordinates": [ring]}, open(sys.argv[1], "w"))
gaps = sorted(set(round(a * 2) / 2 for a in [x / 2 for x in range(-360, 360)]) - set(best))
print(f"точек OSM: {len(pts)}, вершин полигона: {len(ring)}, пустых секторов 0,5°: {len(gaps)}")
