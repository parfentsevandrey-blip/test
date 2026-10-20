"""Ежедневный слепок всех квартир на продажу на Циане в пределах МКАД.

Схема (2026-10-20, после полного прогона постраничным поиском, который оказался в ~6 раз дороже):
  1. Слой id (daily/idmap.py): все id внутри МКАД через карту Циана -- десятки запросов. Даёт точный ежедневный
     состав рынка: что снято, что появилось.
  2. Атрибуты лотов берутся из вчерашнего слепка; карточки докачиваются только для новых id:
     вторичка -- поиск по полигону МКАД, сортировка по дате публикации (новые сверху), остаток -- по точке дома;
     новостройки -- по ЖК, сортировка по дате.
  3. Цены и флаги обновляются ротацией: каждый день 1/ROTATE ценовых срезов вторички и ЖК, плюс ЖК с горячими лотами
     застройщиков -- ежедневно. Поле checked -- дата последней сверки карточки.
  Первый день (вчерашнего слепка нет): атрибуты из постраничного сбора этого дня (raw/) и полного сбора новостроек
  Москвы (data/full), недостающие срезы досбираются.

Полнота листания поиска: при сортировке по цене лоты с одинаковой ценой меняются местами между страницами (тест: срез
14-15 млн, 844 лота -- 764-785 уникальных); по дате публикации -- 832 без повторов, объединение двух сортировок -- 842.
Поэтому срезы до SLICE_MAX, проход по дате, при недоборе > 0,5% -- ещё по цене.

Итог: history/DAY/lots.jsonl.gz + snapshot.json. Части пишутся в raw/ -> прогон продолжается с места остановки.
usage: python daily/snapshot.py [--day YYYY-MM-DD] [--workers 2]
"""
import argparse, datetime as dt, json, os, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import DATA, MKAD_QUERY, GEO, ROOT, cian, client, day_dir, inside_mkad, log, lot_row, read_jsonl, today, write_jsonl
from idmap import id_layer

BASE = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}}
SEC = dict(BASE, geo={"type": "geo", "value": [MKAD_QUERY]}, building_status={"type": "term", "value": 1})
NEW = dict(BASE, geo={"type": "geo", "value": [MKAD_QUERY]}, building_status={"type": "term", "value": 2})
LIMIT = cian.PAGE_SIZE * cian.MAX_PAGES
SLICE_MAX = 600
SORTS = ("creation_date_desc", "price_object_order")
DATE_DESC = {"type": "term", "value": "creation_date_desc"}
ROTATE = 7
HOUSE_CAP = 600  # сколько домов в день добирать точечно (остаток -- завтра)
# стартовая сетка цен, млн ₽: срезы, которые всё равно больше 1512, делятся пополам
GRID = [0, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 26, 28, 30, 33, 36, 40, 45, 50, 57, 65, 75, 90, 110, 140, 200, 350, 100_000]


def count(q):
    d = client()._page(q, 1)
    return d.get("aggregatedCount") or d["offerCount"], d["offerCount"]


def price_q(q, lo, hi):
    return dict(q, price={"type": "range", "value": {"gte": lo, "lte": hi}})


def leaves(q, ranges):
    """Делит ценовые диапазоны пополам, пока в каждом не больше SLICE_MAX лотов. -> [[lo, hi, n]]"""
    out, stack = [], list(ranges)
    while stack:
        lo, hi = stack.pop(0)
        n, _ = count(price_q(q, lo, hi))
        if n > SLICE_MAX and hi - lo > 10_000:
            mid = (lo + hi) // 2
            stack[:0] = [(lo, mid), (mid + 1, hi)]
        elif n:
            if n > LIMIT:
                log(f"WARNING срез {lo:,}-{hi:,}: {n} > {LIMIT}, часть не пролистается")
            out.append([lo, hi, n])
    return out


