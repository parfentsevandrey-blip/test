"""Offline test of devcat.fsk_tree_store (engine step 2) on a tree shaped like the 17.10 Amber City response."""
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
    flat = {"externalId": "222825", "number": "140", "rooms": 2, "areaTotal": 58.9, "floorNumber": 11, "price": 34295644,
            "priceWoDiscount": 46345465, "status": 0, "finishing": 3, "kind": "LIVING",
            "corpus": {"number": "4", "dateDelivery": "2027-09-30T20:59:59.999Z"}, "section": {"number": "1", "floorsCount": 57},
            "project": {"slug": "amber-city"}}
    out = D.fsk_tree_store(db, "amber-city", [[{"sections": [{"floors": [{"flats": [flat, {"number": "x"}]}]}]}]])
    assert out == "fsk tree amber-city 1", out
    r = db.execute("select zk_id, korpus, section, floor, rooms, area, sold from info where source='fsk' and lot_id='222825'").fetchone()
    assert r == ("4888961", "4", "1", 11, 2, 58.9, 0), r
    p = db.execute("select price, price_base, origin from prices where source='fsk' and lot_id='222825'").fetchone()
    assert p == (34295644.0, 46345465.0, "catalog"), p


if __name__ == "__main__":
    test_store()
    print("ok test_store")
