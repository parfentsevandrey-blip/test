"""Registries with stable ids and labels: developers, projects, buildings, flats (registry.db).

ids     D01 (developer), P01 (project = ЖК), P01-B03 (building), P01-F00042 (flat). An id never changes once given: the mapping
        «source, source id -> our id» lives in `xref`, so the next run recognises every developer, ЖК, building and flat it has
        seen before and does not redo work (matching, detail pages, building mapping).
labels  `labels(entity, label, value, since)`: what we know about an entity and what work it no longer needs, e.g.
          flat      details:complete (rooms, area, floor known — no detail page needed again), price:checked <date>,
                    seller:owner, penthouse, collection:<name>, promo, matched:cian <offer ids>, state:on_sale|booked|sold|gone
          building  ndr:<object id> (наш.дом.рф), completion <date>, mapped_by name|completion, needs:mapping
          project   method:<how the site is read>, needs:browser, gap:<s>, order:unstable (needs sort / second pass),
                    declares:total, robots:<rule we rely on>
Sources: pilot_zk.json, devcat.db (developer sites), lots.db (ЦИАН), ../moscow_scan/buildings.csv (наш.дом.рф objects).
Run after the daily collectors (run_devcat.sh / run_lots.sh); idempotent.
"""
import csv
import json
import re
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
DB = HERE / "registry.db"
MSK = timezone(timedelta(hours=3))
SCHEMA = """
create table if not exists developers (id text primary key, name text, grp text, sites text);
create table if not exists projects (id text primary key, dev_id text, name text, cian_zk_id text unique, site_source text, site_slug text,
                                     class text);
create table if not exists buildings (id text primary key, prj_id text, key text, name text, ndr_id text, completion text, flats int,
                                      unique (prj_id, key));
create table if not exists flats (id text primary key, prj_id text, bld_id text, floor int, rooms int, area real, number text,
                                  first_seen text, last_seen text);
create table if not exists xref (source text, source_id text, entity text, kind text, how text, since text,
                                 primary key (source, source_id, kind));
create table if not exists labels (entity text, label text, value text, since text, primary key (entity, label));
"""
# How each developer site is read (also the place to look before changing a collector)
METHOD = {
    "highlife": {"method": "one page catalog (__NEXT_DATA__), sold flats included", "declares:total": "1", "robots": "/search allowed"},
    "lsr": {"method": "list pages ?page=N in a browser (Servicepipe JS check)", "needs:browser": "1", "order:unstable": "repeat passes (L6)",
            "declares:total": "1", "robots": "Allow: *?page*"},
    "dreamriva": {"method": "one page catalog dreamriva.ru/flats/ (__NEXT_DATA__)", "declares:total": "1", "robots": "/flats/ allowed"},
    "granelle": {"method": "visitor in a browser: project page -> project flats -> sort by price -> «Показать еще»", "needs:browser": "1",
                 "order:unstable": "sort + second pass (L6, L11)", "declares:total": "1"},
    "forma": {"method": "visitor in a browser: project page -> «Показать N квартир» (page loads all flats)", "needs:browser": "1",
              "declares:total": "1"},
    "donstroy": {"method": "sitemap-flats.xml daily + flat pages (details, price) weekly", "robots": "*?* and */api/* closed"},
    "fsk": {"method": "sitemap/flats/N.xml daily + flat pages (Nuxt) weekly", "gap": "60-100", "robots": "/api/ and ? closed"},
}
SITE = {"3202206": ("highlife", "high-life"), "7889": ("lsr", "zilart"), "5340468": ("dreamriva", "dream-riva"),
        "3401924": ("granelle", "mypriority-dubrovka"), "5699304": ("granelle", "mypriority-nizhegorodskaya"),
        "4494942": ("granelle", "mypriority-paveletskaya"), "5221539": ("granelle", "mypriority-mnevniki"), "4183417": ("forma", "soul"),
        "2812286": ("donstroy", "ostrov"), "7231": ("donstroy", "simvol"), "4888961": ("fsk", "amber-city"),
        "2446053": ("fsk", "sydney-city"), "4377697": ("fsk", "sydney-prime")}
