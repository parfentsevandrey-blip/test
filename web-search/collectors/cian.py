"""Сборщик ЖК и квартир с cian.ru.

Как Циан отдаёт данные (проверено 2026-10-19, см. docs/research-domrf-cian.md):
- WAF пропускает запросы только с TLS-отпечатком браузера -> curl_cffi impersonate="chrome", браузер не нужен.
- Выдача квартир: POST https://api.cian.ru/search-offers/v2/search-offers-desktop/ {"jsonQuery": {...}},
  28 объявлений на страницу, не больше 54 страниц (~1500) на запрос -> большие выборки режем по цене.
- Каталог ЖК и карточка ЖК: состояние SSR в window._cianConfig[<app>] внутри HTML.

Примеры:
  python collectors/cian.py jk --region 1 --pages 2 --out data/cian-jk.jsonl
  python collectors/cian.py jk-card --url https://zhk-soyuz-i.cian.ru/ --out data/cian-jk-card.json
  python collectors/cian.py flats --newobject 4025502 --out data/cian-flats.jsonl
  python collectors/cian.py flats --region 1 --new --price-from 10000000 --price-to 20000000 --out data/x.jsonl
Прокси (если понадобится): CIAN_PROXY=http://user:pass@host:port
"""
import argparse, json, os, random, re, sys, threading, time

from curl_cffi import requests

API = "https://api.cian.ru/search-offers/v2/search-offers-desktop/"
NB_API = "https://api.cian.ru/newbuilding-search/v1/get-newbuildings-for-serp/"
PAGE_SIZE, MAX_PAGES = 28, 54
STATE_RE = re.compile(r"window\._cianConfig\['([a-z-]+)'\] = \(window\._cianConfig\['[a-z-]+'\] \|\| \[\]\)\.concat\(")


