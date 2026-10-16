"""
Continuous, low-impact collector for наш.дом.рф (ЕИСЖС) project pages, built on Scrapling.

Design rules (each one fixes a mistake from earlier attempts):
  1. Consistent fingerprint: real Google Chrome, headful under Xvfb, ru_RU system locale,
     Europe/Moscow, screen == window, DPR 1, WebGL on. Verified locally (preflight) BEFORE
     any request to the site; the run aborts if anything is inconsistent.
  2. Behave like one visitor: start at the home page, reach pages by clicking links,
     put a whole query into the search box at once (fill), never call the site's API directly.
  3. Request budget: every XHR the page fires is counted; actions wait until the rolling
     per-minute budget allows them. Minimum gap between page-level actions.
  4. Any 403/429 or captcha => stop immediately, never touch the captcha, write a cooldown
     (exponential backoff) to state, rotate the browser profile. The next run respects it.
  5. Resumable: discovered object links and collected pages live in state.json;
     each run does at most MAX_ACTIONS page actions, so repeated runs finish the job gradually.

Usage (run under xvfb-run with ru_RU env, see run.sh):
  python -I ndr_workflow.py preflight   # local fingerprint check only, no site traffic
  python -I ndr_workflow.py run         # one paced pass (respects cooldown)
  python -I ndr_workflow.py loop        # repeat passes until done, sleeping through cooldowns
  python -I ndr_workflow.py report      # print collected results
"""
import json
import random
import re
import sys
import time
from collections import deque
from datetime import datetime, timedelta, timezone
from pathlib import Path

from scrapling.fetchers import StealthySession

HERE = Path(__file__).resolve().parent
STATE_FILE = HERE / "state.json"
LOG_FILE = HERE / "workflow.log"
EXPERIENCE_FILE = HERE / "experience.jsonl"   # machine-written; curated lessons live in EXPERIENCE.md
BASE = "https://xn--80az8a.xn--d1aqf.xn--p1ai"

# Search query -> keywords that must appear in a result card (href or text) to count as the project.
TARGETS = {
    "Ридж": ["ридж"],
    "Сезар": ["будущ", "силик", "сезар", "sezar"],
    "Стоун": ["блик", "новоданилов"],
    "Парквью": ["парквью", "тимирязевская"],
    "Серёгина": ["серёгин", "серегин", "с5"],
    "Сикрет": ["сикрет", "профсоюзн"],
}

# Object page path on the site; IDs can be seeded from open sources so the site's search
# (its heavy /api/kn/object/map endpoint, see EXPERIENCE.md F19) is never used.
OBJECT_PATH = "/сервисы/каталог-новостроек/объект/{id}"
USE_SITE_SEARCH = False

PASS_GAP_MIN = (10, 15)         # minutes between passes: ~30 pages/hour cap (see EXPERIENCE.md F27)
MAX_ACTIONS = 10                # page-level actions per run
ACTION_GAP = (30, 50)           # seconds between page-level actions
XHR_PER_MIN = 15                # rolling budget of XHR/fetch requests to the site
BACKOFF_START = timedelta(minutes=45)
BACKOFF_MAX = timedelta(hours=12)
MAX_OBJECTS_PER_TARGET = 3

BLOCK_MARKERS = ("не робот", "похожи на автоматические", "доступ заблокирован", "forbidden")

# Flags for a NEW session only. An existing session keeps the exact flags it earned its clearance with
# (state["session"]), because the anti-bot clearance cookie (spjs) is bound to that fingerprint. EXPERIENCE.md W12.
# WebGL via ANGLE->OpenGL (Mesa llvmpipe): passes sannysoft, unlike SwiftShader (trial 2026-10-15, W9)
FLAGS = ["--lang=ru-RU", "--use-gl=angle", "--use-angle=gl", "--ignore-gpu-blocklist", "--window-size=1920,1080"]
CLEARANCE_COOKIES = {"spid", "spjs", "spsc"}   # issued by the site's anti-bot after a passed JS check
SITE_HOST = "xn--80az8a.xn--d1aqf.xn--p1ai"
CONTEXT = {"viewport": {"width": 1920, "height": 947}, "screen": {"width": 1920, "height": 1080},
           "device_scale_factor": 1, "color_scheme": "light"}


