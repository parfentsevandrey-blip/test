#!/usr/bin/env python3
"""Сканер публичных превью Telegram-каналов (t.me/s/<handle>) по ключевым словам.

  python3 tools/cian/tg-scan.py --channels propertyinsider,nedvizha --since 2025-06-01 --out posts.json [--max-pages 80]

Листает ленту назад (?before=<id>), пока дата поста не станет раньше --since.
Сохраняет посты, где текст задевает наши локации или тему площадок: ссылка на пост, дата, текст.
"""
import re, json, sys, time, html, urllib.request, urllib.parse
from datetime import datetime, timezone

args = sys.argv[1:]
opt = lambda k, d=None: args[args.index('--' + k) + 1] if '--' + k in args else d
channels = [c.strip() for c in opt('channels', '').split(',') if c.strip()]
since = datetime.fromisoformat(opt('since', '2025-06-01')).replace(tzinfo=timezone.utc)
max_pages = int(opt('max-pages', '80'))
out = opt('out', 'tg-posts.json')

LOC = re.compile(r'Хамовник|Пресн|Садов\w* кольц|внутри Садового|Остоженк|Пречистенк|Патриарш|Якиманк|Замоскворечь|Арбат|Тверск|Сити|Белорусск|Тишинск|Кропоткинск|Полянк|Ордынк|Лужник|Фрунзенск|Саввинск|Никитск|Кузнецк|Рождественк|Столешник|Дмитровк|Бронн|Козихинск|Чистые пруд|Мещанск|Красносельск|Басманн|Таганск|Котельническ|Серебряническ|Тессинск|Бернико|Краснопресненск|Шелепих|Ходынск|Грузинск|Лесная|Новослободск|Долгоруковск|Плющих|Погодинск|Кооперативн|Усачёв|Усачев|Тимура Фрунзе|Льва Толстого|Пироговск|Зубовск|Смоленск|Пятницк|Татарск|Садовническ|Балчуг|Софийск|Бол\.? ?Якиманк|Цветн|Сретенк|Трубн|Неглинн|Петровк|Большая Дмитровка|Малая Дмитровка|Спиридон|Ермолаевск|Трёхпрудн|Трехпрудн|Малая Никитск|Большая Никитск|Поварск|Воздвиженк|Знаменк|Волхонк|ЦАО|центр\w* Москв')
TOPIC = re.compile(r'участ|ЗУ\b|площадк|ГПЗУ|КРТ|АГР|проект\w* планировк|клубн\w+ дом|делюкс|премиум|элитн|старт продаж|старт\w* продаж|купил|приобрел|приобрёл|выкупил|продаёт|продает|торг|аукцион|редевелопмент|реконструкц|снос|застройщик|девелопер|разрешени\w+ на строительство|РНС', re.I)

def fetch(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    for a in range(3):
        try: return urllib.request.urlopen(req, timeout=30).read().decode('utf-8', 'ignore')
        except Exception: time.sleep(2 * (a + 1))
    return ''

def clean(t):
    t = re.sub(r'<br\s*/?>', '\n', t); t = re.sub(r'<[^>]+>', '', t)
    return html.unescape(t).strip()

msg_re = re.compile(r'<div class="tgme_widget_message_wrap[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>\s*(?=<div class="tgme_widget_message_wrap|$)', re.S)
results = json.load(open(out)) if out and __import__('os').path.exists(out) else {}
for ch in channels:
    seen = {p['id'] for p in results.get(ch, {}).get('posts', [])}
    posts = results.get(ch, {}).get('posts', [])
    before = None; pages = 0; oldest = None; title = results.get(ch, {}).get('title')
    while pages < max_pages:
        url = f'https://t.me/s/{ch}' + (f'?before={before}' if before else '')
        page = fetch(url); pages += 1
        if not page: break
        if not title:
            m = re.search(r'tgme_channel_info_header_title"><span dir="auto">([^<]*)', page); title = html.unescape(m.group(1)) if m else ch
        ids = [int(x) for x in re.findall(r'data-post="' + re.escape(ch) + r'/(\d+)"', page)]
        if not ids: break
        blocks = re.findall(r'<div class="tgme_widget_message text_not_supported_wrap js-widget_message" data-post="' + re.escape(ch) + r'/(\d+)"(.*?)(?=<div class="tgme_widget_message_wrap|<section|$)', page, re.S)
        for pid, body in blocks:
            pid = int(pid)
            dm = re.search(r'<time datetime="([^"]+)"', body)
            dt = datetime.fromisoformat(dm.group(1).replace('Z', '+00:00')) if dm else None
            if dt and (oldest is None or dt < oldest): oldest = dt
            tm = re.search(r'<div class="tgme_widget_message_text[^"]*"[^>]*>(.*?)</div>', body, re.S)
            text = clean(tm.group(1)) if tm else ''
            if pid in seen or not text: continue
            if LOC.search(text) and TOPIC.search(text):
                posts.append({'id': pid, 'url': f'https://t.me/{ch}/{pid}', 'date': dt.date().isoformat() if dt else None, 'text': text[:2500],
                              'links': re.findall(r'href="(https?://[^"]+)"', body)[:6]})
                seen.add(pid)
        before = min(ids)
        if oldest and oldest < since: break
        time.sleep(0.8)
    results[ch] = {'title': title, 'pages': pages, 'oldest': oldest.date().isoformat() if oldest else None, 'posts': sorted(posts, key=lambda p: -p['id'])}
    json.dump(results, open(out, 'w'), ensure_ascii=False, indent=1)
    print(f'{ch} ({title}): страниц {pages}, до {oldest.date() if oldest else "?"}, релевантных постов {len(posts)}')
