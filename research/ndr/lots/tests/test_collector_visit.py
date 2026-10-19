"""Offline test of the visitor path in collector.py (L48, L49): Referer passed on, a redirect inside cian.ru followed once,
a redirect elsewhere or to a local address and a 403 treated as a block. No network: the session is a fake."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("collector", Path(__file__).resolve().parent.parent / "collector.py")
C = importlib.util.module_from_spec(spec)
spec.loader.exec_module(C)
C.log = lambda *a: None


class R:
    def __init__(self, status, body="<html>_cianConfig</html>", location=None):
        self.status, self.body, self.headers = status, body.encode(), ({"location": location} if location else {})


class S:
    def __init__(self, answers):
        self.answers, self.calls = list(answers), []

    def get(self, url, headers=None):
        self.calls.append((url, dict(headers or {})))
        a = self.answers.pop(0)
        if isinstance(a, Exception):
            raise a
        return a


class NoPace:
    def wait(self):
        return 0


def visit(answers, url="https://www.cian.ru/sale/flat/1/", ref="https://www.cian.ru/cat.php?p=1"):
    s = S(answers)
    C._visit.clear()
    C._visit.update(s=s, pace=NoPace())
    try:
        return C.fetch_visit(url, ref), s
    except C.Blocked as e:
        return ("blocked", str(e)), s


def test_referer_and_ok():
    (html, st), s = visit([R(200)])
    assert st == 200 and html and s.calls[0][1]["Referer"] == "https://www.cian.ru/cat.php?p=1"


def test_redirect_inside_cian_followed_once():
    (html, st), s = visit([R(301, location="/sale/flat/1/?x=1"), R(200)])
    assert st == 200 and s.calls[1][0] == "https://www.cian.ru/sale/flat/1/?x=1" and s.calls[1][1]["Referer"] == "https://www.cian.ru/sale/flat/1/"


def test_redirect_away_is_block():
    (res, why), _ = visit([R(302, location="http://127.0.0.1/blocked")])
    assert res == "blocked" and "127.0.0.1" in why
    (res, why), _ = visit([R(302, location="https://www.cian.ru/captcha/?r=1")])
    assert res == "blocked"


def test_403_and_internal_redirect_error_are_blocks():
    assert visit([R(403)])[0][0] == "blocked"
    assert visit([Exception("curl: (7) Redirect to internal IP 127.0.0.1 rejected (SSRF protection)")])[0][0] == "blocked"


if __name__ == "__main__":
    for n, f in list(globals().items()):
        if n.startswith("test_"):
            f()
            print("ok", n)
