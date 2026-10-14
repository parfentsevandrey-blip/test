#!/usr/bin/env python3
"""PDF-отчёт: справедливая цена продажи трёх офисных объектов (данные valuation.json) → reports/офисы_оценка.pdf"""
import html
import json
import os

V = json.load(open('commercial-data/valuation.json'))
ROWS = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
PH = os.path.abspath('commercial-data/photos')
SUBJ = os.path.abspath('commercial-data/subj')
DATE = '14.10.2026'


def n(v, d=0):
    if v is None:
        return '—'
    s = f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')
    return s


def mln(v):
    return n(v / 1e6, 1) + ' млн ₽'


def e(s):
    return html.escape(str(s or ''))


def photo(cid, k=0):
    p = f'{PH}/{cid}_{k}.jpg'
    return f'file://{p}' if os.path.exists(p) else None


VAT_RU = {'included': 'с НДС', 'notIncluded': 'НДС сверху', 'usn': 'УСН (без НДС)', None: '—'}
CLS_RU = {'a': 'A', 'aPlus': 'A+', 'b': 'B', 'bPlus': 'B+', 'bMinus': 'B−', 'c': 'C', None: '—'}
ADJ_RU = {'loc': 'локация', 'bld': 'здание', 'fl': 'этаж/вход', 'size': 'масштаб', 'fit': 'отделка/арендатор'}

