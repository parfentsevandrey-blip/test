# -*- coding: utf-8 -*-
"""Load deck.html and report any slide whose content overflows the 1280x720 canvas."""
import os
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--no-sandbox"])
    pg = b.new_page(viewport={"width": 1280, "height": 720})
    pg.goto("file://" + os.path.join(HERE, "deck.html"), wait_until="load")
    pg.wait_for_timeout(500)
    data = pg.evaluate("""() => {
      // decorative layers intentionally bleed past the canvas and are clipped by overflow:hidden
      const DECO = /mesh|grain|corner-blob|d2-ghost|d2-glow|d2-vign|d2-img|d2-scrim|cov-bg|cov-scrim|cov-grid|cov-streak|mesh-cover|map-ring|z-band|z-glow|wash-lite|ring\\b/;
      const out = [];
      document.querySelectorAll('.slide').forEach((s, i) => {
        const r = s.getBoundingClientRect();
        let maxB = 0, culprit = '';
        s.querySelectorAll('*').forEach(el => {
          const cn = (typeof el.className === 'string') ? el.className : '';
          if (DECO.test(cn)) return;                 // skip intended decorative bleed
          const b = el.getBoundingClientRect();
          const relBottom = b.bottom - r.top;
          if (relBottom > maxB) maxB = relBottom;
          if (relBottom > 721 && el.children.length === 0 && el.textContent.trim())
            culprit += ` [${cn}|${el.tagName}: b=${relBottom.toFixed(0)} "${el.textContent.trim().slice(0,22)}"]`;
        });
        out.push({n: i+1, cls: s.className.replace('slide ',''), sh: s.scrollHeight, maxB: Math.round(maxB), culprit});
      });
      return out;
    }""")
    b.close()
bad = 0
for d in data:
    flag = ""
    if d["maxB"] > 722:
        flag = "  <<< OVERFLOW"; bad += 1
    print(f'{d["n"]:02d} {d["cls"][:26]:26s} content_maxB={d["maxB"]:4d}{flag}')
    if flag and d["culprit"]:
        print("     ", d["culprit"][:320])
print(f"\n{bad} slide(s) with real content overflow" if bad else "\nAll slides: content fits within 720px.")
