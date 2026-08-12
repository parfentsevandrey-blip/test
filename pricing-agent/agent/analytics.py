"""Аналитика по конкурентам: кто ещё продаёт в наших ЖК и как мы выглядим рядом.

Здесь два разных взгляда на одни и те же лоты, и путать их нельзя:

  скорректированные ₽/м²  — аналог приведён к характеристикам нашей квартиры
                            (этаж, площадь, отделка, машино-место). Это база для
                            вердикта: сравнение «яблок с яблоками».
  сырая цена лота         — то, что видит покупатель, листающий выдачу. Он не делает
                            поправку на отделку: он видит «155 м² за 118 млн» рядом
                            с нашими «155 м² за 135 млн» и уходит к первому.

Вердикт строится на первом. Разговор с собственником — на втором: «вот три лота,
которые покупатель увидит рядом с нашим, и все дешевле».
"""

from __future__ import annotations

from dataclasses import dataclass, field
from statistics import median

from .adjustments import DEFAULT_CONFIG, PricingConfig, adjust_comp
from .models import Apartment, Comp

# Покупатель с бюджетом на 155 м² смотрит и 130, и 180 — но не 90.
AREA_BAND = 0.22


@dataclass
class Rival:
    """Конкурирующий продавец в одном из наших ЖК."""

    name: str
    complex_name: str
    lots: int
    median_ppsm: float
    median_exposure: float | None
    cutting: int            # сколько лотов снижали цену
    republishing: int       # сколько переподавали объявление
    cheapest_ppsm: float

    @property
    def cutting_share(self) -> float:
        return self.cutting / self.lots if self.lots else 0.0


@dataclass
class LotCompetition:
    """Конкурентное окружение одного нашего лота."""

    apartment: Apartment
    direct: list[Comp]                  # сопоставимые по площади, в том же ЖК
    cheaper: list[Comp]                 # из них дешевле нас по ₽/м²
    alternatives: list[Comp]            # что покупатель увидит вместо нас
    median_ppsm: float | None
    median_exposure: float | None
    cutting: int
    republishing: int
    adjusted_gap: float | None          # наш отрыв от медианы ПОСЛЕ поправок
    raw_gap: float | None               # то же по сырым ₽/м², как видит покупатель
    rivals: list[Rival] = field(default_factory=list)

    @property
    def pressure(self) -> str:
        """Односложная оценка давления — для сортировки портфеля по срочности."""
        if not self.direct:
            return "нет данных"
        share_cheaper = len(self.cheaper) / len(self.direct)
        if share_cheaper >= 0.7 and (self.raw_gap or 0) > 0.08:
            return "высокое"
        if share_cheaper >= 0.5:
            return "среднее"
        return "низкое"


def analyse_lot(
    apartment: Apartment,
    comps: list[Comp],
    cfg: PricingConfig = DEFAULT_CONFIG,
) -> LotCompetition:
    """Конкурентное окружение лота: кто рядом, кто дешевле, куда уйдёт покупатель."""
    direct = [
        c
        for c in comps
        if c.same_complex and abs(c.area - apartment.area) / apartment.area <= AREA_BAND
    ]
    our = apartment.price_per_sqm
    cheaper = sorted((c for c in direct if c.price_per_sqm < our), key=lambda c: c.price_per_sqm)

    # Что покупатель увидит вместо нас: дешевле, но не «однушка вместо пятикомнатной» —
    # ближайшие по метражу из тех, что дешевле.
    alternatives = sorted(cheaper, key=lambda c: abs(c.area - apartment.area))[:3]

    raw_median = median([c.price_per_sqm for c in direct]) if direct else None
    adj = [adjust_comp(apartment, c, cfg).adjusted_price_per_sqm for c in direct]
    adj_median = median(adj) if adj else None
    doms = [c.days_on_market for c in direct if c.days_on_market is not None]

    return LotCompetition(
        apartment=apartment,
        direct=direct,
        cheaper=cheaper,
        alternatives=alternatives,
        median_ppsm=raw_median,
        median_exposure=median(doms) if doms else None,
        cutting=sum(1 for c in direct if c.price_cut_pct),
        republishing=sum(1 for c in direct if (c.republish or 0) > 0),
        adjusted_gap=(our / adj_median - 1) if adj_median else None,
        raw_gap=(our / raw_median - 1) if raw_median else None,
        rivals=rivals_in(direct),
    )


def rivals_in(comps: list[Comp], min_lots: int = 2) -> list[Rival]:
    """Продавцы, у которых в выборке больше одного лота — с ними мы и конкурируем."""
    by_name: dict[tuple[str, str], list[Comp]] = {}
    for c in comps:
        name = c.seller_name.strip() or "не указан"
        by_name.setdefault((name, c.complex_name), []).append(c)

    out: list[Rival] = []
    for (name, complex_name), lots in by_name.items():
        if len(lots) < min_lots:
            continue
        doms = [c.days_on_market for c in lots if c.days_on_market is not None]
        out.append(
            Rival(
                name=name,
                complex_name=complex_name,
                lots=len(lots),
                median_ppsm=median([c.price_per_sqm for c in lots]),
                median_exposure=median(doms) if doms else None,
                cutting=sum(1 for c in lots if c.price_cut_pct),
                republishing=sum(1 for c in lots if (c.republish or 0) > 0),
                cheapest_ppsm=min(c.price_per_sqm for c in lots),
            )
        )
    return sorted(out, key=lambda r: (-r.lots, r.median_ppsm))


