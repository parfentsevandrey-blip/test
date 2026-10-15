#!/usr/bin/env python3
"""Наглядный разбор цены квартиры на Кутузовском, 12 на конкретных аналогах: фото, характеристики, слепая оценка ремонта,
отличие от нашей квартиры и от модели цены (flat_value.py).

python3 flat_examples.py → reports/Кутузовский12_разбор_на_примерах.pdf
"""
import json
import math
import os
from html import escape

import numpy as np

import flat_report as fr
import flat_value as fv

PDF = 'reports/Кутузовский12_разбор_на_примерах.pdf'
HTML = 'reports/kutuzovsky12_examples.html'
PNG = 'reports/kutuzovsky12_examples_chart.png'
D = fr.D
OUR = fr.OUR
OUR_PHOTOS = [0, 3, 9, 18, 20]          # реальные кадры: кухня-гостиная, кухня, спальня, прихожая, вид
OUR_RENDER = 13                          # санузел — 3D-визуализация
DODGE = {OUR: -0.09, 326903675: 0.09}    # сдвиг по горизонтали для точек с одинаковыми баллом и ценой
HOUSE_NOTE = {
    332125658: 'По описанию — кухня-гостиная и одна спальня (у вас две). Похожий лот 95 м² на 2 этаже выставлялся в доме '
               'с сентября 2024 года: 143, затем 133 млн.',
}
# (группа, ID, номера фото, вывод)
GROUPS = [
    ('В вашем доме', [
        (329819607, [0, 5, 12], 'Ремонт сильнее вашего — авторский проект, слэбы камня, деревянный потолок. '
                                'Цену снижали с 160 до 148 млн, с мая покупателя нет.'),
        (332125658, [0, 3, 10], 'Ремонт чуть проще вашего, 2 этаж, окна во двор. На 12 млн дешевле — и тоже без сделки с июля.'),
    ]),
    ('Рядом: ремонт не хуже, а цена ниже', [
        (326130429, [0, 4, 3], 'Пресня, 1,4 км. Ремонт сильнее — орех, оникс, мрамор. Квартира на 3 м² меньше и на 29 млн дешевле.'),
        (334831206, [0, 2, 9], 'У Москва-Сити, 0,9 км. Ремонт 8 из 10, три комнаты, 89 м² — за 94 млн.'),
    ]),
    ('Апартаменты: ещё дешевле, но другой формат', [
        (327945623, [0, 5, 10], 'Тот же район, ремонт сильнее, на 8 м² больше — за 105 млн. Но это апартаменты в башне: '
                                'нет постоянной регистрации, выше налог на имущество и коммунальные платежи.'),
        (323831565, [0, 5, 10], 'Москва-Сити, 61 этаж, панорама. Ремонт сильнее, 102 м² — за 119 млн. Апартаменты в башне — '
                                'покупатель другой, но за м² почти на четверть дешевле вас.'),
    ]),
    ('По вашей цене и дороже: за что платят', [
        (326903675, [0, 2, 13], 'Пресня, 1,4 км. Ремонт как у вас, цена за м² как у вас — и восемь месяцев без покупателя.'),
        (324447008, [0, 1, 6], 'Москва-Сити, 103 м², четыре комнаты, ремонт как у вас — за 175 млн. С ноября 2025 года, '
                               'цену меняли пять раз.'),
        (333995352, [0, 1, 7], 'Клубный дом у Тишинской площади, ремонт сильнее — шпон дуба, травертин. 1,99 млн за м² — '
                               'цена за более центральную локацию и ремонт выше вашего; объявление новое.'),
    ]),
]

