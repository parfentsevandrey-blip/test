"""
Trial-and-error harness: iterate on browser configurations WITHOUT waiting and WITHOUT
spending reputation on the target site.

  1. Each candidate config is scored on public bot-detector pages (no penalties there).
  2. Results go to experience.jsonl (kind="trial") and trials.json, ranked.
  3. Only a candidate that beats the baseline on the detectors may be probed on the target
     site, with ONE home-page load, at most once per PROBE_MIN_GAP (python trial.py probe <name>).

Usage (under run.sh-style env):  python -I trial.py detect [name ...] | probe <name> | rank
"""
import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from scrapling.fetchers import StealthySession

sys.path.insert(0, str(Path(__file__).resolve().parent))   # our own folder (python -I drops it)
import ndr_workflow as wf  # noqa: E402

HERE = Path(__file__).resolve().parent
TRIALS = HERE / "trials.json"
PROBE_MIN_GAP = 600  # seconds between target-site probes

# Each candidate = overrides applied on top of the workflow's session_kwargs().
SWIFT = ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"]
BASE_FLAGS = ["--lang=ru-RU", "--window-size=1920,1080"]
CANDIDATES = {
    "baseline_w1": {},
    "gl_angle_gl_llvmpipe": {"extra_flags": BASE_FLAGS + ["--use-gl=angle", "--use-angle=gl", "--ignore-gpu-blocklist"]},
    "gl_desktop_egl": {"extra_flags": BASE_FLAGS + ["--use-gl=egl", "--ignore-gpu-blocklist"]},
    "no_hide_canvas_no_dark": {"hide_canvas": False},
    "chromium_bundled": {"real_chrome": False},
    "headless_new_real_chrome": {"headless": True},
}

DETECTORS = {
    "sannysoft": "https://bot.sannysoft.com/",
    "creepjs": "https://abrahamjuliot.github.io/creepjs/",
    "browserscan": "https://www.browserscan.net/bot-detection",
}


def kwargs_for(name, st):
    kw = wf.session_kwargs(st)
    kw["user_data_dir"] = str(HERE / f"trial_profile_{name}")
    kw.update(CANDIDATES[name])
    return kw


def parse(det, page):
    text = " ".join(page.inner_text("body").replace("\xa0", " ").split())
    if det == "sannysoft":
        failed = page.eval_on_selector_all("td.failed, td.warn", "els=>els.map(e=>(e.parentElement.innerText||'').replace(/\\s+/g,' ').slice(0,80))")
        return {"failed": failed}
    if det == "creepjs":
        out = {}
        for key in ("headless", "stealth", "like headless"):
            m = re.search(r"(\d+)%\s*" + key, text)
            out[key.replace(" ", "_")] = int(m.group(1)) if m else None
        m = re.search(r"lies\s*\((\d+)\)", text, re.I)
        out["lies"] = int(m.group(1)) if m else 0
        m = re.search(r"trust score[:\s]*([\d.]+%?)", text, re.I)
        out["trust"] = m.group(1) if m else None
        return out
    if det == "browserscan":
        m = re.search(r"(Normal|Robot|Bot detected|Abnormal)", text)
        flagged = [k for k in ("WebDriver", "Headless", "CDP", "User-Agent", "Navigator", "Selenium", "Playwright")
                   if re.search(k + r"[^.]{0,40}(Abnormal|Detected|Yes|Failed)", text)]
        return {"verdict": m.group(1) if m else None, "flagged": flagged}
    return {}


def score(res):
    """Lower is better. Missing data counts as bad so a broken run never wins."""
    s = 0
    s += 3 * len(res.get("sannysoft", {}).get("failed", ["?"] * 5))
    c = res.get("creepjs", {})
    s += (c.get("headless") if c.get("headless") is not None else 100) / 10
    s += (c.get("like_headless") if c.get("like_headless") is not None else 100) / 10
    s += (c.get("stealth") or 0) / 10 + 2 * c.get("lies", 0)
    b = res.get("browserscan", {})
    s += 0 if b.get("verdict") == "Normal" else 5
    s += 3 * len(b.get("flagged", []))
    gl = res.get("fingerprint", {}).get("gl") or ""
    s += 4 if "SwiftShader" in gl else 0
    s += 10 if not gl else 0
    return round(s, 1)


