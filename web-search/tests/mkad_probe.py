"""Разведка: сколько квартир на продажу в Москве и внутри МКАД, и какой формат полигона принимает поиск Циана."""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "collectors"))
import cian
c = cian.Cian()
ring = json.load(open("data/geo/mkad.json"))["coordinates"][0]
simple = ring[::8] + [ring[0]]
base = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}}
def count(q, label):
    try:
        d = c._page(q, 1)
        print(f"{label}: offerCount {d['offerCount']}, aggregatedCount {d.get('aggregatedCount')}, на стр. {len(d['offersSerialized'])}", flush=True)
        return d
    except Exception as e:
        print(f"{label}: ошибка {str(e)[:150]}", flush=True)
reg = dict(base, region={"type": "terms", "value": [1]})
count(reg, "Москва, все")
count(dict(reg, building_status={"type": "term", "value": 1}), "Москва, вторичка")
count(dict(reg, building_status={"type": "term", "value": 2}), "Москва, новостройки")
forms = {
    "polygon str": {"type": "polygon", "name": "МКАД", "coordinates": [[str(x), str(y)] for x, y in simple]},
    "polygon num": {"type": "polygon", "name": "МКАД", "coordinates": [[x, y] for x, y in simple]},
}
for name, g in forms.items():
    d = count(dict(base, geo={"type": "geo", "value": [g]}), f"полигон МКАД ({name})")
    if d and d["offerCount"]:
        json.dump(d["jsonQuery"], open("data/geo/cian-polygon-jsonquery.json", "w"), ensure_ascii=False)
        print("  jsonQuery ответа:", json.dumps(d["jsonQuery"], ensure_ascii=False)[:300])
        count(dict(base, geo={"type": "geo", "value": [g]}, building_status={"type": "term", "value": 1}), "  МКАД, вторичка")
        count(dict(base, geo={"type": "geo", "value": [g]}, building_status={"type": "term", "value": 2}), "  МКАД, новостройки")
        break
