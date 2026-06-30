# 🇳🇱 Еженедельная аналитика рынка недвижимости Нидерландов

ИИ-агент раз в неделю собирает аналитику по рынку недвижимости Нидерландов в три
сегмента, а детерминированный Python-конвейер превращает её в **профессиональный
файл Word**, **HTML-превью** и **тело письма** — и следит за тем, чтобы материалы
не повторялись от недели к неделе.

Сегменты:
- 🏠 **Жилая недвижимость** — законы, новости, тренды, статистика
- 🏪 **Коммерция / стрит-ритейл** — то же самое
- 🏭 **Индустриал** — склады, промзоны, индустриальные и бизнес-парки

**Разделение ответственности.** «Мозг» (исследование, редполитика, источники)
описан в `agent_instructions.md`, `sources.md`, `docs/*.json` — он готов и здесь не
меняется. Этот README про **инженерную упаковку**: как из готового JSON получить
отчёт **одной командой**, надёжно, воспроизводимо и с проверкой данных.

---

## ⚡ Быстрый старт (одной командой)

```bash
make install                       # поставить зависимости (закреплённые версии)
make all WEEK=2026-06-30           # валидация → графики → Word → превью → письмо → история → QA
```

`make all` под капотом запускает единый оркестратор:

```bash
python3 run_report.py --data data/week_2026-06-30.json
```

Он сам выводит **QA-сводку** (сколько пунктов по сегментам, графиков `N/M`, дублей,
какие файлы созданы и их размеры) и возвращает осмысленный **код выхода**.

Артефакты появятся в `reports/`:

| Файл | Что это |
|---|---|
| `2026-06-30_dutch_realestate_RU.docx` | Word-отчёт (графики вшиты в байты, файл переносим) |
| `preview_2026-06-30.html` | HTML-превью (для браузера/телефона) |
| `email_2026-06-30.html` / `.txt` | тело письма (HTML с инлайн-стилями + плейн-текст) |
| `assets/2026-06-30/*.png` | графики недели (детерминированный per-week каталог) |

---

## 🏗 Пайплайн

```
week_YYYY-MM-DD.json  (данные недели, единый источник правды)
        │
        ▼
  run_report.py  ── оркестратор «одной командой» (вызывает модули in-process)
        │
        ├─ 1. загрузка данных        _load_data()         понятные ошибки на битом JSON
        ├─ 2. валидация контракта    validate.validate()  errors/warnings, в --strict блокирует
        ├─ 3. графики (один раз)      charts.render_charts → reports/assets/<week>/
        ├─ 4. Word                    generate_report.build_report → .docx (PNG переиспользуются)
        ├─ 5. HTML-превью             preview_html.render  (относительные ссылки на PNG)
        ├─ 6. тело письма             build_email.build_html / build_text
        ├─ 7. история (антиповтор)    generate_report.update_history
        └─ 8. QA-сводка               print_qa_summary  (для глаза оператора и для cron)
```

Графики рендерятся **один раз** в per-week каталог и переиспользуются и для Word,
и для превью — без двойного рендера и без коллизий имён между неделями.

### Структура проекта

```
dutch-realestate-agent/
├── run_report.py            ← ОРКЕСТРАТОР: весь конвейер одной командой
├── validate.py              ← валидатор контракта данных (errors/warnings)
├── generate_report.py       ← рендер Word из JSON (+ антиповтор, встраивание графиков)
├── charts.py                ← графики/KPI (matplotlib): bar, hbar, grouped_bar,
│                              before_after, stacked_bar, line, donut, kpi
├── preview_html.py          ← HTML-превью отчёта
├── build_email.py           ← сборка тела письма (HTML + текст)
├── render_sources.py        ← рендер sources.md из таксономии
├── Makefile                 ← install / validate / report / preview / email / all / clean
├── requirements.txt         ← закреплённые версии (python-docx, matplotlib)
├── schema.example.json      ← человекочитаемый образец входного JSON (контракт)
├── agent_instructions.md    ← «мозг» агента: что искать, как, в каком формате
├── sources.md               ← источники институционального уровня
├── RUNBOOK.md               ← чек-лист еженедельного запуска для оператора
├── docs/
│   ├── charts_catalog.json  ← каталог типов графиков (когда что строить)
│   ├── design_spec.json     ← принципы оформления
│   └── voice_design.json    ← редполитика / голос
├── data/
│   ├── history.json         ← реестр прошлых материалов (антиповтор) — КОММИТИТЬ
│   ├── sources.json / sources_taxonomy.json
│   └── week_YYYY-MM-DD.json ← данные конкретной недели
└── reports/                 ← артефакты рендера (генерируются, не коммитятся)
    ├── <week>_dutch_realestate_RU.docx
    ├── preview_<week>.html
    ├── email_<week>.html / .txt
    └── assets/<week>/*.png
```

