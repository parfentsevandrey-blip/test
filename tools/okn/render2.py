"""Собирает перечень ОКН: обложка, сводные таблицы, условия, карточки."""
import json, os

from build_okn import (objs, GROUPS, esc, num, money, fdate, unique_area,
                       COVERS, ROOTS, STATUS, COND, SEC, OUT)
from cards import card, role_of, SLUGS, SITE, OWN2, photo_files
from styles import CSS

SHORT = {"Малый Казенный переулок, 5": "М. Казенный, 5",
         "Усадьба Щапово": "Щапово",
         "Усадьба Филимонки": "Филимонки"}
ROLECLS = {"ансамбль": "ens"}


def summary_table():
    rows, n = [], 0
    for gname, _, _, ids in GROUPS:
        gs = SHORT[gname]
        for oid in ids:
            n += 1
            o = objs[str(oid)]
            st = o.get("status")
            rname, rcls = role_of(oid)
            short_role = "ансамбль" if rcls == "ens" else ("в составе" if rcls == "part" else "отдельный")
            rows.append(f"""<tr>
<td class="n">{n}</td>
<td><a href="#okn-{oid}">{esc(o.get('name'))}</a></td>
<td class="cx">{esc(gs)}</td>
<td><span class="role {rcls}">{short_role}</span></td>
<td><span class="{'go' if st=='READY' else 'wait'}"><b>{esc(STATUS.get(st,st))}</b></span></td>
<td>{esc(fdate(o.get('auctionPeriod')) or '—')}</td>
<td class="n">{num(o.get('totalArea'),'')}</td>
<td>{esc(COND.get(o.get('condition'),'—'))}</td>
<td>{esc(SEC.get(o.get('securityCategory'),'—').replace(' значения',''))}</td>
<td>{esc(OWN2.get(o.get('ownType'),'—').replace('Субъекта РФ — город Москва','г. Москва'))}</td>
<td class="n"><a href="{SITE}{SLUGS[str(oid)]}" target="_blank" rel="noopener">{oid}</a></td>
</tr>""")
    return f"""<div class="scroll"><table class="sum">
<thead><tr><th>№</th><th>Объект</th><th>Комплекс</th><th>Роль</th><th>Статус</th>
<th>Срок торгов</th><th class="n">Площадь, м²</th><th>Состояние</th><th>Охрана</th>
<th>Собственность</th><th class="n">ID</th></tr></thead>
<tbody>{''.join(rows)}</tbody></table></div>"""


def complex_table():
    rows = []
    for gname, gsub, _, ids in GROUPS:
        root = next((i for i in ids if i in COVERS), None)
        area = objs[str(root)]["totalArea"] if root else sum(
            objs[str(i)]["totalArea"] for i in ids)
        extra = [i for i in ids if i not in COVERS and i in ROOTS]
        if extra:
            area += sum(objs[str(i)]["totalArea"] for i in extra)
        land = sum((p.get("totalArea") or 0)
                   for i in ids for p in (objs[str(i)].get("landPlotsParameters") or []))
        cost = sum((b.get("realEstateCost") or 0)
                   for i in ids for b in (objs[str(i)].get("realEstateInfo") or []))
        rows.append(f"""<tr>
<td><b>{esc(gname)}</b><br><span class="cx">{esc(gsub)}</span></td>
<td class="n">{len(ids)}</td>
<td class="n">{num(area,'м²',1)}</td>
<td class="n">{num(land,'м²',0) if land else '—'}</td>
<td class="n">{money(cost) if cost else '—'}</td></tr>""")
    return f"""<div class="scroll"><table class="sum">
<thead><tr><th>Комплекс</th><th class="n">Объектов</th><th class="n">Площадь зданий</th>
<th class="n">Земля</th><th class="n">Кад. стоимость зданий</th>
</tr></thead><tbody>{''.join(rows)}</tbody></table></div>"""


