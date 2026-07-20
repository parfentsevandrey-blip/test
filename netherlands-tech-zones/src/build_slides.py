# -*- coding: utf-8 -*-
"""Build a premium 16:9 PDF presentation (Playwright + Chromium). Redesigned look."""
import os, html, base64
from urllib.parse import quote
import qrcode
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers.pil import RoundedModuleDrawer
from qrcode.image.styles.colormasks import SolidFillColorMask
import deck_data as D

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs("assets/qr", exist_ok=True)

# ---------- palette ----------
INK = "#0F1B34"; INK2 = "#18294A"
ORANGE = "#F26313"; ORANGE_L = "#FF9A4D"
BLUE = "#2563EB"; BLUE_L = "#5C9BFF"
GREEN = "#0E9E6E"; GREEN_L = "#3FD69B"
MUTED = "#5E6980"; LINE = "#E7EBF1"; PAPER = "#F5F8FC"; GOLD = "#E0A93B"
CATCOL = {"chips": ORANGE, "data": BLUE, "ai": GREEN}
CATCOL_L = {"chips": ORANGE_L, "data": BLUE_L, "ai": GREEN_L}
CATLABEL = {"chips": "ЧИПЫ", "data": "ДАТА-ЦЕНТРЫ", "ai": "ИИ И НАУКА"}
CATICON = {"chips": "chip", "data": "cloud", "ai": "atom"}


def grad(cat, deg=135):
    return f"linear-gradient({deg}deg, {CATCOL[cat]}, {CATCOL_L[cat]})"


# ---------- fonts (embedded) ----------
def _b64(p):
    return base64.b64encode(open(p, "rb").read()).decode()


def font_faces():
    CYR = "U+0400-04FF,U+0500-052F,U+2DE0-2DFF,U+A640-A69F,U+FE2E-FE2F"
    LAT = "U+0000-00FF,U+0131,U+0152-0153,U+2000-206F,U+2074,U+20AC,U+2122,U+2190-2193,U+2212,U+2215,U+25A0-25FF"
    css = ""
    for fam, cyr, lat in [("Inter", "fonts/inter-cyr.woff2", "fonts/inter-lat.woff2"),
                          ("Manrope", "fonts/manrope-cyr.woff2", "fonts/manrope-lat.woff2")]:
        for rng, path in [(CYR, cyr), (LAT, lat)]:
            css += (f"@font-face{{font-family:'{fam}';font-style:normal;font-weight:100 900;"
                    f"font-display:block;src:url(data:font/woff2;base64,{_b64(path)}) format('woff2');"
                    f"unicode-range:{rng};}}\n")
    return css


# ---------- icons ----------
ICONS = {
    "chip": '<rect x="6.5" y="6.5" width="11" height="11" rx="1.6"/><rect x="9.5" y="9.5" width="5" height="5" rx="1"/><path d="M9 3v3.5M12 3v3.5M15 3v3.5M9 17.5V21M12 17.5V21M15 17.5V21M3 9h3.5M3 12h3.5M3 15h3.5M17.5 9H21M17.5 12H21M17.5 15H21"/>',
    "cloud": '<path d="M7.5 18.5h9a4 4 0 0 0 .4-8A6 6 0 0 0 5.6 9 4.5 4.5 0 0 0 7.5 18.5z"/><path d="M9.5 15v-2M12 15.5v-3M14.5 15v-2"/>',
    "atom": '<circle cx="12" cy="12" r="1.8"/><ellipse cx="12" cy="12" rx="10" ry="4.4"/><ellipse cx="12" cy="12" rx="10" ry="4.4" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="10" ry="4.4" transform="rotate(120 12 12)"/>',
    "bolt": '<path d="M13.5 2.5 5 13.5h5.5L9.5 21.5 19 10h-6z"/>',
    "drop": '<path d="M12 3.2C12 3.2 5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 3.2 12 3.2z"/>',
    "shield": '<path d="M12 3 5 5.6v5.2c0 4.3 3 8 7 9.4 4-1.4 7-5.1 7-9.4V5.6L12 3z"/><path d="M9 12l2 2 4-4"/>',
    "euro": '<circle cx="12" cy="12" r="8.5"/><path d="M15 8.6a4.5 4.5 0 1 0 0 6.8M7.5 11h6M7.5 13.5h5"/>',
    "pin": '<path d="M12 21s7-6.3 7-11a7 7 0 1 0-14 0c0 4.7 7 11 7 11z"/><circle cx="12" cy="10" r="2.6"/>',
    "globe": '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.3 3.8 5.3 3.8 8.5S14.4 18.2 12 20.5c-2.4-2.3-3.8-5.3-3.8-8.5S9.6 5.8 12 3.5z"/>',
    "spark": '<path d="M12 3v5M12 16v5M3 12h5M16 12h5M6 6l3 3M15 15l3 3M18 6l-3 3M9 15l-3 3"/>',
    "arrow": '<path d="M5 12h14M13 6l6 6-6 6"/>',
    "flag": '<path d="M6 21V4M6 5h11l-2 3 2 3H6"/>',
}


