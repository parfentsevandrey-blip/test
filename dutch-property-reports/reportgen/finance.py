"""Финансовые показатели инвестиционного объекта и их форматирование.

Модуль считает то, что раньше выписывалось руками в «specs» карточки:
затраты на приобретение (цена k.k. плюс налог и расходы по сделке),
доходность BAR и NAR, цену и аренду за квадратный метр.

Исходные данные — блок «financials» карточки объекта. Функции чистые:
ни сети, ни файлов, ни глобального состояния. Любое отсутствующее поле
даёт None в зависимых показателях, деления на ноль не происходит.
"""

from __future__ import annotations

# --- Ставки сделки ---------------------------------------------------------
OVB_RATE = 0.104            # overdrachtsbelasting: налог на переход права, нежилая, 2026
CLOSING_EXTRA_RATE = 0.012  # нотариус, кадастр, due diligence — ориентировочно от цены
DEFAULT_OPEX_RATIO = 0.10   # расходы собственника от аренды, если в карточке не заданы

# --- Форматирование --------------------------------------------------------
NBSP = "\u00A0"  # неразрывный пробел между числом и знаком «€», «%», «м²»
EMPTY = "—"      # заглушка вместо незаданного значения


# --------------------------------------------------------------------------
# помощники
# --------------------------------------------------------------------------
def _num(value: object) -> float | None:
    """Число или None: строки, None и прочий мусор из JSON отбрасываются."""
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    return float(value)


def _div(numerator: float | None, denominator: float | None) -> float | None:
    """Деление, устойчивое к None и нулю в знаменателе."""
    if numerator is None or not denominator:
        return None
    return numerator / denominator


def _fmt_number(value: float, decimals: int) -> str:
    """Европейский формат: тысячи через точку, дробная часть через запятую."""
    # f-строка даёт англоязычный вид «975,000.00» — меняем разделители местами
    text = f"{value:,.{decimals}f}"
    return text.replace(",", "\x00").replace(".", ",").replace("\x00", ".")


def _scenarios(items: object, acquisition: float | None, opex_ratio: float) -> list[dict]:
    """Доходность по альтернативным сценариям аренды из карточки."""
    result: list[dict] = []
    for item in items if isinstance(items, list) else []:
        if not isinstance(item, dict):
            continue
        rent = _num(item.get("rent_eur_year"))
        net_rent = None if rent is None else rent * (1.0 - opex_ratio)
        result.append(
            {
                "label": item.get("label") or "",
                "rent": rent,
                "bar_acquisition": _div(rent, acquisition),
                "nar_acquisition": _div(net_rent, acquisition),
            }
        )
    return result


# --------------------------------------------------------------------------
# расчёты
# --------------------------------------------------------------------------
def acquisition_cost(
    price: float | None,
    ovb_rate: float = OVB_RATE,
    extra_rate: float = CLOSING_EXTRA_RATE,
) -> float | None:
    """Полные затраты покупателя на приобретение объекта.

    Цена в листингах указана «k.k.» (kosten koper): налог на переход права
    и расходы по сделке платит покупатель сверх запрашиваемой цены.
    """
    price = _num(price)
    if price is None:
        return None
    return price * (1.0 + ovb_rate + extra_rate)


def metrics(fin: dict | None) -> dict:
    """Показатели по блоку «financials»; недостающие поля дают None."""
    fin = fin or {}
    price = _num(fin.get("price_eur"))
    rent = _num(fin.get("rent_eur_year"))
    market_rent = _num(fin.get("market_rent_eur_year"))
    lettable = _num(fin.get("lettable_sqm"))

    opex_ratio = _num(fin.get("opex_ratio"))
    if opex_ratio is None:
        opex_ratio = DEFAULT_OPEX_RATIO

    acquisition = acquisition_cost(price)
    ovb = None if price is None else price * OVB_RATE
    # NAR считается от чистой аренды: валовая минус расходы собственника
    net_rent = None if rent is None else rent * (1.0 - opex_ratio)

    return {
        "price": price,
        "acquisition": acquisition,
        "ovb": ovb,
        # BAR к цене — как в листинге, BAR к затратам — то, что получает покупатель
        "bar_price": _div(rent, price),
        "bar_acquisition": _div(rent, acquisition),
        "nar_acquisition": _div(net_rent, acquisition),
        "price_per_sqm": _div(price, lettable),
        "rent_per_sqm": _div(rent, lettable),
        # потенциал объекта: рыночная аренда к тем же затратам приобретения
        "market_bar_acquisition": _div(market_rent, acquisition),
        "scenarios": _scenarios(fin.get("scenarios"), acquisition, opex_ratio),
    }


# --------------------------------------------------------------------------
# форматирование
# --------------------------------------------------------------------------
def fmt_eur(value: float | None, decimals: int = 0) -> str:
    """Сумма в евро: «€ 975.000»."""
    number = _num(value)
    if number is None:
        return EMPTY
    return f"€{NBSP}{_fmt_number(number, decimals)}"


def fmt_pct(value: float | None, decimals: int = 1) -> str:
    """Доля в процентах: 0.068 → «6,8 %»."""
    number = _num(value)
    if number is None:
        return EMPTY
    return f"{_fmt_number(number * 100.0, decimals)}{NBSP}%"


def fmt_sqm(value: float | None) -> str:
    """Площадь: «616 м²»."""
    number = _num(value)
    if number is None:
        return EMPTY
    return f"{_fmt_number(number, 0)}{NBSP}м²"


def fmt_int(value: float | None) -> str:
    """Целое число с разделителем тысяч: «1.375»."""
    number = _num(value)
    if number is None:
        return EMPTY
    return _fmt_number(number, 0)
