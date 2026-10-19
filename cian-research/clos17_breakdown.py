#!/usr/bin/env python3
"""«Кло 17» и «Люче»: все лоты на ЦИАН — изменения цены, срок экспозиции, снятые лоты; где застройщик держит цену, где режет.

Данные: clos17-data/jk.json, cards.json (активные), history_cards.json (активные и снятые — clos17_history.py),
clos17-data/our/deal.json (наша сделка — точка реальной цены).
python3 clos17_breakdown.py → reports/private/Кло17_Люче_история_цен.xlsx, .pdf
"""
import datetime as dt
import json
import os
import statistics as st
from html import escape

import stromynka_report as R
from stromynka_report import m

D = 'clos17-data'
OUT = 'reports/private'
XLSX, PDF, HTML = f'{OUT}/Кло17_Люче_история_цен.xlsx', f'{OUT}/Кло17_Люче_история_цен.pdf', f'{OUT}/clos17_hist.html'
TODAY = dt.date(2026, 10, 19)
DATE = '19 октября 2026 г.'
JK = {'Кло 17': 'Кло 17', 'Clos 17': 'Кло 17', 'Фамильный дом Люче': 'Люче', 'LUCE': 'Люче'}   # ЦИАН переименовывал ЖК
UNITS = {'Кло 17': 28, 'Люче': 43}            # по описаниям застройщика на ЦИАН: 26 квартир + 2 пентхауса; 43 квартиры
DEAL = json.load(open(f'{D}/our/deal.json'))
OUTCOME = {'sale': 'в продаже', 'sold': 'снят застройщиком — вероятно продан', 'relist': 'снят и выставлен заново',
           'draft': 'убран в черновик — бронь или пауза', 'ours': 'наша сделка 31.10.2025', 'assign': 'переуступка в продаже'}


def d(s):
    return dt.date.fromisoformat(s[:10]) if s else None


def lots():
    H = json.load(open(f'{D}/history_cards.json')) if os.path.exists(f'{D}/history_cards.json') else {}
    J = json.load(open(f'{D}/jk.json'))
    C = json.load(open(f'{D}/cards.json'))
    rows = {}
    for k, x in J.items():
        c = C.get(k) or {}
        rows[k] = dict(id=int(k), jk=JK[x['jk']], status='published', area=x['area'], floor=x['floor'], rooms=x['rooms'],
                       price=x['price'], developer=x['developer'], created=d(c.get('created') or x['created']), removed=None,
                       views=(c.get('stats') or {}).get('totalViewsFormattedString'), pc=[tuple(p) for p in c.get('price_changes') or []])
    for k, h in H.items():
        if h.get('jk') not in JK or k in J:
            continue
        rows[k] = dict(id=int(k), jk=JK[h['jk']], status=h.get('status'), area=h.get('area'), floor=h.get('floor'), rooms=h.get('rooms'),
                       price=h.get('price'), developer=not h.get('seller') or h.get('seller') == 'MR' or h.get('developer'),
                       created=d(h.get('created')), removed=d(h.get('archived') or h.get('edited')), views=h.get('views'),
                       pc=[tuple(p) for p in h.get('price_changes') or []])
    out = list(rows.values())
    for r in out:
        pc = sorted(p for p in r['pc'] if p[1])
        first = pc[0][1] if pc else r['price']
        r.update(first=first, last=r['price'], change=(r['price'] / first - 1) if first and r['price'] else 0.0,
                 change_date=pc[-1][0] if len(pc) > 1 and pc[-1][1] != pc[0][1] else None,
                 ppsqm=r['price'] / r['area'] if r['price'] and r['area'] else None,
                 kind=('пентхаус' if (r['area'] or 0) > 300 else f"{r['rooms']} сп." if r['rooms'] else '—'))
        end = r['removed'] or TODAY
        r['days'] = (end - r['created']).days if r['created'] else None
        if r['status'] == 'published':
            r['outcome'] = 'sale' if r['developer'] else 'assign'
        elif r['status'] == 'draft':
            r['outcome'] = 'draft'
        else:
            r['outcome'] = 'sold'
    # снятый лот, который позже снова выставлен с тем же этажом и площадью, — не продажа
    for r in out:
        if r['outcome'] in ('sold', 'draft'):
            again = [x for x in out if x['outcome'] == 'sale' and x['jk'] == r['jk'] and x['floor'] == r['floor']
                     and abs((x['area'] or 0) - (r['area'] or 0)) < 0.2 and x['created'] and r['removed'] and x['created'] >= r['removed']]
            if again:
                r['outcome'], r['relisted_as'] = 'relist', again[0]['id']
        if r['jk'] == 'Кло 17' and r['floor'] == DEAL['floor'] and abs((r['area'] or 0) - DEAL['area']) < 0.2 and r['outcome'] != 'sale':
            r['outcome'] = 'ours'
    return sorted(out, key=lambda r: (r['jk'], ['sale', 'assign', 'draft', 'relist', 'sold', 'ours'].index(r['outcome']), r['floor'] or 0, r['area'] or 0))