TEXT = {
    'yard': dict(
        cian='На ЦИАН объект <b>в продаже не выставлен</b>. Блок 303,8 м² на 1 этаже <b>сдаётся в аренду</b> — сразу двумя объявлениями '
             'разных агентов: <a href="https://www.cian.ru/rent/commercial/324943568/">28 819 ₽/м²/год (НДС сверху)</a> и '
             '<a href="https://www.cian.ru/rent/commercial/332870182/">36 600 ₽/м²/год с НДС</a>. Блок 680 м² на 5 этаже в выдаче не найден; '
             'на 5 этаже сдаются части 246 и 250 м² (24 000 и 32 208 ₽/м²/год). Всего в БЦ «Ярд (The Yard)» сейчас 6 объявлений об аренде '
             'и 1 о продаже в соседнем строении 11с7 (164,8 м², 45 млн ₽).',
        location='Хорошёвский район САО, деловой кластер Магистральных улиц в 500 м от ТТК и в 2 км от Москва-Сити. Район быстро '
                 'перестраивается: рядом новые башни iCity, TOP Tower, Сидней Сити, ASPACE — это поднимает престиж и цены, '
                 'но пока вокруг много промзон и строек. Инфраструктура в 20 минутах пешком: кафе и рестораны, Магистраль Плаза, '
                 'Перекрёсток, ВкусВилл, фитнес, гостиницы. Метро не шаговое: Беговая и Полежаевская — 20 мин пешком; '
                 'Хорошёвская (БКЛ) и МЦК Шелепиха — 4–5 мин транспортом, автобус в 100 м.',
        pros=['500 м до ТТК, 10 мин на машине до Москва-Сити, выезд на Звенигородское шоссе',
              'Узнаваемое здание: клинкерный кирпич, «британский» стиль, OTIS, центральное кондиционирование, СКУД 24/7, охраняемая парковка',
              '1 этаж — готовое банковское/депозитарное помещение (кассы, депозитарий, переговорные) рядом с холлом БЦ',
              '5 этаж — цельный блок 680 м² с гибкой планировкой (open space + кабинеты), два лестничных узла, свет по фасаду',
              'Подтверждённый арендный спрос: в БЦ сдаются 6 блоков по 24–36,6 тыс. ₽/м²/год',
              'Район дорожает: новые деловые башни рядом тянут вверх ставки и интерес инвесторов'],
        cons=['Метро далеко — 20 мин пешком; в рекламе это главный аргумент против',
              'Прямой конкурент в 300 м — Магистраль Плаза (B+, 15 мин до метро, развитая инфраструктура, аренда 45–55 тыс. ₽/м²)',
              'Банковская отделка 1 этажа для обычного офиса потребует переделки — круг покупателей уже',
              'Один блок 303,8 м² выставлен в аренду двумя агентами по ставкам 28,8 и 36,6 тыс. — это сбивает рынок; перед продажей стоит оставить одно объявление',
              'Лот 680 м² — «дорогой билет» около 126 млн ₽: покупателей меньше, экспозиция дольше',
              'Здание 1958 г. постройки (реконструкция 2009) — по возрасту конструкций ниже новых БЦ'],
        rent_note='Рыночная ставка без НДС: <b>1 этаж — 28 000 ₽/м²/год</b> (запросы в самом БЦ 25,8–30 тыс. без НДС, скидка на торг), '
                  '<b>5 этаж — 25 000 ₽/м²/год</b> (запросы 24–26,4 тыс.). Магистраль Плаза дороже (40–55 тыс.) за счёт метро и инфраструктуры; '
                  'старые соседние корпуса 11с1/11с5 — 16–20 тыс. без НДС. Средняя ставка класса B по Москве — 26,3 тыс. ₽/м²/год.'),
    'kaleidoscope': dict(
        cian='На ЦИАН объект <b>не найден ни в продаже, ни в аренде</b> (проверены название БЦ, адрес Хлебозаводский проезд, 7 и все объявления '
             'в радиусе 350 м). Ближайшие активные предложения — соседний бизнес-парк «Сириус Парк» (Каширское ш., 3к2): '
             'офисы и ПСН в продаже и десятки блоков в аренде.',
        location='Нагатино-Садовники (ЮАО), деловая зона между Варшавским и Каширским шоссе. Две станции метро в 10–15 минутах пешком: '
                 'Каширская (зелёная линия + БКЛ, крупный пересадочный узел) и Нагатинская (серая линия, МЦД-2). Варшавское и Каширское '
                 'шоссе — минута на машине, ТТК — 3,8 км. В БЦ кафе, банкоматы, охраняемая парковка; рядом фитнес, продукты, ресторан. '
                 'Престиж района средний: офисно-производственная застройка, спальные кварталы.',
        pros=['Две станции метро (в т.ч. пересадочная Каширская с БКЛ) в 10–15 мин пешком, быстрый выезд на Варшавское и Каширское шоссе',
              'Крупный БЦ (44,5 тыс. м²): охрана 24/7, ресепшен, кафе, наземная парковка, лифты OTIS, центральное кондиционирование',
              '1 этаж с отдельным входом с улицы — подходит под клиентский офис, медцентр, шоурум, ПСН; это повышает ликвидность',
              'Два этажа можно продавать раздельно (395 и 401 м²) — шире круг покупателей'],
        cons=['Здание 1995 г., класс B: проигрывает соседнему «Сириус Парку» (B+, потолки 3,6 м, ремонт)',
              'Низкие потолки: 2,57 м на 1 этаже и 2,37 м на 2 этаже (антресоль) — серьёзный минус для офиса',
              '2 этаж — коридорно-кабинетная планировка (8 кабинетов по 30–42 м² и зал 113 м²), без open space',
              'Яркий оранжевый фасад 1990-х воспринимается устаревшим',
              'Объект не экспонировался на ЦИАН — нет истории спроса; рядом активно продаются и сдаются более современные площади'],
        rent_note='Рыночная ставка без НДС: <b>1 этаж с отдельным входом — 27 000 ₽/м²/год</b>, <b>2 этаж — 21 000 ₽/м²/год</b>. '
                  'Сириус Парк сдаёт 1 этажи по 32–44 тыс. и верхние по ~30 тыс. без НДС, но это B+ с высокими потолками; '
                  'Варшавская Плаза (B) — 21–25 тыс.; с учётом низких потолков и возраста здания ставка «Калейдоскопа» ниже.'),
    'imagine': dict(
        cian='Наши 8 блоков на ЦИАН <b>не выставлены</b>. В этом же БЦ (Нагатинская, 16с9) продаётся другой блок 187 м² на 2 этаже — '
             '<a href="https://www.cian.ru/sale/commercial/334389605/">37,4 млн ₽ (200 000 ₽/м², с НДС)</a>, с арендатором и ремонтом «для себя». '
             'Это лучший ориентир цены. Рядом, в ТЦ «Конфетти» (Нагатинская, 16), сдаётся ПСН 146 м² по 35 951 ₽/м²/год.',
        location='Нагатино-Садовники (ЮАО), Нагатинская улица — оживлённая улица с трамваем у входа. Рядом ТЦ «Конфетти» (Перекрёсток, кафе, '
                 'Сбербанк) и спальные кварталы — хороший поток для сервисных арендаторов. Метро: Нагатинская — 12–15 мин пешком, '
                 'Коломенская — около 1,1 км, Нагорная — 800 м по данным продавца соседнего блока. Здание — реконструированный '
                 'промышленный корпус 1974 г.; во дворе дискаунтеры и ателье — престиж локации невысокий.',
        pros=['Все 8 блоков с готовым ремонтом: сплит-системы, приточно-вытяжная вентиляция, спринклеры, СКУД, охрана',
              'Линейка площадей 168–524 м² — удобно малому бизнесу и инвесторам «под аренду»; на 3 этаже балконы',
              'Блоки 1 этажа с отдельными входами и витринами — можно сдавать под сервис/ритейл дороже офисов',
              'Трамвай у входа, ТЦ «Конфетти» рядом, парковка на 50 мест',
              'Есть свежий ориентир цены в самом здании — блок 187 м² за 37,4 млн ₽'],
        cons=['Метро не шаговое: 12–15 минут пешком',
              'Бывшее промышленное здание и непредставительное окружение (дискаунтеры, ателье) — фактически класс B',
              'Противоречие в презентации: общая площадь БЦ 4 000 м² на одной странице и 12 000 м² на другой — стоит сверить документы',
              'Выход на рынок сразу 8 лотов (2 133 м²) давит на цену: продавать лучше поэтапно, оптом — только с дисконтом 10–15%',
              'Арендные ставки в локации невысокие: 22–30 тыс. ₽/м²/год без НДС для офисов'],
        rent_note='Рыночная ставка без НДС: <b>1 этаж — 27 000 ₽/м²/год</b> (ПСН рядом в «Конфетти» — 36 тыс., ПСН в жилых домах — 22 тыс.), '
                  '<b>2–3 этажи — 22 000 ₽/м²/год</b> (Варшавская Плаза 21–25 тыс., Сириус Парк ~30 тыс. без НДС, '
                  'старый БЦ на Нагатинской, 3А — 15 тыс.).'),
}


