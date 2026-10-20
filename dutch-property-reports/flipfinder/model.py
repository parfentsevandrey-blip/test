"""Флип за 12 месяцев: купить свободное здание, сдать, продать сданным инвестору.

Правила, которые модель соблюдает (Нидерланды, 2026):

* Налог на передачу 10,4 % цены (ст. 14 WBR) платит каждый покупатель: мы при покупке
  и инвестор при продаже. Поэтому инвестор считает цену «k.k.»: аренда / доходность,
  налог он платит сверху. Льгота ст. 13 WBR (налог только с прироста) действует при
  перепродаже в течение 6 месяцев — при продаже через 12 месяцев её нет.
* Новое здание, которым ещё не пользовались (до 2 лет после первого использования),
  продаётся с НДС и без налога на передачу (ст. 15, ч. 1, п. a WBR). НДС с цены
  возвращается декларацией, если здание сдаётся с НДС. Инвестор через 12 месяцев
  платит налог 10,4 % как обычно: от начала аренды прошло больше 6 месяцев.
* Аренда с НДС (ст. 11 Wet OB, арендатор — плательщик НДС не меньше чем на 90 %)
  возвращает НДС на ремонт и услуги. Продажа сданного здания — передача действующего
  бизнеса (ст. 37d Wet OB): без НДС, обязательства по пересчёту НДС переходят к инвестору.
* Плата за землю (эрфпахт) и взносы ассоциации собственников — расходы владельца:
  мы платим их, пока здание у нас, инвестор вычитает их из аренды при оценке.
* Налог на прибыль компании (VPB 2026): 19 % до € 200.000, 25,8 % сверх. Налог на
  передачу и все расходы входят в стоимость и уменьшают облагаемую прибыль.
* Покупка за свои средства, без кредита.
"""
from __future__ import annotations

from dataclasses import asdict, dataclass, replace

from flipfinder import config as C


@dataclass
class Deal:
    ask: float                    # цена продавца
    area: float                   # м²
    rent: float                   # рыночная аренда в год, без НДС
    exit_yield: float             # доходность, при которой инвестор купит (k.k.)
    price: float | None = None    # наша цена покупки; по умолчанию — цена продавца
    new_build: bool = False       # новое здание: покупка с НДС, без налога на передачу
    shell: bool = False           # голая коробка: нужна отделка под арендатора
    owner_costs: float = 0.0      # эрфпахт, взносы ассоциации, € в год
    lease_month: int = C.LEASE_MONTH
    rent_free: int = C.RENT_FREE
    horizon: int = C.HORIZON
    vat_lease: bool = C.VAT_LEASE

    def at(self, **kw) -> "Deal":
        return replace(self, **kw)


def capex(d: Deal) -> float:
    per_m2, fixed = (C.CAPEX_M2_SHELL, C.CAPEX_FIXED_SHELL) if d.shell else (C.CAPEX_M2, C.CAPEX_FIXED)
    net = per_m2 * d.area + fixed
    return net if d.vat_lease else net * (1 + C.VAT)


def vpb(profit: float) -> float:
    if profit <= 0:
        return 0.0
    (step, low), (_, high) = C.VPB
    return min(profit, step) * low + max(0.0, profit - step) * high


