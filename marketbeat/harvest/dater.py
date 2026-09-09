#!/usr/bin/env python3
"""URL из stdin -> дата публикации, снятая СО СТРАНИЦЫ, и заголовок.

Дату из URL или из выдачи поисковика брать нельзя: она регулярно врёт. Берём
только то, что напечатано на самой странице — JSON-LD, og:published_time, <time>.
"""
import concurrent.futures as cf
import html
import re
import subprocess
import sys

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124 Safari/537.36")
PATS = [r'"datePublished"\s*:\s*"([0-9]{4}-[0-9]{2}-[0-9]{2})',
        r'property="article:published_time"\s+content="([0-9]{4}-[0-9]{2}-[0-9]{2})',
        r'content="([0-9]{4}-[0-9]{2}-[0-9]{2})[^"]*"\s+property="article:published_time"',
        r'<time[^>]+datetime="([0-9]{4}-[0-9]{2}-[0-9]{2})']


def one(url):
    r = subprocess.run(['curl', '-sL', '-m', '55', '-A', UA, url],
                       capture_output=True, text=True, errors='ignore')
    s = r.stdout
    d = next((m.group(1) for p in PATS for m in [re.search(p, s)] if m), '?')
    t = re.search(r'(?is)<title[^>]*>(.*?)</title>', s)
    title = html.unescape(re.sub(r'\s+', ' ', t.group(1))).strip() if t else ''
    return d, url, title


if __name__ == '__main__':
    lo, hi = sys.argv[1], sys.argv[2]
    urls = [l.strip() for l in sys.stdin if l.strip()]
    with cf.ThreadPoolExecutor(8) as ex:
        for d, u, t in sorted(ex.map(one, urls), reverse=True):
            if lo <= d <= hi:
                print(f'{d}  {t[:88]:88}  {u}')