def comp_card(c):
    img = photo(c['id'])
    return f'''<div class="card">{f'<img src="{img}">' if img else '<div class="noimg">нет фото</div>'}
<div class="cb"><b>{e(c.get('bc') or (c['address'] or '').replace('Россия, ', '').replace('Москва, ', ''))}</b>
<div class="m">{e((c['address'] or '').replace('Россия, ', ''))}</div>
<div class="m">{n(c['area'], 1)} м² · {c['floor']} эт. · класс {CLS_RU.get(c.get('cls'), c.get('cls'))} · м. {e(c.get('metro'))} {c.get('metro_min') or ''}{' мин пешком' if c.get('metro_walk') else ' мин'}</div>
<div class="p">{mln(c['price']) if 'price' in c else ''} · <b>{n(c['ask'])} ₽/м²</b> <span class="m">({VAT_RU.get(c['vat'])})</span></div>
<div class="m">{e(c.get('note'))}</div>
<a href="{c['url']}">cian.ru/{c['id']}</a></div></div>'''


def grid(g):
    rows = ''
    for c in g['comps']:
        adj = ', '.join(f"{ADJ_RU[k]} {'+' if v > 0 else ''}{v}%" for k, v in c['adj'].items())
        rows += (f"<tr><td>{e((c['bc'] or '') + ' ' + (c['address'] or '').replace('Россия, ', '').replace('Москва, ', ''))[:60]}</td>"
                 f"<td class=r>{n(c['area'], 0)}</td><td class=r>{c['floor']}</td><td class=r>{n(c['ask'])}</td><td>{VAT_RU.get(c['vat'])}</td>"
                 f"<td class=r>{n(c['ex_vat'])}</td><td class=r>{n(c['base'])}</td><td>{adj or '—'}</td><td class=r><b>{n(c['adjusted'])}</b></td><td class=r>{c['weight']}</td></tr>")
    return f'''<table class="t"><tr><th>Аналог</th><th>м²</th><th>эт.</th><th>Запрос, ₽/м²</th><th>НДС</th><th>Без НДС</th>
<th>После торга −8%</th><th>Корректировки</th><th>Скорр., ₽/м²</th><th>Вес</th></tr>{rows}
<tr class="tot"><td colspan="8">Средневзвешенная удельная цена (сравнительный подход)</td><td class=r><b>{n(g['value'])}</b></td><td></td></tr></table>'''


