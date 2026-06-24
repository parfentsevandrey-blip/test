"""
Скоринг барометра 0–100 и прогноз даты.

Модель (прозрачная и настраиваемая в config.py):

  1. По каждой релевантной новости в окне берём её сигналы и складываем их с
     учётом «свежести» (полураспад HALFLIFE_DAYS) и доверия к источнику.
  2. Для каждой категории c получаем суммарный сигнал и нормируем его в [-1,1]:
        norm[c] = s / (|s| + scale[c]).
     DeepState нормируется отдельно из суточного изменения площади оккупации
     (отступление → давление вверх).
  3. Композит X = Σ weight[c] · norm[c]  (веса категорий в сумме = 1).
  4. Барометр = 100 · sigmoid(K·X + B): фон ≈ 19, максимум ≈ 89.
  5. Прогноз даты — экстраполяция по скорости роста барометра (история показаний).

Это эвристический OSINT-индикатор, а не предсказание с гарантией.
"""

from __future__ import annotations

import math
from datetime import datetime, timedelta, timezone

import config

STREAM_LABELS = {
    "media": "Независимые СМИ",
    "deepstate": "DeepState (фронт)",
    "analysts": "Аналитики и соцсети",
    "raids": "Облавы и бусификация",
}


def _sigmoid(z: float) -> float:
    if z < -60:
        return 0.0
    if z > 60:
        return 1.0
    return 1.0 / (1.0 + math.exp(-z))


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _parse_dt(s: str | None) -> datetime | None:
    if not s:
        return None
    s = s.strip().replace("T", " ").replace("Z", "")
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return datetime.strptime(s[: len(fmt) + 2].strip(), fmt)
        except Exception:
            continue
    try:
        return datetime.fromisoformat(s)
    except Exception:
        return None


def _decay(age_days: float) -> float:
    age_days = max(age_days, 0.0)
    return 0.5 ** (age_days / config.HALFLIFE_DAYS)


# --------------------------------------------------------------------------- #
#  DeepState → нормированный сигнал                                            #
# --------------------------------------------------------------------------- #
def deepstate_norm(snap: dict | None) -> tuple[float, dict]:
    info = {"available": False, "delta_km2": None, "occupied_km2": None, "trend": "нет данных"}
    if not snap or snap.get("status") != "ok":
        return 0.0, info
    info["available"] = True
    info["occupied_km2"] = snap.get("occupied_km2")
    delta = snap.get("delta_km2")
    if delta is None:
        info["trend"] = "первый замер"
        return 0.0, info
    info["delta_km2"] = delta
    # Отступление РФ (delta < 0) → давление к мобилизации вверх.
    raw = -delta / config.DEEPSTATE_SCALE_KM2
    norm = raw / (abs(raw) + 1.0)
    if delta > 5:
        info["trend"] = f"продвижение РФ +{delta:.0f} км²"
    elif delta < -5:
        info["trend"] = f"отступление РФ {delta:.0f} км²"
    else:
        info["trend"] = "фронт стабилен"
    return norm, info