def run(d: Deal) -> dict:
    price = d.ask if d.price is None else d.price
    n = d.horizon
    tax = 0.0 if d.new_build else price * C.OVB
    buy = {"Цена": price, "Налог на передачу 10,4 %": tax, "Нотариус": C.NOTARY,
           "Проверка здания и документов": C.DD, "Консультант": price * C.ADVISORY}
    works = capex(d)
    first_paid = d.lease_month + d.rent_free
    rows = []
    for m in range(n + 1):
        r = {"m": m, "buy": 0.0, "vat": 0.0, "works": 0.0, "upkeep": 0.0, "letting": 0.0, "rent": 0.0,
             "sale": 0.0, "sale_cost": 0.0}
        if m == 0:
            r["buy"] = -sum(buy.values())
            if d.new_build:
                r["vat"] = -price * C.VAT
        else:
            if m == 2 and d.new_build and d.vat_lease:
                r["vat"] = price * C.VAT            # НДС с цены вернулся декларацией
            if m <= 2:
                r["works"] = -works / 2
            r["upkeep"] = -(price * C.HOLDING + d.owner_costs) / 12 - (C.VOID_M2 * d.area / 12 if m < d.lease_month else 0)
            if m == d.lease_month:
                r["letting"] = -(d.rent * C.LETTING_FEE + C.MARKETING)
            if m >= first_paid:
                r["rent"] = d.rent / 12
        rows.append(r)
    # цена инвестора: (аренда − расходы владельца) / доходность; месяцы без платы после продажи он вычтет
    free_left = max(0, first_paid - 1 - n) * d.rent / 12
    sale = (d.rent - d.owner_costs) / d.exit_yield - free_left
    sale_cost = sale * C.SALE_FEE + C.SALE_FIXED
    rows[n]["sale"], rows[n]["sale_cost"] = sale, -sale_cost
    cum, peak = 0.0, 0.0
    for r in rows:
        r["flow"] = sum(v for k, v in r.items() if k != "m")
        cum += r["flow"]
        r["cum"] = cum
        peak = min(peak, cum)
    tot = {k: sum(r[k] for r in rows) for k in ("buy", "vat", "works", "upkeep", "letting", "rent", "sale", "sale_cost")}
    costs = -(tot["buy"] + tot["vat"] + tot["works"] + tot["upkeep"] + tot["letting"] + tot["sale_cost"])
    income = tot["rent"] + tot["sale"]
    profit = income - costs
    tax_profit = vpb(profit)
    return {
        "deal": asdict(d), "price": price, "buy": buy, "buy_total": sum(buy.values()), "works": works,
        "rows": rows, "tot": tot, "costs": costs, "income": income, "rent_collected": tot["rent"],
        "sale": sale, "sale_cost": sale_cost, "investor_tax": sale * C.OVB, "investor_total": sale * (1 + C.OVB),
        "profit": profit, "margin": profit / costs, "vpb": tax_profit, "net": profit - tax_profit,
        "peak": -peak, "return": (profit - tax_profit) / -peak if peak else 0.0,
    }


def solve(d: Deal, target: float = 0.0, metric: str = "profit", lo: float = 0.2, hi: float = 2.0) -> float | None:
    """Цена покупки, при которой metric = target (бисекция по доле цены продавца)."""
    def f(k):
        return run(d.at(price=d.ask * k))[metric] - target
    a, b = f(lo), f(hi)
    if a * b > 0:
        return None
    for _ in range(80):
        mid = (lo + hi) / 2
        fm = f(mid)
        if a * fm <= 0:
            hi, b = mid, fm
        else:
            lo, a = mid, fm
    return d.ask * (lo + hi) / 2


def evaluate(d: Deal) -> dict:
    """Всё, что нужно для отбора и для отчёта: цена «в ноль», предложение, сценарии."""
    breakeven = solve(d)
    m10 = solve(d, 0.10, "margin")
    offer = min(d.ask, (m10 // 5000) * 5000) if m10 else None
    at_ask = run(d.at(price=d.ask))
    base = run(d.at(price=offer)) if offer else at_ask
    p = base["price"]
    what_if = {
        "Аренда на 10 % ниже": run(d.at(price=p, rent=d.rent * 0.9))["profit"],
        "Инвестор хочет на 0,5 % больше": run(d.at(price=p, exit_yield=d.exit_yield + 0.005))["profit"],
        "Арендатор на 3 месяца позже": run(d.at(price=p, lease_month=d.lease_month + 3))["profit"],
        "Ремонт в 2 раза дороже": run(d.at(price=p))["profit"] - capex(d),
        "Арендатор уже в 2-м месяце": run(d.at(price=p, lease_month=2))["profit"],
    }
    ladder = []
    for k in (1.0, 0.95, 0.90, 0.85, 0.80):
        r = run(d.at(price=round(d.ask * k / 5000) * 5000))
        ladder.append({"price": r["price"], "discount": 1 - r["price"] / d.ask, "profit": r["profit"], "net": r["net"]})
    return {
        "breakeven": breakeven, "breakeven_discount": (1 - breakeven / d.ask) if breakeven else None,
        "m10": m10, "offer": offer, "at_ask": at_ask, "base": base, "what_if": what_if, "ladder": ladder,
        "passes": breakeven is not None and (1 - breakeven / d.ask) <= C.THRESHOLD,
    }
