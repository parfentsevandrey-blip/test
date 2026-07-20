# -*- coding: utf-8 -*-
"""Build a high-impact 16:9 PDF presentation (Playwright + Chromium)."""
import os, html, base64
from urllib.parse import quote
import qrcode
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers.pil import RoundedModuleDrawer
from qrcode.image.styles.colormasks import SolidFillColorMask
import deck_data as D
import deck_extra as E

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
os.makedirs("assets/qr", exist_ok=True)

# ---------- palette ----------
BG = "#0A1226"; BG2 = "#0E1A33"; INK = "#0F1B34"; INK2 = "#1B2B4C"
ORANGE = "#E85D16"; BLUE = "#2563EB"; GREEN = "#0B9E6C"; GOLD = "#F0A93B"; VIOLET = "#7C5CFF"
MUTED = "#5E6980"; LINE = "#E7EBF1"; PAPER = "#F5F8FC"
GRADS = {"chips": ("#FF6A2B", "#FFB23D"), "data": ("#2E7CF6", "#4FD8FF"), "ai": ("#10B981", "#5EEAD4")}
CATCOL = {"chips": ORANGE, "data": BLUE, "ai": GREEN}
CATLABEL = {"chips": "ЧИПЫ", "data": "ДАТА-ЦЕНТРЫ", "ai": "ИИ И НАУКА"}
CATICON = {"chips": "chip", "data": "cloud", "ai": "atom"}
GOLDGRAD = "linear-gradient(100deg,#FF7A2B,#F5B843)"
HB = {"chips": "#FF9A3D", "data": "#5CC0FF", "ai": "#34D6A0"}  # bright solid stat colors (dark cards)


def grad(cat, deg=135):
    a, b = GRADS[cat]; return f"linear-gradient({deg}deg,{a},{b})"


# ---------- fonts ----------
def _b64(p): return base64.b64encode(open(p, "rb").read()).decode()


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


# ---------- noise texture ----------
NOISE = ("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'>"
         "<filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/>"
         "<feColorMatrix type='saturate' values='0'/></filter><rect width='100%25' height='100%25' filter='url(%23n)' opacity='0.5'/></svg>")

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
    "trend": '<path d="M3 17l6-6 4 4 8-8M15 7h6v6"/>',
}


def icon(name, color=INK, size=24, sw=1.9):
    return (f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" fill="none" stroke="{color}" '
            f'stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{ICONS[name]}</svg>')


# ---------- helpers ----------
def data_uri(path):
    ext = "png" if path.lower().endswith("png") else "jpeg"
    return f"data:image/{ext};base64," + base64.b64encode(open(path, "rb").read()).decode()


def esc(s): return html.escape(str(s))
def gmaps_url(q): return "https://www.google.com/maps/search/?api=1&query=" + quote(q)
def _hex(h): h = h.lstrip("#"); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def make_qr(url, key, color):
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=1)
    q.add_data(url); q.make(fit=True)
    pic = q.make_image(image_factory=StyledPilImage, module_drawer=RoundedModuleDrawer(),
                       color_mask=SolidFillColorMask(front_color=_hex(color), back_color=(255, 255, 255)))
    path = f"assets/qr/qr_{key}.png"; pic.save(path); return path


SLIDES = []
def _pos(): return len(SLIDES) + 1
def slide(cls, inner): SLIDES.append(f'<section class="slide {cls}">{inner}</section>')


def footer(n, light=False):
    cls = "footer footer-light" if light else "footer"
    return (f'<div class="{cls}"><span class="fl">Технологические промзоны Нидерландов</span>'
            f'<span class="fr">{n:02d}<span class="ft">/ {TOTAL:02d}</span></span></div>')


def meshbg():
    return '<div class="mesh"></div><div class="grain"></div>'


# ================= data for new slides =================
HERO = [
    ("≈100%", "мирового рынка EUV-литографии — у ASML", "chip", "chips"),
    ("€32,7 млрд", "выручка ASML за 2025 год", "euro", "chips"),
    ("$675 млрд", "капитализация ASML — №1 в Европе", "spark", "chips"),
    ("15 Тбит/с", "пиковый трафик узла AMS-IX", "cloud", "data"),
    ("~852 МВт", "мощность дата-центров Амстердама", "bolt", "data"),
    ("~60 000", "рабочих мест в чип-секторе", "globe", "ai"),
]
CHALLENGES = [
    ("bolt", "chips", "Перегрузка электросетей", "К концу 2025 года около 14 000 компаний стояли в очереди на подключение к электричеству. Операторы вложат до €8 млрд в год, чтобы разгрузить сети."),
    ("shield", "data", "Мораторий на большие ЦОД", "С 2024 года новые гиперскейл-дата-центры (>70 МВт или >10 га) разрешено строить лишь в двух зонах — в Эмсхавене и районе Мидденмера."),
    ("drop", "ai", "Энергия и вода", "Кампус Microsoft в Мидденмере потребляет ~1% всего электричества страны; спор о расходе питьевой воды на охлаждение стал публичным."),
    ("euro", "chips", "Поддержка государства", "«Проект Бетховен»: €2,5 млрд на жильё, дороги, сети и образование в регионе Эйндховена, чтобы удержать ASML и её экосистему."),
]
REVENUE = [("2022", 21.2), ("2023", 27.6), ("2024", 28.3), ("2025", 32.7)]
INVEST = [("Microsoft · Мидденмер", 2.0, "data"), ("Google · Эмсхавен", 1.1, "data"), ("Google · Мидденмер", 0.5, "data")]


# ================= slides =================
def cover_slide():
    bg = data_uri("assets/cover.jpg")
    chips = "".join(
        f'<span class="ctag"><span class="cdot" style="background:{grad(c)}"></span>{lab}</span>'
        for c, lab in [("chips", "Чипы"), ("data", "Дата-центры"), ("ai", "ИИ и наука")])
    inner = f'''
      <div class="cov-bg" style="background-image:url('{bg}')"></div>
      <div class="cov-scrim"></div>
      <div class="mesh mesh-cover"></div>
      <div class="grain"></div>
      <div class="cov-grid"></div>
      <div class="cov-streak"></div>
      <div class="cov-body">
        <div class="cov-kick"><span class="kbar"></span>{esc(D.META["kicker"])}</div>
        <h1 class="cov-title">Технологические<br>промзоны <span class="grad-text">Нидерландов</span></h1>
        <div class="cov-sub">{esc(D.META["subtitle"])}</div>
        <div class="cov-tags">{chips}</div>
      </div>
      <div class="cov-foot"><span>Аналитическая презентация</span><span class="cov-dot">●</span><span>{esc(D.META["date"])}</span></div>'''
    slide("cover dark", inner)


