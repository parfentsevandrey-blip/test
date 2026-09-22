"""Верстает страницу перечня ОКН из карточек build_okn."""
import json, os
from build_okn import (objs, GROUPS, card, esc, num, fdate, STATUS, COND, SEC,
                       OUT, unique_area, role, COVERS, ROOTS, photos)

SLUGS = json.load(open(f"{OUT}/slugs.json"))
SITE = "https://xn--80aicbopm7a.xn--d1aqf.xn--p1ai"

CSS = """
:root{
  --ground:#f3f1ea; --paper:#fffdf8; --ink:#1f242b; --ink-2:#5a626e; --ink-3:#8b939f;
  --hair:#e3dfd4; --hair-2:#efebe1; --zebra:#faf8f2;
  --brick:#8a3a2d; --brick-2:#a44f3d; --gold:#b08d3c;
  --go:#1a7f4b; --wait:#a8722a; --link:#1f5c99;
  --chip:#ece7db; --chip-ink:#6b6354; --yes:#1a7f4b; --no:#9a5b52;
  --shadow:0 1px 2px rgba(31,36,43,.05),0 10px 34px rgba(31,36,43,.07);
}
@media (prefers-color-scheme:dark){
  :root:not([data-theme="light"]){
    --ground:#14171c; --paper:#1c2128; --ink:#e9eaec; --ink-2:#a5adb8; --ink-3:#78818d;
    --hair:#2d3540; --hair-2:#242b34; --zebra:#1f252d;
    --brick:#c8705d; --brick-2:#d8836f; --gold:#c9a55a;
    --go:#4cc98a; --wait:#e0a33f; --link:#6fb2e8;
    --chip:#2a313b; --chip-ink:#bdb49f; --yes:#4cc98a; --no:#d98c80;
    --shadow:0 1px 2px rgba(0,0,0,.4),0 12px 36px rgba(0,0,0,.35);
  }
}
:root[data-theme="dark"]{
  --ground:#14171c; --paper:#1c2128; --ink:#e9eaec; --ink-2:#a5adb8; --ink-3:#78818d;
  --hair:#2d3540; --hair-2:#242b34; --zebra:#1f252d;
  --brick:#c8705d; --brick-2:#d8836f; --gold:#c9a55a;
  --go:#4cc98a; --wait:#e0a33f; --link:#6fb2e8;
  --chip:#2a313b; --chip-ink:#bdb49f; --yes:#4cc98a; --no:#d98c80;
  --shadow:0 1px 2px rgba(0,0,0,.4),0 12px 36px rgba(0,0,0,.35);
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:"PT Serif",Georgia,"Times New Roman",serif;font-size:16px;line-height:1.6}
.wrap{max-width:1080px;margin:0 auto;padding:0 20px 70px}
h1,h2,h3,h4,.ui{font-family:"PT Sans","Helvetica Neue",Arial,sans-serif}

header.top{background:var(--paper);border-bottom:3px solid var(--brick);
  padding:38px 0 26px;margin-bottom:34px}
header.top .wrap{padding-bottom:0}
.kicker{font-family:"PT Sans",Arial,sans-serif;font-size:11.5px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--brick);font-weight:700;margin:0 0 10px}
h1{margin:0;font-size:36px;line-height:1.15;font-weight:700;text-wrap:balance;letter-spacing:-.01em}
.lede{margin:12px 0 0;font-size:17px;color:var(--ink-2);max-width:66ch}
.meta{margin:18px 0 0;font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;
  color:var(--ink-3);letter-spacing:.02em}
.strip{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:26px 0 0}
.strip figure{margin:0}
.strip img{width:100%;height:170px;object-fit:cover;display:block;background:var(--hair-2)}
.strip figcaption{font-family:"PT Sans",Arial,sans-serif;font-size:11px;color:var(--ink-3);
  padding-top:5px;letter-spacing:.03em}

.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:1px;
  background:var(--hair);border:1px solid var(--hair);margin:26px 0 0}
.stats div{background:var(--paper);padding:13px 15px}
.stats dt{font-family:"PT Sans",Arial,sans-serif;font-size:10.5px;letter-spacing:.09em;
  text-transform:uppercase;color:var(--ink-3);font-weight:700;margin:0 0 3px}
.stats dd{margin:0;font-size:21px;font-weight:700;font-variant-numeric:tabular-nums;
  font-family:"PT Sans",Arial,sans-serif}

h2{font-size:24px;margin:52px 0 6px;letter-spacing:-.01em;text-wrap:balance}
h2 .n{color:var(--brick);font-variant-numeric:tabular-nums}
.gsub{margin:0 0 6px;font-family:"PT Sans",Arial,sans-serif;font-size:12px;
  letter-spacing:.09em;text-transform:uppercase;color:var(--ink-3);font-weight:700}
.gnote{margin:0 0 22px;color:var(--ink-2);max-width:66ch}

table.sum{border-collapse:collapse;width:100%;background:var(--paper);
  font-family:"PT Sans",Arial,sans-serif;font-size:13.5px;box-shadow:var(--shadow)}
table.sum th{background:var(--ink);color:var(--paper);text-align:left;padding:10px 12px;
  font-size:10.5px;letter-spacing:.07em;text-transform:uppercase;white-space:nowrap}
table.sum td{padding:9px 12px;border-bottom:1px solid var(--hair-2);vertical-align:top}
table.sum tr:nth-child(even) td{background:var(--zebra)}
table.sum td.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
table.sum a{color:var(--link)}
.scroll{overflow-x:auto;margin:0 0 10px}
table.sum td.cx{white-space:nowrap;color:var(--ink-2)}
.role{font-size:11px;font-weight:700;letter-spacing:.04em;padding:2px 7px;border-radius:2px;
  background:var(--chip);color:var(--chip-ink);white-space:nowrap}
.role.ens{background:var(--brick);color:#fff}
.role.part{opacity:.85}
.tnote{font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;color:var(--ink-3);
  margin:8px 0 0;max-width:80ch}

.okn{background:var(--paper);box-shadow:var(--shadow);margin:0 0 30px;
  border-top:3px solid var(--gold);padding:24px 26px 22px}
.ohead{display:flex;gap:16px;align-items:flex-start;margin:0 0 18px}
.oid{font-family:"PT Sans",Arial,sans-serif;font-size:13px;font-weight:700;color:var(--paper);
  background:var(--brick);width:30px;height:30px;flex:none;display:flex;
  align-items:center;justify-content:center;border-radius:50%}
.ohead h3{margin:0;font-size:22px;line-height:1.25;text-wrap:balance}
.addr{margin:4px 0 0;color:var(--ink-2);font-size:14.5px}
.parent{margin:5px 0 0;font-size:13px;color:var(--ink-3);font-style:italic}
.idtag{margin-left:auto;font-family:"PT Sans",Arial,sans-serif;font-size:11px;
  color:var(--ink-3);letter-spacing:.06em;white-space:nowrap;padding-top:6px}

.gal{display:grid;grid-template-columns:repeat(auto-fit,minmax(215px,1fr));gap:10px;margin:0 0 20px}
.gal figure{margin:0}
.gal img{width:100%;height:210px;object-fit:cover;display:block;background:var(--hair-2)}

.facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(235px,1fr));
  gap:0 26px;margin:0 0 20px;font-family:"PT Sans",Arial,sans-serif;font-size:14px}
.facts .r{display:flex;justify-content:space-between;gap:12px;align-items:baseline;
  padding:7px 0;border-bottom:1px solid var(--hair-2)}
.facts dt{color:var(--ink-3);font-size:12.5px;white-space:nowrap}
.facts dd{margin:0;text-align:right;font-weight:600}
code{font-family:"PT Mono",ui-monospace,monospace;font-size:12.5px;
  background:var(--hair-2);padding:1px 5px;border-radius:2px}
.st{font-weight:700}.st.go{color:var(--go)}.st.wait{color:var(--wait)}

.maps{margin:0 0 20px}
.mgrid{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.mgrid figure{margin:0}
.mgrid img{width:100%;height:250px;object-fit:cover;display:block;border:1px solid var(--hair)}
.mgrid figcaption{font-family:"PT Sans",Arial,sans-serif;font-size:11px;color:var(--ink-3);
  padding-top:4px;letter-spacing:.04em}
.coord{margin:8px 0 0;font-family:"PT Mono",monospace;font-size:12px;color:var(--ink-3)}
.coord a{color:var(--link)}

.block{margin:0 0 18px}
h4{margin:0 0 8px;font-size:11px;letter-spacing:.1em;text-transform:uppercase;
  color:var(--ink-3);font-weight:700}
.sub{font-family:"PT Sans",Arial,sans-serif;font-size:13px;padding:7px 0;
  border-bottom:1px solid var(--hair-2);display:flex;flex-wrap:wrap;gap:4px 12px;align-items:baseline}
.sub span{color:var(--ink-2)}
.sub .vri{flex-basis:100%;font-size:12px;color:var(--ink-3)}
.chips{display:flex;flex-wrap:wrap;gap:6px}
.chip{font-family:"PT Sans",Arial,sans-serif;font-size:11.5px;padding:3px 9px;
  background:var(--chip);color:var(--chip-ink);border-radius:2px;font-weight:600}
.chip.yes{color:var(--yes)}.chip.no{color:var(--no)}
.hist p{margin:0 0 9px;color:var(--ink-2);font-size:15px;max-width:68ch}
.hist p:last-child{margin-bottom:0}

.links{display:flex;flex-wrap:wrap;gap:9px;margin-top:18px;padding-top:16px;
  border-top:1px solid var(--hair)}
.btn{font-family:"PT Sans",Arial,sans-serif;font-size:13px;font-weight:600;
  text-decoration:none;padding:8px 15px;background:var(--brick);color:#fff;border-radius:2px}
.btn.alt{background:transparent;color:var(--link);border:1px solid var(--hair);}
.btn:hover{opacity:.87}
a:focus-visible,.btn:focus-visible{outline:2px solid var(--link);outline-offset:2px}

.terms{background:var(--paper);box-shadow:var(--shadow);padding:26px 28px;
  border-left:4px solid var(--brick);margin:0 0 30px}
.terms h3{margin:0 0 4px;font-size:20px}
.terms .sect{margin:20px 0 0}
.terms ul{margin:6px 0 0;padding-left:20px;color:var(--ink-2)}
.terms li{margin:0 0 6px;max-width:70ch}
.rates{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;margin:14px 0 0}
.rate{border:1px solid var(--hair);padding:13px 15px}
.rate b{font-family:"PT Sans",Arial,sans-serif;font-size:27px;color:var(--brick);
  display:block;line-height:1.1}
.rate span{font-size:13.5px;color:var(--ink-2)}
.deadlines{font-family:"PT Sans",Arial,sans-serif;font-size:13.5px;margin:10px 0 0;
  border-collapse:collapse}
.deadlines td{padding:5px 16px 5px 0;border-bottom:1px solid var(--hair-2)}
.deadlines td:last-child{font-weight:700;white-space:nowrap}

footer.src{margin-top:44px;padding-top:18px;border-top:1px solid var(--hair);
  font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;color:var(--ink-3)}
footer.src a{color:var(--link)}
footer.src ol{padding-left:18px;margin:8px 0 0}
footer.src li{margin:0 0 5px;max-width:78ch}

@media screen and (max-width:720px){
  h1{font-size:27px} .mgrid{grid-template-columns:1fr}
  .okn{padding:20px 16px} .wrap{padding:0 16px 50px}
  .gal img{height:190px} .mgrid img{height:220px}
}
@page{size:A4;margin:13mm 12mm 15mm}
@media print{
  :root{--ground:#fff;--paper:#fff}
  html,body{background:#fff;font-size:9.6pt;line-height:1.5}
  body{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  .wrap{max-width:none;padding:0}

  /* обложка отдельной полосой */
  header.top{border-bottom:none;padding:0;margin:0;break-after:page;
    min-height:250mm;display:flex;flex-direction:column;justify-content:center}
  header.top .kicker{font-size:10pt;letter-spacing:.2em}
  header.top h1{font-size:30pt;line-height:1.1;margin-top:6mm}
  header.top .lede{font-size:12pt;margin-top:7mm;max-width:none}
  .stats{margin-top:12mm;border-color:#d8d3c7;grid-template-columns:repeat(5,1fr)}
  .stats div{border:none;padding:4mm 5mm}
  .stats dt{font-size:6.8pt;letter-spacing:.07em}
  .stats dd{font-size:14pt}
  .meta{margin-top:8mm;font-size:8.5pt}
  .strip{margin-top:9mm;gap:3mm}
  .strip img{height:42mm}
  .strip figcaption{font-size:7.6pt}

  h2{font-size:15pt;margin:0 0 2mm;break-after:avoid;break-before:page}
  h2:first-of-type{break-before:auto}
  .gsub{break-after:avoid;font-size:8pt}
  .gnote{font-size:9.5pt;margin-bottom:5mm;max-width:none;break-before:avoid}
  .tnote{font-size:8.6pt}

  table.sum{font-size:8.2pt;box-shadow:none;border:1px solid #d8d3c7;break-inside:auto}
  table.sum th{padding:2.4mm 2mm;font-size:7pt;background:#20242b !important;color:#fff !important}
  table.sum td{padding:2.1mm 2mm}
  table.sum tr{break-inside:avoid}

  .terms{box-shadow:none;border:1px solid #d8d3c7;border-left:3px solid var(--brick);
    padding:6mm 7mm;break-inside:avoid}
  .terms .sect{break-inside:avoid;margin-top:5mm}
  .rate b{font-size:19pt}
  .deadlines{font-size:9pt}

  /* карточка: не рвём внутренние блоки, саму карточку начинаем с новой полосы */
  .okn{box-shadow:none;border:none;border-top:2px solid var(--gold);
    padding:5mm 0 0;margin:0 0 6mm;break-before:page;break-inside:auto}
  .ohead{margin-bottom:4mm;break-after:avoid}
  .ohead h3{font-size:14.5pt}
  .addr{font-size:10pt}
  .oid{width:7mm;height:7mm;font-size:8.5pt}
  .idtag{font-size:8pt}

  .gal{gap:2.5mm;margin-bottom:4mm;break-inside:avoid;
    grid-template-columns:repeat(auto-fit,minmax(38mm,1fr))}
  .gal img{height:44mm}
  .facts{font-size:9pt;gap:0 8mm;margin-bottom:4mm;break-inside:avoid;
    grid-template-columns:1fr 1fr}
  .facts .r{padding:1.5mm 0}
  .facts dt{font-size:8.4pt}
  .maps{margin-bottom:4mm;break-inside:avoid}
  .mgrid{gap:2.5mm;grid-template-columns:1fr 1fr}
  .mgrid img{height:52mm}
  .mgrid figcaption{font-size:7.6pt}
  .coord{font-size:8pt}
  .block{margin-bottom:3.5mm;break-inside:avoid}
  h4{font-size:8pt;margin-bottom:1.5mm}
  .sub{font-size:8.8pt;padding:1.5mm 0}
  .sub .vri{font-size:8pt}
  .chip{font-size:8pt;padding:.7mm 2mm}
  .hist p{font-size:9.4pt;margin-bottom:2mm;max-width:none}
  .hist{break-inside:auto}

  .links{margin-top:4mm;padding-top:3mm}
  .btn{font-size:8.5pt;padding:1.6mm 3.5mm;background:var(--brick) !important;color:#fff !important}
  .btn.alt{background:#fff !important;color:var(--link) !important;border:1px solid #d8d3c7}

  footer.src{break-before:page;font-size:8.6pt;margin-top:0;padding-top:0;border-top:none}
  footer.src li{max-width:none;margin-bottom:2mm}
  a{text-decoration:none}
}
"""


