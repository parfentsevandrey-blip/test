"""Карточка объекта культурного наследия.

Карточка делится на три полосы (.p1 / .p2 / .p3): обзор с картой и паспортом,
таблицы с выжимкой из охранных документов и раздел фотографий. В печати
между полосами стоит перелом, поэтому блоки не свисают сиротами.

Фотографии подключаются файлами, а не вшиваются в страницу: так они остаются
в исходном разрешении портала — 3840 px по длинной стороне.
"""
import json, os

from build_okn import (objs, esc, num, money, fdate, COVERS,
                       STATUS, COND, TYPE, SEC, USE, LAND, ENG, OUT, b64)

SLUGS = json.load(open(f"{OUT}/slugs.json"))
SITE = "https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"
OWN2 = {"FEDERAL": "Федеральная", "RF_SUBJECT": "Субъекта РФ — город Москва",
        "MUNICIPAL": "Муниципальная", "PRIVATE": "Частная"}
RECOV = {"PREPARATION": "Подготовка к торгам", "PLACING": "Размещение на платформе",
         "DESIGN": "Проектирование", "RECOVERY": "Восстановление"}

try:
    DIGEST = json.load(open(f"{OUT}/digest.json"))
except Exception:
    DIGEST = {}


def rudate(v):
    if not v:
        return "—"
    d = str(v)[:10]
    return f"{d[8:10]}.{d[5:7]}.{d[0:4]}" if len(d) == 10 and d[4] == "-" else d


def wide_map(oid):
    p = f"{OUT}/maps_wide/{oid}.png"
    return b64(p, 1180, 76) if os.path.exists(p) and os.path.getsize(p) > 20000 else None


def photo_files(oid):
    """Пути к полноразмерным снимкам объекта рядом со страницей."""
    out = []
    for i in range(8):
        if os.path.exists(f"{OUT}/build/photos/{oid}_{i}.jpg"):
            out.append(f"photos/{oid}_{i}.jpg")
    return out


def kv_table(rows, cls="kv"):
    items = [(k, v) for k, v in rows if v not in (None, "")]
    half = (len(items) + 1) // 2
    out = []
    for col in (items[:half], items[half:]):
        body = "".join(f'<tr><th>{esc(k)}</th><td>{v}</td></tr>' for k, v in col)
        out.append(f'<table class="{cls}"><tbody>{body}</tbody></table>')
    return f'<div class="kvwrap">{"".join(out)}</div>'


def grid_table(head, rows, cls="grid"):
    if not rows:
        return ""
    th = "".join(f'<th{" class=n" if h[1] else ""}>{esc(h[0])}</th>' for h in head)
    body = ""
    for r in rows:
        tds = "".join(
            f'<td{" class=n" if head[i][1] else ""}'
            f'{" colspan=" + str(len(head)) if c is Ellipsis else ""}>'
            f'{r[i] if r[i] is not None else "—"}</td>'
            for i, c in enumerate(r) if c is not Ellipsis)
        body += f"<tr>{tds}</tr>"
    return f'<table class="{cls}"><thead><tr>{th}</tr></thead><tbody>{body}</tbody></table>'


def role_of(oid):
    if oid in COVERS:
        return "ансамбль целиком", "ens"
    for p, kids in COVERS.items():
        if oid in kids:
            return f"в составе: {(objs[str(p)].get('name') or '').strip(chr(34))}", "part"
    return "отдельный объект", "solo"


def sect(title, body):
    return f'<section class="block"><h4>{esc(title)}</h4>{body}</section>' if body else ""


def card(oid, idx):
    o = objs[str(oid)]
    st = o.get("status")
    url = SITE + SLUGS[str(oid)]
    lat, lon = o.get("coordinateLatitude"), o.get("coordinateLongitude")
    rname, _ = role_of(oid)
    name = o.get("name")

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
    <h3>{esc(name)}</h3>
    <p class="addr">{esc(o.get("address"))}</p>
    <div class="bdgs">{''.join(badges)}</div>
  </div>
  <div class="idtag">ЕГРОКН<br><span>{esc(o.get("egroknNum") or "—")}</span></div>
