"""Карточка объекта ОКН: таблицы вместо разрозненного текста.

Карточка сознательно делится на два разворота (.p1 / .p2): в печати между
ними стоит перелом полосы, поэтому блоки не свисают сиротами на следующую
страницу — раньше «Документы на портале» уезжали на пустую полосу.
"""
import json, os

from build_okn import (objs, esc, num, money, fdate, photos, COVERS,
                       STATUS, COND, TYPE, SEC, USE, LAND, ENG, doc_label, OUT, b64)

SLUGS = json.load(open(f"{OUT}/slugs.json"))
SITE = "https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"
OWN2 = {"FEDERAL": "Федеральная", "RF_SUBJECT": "Субъекта РФ — город Москва",
        "MUNICIPAL": "Муниципальная", "PRIVATE": "Частная"}
RECOV = {"PREPARATION": "Подготовка к торгам", "PLACING": "Размещение на платформе",
         "DESIGN": "Проектирование", "RECOVERY": "Восстановление"}

try:
    DOCIX = json.load(open(f"{OUT}/docs_index.json"))
except Exception:
    DOCIX = {}
try:
    DIGEST = json.load(open(f"{OUT}/digest.json"))
except Exception:
    DIGEST = {}


def rudate(v):
    """ISO-дату портала приводим к ДД.ММ.ГГГГ — в таблицах был разнобой."""
    if not v:
        return "—"
    d = str(v)[:10]
    if len(d) == 10 and d[4] == "-":
        return f"{d[8:10]}.{d[5:7]}.{d[0:4]}"
    return d


def wide_map(oid):
    p = f"{OUT}/maps_wide/{oid}.png"
    if os.path.exists(p) and os.path.getsize(p) > 20000:
        return b64(p, 1180, 76)
    return None


def kv_table(rows, cls="kv"):
    """Две колонки пар «поле — значение»: так паспорт не уезжает в простыню."""
    items = [(k, v) for k, v in rows if v not in (None, "")]
    half = (len(items) + 1) // 2
    cols = (items[:half], items[half:])
    out = []
    for col in cols:
        body = "".join(f'<tr><th>{esc(k)}</th><td>{v}</td></tr>' for k, v in col)
        out.append(f'<table class="{cls}"><tbody>{body}</tbody></table>')
    return f'<div class="kvwrap">{"".join(out)}</div>'


def grid_table(head, rows, cls="grid", foot=None):
    if not rows:
        return ""
    th = "".join(f'<th{" class=n" if h[1] else ""}>{esc(h[0])}</th>' for h in head)
    body = ""
    for r in rows:
        tds = "".join(
            f'<td{" class=n" if head[i][1] else ""}{" colspan=" + str(len(head)) if c is Ellipsis else ""}>'
            f'{r[i] if r[i] is not None else "—"}</td>'
            for i, c in enumerate(r) if c is not Ellipsis)
        body += f"<tr>{tds}</tr>"
    tf = f"<tfoot>{foot}</tfoot>" if foot else ""
    return f'<table class="{cls}"><thead><tr>{th}</tr></thead><tbody>{body}</tbody>{tf}</table>'


def role_of(oid):
    """Роль объекта в комплексе — словами, а не номером карточки."""
    if oid in COVERS:
        return "ансамбль целиком", "ens"
    for p, kids in COVERS.items():
        if oid in kids:
            nm = (objs[str(p)].get("name") or "").strip('"')
            return f"в составе: {nm}", "part"
    return "отдельный объект", "solo"


def sect(title, body):
    """Блок печатается только если внутри что-то есть — иначе остаётся
    висячий заголовок, как было у ансамблей без собственных строений."""
    return f'<section class="block"><h4>{esc(title)}</h4>{body}</section>' if body else ""


