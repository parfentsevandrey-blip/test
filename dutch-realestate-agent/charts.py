#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
charts.py — умение агента строить графики/диаграммы/статистику.

Рендерит графики из «спецификаций» (dict/JSON) в PNG (matplotlib, backend Agg),
в едином стиле отчёта. Картинки затем встраиваются в Word (generate_report.py)
и в HTML-превью (preview_html.py).

Спецификация графика (поля):
  id        — короткий слаг (имя файла)
  segment   — residential | commercial | industrial | overview
  type      — bar | hbar | grouped_bar | line | donut | kpi
  title     — заголовок-вывод (рисуется над графиком)
  caption   — подпись-takeaway (рисуется в отчёте под графиком)
  unit      — единица измерения оси («%», «€ млн», «м²» …)
  labels    — категории / ось X / доли
  series    — [{name, values:[..]}]  (bar/hbar/grouped_bar/line/donut)
  kpi_items — [{label, value, delta, direction}]  (только type=kpi)
  source    — источник (мелкой строкой внизу графика)

render_charts(charts, assets_dir) -> {id: png_path}
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager  # noqa: F401

# ---- палитра отчёта ----
NAVY = "#1F3A5F"
INK = "#222B36"
MUTED = "#6B7785"
HAIR = "#D7DEE6"
SEGCLR = {
    "residential": "#16846F",
    "commercial":  "#C0791C",
    "industrial":  "#2C5F8A",
    "overview":    "#1F3A5F",
}
DIRCLR = {"up": "#1E7A4D", "down": "#B03A3A", "neutral": "#2C5F8A"}
# мягкая многоцветная палитра для нескольких серий/долей
MULTI = ["#1F3A5F", "#16846F", "#C0791C", "#2C5F8A", "#8E6FB3", "#B03A3A", "#5D6D7E", "#3FA796"]

plt.rcParams.update({
    "font.family": "DejaVu Sans",   # поддерживает кириллицу, €, ²
    "font.size": 11,
    "axes.edgecolor": HAIR,
    "axes.linewidth": 0.8,
    "axes.titlesize": 12.5,
    "figure.dpi": 150,
    "savefig.dpi": 150,
})

FIG_W = 7.6  # дюймы → ~16 см при вставке в Word


def _fmt(v):
    """Число → строка с запятой-десятичным и пробелом-разрядом."""
    try:
        f = float(v)
    except (TypeError, ValueError):
        return str(v)
    if f == int(f):
        s = f"{int(f):,}".replace(",", " ")
    else:
        s = f"{f:,.1f}".replace(",", " ").replace(".", ",")
    return s


def _style_axes(ax):
    for sp in ("top", "right"):
        ax.spines[sp].set_visible(False)
    for sp in ("left", "bottom"):
        ax.spines[sp].set_color(HAIR)
    ax.tick_params(colors=MUTED, labelsize=9.5, length=0)
    ax.grid(axis="y", color=HAIR, linewidth=0.7, alpha=0.7)
    ax.set_axisbelow(True)


def _title(fig, ax, spec):
    t = spec.get("title", "")
    if t:
        ax.set_title(t, color=NAVY, fontweight="bold", loc="left", pad=10)
    src = spec.get("source", "")
    if src:
        fig.text(0.012, 0.012, f"Источник: {src}", color=MUTED, fontsize=7.5,
                 style="italic", ha="left", va="bottom")


def _finish(fig, out_path):
    fig.savefig(out_path, bbox_inches="tight", facecolor="white", pad_inches=0.18)
    plt.close(fig)
    return out_path


def _series_vals(spec, i=0):
    s = spec.get("series") or []
    if i < len(s):
        return s[i].get("values") or []
    return []


