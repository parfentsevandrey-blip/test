#!/usr/bin/env python3
"""Кутузовский XII, 95 м²: сравнение с аналогами по Москве — таблица Excel и записка PDF.

Данные: flat_comps.py (выдача, карточки, фото), оценки ремонта по фото (flat-data/reviews.jsonl),
машиноместа (flat_parking.py), история дома (flat-data/house_hist_*.json).
python3 flat_report.py → reports/Кутузовский12_аналоги.xlsx, reports/Кутузовский12_оценка_и_аналоги.pdf
"""
import json
import os
import re
import statistics as st

import flat_parking as fp

D = 'flat-data'
OUR = 333127854
XLSX = 'reports/Кутузовский12_аналоги.xlsx'
PDF = 'reports/Кутузовский12_оценка_и_аналоги.pdf'
DATE = '14 октября 2026 г.'
REPAIR_RU = {'design': 'дизайнерский', 'euro': 'евро', 'cosmetic': 'косметический', 'no': 'без ремонта'}
MONTHS = {'янв': 1, 'фев': 2, 'мар': 3, 'апр': 4, 'мая': 5, 'июн': 6, 'июл': 7, 'авг': 8, 'сен': 9, 'окт': 10, 'ноя': 11, 'дек': 12}


def load():
    offers = json.load(open(f'{D}/offers.json'))
    cards = json.load(open(f'{D}/cards.json'))
    reviews = {r['id']: r for r in map(json.loads, open(f'{D}/reviews.jsonl')) if r}
    blind = json.load(open(f'{D}/blind_ratings.json')) if os.path.exists(f'{D}/blind_ratings.json') else {}
    rows = []
    for u in json.load(open(f'{D}/unique.json')):
        cid = u['id']
        x = dict(offers[str(cid)])
        c = cards.get(str(cid), {})
        ids = [cid] + u['dups']
        pk, pk_note = fp.best([fp.classify(i, cards.get(str(i), {}).get('description') or offers[str(i)]['description'])
                               for i in ids])
        pk_note = pk_note or ''
        x.update(dups=u['dups'], year=c.get('year') or x['year'], repair=REPAIR_RU.get(c.get('repair'), x['repair_filter']),
                 cls=c.get('cls'), ceiling=float(c['ceiling']) if c.get('ceiling') else None, wcs=c.get('wcs'),
                 card_parking=c.get('parking'), price_changes=c.get('price_changes') or [],
                 since=(c.get('created') or x['created'] or '')[:10], parking=pk, parking_note=pk_note,
                 review=reviews.get(cid), blind=blind.get(str(cid)) or {})
        rows.append(x)
    return rows


def house_history():
    out = []
    for o in json.load(open(f'{D}/house_hist_sale.json')):
        title = o['title'].replace('\xa0', ' ')
        m = re.match(r'([\d,]+) м², (.+?), (\d+)/(\d+) этаж', title)
        if not m:
            continue
        price = float(o['prices']['price'].replace('\xa0', ' ').split()[0].replace(',', '.')) * 1e6
        area = float(m.group(1).replace(',', '.'))
        out.append(dict(id=o['id'], status='в продаже' if o['status'] == 'published' else 'снято', start=o.get('dateStart'),
                        end=o.get('dateEnd'), area=area, rooms=m.group(2), floor=int(m.group(3)), price=price,
                        ppsqm=price / area, change={'decrease': 'снижалась', 'increase': 'повышалась'}.get(o['prices'].get('priceDiff'), ''),
                        days=int(re.match(r'\d+', o.get('exposition') or '0').group()) if re.match(r'\d+', o.get('exposition') or '') else None))
    return out


def q(vals, p):
    v = sorted(vals)
    return v[min(len(v) - 1, int(round(p * (len(v) - 1))))] if v else None


