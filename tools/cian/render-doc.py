#!/usr/bin/env python3
"""
lots.json -> HTML-досье.

    python3 tools/cian/render-doc.py --data lots.json --out document.html

Порядок карточек и строк таблицы — по возрастанию цены за квадратный метр.
"""
import argparse, html, json, statistics


def money(v):
    if v is None:
        return '—'
    return f'{int(round(v)):,}'.replace(',', ' ')


def mln(v):
    """В таблице полная цена в рублях даёт колонку в одиннадцать знаков и
    ломается переносом — сравнивать удобнее в миллионах."""
    if v is None:
        return '—'
    s = f'{v / 1e6:.1f}'.rstrip('0').rstrip('.')
    return s.replace('.', ',')


def area_fmt(v):
    if v is None:
        return '—'
    s = f'{v:.1f}'.rstrip('0').rstrip('.')
    return s.replace('.', ',')


def esc(s):
    return html.escape(s or '')


def paragraphs(text):
    """Описание отдаётся одним полем с переводами строк; пустые строки —
    границы абзацев. Списки на дефисах оставляем как есть, но переносим
    каждый пункт на свою строку."""
    if not text:
        return '<p class="muted">Описание в объявлении не заполнено.</p>'
    out = []
    for block in [b.strip() for b in text.split('\n\n') if b.strip()]:
        lines = [l.strip() for l in block.split('\n') if l.strip()]
        if len(lines) > 1 and sum(1 for l in lines if l.startswith(('-', '—', '•'))) >= 2:
            items = ''.join(f'<li>{esc(l.lstrip("-—• ").strip())}</li>' for l in lines
                            if l.startswith(('-', '—', '•')))
            lead = [l for l in lines if not l.startswith(('-', '—', '•'))]
            if lead:
                out.append(f'<p>{esc(" ".join(lead))}</p>')
            out.append(f'<ul>{items}</ul>')
        else:
            out.append(f'<p>{esc(" ".join(lines))}</p>')
    return '\n'.join(out)


def metro_chips(metro):
    if not metro:
        return ''
    bits = []
    for m in metro:
        t = f'{m["time"]} мин' if m.get('time') else ''
        mode = 'пешком' if m.get('walk') else 'на транспорте'
        bits.append(
            f'<span class="metro"><i style="background:{esc(m["color"])}"></i>'
            f'{esc(m["name"])}<em title="{mode}">{t}</em></span>')
    return f'<div class="metros">{"".join(bits)}</div>'


def specs(lot):
    rows = [('Площадь', f'{area_fmt(lot["area"])} м²'),
            ('Тип объекта', lot.get('kind')),
            ('Район', lot.get('district')),
            ('Округ', lot.get('okrug'))]
    if lot.get('floor'):
        rows.append(('Этаж', lot['floor']))
    if lot.get('floorsCount'):
        rows.append(('Этажей в здании', lot['floorsCount']))
    if lot.get('buildingType'):
        rows.append(('Тип здания', lot['buildingType']))
    if lot.get('buildYear'):
        rows.append(('Год постройки', lot['buildYear']))
    if lot.get('condition'):
        rows.append(('Состояние', lot['condition']))
    if lot.get('heating'):
        rows.append(('Отопление', lot['heating']))
    if lot.get('electricity'):
        rows.append(('Электричество', f'{lot["electricity"]} кВт'))
    if lot.get('vat'):
        rows.append(('Налог', lot['vat']))
    if lot.get('bargain'):
        rows.append(('Торг', 'возможен'))
    inc = lot.get('monthlyIncome') or {}
    if inc.get('income'):
        rows.append(('Арендный поток', f'{money(inc["income"])} ₽/мес'))
        if lot.get('priceTotal'):
            years = lot['priceTotal'] / (inc['income'] * 12)
            rows.append(('Окупаемость', f'{years:.1f} лет'.replace('.', ',')))
    cells = ''.join(
        f'<div class="spec"><dt>{esc(str(k))}</dt><dd>{esc(str(v))}</dd></div>'
        for k, v in rows if v not in (None, '', '—'))
    return f'<dl class="specs">{cells}</dl>'


