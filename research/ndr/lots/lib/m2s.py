"""Second reading of a HAR with mitmproxy2swagger (E5 = W25): every own-site endpoint with its parameters, request-body fields and
JSON response fields. Runs after REA, not instead (REA can fail on a large HAR: FORMA, `resource_constraint`, L34).

surface(har, prefixes, work_dir) -> {"endpoints": [...], "error": None | str}
Two passes as the README says: pass 1 writes every path as «ignore:<path>», we keep all of them, pass 2 builds the schema.
"""
import shutil
import subprocess
from pathlib import Path

LAB_BIN = Path("/home/user/test/research/lab/.venv/bin/mitmproxy2swagger")


def _bin():
    return str(LAB_BIN) if LAB_BIN.exists() else shutil.which("mitmproxy2swagger")


def _run(b, har, prefix, out):
    subprocess.run([b, "-i", str(har), "-o", str(out), "-p", prefix, "-f", "har", "-r", r"^\d+$"], check=True, capture_output=True,
                   timeout=600)


def _fields(schema):
    schema = schema or {}
    props = schema.get("properties") or (schema.get("items") or {}).get("properties") or {}
    return list(props)


def surface(har, prefixes, work_dir):
    b = _bin()
    if not b:
        return {"endpoints": [], "error": "mitmproxy2swagger not installed"}
    import yaml
    found = []
    try:
        for prefix in prefixes:
            out = Path(work_dir) / f"{Path(har).stem}.{prefix.split('//')[1]}.m2s.yaml"
            out.unlink(missing_ok=True)
            _run(b, har, prefix, out)
            doc = yaml.safe_load(out.read_text()) or {}
            doc["x-path-templates"] = [p.removeprefix("ignore:") for p in doc.get("x-path-templates") or []]
            out.write_text(yaml.safe_dump(doc, allow_unicode=True, sort_keys=False))
            _run(b, har, prefix, out)
            doc = yaml.safe_load(out.read_text()) or {}
            for path, ops in (doc.get("paths") or {}).items():
                for method, op in (ops or {}).items():
                    rs = op.get("responses") or {}
                    resp = next((rs[c] for c in (200, "200", 201, "201", 204, "204") if c in rs), {})
                    rsch = ((resp.get("content") or {}).get("application/json") or {}).get("schema")
                    bsch = (((op.get("requestBody") or {}).get("content") or {}).get("application/json") or {}).get("schema")
                    if not rsch and not bsch:
                        continue                                   # assets and pages: noise for an adapter
                    found.append({"method": method.upper(), "url": prefix + path,
                                  "params": [p.get("name") for p in op.get("parameters") or []],
                                  "body_fields": _fields(bsch), "response_fields": _fields(rsch)[:30]})
    except Exception as e:                                         # one tool failing must be visible in the summary (L34)
        return {"endpoints": found, "error": f"{type(e).__name__}: {str(e)[:200]}"}
    return {"endpoints": found, "error": None}