def offers_union(q):
    """Лоты среза: проход по дате публикации, при недоборе -- ещё по цене; объединение по id.
    Счётчик берётся с первой страницы прохода (отдельный счётный запрос не нужен). -> (строки, счётчик Циана)"""
    rows, total = {}, 0
    for sort in SORTS:
        n, offers = client().search(dict(q, sort={"type": "term", "value": sort}))
        total = max(total, n)
        for o in offers:
            rows.setdefault(o["id"], lot_row(o))
        if len(rows) >= total * 0.995:
            break
    return list(rows.values()), total


def plan_sec(workers, max_age_days=7):
    """Ценовые срезы вторички. План общий на неделю (history/sec-plan.json): срезы размечены с запасом
    (<= 600 при пределе листания 1512), поэтому дневной прирост объявлений их не переполняет."""
    path = os.path.join(DATA, "sec-plan.json")
    if os.path.exists(path):
        plan = json.load(open(path))
        if plan["day"] >= (dt.date.today() - dt.timedelta(days=max_age_days)).isoformat():
            return plan["leaves"]
    with ThreadPoolExecutor(workers) as ex:
        parts = ex.map(lambda i: leaves(SEC, [(GRID[i] * 10**6, GRID[i + 1] * 10**6 - 1)]), range(len(GRID) - 1))
        out = [x for p in parts for x in p]
    json.dump({"day": dt.date.today().isoformat(), "leaves": out}, open(path, "w"))
    log(f"вторичка: {len(out)} ценовых срезов, {sum(x[2] for x in out)} объявлений")
    return out


def fetch_sec(raw, lo, hi):
    path = os.path.join(raw, f"sec-{lo}-{hi}.jsonl.gz")
    if not os.path.exists(path):
        rows, total = offers_union(price_q(SEC, lo, hi))
        write_jsonl(os.path.join(raw, f"sec-{lo}-{hi}.count"), [{"expected": total, "got": len(rows)}])
        write_jsonl(path, rows)
    c = next(read_jsonl(os.path.join(raw, f"sec-{lo}-{hi}.count")))
    return f"{c['got']} из {c['expected']}"


def fetch_jk(raw, jk):
    path = os.path.join(raw, f"jk-{jk}.jsonl.gz")
    if os.path.exists(path):
        return sum(1 for _ in read_jsonl(path))
    q = dict(BASE, geo={"type": "geo", "value": [{"type": "newobject", "id": jk}]}, building_status={"type": "term", "value": 2})
    rows, expected = offers_union(q)
    if expected > LIMIT:  # крупный ЖК: целиком не пролистать -> нарезка по цене
        rows, expected = [], 0
        for lo, hi, _ in leaves(q, [(0, 3_000_000_000)]):
            part, n = offers_union(price_q(q, lo, hi))
            rows += part
            expected += n
    rows = list({r["id"]: r for r in rows}.values())
    write_jsonl(os.path.join(raw, f"jk-{jk}.count"), [{"expected": expected, "got": len(rows)}])
    return write_jsonl(path, [r for r in rows if inside_mkad(r["lat"], r["lng"])])


def jk_list(max_age_days=7):
    """ЖК внутри МКАД. Раз в неделю каталог Москвы сверяется со списком: новый ЖК проверяется по координатам
    квартир первой страницы и попадает в geo/jk-known.json (id -> внутри МКАД или нет)."""
    known_path = os.path.join(GEO, "jk-known.json")
    if os.path.exists(known_path):
        known = json.load(open(known_path))
    else:
        known = {"checked": "", "jk": {str(j): True for j in json.load(open(os.path.join(GEO, "jk-in-mkad.json")))}}
    if known["checked"] < (dt.date.today() - dt.timedelta(days=max_age_days)).isoformat():
        try:
            fresh = 0
            for n in client().newbuildings(1, 1000):
                if str(n["id"]) in known["jk"]:
                    continue
                q = dict(BASE, geo={"type": "geo", "value": [{"type": "newobject", "id": n["id"]}]})
                offers = client()._page(q, 1)["offersSerialized"]
                known["jk"][str(n["id"])] = any(inside_mkad(*(lambda c: (c.get("lat"), c.get("lng")))((o.get("geo") or {}).get("coordinates") or {}))
                                                 for o in offers)
                fresh += known["jk"][str(n["id"])]
            known["checked"] = dt.date.today().isoformat()
            json.dump(known, open(known_path, "w"), indent=0)
            log(f"каталог ЖК сверен: новых ЖК внутри МКАД {fresh}")
        except Exception as e:  # сверка каталога не должна срывать дневной слепок
            log(f"сверка каталога ЖК не удалась ({str(e)[:80]}), беру известный список")
    return sorted(int(k) for k, v in known["jk"].items() if v)