def summary(rows):
    res = {}
    for jk in ('Кло 17', 'Люче'):
        v = [r for r in rows if r['jk'] == jk]
        sale = [r for r in v if r['outcome'] == 'sale']
        sold = [r for r in v if r['outcome'] in ('sold', 'ours')]
        res[jk] = dict(units=UNITS[jk], sale=sale, sold=sold, assign=[r for r in v if r['outcome'] == 'assign'],
                       relist=[r for r in v if r['outcome'] == 'relist'], draft=[r for r in v if r['outcome'] == 'draft'],
                       cut=[r for r in v if r['change'] < -0.03], up=[r for r in v if r['change'] > 0.005],
                       before=UNITS[jk] - len(sale) - len(sold) - len([r for r in v if r['outcome'] == 'draft']),
                       days_sale=st.median(r['days'] for r in sale), days_sold=st.median(r['days'] for r in sold) if sold else None,
                       pp_sale=st.median(r['ppsqm'] for r in sale), pp_sold=st.median(r['ppsqm'] for r in sold) if sold else None,
                       floor2_sale=len([r for r in sale if r['floor'] == 2]), floor2_sold=len([r for r in sold if r['floor'] == 2]))
    return res


# ---------- графика ----------

def timeline_png(rows, out):
    matplotlib, plt = R.plt_setup()
    import matplotlib.dates as mdates
    col = {'sale': R.BLUE, 'assign': '#8fb3e0', 'sold': R.AQUA, 'ours': R.ORANGE, 'relist': '#c9c7bf', 'draft': '#c9c7bf'}
    v = [r for r in rows if r['created']]
    fig, axes = plt.subplots(1, 2, figsize=(7.4, 5.6), dpi=200, sharex=True)
    fig.patch.set_facecolor(R.SURF)
    for ax, jk in zip(axes, ('Кло 17', 'Люче')):
        ax.set_facecolor(R.SURF)
        xs = sorted([r for r in v if r['jk'] == jk], key=lambda r: (['sold', 'ours', 'draft', 'relist', 'assign', 'sale'].index(r['outcome']), r['floor'], r['area']))
        for i, r in enumerate(xs):
            end = r['removed'] or TODAY
            ax.plot([r['created'], end], [i, i], color=col[r['outcome']], lw=4.2, solid_capstyle='butt')
            if r['change_date']:
                ax.scatter([d(r['change_date'])], [i], marker='v' if r['change'] < 0 else '^', s=26,
                           color=R.INK if abs(r['change']) > 0.03 else R.INK2, zorder=4)
            lab = f"{m(r['area'], 0)} м², {r['floor']} эт."
            ax.text(d('2025-04-25'), i, lab, va='center', ha='right', fontsize=6.4, color=R.INK2)
        ax.set_ylim(len(xs) - 0.4, -0.6)
        ax.set_yticks([])
        ax.set_title(f'«{jk}»', fontsize=10, color=R.INK, loc='left')
        ax.xaxis.set_major_locator(mdates.MonthLocator(bymonth=[1, 4, 7, 10]))
        ax.xaxis.set_major_formatter(mdates.DateFormatter('%m.%y'))
        ax.set_xlim(d('2025-04-25'), d('2026-11-05'))
        ax.grid(axis='x', color=R.GRID, lw=0.8)
        for s in ('top', 'right', 'left'):
            ax.spines[s].set_visible(False)
        ax.spines['bottom'].set_color(R.GRID)
        ax.tick_params(colors=R.INK2, labelsize=7.5)
    from matplotlib.lines import Line2D
    h = [Line2D([0], [0], color=col[k], lw=4, label=lab) for k, lab in
         (('sale', 'в продаже у застройщика'), ('sold', 'снят застройщиком (продан)'), ('ours', 'наша квартира'),
          ('relist', 'снят и вернулся / в черновике'), ('assign', 'переуступка'))]
    h += [Line2D([0], [0], marker='v', color=R.INK, lw=0, label='снижение цены'), Line2D([0], [0], marker='^', color=R.INK2, lw=0, label='повышение')]
    fig.legend(handles=h, loc='lower center', ncol=4, frameon=False, fontsize=7.2, labelcolor=R.INK2)
    fig.tight_layout(rect=(0.06, 0.07, 1, 1))
    fig.savefig(out, facecolor=R.SURF)
    plt.close(fig)


