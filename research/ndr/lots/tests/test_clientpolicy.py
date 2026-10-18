"""Offline tests of lib/clientpolicy.py (L28, L29, L32, L37). No request leaves the machine: the browser test serves a local
page that tries to call api.cian.ru and www.cian.ru; shield() must abort both before they reach the network."""
import http.server
import importlib.util
import json
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

LOTS = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("clientpolicy", LOTS / "lib" / "clientpolicy.py")
cp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cp)


def refused(url, client, cooldown=False):
    try:
        cp.check(url, client, cooldown=cooldown)
        return False
    except cp.Refused:
        return True


def test_matrix():
    assert refused("https://www.cian.ru/sale/flat/1/", "headless")
    assert refused("https://zhk-simvol-i.cian.ru/", "headless")
    assert refused("https://www.cian.ru/cat.php?p=1", "stealthy")
    assert not refused("https://www.cian.ru/cat.php?p=1", "fetcher")
    assert refused("https://api.cian.ru/search-offers/v2/", "fetcher")          # never, any client
    assert refused("https://наш.дом.рф/сервисы/каталог-новостроек/объект/1", "headless")
    assert refused("https://xn--80az8a.xn--d1aqf.xn--p1ai/x", "fetcher")
    assert not refused("https://xn--80az8a.xn--d1aqf.xn--p1ai/x", "ndr_workflow")
    assert refused("https://www.lsr.ru/msk/kvartiry-v-novostroikah/zilart/", "headless")
    assert not refused("https://www.lsr.ru/msk/kvartiry-v-novostroikah/zilart/", "stealthy")
    assert not refused("https://granelle.ru/objects/x/", "headless")
    assert not refused("https://dreamriva.ru/flats/", "fetcher")
    assert refused("https://www.pik.ru/projects", "fetcher")                    # impersonated Fetcher: 401 (L41)
    assert not refused("https://www.pik.ru/projects", "fetcher_plain")         # honest client (L46)
    assert refused("https://www.cian.ru/cat.php?p=1", "fetcher_plain")          # ЦИАН stays Fetcher-only


def test_cooldown_is_honoured():
    cd = json.loads((LOTS / "cooldown.json").read_text()) if (LOTS / "cooldown.json").exists() else {}
    m = importlib.util.module_from_spec(importlib.util.spec_from_file_location("cooldown", LOTS / "lib" / "cooldown.py"))
    m.__spec__.loader.exec_module(m)
    for site in cd:
        url = f"https://{site}/"
        client = sorted(cp.PROTECTED.get(site, {"fetcher"}))[0]
        assert refused(url, client, cooldown=True) == bool(m.active(site)), site


class _H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"""<html><body>ok<img src="/own.png">
        <script>fetch('https://api.cian.ru/newbuilding-widget/v1/x').catch(()=>{});
                fetch('https://www.cian.ru/zhk/').catch(()=>{});</script></body></html>""")

    def log_message(self, *a):
        pass


def test_shield_in_browser():
    from patchright.sync_api import sync_playwright
    srv = http.server.HTTPServer(("127.0.0.1", 0), _H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    aborted, sent = [], []
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        ctx = b.new_context()
        cp.shield(ctx, "headless", lambda *a: aborted.append(a[-1]))
        pg = ctx.new_page()
        pg.on("request", lambda r: sent.append(r.url))
        pg.goto(f"http://127.0.0.1:{srv.server_port}/")
        pg.wait_for_timeout(1500)
        b.close()
    srv.shutdown()
    assert any("api.cian.ru" in u for u in aborted) and any("www.cian.ru" in u for u in aborted), aborted
    assert any("127.0.0.1" in u for u in sent)                                  # own requests still go


def test_audit_refuses_before_browser():
    out = Path(tempfile.mkdtemp())
    r = subprocess.run([sys.executable, "-I", str(LOTS / "lib" / "audit_site.py"), "t", "https://www.cian.ru/zhk/", str(out)],
                       capture_output=True, text=True, timeout=120)
    assert r.returncode != 0 and "Refused" in r.stderr, r.stderr[-400:]
    assert not list(out.glob("*.har"))                                          # no browser was started



def test_shield_with_visitor_lean():
    from patchright.sync_api import sync_playwright
    vs = importlib.util.spec_from_file_location("visitor", LOTS / "lib" / "visitor.py")
    V = importlib.util.module_from_spec(vs)
    vs.loader.exec_module(V)
    srv = http.server.HTTPServer(("127.0.0.1", 0), _H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    aborted, ok = [], []
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        pg = b.new_page()
        V.lean(pg, f"127.0.0.1:{srv.server_port}")           # the visitor's own route first, as in visit()
        cp.shield(pg, "headless", lambda *a: aborted.append(a[-1]))
        pg.on("requestfinished", lambda r: ok.append(r.url))
        pg.goto(f"http://127.0.0.1:{srv.server_port}/")
        pg.wait_for_timeout(1500)
        b.close()
    srv.shutdown()
    assert any("api.cian.ru" in u for u in aborted), aborted
    assert any(u.startswith("http://127.0.0.1") for u in ok), ok


if __name__ == "__main__":
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f()
            print("ok", name)