CSS = '''
@page { size: A4; margin: 17mm 15mm 14mm 16mm; }
body { font-family: "Carlito", "Liberation Sans", sans-serif; font-size: 10pt; color: #111; line-height: 1.38; margin: 0; }
h1 { font-size: 14.5pt; margin: 0 0 6px; padding-bottom: 3px; border-bottom: 0.8pt solid #111; }
h2 { font-size: 12pt; margin: 8px 0 5px; padding-bottom: 2px; border-bottom: 0.5pt solid #999; break-after: avoid; }
p { margin: 0 0 5px; }
a { color: #111; text-decoration: underline; text-decoration-thickness: 0.4pt; }
.pb { break-before: page; }
.t1 { font-size: 20pt; font-weight: bold; line-height: 1.15; margin: 0 0 3px; }
.t2 { font-size: 11pt; color: #333; margin: 0 0 10px; }
.box { border: 0.8pt solid #111; padding: 7px 10px; margin: 6px 0 8px; }
.box p:last-child { margin: 0; }
.kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0; margin: 8px 0 10px; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; }
.kpi { padding: 5px 8px; } .kpi + .kpi { border-left: 0.4pt solid #bbb; }
.kpi .v { font-size: 15pt; font-weight: bold; } .kpi .l { font-size: 8.5pt; color: #444; line-height: 1.25; }
.src { font-size: 8.3pt; color: #444; margin: 2px 0 6px; }
.card { break-inside: avoid; margin: 0 0 7px; padding-top: 5px; border-top: 0.8pt solid #111; }
.card h3 { font-size: 11.5pt; margin: 0 0 4px; display: flex; justify-content: space-between; gap: 8px; }
.card h3 .n { display: inline-block; min-width: 18px; } .card h3 .id { font-weight: normal; font-size: 9pt; color: #333; }
.ph { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; margin: 0 0 5px; }
.ph img { width: 100%; height: 37mm; object-fit: cover; display: block; }
.ph.our { grid-template-columns: repeat(3, 1fr); } .ph.our img { height: 36mm; }
.ph figure { margin: 0; position: relative; }
.ph figcaption { font-size: 7.8pt; color: #333; margin-top: 1px; }
.cols { display: grid; grid-template-columns: 1.35fr 1fr; gap: 10px; }
table.kv { width: 100%; border-collapse: collapse; font-size: 8.4pt; line-height: 1.28; }
.kv td { padding: 1.4px 3px; vertical-align: top; border-bottom: 0.3pt solid #ddd; }
.kv td:first-child { color: #444; width: 23%; white-space: nowrap; }
.cmp { font-size: 9pt; margin: 0 0 4px; padding: 4px 6px; background: #f2f1ec; }
.cmp b { white-space: nowrap; }
.take { font-size: 9.3pt; margin: 0 0 4px; line-height: 1.32; }
.note { font-size: 8pt; color: #444; line-height: 1.28; }
.score { font-weight: bold; }
table.t { width: 100%; border-collapse: collapse; font-size: 8.8pt; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; }
.t th { text-align: left; font-weight: bold; padding: 3px 4px; border-bottom: 0.6pt solid #111; vertical-align: bottom; }
.t td { padding: 2.4px 4px; vertical-align: top; } .t tbody tr + tr td { border-top: 0.3pt solid #ccc; }
.t .r { text-align: right; white-space: nowrap; } .t tr.our td { background: #f2f1ec; font-weight: bold; }
.chart img { width: 100%; display: block; }
ul { margin: 0 0 6px 16px; padding: 0; } li { margin: 0 0 3px; }
'''


def m(v, d=1):
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def sc(v):
    return m(v, 2).rstrip('0').rstrip(',') if v is not None else '—'


def sign(v, d=0, unit=''):
    s = m(abs(v), d)
    return ('+' if v > 0 else '−' if v < 0 else '') + s + unit