def square(lat, lng, d=0.0004):
    return {"type": "polygon", "name": "дом", "coordinates": [[str(x), str(y)] for x, y in
            ((lng - d, lat - d), (lng + d, lat - d), (lng + d, lat + d), (lng - d, lat + d), (lng - d, lat - d))]}


def fetch_new_sec(raw, need, layer):
    """Карточки новых id вторички: свежие публикации сверху выдачи МКАД; кого там нет -- по точке дома."""
    path = os.path.join(raw, "new-sec.jsonl.gz")
    if os.path.exists(path):
        return sum(1 for _ in read_jsonl(path))
    c, rows, idle = client(), {}, 0
    for p in range(1, cian.MAX_PAGES + 1):
        offers = c._page(dict(SEC, sort=DATE_DESC), p)["offersSerialized"]
        if not offers:
            break
        hit = sum(o["id"] in need for o in offers)
        rows.update((o["id"], lot_row(o)) for o in offers)
        if need <= rows.keys():
            break
        idle = 0 if hit else idle + 1
        if idle >= 3:
            break
    points = {}
    for i in need - rows.keys():
        points.setdefault((layer[i]["lat"], layer[i]["lng"]), set()).add(i)
    for (lat, lng), ids in list(points.items())[:HOUSE_CAP]:
        q = dict(BASE, geo={"type": "geo", "value": [square(lat, lng)]}, building_status={"type": "term", "value": 1}, sort=DATE_DESC)
        _, offers = c.search(q, max_pages=3)
        rows.update((o["id"], lot_row(o)) for o in offers)
    log(f"вторичка: новых id {len(need)}, карточек получено {len(need & rows.keys())}, домов точечно {min(len(points), HOUSE_CAP)}")
    return write_jsonl(path, rows.values())


def fetch_new_jk(raw, jk, need):
    """Карточки новых id ЖК: выдача ЖК по дате публикации, пока не найдены все нужные."""
    path = os.path.join(raw, f"newjk-{jk}.jsonl.gz")
    if os.path.exists(path):
        return sum(1 for _ in read_jsonl(path))
    q = dict(BASE, geo={"type": "geo", "value": [{"type": "newobject", "id": jk}]}, building_status={"type": "term", "value": 2},
             sort=DATE_DESC)
    c, rows = client(), {}
    for p in range(1, cian.MAX_PAGES + 1):
        offers = c._page(q, p)["offersSerialized"]
        if not offers:
            break
        rows.update((o["id"], lot_row(o)) for o in offers)
        if need <= rows.keys():
            break
    return write_jsonl(path, rows.values())


def prev_lots(day):
    days = sorted(x for x in os.listdir(DATA) if len(x) == 10 and x < day and os.path.exists(os.path.join(DATA, x, "lots.jsonl.gz")))
    if not days:
        return {}, None
    return {r["id"]: r for r in read_jsonl(os.path.join(DATA, days[-1], "lots.jsonl.gz"))}, days[-1]


