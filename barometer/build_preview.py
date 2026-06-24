"""
Сборка автономного HTML-снимка дашборда.

Запускает один цикл сбора данных и «запекает» текущее состояние в один файл
barometer-preview.html: CSS и JS встроены, данные вшиты, сеть не нужна.
Файл можно открыть двойным кликом (как kutuzovsky-12.html).

    python3 build_preview.py

Это СТАТИЧНЫЙ снимок (не обновляется). Для живой авто-обновляемой версии
запускайте app.py (см. README).
"""

from __future__ import annotations

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from core import pipeline  # noqa: E402


def build() -> str:
    print("→ Собираю данные из источников (один цикл)…")
    state = pipeline.run_pipeline(use_llm=False)

    css = open(os.path.join(HERE, "static/css/barometer.css"), encoding="utf-8").read()
    js = open(os.path.join(HERE, "static/js/barometer.js"), encoding="utf-8").read()
    html = open(os.path.join(HERE, "templates/index.html"), encoding="utf-8").read()

    # Вшиваем CSS вместо <link>.
    html = html.replace(
        "<link rel=\"stylesheet\" href=\"{{ url_for('static', filename='css/barometer.css') }}\">",
        f"<style>\n{css}\n</style>",
    )

    # Безопасно вшиваем JSON (экранируем закрывающие теги).
    state_json = json.dumps(state, ensure_ascii=False).replace("</", "<\\/")
    override = (
        "<script>\n"
        f"const __STATE__ = {state_json};\n"
        "fetchState = async function(){ try{clearTimeout(pollTimer);}catch(e){}; "
        "render(__STATE__); var c=document.getElementById('chip-updated'); "
        "if(c) c.innerHTML += ' · \\uD83D\\uDCF8 статичный снимок'; };\n"
        "refresh = async function(){ render(__STATE__); };\n"
        "</script>"
    )

    # Вшиваем JS вместо <script src>, добавляем оверрайд с данными.
    html = html.replace(
        "<script src=\"{{ url_for('static', filename='js/barometer.js') }}\"></script>",
        f"<script>\n{js}\n</script>\n{override}",
    )

    out = os.path.join(HERE, "barometer-preview.html")
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    r = state.get("reading") or {}
    print(f"✓ Готово: {out}")
    print(f"  Барометр: {r.get('final_barometer')} ({r.get('zone')}), "
          f"новостей: {(r.get('components') or {}).get('relevant_items')}")
    print("  Откройте файл двойным кликом в браузере.")
    return out


if __name__ == "__main__":
    build()
