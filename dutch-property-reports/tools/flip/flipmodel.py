"""Флип-модель склада: покупка свободного объекта, сдача, продажа сданного инвестору.

Помесячный денежный поток, налог на передачу (10,4 %) с льготой ст. 13 WBR при перепродаже
в пределах 6 месяцев, финансирование, налог на прибыль (VPB), IRR, кратность капитала,
предельная цена покупки и точка безубыточности по доходности выхода.
"""
from copy import deepcopy

BASE = {
    # покупка
    "price": 1_000_000,          # цена покупки k.k. (расходы покупателя сверху)
    "area": 1000,                # арендуемая площадь, м²
    "ovb": 0.104,                # налог на передачу нежилой недвижимости 2026
    "notary": 3_500,             # нотариус и кадастр
    "dd": 7_500,                 # техническая, экологическая и юридическая проверка
    "valuation": 3_500,          # оценка для банка
    "advisory_pct": 0.01,        # консультант по покупке
    # подготовка
    "capex_m2": 25,              # косметика, €/м²
    "capex_fixed": 2_500,        # резерв
    "capex_months": 2,
    "capex_vat": 0.21,           # НДС на ремонт — расход, пока аренда без НДС
    # сдача
    "rent_m2": 100,              # рыночная ставка, €/м²/год без НДС
    "lease_month": 4,            # месяц начала аренды (с покупки)
    "rent_free": 3,              # арендные каникулы, мес
    "letting_fee": 0.15,         # комиссия брокера от годовой аренды
    "marketing": 1_500,
    "lease_years": 5,
    # содержание
    "holding_pct": 0.006,        # налог OZB, страховка, мелкий ремонт, % цены в год
    "void_m2": 3.0,              # энергия и охрана в простое, €/м²/год
    # финансирование
    "ltv": 0.60,
    "rate": 0.05128,             # Euribor 3M 2,628 % + маржа 2,5 %
    "bank_fee": 0.01,
    # продажа
    "sale_month": 12,
    "exit_bar": 0.075,           # валовая доходность покупателя k.k.
    "sale_fee": 0.0125,          # брокер на продаже
    "sale_fixed": 2_500,         # юрист, маркетинг продажи
    "art13_share": 0.5,          # доля экономии покупателя по ст. 13, которую забираем в цене
    # новое помещение: покупка и продажа с НДС, без налога на передачу (ст. 15 WBR)
    "vat_route": False,
    "vat_rate": 0.21,            # НДС с цены покупки: платим у нотариуса, возвращаем декларацией
    "vat_refund_month": 2,
    "vat_window": 6,             # продажа не позже 6 месяцев после начала аренды — покупатель без 10,4 %
    "hold_fixed": 0,             # эрфпахт, взносы ассоциации собственников, € в год
    "exit_deduct": 0,            # что инвестор вычитает из аренды при оценке: плата за эрфпахт, € в год
    # налог на прибыль
    "vpb_low": 0.19, "vpb_high": 0.258, "vpb_step": 200_000,
}