def chart(rows, beta, nums):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    for f in font_manager.findSystemFonts():
        if 'Carlito' in f:
            font_manager.fontManager.addfont(f)
    plt.rcParams.update({'font.family': 'Carlito', 'font.size': 10})
    ink, ink2, grid, surf = '#0b0b0b', '#52514e', '#e4e3df', '#fcfcfb'
    fig, ax = plt.subplots(figsize=(7.4, 4.1), dpi=220)
    fig.patch.set_facecolor(surf)
    ax.set_facecolor(surf)
    pts = [(r, r['blind']['overall']) for r in rows if r['blind'].get('overall') is not None and r['id'] != OUR]
    for apart, col, lab in ((False, '#2a78d6', 'квартиры'), (True, '#eb6834', 'апартаменты')):
        sel = [(s, r['ppsqm'] / 1e6) for r, s in pts if r['apart'] == apart and r['id'] not in nums]
        ax.scatter([a for a, b in sel], [b for a, b in sel], s=26, color=col, alpha=0.35, edgecolor=surf, linewidth=0.8,
                   label=lab, zorder=2)
        sel = [(s + DODGE.get(r['id'], 0), r['ppsqm'] / 1e6, nums[r['id']]) for r, s in pts
               if r['apart'] == apart and r['id'] in nums]
        ax.scatter([a for a, b, n in sel], [b for a, b, n in sel], s=150, color=col, edgecolor=surf, linewidth=1.2, zorder=4)
        for a, b, n in sel:
            ax.text(a, b, str(n), ha='center', va='center', fontsize=8.5, color='white', fontweight='bold', zorder=5)
    xs = np.linspace(4, 10, 40)
    ax.plot(xs, [math.exp(beta[0] + beta[1] * x + beta[8]) for x in xs], color=ink2, linewidth=1.5, zorder=1,
            label='цена по модели: квартира в Дорогомилове, дом 2018+')
    our = next(r for r in rows if r['id'] == OUR)
    so = our['blind']['overall'] + DODGE.get(OUR, 0)
    ax.scatter([so], [our['ppsqm'] / 1e6], s=170, color=ink, edgecolor=surf, linewidth=1.3, zorder=6)
    ax.text(so, our['ppsqm'] / 1e6, 'В', ha='center', va='center', fontsize=8.5, color='white', fontweight='bold', zorder=7)
    ax.annotate('ваша квартира, 145 млн', (so, our['ppsqm'] / 1e6), xytext=(5.1, 1.83), fontsize=9.5, color=ink,
                arrowprops=dict(arrowstyle='-', color=ink2, linewidth=0.8, shrinkB=8))
    ax.set_xlabel('Ремонт по фото, балл (слепая оценка двух оценщиков)', color=ink2)
    ax.set_ylabel('Цена объявления, млн руб. за м²', color=ink2)
    ax.set_xlim(4, 9.6)
    ax.set_ylim(0.8, 2.15)
    ax.yaxis.set_major_locator(matplotlib.ticker.MultipleLocator(0.2))
    ax.yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: f'{v:.1f}'.replace('.', ',')))
    ax.grid(axis='y', color=grid, linewidth=0.8)
    ax.set_axisbelow(True)
    for s in ('top', 'right'):
        ax.spines[s].set_visible(False)
    for s in ('left', 'bottom'):
        ax.spines[s].set_color(grid)
    ax.tick_params(colors=ink2)
    ax.legend(loc='upper left', frameon=False, fontsize=8.8, labelcolor=ink2)
    fig.tight_layout()
    fig.savefig(PNG, facecolor=surf)


def times(n):
    return 'раза' if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14) else 'раз'


