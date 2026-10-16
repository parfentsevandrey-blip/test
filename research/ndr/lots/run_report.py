"""Mandatory run log (RUNLOG.md): after every daily run, what was collected, how complete it is, what failed, what looks wrong.

For each of the 13 pilot ЖК:
  developer site — flats collected vs the number the site itself declares (declared; for sitemap / one-page catalogs the list
                   is the declaration), share with a price, share with special characteristics, change vs the previous run;
  ЦИАН           — offers collected vs the total ЦИАН shows for the ЖК, change vs the previous run;
  join           — ЦИАН developer lots matched to the developer site (coverage.json from unify.py).
Fetch failures per site (403/429/5xx/network/blocked) and every anomaly get an id (A1, A2, …) inside the run entry
R-YYYY-MM-DD. Anomalies are analysed by hand under «Разбор» and become lessons L-n in LESSONS.md.
Only the block between the auto markers is regenerated, the «Разбор» part written by hand is kept.
"""
import json
import re
import sqlite3
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOG = HERE / "RUNLOG.md"
PILOT = {z: n for d in json.loads((HERE / "pilot_zk.json").read_text()).values() for z, n in d.items()}
ON_SALE = {"highlife": ("Свободно",), "dreamriva": ("AVAILABLE",), "forma": ("free",), "granelle": ("1",)}
COMPLETE, DROP = 98.0, 15.0          # % collected of declared; % fall of a count vs the previous run


def dev_numbers(dv, day):
    out = {}
    for src, zk, n, n_sale, n_price, n_spec in dv.execute("""
            select s.source, s.zk_id, count(*),
                   sum(case when s.source in ('highlife','dreamriva','forma','granelle') then
                       s.status in ('Свободно','AVAILABLE','free','1') else 1 end),
                   sum(exists(select 1 from prices p where p.source=s.source and p.lot_id=s.lot_id)),
                   sum(i.special is not null or (i.views is not null and i.views not in ('[]','[""]')))
            from seen s join info i on i.source=s.source and i.lot_id=s.lot_id where s.day=? group by 1,2""", (day,)):
        dec = dv.execute("select n from declared where day=? and source=? and zk_id=?", (day, src, zk)).fetchone()
        out[zk] = {"source": src, "collected": n, "on_sale": n_sale, "with_price": n_price, "with_special": n_spec,
                   "declared": dec[0] if dec else n, "declared_by": "сайт" if dec else "список сайта"}
    return out


def cian_numbers(lt, day):
    out = {}
    for zk in PILOT:
        n = lt.execute("select count(*) from obs where day=? and zk_id=?", (day, zk)).fetchone()[0]
        site = lt.execute("select max(total) from pages where day=? and zk_id=? and url like '%cat.php%'", (day, zk)).fetchone()[0]
        out[zk] = {"collected": n, "site_total": site}
    return out


def failures(db, day, sql):
    return db.execute(sql, (day,)).fetchall()


def pct(a, b):
    return round(100 * a / b, 1) if b else None