def card(oid, idx):
    o = objs[str(oid)]
    st = o.get("status")
    url = SITE + SLUGS[str(oid)]
    lat, lon = o.get("coordinateLatitude"), o.get("coordinateLongitude")
    rname, rcls = role_of(oid)

    # ---- шапка -------------------------------------------------------------
    parent = (o.get("parentOkn") or {}).get("name")
    badges = [f'<span class="bdg {"go" if st=="READY" else "wait"}">'
              f'{esc(STATUS.get(st, st))}</span>']
    if o.get("securityCategory") == "FEDERAL_IMPORTANCE":
        badges.append('<span class="bdg fed">Федерального значения</span>')
    if o.get("type") == "ENSEMBLE":
        badges.append('<span class="bdg ens">Ансамбль</span>')
    if o.get("agencyWork"):
        badges.append('<span class="bdg ag">В агентировании ДОМ.РФ</span>')

    head = f"""<header class="ohead">
  <div class="oid">{idx}</div>
  <div class="otitle">
    <h3>{esc(o.get("name"))}</h3>
    <p class="addr">{esc(o.get("address"))}</p>
    <div class="bdgs">{''.join(badges)}</div>
  </div>
  <div class="idtag">ID {oid}<br><span>ЕГРОКН {esc(o.get("egroknNum") or "—")}</span></div>
</header>"""

    # ---- фото и карта ------------------------------------------------------
    ph = photos(oid, 3)
    gal = ('<div class="gal">' + "".join(
        f'<figure><img src="{u}" alt="{esc(o["name"])} — кадр {i+1}"></figure>'
        for i, u in enumerate(ph)) + "</div>") if ph else ""

    mp = wide_map(oid)
    mapb = ""
    if mp:
        mapb = (f'<figure class="bigmap"><img src="{mp}" alt="Расположение объекта на карте">'
                f'<figcaption>Яндекс Карты · {lat}, {lon} · '
                f'<a href="https://yandex.ru/maps/?ll={lon}%2C{lat}&amp;z=17&amp;'
                f'pt={lon},{lat},pm2rdm" target="_blank" rel="noopener">открыть карту</a>'
                f'</figcaption></figure>')

    # ---- паспорт -----------------------------------------------------------
    land_total = sum(p.get("totalArea") or 0 for p in (o.get("landPlotsParameters") or []))
    re_cost = sum(b.get("realEstateCost") or 0 for b in (o.get("realEstateInfo") or []))
    lp_cost = sum(p.get("landPlotCost") or 0 for p in (o.get("landPlotsParameters") or []))
    passport = kv_table([
        ("Статус реализации", f'<b class="{"go" if st=="READY" else "wait"}">'
                              f'{esc(STATUS.get(st, st))}</b>'),
        ("Срок проведения торгов", esc(fdate(o.get("auctionPeriod")) or "не назначен")),
        ("Этап", esc(RECOV.get(o.get("recoveryStatus"), "—"))),
        ("Площадь объекта", f'<b>{num(o.get("totalArea"), "м²")}</b>'),
        ("Площадь участка", num(land_total, "м²", 0) if land_total else "—"),
        ("Состояние", esc(COND.get(o.get("condition"), "—"))),
        ("Вид объекта", esc(TYPE.get(o.get("type"), "—"))),
        ("Категория охраны", esc(SEC.get(o.get("securityCategory"), "—"))),
        ("Роль в комплексе", esc(rname)),
        ("Построено", esc(o.get("constructionPeriod") or "—")),
        ("Вид собственности", esc(OWN2.get(o.get("ownType"), "—"))),
        ("Правообладатель", esc((o.get("realEstateParameters") or {}).get("owner") or "—")),
        ("Фактическое использование", esc(USE.get(o.get("actualUse"), "—"))),
        ("Кадастровая стоимость, здания", money(re_cost) if re_cost else "—"),
        ("Кадастровая стоимость, участок", money(lp_cost) if lp_cost else "—"),
        ("Входит в ансамбль", esc(parent) if parent else "—"),
    ])

    # ---- здания ------------------------------------------------------------
    bl = grid_table(
        [("Кадастровый номер", 0), ("Материал стен", 0), ("Этажей", 1),
         ("Кадастровая стоимость", 1), ("На учёте с", 1)],
        [(f'<code>{esc(b.get("cadastralNumber") or "—")}</code>',
          esc(b.get("wallMaterial") or "не указан"),
          esc(b.get("floors") or "—"),
          money(b.get("realEstateCost")),
          rudate(b.get("realEstateRegDate")))
         for b in (o.get("realEstateInfo") or [])])

    # ---- участок -----------------------------------------------------------
    lps = o.get("landPlotsParameters") or []
    lprows = []
    for p in lps:
        lprows.append((f'<code>{esc(p.get("cadastralNumber") or "—")}</code>',
                       num(p.get("totalArea"), "м²", 0),
                       esc(LAND.get(p.get("category"), p.get("category") or "—")),
                       money(p.get("landPlotCost")),
                       rudate(p.get("landPlotRegDate"))))
        lprows.append((f'<span class="vri"><b>ВРИ:</b> '
                       f'{esc(p.get("typeOfPermittedUse") or "не указан")}</span>',
                       Ellipsis, Ellipsis, Ellipsis, Ellipsis))
    lpb = grid_table([("Кадастровый номер", 0), ("Площадь", 1), ("Категория земель", 0),
                      ("Кадастровая стоимость", 1), ("На учёте с", 1)], lprows)

    # ---- коммуникации ------------------------------------------------------
    eng = o.get("engineeringParameters") or {}
    engrows = [(esc(lbl),
                f'<span class="yn {"y" if eng.get(k)=="YES" else "n"}">'
                f'{"есть" if eng.get(k)=="YES" else "нет"}</span>')
               for k, lbl in ENG]
    engb = grid_table([("Сеть", 0), ("Подключение", 1)], engrows, cls="grid eng")

    # ---- документы ---------------------------------------------------------
    drows = []
    for r in DOCIX.get(str(oid), []):
        drows.append((esc(doc_label(r["type"])),
                      rudate(r.get("publishedAt")),
                      f'{r["pages"]}' if r.get("pages") else "—",
                      f'{(r.get("size") or 0)/1048576:.1f} МБ'.replace(".", ","),
                      f'<a href="{SITE}/okn/api/portal/document?documentId={r["id"]}" '
                      f'target="_blank" rel="noopener">скачать</a>'))
    docb = grid_table([("Документ", 0), ("Размещён", 1), ("Страниц", 1),
                       ("Размер", 1), ("", 1)], drows)

    # ---- выжимка из документов --------------------------------------------
    dg = DIGEST.get(str(oid), {})
    digest = ""
    if dg:
        parts = []
        if dg.get("protection"):
            parts.append('<div class="dg"><h5>Предмет охраны — что менять нельзя</h5>'
                         + "<ul>" + "".join(f"<li>{esc(x)}</li>" for x in dg["protection"])
                         + "</ul></div>")
        if dg.get("tech"):
            parts.append('<div class="dg"><h5>Техническое состояние по акту</h5>'
                         + "<ul>" + "".join(f"<li>{esc(x)}</li>" for x in dg["tech"])
                         + "</ul></div>")
        if dg.get("facts"):
            parts.append('<div class="dg"><h5>Что ещё следует из документов</h5>'
                         + "<ul>" + "".join(f"<li>{esc(x)}</li>" for x in dg["facts"])
                         + "</ul></div>")
        src = dg.get("source")
        srcline = (f'<p class="dgsrc">Источник: {esc(src)}</p>' if src else "")
        digest = (f'<section class="block digest"><h4>Выжимка из документов</h4>'
                  f'{"".join(parts)}{srcline}</section>')

    # ---- история -----------------------------------------------------------
    hist = (o.get("objectHistory") or "").strip()
    histb = ""
    if hist:
        ps = "".join(f"<p>{esc(x.strip())}</p>" for x in hist.split("\n") if x.strip())
        histb = f'<section class="block hist"><h4>История объекта</h4>{ps}</section>'

    # ---- ссылки ------------------------------------------------------------
    links = [f'<a class="btn" href="{url}" target="_blank" rel="noopener">Карточка на портале</a>']
    if o.get("landDomUrl"):
        links.append(f'<a class="btn alt" href="{esc(o["landDomUrl"])}" target="_blank" '
                     f'rel="noopener">Лот на земля.дом.рф</a>')

    return f"""
<article class="okn" id="okn-{oid}">
  <div class="p1">
    {head}
    {gal}
    {mapb}
    <section class="block"><h4>Паспорт объекта</h4>{passport}</section>
  </div>
  <div class="p2">
    <p class="cont">{idx}. {esc(o.get('name'))} — продолжение</p>
    {sect("Здания", bl)}
    {sect("Земельный участок", lpb)}
    {sect("Инженерные коммуникации", engb)}
    {sect("Документы на портале", docb)}
    {digest}
    {histb}
    <div class="links">{''.join(links)}</div>
  </div>
</article>"""
