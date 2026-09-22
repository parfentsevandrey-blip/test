"""Качает документы всех объектов и вынимает текстовый слой.

Ручка отдаёт файл без авторизации, WAF на неё не срабатывает, поэтому
обычного curl достаточно — браузер не нужен.
"""
import json, os, subprocess, sys
import pymupdf

OUT = os.path.dirname(os.path.abspath(__file__))
DOCS = os.path.join(OUT, "docs")
TXT = os.path.join(OUT, "doctext")
SITE = "https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")


def fetch(doc_id, path):
    if os.path.exists(path) and os.path.getsize(path) > 2000:
        return True
    r = subprocess.run(
        ["curl", "-sS", "-o", path, "--max-time", "300", "--retry", "2",
         "-H", f"User-Agent: {UA}", "-H", f"Referer: {SITE}/",
         f"{SITE}/okn/api/portal/document?documentId={doc_id}"],
        capture_output=True)
    return os.path.exists(path) and os.path.getsize(path) > 2000


def main():
    os.makedirs(DOCS, exist_ok=True)
    os.makedirs(TXT, exist_ok=True)
    objs = json.load(open(f"{OUT}/okn_objects.json"))
    index = {}
    for oid, o in objs.items():
        rows = []
        for n, d in enumerate(o.get("documentsInfo") or []):
            did, dtype = d["id"], d["documentType"]
            pdf = f"{DOCS}/{oid}_{n}_{dtype}.pdf"
            ok = fetch(did, pdf)
            chars, pages, kind = 0, 0, "fail"
            if ok:
                try:
                    doc = pymupdf.open(pdf)
                    pages = doc.page_count
                    t = "".join(p.get_text() for p in doc)
                    chars = len(t.strip())
                    kind = "text" if chars > 600 else "scan"
                    if chars > 0:
                        open(f"{TXT}/{oid}_{n}_{dtype}.txt", "w").write(t)
                except Exception as e:
                    kind = f"err:{str(e)[:30]}"
            rows.append({"id": did, "type": dtype, "file": os.path.basename(pdf),
                         "pages": pages, "chars": chars, "kind": kind,
                         "publishedAt": d.get("publishedAt"),
                         "size": d.get("documentSize")})
            print(f"{oid} {dtype[:34]:<34} {pages:>4}с {chars:>7}зн {kind}", flush=True)
        index[oid] = rows
    json.dump(index, open(f"{OUT}/docs_index.json", "w"), ensure_ascii=False, indent=1)
    tot = sum(len(v) for v in index.values())
    txt = sum(1 for v in index.values() for r in v if r["kind"] == "text")
    print(f"\nвсего {tot}, с текстовым слоем {txt}, сканов {tot-txt}")


main()
