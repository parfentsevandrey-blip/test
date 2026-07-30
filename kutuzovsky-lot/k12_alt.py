"""«Что можно купить за те же деньги» — таблица и фотокарточки из одного списка.

В сравнение берутся только те объекты, которые участвуют в аналитике:
свой дом, Capital Towers и Москва-Сити (Neva Towers — апартаменты, помечены *).
Циан даёт цены и ссылки по проектам сравнения, Яндекс Недвижимость — фото
готовых ремонтов (Циан отдаёт 403 на автоматические запросы).
Скрипт идемпотентен: таблицы `alt` и `photos` пересобираются целиком.
"""
import json

nf = lambda v: f'{v:,.0f}'.replace(',', ' ')
mn = lambda v: f'{v / 1e6:.1f}'.replace('.', ',')

Y = 'https://realty.yandex.ru/offer/%s/'
C = 'https://www.cian.ru/sale/flat/%s/'

#  проект, квартира, отделка, цена ₽, площадь м², ссылка, подпись, фото, подпись к фото
LOTS = [
    ('ЖК «Кутузовский XII»', '95,0 м², эт. 6/11',  'Дизайнерский', 162_500_000, 95.0,
     C % '331300080', 'Циан →', 'ph1.jpg', 'дизайнерский, с мебелью'),
    ('Capital Towers', '102,7 м², эт. 54/61',      'Дизайнерский', 162_183_840, 102.7,
     Y % '3939877470324445913', 'Яндекс →', 'ph2.jpg', 'дизайнерский, с мебелью'),
    ('Capital Towers', '103,9 м², эт. 35/61',      'Евроремонт',   170_000_000, 103.9,
     Y % '4936239574886085398', 'Яндекс →', 'ph3.jpg', 'евроремонт, без мебели'),
    ('Capital Towers', '109,3 м², эт. 30/67',      'Евроремонт',   131_160_000, 109.3,
     Y % '1873461760120024627', 'Яндекс →', 'ph4.jpg', 'евроремонт, без мебели'),
    ('Neva Towers*', '110,0 м², эт. 53/78',        'Дизайнерский', 149_000_000, 110.0,
     Y % '2167091926898988289', 'Яндекс →', 'ph5.jpg', 'дизайнерский · апартаменты'),
    ('Neva Towers*', '120,0 м², эт. 33/78',        'Дизайнерский', 135_000_000, 120.0,
     Y % '7312081600090073089', 'Яндекс →', 'ph6.jpg', 'дизайнерский · апартаменты'),
    ('ЖК «Кутузовский XII»', '93,1 м², эт. 2/11',  'Дизайнерский', 158_000_000, 93.1,
     C % '329819607', 'Циан →', None, None),
    ('Capital Towers', '130,0 м², эт. 23/70',      'Дизайнерский', 156_000_000, 130.0,
     C % '331630611', 'Циан →', None, None),
    ('ЖК «Кутузовский XII»', '95,0 м², эт. 2/11',  'Дизайнерский', 133_000_000, 95.0,
     C % '332125658', 'Циан →', None, None),
    ('ЖК «Бадаевский»', '119,3 м², эт. 11/18',     'Без отделки',  168_656_882, 119.25,
     C % '332316179', 'Циан →', None, None),
    ('ЖК «Дом Дау»', '158,7 м², эт. 66/87',        'Без отделки',  162_248_154, 158.74,
     C % '324172645', 'Циан →', None, None),
]

OUR, rest = LOTS[0], sorted(LOTS[1:], key=lambda x: -x[3])
rows = [[('▶ ' if i == 0 else '') + n, q, f, mn(p), nf(p / a), {'text': t, 'link': u}]
        for i, (n, q, f, p, a, u, t, _, _) in enumerate([OUR] + rest)]

cards = [{'img': img, 'title': n + (' — наш лот' if i == 0 else ''),
          'spec': f'{q} · {cap}', 'price': f'{mn(p)} млн ₽', 'ppm': f'{nf(p / a)} ₽/м²',
          'url': u, 'our': i == 0}
         for i, (n, q, f, p, a, u, t, img, cap) in enumerate(LOTS) if img]

# «Причина 3»: что покупатель получает в Сити вместо нашей квартиры
OUR_A, OUR_P = 95.0, 162_500_000
sg = lambda v, u: ('+' if v > 0 else '−') + f'{abs(v):.1f}'.replace('.', ',') + ' ' + u
CITY_NAMES = ('Capital Towers', 'Neva Towers*')
city = [[('▶ ' if i == 0 else '') + n + ('' if i == 0 else ' · ' + f.lower()),
         f'{a:.1f}'.replace('.', ','), q.split('эт. ')[1], mn(p),
         '—' if i == 0 else f'{sg(a - OUR_A, "м²")} · {sg((p - OUR_P) / 1e6, "млн ₽")}',
         {'text': t, 'link': u}]
        for i, (n, q, f, p, a, u, t, _, cap) in
        enumerate([LOTS[0]] + sorted([x for x in LOTS if x[0] in CITY_NAMES], key=lambda x: -x[3]))]

K = json.load(open('k12_tables.json'))
K['alt'], K['photos'], K['city'] = rows, cards, city
json.dump(K, open('k12_tables.json', 'w'), ensure_ascii=False, indent=1)
for r in rows: print(' | '.join(str(c if not isinstance(c, dict) else c['text']) for c in r))
print()
for c in cards: print(c['title'], '|', c['spec'], '|', c['price'], '|', c['ppm'])
print()
for r in city: print(' | '.join(str(c if not isinstance(c, dict) else c['text']) for c in r))
