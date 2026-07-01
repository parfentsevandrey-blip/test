"""Renders a ranked shortlist as Markdown or CSV."""

from __future__ import annotations

import csv
import io
from datetime import date
from typing import List, Optional

from .config import Config
from .pipeline import RankedOffer
from .valuation.scoring import LABELS


def _money(x: float) -> str:
    return f"{x:,.0f}".replace(",", " ")


def _floor(offer) -> str:
    if offer.floor is not None and offer.floors_total is not None:
        return f"{offer.floor}/{offer.floors_total}"
    return "—"


def _rooms(offer) -> str:
    return "студия" if offer.is_studio else str(offer.rooms)


def to_markdown(ranked: List[RankedOffer], cfg: Config, as_of: Optional[date] = None) -> str:
    as_of = as_of or date.today()
    lines = [
        f"# Недооценённые квартиры в Москве (ЖК не старше {cfg.market.min_built_year} года постройки)",
        "",
        f"Найдено предложений: {len(ranked)}",
        "",
        "| # | ЖК | Адрес | Комнат | S, м² | Этаж | Цена | ₽/м² | Медиана ЖК ₽/м² | Скидка | Уверенность | Аналогов | Вердикт | Комментарии | Ссылка |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for i, r in enumerate(ranked, 1):
        o, v = r.offer, r.verdict
        fresh = ""
        days = o.days_on_market(as_of)
        if days is not None and days <= cfg.scoring.fresh_listing_days:
            fresh = " (новое)"
        median_str = _money(v.median_comp_ppsqm) if v.median_comp_ppsqm is not None else "—"
        lines.append(
            "| {i} | {jk} | {addr} | {rooms} | {area:.1f} | {floor} | {price} ₽ | {ppsqm} | {median} | "
            "{discount:+.1f}% | {conf:.0%} | {n} ({tier}) | {label} | {notes} | [ссылка]({url}){fresh} |".format(
                i=i,
                jk=o.residential_complex or "—",
                addr=o.address or "—",
                rooms=_rooms(o),
                area=o.area_total,
                floor=_floor(o),
                price=_money(o.price),
                ppsqm=_money(o.price_per_sqm),
                median=median_str,
                discount=v.discount_pct,
                conf=v.confidence,
                n=v.comparables_count,
                tier=v.comparables_tier or "-",
                label=v.label_ru,
                notes="; ".join(v.notes) if v.notes else "—",
                url=o.url,
                fresh=fresh,
            )
        )
    return "\n".join(lines)


def to_csv(ranked: List[RankedOffer], as_of: Optional[date] = None) -> str:
    as_of = as_of or date.today()
    buf = io.StringIO()
    writer = csv.writer(buf, delimiter=";")
    writer.writerow([
        "residential_complex", "address", "rooms", "area_total_sqm", "floor", "floors_total",
        "price_rub", "price_per_sqm", "median_comp_price_per_sqm", "discount_pct", "confidence",
        "comparables_count", "comparables_tier", "verdict", "verdict_ru", "notes", "days_on_market", "url",
    ])
    for r in ranked:
        o, v = r.offer, r.verdict
        writer.writerow([
            o.residential_complex or "",
            o.address or "",
            "студия" if o.is_studio else o.rooms,
            f"{o.area_total:.1f}",
            o.floor if o.floor is not None else "",
            o.floors_total if o.floors_total is not None else "",
            f"{o.price:.0f}",
            f"{o.price_per_sqm:.0f}",
            f"{v.median_comp_ppsqm:.0f}" if v.median_comp_ppsqm is not None else "",
            f"{v.discount_pct:.1f}",
            f"{v.confidence:.2f}",
            v.comparables_count,
            v.comparables_tier or "",
            v.label,
            LABELS[v.label],
            "; ".join(v.notes),
            o.days_on_market(as_of) if o.days_on_market(as_of) is not None else "",
            o.url,
        ])
    return buf.getvalue()
