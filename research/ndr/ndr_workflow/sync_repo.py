"""Copy research code + data from the scratchpad into the git repo and commit/push it.

Run after every collection pass (post-pass hook) so a lost container loses at most one pass.
Browser profiles (session cookies, ~700 MB) and raw HTML dumps are never copied.
"""
import fnmatch
import shutil
import subprocess
import sys
import time
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent            # scratchpad
REPO = Path("/home/user/test")
DST = REPO / "research" / "ndr"
BRANCH = "claude/clever-ride-v467su"

INCLUDE = {
    "ndr_workflow": ["*.py", "*.sh", "EXPERIENCE.md", "experience.jsonl", "workflow.log", "state.json", "*.csv",
                     "competitors.txt", "report_data.json", "named_competitors.json", "extra_competitors.json"],
    "report": ["*.py", "report.pdf", "report_model.json", "mapsite/map.html", "maps/map_*.png"],
    "moscow_scan": ["*.py", "*.json", "*.csv", "*.pdf"],
    "ndr": ["moscow_map.json"],
    "lots": ["*.py", "*.json", "*.sh", "lib/*.py", "lib/*.js", "lots.db", "devcat.db", "paper.db", "registry.db", "collector.log", "devcat.log", "*.md", "*.pdf", "*.csv"],
    ".": ["tg.py", "tg_propertyinsider.json", "ddu_posts.txt"],
}
NEVER = ("profile", "session_A", "Cookies", ".html.bak", "/raw/")

TRAILER = ("\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n"
           "Claude-Session: https://claude.ai/code/session_01Jwebb6fe4TZTUDWn8VZaVf")


def copy_all():
    n = 0
    for sub, patterns in INCLUDE.items():
        base = SRC / sub
        for p in base.rglob("*") if sub != "." else base.glob("*"):
            if not p.is_file() or any(x in str(p) for x in NEVER):
                continue
            rel = p.relative_to(base).as_posix()
            if any(fnmatch.fnmatch(rel, pat) for pat in patterns):
                out = DST / sub / rel if sub != "." else DST / rel
                out.parent.mkdir(parents=True, exist_ok=True)
                if not out.exists() or out.stat().st_mtime < p.stat().st_mtime or out.stat().st_size != p.stat().st_size:
                    shutil.copy2(p, out)
                    n += 1
    DST.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SRC / "ndr_workflow" / "README_research.md", DST / "README.md")
    (DST / ".gitignore").write_text("# browser profiles hold session cookies - never commit\n**/profile*/\n**/session_*/\n**/*.html\n!report/mapsite/map.html\n")
    return n


def git(*args, check=True):
    return subprocess.run(["git", "-C", str(REPO), *args], capture_output=True, text=True, check=check)


def main(message):
    changed = copy_all()
    git("add", "research/ndr")
    if not git("status", "--porcelain", "research/ndr").stdout.strip():
        print(f"sync: nothing to commit ({changed} files checked)")
        return
    git("commit", "-q", "-m", message + TRAILER)
    for delay in (0, 2, 4, 8, 16):                       # retry only network failures
        time.sleep(delay)
        r = git("push", "-u", "origin", BRANCH, check=False)
        if r.returncode == 0:
            print(f"sync: committed and pushed ({changed} files updated)")
            return
        print("sync: push failed, retrying:", r.stderr.strip()[-200:])
    print("sync: push failed after retries; commit kept locally")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "Update research data")
