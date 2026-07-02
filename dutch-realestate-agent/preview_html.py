#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HTML-превью отчёта из того же JSON, что и Word (обновлённый дизайн).
    python3 preview_html.py --data data/week_2026-06-30.json --out reports/preview.html
"""
import argparse, json, html, os
from datetime import datetime

import constants as _const

# Константы — единый источник в constants.py (см. также generate_report.py,
# charts.py), чтобы палитры/словари не расходились между рендерерами.
RU_MONTHS = _const.RU_MONTHS
SEG = {k: "#" + v["bar"] for k, v in _const.SEG_COLORS_HEX.items()}
SUB_ORDER = _const.SUBSECTION_ORDER
SUB_TITLES = _const.SUBSECTION_TITLES
DIR = {k: {"sym": v["sym"], "bg": "#" + v["bg"], "fg": "#" + v["fg"]}
       for k, v in _const.DIRECTION_HEX.items()}
TSTATUS = {k: {"t": v["label"], "bg": "#" + v["bg"], "fg": "#" + v["fg"]}
           for k, v in _const.THREAD_STATUS_HEX.items()}
KIND_ICON = _const.KIND_ICON
SEG_RU = _const.SEG_RU_SHORT

def period(a,b):
    try:
        d1=datetime.strptime(a,"%Y-%m-%d"); d2=datetime.strptime(b,"%Y-%m-%d")
        if d1.month==d2.month: return f"{d1.day}–{d2.day} {RU_MONTHS[d2.month]} {d2.year} г."
        return f"{d1.day} {RU_MONTHS[d1.month]} – {d2.day} {RU_MONTHS[d2.month]} {d2.year} г."
    except Exception: return f"{a} — {b}"

def esc(s): return html.escape(str(s or ""))

# Реальный файл живёт в корне проекта (profile.json); "config/profile.json" —
# путь из agent_instructions.md §2.9.4, фактически не использовавшийся.
# Проверяем оба, тем же порядком, что validate.py и generate_report.py.
PROFILE_PATH_CANDIDATES = (os.path.join("config", "profile.json"), "profile.json")

def _has_profile(paths=PROFILE_PATH_CANDIDATES):
    """True, только если один из кандидатов существует и непуст.

    Персонализация выключена по умолчанию (agent_instructions.md §2.9.4):
    даже если в data-файле уже есть portfolio_notes, превью не должно
    показывать врезку без явного подтверждающего profile.json — иначе
    ложная персонализация («ваш склад» и т.п.) для читателя без профиля.
    Логика продублирована из generate_report._has_profile, чтобы Word и
    HTML-превью не расходились по контракту персонализации.
    """
    for path in ((paths,) if isinstance(paths, str) else paths):
        if not path or not os.path.exists(path):
            continue
        try:
            with open(path, "r", encoding="utf-8") as f:
                profile = json.load(f)
        except Exception:
            continue
        if profile:
            return True
    return False

def meta(it):
    bits=[]
    if it.get("date"): bits.append(esc(it["date"]))
    if it.get("source"): bits.append(f"Источник: {esc(it['source'])}")
    line=" · ".join(bits)
    if it.get("url"): line+=f' · <a href="{esc(it["url"])}">ссылка ↗</a>'
    return f'<div class="meta">{line}</div>'

def impact_block(it):
    if not it.get("impact"): return ""
    return f'<div class="impact"><b>Значение.</b> {esc(it["impact"])}</div>' 

def chart_imgs(specs, pngs):
    out=[]
    for s in specs or []:
        src=(pngs or {}).get(s.get("id"))
        if not src: continue
        out.append(f'<div class="chart"><img src="{esc(src)}"/>')
        if s.get("caption"): out.append(f'<div class="ccap">{esc(s["caption"])}</div>')
        out.append('</div>')
    return "".join(out)

def render(data, pngs=None):
    gen=data.get("report_date","")
    try:
        gd=datetime.strptime(gen,"%Y-%m-%d"); gen_h=f"{gd.day} {RU_MONTHS[gd.month]} {gd.year} г."
    except Exception: gen_h=gen
    out=[]
    out.append(f"""<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Аналитика недвижимости NL · {esc(period(data.get('week_start',''),data.get('week_end','')))}</title>