def rent_table(S):
    rows = ''
    for r in S['rent']:
        img = photo(r['id'])
        rows += (f"<tr><td>{f'<img class=th src={img}>' if img else ''}</td><td>{e(r['bc'] or '')}<div class=m>{e((r['address'] or '').replace('Россия, ', '').replace('Москва, ', ''))}</div></td>"
                 f"<td class=r>{n(r['area'], 0)}</td><td class=r>{r['floor']}</td><td>{CLS_RU.get(r['cls'], r['cls'])}</td>"
                 f"<td class=r>{n(r['month'])}</td><td class=r>{n(r['ask'])}</td><td>{VAT_RU.get(r['vat'])}</td><td class=r><b>{n(r['ex_vat'])}</b></td>"
                 f"<td><a href=\"{r['url']}\">{r['id']}</a></td></tr>")
    return f'''<table class="t"><tr><th></th><th>БЦ / адрес</th><th>м²</th><th>эт.</th><th>кл.</th><th>₽/мес</th><th>₽/м²/год</th><th>НДС</th><th>Без НДС</th><th>ЦИАН</th></tr>{rows}</table>'''


def income_table(S):
    seen, rows = set(), ''
    for u in S['units_val']:
        if u['group'] in seen:
            continue
        seen.add(u['group'])
        p = u['inc']
        label = {'y1': '1 этаж', 'y5': '5 этаж', 'k1': '1 этаж', 'k2': '2 этаж', 'ground': '1 этаж', 'up': '2–3 этажи'}[u['group']]
        rows += (f"<tr><td>{label}</td><td class=r>{n(p['rent'])}</td><td class=r>{int(p['vac'] * 100)}%</td><td class=r>{int(p['opex'] * 100)}%</td>"
                 f"<td class=r>{n(u['noi'])}</td><td class=r>{n(p['cap'] * 100, 1)}%</td><td class=r><b>{n(u['income'])}</b></td></tr>")
    return f'''<table class="t"><tr><th>Блоки</th><th>Ставка без НДС, ₽/м²/год</th><th>Недозагрузка</th><th>Расходы собственника</th>
<th>Чистый доход, ₽/м²/год</th><th>Ставка капитализации</th><th>Стоимость, ₽/м²</th></tr>{rows}</table>'''


