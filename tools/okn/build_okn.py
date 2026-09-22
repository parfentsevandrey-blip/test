"""Собирает перечень ОКН страницей: фото, карты, характеристики, условия.

Картинки вшиваются как data: URI — при публикации внешние изображения режет CSP
(та же грабля, что в docs/commercial/README.md).
"""
import base64, io, json, os, re
from PIL import Image

OUT = os.path.dirname(os.path.abspath(__file__))
objs = json.load(open(f"{OUT}/okn_objects.json"))

STATUS = {"NO_SOLUTION": "Решение отсутствует", "READY": "Подготовка к торгам",
          "AUCTION": "Конкурс объявлен", "INVESTOR_FOUND": "Инвестор найден",
          "RECOVERY": "Идёт восстановление", "RESTORED": "Восстановлен",
          "NOT_PROVIDED": "Реализация не предусмотрена"}
COND = {"SATISFACTORY": "Удовлетворительное", "UNSATISFACTORY": "Неудовлетворительное",
        "EMERGENCY": "Аварийное", "RUINED": "Руинированное"}
TYPE = {"MONUMENT": "Памятник", "ENSEMBLE": "Ансамбль", "INTEREST_PLACE": "Достопримечательное место"}
SEC = {"FEDERAL_IMPORTANCE": "Федерального значения", "REGIONAL_IMPORTANCE": "Регионального значения",
       "MUNICIPAL_IMPORTANCE": "Муниципального значения"}
USE = {"NOT_USED": "Не используется", "USED": "Используется", "PARTIALLY_USED": "Используется частично"}
OWN = {"FEDERAL": "Федеральная", "REGIONAL": "Региональная", "MUNICIPAL": "Муниципальная",
       "PRIVATE": "Частная"}
LAND = {"SETTLEMENTS_LANDS": "Земли населённых пунктов"}
DOCS = {"PROTECTION_SUBJECT": "Предмет охраны",
        "TECHNICAL_CONDITION_REPORT": "Акт технического состояния",
        "APPROVAL_TERRITORY_BOUNDARIES": "Утверждение границ территории",
        "SECURITY_OBLIGATION": "Охранное обязательство",
        "HISTORICAL_REFERENCE": "Историческая справка",
        "OBJECT_PASSPORT": "Паспорт объекта",
        "INCLUSION_ORDER": "Приказ о включении в реестр",
        "PHOTO_FIXATION": "Фотофиксация",
        # портал пишет тип с опечаткой в корне слова — держим оба написания
        "UNSTATISFACTORY_CONDITION_DECISION": "Решение о неудовл. состоянии",
        "UNSATISFACTORY_CONDITION_DECISION": "Решение о неудовл. состоянии"}


def doc_label(t):
    """Название документа; незнакомый тип не показываем кодом."""
    if t in DOCS:
        return DOCS[t]
    return (t or "").replace("_", " ").capitalize() or "Документ"
ENG = [("electricitySupply", "Электроснабжение"), ("waterSupply", "Водоснабжение"),
       ("waterDisposal", "Водоотведение"), ("heating", "Отопление"), ("gasSupply", "Газоснабжение")]

GROUPS = [
    ("Малый Казенный переулок, 5", "Басманный · м. Курская",
     "Городская усадьба Нарышкиных, с 1845 года — Полицейская больница для бесприютных, "
     "где жил и работал доктор Фёдор Гааз. Шесть объектов внутри Садового кольца, "
     "в десяти минутах пешком от Курского вокзала.",
     [4097, 4100, 4098, 4101, 4336, 1498]),
    ("Усадьба Щапово", "Краснопахорский · Новая Москва",
     "Усадебный комплекс в посёлке Щапово в 35 км от МКАД по Калужскому шоссе: "
     "каретный двор, дом управляющего, кузница, склад и руины хозяйственной постройки.",
     [4103, 3529, 3532, 3530, 3531, 4248]),
    ("Усадьба Филимонки", "Филимонковский · Новая Москва",
     "Усадьба начала XIX века в посёлке Филимонки, рядом с Троицким. "
     "Главный дом, флигель и сам усадебный комплекс.",
     [3764, 1343, 1344]),
]