def icon(name, color=INK, size=24, sw=1.9):
    return (f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="none" '
            f'stroke="{color}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">'
            f'{ICONS[name]}</svg>')


def icon_fill(name, color=INK, size=24):
    return (f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="{color}" '
            f'stroke="none">{ICONS[name]}</svg>')


# ---------- assets helpers ----------
def data_uri(path):
    ext = "png" if path.lower().endswith("png") else "jpeg"
    with open(path, "rb") as f:
        return f"data:image/{ext};base64," + base64.b64encode(f.read()).decode()


def esc(s):
    return html.escape(str(s))


def gmaps_url(query):
    return "https://www.google.com/maps/search/?api=1&query=" + quote(query)


def _hex(h):
    h = h.lstrip("#"); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def make_qr(url, key, color):
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=1)
    q.add_data(url); q.make(fit=True)
    pic = q.make_image(image_factory=StyledPilImage, module_drawer=RoundedModuleDrawer(),
                       color_mask=SolidFillColorMask(front_color=_hex(color), back_color=(255, 255, 255)))
    path = f"assets/qr/qr_{key}.png"; pic.save(path); return path


SLIDES = []
def _pos(): return len(SLIDES) + 1
def slide(cls, inner): SLIDES.append(f'<section class="slide {cls}">{inner}</section>')
def footer(n):
    return (f'<div class="footer"><span class="fl">Технологические промзоны Нидерландов</span>'
            f'<span class="fr">{n:02d}<span class="ft">/ {TOTAL:02d}</span></span></div>')


# ================= extra content for new slides =================
HERO = [
    ("≈100%", "мирового рынка EUV-литографии — у ASML", "chip", "chips"),
    ("€32,7 млрд", "выручка ASML за 2025 год", "euro", "chips"),
    ("$675 млрд", "капитализация ASML — №1 в Европе", "spark", "chips"),
    ("15 Тбит/с", "пиковый трафик узла AMS-IX", "cloud", "data"),
    ("~852 МВт", "мощность дата-центров Амстердама", "bolt", "data"),
    ("~60 000", "рабочих мест в чип-секторе", "globe", "ai"),
]
CHALLENGES = [
    ("bolt", "Перегрузка электросетей", "К концу 2025 года около 14 000 компаний стояли в очереди на подключение к электричеству. Операторы вложат до €8 млрд в год, чтобы разгрузить сети."),
    ("shield", "Мораторий на большие ЦОД", "С 2024 года новые гиперскейл-дата-центры (>70 МВт или >10 га) разрешено строить лишь в двух зонах — в Эмсхавене и районе Мидденмера."),
    ("drop", "Энергия и вода", "Кампус Microsoft в Мидденмере потребляет ~1% всего электричества страны; спор о расходе питьевой воды на охлаждение стал публичным."),
    ("euro", "Поддержка государства", "«Проект Бетховен»: €2,5 млрд на жильё, дороги, сети и образование в регионе Эйндховена, чтобы удержать ASML и её экосистему."),
]
CHARTS = [
    ("assets/chart_asml.png", "Выручка ASML", "Спрос на чипы и ИИ тянет за собой рекордный рост."),
    ("assets/chart_investment.png", "Инвестиции в ЦОД", "Гиперскейлеры вложили в 3 площадки более €3,5 млрд."),
    ("assets/chart_amsterdam_mw.png", "Мощность ЦОД Амстердама", "Один из 4 крупнейших рынков Европы (FLAP-D)."),
]


# ================= slide builders =================
def cover_slide():
    bg = data_uri("assets/cover.jpg")
    chips = "".join(
        f'<span class="ctag"><span class="cdot" style="background:{grad(c)}"></span>{lab}</span>'
        for c, lab in [("chips", "Чипы"), ("data", "Дата-центры"), ("ai", "ИИ и наука")])
    inner = f'''
      <div class="cov-bg" style="background-image:url('{bg}')"></div>
      <div class="cov-scrim"></div>
      <div class="cov-grid"></div>
      <div class="cov-body">
        <div class="cov-kick"><span class="kbar"></span>{esc(D.META["kicker"])}</div>
        <h1 class="cov-title">Технологические<br>промзоны <span class="cov-accent">Нидерландов</span></h1>
        <div class="cov-sub">{esc(D.META["subtitle"])}</div>
        <div class="cov-tags">{chips}</div>
      </div>
      <div class="cov-foot"><span>Аналитическая презентация</span><span class="cov-dot">•</span><span>{esc(D.META["date"])}</span></div>'''
    slide("cover", inner)


def hero_slide():
    cells = ""
    for val, lab, ic, cat in HERO:
        cells += f'''
          <div class="hero-card">
            <div class="hero-ic" style="background:{grad(cat)}">{icon(ic, "#fff", 26)}</div>
            <div class="hero-val">{val}</div>
            <div class="hero-lab">{lab}</div>
          </div>'''
    inner = f'''
      <div class="hero-bgshape"></div>
      <div class="hero-wrap">
        <div class="eyebrow eyebrow-light">НИДЕРЛАНДЫ В ЦИФРАХ</div>
        <h2 class="hero-h">Маленькая страна — <span class="grad-text">огромный вес</span></h2>
        <p class="hero-lead">Три направления — чипы, дата-центры и искусственный интеллект — делают Нидерланды одним из технологических центров планеты.</p>
        <div class="hero-grid">{cells}</div>
      </div>
      {footer(_pos())}'''
    slide("dark hero", inner)


