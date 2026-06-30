#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
validate.py — строгий валидатор контракта данных недельного отчёта.

Проверяет JSON, который потом рендерят generate_report.build_report,
preview_html.render, build_email.build_html/build_text и charts.render_charts,
ДО рендера — чтобы контрактные нарушения ловились заранее, а не вырождались
в пустые ячейки, пропущенные графики или невнятный traceback при сборке .docx.

Зависимостей нет — только стандартная библиотека. jsonschema НЕ требуется,
все проверки реализованы вручную.

Публичный API:
    validate(data, history=None) -> (errors: list[str], warnings: list[str])

CLI:
    python3 validate.py --data data/week_2026-06-30.json \
        [--history data/history.json] [--strict] [--json]

Коды выхода:
    0  — ошибок нет (в --strict: нет ни ошибок, ни предупреждений)
    1  — есть errors
    2  — strict-режим и есть warnings (но нет errors)

Интеграция: вызвать validate() в начале пайплайна; при errors не рендерить.
"""

import argparse
import json
import re
import sys
from datetime import datetime, timedelta

# --------------------------------------------------------------------------- #
#  Допустимые множества значений контракта (зеркалят реальные рендереры)
# --------------------------------------------------------------------------- #
SEGMENT_IDS = ("residential", "commercial", "industrial")
CHART_SEGMENTS = ("residential", "commercial", "industrial", "overview")
DIRECTIONS = ("up", "down", "neutral")
SUBSECTION_KEYS = ("laws", "news", "trends", "stats")
# Канонические типы графиков из charts._DISPATCH (без синонимов):
CHART_TYPES = (
    "bar", "hbar", "grouped_bar", "before_after",
    "stacked_bar", "line", "donut", "kpi",
)
# Синонимы kpi из charts._DISPATCH — принимаются, но рекомендуется нормализовать:
CHART_TYPE_SYNONYMS = ("kpi_card", "kpi_cards")
# Типы графиков, требующие согласования labels <-> series[i].values:
TYPES_LABELS_EQ_VALUES = ("bar", "hbar", "grouped_bar", "stacked_bar", "donut")
# Типы, требующие >= 2 серий:
TYPES_MIN_TWO_SERIES = ("before_after", "grouped_bar")

# Мягкие лимиты (из README/agent_instructions):
MAX_ITEMS_PER_SUBSECTION = 5
KT_MIN, KT_MAX = 3, 5
MIN_TEXT_LEN = 20
MAX_HEADLINE_LEN = 200
MIN_EXEC_SUMMARY_LEN = 100

# Плейсхолдеры, которые выдают незаполненный шаблон:
URL_PLACEHOLDERS = ("https://...", "http://...", "https://", "http://",
                    "http://example", "https://example", "todo", "...", "url")

_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")

# Регэксп для эвристики «дата вне окна» в свободном тексте (DD <месяц>):
_RU_MONTHS = ("январ", "феврал", "март", "апрел", "мая", "май", "июн", "июл",
              "август", "сентябр", "октябр", "ноябр", "декабр")
_TEXT_DATE_RE = re.compile(
    r"\b(\d{1,2})\s+(" + "|".join(_RU_MONTHS) + r")\w*", re.IGNORECASE
)
_OUT_OF_WINDOW_PHRASES = ("вне окна", "buiten venster", "buiten het venster")

# Нормализатор чисел: «14,6», «€14,6 млрд», «97,8%», «150 000 м²».
_NUM_RE = re.compile(r"-?\d[\d  ]*(?:[.,]\d+)?")
_MULTIPLIERS = (
    ("млрд", 1_000_000_000),
    ("миллиард", 1_000_000_000),
    ("млн", 1_000_000),
    ("миллион", 1_000_000),
    ("тыс", 1_000),
)


# --------------------------------------------------------------------------- #
#  Внутренний сборщик проблем
# --------------------------------------------------------------------------- #
class _Issues:
    def __init__(self):
        self.errors = []
        self.warnings = []

    def err(self, path, msg):
        self.errors.append(f"{path}: {msg}")

    def warn(self, path, msg):
        self.warnings.append(f"{path}: {msg}")


# --------------------------------------------------------------------------- #
#  Помощники
# --------------------------------------------------------------------------- #
def _is_str(v):
    return isinstance(v, str)


def _nonempty_str(v):
    return isinstance(v, str) and v.strip() != ""


def _parse_date(s):
    """Строгий ISO YYYY-MM-DD -> datetime или None."""
    if not isinstance(s, str) or not _DATE_RE.match(s):
        return None
    try:
        return datetime.strptime(s, "%Y-%m-%d")
    except ValueError:
        return None


def _is_number(v):
    if isinstance(v, bool):
        return False
    if isinstance(v, (int, float)):
        return True
    if isinstance(v, str):
        try:
            float(v.replace(",", ".").strip())
            return True
        except ValueError:
            return False
    return False


def _to_float(v):
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v.replace(",", ".").strip())
        except ValueError:
            return None
    return None


def _is_valid_url(s):
    """Простейшая проверка http(s)://host без urllib-исключений."""
    if not isinstance(s, str):
        return False
    s = s.strip()
    if not (s.startswith("http://") or s.startswith("https://")):
        return False
    rest = s.split("://", 1)[1]
    host = rest.split("/", 1)[0]
    return "." in host and len(host) > 3


