#!/usr/bin/env python3
"""URL из stdin -> дата, заголовок и читаемый текст.

Текст собираем по абзацам, а не выдиранием <article>: у разных изданий статья
лежит в разных контейнерах, а предложения всегда внутри <p>.
"""
import concurrent.futures as cf
import html
import re
import subprocess
import sys

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124 Safari/537.36")
JUNK = ('Login incorrect', 'Wachtwoord', 'social media', 'niet volledig lezen',
        'nieuwsbrief', 'promotieknop', 'Bij elke bestelling', 'bereikbaar via',
        'moet u zijn ingelogd', 'cookie', 'Cookie', 'privacyverklaring',
        'abonnee', 'Topvacature', 'Interim', 'geselecteerde groepen', 'leden')
DATE = [r'"datePublished"\s*:\s*"([0-9-]{10})',
        r'article:published_time"\s+content="([0-9-]{10})',
        r'<time[^>]+datetime="([0-9-]{10})']


def clean(x):
    x = re.sub(r'(?i)<br\s*/?>', ' ', x)
    return re.sub(r'[\s\xa0]+', ' ', html.unescape(re.sub(r'<[^>]+>', '', x))).strip()


def one(url):
    s = subprocess.run(['curl', '-sL', '-m', '55', '-A', UA, url],
                       capture_output=True, text=True, errors='ignore').stdout
    s = re.sub(r'(?is)<(script|style|noscript|svg)[^>]*>.*?</\1>', ' ', s)
    d = next((m.group(1) for p in DATE for m in [re.search(p, s)] if m), '?')
    t = re.search(r'(?is)<title[^>]*>(.*?)</title>', s)
    out, seen = [], set()
    m = re.search(r'(?is)<meta[^>]+(?:name|property)="(?:og:)?description"[^>]+content="([^"]{40,})"', s)
    if m:
        out.append(clean(m.group(1)))
    for m in re.finditer(r'(?is)<(p|h2|li)[^>]*>(.*?)</\1>', s):
        c = clean(m.group(2))
        if len(c) < 45 or c in seen or any(b in c for b in JUNK):
            continue
        if re.match(r'^\d\d-\d\d-20\d\d \d\d:\d\d ', c):
            continue
        seen.add(c)
        out.append(c)
    return d, url, clean(t.group(1)) if t else '', '\n'.join(out)


if __name__ == '__main__':
    cap = int(sys.argv[1]) if len(sys.argv) > 1 else 700
    urls = [l.strip() for l in sys.stdin if l.strip()]
    with cf.ThreadPoolExecutor(6) as ex:
        for d, u, t, body in ex.map(one, urls):
            print('=' * 90)
            print(f'[{d}] {t[:80]}\n{u}')
            print(body[:cap])
