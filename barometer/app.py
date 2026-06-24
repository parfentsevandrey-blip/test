"""
Барометр мобилизации — веб-приложение (Flask).

Запуск:
    cd barometer
    python app.py
    # открыть http://127.0.0.1:5000

Опциональные переменные окружения:
    ANTHROPIC_API_KEY      — включает глубокий анализ Claude
    BAROMETER_LLM_MODEL    — модель Claude (по умолчанию claude-sonnet-4-6)
    X_BEARER_TOKEN         — включает реальную выгрузку из X/Twitter (иначе сэмпл)
    BAROMETER_REFRESH_MINUTES, BAROMETER_WINDOW_DAYS — параметры расчёта
"""

from __future__ import annotations

import os
import sys
import threading
import time

# Корень проекта (каталог этого файла) — в sys.path, чтобы работали
# абсолютные импорты `import config` и `from core import ...`.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from flask import Flask, jsonify, render_template, request  # noqa: E402

import config  # noqa: E402
from core import pipeline, store  # noqa: E402

app = Flask(__name__, static_folder="static", template_folder="templates")

_scheduler_started = False


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/api/state")
def api_state():
    return jsonify(pipeline.get_state())


@app.route("/api/refresh", methods=["POST"])
def api_refresh():
    use_llm = request.args.get("nollm") != "1"
    return jsonify(pipeline.run_pipeline(use_llm=use_llm))


@app.route("/api/health")
def api_health():
    return jsonify({"ok": True, "pipeline": pipeline.status()})


def _scheduler_loop():
    # Первый прогон при старте, если данных ещё нет.
    try:
        if store.last_reading() is None:
            pipeline.run_pipeline()
    except Exception:  # noqa: BLE001
        pass
    while True:
        time.sleep(max(60, config.REFRESH_MINUTES * 60))
        try:
            pipeline.run_pipeline()
        except Exception:  # noqa: BLE001
            pass


def start_scheduler():
    global _scheduler_started
    if _scheduler_started:
        return
    _scheduler_started = True
    store.init_db()
    threading.Thread(target=_scheduler_loop, name="barometer-scheduler", daemon=True).start()


if __name__ == "__main__":
    start_scheduler()
    port = int(os.environ.get("PORT", "5000"))
    # use_reloader=False — иначе планировщик запустится дважды.
    app.run(host="127.0.0.1", port=port, debug=False, use_reloader=False)
