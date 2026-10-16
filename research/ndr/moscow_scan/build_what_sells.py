"""PDF report «Что продаётся лучше всего внутри МКАД» from what_sells.json (same style as report/build_report.py)."""
import importlib.util
import json
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("br", HERE.parent / "report" / "build_report.py")
R = importlib.util.module_from_spec(spec)
spec.loader.exec_module(R)
E, C, NAVY, INK2, MUTED, GRID = R.E, R.C, R.NAVY, R.INK2, R.MUTED, R.GRID
fi, fp, pct, short, conclusions, kpi = R.fmt_int, R.fmt_price, R.fmt_pct, R.short, R.conclusions, R.kpi
D = json.loads((HERE / "what_sells.json").read_text())
CLS = ["Комфорт", "Бизнес", "Элитный", "Типовой"]
CLS_C = dict(zip(CLS, C))                                   # fixed hue per class, never by rank


def num(x, d=1):
    return "—" if x is None else (f"{x:.{d}f}" if d else f"{round(x)}").replace(".", ",")


def months_to_sell(absorb):
    return "—" if not absorb else R.fmt_months(round(100 / absorb))


def hbar(rows, title, fmt, width=520, color=C[0], note=""):
    """Horizontal bars, one hue (magnitude), value labels in ink, 4px rounded ends at the baseline side."""
    lab_w, val_w, bh, gap = 170, 70, 15, 7
    W = width - lab_w - val_w
    hi = max(v for _, v in rows) or 1
    h = len(rows) * (bh + gap) + 6
    out = [f'<svg viewBox="0 0 {width} {h}" width="100%" role="img" aria-label="{E(title)}">']
    for i, (lab, v) in enumerate(rows):
        y = 3 + i * (bh + gap)
        w = max(W * v / hi, 2)
        out.append(f'<text x="{lab_w - 8}" y="{y + bh - 4}" text-anchor="end" font-size="10.5" fill="{INK2}">{E(lab)}</text>')
        out.append(f'<rect x="{lab_w}" y="{y}" width="{w:.1f}" height="{bh}" rx="2" fill="{color}"/>')
        out.append(f'<text x="{lab_w + w + 6:.1f}" y="{y + bh - 4}" font-size="10.5" fill="#1a1a1a">{E(fmt(v))}</text>')
    out.append("</svg>")
    cap = f'<div class="caption">{E(note)}</div>' if note else ""
    return f'<figure class="chart"><figcaption>{E(title)}</figcaption>{"".join(out)}{cap}</figure>'


def group_table(d, order, first="", extra_idle=True, compact=False):
    rows = []
    tot = sum(v["sum"] for v in d.values())
    for k in order:
        v = d[k]
        rows.append(f"<tr><td class='l'><b>{E(str(k))}</b></td><td class='n'>{fi(v['buildings'])}"
                    + (f" <span class='sub'>+{v['idle_buildings']} без сделок</span>" if extra_idle and v.get("idle_buildings") else "") + "</td>"
                    f"<td class='n'>{fi(v['sum'])}</td><td class='n'>{pct(100 * v['sum'] / tot)}</td><td class='n'>{num(v['p25'], 0)}</td>"
                    f"<td class='n'>{num(v['cv'], 2)}</td><td class='n'>{pct(v['absorb_pct'])}" + ("" if compact else f"<div class='sub'>весь остаток за {months_to_sell(v['absorb_pct'])}</div>") + "</td>"
                    f"<td class='n'>{fp(v['price_m2'])}</td><td class='n'>{pct(v['price_chg_pct'], sign=True)}</td><td class='n'>{num(v['deal_m2'])} м²</td></tr>")
    return (f"<table class='t compact{' dense' if compact else ''}'><thead><tr><th class='l'>{E(first)}</th><th>Корпусов с продажами</th><th>Продано за 12 мес.</th><th>Доля рынка</th>"
            "<th>Стабильный темп<div class='sub'>квартир в мес., 9 мес. из 12</div></th><th>Разброс по месяцам<div class='sub'>0 — ровно, 1 — рвано</div></th>"
            "<th>Скорость<div class='sub'>доля остатка в месяц</div></th><th>Цена сделок, ₽/м²</th><th>Цена за год<div class='sub'>те же корпуса</div></th>"
            "<th>Средняя сделка</th></tr></thead><tbody>" + "".join(rows) + "</tbody></table>")


