"""Excel-модель флипа любого объекта с живыми формулами, в том числе схема с НДС.

build(M, info, out): M — выход flipobj.py, info — тексты и таблицы объекта:
  title, sub, date, parts_basis {часть: основание}, rents {title, rows, mine}, exit_comps, unit_comps,
  candidates, sources [[тема, текст]], late_ovb (налог инвестора при поздней продаже).
Формулы повторяют flipmodel.run; значения подбора (потолок цены, сценарии, сетки) — из M.
"""
import sys

sys.path.append("/root/.local/lib/python3.11/site-packages")
from openpyxl import Workbook  # noqa: E402
from openpyxl.formatting.rule import ColorScaleRule  # noqa: E402
from openpyxl.styles import Alignment, Font, PatternFill  # noqa: E402
from openpyxl.utils import get_column_letter  # noqa: E402
from openpyxl.workbook.defined_name import DefinedName  # noqa: E402

NAVY, OCHRE = "16233A", "9C7C38"
F_HEAD = Font(name="Calibri", bold=True, color="FFFFFF", size=10)
F_TITLE = Font(name="Calibri", bold=True, color=NAVY, size=14)
F_SUB = Font(name="Calibri", italic=True, color="5D6B7C", size=9)
F_INPUT = Font(name="Calibri", color="1F4FB4", size=10)
F_BOLD = Font(name="Calibri", bold=True, size=10)
F_NORM = Font(name="Calibri", size=10)
F_SECTION = Font(bold=True, color=OCHRE, size=11)
FILL_HEAD = PatternFill("solid", fgColor=NAVY)
FILL_INPUT = PatternFill("solid", fgColor="FFF7E0")
FILL_KEY = PatternFill("solid", fgColor="ECE6DA")
EUR = '"€" #,##0;[Red]-"€" #,##0'
EUR2 = '"€" #,##0.00'
PCT = "0.0%"
PCT2 = "0.00%"
NUM = "#,##0"
MONTHS = 25