class Blocked(Exception):
    pass


def now():
    return datetime.now(timezone.utc)


def log(*parts):
    line = f"{now().isoformat(timespec='seconds')} " + " ".join(str(p) for p in parts)
    print(line, flush=True)
    with LOG_FILE.open("a") as f:
        f.write(line + "\n")


def record_experience(kind, **data):
    """Append one observation (success/failure) so future runs and humans can learn from it."""
    entry = {"ts": now().isoformat(timespec="seconds"), "kind": kind,
             "config": {"action_gap": ACTION_GAP,
                        "xhr_per_min": XHR_PER_MIN, "max_actions": MAX_ACTIONS}, **data}
    with EXPERIENCE_FILE.open("a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def load_state():
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text())
    return {"profile_gen": 1, "cooldown_until": None, "backoff_min": None, "session": None,
            "searches": {}, "objects": {}, "runs": []}


def save_state(st):
    tmp = STATE_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(st, ensure_ascii=False, indent=1))
    tmp.replace(STATE_FILE)


def current_session(st):
    """A session = browser profile + the exact config it was created with. Never mixed."""
    if not st.get("session"):
        st["session"] = {"profile": str(HERE / f"profile{st['profile_gen']}"), "flags": FLAGS,
                         "context": CONTEXT, "created": now().isoformat(timespec="seconds"), "cleared": False}
    return st["session"]


def session_kwargs(st):
    ses = current_session(st)
    return dict(headless=False, real_chrome=True, locale="ru-RU", timezone_id="Europe/Moscow",
                google_search=False, user_data_dir=ses["profile"],
                timeout=90000, retries=3, retry_delay=10, extra_flags=ses["flags"], additional_args=ses["context"])


def clearance_cookies(profile_dir):
    """Names of the site's anti-bot clearance cookies present in a profile (read locally, no traffic)."""
    import sqlite3
    db = Path(profile_dir) / "Default" / "Cookies"
    if not db.exists():
        return set()
    try:
        con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
        names = {n for (n,) in con.execute("select name from cookies where host_key like ?", (f"%{SITE_HOST}%",))}
        con.close()
        return names
    except sqlite3.Error:
        return set()


# ---------------------------------------------------------------- preflight (no site traffic)
FP_JS = """async()=>{const g=document.createElement('canvas').getContext('webgl');let gl=null;
if(g){const e=g.getExtension('WEBGL_debug_renderer_info');if(e)gl=g.getParameter(e.UNMASKED_RENDERER_WEBGL)}
let ch=null;try{ch=await navigator.userAgentData.getHighEntropyValues(['fullVersionList'])}catch(e){}
return {ua:navigator.userAgent,lang:navigator.language,intl:Intl.DateTimeFormat().resolvedOptions().locale,
tz:Intl.DateTimeFormat().resolvedOptions().timeZone,webdriver:navigator.webdriver,gl:gl,ch:ch,
sw:screen.width,sh:screen.height,ow:outerWidth,oh:outerHeight,dpr:devicePixelRatio}}"""