def run(p_in: dict | None = None, **kw) -> dict:
    p = deepcopy(BASE)
    p.update(p_in or {})
    p.update(kw)
    n = int(p["sale_month"])
    price, area = p["price"], p["area"]
    rent = p["rent_m2"] * area
    loan = p["ltv"] * price
    acq = {
        "Цена покупки": price,
        "Налог на передачу 10,4 %": price * p["ovb"],
        "Нотариус и кадастр": p["notary"],
        "Проверка объекта": p["dd"],
        "Оценка для банка": p["valuation"] if loan else 0,
        "Консультант по покупке": price * p["advisory_pct"],
        "Комиссия банка": loan * p["bank_fee"],
    }
    capex = (p["capex_m2"] * area + p["capex_fixed"]) * (1 + p["capex_vat"])
    first_paid = p["lease_month"] + p["rent_free"]           # первый оплачиваемый месяц
    rows = []
    for m in range(0, n + 1):
        r = {"m": m, "acq": 0.0, "capex": 0.0, "hold": 0.0, "void": 0.0, "letting": 0.0,
             "rent": 0.0, "interest": 0.0, "sale": 0.0, "sale_cost": 0.0, "loan": 0.0, "vat": 0.0}
        if p["vat_route"] and m == 0:
            r["vat"] -= price * p["vat_rate"]
        if p["vat_route"] and m == min(p["vat_refund_month"], n):
            r["vat"] += price * p["vat_rate"]
        if m == 0:
            r["acq"] = -sum(acq.values())
            r["loan"] = loan
        else:
            if m <= p["capex_months"]:
                r["capex"] = -capex / p["capex_months"]
            r["hold"] = -price * p["holding_pct"] / 12 - p["hold_fixed"] / 12
            if m < p["lease_month"]:
                r["void"] = -p["void_m2"] * area / 12
            if m == p["lease_month"]:
                r["letting"] = -(rent * p["letting_fee"] + p["marketing"])
            if m >= first_paid:
                r["rent"] = rent / 12
            r["interest"] = -loan * p["rate"] / 12
        if m == n:
            r["loan"] -= loan
        rows.append(r)

    # цена продажи: аренда / доходность k.k.; незакончившиеся каникулы покупатель вычитает
    value_let = (rent - p["exit_deduct"]) / p["exit_bar"]
    free_left = max(0, first_paid - 1 - n) * rent / 12
    art13 = n <= 6 and not p["vat_route"]
    # аренда начинается в начале месяца lease_month: 6 месяцев истекают в конце месяца lease_month − 1 + 6
    late = p["vat_route"] and n > p["lease_month"] - 1 + p["vat_window"]
    if late:
        # акт позже 6 месяцев после начала аренды: инвестор платит 10,4 % и снижает цену на столько же
        value_let = value_let / 1.104
    # по ст. 13 покупатель платит 10,4 % только с прироста; при той же его полной цене
    # мы можем поднять цену на ovb·P/(1+ovb); забираем долю art13_share
    uplift = p["art13_share"] * p["ovb"] * price / (1 + p["ovb"]) if art13 else 0.0
    sale = value_let - free_left + uplift
    sale_cost = sale * p["sale_fee"] + p["sale_fixed"]
    rows[n]["sale"] = sale
    rows[n]["sale_cost"] = -sale_cost
    for r in rows:
        r["equity"] = sum(v for k, v in r.items() if k not in ("m",))
    flows = [r["equity"] for r in rows]

    tot = {k: sum(r[k] for r in rows) for k in rows[0] if k not in ("m", "equity")}
    cost_total = -(tot["acq"] + tot["capex"] + tot["hold"] + tot["void"] + tot["letting"]
                   + tot["interest"] + tot["sale_cost"])
    income = tot["sale"] + tot["rent"]
    profit = income - cost_total
    vpb = vpb_tax(profit, p)
    # пиковый капитал: максимальная накопленная потребность в деньгах
    cum, peak = 0.0, 0.0
    for f in flows:
        cum += f
        peak = min(peak, cum)
    equity = -peak
    irr_m = irr(flows)
    after = flows[:]
    after[-1] -= vpb
    irr_m_after = irr(after)
    if p["vat_route"]:
        buyer_ovb = 0.104 * sale if late else 0.0
    else:
        buyer_ovb = p["ovb"] * (sale - price) if art13 else p["ovb"] * sale
    return {
        "p": p, "rows": rows, "acq": acq, "capex": capex, "rent": rent, "first_paid": first_paid,
        "value_let": value_let, "free_left": free_left, "art13": art13, "uplift": uplift, "late": late,
        "sale": sale, "sale_cost": sale_cost, "loan": loan, "tot": tot,
        "cost_total": cost_total, "income": income, "profit": profit,
        "margin": profit / cost_total, "vpb": vpb, "profit_after": profit - vpb,
        "equity": equity, "roe": profit / equity, "roe_after": (profit - vpb) / equity,
        "multiple": (equity + profit - vpb) / equity,
        "irr": annual(irr_m), "irr_after": annual(irr_m_after),
        "buyer_ovb": buyer_ovb, "buyer_total": sale + buyer_ovb,
        "buyer_giy": rent / (sale + buyer_ovb),
        "giy_on_cost": rent / (price + acq["Налог на передачу 10,4 %"] + capex),
        "yoc_price": rent / price,
    }