def _is_placeholder_url(s):
    if not isinstance(s, str):
        return False
    low = s.strip().lower()
    if low in URL_PLACEHOLDERS:
        return True
    return low.startswith("https://...") or low.startswith("http://...")


def _extract_numbers(text):
    """Все числа из строки с учётом множителей млн/млрд/тыс. -> list[float]."""
    if not isinstance(text, str):
        if isinstance(text, (int, float)) and not isinstance(text, bool):
            return [float(text)]
        return []
    out = []
    low = text.lower()
    for m in _NUM_RE.finditer(text):
        raw = m.group(0).replace(" ", " ").strip()
        # «150 000» -> 150000 ; «14,6» -> 14.6
        cleaned = raw.replace(" ", "")
        # запятая как десятичный разделитель (если ровно одна и нет точки)
        if cleaned.count(",") == 1 and "." not in cleaned:
            cleaned = cleaned.replace(",", ".")
        else:
            cleaned = cleaned.replace(",", "")
        try:
            val = float(cleaned)
        except ValueError:
            continue
        # применить множитель, если он стоит вскоре после числа
        tail = low[m.end():m.end() + 12]
        mult = 1
        for token, factor in _MULTIPLIERS:
            if token in tail:
                mult = factor
                break
        out.append(val * mult)
        if mult == 1:
            # запасной вариант без множителя уже добавлен; ничего
            pass
    return out


def _approx_in(value, pool, rel=0.01, absrel=0.05):
    """Есть ли в pool число, близкое к value (с учётом множителей и допуска)."""
    if value == 0:
        return any(abs(p) < 1e-9 for p in pool)
    for p in pool:
        if p == 0:
            continue
        # прямое совпадение с допуском
        if abs(p - value) <= max(abs(value) * absrel, 1e-9):
            return True
        # совпадение по относительной величине (млн<->млрд и т.п.)
        ratio = abs(p / value)
        for scale in (1, 1e3, 1e6, 1e9, 1e-3, 1e-6, 1e-9):
            if abs(ratio - scale) <= scale * rel + 1e-9:
                return True
    return False