ROLECLS = {"ансамбль": "ens", "в составе": "part", "отдельный": "solo"}

SHORT = {"Малый Казенный переулок, 5": "М. Казенный, 5",
         "Усадьба Щапово": "Щапово",
         "Усадьба Филимонки": "Филимонки"}


def summary_table():
    rows = []
    n = 0
    for gname, _, _, ids in GROUPS:
        gshort = SHORT[gname]
        for oid in ids:
            n += 1
            o = objs[str(oid)]
            st = o.get("status")
            rows.append(f"""<tr>
<td class="n">{n}</td>
<td><a href="#okn-{oid}">{esc(o.get('name'))}</a></td>
<td class="cx">{esc(gshort)}</td>
<td><span class="role {ROLECLS[role(oid)]}">{role(oid)}</span></td>
<td><span class="st {'go' if st=='READY' else 'wait'}">{esc(STATUS.get(st,st))}</span></td>
<td>{esc(fdate(o.get('auctionPeriod')) or '—')}</td>
<td class="n">{num(o.get('totalArea'),'')}</td>
<td>{esc(COND.get(o.get('condition'),'—'))}</td>
<td>{esc(SEC.get(o.get('securityCategory'),'—').replace(' значения',''))}</td>
<td class="n"><a href="{SITE}{SLUGS[str(oid)]}" target="_blank" rel="noopener">{oid}</a></td>
</tr>""")
    return f"""<div class="scroll"><table class="sum">
<thead><tr><th>№</th><th>Объект</th><th>Комплекс</th><th>Роль</th><th>Статус</th><th>Срок торгов</th>
<th>Площадь, м²</th><th>Состояние</th><th>Охрана</th><th>ID</th></tr></thead>
<tbody>{''.join(rows)}</tbody></table></div>"""


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
    <table class="deadlines">
      <tr><td>Проектная документация и экспертизы</td><td>18 мес.</td></tr>
      <tr><td>Реставрация, объект до 500 м²</td><td>30 мес.</td></tr>
      <tr><td>Реставрация, объект до 1 500 м²</td><td>36 мес.</td></tr>
      <tr><td>Реставрация, объект до 5 000 м²</td><td>48 мес.</td></tr>
      <tr><td>Реставрация, объект от 5 000 м²</td><td>60 мес.</td></tr>
      <tr><td>Эксплуатация после приёмки</td><td>5 лет</td></tr>
      <tr><td>Крайний срок акта приёмки работ</td><td>31.12.2030</td></tr>
    </table>
  </div>

  <div class="sect">
    <h4>Что обязателен сделать инвестор</h4>
    <ul>
      <li>Принять и исполнять <b>охранное обязательство</b> — оно опубликовано
        в карточке объекта и переходит к новому правообладателю вместе с объектом.</li>
      <li>Разработать проектную документацию на реставрацию и приспособление,
        пройти государственную историко-культурную экспертизу.</li>
      <li>Получить разрешение органа охраны — в Москве это Департамент культурного
        наследия — и вести работы силами лицензированного подрядчика.</li>
      <li>Сохранить <b>предмет охраны</b>: перечень элементов, которые менять нельзя,
        опубликован отдельным документом по каждому объекту.</li>
      <li>Отчитываться о ходе восстановления — этапы видны в «Дорожной карте»
        карточки объекта на портале.</li>
      <li>Уложиться в предельные сроки выше: их срыв лишает льготной ставки.</li>
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
    </ul>
  </div>