def overview_slide():
    img = data_uri("assets/national.jpg")
    legend = "".join(
        f'<div class="leg"><span class="leg-ic" style="background:{grad(c)}">{icon(CATICON[c], "#fff", 15, 2.1)}</span>'
        f'<div><b>{t}</b><span>{sub}</span></div></div>'
        for c, t, sub in [("chips", "Производство чипов", "Эйндховен, Неймеген"),
                          ("data", "Дата-центры", "Амстердам, Эмсхавен, Мидденмер"),
                          ("ai", "ИИ и наука", "Делфт, университеты")])
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ОБЗОР</div>
        <h2 class="stitle">Семь ключевых зон на карте страны</h2>
        <div class="ov-wrap">
          <div class="ov-map-box"><img class="ov-map" src="{img}"/></div>
          <div class="ov-side">
            <p class="ov-lead">Технологическая сила страны сосредоточена в нескольких компактных зонах вдоль главных магистралей и портов.</p>
            <div class="ov-legend">{legend}</div>
            <div class="ov-note">{icon("pin", ORANGE, 18)}<span>Далее — каждая зона отдельно: точный адрес, резиденты и QR-код со ссылкой на Google&nbsp;Карты.</span></div>
          </div>
        </div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def divider_slide(cat, zone_titles):
    s = D.SECTIONS[cat]
    img = data_uri(s["img"])
    lst = "".join(f'<li>{icon("arrow", "#fff", 16, 2.2)}<span>{esc(t)}</span></li>' for t in zone_titles)
    inner = f'''
      <div class="div-left" style="background:{INK}">
        <div class="div-glow" style="background:{grad(cat)}"></div>
        <div class="div-num-wrap">
          <div class="div-ic" style="background:{grad(cat)}">{icon(CATICON[cat], "#fff", 34, 2.0)}</div>
          <div class="div-num">{s["num"]}</div>
        </div>
        <div class="div-cat" style="color:{CATCOL_L[cat]}">{CATLABEL[cat]}</div>
        <h2 class="div-title">{esc(s["title"])}</h2>
        <div class="div-sub">{esc(s["sub"])}</div>
        <ul class="div-list">{lst}</ul>
      </div>
      <div class="div-right"><img src="{img}"/><div class="div-right-scrim" style="box-shadow: inset 90px 0 120px -40px {INK}"></div></div>'''
    slide("divider", inner)


def zone_profile_slide(z):
    p = D.PROFILES[z["key"]]; cat = z["cat"]; col = CATCOL[cat]
    res = "".join(f'<li><b style="color:{col}">{esc(n)}</b> — {esc(w)}</li>' for n, w in p["residents"])
    sph = "".join(f'<li><span class="sph-ic" style="color:{col}">{icon("spark", col, 15, 2.2)}</span>{esc(s)}</li>' for s in p["spheres"])
    ctx = "".join(f'<div class="ctx-card" style="border-color:{col}">{esc(c)}</div>' for c in p.get("context", []))
    inner = f'''
      <div class="z-band" style="background:{grad(cat)}"></div>
      <div class="zp2">
        <div class="zp2-top">
          <div class="zp2-badge-ic" style="background:{grad(cat)}">{icon(CATICON[cat], "#fff", 24, 2.0)}</div>
          <div>
            <div class="eyebrow" style="color:{col}">ПРОМЗОНА · {CATLABEL[cat]}</div>
            <div class="zp2-head">
              <h2 class="zp-name">{esc(p["zone_title"])}</h2>
              <span class="zp2-type" style="background:{grad(cat)}">{esc(p["type"])}</span>
            </div>
            <div class="zp-loc">{icon("pin", MUTED, 15)} {esc(z["city"])}, {esc(z["region"])}</div>
          </div>
        </div>
        <p class="zp2-about">{esc(p["about"])}</p>
        <div class="zp2-cols">
          <div class="zp2-col">
            <div class="zp2-h" style="color:{col};border-color:{col}">РЕЗИДЕНТЫ И ЧЕМ ЗАНИМАЮТСЯ</div>
            <ul class="zp2-res">{res}</ul>
          </div>
          <div class="zp2-col zp2-col-narrow">
            <div class="zp2-h" style="color:{col};border-color:{col}">СФЕРЫ ДЕЯТЕЛЬНОСТИ</div>
            <ul class="zp2-sph">{sph}</ul>
          </div>
        </div>
        <div class="ctx-title" style="color:{col}">КОНТЕКСТ И ЗНАЧЕНИЕ</div>
        <div class="ctx-row">{ctx}</div>
      </div>
      {footer(_pos())}'''
    slide("content zoneprofile", inner)