# --------------------------------------------------------------------------- #
def chart_bar(spec, out_path, horizontal=False):
    labels = spec.get("labels") or []
    vals = _series_vals(spec, 0)
    n = min(len(labels), len(vals))
    labels, vals = labels[:n], vals[:n]
    color = SEGCLR.get(spec.get("segment"), NAVY)
    fig, ax = plt.subplots(figsize=(FIG_W, max(2.6, 0.55 * n + 1.6) if horizontal else 3.9))
    if horizontal:
        bars = ax.barh(labels, vals, color=color, height=0.62)
        ax.invert_yaxis()
        for sp in ("top", "right", "bottom"):
            ax.spines[sp].set_visible(False)
        ax.spines["left"].set_color(HAIR)
        ax.grid(axis="x", color=HAIR, linewidth=0.7, alpha=0.7)
        ax.set_axisbelow(True)
        ax.tick_params(colors=MUTED, labelsize=9.5, length=0)
        mx = max(vals) if vals else 1
        for b, v in zip(bars, vals):
            ax.text(b.get_width() + mx * 0.01, b.get_y() + b.get_height() / 2,
                    _fmt(v), va="center", ha="left", fontsize=9, color=INK, fontweight="bold")
        ax.set_xlabel(spec.get("unit", ""), color=MUTED, fontsize=9)
    else:
        hi = spec.get("highlight")
        if isinstance(hi, int) and 0 <= hi < n:
            cols = ["#C9D3DD"] * n
            cols[hi] = color
        else:
            cols = [color] * n
        bars = ax.bar(labels, vals, color=cols, width=0.6)
        _style_axes(ax)
        mx = max(vals) if vals else 1
        for b, v in zip(bars, vals):
            ax.text(b.get_x() + b.get_width() / 2, b.get_height() + mx * 0.02,
                    _fmt(v), ha="center", va="bottom", fontsize=9, color=INK, fontweight="bold")
        bm = spec.get("benchmark")
        if isinstance(bm, dict) and bm.get("value") is not None:
            ax.axhline(bm["value"], ls="--", lw=1.2, color=MUTED)
            ax.text(n - 0.5, bm["value"], " " + str(bm.get("label", "")),
                    color=MUTED, fontsize=8, va="bottom", ha="right")
        ax.set_ylabel(spec.get("unit", ""), color=MUTED, fontsize=9)
        ax.margins(y=0.16)
        plt.setp(ax.get_xticklabels(), rotation=0)
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_before_after(spec, out_path):
    """Было→стало: серый столбец (было) + цветной (стало) + Δ% над парой."""
    import numpy as np
    labels = spec.get("labels") or []
    series = spec.get("series") or []
    before = spec.get("before") or (series[0].get("values") if len(series) > 0 else [])
    after = spec.get("after") or (series[1].get("values") if len(series) > 1 else [])
    n = min(len(labels), len(before), len(after))
    labels, before, after = labels[:n], before[:n], after[:n]
    color = SEGCLR.get(spec.get("segment"), NAVY)
    fig, ax = plt.subplots(figsize=(FIG_W, 4.0))
    x = np.arange(n)
    w = 0.36
    ax.bar(x - w / 2, before, width=w, color=MUTED, label="Было")
    bars2 = ax.bar(x + w / 2, after, width=w, color=color, label="Стало")
    allv = list(before) + list(after)
    mx = max(allv) if allv else 1
    for xi, b, a in zip(x, before, after):
        ax.text(xi - w / 2, b + mx * 0.02, _fmt(b), ha="center", va="bottom", fontsize=8, color=MUTED)
        ax.text(xi + w / 2, a + mx * 0.02, _fmt(a), ha="center", va="bottom", fontsize=8, color=INK, fontweight="bold")
        if b:
            d = (a - b) / b * 100
            clr = DIRCLR["down"] if d < 0 else DIRCLR["up"]
            ax.text(xi, max(b, a) + mx * 0.10, f"{d:+.0f}%", ha="center", va="bottom",
                    fontsize=9.5, color=clr, fontweight="bold")
    ax.set_xticks(x); ax.set_xticklabels(labels)
    _style_axes(ax); ax.margins(y=0.20)
    ax.set_ylabel(spec.get("unit", ""), color=MUTED, fontsize=9)
    ax.legend(frameon=False, fontsize=9, loc="upper left")
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_stacked_bar(spec, out_path):
    """100%-стек одной полосой: композиция/доли с подписями процентов (замена донату)."""
    labels = spec.get("labels") or []
    vals = _series_vals(spec, 0)
    n = min(len(labels), len(vals))
    labels, vals = labels[:n], vals[:n]
    total = sum(vals) or 1
    parts = [v / total * 100 for v in vals]
    colors = [MULTI[i % len(MULTI)] for i in range(n)]
    fig, ax = plt.subplots(figsize=(FIG_W, 2.4))
    left = 0
    for i, (p, lab) in enumerate(zip(parts, labels)):
        ax.barh([0], [p], left=left, color=colors[i], height=0.5, label=lab)
        if p >= 6:
            ax.text(left + p / 2, 0, f"{p:.0f}%", ha="center", va="center",
                    color="white", fontsize=10, fontweight="bold")
        left += p
    ax.set_xlim(0, 100); ax.set_ylim(-0.5, 0.5)
    ax.axis("off")
    ax.legend(frameon=False, fontsize=9, ncol=min(n, 3), loc="upper center", bbox_to_anchor=(0.5, -0.05))
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_grouped_bar(spec, out_path):
    labels = spec.get("labels") or []
    series = spec.get("series") or []
    n = len(labels)
    fig, ax = plt.subplots(figsize=(FIG_W, 4.0))
    k = max(1, len(series))
    width = 0.8 / k
    import numpy as np
    x = np.arange(n)
    for i, s in enumerate(series):
        vals = (s.get("values") or [])[:n]
        off = (i - (k - 1) / 2) * width
        color = MULTI[i % len(MULTI)]
        bars = ax.bar(x + off, vals, width=width, label=s.get("name", ""), color=color)
        mx = max([max((ss.get("values") or [0]) or [0]) for ss in series] or [1])
        for b, v in zip(bars, vals):
            ax.text(b.get_x() + b.get_width() / 2, b.get_height() + mx * 0.02,
                    _fmt(v), ha="center", va="bottom", fontsize=8, color=INK)
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    _style_axes(ax)
    ax.margins(y=0.16)
    ax.set_ylabel(spec.get("unit", ""), color=MUTED, fontsize=9)
    ax.legend(frameon=False, fontsize=9, loc="upper right")
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_line(spec, out_path):
    labels = spec.get("labels") or []
    series = spec.get("series") or []
    fig, ax = plt.subplots(figsize=(FIG_W, 3.9))
    for i, s in enumerate(series):
        vals = (s.get("values") or [])[:len(labels)]
        color = MULTI[i % len(MULTI)]
        ax.plot(labels[:len(vals)], vals, marker="o", markersize=4, linewidth=2.2,
                color=color, label=s.get("name", ""))
    _style_axes(ax)
    ax.set_ylabel(spec.get("unit", ""), color=MUTED, fontsize=9)
    if len(series) > 1:
        ax.legend(frameon=False, fontsize=9)
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_donut(spec, out_path):
    labels = spec.get("labels") or []
    vals = _series_vals(spec, 0)
    n = min(len(labels), len(vals))
    labels, vals = labels[:n], vals[:n]
    colors = [MULTI[i % len(MULTI)] for i in range(n)]
    fig, ax = plt.subplots(figsize=(FIG_W, 4.1))
    wedges, _t, autot = ax.pie(
        vals, colors=colors, startangle=90, counterclock=False,
        wedgeprops=dict(width=0.42, edgecolor="white", linewidth=2),
        autopct=lambda p: f"{p:.0f}%", pctdistance=0.79,
        textprops=dict(color="white", fontsize=10, fontweight="bold"),
    )
    ax.legend(wedges, labels, frameon=False, fontsize=9.5,
              loc="center left", bbox_to_anchor=(0.98, 0.5))
    ax.set(aspect="equal")
    _title(fig, ax, spec)
    return _finish(fig, out_path)