</header>"""

    mp = wide_map(oid)
    mapb = ""
    if mp:
        mapb = (f'<figure class="bigmap"><img src="{mp}" alt="Расположение объекта на карте">'
                f'<figcaption>Яндекс Карты · {lat}, {lon} · '
                f'<a href="https://yandex.ru/maps/?ll={lon}%2C{lat}&amp;z=17&amp;'
                f'pt={lon},{lat},pm2rdm" target="_blank" rel="noopener">открыть карту</a>'
                f'</figcaption></figure>')

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
    ])

    bl = grid_table(
        [("Кадастровый номер", 0), ("Материал стен", 0), ("Этажей", 1),
         ("Кадастровая стоимость", 1), ("На учёте с", 1)],
        [(f'<code>{esc(b.get("cadastralNumber") or "—")}</code>',
          esc(b.get("wallMaterial") or "не указан"),
          esc(b.get("floors") or "—"),
          money(b.get("realEstateCost")),
          rudate(b.get("realEstateRegDate")))
         for b in (o.get("realEstateInfo") or [])])

    lprows = []
    for p in (o.get("landPlotsParameters") or []):
        lprows.append((f'<code>{esc(p.get("cadastralNumber") or "—")}</code>',
                       num(p.get("totalArea"), "м²", 0),
                       esc(LAND.get(p.get("category"), p.get("category") or "—")),
                       money(p.get("landPlotCost")),
                       rudate(p.get("landPlotRegDate"))))
        lprows.append((f'<span class="vri"><b>Разрешённое использование:</b> '
                       f'{esc(p.get("typeOfPermittedUse") or "не указано")}</span>',
                       Ellipsis, Ellipsis, Ellipsis, Ellipsis))
    lpb = grid_table([("Кадастровый номер", 0), ("Площадь", 1), ("Категория земель", 0),
                      ("Кадастровая стоимость", 1), ("На учёте с", 1)], lprows)

    eng = o.get("engineeringParameters") or {}
    engb = grid_table([("Сеть", 0), ("Подключение", 1)],
                      [(esc(lbl),
                        f'<span class="yn {"y" if eng.get(k)=="YES" else "n"}">'
                        f'{"есть" if eng.get(k)=="YES" else "нет"}</span>')
                       for k, lbl in ENG], cls="grid eng")

    dg = DIGEST.get(str(oid), {})
    digest = ""
    if dg:
        parts = []
        for key, title in (("protection", "Предмет охраны — что менять нельзя"),
                           ("tech", "Техническое состояние"),
                           ("facts", "Что ещё известно об объекте")):
            if dg.get(key):
                parts.append(f'<div class="dg"><h5>{title}</h5><ul>'
                             + "".join(f"<li>{esc(x)}</li>" for x in dg[key]) + "</ul></div>")
        src = (f'<p class="dgsrc">По документам: {esc(dg["source"])}</p>'
               if dg.get("source") else "")
        digest = (f'<section class="block digest"><h4>Что говорят охранные документы</h4>'
                  f'{"".join(parts)}{src}</section>')

    hist = (o.get("objectHistory") or "").strip()
    histb = ""
    if hist:
        ps = "".join(f"<p>{esc(x.strip())}</p>" for x in hist.split("\n") if x.strip())
        histb = f'<section class="block hist"><h4>История объекта</h4>{ps}</section>'

    links = [f'<a class="btn" href="{url}" target="_blank" rel="noopener">Карточка на портале</a>']
    if o.get("landDomUrl"):
        links.append(f'<a class="btn alt" href="{esc(o["landDomUrl"])}" target="_blank" '
                     f'rel="noopener">Лот на земля.дом.рф</a>')

    pf = photo_files(oid)
    photos_block = ""
    if pf:
        shots = "".join(
            f'<figure class="shot"><img src="{u}" alt="{esc(name)} — фотография {i+1}">'
            f'<figcaption>{esc(name)} · фотография {i+1} из {len(pf)}</figcaption></figure>'
            for i, u in enumerate(pf))
        photos_block = f"""<div class="p3">
  <p class="cont">{idx}. {esc(name)} — фотографии</p>
  <section class="block photos"><h4>Фотографии объекта</h4>{shots}</section>
</div>"""

    return f"""
<article class="okn" id="okn-{oid}">
  <div class="p1">
    {head}
    {mapb}
    <section class="block"><h4>Паспорт объекта</h4>{passport}</section>
  </div>
  <div class="p2">
    <p class="cont">{idx}. {esc(name)} — продолжение</p>
    {sect("Здания", bl)}
    {sect("Земельный участок", lpb)}
    {sect("Инженерные коммуникации", engb)}
    {digest}
    {histb}
    <div class="links">{''.join(links)}</div>
  </div>
  {photos_block}
</article>"""