def bootstrap_new():
    """Первый день: новостройки из полного сбора по Москве (data/full, формат flat_row) -> записи lot_row без
    полей, которых там нет (они заполнятся при ротационной сверке в течение недели)."""
    path = os.path.join(ROOT, "data", "full", "cian-moscow-new.dedup.jsonl")
    if not os.path.exists(path):
        return {}
    day = dt.date.fromtimestamp(os.path.getmtime(path)).isoformat()
    out = {}
    for f in read_jsonl(path):
        if not inside_mkad(f.get("lat"), f.get("lng")):
            continue
        area, price, metro = f.get("area_total"), f.get("price_rub"), (f.get("metro") or [[None, None, None]])[0]
        out[f["offer_id"]] = {
            "id": f["offer_id"], "cian_id": f["offer_id"], "url": f["url"], "kind": "new", "category": "newBuildingFlatSale",
            "share": None, "price": price, "pm2": round(price / area) if price and area else None, "area": area,
            "living": f.get("area_living"), "kitchen": f.get("area_kitchen"), "rooms": f.get("rooms"), "flat_type": f.get("flat_type"),
            "apartments": f.get("is_apartments"), "floor": f.get("floor"), "floors": f.get("floors_total"),
            "decoration": f.get("decoration"), "furniture": None, "build_year": None, "material": f.get("material"),
            "deadline": None, "deadline_done": f.get("deadline_done"), "jk_id": f.get("jk_id"), "jk": f.get("jk_name"),
            "house_id": f.get("house_id"), "house": f.get("house_name"), "okrug": None, "raion": None, "street": None,
            "address": f.get("address"), "lat": f.get("lat"), "lng": f.get("lng"), "metro": metro[0], "metro_min": metro[1],
            "metro_by": metro[2], "seller": "developer" if f.get("from_developer") else "agent", "user_id": None,
            "agency": f.get("seller"), "sale_type": f.get("sale_type"), "mortgage": f.get("mortgage"),
            "created": (f.get("created") or "")[:19], "added_ts": None, "photos": len(f.get("photos") or []),
            "layout": bool(f.get("layout_photo")), "descr_len": len(f.get("description") or ""), "checked": day}
    return out