def card(lot, rank, lo, hi):
    imgs = lot.get('images') or []
    main = imgs[0] if imgs else None
    rest = imgs[1:]
    ppm = lot.get('pricePerM2')
    # Положение лота в диапазоне ₽/м² — та же величина, по которой всё
    # отсортировано, показанная полосой.
    pos = 0 if not ppm or hi == lo else (ppm - lo) / (hi - lo)

    photo = (f'<figure class="hero-shot"><img src="{main}" alt="Фотография объекта: '
             f'{esc(lot["address"])}" loading="lazy"></figure>') if main else \
            '<figure class="hero-shot empty">В объявлении нет фотографий</figure>'

    mp = (f'<figure class="map"><img src="{lot["map"]}" alt="Расположение на карте: '
          f'{esc(lot["address"])}" loading="lazy">'
          f'<figcaption>Яндекс Карты · {lot["lat"]:.5f}, {lot["lng"]:.5f}</figcaption></figure>'
          ) if lot.get('map') else ''

    thumbs = ''
    if rest:
        thumbs = '<div class="thumbs">' + ''.join(
            f'<img src="{u}" alt="Фотография объекта {i + 2}: {esc(lot["address"])}" loading="lazy">'
            for i, u in enumerate(rest)) + '</div>'

    feats = ''
    if lot.get('features'):
        feats = '<ul class="tags">' + ''.join(
            f'<li>{esc(f)}</li>' for f in lot['features']) + '</ul>'

    return f'''
<article class="lot" id="lot-{lot['id']}">
  <header class="lot-head">
    <div class="rank"><span>{rank}</span></div>
    <div class="titles">
      <h2>{esc(lot['address'])}</h2>
      <p class="kind">{esc(lot.get('kind') or '')}{
        ' · ' + esc(lot['district']) if lot.get('district') else ''}</p>
      {metro_chips(lot.get('metro'))}
    </div>
    <div class="figures">
      <div class="ppm"><b>{money(ppm)}</b><span>₽/м²</span></div>
      <div class="total">{money(lot.get('priceTotal'))} ₽ · {area_fmt(lot['area'])} м²</div>
      <div class="rangebar" role="img"
           aria-label="Цена за метр относительно остальных объектов подборки">
        <i style="left:{pos * 100:.1f}%"></i>
      </div>
    </div>
  </header>
  <div class="visuals">{photo}{mp}</div>
  {thumbs}
  <div class="detail">
    <div class="left">{specs(lot)}{feats}</div>
    <div class="right">
      <h3>Описание из объявления</h3>
      {paragraphs(lot.get('description'))}
      <p class="src"><a href="{esc(lot['url'])}" target="_blank"
         rel="noopener">Объявление на Циан № {lot['id']}</a></p>
    </div>
  </div>
</article>'''


