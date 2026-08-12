"""Два вывода из одного отчёта.

Наружу — только рекомендованная цена и почему именно она. Полный разбор считается
всегда, но показывается по запросу: менеджеру нужна цена и три аргумента, а не
двадцать таблиц. Проверить, откуда взялась цифра, можно флагом --report.
"""

from __future__ import annotations

from .lotreport import LotReport


def money(rub: float) -> str:
    return f"{rub / 1e6:.1f} млн ₽"


def thousands(v: float) -> str:
    return f"{v / 1000:,.0f}".replace(",", " ")


def recommendation(r: LotReport) -> str:
    """То, что видит менеджер: цена и обоснование."""
    a = r.apartment
    rec = r.recommendation
    lines = [
        f"{a.complex_name.strip()} · {a.area:g} м² · {a.floor}/{a.floors_total} · {a.finish.value}",
        f"Сейчас: {money(a.price)} ({thousands(a.price_per_sqm)} тыс ₽/м²)",
        "",
        f"➜ {rec.headline}",
    ]
    if rec.corridor[0] != rec.corridor[1]:
        lines.append(
            f"  коридор торга {money(rec.corridor[0])} — {money(rec.corridor[1])}"
        )
    if r.finishing_cost:
        lines.append(f"  бюджет въезда для покупателя: {money(rec.move_in)}")
    lines.append(f"  уверенность {rec.confidence:.0%}")

    lines += ["", "Почему:"]
    lines += [f"  • {x}" for x in rec.reasons]
    if rec.caveats:
        lines += ["", "Важно:"]
        lines += [f"  ! {x}" for x in rec.caveats]
    return "\n".join(lines)