</div>
"""


def build():
    cards, n = [], 0
    blocks = []
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

    total = unique_area()          # без двойного счёта ансамблей
    naive = sum(o.get("totalArea") or 0 for o in objs.values())
    ready = sum(1 for o in objs.values() if o.get("status") == "READY")
    unsat = sum(1 for o in objs.values() if o.get("condition") == "UNSATISFACTORY")

    # по одному кадру на комплекс — обложке хватает трёх
    COVER = [(4097, "Малый Казенный, 5 · усадьба Нарышкиных"),
             (3529, "Щапово · каретный двор"),
             (3764, "Филимонки · усадьба, 1801 год")]
    cells = []
    for oid, cap in COVER:
        ph = photos(oid, 1)
        if ph:
            cells.append(f'<figure><img src="{ph[0]}" alt="{esc(cap)}">'
                         f'<figcaption>{esc(cap)}</figcaption></figure>')
    strip = f'<div class="strip">{"".join(cells)}</div>' if cells else ""

    naive_txt = num(naive, 'м²', 1)
    total_txt = num(total, 'м²', 1)
    html = f"""<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Наследие под инвестора</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=PT+Serif:ital,wght@0,400;0,700;1,400&family=PT+Sans:wght@400;700&family=PT+Mono&display=swap">