def result_table(S, total_row=True):
    rows = ''
    for u in S['units_val']:
        rows += (f"<tr><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r>{n(u['comp'])}</td><td class=r>{n(u['income'])}</td>"
                 f"<td class=r><b>{n(u['per_m2'])}</b></td><td class=r><b>{mln(u['total'])}</b></td><td class=r>{n(u['lo'] / 1e6, 1)}–{n(u['hi'] / 1e6, 1)}</td>"
                 f"<td class=r>{mln(u['total'] / 0.92)}</td><td class=r>{n(u['gross_yield'], 1)}%</td></tr>")
    ta = sum(u['area'] for u in S['units_val'])
    tt = sum(u['total'] for u in S['units_val'])
    if total_row:
        rows += (f"<tr class=tot><td>Итого</td><td class=r>{n(ta, 1)}</td><td></td><td></td><td class=r>{n(tt / ta)}</td><td class=r>{mln(tt)}</td>"
                 f"<td class=r>{n(tt * .92 / 1e6, 1)}–{n(tt * 1.08 / 1e6, 1)}</td><td class=r>{mln(tt / 0.92)}</td><td></td></tr>")
    return f'''<table class="t res"><tr><th>Помещение</th><th>м²</th><th>Сравнит., ₽/м²</th><th>Доходный, ₽/м²</th><th>Итог, ₽/м²</th>
<th>Справедливая цена</th><th>Диапазон, млн ₽</th><th>Цена в объявлении*</th><th>Доходность аренды</th></tr>{rows}</table>'''


SUBJ_IMG = {'yard': ['yard-2.jpg', 'yard-7.jpg', 'yard-9.jpg'], 'kaleidoscope': ['kal-photo.jpg', 'kal-2.jpg', 'kal-3.jpg'],
            'imagine': ['img-1.jpg', 'img-4.jpg', 'img-7.jpg']}


def subject_section(sk, S):
    T = TEXT[sk]
    imgs = ''.join(f'<img src="file://{SUBJ}/{f}">' for f in SUBJ_IMG[sk])
    units = ''.join(f"<li>{e(u['name'])} — {n(u['area'], 1)} м²{(' · ' + e(u.get('note'))) if u.get('note') else ''}</li>" for u in S['units'])
    sale_html = ''
    labels = {'y1': '1 этаж (303,8 м²)', 'y5': '5 этаж (680 м²)', 'k1': '1 этаж (394,9 м²)', 'k2': '2 этаж (401,3 м²)',
              'ground': '1 этаж (блоки 1–3)', 'up': '2–3 этажи (блоки 4–8)'}
    shown = set()
    cards = ''
    for g in S['sale'].values():
        for c in g['comps']:
            if c['id'] not in shown:
                shown.add(c['id'])
                cards += comp_card(c)
    for gk, g in S['sale'].items():
        sale_html += f'<div class="keep"><h4>Сетка корректировок: {labels[gk]}</h4>' + grid(g) + '</div>'
    return f'''
<section class="obj">
<h2>{e(S['title'])}</h2><div class="sub">{e(S['address'])} · {e(S['district'])}</div>
<div class="gal">{imgs}</div>
<div class="two"><div><h3>Объект</h3><ul>{units}</ul><p><b>Здание:</b> {e(S['cls'])}.</p><p><b>Метро:</b> {e(S['metro'])}.</p></div>
<div><h3>Статус на ЦИАН</h3><p>{T['cian']}</p></div></div>
<h3>Расположение, инфраструктура, престиж</h3><p>{T['location']}</p>
<div class="two"><div class="pros"><h3>Преимущества</h3><ul>{''.join(f'<li>{e(x)}</li>' for x in T['pros'])}</ul></div>
<div class="cons"><h3>Недостатки</h3><ul>{''.join(f'<li>{e(x)}</li>' for x in T['cons'])}</ul></div></div>
<h3 class="pb">Конкуренты в продаже</h3><div class="cards">{cards}</div>
{sale_html}
<h3 class="pb">Арендные ставки в локации</h3>{rent_table(S)}<p class="note">{T['rent_note']}</p>
<h3>Доходный подход</h3>{income_table(S)}
<p class="note">Чистый доход = ставка × (1 − недозагрузка) × (1 − расходы собственника: налог на имущество, управление, ремонт, страховка). Стоимость = чистый доход ÷ ставка капитализации.</p>
<h3>Справедливая цена продажи</h3>{result_table(S)}
<p class="note">Итог = 50% сравнительного + 50% доходного подхода{'; блоки 3 этажа +3% за балконы, лоты больше 400 м² −5% за масштаб' if sk == 'imagine' else ''}.
* Цена в объявлении = справедливая цена ÷ 0,92 — запас на торг. {'Продажа всех 8 блоков одним лотом — с дисконтом 10–15%: <b>' + n(sum(u['total'] for u in S['units_val']) * .85 / 1e6, 0) + '–' + n(sum(u['total'] for u in S['units_val']) * .90 / 1e6, 0) + ' млн ₽</b>.' if sk == 'imagine' else ''}</p>
</section>'''


