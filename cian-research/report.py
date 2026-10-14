#!/usr/bin/env python3
"""PDF-отчёт: справедливая цена продажи трёх офисных объектов по кластерам конкурентов (valuation.json, bc_profiles.json)
→ reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf"""
import hashlib
import html
import io
import json
import os
import urllib.request

from PIL import Image

V = json.load(open('commercial-data/valuation.json'))
ROWS = {r['id']: r for r in json.load(open('commercial-data/offers.json'))}
PROF = json.load(open('commercial-data/bc_profiles.json'))
RAT = json.load(open('commercial-data/bc_ratings.json'))
PAGES = json.load(open('commercial-data/bc_pages.json'))
PH = os.path.abspath('commercial-data/photos')
BPH = os.path.abspath('commercial-data/bcphotos')
SUBJ = os.path.abspath('commercial-data/subj')
DATE = '14.10.2026'
REP = {'yard': ['y1', 'y5'], 'kaleidoscope': ['k1', 'k2'], 'imagine': ['i2', 'i4']}
CL_SHORT = {'K1': 'БЦ класса B/B+', 'K2': 'БЦ класса A', 'K3': 'админздания, особняки, класс C', 'K4': '1 этажи жилых домов (ПСН)'}
CL_ROLE = {'K1': 'основной ряд — так ищет покупатель офиса в БЦ класса B', 'K2': 'верхняя граница: «что купить, если доплатить за класс»',
           'K3': 'альтернатива подешевле: без инфраструктуры БЦ', 'K4': 'альтернатива для 1 этажа с отдельным входом'}
ADJ_RU = {'loc': 'центр', 'metro': 'метро', 'bld': 'здание', 'fl': 'этаж/вход', 'size': 'масштаб', 'fit': 'отделка/арендатор'}
VAT_RU = {'included': 'с НДС', 'notIncluded': 'НДС сверху', 'usn': 'УСН', None: '—'}
FIT_RU = {'shell': 'без отделки', 'white': 'white box', 'premium': 'дизайнерский ремонт', 'standard': 'с ремонтом'}
HDR = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36',
       'Referer': 'https://www.cian.ru/'}


def n(v, d=0):
    if v is None:
        return '—'
    return f'{v:,.{d}f}'.replace(',', ' ').replace('.', ',')


def mln(v):
    return n(v / 1e6, 1) + ' млн ₽'


def k(v):
    return n(v / 1000) + ' тыс.' if v else '—'


def e(s):
    return html.escape(str(s or ''))


def addr(s):
    return (s or '').replace('Россия, ', '').replace('Москва, ', '').replace('г Москва, ', '').replace('Москва город, ', '')


def fetch_img(u, path, size=520):
    if os.path.exists(path):
        return True
    try:
        data = urllib.request.urlopen(urllib.request.Request(u, headers=HDR), timeout=30).read()
        im = Image.open(io.BytesIO(data)).convert('RGB')
        im.thumbnail((size, size))
        im.save(path, 'JPEG', quality=80)
        return True
    except Exception:
        return False


def photo(cid):
    p = f'{PH}/{cid}_0.jpg'
    if not os.path.exists(p):
        for u in ROWS[cid]['photos'][:2]:
            if fetch_img(u, p):
                break
    return f'file://{p}' if os.path.exists(p) else None


def bc_photo(b):
    """Фото здания: со страницы БЦ на ЦИАН, иначе первое фото из объявления."""
    key = hashlib.md5((b.get('url') or b.get('name') or '').encode()).hexdigest()[:12]
    p = f'{BPH}/bc_{key}.jpg'
    if not os.path.exists(p):
        for u in (b.get('photos') or [])[:2]:
            if fetch_img(u, p, 360):
                break
    if not os.path.exists(p):
        for i in b['sale'] + b['rent']:
            if ROWS[i]['photos'] and fetch_img(ROWS[i]['photos'][0], p, 360):
                break
    return f'file://{p}' if os.path.exists(p) else None


def U(sk, key):
    return next(u for u in V['subjects'][sk]['units_val'] if u['key'] == key)


def TOT(sk):
    return sum(u['total'] for u in V['subjects'][sk]['units_val'])


def AREA(sk):
    return sum(u['area'] for u in V['subjects'][sk]['units_val'])