def vpb_tax(profit: float, p: dict) -> float:
    if profit <= 0:
        return 0.0
    low = min(profit, p["vpb_step"])
    return low * p["vpb_low"] + max(0.0, profit - p["vpb_step"]) * p["vpb_high"]


def irr(flows, lo=-0.99, hi=1.0):
    def npv(r):
        return sum(f / (1 + r) ** i for i, f in enumerate(flows))
    if npv(lo) * npv(hi) > 0:
        return None
    for _ in range(200):
        mid = (lo + hi) / 2
        if npv(lo) * npv(mid) <= 0:
            hi = mid
        else:
            lo = mid
    return (lo + hi) / 2


def annual(r_m):
    return None if r_m is None else (1 + r_m) ** 12 - 1


def solve(key: str, target: float, metric: str, lo: float, hi: float, p: dict | None = None, **kw) -> float:
    """Значение параметра key, при котором metric = target (бисекция)."""
    def f(x):
        return run(p, **{**kw, key: x})[metric] - target
    a, b = f(lo), f(hi)
    if a * b > 0:
        return None
    for _ in range(100):
        mid = (lo + hi) / 2
        fm = f(mid)
        if a * fm <= 0:
            hi, b = mid, fm
        else:
            lo, a = mid, fm
    return (lo + hi) / 2


def hold(r: dict, *, years: int = 5, opex_pct: float = 0.125, refi_ltv: float = 0.55,
         rate: float | None = None, growth: float = 0.02) -> dict:
    """План Б: объект не продан — держим сданным и рефинансируем под стоимость со сдачей."""
    p = r["p"]
    rate = p["rate"] if rate is None else rate
    rent = r["rent"]
    opex = rent * opex_pct            # налог OZB владельца, страховка, ремонт, управление
    noi = rent - opex - p["hold_fixed"]   # плата за землю и взносы владельца — сверх обычных расходов
    invested = (r["acq"]["Цена покупки"] + r["acq"]["Налог на передачу 10,4 %"]
                + sum(v for k, v in r["acq"].items() if k not in ("Цена покупки", "Налог на передачу 10,4 %"))
                + r["capex"] + rent * p["letting_fee"] + p["marketing"])
    loan = refi_ltv * r["value_let"]
    interest = loan * rate
    equity = invested - loan
    cash = noi - interest
    return {"rent": rent, "opex": opex, "noi": noi, "invested": invested, "noi_yield": noi / invested,
            "loan": loan, "interest": interest, "equity": equity, "cash": cash, "coc": cash / equity,
            "value_5y": noi * (1 + growth) ** years / p["exit_bar"]}


def split_sale(r: dict, *, price_m2: float = 850.0, split_cost: float = 90_000, months: int = 10,
               fee: float = 0.02, fixed: float = 5_000, units: int = 2) -> dict:
    """План Б: разделить здание на две части и продать пользователям без арендатора."""
    p = r["p"]
    area = p["area"]
    gross = price_m2 * area
    sale_cost = gross * fee + fixed * units
    acq = sum(r["acq"].values())
    loan = r["loan"]
    interest = loan * p["rate"] * months / 12
    holding = p["price"] * p["holding_pct"] * months / 12 + p["void_m2"] * area * months / 12
    cost = acq + r["capex"] + split_cost + interest + holding + sale_cost
    profit = gross - cost
    return {"gross": gross, "sale_cost": sale_cost, "split_cost": split_cost, "interest": interest,
            "holding": holding, "cost": cost, "profit": profit, "margin": profit / cost,
            "months": months, "price_m2": price_m2}
