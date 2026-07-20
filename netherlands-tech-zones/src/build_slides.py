# -*- coding: utf-8 -*-
"""Build a 16:9 PDF presentation from deck_data.py using Playwright + Chromium."""
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

INK = "#14213D"; ORANGE = "#EA580C"; BLUE = "#2563EB"; GREEN = "#059669"
MUTED = "#5B6472"; LINE = "#E4E8EF"; PAPER = "#F6F8FB"

CATLABEL = {"chips": "ЧИПЫ", "data": "ДАТА-ЦЕНТРЫ", "ai": "ИИ И НАУКА"}


def gmaps_url(query):
    return "https://www.google.com/maps/search/?api=1&query=" + quote(query)


def make_qr(url, key, color):
    img = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=1)
    img.add_data(url); img.make(fit=True)
    pic = img.make_image(image_factory=StyledPilImage,
                         module_drawer=RoundedModuleDrawer(),
                         color_mask=SolidFillColorMask(front_color=_hex(color), back_color=(255, 255, 255)))
    path = f"assets/qr/qr_{key}.png"
    pic.save(path)
    return path


def _hex(h):
    h = h.lstrip("#"); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def data_uri(path):
    ext = "png" if path.lower().endswith("png") else "jpeg"
    with open(path, "rb") as f:
        return f"data:image/{ext};base64," + base64.b64encode(f.read()).decode()


def esc(s):
    return html.escape(str(s))


SLIDES = []


def _pos():
    return len(SLIDES) + 1


def slide(cls, inner):
    SLIDES.append(f'<section class="slide {cls}">{inner}</section>')


# ---------- Title ----------
def title_slide():
    bg = data_uri("assets/cover.jpg")
    inner = f'''
      <div class="cover-bg" style="background-image:url('{bg}')"></div>
      <div class="cover-scrim"></div>
      <div class="cover-body">
        <div class="kicker">{esc(D.META["kicker"])}</div>
        <h1>{esc(D.META["title"])}</h1>
        <div class="cover-sub">{esc(D.META["subtitle"])}</div>
        <div class="cover-rule"></div>
        <div class="cover-meta">Аналитическая презентация&nbsp;&nbsp;·&nbsp;&nbsp;{esc(D.META["date"])}</div>
      </div>
      <div class="cover-tags">
        <span class="ctag" style="background:{ORANGE}">Чипы</span>
        <span class="ctag" style="background:{BLUE}">Дата-центры</span>
        <span class="ctag" style="background:{GREEN}">ИИ и наука</span>
      </div>'''
    slide("title", inner)


# ---------- Overview map ----------
def overview_slide():
    img = data_uri("assets/national.jpg")
    legend = f'''
      <div class="leg"><span class="dot" style="background:{ORANGE}"></span> Производство чипов</div>
      <div class="leg"><span class="dot" style="background:{BLUE}"></span> Дата-центры</div>
      <div class="leg"><span class="dot" style="background:{GREEN}"></span> ИИ и исследования</div>'''
    inner = f'''
      <div class="pad">
        <div class="eyebrow" style="color:{ORANGE}">ОБЗОР</div>
        <h2 class="stitle">Семь ключевых зон на карте страны</h2>
        <div class="ov-wrap">
          <img class="ov-map" src="{img}"/>
          <div class="ov-side">
            <p class="ov-lead">Технологическая сила Нидерландов держится на трёх направлениях, сосредоточенных в нескольких компактных зонах.</p>
            <div class="ov-legend">{legend}</div>
            <p class="ov-note">Далее — каждая зона отдельно: точный адрес, компании-резиденты и QR-код со ссылкой на Google&nbsp;Карты.</p>
          </div>
        </div>
      </div>
      {footer(_pos())}'''
    slide("content", inner)


# ---------- Section divider ----------
def divider_slide(cat):
    s = D.SECTIONS[cat]; col = D.CAT[cat]
    img = data_uri(s["img"])
    inner = f'''
      <div class="div-left" style="background:{INK}">
        <div class="div-num" style="color:{col}">{s["num"]}</div>
        <div class="div-cat" style="color:{col}">{CATLABEL[cat]}</div>
        <h2 class="div-title">{esc(s["title"])}</h2>
        <div class="div-sub">{esc(s["sub"])}</div>
        <div class="div-bar" style="background:{col}"></div>
      </div>
      <div class="div-right"><img src="{img}"/><div class="div-right-scrim" style="box-shadow: inset 60px 0 80px -30px {INK}"></div></div>'''
    slide("divider", inner)


