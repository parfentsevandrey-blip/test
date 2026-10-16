"""PDF report «Суперпремиум: центр Москвы от 2 млн ₽/м²» from superprime.json."""
import importlib.util
import json
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("bw", HERE / "build_what_sells.py")
BW = importlib.util.module_from_spec(spec)
spec.loader.exec_module(BW)
R = BW.R
E, C, fi, fp, pct, short, conclusions, kpi, num, hbar = R.E, R.C, R.fmt_int, R.fmt_price, R.fmt_pct, R.short, R.conclusions, R.kpi, BW.num, BW.hbar
D = json.loads((HERE / "superprime.json").read_text())
QS = ["IV кв. 2025", "I кв. 2026", "II кв. 2026", "III кв. 2026"]


def mln(x):
    return "—" if x is None else num(x, 0) + " млн"


def bln(x):
    return "—" if x is None else num(x, 1) + " млрд"


def years(rem, sales):
    if not sales:
        return "продаж нет"
    y = rem / sales
    return "более 20 лет" if y > 20 else num(y, 1) + " г."


def mprice(x):
    return num(x / 1e6, 2) + " млн"


def proj_rows(ps, start=1):
    out = []
    for i, p in enumerate(ps, start):
        basis = "" if p["price_basis"] == "сделки с квартирами" else f"<div class='sub'>{E(p['price_basis'])}</div>"
        q = " / ".join(str(p["quarters"][k]) for k in QS)
        out.append(
            f"<tr><td class='n'>{i}</td><td class='l'><b>{E(short(p['name'], 32))}</b><div class='sub'>{E(p['dev'] or '')}</div></td>"
            f"<td class='l'>{E(p['district'])}</td><td class='n'><b>{mprice(p['price_m2'])}</b>{basis}</td>"
            f"<td class='n'>{fi(p['flats'])}<div class='sub'>продано {pct(p['sold_pct'])}</div></td><td class='n'><b>{fi(p['sales_12m'])}</b></td>"
            f"<td class='n'>{q}</td><td class='n'>{p['months_with_sales']} из 12</td><td class='n'>{num(p['deal_m2'])}{' м²' if p['deal_m2'] else ''}</td>"
            f"<td class='n'>{mln(p['ticket_mln'])}</td><td class='n'>{bln(p['money_bln']) if p['money_bln'] else '—'}</td>"
            f"<td class='n'>{fi(p['remaining'])}<div class='sub'>{years(p['remaining'], p['sales_12m'])}</div></td>"
            f"<td class='n'>{E(p['completion'][3:] if p['completion'] else '')}</td></tr>")
    return out


P_HEAD = ("<thead><tr><th>№</th><th class='l'>ЖК / застройщик</th><th class='l'>Район</th><th>Цена, ₽/м²</th><th>Квартир в проекте</th>"
          "<th>Продано за 12 мес.</th><th>По кварталам<div class='sub'>IV-25 / I / II / III-26</div></th><th>Месяцев с продажами</th>"
          "<th>Средняя сделка</th><th>Средний чек, ₽</th><th>Оборот, ₽</th><th>Остаток<div class='sub'>уйдёт при нынешнем темпе за</div></th><th>Сдача</th></tr></thead>")


def gtable(d, order, first):
    rows = []
    tot = sum(v["sales_12m"] for v in d.values()) or 1
    for k in order:
        if k not in d:
            continue
        v = d[k]
        q = " / ".join(str(v["quarters"][x]) for x in QS)
        rows.append(f"<tr><td class='l'><b>{E(k)}</b></td><td class='n'>{v['projects']}<span class='sub'> (с продажами {v['with_sales']})</span></td>"
                    f"<td class='n'><b>{fi(v['sales_12m'])}</b></td><td class='n'>{pct(100 * v['sales_12m'] / tot)}</td><td class='n'>{q}</td>"
                    f"<td class='n'>{bln(v['money_bln'])}</td><td class='n'>{mln(v['ticket_mln'])}</td><td class='n'>{num(v['deal_m2'])} м²</td>"
                    f"<td class='n'>{fi(v['remaining'])}</td><td class='n'>{years(v['remaining'], v['sales_12m'])}</td></tr>")
    return ("<table class='t compact'><thead><tr><th class='l'>" + E(first) + "</th><th>Проектов</th><th>Продано за 12 мес.</th><th>Доля</th>"
            "<th>По кварталам<div class='sub'>IV-25 / I / II / III-26</div></th><th>Оборот, ₽</th><th>Средний чек, ₽</th><th>Средняя сделка</th>"
            "<th>Непроданный остаток</th><th>Уйдёт при нынешнем темпе за</th></tr></thead><tbody>" + "".join(rows) + "</tbody></table>")


