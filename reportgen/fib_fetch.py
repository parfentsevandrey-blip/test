#!/usr/bin/env python3
"""funda in business: сбор ссылок выдачи и разбор карточек объектов.

    python3 -m reportgen.fib_fetch --harvest utrecht den-haag rotterdam --out fib/
    python3 -m reportgen.fib_fetch --detail fib/urls.txt --out fib/

Почему отдельный модуль, а не тот же код, что для funda.nl
    fundainbusiness.nl — другой сайт. Серверный HTML без `__NUXT_DATA__`
    (разбор payload оттуда не работает вовсе), свои категории
    (belegging / winkel / kantoor / bedrijfshal / horeca), свои URL вида
    /<категория>/<город>/object-<id>-<адрес>/ и пагинация /p2/.

    Дома «магазин внизу, квартиры сверху» на funda.nl жилого раздела не
    ищутся в принципе: в словаре фильтра `zoning` там только `residential` и
    `recreational`, смешанного назначения нет. Они живут здесь.

Что берётся с карточки
    ld+json — адрес, цена, категория: это структурные данные, а не догадка.
    Площади, доход, состав юнитов и год лежат ТОЛЬКО в прозе объявления,
    поэтому текст сохраняется целиком и разбирается отдельно. Число, вынутое
    регуляркой из прозы, — чтение прозы, а не поле, и помечать его надо так же.
"""
import argparse, json, os, re, sys, time

BASE = "https://www.fundainbusiness.nl"
CATS = ("belegging", "winkel")     # где живут дома «коммерция внизу, жильё сверху»
OBJ = re.compile(r'href="(/[a-z-]+/[a-z-]+/object-\d+[^"]*)"')

_UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
       "(KHTML, like Gecko) Version/18.0 Safari/605.1.15")


class NotFound(Exception):
    pass


def fetch(url, timeout=30):
    """HTML страницы. Транспорт подбирается по тому, что установлено.

    curl_cffi воспроизводит TLS-рукопожатие браузера и проходит там, где
    обычный requests получает блокировку. Если его нет — работаем штатными
    средствами: на funda in business этого обычно хватает, в отличие от
    funda.nl, где Akamai заворачивает почти всё.
    """
    try:
        import curl_cffi.requests as cr
        r = cr.get(url, impersonate="safari18_0", timeout=timeout)
        if r.status_code == 404:
            raise NotFound(url)
        r.raise_for_status()
        return r.text
    except ImportError:
        pass
    import urllib.error, urllib.request
    req = urllib.request.Request(url, headers={"User-Agent": _UA,
                                               "Accept-Language": "nl,en;q=0.8"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            raise NotFound(url) from None
        raise


def strip(html):
    t = re.sub(r"<script.*?</script>", " ", html, flags=re.S)
    t = re.sub(r"<style.*?</style>", " ", t, flags=re.S)
    t = re.sub(r"<[^>]+>", " ", t)
    for a, b in (("&#178;", "²"), ("&euro;", "€"), ("&amp;", "&"), ("&nbsp;", " "),
                 ("&#39;", "'"), ("&quot;", '"'), ("&#235;", "ë"), ("&#233;", "é"),
                 ("&#232;", "è"), ("&#246;", "ö")):
        t = t.replace(a, b)
    return re.sub(r"\s+", " ", t).strip()


def harvest(cities, cats=CATS, max_pages=8, delay=0.8, log=print):
    """Ссылки на объекты по городам и категориям. Идём до страницы без нового."""
    found, seen = [], set()
    for city in cities:
        for cat in cats:
            for page in range(1, max_pages + 1):
                url = f"{BASE}/{cat}/{city}/" + (f"p{page}/" if page > 1 else "")
                try:
                    html = fetch(url)
                except NotFound:
                    break
                except Exception as e:
                    log(f"  {url} — {type(e).__name__}")
                    break
                links = [BASE + u for u in sorted(set(OBJ.findall(html)))]
                fresh = [u for u in links if u not in seen]
                seen.update(fresh)
                found += fresh
                log(f"  {cat}/{city} стр.{page}: на странице {len(links)}, новых {len(fresh)}")
                if not fresh:
                    break
                time.sleep(delay)
    return found


def detail(url):
    """Поля из ld+json плюс текст объявления целиком."""
    html = fetch(url)
    rec = {"url": url, "id": (re.search(r"object-(\d+)", url) or [None, None])[1]}
    m = re.search(r'<script type="application/ld\+json">(.*?)</script>', html, re.S)
    if m:
        try:
            d = json.loads(m.group(1))
            a = d.get("address") or {}
            rec.update(address=d.get("name"), city=a.get("addressLocality"),
                       street=a.get("streetAddress"),
                       price=(d.get("offers") or {}).get("price"),
                       category=d.get("category"),
                       ld_photos=[p.get("contentUrl") for p in (d.get("photo") or [])])
        except Exception:
            pass
    rec["media_ids"] = list(dict.fromkeys(re.findall(r'data-media-id="(\d+)"', html)))
    text = strip(html)
    i = text.lower().find("omschrijving")
    rec["text"] = text[i:i + 12000] if i > 0 else text[:12000]
    rec["title"] = (re.search(r"<title>(.*?)</title>", html, re.S) or ["", ""])[1].strip()
    return rec


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--harvest", nargs="+", metavar="CITY")
    ap.add_argument("--cats", nargs="+", default=list(CATS))
    ap.add_argument("--detail", metavar="URLS.txt")
    ap.add_argument("--out", default="fib")
    ap.add_argument("--max-pages", type=int, default=8)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    if a.harvest:
        urls = harvest(a.harvest, a.cats, a.max_pages)
        p = os.path.join(a.out, "urls.txt")
        open(p, "w", encoding="utf-8").write("\n".join(urls) + "\n")
        print(f"{p}: {len(urls)} объявлений")
        return 0

    if a.detail:
        urls = [u.strip() for u in open(a.detail, encoding="utf-8") if u.strip()]
        recs, bad = [], []
        for n, u in enumerate(urls, 1):
            try:
                recs.append(detail(u))
            except Exception as e:
                bad.append(f"{u}: {type(e).__name__}")
            if n % 10 == 0:
                print(f"  разобрано {n}/{len(urls)}", flush=True)
            time.sleep(0.5)
        p = os.path.join(a.out, "objects.json")
        json.dump(recs, open(p, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        print(f"{p}: {len(recs)} объектов"
              + (f"; НЕ ВЫШЛО {len(bad)}: {bad[0]}" if bad else ""))
        return 0

    ap.print_help()
    return 1


if __name__ == "__main__":
    sys.exit(main())