TERMS = """
<div class="terms" id="investor">
  <h3>Что потребуется от инвестора</h3>
  <p class="gnote">Все пятнадцать объектов идут по программе ДОМ.РФ: государство продаёт
  или сдаёт ОКН, инвестор обязуется его восстановить. Условия ниже — со страницы
  программы льготного кредитования, они общие для всех объектов перечня.</p>

  <div class="sect">
    <h4>Ставка по кредиту</h4>
    <div class="rates">
      <div class="rate"><b>6%</b><span>годовых — если ОКН приспосабливается
        под гостиницу от двух звёзд</span></div>
      <div class="rate"><b>9%</b><span>годовых — на иные проекты восстановления</span></div>
    </div>
    <p class="gnote" style="margin-top:12px">Оператор — ДОМ.РФ. Кредиторы: Банк ДОМ.РФ,
    Альфа-Банк, ВТБ, Совкомбанк, Сбербанк, ВЭБ.РФ. Заёмщик — юрлицо или ИП,
    которое стало собственником, арендатором или иным правообладателем объекта.</p>
  </div>

  <div class="sect">
    <h4>Предельные сроки работ</h4>
    <table class="deadlines"><tbody>
      <tr><td>Проектная документация и экспертизы</td><td>18 мес.</td></tr>
      <tr><td>Реставрация, объект до 500 м²</td><td>30 мес.</td></tr>
      <tr><td>Реставрация, объект до 1 500 м²</td><td>36 мес.</td></tr>
      <tr><td>Реставрация, объект до 5 000 м²</td><td>48 мес.</td></tr>
      <tr><td>Реставрация, объект от 5 000 м²</td><td>60 мес.</td></tr>
      <tr><td>Эксплуатация после приёмки</td><td>5 лет</td></tr>
      <tr><td>Крайний срок акта приёмки работ</td><td>31.12.2030</td></tr>
    </tbody></table>
  </div>

  <div class="sect">
    <h4>Что обязан сделать инвестор</h4>
    <ul>
      <li>Принять и исполнять <b>охранное обязательство</b> — оно опубликовано
        в карточке объекта и переходит к новому правообладателю вместе с объектом.</li>
      <li>Разработать проектную документацию на реставрацию и приспособление,
        пройти государственную историко-культурную экспертизу.</li>
      <li>Получить разрешение Мосгорнаследия и вести работы силами подрядчика
        с лицензией Минкультуры.</li>
      <li>Сохранить <b>предмет охраны</b> — утверждённый распоряжением перечень
        элементов, которые менять нельзя. По каждому объекту он свой; выжимка
        приведена в карточке.</li>
      <li>Отчитываться о ходе восстановления — этапы видны в «Дорожной карте»
        карточки объекта на портале.</li>
      <li>Уложиться в предельные сроки выше: их срыв лишает льготной ставки.</li>
    </ul>
  </div>

  <div class="sect">
    <h4>Что нашлось в охранных обязательствах</h4>
    <ul>
      <li><b>Апартаменты фактически запрещены.</b> Приказ Мосгорнаследия от 14.03.2024 № 69
        запрещает устраивать в ОКН нежилые помещения с возможностью проживания —
        «жилые ячейки» — если совпадают не менее трёх признаков: деление на изолированные
        помещения, индивидуальные санузлы и места под кухню, отдельные стояки и вводы
        электрики, и отсутствие у участка ВРИ под гостиницы, туристическое обслуживание
        или стационарное медобслуживание.</li>
      <li><b>Ни у одного из пятнадцати участков нет ВРИ под гостиницу.</b> Встречаются
        «использование и обслуживание объектов недвижимости», «культурное развитие (3.6)»,
        «эксплуатация зданий историко-культурного назначения», «историко-культурная
        деятельность (9.3)», административные учреждения, научные цели, благоустройство
        и даже «склады (6.9)». А льготная ставка 6% даётся именно под гостиницы от двух
        звёзд — значит смену ВРИ нужно закладывать в план и сроки заранее.</li>
      <li><b>Работы — только аттестованными специалистами.</b> Консервацию и реставрацию
        вправе вести лишь физлица, аттестованные Минкультуры по приказу № 474 и состоящие
        в трудовых отношениях с лицензированным подрядчиком.</li>
      <li><b>Информационная надпись — до 1 июня 2027 года.</b> Охранные обязательства
        по объектам Щапова прямо называют этот срок установки таблички по постановлению
        Правительства РФ от 10.09.2019 № 1178.</li>
      <li><b>Доступность для инвалидов</b> обеспечивается по приказу Минкультуры
        от 20.11.2015 № 2834 — с учётом того, что предмет охраны менять нельзя.</li>
    </ul>
  </div>

  <div class="sect">
    <h4>О чём стоит знать заранее</h4>
    <ul>
      <li><b>Коммуникаций нет.</b> У всех пятнадцати объектов перечня в карточке
        проставлено «нет» по электро-, водоснабжению, водоотведению, отоплению и газу.
        Подключение — забота и смета инвестора.</li>
      <li><b>Продаётся не всегда всё.</b> Часть объектов — отдельные строения внутри
        ансамбля, и земля под ними уходит отдельным лотом либо в аренду;
        ссылка на лот земля.дом.рф есть в карточке, где он заведён.</li>
      <li><b>Решение по объекту может быть ещё не принято.</b> У статуса
        «Решение отсутствует» сроков торгов нет — это объект-кандидат,
        а не готовое предложение.</li>
      <li><b>Предмет охраны бывает шире здания.</b> В ансамблях под охрану попадают
        композиция застройки, соотношение озеленённых и замощённых площадей
        и даже характер мощения двора — то есть благоустройство тоже регламентировано.</li>
    </ul>
  </div>
</div>
"""