def kv_table(r, pm):
    b = r['blind']
    view = (r['review'] or {}).get('view')
    where = 'в вашем доме' if r['km_our'] < 0.05 else f"{m(r['km_our'])} км от вас"
    since = f"{r['since'][8:10]}.{r['since'][5:7]}.{r['since'][:4]}"
    ch = len(r['price_changes']) - 1
    if r['id'] == OUR:
        since = '03.06.2025 (текущее объявление — с 20.08.2026); цена: 150 → 160 → 162,5 → 152 → 145 млн'
        parking = 'есть, в объявлении не упомянуто'
    else:
        since += f", цена менялась {ch} {times(ch)}" if ch > 0 else ''
        parking = r['parking'] + (': ' + r['parking_note'] if r['parking_note'] else '')
    rows = [
        ('Где', f"{escape(r['address'])}, {escape(r['raion'])} · м. {escape(r['metro'] or '—')}, {r['metro_min']} мин"
                + ('' if r['id'] == OUR else f" · {where}")),
        ('Квартира', f"{m(r['area'])} м², {r['rooms']} комн., этаж {r['floor']} из {r['floors']}"
                     + (f", потолки {m(r['ceiling'], 2).rstrip('0').rstrip(',')} м" if r['ceiling'] else '')),
        ('Дом, вид', ('апартаменты' if r['apart'] else 'квартира') + (f", дом {r['year']} г." if r['year'] else '')
                     + f" · вид: {escape(view) if view and view != 'не видно' else 'на фото не видно'}"),
        ('Ремонт вслепую', f"<span class=score>{sc(b['overall'])} из 10</span> ({' и '.join(sc(x) for x in b['raters'])}) · "
                           f"материалы {sc(b['materials'])}, дизайн {sc(b['design'])}, исполнение {sc(b['execution'])}, "
                           f"комплектность {sc(b['completeness'])}"),
        ('Цена', f"<b>{m(r['price'] / 1e6)} млн ₽</b> · {m(r['ppsqm'] / 1e6, 2)} млн ₽/м²"),
        ('По модели', f"{m(pm, 2)} млн ₽/м² — объявление {'дороже' if r['ppsqm'] / 1e6 > pm else 'дешевле'} "
                      f"на {m(abs(100 * (r['ppsqm'] / 1e6 / pm - 1)), 0)}%"),
        ('В продаже', f'с {since}'),
        ('Машиноместо', escape(parking)),
    ]
    return '<table class=kv>' + ''.join(f'<tr><td>{a}</td><td>{b}</td></tr>' for a, b in rows) + '</table>'


def card(n, r, photos, take, our, pm):
    b, ob = r['blind'], our['blind']
    figs = ''.join(f'<figure><img src="file://{os.path.abspath(f"{D}/photos/{r["id"]}_{k:02d}.jpg")}"></figure>' for k in photos)
    d_price = r['price'] / 1e6 - our['price'] / 1e6
    d_ppsqm = 100 * (r['ppsqm'] / our['ppsqm'] - 1)
    cmp = (f"Против вашей: ремонт <b>{sign(b['overall'] - ob['overall'], 2).rstrip('0').rstrip(',')}</b> балла · "
           f"площадь <b>{sign(r['area'] - our['area'], 1)} м²</b> · цена <b>{sign(d_price, 1)} млн</b> · "
           f"за м² <b>{sign(d_ppsqm, 0, '%')}</b>")
    extra = HOUSE_NOTE.get(r['id'])
    note = (b.get('notes') or [''])[0]
    return f'''<div class=card><h3><span><span class=n>{n}.</span> {escape(r['jk'])} — {m(r['area'], 1)} м², {m(r['price'] / 1e6, 1)} млн ₽</span>
<span class=id><a href="{r['url']}">ЦИАН {r['id']}</a></span></h3>
<div class=ph>{figs}</div>
<div class=cols><div>{kv_table(r, pm)}</div>
<div><p class=cmp>{cmp}</p><p class=take>{take}</p>{f'<p class=take>{escape(extra)}</p>' if extra else ''}
<p class=note>Оценщик: {escape(note)}</p></div></div></div>'''