class Cian:
    def __init__(self, delay=(1.5, 3.0)):
        proxy = os.environ.get("CIAN_PROXY")
        self.s = requests.Session(impersonate="chrome", proxies={"https": proxy, "http": proxy} if proxy else None)
        self.s.headers.update({"accept-language": "ru-RU,ru;q=0.9", "origin": "https://www.cian.ru", "referer": "https://www.cian.ru/"})
        self.delay = delay
        self.counts = []  # offerCount каждого первого запроса search() -- для сверки полноты

    def _req(self, method, url, expect_json=False, **kw):
        for attempt in range(5):
            time.sleep(random.uniform(*self.delay))
            try:
                r = self.s.request(method, url, timeout=60, **kw)
            except Exception as e:  # обрыв соединения/таймаут
                problem = e.__class__.__name__
            else:
                # 200 с HTML вместо JSON или страница капчи с кодом 200 = блок, а не данные
                captcha = r.status_code == 200 and len(r.text) < 200_000 and \
                    re.search(r"<title>\s*(Captcha|Verification|Ошибка)", r.text[:5000]) is not None
                if r.status_code == 200 and not captcha and not (expect_json and not r.text.lstrip().startswith("{")):
                    return r
                if r.status_code not in (200, 403, 429, 500, 502, 503, 504):
                    r.raise_for_status()
                problem = f"{r.status_code} {r.text[:60]!r}"
            wait = 10 * 2 ** attempt
            print(f"  {problem} on {url[:80]} -> sleep {wait}s", file=sys.stderr)
            time.sleep(wait)
        raise RuntimeError(f"gave up: {url}")

    def state(self, url, app, key="initialState"):
        html = self._req("GET", url).text
        marker = f'"key":"{key}","value":'
        starts = [m for m in STATE_RE.finditer(html)]
        for i, m in enumerate(starts):
            if m.group(1) != app:
                continue
            # массив целиком бывает не-JSON (JS-литералы в соседних ключах) -> разбираем только нужное значение
            end = starts[i + 1].start() if i + 1 < len(starts) else len(html)
            j = html.find(marker, m.end(), end)
            if j == -1:
                continue
            try:
                return json.JSONDecoder().raw_decode(html, j + len(marker))[0]
            except json.JSONDecodeError:
                continue
        raise KeyError(f"{app}/{key} not found on {url}")

    # --- квартиры -----------------------------------------------------------------------------
    def search(self, json_query, max_pages=MAX_PAGES):
        """Отдаёт (сколько объявлений можно пролистать, генератор сырых объявлений).

        Две ловушки выдачи (найдены стресс-тестом 2026-10-19):
        - без явной сортировки (ML-ранжирование) выдача молча обрывается примерно на 470 объявлениях;
          поэтому _page всегда ставит sort=price_object_order;
        - на уровне региона/района разные квартиры одного ЖК со схожей планировкой сворачиваются в одну
          карточку («ещё N таких же квартир в этом ЖК», similar.count / multi_id): листается только
          aggregatedCount. Внутри фильтра по ЖК свёртки нет -> полные данные даёт обход по ЖК (by_jk).
        """
        first = self._page(json_query, 1)
        total = first.get("aggregatedCount") or first["offerCount"]
        self.last_offer_count = first["offerCount"]
        self.counts.append(first["offerCount"])

        def gen():
            yield from first["offersSerialized"]
            pages = min(max_pages, MAX_PAGES, -(-total // PAGE_SIZE))
            for p in range(2, pages + 1):
                offers = self._page(json_query, p)["offersSerialized"]
                if not offers:
                    break
                yield from offers

        return total, gen()

    def _page(self, json_query, page):
        q = dict(json_query, page={"type": "term", "value": page})
        q.setdefault("sort", {"type": "term", "value": "price_object_order"})
        return self._req("POST", API, expect_json=True, json={"jsonQuery": q}).json()["data"]

    def search_all(self, json_query, lo=0, hi=3_000_000_000):
        """Обходит лимит 1512 на запрос: делит ценовой диапазон пополам, пока срез не влезет."""
        q = dict(json_query, price={"type": "range", "value": {"gte": lo, "lte": hi}})
        total, offers = self.search(q)
        if total <= PAGE_SIZE * MAX_PAGES or hi - lo < 10_000:
            if total > PAGE_SIZE * MAX_PAGES:
                print(f"  WARNING: срез {lo:,}-{hi:,} нельзя делить дальше, потеряно ~{total - PAGE_SIZE * MAX_PAGES} "
                      f"(сузьте запрос: комнаты, район, ЖК)", file=sys.stderr)
            yield from offers
            return
        mid = (lo + hi) // 2
        yield from self.search_all(json_query, lo, mid)
        yield from self.search_all(json_query, mid + 1, hi)

    def jk_flats(self, jk_id, new_only=False):
        """Все квартиры ЖК (без свёртки), с нарезкой по цене, если их больше 1512.
        new_only -- только новостройки (building_status=2, как фильтр «Новостройка» на сайте), без вторички в ЖК."""
        q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2},
             "geo": {"type": "geo", "value": [{"type": "newobject", "id": jk_id}]}}
        if new_only:
            q["building_status"] = {"type": "term", "value": 2}
        yield from self.search_all(q)

    # --- ЖК -----------------------------------------------------------------------------------
    def newbuildings(self, region, max_pages=1):
        """Каталог ЖК через JSON-API (найден в бандле newbuilding-search-frontend); HTML -- запасной путь."""
        try:
            yield from self._newbuildings_api(region, max_pages)
        except Exception as e:
            print(f"  каталог ЖК через API не удался ({str(e)[:80]}), беру HTML", file=sys.stderr)
            yield from self._newbuildings_html(region, max_pages)

    def _newbuildings_api(self, region, max_pages):
        for p in range(max_pages):
            body = {"jsonQuery": {"_type": "newobjectsale", "engine_version": {"type": "term", "value": 2},
                                  "region": {"type": "terms", "value": [region]}},
                    "uri": "/newobjects/list/", "subdomain": "www", "offset": p * 25, "count": 25}
            d = self._req("POST", NB_API, expect_json=True, json=body).json()
            if not d.get("newbuildings"):
                break
            print(f"  page {p + 1}: {len(d['newbuildings'])} of {d.get('total')}", file=sys.stderr)
            yield from d["newbuildings"]

    def _newbuildings_html(self, region, max_pages):
        for p in range(1, max_pages + 1):
            url = f"https://www.cian.ru/newobjects/list/?deal_type=sale&engine_version=2&offer_type=newobject&region={region}&p={p}"
            od = self.state(url, "newbuilding-search-frontend")["offersData"]
            if not od["newbuildings"]:
                break
            print(f"  page {p}: {len(od['newbuildings'])} of {od['total']}", file=sys.stderr)
            yield from od["newbuildings"]

    def newbuilding_card(self, url):
        return self.state(url, "newbuilding-card-desktop-fichering-frontend")