def chart_kpi(spec, out_path):
    """KPI-карточки: каждая в своём subplot с переносом текста (без наложений)."""
    import textwrap
    from matplotlib.lines import Line2D
    items = spec.get("kpi_items") or []
    n = max(1, len(items))
    # ширина символов на карточку — чтобы переносить подписи по её ширине
    wrap_w = max(14, int(46 / n))
    arrows = {"up": "▲", "down": "▼", "neutral": "◆"}
    fig, axes = plt.subplots(1, n, figsize=(FIG_W, 2.05))
    if n == 1:
        axes = [axes]
    fig.subplots_adjust(top=0.80, bottom=0.06, left=0.01, right=0.99, wspace=0.10)
    for ax, it in zip(axes, items):
        ax.axis("off")
        clr = DIRCLR.get(it.get("direction", "neutral"), MUTED)
        label = "\n".join(textwrap.wrap(it.get("label", ""), wrap_w)) or " "
        arr = arrows.get(it.get("direction", "neutral"), "")
        delta = f"{arr} {it.get('delta', '')}".strip()
        delta = "\n".join(textwrap.wrap(delta, wrap_w + 2)) if delta else ""
        ax.text(0.5, 0.92, label, ha="center", va="top", fontsize=8.5,
                color=MUTED, transform=ax.transAxes)
        ax.text(0.5, 0.50, it.get("value", ""), ha="center", va="center", fontsize=16,
                color=NAVY, fontweight="bold", transform=ax.transAxes)
        if delta:
            ax.text(0.5, 0.10, delta, ha="center", va="bottom", fontsize=8.5,
                    color=clr, fontweight="bold", transform=ax.transAxes)
    # тонкие разделители между карточками (в координатах фигуры)
    for i in range(1, n):
        x = 0.01 + (0.98) * i / n
        fig.add_artist(Line2D([x, x], [0.12, 0.74], color=HAIR, lw=0.8,
                              transform=fig.transFigure))
    if spec.get("title"):
        fig.text(0.012, 0.95, spec["title"], ha="left", va="top", fontsize=12.5,
                 color=NAVY, fontweight="bold")
    if spec.get("source"):
        fig.text(0.012, 0.005, f"Источник: {spec['source']}", color=MUTED,
                 fontsize=7.5, style="italic", ha="left", va="bottom")
    fig.savefig(out_path, facecolor="white")
    plt.close(fig)
    return out_path