# ---------- Excel ----------

def write_xlsx(rows, sm):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter as L
    F = 'Arial'
    bold, font = Font(name=F, bold=True, size=9), Font(name=F, size=9)
    grid = Side(style='thin', color='C8CDD3')
    border = Border(left=grid, right=grid, top=grid, bottom=grid)
    head = PatternFill('solid', fgColor='DDE3EA')
    fills = {'sold': 'E2F3EA', 'ours': 'FCE4D6', 'relist': 'EEEEEE', 'draft': 'EEEEEE', 'assign': 'E8EEF8'}
    wb = Workbook()
    ws = wb.active
    ws.title = 'Все лоты'
    cols = [('ЖК', 9), ('ID ЦИАН', 11), ('Итог', 30), ('Спален', 7), ('Площадь, м²', 9), ('Этаж', 6), ('Первая цена, млн ₽', 11),
            ('Последняя цена, млн ₽', 11), ('Изменение', 9), ('Дата изменения', 11), ('₽/м²', 11), ('Выставлен', 11), ('Снят', 11),
            ('Дней на ЦИАН', 9), ('Просмотры', 24)]
    for k, (h_, w) in enumerate(cols, 1):
        c = ws.cell(1, k, h_)
        c.font, c.fill, c.border = bold, head, border
        c.alignment = Alignment(wrap_text=True, vertical='center', horizontal='center')
        ws.column_dimensions[L(k)].width = w
    ws.row_dimensions[1].height = 30
    for n, r in enumerate(rows, 2):
        vals = [r['jk'], r['id'], OUTCOME[r['outcome']] + (f" (новый ID {r['relisted_as']})" if r.get('relisted_as') else ''),
                r['rooms'], r['area'], r['floor'], round(r['first'] / 1e6, 2) if r['first'] else None, round(r['last'] / 1e6, 2) if r['last'] else None,
                round(r['change'], 4), r['change_date'], round(r['ppsqm']) if r['ppsqm'] else None, r['created'], r['removed'], r['days'], r['views']]
        for k, v in enumerate(vals, 1):
            c = ws.cell(n, k, v)
            c.font, c.border = font, border
            if r['outcome'] in fills:
                c.fill = PatternFill('solid', fgColor=fills[r['outcome']])
        ws.cell(n, 2).hyperlink = f"https://www.cian.ru/sale/flat/{r['id']}/"
        ws.cell(n, 9).number_format = '+0.0%;-0.0%;0.0%'
        ws.cell(n, 11).number_format = '#,##0'
        for k in (12, 13):
            ws.cell(n, k).number_format = 'DD.MM.YYYY'
    ws.freeze_panes = 'C2'
    ws.auto_filter.ref = f'A1:{L(len(cols))}{len(rows) + 1}'
    s2 = wb.create_sheet('Сводка')
    s2.column_dimensions['A'].width = 52
    s2.column_dimensions['B'].width = 14
    s2.column_dimensions['C'].width = 14
    lines = [('', 'Кло 17', 'Люче'), ('Квартир в доме (по описанию застройщика)', 'units', None),
             ('Сейчас в продаже у застройщика', 'sale', None), ('Переуступки в продаже', 'assign', None),
             ('Сняты застройщиком с сентября 2025 (вероятно проданы, вкл. нашу)', 'sold', None),
             ('Сняты и выставлены заново / убраны в черновик', 'relist_draft', None),
             ('Проданы до сентября 2025 (оценка: всего − остальные)', 'before', None),
             ('Медиана дней на ЦИАН — лоты в продаже', 'days_sale', None), ('Медиана дней на ЦИАН — снятые', 'days_sold', None),
             ('Медиана цены м² — в продаже, ₽', 'pp_sale', None), ('Медиана цены м² — снятые (последняя цена), ₽', 'pp_sold', None),
             ('Лоты 2-го этажа: в продаже / сняты', 'floor2', None), ('Лоты со снижением цены > 3%', 'cut', None)]
    for i, (lab, key, _) in enumerate(lines, 1):
        s2.cell(i, 1, lab).font = bold if i == 1 else font
        for j, jk in enumerate(('Кло 17', 'Люче'), 2):
            if i == 1:
                s2.cell(i, j, key if j == 2 else _).font = bold
                continue
            s = sm[jk]
            v = {'relist_draft': len(s['relist']) + len(s['draft']), 'floor2': f"{s['floor2_sale']} / {s['floor2_sold']}"}.get(key, s.get(key))
            v = len(v) if isinstance(v, list) else round(v) if isinstance(v, float) else v
            c = s2.cell(i, j, v)
            c.font = font
            if isinstance(v, int) and v > 10000:
                c.number_format = '#,##0'
    wb.save(XLSX)


