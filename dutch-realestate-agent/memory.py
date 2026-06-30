#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
memory.py — слой ПАМЯТИ И ФОРСАЙТА агента (только stdlib).

Зачем это умение (через исходную задачу):
  Читатель-собственник за несколько минут хочет понять «что происходит,
  почему важно мне, что делать / за чем следить». Одноразовый дайджест на
  это отвечает плохо: он не помнит прошлой недели. Этот модуль добавляет
  агенту память между выпусками и форвард-взгляд — ровно то, что инструкция
  («мозг») требует, но что негде хранить:

    • §2.4 Трендовая память — KPI как ВРЕМЕННОЙ РЯД (неделя-к-неделе),
      из которого сам собой рисуется график type=line «динамика».
    • §1.2 / §2.8 Сюжеты в развитии — не просто «без повторов», а умные
      ПРОДОЛЖЕНИЯ историй со статусом и связкой с прошлым выпуском.
    • §2.7 Форвард-календарь и триггеры — ближайшие развилки (дедлайны
      сделок, релизы CBS, голосования, отчётности REIT) для блока outlook.

Контракт данных (всё ОПЦИОНАЛЬНО — backward-compatible):
  В недельный data-файл можно добавить три блока. Если их нет — состояние не
  меняется, а отчёт собирается ровно как раньше.

  data["metrics"]  : [
      {"key": "koopbereidheid", "value": -22, "unit": "index",
       "label": "Готовность к покупкам (CBS)", "segment": "commercial",
       "source": "CBS"},
      ...
  ]
  data["threads"]  : [
      {"id": "prologis_segro", "title": "Prologis торгуется за Segro",
       "segment": "industrial", "status": "open",   # open|developing|resolved|...
       "update": "Совет Segro отклонил оферту как заниженную.",
       "next_trigger": {"date": "2026-07-22", "what": "дедлайн по оферте"}},
      ...
  ]
  data["calendar"] : [
      {"date": "2026-07-22", "what": "Prologis: подтвердить намерение по Segro",
       "segment": "industrial", "source": "Takeover Panel",
       "thread_id": "prologis_segro", "impact": "репер для оценок логистики NL"},
      ...
  ]

Состояние (data/state/):
  metrics.json   — {key: {"unit","label","segment","points":[
                     {"week_end","value","source"}]}}   (временной ряд)
  threads.json   — {id: {"title","segment","status","first_seen","last_update",
                     "next_trigger", "history":[{"week_end","status","update"}]}}
  calendar.json  — {"events":[{...}]}  (будущие события; прошедшие выкидываются
                     относительно week_end текущего выпуска)

Публичный API:
  update_state(data, state_dir='data/state') -> dict(state)   # идемпотентно по week_end
  load_state(state_dir='data/state') -> dict
  trend_chart_specs(metrics_store, current_week_end, min_points=3) -> [chart-spec]
  upcoming(calendar_store, from_date, horizon_days=42) -> [event]
  active_threads(threads_store) -> [thread]