# --------------------------------------------------------------------------- #
#  Проверка одного пункта (laws/news/trends)
# --------------------------------------------------------------------------- #
def _check_item(it, path, iss, win_start, win_end, is_stats=False, is_context=False):
    if not isinstance(it, dict):
        iss.err(path, "элемент подраздела должен быть объектом (dict)")
        return

    text = it.get("text")
    if not _nonempty_str(text):
        # для stats допускается metric как альтернатива text
        alt = it.get("metric") if is_stats else None
        if not (is_stats and _nonempty_str(alt)):
            iss.err(path + ".text", "обязательное непустое строковое поле text отсутствует")
    elif len(text.strip()) < MIN_TEXT_LEN:
        iss.warn(path + ".text",
                 f"очень короткий текст ({len(text.strip())} симв.) — возможно плейсхолдер")

    # direction
    direction = it.get("direction")
    if direction is not None:
        if not _is_str(direction) or direction not in DIRECTIONS:
            iss.err(path + ".direction",
                    f"direction={direction!r} вне допустимого множества {DIRECTIONS}")
    elif not is_stats:
        iss.warn(path + ".direction", "не задан direction (будет нейтральный маркер)")

    # impact
    impact = it.get("impact")
    if direction in ("up", "down") and not _nonempty_str(impact):
        iss.warn(path + ".impact",
                 "у пункта с direction up/down пустой impact (тег влияния без объяснения)")

    # source
    source = it.get("source")
    if not _nonempty_str(source):
        iss.err(path + ".source", "обязательное непустое поле source отсутствует")

    # url
    url = it.get("url")
    if is_stats:
        # для stats url по контракту опционален -> отсутствие WARNING, мусор ERROR
        if url is None or url == "":
            iss.warn(path + ".url", "у статистического пункта нет url (источник непроверяем)")
        elif not _is_valid_url(url):
            iss.err(path + ".url", f"некорректный url: {url!r} (нужен http(s)://host)")
        elif _is_placeholder_url(url):
            iss.warn(path + ".url", f"url выглядит как плейсхолдер: {url!r}")
    else:
        if not _nonempty_str(url):
            iss.err(path + ".url", "обязательное поле url отсутствует/пустое")
        elif not _is_valid_url(url):
            iss.err(path + ".url", f"некорректный url: {url!r} (нужен http(s)://host)")
        elif _is_placeholder_url(url):
            iss.warn(path + ".url", f"url выглядит как незаполненный плейсхолдер: {url!r}")

    # value (обязателен у stats — это ключевая колонка таблицы)
    if is_stats:
        value = it.get("value")
        if not _nonempty_str(value):
            iss.err(path + ".value",
                    "у статистического пункта обязательно непустое value (колонка «Значение/динамика»)")

    # date — строгий ISO + окно недели
    date = it.get("date")
    if is_stats and (date is None):
        # в schema.example у stats нет date — допустимо
        pass
    elif date is None or date == "":
        if not is_stats:
            iss.err(path + ".date", "обязательное поле date отсутствует")
        else:
            iss.warn(path + ".date", "у статистического пункта нет date")
    else:
        d = _parse_date(date)
        if d is None:
            iss.err(path + ".date",
                    f"date={date!r} не в строгом формате YYYY-MM-DD")
        elif win_start is not None and win_end is not None:
            oow = d < win_start or d > win_end
            if oow and is_context:
                # trends/stats = институциональный контекст: данные периодические
                # (индексы, yields, NTA) — допускаются вне окна с честным as_of.
                iss.warn(path + ".date",
                         f"date {date} вне окна — ОК для институционального контекста "
                         f"(trends/stats); убедитесь, что в тексте есть as-of")
            elif d < win_start:
                iss.err(path + ".date",
                        f"date {date} раньше начала окна {win_start.date()} (новости/законы — строго в окне)")
            elif d > win_end:
                iss.err(path + ".date",
                        f"date {date} позже конца окна {win_end.date()} (новости/законы — строго в окне)")

    # эвристика «упоминание даты вне окна» в свободном тексте
    if _nonempty_str(text) and win_start is not None and win_end is not None:
        low = text.lower()
        if any(ph in low for ph in _OUT_OF_WINDOW_PHRASES):
            iss.warn(path + ".text",
                     "в тексте есть фраза «вне окна» — проверьте, что событие попадает в неделю")


# --------------------------------------------------------------------------- #
#  Проверка одного графика
# --------------------------------------------------------------------------- #
def _series_values(spec):
    s = spec.get("series")
    if not isinstance(s, list):
        return []
    return s


