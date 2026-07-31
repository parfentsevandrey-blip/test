"""Как устроена продажа в люкс-нише: измеряем канал и упаковку объявлений.

Считаем по той же выборке из 12 объявлений (10 квартир) внутри Садового кольца
и сравниваем с карточкой нашего лота. Всё, что можно проверить в выгрузке:
кто продаёт, сколько агентов на квартиру, длина текста, какие аргументы
вообще проговариваются, насколько круглая цена.
"""
import json, re, sys, os, collections, statistics
sys.path.insert(0, os.path.dirname(__file__))
import zl_links as Z

SRC = '/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/628f43a5-cian_______________________20260731_12_____.xls'
OUR_URL = 'https://www.cian.ru/sale/flat/326035617/'
LIMIT = 600                      # Циан обрезает описание на 600 знаках

rs = Z.rows(SRC)
hi = next(i for i, (c, _) in enumerate(rs) if c and c[0] == '№')
H = {n: i for i, n in enumerate(rs[hi][0])}
g = lambda c, k: c[H[k]] if k in H and H[k] < len(c) else ''
N = [dict(area=float(g(c, 'Площадь, м²')), price=int(g(c, 'Цена, ₽')),
          sel=g(c, 'Тип продавца'), agent=g(c, 'Продавец'),
          d=(g(c, 'Описание') or '').replace('\n', ' '))
     for c, href in rs[hi + 1:] if g(c, 'Площадь, м²')]

R = json.load(open('zl/zl_raw.json'))
OUR = next(x for x in R['Золотой'] if x['url'] == OUR_URL)
n = len(N)

has = lambda pat, txt: bool(re.search(pat, txt, re.I))
cnt = lambda pat: sum(1 for x in N if has(pat, x['d']))
yn  = lambda pat: 'да' if has(pat, OUR['desc']) else 'нет'

KREML = r'вид[а-я]* на Кремль|окна на Кремль'
SERV  = r'консьерж|паркин|машиномест'
AUTH  = r'дизайн[- ]?проект|архитектурн[а-я]* бюро|архитектор|бюро '
EXCL  = r'эксклюзив'
FURN  = r'мебел|меблир'

agents = len(set(x['agent'] for x in N if x['agent']))
ag = sum(1 for x in N if x['sel'] == 'Агентство')
own = sum(1 for x in N if x['sel'] == 'Собственник')
lim = sum(1 for x in N if len(x['d']) >= LIMIT - 5)
r5 = sum(1 for x in N if x['price'] % 5_000_000 == 0)

rows = [
    ['Кто продаёт', f'{ag} из {n} — агентства и частные агенты',
     f'карточка Циан: «{OUR["seller"]}», {OUR["agent"]}'],
    ['Продают сами собственники', f'{own} из {n}', 'нет'],
    ['Разных агентов на выборку', f'{agents} на {n} объявлений', '1'],
    ['Одна квартира — два объявления', '2 из 10 квартир', 'нет'],
    ['Длина описания', f'{lim} из {n} упираются в лимит Циан {LIMIT} знаков',
     f'{len(OUR["desc"])} знаков — тоже лимит'],
    ['Пишут «вид на Кремль»', f'{cnt(KREML)} из {n}', yn(KREML)],
    ['Пишут про консьержа или паркинг', f'{cnt(SERV)} из {n}', yn(SERV)],
    ['Называют бюро или архитектора', f'{cnt(AUTH)} из {n}', yn(AUTH)],
    ['Заявляют эксклюзив', f'{cnt(EXCL)} из {n}', yn(EXCL)],
    ['Упоминают мебель и комплектацию', f'{cnt(FURN)} из {n}', yn(FURN)],
    ['Круглая цена, кратная 5 млн ₽', f'{r5} из {n}', '280,0 млн ₽ — кратно 10'],
]

stats = {'n': n, 'agents': agents, 'agency': ag, 'owners': own, 'limit': lim,
         'kreml': cnt(KREML), 'serv': cnt(SERV), 'auth': cnt(AUTH),
         'excl': cnt(EXCL), 'furn': cnt(FURN), 'round5': r5,
         'ourLen': len(OUR['desc']), 'ourSeller': OUR['seller'], 'ourAgent': OUR['agent'],
         'typo': 'beedroom' in OUR['desc']}

K = json.load(open('zl/zl_tables.json'))
K['sell'], K['sellStats'] = rows, stats
json.dump(K, open('zl/zl_tables.json', 'w'), ensure_ascii=False, indent=1)

for r in rows: print(' | '.join(r))
print('\nопечатка «master beedroom» в описании нашего лота:', stats['typo'])