NDR_NAME = {"2812286": ["ОСТРОВ"], "7231": ["СИМВОЛ"], "5340468": ["ДРИМ РИВА"], "3401924": ["Дубровка от Гранель"],
            "5699304": ["Нижегородская от Гранель"], "4494942": ["Павелецкая от Гранель"], "5221539": ["Мнёвники от Гранель"],
            "7889": ["ЗИЛАРТ"], "4888961": ["Amber City"], "2446053": ["Sydney City"], "4377697": ["Sydney Prime", "Сидней Прайм"],
            "4183417": ["СОУЛ"], "3202206": ["HIGH LIFE"]}


def today():
    return datetime.now(MSK).strftime("%Y-%m-%d")


def bkey(source, name, slug=None):
    """A building key comparable across sources (L17):
    Остров — «квартал-корпус» (site quarter 122 = ЦИАН «Квартал 12.2»); Символ — the корпус number alone (корпуса are numbered
    through the whole ЖК; the site writes «7-36», ЦИАН «Корпус 36»); others — the building number."""
    s = str(name or "")
    m = re.search(r"[Кк]вартал\s*(\d+(?:\.\d+)?)\D+[Кк]орпус\D*(\d+)", s)
    if m and slug != "simvol":
        return f"{m[1]}-{m[2]}"
    if source == "donstroy" and re.fullmatch(r"\d+-\d+", s):
        q, k = s.split("-")
        if slug == "simvol":
            return k
        return f"{q[:-1]}.{q[-1]}-{k}" if len(q) >= 3 else f"{q}-{k}"
    m = re.search(r"(?:корп\w*\.?|[Дд]ом\s*№?|[КK])\s*(\d+(?:\.\d+)?)", s) or re.search(r"(\d+(?:\.\d+)?)", s)
    return m[1] if m else "?"