def write_xlsx(rows, hist):
    from openpyxl import Workbook
    from openpyxl.comments import Comment
    from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
    from openpyxl.utils import get_column_letter
    F, B = 'Arial', Font(name='Arial', bold=True, size=9)
    grid = Side(style='thin', color='C8CDD3')
    border = Border(left=grid, right=grid, top=grid, bottom=grid)
    head_fill, our_fill = PatternFill('solid', fgColor='DDE3EA'), PatternFill('solid', fgColor='FFF2CC')
    font, link = Font(name=F, size=9), Font(name=F, size=9, color='1F4E9A', underline='single')

    def sheet(ws, cols, data, links=(), fmt=None, fill_row=None, freeze='C2'):
        for k, (h, w, note) in enumerate(cols, 1):
            c = ws.cell(1, k, h)
            c.font, c.fill, c.border = B, head_fill, border
            c.alignment = Alignment(wrap_text=True, vertical='center', horizontal='center')
            if note:
                c.comment = Comment(note, 'анализ')
            ws.column_dimensions[get_column_letter(k)].width = w
        ws.row_dimensions[1].height = 40
        for n, (vals, url) in enumerate(data, 2):
            for k, v in enumerate(vals, 1):
                c = ws.cell(n, k, v)
                c.font, c.border = font, border
                c.alignment = Alignment(wrap_text=True, vertical='top')
                if fmt and k in fmt:
                    c.number_format = fmt[k]
                if fill_row and fill_row(n - 2):
                    c.fill = our_fill
            for k in links:
                if url:
                    ws.cell(n, k).hyperlink = url
                    ws.cell(n, k).font = link
        ws.freeze_panes = freeze
        ws.auto_filter.ref = f'A1:{get_column_letter(len(cols))}{len(data) + 1}'

    wb = Workbook()
    ws = wb.active
    ws.title = 'Аналоги'
    cols = [('№', 5, None), ('ID ЦИАН', 11, 'Ссылка на объявление; жёлтым — наша квартира'), ('ЖК', 20, None), ('Район', 13, None),
            ('Адрес', 22, None), ('До нас, км', 7, 'По прямой от Кутузовского пр-та, 12'), ('До Кремля, км', 7, None),
            ('Метро', 14, None), ('Мин. до метро', 6, None), ('Площадь, м²', 8, None), ('Комнат', 6, None), ('Этаж', 7, None),
            ('Год дома', 7, None), ('Апартаменты', 9, 'Апартаменты обычно дешевле квартир: нет прописки, выше налог и коммуналка'),
            ('Цена, млн ₽', 8, 'Цена в объявлении на 14.10.2026'), ('Млн ₽ за м²', 8, None),
            ('Ремонт по объявлению', 11, None),
            ('Ремонт вслепую, балл', 8, 'Среднее двух независимых слепых оценок по фото (без цены и адреса на листе): '
             '1 — без отделки, 5 — отделка застройщика, 8 — качественный дизайнерский ремонт, 10 — de luxe'),
            ('Оценщик 1', 6, None), ('Оценщик 2', 6, None), ('Материалы', 7, None), ('Дизайн', 7, None), ('Исполнение', 7, None),
            ('Комплектность', 8, 'Кухня, санузлы, свет, мебель и техника'),
            ('Цена по модели, млн ₽/м²', 9, 'Цена м², которую модель ожидает при таком ремонте, районе, типе (квартира/апартаменты), виде, '
             'этажности и возрасте дома (лист «Модель»)'),
            ('Цена к модели, %', 8, 'На сколько цена объявления выше (+) или ниже (−) модели'),
            ('Стиль', 22, None), ('Фото', 12, 'Реальные фото или визуализации'),
            ('Мебель', 9, None), ('Вид', 12, None), ('Комментарий оценщиков', 52, None),
            ('Машиноместо', 16, 'Из описания объявления: включено в цену / продаётся отдельно / есть, условия не указаны'),
            ('Условия по м/м', 22, None), ('Продавец', 18, None), ('В продаже с', 10, 'Дата публикации текущего объявления'),
            ('Изменения цены', 20, None), ('Ещё объявлений этой квартиры', 9, 'Сколько дублей (другие агенты) найдено')]
    import flat_value as fv
    _, _, beta, _ = fv.main()
    order = sorted(rows, key=lambda r: (r['id'] != OUR, r['km_our'] or 99))
    data = []
    for n, r in enumerate(order, 1):
        rv, bl = r['review'] or {}, r['blind']
        pm = fv.predict(r, bl['overall'], beta) if bl.get('overall') is not None else None
        notes = ' | '.join(x for x in (bl.get('notes') or []) if x) or rv.get('notes')
        raters = (bl.get('raters') or []) + [None, None]
        changes = '; '.join(f'{d[8:10]}.{d[5:7]}.{d[2:4]}: {p / 1e6:.1f}' for d, p in r['price_changes']) if len(r['price_changes']) > 1 else ''
        data.append(([n, r['id'], r['jk'], r['raion'], r['address'], r['km_our'], r['km_kremlin'], r['metro'], r['metro_min'],
                      r['area'], r['rooms'], f"{r['floor']}/{r['floors']}" if r['floors'] else r['floor'], r['year'],
                      'да' if r['apart'] else None, round(r['price'] / 1e6, 1), round(r['ppsqm'] / 1e6, 3), r['repair'],
                      bl.get('overall'), raters[0], raters[1], bl.get('materials'), bl.get('design'), bl.get('execution'),
                      bl.get('completeness'), round(pm, 3) if pm else None,
                      round(100 * (r['ppsqm'] / 1e6 / pm - 1)) if pm else None, bl.get('style') or rv.get('style'),
                      rv.get('photos'), rv.get('furniture'), rv.get('view'), notes, r['parking'], r['parking_note'] or None, r['seller'], r['since'],
                      changes or None, len(r['dups']) or None], r['url']))
    sheet(ws, cols, data, links=(2,), fmt={10: '0.0', 15: '0.0', 16: '0.000', 6: '0.0', 7: '0.0', 18: '0.0#', 25: '0.000',
                                           26: '+0;-0;0'},
          fill_row=lambda i: order[i]['id'] == OUR)

    ws2 = wb.create_sheet('Машиноместа')
    cols2 = [('ID ЦИАН', 11, None), ('Что продаётся', 14, None), ('ЖК', 22, None), ('Адрес', 26, None), ('Площадь, м²', 8, None),
             ('Цена, млн ₽', 8, None), ('Условия по м/м', 40, None)]
    data2 = []
    for r in sorted(rows, key=lambda r: fp.RANK.index(r['parking'])):
        if r['parking'] in fp.RANK[:4]:
            data2.append(([r['id'], 'квартира + м/м', r['jk'], r['address'], r['area'], round(r['price'] / 1e6, 1),
                           f"{r['parking']}{': ' + r['parking_note'] if r['parking_note'] else ''}"], r['url']))
    for g in sorted(json.load(open(f'{D}/parking_dorogomilovo.json')), key=lambda g: -(g['price'] or 0)):
        data2.append(([g['id'], 'машиноместо', g['jk'], g['address'], float(g['area']) if g['area'] else None,
                       round(g['price'] / 1e6, 2) if g['price'] else None, 'продажа машиноместа, район Дорогомилово'], g['url']))
    sheet(ws2, cols2, data2, links=(1,), fmt={5: '0.0', 6: '0.0'}, freeze='B2')

    ws3 = wb.create_sheet('История дома')
    cols3 = [('ID ЦИАН', 11, None), ('Статус', 10, None), ('Выставлено', 11, None), ('Снято', 11, None), ('Площадь, м²', 8, None),
             ('Комнат', 9, None), ('Этаж', 6, None), ('Цена, млн ₽', 8, 'Последняя цена объявления'), ('Млн ₽ за м²', 8, None),
             ('Цена менялась', 10, None), ('Дней в продаже', 8, None)]
    data3 = [([h['id'], h['status'], h['start'], h['end'], h['area'], h['rooms'], h['floor'], round(h['price'] / 1e6, 1),
               round(h['ppsqm'] / 1e6, 3), h['change'] or None, h['days']], f"https://www.cian.ru/sale/flat/{h['id']}/") for h in hist]
    sheet(ws3, cols3, data3, links=(1,), fmt={5: '0.0', 8: '0.0', 9: '0.000'}, freeze='B2')

    ws5 = wb.create_sheet('Модель')
    out = json.load(open(f'{D}/value.json'))
    cols5 = [('Признак', 28, None), ('Коэффициент (лог. цены м²)', 12, None), ('Ст. ошибка', 9, 'Робастная (HC1)'),
             ('Влияние на цену м², %', 10, 'exp(коэффициент) − 1')]
    data5 = [([k, round(v['b'], 3), round(v['se'], 3), round(v['pct'], 1) if k != 'константа' else None], None)
             for k, v in out['coef'].items()]
    sheet(ws5, cols5, data5, freeze='A2')
    r0 = len(data5) + 3
    ws5.cell(r0, 1, f"Аналогов: {out['n']}, R² = {out['r2']:.2f}. База: квартира в Дорогомилове, дом до 2018 г., без панорамы, "
                    f"не в башне. Наша квартира: ремонт {out['our_score']:g} → модель {out['pred_ppsqm']:.3f} млн ₽/м², "
                    f"{out['pred_price']:.1f} млн ₽ за 95 м²; цена объявления на {out['resid_pct']:.0f}% выше.").font = Font(name=F, size=9)
    ws5.cell(r0 + 2, 1, 'Проверка устойчивости').font = B
    for k, (h, w) in enumerate([('Вариант', 34), ('Аналогов', 8), ('R²', 6), ('Балл ремонта, % к цене м²', 10),
                                ('Цена по модели, млн ₽', 10), ('Наша цена выше модели, %', 10)], 1):
        c = ws5.cell(r0 + 3, k, h)
        c.font, c.fill, c.border = B, head_fill, border
        c.alignment = Alignment(wrap_text=True)
    for n, v in enumerate(fv.variants(), r0 + 4):
        for k, val in enumerate([v['name'], v['n'], round(float(v['r2']), 2), round(v['point'], 1), round(v['price'], 1),
                                 round(v['over'], 1)], 1):
            c = ws5.cell(n, k, val)
            c.font, c.border = font, border
    ws5.column_dimensions['A'].width = 34

    ws4 = wb.create_sheet('Пояснения')
    ws4.column_dimensions['A'].width = 28
    ws4.column_dimensions['B'].width = 100
    notes = [
        ('Аналоги квартиры Кутузовский пр-т, 12 (ЖК «Кутузовский XII»), 95 м²', None),
        ('Дата', DATE),
        ('Источник', 'ЦИАН: выдача через API, карточки объявлений, история объявлений дома, фото объявлений'),
        ('Отбор аналогов', 'ЦАО и Дорогомилово; 80–115 м²; 2–4 комнаты; 90–250 млн ₽; ремонт «дизайнерский» или «евро» по данным '
                           'продавца; дом 2005 г. и новее или современный ЖК; строящиеся дома исключены'),
        ('Дубли', 'Одна квартира, выставленная несколькими агентами, показана одной строкой (одинаковые ЖК, площадь и этаж)'),
        ('Ремонт вслепую', 'Каждую квартиру, включая нашу, независимо оценили два оценщика по листу из первых 16 фото '
                           'объявления; на листе только код, без цены, адреса и ЖК. Итог — среднее двух оценок. Согласованность '
                           'оценщиков: корреляция 0,94, средняя разница 0,3 балла. Квартиры без реальных фото интерьера не оценивались'),
        ('Модель', 'Логарифм цены м² объясняется баллом ремонта, районом, типом (квартира/апартаменты), панорамным видом, '
                   'башней 30+ этажей и возрастом дома. Коэффициенты и проверки — на листе «Модель»'),
        ('Машиноместа', 'Классы по тексту объявлений: «включено» — продавец прямо пишет, что м/м входит в цену; '
                        '«отдельно» — продаётся за доплату; «есть м/м, условия не указаны» — нужно уточнять у продавца'),
        ('Цены', 'Цены объявлений, а не сделок; итоговая цена сделки обычно ниже за счёт торга'),
    ]
    for k, (a, b) in enumerate(notes, 1):
        ws4.cell(k, 1, a).font = Font(name=F, bold=True, size=12 if k == 1 else 10)
        c = ws4.cell(k, 2, b)
        c.font, c.alignment = Font(name=F, size=10), Alignment(wrap_text=True, vertical='top')
        ws4.cell(k, 1).alignment = Alignment(wrap_text=True, vertical='top')
    wb.save(XLSX)