def summary():
    rows = ''
    for sk, S in V['subjects'].items():
        for u in S['units_val']:
            rows += (f"<tr><td>{e(S['title'])}</td><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r>{n(u['per_m2'])}</td>"
                     f"<td class=r><b>{mln(u['total'])}</b></td><td class=r>{n(u['lo'] / 1e6, 1)}–{n(u['hi'] / 1e6, 1)}</td><td class=r>{mln(u['total'] / 0.92)}</td></tr>")
        ta = sum(u['area'] for u in S['units_val']); tt = sum(u['total'] for u in S['units_val'])
        rows += f"<tr class=tot><td colspan=2>{e(S['title'])} — итого</td><td class=r>{n(ta, 1)}</td><td class=r>{n(tt / ta)}</td><td class=r>{mln(tt)}</td><td></td><td class=r>{mln(tt / 0.92)}</td></tr>"
    return f'''<table class="t res"><tr><th>Объект</th><th>Помещение</th><th>м²</th><th>₽/м²</th><th>Справедливая цена</th><th>Диапазон, млн ₽</th><th>Цена в объявлении</th></tr>{rows}</table>'''


CSS = '''
@page { size: A4; margin: 14mm 12mm; }
body { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 9.2pt; color: #1b2430; line-height: 1.38; }
h1 { font-size: 22pt; margin: 0 0 4px; color: #16324f; } h2 { font-size: 16pt; color: #16324f; margin: 0; padding-top: 2px; border-top: 3px solid #16324f; }
h3 { font-size: 11pt; color: #16324f; margin: 12px 0 5px; } h4 { font-size: 9.5pt; margin: 10px 0 4px; color: #3a4a5c; }
.sub { color: #5b6b7c; margin-bottom: 8px; } .m { color: #5b6b7c; font-size: 8pt; } .note { color: #4a5868; font-size: 8.2pt; }
a { color: #1f5fa8; text-decoration: none; }
.obj { page-break-before: always; } .pb { page-break-before: always; }
.gal { display: grid; grid-template-columns: 1.3fr 1fr 1fr; gap: 6px; margin: 6px 0 4px; } .gal img { width: 100%; height: 150px; object-fit: cover; border-radius: 3px; border: 1px solid #d7dee6; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; } ul { margin: 2px 0; padding-left: 16px; } li { margin: 1px 0; }
.keep { break-inside: avoid; } h3, h4 { break-after: avoid; }
.pros h3 { color: #1f7a4d; } .cons h3 { color: #b2332b; }
.cards { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 7px; }
.card { border: 1px solid #d7dee6; border-radius: 4px; overflow: hidden; break-inside: avoid; font-size: 8pt; }
.card img { width: 100%; height: 110px; object-fit: cover; display: block; } .noimg { height: 110px; background: #eef1f4; color: #8a97a5; display: grid; place-items: center; }
.cb { padding: 5px 7px; } .cb .p { margin: 2px 0; }
table.t { width: 100%; border-collapse: collapse; font-size: 7.8pt; margin: 4px 0; break-inside: auto; }
.t th { background: #16324f; color: #fff; font-weight: 600; padding: 3px 4px; text-align: left; } .t td { border-bottom: 1px solid #e1e6ec; padding: 3px 4px; vertical-align: top; }
.t td.r, .t th.r { text-align: right; white-space: nowrap; } .t tr.tot td { background: #eaf0f6; font-weight: 600; } .res { font-size: 8.4pt; }
img.th { width: 54px; height: 38px; object-fit: cover; border-radius: 2px; }
.cover { }
.box { background: #f2f5f8; border-left: 4px solid #16324f; padding: 8px 12px; margin: 8px 0; }
.kpi { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 10px 0; } .kpi div { border: 1px solid #d7dee6; border-radius: 5px; padding: 8px 10px; }
.kpi b { font-size: 15pt; color: #16324f; display: block; }
'''