def full_report(r: LotReport) -> str:
    """Полный внутренний разбор — для проверки, откуда взялась рекомендация."""
    a = r.apartment
    L: list[str] = [
        "=" * 96,
        f"АНАЛИТИКА ПО ЛОТУ · {a.complex_name.strip()} · {a.address}",
        f"{a.rooms}-комнатная {a.area:g} м², этаж {a.floor}/{a.floors_total}, {a.finish.value}",
    ]
    if r.project:
        p = r.project
        L.append(
            f"Проект: {p.klass or '—'} · {p.developer or '—'} · {p.delivery or '—'}"
            + (f" · м. {p.metro}, {p.metro_minutes} мин" if p.metro else "")
        )
    L += [
        "",
        f"Цена {money(a.price)} · {thousands(a.price_per_sqm)} тыс ₽/м²",
    ]

    # --- 1. Позиция внутри дома ---
    if r.has_house_data:
        L += [
            "",
            "─── 1. ПОЗИЦИЯ ВНУТРИ ДОМА " + "─" * 60,
            f"В экспозиции ЖК: {r.house_lots} лотов, средневзвешенная "
            f"{thousands(r.house_avg_ppsm)} тыс ₽/м²",
            f"Наш лот {r.rank_in_house}-й по цене метра из {r.house_lots} "
            f"({a.price_per_sqm / r.house_avg_ppsm - 1:+.1%} к средней по дому)",
        ]
        if r.rank_among_peers:
            L.append(
                f"Среди сопоставимых по метражу: {r.rank_among_peers}-й из {len(r.peers)}"
            )

    # --- 2. Проверка ценой этажа ---
    if r.peers and r.floor_premium:
        L += [
            "",
            "─── 2. ПРОВЕРКА ЦЕНОЙ ЭТАЖА " + "─" * 59,
            f"Цены соседей приведены к нашему {a.floor}-му этажу. {r.floor_premium.summary}",
            "",
            f"  {'площадь':>8}{'этаж':>7}{'цена':>12}{'₽/м²':>10}{'Δ эт.':>7}"
            f"{'приведено':>12}  отделка",
        ]
        for p in r.peers:
            L.append(
                f"  {p.comp.area:>7.1f}м{p.comp.floor:>4}/{p.comp.floors_total:<2}"
                f"{p.comp.price / 1e6:>10.1f}м{thousands(p.comp.price_per_sqm):>10}"
                f"{p.floor_delta:>+7}{thousands(p.adjusted_ppsm):>12}  {p.comp.finish.value}"
            )
        L.append(
            f"  {'НАШ ЛОТ':>8}{a.floor:>4}/{a.floors_total:<2}{a.price / 1e6:>10.1f}м"
            f"{thousands(a.price_per_sqm):>10}{'—':>7}{thousands(a.price_per_sqm):>12}"
        )
        if r.parity_gap is not None:
            L.append(
                f"\nМедиана соседей после приведения: {thousands(r.parity_ppsm)} тыс ₽/м², "
                f"мы просим {thousands(a.price_per_sqm)} — расхождение {r.parity_gap:+.2%}"
            )

    # --- 3. Бюджет въезда ---
    L += [
        "",
        "─── 3. БЮДЖЕТ ВЪЕЗДА " + "─" * 66,
    ]
    if r.finishing_cost:
        L += [
            f"Цена лота {money(a.price)} + доводка {thousands(r.finishing_cost)} тыс ₽/м² "
            f"({money(r.move_in - a.price)}) = {money(r.move_in)}",
            f"Метр готовой квартиры: {thousands(r.move_in_ppsm)} тыс ₽/м²",
            "Покупатель сравнивает именно это число, а не цену в объявлении.",
        ]
    else:
        L.append(f"Лот готов к заселению — бюджет въезда равен цене: {money(r.move_in)}")

    # --- 4. Локация ---
    if r.location:
        L += [
            "",
            "─── 4. ЛОКАЦИЯ: МЕТР ГОТОВОЙ КВАРТИРЫ " + "─" * 49,
            f"  {'проект':<26}{'класс':<10}{'лотов':>6}{'ср. ₽/м²':>11}"
            f"{'готовой':>11}  срок сдачи",
        ]
        shown = False
        for s in r.location:
            if not shown and s.move_in_ppsm < r.move_in_ppsm:
                L.append(
                    f"  {'▶ НАШ ЛОТ':<26}{(r.project.klass if r.project else '—'):<10}"
                    f"{1:>6}{thousands(a.price_per_sqm):>11}{thousands(r.move_in_ppsm):>11}"
                )
                shown = True
            L.append(
                f"  {s.project.name[:25]:<26}{s.project.klass[:9]:<10}{s.lots:>6}"
                f"{thousands(s.avg_ppsm):>11}{thousands(s.move_in_ppsm):>11}  "
                f"{s.project.delivery or '—'}"
            )
        if not shown:
            L.append(
                f"  {'▶ НАШ ЛОТ':<26}{(r.project.klass if r.project else '—'):<10}"
                f"{1:>6}{thousands(a.price_per_sqm):>11}{thousands(r.move_in_ppsm):>11}"
            )
        L.append(
            f"\nПо метру готовой квартиры лот {r.location_rank}-й из "
            f"{len(r.location) + 1} проектов локации."
        )

    # --- 5. Что покупают вместо нас ---
    ready = [x for x in r.alternatives if x.ready][:8]
    if ready:
        L += [
            "",
            "─── 5. ЧТО ПОКУПАЮТ ВМЕСТО НАШЕГО ЛОТА " + "─" * 48,
            f"  {'проект':<26}{'площадь':>9}{'этаж':>7}{'бюджет':>11}"
            f"{'разница':>12}  отделка",
        ]
        for alt in ready:
            L.append(
                f"  {(alt.project.name if alt.project else '—')[:25]:<26}"
                f"{alt.comp.area:>8.1f}м{alt.comp.floor:>4}/{alt.comp.floors_total:<2}"
                f"{alt.move_in / 1e6:>10.1f}м"
                f"{alt.budget_delta / 1e6:>+11.1f}м  {alt.comp.finish.value}"
            )

    # --- 6. Причины ---
    if r.reasons:
        L += ["", "─── 6. ПОЧЕМУ ЛОТ СТОИТ " + "─" * 62]
        L += [f"  • {x}" for x in r.reasons]

    # --- 7. Рекомендация ---
    L += ["", "─── 7. РЕКОМЕНДАЦИЯ " + "─" * 66, recommendation(r)]
    return "\n".join(L)
