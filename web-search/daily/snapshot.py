"""Ежедневный слепок всех квартир на продажу на Циане в пределах МКАД.

Вторичка: полигон МКАД + building_status=1, нарезка по цене до срезов <= 1512 (свёртки во вторичке нет).
Новостройки: обход по ЖК внутри МКАД (geo/jk-in-mkad.json, раз в неделю сверяется с каталогом) с building_status=2 -- на уровне полигона Циан
сворачивает похожие квартиры ЖК в одну карточку, а внутри ЖК свёртки нет. Лоты за границей МКАД отбрасываются
по координатам.

Полнота листания: при сортировке по цене лоты с одинаковой ценой (ровные 14 000 000 и т. п.) меняются местами между
страницами -- часть повторяется, часть теряется (тест 2026-10-20: срез 14-15 млн, 844 лота, по цене -- 764-785 уникальных).
Сортировка по дате публикации совпадений почти не имеет (832 из 844, без повторов), объединение двух сортировок -- 842.
Поэтому: срезы до SLICE_MAX лотов, первый проход по дате, при недоборе > 0,5% -- второй проход по цене и объединение.

Каждая часть (ценовой срез / ЖК) пишется в raw/ отдельным файлом -> прогон можно продолжить с места остановки.
Итог: history/DAY/lots.jsonl.gz + snapshot.json (полнота относительно счётчиков Циана).
usage: python daily/snapshot.py [--day YYYY-MM-DD] [--workers 2] [--only sec|new] [--limit N]
"""
import argparse, datetime as dt, json, os, sys, time
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import DATA, MKAD_QUERY, GEO, cian, client, day_dir, inside_mkad, log, lot_row, read_jsonl, today, write_jsonl

BASE = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}}
SEC = dict(BASE, geo={"type": "geo", "value": [MKAD_QUERY]}, building_status={"type": "term", "value": 1})
NEW = dict(BASE, geo={"type": "geo", "value": [MKAD_QUERY]}, building_status={"type": "term", "value": 2})
LIMIT = cian.PAGE_SIZE * cian.MAX_PAGES
SLICE_MAX = 600
SORTS = ("creation_date_desc", "price_object_order")
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
    ap.add_argument("--only", choices=["sec", "new"])
    ap.add_argument("--limit", type=int, help="тест: не больше N частей каждого вида")
    ap.add_argument("--delay", type=float, nargs=2, help="пауза между запросами одного потока, с")
    a = ap.parse_args()
    if a.delay:
        cian.DEFAULT_DELAY = tuple(a.delay)
    d = day_dir(a.day)
    raw = os.path.join(d, "raw")
    os.makedirs(raw, exist_ok=True)
    t0, failed, expect = time.time(), [], {}

    if a.only != "new":
        plan = plan_sec(a.workers)[: a.limit]
        failed += run([(f"sec {lo:,}-{hi:,}", fetch_sec, (raw, lo, hi)) for lo, hi, _ in plan], a.workers, "вторичка")
    if a.only != "sec":
        jks = jk_list()[: a.limit]
        expect["new_cian_polygon"] = count(NEW)[1]
        failed += run([(f"ЖК {jk}", fetch_jk, (raw, jk)) for jk in jks], a.workers, "новостройки")

    for kind, prefix in (("sec", "sec-"), ("new_by_jk", "jk-")):  # полнота: счётчики Циана против собранного
        cnt = [next(read_jsonl(os.path.join(raw, f))) for f in os.listdir(raw) if f.startswith(prefix) and f.endswith(".count")]
        if cnt:
            expect[kind] = {"expected": sum(c["expected"] for c in cnt), "got": sum(c["got"] for c in cnt)}
    lots, sec_ids = {}, set()
    for f in sorted(os.listdir(raw)):
        if f.endswith(".jsonl.gz") and (a.only is None or f.startswith(a.only if a.only == "sec" else "jk-")):
            for r in read_jsonl(os.path.join(raw, f)):
                if f.startswith("sec-"):
                    sec_ids.add(r["id"])
                if inside_mkad(r["lat"], r["lng"]):
                    lots[r["id"]] = r
    n = write_jsonl(os.path.join(d, "lots.jsonl.gz"), lots.values())
    if "sec" in expect:
        expect["sec"]["unique"] = len(sec_ids)
    summary = {"day": a.day, "lots": n, "sec": sum(r["kind"] == "sec" for r in lots.values()),
               "new": sum(r["kind"] == "new" for r in lots.values()), "expected": expect, "failed_parts": failed,
               "minutes": round((time.time() - t0) / 60, 1)}
    json.dump(summary, open(os.path.join(d, "snapshot.json"), "w"), ensure_ascii=False, indent=1)
    log("слепок:", json.dumps(summary, ensure_ascii=False))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