def fdate(v):
    """Flight-дата вида $D2026-11-13T00:00:00.000Z -> IV кв. 2026."""
    if not v or not isinstance(v, str):
        return None
    m = re.search(r'(\d{4})-(\d{2})-(\d{2})', v)
    if not m:
        return None
    y, mo = int(m.group(1)), int(m.group(2))
    return f"{['I','II','III','IV'][(mo-1)//3]} кв. {y}"


def num(v, unit="", dec=1):
    if v is None:
        return "—"
    s = f"{v:,.{dec}f}".replace(",", " ").replace(".", ",")
    s = re.sub(r',0+$', '', s)
    return s + (f" {unit}" if unit else "")


def money(v):
    return "—" if v is None else f"{v:,.0f}".replace(",", " ") + " ₽"


def b64(path, width, quality):
    im = Image.open(path)
    if im.mode not in ("RGB", "L"):
        im = im.convert("RGB")
    if im.width > width:
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=quality, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


def photos(oid, limit=3):
    out = []
    for i in range(limit):
        p = f"{OUT}/photos/{oid}_{i}.jpg"
        if os.path.exists(p) and os.path.getsize(p) > 5000:
            out.append(b64(p, 760, 72))
    return out


def maps(oid):
    r = {}
    for k, suff, w, q in (("map", "_map", 760, 72), ("sat", "_sat", 760, 70)):
        p = f"{OUT}/maps/{oid}{suff}.png"
        if os.path.exists(p) and os.path.getsize(p) > 5000:
            r[k] = b64(p, w, q)
    return r


def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            if s is not None else "")


def row(k, v):
    return f'<div class="r"><dt>{esc(k)}</dt><dd>{v}</dd></div>'


