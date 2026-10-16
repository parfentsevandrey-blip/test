"""Re-run only the REA part of an audit on HARs already recorded (no new requests to the sites)."""
import json, subprocess, sys, urllib.parse
from pathlib import Path
out = Path(sys.argv[1]).resolve()
for f in sorted(out.glob("*.json")):
    if f.name.endswith((".rea.json", ".view_in.json")) or not (out / (f.stem + ".clean.har")).exists():
        continue
    d = json.loads(f.read_text())
    if not d.get("rea_error"):
        continue
    clean = out / (f.stem + ".clean.har")
    ev = subprocess.run(["rea", "inspect-web-network-capture", str(clean), "har", "--format", "json"], capture_output=True, text=True, timeout=600)
    try:
        evd = json.loads(ev.stdout)
        if "normalized_result" not in evd:
            raise ValueError(ev.stdout[:300])
        (out / f"{f.stem}.rea.json").write_text(ev.stdout)
        vin = out / f"{f.stem}.view_in.json"
        vin.write_text(json.dumps({"source": {"kind": "inline", "evidence": evd}, "view": {"kind": "api-surface", "include_query_parameter_names": True, "limit": 200}}))
        vr = json.loads(subprocess.run(["rea", "inspect-analysis-view", str(vin), "--format", "json"], capture_output=True, text=True, timeout=600).stdout)
        site = ".".join(urllib.parse.urlsplit(d["url"]).hostname.split(".")[-2:])
        surf = [{"method": g["method"], "origin": g["origin"], "path": g["path_template"], "n": len(g["record_ordinals"]), "status": g["statuses"],
                 "type": g["content_types"], "query": [q["name"] for q in g.get("query_parameter_names", [])]} for g in vr["normalized_result"]["groups"]]
        d["rea_api_surface_own"] = [s for s in surf if (urllib.parse.urlsplit(s["origin"]).hostname or "").endswith(site)]
        d["rea_api_surface_third_party_origins"] = sorted({s["origin"] for s in surf if not (urllib.parse.urlsplit(s["origin"]).hostname or "").endswith(site)})
        d["rea_evidence_id"], d["rea_error"] = evd.get("evidence_id"), None
    except Exception as e:
        d["rea_error"] = str(e)[:300]
    f.write_text(json.dumps(d, ensure_ascii=False, indent=1))
    print(f.stem, "REA own groups", len(d.get("rea_api_surface_own") or []), "third-party origins", len(d.get("rea_api_surface_third_party_origins") or []), d["rea_error"] or "")