<style>
 *{{box-sizing:border-box}}
 body{{font-family:'Segoe UI',Calibri,Arial,sans-serif;color:#222B36;margin:0;background:#eceff3}}
 .page{{max-width:820px;margin:24px auto;background:#fff;padding:40px 48px 36px;box-shadow:0 2px 18px rgba(0,0,0,.12)}}
 .kicker{{color:#6B7785;font-weight:700;letter-spacing:3px;font-size:12px}}
 h1{{color:#1F3A5F;font-size:38px;line-height:1.05;margin:6px 0 14px}}
 .rule{{border-bottom:2px solid #1F3A5F;margin:0 0 14px}}
 .headline{{font-size:19px;font-style:italic;font-weight:700;color:#105A4C;margin:6px 0 12px}}
 .period{{font-size:16px}} .period b{{color:#1F3A5F}}
 .seglist{{color:#6B7785;font-size:13px;margin:4px 0}}
 .gen{{color:#6B7785;font-style:italic;font-size:12px;margin-top:6px}}
 .kt ol{{margin:4px 0 0;padding-left:22px}} .kt li{{margin:6px 0;line-height:1.45;font-size:14.5px}}
 .kt li::marker{{color:#1F3A5F;font-weight:700}}
 h2.sub{{font-size:13px;font-weight:700;color:#1F3A5F;border-bottom:1px solid #D7DEE6;padding-bottom:3px;margin:22px 0 8px}}
 .summary{{line-height:1.5;font-size:15px;margin:4px 0 10px}}
 .bar{{color:#1F3A5F;font-weight:700;font-size:18px;border-bottom:1.5px solid #D7DEE6;padding:0 0 5px;margin:28px 0 8px}}
 .item{{margin:12px 0}}
 .fact{{font-size:14.5px;line-height:1.45}} .fact .sym{{color:#6B7785;margin-right:6px}}
 .impact{{font-size:13px;line-height:1.45;margin:4px 0 2px}}
 .impact b{{color:#6B7785;font-style:italic}}
 .meta{{color:#6B7785;font-size:11.5px;font-style:italic;margin-top:2px}} .meta a{{color:#1155CC;text-decoration:none}}
 table{{border-collapse:collapse;width:100%;margin:6px 0;font-size:13.5px}}
 th{{background:#1F3A5F;color:#fff;text-align:left;padding:7px 10px}}
 td{{padding:7px 10px;border-bottom:1px solid #eceff3;vertical-align:top}}
 tr:nth-child(even) td{{background:#F4F6F8}}
 td .src{{color:#6B7785;font-size:10.5px;font-style:italic}} td .imp{{color:#6B7785;font-size:11px;font-style:italic;margin-top:2px}}
 .concl{{margin:12px 0 4px;line-height:1.45;font-size:14px}} .concl b{{color:#1F3A5F}}
 .watch{{margin:4px 0 6px;font-size:13.5px;line-height:1.45}} .watch b{{color:#1F3A5F}}
 .outlook{{line-height:1.5;font-size:14.5px;margin:4px 0 10px}}
 .empty{{color:#6B7785;font-style:italic;font-size:13.5px}}
 .pbox{{background:#FBF1DF;border-left:5px solid #C0791C;border-radius:0 4px 4px 0;padding:12px 16px;margin:14px 0}}
 .pbox .lbl{{color:#8A5510;font-weight:700;font-size:12px;letter-spacing:1px}} .pbox ul{{margin:6px 0 0;padding-left:20px}} .pbox li{{margin:5px 0;font-size:14px;line-height:1.4}}
 .thread{{margin:10px 0;padding-bottom:8px;border-bottom:1px solid #eceff3}}
 .chip{{display:inline-block;padding:1px 8px;border-radius:10px;font-size:10px;font-weight:700;letter-spacing:.5px;vertical-align:middle}}
 .thread .tt{{font-weight:700;color:#1F3A5F;font-size:15px;margin-left:6px}}
 .thread .nw{{font-size:13.5px;margin-top:3px}} .thread .nw b{{color:#6B7785}}
 .thread .tr{{font-size:12.5px;color:#6B7785;margin-top:2px}} .thread .tr b{{color:#8A5510}}
 .cal{{font-size:13.5px;line-height:1.5}} .cal .d{{color:#1F3A5F;font-weight:700}} .cal .imp{{color:#6B7785;font-size:12px;font-style:italic;margin:0 0 6px 16px}}
 .cal p{{margin:4px 0 0 0}} .cal .segl{{color:#6B7785;font-style:italic;font-size:12px}}
 .chart{{margin:14px 0}} .chart img{{width:100%;display:block;border:1px solid #eceff3;border-radius:4px}}
 .ccap{{color:#6B7785;font-style:italic;font-size:12px;text-align:center;margin-top:4px}}
 .gloss{{font-size:13px;line-height:1.55}} .gloss b{{color:#1F3A5F}} .gloss p{{margin:3px 0}}
 .src-list{{font-size:13px;line-height:1.7}} .src-list a{{color:#1155CC;text-decoration:none}}
 .disc{{border-top:1px solid #D7DEE6;margin-top:22px;padding-top:8px;color:#6B7785;font-size:11px;font-style:italic}}
</style></head><body><div class="page">""")
    out.append('<div class="kicker">ЕЖЕНЕДЕЛЬНАЯ АНАЛИТИКА</div>')
    out.append('<h1>Рынок недвижимости<br>Нидерландов</h1>')
    out.append('<div class="rule"></div>')
    if data.get("headline"):
        out.append(f'<div class="headline">«{esc(data["headline"])}»</div>')
    out.append(f'<div class="period"><b>Период:</b> {esc(period(data.get("week_start",""),data.get("week_end","")))}</div>')
    out.append('<div class="seglist">Сегменты: жилая недвижимость · коммерция (стрит-ритейл) · индустриальная (склады, промзоны)</div>')
    out.append(f'<div class="gen">Отчёт сформирован: {esc(gen_h)}</div>')

    kt = data.get("key_takeaways") or []
    if kt:
        out.append('<div class="bar">Главные выводы недели</div><div class="kt"><ol>')
        for t in kt: out.append(f'<li>{esc(t)}</li>')
        out.append('</ol></div>')

    pn=data.get("portfolio_notes") or []
    if pn and _has_profile():
        out.append('<div class="pbox"><div class="lbl">★ ВАЖНО ДЛЯ ВАШЕГО ПОРТФЕЛЯ</div><ul>')
        for n in pn:
            out.append(f'<li>{esc(n.get("text","") if isinstance(n,dict) else n)}</li>')
        out.append('</ul></div>')

    th=data.get("threads") or []
    if th:
        out.append('<div class="bar">Сюжеты в развитии</div>')
        for t in th:
            st=TSTATUS.get((t.get("status") or "").lower(),{"t":"—","bg":"#EEF2F6","fg":"#6B7785"})
            out.append('<div class="thread">')
            out.append(f'<span class="chip" style="background:{st["bg"]};color:{st["fg"]}">{st["t"]}</span><span class="tt">{esc(t.get("title",""))}</span>')
            if t.get("update"): out.append(f'<div class="nw"><b>Что нового:</b> {esc(t["update"])}</div>')
            nt=t.get("next_trigger") or {}
            if nt.get("date") or nt.get("what"):
                link=f' · <a href="{esc(t["url"])}">↗</a>' if t.get("url") else ''
                out.append(f'<div class="tr"><b>Следующий триггер:</b> {esc((str(nt.get("date",""))+" — "+str(nt.get("what",""))).strip(" —"))}{link}</div>')
            out.append('</div>')

    ov=[c for c in (data.get("charts") or []) if c.get("segment")=="overview"]
    if ov:
        out.append('<div class="bar">Статистика в графиках</div>')
        out.append(chart_imgs(ov,pngs))

    if data.get("executive_summary"):
        out.append('<h2 class="sub">Коротко о неделе</h2>')
        out.append(f'<div class="summary">{esc(data["executive_summary"])}</div>')

    for seg in data.get("segments",[]):
        color=SEG.get(seg.get("id"),"#1F3A5F")
        out.append(f'<div class="bar">{esc(seg.get("title",""))}</div>')
        subs=seg.get("subsections",{})

        # subsections может быть dict (по ключам) или list — как в generate_report.py,
        # чтобы preview_html и Word-рендер не расходились по поддерживаемому контракту.
        def get_items(key, subs=subs):
            if isinstance(subs, dict):
                return subs.get(key) or []
            for s in subs or []:
                if s.get("id") == key or s.get("key") == key:
                    return s.get("items") or []
            return []

        # формальный стиль: единый поток пунктов без подзаголовков и таблиц,
        # порядок подразделов (laws → news → trends → stats) сохранён
        had=False
        for key in SUB_ORDER:
            items=get_items(key)
            if not items: continue
            had=True
            for it in items:
                out.append('<div class="item">')
                out.append(f'<div class="fact"><span class="sym">—</span>{esc(it.get("text",""))}</div>')
                out.append(impact_block(it))
                out.append(meta(it))
                out.append('</div>')
        if not had:
            out.append('<p class="empty">За отчётную неделю значимых событий по этому сегменту не зафиксировано.</p>')
        sc=[c for c in (data.get("charts") or []) if c.get("segment")==seg.get("id")]
        if sc: out.append(chart_imgs(sc,pngs))
        if seg.get("conclusion"):
            out.append(f'<div class="concl"><b>Вывод.</b> {esc(seg["conclusion"])}</div>')
        if seg.get("watch"):
            out.append(f'<div class="watch"><b>За чем следить.</b> {esc(seg["watch"])}</div>')

    if data.get("outlook"):
        out.append('<div class="bar">Картина недели и прогноз</div>')
        out.append(f'<div class="outlook">{esc(data["outlook"])}</div>')

    cal=data.get("calendar") or []
    if cal:
        def _ck(c):
            try: return (0,datetime.strptime(c.get("date",""),"%Y-%m-%d"))
            except Exception: return (1,datetime.max)
        out.append('<div class="bar">Календарь: за чем следить</div>')
        out.append('<div class="cal">')
        for c in sorted(cal,key=_ck):
            segl=esc(SEG_RU.get(c.get("segment"),c.get("segment","")))
            out.append(f'<p><span class="d">{esc(c.get("date",""))}</span> — {esc(c.get("what",""))}'
                       + (f' <span class="segl">({segl})</span>' if segl else '') + '</p>')
            if c.get("impact"):
                out.append(f'<div class="imp">{esc(c["impact"])}</div>')
        out.append('</div>')

    gl = data.get("glossary") or []
    if gl:
        out.append('<div class="bar">Словарь терминов</div><div class="gloss">')
        for g in gl: out.append(f'<p><b>{esc(g.get("term",""))}</b> — {esc(g.get("definition",""))}</p>')
        out.append('</div>')

    if data.get("sources"):
        out.append('<div class="bar">Источники</div><div class="src-list">')
        for s in data["sources"]:
            out.append(f'• {esc(s.get("name",""))} <a href="{esc(s.get("url",""))}">{esc(s.get("url",""))}</a><br>')
        out.append('</div>')

    out.append('<div class="disc">Дисклеймер: материал носит информационно-аналитический характер, подготовлен на основе открытых источников и не является инвестиционной рекомендацией. Проверяйте данные по первоисточникам перед принятием решений.</div>')
    out.append('</div></body></html>')
    return "".join(out)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--data",required=True); ap.add_argument("--out",required=True)
    a=ap.parse_args()
    data=json.load(open(a.data,encoding="utf-8"))
    data["charts"]=(data.get("charts") or [])+(data.get("trend_chart_specs") or [])
    pngs={}
    charts_list=data.get("charts") or []
    if charts_list:
        try:
            import os, charts as _charts
            outdir=os.path.dirname(os.path.abspath(a.out)) or "."
            rendered=_charts.render_charts(charts_list, os.path.join(outdir,"assets"))
            pngs={cid:"assets/"+os.path.basename(p) for cid,p in rendered.items()}
        except Exception as e:
            print("⚠️ графики пропущены:",e)
    open(a.out,"w",encoding="utf-8").write(render(data,pngs))
    print("✅ HTML-превью:",a.out)

if __name__=="__main__": main()