def flat_row(o):
    """Плоская запись квартиры из сырого объявления Циана."""
    bt, b, nb, geo = o.get("bargainTerms") or {}, o.get("building") or {}, o.get("newbuilding") or {}, o.get("geo") or {}
    house, dl = nb.get("house") or {}, b.get("deadline") or {}
    coords = geo.get("coordinates") or {}
    return {
        "source": "cian", "offer_id": o["id"], "url": o["fullUrl"].split("?")[0],
        "price_rub": bt.get("priceRur"), "sale_type": bt.get("saleType"), "mortgage": bt.get("mortgageAllowed"),
        "rooms": o.get("roomsCount"), "flat_type": o.get("flatType"), "is_apartments": o.get("isApartments"),
        "area_total": _f(o.get("totalArea")), "area_living": _f(o.get("livingArea")), "area_kitchen": _f(o.get("kitchenArea")),
        "floor": o.get("floorNumber"), "floors_total": b.get("floorsCount"), "decoration": o.get("decoration"),
        "material": b.get("materialType"), "deadline_year": dl.get("year"), "deadline_quarter": dl.get("quarter"),
        "deadline_done": dl.get("isComplete"),
        "jk_id": nb.get("id"), "jk_name": nb.get("name"), "jk_url": o.get("jkUrl"),
        "house_id": house.get("id"), "house_name": house.get("name"), "section": house.get("section"),
        "from_developer": o.get("fromDeveloper"), "seller": (o.get("user") or {}).get("agencyName"),
        "duplicates": (o.get("similar") or {}).get("count", 0),
        "address": geo.get("userInput"), "lat": coords.get("lat"), "lng": coords.get("lng"),
        "metro": [(u.get("name"), u.get("time"), u.get("transportType")) for u in geo.get("undergrounds") or []],
        "photos": [p["fullUrl"] for p in o.get("photos") or []][:20],
        "layout_photo": next((p["fullUrl"] for p in o.get("photos") or [] if p.get("isLayout")), None),
        "created": o.get("creationDate"), "added": o.get("added"),
        "description": o.get("description"),
    }


def jk_row(n):
    return {
        "source": "cian", "jk_id": n["id"], "name": n.get("name"), "url": n.get("url"),
        "status": n.get("specialStatusDisplay"), "is_problem": n.get("isProblem"), "fz214": n.get("isFz_214"),
        "reliable_flag": n.get("showReliableFlag"), "material": (n.get("firstMaterial") or {}).get("name"),
        "address": " / ".join(a["title"] for a in n.get("addressLine") or []),
        "builders": [b.get("name") for b in n.get("builders") or []],
        "offers_from_developer": n.get("fromDeveloperPropsCount"), "offers_from_agents": n.get("fromAgentsPropsCount"),
        "rooms_from_developer": [
            {"rooms": r.get("countDisplay"), "count": r.get("offersCount"), "min_area": r.get("minTotalArea"),
             "price_min": _f(r.get("minPrice2")), "price_max": _f(r.get("maxPrice2"))}
            for r in n.get("fromDevelopersRooms") or []],
        "metro": (lambda u: u and (u.get("name"), u.get("calcTimeWalk"), u.get("transportType")))(n.get("undergroundInfo")),
        "labels": [x.get("text") for x in n.get("labels") or []],
        "rating": (n.get("reviewStat") or {}).get("totalRate"),
    }