def _check_chart(spec, path, iss, seen_ids):
    if not isinstance(spec, dict):
        iss.err(path, "график должен быть объектом (dict)")
        return

    cid = spec.get("id")
    if not _nonempty_str(cid):
        iss.err(path + ".id", "у графика нет непустого id (нужен для имени PNG)")
    else:
        if cid in seen_ids:
            iss.err(path + ".id",
                    f"дубликат chart.id={cid!r} — PNG перезапишется, один график потеряется")
        seen_ids.add(cid)

    ctype = spec.get("type")
    if not _is_str(ctype):
        iss.err(path + ".type", "у графика нет строкового type")
        return
    if ctype in CHART_TYPE_SYNONYMS:
        iss.warn(path + ".type",
                 f"тип {ctype!r} — синоним; рекомендуется нормализовать в 'kpi'")
        ctype = "kpi"
    elif ctype not in CHART_TYPES:
        iss.err(path + ".type",
                f"type={ctype!r} вне поддерживаемого множества {CHART_TYPES}")
        return

    seg = spec.get("segment")
    if not _is_str(seg) or seg not in CHART_SEGMENTS:
        iss.err(path + ".segment",
                f"segment={seg!r} вне множества {CHART_SEGMENTS}")

    unit = spec.get("unit", "")
    series = _check_series_struct(spec, path, iss)

    # --- KPI ---
    if ctype == "kpi":
        items = spec.get("kpi_items")
        if not isinstance(items, list) or not items:
            iss.err(path + ".kpi_items", "kpi-график без непустого kpi_items")
        else:
            all_zero = True
            for j, k in enumerate(items):
                kp = f"{path}.kpi_items[{j}]"
                if not isinstance(k, dict):
                    iss.err(kp, "kpi_item должен быть объектом")
                    continue
                if not _nonempty_str(k.get("label")):
                    iss.err(kp + ".label", "у kpi_item нет непустого label")
                if not _nonempty_str(str(k.get("value", "")).strip()):
                    iss.err(kp + ".value", "у kpi_item нет непустого value")
                d = k.get("direction")
                if d is not None and (not _is_str(d) or d not in DIRECTIONS):
                    iss.warn(kp + ".direction",
                             f"direction={d!r} вне {DIRECTIONS} (будет нейтральный)")
                nums = _extract_numbers(k.get("value", ""))
                if nums and any(n != 0 for n in nums):
                    all_zero = False
            if items and all_zero:
                iss.warn(path + ".kpi_items",
                         "все значения kpi нулевые/без чисел — вероятно незаполнено")
        return  # для kpi labels/series не нужны

    # --- графики с осями ---
    # series должны существовать
    if not series:
        iss.err(path + ".series", f"график type={ctype} без непустого series[0].values")

    labels = spec.get("labels")
    if not isinstance(labels, list):
        iss.err(path + ".labels", "labels должен быть списком")
        labels = []

    # min 2 серии
    if ctype in TYPES_MIN_TWO_SERIES:
        if len([s for s in series if isinstance(s, dict)]) < 2:
            iss.err(path + ".series",
                    f"type={ctype} требует минимум 2 серии (Было/Стало и т.п.)")

    # согласование labels <-> values
    if ctype in TYPES_LABELS_EQ_VALUES:
        for j, s in enumerate(series):
            if not isinstance(s, dict):
                continue
            vals = s.get("values")
            if not isinstance(vals, list):
                iss.err(f"{path}.series[{j}].values", "values должен быть списком")
                continue
            if len(labels) != len(vals):
                iss.err(f"{path}.series[{j}].values",
                        f"len(values)={len(vals)} != len(labels)={len(labels)} "
                        f"(молчаливый срез исказит график)")

    if ctype == "before_after":
        # before/after: либо две серии, либо before/after поля
        b = spec.get("before")
        a = spec.get("after")
        if b is None and a is None:
            if len(series) >= 1 and isinstance(series[0], dict):
                bvals = series[0].get("values") or []
                if bvals and _to_float(bvals[0]) == 0:
                    iss.warn(path + ".series[0]",
                             "первое значение «Было» = 0 — Δ% не будет рассчитан")

    # числовая валидность всех values
    for j, s in enumerate(series):
        if not isinstance(s, dict):
            continue
        vals = s.get("values") or []
        if not isinstance(vals, list):
            continue
        for vi, v in enumerate(vals):
            if not _is_number(v):
                iss.err(f"{path}.series[{j}].values[{vi}]",
                        f"значение {v!r} не приводится к числу")

    # benchmark
    bm = spec.get("benchmark")
    if bm is not None:
        if not isinstance(bm, dict) or not _is_number(bm.get("value")):
            iss.err(path + ".benchmark", "benchmark должен содержать числовое value")

    # highlight
    hi = spec.get("highlight")
    if hi is not None:
        if not isinstance(hi, int) or isinstance(hi, bool):
            iss.err(path + ".highlight", "highlight должен быть целым числом")
        elif not (0 <= hi < max(len(labels), 1)):
            iss.err(path + ".highlight",
                    f"highlight={hi} вне диапазона 0..{len(labels) - 1}")

    # реалистичность для % и долей
    first_vals = []
    if series and isinstance(series[0], dict):
        first_vals = [_to_float(v) for v in (series[0].get("values") or [])
                      if _to_float(v) is not None]
    if isinstance(unit, str) and unit.strip() == "%":
        for v in first_vals:
            if v < -100 or v > 100:
                iss.warn(path + ".series[0].values",
                         f"значение {v} вне диапазона [-100..100] при unit '%'")
    if ctype in ("stacked_bar", "donut"):
        for v in first_vals:
            if v < 0:
                iss.err(path + ".series[0].values",
                        "отрицательная доля недопустима для stacked_bar/donut")


def _check_series_struct(spec, path, iss):
    """Возвращает список series (как есть) после базовой проверки структуры."""
    series = spec.get("series")
    if series is None:
        return []
    if not isinstance(series, list):
        iss.err(path + ".series", "series должен быть списком")
        return []
    return series


# --------------------------------------------------------------------------- #
#  Дедуп-ключ (повторяет логику generate_report.norm_key)
# --------------------------------------------------------------------------- #
def _norm_key(item):
    """Стабильный ключ — повторяет generate_report.norm_key без импорта."""
    import hashlib
    url = (item.get("url") or "").strip().lower()
    url = re.sub(r"[#?].*$", "", url).rstrip("/")
    if url:
        return "u:" + url
    text = (item.get("text") or item.get("metric") or "").strip().lower()
    text = re.sub(r"\s+", " ", text)
    return "t:" + hashlib.sha1(text.encode("utf-8")).hexdigest()[:16]