# ---------- записка PDF ----------

TIER = ('Дорогомилово', 'Пресненский')      # тот же уровень локации: у ТТК, рядом с Москва-Сити
PARKING_EST = (8, 10, 12)                   # оценка машиноместа в доме, млн ₽: низ / база / верх
OFFER = 140
# фото для записки: (ID, номер фото) — кадры интерьера, выбраны вручную по листам-превью
GRID = [(329819607, 0), (332125658, 0), (334831206, 0), (326130429, 0), (334283941, 1), (324447008, 0),
        (333791672, 0), (334511649, 0)]
OUR_PHOTOS = [0, 9, 18, 20]
PK_SHORT = {'включено': 'включено', 'отдельно, цена указана': 'отдельно', 'продаётся отдельно': 'отдельно',
            'есть м/м, условия не указаны': 'есть, условия?'}

CSS = '''
@page { size: A4; margin: 18mm 17mm 17mm 19mm; }
body { font-family: "Carlito", "Liberation Sans", sans-serif; font-size: 10.3pt; color: #111; line-height: 1.4; }
h1 { font-size: 15pt; margin: 0 0 7px; padding-bottom: 3px; border-bottom: 0.8pt solid #111; }
h2 { font-size: 11.5pt; margin: 13px 0 5px; break-after: avoid; }
p { margin: 0 0 6px; text-align: justify; hyphens: auto; }
ul { margin: 0 0 6px 16px; padding: 0; } li { margin: 0 0 3px; }
a { color: #111; text-decoration: underline; text-decoration-thickness: 0.4pt; }
.pb { page-break-before: always; }
.head .t1 { font-size: 19pt; font-weight: bold; line-height: 1.2; margin-bottom: 4px; }
.head .t2 { font-size: 11pt; color: #333; margin-bottom: 12px; }
.box { border: 0.8pt solid #111; padding: 8px 10px; margin: 6px 0 10px; }
.box p:last-child { margin-bottom: 0; }
.cap { font-size: 9.5pt; margin: 9px 0 3px; break-after: avoid; }
table.t { width: 100%; border-collapse: collapse; font-size: 8.8pt; margin: 0 0 4px; border-top: 1.1pt solid #111; border-bottom: 1.1pt solid #111; }
.t thead th { font-weight: bold; text-align: left; padding: 3px 4px; border-bottom: 0.6pt solid #111; vertical-align: bottom; }
.t td { padding: 2px 4px; vertical-align: top; }
.t tbody tr + tr td { border-top: 0.3pt solid #c8c8c8; }
.t .r { text-align: right; white-space: nowrap; } .t tr.our td { background: #f3f0e6; font-weight: bold; }
.src { font-size: 8.4pt; color: #333; margin: 2px 0 9px; }
.figs { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px 7px; margin: 6px 0 4px; }
figure { margin: 0; break-inside: avoid; } figure img { width: 100%; height: 84px; object-fit: cover; display: block; }
figcaption { font-size: 8pt; line-height: 1.25; margin-top: 2px; color: #222; }
'''