def run(tasks, workers, label):
    """tasks: [(name, fn, args)]. Упавшие части -- повтор через 5 минут, затем ещё раз через 15."""
    for attempt, pause in enumerate((0, 300, 900)):
        if not tasks:
            break
        if pause:
            log(f"{label}: повтор {len(tasks)} частей через {pause} с")
            time.sleep(pause)
        failed, done = [], 0
        with ThreadPoolExecutor(workers) as ex:
            futs = {ex.submit(fn, *args): (name, fn, args) for name, fn, args in tasks}
            for f in as_completed(futs):
                done += 1
                try:
                    n = f.result()
                    if done % 10 == 0 or done == len(tasks):
                        log(f"{label}: {done}/{len(tasks)} частей ({futs[f][0]}: {n})")
                except Exception as e:
                    failed.append(futs[f])
                    log(f"{label}: {futs[f][0]} не собран: {str(e)[:100]}")
        tasks = failed
    return [t[0] for t in tasks]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--day", default=today())
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--delay", type=float, nargs=2, help="пауза между запросами одного потока, с")
    a = ap.parse_args()
    if a.delay:
        cian.DEFAULT_DELAY = tuple(a.delay)
    d = day_dir(a.day)
    raw = os.path.join(d, "raw")
    os.makedirs(raw, exist_ok=True)
    t0 = time.time()

    # 1. слой id
    lpath = os.path.join(raw, "idlayer.json")
    if os.path.exists(lpath):
        cached = json.load(open(lpath))
        layer, lstats = {int(k): v for k, v in cached["ids"].items()}, cached["stats"]
    else:
        layer, lstats = id_layer(workers=a.workers)
        json.dump({"ids": layer, "stats": lstats}, open(lpath, "w"))

    # 2. атрибуты: вчерашний слепок (первый день -- постраничный сбор этого дня и полный сбор новостроек)
    attrs, since = prev_lots(a.day)
    plan = plan_sec(a.workers)
    tasks = []
    if not attrs:
        attrs = bootstrap_new()
        tasks += [(f"sec {lo:,}-{hi:,}", fetch_sec, (raw, lo, hi)) for lo, hi, _ in plan
                  if not os.path.exists(os.path.join(raw, f"sec-{lo}-{hi}.jsonl.gz"))]
        for f in os.listdir(raw):  # уже собранные сегодня срезы
            if f.startswith("sec-") and f.endswith(".jsonl.gz"):
                attrs.update((r["id"], r) for r in read_jsonl(os.path.join(raw, f)))
        log(f"первый день: атрибуты из готовых сборов -- {len(attrs)} лотов, досбор срезов вторички: {len(tasks)}")

    # 3. новые id -> карточки
    need = set(layer) - set(attrs)
    need_sec = {i for i in need if layer[i]["kind"] == "sec"}
    by_jk = {}
    for i in need - need_sec:
        by_jk.setdefault(layer[i]["jk_id"], set()).add(i)
    by_jk.pop(None, None)
    if need_sec and not tasks:
        tasks.append(("новые id вторички", fetch_new_sec, (raw, need_sec, layer)))
    tasks += [(f"новые id ЖК {jk}", fetch_new_jk, (raw, jk, ids)) for jk, ids in by_jk.items()]

    # 4. ротационная сверка цен + ЖК с горячими лотами застройщиков
    k = dt.date.fromisoformat(a.day).toordinal() % ROTATE
    tasks += [(f"сверка sec {lo:,}-{hi:,}", fetch_sec, (raw, lo, hi)) for n, (lo, hi, _) in enumerate(plan)
              if n % ROTATE == k and not any(t[2][1:3] == (lo, hi) for t in tasks if t[1] is fetch_sec)]
    hot_path = os.path.join(DATA, "hot.json")
    hot = json.load(open(hot_path)) if os.path.exists(hot_path) else []
    hot_jk = list(dict.fromkeys(attrs[i]["jk_id"] for i in hot if i in attrs and attrs[i].get("seller") == "developer" and attrs[i].get("jk_id")))[:30]
    refresh = sorted({j for j in jk_list() if j % ROTATE == k} | set(hot_jk))
    tasks += [(f"сверка ЖК {jk}", fetch_jk, (raw, jk)) for jk in refresh]
    log(f"слой id: {len(layer)}; новых id {len(need)} (вторичка {len(need_sec)}, ЖК {len(by_jk)}); "
        f"сверка: срезов {sum(1 for t in tasks if str(t[0]).startswith('сверка sec'))}, ЖК {len(refresh)}")
    failed = run(tasks, a.workers, "слепок")

    # 5. итог дня: состав -- слой id, атрибуты -- свежие карточки дня поверх известных
    fresh = {}
    for f in os.listdir(raw):
        if f.endswith(".jsonl.gz"):
            for r in read_jsonl(os.path.join(raw, f)):
                fresh[r["id"]] = dict(r, checked=a.day)
    attrs.update(fresh)
    lots = [dict(attrs[i], lat=attrs[i].get("lat") or v["lat"], lng=attrs[i].get("lng") or v["lng"])
            for i, v in layer.items() if i in attrs]
    n = write_jsonl(os.path.join(d, "lots.jsonl.gz"), lots)
    cnt = [next(read_jsonl(os.path.join(raw, f))) for f in os.listdir(raw) if f.endswith(".count")]
    summary = {"day": a.day, "id_layer": lstats, "ids": len(layer), "lots_with_card": n,
               "sec": sum(r["kind"] == "sec" for r in lots), "new": sum(r["kind"] == "new" for r in lots),
               "no_card_yet": len(layer) - n, "new_ids": len(need), "since": since,
               "checked_today": sum(1 for i in layer if i in fresh),
               "refresh_completeness": {"expected": sum(c["expected"] for c in cnt), "got": sum(c["got"] for c in cnt)},
               "failed_parts": failed, "minutes": round((time.time() - t0) / 60, 1)}
    json.dump(summary, open(os.path.join(d, "snapshot.json"), "w"), ensure_ascii=False, indent=1)
    log("слепок:", json.dumps(summary, ensure_ascii=False))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
