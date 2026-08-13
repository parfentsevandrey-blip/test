"""Треугольники и математика арбитражного цикла.

Треугольный арбитраж — единственный вид арбитража, доступный внутри одной
спотовой биржи: цепочка из трёх сделок возвращает в исходную валюту, и если
произведение курсов после комиссий больше единицы, разница остаётся у нас.

    USDT ──купить BTC──► BTC ──купить ETH за BTC──► ETH ──продать за USDT──► USDT

Здесь нет ни плеча, ни переводов между биржами, ни ожидания движения цены:
позиция открыта секунды и закрывается в той же валюте, с которой начали.

Два момента, на которых обычно ломаются такие расчёты:

1. **Лучшая цена — не вся цена.** На верхнем уровне стакана стоит ограниченный
   объём. Заявка крупнее исполняется по нескольким уровням, средняя цена хуже,
   и «прибыль», посчитанная по лучшей цене, исчезает. Поэтому объём считается
   проходом по стакану вглубь (`OrderBook.buy_with` / `sell_amount`).

2. **Комиссия берётся на каждом шаге.** Три сделки — три комиссии. При базовой
   ставке Gate.io 0.2% цикл обязан дать больше 0.6% валового расхождения,
   иначе он убыточен ещё до учёта проскальзывания.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Iterable, Optional, Sequence

from ..types import OrderBook, PairSpec, Side


@dataclass(frozen=True)
class Leg:
    """Одно звено цикла: превратить `from_cur` в `to_cur` через пару `symbol`."""

    symbol: str
    side: Side
    from_cur: str
    to_cur: str

    def __str__(self) -> str:
        return f"{self.from_cur}->{self.to_cur} ({self.side.value} {self.symbol})"


@dataclass(frozen=True)
class Triangle:
    """Замкнутый цикл из трёх сделок, начинающийся и кончающийся в `base`."""

    base: str
    legs: tuple[Leg, Leg, Leg]

    @property
    def path(self) -> str:
        return " -> ".join([self.base] + [leg.to_cur for leg in self.legs])

    @property
    def symbols(self) -> tuple[str, str, str]:
        return tuple(leg.symbol for leg in self.legs)  # type: ignore[return-value]

    @property
    def key(self) -> str:
        return "|".join(f"{leg.symbol}:{leg.side.value}" for leg in self.legs)


@dataclass
class Opportunity:
    """Результат оценки треугольника на конкретном стакане и объёме."""

    triangle: Triangle
    start_amount: Decimal
    end_amount: Decimal
    profit_pct: Decimal
    leg_prices: list[Decimal] = field(default_factory=list)
    depth_limited: bool = False
    note: str = ""

    @property
    def profit_abs(self) -> Decimal:
        return self.end_amount - self.start_amount

    @property
    def is_profitable(self) -> bool:
        return self.profit_pct > 0

    def __str__(self) -> str:
        return (
            f"{self.triangle.path}: {self.profit_pct:+.4%} "
            f"({self.profit_abs:+.4f} {self.triangle.base} с {self.start_amount})"
        )


def make_leg(pair: PairSpec, have: str, want: str) -> Optional[Leg]:
    """Построить звено, если пара действительно связывает эти валюты."""
    if pair.base == want and pair.quote == have:
        return Leg(pair.symbol, Side.BUY, have, want)
    if pair.base == have and pair.quote == want:
        return Leg(pair.symbol, Side.SELL, have, want)
    return None


def find_triangles(
    pairs: Sequence[PairSpec],
    base: str = "USDT",
    *,
    allowed_middle: Optional[Iterable[str]] = None,
) -> list[Triangle]:
    """Все циклы base -> A -> B -> base по доступным парам.

    `allowed_middle` ограничивает набор промежуточных валют — обычно им задают
    ликвидные BTC/ETH/USDC, потому что треугольники через неликвид дают
    бумажную прибыль, которую невозможно исполнить.
    """
    by_symbol = {p.symbol: p for p in pairs}

    # Валюты, торгуемые напрямую против базовой.
    direct: dict[str, PairSpec] = {}
    for pair in pairs:
        if pair.quote == base:
            direct[pair.base] = pair
        elif pair.base == base:
            direct[pair.quote] = pair

    allowed = set(allowed_middle) if allowed_middle is not None else None
    triangles: list[Triangle] = []
    seen: set[str] = set()

    for pair in pairs:
        a, b = pair.base, pair.quote
        # Средняя пара не должна касаться базовой валюты, а обе её стороны
        # обязаны торговаться против базовой — иначе цикл не замкнётся.
        if base in (a, b) or a not in direct or b not in direct:
            continue
        if allowed is not None and not (a in allowed or b in allowed):
            continue

        for first, second in ((a, b), (b, a)):
            legs = _build_cycle(base, first, second, direct, by_symbol)
            if legs and legs.key not in seen:
                seen.add(legs.key)
                triangles.append(legs)

    return triangles


def _build_cycle(
    base: str,
    first: str,
    second: str,
    direct: dict[str, PairSpec],
    by_symbol: dict[str, PairSpec],
) -> Optional[Triangle]:
    """base -> first -> second -> base, если все три звена существуют."""
    leg1 = make_leg(direct[first], base, first)
    leg3 = make_leg(direct[second], second, base)
    if leg1 is None or leg3 is None:
        return None

    middle = by_symbol.get(f"{first}_{second}") or by_symbol.get(f"{second}_{first}")
    if middle is None:
        return None
    leg2 = make_leg(middle, first, second)
    if leg2 is None:
        return None

    return Triangle(base=base, legs=(leg1, leg2, leg3))


def evaluate(
    triangle: Triangle,
    books: dict[str, OrderBook],
    start_amount: Decimal,
    fee: Decimal,
) -> Optional[Opportunity]:
    """Прогнать цикл по стаканам и посчитать, что вернётся в базовой валюте.

    Возвращает None, если для какой-то пары нет стакана. `depth_limited`
    означает, что видимой глубины не хватило на весь объём — такую возможность
    исполнять нельзя, её нужно пересчитать на меньшем размере.
    """
    amount = start_amount
    prices: list[Decimal] = []
    depth_limited = False

    for leg in triangle.legs:
        book = books.get(leg.symbol)
        if book is None:
            return None

        if leg.side is Side.BUY:
            got, spent = book.buy_with(amount)
            if got <= 0:
                return None
            if spent < amount:
                depth_limited = True
            prices.append(spent / got)  # средняя цена исполнения
        else:
            got, sold = book.sell_amount(amount)
            if got <= 0:
                return None
            if sold < amount:
                depth_limited = True
            prices.append(got / sold)

        # Комиссия Gate.io списывается с получаемой валюты на каждом шаге.
        amount = got * (1 - fee)

    profit_pct = (amount - start_amount) / start_amount if start_amount > 0 else Decimal(0)
    return Opportunity(
        triangle=triangle,
        start_amount=start_amount,
        end_amount=amount,
        profit_pct=profit_pct,
        leg_prices=prices,
        depth_limited=depth_limited,
        note="не хватило глубины стакана" if depth_limited else "",
    )


def best_size(
    triangle: Triangle,
    books: dict[str, OrderBook],
    max_amount: Decimal,
    fee: Decimal,
    *,
    steps: int = 12,
) -> Optional[Opportunity]:
    """Наибольший объём до `max_amount`, который полностью влезает в стаканы.

    Прибыль в процентах падает с ростом заявки, поэтому ищем не максимум
    доходности, а максимальный исполнимый размер: бинарный поиск по границе
    глубины. Объём, упирающийся в конец видимого стакана, отбрасываем — по нему
    исполнение непредсказуемо.
    """
    full = evaluate(triangle, books, max_amount, fee)
    if full is None:
        return None
    if not full.depth_limited:
        return full

    low, high = Decimal(0), max_amount
    best: Optional[Opportunity] = None
    for _ in range(steps):
        mid = (low + high) / 2
        if mid <= 0:
            break
        probe = evaluate(triangle, books, mid, fee)
        if probe is None:
            break
        if probe.depth_limited:
            high = mid
        else:
            best = probe
            low = mid
    return best


def check_minimums(
    triangle: Triangle,
    specs: dict[str, PairSpec],
    opportunity: Opportunity,
) -> str:
    """Проверить, что все три сделки проходят по минимальному объёму биржи.

    Возвращает пустую строку, если всё в порядке, иначе — причину отказа.
    Мелкий цикл легко упирается в min_quote_amount на втором звене, и тогда
    первая сделка исполнится, а вторая — нет.
    """
    amount = opportunity.start_amount
    for leg, price in zip(triangle.legs, opportunity.leg_prices):
        spec = specs.get(leg.symbol)
        if spec is None:
            return f"нет параметров пары {leg.symbol}"
        if price <= 0:
            return f"нулевая цена на {leg.symbol}"

        base_amount = amount / price if leg.side is Side.BUY else amount
        quote_amount = amount if leg.side is Side.BUY else amount * price

        if spec.min_base_amount and base_amount < spec.min_base_amount:
            return (
                f"{leg.symbol}: объём {base_amount:.8f} меньше минимума "
                f"{spec.min_base_amount} {spec.base}"
            )
        if spec.min_quote_amount and quote_amount < spec.min_quote_amount:
            return (
                f"{leg.symbol}: сумма {quote_amount:.4f} меньше минимума "
                f"{spec.min_quote_amount} {spec.quote}"
            )
        amount = base_amount if leg.side is Side.BUY else quote_amount
    return ""
