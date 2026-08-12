"""Полный внутренний отчёт по лоту — и короткая рекомендация наружу.

Порядок разбора повторяет логику аналитика и важен сам по себе: каждый следующий блок
отвечает на вопрос, который поставил предыдущий.

  1. Позиция внутри дома      — переоценён ли лот относительно соседей?
  2. Проверка ценой этажа     — если нет, то цена корректна или соседи просто выше?
  3. Бюджет въезда            — сколько покупатель реально платит, а не что в объявлении
  4. Сравнение с локацией     — что он купит на те же деньги в соседних проектах
  5. Причины                  — почему лот стоит, если цена внутри дома правильная
  6. Рекомендация             — какая цена и почему именно она

Ключевой вывод, ради которого всё это считается: цена может быть корректной внутри
своего дома и при этом нерыночной в локации. Тогда «снизить и подождать» не работает,
и надо либо менять бюджет въезда, либо продавать другой продукт.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from statistics import median

from .floor_model import FloorPremium, fit_floor_premium
from .location import (
    Project,
    ProjectStats,
    load_projects,
    move_in_budget,
    normalise,
    project_stats,
    weighted_ppsm,
)
from .models import Apartment, Comp
from .pricing import weighted_percentile

# Сопоставимыми считаем лоты той же комнатности в полосе площади вокруг нашей.
AREA_BAND = 0.22
# Шаг цены за один пересмотр — тот же ограничитель, что и в ядре вердикта.
MAX_CUT_STEP = 0.07
# Шаг «переговорной» цены. Ценами вида 53 174 000 ₽ не торгуют.
GRID = 500_000


def _round_down(value: float) -> int:
    return int(value // GRID) * GRID


def _round_up(value: float) -> int:
    return int(-(-value // GRID)) * GRID


@dataclass
class Peer:
    """Сосед по дому, приведённый к нашему этажу."""

    comp: Comp
    adjusted_ppsm: float
    floor_delta: int


@dataclass
class Alternative:
    """Что покупатель купит вместо нашего лота на те же деньги."""

    comp: Comp
    project: Project | None
    move_in: int
    move_in_ppsm: float
    area_delta: float
    budget_delta: int
    ready: bool


@dataclass
class Recommendation:
    """То единственное, что уходит наружу: цена и почему именно она."""

    price: int
    corridor: tuple[int, int]
    delta_pct: float
    move_in: int
    confidence: float
    headline: str
    reasons: list[str]
    caveats: list[str] = field(default_factory=list)
    # Какое из ограничений определило цену: «цель», «пол», «шаг» или «нет данных».
    # Это и есть ответ на вопрос «почему именно столько», поэтому хранится отдельно
    # от текста: по нему строится и объяснение, и проверка расчёта.
    binding: str = ""


@dataclass
class LotReport:
    """Внутренний отчёт. Наружу отдаётся только .recommendation."""

    apartment: Apartment
    project: Project | None

    house_lots: int = 0
    house_avg_ppsm: float = 0.0
    rank_in_house: int | None = None
    peers: list[Peer] = field(default_factory=list)
    rank_among_peers: int | None = None
    floor_premium: FloorPremium | None = None
    parity_ppsm: float | None = None
    parity_gap: float | None = None

    finishing_cost: int = 0
    move_in: int = 0
    move_in_ppsm: float = 0.0

    location: list[ProjectStats] = field(default_factory=list)
    location_rank: int | None = None
    alternatives: list[Alternative] = field(default_factory=list)

    reasons: list[str] = field(default_factory=list)
    recommendation: Recommendation | None = None

    @property
    def has_house_data(self) -> bool:
        return self.house_lots > 0


def build_report(
    apartment: Apartment,
    house_comps: list[Comp],
    location_comps: dict[str, list[Comp]] | None = None,
    projects: dict[str, Project] | None = None,
    price_list: list[Comp] | None = None,
) -> LotReport:
    """Собирает полный отчёт. house_comps — лоты нашего ЖК, location_comps — соседние.

    price_list — вся экспозиция дома, включая прайс застройщика. Он исключён из
    house_comps намеренно (первичка смещает коридор перепродажи), но именно на нём
    меряется надбавка за этаж: там все условия равны, кроме этажа. Без него регрессия
    считается по десятку разнородных частных объявлений и уходит на конфиг.
    """
    projects = projects if projects is not None else load_projects()
    project = projects.get(normalise(apartment.complex_name))
    r = LotReport(apartment=apartment, project=project)

    # --- 3. Бюджет въезда считаем сразу: он нужен всем блокам ниже ------------
    r.finishing_cost = (project or Project(name="")).cost_to_finish(apartment.finish)
    r.move_in = move_in_budget(apartment.price, apartment.area, apartment.finish, project)
    r.move_in_ppsm = r.move_in / apartment.area

    # --- 1. Позиция внутри дома ----------------------------------------------
    if house_comps:
        r.house_lots = len(house_comps) + 1  # соседи плюс мы
        r.house_avg_ppsm = weighted_ppsm(house_comps + [_as_comp(apartment)])
        cheaper = sum(1 for c in house_comps if c.price_per_sqm < apartment.price_per_sqm)
        r.rank_in_house = r.house_lots - cheaper

        lo, hi = apartment.area * (1 - AREA_BAND), apartment.area * (1 + AREA_BAND)
        comparable = [c for c in house_comps if lo <= c.area <= hi]

        # --- 2. Проверка ценой этажа -----------------------------------------
        r.floor_premium = fit_floor_premium(price_list or house_comps, fallback_rate=0.0078)
        r.peers = sorted(
            (
                Peer(
                    comp=c,
                    adjusted_ppsm=r.floor_premium.adjust(
                        c.price_per_sqm, c.floor, apartment.floor
                    ),
                    floor_delta=apartment.floor - c.floor,
                )
                for c in comparable
            ),
            key=lambda p: -p.adjusted_ppsm,
        )
        if r.peers:
            r.rank_among_peers = (
                sum(1 for p in r.peers if p.adjusted_ppsm > apartment.price_per_sqm) + 1
            )
            # Паритет считается по МЕДИАНЕ соседей, приведённых к нашему этажу.
            # Сравнение с ближайшим по цене соседом выглядит естественнее, но оно
            # самоподтверждающееся: чем сильнее лот переоценён, тем вероятнее рядом
            # найдётся такой же переоценённый сосед, и завышение подтвердит само себя.
            # На эталонном доме медиана и ближайший сосед совпадают до рубля, так что
            # устойчивость здесь ничего не стоит.
            r.parity_ppsm = median(p.adjusted_ppsm for p in r.peers)
            r.parity_gap = apartment.price_per_sqm / r.parity_ppsm - 1

    # --- 4. Локация ------------------------------------------------------------
    if location_comps:
        lo, hi = apartment.area * (1 - AREA_BAND), apartment.area * (1 + AREA_BAND)
        for name, comps in location_comps.items():
            proj = projects.get(normalise(name)) or Project(name=name)
            stats = project_stats(comps, proj, area_band=(lo, hi))
            if stats:
                r.location.append(stats)
        r.location.sort(key=lambda s: -s.move_in_ppsm)
        r.location_rank = (
            sum(1 for s in r.location if s.move_in_ppsm > r.move_in_ppsm) + 1
        )
        r.alternatives = _alternatives(apartment, location_comps, projects, r.move_in)

    r.reasons = _reasons(r)
    r.recommendation = _recommend(r)
    return r


def _as_comp(a: Apartment) -> Comp:
    """Наш лот как аналог — чтобы попасть в средневзвешенную по дому."""
    return Comp(
        source="own",
        external_id=a.id,
        complex_name=a.complex_name,
        address=a.address,
        rooms=a.rooms,
        area=a.area,
        floor=a.floor,
        floors_total=a.floors_total,
        price=a.price,
        finish=a.finish,
        has_parking=a.has_parking,
        same_complex=True,
    )


def _alternatives(
    apartment: Apartment,
    location_comps: dict[str, list[Comp]],
    projects: dict[str, Project],
    our_budget: int,
) -> list[Alternative]:
    """Готовые квартиры, которые покупатель рассматривает вместо нашей."""
    lo, hi = apartment.area * (1 - AREA_BAND), apartment.area * (1 + AREA_BAND)
    out: list[Alternative] = []
    for name, comps in location_comps.items():
        proj = projects.get(normalise(name)) or Project(name=name)
        for c in comps:
            if not (lo <= c.area <= hi):
                continue
            budget = move_in_budget(c.price, c.area, c.finish, proj)
            out.append(
                Alternative(
                    comp=c,
                    project=proj,
                    move_in=budget,
                    move_in_ppsm=budget / c.area,
                    area_delta=c.area - apartment.area,
                    budget_delta=budget - our_budget,
                    ready=proj.ready,
                )
            )
    return sorted(out, key=lambda a: a.move_in)


def _reasons(r: LotReport) -> list[str]:
    """Почему лот стоит — то, что в эталонном отчёте разложено по причинам."""
    a = r.apartment
    out: list[str] = []

    if r.parity_gap is not None:
        if abs(r.parity_gap) <= 0.02:
            out.append(
                f"Внутри дома цена выставлена корректно: медиана соседей, приведённая к нашему "
                f"этажу, {r.parity_ppsm / 1000:.0f} тыс ₽/м² против наших "
                f"{a.price_per_sqm / 1000:.0f} — паритет {r.parity_gap:+.2%}. "
                f"Снижать «чтобы догнать своих» не нужно."
            )
        elif r.parity_gap > 0.02:
            out.append(
                f"Внутри дома лот дороже соседей на {r.parity_gap:+.1%} после приведения "
                f"к нашему этажу — это снимается ценой."
            )
        else:
            out.append(
                f"Внутри дома лот дешевле соседей на {abs(r.parity_gap):.1%} после "
                f"приведения к этажу — запас по цене внутри дома есть."
            )

    if r.finishing_cost:
        out.append(
            f"Покупатель считает не {a.price / 1e6:.1f}, а {r.move_in / 1e6:.1f} млн ₽: "
            f"доводка {r.finishing_cost / 1000:.0f} тыс ₽/м² — это "
            f"{(r.move_in - a.price) / 1e6:.1f} млн ₽ сверху и полгода-год работ. "
            f"В сравнении с готовыми квартирами лот участвует по "
            f"{r.move_in_ppsm / 1000:.0f} тыс ₽/м²."
        )

    ready_cheaper = [x for x in r.alternatives if x.ready and x.budget_delta < 0]
    if ready_cheaper:
        # Самый прямой конкурент — БЛИЖАЙШИЙ снизу, а не самый дешёвый. Квартира за
        # 31 млн при нашем бюджете 69 млн — это другой продукт для другого покупателя;
        # выбор происходит там, где бюджеты почти сошлись.
        best = max(ready_cheaper, key=lambda x: x.move_in)
        out.append(
            f"В локации {len(ready_cheaper)} готовых лотов дешевле нашего бюджета въезда. "
            f"Самый прямой: {best.comp.area:g} м² в «{best.project.name}» за "
            f"{best.move_in / 1e6:.1f} млн ₽ — на {abs(best.budget_delta) / 1e6:.1f} млн "
            f"дешевле и с ключами на руках."
        )

    if r.location and r.location_rank:
        # Знаменатель — соседние проекты плюс наш собственный, как в таблице локации.
        out.append(
            f"По метру готовой квартиры лот {r.location_rank}-й из {len(r.location) + 1} "
            f"проектов локации: {r.move_in_ppsm / 1000:.0f} тыс ₽/м²."
        )

    if r.peers and len(r.peers) >= 3:
        cheaper_peers = [p for p in r.peers if p.comp.price < r.apartment.price]
        if len(cheaper_peers) >= 2:
            out.append(
                f"В доме одновременно {len(r.peers)} сопоставимых лотов, из них "
                f"{len(cheaper_peers)} дешевле нашего по бюджету — покупатель, "
                f"выбравший дом, выбирает между ними."
            )
    return out


def _recommend(r: LotReport) -> Recommendation:
    """Из отчёта — в одну цену с обоснованием.

    Логика повторяет вывод эталонного отчёта: цена внутри дома может быть корректной,
    и тогда двигать её нужно не «до рынка дома», а до уровня, на котором бюджет въезда
    становится конкурентоспособным в локации. Нижняя граница — цена соседей по дому,
    приведённая к нашему этажу: ниже неё это уже не рыночная корректировка, а демпинг
    в экспозиции, где застройщик всё равно держит прайс.
    """
    a = r.apartment
    reasons: list[str] = []
    caveats: list[str] = []

    if not r.has_house_data:
        return Recommendation(
            price=a.price,
            corridor=(a.price, a.price),
            delta_pct=0.0,
            move_in=r.move_in,
            confidence=0.15,
            headline="Данных недостаточно — нужна выгрузка по ЖК",
            reasons=["Нет выгрузки по этому ЖК: сравнивать не с чем."],
            caveats=["Сделайте выгрузку расширением по этому ЖК и локации."],
            binding="нет данных",
        )

    # Пол: ниже цены соседей по дому, приведённой к нашему этажу, опускаться незачем.
    floor_limit = (
        min(p.adjusted_ppsm for p in r.peers) * a.area if r.peers else a.price * 0.85
    )

    # Ориентиров для снижения ровно два, и они отвечают на разные вопросы.
    targets: list[tuple[str, float]] = []

    # ЛОКАЦИЯ. Не медиана всех дешёвых лотов района, а БЛИЖАЙШИЙ конкурент, которому
    # мы проигрываем: готовая квартира с бюджетом чуть ниже нашего. Медиана увела бы
    # цель на десятки миллионов вниз и сделала бы обоснование бессмысленным —
    # «опуститься до 44 млн» при цене лота 57.
    rival: Alternative | None = None
    ready_cheaper = [x for x in r.alternatives if x.ready and x.budget_delta < 0]
    if ready_cheaper:
        rival = max(ready_cheaper, key=lambda x: x.move_in)
        targets.append(("цель", rival.move_in - (r.move_in - a.price)))

    # ДОМ. Лот дороже соседей после приведения к нашему этажу — этого достаточно для
    # снижения само по себе, без всякой локации: покупатель, уже выбравший дом,
    # сравнивает нас именно с ними и видит переплату ни за что.
    #
    # Порогом служит верхний квартиль приведённых цен, а не медиана. Медиана
    # означала бы «все лоты дома должны стоить одинаково»: она стягивает к середине
    # и лот с видом, и лот с удачной планировкой — то есть ровно те преимущества,
    # которых нет в выгрузке и которые поправками не описаны. Выше p75 — это уже не
    # преимущество, а выпад из распределения, и он снимается ценой. Тот же принцип,
    # что и в ядре вердикта: «выше p75 → опустить к верхней границе нормы».
    if r.peers:
        house_p75 = weighted_percentile(
            [p.adjusted_ppsm for p in r.peers], [1.0] * len(r.peers), 75
        )
        if a.price_per_sqm > house_p75:
            targets.append(("дом", house_p75 * a.area))

    # Из двух ориентиров берётся более требовательный: цена, конкурентная в локации,
    # автоматически конкурентна и внутри дома, но не наоборот.
    label, target = min(targets, key=lambda x: x[1]) if targets else ("", a.price)

    # Ограничитель шага — тот же, что в ядре вердикта: не больше 7% за пересмотр.
    step_limit = a.price * (1 - MAX_CUT_STEP)
    raw = min(max(target, floor_limit, step_limit), a.price)

    # Какое из ограничений в итоге определило цену — это и есть объяснение.
    # Отдельно разбирается случай, когда снижать не от чего: без ориентира target
    # равен текущей цене и формально «побеждает» в максимуме, но объяснять цену
    # несуществующим конкурентом нельзя — это было бы придуманным обоснованием.
    if raw >= a.price:
        price, binding = a.price, ("уже конкурентна" if targets else "нет ориентира")
    else:
        binding = max(
            ((label, target), ("пол", floor_limit), ("шаг", step_limit)), key=lambda x: x[1]
        )[0]
        # Округление — в сторону сработавшего ограничения, иначе «красивая» цена
        # ломает то самое утверждение, которым она обоснована. Цель («опустить
        # бюджет въезда ниже конкурента») — верхняя граница, и округлять её надо
        # вниз, иначе обоснование становится ложным. Пол и шаг — нижние границы,
        # и округляются вверх, иначе снижение выходит за собственный ограничитель.
        # Из-за шага сетки любая граница может быть задета в пределах 500 тыс ₽ —
        # это единственная допустимая погрешность, и она проверяется тестом.
        price = _round_down(raw) if binding in {"цель", "дом"} else _round_up(raw)

    corridor = (
        int(round(max(floor_limit, step_limit) / 500_000) * 500_000),
        int(round(min(a.price, max(price, floor_limit) * 1.04) / 500_000) * 500_000),
    )
    if corridor[0] > corridor[1]:
        corridor = (corridor[1], corridor[0])

    delta = price / a.price - 1

    # --- обоснование ---------------------------------------------------------
    # Позиция внутри дома проговаривается всегда: это первое, что спросит собственник,
    # и ответ «отклонений не найдено» без числа звучит как отговорка.
    if r.parity_gap is not None:
        if abs(r.parity_gap) <= 0.02:
            reasons.append(
                f"Внутри дома цена корректна (паритет {r.parity_gap:+.2%} с медианой соседей, "
                f"приведённой к {a.floor}-му этажу), поэтому двигать её «до рынка дома» "
                f"не нужно."
            )
        elif r.parity_gap > 0.02:
            reasons.append(
                f"Лот дороже дома: медиана сопоставимых соседей после приведения к "
                f"{a.floor}-му этажу — {r.parity_ppsm / 1000:.0f} тыс ₽/м², мы просим "
                f"{a.price_per_sqm / 1000:.0f} — переплата {r.parity_gap:+.1%} ни за что. "
                f"Покупатель, выбравший дом, видит это первым."
            )
        else:
            reasons.append(
                f"Лот дешевле дома на {abs(r.parity_gap):.1%} после приведения к "
                f"{a.floor}-му этажу — внутри ЖК снижать нечего, запас по цене есть."
            )
    if rival is not None and binding == "цель":
        reasons.append(
            f"Снижение до {price / 1e6:.1f} млн выводит бюджет въезда "
            f"({r.move_in / 1e6:.1f} → {move_in_budget(price, a.area, a.finish, r.project) / 1e6:.1f} млн) "
            f"ниже ближайшего конкурента: {rival.comp.area:g} м² в «{rival.project.name}» "
            f"за {rival.move_in / 1e6:.1f} млн с ключами на руках."
        )
    elif rival is not None:
        reasons.append(
            f"Ближайший конкурент — {rival.comp.area:g} м² в «{rival.project.name}» за "
            f"{rival.move_in / 1e6:.1f} млн готовыми против нашего бюджета въезда "
            f"{r.move_in / 1e6:.1f} млн. Чтобы обойти его по бюджету, цену пришлось бы "
            f"опустить до {target / 1e6:.1f} млн — это ниже допустимого."
        )

    if binding == "дом":
        reasons.append(
            f"{price / 1e6:.1f} млн — это верхняя граница нормального разброса цен в доме "
            f"(p75), приведённая к нашему этажу. Снижение убирает выпад из "
            f"распределения, но оставляет лот в верхней части дома, а не в середине."
        )
    elif binding == "пол":
        reasons.append(
            f"Ниже {floor_limit / 1e6:.1f} млн не идём: там лот уходит под цену соседей "
            f"по дому, приведённую к нашему этажу — это демпинг, а не корректировка."
        )
    elif binding == "шаг":
        reasons.append(
            f"Цена определена ограничителем шага: {MAX_CUT_STEP:.0%} за пересмотр. "
            f"Рынок допускает больше, но снижать нужно поэтапно, с проверкой отклика."
        )
        caveats.append(
            "Следующий шаг — через 3–4 недели, если поток обращений не изменится."
        )
    if not r.location:
        # Без выгрузок соседних проектов проверен только дом. Этого достаточно, чтобы
        # не ошибиться внутри ЖК, и недостаточно, чтобы утверждать, что цена рыночная:
        # лот бывает корректен среди соседей и неконкурентоспособен в районе.
        caveats.append(
            "Проверен только собственный дом: выгрузок по соседним проектам локации нет. "
            "Сделайте выгрузку расширением по конкурирующим ЖК района — без неё "
            "конкурентоспособность бюджета въезда не проверяется."
        )
    if r.finishing_cost:
        caveats.append(
            "Цена — не единственный рычаг. Дизайн-проект с фиксированной сметой "
            f"превращает «{a.price / 1e6:.1f} млн плюс сколько-то и когда-то» в "
            f"«{a.price / 1e6:.1f} + {(r.move_in - a.price) / 1e6:.1f} млн и восемь месяцев» "
            "— это меняет сравнение в нашу пользу, не трогая цену."
        )

    confidence = _confidence(r)
    if delta > -0.005:
        headline = f"Держать {a.price / 1e6:.1f} млн ₽"
    else:
        headline = f"Снизить до {price / 1e6:.1f} млн ₽ ({delta:+.1%})"

    return Recommendation(
        price=price,
        corridor=corridor,
        delta_pct=delta,
        move_in=move_in_budget(price, a.area, a.finish, r.project),
        confidence=confidence,
        headline=headline,
        reasons=reasons or ["Существенных отклонений не найдено — цену можно держать."],
        caveats=caveats,
        binding=binding,
    )


def _confidence(r: LotReport) -> float:
    """Уверенность падает, когда какого-то слоя анализа не было."""
    score = 0.0
    score += min(1.0, len(r.peers) / 6) * 0.35
    score += 0.25 if r.floor_premium and r.floor_premium.measured else 0.05
    score += min(1.0, len(r.location) / 4) * 0.25
    score += 0.15 if r.alternatives else 0.0
    return round(min(score, 0.95), 2)
