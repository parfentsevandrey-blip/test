#!/usr/bin/env python3
"""Контроль заполнения полосы: и переполнение, и пустота — ошибка.

Для одностраничного формата пустая треть колонки — такой же брак, как текст,
уехавший за край. Проверяются все зоны, где содержание может не совпасть с
отведённым местом: две текстовые колонки, левая колонка с показателями и
графиками, три ячейки нижнего ряда (прогноз, сделки, термины) и строка
«главное за неделю».
"""
import os
import sys

from playwright.sync_api import sync_playwright

CHROME = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
MIN_FILL = {'pcol': 0.93, 'left': 0.93, 'outlook': 0.80, 'deals': 0.70, 'gloss': 0.55, 'keys': 0.0}
MAX_FILL = 1.0

JS = """() => [...document.querySelectorAll('.page')].map(pg => {
  const box = el => el.getBoundingClientRect();
  const fill = (sel, inner) => {
    const el = pg.querySelector(sel);
    if (!el) return null;
    const b = box(el);
    const kids = [...el.querySelectorAll(inner)];
    const low = kids.length ? Math.max(...kids.map(k => box(k).bottom)) : b.top;
    return (low - b.top) / b.height;
  };
  const pcols = [...pg.querySelectorAll('.pcol')].map(el => {
    const b = box(el); const kids = [...el.querySelectorAll('.pb > *')];
    const low = kids.length ? Math.max(...kids.map(k => box(k).bottom)) : b.top;
    return (low - b.top) / b.height;
  });
  return {
    sector: pg.dataset.sector,
    pcol: pcols,
    left: fill('.left', '.left > *'),
    outlook: fill('.outlook', '.pb > *'),
    deals: fill('.deals', '.deals > *'),
    gloss: fill('.gloss', '.gloss > *'),
    keys: fill('.keys', '.key > *'),
  };
})"""


def check(html):
    with sync_playwright() as pw:
        b = pw.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
        p = b.new_page(viewport={'width': 1700, 'height': 1120})
        p.goto('file://' + os.path.abspath(html))
        p.wait_for_timeout(900)
        data = p.evaluate(JS)
        b.close()
    bad = 0
    for d in data:
        parts, flags = [], []
        zones = [('текст-1', d['pcol'][0], 'pcol'), ('текст-2', d['pcol'][1], 'pcol'),
                 ('слева', d['left'], 'left'), ('прогноз', d['outlook'], 'outlook'),
                 ('сделки', d['deals'], 'deals'), ('термины', d['gloss'], 'gloss'),
                 ('главное', d['keys'], 'keys')]
        for name, v, kind in zones:
            if v is None:
                flags.append(f'{name}: нет зоны'); bad += 1; continue
            parts.append(f'{name} {v*100:5.1f}%')
            if v > MAX_FILL + 0.002:
                flags.append(f'{name}: ПЕРЕПОЛНЕНО'); bad += 1
            elif v < MIN_FILL[kind]:
                flags.append(f'{name}: ПУСТО'); bad += 1
        print(f"{d['sector']:<11} " + ' · '.join(parts) + ('  <- ' + '; '.join(flags) if flags else '  ОК'))
    return bad


if __name__ == '__main__':
    n = check(sys.argv[1])
    sys.exit(1 if n else 0)