---

## 📐 Контракт данных

Образец — `schema.example.json` (источник истины по форме). Ключевые поля и
допустимые значения, которые проверяет `validate.py`:

**Верхний уровень (обязательны):** `report_date`, `week_start`, `week_end`
(строго `YYYY-MM-DD`, `week_start ≤ week_end`), `headline`, `executive_summary`,
`segments` (непустой список). Опциональны: `key_takeaways` (рекоменд. 3–5),
`outlook`, `glossary`, `sources`, `charts`, `language`.

**Сегмент:** `id ∈ {residential, commercial, industrial}`, `title`, `icon`,
`subsections` (**dict** по ключам), `conclusion`, `watch`.

**Подразделы** (`subsections`, ключи): `laws`, `news`, `trends`, `stats`.
Каждый — список пунктов. Пункт: `text`, `value`, `impact`,
`direction ∈ {up, down, neutral}`, `source`, `url` (`http(s)://…`), `date`
(`YYYY-MM-DD`, в окне `week_start..week_end`). У `stats` ключевое поле — `value`.

> Форма `subsections` — **только dict**. `preview_html.py` и `build_email.py`
> поддерживают только dict-форму; валидатор отвергает list, чтобы Word и
> письмо/превью не расходились молча.

**Графики** (`charts[]`): `id` (уникальный — становится именем PNG), `segment ∈
{residential, commercial, industrial, overview}`, `type ∈ {bar, hbar,
grouped_bar, before_after, stacked_bar, line, donut, kpi}`, `title`, `caption`,
`unit`, `labels`, `series:[{name, values}]`; для `kpi` — `kpi_items:[{label,
value, delta, direction}]`. Для `bar/hbar/grouped_bar/stacked_bar/donut` длина
`labels` должна совпадать с длиной `series.values`.

**Теги влияния:** ▲ возможность/рост · ▼ риск/снижение · ◆ структурный сдвиг.

---

## ✅ Валидация и QA

**Проверка данных перед рендером** (отдельно):

```bash
make validate WEEK=2026-06-30
# или напрямую:
python3 validate.py --data data/week_2026-06-30.json --history data/history.json
python3 validate.py --data data/week_2026-06-30.json --strict   # warnings → ошибки
python3 validate.py --data data/week_2026-06-30.json --json      # машиночитаемый отчёт
```

Что ловит валидатор: отсутствие/тип обязательных полей; даты не-ISO и **вне окна
недели**; enum (`direction`, `segment.id`, `chart.type`, ключи подразделов);
форму `subsections` (только dict); отсутствие `source`/`url`/`value`; рассинхрон
длин `labels`/`values`; дубли `chart.id`; внутринедельные дубли и совпадения с
`history.json` (read-only — историю валидатор не пишет); мягкие лимиты (≤5 пунктов
на подраздел, 3–5 `key_takeaways`).

`ERROR` блокирует рендер, `WARNING` печатается и не блокирует (в `--strict` —
блокирует). Оркестратор вызывает валидацию автоматически на шаге 2.

**QA-сводка** печатается в конце каждого прогона `run_report.py`: период, число
материалов по сегментам/подразделам, графики `N/M`, дубли, список артефактов с
путями и размерами, число предупреждений валидации. Это и есть «проверяемость»
прогона — в том числе в авто-режиме по cron, где на экран никто не смотрит.

### Коды выхода

| Код | `run_report.py` | `validate.py` |
|---|---|---|
| `0` | успех | данные валидны (есть/нет warnings) |
| `1` | ошибка валидации (errors; в `--strict` — и warnings) | есть errors |
| `2` | фатальная ошибка (битый JSON, не собрался Word, ошибка записи) | `--strict` + warnings |
| `3` | частичный успех (graceful degradation: часть графиков/превью/письмо не собрались) | — |

### Флаги `run_report.py`

| Флаг | Действие |
|---|---|
| `--data PATH` | (обязательный) JSON недели |
| `--out DIR` | каталог артефактов (по умолчанию `reports/`) |
| `--history PATH` | файл истории (по умолчанию `data/history.json` рядом с `--data`) |
| `--strict` | warnings валидации и частичный рендер графиков становятся блокирующими |
| `--skip-validate` | пропустить шаг валидации |
| `--no-history-update` | не дописывать историю (только проверка дублей) |
| `--no-charts` | не рендерить графики (быстрый/текстовый прогон) |
| `--file-link URL` | ссылка на `.docx` (Drive) для тела письма |
| `-q` / `-v` | меньше / больше логов |