def preflight(st):
    fp = {}
    with StealthySession(**session_kwargs(st)) as s:
        s.fetch("https://example.com/",
                page_action=lambda p: fp.update(p.evaluate(FP_JS, isolated_context=False)))
    problems = []
    ua_ver = re.search(r"Chrome/(\d+)", fp.get("ua", ""))
    ch_ver = next((b["version"].split(".")[0] for b in (fp.get("ch") or {}).get("fullVersionList", [])
                   if b["brand"] == "Google Chrome"), None)
    if not ua_ver or ua_ver.group(1) != ch_ver:
        problems.append(f"UA version {ua_ver and ua_ver.group(1)} != Client Hints {ch_ver}")
    if not fp.get("lang", "").startswith("ru") or not fp.get("intl", "").startswith("ru"):
        problems.append(f"locale mismatch lang={fp.get('lang')} intl={fp.get('intl')}")
    if fp.get("tz") != "Europe/Moscow":
        problems.append(f"timezone {fp.get('tz')}")
    if fp.get("webdriver"):
        problems.append("navigator.webdriver is true")
    if not fp.get("gl"):
        problems.append("WebGL unavailable")
    if fp.get("ow", 0) > fp.get("sw", 0) + 16 or fp.get("oh", 0) > fp.get("sh", 0):
        problems.append(f"window {fp.get('ow')}x{fp.get('oh')} larger than screen {fp.get('sw')}x{fp.get('sh')}")
    if fp.get("dpr") != 1:
        problems.append(f"devicePixelRatio {fp.get('dpr')}")
    log("preflight", json.dumps({k: fp.get(k) for k in ("ua", "lang", "intl", "tz", "gl", "dpr")}, ensure_ascii=False))
    return problems


# ---------------------------------------------------------------- pacing / block detection
class Pacer:
    def __init__(self):
        self.xhr = deque()
        self.actions = 0
        self.blocked_status = None        # hard: 403/429 on a document, or captcha text
        self.soft_403 = []                # soft: 403 on a secondary XHR while the page itself rendered
        self.last_action = "start"
        self.peak_xhr = 0

    def on_request(self, req):
        if req.resource_type in ("xhr", "fetch") and BASE in req.url:
            self.xhr.append(time.monotonic())

    def on_response(self, resp):
        if BASE in resp.url and resp.status in (403, 429):
            if resp.request.resource_type == "document" or resp.status == 429:
                self.blocked_status = f"{resp.status} {resp.url[:120]}"
            else:
                self.soft_403.append(f"{resp.status} {resp.url[:120]}")

    def xhr_last_minute(self):
        cut = time.monotonic() - 60
        while self.xhr and self.xhr[0] < cut:
            self.xhr.popleft()
        self.peak_xhr = max(self.peak_xhr, len(self.xhr))
        return len(self.xhr)

    def before_action(self, page, first=False, name="action"):
        self.last_action = name
        if self.actions >= MAX_ACTIONS:
            raise StopIteration
        if not first:
            page.wait_for_timeout(int(random.uniform(*ACTION_GAP) * 1000))
        while self.xhr_last_minute() > XHR_PER_MIN // 2:
            page.wait_for_timeout(5000)
        self.actions += 1

    def check(self, page):
        text = page.inner_text("body")
        self.last_page = {"url": page.url[:200], "text": text[:1500]}
        norm = " ".join(text.replace("\xa0", " ").lower().split())   # the site uses non-breaking spaces
        if self.blocked_status or any(m in norm[:2000] for m in BLOCK_MARKERS):
            raise Blocked(self.blocked_status or page.url[:120])
        return text


CARD_FIELDS = ("id", "name", "complex", "address", "coordinates", "buildingClass", "flatsCount", "nonLivingCount",
               "parkingCount", "floorMax", "averagePrice", "objPriceMonthDt", "soldOutPercent", "publicationDate",
               "dateEnd", "dateEndQuarterView", "status", "metro", "addrAreaDesc")


def next_card(page):
    """Project card + 12-month sales series from the page's own embedded __NEXT_DATA__ (no extra requests)."""
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', page.content(), re.S)
    if not m:
        return None
    try:
        card = json.loads(m.group(1))["props"]["initialState"]["kn"]["objectCard"]
    except (KeyError, ValueError):
        return None
    info = (card.get("mainInfo") or {}).get("data") or {}
    out = {k: info.get(k) for k in CARD_FIELDS}
    dev = info.get("developer") or {}
    out["developer"] = dev.get("shortClearNm") or dev.get("name")
    out["monthly"] = ((card.get("salesDynamic") or {}).get("monthlySalesByType")) or {}
    return out