def _iter_items(data):
    """Повторяет generate_report.iter_items: (segment_id, subsection, item)."""
    for seg in data.get("segments", []):
        if not isinstance(seg, dict):
            continue
        subs = seg.get("subsections", {})
        if isinstance(subs, dict):
            for key, items in subs.items():
                if isinstance(items, list):
                    for it in items:
                        if isinstance(it, dict):
                            yield seg.get("id"), key, it
        elif isinstance(subs, list):
            for s in subs:
                if isinstance(s, dict):
                    for it in (s.get("items") or []):
                        if isinstance(it, dict):
                            yield seg.get("id"), s.get("id"), it


# --------------------------------------------------------------------------- #
#  Основная функция валидации
# --------------------------------------------------------------------------- #
def validate(data, history=None):
    """
    Проверяет данные недельного отчёта против контракта.

    Args:
        data: распарсенный JSON (dict).
        history: опционально dict истории {items:{...}, weeks:[...]} для антиповтора.

    Returns:
        (errors: list[str], warnings: list[str])
    """
    iss = _Issues()
    try:
        _validate_impl(data, history, iss)
    except Exception as exc:  # noqa: BLE001 — валидатор не должен падать наружу
        iss.err("$", f"внутренняя ошибка валидатора (INTERNAL): {exc!r}")
    return iss.errors, iss.warnings


def _validate_impl(data, history, iss):
    # --- корень ---
    if not isinstance(data, dict):
        iss.err("$", f"корень JSON должен быть объектом, получено {type(data).__name__}")
        return

    # --- даты окна ---
    rep = data.get("report_date")
    ws = data.get("week_start")
    we = data.get("week_end")
    d_rep = _parse_date(rep)
    d_ws = _parse_date(ws)
    d_we = _parse_date(we)

    if rep is None or rep == "":
        iss.err("report_date", "обязательное поле отсутствует")
    elif d_rep is None:
        iss.err("report_date", f"некорректная дата (нужен YYYY-MM-DD): {rep!r}")

    if ws is None or ws == "":
        iss.err("week_start", "обязательное поле отсутствует")
    elif d_ws is None:
        iss.err("week_start", f"некорректная дата (нужен YYYY-MM-DD): {ws!r}")

    if we is None or we == "":
        iss.err("week_end", "обязательное поле отсутствует")
    elif d_we is None:
        iss.err("week_end", f"некорректная дата (нужен YYYY-MM-DD): {we!r}")

    if d_ws and d_we and d_ws > d_we:
        iss.err("week_start", f"week_start {ws} позже week_end {we} (границы перепутаны)")

    if d_rep and d_ws and d_we:
        if d_rep < d_ws or d_rep > d_we + timedelta(days=7):
            iss.warn("report_date",
                     f"report_date {rep} вне диапазона week_start..week_end+7 — проверьте")

    # окно для проверки пунктов
    win_start, win_end = (d_ws, d_we) if (d_ws and d_we and d_ws <= d_we) else (None, None)

    # --- language ---
    lang = data.get("language")
    if lang is None or lang == "":
        iss.warn("language", "не задан language")
    elif not _is_str(lang):
        iss.err("language", f"language должен быть строкой, получено {type(lang).__name__}")
    elif lang != "ru":
        iss.warn("language", f"language={lang!r} — ожидалось 'ru'")

    # --- headline ---
    headline = data.get("headline")
    if not _nonempty_str(headline):
        iss.err("headline", "обязательное непустое строковое поле headline отсутствует")
    elif len(headline) > MAX_HEADLINE_LEN:
        iss.warn("headline",
                 f"headline длиной {len(headline)} симв. (> {MAX_HEADLINE_LEN}) — может ломать обложку")

    # --- executive_summary ---
    summary = data.get("executive_summary")
    if not _nonempty_str(summary):
        iss.err("executive_summary", "обязательное непустое поле executive_summary отсутствует")
    elif len(summary.strip()) < MIN_EXEC_SUMMARY_LEN:
        iss.warn("executive_summary",
                 f"очень короткое summary ({len(summary.strip())} симв.) — вероятно плейсхолдер")

    # --- key_takeaways ---
    kt = data.get("key_takeaways")
    if kt is None:
        iss.warn("key_takeaways", "блок главных выводов отсутствует")
    elif not isinstance(kt, list):
        iss.err("key_takeaways", "key_takeaways должен быть списком строк")
    else:
        if len(kt) < KT_MIN or len(kt) > KT_MAX:
            iss.warn("key_takeaways",
                     f"{len(kt)} выводов — контракт требует {KT_MIN}–{KT_MAX}")
        for i, t in enumerate(kt):
            if not _nonempty_str(t):
                iss.err(f"key_takeaways[{i}]", "пустой/нестроковый главный вывод")

    # --- segments ---
    segments = data.get("segments")
    if not isinstance(segments, list) or not segments:
        iss.err("segments", "обязательный непустой список segments отсутствует")
        segments = [] if not isinstance(segments, list) else segments

    seen_seg_ids = set()
    present_seg_ids = set()
    for i, seg in enumerate(segments):
        sp = f"segments[{i}]"
        if not isinstance(seg, dict):
            iss.err(sp, "сегмент должен быть объектом (dict)")
            continue
        sid = seg.get("id")
        if not _is_str(sid) or sid not in SEGMENT_IDS:
            iss.err(sp + ".id", f"id={sid!r} вне множества {SEGMENT_IDS}")
        else:
            if sid in seen_seg_ids:
                iss.err(sp + ".id", f"дубликат segment.id={sid!r}")
            seen_seg_ids.add(sid)
            present_seg_ids.add(sid)

        if not _nonempty_str(seg.get("title")):
            iss.err(sp + ".title", "у сегмента нет непустого title")

        _check_subsections(seg, sp, iss, win_start, win_end)

        # conclusion / watch — мягкие
        concl = seg.get("conclusion")
        if not _nonempty_str(concl):
            iss.warn(sp + ".conclusion", "пустой conclusion")

    for need in SEGMENT_IDS:
        if need not in present_seg_ids:
            iss.warn("segments", f"отсутствует ожидаемый сегмент '{need}' (отчёт трёхсегментный)")

    # --- charts ---
    charts = data.get("charts")
    if charts is None:
        iss.warn("charts", "графики отсутствуют")
    elif not isinstance(charts, list):
        iss.err("charts", "charts должен быть списком")
    else:
        seen_chart_ids = set()
        for i, spec in enumerate(charts):
            _check_chart(spec, f"charts[{i}]", iss, seen_chart_ids)
        _cross_check_chart_numbers(data, charts, iss)

    # --- outlook / glossary / sources ---
    if not _nonempty_str(data.get("outlook")):
        iss.warn("outlook", "пустой outlook (картина и прогноз)")

    _check_glossary(data, iss)
    _check_sources(data, iss)

    # --- внутринедельные дубли + сверка с историей ---
    _check_dedup(data, history, iss, d_ws, d_we)


