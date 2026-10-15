"""Runs after every collection pass: rebuild the MKAD scan export, then commit + push to the repo."""
import json
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
st = json.loads((HERE / "state.json").read_text())
done, total = len(st["objects"]), sum(len(v) for v in st["searches"].values())
subprocess.run([sys.executable, "-I", str(HERE.parent / "moscow_scan" / "scan_export.py")], timeout=300)
subprocess.run([sys.executable, "-I", str(HERE / "sync_repo.py"), f"MKAD scan progress: {done}/{total} pages collected"], timeout=600)
