"""Офлайн-тесты устойчивости сборщиков: подменённые ответы, без сети. usage: python tests/test_resilience.py"""
import json, sys

sys.path.insert(0, __file__.rsplit("/tests/", 1)[0] + "/collectors")
import cian, domrf  # noqa: E402


class Resp:
    def __init__(self, code, body):
        self.status_code = code
        self.text = body if isinstance(body, str) else json.dumps(body)

    def json(self):
        return json.loads(self.text)

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")


class Script:
    """Отдаёт ответы по очереди; Exception в списке — бросается."""
    def __init__(self, items):
        self.items, self.calls = list(items), 0

    def request(self, *a, **kw):
        self.calls += 1
        x = self.items.pop(0)
        if isinstance(x, Exception):
            raise x
        return x


SLEPT = []
cian.time.sleep = domrf.time.sleep = lambda s: SLEPT.append(s)
results = []


def case(name, fn):
    SLEPT.clear()
    try:
        out = fn()
        results.append((name, "ok", out, round(sum(SLEPT))))
    except Exception as e:
        results.append((name, "raised", f"{e.__class__.__name__}: {str(e)[:90]}", round(sum(SLEPT))))


def cian_with(items):
    c = cian.Cian(delay=(0, 0))
    c.s = Script(items)
    return c


def domrf_with(items):
    d = domrf.DomRF(delay=(0, 0))
    d.s = Script(items)
    d._new_session = lambda: None  # не терять подменённую сессию
    return d


OK_PAGE = Resp(200, {"data": {"offerCount": 1, "offersSerialized": [{"id": 1}]}})
case("cian: 403 -> 200", lambda: cian_with([Resp(403, "cian_waf_block"), OK_PAGE])._req("POST", "u").status_code)
case("cian: 429,503,502 -> 200", lambda: cian_with([Resp(429, ""), Resp(503, ""), Resp(502, ""), OK_PAGE])._req("POST", "u").status_code)
case("cian: 5x403 -> give up", lambda: cian_with([Resp(403, "")] * 5)._req("POST", "u"))
case("cian: обрыв соединения", lambda: cian_with([ConnectionError("reset"), OK_PAGE])._req("POST", "u").status_code)
case("cian: 404", lambda: cian_with([Resp(404, "")])._req("GET", "u"))
case("cian: 200, но HTML вместо JSON", lambda: cian_with([Resp(200, "<html>captcha</html>")]).search({}, 1))

LIST = Resp(200, {"data": {"list": [{"objId": 1}], "total": 1}})
case("domrf: исключение -> 200", lambda: domrf_with([ConnectionError("reset"), LIST])._req("GET", "u")["data"]["total"])
case("domrf: 403 Servicepipe -> 200", lambda: domrf_with([Resp(403, "Доступ заблокирован"), LIST])._req("GET", "u")["data"]["total"])
case("domrf: 200 с челленджем -> 200", lambda: domrf_with([Resp(200, "<script src=servicepipe.tech>"), LIST])._req("GET", "u")["data"]["total"])
case("domrf: 5x500 -> give up", lambda: domrf_with([Resp(500, {"status": "INTERNAL_SERVER_ERROR"})] * 5)._req("GET", "u"))
case("domrf: 404", lambda: domrf_with([Resp(404, "")])._req("GET", "u"))
case("domrf: units total=0", lambda: list(domrf_with([Resp(200, {"data": [], "total": 0})]).units(1)))
case("domrf: flats [] -> 0", lambda: list(domrf_with([Resp(201, [])]).flats(1)))

# search_all: срез уже нельзя делить (<10k ₽), а объявлений больше лимита -> что вернёт?
big = {"data": {"offerCount": 5000, "offersSerialized": [{"id": i} for i in range(28)]}}
c = cian.Cian(delay=(0, 0))
c.s = type("S", (), {"request": lambda self, *a, **k: Resp(200, big)})()
case("cian: search_all неделимый срез 5000", lambda: sum(1 for _ in c.search_all({}, 1_000_000, 1_005_000)))

w = max(len(r[0]) for r in results)
for name, status, out, slept in results:
    print(f"{name:<{w}}  {status:<6}  sleep={slept:>4}s  {out}")