def build():
    S = V['subjects']
    tot = {k: sum(u['total'] for u in s['units_val']) for k, s in S.items()}
    n_offers = len(ROWS)
    body = f'''
<div class="cover"><div class="m">Аналитическая записка · {DATE}</div>
<h1>Справедливая цена продажи трёх офисных объектов в Москве</h1>
<div class="sub">БЦ «ЯРД» (САО) · БЦ «Калейдоскоп» (ЮАО) · БЦ Imagine Plaza (ЮАО)</div>
<div class="kpi"><div><b>{mln(tot['yard'])}</b>БЦ «ЯРД»: 303,8 + 680 м²</div><div><b>{mln(tot['kaleidoscope'])}</b>БЦ «Калейдоскоп»: 796,2 м²</div>
<div><b>{mln(tot['imagine'])}</b>Imagine Plaza: 8 блоков, 2 133 м²</div></div>
<h3>Сводка</h3>{summary()}
<p class="note">Справедливая цена — наиболее вероятная цена сделки при разумной экспозиции (3–9 месяцев), без НДС (для продавца на УСН или физлица — цена как есть; продавец-плательщик НДС добавляет 22%). «Цена в объявлении» оставляет запас ~8% на торг.</p>
<h3>Главные выводы</h3><ul>
<li><b>«ЯРД»</b> — самая сильная локация (ТТК, 2 км до Сити, район дорожает), но далеко от метро; цена 1 этажа ≈ 207 тыс. ₽/м², 5 этажа ≈ 186 тыс. ₽/м². Блок 1 этажа сейчас сдаётся двумя агентами по разным ставкам — до продажи стоит навести порядок в рекламе.</li>
<li><b>«Калейдоскоп»</b> — удобное метро (Каширская + Нагатинская), отдельный вход на 1 этаже, но здание 1995 г. и низкие потолки (2,37–2,57 м); 1 этаж ≈ 210 тыс. ₽/м², 2 этаж ≈ 148 тыс. ₽/м². Выгоднее продавать этажи раздельно.</li>
<li><b>Imagine Plaza</b> — готовый ремонт и линейка небольших лотов, но непрестижное окружение и метро в 12–15 мин; 1 этаж ≈ 191–201 тыс., 2–3 этажи ≈ 142–154 тыс. ₽/м². В здании уже продаётся блок 187 м² за 200 тыс. ₽/м² с НДС — наши цены без НДС согласуются с ним.</li>
<li>Арендная доходность при этих ценах — 13–15% годовых, на уровне ключевой ставки ЦБ (14%) и рыночных ставок капитализации офисов (11–12% по NF Group): дороже инвестор покупать не будет, дешевле — продавец недополучит.</li></ul>
</div>
<section class="obj"><h2>Рынок и методика</h2>
<div class="box"><b>Рыночный фон (октябрь 2026).</b> Ключевая ставка ЦБ — 14% (решение 11.09.2026). Ставки капитализации офисов в Москве — 11–12% (NF Group, итоги 2025). Средняя ставка аренды офисов класса B в Москве — 26 320 ₽/м²/год, вакантность класса B — 4,5% (Аналитический центр Москвы, 01.04.2026). НДС с 2026 г. — 22%.</div>
<h3>Данные</h3><p>Выгрузка ЦИАН на {DATE}: {n(n_offers)} объявлений о продаже и аренде офисов и помещений свободного назначения площадью 100–2 500 м² в 13 районах вокруг объектов
(САО/ЦАО/СЗАО — Хорошёвский, Беговой, Пресненский, Хорошёво-Мнёвники, Савёловский, Аэропорт; ЮАО — Нагатино-Садовники, Нагорный, Нагатинский затон, Москворечье-Сабурово, Даниловский, Донской, Котловка).
Для каждого объекта рассчитано расстояние до всех предложений; в аналоги отобраны предложения в радиусе 2 км в зданиях классов B/B+ и старше 2020 г.
Башни Москва-Сити, новые БЦ класса A/A+ и предложения на стадии строительства исключены — это другой рынок.</p>
<h3>Сравнительный подход</h3><ol><li>Цена предложения приводится к уровню без НДС (÷1,22, если НДС включён).</li><li>Скидка на торг — 8% (типичный разрыв между предложением и сделкой для офисов).</li>
<li>Корректировки на отличия от объекта: локация и метро, класс/возраст/инженерия/потолки здания, этаж и вход, масштаб лота, отделка и наличие арендатора.</li>
<li>Средневзвешенное значение; больший вес — ближайшим и наиболее похожим аналогам (тот же БЦ, соседние здания).</li></ol>
<h3>Доходный подход</h3><p>Рыночная ставка аренды без НДС определена по аналогам (в т.ч. по объявлениям в самих БЦ) с учётом торга; недозагрузка 8–10%, расходы собственника 12%,
ставка капитализации 11,5% для ликвидных лотов 1 этажа и 12% для верхних этажей и крупных лотов (выше рыночных 11–12% для институциональных объектов — поправка на класс B и дробную собственность).</p>
<h3>Согласование</h3><p>Итог — среднее двух подходов с равными весами; диапазон ±8% отражает разброс цен аналогов. Ограничения: используются цены предложений, а не сделок; осмотр объектов не проводился,
площади и характеристики — из презентаций собственника; юридический статус, кадастровая стоимость и обременения не проверялись.</p></section>
{''.join(subject_section(k, s) for k, s in S.items())}
<section class="obj"><h2>Источники</h2><ul>
<li>Объявления ЦИАН (cian.ru) — ссылки у каждого аналога; выгрузка {DATE}.</li>
<li><a href="https://ac.mos.ru/dashboards/key-indicators/business-service/">Аналитический центр Москвы — основные показатели рынка офисной недвижимости</a></li>
<li><a href="https://wtcmoscow.ru/company/news/7257/">Инвестиции в коммерческую недвижимость Москвы: итоги 2025 и прогноз на 2026 (ставки капитализации NF Group)</a></li>
<li><a href="https://buh.ru/news/tsb-rf-sokhranil-klyuchevuyu-stavku-na-urovne-14-godovykh.html">ЦБ РФ сохранил ключевую ставку на уровне 14% годовых</a></li>
<li>Презентации собственника: БЦ «ЯРД», БЦ «Калейдоскоп», БЦ Imagine Plaza.</li></ul></section>'''
    return f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'


if __name__ == '__main__':
    os.makedirs('reports', exist_ok=True)
    open('reports/offices_valuation.html', 'w').write(build())
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto('file://' + os.path.abspath('reports/offices_valuation.html'))
        pg.wait_for_timeout(1500)
        pg.pdf(path='reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf', format='A4', print_background=True,
               margin=dict(top='14mm', bottom='14mm', left='12mm', right='12mm'),
               display_header_footer=True, header_template='<div></div>',
               footer_template='<div style="font-size:7pt;color:#8a97a5;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>')
        b.close()
    print('ok')
