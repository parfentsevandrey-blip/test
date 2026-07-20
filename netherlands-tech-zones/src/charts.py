"""Consistent, clean analytical charts for the report (theme-matched)."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager as fm

INK = "#14213D"
MUTED = "#5B6472"
GRID = "#E4E8EF"
CHIPS = "#EA580C"
DATA = "#2563EB"
AI = "#7C3AED"
RESEARCH = "#059669"
GOLD = "#D9A521"
SEQ = [CHIPS, DATA, AI, RESEARCH, GOLD, "#0EA5A4", "#DB2777"]

plt.rcParams.update({
    "font.family": "DejaVu Sans",
    "font.size": 13,
    "axes.edgecolor": GRID,
    "axes.linewidth": 1.0,
    "figure.dpi": 200,
    "savefig.dpi": 200,
})


def _clean(ax):
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    ax.spines["left"].set_color(GRID)
    ax.spines["bottom"].set_color(GRID)
    ax.tick_params(colors=MUTED, length=0)
    for lbl in ax.get_xticklabels() + ax.get_yticklabels():
        lbl.set_color(INK)


def hbar_ranking(labels, values, out, title, subtitle="", unit="",
                 highlight=0, color=DATA, value_fmt="{:.0f}"):
    fig, ax = plt.subplots(figsize=(8.2, 0.62 * len(labels) + 1.6))
    y = range(len(labels))[::-1]
    colors = [color if i != highlight else CHIPS for i in range(len(labels))]
    bars = ax.barh(list(y), values, color=colors, height=0.62, zorder=3)
    ax.set_yticks(list(y))
    ax.set_yticklabels(labels)
    ax.set_xticks([])
    _clean(ax)
    for s in ("left", "bottom"):
        ax.spines[s].set_visible(False)
    mx = max(values)
    for b, v in zip(bars, values):
        ax.text(b.get_width() + mx * 0.012, b.get_y() + b.get_height() / 2,
                value_fmt.format(v) + unit, va="center", ha="left",
                color=INK, fontweight="bold", fontsize=12)
    ax.set_xlim(0, mx * 1.16)
    ax.set_title(title, loc="left", color=INK, fontsize=16, fontweight="bold", pad=34)
    if subtitle:
        ax.text(0, 1.04, subtitle, transform=ax.transAxes, color=MUTED, fontsize=11.5)
    fig.subplots_adjust(top=0.80)
    fig.savefig(out, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return out


def vbar(labels, values, out, title, subtitle="", unit="", colors=None,
         value_fmt="{:.0f}"):
    fig, ax = plt.subplots(figsize=(8.4, 4.6))
    colors = colors or [SEQ[i % len(SEQ)] for i in range(len(labels))]
    bars = ax.bar(range(len(labels)), values, color=colors, width=0.66, zorder=3)
    ax.set_xticks(range(len(labels)))
    ax.set_xticklabels(labels)
    ax.set_yticks([])
    _clean(ax)
    for s in ("left",):
        ax.spines[s].set_visible(False)
    mx = max(values)
    for b, v in zip(bars, values):
        ax.text(b.get_x() + b.get_width() / 2, b.get_height() + mx * 0.02,
                value_fmt.format(v) + unit, ha="center", va="bottom",
                color=INK, fontweight="bold", fontsize=12)
    ax.set_ylim(0, mx * 1.18)
    ax.set_title(title, loc="left", color=INK, fontsize=16, fontweight="bold", pad=40)
    if subtitle:
        ax.text(0, 1.045, subtitle, transform=ax.transAxes, color=MUTED, fontsize=11.5)
    fig.subplots_adjust(top=0.82)
    fig.savefig(out, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return out


def donut(labels, values, out, title, subtitle="", center=""):
    fig, ax = plt.subplots(figsize=(7.4, 5.2))
    cols = [SEQ[i % len(SEQ)] for i in range(len(labels))]
    wedges, _ = ax.pie(values, colors=cols, startangle=90,
                       wedgeprops=dict(width=0.42, edgecolor="white", linewidth=2))
    ax.set(aspect="equal")
    total = sum(values)
    leg = [f"{l} — {v/total*100:.0f}%" for l, v in zip(labels, values)]
    ax.legend(wedges, leg, loc="center left", bbox_to_anchor=(1.0, 0.5),
              frameon=False, fontsize=12, labelcolor=INK)
    if center:
        ax.text(0, 0, center, ha="center", va="center", fontsize=15,
                fontweight="bold", color=INK)
    ax.set_title(title, loc="left", color=INK, fontsize=16, fontweight="bold", pad=10)
    if subtitle:
        ax.text(-1.35, 1.25, subtitle, color=MUTED, fontsize=12)
    fig.tight_layout()
    fig.savefig(out, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return out


def stat_strip(items, out, title=""):
    """items: list of (big_value, label, color)."""
    n = len(items)
    fig, ax = plt.subplots(figsize=(2.7 * n, 2.5))
    ax.axis("off")
    for i, (val, lab, col) in enumerate(items):
        x = (i + 0.5) / n
        ax.text(x, 0.62, val, ha="center", va="center", fontsize=30,
                fontweight="bold", color=col, transform=ax.transAxes)
        ax.text(x, 0.28, lab, ha="center", va="center", fontsize=12.5,
                color=MUTED, transform=ax.transAxes, wrap=True)
        if i:
            ax.plot([i / n, i / n], [0.2, 0.75], color=GRID, lw=1.2,
                    transform=ax.transAxes)
    fig.savefig(out, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    return out


if __name__ == "__main__":
    hbar_ranking(["Amsterdam", "Frankfurt", "London", "Paris", "Dublin"],
                 [1400, 1300, 1100, 700, 600], "imgtest/chart_test.png",
                 "Тест", "подзаголовок", " МВт")
    print("chart ok")