def zone_slide(z):
    cat = z["cat"]; col = CATCOL[cat]
    m = data_uri(z["img"])
    url = gmaps_url(z.get("maps_query") or z["address"])
    qr = data_uri(make_qr(url, z["key"], INK))
    residents = ""
    for r in z["residents"]:
        addr = f'<div class="r-addr">{icon("pin", MUTED, 13)} {esc(r["addr"])}</div>' if r.get("addr") else ""
        residents += f'''
          <div class="resident">
            <div class="r-head"><span class="r-name" style="color:{col}">{esc(r["name"])}</span>
            <span class="r-tag" style="color:{col};background:{col}18">{esc(r["tag"])}</span></div>
            <div class="r-text">{esc(r["text"])}</div>{addr}
          </div>'''
    facts = "".join(f'<span class="fact">{esc(f)}</span>' for f in z["facts"])
    inner = f'''
      <div class="z-band" style="background:{grad(cat)}"></div>
      <div class="zg">
        <div class="zg-left">
          <div class="map-frame"><img class="z-map" src="{m}"/></div>
          <div class="z-mapcap">{icon("globe", MUTED, 14)} {esc(z.get("map_caption", "Карта Google"))}</div>
        </div>
        <div class="zg-right">
          <div class="eyebrow" style="color:{col}">{icon(CATICON[cat], col, 16, 2.2)} {CATLABEL[cat]}</div>
          <h2 class="z-name">{esc(z["name"])}</h2>
          <div class="addr-card" style="border-color:{col}22">
            <div class="pin-badge" style="background:{grad(cat)}">{icon("pin", "#fff", 18)}</div>
            <div><b>{esc(z["address"])}</b><span class="z-city">{esc(z["city"])}, {esc(z["region"])}</span></div>
          </div>
          <div class="maps-row">
            <img class="qr" src="{qr}"/>
            <div>
              <div class="maps-title">Google Карты</div>
              <div class="maps-sub">Наведите камеру телефона на QR-код,<br>чтобы открыть точное место на карте</div>
              <a class="maps-link" style="color:{BLUE}" href="{url}">Открыть в Google&nbsp;Картах {icon("arrow", BLUE, 14, 2.4)}</a>
            </div>
          </div>
          <div class="z-restitle" style="color:{col}">РЕЗИДЕНТЫ</div>
          <div class="residents">{residents}</div>
          <div class="z-facts">{facts}</div>
        </div>
      </div>
      {footer(_pos())}'''
    slide("content zone", inner)


def charts_slide():
    cards = ""
    for path, title, take in CHARTS:
        cards += f'''<div class="chart-card">
          <div class="chart-t">{esc(title)}</div>
          <img src="{data_uri(path)}"/>
          <div class="chart-take">{esc(take)}</div></div>'''
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ЭКОНОМИКА</div>
        <h2 class="stitle">Рост, мощности и инвестиции — в графиках</h2>
        <div class="charts-row">{cards}</div>
        <div class="charts-note">{icon("euro", ORANGE, 18)}<span>Полупроводниковый сектор Нидерландов: 300+ компаний, ~9% мирового рынка, экспорт оборудования ~€25 млрд в год.</span></div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def challenges_slide():
    cards = ""
    cols = [ORANGE, BLUE, GREEN, GOLD]
    for i, (ic, t, txt) in enumerate(CHALLENGES):
        c = cols[i % len(cols)]
        cards += f'''<div class="ch-card">
          <div class="ch-ic" style="background:{c}14;color:{c}">{icon(ic, c, 26)}</div>
          <div class="ch-t">{esc(t)}</div>
          <div class="ch-x">{esc(txt)}</div></div>'''
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ОБОРОТНАЯ СТОРОНА</div>
        <h2 class="stitle">Вызовы, политика и поддержка</h2>
        <p class="ch-lead">Быстрый рост упирается в физические пределы страны — энергию, землю и воду. Государство отвечает и ограничениями, и крупными вложениями.</p>
        <div class="ch-grid">{cards}</div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def compare_slide():
    rows = ""
    for cat, zone, anchor, why in D.COMPARE:
        c = CATCOL[cat]
        rows += (f'<tr><td><span class="cbar" style="background:{grad(cat)}"></span>{esc(zone)}</td>'
                 f'<td><b>{esc(anchor)}</b></td><td class="why">{esc(why)}</td></tr>')
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ИТОГ</div>
        <h2 class="stitle">Сравнение ключевых зон</h2>
        <table class="ctable">
          <thead><tr><th>Зона</th><th>Якорные резиденты</th><th>Чем важна</th></tr></thead>
          <tbody>{rows}</tbody>
        </table>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def closing_slide():
    cols = "".join(
        f'<div class="cc"><div class="cc-big grad-text">{v}</div><div class="cc-lab">{l}</div></div>'
        for v, l in [("7", "ключевых зон"), ("3", "направления"), ("№1", "ASML — в Европе")])
    inner = f'''
      <div class="close-glow"></div>
      <div class="close">
        <div class="eyebrow eyebrow-light">СПАСИБО ЗА ВНИМАНИЕ</div>
        <h2 class="close-title">Маленькая страна — <span class="grad-text">огромное влияние</span></h2>
        <p class="close-text">Нидерланды объединяют производство чипов, «облачные» дата-центры и науку об искусственном интеллекте в единую технологическую экосистему мирового значения.</p>
        <div class="close-cols">{cols}</div>
        <div class="close-src">Источники: годовые отчёты ASML, NXP; AMS-IX; datacenters.google; news.microsoft.com; qutech.nl; brainporteindhoven.com; rijksoverheid.nl &nbsp;·&nbsp; Карты: © OpenStreetMap · CARTO</div>
      </div>'''
    slide("dark closing", inner)