# ---------- тексты по объектам ----------
TEXT = {
    'yard': dict(
        cian='На ЦИАН объект <b>в продаже не выставлен</b>. Блок 303,8 м² на 1 этаже <b>сдаётся в аренду</b> двумя объявлениями разных агентов: '
             '<a href="https://www.cian.ru/rent/commercial/324943568/">28 819 ₽/м²/год, НДС и эксплуатация сверху</a> и '
             '<a href="https://www.cian.ru/rent/commercial/332870182/">36 600 ₽/м²/год «всё включено»</a> (≈25 тыс. чистыми). '
             'Блок 680 м² на 5 этаже в выдаче не найден; на 5 этаже сдаются части 246 и 250 м² (≈21–24 тыс. чистыми). '
             'В соседнем строении комплекса (11с7) продаётся офис 164,8 м² на 1 этаже за 45 млн ₽ (УСН, с арендатором).',
        location='Хорошёвский район САО: деловой кластер Магистральных улиц у ТТК, 2 км до Москва-Сити, 5,9 км до Кремля. '
                 'Ближайшая станция — <b>МЦД-4 «Ермакова Роща», 0,5 км (~9 мин пешком)</b>, но электрички ходят реже метро. '
                 'Метро: Шелепиха (МЦК/БКЛ) — 1 км (~17 мин), Звенигородская — 1,1 км (~18 мин), Хорошёвская (БКЛ) — 1,2 км. '
                 'На странице БЦ ЦИАН указывает только Беговую (24 мин пешком) и Хорошёвскую — станция МЦД там не учтена. '
                 'Район перестраивается: рядом iCITY, TOP Tower, ASPACE, Light City. Это поднимает престиж, но и выводит на рынок сотни офисов '
                 'нового класса A (в локации ~280 лотов, большинство строится). Инфраструктура: в БЦ кафе, отделение банка, банкомат, ресепшен; '
                 'в 300 м Магистраль Плаза с сервисами.',
        pros=['500 м до ТТК и 2 км до Москва-Сити; станция МЦД-4 «Ермакова Роща» в 0,5 км',
              'Ухоженное кирпичное здание в «британском» стиле, ресепшен, кафе, банк в здании, СКУД, охраняемая парковка на 28 мест',
              'Подтверждённый спрос: в БЦ 6 объявлений об аренде (5 этаж — 21–24 тыс. ₽/м²/год чистыми, 1 этаж — 25–29 тыс.)',
              '5 этаж — цельный верхний этаж 680 м², open space + кабинеты, свет по фасаду',
              'Офисов класса B на продажу рядом мало (11 лотов в 6 БЦ), а класс A стоит вдвое дороже — у покупателя «класса B» мало альтернатив'],
        cons=['Инженерия по карточке БЦ на ЦИАН: кондиционирование местное (сплит-системы), вентиляция только приточная, ИБП нет, '
              'пожарная сигнализация без спринклеров. В презентации заявлено центральное кондиционирование — это нужно проверить до показов',
              'Метро не шаговое: до Шелепихи ~17 мин; у МЦД редкие интервалы',
              'Сосед в 300 м — Магистраль Плаза (B+, атриум, крупнее): продажа 242–280 тыс. ₽/м² без НДС, аренда 36–47 тыс. чистыми',
              '1 этаж оборудован под банк: для обычного офиса нужна переделка (−5% к цене)',
              'Один блок 1 этажа рекламируется двумя агентами по разным ставкам — сбивает рынок',
              'Лот 680 м² стоит около 140 млн ₽ — узкий круг покупателей, долгая экспозиция']),
    'kaleidoscope': dict(
        cian='На ЦИАН объект <b>не найден ни в продаже, ни в аренде</b>: проверены название БЦ, адрес Хлебозаводский проезд, 7 и все объявления '
             'в радиусе 350 м. Страницы БЦ в справочнике ЦИАН тоже нет. Рядом активно продаются и сдаются офисы бизнес-парка «Сириус Парк» (Каширское ш., 3).',
        location='Нагатино-Садовники (ЮАО), между Варшавским и Каширским шоссе, 9,3 км до Кремля. Пешком до метро далеко: '
                 '<b>Нагорная — 1,4 км (~23 мин)</b>, Нагатинская — 1,6 км (~27 мин), Нахимовский проспект — 1,8 км. '
                 'В презентации — «10–15 минут». Шоссе — в минуте на машине. '
                 'Главный конкурент — «Сириус Парк» в 300–500 м: 5 корпусов класса B/B+ с потолками 3,0–3,2 м, десятки лотов. '
                 'Окружение — офисно-производственные корпуса и спальные кварталы, престиж района средний.',
        pros=['1 этаж с отдельным входом с улицы — подходит под клиентский офис, медцентр, шоурум',
              'Крупный БЦ (44,5 тыс. м²): охрана 24/7, ресепшен, кафе, парковка, лифты OTIS, центральное кондиционирование (по презентации)',
              'Два этажа можно продать раздельно (395 и 401 м²) — шире круг покупателей',
              'Для покупателя «для себя» это один из самых дешёвых входов в БЦ в локации: Сириус Парк стоит 250–313 тыс. ₽/м² без НДС'],
        cons=['Потолки 2,57 м на 1 этаже и 2,37 м на антресоли — ниже нормы офиса (2,7–3 м). Учтено отдельными скидками −3% и −7%',
              'Метро 23–27 мин пешком',
              'Здание 1995 г. с фасадом 1990-х: оценка по фото 2,75 из 5 (Сириус Парк — 3,5–4)',
              '2 этаж — коридорно-кабинетная планировка без open space',
              'Объект не экспонировался — нет истории спроса; в Сириус Парке 30+ блоков в аренде, конкуренция за арендатора высокая',
              'Аренда в локации низкая: офисы класса B — 18–20 тыс. ₽/м²/год чистыми, поэтому инвестор готов платить немного']),
    'imagine': dict(
        cian='Наши 8 блоков на ЦИАН <b>не выставлены</b>. В этом же здании продаётся блок 187 м² на 2 этаже: '
             '<a href="https://www.cian.ru/sale/commercial/334389605/">37,4 млн ₽ (200 000 ₽/м² с НДС)</a> — с арендатором и ремонтом «для себя»; '
             'объявление дублируется вторым агентом. Это прямой ориентир цены. Страница БЦ на ЦИАН: класс B+, 4 992 м², 4 этажа, 4 лифта, '
             'спринклеры, приточная вентиляция, местное кондиционирование.',
        location='Нагатино-Садовники (ЮАО), Нагатинская улица, 8,2 км до Кремля. Метро: <b>Нагатинская — 1,3 км (~21 мин)</b>, '
                 'Коломенская — 1,5 км (~25 мин), МЦК Верхние Котлы — 1,8 км. В объявлениях пишут «15 минут», ЦИАН на странице БЦ — '
                 'Коломенская 23 мин. Трамвай у входа, рядом ТЦ «Конфетти» и жилые кварталы. Здание — реконструированный промышленный корпус 1974 г. '
                 'с входом со двора. Конкуренты: Сириус Парк (B/B+), Варшавская Плаза у метро, Newton Plaza (класс A, 13 мин от Нагатинской). '
                 'Newton Plaza сдаёт офисы за ~22 тыс. чистыми, и это ограничивает арендные ставки класса B вокруг.',
        pros=['Во всех 8 блоках готовый ремонт; спринклеры, СКУД, 4 лифта, охрана',
              'Линейка 168–524 м² — удобна малому бизнесу и частным инвесторам',
              'Блоки 1 этажа с отдельными входами и витринами',
              'Высокие потолки (~4 м) — редкость для офисов класса B',
              'Есть ориентир цены в самом здании (блок 187 м² за 37,4 млн ₽)',
              'Трамвай у входа, ТЦ рядом, большая парковка'],
        cons=['Метро ~21 мин пешком',
              'Бывший завод, вход со двора, непредставительное окружение — оценка здания по фото 3 из 5',
              'Площадь БЦ: ЦИАН — 4 992 м², в презентации 4 000 и 12 000 м² на разных страницах — сверить документы',
              'Восемь лотов сразу давят на цену: продавать поэтапно; оптом — с дисконтом 10–15%',
              'Newton Plaza (класс A) рядом сдаёт по ~22 тыс. чистыми — «потолок» для арендных ставок класса B',
              'В этом же здании блок 2 этажа выставлен по 164 тыс. ₽/м² без НДС — выше этой цены (после торга) верхние этажи не продать']),
}
SUBJ_IMG = {'yard': ['yard-2.jpg', 'yard-7.jpg', 'yard-9.jpg'], 'kaleidoscope': ['kal-photo.jpg', 'kal-2.jpg', 'kal-3.jpg'],
            'imagine': ['img-1.jpg', 'img-4.jpg', 'img-7.jpg']}


# ---------- блоки отчёта ----------
def subject_card(sk, S):
    near = ' · '.join(f"{x['name']} ({x['line'].replace('Рублёво-Архангельская', 'МЦК/БКЛ')}) — {n(x['km'], 1)} км, ~{x['walk']} мин"
                      for x in S['near'])
    units = ''.join(f"<li>{e(u['name'])} — {n(u['area'], 1)} м²{(' · ' + e(u.get('note'))) if u.get('note') else ''}</li>" for u in S['units'])
    rt = S['rating']
    return f'''<div class="two"><div><h3>Объект</h3><ul>{units}</ul><p><b>Здание:</b> {e(S['cls'])}.{(' ' + e(S['tech']) + '.') if S.get('tech') else ''}</p>
<p><b>Метро (расчёт по координатам):</b> {e(near)}. До Кремля {n(S['center_km'], 1)} км. Оценка здания по фото: <b>{n(rt, 2).rstrip('0').rstrip(',')} из 5</b>.</p></div>
<div><h3>Статус на ЦИАН</h3><p>{TEXT[sk]['cian']}</p></div></div>'''


