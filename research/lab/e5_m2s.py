"""E5. mitmproxy2swagger vs the current endpoint search (HAR -> lib/audit_site.py summary + REA api-surface), offline.

Hypothesis: mitmproxy2swagger finds the data endpoints of a developer site from one HAR faster and more completely than
the current path, and gives a usable schema (parameters, response fields).
Sample: HARs of the 16.10 audit, frozen in runs/e5/ (no new requests to the sites):
  donstroy_floor  — floor page of a section (known target: POST /api/v1/floor/choose_floor/, L25)
  granelle_object — project page (known target: GET /api/flats/?project&order&limit&offset; the visit did not open the list)
  forma_soul      — project page with «Показать N квартир» (known target: manager.forma.ru/api/v2/marketplace)
Two passes as the README says: pass 1 writes every path as «ignore:<path>», we keep all paths of the site, pass 2 builds
the schema. Output: runs/e5/<name>.yaml, runs/e5/result.json.
"""
import json
import subprocess
import time
from pathlib import Path

import yaml

R = Path(__file__).resolve().parent / "runs" / "e5"
SCR = Path("/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad")
BIN = Path(__file__).resolve().parent / ".venv" / "bin" / "mitmproxy2swagger"
SITES = {
    "donstroy_floor": {"prefix": ["https://donstroy.moscow"], "target": ("post", "choose_floor"), "baseline": SCR / "rea" / "donstroy_har.json"},
    "granelle_object": {"prefix": ["https://granelle.ru"], "target": ("get", "/api/flats/"), "baseline": SCR / "lots" / "audit" / "granelle_object.json"},
    "forma_soul": {"prefix": ["https://manager.forma.ru", "https://forma.ru"], "target": ("post", "/api/v2/marketplace"),
                   "baseline": SCR / "lots" / "audit" / "forma_soul.json"},
}


def m2s(har, prefix, out):
    subprocess.run([str(BIN), "-i", str(har), "-o", str(out), "-p", prefix, "-f", "har", "-r", r"^\d+$"],
                   check=True, capture_output=True)


def run(name, cfg):
    t0 = time.time()
    found = []
    for prefix in cfg["prefix"]:
        out = R / f"{name}.{prefix.split('//')[1]}.yaml"
        out.unlink(missing_ok=True)
        m2s(R / f"{name}.har", prefix, out)                           # pass 1: candidate paths
        doc = yaml.safe_load(out.read_text())
        doc["x-path-templates"] = [p.removeprefix("ignore:") for p in doc.get("x-path-templates", [])]
        out.write_text(yaml.safe_dump(doc, allow_unicode=True, sort_keys=False))
        m2s(R / f"{name}.har", prefix, out)                           # pass 2: schema for the kept paths
        doc = yaml.safe_load(out.read_text())
        for path, ops in (doc.get("paths") or {}).items():
            for method, op in ops.items():
                params = [p.get("name") for p in op.get("parameters", [])]
                body = bool(op.get("requestBody"))
                rs = op.get("responses", {})
                resp = next((rs[c] for c in (200, "200", 201, "201") if c in rs), {})
                schema = (resp.get("content", {}).get("application/json", {}) or {}).get("schema", {})
                fields = list((schema.get("properties") or schema.get("items", {}).get("properties") or {}).keys())
                rb = (op.get("requestBody", {}).get("content", {}).get("application/json", {}) or {}).get("schema", {})
                found.append({"host": prefix, "method": method, "path": path, "params": params, "body": list((rb.get("properties") or {}).keys())
                              if body else [], "fields": fields[:15], "n_fields": len(fields)})
    secs = round(time.time() - t0, 1)
    tm, tp = cfg["target"]
    hit = [f for f in found if f["method"] == tm and tp in f["path"]]
    text = cfg["baseline"].read_text()                                # audit summary / REA evidence of the same HAR
    own = json.loads(text).get("own_data_responses")
    base_hit = tp in text
    return {"seconds": secs, "endpoints": len(found), "with_json_fields": sum(1 for f in found if f["n_fields"]),
            "target_found": bool(hit), "target": hit[:2], "baseline_own_responses": len(own) if isinstance(own, list) else None,
            "baseline_target_found": base_hit, "endpoints_list": [f"{f['method'].upper()} {f['host']}{f['path']}" for f in found]}


def main():
    res = {n: run(n, c) for n, c in SITES.items()}
    (R / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    for n, r in res.items():
        print(n, {k: v for k, v in r.items() if k not in ("endpoints_list", "target")})
        print("   target:", json.dumps(r["target"], ensure_ascii=False)[:400])


if __name__ == "__main__":
    main()