def _check_subsections(seg, sp, iss, win_start, win_end):
    subs = seg.get("subsections")
    if not isinstance(subs, dict):
        # каноническая форма — dict (preview_html и build_email умеют только dict)
        iss.err(sp + ".subsections",
                "subsections должен быть объектом (dict); list-форму не поддерживают "
                "preview_html и build_email")
        return

    any_content = False
    for key, items in subs.items():
        if key not in SUBSECTION_KEYS:
            iss.err(f"{sp}.subsections.{key}",
                    f"неизвестный ключ подраздела {key!r} (тихо исчезнет в Word/превью); "
                    f"допустимо {SUBSECTION_KEYS}")
            continue
        if items is None:
            continue
        if not isinstance(items, list):
            iss.err(f"{sp}.subsections.{key}", "значение подраздела должно быть списком")
            continue
        if items:
            any_content = True
        if len(items) > MAX_ITEMS_PER_SUBSECTION:
            iss.warn(f"{sp}.subsections.{key}",
                     f"{len(items)} пунктов (> {MAX_ITEMS_PER_SUBSECTION}) — превышен мягкий лимит")
        is_stats = (key == "stats")
        is_context = key in ("trends", "stats")  # институциональный контекст: даты с as-of допустимы
        for j, it in enumerate(items):
            _check_item(it, f"{sp}.subsections.{key}[{j}]", iss,
                        win_start, win_end, is_stats=is_stats, is_context=is_context)

    # conclusion обещает выводы, но в данных пусто во всех подразделах
    if not any_content and _nonempty_str(seg.get("conclusion")):
        iss.warn(sp,
                 "во всех подразделах пусто, но conclusion непустой — проверьте согласованность")


def _check_glossary(data, iss):
    gl = data.get("glossary")
    if gl is None:
        iss.warn("glossary", "словарь терминов отсутствует")
        return
    if not isinstance(gl, list):
        iss.err("glossary", "glossary должен быть списком")
        return
    seen_terms = set()
    # тексты для проверки «неиспользуемый термин»
    haystack = _glossary_haystack(data)
    for i, g in enumerate(gl):
        gp = f"glossary[{i}]"
        if not isinstance(g, dict):
            iss.err(gp, "элемент глоссария должен быть объектом")
            continue
        term = g.get("term")
        definition = g.get("definition")
        if not _nonempty_str(term):
            iss.err(gp + ".term", "у термина нет непустого term")
        else:
            tl = term.strip().lower()
            if tl in seen_terms:
                iss.warn(gp + ".term", f"дубликат термина {term!r}")
            seen_terms.add(tl)
            if haystack and tl not in haystack:
                iss.warn(gp + ".term",
                         f"термин {term!r} не встречается в текстах отчёта (кандидат на удаление)")
        if not _nonempty_str(definition):
            iss.err(gp + ".definition", f"у термина {term!r} нет непустого definition")