# --------------------------------------------------------------------------- #
#  Основной расчёт                                                             #
# --------------------------------------------------------------------------- #
def compute_reading(
    items: list[dict],
    deepstate_snap: dict | None,
    history: list[dict],
    llm: dict | None = None,
    sources_ok_ratio: float = 1.0,
) -> dict:
    now = _now()
    window_start = now - timedelta(days=config.WINDOW_DAYS)

    cat_signal: dict[str, float] = {c: 0.0 for c in config.SIGNAL_CATEGORIES}
    drivers: dict[tuple, dict] = {}
    stream_rel: dict[str, int] = {s: 0 for s in STREAM_LABELS}
    stream_signal: dict[str, float] = {s: 0.0 for s in STREAM_LABELS}
    n_rel = 0

    for it in items:
        if not it.get("relevant"):
            continue
        pub = _parse_dt(it.get("published"))
        if pub and pub < window_start:
            continue
        n_rel += 1
        stream = it.get("stream", "media")
        if stream in stream_rel:
            stream_rel[stream] += 1
        age = (now - pub).total_seconds() / 86400.0 if pub else 0.0
        decay = _decay(age)
        sw = float(it.get("source_weight", 1.0) or 1.0)

        for sig in it.get("signals", []):
            cat = sig["category"]
            if cat not in cat_signal:
                continue
            contrib = sig["polarity"] * sig["weight"] * decay * sw
            cat_signal[cat] += contrib
            if stream in stream_signal:
                stream_signal[stream] += contrib
            key = (cat, sig["term"], 1 if sig["polarity"] >= 0 else -1)
            d = drivers.get(key)
            if not d:
                d = {
                    "category": cat,
                    "category_label": config.SIGNAL_CATEGORIES[cat]["label"],
                    "term": sig["term"],
                    "polarity": sig["polarity"],
                    "abs_sum": 0.0,
                    "count": 0,
                    "_best": -1.0,
                    "example": None,
                }
                drivers[key] = d
            d["abs_sum"] += abs(contrib)
            d["count"] += 1
            single = sig["weight"] * decay * sw
            if single > d["_best"]:
                d["_best"] = single
                d["example"] = {
                    "title": it.get("title", ""),
                    "url": it.get("url", ""),
                    "source": it.get("source_name", ""),
                    "published": it.get("published", ""),
                }

    # --- нормировка категорий ------------------------------------------------ #
    ds_norm, ds_info = deepstate_norm(deepstate_snap)
    norm: dict[str, float] = {}
    for c, meta in config.SIGNAL_CATEGORIES.items():
        if c == "deepstate":
            norm[c] = ds_norm
        else:
            s = cat_signal[c]
            norm[c] = s / (abs(s) + meta["scale"]) if (abs(s) + meta["scale"]) else 0.0

    composite = sum(config.SIGNAL_CATEGORIES[c]["weight"] * norm[c] for c in norm)
    barometer = 100.0 * _sigmoid(config.SCORE_K * composite + config.SCORE_B)
    barometer = round(barometer, 1)

    # --- смешивание с LLM ---------------------------------------------------- #
    llm_score = None
    if llm and isinstance(llm.get("score"), (int, float)):
        llm_score = float(llm["score"])
        alpha = 0.35
        final = (1 - alpha) * barometer + alpha * llm_score
    else:
        final = barometer
    final = round(final, 1)

    # --- компоненты для интерфейса ------------------------------------------ #
    components = {
        "categories": [
            {
                "key": c,
                "label": config.SIGNAL_CATEGORIES[c]["label"],
                "weight": config.SIGNAL_CATEGORIES[c]["weight"],
                "norm": round(norm[c], 3),
                "signed": round(100 * norm[c], 1),  # -100..100 для диверг. шкалы
                "contribution": round(config.SIGNAL_CATEGORIES[c]["weight"] * norm[c], 3),
            }
            for c in config.SIGNAL_CATEGORIES
        ],
        "composite": round(composite, 3),
        "deepstate": ds_info,
        "relevant_items": n_rel,
    }

    # --- стримы (4 опоры) ---------------------------------------------------- #
    streams = [
        {
            "key": s,
            "label": STREAM_LABELS[s],
            "relevant": stream_rel[s],
            "signal": round(stream_signal[s], 2),
        }
        for s in STREAM_LABELS
    ]

    # --- драйверы (топ влияющих сигналов) ----------------------------------- #
    driver_list = sorted(drivers.values(), key=lambda d: d["abs_sum"], reverse=True)[:8]
    max_abs = max((d["abs_sum"] for d in driver_list), default=1.0) or 1.0
    drivers_out = [
        {
            "category_label": d["category_label"],
            "term": d["term"],
            "polarity": d["polarity"],
            "count": d["count"],
            "strength": round(100 * d["abs_sum"] / max_abs, 1),
            "example": d["example"],
        }
        for d in driver_list
    ]

    # --- скорость и прогноз --------------------------------------------------- #
    velocity = _velocity(history, now, final)
    forecast = _forecast(final, velocity, history)

    # --- уверенность --------------------------------------------------------- #
    vol_conf = min(1.0, n_rel / 60.0)
    hist_conf = min(1.0, len(history) / 14.0)
    confidence = round(0.5 * vol_conf + 0.3 * hist_conf + 0.2 * sources_ok_ratio, 2)

    return {
        "taken_at": now.strftime("%Y-%m-%d %H:%M:%S"),
        "barometer": barometer,
        "llm_barometer": llm_score,
        "llm": llm,
        "final_barometer": final,
        "velocity": round(velocity, 3) if velocity is not None else None,
        "predicted_date": forecast.get("date"),
        "confidence": confidence,
        "components": components,
        "streams": streams,
        "drivers": drivers_out,
        "forecast": forecast,
        "zone": _zone(final),
    }


