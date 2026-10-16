"""Per-site pause after a block (L32): a 403/429/captcha sets `until` for the site in cooldown.json; collectors and the resume
script skip the site until then. A calendar day is not a pause: the next day may start minutes after the block."""
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

FILE = Path(__file__).resolve().parent.parent / "cooldown.json"
MSK = timezone(timedelta(hours=3))


def load():
    return json.loads(FILE.read_text()) if FILE.exists() else {}


def set_block(site, hours=24, why=""):
    d = load()
    until = datetime.now(MSK) + timedelta(hours=hours)
    if site not in d or d[site]["until"] < until.isoformat():
        d[site] = {"until": until.isoformat(timespec="seconds"), "why": why, "set": datetime.now(MSK).isoformat(timespec="seconds")}
        FILE.write_text(json.dumps(d, ensure_ascii=False, indent=1))
    return d[site]


def active(site):
    """None if the site may be contacted now, else the record that forbids it."""
    r = load().get(site)
    return r if r and datetime.fromisoformat(r["until"]) > datetime.now(MSK) else None


if __name__ == "__main__":          # cooldown.py check <site>  -> exit 1 while paused
    r = active(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[1] == "check" else None
    print(json.dumps(r, ensure_ascii=False) if r else "ok")
    sys.exit(1 if r else 0)
