# Detect the page each TOC heading lands on and write pages back into report_data.json
import json, re, subprocess

def norm(s):
    return re.sub(r"\s+", " ", s).strip().lower()

# per-page text
out = subprocess.run(["pdftotext", "-layout", "report.pdf", "-"], capture_output=True, text=True).stdout
pages = out.split("\f")  # form-feed separates pages

data = json.load(open("report_data.json"))
toc = data["tocStatic"]

for e in toc:
    full = norm(e["title"])
    key = full[:28]
    found = None
    for pnum, ptext in enumerate(pages, start=1):
        if pnum <= 2:          # skip cover + TOC pages
            continue
        # a heading sits on its own SHORT line; body mentions live inside long paragraph lines
        for line in ptext.split("\n"):
            ln = norm(line)
            if ln.startswith(key) and len(ln) <= len(full) + 8:
                found = pnum
                break
        if found:
            break
    e["page"] = found or 1

json.dump(data, open("report_data.json", "w"), ensure_ascii=False, indent=1)
print("TOC pages detected:")
for e in toc:
    print(f'  L{e["level"]} p{e["page"]:>2}  {e["title"][:52]}')
