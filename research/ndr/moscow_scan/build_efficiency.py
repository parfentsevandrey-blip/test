"""PDF report «Продажи на размер проекта» from efficiency.json."""
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
D = json.loads((HERE / "efficiency.json").read_text())

HEAD = ("<thead><tr><th>№</th><th class='l'>ЖК / застройщик</th><th>Класс</th><th class='l'>Район</th><th>Квартир в ЖК</th>"
        "<th>Стабильно продаёт<div class='sub'>% проекта в мес., 9 мес. из 12</div></th><th>Продано за год<div class='sub'>% проекта</div></th>"
        "<th>Стабильный темп<div class='sub'>квартир в мес.</div></th><th>Разброс</th><th>Цена, ₽/м²</th><th>Средний чек, ₽</th>"
        "<th>Студий и 1к</th><th>Сдача</th></tr></thead>")


def rows(rs, start=1):
    out = []
    for i, r in enumerate(rs, start):
        out.append(f"<tr><td class='n'>{i}</td><td class='l'><b>{E(short(r['name'], 32))}</b><div class='sub'>{E(r['dev'] or '')}</div></td>"
                   f"<td>{E(r['class'].lower())}</td><td class='l'>{E(r['district'] or '—')}<div class='sub'>{E(r['okrug'])} · {num(r['km'])} км</div></td>"
                   f"<td class='n'>{fi(r['flats'])}<div class='sub'>{r['buildings']} корп.</div></td><td class='n'><b>{pct(r['stable_pct'])}</b></td>"
                   f"<td class='n'>{pct(r['year_pct'])}</td><td class='n'>{num(r['p25'], 0)}</td><td class='n'>{num(r['cv'], 2)}</td>"
                   f"<td class='n'>{fp(r['price_m2'])}</td><td class='n'>{num(r['ticket_mln'], 0)} млн</td>"
                   f"<td class='n'>{'—' if r['small_pct'] is None else str(r['small_pct']) + '%'}</td><td class='n'>{E(r['completion'][3:])}</td></tr>")
    return "".join(out)


def ptable(name, d):
    trs = "".join(f"<tr><td class='l'><b>{E(k)}</b></td><td class='n'>{v['zk']}</td><td class='n'><b>{pct(v['median_stable_pct'])}</b></td>"
                  f"<td class='n'>{pct(v['median_year_pct'])}</td><td class='n'>{v['in_top30']}</td></tr>" for k, v in d.items())
    return (f"<table class='t compact'><thead><tr><th class='l'>{E(name)}</th><th>ЖК</th><th>Стабильно продаёт<div class='sub'>медиана, % проекта в мес.</div></th>"
            f"<th>Продано за год<div class='sub'>медиана, % проекта</div></th><th>В топ-30</th></tr></thead><tbody>{trs}</tbody></table>")


