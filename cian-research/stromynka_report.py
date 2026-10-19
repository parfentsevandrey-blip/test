#!/usr/bin/env python3
"""Стромынка, 2 (здание МФЦ на арендованной у города земле): рыночная стоимость и разбор предложения 550 млн.

Данные: stromynka.py (выдача ЦИАН, карточки, фото), выписки ЕГРН и расчёт условий аренды МФЦ.
python3 stromynka_report.py → reports/Стромынка2_оценка.pdf, reports/Стромынка2_аналитика.xlsx
"""
import io
import json
import math
import os
import re
import statistics as st
from html import escape

D = 'stromynka-data'
PDF = 'reports/Стромынка2_оценка.pdf'
XLSX = 'reports/Стромынка2_аналитика.xlsx'
HTML = 'reports/stromynka2.html'
DATE = '19 октября 2026 г.'
OBJ = dict(lat=55.7895825, lng=37.6831089)
OFFER = 550.0
AREA = 1184.5

# --- объект по выпискам ЕГРН от 05.10.2026 и расчёту условий аренды ---
RENT_NOW = 38_951_205          # ₽/год с НДС, 2 740 ₽/м²/мес — текущий договор и вариант №1 МФЦ на 3 года
OPEX = 190_000 * 12            # эксплуатационные расходы на собственнике: оценка по КП №2 (190 тыс. ₽/мес)
YIELDS = (0.09, 0.095, 0.105)  # доходность для госарендатора: низ / база / верх
RENT_OPTIONS = [               # (вариант, ₽/год с НДС, ₽/м²/мес, срок, индексация)
    ('Текущий договор до 30.11.2026', 38_951_205, 2740, '11 лет', '0%'),
    ('КП №1 от 02.03.2026', 50_578_150, 3558, '10 лет', '5%'),
    ('КП №2 от 22.04.2026', 43_200_000, 3039, '10 лет', '5% + 190 тыс./мес экспл.'),
    ('КП по форме МФЦ от 15.05.2026', 45_480_000, 3200, '5 лет', '3%'),
    ('После встречи 06.07.2026', 43_200_000, 3039, '5–10 лет', '2%'),
    ('После встречи 05.08.2026', 41_457_500, 2917, '5 лет', '2%'),
    ('Вариант №1 МФЦ от 01.09.2026', 38_951_205, 2740, '3 года', '0%'),
    ('Вариант №2 МФЦ от 01.09.2026', 39_730_229, 2795, '1 год', '2%'),
]
# аналоги-здания для сравнительного подхода: отдельно стоящие, 400–2 100 м², до 2 км; номер фото фасада
COMPS = [323011386, 332617346, 317611480, 334575746, 333669809, 334638013, 333432976, 322849082, 328649553,
         334575896, 331803513, 323692057]
GAB = [331348350, 331580718, 334521519]       # готовый арендный бизнес рядом — для доводов
ADJ = [('Торг: цена сделки ниже цены объявления', -0.10),
       ('Метро в 2–3 минутах против 7–8 минут у аналогов', 0.07),
       ('Подвал — 30% площади (357,7 м²)', -0.07),
       ('1964 г., каркасно-панельное здание против кирпичных особняков после реконструкции', -0.10),
       ('Здание сдано госарендатору целиком', 0.05)]


def m(v, d=0):
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def load():
    B = json.load(open(f'{D}/buildings.json'))
    S = json.load(open(f'{D}/near_sale.json'))
    allo = {**B, **S}
    C = json.load(open(f'{D}/cards.json'))
    L = json.load(open(f'{D}/land.json'))
    R = json.load(open(f'{D}/near_rent.json'))
    Y = {str(r['id']): r['y'] for r in json.load(open(f'{D}/gab_yields.json'))}
    return allo, C, L, R, Y, B


def what(x):
    """Короткое описание лота: без служебных номеров в начале, без капслока."""
    t = re.sub(r'\s+', ' ', x['title'] or x['description']).strip()
    t = re.sub(r'^(id:?\s*\d+\.?|Номер объекта:?\s*\d+\.?|Лот:?\s*\d+\.?|\d{4,}\.?)\s*', '', t, flags=re.I)
    t = re.sub(r'^(от собственника[.!]?)\s*', '', t, flags=re.I)
    if sum(ch.isupper() for ch in t[:60]) > 0.5 * sum(ch.isalpha() for ch in t[:60]):
        t = t.capitalize()
    return t[:70].rsplit(' ', 1)[0] + '…' if len(t) > 70 else t


def walk(x):
    w = [t for n, t, mo in x['metro'] if mo == 'walk' and t is not None]
    return min(w) if w else None


def land_status(x, c):
    t = (c.get('land') or {}).get('type')
    d = c.get('description') or x['description']
    if t == 'owned' or re.search(r'(земл\w*|участ\w*)[^.\n]{0,40}(собственн)', d, re.I):
        return 'собственность'
    if t == 'rent' or re.search(r'(земл\w*|участ\w*)[^.\n]{0,40}аренд|аренд\w* (земл|участ)|долгосрочн\w* аренд', d, re.I):
        return 'аренда'
    return 'не указано'


def valuation(allo):
    eff = RENT_NOW - OPEX
    inc = {y: eff / y / 1e6 for y in YIELDS}
    pp = sorted(allo[str(c)]['price'] / allo[str(c)]['area'] for c in COMPS)
    med = st.median(pp)
    factor = 1.0
    for _, a in ADJ:
        factor *= 1 + a
    cmp_m2 = med * factor
    cmp = cmp_m2 * AREA / 1e6
    base = 0.6 * inc[0.095] + 0.4 * cmp
    lo = min(inc[0.105], cmp * 0.9)
    hi = max(inc[0.09], cmp * 1.1)
    return dict(eff=eff, inc=inc, med=med, factor=factor, cmp_m2=cmp_m2, cmp=cmp, base=base, lo=lo, hi=hi,
                y_offer=RENT_NOW / (OFFER * 1e6), y_offer_eff=eff / (OFFER * 1e6),
                over=OFFER / base - 1, over_lo=OFFER / hi - 1, over_hi=OFFER / lo - 1,
                need_rent=OFFER * 1e6 * 0.10 + OPEX)


# ---------- графика ----------

def plt_setup():
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    for f in font_manager.findSystemFonts():
        if 'Carlito' in f:
            font_manager.fontManager.addfont(f)
    plt.rcParams.update({'font.family': 'Carlito', 'font.size': 10})
    return matplotlib, plt


INK, INK2, GRID, SURF, BLUE, ORANGE, AQUA = '#0b0b0b', '#52514e', '#e4e3df', '#fcfcfb', '#2a78d6', '#eb6834', '#1baf7a'


