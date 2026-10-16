"""[AUDIT] Baseline metrics of the pipeline, read-only: logs and databases, no requests, no changes.

Sources: ndr_workflow/workflow.log + experience.jsonl (наш.дом.рф), lots/collector.log + lots.db (ЦИАН),
lots/devcat.db fetches (developer sites), lots/qa.json (data defects). Output: baseline.json (printed summary).
"""
import collections
import json
import re
import sqlite3
import statistics
from datetime import datetime
from pathlib import Path

S = Path(__file__).resolve().parents[2]          # scratchpad
LOTS = S / "lots"


def ts(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def gaps(times):
    t = sorted(times)
    return [(b - a).total_seconds() for a, b in zip(t, t[1:])]


def ndr():
    pages, net_err, blocks, cooldown_skips, starts = [], 0, 0, 0, 0
    for line in open(S / "ndr_workflow" / "workflow.log", encoding="utf-8"):
        m = re.match(r"(\d{4}-\d\d-\d\dT\S+) (.*)", line)
        if not m:
            continue
        t, rest = ts(m[1]), m[2]
        if rest.startswith("object "):
            pages.append(t)
        elif rest.startswith("network error"):
            net_err += 1
        elif rest.startswith("BLOCKED"):
            blocks += 1
        elif "cooldown active" in rest:
            cooldown_skips += 1
        elif rest.startswith("preflight"):
            starts += 1
    exp = [json.loads(l) for l in open(S / "ndr_workflow" / "experience.jsonl")]
    passes = [x for x in exp if x["kind"] in ("success", "failure")]
    res = collections.Counter("ok" if x["kind"] == "success" else ("blocked" if x.get("result") == "blocked" else "error") for x in passes)
    g = [x for x in gaps(pages) if x < 1800]
    span_h = (max(pages) - min(pages)).total_seconds() / 3600 if pages else 0
    hours = collections.Counter(p.strftime("%Y-%m-%d %H") for p in pages)
    return {"pages": len(pages), "span_hours": round(span_h, 1), "pages_per_hour_avg": round(len(pages) / span_h, 1) if span_h else None,
            "pages_per_hour_max": max(hours.values()) if hours else None, "sec_between_pages_median": round(statistics.median(g)) if g else None,
            "passes": dict(res), "pass_success_share": round(res["ok"] / sum(res.values()), 2) if res else None,
            "hard_blocks_log": blocks, "network_errors": net_err, "cooldown_skips": cooldown_skips, "run_starts": starts,
            "first": min(pages).isoformat() if pages else None, "last": max(pages).isoformat() if pages else None}


def cian():
    db = sqlite3.connect(LOTS / "lots.db")
    pages = db.execute("select day, ok, fetched from pages where url like '%cat.php%'").fetchall()
    t = [ts(p[2]) for p in pages if p[2]]
    ok = sum(p[1] for p in pages)
    g = [x for x in gaps(t) if x < 600]
    cards = db.execute("select count(*) from cards").fetchone()[0]
    starts = blocks = 0
    for line in open(LOTS / "collector.log", encoding="utf-8"):
        if "run started" in line or " start " in line:
            starts += 1
        if "BLOCKED" in line or "stopped:" in line:
            blocks += 1
    span = (max(t) - min(t)).total_seconds() / 3600 if t else 0
    return {"search_pages": len(pages), "search_ok_share": round(ok / len(pages), 3) if pages else None, "cards": cards,
            "sec_between_requests_median": round(statistics.median(g)) if g else None, "span_hours_search": round(span, 1),
            "pages_per_hour": round(len(t) / span, 1) if span else None, "blocks": blocks, "run_starts_in_log": starts,
            "offers_today": db.execute("select count(*) from obs where day=(select max(day) from obs)").fetchone()[0]}


def devcat():
    db = sqlite3.connect(LOTS / "devcat.db")
    rows = db.execute("select url, status, at from fetches").fetchall()
    by = collections.defaultdict(list)
    for u, st, at in rows:
        host = "visitor:" + u.split(":")[1] if u.startswith("visitor:") and "//" not in u.split(":", 1)[1][:3] else (u.split("/")[2] if "://" in u else u)
        by[host].append((st, ts(at)))
    out = {}
    for h, xs in sorted(by.items()):
        c = collections.Counter(s for s, _ in xs)
        g = [x for x in gaps([t for _, t in xs]) if x < 600]
        span = (max(t for _, t in xs) - min(t for _, t in xs)).total_seconds() / 3600
        out[h] = {"requests": len(xs), "ok_share": round(c.get("200", 0) / len(xs), 3), "statuses": dict(c),
                  "sec_between_median": round(statistics.median(g)) if g else None,
                  "per_hour": round(len(xs) / span, 1) if span > 0.05 else None}
    return out


def quality():
    q = json.loads((LOTS / "qa.json").read_text())
    by = collections.Counter(f["rule"] for f in q["findings"])
    return {"records_checked": q["layer1_records"], "pages_rechecked": q["layer2_checked"], "findings_by_rule": dict(by)}


if __name__ == "__main__":
    res = {"ndr": ndr(), "cian": cian(), "devcat": devcat(), "quality": quality()}
    (LOTS / "audit" / "baseline.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(json.dumps(res, ensure_ascii=False, indent=1))
