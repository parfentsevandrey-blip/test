"""Просмотры лотов из дневного слепка (статистика карточки: всего + по дням за 10 дней).

Циан отдаёт историю за 10 дней, поэтому каждый лот достаточно опрашивать раз в ROTATE дней (по умолчанию 8):
дневной ряд остаётся непрерывным, а нагрузка -- около 1/8 слепка в день. Очередь дня:
  1) горячие лоты из прошлого анализа (data/daily/hot.json) -- каждый день, чтобы видеть форму кривой;
  2) лоты, которых ещё не опрашивали (новые публикации; в первые дни -- весь слепок, сегменты интереса первыми);
  3) лоты, опрошенные ROTATE и более дней назад, самые давние первыми.
Дневной бюджет запросов ограничивает очередь; остаток переходит на следующий день.
Итог: data/daily/DAY/views.jsonl.gz, состояние опроса -- data/daily/views-state.json.
usage: python daily/views.py [--day YYYY-MM-DD] [--budget 12000] [--workers 2] [--rotate 8]
"""
import argparse, datetime as dt, json, os, random, re, sys
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import DATA, cian, client, day_dir, log, read_jsonl, today, write_jsonl

STAT = "https://api.cian.ru/offer-card/v1/get-offer-card-statistic/"
STATE = os.path.join(DATA, "views-state.json")


def interest_first(r):
    """Порядок первичного обхода: новостройки и свежая вторичка раньше старого фонда."""
    fresh = r["kind"] == "sec" and ((r.get("build_year") or 0) >= 2012 or r.get("jk_id"))
    return 0 if r["kind"] == "new" else (1 if fresh else 2)


def queue(lots, state, hot, day, rotate, budget):
    d0 = dt.date.fromisoformat(day)
    age = lambda i: (d0 - dt.date.fromisoformat(state[i])).days if i in state else None
    ids = set(lots)
    q = [i for i in hot if i in ids and age(i) != 0]
    seen = set(q)
    never = [i for i in ids - seen if i not in state]
    # свежие публикации -- первыми; остальное в случайном порядке: каждый день -- случайная выборка рынка
    # (несмещённая база для кластеров), внутри неё сегменты интереса раньше старого фонда
    random.Random(day).shuffle(never)
    fresh_from = (d0 - dt.timedelta(days=3)).isoformat()

    def order(i):
        r = lots[i]
        old_stock_late = interest_first(r) == 2 and random.Random(i).random() < .5  # половина старого фонда -- в конец
        return (r["created"][:10] < fresh_from, old_stock_late)
    never.sort(key=order)
    q += never
    seen.update(never)
    q += sorted((i for i in ids - seen if age(i) >= rotate), key=lambda i: -age(i))
    return q[:budget], len(q)


def fetch(day_views, r):
    path = os.path.join(day_views, f"{r['id']}.json")
    if os.path.exists(path):
        return json.load(open(path))
    s = client()._req("GET", f"{STAT}?offerId={r['id']}&offerCreationDate={r['created'][:10]}", expect_json=True).json()
    total = int(re.sub(r"\D", "", (s.get("totalViews") or "0").split("просм")[0]) or 0)
    out = {"id": r["id"], "total": total,
           "daily": sorted([x["date"], x["views"]] for x in (s.get("daily") or {}).get("dailyViews") or [])}
    json.dump(out, open(path + ".tmp", "w"))
    os.replace(path + ".tmp", path)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--day", default=today())
    ap.add_argument("--budget", type=int, default=12000)
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--rotate", type=int, default=8, help="раз в сколько дней опрашивать каждый лот (<= 9)")
    ap.add_argument("--delay", type=float, nargs=2)
    a = ap.parse_args()
    if a.delay:
        cian.DEFAULT_DELAY = tuple(a.delay)
    d = day_dir(a.day)
    vdir = os.path.join(d, "views")
    os.makedirs(vdir, exist_ok=True)
    lots = {r["id"]: r for r in read_jsonl(os.path.join(d, "lots.jsonl.gz"))
            if r.get("created") and r.get("category") in ("flatSale", "newBuildingFlatSale")}
    state = json.load(open(STATE)) if os.path.exists(STATE) else {}
    state = {int(k): v for k, v in state.items()}
    hot_path = os.path.join(DATA, "hot.json")
    hot = json.load(open(hot_path)) if os.path.exists(hot_path) else []
    todo, backlog = queue(lots, state, hot, a.day, a.rotate, a.budget)
    log(f"просмотры: в слепке {len(lots)}, в очереди {backlog}, сегодня {len(todo)} (горячих {len(hot)})")

    got, failed = {}, 0
    with ThreadPoolExecutor(a.workers) as ex:
        futs = {ex.submit(fetch, vdir, lots[i]): i for i in todo}
        for n, f in enumerate(as_completed(futs), 1):
            try:
                got[futs[f]] = f.result()
                state[futs[f]] = a.day
            except Exception as e:
                failed += 1
                log(f"  {futs[f]}: {str(e)[:100]}")
            if n % 500 == 0:
                log(f"просмотры: {n}/{len(todo)}")
                json.dump(state, open(STATE, "w"))
    json.dump(state, open(STATE, "w"))
    write_jsonl(os.path.join(d, "views.jsonl.gz"), [dict(v, day=a.day) for v in got.values()])
    log(f"просмотры: получено {len(got)} из {len(todo)}, не получено {failed}, ещё в очереди {backlog - len(got)}")


if __name__ == "__main__":
    main()
