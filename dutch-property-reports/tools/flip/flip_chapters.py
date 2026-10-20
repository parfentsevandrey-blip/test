"""Главы флип-модели для карточки объекта: одинаковые таблицы для любого объекта.

Тексты, которые зависят от самого здания (почему подходит, аренда, продажа, риски),
приходят словарём T; всё, что считается моделью, собирается здесь из её JSON.
"""


def eur(v, digits=0):
    s = f"{abs(v):,.{digits}f}".replace(",", " ").replace(".", ",").replace(" ", ".")
    return ("−" if v < 0 else "") + "€ " + s


def r100(v):
    return eur(round(v / 100) * 100)


def tsd(v):
    return ("−" if v < 0 else "+") + f"{abs(v) / 1000:,.1f}".replace(",", " ").replace(".", ",").replace(" ", ".")


def pct(v, digits=1):
    return "—" if v is None else f"{v * 100:.{digits}f} %".replace(".", ",").replace("-", "−")


def per100(v):
    return eur(v * 100, 2)


def signed(v):
    return ("+" if v >= 0 else "") + r100(v)


def T_(title, columns, rows, **kw):
    return dict(title=title, columns=columns, rows=rows, **kw)


def months(n):
    tail = "месяц" if n % 10 == 1 and n % 100 != 11 else (
        "месяца" if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14) else "месяцев")
    return f"{n} {tail}"


