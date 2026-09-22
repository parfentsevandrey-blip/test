"""Точечное распознавание: предмет охраны и акты технического состояния.

Режим psm 6 («одна колонка текста») захлёбывался на полосах со схемами и
планами — страница уходила под полторы минуты. psm 3 сегментирует полосу
сам и на таких документах заметно быстрее; жёсткий потолок на страницу
не даёт одному развороту съесть прогон.
"""
import os, subprocess, sys, tempfile
from concurrent.futures import ProcessPoolExecutor
import pymupdf

OUT = os.path.dirname(os.path.abspath(__file__))
DOCS, TXT = f"{OUT}/docs", f"{OUT}/doctext"
SCALE, PAGE_TIMEOUT = 1.35, 110

TIER1 = ["3529_2_PROTECTION_SUBJECT.pdf", "1343_4_PROTECTION_SUBJECT.pdf",
         "4336_0_PROTECTION_SUBJECT.pdf", "1498_3_PROTECTION_SUBJECT.pdf",
         "3529_4_PROTECTION_SUBJECT.pdf"]
TIER2 = ["4100_1_TECHNICAL_CONDITION_REPORT.pdf", "3532_2_TECHNICAL_CONDITION_REPORT.pdf",
         "4336_2_TECHNICAL_CONDITION_REPORT.pdf", "3530_1_TECHNICAL_CONDITION_REPORT.pdf",
         "3531_2_TECHNICAL_CONDITION_REPORT.pdf", "4248_0_TECHNICAL_CONDITION_REPORT.pdf",
         "1498_2_TECHNICAL_CONDITION_REPORT.pdf", "1343_3_TECHNICAL_CONDITION_REPORT.pdf",
         "3764_2_TECHNICAL_CONDITION_REPORT.pdf", "3764_3_OBJECT_PASSPORT.pdf"]


def one(fname):
    src, dst = f"{DOCS}/{fname}", f"{TXT}/{fname[:-4]}.ocr.txt"
    if os.path.exists(dst) and os.path.getsize(dst) > 400:
        return fname, os.path.getsize(dst), "cached"
    try:
        doc = pymupdf.open(src)
    except Exception as e:
        return fname, 0, f"err:{str(e)[:26]}"
    chunks = []
    with tempfile.TemporaryDirectory() as td:
        for i in range(min(doc.page_count, 16)):
            png = f"{td}/p{i}.png"
            try:
                doc[i].get_pixmap(matrix=pymupdf.Matrix(SCALE, SCALE)).save(png)
                r = subprocess.run(["tesseract", png, "stdout", "-l", "rus", "--psm", "3"],
                                   capture_output=True, timeout=PAGE_TIMEOUT)
                chunks.append(r.stdout.decode("utf-8", "replace"))
            except subprocess.TimeoutExpired:
                chunks.append(f"\n[страница {i+1}: распознавание прервано по таймауту]\n")
            except Exception:
                pass
    text = "\n".join(chunks).strip()
    if text:
        open(dst, "w").write(text)
    return fname, len(text), "ok" if text else "empty"


if __name__ == "__main__":
    jobs = {"1": TIER1, "2": TIER2}.get(sys.argv[1] if sys.argv[1:] else "", TIER1 + TIER2)
    print(f"распознаём {len(jobs)} документов")
    with ProcessPoolExecutor(max_workers=3) as ex:
        for i, (f, n, st) in enumerate(ex.map(one, jobs), 1):
            print(f"  {i:>2}/{len(jobs)} {n:>7}зн {st:<7} {f}", flush=True)
