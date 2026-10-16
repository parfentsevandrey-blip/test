"""Keep the page's own data requests (document, xhr, fetch) of a HAR and make every URL a valid URI for REA."""
import json, sys, urllib.parse
src, dst = sys.argv[1], sys.argv[2]
h = json.load(open(src))
keep = []
for e in h["log"]["entries"]:
    u = e["request"]["url"]
    if not u.startswith("http"):
        continue
    t = (e.get("_resourceType") or "").lower()
    mime = (e["response"].get("content", {}).get("mimeType") or "").lower()
    if t not in ("document", "xhr", "fetch") and "json" not in mime:
        continue
    e["request"]["url"] = urllib.parse.quote(u, safe=":/?&=%#@+,;~!$'()*[]")
    keep.append(e)
h["log"]["entries"] = keep
json.dump(h, open(dst, "w"))
print(len(keep), "entries kept")
