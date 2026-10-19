"""Сборщик каталога новостроек наш.дом.рф (ЕИСЖС).

Что известно (проверено 2026-10-19, см. docs/research-domrf-cian.md):
- Сайт закрыт антиботом Servicepipe: первая страница -- JS-челлендж, после него cookie (spid/spsn/...).
  Без cookie любой путь кроме "/" отдаёт 403 «Доступ заблокирован».
- С датацентровых/иностранных IP челлендж всегда переходит в слайдер-капчу. Капчу этот скрипт НЕ решает:
  запускать с российского домашнего/мобильного IP или через такой прокси (DOMRF_PROXY=http://user:pass@host:port).
- Путь API каталога (/сервисы/api/kn/object, /сервисы/api/object/<id>) взят из открытых источников и вживую
  отсюда не проверен -- поэтому сначала запустите discover: он запишет реальные запросы SPA.

Примеры:
  python collectors/domrf.py discover --out data/domrf-discover         # снять реальные API-запросы
  python collectors/domrf.py catalog --limit 100 --pages 2 --out data/domrf-objects.jsonl
  python collectors/domrf.py object --id 56842 --out data/domrf-object-56842.json
  HEADLESS=0 xvfb-run -a python collectors/domrf.py discover ...       # если headless палится
"""
import argparse, json, os, sys, time
from urllib.parse import quote

from scrapling.fetchers import StealthySession

ROOT = "https://xn--80az8a.xn--d1aqf.xn--p1ai"
CATALOG = ROOT + quote("/сервисы/каталог-новостроек/список-объектов/список") + "?objStatus=0"
OBJECT_PAGE = ROOT + quote("/сервисы/каталог-новостроек/объект/") + "{id}"
API_LIST = quote("/сервисы/api/kn/object") + "?offset={offset}&limit={limit}&sortField=devId.devShortCleanNm&sortType=asc&objStatus=0"
API_OBJECT = quote("/сервисы/api/object/") + "{id}"
BLOCK_MARKERS = ("похожи на автоматические", "Доступ заблокирован")


def blocked(page):
    html = page.content()
    return any(m in html for m in BLOCK_MARKERS)


def pass_challenge(page, wait_ms=20000):
    """Ждём, пока JS-челлендж Servicepipe отработает и вернёт на сайт."""
    page.wait_for_timeout(wait_ms)
    if blocked(page):
        page.screenshot(path="domrf-blocked.png")
        sys.exit("Servicepipe показал капчу/блок (скриншот domrf-blocked.png). "
                 "Нужен российский домашний/мобильный IP: запустите локально или задайте DOMRF_PROXY.")


def api_get(page, path):
    """GET из контекста страницы -- с теми же cookie, TLS и IP, что прошли челлендж."""
    return page.evaluate("""async (p) => {
        const r = await fetch(p, {headers: {'accept': 'application/json'}, credentials: 'include'});
        return {status: r.status, ct: r.headers.get('content-type'), text: await r.text()};
    }""", path)


def session():
    proxy = os.environ.get("DOMRF_PROXY")
    return StealthySession(headless=os.environ.get("HEADLESS", "1") == "1", locale="ru-RU",
                           timezone_id="Europe/Moscow", google_search=False, timeout=90000,
                           proxy=proxy or None)


def cmd_discover(a):
    os.makedirs(a.out, exist_ok=True)
    seen = []

    def act(page):
        def on_resp(r):
            if r.request.resource_type not in ("xhr", "fetch"):
                return
            item = {"status": r.status, "method": r.request.method, "url": r.url, "post": r.request.post_data}
            if "json" in (r.headers.get("content-type") or ""):
                fn = f"{a.out}/resp-{len(seen):03d}.json"
                try:
                    open(fn, "wb").write(r.body())
                    item["file"] = fn
                except Exception as e:
                    item["err"] = str(e)[:80]
            seen.append(item)

        page.on("response", on_resp)
        pass_challenge(page)
        for url in (CATALOG, OBJECT_PAGE.format(id=a.id)):
            page.goto(url, wait_until="domcontentloaded")
            page.wait_for_timeout(15000)
            if blocked(page):
                sys.exit(f"блок на {url}")
            page.screenshot(path=f"{a.out}/{len(seen):03d}.png", full_page=True)

    with session() as s:
        s.fetch(ROOT + "/", page_action=act)
    json.dump(seen, open(f"{a.out}/requests.json", "w"), ensure_ascii=False, indent=1)
    for x in seen:
        print(x["status"], x["method"], x["url"][:160])


def cmd_catalog(a):
    out = open(a.out, "w")
    n = 0

    def act(page):
        nonlocal n
        pass_challenge(page)
        for p in range(a.pages):
            r = api_get(page, API_LIST.format(offset=p * a.limit, limit=a.limit))
            if r["status"] != 200 or "json" not in (r["ct"] or ""):
                sys.exit(f"API ответил {r['status']} {r['ct']}: {r['text'][:200]} -- запустите discover")
            data = json.loads(r["text"])
            items = (data.get("data") or {}).get("list") or []
            for it in items:
                out.write(json.dumps(it, ensure_ascii=False) + "\n")
            n += len(items)
            print(f"  page {p + 1}: {len(items)} (total {(data.get('data') or {}).get('total')})", file=sys.stderr)
            if len(items) < a.limit:
                break
            time.sleep(a.delay)

    with session() as s:
        s.fetch(ROOT + "/", page_action=act)
    print(f"wrote {n} rows -> {a.out}", file=sys.stderr)


def cmd_object(a):
    def act(page):
        pass_challenge(page)
        r = api_get(page, API_OBJECT.format(id=a.id))
        if r["status"] != 200:
            sys.exit(f"API ответил {r['status']}: {r['text'][:200]} -- запустите discover")
        json.dump(json.loads(r["text"]), open(a.out, "w"), ensure_ascii=False, indent=1)

    with session() as s:
        s.fetch(ROOT + "/", page_action=act)
    print(f"wrote {a.out}", file=sys.stderr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["discover", "catalog", "object"])
    ap.add_argument("--id", type=int, default=56842, help="objId объекта (для discover/object)")
    ap.add_argument("--limit", type=int, default=100)
    ap.add_argument("--pages", type=int, default=1)
    ap.add_argument("--delay", type=float, default=2.0)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    {"discover": cmd_discover, "catalog": cmd_catalog, "object": cmd_object}[a.cmd](a)


if __name__ == "__main__":
    main()