def detect(name, st):
    res = {"candidate": name, "ts": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    try:
        with StealthySession(**kwargs_for(name, st)) as s:
            s.fetch("https://example.com/", page_action=lambda p: res.update(
                fingerprint=p.evaluate(wf.FP_JS, isolated_context=False)))
            for det, url in DETECTORS.items():
                holder = {}

                def act(p, det=det, holder=holder):
                    p.wait_for_timeout(9000 if det == "creepjs" else 5000)
                    try:
                        holder.update(parse(det, p))
                    except Exception as e:
                        holder["error"] = str(e)[:150]
                s.fetch(url, network_idle=False, page_action=act)
                res[det] = holder
    except Exception as e:
        res["error"] = f"{type(e).__name__}: {str(e)[:200]}"
    res["score"] = score(res) if "error" not in res else 999
    res["fingerprint"] = {k: (res.get("fingerprint") or {}).get(k) for k in ("ua", "gl", "dpr", "intl")}
    wf.record_experience("trial", result=f"score={res['score']}", candidate=name, detail=res)
    data = json.loads(TRIALS.read_text()) if TRIALS.exists() else {}
    data[name] = res
    TRIALS.write_text(json.dumps(data, ensure_ascii=False, indent=1))
    print(f"{name:<26} score={res['score']:<6} sanny_failed={len(res.get('sannysoft', {}).get('failed', []))} "
          f"creep={res.get('creepjs')} scan={res.get('browserscan')} gl={str(res['fingerprint'].get('gl'))[:60]}"
          + (f" ERROR={res['error']}" if 'error' in res else ""), flush=True)


def probe(name, st):
    """One home-page load on the target site for a detector-validated candidate."""
    data = json.loads(TRIALS.read_text()) if TRIALS.exists() else {}
    last = max((v.get("probe_ts", 0) for v in data.values()), default=0)
    if time.time() - last < PROBE_MIN_GAP:
        print(f"probe gap not reached ({int(time.time()-last)}s < {PROBE_MIN_GAP}s) - run more detector trials instead")
        return
    base = data.get("baseline_w1", {}).get("score", 999)
    if data.get(name, {}).get("score", 999) >= base and name != "baseline_w1":
        print(f"{name} does not beat baseline on detectors ({data.get(name, {}).get('score')} >= {base}); not probing")
        return
    out = {}

    def act(p):
        p.wait_for_timeout(10000)
        t = " ".join(p.inner_text("body").replace("\xa0", " ").lower().split())
        out["blocked"] = any(m in t[:2000] for m in wf.BLOCK_MARKERS)
        out["url"] = p.url[:100]
    with StealthySession(**kwargs_for(name, st)) as s:
        s.fetch(wf.BASE + "/", network_idle=True, page_action=act)
    data.setdefault(name, {})["probe"] = out
    data[name]["probe_ts"] = time.time()
    TRIALS.write_text(json.dumps(data, ensure_ascii=False, indent=1))
    wf.record_experience("probe", result="blocked" if out.get("blocked") else "passed", candidate=name, detail=out)
    print(name, "PROBE:", "BLOCKED" if out.get("blocked") else "PASSED", out)


def rank():
    data = json.loads(TRIALS.read_text()) if TRIALS.exists() else {}
    for n, r in sorted(data.items(), key=lambda kv: kv[1].get("score", 999)):
        print(f"{r.get('score'):<6} {n:<26} probe={r.get('probe')}")


if __name__ == "__main__":
    cmd, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ("rank", [])
    st = wf.load_state()
    if cmd == "detect":
        for n in (args or list(CANDIDATES)):
            detect(n, st)
    elif cmd == "probe":
        probe(args[0], st)
    else:
        rank()