class Reg:
    def __init__(self):
        self.db = sqlite3.connect(DB, timeout=60)
        self.db.executescript(SCHEMA)
        self.day = today()

    def next_id(self, table, prefix, width):
        n = self.db.execute(f"select count(*) from {table} where id like ?", (prefix + "%",)).fetchone()[0]
        return f"{prefix}{n + 1:0{width}d}"

    def label(self, entity, label, value="1"):
        self.db.execute("""insert into labels values (?,?,?,?) on conflict(entity, label) do update set value=excluded.value,
                           since=case when labels.value is excluded.value then labels.since else excluded.since end""",
                        (entity, label, str(value), self.day))

    def xref(self, source, sid, kind):
        r = self.db.execute("select entity from xref where source=? and source_id=? and kind=?", (source, str(sid), kind)).fetchone()
        return r[0] if r else None

    def link(self, source, sid, entity, kind, how):
        self.db.execute("insert or ignore into xref values (?,?,?,?,?,?)", (source, str(sid), entity, kind, how, self.day))

    # --- developers & projects -------------------------------------------------------------------------------------------
    def projects(self, lots):
        pilot = json.loads((HERE / "pilot_zk.json").read_text())
        builder = dict(lots.execute("select zk_id, builder from zk_meta"))
        for grp, zks in pilot.items():
            for zk, name in zks.items():
                dev_name = builder.get(zk) or grp
                dev = self.db.execute("select id from developers where name=?", (dev_name,)).fetchone()
                if not dev:
                    did = self.next_id("developers", "D", 2)
                    self.db.execute("insert into developers values (?,?,?,?)", (did, dev_name, grp, json.dumps([SITE[zk][0]])))
                else:
                    did = dev[0]
                    sites = set(json.loads(self.db.execute("select sites from developers where id=?", (did,)).fetchone()[0]) + [SITE[zk][0]])
                    self.db.execute("update developers set sites=? where id=?", (json.dumps(sorted(sites)), did))
                pid = self.xref("cian_zk", zk, "project")
                if not pid:
                    pid = self.next_id("projects", "P", 2)
                    self.db.execute("insert into projects values (?,?,?,?,?,?,?)", (pid, did, name, zk, SITE[zk][0], SITE[zk][1], None))
                    self.link("cian_zk", zk, pid, "project", "pilot_zk.json")
                    self.link(SITE[zk][0], SITE[zk][1], pid, "project", "site slug")
                for k, v in METHOD[SITE[zk][0]].items():
                    self.label(pid, k, v)

    # --- buildings ----------------------------------------------------------------------------------------------------------
    def building(self, pid, source, name, how):
        slug = self.db.execute("select site_slug from projects where id=?", (pid,)).fetchone()[0]
        key = bkey(source, name, slug)
        r = self.db.execute("select id from buildings where prj_id=? and key=?", (pid, key)).fetchone()
        if r:
            bid = r[0]
        else:
            n = self.db.execute("select count(*) from buildings where prj_id=?", (pid,)).fetchone()[0]
            bid = f"{pid}-B{n + 1:02d}"
            self.db.execute("insert into buildings (id, prj_id, key, name) values (?,?,?,?)", (bid, pid, key, str(name)))
        self.link(source, f"{pid}:{name}", bid, "building", how)
        if key == "?":
            self.label(bid, "needs:mapping", f"name «{name}» has no building number")
        return bid

    def ndr(self):
        """наш.дом.рф objects (one permit = one or a few buildings): completion, flats, monthly sales — the П1 inputs."""
        rows = list(csv.DictReader(open(HERE.parent / "moscow_scan" / "buildings.csv")))
        for pid, zk in self.db.execute("select id, cian_zk_id from projects").fetchall():
            for r in rows:
                if any(k.lower() in (r["name"] or "").lower() for k in NDR_NAME[zk]):
                    bid = self.xref("ndr", r["id"], "ndr_object")
                    if not bid:
                        self.link("ndr", r["id"], pid, "ndr_object", "name match")
                    self.label(f"{pid}:ndr:{r['id']}", "completion", r["completion"])
                    self.label(f"{pid}:ndr:{r['id']}", "flats", r["flats"])

    # --- flats -------------------------------------------------------------------------------------------------------------
    def flats(self, dv, lots):
        prj = {zk: pid for pid, zk in self.db.execute("select id, cian_zk_id from projects")}
        for src, lot, zk, korpus, floor, rooms, area, special, seller, kind in dv.execute(
                "select source, lot_id, zk_id, korpus, floor, rooms, area, special, seller, kind from info where zk_id is not null"):
            pid = prj.get(zk)
            if not pid:
                continue
            fid = self.xref(src, lot, "flat")
            bid = self.building(pid, src, korpus, "developer site") if korpus else None
            sp = json.loads(special) if special else {}
            if not fid:
                n = self.db.execute("select count(*) from flats where prj_id=?", (pid,)).fetchone()[0]
                fid = f"{pid}-F{n + 1:05d}"
                self.db.execute("insert into flats (id, prj_id, bld_id, floor, rooms, area, number, first_seen) values (?,?,?,?,?,?,?,?)",
                                (fid, pid, bid, floor, rooms, area, sp.get("number"), self.day))
                self.link(src, lot, fid, "flat", "developer site")
            else:
                self.db.execute("update flats set bld_id=coalesce(bld_id,?), floor=coalesce(floor,?), rooms=coalesce(rooms,?), area=coalesce(area,?) where id=?",
                                (bid, floor, rooms, area, fid))
            if area and floor is not None and (rooms is not None or kind == "penthouse"):
                self.label(fid, "details:complete")
            if seller == "owner":
                self.label(fid, "seller:owner")
            if kind == "penthouse":
                self.label(fid, "penthouse")
            if kind == "apartments":
                self.label(fid, "kind:apartments", "excluded from flats (L19)")
            if sp.get("collection"):
                self.label(fid, "collection", sp["collection"])
            if sp.get("promotion") or sp.get("promo"):
                self.label(fid, "promo", sp.get("promo_label") or sp.get("promo"))
        # state of every site flat in its latest list
        on = {"highlife": ("Свободно",), "dreamriva": ("AVAILABLE",), "forma": ("free",), "granelle": ("1",)}
        for src, last in dv.execute("select source, max(day) from seen group by 1").fetchall():
            listed = {lot: st for lot, st in dv.execute("select lot_id, status from seen where source=? and day=?", (src, last))}
            for (sid, fid) in self.db.execute("select source_id, entity from xref where source=? and kind='flat'", (src,)).fetchall():
                st = listed.get(sid)
                state = "gone" if st is None else ("on_sale" if src not in on or st in on[src] else
                                                   "booked" if st in ("Резерв", "Уст.бронь", "BOOKED", "reserve") else "sold")
                self.label(fid, "state", state)
                if st is not None:
                    self.db.execute("update flats set last_seen=? where id=?", (last, fid))
            for lot, d in dv.execute("select lot_id, max(day) from prices where source=? and origin in ('page','catalog') group by 1", (src,)):
                fid = self.xref(src, lot, "flat")
                if fid:
                    self.label(fid, "price:checked", d)
        # ЦИАН: every offer -> a flat id; developer listings join the site flat when the match is unique (ЖК, floor, rooms, area ±0.6)
        day = lots.execute("select max(day) from obs").fetchone()[0]
        for oid, zk, house, floor, area, rooms, ft, dev in lots.execute("""
                select o.offer_id, o.zk_id, o.house_name, o.floor, o.area, o.rooms, o.flat_type, (o.from_developer=1 or o.sale_type='fz214')
                from obs b join offers o using(offer_id) where b.day=? and not o.is_apartments""", (day,)):
            pid = prj.get(zk)
            if not pid or self.xref("cian", oid, "flat"):
                continue
            r0 = 0 if (ft == "studio" or rooms == 0) else rooms
            bid = self.building(pid, "cian", house, "ЦИАН") if house else None
            cand = self.db.execute("""select id from flats f where prj_id=? and floor=? and rooms is ? and abs(area-?)<=0.6
                                      and not exists (select 1 from labels l where l.entity=f.id and l.label='kind:apartments')
                                      and (? is null or f.bld_id is null or f.bld_id=?)""",          # L21: same building if both known
                                   (pid, floor, r0, area or 0, bid, bid)).fetchall()
            if len(cand) == 1:
                strong = bid is not None and self.db.execute("select bld_id from flats where id=?", (cand[0][0],)).fetchone()[0] == bid
                self.link("cian", oid, cand[0][0], "flat", "unique match: " + ("building, " if strong else "weak, no building, ")
                          + "floor, rooms, area ±0.6")
                self.label(cand[0][0], "matched:cian", "1")
                continue
            # same flat already registered from another ЦИАН offer (several sellers)
            same = self.db.execute("""select f.id from flats f join xref x on x.entity=f.id and x.source='cian' where f.prj_id=? and f.floor=?
                                      and f.rooms is ? and abs(f.area-?)<=0.05 and coalesce(f.bld_id,'')=coalesce(?, '')""",
                                   (pid, floor, r0, area or 0, bid)).fetchone()
            if same:
                self.link("cian", oid, same[0], "flat", "same lot, another seller")
                continue
            n = self.db.execute("select count(*) from flats where prj_id=?", (pid,)).fetchone()[0]
            fid = f"{pid}-F{n + 1:05d}"
            self.db.execute("insert into flats (id, prj_id, bld_id, floor, rooms, area, first_seen, last_seen) values (?,?,?,?,?,?,?,?)",
                            (fid, pid, bid, floor, r0, area, day, day))
            self.link("cian", oid, fid, "flat", "ЦИАН only")
            self.label(fid, "seen:cian_only", "developer listing without a site match" if dev else "resale/assignment")

    def summary(self):
        q = lambda s, *a: self.db.execute(s, a).fetchone()[0]
        return {"developers": q("select count(*) from developers"), "projects": q("select count(*) from projects"),
                "buildings": q("select count(*) from buildings"), "flats": q("select count(*) from flats"),
                "xref": q("select count(*) from xref"), "labels": q("select count(*) from labels"),
                "details_complete": q("select count(*) from labels where label='details:complete'"),
                "needs_mapping": q("select count(*) from labels where label='needs:mapping'")}


def main():
    r = Reg()
    dv, lots = sqlite3.connect(HERE / "devcat.db", timeout=60), sqlite3.connect(HERE / "lots.db", timeout=60)
    r.projects(lots)
    r.ndr()
    r.flats(dv, lots)
    r.db.commit()
    return r.summary()


if __name__ == "__main__":
    print(main())
