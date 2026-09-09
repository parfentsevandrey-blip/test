#!/usr/bin/env python3
"""HTML -> PDF альбомного формата + PNG каждой полосы для визуальной проверки.

Заполнение полос проверяет fit.py — здесь только вывод.
"""
import os
import sys

from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
W_MM, H_MM = 431.8, 279.4

html = os.path.abspath(sys.argv[1])
pdf = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else 'build/marketbeat.pdf')
shots = os.path.join(HERE, 'build', 'shots')
os.makedirs(shots, exist_ok=True)

with sync_playwright() as pw:
    b = pw.chromium.launch(executable_path=CHROME, args=['--no-sandbox'])
    p = b.new_page(viewport={'width': 1700, 'height': 1120}, device_scale_factor=2)
    p.goto('file://' + html)
    p.wait_for_timeout(1200)
    p.pdf(path=pdf, width=f'{W_MM}mm', height=f'{H_MM}mm', print_background=True,
          margin={'top': '0', 'bottom': '0', 'left': '0', 'right': '0'})
    pages = p.query_selector_all('.page')
    for i, el in enumerate(pages, 1):
        el.screenshot(path=os.path.join(shots, f'p{i:02d}.png'))
    print('полос:', len(pages), '| PDF:', os.path.getsize(pdf), 'байт')
    b.close()