def _glossary_haystack(data):
    parts = []
    for _sid, _sub, it in _iter_items(data):
        for fld in ("text", "impact", "value"):
            v = it.get(fld)
            if isinstance(v, str):
                parts.append(v)
    for seg in data.get("segments", []):
        if isinstance(seg, dict):
            for fld in ("conclusion", "watch"):
                v = seg.get(fld)
                if isinstance(v, str):
                    parts.append(v)
    for fld in ("outlook", "executive_summary", "headline"):
        v = data.get(fld)
        if isinstance(v, str):
            parts.append(v)
    for t in (data.get("key_takeaways") or []):
        if isinstance(t, str):
            parts.append(t)
    return " ".join(parts).lower()


def _domain(url):
    if not isinstance(url, str) or "://" not in url:
        return ""
    return url.split("://", 1)[1].split("/", 1)[0].lower()


def _check_sources(data, iss):
    sources = data.get("sources")
    if sources is None:
        iss.warn("sources", "список источников отсутствует")
        return
    if not isinstance(sources, list):
        iss.err("sources", "sources должен быть списком")
        return
    src_domains = set()
    for i, s in enumerate(sources):
        spp = f"sources[{i}]"
        if not isinstance(s, dict):
            iss.err(spp, "элемент sources должен быть объектом")
            continue
        if not _nonempty_str(s.get("name")):
            iss.warn(spp + ".name", "у источника нет непустого name")
        url = s.get("url")
        if not _nonempty_str(url):
            iss.warn(spp + ".url", "у источника нет url")
        elif not _is_valid_url(url):
            iss.warn(spp + ".url", f"некорректный url источника: {url!r}")
        elif _is_placeholder_url(url):
            iss.warn(spp + ".url", f"url источника — плейсхолдер: {url!r}")
        else:
            src_domains.add(_domain(url))

    # url пунктов, чей домен не отражён в списке источников
    if src_domains:
        for sid, sub, it in _iter_items(data):
            u = it.get("url")
            if _is_valid_url(u):
                dom = _domain(u)
                if dom and dom not in src_domains:
                    iss.warn("sources",
                             f"домен {dom} (пункт сегмента {sid}/{sub}) "
                             f"не представлен в списке источников")
                    # одно предупреждение на домен достаточно
                    src_domains.add(dom)


def _cross_check_chart_numbers(data, charts, iss):
    """Эвристика: числа графика подтверждаются числами пунктов того же сегмента."""
    # числа по сегментам из пунктов
    seg_numbers = {}
    for sid, _sub, it in _iter_items(data):
        pool = seg_numbers.setdefault(sid, [])
        for fld in ("value", "text"):
            pool.extend(_extract_numbers(it.get(fld, "")))

    for i, spec in enumerate(charts):
        if not isinstance(spec, dict):
            continue
        seg = spec.get("segment")
        if seg == "overview" or seg not in seg_numbers:
            continue
        pool = seg_numbers.get(seg, [])
        if not pool:
            continue
        chart_nums = []
        for s in (spec.get("series") or []):
            if isinstance(s, dict):
                for v in (s.get("values") or []):
                    f = _to_float(v)
                    if f is not None:
                        chart_nums.append(f)
        bm = spec.get("benchmark")
        if isinstance(bm, dict):
            f = _to_float(bm.get("value"))
            if f is not None:
                chart_nums.append(f)
        for cn in chart_nums:
            if cn == 0:
                continue
            if not _approx_in(cn, pool):
                iss.warn(f"charts[{i}]",
                         f"число графика {cn:g} не подтверждается ни одним пунктом "
                         f"сегмента '{seg}' — проверьте согласованность")