def hero_slide():
    cells = ""
    stats = E.HERO_STATS if getattr(E, "HERO_STATS", None) else HERO
    for val, lab, ic, cat in stats:
        a, b = GRADS[cat]
        cells += f'''
          <div class="glass hero-card">
            <div class="hero-ic" style="background:{grad(cat)};box-shadow:0 10px 26px {a}55">{icon(ic, "#fff", 26)}</div>
            <div class="hero-val" style="color:{HB[cat]}">{val}</div>
            <div class="hero-lab">{lab}</div>
          </div>'''
    inner = f'''
      {meshbg()}
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
      <div class="corner-blob" style="background:radial-gradient(circle,{GRADS['chips'][0]}22,transparent 70%)"></div>
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
    s = D.SECTIONS[cat]; img = data_uri(s["img"]); a, b = GRADS[cat]
    lst = "".join(f'<li><span class="dl-dot" style="background:{grad(cat)}"></span>{esc(t)}</li>' for t in zone_titles)
    inner = f'''
      <img class="d2-img" src="{img}"/>
      <div class="d2-scrim"></div>
      <div class="d2-glow" style="background:radial-gradient(circle,{a}55,transparent 62%)"></div>
      <div class="grain"></div>
      <div class="d2-ghost" style="background:{grad(cat)}">{s["num"]}</div>
      <div class="d2-body">
        <div class="d2-ic" style="background:{grad(cat)};box-shadow:0 14px 34px {a}66">{icon(CATICON[cat], "#fff", 36, 2.0)}</div>
        <div class="d2-cat" style="color:{b}">{CATLABEL[cat]}</div>
        <h2 class="d2-title">{esc(s["title"])}</h2>
        <div class="d2-sub">{esc(s["sub"])}</div>
        <ul class="d2-list">{lst}</ul>
      </div>'''
    slide("divider2 dark", inner)


def zone_profile_slide(z):
    p = D.PROFILES[z["key"]]; cat = z["cat"]; col = CATCOL[cat]; a, b = GRADS[cat]
    res = "".join(f'<li><b style="color:{col}">{esc(n)}</b> — {esc(w)}</li>' for n, w in p["residents"])
    sph = "".join(f'<li><span class="sph-ic">{icon("spark", col, 15, 2.2)}</span>{esc(s)}</li>' for s in p["spheres"])
    ctx = "".join(f'<div class="ctx-card" style="border-color:{col}">{esc(c)}</div>' for c in p.get("context", []))
    inner = f'''
      <div class="z-band" style="background:{grad(cat)}"></div>
      <div class="corner-blob" style="background:radial-gradient(circle,{a}18,transparent 70%)"></div>
      <div class="zp2">
        <div class="zp2-top">
          <div class="zp2-badge-ic" style="background:{grad(cat)};box-shadow:0 10px 24px {a}55">{icon(CATICON[cat], "#fff", 24, 2.0)}</div>
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
            <div class="zp2-h" style="color:{col}"><span class="hbar-line" style="background:{grad(cat)}"></span>РЕЗИДЕНТЫ И ЧЕМ ЗАНИМАЮТСЯ</div>
            <ul class="zp2-res">{res}</ul>
          </div>
          <div class="zp2-col zp2-col-narrow">
            <div class="zp2-h" style="color:{col}"><span class="hbar-line" style="background:{grad(cat)}"></span>СФЕРЫ ДЕЯТЕЛЬНОСТИ</div>
            <ul class="zp2-sph">{sph}</ul>
          </div>
        </div>
        <div class="ctx-title" style="color:{col}">КОНТЕКСТ И ЗНАЧЕНИЕ</div>
        <div class="ctx-row">{ctx}</div>
      </div>
      {footer(_pos())}'''
    slide("content zoneprofile", inner)


def zone_slide(z):
    cat = z["cat"]; col = CATCOL[cat]; a, b = GRADS[cat]
    m = data_uri(z["img"]); url = gmaps_url(z.get("maps_query") or z["address"]); qr = data_uri(make_qr(url, z["key"], INK))
    residents = ""
    for r in z["residents"]:
        addr = f'<div class="r-addr">{icon("pin", MUTED, 13)} {esc(r["addr"])}</div>' if r.get("addr") else ""
        residents += f'''<div class="resident" style="border-color:{col}">
            <div class="r-head"><span class="r-name" style="color:{col}">{esc(r["name"])}</span>
            <span class="r-tag" style="color:{col};background:{col}18">{esc(r["tag"])}</span></div>
            <div class="r-text">{esc(r["text"])}</div>{addr}</div>'''
    facts = "".join(f'<span class="fact">{esc(f)}</span>' for f in z["facts"])
    nug = getattr(E, "ZONE_NUGGETS", {}).get(z["key"])
    nug_html = (f'<div class="z-nugget" style="border-color:{col};background:{col}10">'
                f'<span style="color:{col}">★ Интересный факт:</span> {esc(nug)}</div>') if nug else ''
    inner = f'''
      <div class="z-band" style="background:{grad(cat)}"></div>
      <div class="zg">
        <div class="zg-left">
          <div class="map-frame"><img class="z-map" src="{m}"/><div class="map-ring" style="border-color:{a}"></div></div>
          <div class="z-mapcap">{icon("globe", MUTED, 14)} {esc(z.get("map_caption", "Карта Google"))}</div>
        </div>
        <div class="zg-right">
          <div class="eyebrow" style="color:{col}">{icon(CATICON[cat], col, 16, 2.2)} {CATLABEL[cat]}</div>
          <h2 class="z-name">{esc(z["name"])}</h2>
          <div class="addr-card">
            <div class="pin-badge" style="background:{grad(cat)};box-shadow:0 8px 18px {a}55">{icon("pin", "#fff", 18)}</div>
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
          <div class="z-restitle" style="color:{col}"><span class="hbar-line" style="background:{grad(cat)}"></span>РЕЗИДЕНТЫ</div>
          <div class="residents">{residents}</div>
          <div class="z-facts">{facts}</div>
          {nug_html}
        </div>
      </div>
      {footer(_pos())}'''
    slide("content zone", inner)


def charts_slide():
    mx = max(v for _, v in REVENUE)
    bars = "".join(
        f'<div class="vbar"><div class="vbar-val">€{v:.1f}</div>'
        f'<div class="vbar-track"><div class="vbar-fill" style="height:{int(v/mx*100)}%;background:{grad("chips")}"></div></div>'
        f'<div class="vbar-x">{yr}</div></div>' for yr, v in REVENUE)
    inv_mx = max(v for _, v, _ in INVEST)
    hbars = "".join(
        f'<div class="ib"><div class="ib-top"><span>{esc(name)}</span><b>€{v:.1f} млрд</b></div>'
        f'<div class="ib-track"><div class="ib-fill" style="width:{int(v/inv_mx*100)}%;background:{grad(cat)}"></div></div></div>'
        for name, v, cat in INVEST)
    ring = f'''conic-gradient(from -90deg, #FF6A2B 0turn, #FFB23D 0.83turn, rgba(255,255,255,.10) 0.83turn 1turn)'''
    inner = f'''
      {meshbg()}
      <div class="pad">
        <div class="eyebrow eyebrow-light">ЭКОНОМИКА</div>
        <h2 class="stitle stitle-light">Рост, доля рынка и инвестиции</h2>
        <div class="charts3">
          <div class="glass ch-panel">
            <div class="ch-h">Выручка ASML, млрд €</div>
            <div class="vbars">{bars}</div>
            <div class="ch-sub">Рекордный рост на волне спроса на чипы и ИИ.</div>
          </div>
          <div class="glass ch-panel ch-panel-ring">
            <div class="ch-h">Доля рынка литографии</div>
            <div class="ring" style="background:{ring}"><div class="ring-hole"></div>
              <div class="ring-c"><div class="ring-big grad-text">~83%</div><div class="ring-l">всех литографов</div></div></div>
            <div class="ch-sub">И почти <b style="color:#FFB23D">100%</b> рынка EUV — только ASML.</div>
          </div>
          <div class="glass ch-panel">
            <div class="ch-h">Инвестиции в дата-центры</div>
            <div class="ibars">{hbars}</div>
            <div class="ch-sub">Гиперскейлеры вложили в 3 площадки более €3,5 млрд.</div>
          </div>
        </div>
        <div class="charts-note-d">{icon("trend", "#FFB23D", 18)}<span>Полупроводниковый сектор: 300+ компаний, ~9% мирового рынка, экспорт оборудования ~€25 млрд в год.</span></div>
      </div>
      {footer(_pos())}'''
    slide("dark charts", inner)


def challenges_slide():
    cards = ""
    for ic, cat, t, txt in CHALLENGES:
        col = CATCOL[cat]; a, b = GRADS[cat]
        cards += f'''<div class="ch-card">
          <div class="ch-ic" style="background:{grad(cat)};box-shadow:0 10px 22px {a}44">{icon(ic, "#fff", 25)}</div>
          <div class="ch-t">{esc(t)}</div><div class="ch-x">{esc(txt)}</div></div>'''
    inner = f'''
      <div class="corner-blob" style="background:radial-gradient(circle,{GRADS['chips'][0]}18,transparent 70%)"></div>
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
        rows += (f'<tr><td><span class="cbar" style="background:{grad(cat)}"></span>{esc(zone)}</td>'
                 f'<td><b>{esc(anchor)}</b></td><td class="why">{esc(why)}</td></tr>')
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ИТОГ</div>
        <h2 class="stitle">Сравнение ключевых зон</h2>
        <table class="ctable"><thead><tr><th>Зона</th><th>Якорные резиденты</th><th>Чем важна</th></tr></thead>
          <tbody>{rows}</tbody></table>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def closing_slide():
    cols = "".join(
        f'<div class="cc"><div class="cc-big grad-text">{v}</div><div class="cc-lab">{l}</div></div>'
        for v, l in [("7", "ключевых зон"), ("3", "направления"), ("№1", "ASML — в Европе")])
    inner = f'''
      {meshbg()}
      <div class="close">
        <div class="eyebrow eyebrow-light">СПАСИБО ЗА ВНИМАНИЕ</div>
        <h2 class="close-title">Маленькая страна — <span class="grad-text">огромное влияние</span></h2>
        <p class="close-text">Нидерланды объединяют производство чипов, «облачные» дата-центры и науку об искусственном интеллекте в единую технологическую экосистему мирового значения.</p>
        <div class="close-cols">{cols}</div>
        <div class="close-src">Источники: годовые отчёты ASML, NXP; AMS-IX; datacenters.google; news.microsoft.com; qutech.nl; brainporteindhoven.com; rijksoverheid.nl &nbsp;·&nbsp; Карты: © OpenStreetMap · CARTO</div>
      </div>'''
    slide("dark closing", inner)


# ================= enrichment slides =================
def didyouknow_slide():
    cards = ""
    for title, text, ic, cat in E.DID_YOU_KNOW:
        a, b = GRADS[cat]
        cards += f'''<div class="dyk-card">
          <div class="dyk-ic" style="background:{grad(cat)};box-shadow:0 10px 24px {a}55">{icon(ic, "#fff", 26)}</div>
          <div class="dyk-t">{esc(title)}</div><div class="dyk-x">{esc(text)}</div></div>'''
    inner = f'''
      {meshbg()}
      <div class="pad">
        <div class="eyebrow eyebrow-light">САМОЕ ИНТЕРЕСНОЕ</div>
        <h2 class="stitle stitle-light">Знаете ли вы?</h2>
        <div class="dyk-grid">{cards}</div>
      </div>
      {footer(_pos())}'''
    slide("dark", inner)


def supplychain_slide():
    n = len(E.SUPPLY_CHAIN)
    steps = ""
    for i, (stage, players, dutch, ic) in enumerate(E.SUPPLY_CHAIN):
        cat = "chips" if i == 0 else ("data" if i == 1 else ("ai" if i == 2 else "chips"))
        a, b = GRADS[cat]
        hl = ' sc-step-hl' if i == 0 else ''
        steps += f'''<div class="sc-step{hl}">
            <div class="sc-ic" style="background:{grad(cat)};box-shadow:0 10px 22px {a}44">{icon(ic, "#fff", 24)}</div>
            <div class="sc-stage">{esc(stage)}</div>
            <div class="sc-players">{esc(players)}</div>
            <div class="sc-dutch">{esc(dutch)}</div>
          </div>'''
        if i < n - 1:
            steps += f'<div class="sc-arrow">{icon("arrow", "#B9C2D0", 26, 2.2)}</div>'
    inner = f'''
      <div class="corner-blob" style="background:radial-gradient(circle,{GRADS['chips'][0]}18,transparent 70%)"></div>
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">МЕСТО В МИРОВОЙ ЦЕПОЧКЕ</div>
        <h2 class="stitle">Без одного звена не работает всё</h2>
        <p class="sc-lead">Каждый передовой чип в мире проходит через оборудование из Нидерландов. Вот как устроена глобальная цепочка — и где в ней Нидерланды.</p>
        <div class="sc-flow">{steps}</div>
        <div class="sc-note">{icon("shield", ORANGE, 18)}<span>ASML — «узкое место»: единственный поставщик EUV. Поэтому вокруг него и разворачивается технологическая геополитика.</span></div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def timeline_slide():
    ev = E.TIMELINE
    nodes = ""
    for i, (yr, title, detail, cat) in enumerate(ev):
        a, b = GRADS[cat]
        up = i % 2 == 0
        card = f'<div class="tl-card"><div class="tl-title">{esc(title)}</div><div class="tl-detail">{esc(detail)}</div></div>'
        nodes += f'''<div class="tl-node {'tl-up' if up else 'tl-down'}">
            {card if up else ''}
            <div class="tl-mid"><div class="tl-year" style="color:{CATCOL[cat]}">{esc(yr)}</div><div class="tl-dot" style="background:{grad(cat)}"></div></div>
            {card if not up else ''}
          </div>'''
    inner = f'''
      <div class="corner-blob" style="background:radial-gradient(circle,{GRADS['data'][0]}16,transparent 70%)"></div>
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ПУТЬ К ВЕРШИНЕ</div>
        <h2 class="stitle">Как складывалась экосистема</h2>
        <div class="tl-wrap"><div class="tl-line"></div><div class="tl-row">{nodes}</div></div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


def geopolitics_slide():
    g = E.GEOPOLITICS
    cards = ""
    cats = ["chips", "data", "ai", "chips"]
    for i, (t, txt, ic) in enumerate(g["points"]):
        cat = cats[i % len(cats)]; a, b = GRADS[cat]
        cards += f'''<div class="glass gp-card">
          <div class="gp-ic" style="background:{grad(cat)};box-shadow:0 10px 22px {a}44">{icon(ic, "#fff", 24)}</div>
          <div class="gp-t">{esc(t)}</div><div class="gp-x">{esc(txt)}</div></div>'''
    inner = f'''
      {meshbg()}
      <div class="pad">
        <div class="eyebrow eyebrow-light">ГЕОПОЛИТИКА</div>
        <h2 class="stitle stitle-light">Чипы — это не только технологии</h2>
        <p class="gp-lead">{esc(g["lead"])}</p>
        <div class="gp-grid">{cards}</div>
      </div>
      {footer(_pos())}'''
    slide("dark", inner)


def outlook_slide():
    o = E.OUTLOOK
    cards = ""
    cats = ["chips", "ai", "data", "chips"]
    for i, (t, txt, ic) in enumerate(o["points"]):
        cat = cats[i % len(cats)]; a, b = GRADS[cat]
        cards += f'''<div class="ol-card">
          <div class="ol-ic" style="background:{grad(cat)};box-shadow:0 10px 22px {a}44">{icon(ic, "#fff", 25)}</div>
          <div class="ol-t">{esc(t)}</div><div class="ol-x">{esc(txt)}</div></div>'''
    inner = f'''
      <div class="corner-blob" style="background:radial-gradient(circle,{GRADS['ai'][0]}18,transparent 70%)"></div>
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ЧТО ДАЛЬШЕ · 2026–2030</div>
        <h2 class="stitle">Куда движется технологическое сердце Европы</h2>
        <p class="ol-lead">{esc(o["lead"])}</p>
        <div class="ol-grid">{cards}</div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


# ================= assemble =================
order = [("chips", ["asml", "htc", "nijmegen"]),
         ("data", ["amsterdam", "eemshaven", "agriport"]),
         ("ai", ["delft"])]
zmap = {z["key"]: z for z in D.ZONES}
znames = {z["key"]: D.PROFILES[z["key"]]["zone_title"] for z in D.ZONES}
_zone_slides = sum(1 + 2 * len(ks) for _, ks in order)  # dividers + profile/residents pairs
TOTAL = 3 + _zone_slides + 1 + 2  # cover+hero+overview, +supplychain, +charts+compare

cover_slide(); hero_slide(); overview_slide()
for cat, keys in order:
    divider_slide(cat, [znames[k] for k in keys])
    for k in keys:
        zone_profile_slide(zmap[k]); zone_slide(zmap[k])
    if cat == "chips":
        supplychain_slide()
charts_slide(); compare_slide()

# ================= CSS =================
CSS = font_faces() + f'''
@page {{ size: 1280px 720px; margin: 0; }}
* {{ box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
html, body {{ margin: 0; padding: 0; background: #fff; }}
.slide {{ width: 1280px; height: 720px; position: relative; overflow: hidden; background: #fff;
  font-family: 'Inter', sans-serif; color: #23303F; page-break-after: always; }}
.slide:last-child {{ page-break-after: auto; }}
h1,h2,.stitle,.zp-name,.z-name,.hero-h,.d2-title,.close-title,.cc-big,.hero-val,.ring-big {{ font-family: 'Manrope', sans-serif; }}
.grad-text {{ background: {GOLDGRAD}; -webkit-background-clip: text; background-clip: text; color: transparent;
  -webkit-box-decoration-break: clone; box-decoration-break: clone; padding-right: .04em; }}
.pad {{ padding: 50px 66px; height: 100%; position: relative; z-index: 2; }}
.eyebrow {{ font-size: 14px; font-weight: 800; letter-spacing: 2.6px; display: flex; align-items: center; gap: 8px; }}
.eyebrow-light {{ color: #FFB23D; }}
.stitle {{ font-size: 39px; font-weight: 800; color: {INK}; margin: 10px 0 26px; letter-spacing: -.5px; position: relative; display: inline-block; }}
.stitle::after {{ content: ''; position: absolute; left: 2px; bottom: -9px; width: 66px; height: 4px; border-radius: 3px; background: {GOLDGRAD}; }}
.stitle-light {{ color: #fff; }}
.corner-blob {{ position: absolute; width: 520px; height: 520px; right: -160px; top: -190px; border-radius: 50%; z-index: 0; }}

/* mesh + grain (dark slides) */
.dark {{ background: {BG}; color: #EAF0F8; }}
.mesh {{ position: absolute; inset: 0; z-index: 0; background:
  radial-gradient(circle at 14% 22%, rgba(255,106,43,.20), transparent 40%),
  radial-gradient(circle at 88% 12%, rgba(46,124,246,.20), transparent 44%),
  radial-gradient(circle at 78% 92%, rgba(124,92,255,.18), transparent 46%),
  radial-gradient(circle at 30% 88%, rgba(16,185,129,.13), transparent 44%),
  linear-gradient(160deg, {BG}, {BG2}); }}
.grain {{ display: none; }}
.dark .footer, .dark .pad, .dark .hero-wrap, .dark .close {{ position: relative; z-index: 2; }}
.footer {{ position: absolute; z-index: 3; bottom: 20px; left: 66px; right: 66px; display: flex; justify-content: space-between;
  align-items: center; font-size: 12.5px; color: {MUTED}; border-top: 1px solid {LINE}; padding-top: 11px; }}
.footer .fr {{ font-weight: 800; color: {INK}; font-family: 'Manrope'; }}
.footer .ft {{ color: {MUTED}; font-weight: 600; margin-left: 3px; }}
.dark .footer {{ border-color: rgba(255,255,255,.13); color: #9FB0C7; }}
.dark .footer .fr {{ color: #fff; }} .dark .footer .ft {{ color: #7E8CA3; }}

/* glass */
.glass {{ background: linear-gradient(158deg, rgba(255,255,255,.11), rgba(255,255,255,.035));
  border: 1px solid rgba(255,255,255,.14); border-radius: 20px;
  box-shadow: 0 22px 50px rgba(0,0,0,.34), inset 0 1px 0 rgba(255,255,255,.20); }}

/* ---- Cover ---- */
.cov-bg {{ position: absolute; inset: 0; background-size: cover; background-position: center; }}
.cov-scrim {{ position: absolute; inset: 0; background: linear-gradient(104deg, rgba(7,12,24,.96) 30%, rgba(7,12,24,.62) 58%, rgba(7,12,24,.14) 100%); }}
.mesh-cover {{ opacity: .8; mix-blend-mode: screen; mask-image: linear-gradient(104deg, #000 45%, transparent 85%); }}
.cov-grid {{ position: absolute; inset: 0; z-index: 1; opacity: .5;
  background-image: linear-gradient(rgba(255,255,255,.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 46px 46px; mask-image: linear-gradient(104deg, #000 28%, transparent 72%); }}
.cov-streak {{ position: absolute; z-index: 1; left: -10%; top: 32%; width: 120%; height: 3px; transform: rotate(-8deg);
  background: linear-gradient(90deg, transparent, rgba(255,178,61,.55), transparent); filter: blur(1px); }}
.cov-body {{ position: absolute; z-index: 2; left: 76px; top: 148px; width: 820px; color: #fff; }}
.cov-kick {{ display: flex; align-items: center; gap: 12px; font-size: 16px; letter-spacing: 4px; font-weight: 800; color: #FFB23D; margin-bottom: 22px; }}
.kbar {{ width: 34px; height: 4px; border-radius: 3px; background: linear-gradient(90deg,#FF6A2B,#FFB23D); }}
.cov-title {{ font-size: 68px; line-height: 1.02; font-weight: 800; margin: 0; letter-spacing: -1.6px; text-shadow: 0 6px 40px rgba(0,0,0,.4); }}
.cov-sub {{ font-size: 24px; color: #CBD5E6; margin-top: 22px; font-weight: 500; max-width: 660px; }}
.cov-tags {{ display: flex; gap: 12px; margin-top: 34px; }}
.ctag {{ display: flex; align-items: center; gap: 9px; background: rgba(255,255,255,.08); border: 1px solid rgba(255,255,255,.18);
  color: #fff; font-size: 15px; font-weight: 700; padding: 9px 18px 9px 14px; border-radius: 24px; box-shadow: inset 0 1px 0 rgba(255,255,255,.12); }}
.cdot {{ width: 12px; height: 12px; border-radius: 50%; }}
.cov-foot {{ position: absolute; z-index: 2; right: 60px; bottom: 44px; display: flex; align-items: center; gap: 14px; color: #9FB0C7; font-size: 15px; font-weight: 600; }}
.cov-dot {{ color: #FF6A2B; font-size: 10px; }}

/* ---- Hero ---- */
.hero-wrap {{ padding: 54px 66px; }}
.hero-h {{ font-size: 45px; font-weight: 800; color: #fff; margin: 10px 0 10px; letter-spacing: -.6px; }}
.hero-lead {{ font-size: 18px; color: #AEBED4; max-width: 900px; line-height: 1.5; margin: 0 0 30px; }}
.hero-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }}
.hero-card {{ padding: 22px 22px 20px; }}
.hero-ic {{ width: 46px; height: 46px; border-radius: 13px; display: flex; align-items: center; justify-content: center; margin-bottom: 14px; }}
.hero-val {{ font-size: 37px; font-weight: 800; line-height: 1; letter-spacing: -.5px; }}
.gv {{ -webkit-background-clip: text; background-clip: text; color: transparent; -webkit-box-decoration-break: clone; box-decoration-break: clone; }}
.hero-lab {{ font-size: 14.5px; color: #AABAD0; margin-top: 9px; line-height: 1.35; }}

/* ---- Overview ---- */
.ov-wrap {{ display: flex; gap: 44px; height: 480px; }}
.ov-map-box {{ border-radius: 18px; overflow: hidden; box-shadow: 0 22px 50px rgba(15,27,52,.20); border: 1px solid {LINE}; height: 100%; }}
.ov-map {{ height: 100%; display: block; }}
.ov-side {{ flex: 1; padding-top: 6px; }}
.ov-lead {{ font-size: 21px; line-height: 1.5; color: #2A3542; font-weight: 600; margin: 0 0 26px; }}
.leg {{ display: flex; align-items: center; gap: 15px; margin-bottom: 18px; }}
.leg-ic {{ width: 40px; height: 40px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; box-shadow: 0 8px 18px rgba(15,27,52,.14); }}
.leg b {{ font-size: 18px; color: {INK}; font-family: 'Manrope'; font-weight: 800; display: block; }}
.leg span {{ font-size: 14px; color: {MUTED}; }}
.ov-note {{ display: flex; gap: 11px; align-items: flex-start; font-size: 15.5px; color: {MUTED}; line-height: 1.5; margin-top: 26px; background: {PAPER}; border-radius: 12px; padding: 14px 16px; }}

/* ---- Divider (full-bleed) ---- */
.divider2 {{ background: {BG}; }}
.d2-img {{ position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 0; }}
.d2-scrim {{ position: absolute; inset: 0; z-index: 1; background: linear-gradient(100deg, {BG} 34%, rgba(10,18,38,.72) 60%, rgba(10,18,38,.30) 100%); }}
.d2-glow {{ position: absolute; z-index: 1; width: 620px; height: 620px; left: -160px; bottom: -220px; border-radius: 50%; filter: blur(10px); opacity: .7; }}
.d2-ghost {{ position: absolute; z-index: 1; right: 40px; top: -30px; font-size: 420px; font-weight: 800; font-family: 'Manrope';
  -webkit-background-clip: text; background-clip: text; color: transparent; opacity: .16; line-height: 1; }}
.d2-body {{ position: absolute; z-index: 2; left: 66px; top: 150px; width: 640px; }}
.d2-ic {{ width: 78px; height: 78px; border-radius: 22px; display: flex; align-items: center; justify-content: center; }}
.d2-cat {{ font-size: 19px; font-weight: 800; letter-spacing: 3px; margin-top: 26px; }}
.d2-title {{ color: #fff; font-size: 54px; font-weight: 800; margin: 12px 0 0; line-height: 1.04; letter-spacing: -1px; text-shadow: 0 6px 30px rgba(0,0,0,.4); }}
.d2-sub {{ color: #C2CFE2; font-size: 21px; margin-top: 16px; }}
.d2-list {{ list-style: none; margin: 30px 0 0; padding: 0; }}
.d2-list li {{ display: flex; align-items: center; gap: 13px; color: #E4EBF5; font-size: 17.5px; font-weight: 600; margin-bottom: 14px; }}
.dl-dot {{ width: 11px; height: 11px; border-radius: 50%; flex-shrink: 0; }}

/* ---- Zone profile ---- */
.z-band {{ position: absolute; top: 0; left: 0; right: 0; height: 7px; z-index: 3; }}
.zoneprofile .zp2 {{ padding: 34px 58px 30px; height: 100%; position: relative; z-index: 2; }}
.zp2-top {{ display: flex; gap: 18px; align-items: flex-start; margin-bottom: 16px; }}
.zp2-badge-ic {{ width: 54px; height: 54px; border-radius: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.zp2-head {{ display: flex; align-items: center; gap: 14px; flex-wrap: wrap; margin-top: 3px; }}
.zp-name {{ font-size: 33px; font-weight: 800; color: {INK}; margin: 0; line-height: 1.05; letter-spacing: -.5px; }}
.zp2-type {{ color: #fff; font-size: 12.5px; font-weight: 800; padding: 5px 14px; border-radius: 20px; }}
.zp-loc {{ display: flex; align-items: center; gap: 6px; font-size: 15px; color: {MUTED}; margin-top: 5px; }}
.zp2-about {{ font-size: 17px; line-height: 1.5; color: #2A3542; margin: 0 0 18px; max-width: 1220px; padding-bottom: 18px; border-bottom: 1px solid {LINE}; }}
.zp2-cols {{ display: flex; gap: 48px; margin-bottom: 18px; }}
.zp2-col {{ flex: 1.35; }} .zp2-col-narrow {{ flex: 1; max-width: 400px; }}
.zp2-h {{ font-size: 13px; font-weight: 800; letter-spacing: 1.2px; margin-bottom: 13px; padding-bottom: 8px; border-bottom: 1px solid {LINE}; display: flex; align-items: center; gap: 9px; }}
.hbar-line {{ width: 20px; height: 4px; border-radius: 3px; display: inline-block; }}
.zp2-res {{ list-style: none; margin: 0; padding: 0; }}
.zp2-res li {{ font-size: 15px; line-height: 1.4; color: #29333F; margin-bottom: 10px; }}
.zp2-res li b {{ font-weight: 800; }}
.zp2-sph {{ list-style: none; margin: 0; padding: 0; }}
.zp2-sph li {{ font-size: 15px; line-height: 1.35; color: #29333F; margin-bottom: 10px; display: flex; align-items: flex-start; gap: 9px; }}
.sph-ic {{ margin-top: 1px; flex-shrink: 0; }}
.ctx-title {{ font-size: 13px; font-weight: 800; letter-spacing: 1.2px; margin: 2px 0 11px; }}
.ctx-row {{ display: flex; gap: 16px; }}
.ctx-card {{ flex: 1; background: {PAPER}; border-left: 4px solid; border-radius: 12px; padding: 12px 15px; font-size: 14px; line-height: 1.4; color: #29333F; box-shadow: 0 6px 16px rgba(15,27,52,.05); }}

/* ---- Zone residents ---- */
.zone .zg {{ display: flex; height: 100%; padding: 24px 0 0; position: relative; z-index: 2; }}
.zg-left {{ width: 484px; padding: 12px 0 12px 46px; }}
.map-frame {{ position: relative; border-radius: 18px; overflow: hidden; box-shadow: 0 20px 44px rgba(15,27,52,.22); border: 1px solid {LINE}; }}
.map-ring {{ position: absolute; inset: 0; border-radius: 18px; border: 2px solid; opacity: .25; pointer-events: none; }}
.z-map {{ width: 100%; height: 470px; object-fit: cover; display: block; }}
.z-mapcap {{ display: flex; align-items: center; justify-content: center; gap: 6px; font-size: 12.5px; color: {MUTED}; margin-top: 10px; }}
.zg-right {{ flex: 1; padding: 6px 54px 14px 42px; }}
.z-name {{ font-size: 31px; font-weight: 800; color: {INK}; margin: 3px 0 10px; line-height: 1.05; letter-spacing: -.5px; }}
.addr-card {{ display: flex; gap: 13px; align-items: center; background: {PAPER}; border: 1px solid {LINE}; border-radius: 14px; padding: 10px 14px; box-shadow: 0 8px 20px rgba(15,27,52,.06); }}
.pin-badge {{ width: 36px; height: 36px; border-radius: 11px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.addr-card b {{ font-size: 17px; color: {INK}; display: block; font-family: 'Manrope'; font-weight: 800; }}
.z-city {{ font-size: 13.5px; color: {MUTED}; display: block; margin-top: 2px; }}
.maps-row {{ display: flex; gap: 15px; align-items: center; margin: 11px 0 11px; }}
.qr {{ width: 84px; height: 84px; border: 1px solid {LINE}; border-radius: 12px; padding: 4px; background: #fff; box-shadow: 0 8px 20px rgba(15,27,52,.12); }}
.maps-title {{ font-size: 16.5px; font-weight: 800; color: {INK}; font-family: 'Manrope'; }}
.maps-sub {{ font-size: 12.5px; color: {MUTED}; margin: 3px 0 7px; line-height: 1.35; }}
.maps-link {{ font-size: 14.5px; font-weight: 800; text-decoration: none; display: inline-flex; align-items: center; gap: 5px; }}
.z-restitle {{ font-size: 13px; font-weight: 800; letter-spacing: 1.6px; margin-bottom: 8px; display: flex; align-items: center; gap: 9px; }}
.residents {{ display: flex; flex-direction: column; gap: 6px; }}
.resident {{ border-left: 3px solid; padding-left: 12px; }}
.r-head {{ display: flex; align-items: center; gap: 10px; }}
.r-name {{ font-size: 16px; font-weight: 800; font-family: 'Manrope'; }}
.r-tag {{ font-size: 11px; font-weight: 700; padding: 2px 9px; border-radius: 20px; }}
.r-text {{ font-size: 13.2px; color: #2A3542; line-height: 1.33; margin-top: 2px; }}
.r-addr {{ display: flex; align-items: center; gap: 5px; font-size: 11.5px; color: {MUTED}; margin-top: 2px; }}
.z-facts {{ display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }}
.fact {{ font-size: 12.5px; font-weight: 700; color: {INK}; background: {PAPER}; border: 1px solid {LINE}; border-radius: 20px; padding: 5px 13px; font-family: 'Manrope'; }}

/* ---- Charts (dark, native) ---- */
.charts3 {{ display: flex; gap: 22px; margin-top: 8px; }}
.ch-panel {{ flex: 1; padding: 22px 24px 20px; color: #EAF0F8; display: flex; flex-direction: column; }}
.ch-h {{ font-size: 16.5px; font-weight: 800; color: #fff; font-family: 'Manrope'; margin-bottom: 18px; }}
.ch-sub {{ font-size: 13px; color: #AABAD0; line-height: 1.4; margin-top: auto; padding-top: 16px; }}
.vbars {{ display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; height: 220px; }}
.vbar {{ flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; }}
.vbar-val {{ font-size: 15px; font-weight: 800; color: #fff; font-family: 'Manrope'; margin-bottom: 6px; }}
.vbar-track {{ width: 100%; height: 100%; display: flex; align-items: flex-end; }}
.vbar-fill {{ width: 100%; border-radius: 8px 8px 3px 3px; box-shadow: 0 6px 18px rgba(255,106,43,.35); }}
.vbar-x {{ font-size: 13px; color: #9FB0C7; margin-top: 9px; }}
.ch-panel-ring {{ align-items: center; text-align: center; }}
.ring {{ width: 188px; height: 188px; border-radius: 50%; position: relative; margin: 6px auto 0; filter: drop-shadow(0 10px 24px rgba(255,106,43,.3)); }}
.ring-hole {{ position: absolute; inset: 20px; border-radius: 50%; background: #101c33; }}
.ring-c {{ position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }}
.ring-big {{ font-size: 44px; font-weight: 800; line-height: 1; }}
.ring-l {{ font-size: 13px; color: #AABAD0; margin-top: 4px; }}
.ibars {{ display: flex; flex-direction: column; gap: 20px; margin-top: 4px; }}
.ib-top {{ display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px; }}
.ib-top span {{ font-size: 13.5px; color: #C6D3E6; }}
.ib-top b {{ font-size: 16px; color: #fff; font-family: 'Manrope'; font-weight: 800; }}
.ib-track {{ height: 15px; border-radius: 8px; background: rgba(255,255,255,.09); overflow: hidden; }}
.ib-fill {{ height: 100%; border-radius: 8px; box-shadow: 0 4px 14px rgba(46,124,246,.4); }}
.charts-note-d {{ display: flex; align-items: center; gap: 11px; font-size: 15px; color: #C6D3E6; margin-top: 24px;
  background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.1); border-radius: 12px; padding: 14px 18px; }}

/* ---- Challenges ---- */
.ch-lead {{ font-size: 18px; color: #2A3542; line-height: 1.5; max-width: 1060px; margin: 0 0 24px; }}
.ch-grid {{ display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }}
.ch-card {{ background: #fff; border: 1px solid {LINE}; border-radius: 18px; padding: 20px 22px; box-shadow: 0 14px 32px rgba(15,27,52,.08); }}
.ch-ic {{ width: 50px; height: 50px; border-radius: 14px; display: flex; align-items: center; justify-content: center; margin-bottom: 13px; }}
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
.close {{ padding: 92px 84px; }}
.close-title {{ font-size: 54px; font-weight: 800; color: #fff; margin: 16px 0 20px; line-height: 1.06; letter-spacing: -1px; }}
.close-text {{ font-size: 22px; color: #B9C7DB; line-height: 1.5; max-width: 920px; }}
.close-cols {{ display: flex; gap: 84px; margin: 46px 0; }}
.cc-big {{ font-size: 62px; font-weight: 800; line-height: 1; }}
.cc-lab {{ font-size: 16px; color: #9FB0C7; margin-top: 8px; }}
.close-src {{ position: absolute; z-index: 2; bottom: 40px; left: 84px; right: 84px; font-size: 12px; color: #7E8CA3; line-height: 1.5; border-top: 1px solid rgba(255,255,255,.12); padding-top: 14px; }}
.fact-star {{ background: transparent !important; border-width: 1.5px !important; font-style: italic; }}

/* ---- Did you know (dark) ---- */
.dyk-grid {{ display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 8px; }}
.dyk-card {{ background: linear-gradient(158deg, rgba(255,255,255,.10), rgba(255,255,255,.03)); border: 1px solid rgba(255,255,255,.13); border-radius: 18px; padding: 22px 24px; box-shadow: inset 0 1px 0 rgba(255,255,255,.16); }}
.dyk-ic {{ width: 50px; height: 50px; border-radius: 14px; display: flex; align-items: center; justify-content: center; margin-bottom: 14px; }}
.dyk-t {{ font-size: 23px; font-weight: 800; color: #fff; font-family: 'Manrope'; margin-bottom: 8px; }}
.dyk-x {{ font-size: 15.5px; color: #B9C7DB; line-height: 1.5; }}

/* ---- Supply chain ---- */
.sc-lead {{ font-size: 18px; color: #2A3542; line-height: 1.5; max-width: 1080px; margin: 0 0 30px; }}
.sc-flow {{ display: flex; align-items: stretch; gap: 6px; }}
.sc-step {{ flex: 1; background: #fff; border: 1px solid {LINE}; border-radius: 16px; padding: 20px 18px; box-shadow: 0 14px 32px rgba(15,27,52,.08); text-align: center; }}
.sc-step-hl {{ border: 2px solid {ORANGE}; box-shadow: 0 16px 36px rgba(242,99,19,.16); }}
.sc-ic {{ width: 52px; height: 52px; border-radius: 15px; display: flex; align-items: center; justify-content: center; margin: 0 auto 14px; }}
.sc-stage {{ font-size: 18px; font-weight: 800; color: {INK}; font-family: 'Manrope'; }}
.sc-players {{ font-size: 14px; color: {INK2}; font-weight: 600; margin: 7px 0 6px; }}
.sc-dutch {{ font-size: 13px; color: {MUTED}; line-height: 1.4; }}
.sc-arrow {{ display: flex; align-items: center; }}
.sc-note {{ display: flex; align-items: center; gap: 11px; font-size: 15px; color: #2A3542; margin-top: 26px; background: {PAPER}; border-radius: 12px; padding: 14px 18px; }}

/* ---- Timeline ---- */
.tl-wrap {{ position: relative; margin-top: 46px; height: 400px; }}
.tl-line {{ position: absolute; left: 0; right: 0; top: 200px; height: 4px; border-radius: 3px; background: linear-gradient(90deg, {ORANGE}, {BLUE}, {GREEN}); opacity: .5; }}
.tl-row {{ display: flex; justify-content: space-between; position: relative; height: 100%; }}
.tl-node {{ flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; height: 100%; }}
.tl-mid {{ display: flex; flex-direction: column; align-items: center; position: absolute; top: 154px; }}
.tl-year {{ font-size: 21px; font-weight: 800; font-family: 'Manrope'; margin-bottom: 6px; }}
.tl-dot {{ width: 18px; height: 18px; border-radius: 50%; border: 3px solid #fff; box-shadow: 0 4px 12px rgba(15,27,52,.25); }}
.tl-card {{ width: 88%; background: #fff; border: 1px solid {LINE}; border-radius: 12px; padding: 11px 13px; box-shadow: 0 10px 26px rgba(15,27,52,.10); position: absolute; }}
.tl-up .tl-card {{ bottom: 236px; }}
.tl-down .tl-card {{ top: 236px; }}
.tl-title {{ font-size: 14.5px; font-weight: 800; color: {INK}; font-family: 'Manrope'; line-height: 1.15; }}
.tl-detail {{ font-size: 12px; color: {MUTED}; line-height: 1.35; margin-top: 4px; }}

/* ---- Geopolitics (dark) ---- */
.gp-lead {{ font-size: 19px; color: #C6D3E6; line-height: 1.5; max-width: 1080px; margin: 0 0 26px; }}
.gp-grid {{ display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }}
.gp-card {{ padding: 20px 22px 18px; display: flex; gap: 16px; }}
.gp-ic {{ width: 46px; height: 46px; border-radius: 13px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.gp-t {{ font-size: 18.5px; font-weight: 800; color: #fff; font-family: 'Manrope'; margin-bottom: 6px; }}
.gp-x {{ font-size: 13.7px; color: #B9C7DB; line-height: 1.45; }}
.z-nugget {{ margin-top: 9px; font-size: 12.5px; color: #2A3542; line-height: 1.4; border-left: 3px solid; border-radius: 8px; padding: 8px 12px; }}
.z-nugget span {{ font-weight: 800; }}

/* ---- Outlook ---- */
.ol-lead {{ font-size: 18px; color: #2A3542; line-height: 1.5; max-width: 1080px; margin: 0 0 26px; }}
.ol-grid {{ display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }}
.ol-card {{ background: #fff; border: 1px solid {LINE}; border-radius: 18px; padding: 22px 22px 20px; box-shadow: 0 14px 32px rgba(15,27,52,.08); }}
.ol-ic {{ width: 50px; height: 50px; border-radius: 14px; display: flex; align-items: center; justify-content: center; margin-bottom: 14px; }}
.ol-t {{ font-size: 19px; font-weight: 800; color: {INK}; font-family: 'Manrope'; margin-bottom: 7px; }}
.ol-x {{ font-size: 15px; color: #3A4656; line-height: 1.48; }}
'''

DOC = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{"".join(SLIDES)}</body></html>'
open("deck.html", "w", encoding="utf-8").write(DOC)
print("wrote deck.html with", len(SLIDES), "slides")

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