def med(ids, f):
    """Медиана по рыночным лотам: без строящихся, торгов, ошибок ввода и исключённых выбросов."""
    ex = {int(x) for x in V['exclude']}
    v = sorted(ROWS[i][f] for i in ids if i not in ex and not ROWS[i].get('offplan') and not ROWS[i].get('nonmarket')
               and (4000 <= (ROWS[i].get(f) or 0) <= 150000 if f == 'net' else 50000 <= (ROWS[i].get(f) or 0) <= 1500000))
    return v[len(v) // 2] if v else None


def dist(sk, b):
    import bc_profiles as bp
    return bp.km(bp.SUBJ[sk], (b['lat'], b['lng']))


def bc_map(sk):
    bcs = [b for b in PROF[sk]['bcs'] if (b['sale'] or len(b['rent']) >= 2) and b['cluster'] in ('K1', 'K2', 'K3')]
    off = [b for b in bcs if b['offplan']]
    live = [b for b in bcs if not b['offplan']]
    sel = []
    for cl, lim in (('K1', 18), ('K2', 7), ('K3', 5)):
        part = sorted([b for b in live if b['cluster'] == cl], key=lambda b: dist(sk, b))
        sel += part[:lim]
    rows = ''
    for b in sel:
        img = bc_photo(b)
        rt = (RAT.get(b['url'] or '') or {})
        rnote = rt.get('note') or ('класс A — оценка по умолчанию 4,5' if b['cluster'] == 'K2' else 'нет оценки (по умолчанию 3)')
        tech = b.get('tech') or {}
        tech_s = ', '.join(x for x in [('конд. ' + tech['Кондиционирование']) if tech.get('Кондиционирование') else '',
                                        ('пожар. ' + tech['Пожаротушение']) if tech.get('Пожаротушение') else ''] if x)
        own = b['url'] and b['url'] == V['subjects'][sk].get('url')
        rows += (f"<tr{' class=same' if own else ''}><td>{f'<img class=th2 src={img}>' if img else ''}</td><td><b>{e(b['title'] or b['name'])}</b>{' — <b>наш объект</b>' if own else ''}<div class=m>{e(addr(b['address']))[:48]}</div>"
                 f"<div class=m>{e(rnote)[:110]}</div></td><td>{b['cluster']}<br>{e(b['cls'] or '—')}</td><td class=r>{b['year'] or '—'}</td>"
                 f"<td class=r><b>{n(rt.get('rating'), 2).rstrip('0').rstrip(',') if rt.get('rating') else '—'}</b></td>"
                 f"<td class=r>{n(dist(sk, b), 1)}</td><td>{e(b['metro'])}<br><span class=m>{b['walk']} мин</span></td><td class=r>{n(b['center_km'], 1)}</td>"
                 f"<td class=r>{len(b['sale'])} / {len(b['rent'])}</td><td class=r>{k(med(b['sale'], 'ex_vat'))}</td><td class=r>{k(med(b['rent'], 'net'))}</td>"
                 f"<td class=m>{e(tech_s)[:60]}</td></tr>")
    subj = PROF[sk]['subject']
    return f'''<table class="t"><tr><th></th><th>Бизнес-центр</th><th>Кластер / класс</th><th>Год</th><th>Оценка по фото</th><th>От объекта, км</th><th>Метро, пешком</th>
<th>До центра, км</th><th>Лотов: продажа / аренда</th><th>Продажа, медиана без НДС ₽/м²</th><th>Аренда, медиана чистая ₽/м²/год</th><th>Инженерия (ЦИАН)</th></tr>{rows}</table>
<p class="note">Объект: метро {e(subj['near'][0]['name'])} ~{subj['near'][0]['walk']} мин, {n(subj['center_km'], 1)} км до центра. Время пешком рассчитано одинаково для всех зданий:
расстояние по прямой × 1,35 (извилистость маршрута) при 80 м/мин — так же, как на страницах ЦИАН. В таблице — {len(sel)} ближайших к объекту БЦ из {len(live)} с лотами в локации (K1 — до 18, K2 — до 7, K3 — до 5);
ещё {len(off)} строящихся БЦ (класс A, сдача 2027–2032) в аналоги не берутся: это покупка «на котловане» с ожиданием и риском.</p>'''


def cluster_table(sk):
    S = V['subjects'][sk]
    reps = REP[sk]
    rows = ''
    for cl in ('K1', 'K2', 'K3', 'K4'):
        c = S['clusters'][cl]
        s, s1, su, r = c['sale'], c['sale_1'], c['sale_up'], c['rent']

        def st(x):
            return f"{k(x['med'])}<div class=m>n={x['n']}, {k(x['q1'])}–{k(x['q3'])}</div>" if x else '—'
        adj = ''
        for uk in reps:
            res = S['comps'][uk]
            sv = res.get(f'sale_{cl}', {}).get('value')
            rv = res.get(f'rent_{cl}', {}).get('value')
            adj += f"<td class=r>{k(sv)}<div class=m>аренда {k(rv)}</div></td>"
        off = f"<div class=m>строящиеся: {c['offplan']['n']} лотов, медиана {k(c['offplan']['med'])}</div>" if c.get('offplan') else ''
        rows += (f"<tr{' class=main' if cl == 'K1' else ''}><td><b>{cl}</b> · {CL_SHORT[cl]}<div class=m>{CL_ROLE[cl]}</div></td>"
                 f"<td class=r>{st(s)}<div class=m>{c['n_bc_sale']} зданий</div>{off}</td><td class=r>{st(s1)}</td><td class=r>{st(su)}</td>"
                 f"<td class=r>{st(r)}<div class=m>{c['n_bc_rent']} зданий</div></td>{adj}</tr>")
    hdr = ''.join(f"<th>Приведено к: {e(U(sk, uk)['name'])}, ₽/м²</th>" for uk in reps)
    return f'''<table class="t"><tr><th>Кластер</th><th>Продажа: медиана без НДС, ₽/м²</th><th>1 этаж</th><th>2+ этажи</th><th>Аренда: чистая ставка, ₽/м²/год</th>{hdr}</tr>{rows}</table>
<p class="note">Левая часть — «сырые» цены предложений в кластере (без НДС, до торга; аренда — без НДС, эксплуатации и коммуналки). Правая — те же кластеры,
приведённые к нашему помещению: торг −8%, поправки на удалённость от центра, метро, здание, этаж/вход, масштаб, отделку; один вес на здание.
Для K2–K4 это ориентиры («сколько стоил бы наш офис, если оценивать его по этому сегменту»), в итог входит только K1.</p>'''


def cv(sk, uk, key):
    return (V['subjects'][sk]['comps'][uk].get(key) or {}).get('value')


def cluster_comment(sk):
    """Что покупатель видит в каждом кластере — вывод по разграниченной аналитике."""
    S = V['subjects'][sk]
    a, b = REP[sk]
    c = S['clusters']
    if sk == 'yard':
        items = [
            f"<b>K1 — рынок узкий.</b> Офисов в БЦ класса B на продажу у Шелепихи, Хорошёвской и Беговой почти нет: {c['K1']['sale']['n']} лотов в {c['K1']['n_bc_sale']} зданиях, "
            f"из них подходят по площади 7–8 (Магистраль Плаза, Шмитовский пр. 3с3, 4-я Магистральная 11, Бега, соседнее строение комплекса 11с7). Зато аренда глубокая — "
            f"{c['K1']['rent']['n']} предложений в {c['K1']['n_bc_rent']} зданиях. Поэтому доходный подход весит 60%.",
            f"<b>K2 — класс A вдвое дороже.</b> Аэродом, Монарх, iCITY: {c['K2']['sale']['n']} существующих лотов, медиана {k(c['K2']['sale']['med'])} ₽/м²; ещё "
            f"{(c['K2'].get('offplan') or {}).get('n', 0)} лотов в строящихся башнях (TOP Tower, ASPACE, STONE). Даже после всех поправок класс A даёт {k(cv(sk, b, 'sale_K2'))} ₽/м² — "
            f"это потолок, к которому ЯРД не приблизится: покупатель, готовый платить 400+ тыс., выберет новую башню.",
            f"<b>K3 — не «дешевле», а другое.</b> Ближайший аналог — административное здание на Хорошёвском ш., 38к1 (B, 1971 г.): 155–192 тыс. ₽/м² — ниже ЯРД, как и должно быть "
            f"для здания без инфраструктуры БЦ. Остальные лоты K3 в кольце станций — старый фонд у Улицы 1905 года и Красной Пресни (особняк со SPA за 784 тыс., помещения "
            f"с уличным входом за 517 тыс.). Они поднимают среднее K3 до {k(cv(sk, b, 'sale_K3'))}, но для ЯРД это не альтернатива.",
            "<b>K4 к ЯРД не применяется:</b> 1 этаж ЯРД — с входом через холл БЦ, а не с улицы; стрит-ритейл на Ленинградском пр. и Красной Пресне — другой рынок.",
            f"<b>Вывод покупателя:</b> за офис класса B в этой локации рынок просит {k(S['comps'][b]['sale_K1']['market'])} ₽/м² после поправок (Магистраль Плаза — сильнее ЯРД, "
            f"4-я Магистральная — слабее), а аренда окупает около {k(U(sk, b)['income'])}. Справедливая цена — между ними."]
    elif sk == 'kaleidoscope':
        items = [
            f"<b>K1 — глубокий рынок.</b> {c['K1']['sale']['n']} лотов в {c['K1']['n_bc_sale']} зданиях и {c['K1']['rent']['n']} предложений аренды. Ближайший конкурент — Сириус Парк "
            f"в 300–500 м (стр. 1, 4, 9, 12; оценка зданий 3,5–4 против 2,75 у «Калейдоскопа»). Лофты у Тульской (Холодильный пер., Даниловская мануфактура) стоят 410–455 тыс. — "
            f"после поправок на центр (−18%) и метро (−15%) они ещё дороже нашего объекта и получают малый вес.",
            f"<b>K2 — класс A рядом есть, и он недорогой.</b> DM Tower (Верхние Котлы), «Башня Рябов» и строящийся ЭОН. Существующий класс A после поправок — "
            f"{k(cv(sk, a, 'sale_K2'))} ₽/м² для 1 этажа и {k(cv(sk, b, 'sale_K2'))} для 2 этажа. Это всего на {round((cv(sk, a, 'sale_K2') / S['comps'][a]['sale_K1']['value'] - 1) * 100)}–{round((cv(sk, b, 'sale_K2') / S['comps'][b]['sale_K1']['value'] - 1) * 100)}% "
            f"выше K1: покупатель с бюджетом 200+ тыс. ₽/м² задумается о классе A, поэтому цена «Калейдоскопа» должна быть заметно ниже.",
            f"<b>K3 — нижняя граница.</b> Административные здания и старые корпуса (пр. Андропова, Каширский пр., Электролитный пр.): {k(cv(sk, a, 'sale_K3'))} / "
            f"{k(cv(sk, b, 'sale_K3'))} ₽/м². «Калейдоскоп» с потолками 2,37–2,57 м по сути на границе K1 и K3.",
            f"<b>K4 — альтернатива для 1 этажа.</b> ПСН с отдельным входом в жилых домах вокруг: от 139 тыс. (Варшавское ш., 69) до 510 тыс. (Нахимовский и Севастопольский проспекты), после поправок "
            f"{k(cv(sk, a, 'sale_K4'))} ₽/м². Для 1 этажа «Калейдоскопа» — вход с тихого проезда, а не с шоссе — это верхний ориентир.",
            f"<b>Вывод покупателя:</b> за {k(U(sk, a)['per_m2'])} ₽/м² он получает отдельный вход в крупном БЦ, но с низким потолком. Рынок K1 вокруг после всех поправок — "
            f"{k(S['comps'][a]['sale_K1']['market'])}, а аренда окупает только {k(U(sk, a)['income'])}: дороже середины он платить не станет, ведь в 300–500 м "
            f"Сириус Парк с потолками 3 м."]
    else:
        items = [
            f"<b>K1 — глубокий рынок и прямой ориентир в самом здании.</b> {c['K1']['sale']['n']} лотов в {c['K1']['n_bc_sale']} зданиях; в Imagine Plaza продаётся блок 187 м² на 2 этаже "
            f"за 164 тыс. ₽/м² без НДС (≈151 тыс. после торга). Рынок вокруг после поправок даёт {k(S['comps'][b]['sale_K1']['market'])} для 2 этажа, но покупатель видит "
            f"ценник в том же здании — ему отдано 40% веса.",
            f"<b>K2 — класс A у Технопарка и Верхних Котлов</b> (Technopark Plaza, DM Tower, новые здания на ул. Мустая Карима и 2-м Нагатинском пр.): после поправок {k(cv(sk, b, 'sale_K2'))} ₽/м² — верхняя граница. "
            f"Рядом Newton Plaza (класс A, 13 мин от Нагатинской) сдаёт по ~22 тыс. чистыми, это ограничивает и аренду Imagine.",
            f"<b>K3 — нижняя граница:</b> административные здания на пр. Андропова, Электролитном пр., Нагатинской, 31 — {k(cv(sk, b, 'sale_K3'))} ₽/м² для верхних этажей.",
            f"<b>K4 — альтернатива для 1 этажа, но не прямой аналог.</b> Среднее {k(cv(sk, a, 'sale_K4'))} ₽/м² задают стрит-ритейл в новых ЖК у ЗИЛа (наб. Марка Шагала, "
            f"380–550 тыс.) и ПСН на первой линии Нагатинской (300 тыс.). У Imagine вход со двора бывшего завода, поэтому K4 — ориентир сверху, а не цена.",
            f"<b>Вывод покупателя:</b> верхние этажи — не дороже ценника в самом здании ({k(U(sk, b)['per_m2'])} ₽/м²); 1 этаж с отдельным входом — на {round((U(sk, a)['per_m2'] / U(sk, b)['per_m2'] - 1) * 100)}% дороже "
            f"({k(U(sk, a)['per_m2'])}). Восемь блоков продавать по очереди, начиная с 1 этажа."]
    return '<div class="box"><b>Что видит покупатель в каждом кластере</b><ul>' + ''.join(f'<li>{x}</li>' for x in items) + '</ul></div>'


def comp_card(c, extra=''):
    img = photo(c['id'])
    adj = ', '.join(f"{ADJ_RU[a]} {'+' if v > 0 else ''}{n(v, 1).replace(',0', '')}%" for a, v in c['adj'].items())
    title = c.get('bc') or addr(c['address'])[:40]
    return f'''<div class="card">{f'<img src="{img}">' if img else '<div class="noimg">нет фото</div>'}
<div class="cb"><b>{e(title)}</b> <span class="tag">{c['cluster']}{(' · ' + e(c['cls'])) if c.get('cls') else ''}</span>
<div class="m">{e(addr(c['address']))[:60]}</div>
<div class="m">{n(c['area'], 1)} м² · {c['floor']} эт. · {e(FIT_RU[c['fit']])}{' · с арендатором' if c['tenant'] else ''} · {e(c['station'])} {c['walk']} мин · {n(c['center_km'], 1)} км до центра · здание {n(c['rating'], 2).rstrip('0').rstrip(',')}</div>
<div class="p">{mln(c['price'])} · {n(c['per_m2'])} ₽/м² ({VAT_RU.get(c['vat'])}) → <b>{n(c['adjusted'])}</b></div>
<div class="m">{e(adj) or 'без поправок'}{extra}</div>
<a href="{c['url'].split('?')[0]}">cian.ru/{c['id']}</a></div></div>'''


def pick_cards(comps, lim, per_bc=1):
    out, seen = [], {}
    for c in sorted([c for c in comps if c.get('used')], key=lambda c: -c['weight']):
        g = c['bc_url'] or addr(c['address'])[:30]
        if seen.get(g, 0) >= per_bc:
            continue
        seen[g] = seen.get(g, 0) + 1
        out.append(c)
        if len(out) >= lim:
            break
    return out


def grid(res, unit, deal='sale'):
    key = f'{deal}_K1'
    g = res[key]
    rows = ''
    shown, per = [], {}
    for c in sorted([c for c in g['comps'] if c.get('used')], key=lambda c: (-c['same'], -c['weight'])):
        key = c['bc_url'] or addr(c['address'])[:30]
        if not c['same'] and per.get(key, 0) >= 2:      # не больше двух лотов одного БЦ — видно разброс по рынку
            continue
        per[key] = per.get(key, 0) + 1
        shown.append(c)
    for c in shown[:18]:
        adj = ', '.join(f"{ADJ_RU[a]} {'+' if v > 0 else ''}{n(v, 1).replace(',0', '')}%" for a, v in c['adj'].items())
        base = (f"<td class=r>{n(c['per_m2'])}</td><td>{VAT_RU.get(c['vat'])}</td><td class=r>{n(c['ex_vat'])}</td><td class=r>{n(c['base'])}</td>" if deal == 'sale' else
                f"<td class=r>{n(c['per_m2'])}</td><td>{VAT_RU.get(c['vat'])}</td><td>{'вкл.' if c['opex_in'] else ('нет' if c['opex_in'] is False else 'вкл.?')}/{'вкл.' if c['util_in'] else 'нет'}</td><td class=r>{n(c['net'])}</td>")
        rows += (f"<tr{' class=same' if c['same'] else ''}><td>{e((c['bc'] or '') + (' · ' if c['bc'] else '') + addr(c['address']))[:58]}"
                 f"<div class=m>{e(c['station'])} {c['walk']} мин · {n(c['center_km'], 1)} км · здание {n(c['rating'], 2).rstrip('0').rstrip(',')} ({e(c['rating_src'])}){' · <b>то же здание</b>' if c['same'] else ''}</div></td>"
                 f"<td>{e(c['cls'] or '—')}</td><td class=r>{n(c['area'], 0)}</td><td class=r>{c['floor']}</td>{base}"
                 f"<td>{e(adj) or '—'}</td><td class=r><b>{n(c['adjusted'])}</b></td><td class=r>{n(c['weight'], 1)}</td><td><a href=\"{c['url'].split('?')[0]}\">{c['id']}</a></td></tr>")
    h = ('<th>Запрос, ₽/м²</th><th>НДС</th><th>Без НДС</th><th>После торга −8%</th>' if deal == 'sale' else
         '<th>Запрос, ₽/м²/год</th><th>НДС</th><th>Эксплуат./коммун.</th><th>Чистая ставка</th>')
    own = f" · лоты в самом здании (доля 40%): <b>{n(g['own'])}</b>" if g.get('own') else ''
    return f'''<table class="t"><tr><th>Аналог (K1)</th><th>Кл.</th><th>м²</th><th>Эт.</th>{h}<th>Поправки</th><th>Итог</th><th>Вес</th><th>ЦИАН</th></tr>{rows}
<tr class="tot"><td colspan="12">Показаны до двух лотов каждого БЦ с наибольшим весом. Рынок K1: {g['n']} лотов в {g['n_bc']} зданиях → средневзвешенно <b>{n(g['market'])}</b>{own} · медиана {n(g['median'])}, квартили {n(g['q1'])}–{n(g['q3'])}
{'· исключено выбросов: ' + str(g['n_out']) if g['n_out'] else ''} → <b>{n(g['value'])} ₽/м²{'/год' if deal == 'rent' else ''}</b></td></tr></table>'''


def ref_block(sk, uk):
    res = V['subjects'][sk]['comps'][uk]
    out = ''
    for cl in ('K2', 'K3', 'K4'):
        r = res.get(f'sale_{cl}')
        if not r or not r['n']:
            continue
        cards = ''.join(comp_card(c) for c in pick_cards(r['comps'], 3))
        out += (f"<h4>{cl} · {CL_SHORT[cl]} — приведённая цена {k(r['value'])} ₽/м² ({r['n']} лотов, {r['n_bc']} зданий; медиана {k(r['median'])})</h4>"
                f"<div class='cards'>{cards}</div>")
    return out


def income_table(S):
    rows = ''
    for u in S['units_val']:
        rows += (f"<tr><td>{e(u['name'])}</td><td class=r>{n(u['rent'])}</td><td class=r>{int(u['vac'] * 100)}%</td><td class=r>{n(V['params']['owner'])}</td>"
                 f"<td class=r>{n(u['noi'])}</td><td class=r>{n(S['cap'] * 100, 1)}%</td><td class=r><b>{n(u['income'])}</b></td></tr>")
    return f'''<table class="t"><tr><th>Помещение</th><th>Рыночная чистая ставка K1, ₽/м²/год</th><th>Недозагрузка</th><th>Расходы собственника</th>
<th>Чистый доход, ₽/м²/год</th><th>Ставка капитализации</th><th>Стоимость, ₽/м²</th></tr>{rows}</table>'''


def result_table(S):
    rows = ''
    for u in S['units_val']:
        ex = f" {'+' if u.get('extra', 0) > 0 else ''}{u['extra']}%" if u.get('extra') else ''
        rows += (f"<tr><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r>{n(u['comp'])}</td><td class=r>{n(u['income'])}</td>"
                 f"<td class=r>{int(u['wc'] * 100)} / {int(100 - u['wc'] * 100)}{ex}{'<div class=m>потолок ' + k(u['cap_own']) + '</div>' if u.get('capped') else ''}</td>"
                 f"<td class=r><b>{n(u['per_m2'])}</b></td><td class=r><b>{mln(u['total'])}</b></td><td class=r>{n(u['lo'] / 1e6, 1)}–{n(u['hi'] / 1e6, 1)}</td>"
                 f"<td class=r>{mln(u['list_price'])}</td><td class=r>{n(u['gross_yield'], 1)}%</td></tr>")
    ta, tt = sum(u['area'] for u in S['units_val']), sum(u['total'] for u in S['units_val'])
    rows += (f"<tr class=tot><td>Итого</td><td class=r>{n(ta, 1)}</td><td></td><td></td><td></td><td class=r>{n(tt / ta)}</td><td class=r>{mln(tt)}</td>"
             f"<td class=r>{n(tt * .92 / 1e6, 1)}–{n(tt * 1.08 / 1e6, 1)}</td><td class=r>{mln(tt / 0.92)}</td><td></td></tr>")
    return f'''<table class="t res"><tr><th>Помещение</th><th>м²</th><th>Сравнит. K1, ₽/м²</th><th>Доходный, ₽/м²</th><th>Веса, % (+ поправка)</th><th>Итог, ₽/м²</th>
<th>Справедливая цена</th><th>Диапазон, млн ₽</th><th>Цена в объявлении*</th><th>Чистая ставка ÷ цена</th></tr>{rows}</table>'''


def reasoning(sk):
    S = V['subjects'][sk]
    P = V['params']
    out = ''
    for u in S['units_val']:
        res = S['comps'][u['key']]
        s1, r1 = res['sale_K1'], res['rent_K1']
        refs = []
        for cl in ('K2', 'K3', 'K4'):
            if res.get(f'sale_{cl}', {}).get('value'):
                refs.append(f"{CL_SHORT[cl]} — {k(res[f'sale_{cl}']['value'])}")
        own = (f" В самом здании лоты дают {k(s1['own'])} ₽/м² после поправок, у остального рынка — {k(s1['market'])}; здание весит 40%." if s1.get('own') else '')
        own_r = (f" В самом БЦ ставки после поправок — {k(r1['own'])}, у рынка — {k(r1['market'])}." if r1.get('own') else '')
        gross = u['rent'] * u['area']
        cap_line = (f"<li><b>Потолок цены.</b> Покупатель видит в этом же здании блок 187 м² за 164 тыс. ₽/м² без НДС; после торга и поправок к нашему блоку это "
                    f"{k(u['cap_own'] / 1.03)} ₽/м². Больше чем на 3% дороже он не заплатит, поэтому смесь подходов ({k(round(u['wc'] * u['comp'] + (1 - u['wc']) * u['income']))}) "
                    f"ограничена уровнем {k(u['cap_own'])}.</li>" if u.get('capped') else '')
        out += f'''<p><b>{e(u['name'])}, {n(u['area'], 1)} м² → {mln(u['total'])} ({k(u['per_m2'])} ₽/м²).</b></p><ul>
<li><b>Покупатель «для себя»</b> сравнивает с офисами в БЦ класса B вокруг: {s1['n']} лотов в {s1['n_bc']} зданиях. Каждый лот пересчитан в наш:
торг, удалённость от центра и метро, качество здания, этаж, площадь, отделка. Получается <b>{k(u['comp'])} ₽/м²</b>.{own}</li>
<li><b>Инвестор</b> считает аренду: рыночная чистая ставка по {r1['n']} предложениям в {r1['n_bc']} зданиях — {k(u['rent'])} ₽/м²/год, это {n(gross / 1e6, 1)} млн ₽ в год.{own_r}
После простоя {int(u['vac'] * 100)}% и расходов собственника ({n(P['owner'])} ₽/м²) чистый доход — {n(u['noi'] * u['area'] / 1e6, 1)} млн ₽. При доходности {n(S['cap'] * 100, 1)}% инвестор заплатит
<b>{k(u['income'])} ₽/м²</b> ({mln(u['income'] * u['area'])}).</li>
<li><b>Проверка другими классами</b> (приведено к нашему помещению): {'; '.join(refs) or '—'}.</li>
{cap_line}<li><b>Итог</b> — {int(u['wc'] * 100)}% «для себя» + {int(100 - u['wc'] * 100)}% «инвестор»{' с потолком по соседнему блоку' if u.get('capped') else ''}{(', ' + e(u['note'])) if u.get('extra') else ''}: {k(u['per_m2'])} ₽/м².</li></ul>'''
    return out


def subject_section(sk):
    S = V['subjects'][sk]
    T = TEXT[sk]
    imgs = ''.join(f'<img src="file://{SUBJ}/{f}">' for f in SUBJ_IMG[sk])
    reps = REP[sk]
    k1cards = ''
    for uk in reps[:1]:
        k1cards = ''.join(comp_card(c) for c in pick_cards(S['comps'][uk]['sale_K1']['comps'], 9))
    grids = ''.join(f"<div class='keep'><h4>Сетка поправок K1 — {e(U(sk, uk)['name'])}, {n(U(sk, uk)['area'], 1)} м²</h4>{grid(S['comps'][uk], U(sk, uk))}</div>" for uk in reps)
    rents = ''.join(f"<div class='keep'><h4>Аренда K1 — {e(U(sk, uk)['name'])}</h4>{grid(S['comps'][uk], U(sk, uk), 'rent')}</div>" for uk in reps)
    refs = ''.join(f"<h3>Аналоги других классов — {e(U(sk, uk)['name'])}</h3>{ref_block(sk, uk)}" for uk in reps)
    bulk = (f" Продажа всех 8 блоков одним лотом — с дисконтом 10–15%: <b>{n(TOT(sk) * .85 / 1e6, 0)}–{n(TOT(sk) * .90 / 1e6, 0)} млн ₽</b>." if sk == 'imagine' else '')
    return f'''
<section class="obj">
<h2>{e(S['title'])} — {mln(TOT(sk))}</h2><div class="sub">{e(S['address'])} · {e(S['district'])}</div>
<div class="gal">{imgs}</div>
{subject_card(sk, S)}
<h3>Расположение, инфраструктура, престиж</h3><p>{T['location']}</p>
<div class="two"><div class="pros"><h3>Преимущества</h3><ul>{''.join(f'<li>{e(x)}</li>' for x in T['pros'])}</ul></div>
<div class="cons"><h3>Недостатки</h3><ul>{''.join(f'<li>{e(x)}</li>' for x in T['cons'])}</ul></div></div>
<h3 class="pb">1. Конкурентная карта: бизнес-центры у тех же и соседних станций</h3>{bc_map(sk)}
<h3 class="pb">2. Аналитика по кластерам</h3>{cluster_table(sk)}{cluster_comment(sk)}
<h3>3. Основной ряд K1: офисы в БЦ класса B — что реально купит покупатель</h3><div class="cards">{k1cards}</div>
{grids}
{refs}
<h3 class="pb">4. Аренда и доходный подход (K1)</h3>{rents}
{income_table(S)}
<p class="note">Чистый доход = чистая ставка × (1 − недозагрузка) − невозмещаемые расходы собственника (налог на имущество, страховка, управление, резерв).
Эксплуатацию и коммуналку возмещает арендатор. Стоимость = чистый доход ÷ ставка капитализации.</p>
<h3>5. Справедливая цена продажи</h3>{result_table(S)}
<p class="note">Веса: сравнительный / доходный; при узком рынке K1 (меньше 5 зданий с продажами) сравнительному даётся 40%. «Потолок» — цена такого же блока в этом же здании после торга и поправок +3%. * Цена в объявлении = справедливая цена ÷ 0,92 — запас на торг.{bulk}</p>
<h3>6. Обоснование простыми словами</h3>{reasoning(sk)}
</section>'''


def summary():
    rows = ''
    for sk, S in V['subjects'].items():
        for u in S['units_val']:
            rows += (f"<tr><td>{e(S['title'])}</td><td>{e(u['name'])}</td><td class=r>{n(u['area'], 1)}</td><td class=r>{n(u['per_m2'])}</td>"
                     f"<td class=r><b>{mln(u['total'])}</b></td><td class=r>{n(u['lo'] / 1e6, 1)}–{n(u['hi'] / 1e6, 1)}</td><td class=r>{mln(u['list_price'])}</td></tr>")
        rows += (f"<tr class=tot><td colspan=2>{e(S['title'])} — итого</td><td class=r>{n(AREA(sk), 1)}</td><td class=r>{n(TOT(sk) / AREA(sk))}</td>"
                 f"<td class=r>{mln(TOT(sk))}</td><td class=r>{n(TOT(sk) * .92 / 1e6, 1)}–{n(TOT(sk) * 1.08 / 1e6, 1)}</td><td class=r>{mln(TOT(sk) / 0.92)}</td></tr>")
    return f'''<table class="t res"><tr><th>Объект</th><th>Помещение</th><th>м²</th><th>₽/м²</th><th>Справедливая цена</th><th>Диапазон, млн ₽</th><th>Цена в объявлении</th></tr>{rows}</table>'''


def conclusions():
    y1, y5, k1, k2 = U('yard', 'y1'), U('yard', 'y5'), U('kaleidoscope', 'k1'), U('kaleidoscope', 'k2')
    im = V['subjects']['imagine']['units_val']
    g = [u['per_m2'] for u in im if u['floor'] == 1]
    up = [u['per_m2'] for u in im if u['floor'] > 1]
    return (f"<li><b>«ЯРД» — {mln(TOT('yard'))}</b>: 1 этаж {k(y1['per_m2'])} ₽/м², 5 этаж {k(y5['per_m2'])} ₽/м². Самая сильная локация (ТТК, Сити, МЦД-4 в 0,5 км), "
            f"а продаж офисов класса B рядом мало. Слабые места — местное кондиционирование и соседство с более сильной Магистраль Плазой.</li>"
            f"<li><b>«Калейдоскоп» — {mln(TOT('kaleidoscope'))}</b>: 1 этаж {k(k1['per_m2'])}, антресоль {k(k2['per_m2'])} ₽/м². Отдельный вход — плюс; "
            f"низкие потолки, метро 23+ мин и сильный соседний Сириус Парк держат цену в нижней части рынка класса B.</li>"
            f"<li><b>Imagine Plaza — {mln(TOT('imagine'))}</b> по блокам (одним лотом {n(TOT('imagine') * .85 / 1e6, 0)}–{n(TOT('imagine') * .90 / 1e6, 0)} млн ₽): "
            f"1 этаж {k(min(g))}–{k(max(g))}, 2–3 этажи {k(min(up))}–{k(max(up))} ₽/м². Потолок цены верхних этажей задаёт блок в этом же здании (164 тыс. без НДС до торга).</li>"
            "<li>Цена каждого помещения — середина между ценой покупателя «для себя» (офисы в БЦ класса B вокруг, кластер K1) и ценой инвестора "
            "(аренда K1 при доходности 11,5–12,5%). Класс A (K2), админздания (K3) и ПСН в жилых домах (K4) — ориентиры сверху и сбоку, в итог не входят.</li>")


def method():
    rows = ''
    for sk, S in V['subjects'].items():
        st = {'yard': 'ядро: Шелепиха, Хорошёвская, Беговая, Полежаевская; кольцо: Хорошёво, Звенигородская, Улица 1905 года, Народное Ополчение, ЦСКА, Петровский парк, Динамо, Зорге, Октябрьское поле, Мнёвники',
              'kaleidoscope': 'ядро: Нагорная, Нагатинская, Каширская; кольцо: Варшавская, Коломенская, Нахимовский проспект, Верхние Котлы, Тульская, Кантемировская, Москворечье',
              'imagine': 'ядро: Нагатинская, Коломенская, Нагорная; кольцо: Технопарк, Нагатинский Затон, Каширская, Верхние Котлы, Тульская, Автозаводская, ЗИЛ'}[sk]
        c = S['clusters']
        rows += (f"<tr><td><b>{e(S['title'])}</b></td><td>{e(st)}</td>" +
                 ''.join(f"<td class=r>{(c[cl]['sale'] or {}).get('n', 0)} / {(c[cl]['rent'] or {}).get('n', 0)}</td>" for cl in ('K1', 'K2', 'K3', 'K4')) + '</tr>')
    n_off = sum(1 for r in ROWS.values() if r.get('offplan'))
    n_nm = sum(1 for r in ROWS.values() if r.get('nonmarket'))
    return f'''<section class="obj"><h2>Как оценивали: глазами покупателя</h2>
<div class="box"><b>Рыночный фон (октябрь 2026).</b> Ключевая ставка ЦБ — 14% (11.09.2026). Ставки капитализации офисов Москвы — 11–12% (NF Group).
Средняя ставка аренды офисов класса B — 26 320 ₽/м²/год, вакантность класса B — 4,5% (Аналитический центр Москвы, 01.04.2026). НДС — 22%.</div>
<h3>Кто покупатель и где он ищет</h3>
<p>Покупатель офиса в БЦ класса B ищет похожее помещение у той же станции метро и у соседних 2–3 станций по линиям. Поэтому локация каждого объекта — это не радиус,
а набор станций: «ядро» (ближайшие) и «кольцо» (соседние). Дальше он смотрит на сам бизнес-центр: класс, возраст, фасад и холл, инженерию, расстояние до метро.
Одновременно он видит альтернативы: доплатить за класс A, сэкономить на административном здании или — для 1 этажа — взять помещение на 1 этаже жилого дома.</p>
<table class="t"><tr><th>Объект</th><th>Станции</th><th>K1 прод./аренда</th><th>K2</th><th>K3</th><th>K4</th></tr>{rows}</table>
<h3>Кластеры</h3><table class="t"><tr><th>Кластер</th><th>Что входит</th><th>Роль в оценке</th></tr>
<tr class=main><td><b>K1</b></td><td>Офис в бизнес-центре класса B/B+/B− (класс — по справочнику БЦ ЦИАН и по большинству карточек здания)</td><td>Основной ряд: сравнительный и доходный подходы</td></tr>
<tr><td><b>K2</b></td><td>Офис в БЦ класса A/A+ и новые офисные башни без указанного класса; строящиеся показаны отдельно</td><td>Верхняя граница</td></tr>
<tr><td><b>K3</b></td><td>Административные здания, особняки, старый фонд, класс C</td><td>Альтернатива без инфраструктуры БЦ</td></tr>
<tr><td><b>K4</b></td><td>Помещения в жилых домах и ЖК (ПСН, стрит-ритейл)</td><td>Альтернатива только для 1 этажей с отдельным входом</td></tr></table>
<p class="note">Из всех кластеров убраны: {n_off} строящихся лотов (сдача 2027+), {n_nm} нерыночных (городские торги, банкротство), ошибки ввода («аренда» с ценой продажи,
ставки ниже 4 000 ₽/м²/год), явные выбросы (Магистраль Плаза 533 тыс. — вдвое выше соседних лотов того же БЦ; 1 Zhukov 1 млн ₽/м²) и лоты, которые после поправок
отличаются от медианы ряда больше чем в 2,5 раза. Квартиры и апартаменты, ТЦ и склады — вне анализа.</p>
<h3>Профили бизнес-центров</h3>
<p>Для каждого БЦ из выборки открыта его страница на ЦИАН (через браузер Scrapling — страницы БЦ закрыты антиботом): класс, год, этажность, площадь, парковка, доступ,
лифты, инфраструктура, инженерия (вентиляция, кондиционирование, пожаротушение, электроснабжение). Здания оценены по фотографиям по шкале 1–5:
фасад, холл, качество отделки, возраст и инженерия, потолки. Ориентиры шкалы: 5 — новая башня класса A; 4–4,5 — современный БЦ или качественный лофт (Магистраль Плаза, Danilov Plaza, Холодильный пер.);
3–3,5 — типичный класс B (Варшавская Плаза, Imagine Plaza, ЯРД); 2–2,5 — советское админздание без реконструкции. Всего оценено {sum(1 for x in RAT.values() if isinstance(x, dict))} зданий.</p>
<h3>Шкала поправок (одна для всех аналогов)</h3>
<table class="t"><tr><th>Фактор</th><th>Поправка</th><th>Откуда</th></tr>
<tr><td>Торг</td><td>−8% к цене предложения</td><td>Типичный разрыв предложение/сделка для офисов Москвы</td></tr>
<tr><td>Удалённость от центра</td><td>−4% за каждый км, на который аналог ближе к Кремлю (не больше ±20%)</td><td>Регрессия по 195 продажам офисов в выборке: −5,5%/км; взято с запасом, часть эффекта — качество зданий</td></tr>
<tr><td>Метро</td><td>−1% за каждую минуту, на которую аналог ближе (не больше ±15%); МЦД +4 мин за редкие интервалы</td><td>Та же регрессия: −2%/мин; взято вдвое осторожнее</td></tr>
<tr><td>Здание</td><td>6% за балл оценки по фото (не больше ±18%)</td><td>Экспертная шкала; разница внутри класса B. Разрыв B→A шкала не покрывает — поэтому K2 только ориентир</td></tr>
<tr><td>Этаж и вход</td><td>1 этаж с уличным входом против верхних +10%; 1 этаж с входом через холл +3%; верхний против 1 этажа −3% (с отдельным входом −10%)</td>
<td>Внутри одних и тех же БЦ медианная разница 1 и верхних этажей — 0% по аренде (77 БЦ) и 3% по продаже (14 БЦ); премия за улицу — экспертно</td></tr>
<tr><td>Масштаб</td><td>−3% за каждое удвоение площади (аренда −2%)</td><td>Регрессия и практика: крупный лот — меньше покупателей</td></tr>
<tr><td>Отделка, арендатор</td><td>Без отделки +10%, white box +5%, дизайнерский ремонт −5%, продаётся с арендатором −3%</td><td>Стоимость ремонта 25–40 тыс. ₽/м²; покупатель «для себя» не доплачивает за чужой арендный договор</td></tr></table>
<h3>Как сводятся аналоги</h3>
<p>Вес аналога = 1 ÷ (5% + сумма модулей поправок): чем меньше пришлось поправлять, тем ближе аналог. У одного бизнес-центра — один вес, поэтому БЦ с 10 лотами не перевешивает
рынок. Аналоги с суммой поправок больше 60% не используются. Лоты в самом здании объекта — прямое свидетельство для покупателя: им отдаётся 40% итога сравнительного подхода
(ЯРД: соседнее строение комплекса и 6 объявлений аренды; Imagine Plaza: блок 187 м² на 2 этаже). Если в самом здании продаётся такой же блок,
итоговая цена не может быть выше его цены после торга и поправок больше чем на 3%: покупатель просто купит соседний.</p>
<h3>Доходный подход и согласование</h3>
<p>Ставки аренды K1 приведены к «чистой» базе: без НДС, эксплуатационных (5 000 ₽/м²/год) и коммунальных (1 500 ₽/м²/год) платежей, если они включены в запрос
(условия — по карточке и тексту объявления; если не сказано — считаем, что эксплуатация включена). Дальше та же шкала поправок. Недозагрузка 8% (до 400 м²) или 10%,
расходы собственника {n(V['params']['owner'])} ₽/м²/год, ставка капитализации 11,5% (ЯРД), 12% (Imagine Plaza), 12,5% («Калейдоскоп» — старое здание с низкими потолками).
Итог — 50/50 сравнительный и доходный (40/60 при узком рынке K1), диапазон ±8%.</p></section>'''


def history():
    return f'''<section class="obj"><h2>Проверка и история версий</h2>
<h3>Что изменилось в этой версии (кластеры)</h3><table class="t"><tr><th>Было</th><th>Стало</th><th>Влияние</th></tr>
<tr><td>Аналоги — смесь офисов в БЦ, админзданий и ПСН в жилых домах в радиусе 2 км</td><td>Четыре кластера; в итог входит только K1 (БЦ класса B), остальные — ориентиры</td><td>Офис в БЦ больше не сравнивается напрямую с первым этажом жилого дома</td></tr>
<tr><td>Радиус 2 км</td><td>Станции «ядра» и соседние 2–3 станции; выборка выросла: K1 — {V['subjects']['yard']['clusters']['K1']['sale']['n']} / {V['subjects']['kaleidoscope']['clusters']['K1']['sale']['n']} / {V['subjects']['imagine']['clusters']['K1']['sale']['n']} продаж и
{V['subjects']['yard']['clusters']['K1']['rent']['n']} / {V['subjects']['kaleidoscope']['clusters']['K1']['rent']['n']} / {V['subjects']['imagine']['clusters']['K1']['rent']['n']} аренд (ЯРД / Калейдоскоп / Imagine)</td><td>Медианы опираются на десятки зданий, а не на 5–8 подобранных лотов</td></tr>
<tr><td>Поправки «на глаз» к каждому аналогу</td><td>Единая шкала: центр, метро, оценка здания по фото, этаж, масштаб, отделка; веса по сумме поправок, один вес на БЦ</td><td>Воспроизводимо; дорогие лофты у Тульской и дешёвые корпуса у МКАД приводятся к объекту одинаково</td></tr>
<tr><td>Метро — из объявлений (у разных агентов по-разному)</td><td>Время пешком по координатам для всех зданий одним методом</td><td>У ЯРД найдена станция МЦД-4 «Ермакова Роща» в 0,5 км; у «Калейдоскопа» и Imagine — 21–23 мин вместо «10–15»</td></tr>
<tr><td>Класс и инженерия — по карточкам объявлений</td><td>Страницы БЦ на ЦИАН (Scrapling): класс, год, инженерия</td><td>ЯРД: кондиционирование местное, а не центральное; Imagine: 4 992 м², спринклеры</td></tr>
<tr><td>Аренда — 10 подобранных ставок</td><td>Все ставки K1 в локации, чистая база, поправки, один вес на БЦ, 40% — ставки самого БЦ</td><td>ЯРД: рынок ~29–30 тыс., сам БЦ ~23 тыс. → 27 тыс.; ЮАО: 18–22 тыс.</td></tr>
<tr><td>Потолки «Калейдоскопа» — в общей поправке на здание</td><td>Отдельно: −3% (2,57 м) и −7% (2,37 м)</td><td>Низкий потолок отсекает часть покупателей</td></tr></table>
<h3>Как менялась оценка</h3>
<table class="t"><tr><th>Объект</th><th>v1</th><th>v2: проверка аренды</th><th>v3: проверка конкурентов</th><th>v4: кластеры (итог)</th><th>Почему изменилось в v4</th></tr>
<tr><td>БЦ «ЯРД»</td><td class=r>189,4</td><td class=r>189,7</td><td class=r>183,1</td><td class=r><b>{n(TOT('yard') / 1e6, 1)}</b></td>
<td>Рыночная аренда K1 по 32 БЦ (Магистраль Плаза, Минерал Беговая, Линкор: 30–45 тыс. чистыми) выше, чем ставки 5 этажа ЯРД; МЦД-4 рядом. Сдерживают: свои ставки БЦ (40% веса), местное кондиционирование</td></tr>
<tr><td>БЦ «Калейдоскоп»</td><td class=r>142,3</td><td class=r>124,4</td><td class=r>110,5</td><td class=r><b>{n(TOT('kaleidoscope') / 1e6, 1)}</b></td>
<td>K1 — 38–40 лотов в 20 БЦ вместо 11 подобранных; рынок K1 для верхних этажей выше прежней оценки; отдельные скидки за потолки</td></tr>
<tr><td>БЦ Imagine Plaza</td><td class=r>360,3</td><td class=r>316,7</td><td class=r>300,5</td><td class=r><b>{n(TOT('imagine') / 1e6, 1)}</b></td>
<td>Аренда 2–3 этажей по 60+ БЦ — 19–20 тыс. чистыми (было 16 тыс.), поэтому доходный подход выше. Но все 8 блоков упираются в потолок —
блок 187 м² в этом же здании (164 тыс. без НДС до торга): верхние этажи 147–158 тыс., 1 этаж 161–169 тыс. ₽/м²</td></tr></table>
<p class="note">Суммы в млн ₽ без НДС.</p>
<h3>Что проверить нельзя</h3><ul>
<li>ЦИАН показывает цены предложений, а не сделок; торг 8% — типичный, в ЮАО при большом предложении он может быть 10–15%.</li>
<li>Оценки зданий по фото — экспертные; ±0,5 балла меняют сравнительный подход на ±3%.</li>
<li>Эксплуатационные ставки в конкретных БЦ и кадастровая стоимость — допущения; ±1 000 ₽/м²/год меняет доходный подход на ~5–8%.</li>
<li>Объекты не осматривались; площади, потолки и состояние — из презентаций собственника. Инженерию ЯРД (центральное или местное кондиционирование) нужно уточнить.</li></ul></section>'''


CSS = '''
@page { size: A4; margin: 14mm 12mm; }
body { font-family: "DejaVu Sans", Arial, sans-serif; font-size: 9pt; color: #1b2430; line-height: 1.36; }
h1 { font-size: 21pt; margin: 0 0 4px; color: #16324f; } h2 { font-size: 15pt; color: #16324f; margin: 0; padding-top: 2px; border-top: 3px solid #16324f; }
h3 { font-size: 10.8pt; color: #16324f; margin: 11px 0 5px; } h4 { font-size: 9.3pt; margin: 9px 0 4px; color: #3a4a5c; }
.sub { color: #5b6b7c; margin-bottom: 8px; } .m { color: #5b6b7c; font-size: 7.6pt; } .note { color: #4a5868; font-size: 8pt; }
a { color: #1f5fa8; text-decoration: none; }
.obj { page-break-before: always; } .pb { page-break-before: always; }
.gal { display: grid; grid-template-columns: 1.3fr 1fr 1fr; gap: 6px; margin: 6px 0 4px; } .gal img { width: 100%; height: 140px; object-fit: cover; border-radius: 3px; border: 1px solid #d7dee6; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; } ul { margin: 2px 0; padding-left: 16px; } li { margin: 1px 0; }
.keep { break-inside: avoid; } h3, h4 { break-after: avoid; }
.pros h3 { color: #1f7a4d; } .cons h3 { color: #b2332b; }
.cards { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 7px; }
.card { border: 1px solid #d7dee6; border-radius: 4px; overflow: hidden; break-inside: avoid; font-size: 7.8pt; }
.card img { width: 100%; height: 105px; object-fit: cover; display: block; } .noimg { height: 105px; background: #eef1f4; color: #8a97a5; display: grid; place-items: center; }
.cb { padding: 5px 7px; } .cb .p { margin: 2px 0; } .tag { background: #e3ebf3; color: #16324f; border-radius: 3px; padding: 0 4px; font-size: 7.2pt; }
table.t { width: 100%; border-collapse: collapse; font-size: 7.5pt; margin: 4px 0; break-inside: auto; }
.t th { background: #16324f; color: #fff; font-weight: 600; padding: 3px 4px; text-align: left; } .t td { border-bottom: 1px solid #e1e6ec; padding: 3px 4px; vertical-align: top; }
.t td.r, .t th.r { text-align: right; white-space: nowrap; } .t tr.tot td { background: #eaf0f6; font-weight: 600; } .res { font-size: 8.2pt; }
.t tr.main td { background: #f3f8f2; } .t tr.same td { background: #fff7e0; }
img.th { width: 54px; height: 38px; object-fit: cover; border-radius: 2px; } img.th2 { width: 74px; height: 52px; object-fit: cover; border-radius: 2px; }
.box { background: #f2f5f8; border-left: 4px solid #16324f; padding: 8px 12px; margin: 8px 0; }
.kpi { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 10px 0; } .kpi div { border: 1px solid #d7dee6; border-radius: 5px; padding: 8px 10px; }
.kpi b { font-size: 15pt; color: #16324f; display: block; }
'''


def build():
    body = f'''
<div><div class="m">Аналитическая записка · {DATE} · версия 4 (кластеры конкурентов)</div>
<h1>Справедливая цена продажи трёх офисных объектов в Москве</h1>
<div class="sub">БЦ «ЯРД» (САО) · БЦ «Калейдоскоп» (ЮАО) · БЦ Imagine Plaza (ЮАО) — оценка глазами покупателя</div>
<div class="kpi"><div><b>{mln(TOT('yard'))}</b>БЦ «ЯРД»: 303,8 + 680 м²</div><div><b>{mln(TOT('kaleidoscope'))}</b>БЦ «Калейдоскоп»: 796,2 м²</div>
<div><b>{mln(TOT('imagine'))}</b>Imagine Plaza: 8 блоков, 2 133 м²</div></div>
<h3>Сводка</h3>{summary()}
<p class="note">Справедливая цена — наиболее вероятная цена сделки при экспозиции 3–9 месяцев, без НДС (продавец на УСН или физлицо — цена как есть; плательщик НДС добавляет 22%).
«Цена в объявлении» оставляет ~8% на торг.</p>
<h3>Главные выводы</h3><ul>{conclusions()}</ul>
<div class="box">Данные: {n(len(ROWS))} объявлений ЦИАН о продаже и аренде офисов и ПСН 80–3 000 м² (выгрузка {DATE}), страницы {sum(1 for v in PAGES.values() if v.get('cls'))} бизнес-центров,
фото {sum(1 for x in RAT.values() if isinstance(x, dict))} зданий. Аналитика разделена: для каждого объекта — конкурентная карта БЦ, статистика по четырём кластерам, основной ряд K1 с поправками,
аналоги других классов, аренда и доходный подход, итог и обоснование.</div></div>
{method()}
{''.join(subject_section(k) for k in V['subjects'])}
{history()}
<section class="obj"><h2>Источники</h2><ul>
<li>Объявления и страницы бизнес-центров ЦИАН (cian.ru) — ссылки у каждого аналога; выгрузка {DATE}.</li>
<li>Координаты станций метро, МЦК и МЦД — справочник api.hh.ru/metro/1.</li>
<li><a href="https://ac.mos.ru/dashboards/key-indicators/business-service/">Аналитический центр Москвы — показатели рынка офисной недвижимости</a></li>
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
        pg.wait_for_timeout(2000)
        pg.pdf(path='reports/Оценка_офисов_ЯРД_Калейдоскоп_ImaginePlaza.pdf', format='A4', print_background=True,
               margin=dict(top='14mm', bottom='14mm', left='12mm', right='12mm'),
               display_header_footer=True, header_template='<div></div>',
               footer_template='<div style="font-size:7pt;color:#8a97a5;width:100%;text-align:center"><span class="pageNumber"></span> / <span class="totalPages"></span></div>')
        b.close()
    print('ok')
