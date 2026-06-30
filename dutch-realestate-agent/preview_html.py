#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HTML-превью отчёта из того же JSON, что и Word (обновлённый дизайн).
    python3 preview_html.py --data data/week_2026-06-30.json --out reports/preview.html
"""
import argparse, json, html
from datetime import datetime

RU_MONTHS = {1:"января",2:"февраля",3:"марта",4:"апреля",5:"мая",6:"июня",
             7:"июля",8:"августа",9:"сентября",10:"октября",11:"ноября",12:"декабря"}
SEG = {"residential":"#16846F","commercial":"#C0791C","industrial":"#2C5F8A"}
SUB_ORDER = ["laws","news","trends","stats"]
SUB_TITLES = {"laws":"Изменения в законах и регулировании","news":"Новости",
              "trends":"Тренды","stats":"Статистика"}
DIR = {
  "up":      {"sym":"▲","bg":"#E7F4EE","fg":"#1E7A4D"},
  "down":    {"sym":"▼","bg":"#FBEAEA","fg":"#B03A3A"},
  "neutral": {"sym":"◆","bg":"#E8F0F7","fg":"#2C5F8A"},
}

def period(a,b):
    try:
        d1=datetime.strptime(a,"%Y-%m-%d"); d2=datetime.strptime(b,"%Y-%m-%d")
        if d1.month==d2.month: return f"{d1.day}–{d2.day} {RU_MONTHS[d2.month]} {d2.year} г."
        return f"{d1.day} {RU_MONTHS[d1.month]} – {d2.day} {RU_MONTHS[d2.month]} {d2.year} г."
    except Exception: return f"{a} — {b}"

def esc(s): return html.escape(str(s or ""))

def meta(it):
    bits=[]
    if it.get("date"): bits.append(f"📅 {esc(it['date'])}")
    if it.get("source"): bits.append(f"Источник: {esc(it['source'])}")
    line=" · ".join(bits)
    if it.get("url"): line+=f' · <a href="{esc(it["url"])}">ссылка ↗</a>'
    return f'<div class="meta">{line}</div>'

def impact_block(it):
    d = DIR.get(it.get("direction"))
    if not it.get("impact"): return ""
    if not d: d = {"bg":"#EEF2F6","fg":"#6B7785","sym":"▪"}
    return (f'<div class="impact" style="background:{d["bg"]}">'
            f'<b style="color:{d["fg"]}">{d["sym"]} Почему важно:</b> {esc(it["impact"])}</div>')

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
 .ribbon{{display:flex;height:8px;margin:0 0 14px;border-radius:2px;overflow:hidden}}
 .ribbon span{{flex:1}}
 .headline{{font-size:19px;font-style:italic;font-weight:700;color:#105A4C;margin:6px 0 12px}}
 .period{{font-size:16px}} .period b{{color:#1F3A5F}}
 .seglist{{color:#6B7785;font-size:13px;margin:4px 0}}
 .gen{{color:#6B7785;font-style:italic;font-size:12px;margin-top:6px}}
 .kt{{background:#1F3A5F;color:#ECF1F6;border-radius:6px;padding:16px 20px;margin:18px 0}}
 .kt h2{{color:#fff;font-size:14px;letter-spacing:1px;margin:0 0 8px}}
 .kt ol{{margin:0;padding-left:22px}} .kt li{{margin:6px 0;line-height:1.4;font-size:14.5px}}
 .kt li::marker{{color:#F0C46A;font-weight:700}}
 h2.sub{{font-size:13px;font-weight:700;color:#1F3A5F;border-bottom:1px solid #D7DEE6;padding-bottom:3px;margin:22px 0 8px}}
 .summary{{background:#EEF2F6;padding:14px 16px;border-radius:4px;line-height:1.5;font-size:15px}}
 .bar{{color:#fff;font-weight:700;font-size:18px;padding:11px 16px;border-radius:3px;margin:26px 0 4px}}
 h3.ssub{{font-size:15px;font-weight:700;border-bottom:1px solid #D7DEE6;padding-bottom:2px;margin:16px 0 6px}}
 .item{{margin:10px 0}}
 .fact{{font-size:14.5px;line-height:1.42}} .fact .sym{{font-weight:700;margin-right:6px}}
 .impact{{font-size:13px;line-height:1.4;padding:6px 10px;border-radius:4px;margin:4px 0}}
 .meta{{color:#6B7785;font-size:11.5px;font-style:italic;margin-top:2px}} .meta a{{color:#1155CC;text-decoration:none}}
 table{{border-collapse:collapse;width:100%;margin:6px 0;font-size:13.5px}}
 th{{background:#1F3A5F;color:#fff;text-align:left;padding:7px 10px}}
 td{{padding:7px 10px;border-bottom:1px solid #eceff3;vertical-align:top}}
 tr:nth-child(even) td{{background:#F4F6F8}}
 td .src{{color:#6B7785;font-size:10.5px;font-style:italic}} td .imp{{color:#6B7785;font-size:11px;font-style:italic;margin-top:2px}}
 .concl{{background:#EAF3EE;border-left:5px solid #16846F;padding:12px 16px;margin:10px 0 4px;border-radius:0 3px 3px 0}}
 .concl .lbl{{color:#105A4C;font-weight:700;font-size:11px;letter-spacing:1px}} .concl p{{margin:4px 0 0;line-height:1.45;font-size:14px}}
 .watch{{background:#FFF6E5;border-left:5px solid #C0791C;padding:10px 16px;margin:8px 0 4px;border-radius:0 3px 3px 0;font-size:13.5px;line-height:1.4}}
 .watch b{{color:#8A5510}}
 .outlook{{background:#EEF2F6;padding:14px 16px;border-radius:4px;line-height:1.5;font-size:14.5px}}
 .empty{{color:#6B7785;font-style:italic;font-size:13.5px}}
 .chart{{margin:14px 0}} .chart img{{width:100%;display:block;border:1px solid #eceff3;border-radius:4px}}
 .ccap{{color:#6B7785;font-style:italic;font-size:12px;text-align:center;margin-top:4px}}
 .gloss td:first-child{{font-weight:700;color:#1F3A5F;width:26%}}
 .src-list{{font-size:13px;line-height:1.7}} .src-list a{{color:#1155CC;text-decoration:none}}
 .disc{{border-top:1px solid #D7DEE6;margin-top:22px;padding-top:8px;color:#6B7785;font-size:11px;font-style:italic}}
</style></head><body><div class="page">""")
    out.append('<div class="kicker">ЕЖЕНЕДЕЛЬНАЯ АНАЛИТИКА</div>')
    out.append('<h1>Рынок недвижимости<br>Нидерландов</h1>')
    out.append('<div class="ribbon">'+''.join(f'<span style="background:{SEG[k]}"></span>' for k in ["residential","commercial","industrial"])+'</div>')
    if data.get("headline"):
        out.append(f'<div class="headline">«{esc(data["headline"])}»</div>')
    out.append(f'<div class="period"><b>Период:</b> {esc(period(data.get("week_start",""),data.get("week_end","")))}</div>')
    out.append('<div class="seglist">Сегменты: жилая недвижимость · коммерция (стрит-ритейл) · индустриальная (склады, промзоны)</div>')
    out.append(f'<div class="gen">Отчёт сформирован: {esc(gen_h)}</div>')

    kt = data.get("key_takeaways") or []
    if kt:
        out.append('<div class="kt"><h2>ГЛАВНЫЕ ВЫВОДЫ НЕДЕЛИ</h2><ol>')
        for t in kt: out.append(f'<li>{esc(t)}</li>')
        out.append('</ol></div>')

    ov=[c for c in (data.get("charts") or []) if c.get("segment")=="overview"]
    if ov:
        out.append('<div class="bar" style="background:#1F3A5F">📊 Статистика в графиках</div>')
        out.append(chart_imgs(ov,pngs))

    if data.get("executive_summary"):
        out.append('<h2 class="sub">Коротко о неделе</h2>')
        out.append(f'<div class="summary">{esc(data["executive_summary"])}</div>')

    for seg in data.get("segments",[]):
        color=SEG.get(seg.get("id"),"#1F3A5F")
        out.append(f'<div class="bar" style="background:{color}">{esc(seg.get("icon",""))} {esc(seg.get("title",""))}</div>')
        subs=seg.get("subsections",{}); had=False
        for key in SUB_ORDER:
            items=subs.get(key) or []
            if not items: continue
            had=True
            out.append(f'<h3 class="ssub" style="color:{color}">{SUB_TITLES[key]}</h3>')
            if key=="stats":
                out.append('<table><tr><th>Показатель</th><th>Значение / динамика</th></tr>')
                for it in items:
                    d=DIR.get(it.get("direction")); arrow=f'<span style="color:{d["fg"]}">{d["sym"]} </span>' if d else ''
                    imp=f'<div class="imp">→ {esc(it["impact"])}</div>' if it.get("impact") else ''
                    src=f'<div class="src">{esc(it.get("source",""))}'+(f' · <a href="{esc(it["url"])}">↗</a>' if it.get("url") else '')+'</div>' if (it.get("source") or it.get("url")) else ''
                    out.append(f'<tr><td>{esc(it.get("text",""))}{imp}</td><td>{arrow}<b>{esc(it.get("value",""))}</b>{src}</td></tr>')
                out.append('</table>')
            else:
                for it in items:
                    d=DIR.get(it.get("direction")); sym=d["sym"] if d else "▪"; col=d["fg"] if d else "#6B7785"
                    out.append('<div class="item">')
                    out.append(f'<div class="fact"><span class="sym" style="color:{col}">{sym}</span>{esc(it.get("text",""))}</div>')
                    out.append(impact_block(it))
                    out.append(meta(it))
                    out.append('</div>')
        if not had:
            out.append('<p class="empty">За отчётную неделю значимых событий по этому сегменту не зафиксировано.</p>')
        sc=[c for c in (data.get("charts") or []) if c.get("segment")==seg.get("id")]
        if sc: out.append(chart_imgs(sc,pngs))
        if seg.get("conclusion"):
            out.append(f'<div class="concl"><div class="lbl">ВЫВОД</div><p>{esc(seg["conclusion"])}</p></div>')
        if seg.get("watch"):
            out.append(f'<div class="watch"><b>👁 За чем следить.</b> {esc(seg["watch"])}</div>')

    if data.get("outlook"):
        out.append('<div class="bar" style="background:#1F3A5F">🧭 Картина недели и прогноз</div>')
        out.append(f'<div class="outlook">{esc(data["outlook"])}</div>')

    gl = data.get("glossary") or []
    if gl:
        out.append('<div class="bar" style="background:#5D6D7E">📖 Словарь терминов</div><table class="gloss">')
        for g in gl: out.append(f'<tr><td>{esc(g.get("term",""))}</td><td>{esc(g.get("definition",""))}</td></tr>')
        out.append('</table>')

    if data.get("sources"):
        out.append('<div class="bar" style="background:#5D6D7E">🔗 Источники</div><div class="src-list">')
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