def zk_rows(zs, start=1):
    out = []
    for i, z in enumerate(zs, start):
        m = z["monthly"]
        out.append(f"<tr><td class='n'>{i}</td><td class='l'><b>{E(short(z['name'], 34))}</b><div class='sub'>{E(z['dev'] or '')}</div></td>"
                   f"<td>{E(z['class'].lower())}</td><td class='l'>{E(z['district'] or '—')}<div class='sub'>{E(z['okrug'])}</div></td><td class='n'>{num(z['km'])}</td>"
                   f"<td class='n'><b>{num(z['p25'], 0)}</b></td><td class='n'>{num(z['median'], 0)}</td><td class='n'>{num(z['cv'], 2)}</td>"
                   f"<td class='n'>{fi(z['sum'])}</td><td class='n'>{pct(z['sold_pct'])}<div class='sub'>осталось {fi(z['remaining'])}</div></td>"
                   f"<td class='n'>{fp(z['price_m2'])}</td><td class='n'>{pct(z['price_chg_pct'], sign=True)}</td></tr>")
    return "".join(out)


ZK_HEAD = ("<thead><tr><th>№</th><th class='l'>ЖК / застройщик</th><th>Класс</th><th class='l'>Район</th><th>Км от центра</th>"
           "<th>Стабильный темп<div class='sub'>9 мес. из 12, кв./мес.</div></th><th>Обычный месяц<div class='sub'>медиана</div></th>"
           "<th>Разброс</th><th>Продано за 12 мес.</th><th>Распродано</th><th>Цена сделок, ₽/м²</th><th>Цена за год</th></tr></thead>")


