"""PDF report «Элитные новостройки внутри МКАД: что продаётся лучше всего» from elite.json."""
import importlib.util
import json
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


BW = load("bw", HERE / "build_what_sells.py")
R = BW.R
E, C, fi, fp, pct, short, conclusions, kpi, num, hbar = R.E, R.C, R.fmt_int, R.fmt_price, R.fmt_pct, R.short, R.conclusions, R.kpi, BW.num, BW.hbar
months_to_sell = BW.months_to_sell
D = json.loads((HERE / "elite.json").read_text())
ALL = json.loads((HERE / "what_sells.json").read_text())


def mln(x):
    return "—" if x is None else num(x, 0 if x >= 100 else 1) + " млн"


def bln(x):
    return "—" if x is None else num(x, 1) + " млрд"


def etable(d, order, first, idle=True, compact=False):
    tot = sum(v["sum"] for v in d.values())
    rows = []
    for k in order:
        if k not in d:
            continue
        v = d[k]
        rows.append(
            f"<tr><td class='l'><b>{E(str(k))}</b></td><td class='n'>{fi(v['buildings'])}"
            + (f" <span class='sub'>+{v['idle_buildings']} без сделок</span>" if idle and v.get("idle_buildings") else "") + "</td>"
            f"<td class='n'>{fi(v['sum'])}</td><td class='n'>{pct(100 * v['sum'] / tot)}</td><td class='n'>{num(v['p25'], 0)}</td>"
            f"<td class='n'>{num(v['cv'], 2)}</td><td class='n'>{pct(v['absorb_pct'])}"
            + ("" if compact else f"<div class='sub'>остаток за {months_to_sell(v['absorb_pct'])}</div>") + "</td>"
            f"<td class='n'>{fp(v['price_m2'])}</td><td class='n'>{pct(v['price_chg_pct'], sign=True)}</td>"
            f"<td class='n'>{num(v['deal_m2'])} м²</td><td class='n'>{mln(v['ticket_mln'])}</td><td class='n'>{bln(v['money_bln'])}</td></tr>")
    return (f"<table class='t compact{' dense' if compact else ''}'><thead><tr><th class='l'>{E(first)}</th><th>Корпусов с продажами</th>"
            "<th>Продано за 12 мес.</th><th>Доля элиты</th><th>Стабильный темп<div class='sub'>кв./мес., 9 мес. из 12</div></th>"
            "<th>Разброс</th><th>Скорость<div class='sub'>доля остатка в месяц</div></th><th>Цена сделок, ₽/м²</th>"
            "<th>Цена за год<div class='sub'>те же корпуса</div></th><th>Средняя площадь</th><th>Средний чек, ₽</th><th>Оборот за год, ₽</th></tr></thead><tbody>"
            + "".join(rows) + "</tbody></table>")


ZK_HEAD = ("<thead><tr><th>№</th><th class='l'>ЖК / застройщик</th><th class='l'>Район</th><th>Км от центра</th>"
           "<th>Стабильный темп<div class='sub'>кв./мес., 9 мес. из 12</div></th><th>Обычный месяц</th><th>Разброс</th>"
           "<th>Продано за 12 мес.</th><th>Распродано</th><th>Цена сделок, ₽/м²</th><th>Цена за год</th><th>Средний чек, ₽</th></tr></thead>")


def zk_rows(zs, start=1, sell=False):
    out = []
    for i, z in enumerate(zs, start):
        extra = (f"<div class='sub'>остаток уйдёт за {months_to_sell(z['absorb_pct'])}</div>" if sell else "")
        out.append(f"<tr><td class='n'>{i}</td><td class='l'><b>{E(short(z['name'], 34))}</b><div class='sub'>{E(z['dev'] or '')}</div></td>"
                   f"<td class='l'>{E(z['district'] or '—')}<div class='sub'>{E(z['okrug'])}</div></td><td class='n'>{num(z['km'])}</td>"
                   f"<td class='n'><b>{num(z['p25'], 0)}</b></td><td class='n'>{num(z['median'], 0)}</td><td class='n'>{num(z['cv'], 2)}</td>"
                   f"<td class='n'>{fi(z['sum'])}</td><td class='n'>{pct(z['sold_pct'])}<div class='sub'>осталось {fi(z['remaining'])}</div>{extra}</td>"
                   f"<td class='n'>{fp(z['price_m2'])}</td><td class='n'>{pct(z['price_chg_pct'], sign=True)}</td><td class='n'>{mln(z['ticket_mln'])}</td></tr>")
    return "".join(out)


