#!/usr/bin/env python3
"""Измерение высоты сюжетов в браузере — на той самой ширине, что и на полосе.

Движок отбора решает задачу с ограничением по высоте, а значит высота должна
быть настоящей. Считать по числу символов нельзя: в кириллице другая средняя
ширина глифа, переносы меняют число строк, а последняя строка абзаца почти
никогда не полная. Ошибка накапливается и полоса либо переполняется, либо
остаётся полупустой.

Ловушка, стоившая целой сессии в прошлой системе: пробный документ ОБЯЗАН
начинаться с <!DOCTYPE html>. Без него Chromium уходит в quirks mode, теряет
strut строчного бокса и занижает все высоты примерно на пятую часть.
"""
import json
import os
import sys

from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'


def measure(fragments, width_mm, css):
    """fragments: {id: html}. Возвращает {id: высота в CSS-пикселях}."""
    probe = os.path.join(HERE, 'build', '.probe.html')
    os.makedirs(os.path.dirname(probe), exist_ok=True)
    ids = list(fragments)
    body = ''.join(
        f'<div class="frag" data-id="{i}" style="width:{width_mm}mm">{fragments[i]}</div>'
        for i in ids)
    open(probe, 'w', encoding='utf-8').write(
        '<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><style>'
        + css + '.frag{margin:0 0 40px 0}</style></head><body>' + body + '</body></html>')

    with sync_playwright() as pw:
        b = pw.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
        page = b.new_page(viewport={'width': 1700, 'height': 1200})
        page.goto('file://' + probe)
        page.wait_for_timeout(500)
        out = page.evaluate("""() => {
            const o = {};
            for (const el of document.querySelectorAll('.frag')) {
                o[el.dataset.id] = el.getBoundingClientRect().height;
            }
            return o;
        }""")
        b.close()
    return {k: round(v, 1) for k, v in out.items()}


if __name__ == '__main__':
    pool = json.load(open(sys.argv[1], encoding='utf-8'))
    from build_mb import CSS, item_html, COL_MM
    frags = {i['id']: item_html(i) for i in pool['items']}
    h = measure(frags, COL_MM, CSS)
    for i in pool['items']:
        i['height'] = h[i['id']]
    json.dump(pool, open(sys.argv[1], 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print(f'измерено {len(h)} сюжетов, ширина колонки {COL_MM} мм')
    print('высоты:', ', '.join(f'{k}={v:.0f}' for k, v in sorted(h.items())))