def idle_like_human(page, lo=6000, hi=9000):
    page.wait_for_timeout(random.randint(lo, hi))
    for _ in range(3):
        page.mouse.move(random.randint(200, 1500), random.randint(200, 800), steps=random.randint(15, 35))
        page.wait_for_timeout(random.randint(600, 1400))


def object_links(page):
    links = page.eval_on_selector_all(
        "a[href]", "els=>els.map(e=>[decodeURI(e.getAttribute('href')),e.innerText.replace(/\\s+/g,' ').trim().slice(0,300)])")
    return [(h, t) for h, t in links if "/объект/" in h]


def open_catalog(page, pacer):
    link = page.get_by_role("link", name="Каталог новостроек").first
    link.hover(timeout=20000)
    page.wait_for_timeout(random.randint(600, 1200))
    link.click()
    page.wait_for_load_state("networkidle")
    idle_like_human(page)
    pacer.check(page)


# ---------------------------------------------------------------- one paced pass
def run_once(st):
    if st["cooldown_until"] and now() < datetime.fromisoformat(st["cooldown_until"]):
        log("cooldown active until", st["cooldown_until"], "- skipping run")
        return "cooldown"
    st["cooldown_until"] = None                     # an expired cooldown must not shorten the next pass gap (F28)
    problems = preflight(st)
    if problems:
        log("PREFLIGHT FAILED, not contacting the site:", problems)
        record_experience("failure", result="preflight_failed", problems=problems)
        return "preflight_failed"

    pacer = Pacer()
    outcome = {"started": now().isoformat(timespec="seconds")}

    def setup(page):
        page.on("request", pacer.on_request)
        page.on("response", pacer.on_response)

    def action(page):
        idle_like_human(page, 8000, 12000)           # let the site's own JS check finish
        pacer.check(page)
        on_catalog = False
        if USE_SITE_SEARCH:
            pacer.before_action(page, first=True, name="open_catalog")
            open_catalog(page, pacer)
            on_catalog = True

        # 1) discovery: one whole-query search per target, only if site search is enabled
        box = page.get_by_placeholder("Адрес, Название ЖК").first if USE_SITE_SEARCH else None
        for q, keys in (TARGETS.items() if USE_SITE_SEARCH else []):
            if q in st["searches"]:
                continue
            pacer.before_action(page, name=f"search:{q}")
            box.click()
            box.fill("")
            page.wait_for_timeout(random.randint(400, 800))
            box.fill(q)                                # one input event => one request batch
            page.wait_for_timeout(random.randint(6000, 8000))
            pacer.check(page)
            found = {}
            for href, txt in object_links(page):
                if any(k in (href + " " + txt).lower() for k in keys) and len(found) < MAX_OBJECTS_PER_TARGET:
                    found.setdefault(href, txt)
            st["searches"][q] = [{"href": h, "card": t} for h, t in found.items()]
            save_state(st)
            log("search", q, "->", len(found), "objects")

        # 2) object pages: open each pending one by clicking its link (or as a bookmark if not on screen)
        for q, items in st["searches"].items():
            for it in items:
                if it["href"] in st["objects"]:
                    continue
                pacer.before_action(page, name=f"object:{it['href']}")
                captured = []
                handler = lambda r: captured.append(r) if "/api/" in r.url and r.request.resource_type in ("xhr", "fetch") else None
                page.on("response", handler)
                link = page.locator(f'a[href="{it["href"]}"]').first if on_catalog else None
                if link is not None and link.count():
                    link.scroll_into_view_if_needed()
                    link.click()
                else:
                    for attempt in (1, 2):              # one retry for transient tunnel resets (net::ERR_*), never for 403s
                        try:
                            page.goto(BASE + it["href"])
                            break
                        except Exception as e:
                            if attempt == 2 or "net::ERR_" not in str(e):
                                raise
                            log("network error, retrying once:", str(e)[:80])
                            page.wait_for_timeout(random.randint(20000, 30000))
                page.wait_for_load_state("networkidle")
                idle_like_human(page)
                text = pacer.check(page)
                bodies = []
                for r in captured:
                    try:
                        if r.status == 200 and "json" in (r.headers.get("content-type") or ""):
                            bodies.append({"url": r.url, "json": r.json()})
                    except Exception:
                        pass
                page.remove_listener("response", handler)
                m = re.search(r"[Рр]аспроданн\w*[^\d]{0,40}(\d+[.,]?\d*)\s*%", text)
                st["objects"][it["href"]] = {"query": q, "card": it["card"], "url": page.url, "next": next_card(page),
                                             "sold_pct_text": m.group(1) if m else None,
                                             "text": text[:12000], "api": bodies,
                                             "collected": now().isoformat(timespec="seconds")}
                save_state(st)
                log("object", q, it["href"], "sold%", m.group(1) if m else "?")
                if pacer.soft_403:
                    st["objects"][it["href"]]["soft_403"] = list(pacer.soft_403)
                    save_state(st)
                    record_experience("soft_403", result="page rendered, secondary XHR 403", object=it["href"],
                                      soft=pacer.soft_403, has_next=bool(st["objects"][it["href"]]["next"]))
                    pacer.soft_count = getattr(pacer, "soft_count", 0) + 1
                    log("soft 403 on secondary request(s):", pacer.soft_403, "- page saved; extra pause")
                    pacer.soft_403 = []
                    if pacer.soft_count >= 2:
                        raise Blocked("two soft 403 signals in one pass")
                    page.wait_for_timeout(random.randint(120000, 180000))
                on_catalog = False

    # Scrapling logs and swallows exceptions raised inside page_action, so we carry them out ourselves.
    raised = []

    def guarded_action(page):
        try:
            action(page)
        except BaseException as e:
            raised.append(e)

    try:
        with StealthySession(**session_kwargs(st)) as s:
            s.fetch(BASE + "/", network_idle=True, page_setup=setup, page_action=guarded_action)
        if raised:
            raise raised[0]
        if pacer.blocked_status:
            raise Blocked(pacer.blocked_status)
        result = "ok"
        current_session(st)["cleared"] = CLEARANCE_COOKIES <= clearance_cookies(current_session(st)["profile"])
        st["backoff_min"] = None
        st["cooldown_until"] = None
    except Blocked as b:
        result = "blocked"
        backoff = timedelta(minutes=st["backoff_min"]) * 2 if st["backoff_min"] else BACKOFF_START
        backoff = min(backoff, BACKOFF_MAX)
        st["backoff_min"] = backoff.total_seconds() / 60
        st["cooldown_until"] = (now() + backoff).isoformat(timespec="seconds")
        ses = current_session(st)
        names = clearance_cookies(ses["profile"])
        if CLEARANCE_COOKIES <= names:
            # A cleared session is the most valuable asset: keep it and its exact config, just back off.
            log("session keeps clearance cookies", sorted(names & CLEARANCE_COOKIES), "- keeping profile and config")
        else:
            # Never cleared (e.g. only 'rndcaptcha'): this profile is useless; next run starts a new session
            # with a single calm warm-up visit.
            log("session has no clearance", sorted(names), "- replacing it")
            st["profile_gen"] += 1
            st["session"] = None
        log("BLOCKED:", b, "- stopping; cooldown", backoff, "until", st["cooldown_until"])
        record_experience("block_context", result=str(b)[:200], page=getattr(pacer, "last_page", None))
    except StopIteration:
        result = "budget_reached"
    except Exception as e:                            # navigation timeouts etc: no hammering, just end the pass
        result = f"error: {type(e).__name__}: {str(e)[:200]}"
        log("pass error", result)
    outcome.update(result=result, actions=pacer.actions, xhr_last_min=pacer.xhr_last_minute())
    record_experience("success" if result in ("ok", "budget_reached") else "failure", result=result,
                      last_action=pacer.last_action, actions=pacer.actions, peak_xhr_per_min=pacer.peak_xhr,
                      session=st.get("session") or "replaced")
    st["runs"].append(outcome)
    save_state(st)
    log("pass finished", outcome)
    return result


