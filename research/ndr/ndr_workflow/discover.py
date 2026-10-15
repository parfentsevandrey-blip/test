"""One paced visit: open an object page, scroll through all sections, save every JSON the page loads."""
import json, math, random, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import ndr_workflow as wf
from scrapling.fetchers import StealthySession
oid = int(sys.argv[1])
st = wf.load_state()
got, raised = [], []
def setup(page):
    page.on("response", lambda r: got.append(r) if "/api/" in r.url and r.request.resource_type in ("xhr", "fetch") else None)
def act(page):
    try:
        wf.idle_like_human(page, 8000, 11000)
        page.wait_for_timeout(random.randint(20000, 30000))
        page.goto(wf.BASE + wf.OBJECT_PATH.format(id=oid)); page.wait_for_load_state("networkidle")
        wf.idle_like_human(page)
        for _ in range(14):
            page.mouse.wheel(0, random.randint(500, 800)); page.wait_for_timeout(random.randint(1200, 2200))
        tab = page.get_by_text("Динамика продаж").first
        if tab.count(): tab.scroll_into_view_if_needed(); page.wait_for_timeout(1500); tab.click(); page.wait_for_timeout(5000)
        t = page.inner_text("body"); i = t.find("Динамика продаж")
        Path(f"discover_{oid}.txt").write_text(t)
        Path(f"discover_{oid}.html").write_text(page.content())
        keys = page.evaluate("Object.keys(window).filter(k=>/^__|NUXT|NEXT|STATE|APOLLO|INITIAL/i.test(k))", isolated_context=False)
        print("WINDOW KEYS:", keys)
        svg = page.evaluate("""()=>{const s=[...document.querySelectorAll('svg')].map(v=>v.outerHTML).filter(h=>/path|rect/.test(h)&&h.length>2000);return s.map(h=>h.slice(0,3000))}""", isolated_context=False)
        Path(f"discover_{oid}_svg.json").write_text(json.dumps(svg, ensure_ascii=False))
        print("SVG charts:", len(svg))
        print("DYN TEXT:", t[i:i+900].replace("\n", " | ") if i >= 0 else "not found")
    except BaseException as e:
        raised.append(e)
with StealthySession(**wf.session_kwargs(st)) as s:
    s.fetch(wf.BASE + "/", network_idle=True, page_setup=setup, page_action=act)
out = []
for r in got:
    try: out.append({"url": r.url, "status": r.status, "json": r.json()})
    except Exception: out.append({"url": r.url, "status": r.status})
Path(f"discover_{oid}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
for o in out: print(o["status"], o["url"][-110:])
if raised: print("ERROR", raised[0])