def build(day=None):
    dv, lt = sqlite3.connect(HERE / "devcat.db", timeout=60), sqlite3.connect(HERE / "lots.db", timeout=60)
    day = day or max(dv.execute("select max(day) from seen").fetchone()[0], lt.execute("select max(day) from obs").fetchone()[0])
    prev_d = dv.execute("select max(day) from seen where day<?", (day,)).fetchone()[0]
    prev_c = lt.execute("select max(day) from obs where day<?", (day,)).fetchone()[0]
    dev, dev_p = dev_numbers(dv, day), dev_numbers(dv, prev_d) if prev_d else {}
    cian, cian_p = cian_numbers(lt, day), cian_numbers(lt, prev_c) if prev_c else {}
    cov = json.loads((HERE / "coverage.json").read_text())["projects"] if (HERE / "coverage.json").exists() else {}
    anomalies, rows = [], []
    for zk, name in PILOT.items():
        d, c, dp, cp = dev.get(zk), cian[zk], dev_p.get(zk), cian_p.get(zk)
        dev_c = pct(d["collected"], d["declared"]) if d else None
        cian_c = pct(c["collected"], c["site_total"])
        rows.append((name, d, dev_c, c, cian_c, (cov.get(name) or {})))
        if not d:
            anomalies.append(f"{name}: нет данных с сайта застройщика за {day}")
        elif dev_c is not None and dev_c < COMPLETE:
            anomalies.append(f"{name}: с сайта собрано {d['collected']} из {d['declared']} ({dev_c}%)")
        if d and dp and dp["collected"] and d["collected"] < dp["collected"] * (1 - DROP / 100):
            anomalies.append(f"{name}: на сайте {d['collected']} против {dp['collected']} в прошлый прогон (−{round(100 - pct(d['collected'], dp['collected']), 1)}%) — разбор сайта или реальное выбытие?")
        if cian_c is not None and cian_c < COMPLETE:
            anomalies.append(f"{name}: на ЦИАН собрано {c['collected']} из {c['site_total']} ({cian_c}%)")
        if cp and cp["collected"] and c["collected"] < cp["collected"] * (1 - DROP / 100):
            anomalies.append(f"{name}: на ЦИАН {c['collected']} против {cp['collected']} в прошлый прогон")
    fail_d = failures(dv, day, "select substr(url,1,instr(substr(url,9),'/')+8), status, count(*) from fetches where day=? and status not in ('200') group by 1,2")
    fail_c = failures(lt, day, "select substr(url,1,40), status, count(*) from pages where day=? and status!='200' group by 1,2") \
        if "status" in [r[1] for r in lt.execute("pragma table_info(pages)")] else []
    for h, st, n in fail_d + fail_c:
        anomalies.append(f"сбой {h}: {st} × {n}")
    groups = dv.execute("select grp, status, finished from gruns where day=? order by grp", (day,)).fetchall()
    for g, st, fin in groups:
        if not fin:
            anomalies.append(f"группа devcat «{g}» не завершена: {st}")

    t = [f"<!-- auto R-{day} -->", f"## R-{day}", "",
         f"Снимок {day}; предыдущий: сайты {prev_d or '—'}, ЦИАН {prev_c or '—'}. Группы devcat: "
         + ", ".join(f"{g} — {'готово' if fin else st}" for g, st, fin in groups) + ".", "",
         "| ЖК | Сайт: собрано / объявлено | Полнота | В продаже | С ценой | Со спецхар. | Δ к прошлому | ЦИАН: собрано / на сайте | Полнота | ЦИАН-застройщик ↔ сайт |",
         "|---|---|---|---|---|---|---|---|---|---|"]
    for name, d, dev_c, c, cian_c, cv in rows:
        dp = dev_p.get(cv.get("zk_id") or next(z for z, n in PILOT.items() if n == name))
        delta = (d["collected"] - dp["collected"]) if d and dp else None
        t.append(f"| {name} | " + (f"{d['collected']} / {d['declared']} ({d['declared_by']})" if d else "—") +
                 f" | {dev_c if dev_c is not None else '—'}% | {d['on_sale'] if d else '—'} | {d['with_price'] if d else '—'} | "
                 f"{d['with_special'] if d else '—'} | {('+' if delta and delta > 0 else '') + str(delta) if delta is not None else '—'} | "
                 f"{c['collected']} / {c['site_total'] or '—'} | {cian_c if cian_c is not None else '—'}% | "
                 + (f"{cv.get('cian_dev_matched_to_site')} из {cv.get('cian_dev_lots')}" if cv else "—") + " |")
    sig = []
    for f, label, key in (("c2_signals.json", "С2 сигналов", "signal"), ("dev_signals.json", "С1 сигналов", "signal"),
                          ("c3_signals.json", "С3 дефицитных типов", "deficit")):
        if (HERE / f).exists():
            sig.append(f"{label}: {sum(int(x.get(key) or 0) for x in json.loads((HERE / f).read_text())['types'])}")
    cards = lt.execute("select count(*) from cards where day=?", (day,)).fetchone()[0]
    if (HERE / "paper.db").exists():
        pdb = sqlite3.connect(HERE / "paper.db")
        sig.append(f"П6 записей: сегодня {pdb.execute('select count(*) from trades where day=?', (day,)).fetchone()[0]}, "
                   f"всего {pdb.execute('select count(*) from trades').fetchone()[0]}")
    t += ["", f"Сигналы и журнал: {'; '.join(sig)}; карточек ЦИАН с просмотрами сегодня: {cards}."]
    t += ["", "**Аномалии и сбои** (каждую разобрать ниже, итог — урок в `LESSONS.md`):", ""]
    t += [f"- A{i}. {a}" for i, a in enumerate(anomalies, 1)] or ["- нет"]
    t += [f"<!-- /auto R-{day} -->"]
    block = "\n".join(t)

    text = LOG.read_text() if LOG.exists() else ("# Журнал прогонов\n\nОбязательный журнал: запись `R-<дата>` после каждого прогона "
                                                 "(собирается `run_report.py`), под ней — «Разбор» аномалий вручную со ссылками на "
                                                 "уроки `L-n` из `LESSONS.md`. На записи ссылаемся при любой проблеме.\n")
    m = re.search(rf"<!-- auto R-{day} -->.*?<!-- /auto R-{day} -->", text, re.S)
    if m:
        text = text[:m.start()] + block + text[m.end():]
    else:
        head, _, rest = text.partition("\n## ")
        entry = block + "\n\n**Разбор:**\n\n- " + ("ожидает разбора" if anomalies else "аномалий нет") + "\n"
        text = text.rstrip() + "\n\n" + entry if not rest else head.rstrip() + "\n\n" + entry + "\n## " + rest
    LOG.write_text(text)
    hist = json.loads((HERE / "runlog.json").read_text()) if (HERE / "runlog.json").exists() else {}
    hist[day] = {"anomalies": anomalies, "dev": dev, "cian": cian}
    (HERE / "runlog.json").write_text(json.dumps(hist, ensure_ascii=False, indent=1))
    return day, anomalies


if __name__ == "__main__":
    d, a = build()
    print(f"R-{d}: {len(a)} anomalies")
    for x in a:
        print(" -", x)