def table(lots):
    rows = []
    for i, l in enumerate(lots, 1):
        inc = (l.get('monthlyIncome') or {}).get('income')
        rows.append(f'''<tr>
      <td class="n">{i}</td>
      <td class="addr"><a href="#lot-{l['id']}">{esc(l['address'])}</a>
        <span>{esc(l.get('district') or '')}</span></td>
      <td>{esc(l.get('kind') or '')}</td>
      <td class="num">{area_fmt(l['area'])}</td>
      <td class="num">{mln(l.get('priceTotal'))}</td>
      <td class="num strong">{money(l.get('pricePerM2'))}</td>
      <td class="num">{money(inc) if inc else '—'}</td>
    </tr>''')
    return '\n'.join(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--data', required=True)
    ap.add_argument('--out', required=True)
    args = ap.parse_args()

    lots = json.load(open(args.data, encoding='utf-8'))
    lots.sort(key=lambda x: x['pricePerM2'] or 0)

    ppms = [l['pricePerM2'] for l in lots if l.get('pricePerM2')]
    lo, hi = min(ppms), max(ppms)
    med = statistics.median(ppms)
    total_area = sum(l['area'] for l in lots if l.get('area'))
    total_sum = sum(l['priceTotal'] for l in lots if l.get('priceTotal'))
    districts = sorted({l['district'] for l in lots if l.get('district')})
    photos = sum(len(l.get('images') or []) for l in lots)

    cards = '\n'.join(card(l, i, lo, hi) for i, l in enumerate(lots, 1))

    doc = f'''<title>Двадцать лотов в центре</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,400;7..72,600;7..72,700&family=Golos+Text:wght@400;500;600;700&display=swap">
<style>
:root {{
  --paper:#FAFBFC; --card:#FFFFFF; --ink:#16202B; --ink-soft:#3B4855;
  --muted:#6B7885; --line:#E3E7EC; --line-soft:#EFF2F5;
  --accent:#1F5F5B; --accent-soft:#E8F0EF; --accent-ink:#164945;
  --shadow:0 1px 2px rgba(22,32,43,.05), 0 8px 24px -12px rgba(22,32,43,.16);
  --serif:"Literata",Georgia,"Times New Roman",serif;
  --sans:"Golos Text","Helvetica Neue",Arial,sans-serif;
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    --paper:#11171E; --card:#171F28; --ink:#E7ECF1; --ink-soft:#C2CCD6;
    --muted:#8C9AA8; --line:#2A3641; --line-soft:#212B35;
    --accent:#6FBDB4; --accent-soft:#1B2E2D; --accent-ink:#9AD6CE;
    --shadow:0 1px 2px rgba(0,0,0,.4), 0 10px 28px -14px rgba(0,0,0,.7);
  }}
}}
:root[data-theme="dark"] {{
  --paper:#11171E; --card:#171F28; --ink:#E7ECF1; --ink-soft:#C2CCD6;
  --muted:#8C9AA8; --line:#2A3641; --line-soft:#212B35;
  --accent:#6FBDB4; --accent-soft:#1B2E2D; --accent-ink:#9AD6CE;
  --shadow:0 1px 2px rgba(0,0,0,.4), 0 10px 28px -14px rgba(0,0,0,.7);
}}

* {{ box-sizing:border-box; }}
body {{
  margin:0; background:var(--paper); color:var(--ink);
  font-family:var(--sans); font-size:15px; line-height:1.6;
  -webkit-font-smoothing:antialiased;
}}
.wrap {{ max-width:1120px; margin:0 auto; padding:0 24px 96px; }}
a {{ color:var(--accent-ink); }}
h1,h2,h3 {{ font-family:var(--serif); text-wrap:balance; margin:0; }}
.num, .ppm b, .total, td.num {{ font-variant-numeric:tabular-nums; }}
/* Разряды разделены обычным пробелом, поэтому длинные суммы рвутся
   переносом посреди числа — запрещаем перенос там, где стоят цифры. */
td.num, th.num, .ppm b, .total, .stats b {{ white-space:nowrap; }}

/* ---------- шапка ---------- */
.cover {{ padding:72px 0 40px; border-bottom:1px solid var(--line); }}
.eyebrow {{
  font-size:12px; letter-spacing:.14em; text-transform:uppercase;
  color:var(--accent-ink); font-weight:600; margin:0 0 18px;
}}
.cover h1 {{ font-size:clamp(34px,5.2vw,56px); line-height:1.08; font-weight:700; letter-spacing:-.02em; }}
.cover .lede {{
  margin:20px 0 0; max-width:62ch; color:var(--ink-soft); font-size:17px;
}}
.stats {{
  display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr));
  gap:1px; margin-top:40px; background:var(--line); border:1px solid var(--line);
}}
.stats div {{ background:var(--paper); padding:16px 18px; }}
.stats b {{
  display:block; font-family:var(--serif); font-size:24px; font-weight:600;
  font-variant-numeric:tabular-nums; letter-spacing:-.01em;
}}
.stats span {{ font-size:12.5px; color:var(--muted); }}
.note {{
  margin:28px 0 0; padding:14px 16px; background:var(--accent-soft);
  border-left:2px solid var(--accent); font-size:13.5px; color:var(--ink-soft);
  max-width:76ch;
}}

/* ---------- карточка лота ---------- */
.lot {{
  margin-top:56px; background:var(--card); border:1px solid var(--line);
  box-shadow:var(--shadow); padding:26px;
}}
.lot-head {{
  display:grid; grid-template-columns:auto 1fr auto; gap:20px;
  align-items:start; padding-bottom:22px; border-bottom:1px solid var(--line-soft);
}}
.rank span {{
  display:grid; place-items:center; width:38px; height:38px;
  border:1px solid var(--accent); color:var(--accent-ink);
  font-family:var(--serif); font-size:16px; font-weight:600;
  font-variant-numeric:tabular-nums;
}}
.titles h2 {{ font-size:26px; font-weight:600; letter-spacing:-.015em; }}
.kind {{ margin:5px 0 0; color:var(--muted); font-size:13.5px; }}
.metros {{ display:flex; flex-wrap:wrap; gap:6px 14px; margin-top:12px; }}
.metro {{ display:inline-flex; align-items:center; gap:6px; font-size:13px; color:var(--ink-soft); }}
.metro i {{ width:9px; height:9px; border-radius:50%; flex:none; }}
.metro em {{ font-style:normal; color:var(--muted); font-size:12.5px; }}
.figures {{ text-align:right; min-width:180px; }}
.ppm {{ display:flex; align-items:baseline; gap:5px; justify-content:flex-end; }}
.ppm b {{ font-family:var(--serif); font-size:30px; font-weight:700; letter-spacing:-.02em; }}
.ppm span {{ font-size:13px; color:var(--muted); }}
.total {{ font-size:13.5px; color:var(--ink-soft); margin-top:3px; }}
.rangebar {{
  position:relative; height:3px; background:var(--line); margin-top:14px;
}}
.rangebar i {{
  position:absolute; top:-3px; width:3px; height:9px; background:var(--accent);
  transform:translateX(-1px);
}}

.visuals {{ display:grid; grid-template-columns:1.62fr 1fr; gap:14px; margin-top:22px; }}
.hero-shot, .map {{ margin:0; position:relative; }}
.hero-shot img, .map img {{
  display:block; width:100%; height:340px; object-fit:cover;
  background:var(--line-soft);
}}
.hero-shot.empty {{
  display:grid; place-items:center; height:340px; background:var(--line-soft);
  color:var(--muted); font-size:13.5px;
}}
.map figcaption {{
  position:absolute; left:0; bottom:0; right:0; padding:6px 9px;
  background:rgba(22,32,43,.72); color:#fff; font-size:11.5px;
  font-variant-numeric:tabular-nums;
}}
.thumbs {{
  display:grid; grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
  gap:10px; margin-top:10px;
}}
.thumbs img {{
  display:block; width:100%; height:104px; object-fit:cover;
  background:var(--line-soft);
}}

.detail {{ display:grid; grid-template-columns:minmax(240px,1fr) 1.7fr; gap:34px; margin-top:26px; }}
.specs {{ margin:0; display:grid; gap:0; border-top:1px solid var(--line-soft); }}
.spec {{
  display:flex; justify-content:space-between; gap:16px; padding:8px 0;
  border-bottom:1px solid var(--line-soft); font-size:13.5px;
}}
.spec dt {{ color:var(--muted); }}
.spec dd {{ margin:0; text-align:right; font-weight:500; font-variant-numeric:tabular-nums; }}
.tags {{ list-style:none; display:flex; flex-wrap:wrap; gap:6px; margin:16px 0 0; padding:0; }}
.tags li {{
  font-size:12px; padding:3px 9px; background:var(--accent-soft);
  color:var(--accent-ink); border:1px solid var(--line);
}}
.detail h3 {{
  font-size:12px; letter-spacing:.13em; text-transform:uppercase;
  color:var(--muted); font-family:var(--sans); font-weight:600; margin-bottom:10px;
}}
.detail .right p {{ margin:0 0 11px; max-width:66ch; color:var(--ink-soft); }}
.detail .right ul {{ margin:0 0 12px; padding-left:18px; color:var(--ink-soft); }}
.detail .right li {{ margin-bottom:4px; }}
.muted {{ color:var(--muted); }}
.src {{ font-size:13px; }}

/* ---------- сводная таблица ---------- */
.summary {{ margin-top:80px; padding-top:44px; border-top:2px solid var(--ink); }}
.summary h2 {{ font-size:30px; font-weight:700; letter-spacing:-.02em; }}
.summary p.sub {{ color:var(--muted); margin:8px 0 24px; font-size:14px; }}
.tablewrap {{ overflow-x:auto; border:1px solid var(--line); }}
table {{ border-collapse:collapse; width:100%; min-width:760px; background:var(--card); }}
th, td {{ padding:11px 14px; text-align:left; border-bottom:1px solid var(--line-soft); }}
thead th {{
  font-size:11.5px; letter-spacing:.09em; text-transform:uppercase;
  color:var(--muted); font-weight:600; background:var(--paper);
  border-bottom:1px solid var(--line); position:sticky; top:0;
}}
th.num, td.num {{ text-align:right; }}
td.n {{ color:var(--muted); width:34px; font-variant-numeric:tabular-nums; }}
td.addr a {{ font-weight:500; text-decoration:none; }}
td.addr a:hover {{ text-decoration:underline; }}
td.addr span {{ display:block; font-size:12px; color:var(--muted); }}
td.strong {{ font-weight:700; }}
tbody tr:last-child td {{ border-bottom:none; }}
tbody tr:hover td {{ background:var(--accent-soft); }}

footer {{ margin-top:56px; padding-top:22px; border-top:1px solid var(--line);
  font-size:12.5px; color:var(--muted); }}

a:focus-visible, img:focus-visible {{ outline:2px solid var(--accent); outline-offset:2px; }}

@media (max-width:860px) {{
  .lot-head {{ grid-template-columns:auto 1fr; }}
  .figures {{ grid-column:1/-1; text-align:left; }}
  .ppm {{ justify-content:flex-start; }}
  .visuals {{ grid-template-columns:1fr; }}
  .hero-shot img, .map img {{ height:260px; }}
  .detail {{ grid-template-columns:1fr; gap:24px; }}
}}
@media print {{
  body {{ background:#fff; }}
  .lot {{ break-inside:avoid; box-shadow:none; }}
  .thumbs img {{ height:88px; }}
}}
@media (prefers-reduced-motion:reduce) {{ * {{ animation:none!important; transition:none!important; }} }}
</style>

<div class="wrap">
<header class="cover">
  <p class="eyebrow">Коммерческая недвижимость · Москва</p>
  <h1>Двадцать лотов в центре</h1>
  <p class="lede">Подборка объектов, выставленных на продажу на Циан: отдельно
  стоящие здания, помещения свободного назначения, торговые площади и готовый
  бизнес. Карточки и итоговая таблица упорядочены по возрастанию цены за
  квадратный метр — от {money(lo)} до {money(hi)} ₽/м².</p>
  <div class="stats">
    <div><b>{len(lots)}</b><span>объектов</span></div>
    <div><b>{money(round(total_area))}</b><span>м² суммарно</span></div>
    <div><b>{(f"{total_sum / 1e9:.2f}").replace(".", ",")}</b><span>млрд ₽ суммарно</span></div>
    <div><b>{money(med)}</b><span>₽/м² медиана</span></div>
    <div><b>{len(districts)}</b><span>районов</span></div>
  </div>
  <p class="note">Данные, фотографии и координаты получены из объявлений Циан.
  Карты — Яндекс Карты по координатам объявления. В исходном списке из
  21 ссылки объявление № 331069037 встречалось дважды, поэтому объектов
  двадцать. Фотографий в документе {photos} — до шести на объект;
  полные галереи и актуальные цены остаются на Циан.</p>
</header>

{cards}

<section class="summary">
  <h2>Сводная таблица</h2>
  <p class="sub">По возрастанию цены за квадратный метр. Адрес ведёт к карточке объекта.</p>
  <div class="tablewrap">
    <table>
      <thead><tr>
        <th></th><th>Адрес</th><th>Тип</th>
        <th class="num">Площадь, м²</th><th class="num">Цена, млн ₽</th>
        <th class="num">₽/м²</th><th class="num">Аренда, ₽/мес</th>
      </tr></thead>
      <tbody>
{table(lots)}
      </tbody>
    </table>
  </div>
</section>

<footer>Источник: cian.ru · картография: Яндекс Карты · подборка собрана {len(lots)} объектов</footer>
</div>
'''
    with open(args.out, 'w', encoding='utf-8') as f:
        f.write(doc)
    mb = len(doc.encode()) / 1e6
    print(f'{args.out} — {mb:.1f} МБ, {len(lots)} лотов, {photos} фото, '
          f'₽/м² от {money(lo)} до {money(hi)}, медиана {money(med)}')


if __name__ == '__main__':
    main()
