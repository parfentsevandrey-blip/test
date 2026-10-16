"""Pilot report «Где явный спрос на конкретные лоты» (HTML -> PDF) from model.json + lots.db."""
import importlib.util
import json
import sqlite3
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("br", HERE.parent / "report" / "build_report.py")
R = importlib.util.module_from_spec(spec)
spec.loader.exec_module(R)
E, fi, fp, pct, conclusions, kpi = R.E, R.fmt_int, R.fmt_price, R.fmt_pct, R.conclusions, R.kpi
M = json.loads((HERE / "model.json").read_text())
SELLER = {"developer": "застройщик", "assignment": "переуступка", "resale": "вторичка"}


def num(x, d=1):
    return "—" if x is None else (f"{x:.{d}f}" if d else f"{round(x)}").replace(".", ",")


def mln(x):
    return "—" if not x else num(x / 1e6, 1) + " млн"


def coverage_table(db, day):
    pilot = json.loads((HERE / "pilot_zk.json").read_text())
    rows = []
    for dev, zks in pilot.items():
        for zk, name in zks.items():
            site = db.execute("select max(total) from pages where day=? and zk_id=? and url like '%cat.php%'", (day, zk)).fetchone()[0]
            got = dict(db.execute("""select case when o.from_developer=1 or o.sale_type='fz214' then 'developer' when o.sale_type='dupt'
                                     then 'assignment' else 'resale' end, count(*) from obs b join offers o using(offer_id)
                                     where b.day=? and b.zk_id=? group by 1""", (day, zk)).fetchall())
            builder = (db.execute("select builder from zk_meta where zk_id=?", (zk,)).fetchone() or [None])[0]
            n = sum(got.values())
            rows.append(f"<tr><td class='l'><b>{E(name)}</b><div class='sub'>{E(builder or dev)}</div></td><td class='n'>{fi(site)}</td>"
                        f"<td class='n'><b>{fi(n)}</b></td><td class='n'>{pct(100 * n / site) if site else '—'}</td>"
                        f"<td class='n'>{fi(got.get('developer', 0))}</td><td class='n'>{fi(got.get('assignment', 0))}</td><td class='n'>{fi(got.get('resale', 0))}</td></tr>")
    return ("<table class='t compact'><thead><tr><th class='l'>ЖК / застройщик</th><th>Объявлений на ЦИАН</th><th>Собрано</th><th>Охват</th>"
            "<th>От застройщика</th><th>Переуступки</th><th>Вторичка</th></tr></thead><tbody>" + "".join(rows) + "</tbody></table>")


def signals_table(sig):
    rows = "".join(
        f"<tr><td class='l'><b>{E(s['zk'])}</b></td><td>{E(s['type'])}</td><td class='n'>{fi(s['dev_listed'])}</td><td class='n'>{fp(s['dev_price_m2'])}</td>"
        f"<td class='n'>{fi(s['resale_building'])}<div class='sub'>{fp(s['resale_building_m2'])}</div></td><td class='n'><b>{pct(s['premium_pct'], True)}</b></td>"
        f"<td class='n'>{fi(s['resale_ready'])}<div class='sub'>{fp(s['resale_ready_m2'])}</div></td><td class='n'><b>{pct(s['ready_uplift_pct'], True)}</b></td>"
        f"<td class='n'>{num(s['resale_views_day'])}</td>"
        f"<td class='n'>{num(s['resale_median_age_days'], 0)} дн.</td><td class='l'>{E(s['flags'] or '—')}</td></tr>" for s in sig)
    return ("<table class='t small'><thead><tr><th class='l'>ЖК</th><th>Тип</th><th>У застройщика</th><th>Цена застройщика, ₽/м²</th>"
            "<th>Строящиеся: переуступки и перепродажа<div class='sub'>шт. и ₽/м²</div></th><th>Разница со застройщиком</th>"
            "<th>Готовые: вторичка<div class='sub'>шт. и ₽/м²</div></th><th>Готовое дороже на</th><th>Просмотров в день</th><th>Срок экспозиции</th>"
            "<th class='l'>Признаки</th></tr></thead><tbody>" + rows + "</tbody></table>")


def cand_table(cs):
    rows = "".join(
        f"<tr><td class='l'><b><a href='{c['url']}'>{c['offer_id']}</a></b><div class='sub'>{E(c['zk'])}</div></td><td>{E(c['type'])}</td>"
        f"<td>{E(SELLER[c['seller']])}</td><td class='l'>{E(c['house'] or '—')}<div class='sub'>сдача {E(c['finish'] or '—')}</div></td>"
        f"<td class='n'>{c['floor']}/{c['floors']}</td><td class='n'>{num(c['area'])} м²</td><td class='n'>{mln(c['price'])}</td>"
        f"<td class='n'>{fp(c['price_m2'])}</td><td class='n'>{fp(c['exit_m2'])}<div class='sub'>по {c['comps']} объявл.</div></td>"
        f"<td class='n'><b>{pct(100 * c['gross_spread'])}</b></td><td class='l'>{E(c['check'] or '—')}</td></tr>" for c in cs)
    return ("<table class='t small'><thead><tr><th class='l'>Объявление / ЖК</th><th>Тип</th><th>Продавец</th><th class='l'>Корпус</th>"
            "<th>Этаж</th><th>Площадь</th><th>Цена</th><th>₽/м²</th><th>Медиана сопоставимых, ₽/м²</th><th>Дешевле на</th><th class='l'>Что проверить</th>"
            "</tr></thead><tbody>" + rows + "</tbody></table>")


