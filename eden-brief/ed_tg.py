"""Сбор визуализаций и новостей по EDEN из отраслевых Telegram-каналов.

Официальной галереи у проекта нет, поэтому визуализации и хроника согласований
собраны из публичных постов отраслевых каналов. Пост отдаётся по адресу
https://t.me/<канал>/<номер>?embed=1 — это обычный HTML: текст лежит в
div.tgme_widget_message_text, картинки — в inline-стиле
background-image:url('https://cdn4.telesco.pe/file/...').

Вариант ?embed=1&mode=tme обрезает текст, поэтому используется голый ?embed=1.

Что из чего взято в справке:

  assets/r1.jpg  <- propertyinsider/32429  главный фасад
  assets/r2.jpg  <- startyprodazh/9725     угловой ракурс
  assets/r3.jpg  <- startyprodazh/9725     вид с Воздвиженки
  assets/r4.jpg  <- startyprodazh/9725     вид из переулка

Факты, попавшие в таблицу «Хроника проекта» (ed_data.py, CHRON):

  9568  23.04.2024  анонс двух проектов де-люкс, ПИК как соинвестор,
                    рабочее название «Дом Белый», ожидалось ~20 000 м²
  9725  03.05.2024  акт ГИКЭ положительный: сносимые здания объектами
                    культурного наследия не являются; участок — в границах
                    культурного слоя «Белого города» XIV–XVII вв.
  11406 16.09.2024  экспертиза пройдена, № 77-2-1-3-052163-2024 от 05.09.2024,
                    площадь комплекса ~12 700 м², генпроектировщик АПЕКС,
                    архитектура Gregory Tuck Architects
  32429 10.12.2025  Sense + ПИК + частные инвесторы; спор об авторстве
                    концепции; «средняя площадь квартиры — 281 кв. м»;
                    «Никитский, 6» оценивают «на уровне 3 мультов с метра»
"""
import os, re, urllib.request

POSTS = [
    ('startyprodazh', 9568),
    ('startyprodazh', 9725),
    ('startyprodazh', 11406),
    ('propertyinsider', 32429),
]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tg')
UA = {'User-Agent': 'Mozilla/5.0'}

get = lambda u: urllib.request.urlopen(
    urllib.request.Request(u, headers=UA), timeout=60).read()

strip = lambda h: re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', h)).strip()


def post(chan, num):
    html = get(f'https://t.me/{chan}/{num}?embed=1').decode('utf-8', 'replace')
    texts = [strip(m) for m in re.findall(
        r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', html, re.S)]
    imgs = re.findall(r"background-image:url\('([^']+)'\)", html)
    imgs = [u for u in imgs if 'telesco.pe/file' in u]
    return texts, imgs


if __name__ == '__main__':
    os.makedirs(os.path.join(OUT, 'img'), exist_ok=True)
    for chan, num in POSTS:
        texts, imgs = post(chan, num)
        print(f'\n=== {chan}/{num} — {len(texts)} текстов, {len(imgs)} картинок ===')
        for t in texts[:2]:
            print(' ', t[:400])
        for i, u in enumerate(imgs):
            p = os.path.join(OUT, 'img', f'{chan}_{num}_{i}.jpg')
            open(p, 'wb').write(get(u))
            print('  ->', os.path.basename(p))
    print('\nНужные кадры вручную отобраны в assets/r1..r4.jpg — соседние посты '
          'в выдаче embed отдают свои картинки, автоматический отбор не годится.')