<style>{CSS}</style>
</head>
<body>
<header class="top"><div class="wrap">
  <p class="kicker">ДОМ.РФ · объекты культурного наследия · Москва</p>
  <h1>Пятнадцать памятников, которые ищут инвестора</h1>
  <p class="lede">Полный перечень московских ОКН со статусами «Подготовка к торгам»
  и «Решение отсутствует»: три усадебных комплекса и {num(total,'м²',1)} под реставрацию —
  от ограды в семь метров до больницы почти на 1 200.</p>
  <dl class="stats">
    <div><dt>Объектов</dt><dd>{len(objs)}</dd></div>
    <div><dt>Готовятся к торгам</dt><dd>{ready}</dd></div>
    <div><dt>Площадь без двойного счёта</dt><dd>{num(total,'м²',1)}</dd></div>
    <div><dt>В неудовл. состоянии</dt><dd>{unsat}</dd></div>
    <div><dt>Комплексов</dt><dd>{len(GROUPS)}</dd></div>
  </dl>
  {strip}
  <p class="meta">Источник: наследие.дом.рф, фильтр «Решение отсутствует, Подготовка к торгам» ·
  карты: Яндекс Карты · собрано 22.09.2026</p>
</div></header>

<div class="wrap">
  <h2>Сводная таблица</h2>
  {summary_table()}
  <p class="tnote"><b>Про площади.</b> Три строки перечня — ансамбли, и площадь каждого
  равна сумме его же строений, которые в перечне идут отдельными карточками. Поэтому
  складывать все пятнадцать строк нельзя: получится {naive_txt} вместо {total_txt}.
  В шапке — площадь без двойного счёта: три ансамбля плюс отдельно стоящее здание
  бывшей Александровской больницы.</p>

  <h2>Условия для инвестора</h2>
  {TERMS}
  {''.join(blocks)}

  <footer class="src">
    <b>Как собрано и что перепроверить</b>
    <ol>
      <li>Данные карточек — из каталога наследие.дом.рф по фильтру
        <code>status=NO_SOLUTION,READY</code>, регион «Город Москва»: ровно 15 объектов.</li>
      <li>Скриншоты расположения сняты по координатам из карточки объекта
        (map-widget Яндекс Карт, схема z=16 и спутник z=18). Метка — точка из карточки,
        а не геокодирование адреса.</li>
      <li>Фотографии — из галереи карточки объекта на портале.</li>
      <li>Кадастровые стоимости — те, что указаны на портале; это не рыночная оценка
        и не стартовая цена торгов. Цены объектов портал не публикует.</li>
      <li>Сроки торгов — плановый квартал из карточки, он двигается.
        Точную дату смотрите в лоте на земля.дом.рф, где он заведён.</li>
      <li>Условия кредитования — со страницы программы льготного кредитования
        наследие.дом.рф на 22.09.2026.</li>
      <li>Двойной счёт площадей проверен арифметикой: 929,6 = 376,5 + 273,2 + 272,3 + 7,6
        (Нарышкина), 792,2 = 493,7 + 122,6 + 115,6 + 22,5 + 37,8 (Щапово),
        797,2 = 589,3 + 207,9 (Филимонки). Связь «ансамбль — строение» портал проставил
        не везде: у ограды со сторожкой и у обоих объектов Филимонок поле родителя пустое,
        состав восстановлен по совпадению сумм и адресов.</li>
    </ol>
  </footer>
</div>
</body>
</html>"""
    return html


if __name__ == "__main__":
    h = build()
    p = f"{OUT}/okn-moscow.html"
    open(p, "w").write(h)
    print(f"{p}  {len(h)/1024/1024:.2f} МБ")