def seed(query, ids, note=""):
    """Register known object IDs (from open sources) for a target, bypassing the site's search."""
    st = load_state()
    items = st["searches"].setdefault(query, [])
    have = {it["href"] for it in items}
    for i in ids:
        href = OBJECT_PATH.format(id=int(i))
        if href not in have:
            items.append({"href": href, "card": f"seed id={i} {note}".strip()})
    save_state(st)
    log("seeded", query, ids)


def done(st):
    if USE_SITE_SEARCH and set(st["searches"]) != set(TARGETS):
        return False
    if not st["searches"]:
        return False
    return all(it["href"] in st["objects"] for items in st["searches"].values() for it in items)


def last_page_at():
    """Time of the last page fetched, from the log (a pass killed by a container restart never reaches st['runs'])."""
    try:
        for line in reversed(LOG_FILE.read_text().splitlines()[-500:]):
            if " object " in line:
                return datetime.fromisoformat(line.split(" ", 1)[0])
    except Exception:
        pass
    return None


def loop():
    last = last_page_at()                            # restart must not shorten the pass gap (F29)
    if last and (now() - last).total_seconds() < PASS_GAP_MIN[0] * 60:
        wait = PASS_GAP_MIN[0] * 60 - (now() - last).total_seconds()
        log(f"resumed after restart, first pass in {wait/60:.0f} min")
        time.sleep(wait)
    while True:
        st = load_state()
        if done(st):
            log("all targets collected")
            return
        result = run_once(st)
        st = load_state()
        hook = HERE / "post_pass_hook.py"           # save progress after every pass (stability first)
        if hook.exists():
            try:
                import subprocess
                subprocess.run([sys.executable, "-I", str(hook)], timeout=900)
            except Exception as e:
                log("post-pass hook failed:", str(e)[:200])
        if result == "preflight_failed":
            return
        if st["cooldown_until"] and datetime.fromisoformat(st["cooldown_until"]) > now():
            wait = (datetime.fromisoformat(st["cooldown_until"]) - now()).total_seconds()
        else:
            wait = random.uniform(*PASS_GAP_MIN) * 60   # pause between passes even when all is well
        log(f"next pass in {wait/60:.0f} min")
        time.sleep(max(wait, 60))