def build():
    rk, pr = D["ranking"], D["profiles"]
    top = rk[0]
    el = [r for r in rk if "Элитный" in r["class"]]
    prime = [r for r in rk if (r["price_m2"] or 0) >= 1_000_000]
    t20 = pr["Средний чек"]["до 20 млн"]; t80 = pr["Средний чек"]["80 млн+"]
    sz = pr["Размер проекта"]
    summary = [
        f"Как считали: не сколько квартир продано, а какую долю своего проекта ЖК продаёт в месяц — и не в лучший месяц, а как минимум в 9 месяцах из 12. Большой проект на этом не выигрывает. В сравнении {D['eligible']} ЖК, которые весь год были в продаже и имели что продавать.",
        f"Обычный ЖК внутри МКАД стабильно продаёт {pct(D['median_stable_pct'])} своих квартир в месяц и {pct(D['median_year_pct'])} за год.",
        f"Лидер — {short(top['name'], 30)} ({fi(top['flats'])} квартир, один корпус): стабильно {pct(top['stable_pct'])} проекта в месяц, за год продано {pct(top['year_pct'])} — в 4 раза быстрее рынка. Рядом — две башни Гранель (Дубровка и Нижегородская) и Лосиноостровский (235 квартир).",
        f"Размер проекта сам по себе не решает: ЖК на 300–600 квартир и на 1000+ продают одинаковую долю ({pct(sz['300–600']['median_stable_pct'])} и {pct(sz['1000+']['median_stable_pct'])} в месяц). Количество корпусов тоже не важно.",
        f"Решает бюджет квартиры: при среднем чеке до 20 млн ₽ ЖК стабильно продаёт {pct(t20['median_stable_pct'])} проекта в месяц, при чеке от 80 млн ₽ — {pct(t80['median_stable_pct'])}, в 3 раза меньше. Половина топ-30 — проекты с чеком до 20 млн ₽.",
        "Формула самых эффективных: цена 350–550 тыс. ₽/м², чек 15–30 млн ₽, 7–15 км от центра, сдача через 1–2 года, много студий и 1-комнатных (у лидеров с известным составом — 55–75%).",
        f"В элите лучше всех по этой мерке Дрим Рива ({pct(el[0]['stable_pct'])} проекта в месяц), Остров и Союз. Среди проектов от 1 млн ₽/м² — Дом 7 Даниловский и Монблан: около 1% проекта в месяц, вдвое ниже рынка.",
    ]
    prof = "".join(f"<div>{ptable(k, pr[k])}</div>" for k in ["Средний чек", "Цена, ₽/м²", "Удалённость от Кремля", "Класс"])
    prof2 = "".join(f"<div>{ptable(k, pr[k])}</div>" for k in ["Размер проекта", "Корпусов в ЖК", "Доля студий и 1к", "До сдачи"])
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Аналитический отчёт · 16 октября 2026</div>
    <h1>Кто продаёт больше всего на свой размер</h1>
    <p class="lead">Новостройки внутри МКАД: какую долю своих квартир ЖК стабильно продаёт каждый месяц — без преимущества больших проектов. 12 месяцев (октябрь 2025 — сентябрь 2026), данные ЕИСЖС наш.дом.рф.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>{D['eligible']}</b><span>ЖК в сравнении</span></div>
    <div class="stat"><b>{pct(D['median_stable_pct'])}</b><span>проекта в месяц — обычный ЖК</span></div>
    <div class="stat"><b>{pct(top['stable_pct'])}</b><span>проекта в месяц — лидер</span></div>
    <div class="stat"><b>{pct(top['year_pct'])}</b><span>проекта за год — лидер</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Главное</h2>
  {conclusions(summary, "Главное простым языком")}
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Топ-30: места 1–10</h2>
  <p class="lead">Отсортировано по доле проекта, которую ЖК продавал как минимум в 9 месяцах из 12.</p>
  <table class="t small">{HEAD}<tbody>{rows(rk[:10])}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Топ-30: места 11–20</h2>
  <table class="t small">{HEAD}<tbody>{rows(rk[10:20], 11)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Топ-30: места 21–30</h2>
  <table class="t small">{HEAD}<tbody>{rows(rk[20:30], 21)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Что общего у эффективных ЖК: бюджет, цена, место, класс</h2>
  <div class="charts">{prof}</div>
</section>
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Что не решает: размер, корпуса, нарезка, стадия</h2>
  <div class="charts">{prof2}</div>
  {conclusions([
      "Чем дешевле квартира целиком (чек), тем большую долю проекта ЖК продаёт: это самый сильный фактор из всех.",
      "Размер проекта и число корпусов почти не влияют: большой ЖК продаёт больше квартир только потому, что он большой.",
      "Проекты, где больше 60% студий и 1-комнатных, продают заметно большую долю (30% проекта за год против 18–22%) — но в основном в бюджетных ценах; в элите эффект слабый.",
      "Чем ближе сдача, тем выше доля продаж, но разница умеренная.",
  ])}
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>Элитный класс: топ-10</h2>
  <table class="t small">{HEAD}<tbody>{rows(el[:10])}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 4</div>
  <h2>Проекты от 1 млн ₽/м²</h2>
  <table class="t small">{HEAD}<tbody>{rows(prime)}</tbody></table>
</section>
<section class="page">
  <div class="eyebrow">Раздел 5</div>
  <h2>Методика</h2>
  <ul class="plain">
    <li><b>Стабильно продаёт</b> — 25-й процентиль помесячных продаж ЖК ÷ число квартир в ЖК. Это доля проекта, которую ЖК продавал как минимум в 9 месяцах из 12; один удачный месяц на неё не влияет.</li>
    <li><b>Кто в сравнении</b>: ЖК внутри МКАД, в продаже весь год (с октября 2025 года), с продажами минимум в 10 месяцах, от 40 квартир и с непроданным остатком на начало периода не меньше 25% — чтобы почти распроданные проекты не выглядели медленными или быстрыми. Фонд реновации исключён, оптовые сделки посчитаны по числу договоров.</li>
    <li><b>ЖК</b> — корпуса одного застройщика с одним названием; поэтому один комплекс иногда разбит на очереди разных юрлиц (например, Остров).</li>
    <li><b>Средний чек</b> — проданные м² × цена ÷ число проданных квартир. <b>Доля студий и 1к</b> — по составу квартир из карточки дома, есть не у всех ЖК.</li>
    <li>Данные ЕИСЖС наш.дом.рф, 12 месяцев — вся доступная глубина.</li>
  </ul>
</section>"""
    css = R.CSS + "table.t.small td { padding: .6mm 1.3mm; } table.t.small th { padding: 1.2mm 1.3mm; }\n.charts > div table { margin-top: 0; }\n"
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Продажи на размер проекта</title><style>{css}</style></head><body>{body}</body></html>'
    (HERE / "efficiency.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "efficiency.html").as_uri())
        pg.wait_for_timeout(800)
        pg.pdf(path=str(HERE / "efficiency.pdf"), format="A4", landscape=True, print_background=True, display_header_footer=True,
               header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Продажи на размер проекта</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               '<span>Новостройки внутри МКАД · данные ЕИСЖС наш.дом.рф, 16.10.2026</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    shutil.copy2(HERE / "efficiency.pdf", HERE.parent / "Продажи_на_размер_проекта_2026-10.pdf")
    print("ok", (HERE / "efficiency.pdf").stat().st_size)


if __name__ == "__main__":
    build()
