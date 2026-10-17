"""Offline test of devcat.donstroy_search_store (engine step 2) on a response shaped like the 17.10 trial."""
import importlib.util
import sqlite3
import tempfile
from pathlib import Path

LOTS = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("devcat", LOTS / "devcat.py")
D = importlib.util.module_from_spec(spec)
spec.loader.exec_module(D)


def test_store():
    db = sqlite3.connect(Path(tempfile.mkdtemp()) / "t.db")
    D.migrate(db)
    got = [{"total_flats": 1931, "flats": [
        {"id": "10-37-1-260137006", "rooms": 0, "area": "28", "price": 17836000, "price_old": None, "price_request": False,
         "link": "/objects/simvol/plans/quarter7/korpus37/section1/floor2/flat260137006/"},
        {"id": "x", "rooms": 2, "area": "61.4", "price": 50000000, "price_old": 56000000, "price_request": False,
         "link": "/objects/reka/plans/quarter1/korpus1/section1/floor3/flat1/"},
        {"id": "promo-card"}]}]
    out = D.donstroy_search_store(db, got)
    assert "pilot 1" in out and "'reka': 1" in out, out
    r = db.execute("select zk_id, korpus, section, floor, rooms, area from info where source='donstroy' and lot_id='260137006'").fetchone()
    assert r == ("7231", "7-37", "1", 2, 0, 28.0), r
    p = db.execute("select price, price_base, origin from prices where source='donstroy' and lot_id='260137006'").fetchone()
    assert p == (17836000.0, None, "catalog"), p


if __name__ == "__main__":
    test_store()
    print("ok test_store")