---

## 📨 Доставка на почту

`run_report.py` готовит тело письма (`email_<week>.html` + `.txt`) — полный отчёт
прямо в письме (читается на телефоне), плюс ссылка на файл `.docx`. Чтобы вшить
ссылку на Drive:

```bash
python3 run_report.py --data data/week_2026-06-30.json \
    --file-link "https://drive.google.com/…"
```

Далее `.docx` загружается в Google Drive (для ссылки), и письмо создаётся в Gmail
на `parfentsev.andrey@gmail.com`.

> ⚠️ **Нужна авторизация коннекторов.** Отправка письма и загрузка в Drive
> работают только когда коннекторы **Gmail** и **Google Drive** авторизованы
> (claude.ai → настройки коннекторов). Если токен истёк, отчёт всё равно
> соберётся в `reports/`, но письмо не уйдёт, пока коннекторы не переподключить.

---

## ♻️ Воспроизводимость

- **Закреплённые версии.** `requirements.txt` фиксирует `python-docx==1.2.0` и
  `matplotlib==3.11.0` (проверенные в этом окружении), чтобы рендер графиков и
  Word не «поплыл» при мажорном апгрейде.
- **Python ≥ 3.10** (разработка/прогон на 3.11).
- **Per-week графики.** PNG лежат в `reports/assets/<week_end>/` — старый отчёт
  можно пересобрать, имена недель не конфликтуют.
- **`data/history.json` нужно коммитить.** Это не артефакт, а состояние
  антиповтора: окружение веба эфемерно — что не закоммичено, теряется.
- **Идемпотентность.** Повторный прогон той же недели подсветит её как дубли в
  истории (`week_start уже в history.weeks`). Для чистой пересборки без записи
  истории используйте `--no-history-update`.

---

## ⛔ Ограничения и предпосылки

- **matplotlib опционален для текста, обязателен для графиков.** Без него Word
  соберётся без диаграмм; в QA-сводке это видно как `Графики: 0/N (нет
  matplotlib)`, а в `--strict` даёт ненулевой код выхода.
- **Шрифт графиков — DejaVu Sans** (поддерживает кириллицу, `€`, `²`); эмодзи в
  нём отсутствуют.
- **Коннекторы Gmail/Drive требуют авторизации** (см. «Доставка»).
- **Вложения в черновики Gmail не поддерживаются** — `.docx` идёт ссылкой на
  Drive; копия всегда лежит в `reports/`.
- **git-корень общий** (`/home/user/test` — мультипроект); `.gitignore` подпроекта
  игнорирует артефакты рендера, но не `data/`.

---

## 🛠 Настройка

- **Язык** — `language` в JSON и тексты в `generate_report.py` (по умолчанию ru).
- **Цвета/оформление** — палитра вверху `generate_report.py` и `charts.py`,
  принципы в `docs/design_spec.json`.
- **Источники** — `sources.md` / `data/sources_taxonomy.json`.
- **Глубина/объём** — `agent_instructions.md`.

См. также **[RUNBOOK.md](RUNBOOK.md)** — пошаговый чек-лист еженедельного запуска.

---

## 🧠 Память, форсайт и персонализация (слой «memory & foresight»)

Поверх еженедельного отчёта агент ведёт состояние в `data/state/` и
персонализирует выдачу:

- **Память трендов** — `data/state/metrics.json`: числовые KPI неделя-к-неделе.
  При ≥3 неделях `memory.py` авто-строит линии динамики (вставляются в «Статистику
  в графиках»). В недельных данных — блок `metrics[] {key,value,unit,segment,source}`.
- **Сюжеты в развитии** — `data/state/stories.json` + блок `threads[]`
  ({id,title,segment,status: new/developing/watch/resolved, update, next_trigger}).
  Превращает «не повторяться» в умные продолжения со статусом и след. триггером.
  В отчёте — раздел «🧵 Сюжеты в развитии».
- **Форвард-календарь** — `data/state/calendar.json` + блок `calendar[]`
  ({date,what,segment,kind,impact}). Раздел «📅 Календарь: за чем следить».
- **Персонализация** — `profile.json` (сегменты/регионы/интересы владельца) →
  агент кладёт `portfolio_notes[]`, рендерится врезка «★ Важно для вашего портфеля».

Модуль `memory.py`: `update_state(data)` (идемпотентно по `week_end`),
`load_state()`, `trend_chart_specs()`, `upcoming()`, `active_threads()`.
Подключён в `run_report.py` (шаг 1b) — мягко, без падения если данных нет.
**Коммитьте `data/state/`** — иначе память «забудется» (окружение эфемерно).
