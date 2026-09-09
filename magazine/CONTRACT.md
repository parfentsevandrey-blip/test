# Контракт сборки журнала

Всё лежит в `magazine/`. Модули связаны только через файлы, описанные здесь.
Меняя формат — правь этот файл первым.

## Дерево

```
magazine/
  CONTRACT.md          — этот файл
  requirements.txt     — зависимости pip
  setup.sh             — установка окружения на чистой машине
  build_kf.py          — issue.json + sources.json -> magazine.html
  make_maps.py         — geojson -> build/map_*.svg
  render_kf.py         — magazine.html -> PDF + shots/pNN.png
  qa_kf.py             — заполнение полос, переполнение
  qa_overlap.py        — перекрытия текста нижними блоками
  extract_layout.py    — magazine.html -> build/layout.json + build/bg/bg-NN.png
  bg_to_jpg.py         — PNG-фоны -> JPEG для Word
  build_editable.py    — layout.json + фоны -> .docx
  harvest/dater.py     — список URL -> дата публикации со страницы
  harvest/arts.py      — список URL -> дата, заголовок, текст
  fonts/               — SourceSerif4 и SourceSans3: woff2 для CSS, ttf для Word
  data/                — nl_gem.geojson (342), nl_prov.geojson (12), поле statnaam
  photos/              — снимки + credits.json
  content/issue.json   — содержание выпуска
  content/sources.json — приложение «Источники»
  build/               — всё, что генерируется; в git не хранится
```

## HTML, который выдаёт build_kf.py

Один файл, самодостаточный: CSS внутри `<style>`, шрифты подключены как
`@font-face` с относительными путями на `fonts/`. Никаких внешних запросов.

Полоса — ровно один `<div class="page">`, размер A4 (210×297 мм), без полей
страницы; в браузере при `device_scale_factor=2` это 793.7×1122.5 CSS-пикселя.
Всё остальное позиционируется внутри полосы.

Обязательные классы, на которые опираются render/qa/extract:

| Класс | Смысл |
|---|---|
| `.page` | полоса; ровно одна на страницу PDF |
| `.pad` | текстовый блок с полями 20 мм по бокам |
| `.pad.foot` | блок, прижатый к низу полосы (`position:absolute;bottom`) |
| `.body` | тело статьи; на новостных полосах `columns:2;column-gap:11mm` |
| `.folio` | колонцифра |
| `.rh` | колонтитул |

Правила, которые нельзя нарушать:

- Текст не должен опускаться ниже 1050 CSS-пикселей от верха полосы —
  дальше начинается колонцифра. `qa_kf.py` это проверяет.
- `.pad.foot` не должен накрывать текст: `qa_overlap.py` это проверяет.
- Каждый `<img>` — с относительным путём; PDF рендерится из `file://`.

## Пагинация

`build_kf.py` не полагается на естественный перенос: он измеряет каждый блок в
Chromium на ширине колонки и раскладывает сам.

Ловушка, стоившая сессии: пробный документ для измерения **обязан** начинаться с
`<!DOCTYPE html>`. Без него Chromium уходит в quirks mode, теряет строчный
strut и занижает высоты примерно на 20%.

Бюджет полосы — 2 × 930 пикселей (две колонки, колонцифра свободна).

## issue.json

```
nameplate, strapline, issue_line, running_head, prepared_by
cover      {photo, photo_caption, main{kicker,title,sub,page}, secondary[{topic,text,page}]}
contents_title, contents[{page,rubric,title,desc}]
numbers_title, numbers[{value,text}]
calendar_title, watchlist[{date,text}]
editorial  {rubric, deck, paras[], signature}
feature    {rubric,title,deck,photo,photo_caption,chart,paras[6],quote,quote_attrib,
            quote_after,sources_line,stats[{value,label}],context_title,context_text,
            closing_photo,closing_caption}
sections[] {rubric,title,deck,chart,page_stat{value,label},quote,quote_attrib,
            stories[{kicker,headline,source,paras[]}],
            briefs_title, briefs[{lead,text}],
            map_page{title,deck,src,caption,stats[[value,label]]}}
analysis   {rubric,title,deck,points[{lead,text}],names_title,names[{role,name,text}]}
glossary_title, glossary_note, glossary[{term,text}]
sources_title, sources_note, photo_credits_title, photo_credits_note
methodology_title, methodology, methodology_split[2], imprint[3]
fullnote_title, fullnote_text
```

`feature.paras` — ровно шесть абзацев: три на первой полосе темы, три на второй.
`contents[].rubric` должен совпадать с ключами, которые build_kf кладёт в
`PAGE_OF`, иначе номер полосы возьмётся из литерала и разъедется.

## sources.json

```
{"sectors":[{"sectorNameRu":"Жильё","sourcesRu":[{"source","date","url","title"}]}]}
```

`date` — строго `ДД.ММ.ГГГГ`, и строго внутри окна выпуска. Записи без даты не
допускаются: приложение «Источники» — это доказательство проверки.

## Карты

`make_maps.py` рисует по реальной геометрии, без библиотек: равнопромежуточная
проекция с поправкой на косинус широты. Никаких примитивных диаграмм — редакция
их запретила; допустимы только карты и наборы цифр.

Подпись у точки разводится по вертикали функцией `spread()`, чтобы соседние не
наезжали. Шрифты внутри SVG — base64 woff2, иначе они не подхватятся при
загрузке через `<img>`.

Функция `nl_map(name, pins)` принимает точки вида
`{gem, town, head, sub, side:'l'|'r', r, kind:'deal'|'rule', dlon, dlat}`,
где `gem` — `statnaam` из `nl_gem.geojson`.

## Word

`extract_layout.py` снимает координаты каждого текстового фрагмента и отдельно
рендерит фоны без текста. `build_editable.py` кладёт фон картинкой и поверх —
настоящие текстовые рамки, поэтому текст в Word остаётся редактируемым.

Две ловушки: подписи внутри `<svg>` пропускаются (`el.closest('svg')`), иначе
они задвоятся; фрагмент уже, чем колонка, сохраняет собственную рамку, иначе
уезжает к полю.

Фоны Word ждёт в JPEG (`bg-NN.jpg`), браузер отдаёт PNG — между ними
`bg_to_jpg.py`.