def build(M, info, out):
    wb = Workbook()
    vat = M["route"] == "vat"
    p = M["base"]["p"]
    L = M["labels"]

    def name(sheet, cell, nm):
        wb.defined_names[nm] = DefinedName(nm, attr_text=f"'{sheet}'!${cell[0]}${cell[1:]}")

    def title(ws, text, sub=None):
        ws["A1"] = text
        ws["A1"].font = F_TITLE
        if sub:
            ws["A2"] = sub
            ws["A2"].font = F_SUB

    def head(ws, row, values, col0=1):
        for i, v in enumerate(values):
            c = ws.cell(row=row, column=col0 + i, value=v)
            c.font, c.fill = F_HEAD, FILL_HEAD
            c.alignment = Alignment(horizontal="left" if i == 0 else "center", vertical="center", wrap_text=True)

    def widths(ws, ws_widths):
        for i, w in enumerate(ws_widths, start=1):
            ws.column_dimensions[get_column_letter(i)].width = w

    # ------------------------------------------------------------ допущения
    A = wb.active
    A.title = "Допущения"
    title(A, f"Флип-модель: {info['title']} — допущения",
          "Синие значения на жёлтом фоне можно менять: все листы пересчитаются. Суммы без НДС, евро.")
    widths(A, [50, 16, 16, 15, 15, 15, 15, 70])
    pct_off = 1 - M["price"] / M["ask"]
    inputs = [
        ("Покупка", None),
        ("Цена продавца" + (" (vrij op naam, без НДС)" if vat else " (k.k.)"), M["ask"], EUR, "ask",
         f"funda in business, {info['date']}"),
        ("Цена покупки — наше предложение", M["price"], EUR, "buy_price",
         f"{pct_off * 100:.0f} % ниже цены продавца; потолок без убытка — на листе «Сводка»" if pct_off > 0
         else "цена продавца"),
        ("Площадь, м²", M["area"], NUM, "area_m2", info.get("area_note", "")),
        ("Схема с НДС: 1 — новое помещение, 0 — обычная покупка", 1 if vat else 0, NUM, "vat_route",
         "ст. 15 WBR: новое здание продаётся с НДС и без налога на передачу" if vat else ""),
        ("Налог на передачу при нашей покупке", p["ovb"], PCT, "ovb",
         "0 % — схема с НДС" if vat else "ст. 14 WBR, нежилая недвижимость"),
        ("НДС с цены покупки", p["vat_rate"], PCT, "vat_rate", "платим у нотариуса, возвращаем декларацией"),
        ("Месяц возврата НДС", p["vat_refund_month"], NUM, "vat_refund_month", ""),
        ("Нотариус и кадастр", p["notary"], EUR, "notary", "в цене vrij op naam" if vat else "оценка"),
        ("Проверка объекта и договора", p["dd"], EUR, "dd", "оценка"),
        ("Оценка для банка", p["valuation"], EUR, "valuation", "только при кредите"),
        ("Консультант по покупке, % цены", p["advisory_pct"], PCT, "advisory", "оценка"),
        ("Отделка под арендатора", None),
        ("Отделка, € на м²", p["capex_m2"], EUR, "capex_m2", info.get("capex_note", "")),
        ("Отделка, резерв", p["capex_fixed"], EUR, "capex_fixed", ""),
        ("НДС на отделку как расход", p["capex_vat"], PCT, "capex_vat",
         "0 %: аренда с НДС, НДС на работы возвращается" if vat else "пока аренда без НДС"),
        ("Месяцев на отделку", p["capex_months"], NUM, "capex_months", ""),
        ("Сдача в аренду", None),
        ("Брокер по аренде, % годовой аренды", p["letting_fee"], PCT, "letting_fee", ""),
        ("Реклама и показы", p["marketing"], EUR, "marketing", ""),
        ("Содержание", None),
        ("Налог OZB, страховка, ремонт: % цены в год", p["holding_pct"], PCT2, "holding_pct", ""),
        ("Постоянные платежи владельца, € в год", p["hold_fixed"], EUR, "hold_fixed", info.get("hold_note", "")),
        ("Энергия и охрана в простое, € на м² в год", p["void_m2"], EUR2, "void_m2", ""),
        ("Что инвестор вычитает из аренды при оценке, € в год", p.get("exit_deduct", 0), EUR, "exit_deduct",
         info.get("deduct_note", "плата за землю (эрфпахт), которую платит владелец")),
        ("Кредит — по умолчанию не используется: покупка за свои средства", None),
        ("Кредит, доля цены покупки", p["ltv"], PCT, "ltv", "0 % — покупка за свои средства"),
        ("Ставка кредита", p["rate"], PCT2, "loan_rate", "только если включить кредит"),
        ("Комиссия банка, % кредита", p["bank_fee"], PCT, "bank_fee", "только если включить кредит"),
        ("Продажа", None),
        ("Брокер на продаже, % цены", p["sale_fee"], PCT2, "sale_fee", ""),
        ("Юрист и реклама продажи", p["sale_fixed"], EUR, "sale_fixed", ""),
        ("Срок продажи без налога 10,4 % для инвестора, месяцев после начала аренды", p["vat_window"], NUM,
         "vat_window", "ст. 15 WBR; аренда начинается в начале месяца въезда"),
        ("Налог на передачу для инвестора при поздней продаже", info.get("late_ovb", 0.104), PCT, "late_ovb",
         "инвестор снижает цену на эту сумму"),
        ("Доля экономии покупателя по ст. 13 WBR, которую забираем в цене", p["art13_share"], PCT, "art13_share",
         "только для обычной покупки и продажи до 6 месяцев"),
        ("Налог на прибыль (VPB) 2026", None),
        ("Ставка до порога", p["vpb_low"], PCT, "vpb_low", "KVK, 2026"),
        ("Ставка выше порога", p["vpb_high"], PCT2, "vpb_high", ""),
        ("Порог", p["vpb_step"], EUR, "vpb_step", ""),
    ]
    row = 4
    for item in inputs:
        if item[1] is None:
            A.cell(row=row, column=1, value=item[0]).font = Font(bold=True, color=OCHRE, size=10)
            row += 1
            continue
        label, value, fmt, nm, note = item
        A.cell(row=row, column=1, value=label).font = F_NORM
        c = A.cell(row=row, column=2, value=value)
        c.font, c.fill, c.number_format = F_INPUT, FILL_INPUT, fmt
        A.cell(row=row, column=8, value=note).font = F_SUB
        name("Допущения", f"B{row}", nm)
        row += 1
    row += 1

    A.cell(row=row, column=1, value="Сценарии").font = Font(bold=True, color=OCHRE, size=10)
    row += 1
    names = list(M["scenarios"])
    head(A, row, ["Параметр", "Активный", ""] + names)
    row += 1
    base_rent = M["rent"]
    scen_rows = [
        ("Аренда, множитель к оценке", lambda s: s["rent"] / base_rent, "0.00", "rent_mult"),
        ("Доходность для инвестора", lambda s: s["p"]["exit_bar"], PCT2, "exit_bar"),
        ("Месяц въезда арендатора", lambda s: s["p"]["lease_month"], NUM, "lease_month"),
        ("Месяцев без платы", lambda s: s["p"]["rent_free"], NUM, "rent_free"),
        ("Месяц продажи (акт у нотариуса)", lambda s: s["p"]["sale_month"], NUM, "sale_month"),
    ]
    last = get_column_letter(3 + len(names))
    for label, fn, fmt, nm in scen_rows:
        A.cell(row=row, column=1, value=label).font = F_NORM
        for j, n in enumerate(names):
            c = A.cell(row=row, column=4 + j, value=fn(M["scenarios"][n]))
            c.font, c.fill, c.number_format = F_INPUT, FILL_INPUT, fmt
        c = A.cell(row=row, column=2, value=f"=INDEX(D{row}:{last}{row},scenario)")
        c.number_format, c.font = fmt, F_BOLD
        name("Допущения", f"B{row}", nm)
        row += 1
    A.cell(row=row, column=1, value="Активный сценарий: " + ", ".join(
        f"{i} — {n.lower()}" for i, n in enumerate(names, start=1))).font = F_BOLD
    c = A.cell(row=row, column=2, value=names.index("Базовый") + 1)
    c.font, c.fill = F_INPUT, FILL_INPUT
    name("Допущения", f"B{row}", "scenario")

    # ------------------------------------------------------------ аренда
    R = wb.create_sheet("Аренда")
    title(R, "Аренда по частям помещения", f"Ставки — по объявлениям на funda in business, {info['date']}; без НДС")
    widths(R, [52, 10, 14, 16, 70])
    head(R, 4, ["Часть", "м²", "€ за м² в год", "Аренда в год", "Основание ставки"])
    r0 = 5
    for i, (label, a, rate) in enumerate(M["parts"]):
        rr = r0 + i
        R.cell(row=rr, column=1, value=label)
        R.cell(row=rr, column=2, value=a).number_format = "#,##0.0"
        c = R.cell(row=rr, column=3, value=rate)
        c.font, c.fill, c.number_format = F_INPUT, FILL_INPUT, EUR
        R.cell(row=rr, column=4, value=f"=B{rr}*C{rr}").number_format = EUR
        R.cell(row=rr, column=5, value=info["parts_basis"].get(label, "")).font = F_SUB
    rt = r0 + len(M["parts"])
    R.cell(row=rt, column=1, value="Итого").font = F_BOLD
    R.cell(row=rt, column=2, value=f"=SUM(B{r0}:B{rt - 1})").number_format = "#,##0.0"
    R.cell(row=rt, column=3, value=f"=D{rt}/B{rt}").number_format = EUR2
    c = R.cell(row=rt, column=4, value=f"=SUM(D{r0}:D{rt - 1})")
    c.number_format, c.font = EUR, F_BOLD
    name("Аренда", f"D{rt}", "rent_base")
    R.cell(row=rt + 2, column=1, value="Аренда с учётом сценария").font = F_BOLD
    R.cell(row=rt + 2, column=4, value="=rent_base*rent_mult").number_format = EUR
    name("Аренда", f"D{rt + 2}", "rent_eff")
    R.cell(row=rt + 3, column=1, value="К цене продавца")
    R.cell(row=rt + 3, column=4, value="=rent_eff/ask").number_format = PCT2
    R.cell(row=rt + 4, column=1, value="К цене покупки")
    R.cell(row=rt + 4, column=4, value="=rent_eff/buy_price").number_format = PCT2
    row = rt + 7
    R.cell(row=row, column=1, value=info["rents"]["title"]).font = F_SECTION
    head(R, row + 1, ["Объект", "", "€ за м² в год", "", "Что за помещение"])
    row += 2
    for lab, v, sub in info["rents"]["rows"] + [info["rents"]["mine"]]:
        R.cell(row=row, column=1, value=lab)
        R.cell(row=row, column=3, value=v).number_format = EUR2
        R.cell(row=row, column=5, value=sub).font = F_SUB
        row += 1

    # ------------------------------------------------------------ сводка
    S = wb.create_sheet("Сводка", 0)
    title(S, f"Флип-модель: {info['title']} — сводка", info["sub"])
    widths(S, [60, 18, 64])
    flow = "'Денежный поток'"
    calc = [
        ("Покупка", None),
        ("Цена покупки", "=buy_price", EUR, "acq_price"),
        ("Налог на передачу", "=buy_price*ovb", EUR, None),
        ("Нотариус и кадастр", "=notary", EUR, None),
        ("Проверка объекта", "=dd", EUR, None),
        ("Оценка для банка", "=IF(ltv>0,valuation,0)", EUR, None),
        ("Консультант по покупке", "=buy_price*advisory", EUR, None),
        ("Комиссия банка", "=loan*bank_fee", EUR, None),
        ("Итого на покупку", "=SUM(B{a}:B{b})", EUR, "acq_total"),
        ("НДС с цены: платим у нотариуса и возвращаем", "=IF(vat_route=1,buy_price*vat_rate,0)", EUR, "vat_paid"),
        ("Кредит", "=buy_price*ltv", EUR, "loan"),
        ("Отделка", "=(capex_m2*area_m2+capex_fixed)*(1+capex_vat)", EUR, "capex_total"),
        ("Продажа", None),
        ("Аренда в год (сценарий)", "=rent_eff", EUR, None),
        ("Первый оплачиваемый месяц", "=lease_month+rent_free", NUM, "first_paid"),
        ("Продажа позже срока без налога 10,4 % (1 — да)",
         "=IF(AND(vat_route=1,sale_month>lease_month-1+vat_window),1,0)", NUM, "late"),
        ("Стоимость сданного помещения: (аренда − вычет) / доходность для инвестора",
         "=(rent_eff-exit_deduct)/exit_bar/IF(late=1,1+late_ovb,1)", EUR, "value_let"),
        ("Минус не закончившиеся месяцы без платы", "=MAX(0,first_paid-1-sale_month)*rent_eff/12", EUR, "free_left"),
        ("Плюс доля экономии покупателя по ст. 13",
         "=IF(AND(vat_route=0,sale_month<=6),art13_share*ovb*buy_price/(1+ovb),0)", EUR, "uplift"),
        ("Цена продажи", "=value_let-free_left+uplift", EUR, "sale_price"),
        ("Расходы на продажу", "=sale_price*sale_fee+sale_fixed", EUR, "sale_cost"),
        ("Результат", None),
        ("Доходы: аренда за период и продажа", f"=SUM({flow}!B11:Z11)+SUM({flow}!B14:Z14)", EUR, "income"),
        ("Все затраты" + (" (НДС возвращается и не входит)" if vat else ""),
         f"=-(SUM({flow}!B5:Z5)+SUM({flow}!B7:Z10)+SUM({flow}!B12:Z12)+SUM({flow}!B15:Z15))", EUR, "cost_total"),
        ("Прибыль до налога", f"=SUM({flow}!B16:Z16)", EUR, "profit"),
        ("Заработок на каждые € 100 затрат", "=profit/cost_total*100", EUR2, None),
        ("Налог на прибыль VPB", "=IF(profit<=0,0,MIN(profit,vpb_step)*vpb_low+MAX(0,profit-vpb_step)*vpb_high)",
         EUR, "vpb"),
        ("Прибыль после налога", "=profit-vpb", EUR, None),
        ("Вложено своих денег на пике", f"=-MIN({flow}!B17:Z17)", EUR, "equity"),
        ("Прибыль на свои деньги после налога", "=(profit-vpb)/equity", PCT, None),
        ("В пересчёте на год (IRR), до налога", f"=(1+IRR({flow}!B16:Z16,0.01))^12-1", PCT, None),
        ("В пересчёте на год (IRR), после налога", f"=(1+IRR({flow}!B19:Z19,0.01))^12-1", PCT, None),
        ("Сколько вернётся на € 1 своих денег, после налога", "=(equity+profit-vpb)/equity", EUR2, None),
        ("Глазами инвестора", None),
        ("Налог на передачу, который платит инвестор",
         "=IF(vat_route=1,IF(late=1,late_ovb*sale_price,0),IF(sale_month<=6,ovb*(sale_price-buy_price),ovb*sale_price))",
         EUR, "buyer_ovb"),
        ("Его полная цена", "=sale_price+buyer_ovb", EUR, "buyer_total"),
        ("Его доходность на все расходы", "=rent_eff/buyer_total", PCT2, None),
    ]
    notes = {
        "Продажа позже срока без налога 10,4 % (1 — да)":
            "акт у нотариуса позже 6 месяцев после начала аренды: инвестор платит налог и снижает цену",
        "Цена продажи": "продажа сданного помещения инвестору" + (" с НДС, который он возвращает" if vat else ""),
        "Вложено своих денег на пике": "сколько денег занято в проекте в самый затратный момент"
                                       + (", включая НДС до возврата" if vat else ""),
        "Заработок на каждые € 100 затрат": "прибыль до налога, делённая на все затраты",
        "В пересчёте на год (IRR), до налога": "учитывает, сколько месяцев деньги заняты в проекте",
        "НДС с цены: платим у нотариуса и возвращаем": "в затраты не входит, но занимает деньги до возврата",
    }
    key_rows = ("Прибыль до налога", "Прибыль после налога", "Цена продажи", "Итого на покупку",
                "Вложено своих денег на пике", "В пересчёте на год (IRR), после налога")
    row, acq_start = 4, None
    for item in calc:
        label, formula = item[0], item[1]
        if formula is None:
            S.cell(row=row, column=1, value=label).font = F_SECTION
            row += 1
            continue
        fmt, nm = item[2], item[3]
        if label == "Цена покупки":
            acq_start = row
        if "{a}" in formula:
            formula = formula.format(a=acq_start, b=row - 1)
        S.cell(row=row, column=1, value=label).font = F_NORM
        c = S.cell(row=row, column=2, value=formula)
        c.number_format = fmt
        if label in key_rows:
            c.font, c.fill = F_BOLD, FILL_KEY
            S.cell(row=row, column=1).font = F_BOLD
        if label in notes:
            S.cell(row=row, column=3, value=notes[label]).font = F_SUB
        if nm:
            name("Сводка", f"B{row}", nm)
        row += 1
    row += 1
    S.cell(row=row, column=1, value="Потолок цены покупки (подбор моделью, базовые допущения)").font = F_SECTION
    row += 1
    for label, key, sub in [(f"Прибыль 0 — дороже не покупать, {L['main']}", "breakeven", "12"),
                            (f"€ 10 на каждые € 100 затрат, {L['main']}", "m10", "12"),
                            (f"€ 15 на каждые € 100 затрат, {L['main']}", "m15", "12"),
                            (f"Прибыль 0, {L['alt']}", "breakeven", "6"),
                            (f"€ 10 на каждые € 100 затрат, {L['alt']}", "m10", "6")]:
        v = M["bids"][sub][key]
        S.cell(row=row, column=1, value=label)
        if v is not None:
            S.cell(row=row, column=2, value=round(v)).number_format = EUR
            S.cell(row=row, column=3, value=f"=B{row}/ask-1").number_format = "+0.0%;-0.0%"
        else:
            S.cell(row=row, column=2, value="нет решения")
        row += 1
    S.cell(row=row, column=1, value="Доходность для инвестора, при которой прибыль = 0")
    S.cell(row=row, column=2, value=M["bids"]["12"]["bar_be"]).number_format = PCT2
    row += 1
    S.cell(row=row, column=1, value="Аренда, при которой прибыль = 0, € за м² в год")
    S.cell(row=row, column=2, value=M["bids"]["12"]["rent_be"]).number_format = EUR2
    row += 1
    S.cell(row=row, column=1, value="Значения подобраны Python-моделью; в Excel их можно перепроверить подбором "
                                    "параметра.").font = F_SUB

    # ------------------------------------------------------------ денежный поток
    D = wb.create_sheet("Денежный поток", 2)
    title(D, "Денежный поток по месяцам", "Месяц 0 — акт у нотариуса и оплата; продажа — в месяц продажи")
    D.column_dimensions["A"].width = 40
    labels = ["Месяц", "В работе (до продажи)", "Покупка и расходы на сделку", "НДС с цены: оплата и возврат",
              "Отделка", "Содержание", "Энергия и охрана в простое", "Сдача: брокер и реклама", "Аренда",
              "Проценты", "Кредит: получен / погашен", "Продажа", "Расходы на продажу", "Поток до налога",
              "Накопленный поток", "Налог на прибыль", "Поток после налога"]
    # строки: 3 месяц, 4 в работе, 5 покупка, 6 НДС, 7–10 отделка, содержание, простой, сдача, 11 аренда,
    # 12 проценты, 13 кредит, 14 продажа, 15 расходы на продажу, 16 поток, 17 накоплено, 18 налог, 19 после налога
    rows_at = {lab: 3 + i for i, lab in enumerate(labels)}
    for lab, r in rows_at.items():
        c = D.cell(row=r, column=1, value=lab)
        c.font = F_BOLD if lab in ("Месяц", "Поток до налога", "Накопленный поток", "Поток после налога") else F_NORM
    for m in range(MONTHS):
        col = get_column_letter(2 + m)
        D.column_dimensions[col].width = 11
        mc = f"{col}$3"
        D[f"{col}3"] = m
        D[f"{col}3"].font, D[f"{col}3"].fill = F_HEAD, FILL_HEAD
        f = {
            "В работе (до продажи)": f"=IF({mc}<=sale_month,1,0)",
            "Покупка и расходы на сделку": f"=IF({mc}=0,-acq_total,0)",
            "НДС с цены: оплата и возврат": f"=IF({mc}=0,-vat_paid,0)+IF({mc}=MIN(vat_refund_month,sale_month),vat_paid,0)",
            "Отделка": f"=IF(AND({mc}>=1,{mc}<=capex_months,{mc}<=sale_month),-capex_total/capex_months,0)",
            "Содержание": f"=IF(AND({mc}>=1,{mc}<=sale_month),-buy_price*holding_pct/12-hold_fixed/12,0)",
            "Энергия и охрана в простое": f"=IF(AND({mc}>=1,{mc}<lease_month,{mc}<=sale_month),-void_m2*area_m2/12,0)",
            "Сдача: брокер и реклама": f"=IF(AND({mc}>=1,{mc}=lease_month,{mc}<=sale_month),-(rent_eff*letting_fee+marketing),0)",
            "Аренда": f"=IF(AND({mc}>=1,{mc}>=first_paid,{mc}<=sale_month),rent_eff/12,0)",
            "Проценты": f"=IF(AND({mc}>=1,{mc}<=sale_month),-loan*loan_rate/12,0)",
            "Кредит: получен / погашен": f"=IF({mc}=0,loan,0)-IF({mc}=sale_month,loan,0)",
            "Продажа": f"=IF({mc}=sale_month,sale_price,0)",
            "Расходы на продажу": f"=IF({mc}=sale_month,-sale_cost,0)",
            "Поток до налога": f"=SUM({col}5:{col}15)",
            "Накопленный поток": (f"={col}16" if m == 0 else f"={get_column_letter(1 + m)}17+{col}16"),
            "Налог на прибыль": f"=IF({mc}=sale_month,-vpb,0)",
            "Поток после налога": f"={col}16+{col}18",
        }
        for lab, formula in f.items():
            cell = D[f"{col}{rows_at[lab]}"]
            cell.value = formula
            cell.number_format = NUM if lab == "В работе (до продажи)" else EUR
            if lab in ("Поток до налога", "Накопленный поток", "Поток после налога"):
                cell.font = F_BOLD
    D.freeze_panes = "B4"

    # ------------------------------------------------------------ сценарии
    SC = wb.create_sheet("Сценарии")
    title(SC, "Сценарии и цена покупки", "Рассчитано той же моделью; активный сценарий переключается на листе «Допущения»")
    widths(SC, [44, 18, 18, 18, 18, 16, 16, 16, 16])
    head(SC, 4, ["Показатель"] + names)
    srows = [
        ("Аренда в год", lambda r: r["rent"], EUR),
        ("Доходность для инвестора", lambda r: r["p"]["exit_bar"], PCT2),
        ("Месяц въезда арендатора", lambda r: r["p"]["lease_month"], NUM),
        ("Месяц продажи", lambda r: r["p"]["sale_month"], NUM),
        ("Цена продажи", lambda r: r["sale"], EUR),
        ("Все затраты", lambda r: r["cost_total"], EUR),
        ("Прибыль до налога", lambda r: r["profit"], EUR),
        ("Заработок на € 100 затрат", lambda r: r["margin"] * 100, EUR2),
        ("Прибыль после налога", lambda r: r["profit_after"], EUR),
        ("Вложено своих денег на пике", lambda r: r["equity"], EUR),
        ("Прибыль на свои деньги после налога", lambda r: r["roe_after"], PCT),
        ("В пересчёте на год после налога (IRR)", lambda r: r["irr_after"], PCT),
    ]
    for i, (label, fn, fmt) in enumerate(srows):
        SC.cell(row=5 + i, column=1, value=label)
        for j, n in enumerate(names):
            SC.cell(row=5 + i, column=2 + j, value=fn(M["scenarios"][n])).number_format = fmt
    row = 5 + len(srows) + 2
    SC.cell(row=row, column=1, value="Цена покупки и прибыль (базовые допущения)").font = F_SECTION
    row += 1
    head(SC, row, ["Цена покупки", "К цене продавца", "€ за м²", f"Прибыль, {L['main_short']}",
                   f"На € 100 затрат, {L['main_short']}", f"В год, {L['main_short']}",
                   f"Прибыль, {L['alt_short']}", f"На € 100 затрат, {L['alt_short']}", f"В год, {L['alt_short']}"])
    for step in M["ladder"]:
        row += 1
        vals = [(step["price"], EUR), (step["disc"], "+0%;-0%;0%"), (step["pm2"], EUR), (step["p12"], EUR),
                (step["m12"] * 100, EUR2), (step["irr12"], PCT), (step["p6"], EUR), (step["m6"] * 100, EUR2),
                (step["irr6"], PCT)]
        for i, (v, fmt) in enumerate(vals, start=1):
            c = SC.cell(row=row, column=i, value=v)
            c.number_format = fmt
            if abs(step["price"] - M["price"]) < 1:
                c.fill = FILL_KEY

    # ------------------------------------------------------------ чувствительность
    SE = wb.create_sheet("Чувствительность")
    title(SE, "Что будет, если расчёт не сойдётся",
          f"Покупка за € {M['price']:,.0f}".replace(",", ".") + f", {L['main']}; меняется одно допущение")
    SE.column_dimensions["A"].width = 34

    def simple(top, caption, cols, recs, fmts, base_row=None):
        SE.cell(row=top, column=1, value=caption).font = F_SECTION
        head(SE, top + 1, cols)
        for i, rec in enumerate(recs):
            for j, (v, fmt) in enumerate(zip(rec, fmts), start=1):
                c = SE.cell(row=top + 2 + i, column=j, value=v)
                if fmt:
                    c.number_format = fmt
                if base_row == i:
                    c.fill, c.font = FILL_KEY, F_BOLD
        return top + 3 + len(recs)

    bars = [r["bar"] for r in M["sens_bar"]]
    top = simple(4, "Если инвестор захочет другую доходность", ["Инвестор хочет получать в год", "Значит, заплатит",
                                                               "Наша прибыль"],
                 [[r["bar"], r["sale"], r["profit"]] for r in M["sens_bar"]], [PCT2, EUR, EUR],
                 base_row=min(range(len(bars)), key=lambda i: abs(bars[i] - p["exit_bar"])))
    top = simple(top, "Если аренда окажется другой", ["Аренда к нашей оценке", "Аренда в год", "Инвестор заплатит",
                                                      "Наша прибыль"],
                 [[r["k"] - 1, r["rent"], r["sale"], r["profit"]] for r in M["sens_rent"]],
                 ["+0%;-0%;0%", EUR, EUR, EUR], base_row=[r["k"] for r in M["sens_rent"]].index(1.0))

    def grid(top, caption, row_label, row_vals, row_fmt, cols, col_fmt, cells, note=None):
        SE.cell(row=top, column=1, value=caption).font = F_SECTION
        head(SE, top + 1, [row_label] + list(cols))
        for j, v in enumerate(cols):
            SE.cell(row=top + 1, column=2 + j).number_format = col_fmt
            SE.column_dimensions[get_column_letter(2 + j)].width = 14
        for i, rv in enumerate(row_vals):
            c = SE.cell(row=top + 2 + i, column=1, value=rv)
            c.number_format, c.font = row_fmt, F_BOLD
            for j, v in enumerate(cells[i]):
                SE.cell(row=top + 2 + i, column=2 + j, value=v).number_format = EUR
        rng = f"B{top + 2}:{get_column_letter(1 + len(cols))}{top + 1 + len(row_vals)}"
        SE.conditional_formatting.add(rng, ColorScaleRule(start_type="min", start_color="E6B8AF", mid_type="num",
                                                          mid_value=0, mid_color="FFFFFF", end_type="max",
                                                          end_color="B6D7A8"))
        if note:
            SE.cell(row=top + 2 + len(row_vals), column=1, value=note).font = F_SUB
        return top + 4 + len(row_vals)

    g = M["grid_lease_sale"]
    top = grid(top, "Месяц въезда арендатора (строки) и месяц продажи (столбцы): прибыль до налога", "Въезд \\ продажа",
               g["leases"], NUM, g["sales"], NUM,
               [[(c["profit"] if c else None) for c in r] for r in g["cells"]],
               note=("Продажа позже 6 месяцев после начала аренды: инвестор платит налог 10,4 % — ячейки: "
                     + ", ".join(f"въезд {lm} / продажа {sm}" for lm, r in zip(g["leases"], g["cells"])
                                 for sm, c in zip(g["sales"], r) if c and c.get("late"))) if vat else None)
    g = M["grid_price_bar"]
    top = grid(top, "Цена покупки (строки) и доходность для инвестора (столбцы): прибыль до налога", "Цена \\ доходность",
               g["prices"], EUR, g["bars"], PCT2, g["profit"])
    g = M["grid_rent_bar"]
    grid(top, "Аренда к оценке (строки) и доходность для инвестора (столбцы): прибыль до налога", "Аренда \\ доходность",
         g["rents"], "0%", g["bars"], PCT2, g["profit"])

    # ------------------------------------------------------------ план Б
    B = wb.create_sheet("План Б")
    title(B, "План Б: если быстро продать не получается", "Формулы от листов «Допущения», «Аренда» и «Сводка»")
    widths(B, [56, 18, 60])
    plan = [
        ("Держать сданным", None),
        ("Расходы владельца, % аренды", 0.125, PCT, "hold_opex", "налог OZB, страховка, ремонт, управление"),
        ("Чистый доход в год", "=rent_eff*(1-hold_opex)-hold_fixed", EUR, "hold_noi", "за вычетом постоянных платежей владельца"),
        ("Вложено всего", "=acq_total+capex_total+rent_eff*letting_fee+marketing", EUR, "hold_invested",
         "НДС к этому моменту возвращён"),
        ("Чистая доходность на вложенное", "=hold_noi/hold_invested", PCT2, None, ""),
        ("Продать без арендатора по цене покупки", None),
        ("Год содержания и простоя", "=buy_price*holding_pct+hold_fixed+void_m2*area_m2", EUR, "vac_carry", ""),
        ("Все затраты", "=acq_total+capex_total+vac_carry+buy_price*sale_fee+sale_fixed", EUR, "vac_cost", ""),
        ("Результат", "=buy_price-vac_cost", EUR, None, "крайний вариант: прибыль уходит на отделку и расходы"),
    ]
    row = 4
    for item in plan:
        if item[1] is None:
            B.cell(row=row, column=1, value=item[0]).font = F_SECTION
            row += 1
            continue
        label, value, fmt, nm, note = item
        B.cell(row=row, column=1, value=label)
        c = B.cell(row=row, column=2, value=value)
        c.number_format = fmt
        if not str(value).startswith("="):
            c.font, c.fill = F_INPUT, FILL_INPUT
        B.cell(row=row, column=3, value=note).font = F_SUB
        if nm:
            name("План Б", f"B{row}", nm)
        row += 1

    # ------------------------------------------------------------ конкуренты
    CO = wb.create_sheet("Конкуренты")
    title(CO, "Сопоставимые объекты", f"funda in business, выгрузка через Scrapling {info['date']}")
    widths(CO, [44, 12, 16, 16, 14, 30])
    row = 4
    for block in info["tables"]:
        CO.cell(row=row, column=1, value=block["title"]).font = F_SECTION
        head(CO, row + 1, block["columns"])
        row += 2
        for rec in block["rows"]:
            for i, (v, fmt) in enumerate(zip(rec, block["fmts"]), start=1):
                c = CO.cell(row=row, column=i, value=v)
                if fmt:
                    c.number_format = fmt
            row += 1
        if block.get("note"):
            CO.cell(row=row, column=1, value=block["note"]).font = F_SUB
            row += 1
        row += 1

    # ------------------------------------------------------------ источники
    SR = wb.create_sheet("Источники")
    title(SR, "Источники и правила")
    widths(SR, [34, 120])
    for i, (a, b) in enumerate(info["sources"], start=4):
        SR.cell(row=i, column=1, value=a).font = F_BOLD
        SR.cell(row=i, column=2, value=b).alignment = Alignment(wrap_text=True)

    for ws in wb.worksheets:
        ws.sheet_view.showGridLines = False
    wb.calculation.fullCalcOnLoad = True
    wb.save(out)