def build():
    mk, cl, ok, dist = D["market"], D["classes"], D["okrugs"], D["districts"]
    L = [z for z in D["complexes"] if z["long_run"]]
    tot = mk["sum"]
    share = {c: 100 * cl[c]["sum"] / tot for c in CLS}
    top20 = L[:20]
    n_stable = sum(1 for z in L if z["p25"] >= 20)

    # ---------- page: market by class ----------
    chart_cls = R.line_chart([(c, cl[c]["monthly"], CLS_C[c]) for c in CLS], "Продажи по месяцам, квартир", lambda v: fi(v))
    idx = [(c, [round(100 * x / (sum(cl[c]["monthly"]) / 12)) for x in cl[c]["monthly"]], CLS_C[c]) for c in CLS]
    chart_idx = R.line_chart(idx, "Тот же график в процентах от среднего месяца класса (100 = средний месяц)", lambda v: f"{v:.0f}")
    cls_concl = [
        f"Больше всего квартир продаёт бизнес-класс: {pct(share['Бизнес'])} всех сделок внутри МКАД. Комфорт — {pct(share['Комфорт'])}, элитный — {pct(share['Элитный'])}, типовой — {pct(share['Типовой'])}.",
        f"Быстрее и ровнее всего продаётся комфорт: каждый месяц уходит {pct(cl['Комфорт']['absorb_pct'])} остатка, то есть весь остаток примерно за {months_to_sell(cl['Комфорт']['absorb_pct'])}, а разброс между месяцами самый маленький ({num(cl['Комфорт']['cv'], 2)}).",
        f"Элитный класс — самый медленный: {pct(cl['Элитный']['absorb_pct'])} остатка в месяц, то есть на весь остаток нужно около {months_to_sell(cl['Элитный']['absorb_pct'])}. При этом у элитного класса больше всего корпусов без единой сделки за год ({cl['Элитный']['idle_buildings']}), хотя большинство из них просто ещё не открыли продажи.",
        "Весь рынок пережил одну и ту же волну: пик в декабре 2025 года, провал в феврале 2026-го (в 2,6 раза ниже декабря) и постепенное восстановление к сентябрю. Вероятная причина — покупатели спешили до ужесточения условий семейной ипотеки с февраля 2026 года; данные показывают сам всплеск, но не его причину.",
        f"Цены за год выросли во всех классах (на тех же корпусах): комфорт {pct(cl['Комфорт']['price_chg_pct'], True)}, типовой {pct(cl['Типовой']['price_chg_pct'], True)}, элитный {pct(cl['Элитный']['price_chg_pct'], True)}, бизнес {pct(cl['Бизнес']['price_chg_pct'], True)}.",
    ]

    # ---------- page: districts ----------
    ok_order = sorted(ok, key=lambda k: -ok[k]["sum"])
    dist_ok = [k for k, v in dist.items() if v["buildings"] >= 4]
    by_speed = sorted(dist_ok, key=lambda k: -(dist[k]["absorb_pct"] or 0))
    by_vol = sorted(dist, key=lambda k: -dist[k]["sum"])[:12]
    chart_vol = hbar([(k, dist[k]["sum"]) for k in by_vol], "Районы-лидеры по числу проданных квартир за 12 мес.", fi)
    chart_speed = hbar([(k, dist[k]["absorb_pct"]) for k in by_speed[:12]], "Самые быстрые районы: доля остатка, проданная за месяц", lambda v: pct(v),
                       color=C[2], note="Районы, где продаются хотя бы 4 корпуса.")
    slow = [k for k in sorted(dist_ok, key=lambda k: dist[k]["absorb_pct"] or 0)[:5]]
    dist_concl = [
        f"По объёму лидирует Даниловский район (ЗИЛ, {fi(dist['Даниловский']['sum'])} квартир за год), за ним Хорошёво-Мнёвники ({fi(dist['Хорошёво-Мнёвники']['sum'])}), Покровское-Стрешнево, Печатники и Очаково-Матвеевское. Объём здесь дают крупные кварталы, а не скорость.",
        f"Самые ровные округа — САО и СЗАО (разброс {num(ok['САО']['cv'], 2)} и {num(ok['СЗАО']['cv'], 2)}): продажи почти не скачут от месяца к месяцу. В ЮАО объём большой, но рваный ({num(ok['ЮАО']['cv'], 2)}) из-за всплесков в отдельных ЖК.",
        f"Быстрее всего остаток уходит в районах, где строят немного и недорого: {', '.join(by_speed[:5])}. Это небольшие рынки, где покупатель быстро разбирает предложение.",
        f"Центр продаётся хуже всего: ЦАО — {pct(ok['ЦАО']['absorb_pct'])} остатка в месяц, у ЦАО и самая высокая цена — {fp(ok['ЦАО']['price_m2'])} ₽/м², и самая крупная средняя сделка ({num(ok['ЦАО']['deal_m2'])} м²). Самые медленные районы: {', '.join(slow)}.",
    ]

    # ---------- page: flat types ----------
    mix = D["mix"]
    mix_rows = "".join(
        f"<tr><td class='l'><b>{E(c)}</b></td>" + "".join(f"<td class='n'>{pct(mix[c]['mix_pct'].get(k))}</td>" for k in ["студии", "1к", "2к", "3к", "4к+"])
        + f"<td class='n'>{num(mix[c]['stock_avg_m2'])} м²</td><td class='n'><b>{num(mix[c]['deal_avg_m2'])} м²</b></td>"
          f"<td class='n'>{pct(100 * (mix[c]['deal_avg_m2'] / mix[c]['stock_avg_m2'] - 1), True)}</td><td class='n'>{mix[c]['buildings_with_mix']}</td></tr>"
        for c in CLS + ["Все"])
    sm = D["small_share"]
    sm_rows = "".join(
        f"<tr><td class='l'><b>{E(c)}</b></td>" + "".join(
            f"<td class='n'>{pct(sm[c][g]['absorb_pct'])}<div class='sub'>студий и 1к: {sm[c][g]['small_share_pct']}% · сделка {num(sm[c][g]['deal_m2'])} м²</div></td>"
            for g in ["мало малогабаритных", "средне", "много малогабаритных"]) + "</tr>" for c in ["Комфорт", "Бизнес", "Элитный"] if c in sm)
    deal = D["deal_m2_by_month"]
    chart_deal = R.line_chart([("Средняя площадь сделки", deal, C[0])], "Средняя площадь проданной квартиры по месяцам, м²", lambda v: f"{v:.0f}", ymin0=False)
    mix_concl = [
        f"Покупатели берут квартиры меньше, чем в среднем строят: средняя сделка — {num(mix['Все']['deal_avg_m2'])} м², а средняя квартира в продаваемых корпусах — {num(mix['Все']['stock_avg_m2'])} м². Значит, внутри каждого проекта первыми уходят студии, 1- и небольшие 2-комнатные.",
        f"Сильнее всего это видно в элитном классе: строят в среднем {num(mix['Элитный']['stock_avg_m2'])} м², а покупают {num(mix['Элитный']['deal_avg_m2'])} м². Большие лоты (3к и 4к+) — главный риск зависших остатков.",
        "Но проект с перекосом в малогабаритные не продаётся быстрее: в бизнес-классе корпуса, где студий и 1к больше 60%, распродаются медленнее, чем сбалансированные. Выигрывает сбалансированная нарезка с небольшими квартирами, а не «башня из студий».",
        f"С начала 2026 года средняя сделка выросла с {num(deal[0])}–{num(deal[2])} м² до {num(deal[-1])} м²: после пика конца 2025 года малогабаритные квартиры стали брать меньше.",
        "Важно: сайт отдаёт продажи по корпусу целиком, без разбивки по типам квартир. Выводы о типах — по проданной площади и составу квартир, это надёжный косвенный признак, но не прямой счёт.",
    ]

    # ---------- page: other factors ----------
    pb, kb, sb = D["price_bands"], D["distance"], D["stage"]
    pb_order = [k for k in ["до 400 тыс/м²", "400–600", "600–900", "900–1500", "1500+"] if k in pb]
    kb_order = ["10+ км (к МКАД)", "6–10 км", "3–6 км (≈ТТК)", "до 3 км (≈Садовое)"]
    sb_order = ["сдача ≤ 6 мес", "6–18 мес", "18–36 мес", "36+ мес"]
    other_concl = [
        f"Главная зона спроса — сделки по 400–600 тыс. ₽/м²: {pct(100 * pb['400–600']['sum'] / tot)} всех продаж. Дешевле 400 тыс. продаётся быстрее всего ({pct(pb['до 400 тыс/м²']['absorb_pct'])} остатка в месяц), дороже 900 тыс. — медленно (около {pct(pb['900–1500']['absorb_pct'])}).",
        f"Чем дальше от центра, тем больше и быстрее продаётся: в 10+ км от Кремля — {pct(100 * kb['10+ км (к МКАД)']['sum'] / tot)} всех сделок и {pct(kb['10+ км (к МКАД)']['absorb_pct'])} остатка в месяц. Внутри Садового — только {pct(100 * kb['до 3 км (≈Садовое)']['sum'] / tot)} сделок.",
        f"Почти готовые дома продаются быстрее всех: при сдаче в ближайшие 6 месяцев уходит {pct(sb['сдача ≤ 6 мес']['absorb_pct'])} остатка в месяц, а у проектов со сдачей через 3+ года — всего {pct(sb['36+ мес']['absorb_pct'])}. Котлован продаётся медленно — это нормально, но закладывать в план нужно именно это.",
    ]

    # ---------- top ЖК by class ----------
    cls_tab = lambda c: f"<h3>{E(c)}</h3><table class='t small'>{ZK_HEAD}<tbody>{zk_rows([z for z in L if z['class'] == c][:5])}</tbody></table>"
    zk_concl = [
        f"Стабильно сильных ЖК (не меньше 20 квартир в месяц в 9 месяцах из 12) внутри МКАД — {n_stable}. Все они — комфорт, бизнес или типовой по 340–690 тыс. ₽/м² в 5–20 км от центра; элитных среди них нет.",
        f"Самые надёжные продавцы года: {', '.join(short(z['name'], 22) for z in L[:5])}.",
        "Самые ровные — Инджой (разброс 0,21), Мичуринский парк, Полар и 2-й Иртышский (около 0,25): они продают почти одинаково каждый месяц, без всплесков.",
        "ЗИЛАРТ Спарк и Шагал продают много, но рывками (разброс 0,56 и 0,83): часть объёма — отдельные крупные месяцы. Квартал Метроном и Квартал Герцена уже распроданы на 92–95%, их темп дальше будет падать просто потому, что нечего продавать.",
        f"В элитном классе стабильных лидеров почти нет: лучший — Sydney City ({num(L and next(z['p25'] for z in L if z['class'] == 'Элитный'), 0)} квартир в месяц), у типичного элитного ЖК стабильный темп — около 2 квартир в месяц.",
    ]

    summary = [
        f"Лучше всего на длинной дистанции продаются комфорт и бизнес: вместе {pct(share['Комфорт'] + share['Бизнес'])} всех сделок. Ядро спроса — квартиры по 400–600 тыс. ₽/м² ({pct(100 * D['price_bands']['400–600']['sum'] / tot)} сделок) в 6–20 км от центра.",
        f"Комфорт — самый быстрый и ровный класс (остаток уходит за ~{months_to_sell(cl['Комфорт']['absorb_pct'])}), бизнес — самый большой по объёму ({pct(share['Бизнес'])} сделок), элитный — самый медленный (~{months_to_sell(cl['Элитный']['absorb_pct'])}).",
        "Районы-лидеры по объёму: Даниловский (ЗИЛ), Хорошёво-Мнёвники, Покровское-Стрешнево, Печатники, Очаково-Матвеевское. Самые ровные округа — САО и СЗАО. Центр (ЦАО) продаётся хуже всех.",
        "Типы квартир: покупатели берут в среднем на 20% меньше, чем строят (54 м² против 68 м²), но «башни из студий» не продаются быстрее сбалансированных проектов.",
        f"Стабильно сильных ЖК (20+ квартир в месяц в 9 месяцах из 12) — {n_stable}. Надёжнее всех: {', '.join(short(z['name'], 22) for z in L[:3])}.",
        f"Без единой сделки за год — {mk['idle_buildings']} корпусов из {mk['buildings'] + mk['idle_buildings']}, и большинство из них ({mk['idle_not_started']}) просто ещё не открыли продажи. Объекты без квартир (апартаменты, офисы, паркинги — {mk['no_flats_objects']}) в анализ не входят.",
    ]

    toc = "".join(f"<li><span>{i:02d}</span>{E(n)}</li>" for i, n in enumerate(
        ["Главное", "Классы: кто продаёт больше и ровнее", "Лидеры: 20 самых стабильных ЖК", "Лидеры внутри каждого класса",
         "Округа и районы", "Типы квартир", "Цена, удалённость, стадия стройки", "Методика"], 1))
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Аналитический отчёт · 16 октября 2026</div>
    <h1>Что лучше всего продаётся в новостройках внутри МКАД</h1>
    <p class="lead">Классы, ЖК, районы и типы квартир со стабильно высокими продажами за 12 месяцев (октябрь 2025 — сентябрь 2026). Все строящиеся дома внутри МКАД по данным ЕИСЖС наш.дом.рф.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>{fi(tot)}</b><span>квартир продано за 12 месяцев</span></div>
    <div class="stat"><b>{fi(mk['buildings'])}</b><span>корпусов с продажами</span></div>
    <div class="stat"><b>{len(D['complexes'])}</b><span>ЖК с продажами</span></div>
    <div class="stat"><b>{n_stable}</b><span>стабильно сильных ЖК</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Содержание</div>
  <h2>Что в отчёте</h2>
  <ol class="toc">{toc}</ol>
  <h3>Как читать показатели</h3>
  <ul class="plain">
    <li><b>Стабильный темп</b> — сколько квартир ЖК продавал как минимум в 9 месяцах из 12. Один удачный месяц его не поднимает, поэтому он показывает именно «стабильно высокие продажи».</li>
    <li><b>Разброс по месяцам</b> — насколько продажи скачут: 0,2–0,3 — почти одинаково каждый месяц, 0,8 и выше — рывками.</li>
    <li><b>Скорость</b> — какая доля непроданных квартир уходит за месяц. Позволяет честно сравнивать большие и маленькие проекты: 5% в месяц — остаток уйдёт примерно за 1,7 года.</li>
    <li><b>Цена за год</b> — изменение цены сделок с IV кв. 2025 по III кв. 2026 на одних и тех же корпусах.</li>
  </ul>
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Главное</h2>
  <div class="kpis">
    {kpi("Комфорт", pct(cl['Комфорт']['absorb_pct']), "остатка в месяц — самый быстрый")}
    {kpi("Бизнес", pct(share['Бизнес']), "всех сделок — самый большой")}
    {kpi("Элитный", pct(cl['Элитный']['absorb_pct']), "остатка в месяц — самый медленный")}
    {kpi("Средняя сделка", num(mix['Все']['deal_avg_m2']) + " м²", f"при средней квартире {num(mix['Все']['stock_avg_m2'])} м²")}
  </div>
  {conclusions(summary, "Главное простым языком")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Классы: кто продаёт больше и ровнее</h2>
  <p class="lead">Все строящиеся корпуса внутри МКАД без Фонда реновации. Месяцы с оптовыми сделками (одним договором много квартир) посчитаны по числу договоров.</p>
  {group_table(cl, CLS, "Класс")}
  <div class="charts">{chart_cls}{chart_idx}</div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2 · выводы</div>
  <h2>Классы: выводы</h2>
  {conclusions(cls_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>20 самых стабильных ЖК внутри МКАД</h2>
  <p class="lead">ЖК, которые продавались все 12 месяцев (в продаже с октября 2025 года или раньше). Отсортированы по стабильному темпу — сколько квартир они продавали как минимум в 9 месяцах из 12.</p>
  <table class="t small">{ZK_HEAD}<tbody>{zk_rows(top20[:10])}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>20 самых стабильных ЖК: места 11–20</h2>
  <table class="t small">{ZK_HEAD}<tbody>{zk_rows(top20[10:], 11)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 3 · выводы</div>
  <h2>ЖК-лидеры: выводы</h2>
  {conclusions(zk_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>Лидеры внутри каждого класса: пять самых стабильных ЖК</h2>
  {cls_tab("Комфорт")}{cls_tab("Бизнес")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>Лидеры элитного класса</h2>
  {cls_tab("Элитный")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 5</div>
  <h2>Округа</h2>
  {group_table(ok, ok_order, "Округ")}
  <div class="charts">{chart_vol}{chart_speed}</div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 5</div>
  <h2>Районы с 4 и более корпусами в продаже</h2>
  {group_table(dist, sorted(dist_ok, key=lambda k: -dist[k]['sum']), "Район", compact=True)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 5 · выводы</div>
  <h2>Округа и районы: выводы</h2>
  {conclusions(dist_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 6</div>
  <h2>Типы квартир</h2>
  <p class="lead">Состав квартир в корпусах (из карточки дома) и средняя площадь реально проданной квартиры (проданные м² ÷ число сделок).</p>
  <table class="t compact"><thead><tr><th class="l">Класс</th><th>Студии</th><th>1-комн.</th><th>2-комн.</th><th>3-комн.</th><th>4+ комн.</th>
  <th>Средняя квартира в доме</th><th>Средняя проданная</th><th>Разница</th><th>Корпусов</th></tr></thead><tbody>{mix_rows}</tbody></table>
  <div class="charts">
    <div><h3>Ускоряет ли продажи доля малогабаритных?</h3>
    <table class="t compact"><thead><tr><th class="l">Класс</th><th>Мало студий и 1к<div class="sub">треть корпусов</div></th><th>Средне</th><th>Много студий и 1к</th></tr></thead><tbody>{sm_rows}</tbody></table>
    <div class="caption">Скорость — доля остатка, проданная за месяц; корпуса в продаже весь год.</div></div>
    {chart_deal}
  </div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 6 · выводы</div>
  <h2>Типы квартир: выводы</h2>
  {conclusions(mix_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 7</div>
  <h2>Цена, удалённость от центра, стадия стройки</h2>
  <h3>Цена сделок за м²</h3>{group_table(pb, pb_order, "Цена, тыс. ₽/м²", extra_idle=False)}
  <h3>Расстояние от Кремля</h3>{group_table(kb, kb_order, "Удалённость")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 7</div>
  <h2>Стадия стройки</h2>
  {group_table(sb, sb_order, "До сдачи дома")}
  {conclusions(other_concl)}
</section>
<section class="page">
  <div class="eyebrow">Раздел 8</div>
  <h2>Методика</h2>
  <ul class="plain">
    <li><b>Источник</b> — ЕИСЖС наш.дом.рф, карточки всех строящихся домов Москвы: счётчик проданных квартир, продажи и цены по месяцам за 12 месяцев (октябрь 2025 — сентябрь 2026), состав квартир. Собрано 432 карточки, 15–16 октября 2026.</li>
    <li><b>Внутри МКАД</b> — по координатам дома и точному контуру МКАД (OpenStreetMap). Дома Фонда реновации исключены: это квартиры для переселения, а не рыночные продажи.</li>
    <li><b>Продажи</b> — зарегистрированные ДДУ, квартиры. В месяцах с оптовой сделкой (в 5+ раз больше квартир, чем договоров) учтено число договоров, чтобы одна покупка фонда не выглядела как спрос ({fi(mk['bulk_flats'])} квартир за год).</li>
    <li><b>ЖК</b> — корпуса одного застройщика с одним названием. «Долгая дистанция» — ЖК, которые продавались все 12 месяцев: {len(L)} из {len(D['complexes'])}.</li>
    <li><b>Скорость</b> — средние продажи в месяц ÷ средний непроданный остаток за год. Остаток на начало каждого месяца восстановлен по сегодняшнему счётчику и помесячным продажам.</li>
    <li><b>Корпуса без сделок</b> ({mk['idle_buildings']}) в скорость не входят и показаны отдельно: у {mk['idle_not_started']} из них продажи по ДДУ официально ещё не открыты. Объекты без квартир — апартаменты, офисы, паркинги ({mk['no_flats_objects']}) — исключены.</li>
    <li><b>Ограничения:</b> 12 месяцев — вся глубина данных на сайте; продаж по типам квартир сайт не публикует, поэтому выводы о типах сделаны по проданной площади.</li>
  </ul>
</section>"""
    css = R.CSS + ".charts > div h3 { margin-top: 0; }\ntable.t.dense td { padding: .55mm 1.6mm; font-size: 8pt; }\ntable.t.small td { padding: .55mm 1.4mm; } table.t.small th { padding: 1.2mm 1.4mm; }\n"
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Что продаётся внутри МКАД</title><style>{css}</style></head><body>{body}</body></html>'
    (HERE / "what_sells.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "what_sells.html").as_uri())
        pg.wait_for_timeout(800)
        pg.pdf(path=str(HERE / "what_sells.pdf"), format="A4", landscape=True, print_background=True, display_header_footer=True,
               header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Что продаётся внутри МКАД</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               '<span>Новостройки внутри МКАД · данные ЕИСЖС наш.дом.рф, 16.10.2026</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    shutil.copy2(HERE / "what_sells.pdf", HERE.parent / "Что_продаётся_внутри_МКАД_2026-10.pdf")
    print("ok", (HERE / "what_sells.pdf").stat().st_size)


if __name__ == "__main__":
    build()
