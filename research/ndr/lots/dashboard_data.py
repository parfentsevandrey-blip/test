"""Data for the owner's dashboard «Пульт пилота перепродажи» (claude.ai artifact): one JSON file per dataset in dash/.
Facts (daily completeness, changes, lessons) come from lots.db, devcat.db, runlog.json and LESSONS.md; the work board,
the owner's open decisions and the plan are kept here by hand and updated with STATUS.md.
usage: python3 -I dashboard_data.py      -> dash/*.json
"""
import json
import re
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "dash"
MSK = timezone(timedelta(hours=3))
PILOT = json.loads((HERE / "pilot_zk.json").read_text())
ZK_NAME = {z: n for d in PILOT.values() for z, n in d.items()}
ZK_DEV = {z: dv for dv, d in PILOT.items() for z in d}
EXTRA = json.loads((HERE / "extra_zk.json").read_text()) if (HERE / "extra_zk.json").exists() else {}
PIK = {z for d in EXTRA.values() for z in d}

# status: done | work (идёт) | wait (ждёт данных или времени) | owner (ждёт вашего решения) | blocked | todo
TRACKS = [
    ("Сбор", "С1", "Выбытие из прайсов застройщиков", "work", "13 ЖК каждый день, 100% квартир с сайтов", "Первая недельная сводка 23–24.10; рост цены тех же квартир — через 4 недели"),
    ("Сбор", "С2", "Вторичка и переуступки на ЦИАН", "blocked", "19.10 в 11:28 ЦИАН ответил 403: пауза до 20.10 11:28. Причина разобрана (L49)", "20.10 с 11:35 — «режим посетителя»: сессия с куками, ≤ 80 запросов в час, перерывы"),
    ("Сбор", "С2·ПИК", "ПИК внутри МКАД на ЦИАН", "work", "14 ЖК, первый сбор 19.10: 99,2%", "Своя сводка по ПИК отдельно от пилота"),
    ("Сбор", "pik.ru", "Сайт ПИК", "blocked", "Qrator: 401 у трёх клиентов без JavaScript", "19.10 21:16 — одна попытка настоящим Chrome; при отказе ПИК остаётся на ЦИАН"),
    ("Сбор", "С3", "Просмотры и избранное", "wait", "300 карточек ЦИАН в день", "Первая карта спроса по типам — 23–24.10"),
    ("Сбор", "С4", "Модель ликвидности", "wait", "Признаки этажа, площади и отделки копятся", "Нужна история в несколько недель"),
    ("Отбор", "П1", "Отбор ЖК по порогам", "owner", "Пороги есть, условия переуступки не заполнены", "Нужен assignment_terms.csv — условия переуступки по застройщикам"),
    ("Отбор", "П2", "Дефицитные типы", "work", "Наценка по квартирам той же площади (L45); 7 форматов, которых нет у застройщика", "Недельный отчёт об изменениях"),
    ("Отбор", "П3", "Квартира для входа", "wait", "37 квартир ниже соседних, все на ручную проверку", "Отбор после первой недели истории"),
    ("Отбор", "П4", "Расчёт сделки", "work", "Издержки, налоги, порог 14–27%", "Цена выхода по снятым без снижения, когда их ≥ 5 на тип"),
    ("Отбор", "П5", "Выход", "todo", "Не начат", "Срок регистрации права в сценариях для сданных корпусов"),
    ("Отбор", "П6", "Проверка без денег", "wait", "Журнал с 16.10, 39 записей в день", "Первая сводка ≈ 30.10"),
    ("Движок", "Шаг 1", "Фильтр «только современные», пороги", "done", "Утверждено 17.10", "—"),
    ("Движок", "Шаг 2", "API вместо страниц (≤ 50 запросов на каталог)", "work", "5 из 7 застройщиков уложились; Донстрой — недельный список, ЛСР — 67 догрузок", "Фильтр проекта у Донстроя (≈99 порций); пробник ЛСР"),
    ("Движок", "Шаг 3", "Параллельные воркеры и инкремент ЦИАН", "todo", "После шага 2", "Затем расширение ЦИАН до 50 ЖК (вариант A)"),
    ("Движок", "Шаги 4–7", "Подключение новых ЖК и застройщиков", "todo", "Волна 1 — 8 проектов Донстроя", "Порядок волн — на ваше утверждение"),
    ("Качество", "QA", "Двойная проверка и журнал прогонов", "done", "Каждая аномалия разбирается в тот же день", "—"),
    ("Качество", "Политика", "Какой клиент куда ходит, паузы при блокировке", "done", "Ни одной блокировки сборщиков с 17.10", "—"),
]
DECISIONS = [
    ("D5", "2026-10-19", "ЦИАН после блокировки: «режим посетителя» с 20.10", "Подтвердить: ≤ 80 запросов в час с перерывами, 200 карточек вместо 300, ПИК через день. Без ответа включаю его", "С2, ПИК, С3"),
    ("D6", "2026-10-19", "Российский адрес для ЦИАН (внешняя машина или прокси)", "Сейчас запрещено правилом «только облако»: разрешить или нет", "Надёжность ЦИАН"),
    ("D1", "2026-10-18", "ПИК: Полар (медиана 397 тыс. ₽/м², порог 400) и 2-й Иртышский (класс «Бизнес/Комфорт», медиана 371)", "Оставить в сборе или исключить по правилу M5", "Сбор ПИК"),
    ("D2", "2026-10-17", "Новые источники: erzrf.ru и Telegram-каналы", "Разрешить и записать в NOTES — или нет", "Движок, шаги 4–7"),
    ("D3", "2026-10-17", "Порядок волн подключения новых ЖК", "Утвердить: волна 1 — 8 проектов Донстроя", "Движок, шаги 4–7"),
    ("D4", "2026-10-16", "Условия переуступки по застройщикам (assignment_terms.csv)", "Заполнить вам или запросить у застройщиков", "П1 отбор ЖК"),
]
PLAN = [
    ("2026-10-20", "ЦИАН после паузы, с 11:35: снимок дня и дочитка Острова (L47)", "Сбор"),
    ("2026-10-19", "pik.ru: одна попытка настоящим Chrome, 21:16", "Сбор"),
    ("2026-10-20", "Символ: если разрыв счётчика ЦИАН держится — предложить записать его как известный", "Качество"),
    ("2026-10-23", "Первая недельная сводка С1 и карта спроса С3", "Аналитика"),
    ("2026-10-25", "Недельный список цен Донстроя с фильтром проекта", "Движок"),
    ("2026-10-30", "П6: первая сводка проверки без денег", "Аналитика"),
    ("2026-11-14", "С1: рост цены тех же квартир за 4 недели", "Аналитика"),
]


