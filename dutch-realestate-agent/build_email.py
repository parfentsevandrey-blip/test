#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Готовит тело письма (HTML с инлайн-стилями + плейн-текст) из того же JSON.
Используется на шаге доставки: создать черновик/письмо в Gmail.

    python3 build_email.py --data data/week_2026-06-28.json \
        --out-html reports/email.html --out-txt reports/email.txt \
        [--file-link https://drive.google.com/...]

Печатает в stdout готовую тему письма (subject).
Инлайн-стили — чтобы письмо корректно отображалось в почтовых клиентах.
"""
import argparse, json, html, os, sys
from datetime import datetime

RU_MONTHS = {1:"января",2:"февраля",3:"марта",4:"апреля",5:"мая",6:"июня",
             7:"июля",8:"августа",9:"сентября",10:"октября",11:"ноября",12:"декабря"}
SEG = {"residential":"#16846F","commercial":"#C0791C","industrial":"#2C5F8A"}
SUB_ORDER = ["laws","news","trends","stats"]
SUB_TITLES = {"laws":"Законы и регулирование","news":"Новости","trends":"Тренды","stats":"Статистика"}

def period(a,b):
    try:
        d1=datetime.strptime(a,"%Y-%m-%d"); d2=datetime.strptime(b,"%Y-%m-%d")
        if d1.month==d2.month: return f"{d1.day}–{d2.day} {RU_MONTHS[d2.month]} {d2.year}"
        return f"{d1.day} {RU_MONTHS[d1.month]} – {d2.day} {RU_MONTHS[d2.month]} {d2.year}"
    except Exception: return f"{a} — {b}"

def esc(s): return html.escape(str(s or ""))

def build_html(data, file_link=None):
    per=period(data.get("week_start",""),data.get("week_end",""))
    P=[]
    P.append(f'<div style="font-family:Segoe UI,Arial,sans-serif;color:#222B36;max-width:660px;margin:0 auto">')
    P.append(f'<div style="background:#1F3A5F;color:#fff;padding:18px 22px;border-radius:6px 6px 0 0">'
             f'<div style="font-size:12px;letter-spacing:2px;color:#A9BBD0">ЕЖЕНЕДЕЛЬНАЯ АНАЛИТИКА</div>'
             f'<div style="font-size:22px;font-weight:700;margin-top:4px">Рынок недвижимости Нидерландов</div>'
             f'<div style="font-size:14px;color:#D6E0EC;margin-top:4px">Период: {esc(per)}</div></div>')
    P.append('<div style="height:6px;background:linear-gradient(90deg,#16846F 33%,#C0791C 33%,#C0791C 66%,#2C5F8A 66%)"></div>')
    P.append('<div style="padding:18px 22px;border:1px solid #E2E8EF;border-top:none;border-radius:0 0 6px 6px">')

    if file_link:
        P.append(f'<div style="background:#EEF2F6;padding:10px 14px;border-radius:4px;margin-bottom:14px;font-size:14px">'
                 f'📎 Файл Word: <a href="{esc(file_link)}" style="color:#1155CC">скачать отчёт (.docx)</a></div>')

    if data.get("headline"):
        P.append(f'<p style="font-size:16px;font-style:italic;font-weight:700;color:#105A4C;margin:0 0 12px">«{esc(data["headline"])}»</p>')

    kt = data.get("key_takeaways") or []
    if kt:
        P.append('<div style="background:#1F3A5F;color:#ECF1F6;border-radius:6px;padding:14px 18px;margin:0 0 16px">'
                 '<div style="font-size:13px;letter-spacing:1px;font-weight:700;color:#fff;margin-bottom:6px">ГЛАВНЫЕ ВЫВОДЫ НЕДЕЛИ</div><ol style="margin:0;padding-left:20px">')
        for t in kt:
            P.append(f'<li style="font-size:14px;line-height:1.4;margin:5px 0">{esc(t)}</li>')
        P.append('</ol></div>')

    if data.get("executive_summary"):
        P.append(f'<p style="font-size:15px;line-height:1.5;margin:0 0 16px">{esc(data["executive_summary"])}</p>')

    for seg in data.get("segments",[]):
        color=SEG.get(seg.get("id"),"#1F3A5F")
        P.append(f'<div style="background:{color};color:#fff;font-weight:700;font-size:16px;padding:8px 14px;border-radius:4px;margin:18px 0 8px">{esc(seg.get("icon",""))} {esc(seg.get("title",""))}</div>')
        subs=seg.get("subsections",{}); had=False
        for key in SUB_ORDER:
            items=subs.get(key) or []
            if not items: continue
            had=True
            P.append(f'<div style="font-weight:700;color:{color};font-size:13px;margin:10px 0 4px">{SUB_TITLES[key]}</div>')
            P.append('<ul style="margin:0 0 8px;padding-left:20px">')
            for it in items:
                val=f' <b>{esc(it.get("value"))}</b>' if it.get("value") else ""
                src=[]
                if it.get("date"): src.append(esc(it["date"]))
                if it.get("source"): src.append(esc(it["source"]))
                tail=""
                if src or it.get("url"):
                    tail=' <span style="color:#6B7785;font-size:12px">— '+" · ".join(src)
                    if it.get("url"): tail+=f' · <a href="{esc(it["url"])}" style="color:#1155CC">источник</a>'
                    tail+="</span>"
                P.append(f'<li style="font-size:14px;line-height:1.4;margin:5px 0">{esc(it.get("text",""))}{val}{tail}</li>')
            P.append('</ul>')
        if not had:
            P.append('<p style="color:#6B7785;font-style:italic;font-size:13px">За неделю значимых событий не зафиксировано.</p>')
        if seg.get("conclusion"):
            P.append(f'<div style="background:#EAF3EE;border-left:4px solid #16846F;padding:10px 14px;margin:8px 0;border-radius:0 4px 4px 0">'
                     f'<span style="color:#105A4C;font-weight:700;font-size:11px;letter-spacing:1px">ВЫВОД</span>'
                     f'<div style="font-size:14px;line-height:1.45;margin-top:3px">{esc(seg["conclusion"])}</div></div>')
        if seg.get("watch"):
            P.append(f'<div style="background:#FFF6E5;border-left:4px solid #C0791C;padding:8px 14px;margin:6px 0;border-radius:0 4px 4px 0;font-size:13px;line-height:1.4">'
                     f'<b style="color:#8A5510">За чем следить.</b> {esc(seg["watch"])}</div>')

    if data.get("outlook"):
        P.append('<div style="background:#EEF2F6;border-radius:6px;padding:14px 18px;margin:14px 0">'
                 '<div style="font-size:13px;letter-spacing:1px;font-weight:700;color:#1F3A5F;margin-bottom:6px">КАРТИНА НЕДЕЛИ И ПРОГНОЗ</div>'
                 f'<div style="font-size:14px;line-height:1.5">{esc(data["outlook"])}</div></div>')

    P.append('<div style="border-top:1px solid #E2E8EF;margin-top:18px;padding-top:8px;color:#6B7785;font-size:11px;line-height:1.4">'
             'Материал носит информационно-аналитический характер, подготовлен на основе открытых источников и не является инвестиционной рекомендацией. '
             'Полная версия с источниками — во вложенном/прикреплённом файле Word.</div>')
    P.append('</div></div>')
    return "".join(P)

def build_text(data, file_link=None):
    per=period(data.get("week_start",""),data.get("week_end",""))
    L=[f"РЫНОК НЕДВИЖИМОСТИ НИДЕРЛАНДОВ — {per}", ""]
    if file_link: L.append(f"Файл Word: {file_link}\n")
    if data.get("executive_summary"): L += [data["executive_summary"], ""]
    for seg in data.get("segments",[]):
        L.append(f"== {seg.get('title','')} ==")
        subs=seg.get("subsections",{}); had=False
        for key in SUB_ORDER:
            items=subs.get(key) or []
            if not items: continue
            had=True
            L.append(f"  {SUB_TITLES[key]}:")
            for it in items:
                val=f" [{it['value']}]" if it.get("value") else ""
                src=" · ".join(x for x in [it.get('date'),it.get('source')] if x)
                L.append(f"   - {it.get('text','')}{val}" + (f" ({src})" if src else ""))
        if not had: L.append("  (за неделю значимых событий не зафиксировано)")
        if seg.get("conclusion"): L.append(f"  ВЫВОД: {seg['conclusion']}")
        L.append("")
    return "\n".join(L)

def _load_data(path):
    """Загрузить и распарсить входной JSON с понятными сообщениями об ошибках."""
    if not os.path.exists(path):
        raise FileNotFoundError(f"файл данных не найден: {path}")
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except UnicodeDecodeError as e:
        raise ValueError(f"файл не в UTF-8 ({path}): {e}") from e
    except json.JSONDecodeError as e:
        raise ValueError(
            f"некорректный JSON в {path}: строка {e.lineno}, столбец {e.colno}: {e.msg}"
        ) from e
    if not isinstance(data, dict):
        raise ValueError(f"корень JSON должен быть объектом, а не {type(data).__name__}: {path}")
    return data


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--data",required=True)
    ap.add_argument("--out-html",required=True); ap.add_argument("--out-txt",required=True)
    ap.add_argument("--file-link",default=None)
    a=ap.parse_args()

    try:
        data = _load_data(a.data)
    except (FileNotFoundError, ValueError) as e:
        print(f"ошибка: {e}", file=sys.stderr)
        sys.exit(2)

    try:
        for out_path, body in (
            (a.out_html, build_html(data, a.file_link)),
            (a.out_txt, build_text(data, a.file_link)),
        ):
            out_dir = os.path.dirname(out_path)
            if out_dir:
                os.makedirs(out_dir, exist_ok=True)
            with open(out_path, "w", encoding="utf-8") as f:
                f.write(body)
    except OSError as e:
        print(f"ошибка записи выходного файла: {e}", file=sys.stderr)
        sys.exit(2)

    print(f"Аналитика рынка недвижимости NL · {period(data.get('week_start',''),data.get('week_end',''))}")

if __name__=="__main__": main()