def build():
    blocks, n = [], 0
    for gname, gsub, gnote, ids in GROUPS:
        inner = []
        for oid in ids:
            n += 1
            inner.append(card(oid, n))
        blocks.append(f"""<section>
  <p class="gsub">{esc(gsub)}</p>
  <h2>{esc(gname)} <span class="n">· {len(ids)}</span></h2>
  <p class="gnote">{esc(gnote)}</p>
  {''.join(inner)}
</section>""")

    total = unique_area()
    ready = sum(1 for o in objs.values() if o.get("status") == "READY")
    unsat = sum(1 for o in objs.values() if o.get("condition") == "UNSATISFACTORY")

    COVER = [(4097, "Малый Казенный, 5 · усадьба Нарышкиных"),
             (3529, "Щапово · каретный двор"),
             (3764, "Филимонки · усадьба, 1801 год")]
    cells = []
    for oid, cap in COVER:
        pf = photo_files(oid)
        if pf:
            cells.append(f'<figure><img src="{pf[0]}" alt="{esc(cap)}">'
                         f'<figcaption>{esc(cap)}</figcaption></figure>')
    strip = f'<div class="strip">{"".join(cells)}</div>' if cells else ""

    return f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Объекты культурного наследия ДОМ.РФ</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=PT+Serif:ital,wght@0,400;0,700;1,400&family=PT+Sans:wght@400;700&family=PT+Mono&display=swap">
<style>{CSS}</style>
</head>
<body>
<header class="top"><div class="wrap">
  <p class="kicker">ДОМ.РФ · объекты культурного наследия · Москва</p>
  <h1>Объекты культурного наследия ДОМ.РФ</h1>
  <p class="lede">Пятнадцать московских памятников, которые ДОМ.РФ готовит к передаче
  инвестору: три усадебных комплекса и {num(total,'м²',1)} под реставрацию —
  от ограды в семь метров до больницы почти на 1 200. По каждому объекту —
  расположение, характеристики, предмет охраны и фотографии.</p>
  <dl class="stats">
    <div><dt>Объектов</dt><dd>{len(objs)}</dd></div>
    <div><dt>Готовятся к торгам</dt><dd>{ready}</dd></div>
    <div><dt>Площадь под реставрацию</dt><dd>{num(total,'м²',1)}</dd></div>
    <div><dt>В неудовл. состоянии</dt><dd>{unsat}</dd></div>
  </dl>
  {strip}
  <p class="meta">Портал ДОМ.РФ «Объекты культурного наследия» · схемы расположения —
  Яндекс Карты · сведения на 22 сентября 2026 года</p>
</div></header>

<div class="wrap">
  <h2>Три комплекса</h2>
  {complex_table()}

  <h2>Сводная таблица</h2>
  {summary_table()}
  <p class="tnote">Три строки перечня — ансамбли целиком, и площадь каждого уже включает
  площади его строений, которые идут отдельными карточками. Складывать все пятнадцать
  строк поэтому не следует: суммарная площадь комплексов — {num(total,'м²',1)}.</p>

  <h2>Условия для инвестора</h2>
  {TERMS}
  {''.join(blocks)}

  <footer class="src">
    <p>Сведения об объектах, фотографии и документы — портал ДОМ.РФ
    «Объекты культурного наследия» (наследие.дом.рф), раздел «Город Москва»,
    статусы «Подготовка к торгам» и «Решение отсутствует». Схемы расположения —
    Яндекс Карты. Данные приведены по состоянию на 22 сентября 2026 года.</p>
    <p>Кадастровая стоимость не является рыночной оценкой и не равна стартовой цене
    торгов; сроки торгов указаны плановым кварталом и могут измениться. Актуальные
    сведения и полные тексты охранных документов — в карточке объекта на портале.</p>
  </footer>
</div>
</body>
</html>"""


if __name__ == "__main__":
    h = build()
    p = f"{OUT}/build/okn-moscow.html"
    open(p, "w").write(h)
    print(f"{p}  {len(h)/1048576:.2f} МБ")