def portfolio_rivals(by_lot: list[LotCompetition], min_lots: int = 2) -> list[Rival]:
    """Сводка по конкурентам всего портфеля, без двойного счёта одного лота."""
    seen: set[str] = set()
    pooled: list[Comp] = []
    for lot in by_lot:
        for c in lot.direct:
            if c.external_id in seen:
                continue
            seen.add(c.external_id)
            pooled.append(c)
    return rivals_in(pooled, min_lots=min_lots)


# --- вывод в терминал ---------------------------------------------------------------


def money(rub: float) -> str:
    return f"{rub / 1e6:.1f} млн"


def k(ppsm: float) -> str:
    return f"{ppsm / 1000:.0f}"


def render_lot(c: LotCompetition) -> str:
    a = c.apartment
    lines = [
        "=" * 92,
        f"{a.complex_name.strip()} · {a.address} · {a.area:g} м² · этаж {a.floor}/{a.floors_total}",
        f"Наша цена: {money(a.price)} ({k(a.price_per_sqm)} тыс ₽/м²) · {a.finish.value}",
        "",
    ]

    if not c.direct:
        lines.append("Сопоставимых лотов в выгрузке нет — выгрузка по этому ЖК не сделана.")
        return "\n".join(lines)

    band_lo = a.area * (1 - AREA_BAND)
    band_hi = a.area * (1 + AREA_BAND)
    lines.append(
        f"Прямых конкурентов: {len(c.direct)} "
        f"(тот же ЖК, {band_lo:.0f}–{band_hi:.0f} м²)"
    )
    lines.append(
        f"Дешевле нас: {len(c.cheaper)} из {len(c.direct)} · "
        f"медиана {k(c.median_ppsm)} тыс ₽/м² "
        f"({c.raw_gap:+.1%} к нашей цене)"
    )
    if c.adjusted_gap is not None:
        lines.append(
            f"После поправок на этаж, площадь и отделку отрыв {c.adjusted_gap:+.1%} — "
            + (
                "разрыв не объясняется характеристиками лота"
                if abs(c.adjusted_gap) > 0.05 and c.adjusted_gap > 0
                else "разница в основном объясняется характеристиками"
            )
        )
    lines.append(f"Давление конкурентов: {c.pressure}")
    lines.append("")

    if c.alternatives:
        lines.append("Что покупатель увидит вместо нашего лота:")
        for alt in c.alternatives:
            gap = alt.price_per_sqm / a.price_per_sqm - 1
            tags = []
            if alt.days_on_market:
                tags.append(f"{alt.days_on_market} дн. в продаже")
            if alt.price_cut_pct:
                tags.append(f"снижал цену на {alt.price_cut_pct:.0%}")
            if alt.republish:
                tags.append(f"переподач {alt.republish}")
            lines.append(
                f"  • {alt.area:g} м², {alt.floor}/{alt.floors_total} — {money(alt.price)} "
                f"({k(alt.price_per_sqm)} тыс, {gap:+.0%}) · {alt.finish.value}"
            )
            lines.append(f"      {alt.seller_name or '—'} · " + " · ".join(tags or ["без истории"]))
        lines.append("")

    lines.append("Поведение конкурентов:")
    lines.append(
        f"  снижали цену {c.cutting} из {len(c.direct)} · "
        f"переподавали {c.republishing} из {len(c.direct)}"
    )
    if c.median_exposure is not None:
        ours = a.days_on_market
        tail = f", у нас {ours} дн." if ours is not None else ""
        lines.append(f"  медиана экспозиции {c.median_exposure:.0f} дн.{tail}")

    if c.rivals:
        lines.append("")
        lines.append("Кто ещё продаёт в этом ЖК:")
        for r in c.rivals[:5]:
            lines.append(
                f"  {r.name:<16} {r.lots} лот(ов) · медиана {k(r.median_ppsm)} тыс · "
                f"снижали {r.cutting}/{r.lots}"
                + (f" · экспозиция {r.median_exposure:.0f} дн." if r.median_exposure else "")
            )
    return "\n".join(lines)


def render_rivals(rivals: list[Rival]) -> str:
    if not rivals:
        return "Конкурентов с несколькими лотами в выгрузках нет."
    lines = [
        f"{'продавец':<18}{'ЖК':<22}{'лотов':>6}{'медиана':>10}{'мин':>9}"
        f"{'снижали':>9}{'переподач':>11}{'экспозиция':>12}",
        "-" * 97,
    ]
    for r in rivals:
        lines.append(
            f"{r.name[:17]:<18}{r.complex_name[:21]:<22}{r.lots:>6}"
            f"{k(r.median_ppsm):>10}{k(r.cheapest_ppsm):>9}"
            f"{r.cutting_share:>8.0%}{r.republishing:>11}"
            + (f"{r.median_exposure:>11.0f} дн." if r.median_exposure else f"{'—':>12}")
        )
    return "\n".join(lines)