# ================= assemble =================
order = [("chips", ["asml", "htc", "nijmegen"]),
         ("data", ["amsterdam", "eemshaven", "agriport"]),
         ("ai", ["delft"])]
zmap = {z["key"]: z for z in D.ZONES}
znames = {z["key"]: D.PROFILES[z["key"]]["zone_title"] for z in D.ZONES}

# pre-count total slides for footer "NN / TOTAL"
TOTAL = 3 + sum(1 + 2 * len(ks) for _, ks in order) + 3  # cover+hero+overview + (divider+2*zones) + charts+challenges+compare ... plus closing
# compute exactly:
_n = 1  # cover
_n += 1  # hero
_n += 1  # overview
for cat, ks in order:
    _n += 1                # divider
    _n += 2 * len(ks)      # profile + residents
_n += 1  # charts
_n += 1  # challenges
_n += 1  # compare
_n += 1  # closing
TOTAL = _n

cover_slide()
hero_slide()
overview_slide()
for cat, keys in order:
    divider_slide(cat, [znames[k] for k in keys])
    for k in keys:
        zone_profile_slide(zmap[k])
        zone_slide(zmap[k])
charts_slide()
challenges_slide()
compare_slide()
closing_slide()

# ================= CSS =================
CSS = font_faces() + f'''
@page {{ size: 1280px 720px; margin: 0; }}
* {{ box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
html, body {{ margin: 0; padding: 0; background: #fff; }}
.slide {{ width: 1280px; height: 720px; position: relative; overflow: hidden; background: #fff;
  font-family: 'Inter', sans-serif; color: #23303F; page-break-after: always; }}
.slide:last-child {{ page-break-after: auto; }}
h1, h2, .stitle, .zp-name, .z-name, .hero-h, .div-title, .close-title, .cc-big, .hero-val {{ font-family: 'Manrope', sans-serif; }}
.grad-text {{ background: linear-gradient(100deg, {ORANGE}, {GOLD}); -webkit-background-clip: text; background-clip: text; color: transparent; }}
.pad {{ padding: 52px 66px; height: 100%; }}
.eyebrow {{ font-size: 14px; font-weight: 800; letter-spacing: 2.6px; display: flex; align-items: center; gap: 8px; }}
.eyebrow-light {{ color: {ORANGE_L}; }}
.stitle {{ font-size: 39px; font-weight: 800; color: {INK}; margin: 9px 0 26px; letter-spacing: -.5px; }}
.footer {{ position: absolute; bottom: 20px; left: 66px; right: 66px; display: flex; justify-content: space-between;
  align-items: center; font-size: 12.5px; color: {MUTED}; border-top: 1px solid {LINE}; padding-top: 11px; }}
.footer .fr {{ font-weight: 800; color: {INK}; font-family: 'Manrope'; }}
.footer .ft {{ color: {MUTED}; font-weight: 600; margin-left: 3px; }}

/* ---- Cover ---- */
.cover {{ background: {INK}; }}
.cov-bg {{ position: absolute; inset: 0; background-size: cover; background-position: center; }}
.cov-scrim {{ position: absolute; inset: 0; background:
  linear-gradient(103deg, rgba(9,15,29,.96) 32%, rgba(9,15,29,.66) 60%, rgba(9,15,29,.24) 100%); }}
.cov-grid {{ position: absolute; inset: 0; opacity: .5;
  background-image: linear-gradient(rgba(255,255,255,.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 46px 46px; mask-image: linear-gradient(103deg, #000 30%, transparent 75%); }}
.cov-body {{ position: absolute; left: 76px; top: 150px; width: 800px; color: #fff; }}
.cov-kick {{ display: flex; align-items: center; gap: 12px; font-size: 16px; letter-spacing: 4px; font-weight: 800; color: {ORANGE_L}; margin-bottom: 22px; }}
.kbar {{ width: 34px; height: 4px; border-radius: 3px; background: linear-gradient(90deg, {ORANGE}, {ORANGE_L}); }}
.cov-title {{ font-size: 66px; line-height: 1.03; font-weight: 800; margin: 0; letter-spacing: -1.5px; }}
.cov-accent {{ background: linear-gradient(100deg, {ORANGE}, {GOLD}); -webkit-background-clip: text; background-clip: text; color: transparent; }}
.cov-sub {{ font-size: 24px; color: #CBD5E6; margin-top: 22px; font-weight: 500; max-width: 660px; }}
.cov-tags {{ display: flex; gap: 12px; margin-top: 34px; }}
.ctag {{ display: flex; align-items: center; gap: 9px; background: rgba(255,255,255,.09); border: 1px solid rgba(255,255,255,.16);
  color: #fff; font-size: 15px; font-weight: 700; padding: 9px 18px 9px 14px; border-radius: 24px; }}
.cdot {{ width: 12px; height: 12px; border-radius: 50%; }}
.cov-foot {{ position: absolute; right: 60px; bottom: 44px; display: flex; align-items: center; gap: 14px;
  color: #9FB0C7; font-size: 15px; font-weight: 600; }}
.cov-dot {{ color: {ORANGE}; }}

/* ---- Dark / hero ---- */
.dark {{ background: {INK}; color: #EAF0F8; }}
.hero-bgshape {{ position: absolute; width: 720px; height: 720px; right: -180px; top: -180px; border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, rgba(242,99,19,.28), transparent 60%); }}
.hero-wrap {{ position: relative; padding: 56px 66px; }}
.hero-h {{ font-size: 44px; font-weight: 800; color: #fff; margin: 10px 0 10px; letter-spacing: -.6px; }}
.hero-lead {{ font-size: 18px; color: #AEBED4; max-width: 900px; line-height: 1.5; margin: 0 0 30px; }}
.hero-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }}
.hero-card {{ background: linear-gradient(160deg, rgba(255,255,255,.07), rgba(255,255,255,.03)); border: 1px solid rgba(255,255,255,.10);
  border-radius: 18px; padding: 22px 22px 20px; }}
.hero-ic {{ width: 46px; height: 46px; border-radius: 12px; display: flex; align-items: center; justify-content: center; margin-bottom: 14px; }}
.hero-val {{ font-size: 34px; font-weight: 800; color: #fff; line-height: 1; letter-spacing: -.5px; }}
.hero-lab {{ font-size: 14.5px; color: #A9B8CE; margin-top: 8px; line-height: 1.35; }}
.dark .footer {{ border-color: rgba(255,255,255,.12); color: #9FB0C7; }}
.dark .footer .fr {{ color: #fff; }}

/* ---- Overview ---- */
.ov-wrap {{ display: flex; gap: 44px; height: 486px; }}
.ov-map-box {{ border-radius: 16px; overflow: hidden; box-shadow: 0 18px 44px rgba(15,27,52,.16); border: 1px solid {LINE}; height: 100%; }}
.ov-map {{ height: 100%; display: block; }}
.ov-side {{ flex: 1; padding-top: 6px; }}
.ov-lead {{ font-size: 21px; line-height: 1.5; color: #2A3542; font-weight: 600; margin: 0 0 26px; }}
.leg {{ display: flex; align-items: center; gap: 15px; margin-bottom: 18px; }}
.leg-ic {{ width: 38px; height: 38px; border-radius: 11px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.leg b {{ font-size: 18px; color: {INK}; font-family: 'Manrope'; font-weight: 800; display: block; }}
.leg span {{ font-size: 14px; color: {MUTED}; }}
.ov-note {{ display: flex; gap: 11px; align-items: flex-start; font-size: 15.5px; color: {MUTED}; line-height: 1.5; margin-top: 26px;
  background: {PAPER}; border-radius: 12px; padding: 14px 16px; }}

/* ---- Divider ---- */
.divider {{ display: flex; }}
.div-left {{ width: 60%; height: 100%; padding: 76px 62px; position: relative; overflow: hidden; }}
.div-glow {{ position: absolute; width: 360px; height: 360px; border-radius: 50%; left: -120px; bottom: -140px; opacity: .28; filter: blur(20px); }}
.div-num-wrap {{ display: flex; align-items: center; gap: 22px; }}
.div-ic {{ width: 74px; height: 74px; border-radius: 20px; display: flex; align-items: center; justify-content: center; }}
.div-num {{ font-size: 118px; font-weight: 800; line-height: 1; color: rgba(255,255,255,.14); font-family: 'Manrope'; }}
.div-cat {{ font-size: 19px; font-weight: 800; letter-spacing: 3px; margin-top: 22px; }}
.div-title {{ color: #fff; font-size: 50px; font-weight: 800; margin: 10px 0 0; line-height: 1.05; letter-spacing: -.5px; }}
.div-sub {{ color: #AEBED4; font-size: 21px; margin-top: 16px; }}
.div-list {{ list-style: none; margin: 30px 0 0; padding: 0; }}
.div-list li {{ display: flex; align-items: center; gap: 12px; color: #DCE5F1; font-size: 17px; font-weight: 600; margin-bottom: 13px; }}
.div-list li svg {{ opacity: .8; }}
.div-right {{ width: 40%; height: 100%; position: relative; overflow: hidden; }}
.div-right img {{ width: 100%; height: 100%; object-fit: cover; }}
.div-right-scrim {{ position: absolute; inset: 0; }}

/* ---- Zone profile ---- */
.z-band {{ position: absolute; top: 0; left: 0; right: 0; height: 7px; }}
.zoneprofile .zp2 {{ padding: 34px 58px 30px; height: 100%; }}
.zp2-top {{ display: flex; gap: 18px; align-items: flex-start; margin-bottom: 16px; }}
.zp2-badge-ic {{ width: 54px; height: 54px; border-radius: 15px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 8px 20px rgba(15,27,52,.16); }}
.zp2-head {{ display: flex; align-items: center; gap: 14px; flex-wrap: wrap; margin-top: 3px; }}
.zp-name {{ font-size: 33px; font-weight: 800; color: {INK}; margin: 0; line-height: 1.05; letter-spacing: -.5px; }}
.zp2-type {{ color: #fff; font-size: 12.5px; font-weight: 800; letter-spacing: .3px; padding: 5px 14px; border-radius: 20px; }}
.zp-loc {{ display: flex; align-items: center; gap: 6px; font-size: 15px; color: {MUTED}; margin-top: 5px; }}
.zp2-about {{ font-size: 17px; line-height: 1.5; color: #2A3542; margin: 0 0 18px; max-width: 1220px;
  padding-bottom: 18px; border-bottom: 1px solid {LINE}; }}
.zp2-cols {{ display: flex; gap: 48px; margin-bottom: 18px; }}
.zp2-col {{ flex: 1.35; }}
.zp2-col-narrow {{ flex: 1; max-width: 400px; }}
.zp2-h {{ font-size: 13px; font-weight: 800; letter-spacing: 1.2px; margin-bottom: 13px; padding-bottom: 8px; border-bottom: 2px solid; }}
.zp2-res {{ list-style: none; margin: 0; padding: 0; }}
.zp2-res li {{ font-size: 15px; line-height: 1.4; color: #29333F; margin-bottom: 10px; }}
.zp2-res li b {{ font-weight: 800; }}
.zp2-sph {{ list-style: none; margin: 0; padding: 0; }}
.zp2-sph li {{ font-size: 15px; line-height: 1.35; color: #29333F; margin-bottom: 10px; display: flex; align-items: flex-start; gap: 9px; }}
.sph-ic {{ margin-top: 1px; flex-shrink: 0; }}
.ctx-title {{ font-size: 13px; font-weight: 800; letter-spacing: 1.2px; margin: 2px 0 11px; }}
.ctx-row {{ display: flex; gap: 16px; }}
.ctx-card {{ flex: 1; background: {PAPER}; border-left: 4px solid; border-radius: 10px; padding: 12px 15px; font-size: 14px; line-height: 1.4; color: #29333F; }}

/* ---- Zone residents ---- */
.zone .zg {{ display: flex; height: 100%; padding: 30px 0 0; }}
.zg-left {{ width: 484px; padding: 18px 0 18px 46px; }}
.map-frame {{ border-radius: 16px; overflow: hidden; box-shadow: 0 14px 34px rgba(15,27,52,.18); border: 1px solid {LINE}; }}
.z-map {{ width: 100%; height: 486px; object-fit: cover; display: block; }}
.z-mapcap {{ display: flex; align-items: center; justify-content: center; gap: 6px; font-size: 12.5px; color: {MUTED}; margin-top: 11px; }}
.zg-right {{ flex: 1; padding: 12px 54px 18px 42px; }}
.z-name {{ font-size: 32px; font-weight: 800; color: {INK}; margin: 5px 0 13px; line-height: 1.06; letter-spacing: -.5px; }}
.addr-card {{ display: flex; gap: 13px; align-items: center; background: {PAPER}; border: 1.5px solid; border-radius: 13px; padding: 12px 15px; }}
.pin-badge {{ width: 34px; height: 34px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.addr-card b {{ font-size: 17px; color: {INK}; display: block; font-family: 'Manrope'; font-weight: 800; }}
.z-city {{ font-size: 13.5px; color: {MUTED}; display: block; margin-top: 2px; }}
.maps-row {{ display: flex; gap: 16px; align-items: center; margin: 15px 0 16px; }}
.qr {{ width: 92px; height: 92px; border: 1px solid {LINE}; border-radius: 10px; padding: 4px; background: #fff; box-shadow: 0 6px 16px rgba(15,27,52,.10); }}
.maps-title {{ font-size: 16.5px; font-weight: 800; color: {INK}; font-family: 'Manrope'; }}
.maps-sub {{ font-size: 12.5px; color: {MUTED}; margin: 3px 0 7px; line-height: 1.35; }}
.maps-link {{ font-size: 14.5px; font-weight: 800; text-decoration: none; display: inline-flex; align-items: center; gap: 5px; }}
.z-restitle {{ font-size: 13px; font-weight: 800; letter-spacing: 1.6px; margin-bottom: 9px; }}
.residents {{ display: flex; flex-direction: column; gap: 8px; }}
.resident {{ border-left: 3px solid {LINE}; padding-left: 12px; }}
.r-head {{ display: flex; align-items: center; gap: 10px; }}
.r-name {{ font-size: 16px; font-weight: 800; font-family: 'Manrope'; }}
.r-tag {{ font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 20px; }}
.r-text {{ font-size: 13.3px; color: #2A3542; line-height: 1.38; margin-top: 3px; }}
.r-addr {{ display: flex; align-items: center; gap: 5px; font-size: 11.5px; color: {MUTED}; margin-top: 3px; }}
.z-facts {{ display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }}
.fact {{ font-size: 12.5px; font-weight: 700; color: {INK}; background: {PAPER}; border: 1px solid {LINE}; border-radius: 20px; padding: 5px 13px; font-family: 'Manrope'; }}

/* ---- Charts ---- */
.charts-row {{ display: flex; gap: 22px; }}
.chart-card {{ flex: 1; background: #fff; border: 1px solid {LINE}; border-radius: 16px; padding: 16px 16px 14px; box-shadow: 0 12px 30px rgba(15,27,52,.08); }}
.chart-t {{ font-size: 16px; font-weight: 800; color: {INK}; font-family: 'Manrope'; margin-bottom: 6px; }}
.chart-card img {{ width: 100%; border-radius: 8px; }}
.chart-take {{ font-size: 13px; color: {MUTED}; line-height: 1.4; margin-top: 8px; }}
.charts-note {{ display: flex; align-items: center; gap: 11px; font-size: 15px; color: #2A3542; margin-top: 22px; background: {PAPER}; border-radius: 12px; padding: 14px 18px; }}

/* ---- Challenges ---- */
.ch-lead {{ font-size: 18px; color: #2A3542; line-height: 1.5; max-width: 1060px; margin: 0 0 24px; }}
.ch-grid {{ display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }}
.ch-card {{ background: #fff; border: 1px solid {LINE}; border-radius: 16px; padding: 20px 22px; box-shadow: 0 10px 26px rgba(15,27,52,.07); }}
.ch-ic {{ width: 50px; height: 50px; border-radius: 13px; display: flex; align-items: center; justify-content: center; margin-bottom: 13px; }}
.ch-t {{ font-size: 20px; font-weight: 800; color: {INK}; font-family: 'Manrope'; margin-bottom: 7px; }}
.ch-x {{ font-size: 15px; color: #3A4656; line-height: 1.48; }}

/* ---- Compare ---- */
.ctable {{ width: 100%; border-collapse: collapse; font-size: 18px; }}
.ctable th {{ text-align: left; color: {MUTED}; font-size: 13px; letter-spacing: 1.4px; font-weight: 800; padding: 0 16px 12px; border-bottom: 2px solid {LINE}; }}
.ctable td {{ padding: 14px 16px; border-bottom: 1px solid {LINE}; color: #2A3542; vertical-align: middle; }}
.ctable td:first-child {{ font-weight: 800; color: {INK}; white-space: nowrap; font-family: 'Manrope'; font-size: 17px; }}
.ctable td b {{ color: {INK2}; font-weight: 700; }}
.ctable .why {{ color: {MUTED}; font-size: 16px; }}
.cbar {{ display: inline-block; width: 6px; height: 22px; border-radius: 3px; margin-right: 13px; vertical-align: -5px; }}

/* ---- Closing ---- */
.closing {{ background: {INK}; }}
.close-glow {{ position: absolute; width: 640px; height: 640px; right: -160px; bottom: -220px; border-radius: 50%;
  background: radial-gradient(circle at 40% 40%, rgba(242,99,19,.26), transparent 62%); }}
.close {{ position: relative; padding: 92px 84px; }}
.close-title {{ font-size: 52px; font-weight: 800; color: #fff; margin: 16px 0 20px; line-height: 1.06; letter-spacing: -1px; }}
.close-text {{ font-size: 22px; color: #B9C7DB; line-height: 1.5; max-width: 920px; }}
.close-cols {{ display: flex; gap: 84px; margin: 46px 0; }}
.cc-big {{ font-size: 60px; font-weight: 800; line-height: 1; }}
.cc-lab {{ font-size: 16px; color: #9FB0C7; margin-top: 8px; }}
.close-src {{ position: absolute; bottom: 40px; left: 84px; right: 84px; font-size: 12px; color: #7E8CA3; line-height: 1.5;
  border-top: 1px solid rgba(255,255,255,.12); padding-top: 14px; }}
'''

DOC = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{"".join(SLIDES)}</body></html>'
open("deck.html", "w", encoding="utf-8").write(DOC)
print("wrote deck.html with", len(SLIDES), "slides (TOTAL const:", TOTAL, ")")

# ================= render =================
from playwright.sync_api import sync_playwright
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--no-sandbox"])
    pg = b.new_page()
    pg.goto("file://" + os.path.join(HERE, "deck.html"), wait_until="load")
    pg.wait_for_timeout(700)
    pg.pdf(path="presentation.pdf", prefer_css_page_size=True, print_background=True)
    b.close()
print("wrote presentation.pdf", os.path.getsize("presentation.pdf"), "bytes")