# ---------- Zone slide ----------
def zone_slide(z, idx):
    col = D.CAT[z["cat"]]
    sat = data_uri(z["img"])
    url = gmaps_url(z.get("maps_query") or z["address"])
    qr = data_uri(make_qr(url, z["key"], INK))
    residents = ""
    for r in z["residents"]:
        addr = f'<div class="r-addr">📍 {esc(r["addr"])}</div>' if r.get("addr") else ""
        residents += f'''
          <div class="resident">
            <div class="r-head"><span class="r-name" style="color:{col}">{esc(r["name"])}</span>
            <span class="r-tag" style="background:{col}1A;color:{col}">{esc(r["tag"])}</span></div>
            <div class="r-text">{esc(r["text"])}</div>
            {addr}
          </div>'''
    facts = "".join(f'<span class="fact">{esc(f)}</span>' for f in z["facts"])
    inner = f'''
      <div class="z-top" style="background:{col}"></div>
      <div class="z-grid">
        <div class="z-left">
          <img class="z-sat" src="{sat}"/>
          <div class="z-satcap">Спутниковый снимок · {esc(z["lat"])}, {esc(z["lon"])}</div>
        </div>
        <div class="z-right">
          <div class="z-cat" style="color:{col}">{CATLABEL[z["cat"]]}</div>
          <h2 class="z-name">{esc(z["name"])}</h2>
          <div class="z-addr"><span class="pin" style="background:{col}">📍</span>
            <div><b>{esc(z["address"])}</b><span class="z-city">{esc(z["city"])}, {esc(z["region"])}</span></div></div>
          <div class="z-maps">
            <img class="qr" src="{qr}"/>
            <div class="maps-txt">
              <div class="maps-title">Google Карты</div>
              <div class="maps-sub">Наведите камеру телефона на QR-код,<br>чтобы открыть точное место на карте</div>
              <a class="maps-link" href="{url}">Открыть в Google&nbsp;Картах →</a>
            </div>
          </div>
          <div class="z-restitle">Резиденты</div>
          <div class="residents">{residents}</div>
          <div class="z-facts">{facts}</div>
        </div>
      </div>
      {footer(_pos())}'''
    slide("content zone", inner)


# ---------- Comparison ----------
def compare_slide():
    rows = ""
    for cat, zone, anchor, why in D.COMPARE:
        col = D.CAT[cat]
        rows += f'''<tr>
          <td><span class="cbar" style="background:{col}"></span>{esc(zone)}</td>
          <td>{esc(anchor)}</td>
          <td class="why">{esc(why)}</td></tr>'''
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


# ---------- Closing ----------
def closing_slide():
    inner = f'''
      <div class="close">
        <div class="close-kick" style="color:{ORANGE}">СПАСИБО ЗА ВНИМАНИЕ</div>
        <h2 class="close-title">Маленькая страна — <span style="color:{ORANGE}">огромное</span> влияние</h2>
        <p class="close-text">Нидерланды объединяют производство чипов, «облачные» дата-центры и науку об искусственном интеллекте в единую технологическую экосистему мирового значения.</p>
        <div class="close-cols">
          <div><div class="cc-big" style="color:{ORANGE}">7</div><div class="cc-lab">ключевых зон</div></div>
          <div><div class="cc-big" style="color:{BLUE}">3</div><div class="cc-lab">направления</div></div>
          <div><div class="cc-big" style="color:{GREEN}">1</div><div class="cc-lab">цель — быть лидером</div></div>
        </div>
        <div class="close-src">Источники: годовые отчёты ASML, NXP; AMS-IX; datacenters.google; news.microsoft.com; qutech.nl; brainporteindhoven.com; rijksoverheid.nl · Карты: © Esri, © OpenStreetMap / CARTO</div>
      </div>'''
    slide("closing", inner)


def footer(page):
    return f'<div class="footer"><span>Технологические промзоны Нидерландов</span><span class="pg">{page}</span></div>'


# ---------- assemble ----------
title_slide()
overview_slide()
order = [("chips", ["asml", "htc", "nijmegen"]),
         ("data", ["amsterdam", "eemshaven", "agriport"]),
         ("ai", ["delft"])]
zmap = {z["key"]: z for z in D.ZONES}
for cat, keys in order:
    divider_slide(cat)
    for k in keys:
        zone_slide(zmap[k], keys.index(k))
compare_slide()
closing_slide()