def lessons():
    out = []
    for l in (HERE / "LESSONS.md").read_text().splitlines():
        m = re.match(r"^\|\s*(L\d+)\s*\|(.*)", l)
        if not m:
            continue
        cells = [c.strip() for c in m[2].split("|")]
        what = re.sub(r"[`*]", "", cells[0])
        d = re.search(r"\b(\d{1,2})\.(10|11|12)\b", what)
        day = f"2026-{d[2]}-{int(d[1]):02d}" if d else None
        fix = re.sub(r"[`*]", "", cells[2]) if len(cells) > 2 else ""
        out.append({"id": m[1], "n": int(m[1][1:]), "day": day, "problem": what[:220], "fix": fix[:220]})
    return out


def main():
    OUT.mkdir(exist_ok=True)
    dv, lt = sqlite3.connect(HERE / "devcat.db", timeout=60), sqlite3.connect(HERE / "lots.db", timeout=60)
    runlog = json.loads((HERE / "runlog.json").read_text()) if (HERE / "runlog.json").exists() else {}
    days = [r[0] for r in lt.execute("select distinct day from obs order by day")]
    pilot = list(ZK_NAME)
    q = lambda c, s, a=(): c.execute(s, a).fetchone()[0] or 0
    inl = lambda ids: ",".join("?" * len(ids))
    les = lessons()
    daily, zk_rows = [], []
    for i, day in enumerate(days):
        prev = days[i - 1] if i else None
        site = {z: q(dv, "select count(*) from seen where day=? and zk_id=?", (day, z)) for z in pilot}
        decl = {z: (runlog.get(day, {}).get("dev", {}).get(z) or {}).get("declared") or site[z] for z in pilot}
        cian = {z: q(lt, "select count(*) from obs where day=? and zk_id=?", (day, z)) for z in pilot}
        ctot = {z: q(lt, "select max(total) from pages where day=? and zk_id=?", (day, z)) for z in pilot}
        fin = lt.execute("select finished from runs where day=?", (day,)).fetchone()
        r = {"day": day, "site_collected": sum(site.values()), "site_declared": sum(decl.values()),
             "cian_collected": sum(cian.values()), "cian_total": sum(ctot.values()), "cian_finished": bool(fin and fin[0]),
             "pik_collected": q(lt, f"select count(*) from obs where day=? and zk_id in ({inl(PIK)})", (day, *PIK)) if PIK else 0,
             "pik_total": sum(q(lt, "select max(total) from pages where day=? and zk_id=?", (day, z)) for z in PIK),
             "anomalies": len(runlog.get(day, {}).get("anomalies") or []),
             "lessons_new": sum(1 for x in les if x["day"] == day)}
        if prev:
            a = {(s, l) for s, l in dv.execute("select source, lot_id from seen where day=?", (prev,))}
            b = {(s, l) for s, l in dv.execute("select source, lot_id from seen where day=?", (day,))}
            ch = dv.execute("""select b.price/a.price-1 from prices a join prices b on a.source=b.source and a.lot_id=b.lot_id
                               and a.origin=b.origin where a.day=? and b.day=? and a.price!=b.price""", (prev, day)).fetchall()
            r.update(c1_gone=len(a - b), c1_new=len(b - a), c1_price_up=sum(1 for (x,) in ch if x > 0), c1_price_down=sum(1 for (x,) in ch if x < 0))
            pa = {o for (o,) in lt.execute(f"select offer_id from obs where day=? and zk_id in ({inl(pilot)})", (prev, *pilot))}
            pb = {o for (o,) in lt.execute("select offer_id from obs where day=?", (day,))}
            gd = gr = gc = 0
            for o in pa - pb:
                dev = lt.execute("select (from_developer=1 or sale_type='fz214') from offers where offer_id=?", (o,)).fetchone()[0]
                if dev:
                    gd += 1
                    continue
                gr += 1
                ps = [p for (p,) in lt.execute("select price from obs where offer_id=? order by day", (o,))]
                card = lt.execute("select price_changes from cards where offer_id=? order by day desc limit 1", (o,)).fetchone()
                hist = [x["priceData"]["price"] for x in sorted(json.loads(card[0]) if card and card[0] else [], key=lambda x: x["changeTime"])]
                gc += any(y < x for x, y in zip(ps, ps[1:])) or any(y < x for x, y in zip(hist, hist[1:]))
            if r["cian_finished"]:                       # an unfinished ЦИАН day would count unread offers as exits
                r.update(c2_gone_dev=gd, c2_gone_resale=gr, c2_gone_resale_cut=gc)
        daily.append(r)
        if day == days[-1]:
            for z in pilot:
                zk_rows.append({"zk": ZK_NAME[z], "developer": ZK_DEV[z], "site_collected": site[z], "site_declared": decl[z],
                                "cian_collected": cian[z], "cian_total": ctot[z] or None})
    now = datetime.now(MSK).isoformat(timespec="minutes")
    data = {
        "daily": daily, "zk_today": zk_rows, "lessons": les,
        "tracks": [{"area": a, "item": i, "title": t, "status": s, "now": n, "next": x} for a, i, t, s, n, x in TRACKS],
        "decisions": [{"id": i, "since": d, "question": q_, "choice": c, "blocks": b} for i, d, q_, c, b in DECISIONS],
        "plan": [{"day": d, "what": w, "area": a} for d, w, a in PLAN if d >= datetime.now(MSK).strftime("%Y-%m-%d")],
    }
    for k, v in data.items():
        (OUT / f"{k}.json").write_text(json.dumps(v, ensure_ascii=False, indent=1))
    print(now, {k: len(v) for k, v in data.items()})


if __name__ == "__main__":
    main()
