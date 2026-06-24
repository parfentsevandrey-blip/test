"""
Конвейер: собрать → проанализировать → сохранить → посчитать барометр.

run_pipeline()  — один полный цикл (вызывается планировщиком и кнопкой «Обновить»).
get_state()     — собирает текущее состояние для фронтенда из хранилища.
"""

from __future__ import annotations

import threading

import config
from core import analyze, llm, scoring, sources, store

_RUN_LOCK = threading.Lock()
_LAST_RESULT: dict = {"running": False, "last_run": None, "last_error": None}


def status() -> dict:
    return dict(_LAST_RESULT)


def run_pipeline(use_llm: bool = True) -> dict:
    """Полный цикл сбора и пересчёта. Возвращает актуальное состояние."""
    if not _RUN_LOCK.acquire(blocking=False):
        return get_state(extra={"note": "Обновление уже выполняется"})
    _LAST_RESULT["running"] = True
    try:
        store.init_db()

        collected = sources.collect_all()
        items = analyze.analyze_items(collected["items"])
        store.upsert_items(items)

        ds_snap = store.add_deepstate(collected["deepstate"])

        statuses = collected["statuses"]
        for st in statuses:
            store.set_source_status(st)
        ok = sum(1 for s in statuses if s.get("mode") != "error")
        ratio = ok / len(statuses) if statuses else 1.0

        window_items = store.recent_items(config.WINDOW_DAYS, only_relevant=True, limit=4000)
        history = store.reading_history(limit=365)

        llm_result = llm.analyze_with_claude(window_items) if use_llm else None

        reading = scoring.compute_reading(
            window_items, ds_snap, history, llm=llm_result, sources_ok_ratio=ratio
        )

        # Сохраняем показание (компоненты обогащаем для самодостаточности истории).
        to_store = dict(reading)
        to_store["components"] = {
            **reading["components"],
            "streams": reading["streams"],
            "forecast": reading["forecast"],
            "zone": reading["zone"],
            "llm": reading.get("llm"),
        }
        to_store["sources"] = [
            {"name": s["name"], "stream": s["stream"], "mode": s["mode"],
             "items": s.get("items_count", 0)}
            for s in statuses
        ]
        store.add_reading(to_store)

        _LAST_RESULT["last_run"] = reading["taken_at"]
        _LAST_RESULT["last_error"] = None
        return get_state()
    except Exception as e:  # noqa: BLE001
        _LAST_RESULT["last_error"] = f"{type(e).__name__}: {e}"
        return get_state(extra={"error": _LAST_RESULT["last_error"]})
    finally:
        _LAST_RESULT["running"] = False
        _RUN_LOCK.release()


def get_state(extra: dict | None = None) -> dict:
    store.init_db()
    reading = store.last_reading()
    if reading:
        # Поднимаем удобные поля из components на верхний уровень reading,
        # чтобы у фронтенда был единый формат (как в compute_reading).
        comp = reading.get("components") or {}
        reading["zone"] = comp.get("zone")
        reading["forecast"] = comp.get("forecast") or {}
        reading["streams"] = comp.get("streams") or []
        reading["llm"] = comp.get("llm")
    statuses = store.all_source_status()
    deepstate = store.last_deepstate()
    hist = store.reading_history(limit=180)

    history_series = [
        {"t": h["taken_at"], "v": h["final_barometer"]}
        for h in hist
        if h.get("final_barometer") is not None
    ]

    feed = []
    for it in store.recent_items(config.WINDOW_DAYS, only_relevant=True, limit=60):
        terms = []
        seen = set()
        for s in it.get("signals", []):
            t = s["term"]
            if t not in seen:
                seen.add(t)
                terms.append({"term": t, "polarity": s["polarity"]})
        feed.append(
            {
                "title": it.get("title", ""),
                "url": it.get("url", ""),
                "source": it.get("source_name", ""),
                "stream": it.get("stream", "media"),
                "published": it.get("published", ""),
                "terms": terms[:6],
            }
        )

    state: dict = {
        "status": "ok" if reading else "empty",
        "generated_at": reading["taken_at"] if reading else None,
        "pipeline": status(),
        "reading": reading,
        "deepstate": deepstate,
        "sources": statuses,
        "history": history_series,
        "feed": feed,
        "config": {
            "window_days": config.WINDOW_DAYS,
            "refresh_minutes": config.REFRESH_MINUTES,
            "llm_enabled": llm.is_enabled(),
            "x_live": bool(config.X_BEARER_TOKEN),
        },
    }
    if extra:
        state.update(extra)
    return state