def tile_xy(lat, lng, z):
    n = 2 ** z
    x = (lng + 180) / 360 * n
    y = (1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n
    return x, y


def map_png(allo, out):
    """Карта: объект, аналоги-здания и ГАБ рядом на подложке OpenStreetMap."""
    import urllib.request
    from PIL import Image
    matplotlib, plt = plt_setup()
    z = 15
    cx, cy = tile_xy(OBJ['lat'], OBJ['lng'], z)
    x0, x1, y0, y1 = int(cx - 3.2), int(cx + 3.2), int(cy - 2.4), int(cy + 2.4)
    os.makedirs(f'{D}/tiles', exist_ok=True)
    W, H = (x1 - x0 + 1) * 256, (y1 - y0 + 1) * 256
    img = Image.new('RGB', (W, H), 'white')
    for tx in range(x0, x1 + 1):
        for ty in range(y0, y1 + 1):
            p = f'{D}/tiles/{z}_{tx}_{ty}.png'
            if not os.path.exists(p):
                req = urllib.request.Request(f'https://tile.openstreetmap.org/{z}/{tx}/{ty}.png',
                                             headers={'User-Agent': 'cian-research/1.0 (analytics report)'})
                open(p, 'wb').write(urllib.request.urlopen(req, timeout=30).read())
            img.paste(Image.open(p).convert('RGB'), ((tx - x0) * 256, (ty - y0) * 256))
    img = Image.blend(img, Image.new('RGB', img.size, 'white'), 0.35)
    fig, ax = plt.subplots(figsize=(7.4, 5.4), dpi=200)
    ax.imshow(img)
    ax.set_xlim(0, W)
    ax.set_ylim(H, 0)
    ax.axis('off')

    def px(lat, lng):
        x, y = tile_xy(lat, lng, z)
        return (x - x0) * 256, (y - y0) * 256
    for n, cid in enumerate(COMPS, 1):
        x = allo[str(cid)]
        a, b = px(x['lat'], x['lng'])
        ax.scatter([a], [b], s=150, color=BLUE, edgecolor='white', linewidth=1.2, zorder=4)
        ax.text(a, b, str(n), ha='center', va='center', color='white', fontsize=8, fontweight='bold', zorder=5)
    for n, cid in enumerate(GAB, 1):
        x = allo[str(cid)]
        a, b = px(x['lat'], x['lng'])
        ax.scatter([a], [b], s=150, color=ORANGE, edgecolor='white', linewidth=1.2, zorder=4)
        ax.text(a, b, f'Г{n}', ha='center', va='center', color='white', fontsize=7, fontweight='bold', zorder=5)
    a, b = px(OBJ['lat'], OBJ['lng'])
    ax.scatter([a], [b], s=260, color=INK, edgecolor='white', linewidth=1.5, zorder=6, marker='*')
    ax.annotate('Стромынка, 2', (a, b), xytext=(a + 60, b - 70), fontsize=10, fontweight='bold', color=INK,
                bbox=dict(boxstyle='round,pad=0.25', facecolor='white', edgecolor='none'),
                arrowprops=dict(arrowstyle='-', color=INK2, lw=0.8), zorder=7)
    for name, lat, lng in (('м. Сокольники', 55.7888, 37.6798), ('м. Красносельская', 55.7791, 37.6668),
                           ('м. Электрозаводская', 55.7822, 37.7053), ('м. Преображенская пл.', 55.7946, 37.7145)):
        a, b = px(lat, lng)
        if 0 < a < W and 0 < b < H:
            ax.scatter([a], [b], s=40, color='#c0392b', marker='s', zorder=3)
            ax.text(a + 14, b + 4, name, fontsize=8, color='#7b1f17', zorder=3,
                    bbox=dict(boxstyle='round,pad=0.15', facecolor='white', edgecolor='none', alpha=0.8))
    ax.text(W - 8, H - 8, '© участники OpenStreetMap', ha='right', va='bottom', fontsize=6.5, color=INK2)
    fig.tight_layout(pad=0.2)
    fig.savefig(out, facecolor='white')
    plt.close(fig)


def bars_png(allo, val, out):
    matplotlib, plt = plt_setup()
    rows = sorted([(allo[str(c)]['price'] / allo[str(c)]['area'] / 1e3, n, allo[str(c)]['address'])
                   for n, c in enumerate(COMPS, 1)])
    fig, ax = plt.subplots(figsize=(7.4, 3.9), dpi=200)
    fig.patch.set_facecolor(SURF)
    ax.set_facecolor(SURF)
    ys = range(len(rows))
    ax.barh(list(ys), [r[0] for r in rows], color=BLUE, height=0.62, zorder=2)
    ax.set_yticks(list(ys))
    ax.set_yticklabels([f'{r[1]}. {r[2]}' for r in rows], fontsize=8.5, color=INK2)
    for y, r in zip(ys, rows):
        ax.text(r[0] + 6, y, m(r[0]), va='center', fontsize=8, color=INK2)
    offer = OFFER * 1e3 / AREA
    for v, lab, col, ls, ha in ((offer, f'предложение 550 млн = {m(offer)} тыс./м²', ORANGE, '-', 'left'),
                                (val['cmp_m2'] / 1e3, f'наша оценка = {m(val["cmp_m2"] / 1e3)} тыс./м²', INK, '--', 'right')):
        ax.axvline(v, color=col, lw=1.6, ls=ls, zorder=3)
        ax.text(v + (5 if ha == 'left' else -5), len(rows) + 0.1, lab, fontsize=8.5, color=col, va='bottom', ha=ha,
                bbox=dict(boxstyle='square,pad=0.15', facecolor=SURF, edgecolor='none'))
    ax.set_xlabel('Цена объявления, тыс. руб. за м²', color=INK2)
    ax.set_xlim(0, 700)
    ax.set_ylim(-0.6, len(rows) + 0.9)
    ax.grid(axis='x', color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right', 'left'):
        ax.spines[s].set_visible(False)
    ax.spines['bottom'].set_color(GRID)
    ax.tick_params(colors=INK2, length=0)
    fig.tight_layout()
    fig.savefig(out, facecolor=SURF)
    plt.close(fig)


def yields_png(allo, Y, val, out):
    matplotlib, plt = plt_setup()
    pts = []
    for cid, y in Y.items():
        x = allo.get(cid)
        if x and x['km_obj'] is not None and x['km_obj'] <= 3.5:
            pts.append((y, x['area'] or 0, x['address']))
    fig, ax = plt.subplots(figsize=(7.4, 2.3), dpi=200)
    fig.patch.set_facecolor(SURF)
    ax.set_facecolor(SURF)
    import random
    rnd = random.Random(3)
    ax.scatter([p[0] for p in pts], [rnd.uniform(-0.25, 0.25) for _ in pts], s=36, color=BLUE, alpha=0.8,
               edgecolor=SURF, linewidth=0.8, zorder=3, label='готовый арендный бизнес до 3,5 км')
    med = st.median([p[0] for p in pts])
    ax.axvline(med, color=INK2, lw=1.2, ls='--')
    ax.text(med + 0.1, 0.42, f'медиана {m(med, 1)}%', fontsize=8.5, color=INK2)
    for v, lab in ((100 * val['y_offer'], f'550 млн при текущей аренде = {m(100 * val["y_offer"], 1)}%'),):
        ax.axvline(v, color=ORANGE, lw=1.8)
        ax.text(v - 0.1, 0.42, lab, fontsize=8.5, color=ORANGE, ha='right')
    ax.set_ylim(-0.5, 0.6)
    ax.set_yticks([])
    ax.set_xlim(4, 17)
    ax.set_xlabel('Доходность, заявленная продавцом, % в год', color=INK2)
    ax.grid(axis='x', color=GRID, lw=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right', 'left'):
        ax.spines[s].set_visible(False)
    ax.spines['bottom'].set_color(GRID)
    ax.tick_params(colors=INK2)
    fig.tight_layout()
    fig.savefig(out, facecolor=SURF)
    plt.close(fig)


def site_png(out):
    """Схема по координатам ЕГРН (ПМСК Москвы): здание, участок 484 м² и общий участок 3 129 м²."""
    matplotlib, plt = plt_setup()
    co = json.load(open(f'{D}/our/coords.json'))

    def contours(pts):
        res, cur = [], []
        for n, x, y in pts:
            if cur and (x, y) == cur[0]:
                cur.append((x, y))
                res.append(cur)
                cur = []
            else:
                cur.append((x, y))
        if cur:
            res.append(cur)
        return res
    fig, ax = plt.subplots(figsize=(4.2, 4.2), dpi=200)
    fig.patch.set_facecolor('white')
    for c in contours(co['l1863']):
        ax.fill([p[1] for p in c], [p[0] for p in c], color='#dfe9f6', ec=BLUE, lw=0.8, zorder=1)
    l4 = [(x, y) for _, x, y in co['l4']]
    ax.fill([p[1] for p in l4], [p[0] for p in l4], color='#f7d9c9', ec=ORANGE, lw=1.2, zorder=2)
    b = [(x, y) for _, x, y in co['b']]
    ax.fill([p[1] for p in b], [p[0] for p in b], color='#555', ec=INK, lw=0.8, zorder=3, alpha=0.85)
    ax.set_aspect('equal')
    ax.axis('off')
    from matplotlib.patches import Patch
    ax.legend(handles=[Patch(color='#555', label='здание 1 184,5 м² (пятно 441 м²)'),
                       Patch(facecolor='#f7d9c9', edgecolor=ORANGE, label='участок :4 — 484 м²'),
                       Patch(facecolor='#dfe9f6', edgecolor=BLUE, label='участок :1863 — 3 129 м² (общий)')],
              loc='lower center', bbox_to_anchor=(0.5, -0.16), fontsize=7.5, frameon=False, ncol=1)
    fig.tight_layout()
    fig.savefig(out, facecolor='white')
    plt.close(fig)


# ---------- Excel ----------

def write_xlsx(allo, C, L, R, Y, B, val):
    from openpyxl import Workbook
    from openpyxl.comments import Comment
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter
    F = 'Arial'
    bold, font = Font(name=F, bold=True, size=9), Font(name=F, size=9)
    blue = Font(name=F, size=9, color='0000FF')
    link = Font(name=F, size=9, color='1F4E9A', underline='single')
    grid = Side(style='thin', color='C8CDD3')
    border = Border(left=grid, right=grid, top=grid, bottom=grid)
    head = PatternFill('solid', fgColor='DDE3EA')
    yellow = PatternFill('solid', fgColor='FFF2CC')
    wb = Workbook()

    # Лист 1: оценка с формулами
    ws = wb.active
    ws.title = 'Оценка'
    ws.column_dimensions['A'].width = 58
    ws.column_dimensions['B'].width = 18
    ws.column_dimensions['C'].width = 60
    rows = [
        ('Стромынка, 2 — рыночная стоимость (здание 1 184,5 м² + право аренды земли)', None, None),
        (None, None, None),
        ('ИСХОДНЫЕ ДАННЫЕ', None, 'Синий шрифт — вводимые значения, их можно менять'),
        ('Площадь здания, м²', AREA, 'ЕГРН, выписка от 05.10.2026'),
        ('Арендная плата МФЦ, ₽/год с НДС', RENT_NOW, 'Текущий договор и вариант №1 МФЦ от 01.09.2026 (3 года, без индексации)'),
        ('Эксплуатационные расходы собственника, ₽/год', OPEX, 'Оценка по КП №2: 190 тыс. ₽/мес; по договору они входят в арендную плату'),
        ('Доходность — нижняя граница', YIELDS[0], 'Рынок: медиана 10,3%, половина лотов 8,8–11,0% (ГАБ от 300 м² до 3,5 км, ЦИАН)'),
        ('Доходность — базовая', YIELDS[1], 'Госарендатор надёжнее рынка, но договор короткий и без индексации'),
        ('Доходность — верхняя граница', YIELDS[2], None),
        ('Медиана цены аналогов-зданий, ₽/м²', round(val['med']), '12 отдельно стоящих зданий 400–2 100 м² до 2 км (лист «Аналоги-здания»)'),
    ]
    for _, a in ADJ:
        rows.append((f'Поправка: {_}', a, None))
    rows += [
        ('Предложение покупателя, млн ₽', OFFER, None),
        ('Вес доходного подхода', 0.6, 'Для здания с арендатором основной подход — доходный'),
        (None, None, None),
        ('РАСЧЁТ', None, None),
    ]
    for k, (a, b, c) in enumerate(rows, 1):
        ws.cell(k, 1, a).font = bold if (a and (a.isupper() or k == 1)) else font
        if b is not None:
            cell = ws.cell(k, 2, b)
            cell.font = blue
            cell.fill = yellow if 'Доходность — базовая' in (a or '') or 'Предложение' in (a or '') else PatternFill()
            cell.number_format = '0.0%' if isinstance(b, float) and abs(b) < 1 else '#,##0'
        if c:
            ws.cell(k, 3, c).font = Font(name=F, size=8.5, color='555555')
    r0 = len(rows)
    adj_first, adj_last = 11, 10 + len(ADJ)
    offer_row, w_row = adj_last + 1, adj_last + 2
    calc = [
        ('Чистый доход для расчёта, ₽/год', '=B5-B6', '#,##0'),
        ('Стоимость доходным подходом — базовая, млн ₽', f'=B{r0 + 1}/B8/1e6', '#,##0.0'),
        ('Стоимость доходным подходом — при доходности 9%, млн ₽', f'=B{r0 + 1}/B7/1e6', '#,##0.0'),
        ('Стоимость доходным подходом — при доходности 10,5%, млн ₽', f'=B{r0 + 1}/B9/1e6', '#,##0.0'),
        ('Совокупная поправка сравнительного подхода', '=' + '*'.join(f'(1+B{i})' for i in range(adj_first, adj_last + 1)), '0.000'),
        ('Цена м² с поправками, ₽', f'=B10*B{r0 + 5}', '#,##0'),
        ('Стоимость сравнительным подходом, млн ₽', f'=B{r0 + 6}*B4/1e6', '#,##0.0'),
        ('ИТОГ: рыночная стоимость, млн ₽', f'=B{w_row}*B{r0 + 2}+(1-B{w_row})*B{r0 + 7}', '#,##0.0'),
        ('Предложение выше рыночной стоимости на', f'=B{offer_row}/B{r0 + 8}-1', '0.0%'),
        ('Доходность покупателя при 550 млн и текущей аренде', f'=B5/(B{offer_row}*1e6)', '0.0%'),
        ('Аренда, нужная для 550 млн при доходности 10%, ₽/год с НДС', f'=B{offer_row}*1e6*0.1+B6', '#,##0'),
        ('То же, ₽/м² в месяц с НДС', f'=B{r0 + 11}/B4/12', '#,##0'),
    ]
    for k, (a, f_, fmt) in enumerate(calc, r0 + 1):
        ws.cell(k, 1, a).font = bold if a.startswith('ИТОГ') else font
        c = ws.cell(k, 2, f_)
        c.font = Font(name=F, size=9, bold=a.startswith('ИТОГ'))
        c.number_format = fmt
    s0 = r0 + len(calc) + 2
    ws.cell(s0, 1, 'ЧУВСТВИТЕЛЬНОСТЬ: стоимость, млн ₽ (строки — аренда, столбцы — доходность)').font = bold
    ylist = [0.08, 0.09, 0.10, 0.11, 0.12]
    for j, y in enumerate(ylist, 2):
        c = ws.cell(s0 + 1, j, y)
        c.font, c.number_format = blue, '0%'
    for i, (name, rent, *_r) in enumerate([o for o in RENT_OPTIONS if o[0] != 'Текущий договор до 30.11.2026'], s0 + 2):
        ws.cell(i, 1, f'{name}: {m(rent / 1e6, 1)} млн ₽/год').font = font
        rc = ws.cell(i, 8, rent)
        rc.font, rc.number_format = blue, '#,##0'
        for j in range(2, 2 + len(ylist)):
            col = get_column_letter(j)
            c = ws.cell(i, j, f'=($H{i}-$B$6)/{col}${s0 + 1}/1e6')
            c.font, c.number_format = font, '#,##0'
    ws.cell(s0 + 1, 8, 'Аренда, ₽/год').font = bold
    for j in range(2, 9):
        ws.column_dimensions[get_column_letter(j)].width = max(ws.column_dimensions[get_column_letter(j)].width or 10, 12)

    def table(title, cols, data, links=(), freeze='B2'):
        sh = wb.create_sheet(title)
        for k, (h, w, note) in enumerate(cols, 1):
            c = sh.cell(1, k, h)
            c.font, c.fill, c.border = bold, head, border
            c.alignment = Alignment(wrap_text=True, vertical='center', horizontal='center')
            if note:
                c.comment = Comment(note, 'анализ')
            sh.column_dimensions[get_column_letter(k)].width = w
        sh.row_dimensions[1].height = 40
        for n, (vals, url) in enumerate(data, 2):
            for k, v in enumerate(vals, 1):
                c = sh.cell(n, k, v)
                c.font, c.border = font, border
                c.alignment = Alignment(wrap_text=True, vertical='top')
            if url:
                for k in links:
                    sh.cell(n, k).hyperlink = url
                    sh.cell(n, k).font = link
        sh.freeze_panes = freeze
        sh.auto_filter.ref = f'A1:{get_column_letter(len(cols))}{len(data) + 1}'
        return sh

    def brow(x, c):
        y = Y.get(str(x['id']))
        f = (c or {}).get('features') or {}
        return [x['id'], x['address'], x['raion'], x['km_obj'], walk(x), x['metro'][0][0] if x['metro'] else None, x['area'],
                round(x['price'] / 1e6, 1), round(x['price'] / x['area']), x['vat'], land_status(x, c or {}),
                round(y, 1) if y else None, f.get('Состояние'), (x['title'] or None),
                re.sub(r'\s+', ' ', (c or {}).get('description') or x['description'])[:600]]
    bcols = [('ID ЦИАН', 11, None), ('Адрес', 28, None), ('Район', 14, None), ('От объекта, км', 8, None),
             ('Метро пешком, мин', 8, None), ('Станция', 14, None), ('Площадь, м²', 9, None), ('Цена, млн ₽', 9, None),
             ('₽ за м²', 10, None), ('НДС', 10, None), ('Земля', 12, 'Из карточки и описания объявления'),
             ('Доходность, %', 9, 'Заявленная продавцом или рассчитанная из дохода в объявлении'),
             ('Состояние', 16, None), ('Заголовок', 18, None), ('Описание', 70, None)]
    table('Аналоги-здания', bcols, [(brow(allo[str(c)], C.get(str(c))), allo[str(c)]['url']) for c in COMPS + GAB], links=(1,))
    near = sorted([allo[k] for k in C], key=lambda x: x['km_obj'])
    table('Здания рядом (3,6 км)', bcols, [(brow(x, C.get(str(x['id']))), x['url']) for x in near], links=(1,))
    gab = sorted([(allo[k], y) for k, y in Y.items() if k in allo], key=lambda t: t[0]['km_obj'] or 99)
    table('ГАБ доходность', [('ID ЦИАН', 11, None), ('Адрес', 28, None), ('Район', 14, None), ('От объекта, км', 8, None),
                             ('Площадь, м²', 9, None), ('Цена, млн ₽', 9, None), ('Доходность, %', 9, None), ('Описание', 80, None)],
          [([x['id'], x['address'], x['raion'], x['km_obj'], x['area'], round(x['price'] / 1e6, 1), round(y, 1),
             re.sub(r'\s+', ' ', x['description'])[:500]], x['url']) for x, y in gab], links=(1,))
    land = sorted(L.values(), key=lambda x: x['km_obj'] or 99)
    table('Земля (ЗУ) Москва', [('ID ЦИАН', 11, None), ('Округ', 10, None), ('Район', 16, None), ('От объекта, км', 8, None),
                                ('До Кремля, км', 8, None), ('Площадь ЗУ, м²', 10, None), ('Цена, млн ₽', 9, None),
                                ('₽ за м² земли', 10, None), ('ВРИ (ЦИАН)', 18, None), ('Заголовок', 20, None), ('Описание', 70, None)],
          [([x['id'], x['okrug'], x['raion'], x['km_obj'], x['km_kremlin'], round(x['land_m2']) if x['land_m2'] else None,
             round(x['price'] / 1e6, 1) if x['price'] else None,
             round(x['price'] / x['land_m2']) if x['price'] and x['land_m2'] else None, x['permitted'], x['title'],
             re.sub(r'\s+', ' ', x['description'])[:500]], x['url']) for x in land], links=(1,))
    rent = sorted([x for x in R.values() if x['km_obj'] is not None and x['km_obj'] <= 2 and x['price_type'] == 'all'
                   and x['area'] and x['area'] >= 100], key=lambda x: x['km_obj'])
    table('Аренда рядом (2 км)', [('ID ЦИАН', 11, None), ('Тип', 16, None), ('Адрес', 28, None), ('От объекта, км', 8, None),
                                  ('Площадь, м²', 9, None), ('₽ в месяц', 12, None), ('₽ за м² в месяц', 10, None), ('НДС', 10, None)],
          [([x['id'], x['category'], x['address'], x['km_obj'], x['area'], x['price'], round(x['price'] / x['area']), x['vat']],
            x['url']) for x in rent], links=(1,))
    table('МФЦ — варианты аренды', [('Вариант', 34, None), ('₽ в год с НДС', 14, None), ('₽ за м² в месяц', 10, None),
                                    ('Срок', 10, None), ('Индексация', 18, None)],
          [([a, b, c, d, e], None) for a, b, c, d, e in RENT_OPTIONS], freeze='A2')
    wb.save(XLSX)


# ---------- PDF ----------

CSS = '''
@page { size: A4; margin: 17mm 15mm 14mm 16mm; }
body { font-family: "Carlito", "Liberation Sans", sans-serif; font-size: 10pt; color: #111; line-height: 1.38; margin: 0; }
h1 { font-size: 14pt; margin: 14px 0 6px; padding-bottom: 3px; border-bottom: 0.8pt solid #111; break-after: avoid; }
h2 { font-size: 11.5pt; margin: 10px 0 4px; break-after: avoid; }
p { margin: 0 0 5px; } ul { margin: 0 0 6px 16px; padding: 0; } li { margin: 0 0 3px; }
a { color: #111; text-decoration: underline; text-decoration-thickness: 0.4pt; }
.pb { break-before: page; }
.t1 { font-size: 18pt; font-weight: bold; line-height: 1.15; margin: 0 0 3px; }
.t2 { font-size: 11pt; color: #333; margin: 0 0 10px; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); margin: 6px 0 10px; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; }
.kpi { padding: 5px 8px; } .kpi + .kpi { border-left: 0.4pt solid #bbb; }
.kpi .v { font-size: 15pt; font-weight: bold; } .kpi .l { font-size: 8.5pt; color: #444; line-height: 1.25; }
.box { border: 0.8pt solid #111; padding: 7px 10px; margin: 6px 0 8px; } .box p:last-child { margin: 0; }
.src { font-size: 8.3pt; color: #444; margin: 2px 0 7px; }
table.t { width: 100%; border-collapse: collapse; font-size: 8.8pt; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; margin: 0 0 4px; }
.t th { text-align: left; font-weight: bold; padding: 3px 4px; border-bottom: 0.6pt solid #111; vertical-align: bottom; }
.t td { padding: 2.2px 4px; vertical-align: top; } .t tbody tr + tr td { border-top: 0.3pt solid #ccc; }
.t .r { text-align: right; white-space: nowrap; } .t tr.hl td { background: #f2f1ec; font-weight: bold; }
.two { display: grid; grid-template-columns: 1.25fr 1fr; gap: 12px; align-items: start; }
img.full { width: 100%; display: block; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px 8px; }
.cd { break-inside: avoid; font-size: 8.4pt; line-height: 1.28; }
.cd img { width: 100%; height: 21mm; object-fit: cover; display: block; margin-bottom: 2px; }
.cd b { font-size: 8.8pt; }
.pro li::marker { content: '+  '; font-weight: bold; } .con li::marker { content: '−  '; font-weight: bold; }
'''


def build_html(allo, C, L, R, Y, val):
    S = lambda v: escape(str(v)) if v is not None else '—'
    tag = lambda cid: f'<a href="{allo[str(cid)]["url"]}">{cid}</a>'
    old_land = [x for x in L.values() if x['okrug'] not in ('НАО (Новомосковский)', 'ТАО (Троицкий)', 'ЗелАО', None)
                and x['land_m2'] and x['price'] and x['land_m2'] < 200000]
    lp = sorted(x['price'] / x['land_m2'] for x in old_land)
    lp_med = st.median(lp)
    close_land = sorted([x for x in old_land if x['km_kremlin'] <= 7.5 and 400 <= x['land_m2'] <= 13000],
                        key=lambda x: x['km_obj'])
    near_b = [x for x in allo.values() if x['category'] == 'buildingSale' and x['km_obj'] is not None and x['km_obj'] <= 3.6]
    near1 = [x for x in near_b if x['km_obj'] <= 1.0]
    comp_walk = st.median([walk(allo[str(c)]) for c in COMPS if walk(allo[str(c)])])
    rents = [x['price'] / x['area'] for x in R.values() if x['price_type'] == 'all' and x['area'] and x['area'] >= 300
             and x['km_obj'] is not None and x['km_obj'] <= 1.5 and x['category'] in ('officeRent', 'buildingRent', 'freeAppointmentObjectRent')]
    rent_q = [sorted(rents)[int(len(rents) * q)] for q in (0.25, 0.5, 0.75)]
    ny = [Y[k] for k in Y if k in allo and allo[k]['km_obj'] is not None and allo[k]['km_obj'] <= 3.5 and (allo[k]['area'] or 0) >= 300]
    ny_q = [sorted(ny)[int(len(ny) * q)] for q in (0.25, 0.5, 0.75)]

    land_rows = ''.join(f'<tr><td><a href="{x["url"]}">{x["id"]}</a></td><td>{S(x["raion"])}</td><td class=r>{m(x["km_kremlin"], 1)}</td>'
                        f'<td class=r>{m(x["land_m2"])}</td><td class=r>{m(x["price"] / 1e6)}</td><td class=r>{m(x["price"] / x["land_m2"] / 1e3)}</td>'
                        f'<td>{S(what(x))}</td></tr>' for x in close_land[:10])
    comp_rows = ''
    for n, cid in enumerate(COMPS, 1):
        x, c = allo[str(cid)], C.get(str(cid), {})
        comp_rows += (f'<tr><td>{n}</td><td>{tag(cid)}</td><td>{S(x["address"])}</td><td class=r>{m(x["area"])}</td>'
                      f'<td class=r>{m(x["price"] / 1e6)}</td><td class=r>{m(x["price"] / x["area"] / 1e3)}</td>'
                      f'<td class=r>{S(walk(x))}</td><td>{land_status(x, c)}</td><td>{S((c.get("features") or {}).get("Состояние"))}</td></tr>')
    cards = ''
    notes = {
        323011386: 'Здание застройщика ЖК «Ренессанс», без отделки, 12 мин до метро. Самый близкий к нам аналог по размеру — 450 тыс./м².',
        332617346: 'Здание 1967 г. в бизнес-комплексе, дизайнерский ремонт, 6 мин до метро. Ровесник нашего — 355 тыс./м².',
        317611480: 'Два здания класса B, все помещения сданы, земля в долгосрочной аренде, 8 мин до метро — 228 тыс./м².',
        334575746: 'Особняк в ЦАО, продажа 100% долей ООО, 13 мин до метро — 386 тыс./м².',
        333669809: 'Исторический лофт после капремонта 2022 г., участок 1 300 м² в долгосрочной аренде — 399 тыс./м².',
        334638013: 'Кирпичное здание 1877 г., свободно, 4 мин до метро, участок 441 м² в аренде у города — 288 тыс./м².',
        333432976: 'Торги по банкротству 18.11.2026, с правом аренды земли 451 м² — 190 тыс./м².',
        322849082: 'Особняк начала XIX в. после реконструкции, под отделку — 388 тыс./м².',
        328649553: 'Здание на участке 0,36 га в аренде; цену подняли с 465 до 600 млн — 400 тыс./м².',
        334575896: 'Особняк после полного капремонта, цоколь 450 м² — 307 тыс./м².',
        331803513: 'Особняк 477 м² у Красносельской, реконструкция 2015 г., 4 мин до метро — 524 тыс./м².',
        323692057: 'Особняк в стиле классицизма, лифт, ремонт — 369 тыс./м².',
    }
    for n, cid in enumerate(COMPS, 1):
        x = allo[str(cid)]
        p = os.path.abspath(f'{D}/photos/{cid}_0.jpg')
        cards += (f'<div class=cd><img src="file://{p}"><b>{n}. {S(x["address"])}</b><br>{m(x["area"])} м² · {m(x["price"] / 1e6)} млн · '
                  f'<b>{m(x["price"] / x["area"] / 1e3)} тыс./м²</b><br>{S(notes.get(cid, ""))}</div>')
    gab_rows = ''
    for n, cid in enumerate(GAB, 1):
        x = allo[str(cid)]
        gab_rows += (f'<tr><td>Г{n}</td><td>{tag(cid)}</td><td>{S(x["address"])}</td><td class=r>{m(x["area"])}</td>'
                     f'<td class=r>{m(x["price"] / 1e6)}</td><td class=r>{m(x["price"] / x["area"] / 1e3)}</td>'
                     f'<td class=r>{m(Y.get(str(cid)) or 0, 1)}%</td><td class=r>{S(walk(x))}</td></tr>')
    rent_rows = ''.join(f'<tr class="{"hl" if a.startswith("Вариант №1") else ""}"><td>{escape(a)}</td><td class=r>{m(b / 1e6, 1)}</td><td class=r>{m(c)}</td>'
                        f'<td>{d}</td><td>{escape(e)}</td><td class=r>{m((b - OPEX) / 0.10 / 1e6)}</td></tr>'
                        for a, b, c, d, e in RENT_OPTIONS)
    adj_rows = ''.join(f'<tr><td>{escape(a)}</td><td class=r>{"+" if v > 0 else "−"}{m(abs(v) * 100)}%</td></tr>' for a, v in ADJ)
    inc = val['inc']

    body = f'''
<div class=t1>Стромынка, 2: рыночная стоимость и предложение 550 млн</div>
<div class=t2>Здание МФЦ 1 184,5 м² на арендованной у города земле, Сокольники · данные ЦИАН на {DATE}</div>
<div class=kpis>
<div class=kpi><div class=v>{m(val["lo"], 0)}–{m(val["hi"], 0)} млн</div><div class=l>рыночная стоимость,<br>центр оценки ≈ {m(val["base"], 0)} млн</div></div>
<div class=kpi><div class=v>550 млн</div><div class=l>предложение покупателя,<br>{m(OFFER * 1e3 / AREA)} тыс. ₽ за м²</div></div>
<div class=kpi><div class=v>+{m(val["over"] * 100)}%</div><div class=l>предложение выше рынка<br>(от +{m(val["over_lo"] * 100)} до +{m(val["over_hi"] * 100)}%)</div></div>
<div class=kpi><div class=v>{m(val["y_offer"] * 100, 1)}%</div><div class=l>доходность покупателя при 550 млн;<br>рынок — около {m(ny_q[1], 0)}%</div></div>
</div>
<div class=box>
<p><b>Предложение 550 млн заметно выше рынка — его стоит принимать.</b> По доходу от МФЦ и по ценам аналогов рыночная стоимость
здания с правом аренды земли — {m(val["lo"], 0)}–{m(val["hi"], 0)} млн, в центре оценки около {m(val["base"], 0)} млн. 550 млн выше на
{m(val["over"] * 100)}%. При текущей аренде покупатель получает {m(val["y_offer"] * 100, 1)}% годовых, а готовый арендный бизнес рядом
продаётся с доходностью {m(ny_q[0], 1)}–{m(ny_q[2], 1)}%.</p>
<p>550 млн были бы рыночной ценой, если бы МФЦ платил около {m(val["need_rent"] / 1e6, 0)} млн в год ({m(val["need_rent"] / AREA / 12)} ₽ за м² в месяц) —
больше, чем в коммерческом предложении №1 (50,6 млн), от которого МФЦ отказался. Сейчас МФЦ предлагает продлить договор по текущей ставке
{m(RENT_NOW / 1e6, 2)} млн без индексации. Главный риск сделки — покупатель пересмотрит цену, когда увидит эти условия на проверке.</p>
<p><b>Что делать:</b> закрепить цену в предварительном договоре с задатком, сразу раскрыть условия аренды и уточнить, включён ли НДС в 550 млн
(при продаже здания юрлицом на общей системе — 22%).</p></div>

<h1>1. Объект</h1>
<div class=two><div>
<table class=t><tbody>
<tr><td>Здание</td><td>77:03:0003008:1017 · 1 184,5 м² (подвал 357,7, 1 этаж 417,1, 2 этаж 409,7) · 1964 г., каркасно-панельное ·
кадастровая стоимость 32,4 млн · собственник ООО «Эстейт-инвест» с 2019 г.</td></tr>
<tr><td>Земля</td><td>Участок 77:03:0003008:4 — 484 м², ВРИ «эксплуатация здания под бытовые услуги», кадастровая стоимость 12,5 млн.
Здание также частично на общем участке :1863 (3 129 м², госуправление, магазины, общепит; кадастровая стоимость 202,8 млн).
Оба участка — <b>в аренде у города до 16.05.2066</b>, на стороне арендатора несколько лиц</td></tr>
<tr><td>Ограничения</td><td>Охранная зона объектов культурного наследия № 186, зона регулирования застройки № 1, территория
выявленного объекта археологии («культурный слой XVIII в.») — реконструкция и новое строительство ограничены</td></tr>
<tr><td>Арендатор</td><td>ГБУ «МФЦ города Москвы» — всё здание, договор с 01.12.2015 по <b>30.11.2026</b>; {m(RENT_NOW / 1e6, 2)} млн в год с НДС
(2 740 ₽/м² в месяц), эксплуатационные расходы — на собственнике</td></tr>
<tr><td>Локация</td><td>Первая линия Стромынки у Сокольнической площади, метро «Сокольники» (Сокольническая линия и БКЛ) — 2–3 минуты пешком,
парк «Сокольники» рядом; около 5,9 км до Кремля</td></tr>
</tbody></table>
<p class=src>По выпискам ЕГРН от 05.10.2026. Отчёт об оценке двухлетней давности к материалам не приложен — расчёт сделан без него.</p>
</div><div><img class=full src="file://{os.path.abspath(f"{D}/site.png")}"><p class=src>Схема по координатам ЕГРН: здание почти целиком стоит на участке 484 м².</p></div></div>

<h2>Переговоры с МФЦ о новом сроке</h2>
<table class=t><thead><tr><th>Вариант</th><th class=r>Млн ₽/год с НДС</th><th class=r>₽/м² в месяц</th><th>Срок</th><th>Индексация</th>
<th class=r>Стоимость при доходности 10%, млн</th></tr></thead><tbody>{rent_rows}</tbody></table>
<p class=src>Последнее предложение МФЦ (выделено) — текущая ставка на 3 года без индексации. Стоимость — (аренда − эксплуатационные расходы 2,28 млн) / 10%.</p>

<section><h1>2. Рынок: что продаётся на ЦИАН</h1>
<p><b>Земля без зданий.</b> В Москве продаётся {len(L)} участков коммерческой земли, в старой Москве — {len(old_land)}; медиана
{m(lp_med / 1e3)} тыс. ₽ за м² (половина — {m(lp[len(lp) // 4] / 1e3)}–{m(lp[3 * len(lp) // 4] / 1e3)} тыс.). В радиусе 7,5 км от центра участки
400–13 000 м² стоят 50–250 тыс. ₽ за м², у Арбата — до 680 тыс. Даже по верху этого диапазона участок 484 м² стоил бы
{m(484 * 160e3 / 1e6)} млн — цена объекта определяется зданием и арендатором, а не землёй.</p>
<table class=t><thead><tr><th>ID ЦИАН</th><th>Район</th><th class=r>До центра, км</th><th class=r>Участок, м²</th><th class=r>Млн ₽</th>
<th class=r>Тыс. ₽/м²</th><th>Что продаётся</th></tr></thead><tbody>{land_rows}</tbody></table>
<p class=src>Ближайшие к объекту участки старой Москвы. Полный список — в таблице Excel, лист «Земля (ЗУ) Москва».</p>

<p><b>Земля со зданием.</b> Отдельно стоящих зданий в продаже — {len([x for x in allo.values() if x['category'] == 'buildingSale'])} по Москве,
{len(near_b)} в радиусе 3,6 км и {len(near1)} в радиусе 1 км. Здание 800–2 000 м² в 1 км от нас продаётся одно — Рубцовско-Дворцовая, 4, и до метро от него 12 минут.
Модель цены по 933 зданиям Москвы (удалённость от центра и метро, площадь, арендатор) даёт для нашей точки и площади около
317 тыс. ₽ за м² в объявлениях.</p>
<div class=two><div><img class=full src="file://{os.path.abspath(f"{D}/map.png")}"></div>
<div><p class=src style="margin-top:0">Синие — 12 аналогов-зданий для сравнения (номера как в таблице), оранжевые — готовый арендный бизнес (Г1–Г3),
звезда — Стромынка, 2.</p>
<table class=t><thead><tr><th></th><th>Готовый арендный бизнес</th><th class=r>м²</th><th class=r>Млн</th><th class=r>Тыс./м²</th>
<th class=r>Доход.</th><th class=r>Мин.</th></tr></thead><tbody>
{"".join(f'<tr><td>Г{n}</td><td>{S(allo[str(c)]["address"])}</td><td class=r>{m(allo[str(c)]["area"])}</td><td class=r>{m(allo[str(c)]["price"] / 1e6)}</td><td class=r>{m(allo[str(c)]["price"] / allo[str(c)]["area"] / 1e3)}</td><td class=r>{m(Y.get(str(c)) or 0, 1)}%</td><td class=r>{S(walk(allo[str(c)]))}</td></tr>' for n, c in enumerate(GAB, 1))}
</tbody></table>
<p class=src>Г1 — торговый центр у того же метро «Сокольники»; Г3 — магазин с «Пятёрочкой» на той же Стромынке.</p>
<p><b>Доходность.</b> {len(ny)} предложений готового арендного бизнеса от 300 м² в 3,5 км: медиана {m(ny_q[1], 1)}%,
половина — {m(ny_q[0], 1)}–{m(ny_q[2], 1)}%.</p>
<p><b>Аренда рядом.</b> Офисы, здания и ПСН от 300 м² в 1,5 км — {m(rent_q[0])}–{m(rent_q[2])} ₽ за м² в месяц (медиана {m(rent_q[1])}).
МФЦ платит 2 740 ₽ за всё здание, включая подвал, — это рыночная ставка: ни занижения, ни запаса для роста.</p></div></div>
<img class=full src="file://{os.path.abspath(f"{D}/yields.png")}">
</section>

<section class=pb><h1>3. Аналоги-здания</h1>
<p class=src style="margin-top:-2px">Отдельно стоящие здания 400–2 100 м² в радиусе 2 км. Цены объявлений; сделки обычно проходят ниже.</p>
<div class=cards>{cards}</div>
<table class=t style="margin-top:8px"><thead><tr><th>№</th><th>ID ЦИАН</th><th>Адрес</th><th class=r>м²</th><th class=r>Млн ₽</th><th class=r>Тыс. ₽/м²</th>
<th class=r>Метро, мин</th><th>Земля</th><th>Состояние</th></tr></thead><tbody>{comp_rows}</tbody></table>
</section>

<section class=pb><h1>4. Оценка</h1>
<img class=full src="file://{os.path.abspath(f"{D}/bars.png")}">
<div class=two><div>
<h2>Сравнительный подход</h2>
<table class=t><tbody>
<tr><td>Медиана цены 12 аналогов</td><td class=r>{m(val["med"] / 1e3)} тыс. ₽/м²</td></tr>
{adj_rows}
<tr class=hl><td>Цена м² для нашего здания</td><td class=r>{m(val["cmp_m2"] / 1e3)} тыс. ₽/м²</td></tr>
<tr class=hl><td>Стоимость, 1 184,5 м²</td><td class=r>{m(val["cmp"])} млн</td></tr>
</tbody></table>
</div><div>
<h2>Доходный подход</h2>
<table class=t><tbody>
<tr><td>Аренда МФЦ, с НДС</td><td class=r>{m(RENT_NOW / 1e6, 2)} млн/год</td></tr>
<tr><td>Минус эксплуатационные расходы</td><td class=r>−{m(OPEX / 1e6, 2)} млн</td></tr>
<tr><td>Доход для расчёта</td><td class=r>{m(val["eff"] / 1e6, 2)} млн</td></tr>
<tr><td>При доходности 9%</td><td class=r>{m(inc[0.09])} млн</td></tr>
<tr class=hl><td>При доходности 9,5% (база)</td><td class=r>{m(inc[0.095])} млн</td></tr>
<tr><td>При доходности 10,5%</td><td class=r>{m(inc[0.105])} млн</td></tr>
</tbody></table>
<p class=src>Доходность ниже рыночной медианы ({m(ny_q[1], 1)}%) — за госарендатора; не ниже 9% — из-за короткого договора без индексации.</p>
</div></div>
<div class=box><p><b>Рыночная стоимость — {m(val["lo"], 0)}–{m(val["hi"], 0)} млн, центр ≈ {m(val["base"], 0)} млн</b>
(доходный подход с весом 60%, сравнительный — 40%). Предложение 550 млн выше центра оценки на {m(val["over"] * 100)}%
и выше верхней границы на {m(val["over_lo"] * 100)}%. Модель по 933 зданиям Москвы даёт близкую цифру — около 375 млн по ценам объявлений.</p></div>
</section>

<section><h1>5. Доводы в нашу пользу и против</h1>
<div class=two><div>
<h2>В нашу пользу</h2>
<ul class=pro>
<li><b>Метро в 2–3 минутах</b>, две линии (Сокольническая и БКЛ). У аналогов-зданий в медиане {m(comp_walk, 1)} минуты; так же близко только
торговые центры на Сокольнической, 9А и Большой Семёновской, 17А.</li>
<li><b>Первая линия Стромынки у Сокольнической площади</b> — поток пассажиров метро и посетителей парка «Сокольники».</li>
<li><b>Здание целиком у государственного арендатора с 2015 года.</b> Ставка 2 740 ₽ за м² в рынке ({m(rent_q[0])}–{m(rent_q[2])} ₽ рядом),
поэтому доход не завышен и устойчив.</li>
<li><b>Редкий формат.</b> Отдельно стоящее здание около 1 200 м² у метро. В 1 км в продаже одно похожее по размеру —
Рубцовско-Дворцовая, 4 за 488 млн (450 тыс./м²), без отделки и в 12 минутах от метро.</li>
<li><b>Аренда земли на 40 лет</b> (до 2066 г.) и возможность выкупить участок под зданием — его кадастровая стоимость всего 12,5 млн
(условия выкупа нужно уточнить в ДГИ).</li>
</ul></div><div>
<h2>Против нас</h2>
<ul class=con>
<li><b>Здание 1964 года, каркасно-панельное, 30% площади — подвал.</b> Дорогие аналоги (380–520 тыс./м²) — кирпичные особняки
после реконструкции: Ольховская, 45С3; Электрозаводская, 35С4; Краснопрудная, 30-34С3.</li>
<li><b>Земля не в собственности, на участке зоны охраны наследия и археология.</b> Надстроить или построить новое почти нельзя,
у участка 484 м² нет девелоперского потенциала.</li>
<li><b>Договор с МФЦ заканчивается 30.11.2026.</b> МФЦ предлагает текущую ставку без индексации на 3 года или на 1 год;
эксплуатационные расходы остаются на собственнике.</li>
<li><b>Готовый арендный бизнес рядом дешевле:</b> ТЦ на Сокольнической, 9А у того же метро — 196 тыс./м² при доходности 9%;
Большая Семёновская, 17А — 288 тыс./м² и 10%; Стромынка, 19А с «Пятёрочкой» — 243 тыс./м² и 10,5%.</li>
<li><b>Большой выбор у покупателя:</b> {len(near_b)} зданий в продаже в 3,6 км, ставки по кредитам высокие, инвесторы ждут 9–11%.</li>
</ul></div></div>

<h1>6. Предложение 550 млн</h1>
<table class=t><tbody>
<tr><td>Рыночная стоимость (центр / диапазон)</td><td class=r>{m(val["base"])} млн / {m(val["lo"])}–{m(val["hi"])} млн</td></tr>
<tr class=hl><td>Предложение выше рынка</td><td class=r>на {m(val["over"] * 100)}% (+{m(OFFER - val["base"])} млн); от +{m(val["over_lo"] * 100)} до +{m(val["over_hi"] * 100)}%</td></tr>
<tr><td>Цена за м²: предложение / наша оценка / медиана аналогов</td><td class=r>{m(OFFER * 1e3 / AREA)} / {m(val["cmp_m2"] / 1e3)} / {m(val["med"] / 1e3)} тыс. ₽</td></tr>
<tr><td>Доходность покупателя при текущей аренде</td><td class=r>{m(val["y_offer"] * 100, 1)}% (за вычетом эксплуатации — {m(val["y_offer_eff"] * 100, 1)}%)</td></tr>
<tr><td>Аренда, при которой 550 млн — рынок (доходность 10%)</td><td class=r>{m(val["need_rent"] / 1e6, 0)} млн в год, {m(val["need_rent"] / AREA / 12)} ₽/м² в месяц</td></tr>
</tbody></table>
<ul>
<li><b>Вывод:</b> сумма выше рынка на {m(val["over"] * 100)}% (от {m(val["over_lo"] * 100)} до {m(val["over_hi"] * 100)}% в зависимости от допущений) — в нашу пользу. Альтернативного покупателя по такой цене
на открытом рынке, скорее всего, не будет: аналоги с арендаторами продаются по 200–290 тыс. ₽ за м².</li>
<li><b>Почему покупатель может платить больше:</b> стратегический интерес — соседние участки (общий участок :1863), собственное
использование у метро и парка, расчёт на рост аренды или на выкуп земли.</li>
<li><b>Как не потерять сделку:</b> раскрыть условия МФЦ до подписания, зафиксировать цену и задаток, согласовать НДС и форму сделки
(продажа здания с правом аренды земли или продажа долей ООО), снять вопрос с соарендатором-физлицом по участку :4.</li>
</ul>
<p class=src>Как считали: выгрузка всех объявлений ЦИАН о продаже коммерческой земли ({len(L)}) и отдельно стоящих зданий
({len([x for x in allo.values() if x['category'] == 'buildingSale'])}) в Москве, коммерции на продажу и в аренду в Сокольниках и соседних районах,
карточки {len(C)} ближайших зданий. Доходность — заявленная продавцами или рассчитанная из дохода в объявлении. Все цены — цены объявлений.
Расчёт с формулами и полные списки — в таблице «Стромынка2_аналитика.xlsx».</p>
</section>'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'


def write_pdf(html):
    open(HTML, 'w').write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath(HTML))
        pg.wait_for_timeout(1500)
        pg.pdf(path=PDF, format='A4', print_background=True, margin=dict(top='17mm', bottom='14mm', left='16mm', right='15mm'),
               display_header_footer=True,
               header_template='<div style="font-family:Carlito,sans-serif;font-size:7.5pt;color:#555;width:100%;padding:0 15mm 0 16mm;'
                               'display:flex;justify-content:space-between"><span>Стромынка, 2: рыночная стоимость</span>'
                               f'<span>{DATE}</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()


if __name__ == '__main__':
    os.makedirs('reports', exist_ok=True)
    allo, C, L, R, Y, B = load()
    val = valuation(allo)
    site_png(f'{D}/site.png')
    map_png(allo, f'{D}/map.png')
    bars_png(allo, val, f'{D}/bars.png')
    yields_png(allo, Y, val, f'{D}/yields.png')
    write_xlsx(allo, C, L, R, Y, B, val)
    write_pdf(build_html(allo, C, L, R, Y, val))
    print(json.dumps({k: (round(v, 3) if isinstance(v, float) else v) for k, v in val.items() if k != 'inc'}, ensure_ascii=False))
    print({k: round(v, 1) for k, v in val['inc'].items()})