def build(summary_points, extra_pages=""):
    db = sqlite3.connect(HERE / "lots.db")
    day = M["day"]
    sig = M["signals"]
    good = [s for s in sig if s["score"] >= 1 and s["type"] != "своб."][:13]
    cands = M["candidates"][:30]
    d = M["deal_params"]
    n_obs = db.execute("select count(*) from obs where day=?", (day,)).fetchone()[0]
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Пилот · {E(day)}</div>
    <h1>Где явный спрос на конкретные лоты</h1>
    <p class="lead">13 ЖК бизнес- и премиум-класса пяти крупнейших застройщиков у центра Москвы: все объявления ЦИАН, сигналы дефицита по типам квартир и кандидаты на покупку для перепродажи за 6 месяцев.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>{fi(n_obs)}</b><span>лотов в снимке</span></div>
    <div class="stat"><b>13</b><span>ЖК, 5 застройщиков</span></div>
    <div class="stat"><b>{len(good)}</b><span>типов квартир с сигналами спроса</span></div>
    <div class="stat"><b>{len(M['candidates'])}</b><span>кандидатов на покупку</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Главное</div>
  <h2>Первый снимок: что видно уже сейчас</h2>
  {conclusions(summary_points, "Главное простым языком")}
</section>
<section class="page">
  <div class="eyebrow">Охват</div>
  <h2>Что собрано</h2>
  <p class="lead">Все объявления каждого ЖК на ЦИАН в день снимка: от застройщика, переуступки и вторичка.</p>
  {coverage_table(db, day)}
</section>
<section class="page">
  <div class="eyebrow">Сигналы</div>
  <h2>Типы квартир с признаками спроса</h2>
  <p class="lead">Строящиеся корпуса сравниваются с ценой застройщика отдельно от готовых: «разница» показывает, можно ли продать переуступкой дороже покупки; «готовое дороже» — сколько добавляет сдача дома.</p>
  {signals_table(good)}
</section>
<section class="page">
  <div class="eyebrow">Кандидаты</div>
  <h2>Объявления сильно ниже рынка: список для ручной проверки</h2>
  <p class="lead">Покупка по цене объявления, продажа по медиане сопоставимых объявлений перепродажи минус {pct(100 * d['bargain'], False)} на торг; в расчёте согласие застройщика на переуступку {pct(100 * d['assignment_fee'])}, регистрация, стоимость денег {pct(100 * d['capital_cost_year'])} годовых, НДС с маржи и налог на прибыль {pct(100 * d['profit_tax'])}. Порог — {pct(100 * d['min_roi_6m'])} чистыми за 6 месяцев.</p>
  {cand_table(cands)}
</section>
{extra_pages}
<section class="page">
  <div class="eyebrow">Методика</div>
  <h2>Как это работает и что дальше</h2>
  <ul class="plain">
    <li><b>Сбор</b> — раз в день все объявления 13 ЖК на ЦИАН (страницы поиска по ЖК, сортировка по цене), карточки ЖК (остаток застройщика по типам и корпусам) и карточки объявлений (просмотры, история цены). Темп — как у человека, без решения капч.</li>
    <li><b>Тип лота</b> — ЖК × комнатность. Цена выхода считается по объявлениям перепродажи того же типа, той же готовности дома и близкой площади (±20%), если их не меньше 5.</li>
    <li><b>Чего пока нет</b>: выбытия объявлений (продано или снято) — оно появится со второго снимка; через 2–3 недели станет видно, какие типы реально уходят быстрее 6 месяцев. Просмотры есть пока у части объявлений.</li>
    <li><b>Проверить с бухгалтером</b>: ставку НДС с маржи при переуступке ({num(100 * d['vat_on_margin'], 1)}% маржи) и налог на прибыль; плату за согласие на переуступку уточнить у каждого застройщика.</li>
  </ul>
</section>"""
    css = R.CSS + "table.t.small td { padding: .6mm 1.3mm; } table.t.small th { padding: 1.2mm 1.3mm; } a { color: #1c2541; }\n"
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Пилот: спрос на лоты</title><style>{css}</style></head><body>{body}</body></html>'
    (HERE / "pilot_report.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "pilot_report.html").as_uri())
        pg.wait_for_timeout(500)
        pg.pdf(path=str(HERE / "pilot_report.pdf"), format="A4", landscape=True, print_background=True, display_header_footer=True,
               header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Пилот перепродажи · спрос на лоты</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               f'<span>Данные ЦИАН, снимок {day}</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    return HERE / "pilot_report.pdf"


if __name__ == "__main__":
    print(build(["Черновик: выводы появятся после полного снимка."]))
