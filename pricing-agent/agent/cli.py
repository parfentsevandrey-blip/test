"""Прогон ценообразования в терминале — то же ядро, что и в боте, без Telegram.

    python -m agent.cli                 # все объекты, кратко
    python -m agent.cli sky-house-7     # один объект, подробно
    python -m agent.cli --no-ops        # без демо-данных о спросе
"""

from __future__ import annotations

import json
import sys
from datetime import date
from pathlib import Path

from .models import Action, Verdict
from .pricing import evaluate
from .providers import ChainProvider
from .providers.cian import CianProvider
from .providers.demo import DemoProvider
from .registry import load_registry, portfolio_checks

ROOT = Path(__file__).resolve().parent.parent

ICON = {
    Action.CUT: "снизить",
    Action.RAISE: "поднять",
    Action.HOLD: "держать",
    Action.MANUAL: "ручная оценка",
}


def apply_demo_ops(apartments):
    """Подмешивает синтетические оперативные данные (срок экспозиции, спрос)."""
    path = ROOT / "data" / "ops_demo.json"
    if not path.exists():
        return apartments
    ops = json.loads(path.read_text(encoding="utf-8"))["ops"]
    for a in apartments:
        item = ops.get(a.id)
        if not item:
            continue
        a.listed_at = date.fromisoformat(item["listed_at"])
        a.views_7d = item["views_7d"]
        a.calls_7d = item["calls_7d"]
        a.viewings_30d = item["viewings_30d"]
    return apartments


def brief(v: Verdict) -> str:
    a = v.apartment
    delta = f"{v.delta_pct:+.1%}" if v.action is not Action.MANUAL else "—"
    return (
        f"{a.complex_name:<24} {a.area:>6.1f} м²  "
        f"{a.price / 1e6:>6.1f} млн  "
        f"{a.price_per_sqm / 1000:>6.0f} тыс/м²  "
        f"p{v.our_percentile:>3.0f}  "
        f"{ICON[v.action]:<14} {delta:>7}  "
        f"→ {v.recommended_price / 1e6:>6.1f} млн   conf {v.confidence:.2f}"
    )


def detail(v: Verdict) -> str:
    from .narrative import template_explanation

    a = v.apartment
    lines = [
        "=" * 88,
        f"{a.complex_name} — {a.address}",
        f"{a.rooms} комн., {a.area} м², этаж {a.floor}/{a.floors_total}, {a.finish.value}, "
        f"машино-место: {'да' if a.has_parking else 'нет'}",
        f"Текущая цена: {a.price:,} ₽ ({a.price_per_sqm / 1000:.0f} тыс ₽/м²)".replace(",", " "),
        "",
        template_explanation(v),
        "",
        "Сценарии:",
    ]
    for s in v.scenarios:
        lines.append(
            f"  {s.name:<18} {s.price / 1e6:>6.1f} млн ₽   ~{s.expected_days:>3} дн.   {s.comment}"
        )

    lines += ["", f"Аналоги (топ-5 по весу) из {len(v.comps)}:"]
    for ac in v.comps[:5]:
        c = ac.comp
        tag = "сделка" if c.is_closed_deal else f"{c.days_on_market} дн."
        lines.append(
            f"  [{c.source}] {c.complex_name[:22]:<22} {c.area:>6.1f} м² "
            f"{c.floor:>2}/{c.floors_total:<2} "
            f"{c.price_per_sqm / 1000:>6.0f} → {ac.adjusted_price_per_sqm / 1000:>6.0f} тыс/м² "
            f"({ac.total_adjustment_pct:+.1%}, вес {ac.weight:.2f}, {tag})"
        )
        for adj in ac.adjustments:
            lines.append(f"        · {adj.name}: {adj.explanation}")
    return "\n".join(lines)


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    use_ops = "--no-ops" not in sys.argv

    apartments = load_registry()
    if use_ops:
        apartments = apply_demo_ops(apartments)

    provider = ChainProvider(CianProvider(), DemoProvider())
    if isinstance(provider.providers[0], CianProvider) and not provider.providers[0].configured:
        print("⚠️  Циан API не сконфигурирован — работаем на СИНТЕТИЧЕСКИХ демо-аналогах.\n")

    if args:
        target = next((a for a in apartments if a.id == args[0]), None)
        if target is None:
            print(f"Объект {args[0]!r} не найден. Доступны: {', '.join(a.id for a in apartments)}")
            raise SystemExit(1)
        print(detail(evaluate(target, provider.fetch_comps(target))))
        return

    print(
        f"{'ЖК':<24} {'площадь':>8} {'цена':>10} {'за м²':>12} {'перц':>5}  "
        f"{'вердикт':<14} {'Δ':>7}   рекомендация"
    )
    print("-" * 120)
    verdicts = []
    for a in apartments:
        v = evaluate(a, provider.fetch_comps(a))
        verdicts.append(v)
        print(brief(v))

    notes = portfolio_checks(apartments)
    if notes:
        print("\nПроверка консистентности собственного прайса:")
        for n in notes:
            print(f"  ⚠️  {n}")

    total = sum(v.apartment.price for v in verdicts)
    recommended = sum(v.recommended_price for v in verdicts)
    print(
        f"\nПортфель: {total / 1e6:.0f} млн ₽ → {recommended / 1e6:.0f} млн ₽ "
        f"({recommended / total - 1:+.1%})"
    )


if __name__ == "__main__":
    main()
