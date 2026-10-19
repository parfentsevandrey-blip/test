#!/usr/bin/env python3
"""Продажа квартиры в «Кло 17» (уступка ДДУ, без отделки): рынок в ЖК и вокруг, цена продажи за 8 месяцев, паспорт сделки.

Данные: clos17.py (ЦИАН), clos17-data/our/deal.json (условия ДДУ — не в git).
python3 clos17_report.py → reports/private/Кло17_продажа.pdf, Кло17_аналитика.xlsx, Паспорт_сделки_Кло17.docx
"""
import collections
import copy
import datetime as dt
import json
import math
import os
import re
import statistics as st
from html import escape

import numpy as np

import stromynka_report as R
from stromynka_report import m

D = 'clos17-data'
OUT = 'reports/private'
PDF, XLSX, DOCX, HTML = (f'{OUT}/Кло17_продажа.pdf', f'{OUT}/Кло17_аналитика.xlsx', f'{OUT}/Паспорт_сделки_Кло17.docx',
                         f'{OUT}/clos17.html')
TEMPLATE = f'{D}/our/passport_template.docx'     # форма паспорта сделки (как по Кутузовскому) — не в git
TODAY = dt.date(2026, 10, 19)
DATE = '19 октября 2026 г.'
DEAL = json.load(open(f'{D}/our/deal.json'))
AREA, FLOOR = DEAL['area'], DEAL['floor']
# сценарии продажи: (название, цена, дата сделки)
SCEN = [('Уступка за 8 мес.', 235_000_000, dt.date(2027, 6, 19)),
        ('Быстрая продажа, 3–4 мес.', 225_000_000, dt.date(2027, 2, 19))]
ASK = 249_000_000                                  # цена объявления на старте
LADDER = [('ноябрь–декабрь 2026', 249), ('январь–февраль 2027', 242), ('март–апрель 2027', 238), ('май–июнь 2027', 232)]
COSTS = dict(duty=6_000, extra=100_000, agent=0.03)   # госпошлина за уступку (организация), аккредитив и юр. сопровождение, агент
VAT, PROFIT_TAX = 0.22, 0.25
NIKITSKY_FINISH = (200_000, 300_000)               # отделка делюкс-квартиры, ₽/м² — для сравнения с лотами «с отделкой»


def age(x):
    try:
        return (TODAY - dt.date.fromisoformat((x.get('created') or '')[:10])).days
    except ValueError:
        return None


def load():
    J = json.load(open(f'{D}/jk.json'))
    N = json.load(open(f'{D}/new.json'))
    Rs = json.load(open(f'{D}/resale.json'))
    C = json.load(open(f'{D}/cards.json'))
    return J, N, Rs, C


def list_equivalent(J):
    """Цена нашей квартиры в прайсе застройщика: log(цена м²) ~ этаж + log(площадь) по лотам «Кло 17»."""
    xs = [x for x in J.values() if x['jk'] == 'Кло 17']
    X = np.array([[1, x['floor'], math.log(x['area'])] for x in xs])
    y = np.array([math.log(x['ppsqm']) for x in xs])
    b, *_ = np.linalg.lstsq(X, y, rcond=None)
    res = y - X @ b
    r2 = 1 - res @ res / ((y - y.mean()) @ (y - y.mean()))
    return math.exp(np.array([1, FLOOR, math.log(AREA)]) @ b), 100 * (math.exp(b[1]) - 1), r2


def economics(price, date):
    """Строки паспорта сделки по шаблону: налоги, расходы, доходность."""
    buy = DEAL['price']
    vat = max(0, price - buy) * VAT / (1 + VAT)             # п. 3 ст. 155 НК РФ: база — разница при уступке прав на жильё
    agent = price * COSTS['agent']
    costs = buy + COSTS['duty'] + COSTS['extra'] + agent
    pbt = price - vat - costs
    tax = max(0, pbt) * PROFIT_TAX
    net = pbt - tax
    months = (date - dt.date.fromisoformat(DEAL['ddu_date'])).days / 30.4375
    rent = 100 * net / costs
    return dict(price=price, ppsqm=price / AREA, vat=vat, tax=tax, taxes=vat + tax, agent=agent, costs=costs, net=net,
                months=months, rent=rent, annual=rent * 12 / months, revenue_ex_vat=price - vat)


def breakeven():
    buy, extra = DEAL['price'], COSTS['duty'] + COSTS['extra']
    k = VAT / (1 + VAT)
    return (buy + extra - buy * k) / (1 - COSTS['agent'] - k)


def nearby_primary(N):
    xs = [x for x in N.values() if x['km_obj'] is not None and x['km_obj'] <= 2.0 and 85 <= x['area'] <= 130
          and 180e6 <= x['price'] <= 320e6 and x['jk'] not in ('Кло 17', 'Звёзды Арбата')]
    return sorted(xs, key=lambda x: x['ppsqm'])


def nearby_resale(Rs):
    xs = [x for x in Rs.values() if x['km_obj'] is not None and x['km_obj'] <= 1.5 and 85 <= x['area'] <= 130
          and (x['year'] or 0) >= 2000 and x['ppsqm'] and x['price'] <= 400e6]
    seen, out = set(), []
    for x in sorted(xs, key=lambda x: x['km_obj']):
        key = (x['address'], round(x['area']), x['price'])
        if key not in seen:
            seen.add(key)
            out.append(x)
    return out


def assignments(N):
    """Переуступки в новостройках ЦАО дороже 1,5 млн ₽/м²: цена против медианы застройщика в том же ЖК и срок в продаже."""
    g = collections.defaultdict(list)
    for x in N.values():
        g[x['jk']].append(x)
    rows = []
    for jk, v in g.items():
        dev = [x for x in v if x['developer'] and x['ppsqm']]
        if len(dev) < 3:
            continue
        med = st.median(x['ppsqm'] for x in dev)
        if med < 1.5e6:
            continue
        for x in v:
            if not x['developer'] and x['ppsqm'] and age(x) is not None:
                rows.append(dict(jk=jk, disc=x['ppsqm'] / med - 1, age=age(x), area=x['area'], ppsqm=x['ppsqm'], med=med, url=x['url'], id=x['id']))
    return rows