def chapters(M, T, chart_dir):
    B, A, ask, price, area, rent = M["base"], M["fast"], M["ask"], M["price"], M["area"], M["rent"]
    L, bids, vat = M["labels"], M["bids"], M["route"] == "vat"
    p = B["p"]
    acq = B["acq"]
    peak_m, cum, low = 0, 0.0, 0.0
    for r in B["rows"]:
        cum += r["equity"]
        if cum < low:
            low, peak_m = cum, r["m"]
    out = []

    # ---------------------------------------------------------------- 1. итог
    summary = [
        ["Покупаем за", eur(price), eur(price)],
        ["Тратим всего: цена, отделка, комиссии, содержание" if vat else "Тратим всего: цена, налоги, ремонт, комиссии",
         eur(B["cost_total"]), eur(A["cost_total"])],
        ["Получаем аренды, пока помещение у нас", eur(B["tot"]["rent"]), eur(A["tot"]["rent"])],
        ["Продаём сданное помещение инвестору за", eur(B["sale"]), eur(A["sale"])],
        ["Зарабатываем до налога на прибыль", eur(B["profit"]), eur(A["profit"])],
        ["Заработок на каждые € 100 затрат", per100(B["margin"]), per100(A["margin"])],
        ["Налог на прибыль компании", eur(-B["vpb"]), eur(-A["vpb"])],
        ["Остаётся чистыми", eur(B["profit_after"]), eur(A["profit_after"])],
        ["Вложено своих денег на пике", eur(B["equity"]), eur(A["equity"])],
        ["Чистая прибыль на свои деньги", pct(B["roe_after"]), pct(A["roe_after"])],
        ["То же в пересчёте на год", pct(B["irr_after"]), pct(A["irr_after"])],
        ["Срок от покупки до продажи", f"{p['sale_month']} месяцев", f"{A['p']['sale_month']} месяцев"],
    ]
    vat_note = (f"«Вложено своих денег на пике» — сколько денег занято в проекте в самый затратный момент, на "
                f"{peak_m}-м месяце: цена, НДС 21 % до возврата и отделка. Покупка за свои средства, без кредита. "
                "«В пересчёте на год» — годовая доходность вложенных денег (IRR). Суммы без НДС, если не сказано иное.")
    out.append({
        "kicker": "Флип-модель", "title": T["sum_title"], "paragraphs": T["sum_paragraphs"],
        "tables": [T_(f"Итог при покупке за {eur(price)}", ["Что считаем", L["main"].capitalize(), L["alt"]],
                      summary, widths=[96, 37, 37], strong=[4, 7], note=vat_note)],
        "rows_title": "Главное для переговоров",
        "rows": [
            ["Дороже не покупать", r100(bids["12"]["breakeven"]) + " — прибыль 0"],
            ["Для прибыли 10 %", "покупать за " + r100(bids["12"]["m10"])],
            ["Наше предложение", eur(price) + (", " + pct(abs(price / ask - 1), 0) + " ниже" if price < ask else "")],
            ["Цена продавца", eur(ask) + " — " + ("прибыль " + r100(M["at_ask"]["profit"]) if M["at_ask"]["profit"] >= 0
                                                 else "убыток " + r100(-M["at_ask"]["profit"]))],
            ["Аренда не ниже", eur(bids["12"]["rent_be"] * area) + " в год"],
            ["Инвестор платит", r100(B["sale"])],
            ["На € 1 своих денег", "вернётся " + eur(B["multiple"], 2) + " чистыми"],
            ["Аренда в модели", eur(rent) + " в год"],
        ] + ([
            ["Въезд арендатора", f"{p['lease_month']}-й месяц"],
            ["Без платы", months(p["rent_free"])],
            ["Акт с инвестором", f"{p['sale_month']}-й месяц"],
            ["Крайний срок акта", f"конец {p['lease_month'] - 1 + p['vat_window']}-го месяца"],
        ] if vat else []) + T.get("sum_rows_extra", []),
    })

    # ---------------------------------------------------------------- 2. почему подходит
    out.append({"kicker": "Флип-модель", "title": T["why_title"], "paragraphs": T["why_paragraphs"],
                "tables": T.get("why_tables", []), "rows_title": T.get("why_rows_title", "В цифрах"),
                "rows": T.get("why_rows", [])})

    # ---------------------------------------------------------------- 3. аренда
    parts_rows = [[lab, f"{a:,.0f}".replace(",", ".") if a == int(a) else f"{a:.1f}".replace(".", ","),
                   eur(r_), eur(a * r_)] for lab, a, r_ in M["parts"]]
    parts_rows.append(["Итого и средняя ставка", f"{area:,}".replace(",", "."), eur(rent / area, 2), eur(rent)])
    out.append({"kicker": "Конкуренты", "title": T["rent_title"], "paragraphs": T["rent_paragraphs"],
                "tables": [T_("Аренда по частям помещения", ["Часть", "м²", "€ за м² в год", "Аренда в год"],
                              parts_rows, widths=[88, 20, 30, 32], strong=[len(parts_rows) - 1])],
                "figures": [{"path": f"{chart_dir}/rents.png", "width_mm": 170, "before": 12,
                             "caption": "Ставки из объявлений на funda in business, 20.10.2026; без НДС"}],
                "rows_title": T.get("rent_rows_title"), "rows": T.get("rent_rows", [])})

    # ---------------------------------------------------------------- 4. продажа инвестору
    out.append({"kicker": "Конкуренты", "title": T["exit_title"], "paragraphs": T["exit_paragraphs"],
                "tables": T["exit_tables"],
                "rows_title": "Как инвестор считает цену",
                "rows": [
                    ["Доходность", pct(p["exit_bar"], 2) + ": аренда / цена"],
                    ["Цена инвестора", r100(B["sale"]) + (" + НДС, без 10,4 %" if vat else " + налог 10,4 %")],
                    ["Его доходность", pct(B["buyer_giy"], 2) + " на все расходы"],
                    ["Наш предел", pct(bids["12"]["bar_be"]) + ": выше — убыток"],
                    ["Метр после сдачи", eur(B["sale"] / area)],
                    ["Метр у продавца", eur(ask / area)],
                ] + T.get("exit_rows_extra", [])})

    # ---------------------------------------------------------------- 5. затраты
    cost = [
        ["Цена покупки (vrij op naam: нотариус включён)" if vat else "Цена покупки", eur(price), eur(price)],
    ]
    if vat:
        cost.append(["НДС 21 % с цены: платим и возвращаем через 2 месяца", eur(price * p["vat_rate"]),
                     eur(price * p["vat_rate"])])
    else:
        cost.append(["Налог на передачу 10,4 %", eur(acq["Налог на передачу 10,4 %"]),
                     eur(A["acq"]["Налог на передачу 10,4 %"])])
    cost += [
        ["Проверка: техника, право, договор с застройщиком" if vat else "Проверка: техника, грунт, право",
         eur(acq["Проверка объекта"]), eur(acq["Проверка объекта"])],
        ["Консультант по покупке, 1 %", eur(acq["Консультант по покупке"]), eur(acq["Консультант по покупке"])],
        ["Отделка: тепло и охлаждение, свет, туалет, кухня, пожарная защита" if vat else "Ремонт с НДС",
         eur(-B["tot"]["capex"]), eur(-A["tot"]["capex"])],
        ["Брокер по аренде 15 % и реклама", eur(-B["tot"]["letting"]), eur(-A["tot"]["letting"])],
        ["Содержание: налог OZB, страховка" + (", " + T["hold_fixed_label"] if T.get("hold_fixed_label") else ""),
         eur(-B["tot"]["hold"] - B["tot"]["void"]), eur(-A["tot"]["hold"] - A["tot"]["void"])],
        ["Брокер и юрист на продаже", eur(-B["tot"]["sale_cost"]), eur(-A["tot"]["sale_cost"])],
        ["Все затраты (без НДС, он возвращается)" if vat else "Все затраты", eur(B["cost_total"]), eur(A["cost_total"])],
        ["Аренда до продажи", eur(B["tot"]["rent"]), eur(A["tot"]["rent"])],
        ["Цена продажи", eur(B["sale"]), eur(A["sale"])],
        ["Прибыль до налога", eur(B["profit"]), eur(A["profit"])],
        ["Налог на прибыль: 19 % до € 200.000", eur(-B["vpb"]), eur(-A["vpb"])],
        ["Прибыль после налога", eur(B["profit_after"]), eur(A["profit_after"])],
    ]
    strong = [i for i, r in enumerate(cost) if r[0].startswith(("Все затраты", "Прибыль до", "Прибыль после"))]
    out.append({"kicker": "Флип-модель", "title": "Затраты и прибыль: все статьи", "paragraphs": T["cost_paragraphs"],
                "tables": [T_("Затраты и результат", ["Статья", L["main_short"], L["alt_short"]], cost,
                              widths=[100, 35, 35], strong=strong)],
                "rows_title": "Деньги в проекте",
                "rows": [
                    ["Покупка", "за свои средства, без кредита"],
                    ["К передаче", r100(sum(acq.values()) + (price * p["vat_rate"] if vat else 0))
                     + (" с НДС" if vat else "")],
                    ["На пике", r100(B["equity"]) + f", {peak_m}-й месяц"],
                    ["НДС вернётся", r100(price * p["vat_rate"]) + f", {p['vat_refund_month']}-й месяц" if vat else "—"],
                    ["Отделка", r100(B["capex"])],
                    ["Вернётся при продаже", r100(B["sale"] - B["sale_cost"])],
                    ["Чистая прибыль", r100(B["profit_after"])],
                    ["В пересчёте на год", pct(B["irr_after"]) + " после налога"],
                ]})

    # ---------------------------------------------------------------- 6. сроки и налоги
    out.append({"kicker": "Флип-модель", "title": T.get("timing_title", "Сроки и налоги: схема с НДС"),
                "paragraphs": T["timing_paragraphs"],
                "figures": [{"path": f"{chart_dir}/timeline.png", "width_mm": 170, "before": 10}],
                "rows_title": "Налоги сделки",
                "rows": T["tax_rows"]})

    # ---------------------------------------------------------------- 7. денежный поток
    cf, cum = [], 0.0
    for r in B["rows"]:
        cum += r["equity"]
        costs = r["acq"] + r["capex"] + r["letting"]
        carry = r["hold"] + r["void"]
        cf.append([str(r["m"]), tsd(costs) if costs else "", tsd(r.get("vat", 0)) if r.get("vat") else "",
                   tsd(carry) if carry else "", tsd(r["rent"]) if r["rent"] else "",
                   tsd(r["sale"] + r["sale_cost"]) if r["sale"] else "", tsd(r["equity"]), tsd(cum)])
    out.append({"kicker": "Флип-модель", "title": "Денежный поток по месяцам",
                "paragraphs": [T["cash_paragraph"]],
                "tables": [T_("Базовый вариант, тыс. €",
                              ["Месяц", "Покупка, отделка, сдача", "НДС", "Содержание", "Аренда", "Продажа",
                               "Поток за месяц", "Накоплено"],
                              cf, widths=[13, 27, 18, 21, 18, 22, 24, 27],
                              align=["c", "r", "r", "r", "r", "r", "r", "r"], strong=[len(cf) - 1])],
                "figures": [{"path": f"{chart_dir}/cash.png", "width_mm": 170, "before": 12}],
                "rows_title": "Деньги по шагам",
                "rows": [r for r in [
                    ["Покупка, месяц 0", r100(-B["rows"][0]["equity"]) + (" с НДС" if vat else "")],
                    [f"Пик, месяц {peak_m}", r100(-low)],
                    [f"НДС назад, месяц {p['vat_refund_month']}", "+" + r100(price * p["vat_rate"])] if vat else None,
                    [f"Аренда с месяца {p['lease_month'] + p['rent_free']}",
                     "+" + r100(B["rent"] / 12) + " в месяц до налога"],
                    [f"Продажа, месяц {p['sale_month']}", "+" + r100(B["sale"] - B["sale_cost"]) + " после брокера"],
                    ["Итог до налога", r100(B["profit"])],
                    ["Налог на прибыль", eur(-B["vpb"])],
                    ["Итог после налога", r100(B["profit_after"])],
                ] if r]})

    # ---------------------------------------------------------------- 8. сценарии
    names = list(M["scenarios"])
    S = M["scenarios"]
    scen_rows = [
        ["Аренда в год"] + [eur(S[n]["rent"]) for n in names],
        ["Доходность для инвестора"] + [pct(S[n]["p"]["exit_bar"], 2) for n in names],
        ["Арендатор въезжает, месяц"] + [str(S[n]["p"]["lease_month"]) for n in names],
        ["Продажа, месяц"] + [str(S[n]["p"]["sale_month"]) for n in names],
        ["Продаём инвестору за"] + [eur(S[n]["sale"]) for n in names],
        ["Прибыль до налога"] + [eur(S[n]["profit"]) for n in names],
        ["Заработок на € 100 затрат"] + [per100(S[n]["margin"]) for n in names],
        ["Остаётся чистыми"] + [eur(S[n]["profit_after"]) for n in names],
        ["Чистыми в пересчёте на год"] + [pct(S[n]["irr_after"]) for n in names],
    ]
    heads = ["Что считаем", "Плохой", "Базовый", "Хороший", "Поздняя продажа" if vat else "Быстрый"]
    out.append({"kicker": "Флип-модель", "title": "Четыре сценария и цена покупки",
                "paragraphs": T["scen_paragraphs"],
                "tables": [T_(f"Сценарии при покупке за {eur(price)}", heads, scen_rows,
                              widths=[54, 29, 29, 29, 29], strong=[5, 7])],
                "figures": [{"path": f"{chart_dir}/ladder.png", "width_mm": 170, "before": 12}],
                "rows_title": "Прибыль до налога по сценариям",
                "rows": [[h, ("прибыль " if S[n]["profit"] >= 0 else "убыток ") + r100(abs(S[n]["profit"]))]
                         for h, n in zip(heads[1:], names)]})

    # ---------------------------------------------------------------- 9. что будет, если
    base_bar = p["exit_bar"]
    sb = [[pct(r["bar"], 2) + (" — наша оценка" if abs(r["bar"] - base_bar) < 1e-9 else ""), r100(r["sale"]),
           signed(r["profit"])] for r in M["sens_bar"]]
    sb_hi = [i for i, r in enumerate(M["sens_bar"]) if abs(r["bar"] - base_bar) < 1e-9]
    sr = [["наша оценка" if abs(r["k"] - 1) < 1e-9 else f"на {abs(r['k'] - 1) * 100:.0f} % "
           + ("ниже" if r["k"] < 1 else "выше"), r100(r["rent"]), r100(r["sale"]), signed(r["profit"])]
          for r in M["sens_rent"]]
    sr_hi = [i for i, r in enumerate(M["sens_rent"]) if abs(r["k"] - 1) < 1e-9]
    gl = M["grid_lease_sale"]
    sales = gl["sales"]
    lease_rows = []
    for lm, row in zip(gl["leases"], gl["cells"]):
        cells = dict(zip(sales, row))
        lease_rows.append([f"на {lm}-м месяце"] + [
            (signed(cells[s]["profit"]) + (" *" if cells[s].get("late") else "")) if cells.get(s) else "—"
            for s in sales])
    base_row = gl["leases"].index(p["lease_month"]) if p["lease_month"] in gl["leases"] else None
    out.append({"kicker": "Флип-модель", "title": "Что будет, если расчёт не сойдётся",
                "paragraphs": T.get("sens_paragraphs") or [
                    "Прибыль держится на трёх допущениях: сколько заплатит инвестор, какая будет аренда и когда "
                    "въедет арендатор. Ниже — что станет с прибылью, если одно из них окажется другим; всё "
                    f"остальное — как в базовом расчёте: покупка за {eur(price)} и {L['main']}.",
                    "Инвестор считает цену так: годовая аренда, делённая на доходность, которую он хочет получить. "
                    f"Чем больше процентов он хочет, тем меньше платит: при аренде {eur(rent)} и "
                    f"{pct(base_bar, 2)} — {r100(rent / base_bar)}, при {pct(base_bar + 0.0075, 2)} — "
                    f"{r100(rent / (base_bar + 0.0075))}.",
                ],
                "tables": [
                    T_("Если инвестор захочет другую доходность",
                       ["Инвестор хочет получать в год", "Значит, заплатит", "Наша прибыль"],
                       sb, widths=[70, 50, 50], highlight=sb_hi, align=["l", "r", "r"]),
                    T_("Если аренда окажется другой", ["Аренда", "В год", "Инвестор заплатит", "Наша прибыль"],
                       sr, widths=[50, 36, 42, 42], highlight=sr_hi, align=["l", "r", "r", "r"], before=12),
                    T_("Если арендатор въедет позже: прибыль до налога",
                       ["Арендатор въезжает"] + [f"продаём на {s}-м мес." for s in sales],
                       lease_rows, widths=[38] + [26.4] * len(sales),
                       highlight=[base_row] if base_row is not None else [],
                       align=["l"] + ["r"] * len(sales), before=12,
                       note=("* — акт у нотариуса позже 6 месяцев после начала аренды: инвестор платит налог 10,4 % "
                             "и на столько же снижает цену." if vat else
                             "Продажа через 6 месяцев — с экономией покупателя на налоге по статье 13.")),
                ],
                "rows_title": "Когда уходим в убыток",
                "rows": [
                    ["Цена покупки", "дороже " + r100(bids["12"]["breakeven"])],
                    ["Цена от инвестора", "ниже " + r100(rent / bids["12"]["bar_be"])],
                    ["Аренда", "меньше " + r100(bids["12"]["rent_be"] * area) + " в год"],
                    ["Доходность инвестора", "выше " + pct(bids["12"]["bar_be"], 2)],
                ]})

    # ---------------------------------------------------------------- 10. план Б и риски
    H = M["hold"]
    late = S.get("Поздняя продажа")
    plan_rows = [
        ["Держать сданным", f"вложено {r100(H['invested'])}", f"{r100(H['noi'])} в год чистыми, {pct(H['noi_yield'])}"],
    ]
    if late:
        plan_rows.append(["Продать на 12-м месяце, инвестор платит 10,4 %", f"цена {r100(late['sale'])}",
                          ("прибыль " if late["profit"] >= 0 else "убыток ") + r100(abs(late["profit"]))])
    plan_rows.append(["Продать без арендатора по цене покупки", f"цена {r100(price)}",
                      "убыток " + r100(-M["resale_vacant"]["profit"])])
    plan_rows += T.get("plan_rows_extra", [])
    out.append({"kicker": "План Б", "title": "Если продать быстро не получается",
                "paragraphs": T["planb_paragraphs"],
                "tables": [T_("Варианты", ["Вариант", "Деньги", "Результат"], plan_rows, widths=[70, 42, 58]),
                           T_("Риски и что делаем", ["Риск", "Что делаем"], T["risks"], widths=[58, 112],
                              before=12, align=["l", "l"])],
                "rows_title": "Что проверить до покупки", "rows": T["checks"]})

    # дополнения к главам по номеру: абзацы, строки, таблицы
    for i, add in T.get("extra", {}).items():
        ch = out[int(i)]
        ch["paragraphs"] = ch.get("paragraphs", []) + add.get("paragraphs", [])
        ch["tables"] = ch.get("tables", []) + add.get("tables", [])
        ch["rows"] = (ch.get("rows") or []) + add.get("rows", [])
        if add.get("rows_title") and not ch.get("rows_title"):
            ch["rows_title"] = add["rows_title"]
    return out