Зависимости: только стандартная библиотека. Даты — ISO (YYYY-MM-DD).
Ничего не роняет: пустые/битые файлы и кривые поля деградируют мягко.
"""

import json
import os
from datetime import date, datetime, timedelta

DEFAULT_STATE_DIR = "data/state"

METRICS_FILE = "metrics.json"
THREADS_FILE = "threads.json"
CALENDAR_FILE = "calendar.json"

# Палитра сегментов — синхронно с charts.py (цвет несёт смысл, не радугу).
SEGMENT_COLOR = {
    "residential": "#16846F",
    "commercial": "#C0791C",
    "industrial": "#2C5F8A",
    "overview": "#1F3A5F",
}

# Статусы сюжетов, которые считаем «закрытыми» (в active_threads не попадают).
RESOLVED_STATUSES = {"resolved", "closed", "done", "archived"}


# --------------------------------------------------------------------------- #
#  Низкоуровневые хелперы (никогда не падают)
# --------------------------------------------------------------------------- #
def _read_json(path, default):
    """Прочитать JSON; вернуть default на любой беде (нет файла / битый / не тот тип)."""
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError, UnicodeDecodeError):
        return _copy_default(default)
    if default is not None and not isinstance(data, type(default)):
        return _copy_default(default)
    return data


def _copy_default(default):
    if isinstance(default, dict):
        return {}
    if isinstance(default, list):
        return []
    return default


def _write_json(path, obj):
    """Атомарно записать JSON (через .tmp + replace), создав каталог при нужде."""
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, sort_keys=False)
    os.replace(tmp, path)


def _as_list(x):
    """Привести значение к списку: None → [], скаляр → [скаляр], список как есть."""
    if x is None:
        return []
    if isinstance(x, list):
        return x
    return [x]


def _parse_date(s):
    """ISO-строка/date/datetime → date или None. Терпит дату с временем и хвостом."""
    if isinstance(s, date) and not isinstance(s, datetime):
        return s
    if isinstance(s, datetime):
        return s.date()
    if not isinstance(s, str):
        return None
    s = s.strip()
    if not s:
        return None
    # Срезаем время, если оно есть: "2026-07-22T09:00" / "2026-07-22 09:00".
    head = s.replace("T", " ").split(" ", 1)[0]
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%d.%m.%Y"):
        try:
            return datetime.strptime(head, fmt).date()
        except ValueError:
            continue
    return None


def _num(v):
    """Привести к float, если возможно (числа в графике должны быть числами)."""
    if isinstance(v, bool):  # bool — подкласс int, но не метрика
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        t = v.strip().replace(" ", "").replace(" ", "").replace(",", ".")
        try:
            return float(t)
        except ValueError:
            return None
    return None


def _week_end_of(data):
    """Извлечь week_end текущего выпуска (с разумными фолбэками)."""
    if not isinstance(data, dict):
        return None
    for k in ("week_end", "report_date"):
        d = _parse_date(data.get(k))
        if d:
            return d.isoformat()
    return None


# --------------------------------------------------------------------------- #
#  update_state — впитать недельные блоки в постоянное состояние
# --------------------------------------------------------------------------- #
def update_state(data, state_dir=DEFAULT_STATE_DIR):
    """
    Обновить data/state/{metrics,threads,calendar}.json блоками текущего выпуска.

    Идемпотентно по week_end: повторный прогон того же выпуска не плодит дублей
    (точки метрик/записи истории сюжетов с тем же week_end перезаписываются на
    месте, прошедшие события календаря отсекаются один раз).

    Возвращает свежее состояние. Никогда не бросает исключение из-за кривых
    данных — некорректные элементы тихо пропускаются.
    """
    if not isinstance(data, dict):
        data = {}

    week_end = _week_end_of(data) or date.today().isoformat()

    metrics_path = os.path.join(state_dir, METRICS_FILE)
    threads_path = os.path.join(state_dir, THREADS_FILE)
    calendar_path = os.path.join(state_dir, CALENDAR_FILE)

    metrics_store = _read_json(metrics_path, {})
    threads_store = _read_json(threads_path, {})
    calendar_store = _read_json(calendar_path, {})
    if not isinstance(calendar_store, dict):
        calendar_store = {}
    calendar_store.setdefault("events", [])

    _update_metrics(metrics_store, _as_list(data.get("metrics")), week_end)
    _update_threads(threads_store, _as_list(data.get("threads")), week_end)
    _update_calendar(calendar_store, _as_list(data.get("calendar")), week_end)

    _write_json(metrics_path, metrics_store)
    _write_json(threads_path, threads_store)
    _write_json(calendar_path, calendar_store)

    return {
        "metrics": metrics_store,
        "threads": threads_store,
        "calendar": calendar_store,
    }


def _update_metrics(store, items, week_end):
    """Дописать точку временного ряда каждому KPI (идемпотентно по week_end)."""
    for it in items:
        if not isinstance(it, dict):
            continue
        key = (it.get("key") or it.get("id") or "").strip()
        if not key:
            continue
        val = _num(it.get("value"))
        if val is None:
            continue
        entry = store.get(key)
        if not isinstance(entry, dict):
            entry = {"unit": "", "label": key, "segment": "overview", "points": []}
            store[key] = entry
        # Метаданные обновляем последними виденными значениями (не теряя старых).
        if it.get("unit"):
            entry["unit"] = str(it["unit"])
        if it.get("label"):
            entry["label"] = str(it["label"])
        if it.get("segment"):
            entry["segment"] = str(it["segment"])
        if not isinstance(entry.get("points"), list):
            entry["points"] = []

        point = {
            "week_end": week_end,
            "value": val,
            "source": str(it.get("source") or ""),
        }
        pts = entry["points"]
        # Идемпотентность: точка с тем же week_end перезаписывается на месте.
        for i, p in enumerate(pts):
            if isinstance(p, dict) and p.get("week_end") == week_end:
                pts[i] = point
                break
        else:
            pts.append(point)
        # Держим ряд хронологически отсортированным.
        pts.sort(key=lambda p: _parse_date(p.get("week_end")) or date.min)


def _update_threads(store, items, week_end):
    """Завести/продвинуть сюжеты: статус, first_seen/last_update, next_trigger, история."""
    for it in items:
        if not isinstance(it, dict):
            continue
        tid = (it.get("id") or "").strip()
        if not tid:
            continue
        thread = store.get(tid)
        if not isinstance(thread, dict):
            thread = {
                "title": "",
                "segment": "overview",
                "status": "open",
                "first_seen": week_end,
                "last_update": week_end,
                "next_trigger": None,
                "history": [],
            }
            store[tid] = thread

        if it.get("title"):
            thread["title"] = str(it["title"])
        if it.get("segment"):
            thread["segment"] = str(it["segment"])
        status = str(it.get("status") or thread.get("status") or "open").strip().lower()
        thread["status"] = status
        thread["last_update"] = week_end
        if not thread.get("first_seen"):
            thread["first_seen"] = week_end

        nt = it.get("next_trigger")
        if isinstance(nt, dict) and (nt.get("date") or nt.get("what")):
            thread["next_trigger"] = {
                "date": str(nt.get("date") or ""),
                "what": str(nt.get("what") or ""),
            }
        elif "next_trigger" in it and not nt:
            # Явно переданный пустой триггер сбрасывает прошлый.
            thread["next_trigger"] = None

        if not isinstance(thread.get("history"), list):
            thread["history"] = []
        record = {
            "week_end": week_end,
            "status": status,
            "update": str(it.get("update") or ""),
        }
        hist = thread["history"]
        for i, h in enumerate(hist):
            if isinstance(h, dict) and h.get("week_end") == week_end:
                hist[i] = record  # идемпотентность по week_end
                break
        else:
            hist.append(record)
        hist.sort(key=lambda h: _parse_date(h.get("week_end")) or date.min)


def _update_calendar(store, items, week_end):
    """Добавить будущие события и выкинуть прошедшие относительно week_end."""
    cutoff = _parse_date(week_end) or date.today()
    events = store.get("events")
    if not isinstance(events, list):
        events = []

    # Индекс существующих по (date, what) — чтобы не плодить дубли при повторе.
    def _ekey(e):
        return (str(e.get("date") or ""), (str(e.get("what") or "")).strip().lower())

    index = {_ekey(e): i for i, e in enumerate(events) if isinstance(e, dict)}

    for it in items:
        if not isinstance(it, dict):
            continue
        ev = {
            "date": str(it.get("date") or ""),
            "what": str(it.get("what") or ""),
            "segment": str(it.get("segment") or "overview"),
            "source": str(it.get("source") or ""),
            "thread_id": str(it.get("thread_id") or ""),
            "impact": str(it.get("impact") or ""),
            "added_week": week_end,
        }
        if not (ev["date"] or ev["what"]):
            continue
        k = _ekey(ev)
        if k in index:
            events[index[k]] = ev  # обновить существующее
        else:
            index[k] = len(events)
            events.append(ev)

    # Чистка прошедшего: оставляем события строго в будущем или без даты.
    kept = []
    for e in events:
        if not isinstance(e, dict):
            continue
        d = _parse_date(e.get("date"))
        if d is None or d >= cutoff:
            kept.append(e)
    kept.sort(key=lambda e: _parse_date(e.get("date")) or date.max)
    store["events"] = kept


# --------------------------------------------------------------------------- #
#  load_state
# --------------------------------------------------------------------------- #
def load_state(state_dir=DEFAULT_STATE_DIR):
    """Загрузить всё состояние. Отсутствующие/битые файлы → пустые структуры."""
    calendar = _read_json(os.path.join(state_dir, CALENDAR_FILE), {})
    if not isinstance(calendar, dict):
        calendar = {}
    calendar.setdefault("events", [])
    return {
        "metrics": _read_json(os.path.join(state_dir, METRICS_FILE), {}),
        "threads": _read_json(os.path.join(state_dir, THREADS_FILE), {}),
        "calendar": calendar,
    }


# --------------------------------------------------------------------------- #
#  trend_chart_specs — авто-линии динамики из памяти KPI
# --------------------------------------------------------------------------- #
def trend_chart_specs(metrics_store, current_week_end, min_points=3):
    """
    Построить спецификации графиков type=line для каждого KPI, у которого
    накопилось >= min_points точек (включая текущую неделю).

    Формат каждой спеки — ровно как ждёт charts.py / generate_report.py:
        {id, segment, type:"line", title, caption, unit,
         labels=[недели], series=[{name, values}], source}

    Сортировка результата — по сегменту, затем по id (детерминизм).
    Никогда не падает: некорректные ряды/точки пропускаются.
    """
    if not isinstance(metrics_store, dict):
        return []
    try:
        min_points = max(2, int(min_points))
    except (TypeError, ValueError):
        min_points = 3

    cur = _parse_date(current_week_end)

    specs = []
    for key, entry in metrics_store.items():
        if not isinstance(entry, dict):
            continue
        pts = entry.get("points")
        if not isinstance(pts, list):
            continue

        clean = []
        for p in pts:
            if not isinstance(p, dict):
                continue
            d = _parse_date(p.get("week_end"))
            v = _num(p.get("value"))
            if d is None or v is None:
                continue
            # Не заглядываем в будущее относительно текущего выпуска.
            if cur is not None and d > cur:
                continue
            clean.append((d, v, str(p.get("source") or "")))
        if len(clean) < min_points:
            continue
        clean.sort(key=lambda t: t[0])

        labels = [d.strftime("%d.%m") for d, _v, _s in clean]
        values = [v for _d, v, _s in clean]
        unit = str(entry.get("unit") or "")
        label = str(entry.get("label") or key)
        segment = str(entry.get("segment") or "overview")
        if segment not in SEGMENT_COLOR:
            segment = "overview"
        source = next((s for _d, _v, s in reversed(clean) if s), "")

        first_v, last_v = values[0], values[-1]
        delta = last_v - first_v
        if delta > 0:
            move = "растёт"
        elif delta < 0:
            move = "снижается"
        else:
            move = "без изменений"
        title = f"{label}: {move} {len(values)}-ю неделю"
        caption = (
            f"Динамика по неделям: {_fmt_ru(first_v)} → {_fmt_ru(last_v)}"
            + (f" {unit}".rstrip() if unit else "")
            + " — следите за направлением."
        )

        specs.append({
            "id": f"trend_{_slug(key)}",
            "segment": segment,
            "type": "line",
            "title": title,
            "caption": caption,
            "unit": unit,
            "labels": labels,
            "series": [{"name": label, "values": values}],
            "source": source,
        })

    specs.sort(key=lambda s: (s.get("segment", ""), s.get("id", "")))
    return specs


def _fmt_ru(v):
    """Лёгкое русское форматирование числа для caption (запятая-десятичный)."""
    try:
        f = float(v)
    except (TypeError, ValueError):
        return str(v)
    if f == int(f):
        return f"{int(f)}"
    return f"{f:.1f}".replace(".", ",")


def _slug(s):
    """Безопасный слаг для id графика/имени файла."""
    out = []
    for ch in str(s).strip().lower():
        if ch.isalnum():
            out.append(ch)
        elif ch in " -_/.":
            out.append("_")
    slug = "".join(out).strip("_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    return slug or "metric"


# --------------------------------------------------------------------------- #
#  upcoming — форвард-календарь / вотчлист
# --------------------------------------------------------------------------- #
def upcoming(calendar_store, from_date, horizon_days=42):
    """
    Ближайшие события календаря в окне [from_date, from_date + horizon_days].

    События без даты не попадают (нечего ставить на форвард-шкалу). Результат
    отсортирован по дате (раньше → позже). Каждому событию добавляется
    производное поле days_until (целое, >= 0).
    """
    if isinstance(calendar_store, dict):
        events = calendar_store.get("events")
    elif isinstance(calendar_store, list):
        events = calendar_store
    else:
        events = []
    if not isinstance(events, list):
        return []

    start = _parse_date(from_date)
    if start is None:
        start = date.today()
    try:
        horizon_days = int(horizon_days)
    except (TypeError, ValueError):
        horizon_days = 42
    end = start + timedelta(days=horizon_days)

    out = []
    for e in events:
        if not isinstance(e, dict):
            continue
        d = _parse_date(e.get("date"))
        if d is None or d < start or d > end:
            continue
        ev = dict(e)
        ev["days_until"] = (d - start).days
        out.append(ev)
    out.sort(key=lambda e: _parse_date(e.get("date")) or date.max)
    return out


# --------------------------------------------------------------------------- #
#  active_threads — сюжеты в развитии
# --------------------------------------------------------------------------- #
def active_threads(threads_store):
    """
    Незавершённые сюжеты (status не в RESOLVED_STATUSES), отсортированные по
    дате последнего обновления (свежие — первыми). id вшивается в каждую запись.
    """
    if not isinstance(threads_store, dict):
        return []
    out = []
    for tid, thread in threads_store.items():
        if not isinstance(thread, dict):
            continue
        status = str(thread.get("status") or "open").strip().lower()
        if status in RESOLVED_STATUSES:
            continue
        item = dict(thread)
        item["id"] = tid
        item["status"] = status
        out.append(item)
    out.sort(
        key=lambda t: (_parse_date(t.get("last_update")) or date.min, t.get("id", "")),
        reverse=True,
    )
    return out


# --------------------------------------------------------------------------- #
#  Самотест
# --------------------------------------------------------------------------- #
if __name__ == "__main__":
    import tempfile

    sd = tempfile.mkdtemp(prefix="memory_state_")

    week1 = {
        "week_end": "2026-06-16",
        "metrics": [
            {"key": "koopbereidheid", "value": -28, "unit": "index",
             "label": "Готовность к покупкам (CBS)", "segment": "commercial",
             "source": "CBS"},
        ],
        "threads": [
            {"id": "prologis_segro", "title": "Prologis торгуется за Segro",
             "segment": "industrial", "status": "open",
             "update": "Появились слухи об оферте.",
             "next_trigger": {"date": "2026-07-22", "what": "дедлайн по оферте"}},
        ],
        "calendar": [
            {"date": "2026-07-22", "what": "Prologis: подтвердить намерение по Segro",
             "segment": "industrial", "source": "Takeover Panel",
             "thread_id": "prologis_segro"},
            {"date": "2026-06-10", "what": "Прошедшее событие (должно отсеяться)"},
        ],
    }
    week2 = {
        "week_end": "2026-06-23",
        "metrics": [{"key": "koopbereidheid", "value": -25, "unit": "index",
                     "label": "Готовность к покупкам (CBS)", "segment": "commercial",
                     "source": "CBS"}],
    }
    week3 = {
        "week_end": "2026-06-30",
        "metrics": [{"key": "koopbereidheid", "value": -22, "unit": "index",
                     "label": "Готовность к покупкам (CBS)", "segment": "commercial",
                     "source": "CBS"}],
        "threads": [{"id": "prologis_segro", "status": "developing",
                     "update": "Совет Segro отклонил оферту как заниженную."}],
    }

    update_state(week1, sd)
    update_state(week2, sd)
    update_state(week1, sd)  # повтор — проверка идемпотентности
    st = update_state(week3, sd)

    specs = trend_chart_specs(st["metrics"], "2026-06-30", min_points=3)
    print("trend specs:", json.dumps(specs, ensure_ascii=False, indent=2))

    up = upcoming(st["calendar"], "2026-06-30", horizon_days=42)
    print("upcoming:", json.dumps(up, ensure_ascii=False, indent=2))

    act = active_threads(st["threads"])
    print("active threads:", json.dumps(act, ensure_ascii=False, indent=2))

    # Идемпотентность ряда: 3 недели → 3 точки, не больше.
    pts = st["metrics"]["koopbereidheid"]["points"]
    assert len(pts) == 3, f"ожидали 3 точки, получили {len(pts)}"
    assert len(specs) == 1 and specs[0]["type"] == "line"
    assert specs[0]["series"][0]["values"] == [-28.0, -25.0, -22.0]
    assert len(up) == 1 and up[0]["days_until"] == 22
    assert act and act[0]["status"] == "developing"
    print("OK self-test passed; state dir:", sd)