def build():
    t, near, ps = D["total"], D["near"], D["projects"]
    act = [p for p in ps if p["sales_12m"]]
    ns = [p for p in ps if not p["sales_12m"]]
    nic, fr = ps[0], next(p for p in ps if p["name"] == "Фрунзенский")
    luzh = D["near_projects"][0]
    bt, bp, bd, bs, bst = D["by_ticket"], D["by_price"], D["by_district"], D["by_size"], D["by_stage"]
    share_money = 100 * t["money_bln"] / D["market_money_bln"]
    q = t["quarters"]
    stock_avg = [p["stock_avg_m2"] for p in act if p["stock_avg_m2"]]
    stock_avg = sum(stock_avg) / len(stock_avg)
    chart_q = hbar([(k, q[k]) for k in QS], "Продажи сегмента по кварталам, квартир", fi, width=460)
    chart_p = hbar([(short(p["name"], 22), p["sales_12m"]) for p in act[:10]], "Проекты-лидеры: квартир продано за 12 месяцев", fi, width=460, color=C[1])
    ap_sales = sum(p["apart_sales_12m"] for p in ps)

    summary = [
        f"Суперпремиум — это крошечный рынок: {len(ps)} строящихся проекта дороже 2 млн ₽/м² внутри МКАД ({sum(1 for p in ps if p["okrug"] == "ЦАО")} из них — в ЦАО), {fi(t['sales_12m'])} квартир за год. Это {pct(share_money)} всех денег рынка новостроек внутри МКАД ({bln(t['money_bln'])} ₽), средний чек — {mln(t['ticket_mln'])} ₽.",
        f"Продажи падают: {q['IV кв. 2025']} квартир в IV кв. 2025 года, {q['III кв. 2026']} — в III кв. 2026-го. Непроданный остаток ({fi(t['remaining'])} квартир) при нынешнем темпе уйдёт примерно за {num(t['years_to_sell'], 0)} лет.",
        f"Лучше всего продаётся «Николь» (Б-Холдинг, Тверской, 2,5 млн ₽/м²): {nic['sales_12m']} квартир и продажи в {nic['months_with_sales']} месяцах из 12 — единственный по-настоящему стабильный проект. Второй — «Фрунзенский» ({fr['sales_12m']}), но его продажи затухают: 11 → 6 → 4 → 1 по кварталам.",
        f"Устойчивый бюджет — 300–500 млн ₽ за квартиру: продажи есть каждый квартал. Лоты дороже 500 млн ₽ в III кв. 2026 года почти встали ({bt['500 млн+']['quarters']['III кв. 2026']} сделка за квартал).",
        f"Покупатель берёт в среднем {num(t['deal_m2'], 0)} м², хотя средняя квартира в этих домах — около {num(stock_avg, 0)} м². Квартиры на 200+ м² — основной зависающий остаток.",
        f"Спрос живёт чуть ниже порога: один ЖК «Коллекция Лужники» (1,9 млн ₽/м², чек {mln(luzh['ticket_mln'])} ₽) продал {luzh['sales_12m']} квартир — почти столько же, сколько весь сегмент от 2 млн ₽/м².",
        f"Впереди {len(ns)} проекта без открытых продаж ({fi(sum(p['remaining'] for p in ns))} квартир по 2,1–3,6 млн ₽/м²) — ещё почти два года продаж всего сегмента сверху к нынешнему остатку.",
    ]
    proj_concl = [
        f"Стабильно продаются только «Николь» ({nic['months_with_sales']} месяцев с продажами из 12), Клубный дом PHANTOM (7 из 12, но с падением цены сделок), «Николь Коллекция» и Дом Соболева (продажи в каждом квартале, по 1–2 квартиры).",
        "Никитский-6 продал 7 квартир в IV кв. 2025 года и почти остановился (2, 1 и 0 в следующих кварталах); Русские Сезоны (5,4 млн ₽/м²) — 3 квартиры за год.",
        "Новые проекты стартуют медленно: Пыжевский, Узоры, Палашёвский 11, Резиденция Омега и проект «Русского капитала» в Хамовниках продали по 3–6 квартир за год при остатках 50–70 квартир.",
        "Где цена сделок на тех же корпусах посчитана, она ниже, чем год назад: Фрунзенский −24%, Николь Коллекция −28%, PHANTOM −17%. Сделок мало, поэтому цифры неточные, но направление одно — скидки.",
    ]
    grp_concl = [
        f"По цене: больше всего продаётся ступень 2,5–3,5 млн ₽/м² ({fi(bp['2,5–3,5 млн/м²']['sales_12m'])} квартир) — за счёт Николь и Фрунзенского. Дороже 3,5 млн ₽/м² — {fi(bp['3,5 млн/м² +']['sales_12m'])} квартир за год, остаток там уйдёт больше чем за 20 лет.",
        f"По бюджету: 150–300 млн ₽ — единственная ступень, где продажи к концу года растут ({' → '.join(str(bt['150–300 млн']['quarters'][k]) for k in QS)}); 300–500 млн — самая большая и ровная; 500 млн+ — затухает ({' → '.join(str(bt['500 млн+']['quarters'][k]) for k in QS)}).",
        "По месту: лучше всего — Тверской (Николь, Палашёвский) и Арбат; Хамовники дают больше всего денег, но остаток там уйдёт за 13 лет. Якиманка и Замоскворечье — почти без сделок.",
        "Клубные дома до 40 квартир не продаются быстрее больших: формат сам по себе не создаёт спрос, решают адрес и бюджет лота.",
        f"Почти готовые дома (сдача в течение года) распродают остаток вдвое быстрее, чем проекты со сдачей через 2+ года: {num(bst['сдача в течение года']['years_to_sell'], 1)} против {num(bst['через 2+ года']['years_to_sell'], 1)} лет.",
    ]
    near_rows = "".join(
        f"<tr><td class='l'><b>{E(short(p['name'], 34))}</b><div class='sub'>{E(p['dev'] or '')}</div></td><td class='l'>{E(p['district'])}</td>"
        f"<td class='n'>{mprice(p['price_m2'])}</td><td class='n'><b>{fi(p['sales_12m'])}</b></td><td class='n'>{' / '.join(str(p['quarters'][k]) for k in QS)}</td>"
        f"<td class='n'>{num(p['deal_m2'])}{' м²' if p['deal_m2'] else ''}</td><td class='n'>{mln(p['ticket_mln'])}</td><td class='n'>{fi(p['remaining'])}</td></tr>"
        for p in D["near_projects"] if p["flats"])
    rows = proj_rows(ps)

    toc = "".join(f"<li><span>{i:02d}</span>{E(n)}</li>" for i, n in enumerate(
        ["Главное", "Все проекты от 2 млн ₽/м²", "Цена, бюджет, место, формат, стадия", "Чуть ниже порога: 1,7–2 млн ₽/м²", "Методика и ограничения"], 1))
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Аналитический отчёт · 16 октября 2026</div>
    <h1>Суперпремиум: новостройки центра Москвы дороже 2 млн ₽ за м²</h1>
    <p class="lead">Все строящиеся проекты внутри МКАД с ценой сделок от 2 млн ₽/м²: что и как продаётся за 12 месяцев (октябрь 2025 — сентябрь 2026). Данные ЕИСЖС наш.дом.рф.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>{len(ps)}</b><span>проекта в сегменте</span></div>
    <div class="stat"><b>{fi(t['sales_12m'])}</b><span>квартир продано за год</span></div>
    <div class="stat"><b>{num(t['money_bln'], 1)} млрд ₽</b><span>оборот за год</span></div>
    <div class="stat"><b>{mln(t['ticket_mln'])} ₽</b><span>средний чек</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Главное</h2>
  <div class="kpis">
    {kpi("Продано за год", fi(t['sales_12m']), "квартир во всём сегменте")}
    {kpi("Средняя сделка", num(t['deal_m2'], 0) + " м²", f"по {mprice(t['price_m2'])} ₽/м²")}
    {kpi("Остаток", fi(t['remaining']), f"квартир — около {num(t['years_to_sell'], 0)} лет при нынешнем темпе")}
    {kpi("III кв. 2026", fi(q['III кв. 2026']), f"квартир против {q['IV кв. 2025']} год назад")}
  </div>
  {conclusions(summary, "Главное простым языком").replace('class="concl"', 'class="concl tight"')}
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Динамика и лидеры</h2>
  <div class="charts">{chart_q}{chart_p}</div>
  <p class="caption">Апартаменты (нежилые помещения) в этих проектах продаются ещё реже: {ap_sales} сделок за год, в основном во «Фрунзенском» — они не входят в цифры по квартирам.</p>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Все проекты от 2 млн ₽/м²: места 1–11</h2>
  <table class="t small">{P_HEAD}<tbody>{''.join(rows[:11])}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Все проекты от 2 млн ₽/м²: места 12–{len(ps)}</h2>
  <table class="t small">{P_HEAD}<tbody>{''.join(rows[11:])}</tbody></table>
  <p class="caption">Проекты без сделок за 12 месяцев ещё не открыли продажи по ДДУ; их цена — заявленная застройщиком средняя цена.</p>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2 · выводы</div>
  <h2>Проекты: выводы</h2>
  {conclusions(proj_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Цена и бюджет сделки</h2>
  <h3>Цена за м²</h3>{gtable(bp, ["2–2,5 млн/м²", "2,5–3,5 млн/м²", "3,5 млн/м² +"], "Цена")}
  <h3>Средний чек проекта</h3>{gtable(bt, ["150–300 млн", "300–500 млн", "500 млн+", "нет сделок за 12 мес"], "Чек за квартиру")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Место</h2>
  <h3>Район</h3>{gtable(bd, sorted(bd, key=lambda k: -bd[k]['sales_12m']), "Район")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Формат и стадия</h2>
  <h3>Размер проекта</h3>{gtable(bs, ["до 40 квартир (клубный дом)", "40–80", "80–150", "150+"], "Квартир в проекте")}
  <h3>Стадия стройки</h3>{gtable(bst, ["сдача в течение года", "через 1–2 года", "через 2+ года"], "До сдачи")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3 · выводы</div>
  <h2>Цена, бюджет, место, формат: выводы</h2>
  {conclusions(grp_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>Чуть ниже порога: ЦАО по 1,7–2 млн ₽/м²</h2>
  <p class="lead">За год здесь продано {fi(near['sales_12m'])} квартир — больше, чем во всём сегменте от 2 млн ₽/м² ({fi(t['sales_12m'])}), при среднем чеке {mln(near['ticket_mln'])} ₽.</p>
  <table class="t compact"><thead><tr><th class="l">ЖК / застройщик</th><th class="l">Район</th><th>Цена, ₽/м²</th><th>Продано за 12 мес.</th>
  <th>По кварталам<div class="sub">IV-25 / I / II / III-26</div></th><th>Средняя сделка</th><th>Средний чек, ₽</th><th>Остаток</th></tr></thead><tbody>{near_rows}</tbody></table>
  {conclusions([f"Коллекция Лужники продаёт {luzh['sales_12m']} квартир в год при цене 1,9 млн ₽/м² и чеке около {mln(luzh['ticket_mln'])} ₽: это самый ликвидный дорогой строящийся проект внутри МКАД. Похоже, покупатель охотнее берёт квартиру за 200 млн ₽ в большом проекте с инфраструктурой, чем лот за 500+ млн ₽ в клубном доме.", "Граница 2 млн ₽/м² — заметный психологический порог: ниже него спрос в разы плотнее."])}
</section>
<section class="page">
  <div class="eyebrow">Раздел 5</div>
  <h2>Методика и ограничения</h2>
  <ul class="plain">
    <li><b>Сегмент</b> — проекты (корпуса одного застройщика с одним названием) внутри МКАД, где средняя цена сделок с квартирами за 12 месяцев — от 2 млн ₽/м². Если квартир не продавали — цена сделок с апартаментами, если сделок нет совсем — заявленная средняя цена застройщика.</li>
    <li><b>Источник</b> — ЕИСЖС наш.дом.рф: зарегистрированные ДДУ по месяцам (квартиры, проданная площадь, средняя цена), счётчик проданных квартир, состав квартир. Октябрь 2025 — сентябрь 2026, собрано 15–16 октября 2026.</li>
    <li><b>Чего в данных нет:</b> продаж в уже сданных домах (там продают по ДКП), сделок до регистрации ДДУ и любых продаж вне ДДУ. В суперпремиуме заметная часть покупок происходит после ввода дома, поэтому реальный спрос в сегменте выше, чем видно здесь, — но динамика и сравнение проектов между собой корректны.</li>
    <li><b>Малые числа:</b> у большинства проектов 1–10 сделок за год. Одна сделка заметно двигает средний чек и цену, поэтому изменение цены приводится только как направление.</li>
  </ul>
</section>"""
    css = R.CSS + ".charts > div h3 { margin-top: 0; }\ntable.t.small td { padding: .7mm 1.3mm; } table.t.small th { padding: 1.2mm 1.3mm; }\n"
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Суперпремиум центра Москвы</title><style>{css}</style></head><body>{body}</body></html>'
    (HERE / "superprime.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "superprime.html").as_uri())
        pg.wait_for_timeout(800)
        pg.pdf(path=str(HERE / "superprime.pdf"), format="A4", landscape=True, print_background=True, display_header_footer=True,
               header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Суперпремиум · центр Москвы от 2 млн ₽/м²</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               '<span>Суперпремиум внутри МКАД · данные ЕИСЖС наш.дом.рф, 16.10.2026</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    shutil.copy2(HERE / "superprime.pdf", HERE.parent / "Суперпремиум_центр_Москвы_2026-10.pdf")
    print("ok", (HERE / "superprime.pdf").stat().st_size)


if __name__ == "__main__":
    build()
