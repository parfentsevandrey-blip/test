import charts as C

# Chart 1 — ASML net sales 2022-2025 (chips)
C.vbar(
    ["2022", "2023", "2024", "2025"],
    [21.2, 27.6, 28.3, 32.7],
    "assets/chart_asml.png",
    "Выручка ASML растёт год за годом",
    "Чистая выручка, млрд евро · источник: годовые отчёты ASML",
    unit=" млрд €",
    colors=[C.CHIPS]*4,
    value_fmt="{:.1f}",
)

# Chart 2 — Amsterdam data center capacity (data centers)
C.vbar(
    ["Действующие", "Строятся", "Запланированы"],
    [852, 182, 250],
    "assets/chart_amsterdam_mw.png",
    "Мощность дата-центров Амстердама",
    "Электрическая мощность, МВт · один из 4 крупнейших рынков Европы (FLAP-D)",
    unit=" МВт",
    colors=[C.DATA, "#60A5FA", "#A9C7F5"],
    value_fmt="{:.0f}",
)

# Chart 3 — hyperscaler site investment (data centers)
C.hbar_ranking(
    ["Microsoft · Мидденмер", "Google · Эмсхавен", "Google · Мидденмер"],
    [2.0, 1.1, 0.5],
    "assets/chart_investment.png",
    "Инвестиции в гиперскейл дата-центры",
    "Объявленные вложения по площадкам, млрд евро",
    unit=" млрд €",
    highlight=0,
    color=C.DATA,
    value_fmt="{:.1f}",
)
print("charts done")