def m(v, d=1):
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def build_html(rows, hist):
    from html import escape
    by = {r['id']: r for r in rows}
    our = by[OUR]
    ad = json.load(open(f'{D}/offerData.json'))
    S = lambda v: escape(str(v)) if v is not None else '—'
    link = lambda r: f'<a href="{r["url"]}">{r["id"]}</a>'
    score = lambda r: r['blind'].get('overall')
    sf = lambda v: m(v, 2).rstrip('0').rstrip(',') if v is not None else '—'      # 7,75 / 8 / 7,5
    import flat_value as fv
    val = json.load(open(f'{D}/value.json'))
    var = fv.variants()
    vlo, vhi = min(v['price'] for v in var), max(v['price'] for v in var)
    olo, ohi = min(v['over'] for v in var), max(v['over'] for v in var)
    s_our = score(our)
    rated = [r for r in rows if score(r) is not None and r['id'] != OUR]
    higher = [r for r in rated if score(r) > s_our]
    bl_our = our['blind']

    # история нашей квартиры и соседей по дому
    mine = sorted([h for h in hist if h['floor'] == 6 and abs(h['area'] - 95) < 0.6 and h['rooms'] == '3-комн.'],
                  key=lambda h: (h['start'][-4:], MONTHS[h['start'].split()[1][:3]], int(h['start'].split()[0])))
    # история дома не содержит само текущее объявление — добавляем его из карточки
    mine.append(dict(id=OUR, status='в продаже', start='20 авг 2026', end=None, area=our['area'], rooms='3-комн.', floor=6,
                     price=our['price'], ppsqm=our['ppsqm'], change='снижалась', days=55))
    nb = sorted([h for h in hist if 85 <= h['area'] <= 110 and h not in mine],
                key=lambda h: (h['status'] != 'в продаже', -(int(h['start'][-4:]) * 100 + MONTHS[h['start'].split()[1][:3]])))

    # аналоги того же уровня локации и ремонта
    tier = sorted([r for r in rows if r['raion'] in TIER and (score(r) or 0) >= 7 and r['km_our'] <= 3.5],
                  key=lambda r: r['ppsqm'])
    shown = [r for r in tier if (score(r) or 0) >= 7.5 or not r['apart']]   # в записке: квартиры 7+ и апартаменты 7,5+
    t8 = [r['ppsqm'] / 1e6 for r in tier if (score(r) or 0) >= 7.5 and r['id'] != OUR]
    t8f = [r['ppsqm'] / 1e6 for r in tier if (score(r) or 0) >= 7.5 and r['id'] != OUR and not r['apart']]
    t7f = [r['ppsqm'] / 1e6 for r in tier if r['id'] != OUR and not r['apart']]
    center = [r for r in rows if r['raion'] not in TIER and (score(r) or 0) >= 7.5]
    cen = [r['ppsqm'] / 1e6 for r in center]
    dev = [r['ppsqm'] / 1e6 for r in rows if r['raion'] in TIER and (r['review'] or {}).get('age') == 'отделка застройщика']
    med = lambda v: st.median(v) if v else None
    ask = our['price'] / 1e6
    net = {p: OFFER - p for p in PARKING_EST}
    per = {p: (OFFER - p) / our['area'] for p in PARKING_EST}
    incl = [r for r in rows if r['parking'] == 'включено']
    sep_price = [r for r in rows if r['parking'] == 'отдельно, цена указана']
    n_sep = sum(1 for r in rows if r['parking'] in ('продаётся отдельно', 'отдельно, цена указана'))
    n_unk = sum(1 for r in rows if r['parking'] == 'есть м/м, условия не указаны')
    gar = json.load(open(f'{D}/parking_dorogomilovo.json'))
    prem = [g for g in gar if g['jk'] in ('Victory Park Residences', 'Поклонная 9') or 'Резервный' in (g['address'] or '')]
    first = mine[0] if mine else None
    in_house_now = [h for h in nb if h['status'] == 'в продаже']
    better = by.get(329819607)
    simpler = by.get(332125658)

    def tr(r, extra_cls=''):
        rv = r['review'] or {}
        return (f'<tr class="{extra_cls}"><td>{link(r) if r["id"] != OUR else "наша"}</td><td>{S(r["jk"])}</td>'
                f'<td class=r>{m(r["area"])}</td><td class=r>{r["floor"]}/{r["floors"]}</td><td>{"апарт." if r["apart"] else "кв."}</td>'
                f'<td class=r>{m(r["price"] / 1e6)}</td><td class=r>{m(r["ppsqm"] / 1e6, 2)}</td><td class=r>{sf(score(r))}</td>'
                f'<td>{S(rv.get("view")).replace(" Сити/реки", "") if rv.get("view") != "не видно" else ""}</td><td>{r["since"][8:10]}.{r["since"][5:7]}.{r["since"][2:4]}</td>'
                f'<td>{PK_SHORT.get(r["parking"], "")}</td></tr>')

    head = ('<thead><tr><th>ID ЦИАН</th><th>ЖК</th><th class=r>м²</th><th class=r>Этаж</th><th>Тип</th><th class=r>Млн ₽</th>'
            '<th class=r>Млн/м²</th><th class=r>Ремонт</th><th>Вид</th><th>С</th><th>Машиноместо</th></tr></thead>')
    tier_rows = ''.join(tr(r, 'our' if r['id'] == OUR else '') for r in shown)
    hist_rows = ''.join(f'<tr><td>{h["start"]}</td><td>{h["end"] or "сейчас"}</td><td class=r>{m(h["price"] / 1e6)}</td>'
                        f'<td class=r>{m(h["ppsqm"] / 1e6, 2)}</td><td class=r>{S(h["days"])}</td></tr>' for h in mine)
    nb_rows = ''.join(f'<tr><td><a href="https://www.cian.ru/sale/flat/{h["id"]}/">{h["id"]}</a></td><td>{h["status"]}</td>'
                      f'<td>{h["start"]}</td><td>{h["end"] or ""}</td><td class=r>{m(h["area"])}</td><td class=r>{h["floor"]}</td>'
                      f'<td class=r>{m(h["price"] / 1e6)}</td><td class=r>{m(h["ppsqm"] / 1e6, 2)}</td><td class=r>{S(h["days"])}</td></tr>'
                      for h in [h for h in nb if h['status'] == 'в продаже' or h['start'][-4:] >= '2025'][:8])
    incl_rows = ''.join(f'<tr><td>{link(r)}</td><td>{S(r["jk"])}</td><td>{"апарт." if r["apart"] else "кв."}</td>'
                        f'<td class=r>{m(r["area"])}</td><td class=r>{m(r["price"] / 1e6)}</td><td class=r>{sf(score(r))}</td>'
                        f'<td>{S(r["parking_note"])}</td></tr>' for r in incl + sep_price)
    gar_rows = ''.join(f'<tr><td><a href="{g["url"]}">{g["id"]}</a></td><td>{S(g["jk"])}{", " + S(g["address"]) if g["address"] else ""}</td>'
                       f'<td class=r>{m(float(g["area"]))}</td><td class=r>{m(g["price"] / 1e6)}</td></tr>'
                       for g in sorted(prem, key=lambda g: -g['price']))

    def fig(cid, k, cap):
        p = os.path.abspath(f'{D}/photos/{cid}_{k:02d}.jpg')
        return f'<figure><img src="file://{p}"><figcaption>{cap}</figcaption></figure>'
    our_figs = ''.join(f'<figure><img src="file://{os.path.abspath(f"{D}/our/{k:02d}.jpg")}"></figure>' for k in OUR_PHOTOS)
    grid = ''.join(fig(cid, k, f'{S(by[cid]["jk"])}, {m(by[cid]["area"], 0)} м², {m(by[cid]["price"] / 1e6, 0)} млн, '
                               f'ремонт {sf(score(by[cid]))} из 10') for cid, k in GRID if cid in by)

    coef = val['coef']
    in_house = [(by[c], fv.predict(by[c], score(by[c]), fv.main()[2])) for c in (329819607, 332125658) if c in by]
    var_rows = ''.join(f'<tr><td>{escape(v["name"])}</td><td class=r>{v["n"]}</td><td class=r>{m(v["point"], 1)}%</td>'
                       f'<td class=r>{m(v["price"], 0)}</td><td class=r>+{m(v["over"], 0)}%</td></tr>' for v in var)
    top = sorted([r for r in rated if r['raion'] in TIER and r['km_our'] <= 3.5 and not r['apart'] and score(r) >= s_our],
                 key=lambda r: r['ppsqm'])
    top_rows = ''.join(f'<tr><td>{link(r)}</td><td>{S(r["jk"])}</td><td class=r>{m(r["area"])}</td><td class=r>{m(r["price"] / 1e6)}</td>'
                       f'<td class=r>{m(r["ppsqm"] / 1e6, 2)}</td><td class=r>{sf(score(r))}</td></tr>' for r in top)
    value_section = f'''<section><h1 style="margin-top:14px">4. Ремонт и цена: стоит ли квартира 145 млн ₽</h1>
<p>Все {len(rated) + 1} квартир с реальными фото интерьера, включая вашу, вслепую оценили два независимых оценщика: на листе с фото
был только код — без цены, адреса и названия ЖК. Оценки почти совпали (корреляция 0,94, средняя разница 0,3 балла).
Ваша квартира получила {' и '.join(sf(x) for x in bl_our.get('raters', []))}, итог — {sf(s_our)} из 10.
Выше оценены {len(higher)} квартир из {len(rated)}.</p>
<figure><img src="file://{os.path.abspath('reports/kutuzovsky12_value.png')}" style="height:auto"></figure>
<p class=src>Каждая точка — квартира-аналог. Линия — цена по модели для квартиры в Дорогомилове в доме 2018 г. и новее без панорамного вида.</p>
<p>Модель по {val['n']} аналогам: каждый балл ремонта добавляет к цене м² {m(coef['ремонт, балл']['pct'], 1)}%
(± {m(100 * coef['ремонт, балл']['se'], 1)}%); Хамовники дороже Дорогомилова на {m(coef['Хамовники']['pct'], 0)}%, апартаменты дешевле квартир
на {m(-coef['апартаменты']['pct'], 0)}%, башни 30+ этажей — на {m(-coef['башня 30+ этажей']['pct'], 0)}%. Для вашей квартиры модель даёт
{m(val['pred_ppsqm'], 2)} млн ₽/м², то есть {m(val['pred_price'], 0)} млн ₽. Цена объявления на {m(val['resid_pct'], 0)}% выше;
дороже модели выставлены {m(100 * val['share_more_expensive'], 0)}% аналогов.</p>
<table class=t><thead><tr><th>Вариант модели</th><th class=r>Аналогов</th><th class=r>Балл ремонта</th><th class=r>Цена по модели, млн ₽</th>
<th class=r>Ваша цена выше</th></tr></thead><tbody>{var_rows}</tbody></table>
<p class=src>Вывод не зависит от выбора аналогов и признаков: во всех вариантах цена 145 млн ₽ выше модели на {m(olo, 0)}–{m(ohi, 0)}%.</p>
<p>Квартиры в вашем доме тоже выставлены выше модели: 93 м² с ремонтом {sf(score(in_house[0][0]))} — на
{m(100 * (in_house[0][0]['ppsqm'] / 1e6 / in_house[0][1] - 1), 0)}%, 95 м² с ремонтом {sf(score(in_house[1][0]))} — на
{m(100 * (in_house[1][0]['ppsqm'] / 1e6 / in_house[1][1] - 1), 0)}%, и обе не продаются месяцами. Даже если признать за домом надбавку
около 5%, рыночная цена — не больше {m(vhi * 1.05, 0)} млн ₽.</p>
<h2>Квартиры рядом с ремонтом не хуже вашего</h2>
<table class=t><thead><tr><th>ID ЦИАН</th><th>ЖК</th><th class=r>м²</th><th class=r>Млн ₽</th><th class=r>Млн ₽/м²</th><th class=r>Ремонт</th></tr></thead>
<tbody>{top_rows}</tbody></table>
<p class=src>Только квартиры (не апартаменты) в Дорогомилове и Пресне до 3,5 км с ремонтом {sf(s_our)}+ по слепой оценке.</p>
<p><b>Ответ:</b> за 145 млн ₽ покупатель получает ремонт уровня {sf(s_our)} из 10, а рядом есть квартиры с ремонтом не хуже
дешевле за м². Рыночная цена объявления — {m(vlo, 0)}–{m(vhi, 0)} млн ₽ без машиноместа; предложение 140 млн ₽ с машиноместом
соответствует ей.</p>
</section>'''

    body = f'''
<div class=head><div class=t1>Квартира на Кутузовском проспекте, 12: цена и аналоги</div>
<div class=t2>ЖК «Кутузовский XII», 95 м², 6 этаж из 11 · данные ЦИАН на {DATE}</div></div>

<h1>1. Главное</h1>
<div class=box>
<p><b>За 145 млн ₽ квартира дороже аналогов; предложение 140 млн ₽ с машиноместом соответствует рынку.</b>
Ремонт по слепой оценке по фото — {sf(s_our)} из 10: хороший дизайнерский, но не топовый, у {len(higher)} из {len(rated)} оценённых
аналогов он выше. С учётом ремонта, района, типа жилья, вида и возраста дома рынок выставляет такие квартиры за
{m(vlo, 0)}–{m(vhi, 0)} млн ₽ (в базовом варианте модели — {m(val['pred_price'], 0)} млн ₽). Ваши 145 млн ₽ на {m(olo, 0)}–{m(ohi, 0)}% выше.
Если машиноместо стоит {PARKING_EST[0]}–{PARKING_EST[2]} млн ₽, за саму квартиру покупатель даёт {net[PARKING_EST[2]]}–{net[PARKING_EST[0]]} млн ₽ —
ровно рыночный уровень.</p>
<p>Квартира продаётся с
{first['start'] if first else '—'}: цена была 150, 160, 162,5, 152 и 145 млн ₽, покупателя за это время не нашлось.
В этом же доме два похожих лота тоже стоят без движения: 93 м² с более дорогим ремонтом за {m(better['price'] / 1e6, 0)} млн ₽
с мая и 95 м² с более простым ремонтом за {m(simpler['price'] / 1e6, 0)} млн ₽ с июля.</p>
<p>Отдавать машиноместо в цену квартиры на этом рынке не принято: из {len(rows) - 1} аналогов так делают {len(incl)} продавца,
а {n_sep} продают места отдельно — по 6–15 млн ₽. Поэтому место — главный предмет торга. Разумно попросить
143–145 млн ₽ с машиноместом или 140 млн ₽ без него. Согласиться на 140 млн ₽ с местом тоже допустимо:
это рыночная цена, а не уступка ниже рынка.</p></div>

<h2>Что ещё важно</h2>
<ul>
<li>Все 5 фото санузлов в объявлении — 3D-визуализации, остальные фото реальные. У конкурентов с ремонтом нашего уровня
санузлы сняты вживую. Реальная фотосъёмка ванных — самое дешёвое улучшение объявления.</li>
<li>Ремонт свежий, дизайнерский, с мебелью и техникой, но по материалам уступает лучшим конкурентам: подоценки — материалы
{sf(bl_our.get('materials'))}, дизайн {sf(bl_our.get('design'))}, исполнение {sf(bl_our.get('execution'))}, комплектность {sf(bl_our.get('completeness'))}
(керамогранит под мрамор, а не камень). Ремонт выше вашего — в основном в апартаментах башен Сити и в клубных домах центра.
Многие конкуренты в Москва-Сити и у Поклонной ({len(dev)} из
{sum(1 for r in rows if r['raion'] in TIER)} в Дорогомилове и Пресне) продаются с отделкой застройщика без мебели, в медиане
за {m(med(dev), 2)} млн ₽/м².</li>
<li>Минусы для покупателя: вид во двор и на сталинские дома, а не панорама, как у башен Сити; метро в 15 минутах пешком.</li>
<li>Если квартира в собственности больше 5 лет, налога с продажи нет (машиноместо — отдельный объект, правило то же).</li>
</ul>

<section class=pb><h1>2. Квартира, дом и локация</h1>
<table class=t><tbody>
<tr><td>Площадь</td><td>95 м²: кухня-гостиная ~29 м², 2 спальни по 16 м², 2 санузла (мастер-санузел с ванной и душем), гардеробные, постирочная</td></tr>
<tr><td>Этаж, потолки</td><td>6 из 11, 3,1 м</td></tr>
<tr><td>Ремонт</td><td>Дизайнерский, свежий, в стиле мягкой современной классики; продаётся с мебелью и техникой
({sf(s_our)} из 10 по слепой оценке двух оценщиков: {' и '.join(sf(x) for x in bl_our.get('raters', []))})</td></tr>
<tr><td>Дом</td><td>«Кутузовский XII», Capital Group, класс «премиум», сдан в 2020 г.; клубный дом на 11 этажей, гранитный фасад, лобби с консьержем,
подземный паркинг; рейтинг ЖК на ЦИАН {ad.get("newbuildingRating")}</td></tr>
<tr><td>Локация</td><td>Дорогомилово, внутри ТТК: рядом гостиница «Украина», набережная Тараса Шевченко и квартал «Бадаевский»
(парк, набережная, пешеходный мост на Пресню); Киевская и Москва-Сити — 15 минут пешком</td></tr>
<tr><td>Цена в объявлении</td><td>145 млн ₽ ({m(our['ppsqm'] / 1e6, 2)} млн ₽/м²), агентство PANDA ESTATE, просмотров: {S(ad.get("stats", {}).get("totalViewsFormattedString"))}</td></tr>
</tbody></table>
<div class=figs>{our_figs}</div>
<p class=src>Реальные фото из объявления: кухня-гостиная, спальня, прихожая, вид из окна.</p>
<p>Ремонт сделан на хорошем уровне: индивидуальная мебель и стеновые панели, крупноформатный керамогранит под мрамор, продуманный свет.
Стиль — популярная светлая неоклассика, она нравится широкому кругу покупателей, но не уникальна. Квартиры в том же доме с более
выразительным авторским ремонтом просят дороже, но тоже продаются долго.</p>

<h2>История продажи нашей квартиры</h2>
<table class=t><thead><tr><th>Выставлена</th><th>Снята</th><th class=r>Последняя цена, млн ₽</th><th class=r>Млн ₽/м²</th><th class=r>Дней</th></tr></thead>
<tbody>{hist_rows}</tbody></table>
<p class=src>По истории объявлений дома на ЦИАН. Перевыставление не скрывает историю: покупатели и агенты видят все прошлые цены.</p>

<h2>Похожие квартиры в этом же доме (85–110 м²)</h2>
<table class=t><thead><tr><th>ID ЦИАН</th><th>Статус</th><th>Выставлена</th><th>Снята</th><th class=r>м²</th><th class=r>Этаж</th>
<th class=r>Последняя цена, млн ₽</th><th class=r>Млн/м²</th><th class=r>Дней</th></tr></thead><tbody>{nb_rows}</tbody></table>
<p class=src>«Снято» не значит «продано»: объявление могли снять без сделки. Дешёвые лоты 2024–2025 гг. (0,9–1,2 млн ₽/м²) —
разного состояния: например, 95 м² за 88 млн ₽ продавали без отделки, 105 м² за 98 млн ₽ — с простым ремонтом.</p>
</section>

<section class=pb><h1>3. Что можно купить за те же деньги</h1>
<p>Аналоги того же уровня локации (Дорогомилово и Пресня, до 3,5 км от дома), 85–110 м²: квартиры с ремонтом от 7 баллов
и апартаменты от 7,5 балла. Ремонт оценён вслепую по первым 16 фото каждого объявления (раздел 4).
Апартаменты обычно дешевле квартир: в них нельзя прописаться, выше налог и коммунальные платежи.</p>
<table class=t>{head}<tbody>{tier_rows}</tbody></table>
<p class=src>Цены объявлений на {DATE}. Полный список ({len(rows)} квартир), комментарии к ремонтам и ссылки — в таблице Excel.
Как отбирали: объявления ЦИАН в ЦАО и Дорогомилове, 80–115 м², 2–4 комнаты, 90–250 млн ₽, ремонт «дизайнерский» или «евро»
по данным продавца, дома 2005 г. и новее, без строящихся; дубли одной квартиры от разных агентов объединены.</p>
<p>Медиана цен объявлений: квартиры и апартаменты с ремонтом от 7,5 балла — {m(med(t8), 2)} млн ₽/м², только квартиры от 7,5 — {m(med(t8f), 2)},
квартиры от 7 — {m(med(t7f), 2)}. В центре — Хамовники, Якиманка, Замоскворечье, Тверской — квартиры с ремонтом нашего уровня
дороже: медиана {m(med(cen), 2)} млн ₽/м², но это другой уровень локации.</p>
<div class=figs>{grid}</div>
<p class=src>Фото из объявлений, по одному кадру интерьера. Первые два — квартиры в нашем доме.</p>
</section>

{value_section}
<section><h1 style="margin-top:14px">5. Машиноместа</h1>
<p>Из {len(rows) - 1} аналогов машиноместо включено в цену только у {len(incl)}. Ещё у {n_sep} место продаётся отдельно,
у {n_unk} оно есть, но условия не указаны. Цены, которые продавцы пишут в объявлениях: Capital Towers — 10–15 млн ₽ за место,
Sole Hill — 6 млн ₽, Садовые кварталы — +5 млн ₽ за место с кладовой.</p>
<table class=t><thead><tr><th>ID ЦИАН</th><th>ЖК</th><th>Тип</th><th class=r>м²</th><th class=r>Млн ₽</th><th class=r>Ремонт</th><th>Условия</th></tr></thead>
<tbody>{incl_rows}</tbody></table>
<p class=src>Включают место в цену в основном продавцы квартир с более простым ремонтом — как довод к цене.</p>
<h2>Цены машиномест рядом</h2>
<table class=t><thead><tr><th>ID ЦИАН</th><th>ЖК, адрес</th><th class=r>м²</th><th class=r>Млн ₽</th></tr></thead><tbody>{gar_rows}</tbody></table>
<p>В «Кутузовском XII» сейчас нет машиномест в продаже. Соседний лот (93 м²) предлагает место за отдельную плату без цены.
В премиальных домах Дорогомилова места продают за 8–23 млн ₽. В нашем доме, по описаниям объявлений, около 125 мест —
примерно по месту на квартиру, дефицита нет. Ориентир — {PARKING_EST[0]}–{PARKING_EST[2]} млн ₽.
Точную цену стоит проверить у управляющей компании или соседей.</p>

<h2>Предложение 140 млн ₽ с машиноместом</h2>
<table class=t><thead><tr><th>Машиноместо стоит</th><th class=r>За квартиру, млн ₽</th><th class=r>Млн ₽/м²</th><th class=r>Скидка к 145 млн</th></tr></thead><tbody>
{''.join(f'<tr><td>{p} млн ₽</td><td class=r>{net[p]}</td><td class=r>{m(per[p], 2)}</td><td class=r>{m(100 * (1 - net[p] / ask), 0)}%</td></tr>' for p in PARKING_EST)}
</tbody></table>
<p>Для сравнения: по модели квартира с вашим ремонтом и локацией выставляется за {m(vlo, 0)}–{m(vhi, 0)} млн ₽ без машиноместа,
медиана объявлений похожих квартир в Дорогомилове и Пресне — {m(med(t7f) * our['area'], 0)}–{m(med(t8f) * our['area'], 0)} млн ₽.
Это цены объявлений, сделки проходят ниже. Каждый месяц ожидания тоже стоит денег:
при доходности вклада 10% годовых 140 млн ₽ приносят около 1,2 млн ₽ в месяц.</p>

</section>'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'


def write_pdf(html):
    path = 'reports/kutuzovsky12.html'
    open(path, 'w').write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath(path))
        pg.wait_for_timeout(1500)
        pg.pdf(path=PDF, format='A4', print_background=True, margin=dict(top='18mm', bottom='17mm', left='19mm', right='17mm'),
               display_header_footer=True,
               header_template='<div style="font-family:Carlito,sans-serif;font-size:7.5pt;color:#555;width:100%;padding:0 17mm 0 19mm;'
                               'display:flex;justify-content:space-between"><span>Кутузовский пр-т, 12: цена и аналоги</span>'
                               f'<span>{DATE}</span></div>',
               footer_template='<div style="font-family:Carlito,sans-serif;font-size:8pt;color:#555;width:100%;text-align:center">'
                               '<span class="pageNumber"></span></div>')
        b.close()


if __name__ == '__main__':
    os.makedirs('reports', exist_ok=True)
    rows = load()
    hist = house_history()
    write_xlsx(rows, hist)
    write_pdf(build_html(rows, hist))
    print(XLSX, PDF, len(rows), 'аналогов,', sum(1 for r in rows if r['review']), 'с оценкой ремонта')
