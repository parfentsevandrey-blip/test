"""Render the business report (HTML -> PDF via Chromium) from report_model.json + maps/*.png."""
import base64
import html
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
M = json.loads((HERE / "report_model.json").read_text())
MONTHS = M["months"]
MON_RU = ["янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]
MLAB = [f"{MON_RU[int(m[:2]) - 1]} {m[5:]}" for m in MONTHS]          # 'окт 25'
C = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100"]                       # validated categorical slots 1-4 (light)
INK, INK2, MUTED, GRID = "#1a1a1a", "#55534e", "#8d8b85", "#ece9e2"
NAVY, GOLD, PAPER = "#1c2541", "#a8824a", "#f7f5f0"
ORDER = ["Ридж", "Сезар Будущее", "Сезар Силика", "Стоун Блик", "26 ПаркВью", "С5", "Сикрет Гарден"]
E = html.escape


# ------------------------------------------------------------------ formatting
def fmt_int(n):
    return "—" if n is None else f"{int(n):,}".replace(",", " ")


def fmt_price(p):
    return "—" if not p else f"{p / 1000:,.0f}".replace(",", " ") + " тыс."


def fmt_months(m):
    if m is None:
        return "нет продаж"
    if m > 120:
        return "более 10 лет"
    if m <= 24:
        return f"{m} мес."
    return f"{m / 12:.1f}".replace(".", ",") + " года"


def fmt_pct(x, sign=False):
    if x is None:
        return "—"
    s = f"{x:+.1f}" if sign else f"{x:.1f}"
    return s.replace(".", ",").replace("-", "−") + "%"


def short(name, n=34):
    n0 = (name or "").replace("Жилой комплекс", "").replace("Жилой квартал", "").replace("ЖК", "")
    n0 = n0.replace("«", "").replace("»", "").replace('"', "").strip(" ,")
    return n0 if len(n0) <= n else n0[: n - 1] + "…"


def cls_ru(cls):
    return "/".join({"Элитный": "элит", "Бизнес": "бизнес", "Комфорт": "комфорт"}.get(c, c) for c in cls)


def img64(path):
    return "data:image/png;base64," + base64.b64encode(Path(path).read_bytes()).decode()


# ------------------------------------------------------------------ SVG line chart
def line_chart(series, title, unit_fmt, width=760, height=330, ymin0=True):
    pad_l, pad_r, pad_t, pad_b = 54, 132, 14, 30
    W, H = width - pad_l - pad_r, height - pad_t - pad_b
    vals = [v for _, s, _ in series for v in s if v is not None]
    if not vals:
        return ""
    lo = 0 if ymin0 else min(vals) * 0.92
    hi = max(vals) * 1.08 or 1
    step = nice_step((hi - lo) / 4)
    lo = (lo // step) * step
    hi = ((hi // step) + 1) * step
    x = lambda i: pad_l + W * i / 11
    y = lambda v: pad_t + H * (1 - (v - lo) / (hi - lo))
    out = [f'<svg viewBox="0 0 {width} {height}" width="100%" role="img" aria-label="{E(title)}">']
    t = lo
    while t <= hi + 1e-9:
        out.append(f'<line x1="{pad_l}" x2="{pad_l + W}" y1="{y(t):.1f}" y2="{y(t):.1f}" stroke="{GRID}" stroke-width="1"/>')
        out.append(f'<text x="{pad_l - 8}" y="{y(t) + 4:.1f}" text-anchor="end" font-size="11" fill="{MUTED}">{E(unit_fmt(t))}</text>')
        t += step
    for i, lab in enumerate(MLAB):
        out.append(f'<text x="{x(i):.1f}" y="{height - 10}" text-anchor="middle" font-size="10.5" fill="{MUTED}">{lab}</text>')
    ends = []
    for name, s, color in series:
        seg = []
        for i, v in enumerate(s):
            if v is None:
                if len(seg) > 1:
                    out.append(path(seg, color))
                seg = []
            else:
                seg.append((x(i), y(v)))
        if len(seg) > 1:
            out.append(path(seg, color))
        for i, v in enumerate(s):
            if v is not None:
                out.append(f'<circle cx="{x(i):.1f}" cy="{y(v):.1f}" r="3.2" fill="{color}" stroke="#fff" stroke-width="1.5"/>')
        last = max((i for i, v in enumerate(s) if v is not None), default=None)
        if last is not None:
            ends.append([y(s[last]), name, color, s[last]])
    ends.sort(key=lambda e: e[0])                     # direct labels, pushed apart to avoid collisions
    for i in range(1, len(ends)):
        ends[i][0] = max(ends[i][0], ends[i - 1][0] + 13)
    for yy, name, color, v in ends:
        out.append(f'<rect x="{pad_l + W + 8}" y="{yy - 4:.1f}" width="8" height="8" rx="2" fill="{color}"/>')
        out.append(f'<text x="{pad_l + W + 20}" y="{yy + 4:.1f}" font-size="11" fill="{INK2}">{E(short(name, 18))}</text>')
    out.append("</svg>")
    legend = "".join(f'<span class="lg"><i style="background:{c}"></i>{E(short(n, 30))}</span>' for n, _, c in series)
    return f'<figure class="chart"><figcaption>{E(title)}</figcaption><div class="legend">{legend}</div>{"".join(out)}</figure>'


def path(seg, color):
    d = "M" + " L".join(f"{a:.1f},{b:.1f}" for a, b in seg)
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>'


def nice_step(raw):
    import math
    if raw <= 0:
        return 1
    p = 10 ** math.floor(math.log10(raw))
    for k in (1, 2, 2.5, 5, 10):
        if raw <= k * p:
            return k * p
    return 10 * p


# ------------------------------------------------------------------ conclusions (plain language)
SUMMARY = [
    "Цифры из Telegram-поста подтверждаются официальными данными ЕИСЖС (наш.дом.рф) почти один в один: Ридж 75 против 73, Стоун Блик 109 против 108, Сезары 36 и 33, Сикрет Гарден 23.",
    "Пять проектов из семи распродают остаток быстрее, чем строятся: С5, Стоун Блик, Ридж, 26 ПаркВью и (впритык) Сезар Будущее. Сезар Силика идёт ровно по графику.",
    "Явный аутсайдер — Сикрет Гарден: при нынешнем темпе он будет распродавать остаток больше 10 лет, а дом сдаётся через 3 года.",
    "У С5 сильный старт, но продажи падают каждый месяц: 143 → 51 → 14. Если сентябрьский темп сохранится, остаток уйдёт как раз к сдаче — без запаса.",
    "Цена решает: лучше всего продаётся «входной» премиум по 550–720 тыс. ₽/м². Элитные проекты дороже 1 млн ₽/м² распродаются годами.",
]
MARKET = [
    "Продажи в уже идущих элитных проектах этих районов падают: 747 сделок во II квартале и 574 в III квартале (−23%).",
    "Весь прирост дали новые старты: семь наших проектов за июль–сентябрь продали 505 квартир — почти половину всех элитных сделок в своих районах.",
    "Бизнес-класс рядом держится лучше: 1 461 → 1 696 сделок (+16%). Покупатели, которым элитный сегмент дорог, уходят туда.",
    "Цены сделок в элитном классе за год выросли примерно на 15% (659 → 762 тыс. ₽/м²), в бизнес-классе — на 4% (548 → 568 тыс.). Часть роста — из-за того, что продаются более дорогие лоты, а не только из-за подорожания.",
    "Пик продаж — декабрь 2025 (типичная предновогодняя волна), провал — февраль–март и август.",
]
SECTIONS = {
    "Ридж": [
        "Лучший старт района: 75 квартир за первый же месяц регистраций. Таким темпом остаток уйдёт за 8 месяцев, а дом сдаётся через 3,7 года — огромный запас.",
        "Но продаётся Ридж по цене бизнес-класса: средняя цена сделок 558 тыс. ₽/м², ровно как у соседнего TATE от Кортроса и ниже прайса (640–710 тыс.). Похоже, работают скидки и рассрочка.",
        "Ближайшие элитные соседи продают в 5–8 раз медленнее (TATE около 14 квартир в месяц, Слава — 9). Бизнес-класс рядом (Апсайд Тауэрс, КОД Сокольники) продаёт 22–30 в месяц.",
        "Дорогие элитные проекты района проседают и снижают цены: PHANTOM −17%, Энигмия −11% за год.",
        "Вывод: старт успешный, но куплен ценой. Главный вопрос — удержит ли Ридж темп, когда начнёт поднимать цены.",
    ],
    "Сезар Будущее": [
        "Продажи открылись в июне, но регистрации пошли только в сентябре — сразу 36 сделок. Это лучший месяц среди элитных проектов «Большого Сити».",
        "При сентябрьском темпе остаток уйдёт примерно за 2,8 года, дом сдаётся через 3,2 года — успевает, но впритык.",
        "Рядом есть сильный соперник того же девелопера — бизнес-класс «Сезар Сити»: 29 квартир в месяц, цена +11% за год. Застройщик сам себе конкурент.",
        "Дорогие соседи (Начало, Оне, Аурус — 1,06–1,12 млн ₽/м²) продаются очень медленно: остатки уйдут за 8–40+ лет. Будущее стоит дешевле (552 тыс.) — это его главное преимущество.",
        "Вывод: средний результат, а не провал. Но темп держится на скидке 20% — после её окончания продажи могут просесть.",
    ],
    "Сезар Силика": [
        "33 квартиры за два месяца (31 — в сентябре). Таким темпом остаток уйдёт за 3,2 года, дом сдаётся через 3,4 года — ровно по графику.",
        "Среди элитных соседей Силика уже обгоняет Джойс (около 8 в месяц), но уступает Sydney City (20 в месяц).",
        "Главная угроза — сильный бизнес-класс рядом: Остров продаёт 63 квартиры в месяц, Мнёвники от Гранель — 53, INDY Towers — 32.",
        "Цена Силики (531 тыс. ₽/м²) почти как у бизнес-класса района, поэтому покупатель сравнивает её именно с ним.",
        "Вывод: нормальный старт, запаса нет. Чтобы держать темп, проекту нужно отличаться от бизнес-класса продуктом, а не только ценой.",
    ],
    "Стоун Блик": [
        "109 квартир за два месяца: 81 в августе (накопленные брони) и 28 в сентябре. Даже по сентябрьскому темпу остаток уйдёт за 2 года, а дом сдаётся через 4 года — хороший запас.",
        "Среди элитных проектов рядом Стоун Блик — лидер: Аура продаёт 12 в месяц, Первый Нагатинский — 3, Файв Тауэрс — 1.",
        "Главный конкурент — бизнес-класс ЗИЛАРТ: два корпуса продают около 87 квартир в месяц при цене на 15–20% ниже. ЗИЛАРТ за год снизил цену сделок на 5–6%.",
        "Дрим Рива почти распродан (80%) и поднял цены на 17% — его покупатели теперь ищут альтернативу, и Стоун Блик в выигрышной позиции.",
        "Вывод: уверенный успех. Риск — давление дешёвого ЗИЛАРТа.",
    ],
    "26 ПаркВью": [
        "20 квартир за сентябрь при цене 847 тыс. ₽/м² — сильный результат для небольшого клубного дома (257 квартир).",
        "При сентябрьском темпе остаток уйдёт за год, по среднему за два месяца — за 1,9 года; дом сдаётся через 2,7 года. Успевает с запасом.",
        "Элитные соседи сопоставимой цены продаются гораздо медленнее: Авторская коллекция резиденций (962 тыс.) — 3 квартиры в месяц, PRIME PARK (709 тыс.) — 13.",
        "Рядом продаётся бизнес-класс (СОУЛ, Вэри, Апсайд) — дешевле в 1,6 раза, но это другой покупатель.",
        "Вывод: успешный старт. Дорогой, но редкий продукт у парка находит покупателя.",
    ],
    "С5": [
        "Самый большой объём продаж среди семи проектов: 208 квартир, 27% дома за три месяца.",
        "Но темп резко падает: 143 в июле, 51 в августе, 14 в сентябре. Июльский всплеск — накопленный спрос на старте; оптовых сделок в данных нет (каждая квартира — отдельный договор).",
        "По среднему за три месяца остаток уйдёт за 8 месяцев. По сентябрьскому темпу — примерно за 3,3 года, ровно к сдаче (3,4 года). Запаса больше нет.",
        "Соседи-конкуренты: PRIME PARK (13 в месяц, 709 тыс. ₽/м²), Мастерс (10, 755 тыс.), Слава (9, 920 тыс.). Сентябрьские 14 продаж С5 — уже на их уровне.",
        "Вывод: старт отличный, но эффект старта закончился. Автор поста прав, что продажи придётся раскачивать.",
    ],
    "Сикрет Гарден": [
        "Стабильно 7–8 квартир в месяц. Для дома на 1 066 квартир это очень мало: остаток уйдёт больше чем за 10 лет, а дом сдаётся через 3,2 года.",
        "Элитный сегмент у Калужской почти не продаётся: Айс Тауэрс — 3 квартиры в месяц, Вавилова 64 и ВЕРН ещё без продаж. Сикрет Гарден даже лучший среди них — но этого мало.",
        "Покупатель здесь выбирает бизнес-класс: Обручева 30 продаёт 20 квартир в месяц, Татум — 19 (и он дешевле на 70 тыс. ₽/м²).",
        "Цена не главная проблема — Сикрет Гарден и так один из самых дешёвых элитных проектов (574 тыс.). Похоже, не работают продукт или позиционирование.",
        "Вывод: аутсайдер. Без смены стратегии (продукт, маркетинг, условия покупки) проект не распродастся к сдаче.",
    ],
}
RANK = [
    "Лучше всех продаются проекты с ценой 550–720 тыс. ₽/м²: Дрим Рива, Ридж, С5, Стоун Блик, Остров, Союз, Sydney City.",
    "Дорогие проекты (около 1 млн ₽/м² и выше: Оне, Аурус Резиденции, Дом Дау, PHANTOM) будут распродавать остаток 10–40 лет. Им придётся снижать цену или продавать после сдачи.",
    "Дешевизна сама по себе не спасает: Первый Нагатинский (489 тыс.) и Высотка на Жукова тоже в конце списка — важны локация и продукт.",
    "Среди аутсайдеров чаще встречаются снижения цен за год (PHANTOM −17%, Файв Тауэрс −4%, Мариин Парк −4%) — это признак затоваривания.",
]



# ------------------------------------------------------------------ was the post's author right?
POST = {
    "Ридж": ("прав", "«73 ДДУ в сентябре; привлекательная цена собрала инвесторов»", [
        "Цифра подтверждается: по наш.дом.рф в сентябре 75 сделок, и это первый месяц регистраций.",
        "Про цену — тоже верно: средняя цена сделок 558 тыс. ₽/м², самая низкая среди элитных новостроек района и ниже прайса.",
        "Что покупатели — именно инвесторы, данные наш.дом.рф проверить не позволяют: тип покупателя там не виден.",
    ]),
    "Сезар Будущее": ("частично прав", "«36 ДДУ, бодрый старт; стартовали дёшево, потом начали дёргать ценник»", [
        "36 сделок подтверждаются, и для «Большого Сити» это действительно лучший сентябрь среди элитных проектов.",
        "Но это не продажи за месяц, а весь итог с июня: в июне–августе не было зарегистрировано ни одной сделки.",
        "Повышение цены данными не подтверждается: сделки есть только за сентябрь (552 тыс. ₽/м²), а по открытым источникам самый дешёвый лот подешевел и действует скидка 20%.",
    ]),
    "Сезар Силика": ("прав", "«33 ДДУ, вполне бодрый старт»", [
        "33 сделки подтверждаются (2 в августе и 31 в сентябре).",
        "Темп достаточный: остаток уйдёт за 3,2 года при сдаче через 3,4 года, и Силика уже обгоняет соседний элитный Джойс.",
        "Оговорка, которой нет в посте: запаса нет, а бизнес-класс рядом (Остров, Мнёвники от Гранель) продаёт в 3–4 раза больше.",
    ]),
    "Стоун Блик": ("прав", "«28 ДДУ в сентябре после 80 в августе (брони до РНС); результат нормальный»", [
        "Цифры совпадают: 81 сделка в августе и 28 в сентябре.",
        "Августовский всплеск действительно похож на накопленные брони: в сентябре темп вернулся к спокойному уровню.",
        "«Нормальный» — даже скромно: по сентябрьскому темпу остаток уйдёт за 2 года при сдаче через 4 года, это лидер среди элитных проектов района.",
    ]),
    "26 ПаркВью": ("прав", "«21 ДДУ; уверенный старт с конским ценником; проблем с реализацией не будет; малоэтажная застройка»", [
        "21 сделка подтверждается (1 в августе и 20 в сентябре) при самой высокой цене среди семи проектов — 847 тыс. ₽/м².",
        "Прогноз автора поддерживается данными: остаток уйдёт за 1–2 года при сдаче через 2,7 года, а дорогие соседи продаются в разы медленнее.",
        "Неточность: дом не малоэтажный — секции от 5 до 16 этажей.",
    ]),
    "С5": ("частично прав", "«136 → 50 → 17; MR сама затарилась; после повышения цен 15–17 сделок явно недостаточно»", [
        "Падение подтверждается: 143 → 51 → 14 сделок, и в сентябре цена сделок выросла с 677 до 784 тыс. ₽/м² (+16%). Связь с повышением цен видна.",
        "«MR сама затарилась» не подтверждается: в июле 143 квартиры оформлены 143 отдельными договорами (оптовых сделок нет), а «Метриум», который считает только физлиц, насчитал почти столько же — 197.",
        "«Явно недостаточно» — преувеличение: при сентябрьском темпе остаток уйдёт за 3,3 года, ровно к сдаче (3,4 года). Это без запаса, но не провал.",
    ]),
    "Сикрет Гарден": ("прав", "«23 ДДУ за три месяца — грустно; проект не камерный; публика не оценила архитектуру»", [
        "Цифра подтверждается: 8 + 8 + 7 сделок. Для дома на 1 066 квартир это очень мало — остаток уйдёт больше чем за 10 лет.",
        "Главный вывод автора верный: это аутсайдер среди семи проектов.",
        "Причину («архитектура») данные не проверяют. Они показывают другое: элитный сегмент у Калужской не продаётся вообще, а покупатель уходит в соседний бизнес-класс (Татум, Обручева 30).",
    ]),
}
VERDICT_CLS = {"прав": "ok", "частично прав": "mid", "не прав": "bad"}


def post_box(t):
    v, claim, args = POST[t]
    lis = "".join(f"<li>{E(a)}</li>" for a in args)
    return (f'<div class="concl post"><h3>Прав ли автор поста? <span class="tag {VERDICT_CLS[v]}">{E(v)}</span></h3>'
            f'<p class="claim">Пост: {E(claim)}</p><ul>{lis}</ul></div>')


def post_page():
    rows = "".join(f"<tr><td class='l'><b>{E(t)}</b></td><td class='l'>{E(POST[t][1])}</td><td><span class='tag {VERDICT_CLS[POST[t][0]]}'>{E(POST[t][0])}</span></td>"
                   f"<td class='l'>{E(POST[t][2][0])}</td></tr>" for t in ORDER)
    n_ok = sum(1 for t in ORDER if POST[t][0] == "прав")
    n_mid = sum(1 for t in ORDER if POST[t][0] == "частично прав")
    concl = [f"Автор прав по {n_ok} проектам из 7 и частично прав по {n_mid}. Ни одна цифра из поста не опровергнута — расхождения с наш.дом.рф в пределах 1–7 сделок.",
             "Ошибки — в трактовках, а не в цифрах: у Сезар Будущее 36 сделок — это итог с июня, а не бодрый первый месяц; у С5 нет признаков, что застройщик «затарился» сам, а сентябрьский темп хоть и без запаса, но достаточный.",
             "Самые точные оценки поста — Стоун Блик, 26 ПаркВью и Сикрет Гарден: данные подтверждают и цифры, и общий вывод."]
    return f"""
<section class="page">
  <div class="eyebrow">Раздел 2</div>
  <h2>Был ли прав автор поста</h2>
  <p class="lead">Утверждения из Telegram-поста «Недвижимость инсайды» против данных наш.дом.рф. Подробные доводы — на страницах каждого проекта.</p>
  <table class="t compact"><thead><tr><th class="l">ЖК</th><th class="l">Что сказано в посте</th><th>Вердикт</th><th class="l">Главный довод из данных</th></tr></thead><tbody>{rows}</tbody></table>
  {conclusions(concl).replace('class="concl"', 'class="concl tight"')}
</section>"""

# ------------------------------------------------------------------ page building
def kpi(label, value, note=""):
    return f'<div class="kpi"><div class="kl">{E(label)}</div><div class="kv">{value}</div><div class="kn">{E(note)}</div></div>'


def conclusions(items, title="Выводы простым языком"):
    lis = "".join(f"<li>{E(t)}</li>" for t in items)
    return f'<div class="concl"><h3>{E(title)}</h3><ul>{lis}</ul></div>'


def summary_table():
    rows = []
    for t in ORDER:
        s = M["targets"][t]["self"]
        sep_pace = s["sales12"][-1]
        sep_sellout = round(s["remaining"] / sep_pace) if sep_pace else None
        ok = s["on_track"]
        verdict = '<span class="tag ok">успевает</span>' if ok else '<span class="tag bad">не успевает</span>'
        rows.append(
            f"<tr><td class='l'><b>{E(t)}</b><div class='sub'>{E(s['metro'] or '')}</div></td>"
            f"<td class='n'>{fmt_int(s['total'])}</td><td class='n'>{fmt_int(s['sold'])} <span class='sub'>({fmt_pct(s['sold_pct'])})</span></td>"
            f"<td class='n'>{s['sales12'][-3]} / {s['sales12'][-2]} / {s['sales12'][-1]}</td>"
            f"<td class='n'>{fmt_months(s['sellout_months'])}<div class='sub'>по сентябрю: {fmt_months(sep_sellout)}</div></td>"
            f"<td class='n'>{fmt_months(s['to_completion'])}</td><td>{verdict}</td>"
            f"<td class='n'>{fmt_price(s['price_end'])}</td></tr>")
    return ("<table class='t compact'><thead><tr><th class='l'>ЖК</th><th>Квартир</th><th>Продано</th><th>Июль / Авг / Сен</th>"
            "<th>Распродаст остаток при нынешнем темпе</th><th>До сдачи дома</th><th>Итог</th><th>Цена сделок, ₽/м²<div class='sub'>июль–сентябрь</div></th>"
            "</tr></thead><tbody>" + "".join(rows) + "</tbody></table>")


def comp_table(t):
    v = M["targets"][t]
    rows = []
    s = v["self"]
    rows.append(row("★", t, s, highlight=True))
    for i, c in enumerate(v["competitors"], 1):
        rows.append(row(str(i), c["name"], c))
    return ("<table class='t small'><thead><tr><th>№</th><th class='l'>ЖК / девелопер</th><th>Класс</th><th>км</th><th>Квартир</th><th>Продано</th>"
            "<th>Продажи за 12 мес.</th><th>Июль / Авг / Сен</th><th>Распродаст остаток при нынешнем темпе</th><th>Сдача</th>"
            "<th>Цена сделок, ₽/м²<div class='sub'>июль–сен</div></th><th>Цена за год</th></tr></thead><tbody>" + "".join(rows) + "</tbody></table>")


def row(num, name, c, highlight=False):
    note = " <span class='sub'>(вне радиуса, из рейтингов)</span>" if c.get("outside_radius") else ""
    km = "—" if highlight else str(c.get("km", "")).replace(".", ",")
    end = c.get("end") or ""
    return (f"<tr class='{'me' if highlight else ''}'><td class='n'>{num}</td><td class='l'><b>{E(short(name, 40))}</b>{note}<div class='sub'>{E(c.get('dev') or '')}</div></td>"
            f"<td>{cls_ru(c['cls'])}</td><td class='n'>{km}</td><td class='n'>{fmt_int(c['total'])}</td><td class='n'>{fmt_pct(c['sold_pct'])}</td>"
            f"<td class='n'>{fmt_int(c['sold12'])}</td><td class='n'>{c['sales12'][-3]} / {c['sales12'][-2]} / {c['sales12'][-1]}</td>"
            f"<td class='n'>{fmt_months(c['sellout_months'])}</td><td class='n'>{E(end[3:] if len(end) == 10 else end)}</td>"
            f"<td class='n'>{fmt_price(c['price_end'])}</td><td class='n'>{fmt_pct(c['price_chg'], sign=True)}</td></tr>")


def project_section(idx, t):
    v = M["targets"][t]
    s = v["self"]
    sep = s["sales12"][-1]
    top = sorted([c for c in v["competitors"] if c["pace"] and c["pace"] > 0], key=lambda c: -c["pace"])[:3]
    ser_sales = [(t, s["sales12"], C[0])] + [(c["name"], c["sales12"], C[i + 1]) for i, c in enumerate(top)]
    ser_price = [(t, [p / 1000 if p else None for p in s["price12"]], C[0])] + \
                [(c["name"], [p / 1000 if p else None for p in c["price12"]], C[i + 1]) for i, c in enumerate(top)]
    legend = "".join(f"<li><b>{i}</b> {E(short(c['name'], 38))} <span class='sub'>{E(cls_ru(c['cls']))}, {str(c['km']).replace('.', ',')} км</span></li>"
                     for i, c in enumerate(v["competitors"], 1))
    return f"""
<section class="page">
  <div class="eyebrow">Проект {idx} из 7</div>
  <h2>{E(t)}</h2>
  <p class="lead">{E(cls_ru(s['cls']))} · {E(s['district'] or '')} · м. {E(s['metro'] or '')} · девелопер {E(s['dev'] or '')} · сдача {E(s['end'] or '—')}</p>
  <div class="kpis">
    {kpi('Продано квартир', f"{fmt_int(s['sold'])} из {fmt_int(s['total'])}", fmt_pct(s['sold_pct']))}
    {kpi('Продажи июль / авг / сен', f"{s['sales12'][-3]} / {s['sales12'][-2]} / {sep}", 'зарегистрированные ДДУ')}
    {kpi('Распродаст остаток при нынешнем темпе', fmt_months(s['sellout_months']), f"до сдачи дома: {fmt_months(s['to_completion'])}")}
    {kpi('Цена сделок', fmt_price(s['price_end']) + ' ₽/м²', 'среднее июль–сентябрь')}
  </div>
  <div class="mapwrap"><img src="{img64(HERE / 'maps' / f'map_{idx}.png')}" alt="Карта: {E(t)} и конкуренты">
    <ol class="maplegend"><li class="me"><b>★</b> {E(t)}</li>{legend}</ol></div>
  <div class="caption">Карта: Яндекс Карты. Красная метка — наш ЖК, синие номера — конкуренты из таблицы (радиус {str(v['radius_km']).replace('.', ',')} км + проекты из рыночных рейтингов).</div>
</section>
<section class="page">
  <h3 class="h3">Конкуренты: продажи и цены за 12 месяцев</h3>
  {comp_table(t)}
</section>
<section class="page">
  <h3 class="h3">{E(t)}: продажи и цены по месяцам</h3>
  <div class="charts">
    {line_chart(ser_sales, 'Продажи квартир по месяцам, шт. (наш ЖК и три самых быстрых конкурента)', lambda x: f'{x:.0f}', height=235)}
    {line_chart(ser_price, 'Цена сделок по месяцам, тыс. ₽/м² (пропуск — в этот месяц сделок не было)', lambda x: f'{x:.0f}', height=235, ymin0=False)}
  </div>
  {conclusions(SECTIONS[t])}
  {post_box(t)}
</section>"""


def market_section():
    mk = M["market"]
    elite, biz, tg = mk["Элитный"], mk["Бизнес"], mk["targets_sales"]
    q = lambda a, i: sum(a[i:i + 3])
    rows = "".join(f"<tr><td class='l'>{n}</td>" + "".join(f"<td class='n'>{fmt_int(q(a, i))}</td>" for i in (0, 3, 6, 9)) + "</tr>"
                   for n, a in (("Элитный (действующие проекты)", elite["sales"]), ("Бизнес-класс", biz["sales"]), ("Наши 7 новых проектов", tg)))
    return f"""
<section class="page">
  <div class="eyebrow">Раздел 3</div>
  <h2>Темп рынка за 12 месяцев</h2>
  <p class="lead">Все 119 соседних корпусов вокруг наших проектов (элитный и бизнес-класс), продажи и цены по месяцам, октябрь 2025 — сентябрь 2026.</p>
  <div class="charts">
  {line_chart([('Элитный (действующие)', elite['sales'], C[0]), ('Бизнес-класс', biz['sales'], C[1]), ('Наши 7 новых проектов', tg, C[2])], 'Продажи квартир по месяцам, шт.', lambda x: f'{x:.0f}', height=255)}
  {line_chart([('Элитный', [p / 1000 if p else None for p in elite['price']], C[0]), ('Бизнес-класс', [p / 1000 if p else None for p in biz['price']], C[1])], 'Средняя цена сделок по месяцам, тыс. ₽/м²', lambda x: f'{x:.0f}', height=255, ymin0=False)}
  </div>
  <table class="t compact"><thead><tr><th class='l'>Продажи по кварталам, квартир</th><th>IV кв. 2025</th><th>I кв. 2026</th><th>II кв. 2026</th><th>III кв. 2026</th></tr></thead><tbody>{rows}</tbody></table>
  {conclusions(MARKET).replace('class="concl"', 'class="concl tight"')}
</section>"""


def rank_section():
    r = M["elite_rank"]

    def tbl(items, start):
        return "".join(f"<tr><td class='n'>{start + i}</td><td class='l'><b>{E(short(d['name'], 36))}</b><div class='sub'>{E(d['dev'] or '')}</div></td><td class='l'>{E(d['metro'] or '')}</td>"
                       f"<td class='n'>{fmt_pct(d['sold_pct'])}</td><td class='n'>{d['pace']:.0f}</td><td class='n'>{fmt_months(d['sellout_months'])}</td>"
                       f"<td class='n'>{fmt_price(d['price_end'])}</td><td class='n'>{fmt_pct(d['price_chg'], sign=True)}</td></tr>" for i, d in enumerate(items))
    head = ("<thead><tr><th>#</th><th class='l'>ЖК</th><th class='l'>Метро</th><th>Продано</th><th>Квартир в месяц</th><th>Распродаст остаток при нынешнем темпе</th>"
            "<th>Цена сделок, ₽/м²</th><th>Цена за год</th></tr></thead>")
    return f"""
<section class="page">
  <div class="eyebrow">Раздел 11</div>
  <h2>Лидеры и аутсайдеры элитного класса</h2>
  <p class="lead">{len(r)} элитных ЖК с продажами в июле–сентябре 2026 в районах наших проектов, отсортированы по сроку распродажи остатка.</p>
  <h3 class="h3">10 лидеров</h3><table class="t small">{head}<tbody>{tbl(r[:10], 1)}</tbody></table>
</section>
<section class="page">
  <h3 class="h3">10 аутсайдеров</h3><table class="t small">{head}<tbody>{tbl(r[-10:], len(r) - 9)}</tbody></table>
  {conclusions(RANK)}
</section>"""


METHOD = """
<section class="page">
  <div class="eyebrow">Раздел 12</div>
  <h2>Методика и источники</h2>
  <h3 class="h3">Откуда данные</h3>
  <ul class="plain">
    <li><b>ЕИСЖС «наш.дом.рф»</b> (оператор — ДОМ.РФ, ведётся по 214-ФЗ). Для каждого корпуса: число квартир и проданных квартир, продажи по месяцам и средняя цена сделок за м². Продажа = зарегистрированный договор долевого участия (ДДУ).</li>
    <li>Глубина помесячных данных — <b>12 месяцев (октябрь 2025 — сентябрь 2026)</b>. Это максимум, который публикует наш.дом.рф бесплатно; более длинная история есть только в платном сервисе «продома.дом.рф».</li>
    <li>Собрано 127 корпусов (97 ЖК): наши 7 проектов, все строящиеся корпуса в радиусе 2,5–3,5 км и конкуренты из рыночных рейтингов («Метриум», «Новострой-М»): Эра, Дрим Рива, Союз.</li>
    <li>Карты — Яндекс Карты. Данные собраны 15 октября 2026 г.</li>
  </ul>
  <h3 class="h3">Как считаются показатели</h3>
  <ul class="plain">
    <li><b>Распродаст остаток при нынешнем темпе</b> = непроданные квартиры ÷ средние продажи в месяц за июль–сентябрь. Для новых проектов среднее берётся только за месяцы, когда уже были продажи. Если срок меньше, чем осталось до сдачи дома, — проект успевает.</li>
    <li><b>Цена сделок</b> — средняя фактическая цена м² по зарегистрированным ДДУ (взвешена по площади), а не цена в прайсе. <b>Цена за год</b> — изменение средней цены сделок: октябрь–декабрь 2025 против июля–сентября 2026.</li>
    <li>Корпуса объединены в ЖК по застройщику и названию.</li>
  </ul>
</section>
<section class="page">
  <h3 class="h3">Ограничения</h3>
  <ul class="plain">
    <li>Регистрация ДДУ отстаёт от реальной сделки на 1–4 недели, поэтому сентябрь может ещё немного вырасти.</li>
    <li>Брони до получения разрешения на строительство регистрируются пачкой (так у Стоун Блик в августе), это завышает первый месяц.</li>
    <li>Средняя цена сделок зависит и от того, какие лоты продавались (большие/маленькие, высокие/низкие этажи), а не только от изменения прайса.</li>
    <li>Квартиры, проданные в рассрочку без регистрации ДДУ, в данных не видны.</li>
  </ul>
  <h3 class="h3">Сверка с Telegram-постом</h3>
  <table class="t small"><thead><tr><th class="l">ЖК</th><th>Пост</th><th>наш.дом.рф</th></tr></thead><tbody>
  <tr><td class="l">Ридж</td><td class="n">73 (сентябрь)</td><td class="n">75</td></tr>
  <tr><td class="l">Сезар Будущее / Силика</td><td class="n">36 / 33</td><td class="n">36 / 33</td></tr>
  <tr><td class="l">Стоун Блик</td><td class="n">80 + 28</td><td class="n">81 + 28</td></tr>
  <tr><td class="l">26 ПаркВью</td><td class="n">21</td><td class="n">21 (1 + 20)</td></tr>
  <tr><td class="l">С5</td><td class="n">136 / 50 / 17</td><td class="n">143 / 51 / 14</td></tr>
  <tr><td class="l">Сикрет Гарден</td><td class="n">23 за 3 мес.</td><td class="n">23 (8 + 8 + 7)</td></tr>
  </tbody></table>
</section>"""

CSS = f"""
@page {{ size: A4 landscape; margin: 14mm 14mm 14mm 14mm; }}
* {{ box-sizing: border-box; }}
body {{ font-family: 'Inter', 'DejaVu Sans', sans-serif; color: {INK}; background: #fff; font-size: 9.6pt; line-height: 1.45;
        margin: 0; font-feature-settings: 'tnum' 1, 'cv11' 1; -webkit-font-smoothing: antialiased; }}
.page {{ page-break-after: always; }}
h2 {{ font-family: 'Inter Display', 'Inter', sans-serif; font-size: 21pt; font-weight: 600; color: {NAVY}; margin: 0 0 1.5mm; letter-spacing: -0.015em; }}
h3, .h3 {{ font-family: 'Inter Display', 'Inter', sans-serif; font-size: 12pt; font-weight: 600; color: {NAVY}; margin: 4mm 0 2.5mm; letter-spacing: -0.005em; }}
.eyebrow {{ color: {GOLD}; font-weight: 600; font-size: 7.6pt; text-transform: uppercase; letter-spacing: .14em; margin-bottom: 1.5mm; }}
.eyebrow::after {{ content: ''; display: block; width: 14mm; height: .5mm; background: {GOLD}; margin-top: 1.5mm; }}
.lead {{ color: {INK2}; margin: 0 0 4mm; font-size: 10pt; max-width: 230mm; }}
.sub {{ color: {MUTED}; font-size: 7.6pt; font-weight: 400; line-height: 1.3; }}

/* cover */
.cover {{ height: 182mm; background: {NAVY}; color: #fff; border-radius: 3mm; padding: 22mm 24mm; display: flex; flex-direction: column; justify-content: space-between; }}
.cover .eyebrow {{ color: #d9bf8f; }} .cover .eyebrow::after {{ background: #d9bf8f; }}
.cover h1 {{ font-family: 'Inter Display', 'Inter', sans-serif; font-weight: 600; font-size: 31pt; line-height: 1.12; letter-spacing: -0.02em; margin: 3mm 0 5mm; max-width: 200mm; }}
.cover .lead {{ color: #c9cfdd; font-size: 11pt; max-width: 190mm; }}
.cover .stats {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 6mm; border-top: 1px solid rgba(255,255,255,.18); padding-top: 6mm; }}
.cover .stat b {{ display: block; font-family: 'Inter Display', 'Inter', sans-serif; font-size: 24pt; font-weight: 600; color: #fff; }}
.cover .stat span {{ color: #aab3c7; font-size: 8.5pt; }}
.toc {{ columns: 2; column-gap: 14mm; padding-left: 0; list-style: none; margin: 6mm 0 0; font-size: 10pt; }}
.toc li {{ padding: 1.6mm 0; border-bottom: 1px solid {GRID}; break-inside: avoid; }}
.toc li span {{ color: {GOLD}; font-weight: 600; display: inline-block; width: 8mm; }}

/* KPI cards */
.kpis {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 4mm; margin: 3mm 0 5mm; }}
.kpi {{ background: {PAPER}; border-radius: 2.5mm; padding: 3.5mm 4.5mm; text-align: center; }}
.kl {{ color: {INK2}; font-size: 7.6pt; text-transform: uppercase; letter-spacing: .06em; }}
.kv {{ font-family: 'Inter Display', 'Inter', sans-serif; font-size: 17pt; font-weight: 600; color: {NAVY}; margin: 1.2mm 0 .6mm; }}
.kn {{ color: {MUTED}; font-size: 8pt; }}

/* map */
.mapwrap {{ display: grid; grid-template-columns: 178mm 1fr; gap: 6mm; align-items: start; }}
.mapwrap img {{ width: 178mm; height: 112mm; object-fit: cover; border-radius: 2.5mm; box-shadow: 0 0 0 1px {GRID}; }}
.maplegend {{ list-style: none; padding: 0; margin: 0; font-size: 8.2pt; }}
.maplegend li {{ padding: 1mm 0; border-bottom: 1px solid {GRID}; display: flex; gap: 2mm; align-items: baseline; }}
.maplegend li b {{ flex: none; display: inline-block; width: 5.4mm; height: 5.4mm; line-height: 5.4mm; text-align: center; border-radius: 50%;
                   background: #1e98ff; color: #fff; font-size: 7pt; font-weight: 600; }}
.maplegend li.me b {{ background: #ed4543; }}
.maplegend li.me {{ font-weight: 600; color: {NAVY}; }}
.caption {{ color: {MUTED}; font-size: 7.8pt; margin-top: 2.5mm; }}

/* tables: figures centred, names left */
table.t {{ width: 100%; border-collapse: separate; border-spacing: 0; margin: 2mm 0 4mm; font-size: 8.8pt; }}
table.t.small {{ font-size: 8pt; line-height: 1.3; }}
table.t.compact td {{ padding: 1mm 1.6mm; line-height: 1.25; }}
.concl.tight li {{ margin: .5mm 0; }}
.t th {{ text-align: center; vertical-align: middle; color: {INK2}; font-weight: 600; font-size: 7pt; text-transform: uppercase; letter-spacing: .05em;
         background: #f2f0eb; padding: 2mm 1.6mm; border-bottom: 1px solid #d9d5cb; }}
.t th:first-child {{ border-top-left-radius: 2mm; }} .t th:last-child {{ border-top-right-radius: 2mm; }}
.t th .sub {{ text-transform: none; letter-spacing: 0; font-size: 7pt; }}
.t td {{ text-align: center; vertical-align: middle; padding: 1.3mm 1.6mm; border-bottom: 1px solid {GRID}; }}
.t th.l, .t td.l {{ text-align: left; }}
.t td.n {{ white-space: nowrap; }}
.t tr.me td {{ background: #edf2fb; font-weight: 600; color: {NAVY}; }}
.t tr.me td:first-child {{ box-shadow: inset .9mm 0 0 {NAVY}; }}
.t tbody tr:last-child td {{ border-bottom: 1px solid #d9d5cb; }}
.tag {{ display: inline-block; padding: .6mm 2.4mm; border-radius: 3mm; font-size: 7.6pt; font-weight: 600; letter-spacing: .02em; }}
.tag.ok {{ background: #e5f2ea; color: #1d6b43; }}
.tag.bad {{ background: #f8e4e2; color: #9b2c25; }}
.tag.mid {{ background: #fbf0dc; color: #8a5a12; }}
.concl.post {{ background: #f3f5fa; }} .concl.post::before {{ background: {NAVY}; }} .concl.post h3 {{ color: {NAVY}; display: flex; align-items: center; gap: 3mm; }}
.concl.post .tag {{ text-transform: none; letter-spacing: .02em; font-size: 7.6pt; }}
.claim {{ margin: 0 0 1.5mm; color: {INK2}; font-style: italic; }}
.concl {{ padding-top: 3.2mm; padding-bottom: 3.2mm; }}

/* charts */
.charts {{ display: grid; grid-template-columns: 1fr 1fr; gap: 7mm; }}
.chart {{ margin: 1mm 0 2mm; break-inside: avoid; background: #fff; }}
.chart figcaption {{ font-weight: 600; font-size: 9pt; color: {NAVY}; margin-bottom: 1.5mm; }}
.legend {{ display: flex; flex-wrap: wrap; gap: 1mm 5mm; font-size: 8pt; color: {INK2}; margin-bottom: 1.5mm; }}
.lg i {{ display: inline-block; width: 2.6mm; height: 2.6mm; border-radius: 50%; margin-right: 1.4mm; vertical-align: -0.2mm; }}

/* conclusions */
.concl {{ background: {PAPER}; border-radius: 2.5mm; padding: 4mm 6mm 4mm 6mm; margin-top: 4mm; break-inside: avoid; position: relative; }}
.concl::before {{ content: ''; position: absolute; left: 0; top: 4mm; bottom: 4mm; width: .9mm; background: {GOLD}; border-radius: 1mm; }}
.concl h3 {{ margin: 0 0 2mm; font-size: 8pt; color: {GOLD}; text-transform: uppercase; letter-spacing: .12em; }}
.concl ul {{ margin: 0; padding-left: 4.5mm; }}
.concl li {{ margin: .9mm 0; }}
.concl li::marker {{ color: {GOLD}; }}
ul.plain {{ padding-left: 5mm; }} ul.plain li {{ margin: 1.2mm 0; }} ul.plain li::marker {{ color: {GOLD}; }}
"""


def build():
    toc = "".join(f"<li><span>{i:02d}</span>{E(n)}</li>" for i, n in enumerate(
        ["Главное", "Был ли прав автор поста", "Темп рынка за 12 месяцев"] + [f"{t}: конкуренты, продажи, цены" for t in ORDER] +
        ["Лидеры и аутсайдеры элитного класса", "Методика и источники"], 1))
    body = f"""
<section class="page cover">
  <div>
    <div class="eyebrow">Аналитический отчёт · 15 октября 2026</div>
    <h1>Продажи новых премиальных ЖК Москвы и их конкурентов</h1>
    <p class="lead">Ридж, Сезар Будущее, Сезар Силика, Стоун Блик, 26 ПаркВью, С5 и Сикрет Гарден: продажи, цены и конкуренты за 12 месяцев (октябрь 2025 — сентябрь 2026) по данным ЕИСЖС наш.дом.рф.</p>
  </div>
  <div class="stats">
    <div class="stat"><b>7</b><span>проектов в фокусе</span></div>
    <div class="stat"><b>97</b><span>ЖК-конкурентов рядом</span></div>
    <div class="stat"><b>127</b><span>корпусов с помесячными данными</span></div>
    <div class="stat"><b>12</b><span>месяцев продаж и цен</span></div>
  </div>
</section>
<section class="page">
  <div class="eyebrow">Содержание</div>
  <h2>Что в отчёте</h2>
  <ol class="toc">{toc}</ol>
</section>
<section class="page">
  <div class="eyebrow">Раздел 1</div>
  <h2>Главное</h2>
  <p class="lead">Семь проектов из Telegram-поста «Недвижимость инсайды»: сколько продано, успевают ли распродаться к сдаче дома и по какой цене продаются.</p>
  {summary_table()}
  {conclusions(SUMMARY).replace('class="concl"', 'class="concl tight"')}
</section>
{post_page()}
{market_section()}
{''.join(project_section(i, t) for i, t in enumerate(ORDER, 1))}
{rank_section()}
{METHOD}"""
    doc = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Продажи премиальных ЖК</title><style>{CSS}</style></head><body>{body}</body></html>'
    (HERE / "report.html").write_text(doc)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        pg.goto((HERE / "report.html").as_uri())
        pg.wait_for_timeout(800)
        pg.pdf(path=str(HERE / "report.pdf"), format="A4", landscape=True, print_background=True,
               display_header_footer=True, header_template='<div style="font-size:6.5pt;color:#a8824a;width:100%;padding:0 14mm;letter-spacing:.14em;text-transform:uppercase;font-family:Inter,sans-serif">Продажи премиальных ЖК Москвы</div>',
               footer_template='<div style="font-size:6.5pt;color:#8d8b85;width:100%;padding:0 14mm;display:flex;justify-content:space-between;font-family:Inter,sans-serif">'
                               '<span>Продажи премиальных ЖК Москвы · данные ЕИСЖС наш.дом.рф, 15.10.2026</span>'
                               '<span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
               margin={"top": "14mm", "bottom": "14mm", "left": "14mm", "right": "14mm"})
        b.close()
    print("ok", (HERE / "report.pdf").stat().st_size)


if __name__ == "__main__":
    build()