def _zone(v: float) -> str:
    if v >= config.ANNOUNCED_THRESHOLD:
        return "объявлена"
    if v >= 70:
        return "высокий"
    if v >= 45:
        return "повышенный"
    if v >= 25:
        return "умеренный"
    return "низкий"


def _velocity(history: list[dict], now: datetime, current: float) -> float | None:
    """Скорость роста барометра (пунктов/день) линейной регрессией по истории."""
    pts: list[tuple[float, float]] = []
    cutoff = now - timedelta(days=21)
    for h in history:
        t = _parse_dt(h.get("taken_at"))
        y = h.get("final_barometer")
        if t and y is not None and t >= cutoff:
            pts.append(((t - cutoff).total_seconds() / 86400.0, float(y)))
    pts.append(((now - cutoff).total_seconds() / 86400.0, current))
    if len(pts) < 2:
        return None
    span = max(x for x, _ in pts) - min(x for x, _ in pts)
    if span < 0.5:  # меньше полудня данных — рано
        return None
    n = len(pts)
    sx = sum(x for x, _ in pts)
    sy = sum(y for _, y in pts)
    sxx = sum(x * x for x, _ in pts)
    sxy = sum(x * y for x, y in pts)
    denom = n * sxx - sx * sx
    if abs(denom) < 1e-9:
        return None
    return (n * sxy - sx * sy) / denom


def _forecast(current: float, velocity: float | None, history: list[dict]) -> dict:
    today = _now().date()
    if current >= config.ANNOUNCED_THRESHOLD:
        return {"label": "Мобилизация объявлена / идёт", "date": today.isoformat(),
                "days": 0, "basis": "барометр в красной зоне"}
    if velocity is None:
        return {"label": "Накопление данных — прогноз появится после нескольких замеров",
                "date": None, "days": None, "basis": "недостаточно истории"}
    if velocity <= 0.15:
        return {"label": "В обозримом будущем не прогнозируется",
                "date": None, "days": None,
                "basis": f"барометр не растёт ({velocity:+.2f}/день)"}
    days = (config.FORECAST_THRESHOLD - current) / velocity
    if days <= 0:
        return {"label": "Риск максимальный — возможно в любой момент",
                "date": today.isoformat(), "days": 0, "basis": "экстраполяция тренда"}
    if days > 730:
        return {"label": "Более 2 лет — фактически не прогнозируется",
                "date": None, "days": int(days),
                "basis": f"очень медленный рост ({velocity:+.2f}/день)"}
    date = today + timedelta(days=int(round(days)))
    return {"label": f"Ориентировочно через ~{int(round(days))} дн.",
            "date": date.isoformat(), "days": int(round(days)),
            "basis": f"рост {velocity:+.2f} пункта/день до порога {config.FORECAST_THRESHOLD}"}