def _check_dedup(data, history, iss, d_ws, d_we):
    # внутринедельные дубли
    seen = {}
    for sid, sub, it in _iter_items(data):
        k = _norm_key(it)
        if k.startswith("t:"):
            iss.warn(f"segments/{sid}/{sub}",
                     "пункт без url — антиповтор по тексту нестабилен (добавьте url)")
        if k in seen:
            iss.err(f"segments/{sid}/{sub}",
                    f"внутринедельный дубль материала (ключ {k}) — уже встречается в "
                    f"{seen[k]}")
        else:
            seen[k] = f"{sid}/{sub}"

    if history is None:
        return
    if not isinstance(history, dict):
        iss.warn("history", "история не является объектом — сверка пропущена")
        return

    hist_items = history.get("items") or {}
    if isinstance(hist_items, dict):
        for sid, sub, it in _iter_items(data):
            k = _norm_key(it)
            if k in hist_items:
                when = hist_items[k].get("date", "?") if isinstance(hist_items[k], dict) else "?"
                iss.warn(f"segments/{sid}/{sub}",
                         f"материал уже был в прошлом отчёте ({when}) — замените свежим (ключ {k})")

    # повторный прогон той же недели
    weeks = history.get("weeks") or []
    if isinstance(weeks, list) and d_ws and d_we:
        for w in weeks:
            if isinstance(w, dict) and w.get("week_start") == data.get("week_start") \
                    and w.get("week_end") == data.get("week_end"):
                iss.warn("week_start",
                         "эта неделя уже присутствует в history.weeks — повторный прогон "
                         "приведёт к двойной записи истории")
                break


# --------------------------------------------------------------------------- #
#  Загрузка JSON с детектом дублей ключей и понятными ошибками
# --------------------------------------------------------------------------- #
def _dup_key_hook(pairs):
    """object_pairs_hook: ловит дублирующиеся ключи в одном объекте."""
    seen = {}
    for k, v in pairs:
        if k in seen:
            raise ValueError(f"дублирующийся ключ {k!r} в одном JSON-объекте")
        seen[k] = v
    return seen


def load_json_file(path):
    """
    Читает и парсит JSON-файл. Возвращает (data, error_str|None).
    Никогда не бросает исключение наружу.
    """
    try:
        with open(path, "r", encoding="utf-8") as f:
            raw = f.read()
    except FileNotFoundError:
        return None, f"файл не найден: {path}"
    except OSError as exc:
        return None, f"не удалось прочитать {path}: {exc}"

    if raw.startswith("﻿"):
        raw = raw.lstrip("﻿")

    try:
        data = json.loads(raw, object_pairs_hook=_dup_key_hook)
    except ValueError as exc:
        return None, f"ошибка разбора JSON в {path}: {exc}"
    return data, None


# --------------------------------------------------------------------------- #
#  CLI
# --------------------------------------------------------------------------- #
def _print_report(data_path, errors, warnings, as_json, strict):
    if as_json:
        report = {
            "data": data_path,
            "ok": not errors and not (strict and warnings),
            "errors": errors,
            "warnings": warnings,
            "error_count": len(errors),
            "warning_count": len(warnings),
            "strict": strict,
        }
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return

    print(f"Валидация: {data_path}")
    print("-" * 60)
    if errors:
        print(f"ОШИБКИ ({len(errors)}):")
        for e in errors:
            print(f"  [ERROR] {e}")
    if warnings:
        print(f"ПРЕДУПРЕЖДЕНИЯ ({len(warnings)}):")
        for w in warnings:
            print(f"  [WARN]  {w}")
    print("-" * 60)
    if not errors and not warnings:
        print("OK — нарушений контракта не найдено.")
    elif not errors:
        print(f"OK с предупреждениями: 0 ошибок, {len(warnings)} предупреждений.")
        if strict:
            print("strict-режим: предупреждения трактуются как ошибки (код 2).")
    else:
        print(f"НЕ ПРОШЛО: {len(errors)} ошибок, {len(warnings)} предупреждений.")


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Валидатор контракта данных недельного отчёта (без внешних зависимостей)."
    )
    ap.add_argument("--data", required=True, help="JSON с данными недели")
    ap.add_argument("--history", default=None, help="history.json для антиповтора (read-only)")
    ap.add_argument("--strict", action="store_true",
                    help="трактовать предупреждения как ошибки (код выхода 2)")
    ap.add_argument("--json", dest="as_json", action="store_true",
                    help="машиночитаемый JSON-отчёт")
    args = ap.parse_args(argv)

    data, err = load_json_file(args.data)
    if err is not None:
        if args.as_json:
            print(json.dumps(
                {"data": args.data, "ok": False, "errors": [err], "warnings": [],
                 "error_count": 1, "warning_count": 0, "strict": args.strict},
                ensure_ascii=False, indent=2))
        else:
            print(f"[ERROR] {err}")
        return 1

    history = None
    if args.history:
        history, herr = load_json_file(args.history)
        if herr is not None:
            # история — вспомогательный вход; не валим прогон, но предупреждаем
            history = None
            if not args.as_json:
                print(f"[WARN] история недоступна, сверка пропущена: {herr}")

    errors, warnings = validate(data, history)
    _print_report(args.data, errors, warnings, args.as_json, args.strict)

    if errors:
        return 1
    if args.strict and warnings:
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())