def _f(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def write_jsonl(rows, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    n = 0
    with open(path, "w") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            n += 1
    print(f"wrote {n} rows -> {path}", file=sys.stderr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["jk", "jk-card", "flats"])
    ap.add_argument("--region", type=int, default=1, help="1 = Москва, 2 = СПб, 4593 = МО")
    ap.add_argument("--pages", type=int)
    ap.add_argument("--url")
    ap.add_argument("--newobject", type=int, help="ID ЖК на Циане")
    ap.add_argument("--new", action="store_true", help="только новостройки")
    ap.add_argument("--price-from", type=int)
    ap.add_argument("--price-to", type=int)
    ap.add_argument("--all", action="store_true", help="обойти лимит 1512 нарезкой по цене (свёрнутая выдача!)")
    ap.add_argument("--by-jk", action="store_true", help="полный сбор новостроек региона: каталог ЖК -> квартиры каждого ЖК")
    ap.add_argument("--workers", type=int, default=1, help="параллельных ЖК в режиме --by-jk (безопасно до 4)")
    ap.add_argument("--raw", action="store_true", help="писать сырые объекты Циана")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    c = Cian()

    if a.cmd == "jk":
        rows = c.newbuildings(a.region, a.pages or 1)
        write_jsonl(rows if a.raw else map(jk_row, rows), a.out)
    elif a.cmd == "jk-card":
        os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
        json.dump(c.newbuilding_card(a.url), open(a.out, "w"), ensure_ascii=False, indent=1)
        print(f"wrote {a.out}", file=sys.stderr)
    elif a.by_jk:
        jk_ids = [n["id"] for n in c.newbuildings(a.region, a.pages or 1000)]
        write_jsonl(by_jk(jk_ids, a.workers, a.raw, a.new, a.out + ".jk-stats.jsonl"), a.out)
    else:
        q = {"_type": "flatsale", "engine_version": {"type": "term", "value": 2}}
        if a.newobject:
            q["geo"] = {"type": "geo", "value": [{"type": "newobject", "id": a.newobject}]}
        else:
            q["region"] = {"type": "terms", "value": [a.region]}
        if a.new:
            q["building_status"] = {"type": "term", "value": 2}
        if a.price_from or a.price_to:
            q["price"] = {"type": "range", "value": {k: v for k, v in (("gte", a.price_from), ("lte", a.price_to)) if v}}
        if a.newobject:
            offers = c.jk_flats(a.newobject)
        elif a.all:
            offers = c.search_all(q, a.price_from or 0, a.price_to or 3_000_000_000)
        else:
            total, offers = c.search(q, a.pages or 1)
            print(f"  можно пролистать {total} из {c.last_offer_count} объявлений", file=sys.stderr)
        write_jsonl(dedup(offers if a.raw else map(flat_row, offers)), a.out)


def dedup(rows):
    seen = set()
    for r in rows:
        k = r.get("offer_id") or r.get("id")
        if k not in seen:
            seen.add(k)
            yield r


def by_jk(jk_ids, workers, raw, new_only=False, stats_path=None):
    """Квартиры по каждому ЖК; ЖК обрабатываются параллельно, у каждого потока своя сессия.
    В stats_path пишется по ЖК: offerCount, собрано, ошибка. Упавшие ЖК повторяются один раз после паузы."""
    from concurrent.futures import ThreadPoolExecutor, as_completed
    local = threading.local()
    stats = open(stats_path, "w") if stats_path else None
    seen = set()

    def one(jk):
        if not hasattr(local, "c"):
            local.c = Cian()
        local.c.counts = []
        offers = list(local.c.jk_flats(jk, new_only))
        return offers, (local.c.counts[0] if local.c.counts else None)

    def run(ids, attempt):
        failed = []
        with ThreadPoolExecutor(workers) as ex:
            futs = {ex.submit(one, jk): jk for jk in ids}
            for i, f in enumerate(as_completed(futs), 1):
                jk = futs[f]
                try:
                    offers, count = f.result()
                except Exception as e:  # один ЖК не должен ронять весь прогон
                    failed.append(jk)
                    print(f"  [{i}/{len(ids)}] ЖК {jk}: FAILED {str(e)[:100]}", file=sys.stderr)
                    if stats:
                        stats.write(json.dumps({"jk_id": jk, "attempt": attempt, "error": str(e)[:200]}) + "\n")
                    continue
                new = [o for o in offers if o["id"] not in seen]
                seen.update(o["id"] for o in new)
                print(f"  [{i}/{len(ids)}] ЖК {jk}: {len(offers)} из {count}", file=sys.stderr)
                if stats:
                    stats.write(json.dumps({"jk_id": jk, "attempt": attempt, "offer_count": count,
                                            "fetched": len(offers), "new_unique": len(new)}) + "\n")
                    stats.flush()
                for o in new:
                    yield o if raw else flat_row(o)
        return failed

    failed = yield from run(jk_ids, 1)
    if failed:
        print(f"  повтор {len(failed)} упавших ЖК через 5 мин: {failed[:10]}", file=sys.stderr)
        time.sleep(300)
        failed = yield from run(failed, 2)
        if failed:
            print(f"  FAILED ЖК после повтора: {failed}", file=sys.stderr)


if __name__ == "__main__":
    main()