def build():
    rows = fr.load()
    by = {r['id']: r for r in rows}
    out, comps, beta, s_our = fv.main()
    var = fv.variants()
    vlo, vhi = min(v['price'] for v in var), max(v['price'] for v in var)
    olo, ohi = min(v['over'] for v in var), max(v['over'] for v in var)
    our = by[OUR]
    pm_our = fv.predict(our, our['blind']['overall'], beta)
    nums, n = {}, 0
    for _, items in GROUPS:
        for cid, _, _ in items:
            n += 1
            nums[cid] = n
    chart(rows, beta, nums)

    ob = our['blind']
    our_figs = ''.join(f'<figure><img src="file://{os.path.abspath(f"{D}/our/{k:02d}.jpg")}"></figure>' for k in OUR_PHOTOS[:3])
    our_figs2 = ''.join(f'<figure><img src="file://{os.path.abspath(f"{D}/our/{k:02d}.jpg")}"></figure>' for k in OUR_PHOTOS[3:])
    our_figs2 += (f'<figure><img src="file://{os.path.abspath(f"{D}/our/{OUR_RENDER:02d}.jpg")}">'
                  f'<figcaption>санузел — в объявлении только 3D-визуализация</figcaption></figure>')
    rated = [r for r in rows if r['blind'].get('overall') is not None and r['id'] != OUR]
    higher = sum(1 for r in rated if r['blind']['overall'] > ob['overall'])

    cards = ''
    for title, items in GROUPS:
        block = ''.join(card(nums[cid], by[cid], ph, take, our, fv.predict(by[cid], by[cid]['blind']['overall'], beta))
                        for cid, ph, take in items)
        cards += f'<h2>{escape(title)}</h2>{block}'

    summary_rows = ''
    for cid in [OUR] + list(nums):
        r = by[cid]
        pm = fv.predict(r, r['blind']['overall'], beta)
        summary_rows += (f'<tr class="{"our" if cid == OUR else ""}"><td>{"В" if cid == OUR else nums[cid]}</td><td>{escape(r["jk"])}</td>'
                         f'<td>{"апарт." if r["apart"] else "кв."}</td><td class=r>{m(r["area"])}</td><td class=r>{sc(r["blind"]["overall"])}</td>'
                         f'<td class=r>{m(r["price"] / 1e6)}</td><td class=r>{m(r["ppsqm"] / 1e6, 2)}</td><td class=r>{m(pm, 2)}</td>'
                         f'<td class=r>{sign(100 * (r["ppsqm"] / 1e6 / pm - 1), 0, "%")}</td>'
                         f'<td>{r["since"][8:10]}.{r["since"][5:7]}.{r["since"][2:4]}</td></tr>')

    body = f'''
<div class=t1>Стоит ли квартира 145 млн: разбор на примерах</div>
<div class=t2>Кутузовский пр-т, 12, ЖК «Кутузовский XII» · 95 м², 6 этаж из 11 · объявления ЦИАН на 14 октября 2026 г.</div>
<div class=kpis>
<div class=kpi><div class=v>145 млн</div><div class=l>цена в объявлении,<br>{m(our['ppsqm'] / 1e6, 2)} млн ₽/м²</div></div>
<div class=kpi><div class=v>{sc(ob['overall'])} из 10</div><div class=l>ремонт по слепой оценке;<br>у {higher} из {len(rated)} аналогов выше</div></div>
<div class=kpi><div class=v>{m(vlo, 0)}–{m(vhi, 0)} млн</div><div class=l>за что рынок выставляет<br>квартиру с таким ремонтом</div></div>
<div class=kpi><div class=v>+{m(olo, 0)}–{m(ohi, 0)}%</div><div class=l>на столько ваша цена<br>выше рынка</div></div>
</div>
<div class=box><p><b>Вывод.</b> Ремонт хороший, но не лучший на рынке. Покупатель с бюджетом 145 млн видит рядом квартиры
с ремонтом не хуже — за 94–116 млн, а лоты по вашей цене стоят месяцами без сделки, в том числе два в вашем доме.
Предложение 140 млн с машиноместом даёт за саму квартиру 128–132 млн — это рыночный уровень.</p></div>
<div class=chart><img src="file://{os.path.abspath(PNG)}"></div>
<p class=src>Каждая точка — аналог 85–110 м² в ЦАО и Дорогомилове с ремонтом «дизайнерский» или «евро» ({len(rated)} квартир
с реальными фото интерьера). Цифры — примеры из этого разбора. Линия — сколько в среднем стоит м² при данном ремонте
для квартиры в Дорогомилове в доме 2018 г. и новее. Каждый балл ремонта добавляет к цене м² около {m(out['coef']['ремонт, балл']['pct'], 0)}%.</p>
<p class=src>Как оценивали ремонт: два независимых оценщика смотрели первые 16 фото каждого объявления вслепую, без цены,
адреса и названия ЖК; итог — среднее их оценок. Оценки почти совпали (корреляция 0,94). Шкала: 5 — отделка застройщика,
7 — хороший современный ремонт, 8 — качественный дизайнерский, 9 — авторский с дорогими материалами.</p>

<section class=pb><h2 style="margin-top:0">Ваша квартира</h2>
<div class=card style="border-top:none;padding-top:0">
<div class="ph our">{our_figs}</div><div class="ph our">{our_figs2}</div>
<div class=cols><div>{kv_table(our, pm_our)}</div>
<div><p class=take>Ремонт свежий, дизайнерский, с мебелью и техникой: индивидуальная столярка, стеновые панели, крупноформатный
керамогранит под мрамор, продуманный свет. Слабее всего — материалы ({sc(ob['materials'])} из 10): керамогранит вместо камня,
популярная бежевая неоклассика.</p>
<p class=take>Все пять фото санузлов в объявлении — 3D-визуализации. Оценщики оценивали ремонт по комнатам и кухне
и снизили уверенность. У конкурентов с ремонтом 8+ санузлы сняты вживую.</p>
<p class=note>Оценщик: {escape((ob.get('notes') or [''])[1] if len(ob.get('notes') or []) > 1 else '')}</p></div></div></div>
{cards}</section>

<section class=pb><h2>Сводка по примерам</h2>
<table class=t><thead><tr><th>№</th><th>ЖК</th><th>Тип</th><th class=r>м²</th><th class=r>Ремонт</th><th class=r>Млн ₽</th>
<th class=r>Млн ₽/м²</th><th class=r>По модели</th><th class=r>К модели</th><th>В продаже с</th></tr></thead><tbody>{summary_rows}</tbody></table>
<p class=src>«По модели» — цена м², которую рынок в среднем назначает за такой ремонт, район, тип жилья, вид, этажность и возраст дома
(модель по {out['n']} аналогам). «К модели» — на сколько объявление дороже (+) или дешевле (−).</p>
<h2>Что из этого следует</h2>
<ul>
<li><b>Лоты дороже модели не продаются.</b> Ваша квартира (+{m(out['resid_pct'], 0)}%), обе квартиры в вашем доме, «Резиденция МОНЭ»
и City Park за 175 млн выставлены выше модели и стоят месяцами.</li>
<li><b>За те же деньги покупатель получает больше.</b> RedSide и City Park рядом — ремонт не хуже, цена на 29–51 млн ниже.
Апартаменты у Поклонной и в Сити ещё дешевле, хотя это другой формат.</li>
<li><b>Дороже 1,6 млн за м² платят за другое.</b> Ремонт 8,5–9 и более центральная локация: Дом на Тишинке, авторская квартира в вашем доме.</li>
<li><b>Рыночная цена вашей квартиры — {m(vlo, 0)}–{m(vhi, 0)} млн без машиноместа.</b> Предложение 140 млн с машиноместом
за 8–12 млн даёт за квартиру 128–132 млн — это рынок.</li>
<li><b>Что поднимет цену в глазах покупателя:</b> реальные фото санузлов вместо визуализаций и профессиональная съёмка,
как у квартиры № 1.</li>
</ul>
<p class=src>Цены — из объявлений, а не сделок; сделки обычно проходят ниже. Полный список аналогов с оценками и ссылками —
в таблице «Кутузовский12_аналоги.xlsx».</p></section>'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'


def main():
    html = build()
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
                               'display:flex;justify-content:space-between"><span>Кутузовский пр-т, 12: разбор цены на примерах</span>'
                               f'<span>{fr.DATE}</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()
    print(PDF)


if __name__ == '__main__':
    main()
