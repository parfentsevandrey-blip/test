#!/usr/bin/env python3
"""Собрать docs/premium-cao/fonts.css — шрифт документа, вшитый в CSS как data: URI.

  python3 tools/cian/fetch-fonts.py

Берём у Google Fonts только нужные срезы (латиница + кириллица) и кладём их
внутрь CSS: тогда печать PDF не зависит от сети и даёт один и тот же результат.
"""
import base64, re, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs' / 'premium-cao' / 'fonts.css'
UA = 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'
API = 'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap'
KEEP = ('cyrillic', 'latin')     # остальные срезы (греческий, вьетнамский) документу не нужны


def get(url, headers=None):
    req = urllib.request.Request(url, headers={'User-Agent': UA, **(headers or {})})
    return urllib.request.urlopen(req, timeout=60).read()


css = get(API).decode('utf-8')
blocks, kept, skipped = [], 0, 0
for block in re.findall(r'/\*\s*([\w\-]+)\s*\*/\s*(@font-face\s*\{[^}]*\})', css):
    subset, face = block
    if subset not in KEEP and not subset.startswith(tuple(KEEP)):
        skipped += 1
        continue
    m = re.search(r'url\((https://fonts\.gstatic\.com[^)]+)\)', face)
    if not m:
        continue
    data = base64.b64encode(get(m.group(1))).decode('ascii')
    blocks.append(face.replace(m.group(1), f'data:font/woff2;base64,{data}'))
    kept += 1

OUT.write_text('/* Сгенерировано tools/cian/fetch-fonts.py — не править руками */\n'
               + '\n'.join(blocks) + '\n', encoding='utf-8')
print(f'-> {OUT}: срезов {kept} (пропущено {skipped}), {OUT.stat().st_size // 1024} КБ')