# ---------- графика ----------

def floor_png(J, L, out):
    matplotlib, plt = R.plt_setup()
    xs = [x for x in J.values() if x['jk'] == 'Кло 17']
    fig, ax = plt.subplots(figsize=(7.4, 3.3), dpi=200)
    fig.patch.set_facecolor(R.SURF)
    ax.set_facecolor(R.SURF)
    cols = {1: R.BLUE, 2: '#8fb3e0', 3: '#c9c7bf'}
    labs = {1: '1 спальня (82–106 м²)', 2: '2 спальни (152–154 м²)', 3: '3 спальни (219–220 м²)'}
    rng = np.random.default_rng(2)
    for r in (1, 2, 3):
        pts = [(x['floor'] + rng.uniform(-0.12, 0.12), x['ppsqm'] / 1e6) for x in xs if x['rooms'] == r]
        ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=46, color=cols[r], edgecolor=R.SURF, linewidth=1, zorder=3, label=labs[r])
    marks = [(L / 1e6, 'наша квартира в прайсе застройщика', R.INK2, 'o', 0),
             (DEAL['ppsqm'] / 1e6, 'цена по ДДУ', R.INK, 's', 0.035),
             (SCEN[0][1] / AREA / 1e6, 'продажа за 8 мес.', R.ORANGE, 'D', -0.035)]
    for v, lab, col, mk, dy in marks:
        ax.scatter([FLOOR + 0.32], [v], s=70, color=col, marker=mk, zorder=5, edgecolor=R.SURF, linewidth=1)
        ax.text(FLOOR + 0.45, v + dy, f'{lab}: {m(v, 2)}', va='center', fontsize=8.5, color=col)
    ax.set_xticks([2, 3, 4, 5])
    ax.set_xlim(1.6, 6.3)
    ax.set_ylim(2.0, 3.2)
    ax.set_xlabel('Этаж', color=R.INK2)
    ax.set_ylabel('Цена в прайсе, млн руб. за м²', color=R.INK2)
    ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: m(v, 1)))
    ax.grid(axis='y', color=R.GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right'):
        ax.spines[s].set_visible(False)
    for s in ('left', 'bottom'):
        ax.spines[s].set_color(R.GRID)
    ax.tick_params(colors=R.INK2)
    ax.legend(loc='upper left', frameon=False, fontsize=8.3, labelcolor=R.INK2)
    fig.tight_layout()
    fig.savefig(out, facecolor=R.SURF)
    plt.close(fig)


def market_png(prim, res, out):
    """Цена м² альтернатив в бюджете: первичка (с отделкой и без) и готовая вторичка в клубных домах."""
    matplotlib, plt = R.plt_setup()
    fig, ax = plt.subplots(figsize=(7.4, 2.7), dpi=200)
    fig.patch.set_facecolor(R.SURF)
    ax.set_facecolor(R.SURF)
    rng = np.random.default_rng(5)
    groups = [('Новостройки без отделки', [x for x in prim if x['decoration'] != 'fine'], R.BLUE, 2),
              ('Новостройки с отделкой', [x for x in prim if x['decoration'] == 'fine'], R.AQUA, 1),
              ('Вторичка в домах 2000+ г.', res, '#9a968c', 0)]
    for lab, xs, col, y in groups:
        ax.scatter([x['ppsqm'] / 1e6 for x in xs], [y + rng.uniform(-0.18, 0.18) for _ in xs], s=34, color=col, alpha=0.85,
                   edgecolor=R.SURF, linewidth=0.8, zorder=3)
    ax.set_yticks([0, 1, 2])
    ax.set_yticklabels([g[0] for g in groups][::-1], fontsize=8.5, color=R.INK2)
    for v, lab, col, ls, ha in ((DEAL['ppsqm'] / 1e6, ' ДДУ', R.INK, ':', 'left'), (SCEN[0][1] / AREA / 1e6, 'продажа за 8 мес. ', R.ORANGE, '-', 'right')):
        ax.axvline(v, color=col, lw=1.5, ls=ls, zorder=2)
        ax.text(v, 2.45, lab, ha=ha, fontsize=8.5, color=col)
    ax.set_xlim(1.0, 4.0)
    ax.set_ylim(-0.5, 2.6)
    ax.set_xlabel('Цена в объявлении, млн руб. за м² (лоты 85–130 м²)', color=R.INK2)
    ax.xaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: m(v, 1)))
    ax.grid(axis='x', color=R.GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right', 'left'):
        ax.spines[s].set_visible(False)
    ax.spines['bottom'].set_color(R.GRID)
    ax.tick_params(colors=R.INK2, length=0)
    fig.tight_layout()
    fig.savefig(out, facecolor=R.SURF)
    plt.close(fig)


# ---------- паспорт сделки (docx по шаблону) ----------

def set_cell(cell, text):
    p = cell.paragraphs[0]
    if p.runs:
        p.runs[0].text = text
        for r in p.runs[1:]:
            r.text = ''
    else:
        p.add_run(text)
    for extra in cell.paragraphs[1:]:
        extra._element.getparent().remove(extra._element)


def fmt(v, d=0):
    return m(v, d).replace('-', '−')


def write_passport(ec):
    import docx
    import docx.text.paragraph
    d = docx.Document(TEMPLATE)
    t = d.tables[0]
    rows = t.rows
    a, b = ec
    vals = {
        2: ('Уступка, 8 мес.', 'Уступка, 3–4 мес.'),
        3: ('MR Group',) * 2,
        4: (f"кв. {DEAL['flat_no'].split()[0]} (усл.), {FLOOR} этаж, Староваганьковский пер., 17, «{DEAL['jk']}»",) * 2,
        5: (m(AREA, 1),) * 2,
        8: ('ДДУ',) * 2,
        9: (m(DEAL['ppsqm']),) * 2,
        10: (m(DEAL['price']),) * 2,
        11: ('нет — дом строится',) * 2,
        12: ('31.10.2025–29.12.2026, 6 платежей',) * 2,
        14: (m(a['price']), m(b['price'])),
        15: (m(a['ppsqm']), m(b['ppsqm'])),
        17: (m(a['taxes']), m(b['taxes'])),
        18: (m(a['vat']), m(b['vat'])),
        19: (m(a['tax']), m(b['tax'])),
        20: (m(COSTS['duty']),) * 2,
        21: ('0',) * 2,
        22: ('0',) * 2,
        23: (m(COSTS['extra']),) * 2,
        24: (m(a['agent']), m(b['agent'])),
        25: ('0',) * 2,
        26: (m(a['months'], 1), m(b['months'], 1)),
        27: (fmt(a['annual'], 2), fmt(b['annual'], 2)),
        28: (fmt(a['rent'], 2), fmt(b['rent'], 2)),
        29: (fmt(a['net']), fmt(b['net'])),
        33: (f"все расходы: {m(a['costs'])}", f"все расходы: {m(b['costs'])}"),
        34: (f"выручка без НДС: {m(a['revenue_ex_vat'])}", f"выручка без НДС: {m(b['revenue_ex_vat'])}"),
    }
    for i, (va, vb) in vals.items():
        set_cell(rows[i].cells[2], va)
        set_cell(rows[i].cells[4], vb)
    set_cell(rows[13].cells[0], '3. Условия продажи объекта (уступка прав по ДДУ)')
    # строки ремонта, мебели и общей прибыли по двум объектам к этой сделке не относятся
    for i in sorted([30, 31, 32] + list(range(35, len(rows))), reverse=True):
        tr = rows[i]._tr
        tr.getparent().remove(tr)
    notes = [p for p in d.paragraphs if p.text.strip()]
    texts = [
        f"Застройщик — {DEAL['developer']}; ДДУ № {DEAL['ddu']} от {dt.date.fromisoformat(DEAL['ddu_date']):%d.%m.%Y}.",
        f"* Срок реализации — с даты ДДУ {dt.date.fromisoformat(DEAL['ddu_date']):%d.%m.%Y}; дата уступки принята "
        f"{SCEN[0][2]:%d.%m.%Y} (8 месяцев) и {SCEN[1][2]:%d.%m.%Y} (быстрая продажа).",
        f"Продаём право требования по ДДУ — квартиру без отделки, до передаточного акта. До уступки нужно внести последний платёж "
        f"{m(DEAL['payments'][-1][1])} руб. (срок — 29.12.2026) или переводить долг на покупателя с согласия застройщика. "
        f"Застройщика уведомить в течение 3 рабочих дней после регистрации уступки (п. 3.4.2 ДДУ).",
        f"НДС: при уступке прав на жильё база — разница между ценой уступки и ценой ДДУ (п. 3 ст. 155 НК РФ), ставка 22/122; "
        f"при цене не выше {m(DEAL['price'])} руб. НДС нет. Налог на прибыль — 25% × (сумма договора − НДС − все расходы), при убытке — 0. "
        f"Госпошлина за регистрацию уступки для организации — 6 000 руб. (пп. 30 п. 1 ст. 333.33 НК РФ); налога на имущество и "
        f"коммунальных платежей до передаточного акта нет. Комиссия агента — 3%.",
        f"Цена объявления на старте — {m(ASK)} руб. ({m(ASK / AREA)} руб./м²). Продажа в ноль — {m(round(breakeven(), -5))} руб. "
        f"({m(breakeven() / AREA)} руб./м²): до этой цены сделка убыточна из-за комиссии агента, выше — появляется НДС с разницы.",
    ]
    while len(notes) < len(texts):                     # в шаблоне три примечания — недостающие копируем с последнего
        el = copy.deepcopy(notes[-1]._element)
        notes[-1]._element.addnext(el)
        notes.append(docx.text.paragraph.Paragraph(el, notes[-1]._parent))
    for p, txt in zip(notes, texts):
        set_cell_par(p, txt)
    for p in notes[len(texts):]:
        p._element.getparent().remove(p._element)
    d.save(DOCX)


def set_cell_par(p, text):
    if p.runs:
        p.runs[0].text = text
        for r in p.runs[1:]:
            r.text = ''
    else:
        p.add_run(text)


# ---------- Excel ----------

def write_xlsx(J, N, Rs, C, prim, res, asg, L):
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter as Lc
    F = 'Arial'
    bold, font = Font(name=F, bold=True, size=9), Font(name=F, size=9)
    blue, note = Font(name=F, size=9, color='0000FF'), Font(name=F, size=8.5, color='555555')
    grid = Side(style='thin', color='C8CDD3')
    border = Border(left=grid, right=grid, top=grid, bottom=grid)
    head = PatternFill('solid', fgColor='DDE3EA')
    yellow = PatternFill('solid', fgColor='FFF2CC')
    wb = Workbook()
    ws = wb.active
    ws.title = 'Расчёт продажи'
    ws.column_dimensions['A'].width = 56
    for c in 'BCD':
        ws.column_dimensions[c].width = 18
    ws.column_dimensions['E'].width = 60
    ws['A1'] = f"«{DEAL['jk']}», кв. {DEAL['flat_no'].split()[0]}: уступка прав по ДДУ без отделки — расчёт сделки"
    ws['A1'].font = Font(name=F, bold=True, size=11)
    ws['A2'] = 'Синий шрифт — допущения, их можно менять. Жёлтым — цены продажи по сценариям.'
    ws['A2'].font = note
    inputs = [
        ('Площадь по ДДУ, м²', AREA, '0.0', 'ДДУ, п. 1.2'),
        ('Цена по ДДУ, ₽', DEAL['price'], '#,##0', 'ДДУ, п. 4.2; НДС не облагается'),
        ('Ставка НДС', VAT, '0%', 'С разницы между ценой уступки и ценой ДДУ (п. 3 ст. 155 НК РФ)'),
        ('Налог на прибыль', PROFIT_TAX, '0%', None),
        ('Комиссия агента, доля цены', COSTS['agent'], '0.0%', None),
        ('Госпошлина за регистрацию уступки, ₽', COSTS['duty'], '#,##0', 'Организация, пп. 30 п. 1 ст. 333.33 НК РФ'),
        ('Аккредитив, выписки, юр. сопровождение, ₽', COSTS['extra'], '#,##0', 'Оценка'),
        ('Цена в прайсе застройщика за такую квартиру, ₽/м²', round(L), '#,##0', 'Модель по 16 лотам «Кло 17»: этаж и площадь'),
    ]
    for i, (lab, v, f_, src) in enumerate(inputs, 4):
        ws.cell(i, 1, lab).font = font
        c = ws.cell(i, 2, v)
        c.font, c.number_format = blue, f_
        if src:
            ws.cell(i, 5, src).font = note
    r0 = 4 + len(inputs) + 1
    ws.cell(r0, 1, 'СЦЕНАРИИ').font = bold
    hdr = ['', SCEN[0][0], SCEN[1][0], 'Цена объявления']
    for j, h in enumerate(hdr, 1):
        ws.cell(r0 + 1, j, h).font = bold
    prices = [SCEN[0][1], SCEN[1][1], ASK]
    dates = [SCEN[0][2], SCEN[1][2], dt.date(2027, 10, 19)]
    lines = [
        ('Цена уступки, ₽', None, '#,##0'),
        ('Дата сделки', None, 'DD.MM.YYYY'),
        ('Цена за м², ₽', '={c}{p}/$B$4', '#,##0'),
        ('К цене в прайсе застройщика', '={c}{p}/$B$4/$B$11-1', '0.0%'),
        ('НДС с разницы, ₽', '=MAX(0,{c}{p}-$B$5)*$B$6/(1+$B$6)', '#,##0'),
        ('Комиссия агента, ₽', '={c}{p}*$B$8', '#,##0'),
        ('Все расходы (цена ДДУ, пошлина, сопровождение, агент), ₽', '=$B$5+$B$9+$B$10+{c}{ag}', '#,##0'),
        ('Прибыль до налога, ₽', '={c}{p}-{c}{vat}-{c}{cost}', '#,##0'),
        ('Налог на прибыль, ₽', '=MAX(0,{c}{pbt})*$B$7', '#,##0'),
        ('Чистая прибыль, ₽', '={c}{pbt}-{c}{tx}', '#,##0'),
        ('Срок с даты ДДУ, мес.', '=({c}{d}-DATE(2025,10,31))/30.4375', '0.0'),
        ('Рентабельность, % (ЧП / все расходы)', '={c}{net}/{c}{cost}', '0.00%'),
        ('Годовая доходность, % (рентабельность × 12 / срок)', '={c}{rent}*12/{c}{mo}', '0.00%'),
    ]
    keys = ['p', 'd', 'pp', 'vsl', 'vat', 'ag', 'cost', 'pbt', 'tx', 'net', 'mo', 'rent', 'yr']
    rows = {k: r0 + 2 + i for i, k in enumerate(keys)}
    for k, (lab, f_, nf) in zip(keys, lines):
        ws.cell(rows[k], 1, lab).font = bold if k == 'net' else font
        for j in range(3):
            c = Lc(2 + j)
            if k == 'p':
                cell = ws.cell(rows[k], 2 + j, prices[j])
                cell.font, cell.fill = blue, yellow
            elif k == 'd':
                cell = ws.cell(rows[k], 2 + j, dates[j])
                cell.font = blue
            else:
                cell = ws.cell(rows[k], 2 + j, f_.format(c=c, **rows))
                cell.font = Font(name=F, size=9, bold=k == 'net')
            cell.number_format = nf
    rb = rows['yr'] + 2
    ws.cell(rb, 1, 'Цена продажи в ноль (после НДС с разницы и комиссии), ₽').font = bold
    ws.cell(rb, 2, '=($B$5+$B$9+$B$10-$B$5*$B$6/(1+$B$6))/(1-$B$8-$B$6/(1+$B$6))').number_format = '#,##0'
    ws.cell(rb, 2).font = bold
    ws.cell(rb + 1, 1, 'То же за м², ₽').font = font
    ws.cell(rb + 1, 2, f'=B{rb}/$B$4').number_format = '#,##0'
    ws.cell(rb + 3, 1, 'ЛЕСТНИЦА ЦЕНЫ В ОБЪЯВЛЕНИИ').font = bold
    for i, (per, v) in enumerate(LADDER, rb + 4):
        ws.cell(i, 1, per).font = font
        c = ws.cell(i, 2, v * 1_000_000)
        c.font, c.number_format = blue, '#,##0'
        ws.cell(i, 3, f'=B{i}/$B$4').number_format = '#,##0'

    def table(title, cols, data):
        sh = wb.create_sheet(title)
        for k, (h, w) in enumerate(cols, 1):
            c = sh.cell(1, k, h)
            c.font, c.fill, c.border = bold, head, border
            c.alignment = Alignment(wrap_text=True, vertical='center', horizontal='center')
            sh.column_dimensions[Lc(k)].width = w
        sh.row_dimensions[1].height = 30
        for n, (vals, url) in enumerate(data, 2):
            for k, v in enumerate(vals, 1):
                c = sh.cell(n, k, v)
                c.font, c.border = font, border
                c.alignment = Alignment(vertical='top', wrap_text=True)
            if url:
                sh.cell(n, 1).hyperlink = url
                sh.cell(n, 1).font = Font(name=F, size=9, color='1F4E9A', underline='single')
        sh.freeze_panes = 'B2'
        sh.auto_filter.ref = f'A1:{Lc(len(cols))}{len(data) + 1}'
    kc = lambda x: C.get(str(x['id'])) or {}
    first = lambda x: (kc(x).get('price_changes') or [[None, None]])[0]
    table('Лоты «Кло 17» и «Люче»', [('ID ЦИАН', 11), ('ЖК', 18), ('Спален', 7), ('Площадь, м²', 9), ('Этаж', 6), ('Цена, млн ₽', 10),
                                     ('₽/м²', 11), ('Продавец', 12), ('В продаже с', 11), ('Первая цена, млн ₽', 10), ('Отделка', 10), ('Просмотры', 22)],
          [([x['id'], x['jk'], x['rooms'], x['area'], x['floor'], round(x['price'] / 1e6, 1), x['ppsqm'],
             'застройщик' if x['developer'] else 'переуступка', (x['created'] or '')[:10],
             round(first(x)[1] / 1e6, 1) if first(x)[1] else None, x['decoration'], (kc(x).get('stats') or {}).get('totalViewsFormattedString')],
            x['url'].split('?')[0]) for x in sorted(J.values(), key=lambda x: (x['jk'], x['floor'], x['area']))])
    table('Первичка рядом (2 км)', [('ID ЦИАН', 11), ('ЖК', 26), ('Площадь, м²', 9), ('Этаж', 6), ('Цена, млн ₽', 10), ('₽/м²', 11),
                                    ('Отделка', 10), ('Сдача', 10), ('Апартаменты', 9), ('Продавец', 12), ('От «Кло 17», км', 9), ('Метро', 22)],
          [([x['id'], x['jk'], x['area'], x['floor'], round(x['price'] / 1e6, 1), x['ppsqm'], x['decoration'],
             f"{(x['finish'] or {}).get('quarter')} кв. {(x['finish'] or {}).get('year')}" if x['finish'] else None, 'да' if x['apart'] else 'нет',
             'застройщик' if x['developer'] else 'переуступка', x['km_obj'], f"{x['metro']}, {x['metro_min']} мин"], x['url'].split('?')[0]) for x in prim])
    table('Вторичка рядом (1,5 км)', [('ID ЦИАН', 11), ('ЖК', 24), ('Адрес', 24), ('Год дома', 8), ('Площадь, м²', 9), ('Этаж', 6),
                                      ('Цена, млн ₽', 10), ('₽/м²', 11), ('От «Кло 17», км', 9), ('В продаже с', 11)],
          [([x['id'], x['jk'], x['address'], x['year'], x['area'], x['floor'], round(x['price'] / 1e6, 1), x['ppsqm'], x['km_obj'],
             (x['created'] or '')[:10]], x['url'].split('?')[0]) for x in res])
    table('Переуступки ЦАО', [('ID ЦИАН', 11), ('ЖК', 28), ('Площадь, м²', 9), ('₽/м²', 11), ('Медиана застройщика в ЖК, ₽/м²', 13),
                               ('К цене застройщика', 10), ('Дней в продаже', 9)],
          [([r['id'], r['jk'], r['area'], r['ppsqm'], round(r['med']), round(r['disc'], 3), r['age']], r['url'].split('?')[0])
           for r in sorted(asg, key=lambda r: r['disc'])])
    wb.save(XLSX)


# ---------- PDF ----------

def build_html(J, N, Rs, C, prim, res, asg, L, step, r2, ec):
    S = lambda v: escape(str(v)) if v is not None else '—'
    a, b = ec
    clos = sorted([x for x in J.values() if x['jk'] == 'Кло 17'], key=lambda x: (x['floor'], x['area']))
    luce = [x for x in J.values() if x['jk'] != 'Кло 17']
    luce_ass = [x for x in luce if not x['developer']]
    paid = sum(p[1] for p in DEAL['payments'] if dt.date.fromisoformat(p[0]) <= TODAY)
    left = DEAL['price'] - paid
    kc = lambda x: C.get(str(x['id'])) or {}
    first = lambda x: (kc(x).get('price_changes') or [[None, None]])[0]
    since = lambda x: dt.date.fromisoformat((x['created'] or '')[:10]).strftime('%m.%Y') if x['created'] else '—'
    cut = [x for x in clos if first(x)[1] and x['price'] / first(x)[1] - 1 < -0.05]
    old = [x for x in clos if age(x) and age(x) > 365]
    be = breakeven()
    res_ppsqm = sorted(x['ppsqm'] for x in res)
    res_q = [res_ppsqm[int(len(res_ppsqm) * q)] for q in (0.25, 0.5, 0.75)]
    nik = [x for x in prim if x['jk'] == 'Никитский-6']
    nik_pp = st.median(x['ppsqm'] for x in nik) if nik else None
    lav = [x for x in prim if x['jk'] == 'Дом Лаврушинский']
    big = [r for r in asg if r['disc'] > -0.07]
    big_old = [r for r in big if r['age'] > 240]
    clos_rows = ''.join(
        f'<tr><td><a href="{x["url"].split("?")[0]}">{x["id"]}</a></td><td class=r>{x["rooms"]}</td><td class=r>{m(x["area"], 1)}</td>'
        f'<td class=r>{x["floor"]}</td><td class=r>{m(x["price"] / 1e6, 1)}</td><td class=r>{m(x["ppsqm"] / 1e6, 2)}</td><td>{since(x)}</td>'
        f'<td class=r>{m(first(x)[1] / 1e6, 1) if first(x)[1] else "—"}</td></tr>' for x in clos)
    prim_rows = ''.join(
        f'<tr><td><a href="{x["url"].split("?")[0]}">{escape(x["jk"][:30])}</a></td><td class=r>{m(x["area"], 1)}</td><td class=r>{x["floor"]}</td>'
        f'<td class=r>{m(x["price"] / 1e6)}</td><td class=r>{m(x["ppsqm"] / 1e6, 2)}</td>'
        f'<td>{"с отделкой" if x["decoration"] == "fine" else "без отделки"}{", апарт." if x["apart"] else ""}</td>'
        f'<td>{S((x["finish"] or {}).get("year"))}</td><td class=r>{m(x["km_obj"], 1)}</td></tr>' for x in sorted(prim, key=lambda x: x["km_obj"])[:13])
    res_rows = ''.join(
        f'<tr><td><a href="{x["url"].split("?")[0]}">{escape(x["jk"] or x["address"])[:34]}</a></td><td class=r>{x["year"]}</td>'
        f'<td class=r>{m(x["area"], 1)}</td><td class=r>{m(x["price"] / 1e6)}</td><td class=r>{m(x["ppsqm"] / 1e6, 2)}</td>'
        f'<td class=r>{m(x["km_obj"], 1)}</td></tr>' for x in [x for x in res if 1.4e6 <= x['ppsqm'] <= 3.2e6][:12])
    pay_rows = ''.join(f'<tr><td>{dt.date.fromisoformat(d):%d.%m.%Y}</td><td class=r>{m(v)}</td>'
                       f'<td>{"оплачено" if dt.date.fromisoformat(d) <= TODAY else "предстоит"}</td></tr>' for d, v, _ in DEAL['payments'])
    ladder = ''.join(f'<tr><td>{p}</td><td class=r>{v} млн</td><td class=r>{m(v * 1e6 / AREA / 1e6, 2)} млн/м²</td></tr>' for p, v in LADDER)
    pp8 = SCEN[0][1] / AREA

    def pr(e):
        return (f'<td class=r>{m(e["price"])}</td><td class=r>{m(e["ppsqm"])}</td><td class=r>{m(e["vat"])}</td><td class=r>{m(e["agent"])}</td>'
                f'<td class=r>{fmt(e["net"])}</td><td class=r>{fmt(e["rent"], 1)}%</td><td class=r>{fmt(e["annual"], 1)}%</td>')
    css = R.CSS + '.kpis { grid-template-columns: repeat(4, 1fr); } .pos { color:#1a7f4b } .neg { color:#b3261e }'
    body = f'''
<div class=t1>«{DEAL["jk"]}», кв. {DEAL["flat_no"].split()[0]}: за сколько продать за 8 месяцев</div>
<div class=t2>{m(AREA, 1)} м², {FLOOR} этаж, без отделки · уступка прав по ДДУ · рынок ЦИАН на {DATE}</div>
<div class=kpis>
<div class=kpi><div class=v>{m(SCEN[0][1] / 1e6)} млн</div><div class=l>реальная цена продажи за 8 мес.<br>({m(pp8 / 1e6, 2)} млн/м²), коридор 230–240</div></div>
<div class=kpi><div class=v>{m(ASK / 1e6)} млн</div><div class=l>цена в объявлении на старте,<br>затем снижать по плану</div></div>
<div class=kpi><div class=v>{m(L * AREA / 1e6)} млн</div><div class=l>столько стоила бы квартира в прайсе<br>застройщика ({m(L / 1e6, 2)} млн/м²)</div></div>
<div class=kpi><div class="v neg">{fmt(a["net"] / 1e6, 1)} млн</div><div class=l>результат сделки за 8 мес.<br>после комиссии агента 3%</div></div>
</div>
<div class=box>
<p><b>За 8 месяцев квартиру реально продать примерно за {m(SCEN[0][1] / 1e6)} млн ({m(pp8 / 1e6, 2)} млн ₽/м²) — это около цены ДДУ
({m(DEAL["price"] / 1e6, 2)} млн).</b> После комиссии агента сделка даёт убыток около {m(-a["net"] / 1e6, 1)} млн. В ноль выходим при цене
{m(be / 1e6, 1)} млн ({m(be / AREA / 1e6, 2)} млн/м²); выше этой цены уже появляется НДС с разницы.</p>
<p><b>Почему не дороже, хотя в прайсе застройщика такая квартира стоит {m(L * AREA / 1e6)} млн.</b> Прайс «{DEAL["jk"]}» почти не менялся с сентября
2025 года, и {len(old)} из {len(clos)} лотов застройщика висят на ЦИАН больше года. Самую маленькую квартиру MR Group в июле 2026 года уценила на
{m(-100 * (cut[0]["price"] / first(cut[0])[1] - 1)) if cut else "—"}%. Мы сами купили на {m(100 * (1 - DEAL["ppsqm"] / L))}% ниже нынешнего прайса,
значит, реальные цены сделок у застройщика заметно ниже прайса. Покупатель сравнит нас с лотами застройщика и с готовыми квартирами рядом. Готовые квартиры с ремонтом
в клубных домах в 1,5 км стоят в среднем {m(res_q[1] / 1e6, 1)} млн/м², «Никитский-6» с отделкой — около {m(nik_pp / 1e6, 2) if nik_pp else "—"} млн/м².
Сделок с элитными новостройками в III квартале 2026 года стало почти на 40% меньше, чем годом раньше.</p>
<p><b>Как продавать:</b> выставить за {m(ASK / 1e6)} млн и снижать цену каждые 2 месяца по плану в разделе 5. Последний платёж
{m(left / 1e6, 2)} млн внести до 29.12.2026: уступка возможна только после полной оплаты. Ниже {m(SCEN[1][1] / 1e6)} млн не опускаться,
это цена быстрой продажи.</p></div>

<h1>1. Квартира и договор</h1>
<div class=two><div>
<table class=t><tbody>
<tr><td>Договор</td><td>ДДУ № {DEAL["ddu"]} от 31.10.2025, покупатель — {DEAL["participant"]}</td></tr>
<tr><td>Застройщик</td><td>{DEAL["developer"]}; эскроу — {DEAL["escrow_bank"]}</td></tr>
<tr><td>Дом</td><td>Клубный дом «{DEAL["jk"]}», Староваганьковский пер., 17 — 26 квартир и 2 пентхауса, 8–9 этажей по ДДУ, подземный паркинг;
{DEAL["building"].split(", ", 1)[1]}. Передача квартиры — не позднее 31.12.2027 (на ЦИАН застройщик указывает I кв. 2027)</td></tr>
<tr><td>Квартира</td><td>Усл. № {DEAL["flat_no"].split()[0]}, {FLOOR} этаж, {m(AREA, 1)} м²: {DEAL["rooms"]}, две гардеробные, постирочная, ванная и санузел</td></tr>
<tr><td>Отделка</td><td>Без отделки: нет перегородок, стяжки, разводки; есть окна и входная дверь</td></tr>
<tr><td>Цена</td><td>{m(DEAL["price"])} ₽ ({m(DEAL["ppsqm"])} ₽/м²), НДС не облагается</td></tr>
<tr><td>Уступка</td><td>После полной оплаты (или вместе с переводом долга), до подписания передаточного акта; согласие застройщика не нужно,
но его нужно уведомить в течение 3 рабочих дней после регистрации</td></tr>
</tbody></table></div><div>
<table class=t><thead><tr><th>Платёж</th><th class=r>Сумма, ₽</th><th></th></tr></thead><tbody>{pay_rows}</tbody></table>
<p class=src>Оплачено на {TODAY:%d.%m.%Y} — {m(paid)} ₽ (если платежи шли по графику), осталось {m(left)} ₽.</p>
</div></div>

<section class=pb><h1>2. Что продаёт застройщик в «{DEAL["jk"]}»</h1>
<img class=full src="file://{os.path.abspath(f"{D}/our/floor.png")}">
<table class=t style="margin-top:6px"><thead><tr><th>ID ЦИАН</th><th class=r>Спален</th><th class=r>Площадь, м²</th><th class=r>Этаж</th><th class=r>Цена, млн ₽</th>
<th class=r>Млн ₽/м²</th><th>В продаже с</th><th class=r>Первая цена, млн ₽</th></tr></thead><tbody>{clos_rows}</tbody></table>
<p class=src>Все лоты дома на ЦИАН — от застройщика, переуступок нет. Каждый этаж выше дороже на {m(step, 1)}% (модель по {len(clos)} лотам, R² = {m(r2, 2)}).
В соседнем «Люче» (тоже MR Private, 50 м) застройщик продаёт {len(luce) - len(luce_ass)} лотов по 2,45–3,25 млн/м².
{"Единственная переуступка там — " + m(luce_ass[0]["area"]) + " м² на " + str(luce_ass[0]["floor"]) + " этаже за " + m(luce_ass[0]["price"] / 1e6) + " млн (" + m(luce_ass[0]["ppsqm"] / 1e6, 2) + " млн/м², примерно на 7% ниже прайса застройщика) — в продаже с " + since(luce_ass[0]) + "." if luce_ass else ""}</p>

</section>
<section class=pb><h1>3. Что ещё может купить покупатель с бюджетом 200–300 млн</h1>
<img class=full src="file://{os.path.abspath(f"{D}/our/alts.png")}">
<div class=two><div>
<h2>Первичка в 2 км, 85–130 м²</h2>
<table class=t><thead><tr><th>ЖК</th><th class=r>м²</th><th class=r>Эт.</th><th class=r>Млн</th><th class=r>Млн/м²</th><th>Отделка</th><th>Сдача</th><th class=r>Км</th></tr></thead>
<tbody>{prim_rows}</tbody></table>
<p class=src>Ближе всего — «Никитский-6» (400 м, с отделкой, сдача в IV кв. 2026) и «Дом Лаврушинский» (срок сдачи — I кв. 2025, без отделки).
«Камергер» и «Ильинка» — апартаменты.</p>
</div><div>
<h2>Вторичка в 1,5 км, дома 2000+ г.</h2>
<table class=t><thead><tr><th>Дом</th><th class=r>Год</th><th class=r>м²</th><th class=r>Млн</th><th class=r>Млн/м²</th><th class=r>Км</th></tr></thead>
<tbody>{res_rows}</tbody></table>
<p class=src>{len(res)} квартир 85–130 м² в домах 2000 года и новее; половина — {m(res_q[0] / 1e6, 2)}–{m(res_q[2] / 1e6, 2)} млн/м², медиана —
{m(res_q[1] / 1e6, 2)}. Большинство с ремонтом, въезжать можно сразу.</p>
</div></div>
</section>

<h1>4. Рынок сейчас</h1>
<ul>
<li><b>Спрос на элитные новостройки упал.</b> За III квартал 2026 года сделок на 39% меньше, чем годом раньше («Метриум»), предложение выросло
на 28%, средняя скидка застройщиков — 8,4%. Покупатели ждут торга и снижения ставки.</li>
<li><b>Цены держатся.</b> Средняя цена элитных новостроек за год выросла на 6–8%, делюкс — примерно на 10%, до 3,4 млн/м². Застройщики не снижают прайс, а дают скидки в сделке.</li>
<li><b>Готовые элитные квартиры дорожают быстрее</b> — +20–22% за год («Ведомости», Kalinka, Point Estate): покупатели уходят в готовое жильё.
Это конкурент квартиры в строящемся доме без отделки.</li>
<li><b>Переуступки в элитных домах ЦАО</b> (в продаже — {len(asg)}): из тех, что дешевле застройщика меньше чем на 7% или дороже его, {len(big_old)} из {len(big)}
висят дольше 8 месяцев. Быстро уходят только заметно более дешёвые.</li>
</ul>

<h1>5. Цена и план продажи</h1>
<div class=two><div>
<table class=t><tbody>
<tr><td>Прайс застройщика за такую квартиру</td><td class=r>{m(L / 1e6, 2)} млн/м² · {m(L * AREA / 1e6)} млн</td></tr>
<tr><td>Минус реальная скидка застройщика (наша — {m(100 * (1 - DEAL["ppsqm"] / L))}%)</td><td class=r>≈ 2,25–2,35 млн/м²</td></tr>
<tr><td>«Никитский-6» с отделкой за вычетом отделки 0,2–0,3 млн/м²</td><td class=r>≈ {m((nik_pp - 3e5) / 1e6, 2) if nik_pp else "—"}–{m((nik_pp - 2e5) / 1e6, 2) if nik_pp else "—"} млн/м²</td></tr>
<tr><td>Готовая вторичка с ремонтом в клубных домах, медиана</td><td class=r>{m(res_q[1] / 1e6, 2)} млн/м²</td></tr>
<tr class=hl><td>Цена сделки за 8 месяцев</td><td class=r>{m(pp8 / 1e6, 2)} млн/м² · {m(SCEN[0][1] / 1e6)} млн</td></tr>
</tbody></table>
<p class=src>Переуступку покупают со скидкой к застройщику: нет его акций и рассрочки, покупатель платит сразу всю сумму.</p>
</div><div>
<table class=t><thead><tr><th>Период</th><th class=r>Цена в объявлении</th><th class=r>За м²</th></tr></thead><tbody>{ladder}</tbody></table>
<p class=src>Если на цену за 2 месяца нет просмотров и звонков, снижаем на следующую ступень. Целевая сделка — 235–240 млн.</p>
</div></div>
<h2>Паспорт сделки</h2>
<table class=t><thead><tr><th>Сценарий</th><th class=r>Цена, ₽</th><th class=r>₽/м²</th><th class=r>НДС</th><th class=r>Агент 3%</th>
<th class=r>Чистая прибыль</th><th class=r>Рентаб.</th><th class=r>Годовых</th></tr></thead><tbody>
<tr class=hl><td>{SCEN[0][0]}</td>{pr(a)}</tr><tr><td>{SCEN[1][0]}</td>{pr(b)}</tr>
<tr><td>Цена объявления, если найдётся покупатель</td>{pr(economics(ASK, dt.date(2027, 10, 19)))}</tr>
</tbody></table>
<p class=src>Налог на прибыль 25% и НДС 22/122 с разницы при уступке (п. 3 ст. 155 НК РФ); госпошлина 6 000 ₽, аккредитив и сопровождение — 100 000 ₽.
Полный паспорт — в файле «Паспорт_сделки_Кло17.docx».</p>

<h1>6. Доводы покупателя</h1>
<div class=two><div><h2>За нашу квартиру</h2><ul class=pro>
<li>Дешевле застройщика: {m(pp8 / 1e6, 2)} млн/м² против {m(L / 1e6, 2)} в прайсе за такую же на {FLOOR} этаже и 2,40 на 2 этаже.</li>
<li>Делюкс от MR Private в 4 минутах от «Арбатской», у Кремля и Пашкова дома; всего 28 квартир.</li>
<li>Удобная планировка: кухня-столовая 27 м², спальня 19 м², две гардеробные, постирочная.</li>
<li>Оплата по ДДУ лежит на эскроу в Сбербанке до ввода дома — риск недостроя для покупателя минимален.</li>
</ul></div><div><h2>Против</h2><ul class=con>
<li>Без отделки: ремонт ещё на 1–1,5 года и 20–30 млн; «Никитский-6» рядом продаётся с отделкой.</li>
<li>Застройщик сам продаёт в доме {len(clos)} квартир, у него акции и скидки, агенты чаще ведут клиентов к нему.</li>
<li>Готовую квартиру с ремонтом рядом можно купить за {m(res_q[1] / 1e6, 1)} млн/м² и въехать сразу.</li>
<li>Сделок с элитными новостройками стало почти на 40% меньше, покупатели торгуются.</li>
</ul></div></div>
<p class=src>Источники: объявления ЦИАН на {DATE}; «Коммерсантъ» и «Метриум» — элитные новостройки за 9 месяцев 2026 года; «Ведомости»
(15.10.2026) — элитная вторичка; ДДУ от 31.10.2025.</p>'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{css}</style></head><body>{body}</body></html>'


def write_pdf(html):
    open(HTML, 'w').write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath(HTML))
        pg.wait_for_timeout(1200)
        pg.pdf(path=PDF, format='A4', print_background=True, margin=dict(top='17mm', bottom='14mm', left='16mm', right='15mm'),
               display_header_footer=True,
               header_template='<div style="font-family:Carlito,sans-serif;font-size:7.5pt;color:#555;width:100%;padding:0 15mm 0 16mm;'
                               f'display:flex;justify-content:space-between"><span>«{DEAL["jk"]}»: продажа квартиры</span><span>{DATE}</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    J, N, Rs, C = load()
    L, step, r2 = list_equivalent(J)
    prim, res, asg = nearby_primary(N), nearby_resale(Rs), assignments(N)
    ec = [economics(p, d) for _, p, d in SCEN]
    floor_png(J, L, f'{D}/our/floor.png')
    market_png(prim, res, f'{D}/our/alts.png')
    write_xlsx(J, N, Rs, C, prim, res, asg, L)
    write_passport(ec)
    write_pdf(build_html(J, N, Rs, C, prim, res, asg, L, step, r2, ec))
    print('прайс-эквивалент', round(L), 'шаг этажа', round(step, 1), 'R2', round(r2, 3), 'в ноль', round(breakeven()))
    for (n, p, d), e in zip(SCEN, ec):
        print(n, {k: round(v, 2) for k, v in e.items()})