_DISPATCH = {
    "bar": lambda s, p: chart_bar(s, p, horizontal=False),
    "hbar": lambda s, p: chart_bar(s, p, horizontal=True),
    "grouped_bar": chart_grouped_bar,
    "before_after": chart_before_after,
    "stacked_bar": chart_stacked_bar,
    "line": chart_line,
    "donut": chart_donut,
    "kpi": chart_kpi,
    "kpi_card": chart_kpi,   # синоним
    "kpi_cards": chart_kpi,  # синоним
}


def render_chart(spec, out_path):
    fn = _DISPATCH.get(spec.get("type"))
    if not fn:
        print(f"⚠️  неизвестный тип графика: {spec.get('type')} (id={spec.get('id')})")
        return None
    try:
        return fn(spec, out_path)
    except Exception as e:  # noqa: BLE001
        print(f"⚠️  не удалось построить график {spec.get('id')}: {e}")
        return None


def render_charts(charts, assets_dir):
    """Рендерит список графиков; возвращает {id: png_path} для успешных."""
    os.makedirs(assets_dir, exist_ok=True)
    out = {}
    for i, spec in enumerate(charts or []):
        cid = spec.get("id") or f"chart{i}"
        path = os.path.join(assets_dir, f"{cid}.png")
        res = render_chart(spec, path)
        if res:
            out[cid] = res
    return out


if __name__ == "__main__":
    # быстрый самотест
    demo = [
        {"id": "t_bar", "segment": "commercial", "type": "bar", "title": "Вакантность",
         "caption": "", "unit": "%", "labels": ["Ритейл-парки", "Рынок"],
         "series": [{"name": "Вакантность", "values": [4.4, 5.5]}], "kpi_items": [], "source": "CBRE"},
        {"id": "t_grp", "segment": "industrial", "type": "grouped_bar", "title": "Целевые цены",
         "caption": "", "unit": "€", "labels": ["CTP", "WDP", "Montea", "VGP"],
         "series": [{"name": "Было", "values": [22, 30, 85, 120]}, {"name": "Стало", "values": [19, 25, 73, 100]}],
         "kpi_items": [], "source": "Deutsche Bank"},
        {"id": "t_donut", "segment": "residential", "type": "donut", "title": "Структура жилья",
         "caption": "", "unit": "%", "labels": ["Соц. аренда", "Средняя", "Дороже"],
         "series": [{"name": "Доли", "values": [27, 37, 36]}], "kpi_items": [], "source": "Heijmans"},
        {"id": "t_kpi", "segment": "overview", "type": "kpi", "title": "Пульс недели",
         "caption": "", "unit": "", "labels": [], "series": [],
         "kpi_items": [{"label": "Оферта Prologis за Segro", "value": "€14,6 млрд", "delta": "премия 24,6%", "direction": "up"},
                       {"label": "Склад Panattoni, Эммен", "value": "150 000 м²", "delta": "спекулятивно", "direction": "neutral"}],
         "source": ""},
    ]
    r = render_charts(demo, "/tmp/_charts_test")
    print("OK:", r)
