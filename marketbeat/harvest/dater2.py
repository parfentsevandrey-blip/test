#!/usr/bin/env python3
"""Как dater.py, но понимает и «человеческие» даты на странице: «7 sep. 26»
(Vakmedianet: Vastgoedmarkt, Cobouw, Logistiek), «4 september 2026 om 09:57»
(Vastgoedjournaal), «1 september 2026». Берём первую дату в теле статьи."""
import concurrent.futures as cf, html, re, subprocess, sys
UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124 Safari/537.36")
MON = {'jan':1,'feb':2,'mrt':3,'apr':4,'mei':5,'jun':6,'jul':7,'aug':8,'sep':9,'okt':10,'nov':11,'dec':12,
       'januari':1,'februari':2,'maart':3,'april':4,'juni':6,'juli':7,'augustus':8,'september':9,'oktober':10,'november':11,'december':12}
PATS = [r'"datePublished"\s*:\s*"(\d{4}-\d\d-\d\d)',
        r'article:published_time"\s+content="(\d{4}-\d\d-\d\d)',
        r'content="(\d{4}-\d\d-\d\d)[^"]*"\s+property="article:published_time"',
        r'<time[^>]+datetime="(\d{4}-\d\d-\d\d)']
def one(url):
    s = subprocess.run(['curl','-sL','-m','55','-A',UA,url],capture_output=True,text=True,errors='ignore').stdout
    d = next((m.group(1) for p in PATS for m in [re.search(p, s)] if m), None)
    if not d:
        body = re.sub(r'(?is)<(script|style|nav|header|footer)[^>]*>.*?</\1>', ' ', s)
        m = re.search(r'fa-calendar[^>]*></i>\s*(\d{1,2}) ([a-z]+)\.? (20\d\d)', body) or \
            re.search(r'(?<![\d-])(\d{1,2}) (jan|feb|mrt|apr|mei|jun|jul|aug|sep|okt|nov|dec)\.? ?(\d\d)(?!\d)', body) or \
            re.search(r'(?<![\d-])(\d{1,2}) (januari|februari|maart|april|mei|juni|juli|augustus|september|oktober|november|december) (20\d\d)', body)
        if m:
            y = m.group(3); y = ('20' + y) if len(y) == 2 else y
            d = f'{y}-{MON[m.group(2).lower()]:02d}-{int(m.group(1)):02d}'
    t = re.search(r'(?is)<title[^>]*>(.*?)</title>', s)
    title = html.unescape(re.sub(r'\s+',' ',t.group(1))).strip() if t else ''
    return d or '?', url, title
if __name__ == '__main__':
    lo, hi = sys.argv[1], sys.argv[2]
    urls = [l.strip() for l in sys.stdin if l.strip()]
    with cf.ThreadPoolExecutor(8) as ex:
        for d, u, t in sorted(ex.map(one, urls), reverse=True):
            if lo <= d <= hi: print(f'{d}  {t[:88]:88}  {u}')