def card(oid, idx):
    o = objs[str(oid)]
    ph, mp = photos(oid), maps(oid)
    st = o.get("status")
    okn_url = ("https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"
               + json.load(open(f"{OUT}/slugs.json"))[str(oid)])
    lat, lon = o.get("coordinateLatitude"), o.get("coordinateLongitude")

    gal = ""
    if ph:
        gal = '<div class="gal">' + "".join(
            f'<figure><img src="{u}" alt="{esc(o["name"])} — фото {i+1}">'
            f'</figure>' for i, u in enumerate(ph)) + '</div>'

    mapblock = ""
    if mp:
        cells = []
        if "map" in mp:
            cells.append(f'<figure><img src="{mp["map"]}" alt="Схема расположения" '
                         f'><figcaption>Схема · Яндекс Карты</figcaption></figure>')
        if "sat" in mp:
            cells.append(f'<figure><img src="{mp["sat"]}" alt="Спутник" '
                         f'><figcaption>Спутник · Яндекс Карты</figcaption></figure>')
        mapblock = ('<div class="maps"><h4>Расположение</h4><div class="mgrid">'
                    + "".join(cells) + '</div>'
                    + (f'<p class="coord">{lat}, {lon} · '
                       f'<a href="https://yandex.ru/maps/?ll={lon}%2C{lat}&amp;z=17&amp;'
                       f'pt={lon},{lat},pm2rdm" target="_blank" rel="noopener">'
                       f'открыть в Яндекс Картах</a></p>' if lat else "") + '</div>')

    facts = [
        row("Статус", f'<span class="st {"go" if st=="READY" else "wait"}">'
                      f'{esc(STATUS.get(st, st))}</span>'),
        row("Срок торгов", esc(fdate(o.get("auctionPeriod")) or "не назначен")),
        row("Площадь объекта", num(o.get("totalArea"), "м²")),
        row("Состояние", esc(COND.get(o.get("condition"), "—"))),
        row("Категория охраны", esc(SEC.get(o.get("securityCategory"), "—"))),
        row("Вид объекта", esc(TYPE.get(o.get("type"), "—"))),
        row("Построено", esc(o.get("constructionPeriod") or "—")),
        row("Номер ЕГРОКН", f'<code>{esc(o.get("egroknNum") or "—")}</code>'),
        row("Собственность", esc(OWN.get(o.get("ownType"), "—"))),
        row("Правообладатель", esc((o.get("realEstateParameters") or {}).get("owner") or "—")),
        row("Использование", esc(USE.get(o.get("actualUse"), "—"))),
    ]

    re_info = o.get("realEstateInfo") or []
    bl = ""
    if re_info:
        bl = "".join(
            f'<div class="sub"><code>{esc(b.get("cadastralNumber") or "—")}</code>'
            f'<span>{esc(b.get("wallMaterial") or "материал не указан")}'
            f'{" · " + esc(b["floors"]) + " эт." if b.get("floors") else ""}'
            f' · кад. стоимость {money(b.get("realEstateCost"))}</span></div>'
            for b in re_info)
        bl = f'<div class="block"><h4>Здания ({len(re_info)})</h4>{bl}</div>'

    lp = o.get("landPlotsParameters") or []
    lpb = ""
    if lp:
        lpb = "".join(
            f'<div class="sub"><code>{esc(p.get("cadastralNumber") or "—")}</code>'
            f'<span>{num(p.get("totalArea"), "м²", 0)} · '
            f'{esc(LAND.get(p.get("category"), p.get("category") or ""))} · '
            f'кад. стоимость {money(p.get("landPlotCost"))}</span>'
            f'<span class="vri">ВРИ: {esc(p.get("typeOfPermittedUse") or "—")}</span></div>'
            for p in lp)
        lpb = f'<div class="block"><h4>Земельный участок ({len(lp)})</h4>{lpb}</div>'

    eng = o.get("engineeringParameters") or {}
    engb = ""
    if eng:
        chips = "".join(
            f'<span class="chip {"yes" if eng.get(k)=="YES" else "no"}">{lbl}: '
            f'{"есть" if eng.get(k)=="YES" else "нет"}</span>' for k, lbl in ENG)
        engb = f'<div class="block"><h4>Инженерные коммуникации</h4><div class="chips">{chips}</div></div>'

    dl = o.get("documentsInfo") or []
    docb = ""
    if dl:
        seen, items = set(), []
        for d in dl:
            t = doc_label(d.get("documentType"))
            if t in seen:
                continue
            seen.add(t)
            items.append(f'<span class="chip doc">{esc(t)}</span>')
        docb = (f'<div class="block"><h4>Документы на портале ({len(dl)})</h4>'
                f'<div class="chips">{"".join(items)}</div></div>')

    hist = o.get("objectHistory") or ""
    histb = ""
    if hist.strip():
        paras = "".join(f"<p>{esc(x.strip())}</p>" for x in hist.split("\n") if x.strip())
        histb = f'<div class="block hist"><h4>История</h4>{paras}</div>'

    land_url = o.get("landDomUrl")
    links = [f'<a class="btn" href="{okn_url}" target="_blank" rel="noopener">Карточка ОКН</a>']
    if land_url:
        links.append(f'<a class="btn alt" href="{esc(land_url)}" target="_blank" '
                     f'rel="noopener">Лот на земля.дом.рф</a>')

    parent = o.get("parentOkn") or {}
    pline = (f'<p class="parent">В составе ансамбля: {esc(parent.get("name"))}</p>'
             if parent.get("name") else "")

    return f"""
<article class="okn" id="okn-{oid}">
  <header class="ohead">
    <div class="oid">{idx}</div>
    <div>
      <h3>{esc(o.get("name"))}</h3>
      <p class="addr">{esc(o.get("address"))}</p>
      {pline}
    </div>
    <div class="idtag">ID {oid}</div>
  </header>
  {gal}
  <dl class="facts">{"".join(facts)}</dl>
  {mapblock}
  {bl}{lpb}{engb}{docb}{histb}
  <div class="links">{"".join(links)}</div>
</article>"""


# Ансамбль в каталоге заведён отдельной карточкой и его площадь равна сумме
# площадей собственных строений — они в перечне тоже есть. Поэтому суммировать
# все пятнадцать строк нельзя: получится двойной счёт. Ниже — кто кого покрывает.
# Портал сам связь проставил не везде (у 4336 и у детей 3764 parentOkn пуст),
# поэтому состав задан явно и сверен по сумме.
COVERS = {
    4097: [4100, 4098, 4101, 4336],
    4103: [3529, 3532, 4248, 3530, 3531],
    3764: [1343, 1344],
}
ROOTS = [4097, 4103, 3764, 1498]


def unique_area():
    return sum(objs[str(i)]["totalArea"] for i in ROOTS)


def role(oid):
    if oid in COVERS:
        return "ансамбль"
    for parent, kids in COVERS.items():
        if oid in kids:
            return "в составе"
    return "отдельный"
