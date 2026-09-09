#!/usr/bin/env python3
"""Контроль заполнения полосы: и переполнение, и пустота — ошибка.

Первая версия проверяла только переполнение и пропустила полосы, заполненные
наполовину. Для одностраничного формата пустая половина — такой же брак, как
текст, уехавший за край: полоса обязана быть плотной.
"""
import os
import sys

from playwright.sync_api import sync_playwright

CHROME = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
MIN_FILL = 0.88

JS = """() => [...document.querySelectorAll('.page')].map(pg => {
  const box = el => el.getBoundingClientRect();
  const body = box(pg.querySelector('.body'));
  const low = sel => {
    const els = [...pg.querySelectorAll(sel)];
    return els.length ? Math.max(...els.map(e => box(e).bottom)) : body.top;
  };
  const prose = pg.querySelector('.prose');
  const pb = [...prose.querySelectorAll('.pb')];
  return {
    sector: pg.dataset.sector,
    body_h: body.height,
    prose_used: low('.prose .pb') - body.top,
    rail_used: low('.rail > *') - body.top,
    chart_used: low('.charts .chart') - body.top,
    blocks: pb.map(b => ({key: b.dataset.block, h: box(b).height})),
  };
})"""


def check(html):
    with sync_playwright() as pw:
        b = pw.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
        p = b.new_page(viewport={'width': 1700, 'height': 1120})
        p.goto('file://' + os.path.abspath(html))
        p.wait_for_timeout(800)
        data = p.evaluate(JS)
        b.close()
    bad = 0
    for d in data:
        fill = d['prose_used'] / d['body_h']
        rail = d['rail_used'] / d['body_h']
        state = 'ОК'
        if fill > 1.0:
            state, bad = 'ПЕРЕПОЛНЕНО', bad + 1
        elif fill < MIN_FILL:
            state, bad = 'ПУСТО', bad + 1
        if rail > 1.0:
            state, bad = state + ' · РЕЙКА ВЫШЛА ЗА ТЕЛО', bad + 1
        blocks = ', '.join(f"{x['key']} {x['h']:.0f}px" for x in d['blocks'])
        print(f"{d['sector']:<11} проза {fill*100:5.1f}%  рейка {rail*100:5.1f}%  "
              f"графики {d['chart_used']/d['body_h']*100:5.1f}%  [{blocks}]  {state}")
    return bad


if __name__ == '__main__':
    n = check(sys.argv[1])
    sys.exit(1 if n else 0)