CSS = f'''
@page {{ size: 1280px 720px; margin: 0; }}
* {{ box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }}
html, body {{ margin: 0; padding: 0; background: #fff; }}
.slide {{ width: 1280px; height: 720px; position: relative; overflow: hidden; background: #fff;
  font-family: 'Liberation Sans', 'DejaVu Sans', Arial, sans-serif; color: #1F2733; page-break-after: always; }}
.slide:last-child {{ page-break-after: auto; }}
.pad {{ padding: 54px 64px; height: 100%; }}
.eyebrow {{ font-size: 16px; font-weight: 700; letter-spacing: 3px; }}
.stitle {{ font-size: 40px; font-weight: 800; color: {INK}; margin: 8px 0 26px; }}
.footer {{ position: absolute; bottom: 22px; left: 64px; right: 64px; display: flex; justify-content: space-between;
  font-size: 13px; color: {MUTED}; border-top: 1px solid {LINE}; padding-top: 10px; }}
.footer .pg {{ font-weight: 700; color: {INK}; }}

/* Title */
.title .cover-bg {{ position: absolute; inset: 0; background-size: cover; background-position: center; }}
.title .cover-scrim {{ position: absolute; inset: 0; background: linear-gradient(105deg, rgba(10,15,30,.94) 30%, rgba(10,15,30,.62) 60%, rgba(10,15,30,.28) 100%); }}
.title .cover-body {{ position: absolute; left: 72px; top: 168px; width: 760px; color: #fff; }}
.title .kicker {{ font-size: 18px; letter-spacing: 5px; font-weight: 700; color: {ORANGE}; margin-bottom: 18px; }}
.title h1 {{ font-size: 62px; line-height: 1.05; font-weight: 800; margin: 0; letter-spacing: -1px; }}
.title .cover-sub {{ font-size: 25px; color: #D7DEE8; margin-top: 20px; font-weight: 500; }}
.title .cover-rule {{ width: 120px; height: 5px; background: {ORANGE}; margin: 28px 0 20px; border-radius: 3px; }}
.title .cover-meta {{ font-size: 16px; color: #AEB8C6; letter-spacing: .5px; }}
.title .cover-tags {{ position: absolute; right: 60px; bottom: 54px; display: flex; gap: 12px; }}
.title .ctag {{ color: #fff; font-size: 16px; font-weight: 700; padding: 9px 20px; border-radius: 22px; }}

/* Overview */
.ov-wrap {{ display: flex; gap: 40px; height: 500px; }}
.ov-map {{ height: 100%; border-radius: 12px; border: 1px solid {LINE}; box-shadow: 0 12px 34px rgba(20,33,61,.14); }}
.ov-side {{ flex: 1; padding-top: 8px; }}
.ov-lead {{ font-size: 22px; line-height: 1.5; color: #2A3340; font-weight: 600; }}
.ov-legend {{ margin: 26px 0; }}
.leg {{ font-size: 19px; color: #2A3340; margin: 14px 0; display: flex; align-items: center; }}
.leg .dot {{ width: 16px; height: 16px; border-radius: 50%; display: inline-block; margin-right: 14px; }}
.ov-note {{ font-size: 17px; color: {MUTED}; line-height: 1.5; margin-top: 30px; }}

/* Divider */
.divider {{ display: flex; }}
.div-left {{ width: 58%; height: 100%; padding: 92px 64px; position: relative; }}
.div-num {{ font-size: 130px; font-weight: 800; line-height: 1; opacity: .95; }}
.div-cat {{ font-size: 20px; font-weight: 700; letter-spacing: 4px; margin-top: 18px; }}
.div-title {{ color: #fff; font-size: 52px; font-weight: 800; margin: 14px 0 0; line-height: 1.06; }}
.div-sub {{ color: #B9C2D0; font-size: 23px; margin-top: 18px; }}
.div-bar {{ width: 90px; height: 6px; border-radius: 3px; margin-top: 34px; }}
.div-right {{ width: 42%; height: 100%; position: relative; overflow: hidden; }}
.div-right img {{ width: 100%; height: 100%; object-fit: cover; }}
.div-right-scrim {{ position: absolute; inset: 0; }}

/* Zone */
.zone .z-top {{ position: absolute; top: 0; left: 0; right: 0; height: 8px; }}
.z-grid {{ display: flex; height: 100%; padding: 34px 0 0; }}
.z-left {{ width: 486px; padding: 20px 0 20px 40px; }}
.z-sat {{ width: 100%; height: 508px; object-fit: cover; border-radius: 12px; border: 1px solid {LINE}; box-shadow: 0 10px 30px rgba(20,33,61,.16); }}
.z-satcap {{ font-size: 12.5px; color: {MUTED}; margin-top: 10px; text-align: center; }}
.z-right {{ flex: 1; padding: 16px 54px 20px 40px; }}
.z-cat {{ font-size: 14px; font-weight: 700; letter-spacing: 3px; }}
.z-name {{ font-size: 33px; font-weight: 800; color: {INK}; margin: 4px 0 14px; line-height: 1.08; }}
.z-addr {{ display: flex; gap: 12px; align-items: flex-start; background: {PAPER}; border: 1px solid {LINE}; border-radius: 10px; padding: 12px 14px; }}
.z-addr .pin {{ color: #fff; font-size: 13px; width: 26px; height: 26px; border-radius: 7px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }}
.z-addr b {{ font-size: 17px; color: {INK}; display: block; }}
.z-city {{ font-size: 14px; color: {MUTED}; display: block; margin-top: 2px; }}
.z-maps {{ display: flex; gap: 16px; align-items: center; margin: 14px 0 16px; }}
.qr {{ width: 96px; height: 96px; border: 1px solid {LINE}; border-radius: 8px; padding: 4px; background: #fff; }}
.maps-title {{ font-size: 17px; font-weight: 700; color: {INK}; }}
.maps-sub {{ font-size: 13px; color: {MUTED}; margin: 3px 0 7px; line-height: 1.35; }}
.maps-link {{ font-size: 14.5px; font-weight: 700; color: {BLUE}; text-decoration: none; }}
.z-restitle {{ font-size: 14px; font-weight: 700; letter-spacing: 2px; color: {MUTED}; text-transform: uppercase; margin-bottom: 8px; }}
.residents {{ display: flex; flex-direction: column; gap: 9px; }}
.resident {{ border-left: 3px solid {LINE}; padding-left: 12px; }}
.r-head {{ display: flex; align-items: center; gap: 10px; }}
.r-name {{ font-size: 16.5px; font-weight: 800; }}
.r-tag {{ font-size: 11.5px; font-weight: 700; padding: 2px 9px; border-radius: 20px; }}
.r-text {{ font-size: 13.7px; color: #2A3340; line-height: 1.4; margin-top: 3px; }}
.r-addr {{ font-size: 12px; color: {MUTED}; margin-top: 3px; }}
.z-facts {{ display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }}
.fact {{ font-size: 12.5px; font-weight: 600; color: {INK}; background: {PAPER}; border: 1px solid {LINE}; border-radius: 20px; padding: 5px 12px; }}

/* Compare */
.ctable {{ width: 100%; border-collapse: collapse; font-size: 18px; }}
.ctable th {{ text-align: left; background: {INK}; color: #fff; font-size: 15px; padding: 13px 16px; }}
.ctable td {{ padding: 12px 16px; border-bottom: 1px solid {LINE}; color: #26303C; vertical-align: middle; }}
.ctable tbody tr:nth-child(even) {{ background: #F4F7FB; }}
.ctable td:first-child {{ font-weight: 700; color: {INK}; white-space: nowrap; }}
.ctable .why {{ color: {MUTED}; font-size: 16.5px; }}
.cbar {{ display: inline-block; width: 5px; height: 20px; border-radius: 3px; margin-right: 12px; vertical-align: -4px; }}

/* Closing */
.closing {{ background: {INK}; height: 100%; padding: 96px 84px; color: #fff; }}
.close-kick {{ font-size: 18px; letter-spacing: 4px; font-weight: 700; }}
.close-title {{ font-size: 52px; font-weight: 800; margin: 18px 0 22px; line-height: 1.08; }}
.close-text {{ font-size: 23px; color: #C4CDDB; line-height: 1.5; max-width: 900px; }}
.close-cols {{ display: flex; gap: 90px; margin: 46px 0; }}
.cc-big {{ font-size: 66px; font-weight: 800; line-height: 1; }}
.cc-lab {{ font-size: 17px; color: #AEB8C6; margin-top: 6px; }}
.close-src {{ position: absolute; bottom: 44px; left: 84px; right: 84px; font-size: 12.5px; color: #8A94A6; line-height: 1.5; border-top: 1px solid #2A3550; padding-top: 14px; }}
'''

DOC = f'<!doctype html><html lang="ru"><head><meta charset="utf-8"><style>{CSS}</style></head><body>{"".join(SLIDES)}</body></html>'
open("deck.html", "w", encoding="utf-8").write(DOC)
print("wrote deck.html with", len(SLIDES), "slides")

# ---------- render ----------
from playwright.sync_api import sync_playwright
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=CHROME, args=["--no-sandbox"])
    pg = b.new_page()
    pg.goto("file://" + os.path.join(HERE, "deck.html"), wait_until="load")
    pg.wait_for_timeout(600)
    pg.pdf(path="presentation.pdf", prefer_css_page_size=True, print_background=True)
    b.close()
print("wrote presentation.pdf", os.path.getsize("presentation.pdf"), "bytes")
