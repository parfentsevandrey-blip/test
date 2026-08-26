"""Сбор хроники согласований по трём площадкам AURIX из Telegram.

Официальных сообщений о прохождении градостроительных комиссий застройщик
не публикует, а отраслевые каналы следят за ГЗК и выкладывают показатели
проектов сразу после согласования. Отсюда взяты ТЭПы по «Малой Полянке»
и «Земледельческому» и даты по всем трём площадкам.

Пост отдаётся по адресу https://t.me/<канал>/<номер>?embed=1 — обычный HTML:
текст в div.tgme_widget_message_text, дата в атрибуте datetime тега <time>.
Вариант ?embed=1&mode=tme обрезает текст, поэтому используется голый ?embed=1.

Что из какого поста взято:

  31831  07.11.2025  «Малая Полянка, 3» выходит на согласование: 4,9 тыс. м²,
                     34 квартиры, средняя площадь 144 м², паркинг 36 мест;
                     оснований сохранять существующее здание нет
  33421  12.03.2026  ГЗК согласовала концепцию «Большая Ордынка, 25, стр. 1»;
                     самый дорогой проект «Эталона» в Москве; ориентир
                     «не менее 3 млн ₽ за метр»; конкуренты по адресу —
                     Sminex, «Русские сезоны», «Текта»; сделка по акциям
                     «Бизнес-Недвижимости» закрыта в декабре 2025 года
  34170  07.05.2026  «Земледельческий, 15»: согласовано 8 тыс. м² жилья
                     под 77 квартир, несмотря на плотную застройку вокруг
                     и ограничения по инсоляции; планка Хамовников — 3 млн
                     ₽ за метр; в очереди также «Малая Дмитровка, 5/9»
  35495  25.08.2026  «Эталон» разместил на сайте AURIX три люксовых проекта:
                     «Большая Ордынка, 25», «Земледельческий, 15»
                     и «Малая Полянка, 3»
"""
import os, re, urllib.request

POSTS = [('propertyinsider', n) for n in (31831, 33421, 34170, 35495)]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tg')
UA = {'User-Agent': 'Mozilla/5.0'}

get = lambda u: urllib.request.urlopen(
    urllib.request.Request(u, headers=UA), timeout=60).read()


def post(chan, num):
    html = get(f'https://t.me/{chan}/{num}?embed=1').decode('utf-8', 'replace')
    texts = []
    for raw in re.findall(
            r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', html, re.S):
        t = re.sub(r'<br\s*/?>', '\n', raw)
        t = re.sub(r'<[^>]+>', '', t)
        t = (t.replace('&quot;', '"').replace('&amp;', '&')
              .replace('&gt;', '>').replace('&lt;', '<').replace('&nbsp;', ' '))
        t = re.sub(r'[ \t]+', ' ', t).strip()
        if len(t) > 40:
            texts.append(t)
    date = (re.search(r'<time[^>]*datetime="([^"]+)"', html) or [None, ''])[1]
    return date, texts


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for chan, num in POSTS:
        date, texts = post(chan, num)
        print(f'\n=== {chan}/{num} — {date[:10]} ===')
        for t in texts:
            print(t[:1800])