# ---------- PDF ----------

def build_html(rows, sm):
    k, l = sm['Кло 17'], sm['Люче']
    ours = next(r for r in rows if r['outcome'] == 'ours')
    disc = DEAL['price'] / ours['last'] - 1
    sold = [r for r in rows if r['outcome'] in ('sold', 'ours', 'relist', 'draft')]
    sold_rows = ''.join(
        f'<tr class="{"hl" if r["outcome"] == "ours" else ""}"><td>{r["jk"]}</td><td><a href="https://www.cian.ru/sale/flat/{r["id"]}/">{r["id"]}</a></td>'
        f'<td>{r["kind"]}</td><td class=r>{m(r["area"], 1)}</td><td class=r>{r["floor"]}</td><td class=r>{m(r["last"] / 1e6, 1)}</td>'
        f'<td class=r>{m(r["ppsqm"] / 1e6, 2)}</td><td>{r["created"]:%d.%m.%y}</td><td>{r["removed"]:%d.%m.%y}</td><td class=r>{r["days"]}</td>'
        f'<td>{escape(OUTCOME[r["outcome"]])}</td></tr>' for r in sorted(sold, key=lambda r: r['removed']))
    ch = [r for r in rows if r['change_date'] and abs(r['change']) > 0.0005]
    ch_rows = ''.join(
        f'<tr><td>{r["jk"]}</td><td>{r["kind"]}</td><td class=r>{m(r["area"], 1)}</td><td class=r>{r["floor"]}</td>'
        f'<td class=r>{m(r["first"] / 1e6, 1)}</td><td class=r>{m(r["last"] / 1e6, 1)}</td>'
        f'<td class=r><b>{"+" if r["change"] > 0 else "−"}{m(abs(100 * r["change"]), 1)}%</b></td><td>{dt.date.fromisoformat(r["change_date"]):%d.%m.%y}</td></tr>'
        for r in sorted(ch, key=lambda r: r['change']) if abs(r['change']) > 0.003)
    small = len([r for r in ch if 0.003 >= abs(r['change'])])

    def kpi(v, lab):
        return f'<div class=kpi><div class=v>{v}</div><div class=l>{lab}</div></div>'
    body = f'''
<div class=t1>«Кло 17» и «Люче»: цены, экспозиция и продажи на ЦИАН</div>
<div class=t2>Все объявления двух клубных домов MR Group в Староваганьковском переулке, включая снятые · на {DATE}</div>
<div class=kpis>
{kpi(f"{len(k['sold'])} и {len(l['sold'])}", "лотов снято застройщиком за 13 мес.<br>в «Кло 17» и «Люче» (вкл. нашу)")}
{kpi(f"{m(k['days_sale'])}–{m(l['days_sale'])} дн.", "висят лоты, которые сейчас<br>в продаже (медиана)")}
{kpi(f"−{m(-100 * disc, 1)}%", f"наша сделка к прайсу: {m(ours['last'] / 1e6, 1)} млн<br>в объявлении → {m(DEAL['price'] / 1e6, 2)} млн по ДДУ")}
{kpi(f"{len(k['cut']) + len(l['cut'])} из {len(rows)}", "лотов с заметным снижением цены<br>(больше 3%) в двух домах")}
</div>
<div class=box>
<p><b>Наша квартира была на ЦИАН у застройщика.</b> С 16.09.2025 её выставляли за {m(ours['last'] / 1e6, 1)} млн ({m(ours['ppsqm'] / 1e6, 2)} млн/м²).
31.10.2025 мы купили её по ДДУ за {m(DEAL['price'] / 1e6, 2)} млн, на {m(-100 * disc, 1)}% ниже объявления, а 23.11.2025 объявление сняли.
Это единственная известная нам реальная цена сделки в этих домах. Застройщик держит прайс в объявлениях, а скидку даёт в сделке.</p>
<p><b>Продаётся медленно.</b> С сентября 2025 года застройщик снял {len(k['sold'])} лота в «Кло 17» и {len(l['sold'])} в «Люче» — меньше одной квартиры
в месяц на два дома. Лоты в продаже висят по {m(k['days_sale'])} и {m(l['days_sale'])} дней. Ещё {len(l['relist'])} лот в «Люче» сняли и через месяц
выставили заново — сделка, видимо, сорвалась.</p>
<p><b>Что держит цену и где дисконт.</b> Прайс большинства лотов не менялся 13 месяцев (±1%). Заметно снижали только {len(k['cut']) + len(l['cut'])} лота,
все на краях линейки: самую маленькую квартиру «Кло 17» (−22%, чтобы был вход от 227,5 млн) и самые большие в «Люче» (−8% и пентхаус −18%).
Уходят верхние этажи и маленькие квартиры: в «Люче» все 1-спальные по 78–79 м² уже сняты. Второй этаж стоит:
в продаже {k['floor2_sale']} лотов 2-го этажа в «Кло 17» и {l['floor2_sale']} в «Люче», и ни один лот 2-го этажа за 13 месяцев не снят —
все проданные лоты с 3-го этажа и выше.</p></div>
<img class=full src="file://{os.path.abspath(f"{D}/our/timeline.png")}">
<p class=src>Каждая полоса — объявление: от публикации до снятия или до сегодня. Слева — площадь и этаж. Треугольник — изменение цены.</p>

<section class=pb><h1>1. Снятые лоты — что и почём ушло</h1>
<table class=t><thead><tr><th>ЖК</th><th>ID ЦИАН</th><th>Тип</th><th class=r>м²</th><th class=r>Эт.</th><th class=r>Цена в объявл., млн</th>
<th class=r>Млн/м²</th><th>Выставлен</th><th>Снят</th><th class=r>Дней</th><th>Итог</th></tr></thead><tbody>{sold_rows}</tbody></table>
<p class=src>Снятые лоты найдены по соседним номерам объявлений: застройщик выгружает лоты пачкой (проверено 86 из 106 номеров, в пачках
застройщика — все, кроме одного). Цена — последняя в объявлении, реальная цена сделки обычно ниже: у нас — на {m(-100 * disc, 1)}%.
Лоты, проданные до сентября 2025 года (около {k['before']} в «Кло 17» и {l['before']} в «Люче»), так найти нельзя.</p>

<h1>2. Изменения цены</h1>
<table class=t><thead><tr><th>ЖК</th><th>Тип</th><th class=r>м²</th><th class=r>Эт.</th><th class=r>Было, млн</th><th class=r>Стало, млн</th>
<th class=r>Изменение</th><th>Когда</th></tr></thead><tbody>{ch_rows}</tbody></table>
<p class=src>Ещё у {small} лотов «Кло 17» цена 09.07.2026 сдвинулась меньше чем на 0,3%. У остальных лотов изменений нет.
В «Люче» у большинства лотов история цены на ЦИАН начинается 06.09.2026 — более ранние изменения, если были, не видны.</p>

<h1>3. Что это значит для нашей продажи</h1>
<ul>
<li><b>Реальный уровень цены — прайс минус 10–12%.</b> Так мы купили сами; так, скорее всего, продаёт застройщик и сейчас — на фоне скидок
по рынку 8,4% и беспроцентной рассрочки при взносе от 30%.</li>
<li><b>Наш сегмент живой.</b> 1-спальные квартиры около 100 м² на 3–5 этажах уходят: снята наша, 96,85 м² на 5-м этаже и 1-спальные в «Люче».
Конкуренты — лоты застройщика на 2-м этаже (96,9 и 105,3 м² по 2,40 млн/м²), которые не продаются больше года.</li>
<li><b>Быстро продаётся то, что дешевле прайса.</b> Переуступка в «Люче» на 6–8% ниже прайса висит 17 месяцев. Наша цена должна быть ниже не прайса,
а реальной цены застройщика в сделке.</li>
</ul>'''
    css = R.CSS + '.kpis { grid-template-columns: repeat(4, 1fr); }'
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{css}</style></head><body>{body}</body></html>'


def write_pdf(html):
    open(HTML, 'w').write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath(HTML))
        pg.wait_for_timeout(1000)
        pg.pdf(path=PDF, format='A4', print_background=True, margin=dict(top='17mm', bottom='14mm', left='16mm', right='15mm'),
               display_header_footer=True,
               header_template='<div style="font-family:Carlito,sans-serif;font-size:7.5pt;color:#555;width:100%;padding:0 15mm 0 16mm;'
                               f'display:flex;justify-content:space-between"><span>«Кло 17» и «Люче»: история лотов</span><span>{DATE}</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    rows = lots()
    sm = summary(rows)
    timeline_png(rows, f'{D}/our/timeline.png')
    write_xlsx(rows, sm)
    write_pdf(build_html(rows, sm))
    for r in rows:
        print(r['jk'], r['id'], r['outcome'], r['kind'], r['area'], r['floor'], round((r['first'] or 0) / 1e6, 1), '→',
              round((r['last'] or 0) / 1e6, 1), f"{100 * r['change']:+.1f}%", r['change_date'], r['created'], r['removed'], r['days'])
    for jk, s in sm.items():
        print(jk, {k: (len(v) if isinstance(v, list) else v) for k, v in s.items()})