def build():
    mk, pb, tb, ok, dist, km, st = D["market"], D["price_bands"], D["ticket_bands"], D["okrugs"], D["districts"], D["distance"], D["stage"]
    ms = D["money_share"]["Элитный"]
    L = [z for z in D["complexes"] if z["long_run"]]
    tot = mk["sum"]
    P_ORDER = ["до 700 тыс/м²", "700 тыс – 1 млн", "1–1,5 млн", "1,5–2,5 млн", "2,5 млн+"]
    T_ORDER = ["до 40 млн", "40–70 млн", "70–120 млн", "120–250 млн", "250 млн+"]
    real_elite = sum(pb[k]["sum"] for k in P_ORDER[2:] if k in pb)
    entry = pb["до 700 тыс/м²"]
    top = sum(pb[k]["monthly"][i] for k in P_ORDER[2:] for i in range(12) if k in pb)
    series = [("до 700 тыс/м²", pb["до 700 тыс/м²"]["monthly"], C[0]), ("700 тыс – 1 млн", pb["700 тыс – 1 млн"]["monthly"], C[1]),
              ("от 1 млн/м²", [sum(pb[k]["monthly"][i] for k in P_ORDER[2:] if k in pb) for i in range(12)], C[2])]
    chart_m = R.line_chart(series, "Продажи элитных квартир по месяцам и цене сделки, квартир", lambda v: fi(v))
    chart_t = R.line_chart([("Средний чек", D["ticket_by_month_mln"], C[3])], "Средний чек элитной сделки, млн ₽", lambda v: f"{v:.0f}", ymin0=False)
    laggards = sorted([z for z in L if z["remaining"] >= 150], key=lambda z: z["absorb_pct"] or 0)[:10]
    idle_ns = [i for i in D["idle"] if i["not_started"]]
    idle_open = [i for i in D["idle"] if not i["not_started"]]
    dist_ok = [k for k, v in dist.items() if v["buildings"] >= 3]
    by_vol = sorted(dist, key=lambda k: -dist[k]["sum"])[:12]
    by_money = sorted(dist, key=lambda k: -dist[k]["money_bln"])[:12]
    hm, dn, pr, ha = dist["Хорошёво-Мнёвники"], dist["Даниловский"], dist["Пресненский"], dist["Хамовники"]
    mix, sm = D["mix"], D["small_share"]

    summary = [
        f"Элитный класс — это {pct(ms['flats_pct'])} проданных квартир внутри МКАД, но {pct(ms['money_pct'])} всех денег: {bln(mk['money_bln'])} ₽ за год при среднем чеке {mln(mk['ticket_mln'])} ₽.",
        f"«Элитный» — класс по документам застройщика. Больше половины элитных продаж ({pct(100 * entry['sum'] / tot)}) — это «входная элита» дешевле 700 тыс. ₽/м²: Sydney City, Дрим Рива, Преображенская площадь, Эра. Именно она продаётся стабильно.",
        f"Настоящая дорогая элита (от 1 млн ₽/м²) — всего {fi(real_elite)} квартир за год на всю Москву внутри МКАД ({pct(100 * real_elite / tot)} элитных продаж); остаток там уходит за 5–9 лет.",
        f"Лучший бюджет по устойчивости — 70–120 млн ₽ за квартиру: самая высокая скорость ({pct(tb['70–120 млн']['absorb_pct'])} остатка в месяц) и самые ровные продажи (разброс {num(tb['70–120 млн']['cv'], 2)}). Лоты дороже 250 млн продаются по 1–2 в месяц на весь город.",
        f"Где продаётся: по числу квартир — Хорошёво-Мнёвники ({fi(hm['sum'])}) и Даниловский ({fi(dn['sum'])}); по деньгам — ЦАО ({bln(ok['ЦАО']['money_bln'])} ₽, больше любого другого округа), хотя квартир там продаётся мало.",
        f"Покупатель элиты берёт квартиры меньше, чем строят: в среднем {num(mix['deal_avg_m2'])} м² при средней квартире в доме {num(mix['stock_avg_m2'])} м². Большие лоты — главный зависающий остаток.",
        f"Цены за год: входная элита {pct(entry['price_chg_pct'], True)}, 1–1,5 млн/м² {pct(pb['1–1,5 млн']['price_chg_pct'], True)}, самый верх (2,5 млн+) {pct(pb['2,5 млн+']['price_chg_pct'], True)} — наверху цены сделок снижаются.",
    ]
    month_concl = [
        f"Пик — декабрь 2025 года ({fi(mk['monthly'][2])} квартир), провал — январь 2026-го ({fi(mk['monthly'][3])}). С весны рынок держится на уровне 400–600 сделок в месяц, сентябрь ({fi(mk['monthly'][-1])}) — лучший месяц 2026 года.",
        "Декабрьский всплеск в основном дала входная и средняя элита (до 1 млн ₽/м²) — там, где чаще покупают с ипотекой. Дорогая элита от 1 млн ₽/м² в декабре тоже выросла (135 квартир), но в остальные месяцы держится ровно: 50–75 квартир в месяц на всю Москву внутри МКАД.",
        f"Средний чек в 2026 году вырос: с {mln(D['ticket_by_month_mln'][0])} в октябре 2025-го до {mln(max(D['ticket_by_month_mln']))} в пиковые месяцы. Ипотечных покупателей стало меньше, и в продажах выросла доля дорогих лотов.",
    ]
    price_concl = [
        f"Входная элита дешевле 700 тыс. ₽/м² — главный двигатель: {fi(entry['sum'])} квартир за год, стабильный темп {num(entry['p25'], 0)} в месяц, цены за год {pct(entry['price_chg_pct'], True)}.",
        f"Ступень 1–1,5 млн ₽/м² — самая слабая: {pct(pb['1–1,5 млн']['absorb_pct'])} остатка в месяц, то есть остаток уйдёт за {months_to_sell(pb['1–1,5 млн']['absorb_pct'])}. Это проекты, которые уже не «входные», но ещё не уникальные.",
        f"Самый верх (от 2,5 млн ₽/м², чек около {mln(pb['2,5 млн+']['ticket_mln'])}) — {fi(pb['2,5 млн+']['sum'])} квартир за год, а цены сделок на тех же корпусах на {pct(-pb['2,5 млн+']['price_chg_pct'])} ниже, чем год назад. Сделок мало, поэтому цифра неточная, но направление — скидки.",
        f"По бюджету лучше всего держится диапазон 70–120 млн ₽: {fi(tb['70–120 млн']['sum'])} квартир за год, остаток уходит быстрее всех ({pct(tb['70–120 млн']['absorb_pct'])} в месяц), месяцы почти не скачут.",
    ]
    zk_concl = [
        f"Стабильных лидеров в элите мало: ни один ЖК не продаёт 20+ квартир в месяц в 9 месяцах из 12. Лучший стабильный темп — {num(L[0]['p25'], 0)} квартир в месяц ({short(L[0]['name'], 30)}).",
        "Большая часть верхушки рейтинга — входная элита по 500–670 тыс. ₽/м² с чеком 30–45 млн ₽. Исключения — Ривер Парк Кутузовский (890 тыс. ₽/м²), Остров (806 тыс. ₽/м², чек 91 млн ₽) и High Life (756 тыс. ₽/м²): они продают стабильно и в более дорогом сегменте.",
        f"Самые ровные продажи — у Острова (разброс {num(next((z['cv'] for z in L if 'ОСТРОВ' in (z['name'] or '')), 0), 2)}), Родины Парк, Sydney City и Дрим Рива.",
        f"Зависшие проекты — большие остатки при почти нулевой скорости: {', '.join(short(z['name'], 22) for z in laggards[:5])}. При нынешнем темпе их остатки будут распродаваться десятилетиями.",
    ]
    geo_concl = [
        f"По числу квартир лидируют Хорошёво-Мнёвники ({fi(hm['sum'])}, ровно: разброс {num(hm['cv'], 2)}) и Даниловский ({fi(dn['sum'])}). Это районы с крупными проектами входной элиты у воды и парков.",
        f"Деньги элиты — в центре: на ЦАО приходится {bln(ok['ЦАО']['money_bln'])} ₽ из {bln(mk['money_bln'])} ({pct(100 * ok['ЦАО']['money_bln'] / mk['money_bln'])}) при {pct(100 * ok['ЦАО']['sum'] / tot)} квартир. Хамовники продали всего {fi(ha['sum'])} квартир, но на {bln(ha['money_bln'])} ₽.",
        f"Пресненский (Сити) продаёт рывками (разброс {num(pr['cv'], 2)}) и медленно ({pct(pr['absorb_pct'])} остатка в месяц): башен с большим предложением больше, чем покупателей.",
        "Внутри Садового кольца элитные квартиры продаются хуже всего: Тверской, Мещанский, Таганский, Арбат и Якиманка — единицы сделок в месяц на весь район.",
    ]
    lot_concl = [
        f"В элитных домах строят в среднем {num(mix['stock_avg_m2'])} м² на квартиру, а продают {num(mix['deal_avg_m2'])} м²: покупатель берёт меньший лот, чем предлагают. 3-комнатные и 4+ (треть всех элитных квартир) — главный риск зависших остатков.",
        f"Но дома без небольших квартир не обречены: дома, где почти нет студий и 1-комнатных, продаются со скоростью {pct(sm['мало малогабаритных']['absorb_pct'])} в месяц, а с большой долей малогабаритных — {pct(sm['много малогабаритных']['absorb_pct'])}. Разница небольшая: в элите важнее цена за м² и место, чем нарезка.",
        f"Почти готовые дома продаются в 4 раза быстрее котлована: {pct(st['сдача ≤ 6 мес']['absorb_pct'])} остатка в месяц против {pct(st['36+ мес']['absorb_pct'])} у проектов со сдачей через 3+ года.",
        f"Впереди большое новое предложение: {len(idle_ns)} элитных корпусов ({fi(sum(i['flats'] for i in idle_ns))} квартир) уже зарегистрированы, но продажи по ДДУ ещё не открыты, крупнейшие — {', '.join(short(i['name'], 26) for i in idle_ns[:3])}.",
    ]

    toc = "".join(f"<li><span>{i:02d}</span>{E(n)}</li>" for i, n in enumerate(
        ["Главное", "Элита по месяцам", "Ценовые ступени и бюджет сделки", "20 самых стабильных элитных ЖК", "Зависшие проекты",
         "Округа и районы", "Лоты, стадия стройки, новое предложение", "Методика"], 1))
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Аналитический отчёт · 16 октября 2026</div>
    <h1>Элитные новостройки внутри МКАД: что продаётся лучше всего</h1>
    <p class="lead">ЖК, районы, цены, бюджеты и лоты элитного класса со стабильно высокими продажами за 12 месяцев (октябрь 2025 — сентябрь 2026). Данные ЕИСЖС наш.дом.рф.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>{fi(tot)}</b><span>элитных квартир продано за год</span></div>
    <div class="stat"><b>{num(mk['money_bln'], 0)} млрд ₽</b><span>оборот — {pct(ms['money_pct'])} денег рынка</span></div>
    <div class="stat"><b>{num(mk['ticket_mln'], 1)} млн ₽</b><span>средний чек</span></div>
    <div class="stat"><b>{len(D['complexes'])}</b><span>элитных ЖК с продажами</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Содержание</div>
  <h2>Что в отчёте</h2>
  <ol class="toc">{toc}</ol>
  <h3>Как читать показатели</h3>
  <ul class="plain">
    <li><b>Стабильный темп</b> — сколько квартир ЖК продавал как минимум в 9 месяцах из 12; один удачный месяц его не поднимает.</li>
    <li><b>Разброс</b> — насколько продажи скачут: 0,3–0,4 — ровно, 1 и выше — рывками.</li>
    <li><b>Скорость</b> — доля непроданных квартир, которая уходит за месяц. 2% в месяц — весь остаток уйдёт примерно за 4 года.</li>
    <li><b>Средний чек</b> — сколько в среднем стоила одна проданная квартира (проданные м² × цена ÷ число квартир).</li>
    <li><b>Элитный класс</b> — так, как его указал застройщик в проектной декларации. Поэтому внутри класса мы отдельно смотрим цену за м².</li>
  </ul>
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Главное</h2>
  <div class="kpis">
    {kpi("Оборот за год", num(mk['money_bln'], 0) + " млрд ₽", f"{pct(ms['money_pct'])} денег при {pct(ms['flats_pct'])} квартир")}
    {kpi("Входная элита", pct(100 * entry['sum'] / tot), "продаж — дешевле 700 тыс. ₽/м²")}
    {kpi("Скорость", pct(mk['absorb_pct']), f"остатка в месяц — весь за {months_to_sell(mk['absorb_pct'])}")}
    {kpi("Средняя сделка", num(mix['deal_avg_m2']) + " м²", f"при средней квартире {num(mix['stock_avg_m2'])} м²")}
  </div>
  {conclusions(summary, "Главное простым языком").replace('class="concl"', 'class="concl tight"')}
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Элита по месяцам</h2>
  <div class="charts">{chart_m}{chart_t}</div>
  {conclusions(month_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Ценовые ступени и бюджет сделки</h2>
  <h3>Цена сделки за м²</h3>{etable(pb, P_ORDER, "Цена, ₽/м²", idle=False)}
  <h3>Средний чек корпуса</h3>{etable(tb, T_ORDER, "Чек за квартиру", idle=False)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3 · выводы</div>
  <h2>Цена и бюджет: выводы</h2>
  {conclusions(price_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>20 самых стабильных элитных ЖК</h2>
  <p class="lead">Элитные корпуса, которые продавались все 12 месяцев; отсортированы по стабильному темпу.</p>
  <table class="t small">{ZK_HEAD}<tbody>{zk_rows(L[:10])}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>20 самых стабильных элитных ЖК: места 11–20</h2>
  <table class="t small">{ZK_HEAD}<tbody>{zk_rows(L[10:20], 11)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 4 · выводы</div>
  <h2>Элитные ЖК: выводы</h2>
  {conclusions(zk_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 5</div>
  <h2>Зависшие проекты</h2>
  <p class="lead">Элитные ЖК в продаже весь год, с остатком от 150 квартир и самой низкой скоростью продаж.</p>
  <table class="t small">{ZK_HEAD}<tbody>{zk_rows(laggards, sell=True)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 6</div>
  <h2>Округа</h2>
  {etable(ok, sorted(ok, key=lambda k: -ok[k]['sum']), "Округ")}
  <div class="charts">{hbar([(k, dist[k]['sum']) for k in by_vol], "Районы: элитных квартир продано за год", fi)}
  {hbar([(k, dist[k]['money_bln']) for k in by_money], "Районы: оборот элитных продаж за год, млрд ₽", lambda v: num(v, 1), color=C[1])}</div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 6</div>
  <h2>Районы с 3 и более элитными корпусами</h2>
  {etable(dist, sorted(dist_ok, key=lambda k: -dist[k]['sum']), "Район", compact=True)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 6 · выводы</div>
  <h2>Округа и районы: выводы</h2>
  {conclusions(geo_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 7</div>
  <h2>Лоты, стадия стройки, удалённость</h2>
  <table class="t compact"><thead><tr><th class="l">Состав элитных домов</th><th>Студии</th><th>1-комн.</th><th>2-комн.</th><th>3-комн.</th><th>4+ комн.</th>
  <th>Средняя квартира в доме</th><th>Средняя проданная</th></tr></thead><tbody><tr><td class="l"><b>Элитный класс</b></td>
  {''.join(f"<td class='n'>{pct(mix['mix_pct'].get(k))}</td>" for k in ['студии', '1к', '2к', '3к', '4к+'])}<td class="n">{num(mix['stock_avg_m2'])} м²</td><td class="n"><b>{num(mix['deal_avg_m2'])} м²</b></td></tr></tbody></table>
  <h3>Стадия стройки</h3>{etable(st, ["сдача ≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"], "До сдачи дома")}
  <h3>Удалённость от Кремля</h3>{etable(km, ["10+ км", "6–10 км", "3–6 км (≈ТТК)", "до 3 км (≈Садовое)"], "Удалённость", compact=True)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 7 · выводы</div>
  <h2>Лоты, стройка, новое предложение: выводы</h2>
  {conclusions(lot_concl)}
  <h3>Элитные корпуса, где продажи ещё не открыты</h3>
  <p class="lead">{', '.join(f"{E(short(i['name'], 30))} ({fi(i['flats'])})" for i in idle_ns if i['flats'] >= 30)}.</p>
</section>
<section class="page">
  <div class="eyebrow">Раздел 8</div>
  <h2>Методика</h2>
  <ul class="plain">
    <li><b>Источник</b> — ЕИСЖС наш.дом.рф: карточки всех строящихся домов внутри МКАД, продажи (зарегистрированные ДДУ), проданная площадь и цена сделок по месяцам за 12 месяцев, состав квартир. Собрано 15–16 октября 2026.</li>
    <li><b>Элитный класс</b> — по декларации застройщика: {mk['buildings']} корпусов с продажами и {mk['idle_buildings']} без сделок за год (из них {len(idle_ns)} ещё не открыли продажи). Объекты без квартир (апартаменты, офисы, паркинги) не учитываются.</li>
    <li><b>Средний чек и оборот</b> — проданные м² × средняя цена сделок за месяц, сложенные за 12 месяцев. Доля денег сравнивается со всем рынком квартир внутри МКАД ({bln(sum(v['money_bln'] for v in D['money_share'].values()))} ₽ за год).</li>
    <li><b>Оптовые сделки</b> (одним договором много квартир) в стабильном темпе учтены по числу договоров.</li>
    <li><b>Цена за год</b> — изменение цены сделок с IV кв. 2025 по III кв. 2026 на одних и тех же корпусах; при малом числе сделок (верхние ступени) показатель шумный.</li>
    <li><b>Ограничения:</b> 12 месяцев — вся глубина данных на сайте; продажи по типам квартир не публикуются, поэтому выводы о лотах — по проданной площади и чеку.</li>
  </ul>
</section>"""
    css = R.CSS + ".charts > div h3 { margin-top: 0; }\ntable.t.dense td { padding: .55mm 1.6mm; font-size: 8pt; }\ntable.t.small td { padding: .55mm 1.4mm; } table.t.small th { padding: 1.2mm 1.4mm; }\n"
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Элитные новостройки внутри МКАД</title><style>{css}</style></head><body>{body}</body></html>'
    (HERE / "elite.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "elite.html").as_uri())
        pg.wait_for_timeout(800)
        pg.pdf(path=str(HERE / "elite.pdf"), format="A4", landscape=True, print_background=True, display_header_footer=True,
               header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Элитные новостройки внутри МКАД</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               '<span>Элитный класс внутри МКАД · данные ЕИСЖС наш.дом.рф, 16.10.2026</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    shutil.copy2(HERE / "elite.pdf", HERE.parent / "Элитные_новостройки_внутри_МКАД_2026-10.pdf")
    print("ok", (HERE / "elite.pdf").stat().st_size)


if __name__ == "__main__":
    build()
