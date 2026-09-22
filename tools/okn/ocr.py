"""Распознаёт сканы документов ОКН — по одному разу на уникальный файл.

Документы ансамбля портал вешает на каждое строение отдельно, поэтому один
и тот же PDF приходит до шести раз: предмет охраны Щапова висит на всех
шести объектах усадьбы. Дедуп по содержимому срезает работу почти вдвое.

Масштаб рендера — 1.5 (≈150 dpi): на 2.2 страница уходила под минуту,
а на чистых сканах распоряжений выигрыша в качестве не давала.
"""
import hashlib, json, os, subprocess, tempfile
from concurrent.futures import ProcessPoolExecutor
import pymupdf

OUT = os.path.dirname(os.path.abspath(__file__))
DOCS, TXT = f"{OUT}/docs", f"{OUT}/doctext"
PRIO = {"PROTECTION_SUBJECT", "TECHNICAL_CONDITION_REPORT",
        "UNSTATISFACTORY_CONDITION_DECISION", "OBJECT_PASSPORT"}
MAXP, SCALE = 14, 1.5


def ocr_one(args):
    src, dst = args
    if os.path.exists(dst) and os.path.getsize(dst) > 400:
        return dst, os.path.getsize(dst), "cached"
    try:
        doc = pymupdf.open(src)
    except Exception as e:
        return dst, 0, f"err:{str(e)[:28]}"
    chunks = []
    with tempfile.TemporaryDirectory() as td:
        for i in range(min(doc.page_count, MAXP)):
            png = f"{td}/p{i}.png"
            try:
                doc[i].get_pixmap(matrix=pymupdf.Matrix(SCALE, SCALE)).save(png)
                r = subprocess.run(["tesseract", png, "stdout", "-l", "rus", "--psm", "6"],
                                   capture_output=True, timeout=150)
                chunks.append(r.stdout.decode("utf-8", "replace"))
            except Exception:
                pass
    text = "\n".join(chunks).strip()
    if text:
        open(dst, "w").write(text)
    return dst, len(text), "ok" if text else "empty"


def main():
    os.makedirs(TXT, exist_ok=True)
    ix = json.load(open(f"{OUT}/docs_index.json"))
    seen, jobs, share = {}, [], {}
    for oid, rows in ix.items():
        for r in rows:
            p = f"{DOCS}/{r['file']}"
            if not os.path.exists(p):
                continue
            h = hashlib.md5(open(p, "rb").read()).hexdigest()
            share.setdefault(h, []).append((oid, r["file"]))
            if h in seen:
                continue
            seen[h] = r
            if r["kind"] == "scan" and r["type"] in PRIO:
                jobs.append((p, f"{TXT}/_{h[:10]}_{r['type']}.ocr.txt"))
    json.dump({h: v for h, v in share.items()},
              open(f"{OUT}/docs_share.json", "w"), ensure_ascii=False, indent=1)
    print(f"уникальных файлов {len(seen)}, к распознаванию {len(jobs)}")
    done = 0
    with ProcessPoolExecutor(max_workers=4) as ex:
        for dst, n, st in ex.map(ocr_one, jobs):
            done += 1
            print(f"  {done:>2}/{len(jobs)}  {n:>7}зн  {st:<6} {os.path.basename(dst)}",
                  flush=True)


main()