def sales_agg(o):
    """The site's own per-building counters (units, not area): total / realised / perc per category."""
    for b in o.get("api", []):
        if b["url"].endswith("/sales_agg"):
            return (b.get("json") or {}).get("data") or {}
    return {}


def report():
    st = load_state()
    for href, o in st["objects"].items():
        agg = sales_agg(o)
        flats = agg.get("apartmentsAggData") or {}
        status = "sales not started" if agg.get("saleNotStartedFlg") else \
            f"flats sold {flats.get('realised')}/{flats.get('total')} ({flats.get('perc')}%)"
        print(f"{o['query']:<10} {href.split('/')[-1]:<6} {status:<32} header%={o['sold_pct_text']!s:<5} "
              f"collected={o['collected']}  {BASE}{href}")
    print("searches:", {q: len(v) for q, v in st["searches"].items()}, "| cooldown:", st["cooldown_until"])


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "run"
    if cmd == "preflight":
        p = preflight(load_state())
        print("OK" if not p else p)
    elif cmd == "run":
        run_once(load_state())
    elif cmd == "loop":
        loop()
    elif cmd == "report":
        report()
    elif cmd == "seed":                      # python ndr_workflow.py seed <query> <id> [<id> ...]
        seed(sys.argv[2], sys.argv[3:])
