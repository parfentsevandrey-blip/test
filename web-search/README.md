# web-search

Новый проект по поиску в интернете. Здесь собран инструментарий; сам код проекта появится позже.

## Что установлено

| Утилита | Что это | Где |
|---|---|---|
| [Scrapling](https://github.com/D4Vinci/Scrapling) 0.5 | Скрейпинг: HTTP, stealth-браузер, MCP-сервер | `web-search/.venv`, MCP `scrapling` |
| [crawl4ai](https://github.com/unclecode/crawl4ai) 0.9.5 | Краулер, отдающий Markdown для LLM | `web-search/.venv` |
| [rea](https://github.com/morluto/rea) 6.7.0 | Реверс-инжиниринг приложений и сайтов (CLI + MCP) | `rea` в PATH, MCP `rea`, скилл `reverse-engineer-anything` |
| [last30days](https://github.com/mvanhorn/last30days-skill) | Что люди писали о теме за 30 дней (Reddit, X, YouTube, HN…) | скилл `last30days` |
| [hallmark](https://github.com/Nutlope/hallmark) | Дизайн без «AI-слопа» | скилл `hallmark` |
| [impeccable](https://github.com/pbakaus/impeccable) | Дизайн-ревью и полировка фронтенда | скилл `impeccable` |
| [ui-ux-pro-max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | База стилей, палитр, шрифтов, UX-правил | скиллы `ui-ux-pro-max`, `ui-styling`, `design`, `design-system`, `brand`, `banner-design`, `slides` |
| [diagram-design](https://github.com/cathrynlavery/diagram-design) | Диаграммы в HTML/SVG | скилл `diagram-design` |
| [humanizer](https://github.com/blader/humanizer) | Убирает признаки AI-текста | скилл `humanizer` |
| [ponytail](https://github.com/DietrichGebert/ponytail) | Режим «минимальный код» и ревью | скиллы `ponytail`, `ponytail-review`, `ponytail-audit`, `ponytail-debt`, `ponytail-gain`, `ponytail-help` |

Скиллы лежат в `.claude/skills/` (версии — в `skills-lock.json`), MCP-серверы — в `.mcp.json`.

## Установка в новом окружении

Хук `SessionStart` из `.claude/settings.json` сам запускает `web-search/setup.sh`: создаёт venv, ставит пакеты из `requirements.txt`, качает Chromium в `web-search/.browsers` и ставит `rea`. Вручную:

```bash
web-search/setup.sh
```

## Быстрая проверка

```bash
cd web-search
export PLAYWRIGHT_BROWSERS_PATH=$PWD/.browsers
.venv/bin/crawl4ai-doctor
.venv/bin/scrapling extract get https://quotes.toscrape.com/ out.md
rea doctor --json
```

`last30days` без ключей работает в урезанном режиме (Reddit, HN, Polymarket, GitHub). Для X, YouTube, TikTok нужны ключи вроде `SCRAPECREATORS_API_KEY` или `XAI_API_KEY`; список — в `.claude/skills/last30days/SKILL.md`.

## Сборщики

Исследование, как сайты отдают данные: `docs/research-domrf-cian.md`. Описание всех инструментов и скиллов: `docs/toolkit.md`.

```bash
cd web-search
.venv/bin/python collectors/cian.py jk --region 1 --pages 2 --out data/cian-jk.jsonl        # каталог ЖК
.venv/bin/python collectors/cian.py jk-card --url https://zhk-soyuz-i.cian.ru/ --out data/jk.json
.venv/bin/python collectors/cian.py flats --newobject 4025502 --out data/flats.jsonl       # квартиры ЖК
.venv/bin/python collectors/cian.py flats --region 1 --new --all --out data/all.jsonl      # весь рынок, нарезка по цене

# наш.дом.рф: только с российского IP (из облака Servicepipe показывает капчу)
PLAYWRIGHT_BROWSERS_PATH=$PWD/.browsers DOMRF_PROXY=http://user:pass@host:port \
  .venv/bin/python collectors/domrf.py discover --out data/domrf-discover
```

Результаты тестового прогона: `samples/2026-10-19/`.
