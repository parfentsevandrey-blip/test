# Scrapling — установка и разбор

[Scrapling](https://github.com/D4Vinci/Scrapling) — Python-фреймворк для веб-скрапинга
(автор Karim Shoair, лицензия BSD-3). Объединяет HTTP-клиент с подменой TLS-отпечатка,
стелс-браузер, быстрый парсер и краулер в стиле Scrapy. Изучена версия **0.5**
(коммит `fd53131` от 18.10.2026).

## Установка

```bash
bash tools/scrapling/setup.sh          # venv + pip install "scrapling[all]" + scrapling install
```

| Пакет | Что ставит |
| --- | --- |
| `scrapling` | только парсер (`Selector`): lxml, cssselect, orjson, w3lib, tld |
| `scrapling[fetchers]` | `Fetcher` (curl_cffi), `StealthyFetcher` (patchright), спайдеры, CLI |
| `scrapling[ai]` | + MCP-сервер (`mcp`, `markdownify`) |
| `scrapling[shell]` | + интерактивная оболочка IPython и `scrapling extract` |
| `scrapling[all]` | всё вышеперечисленное |

- Python ≥ 3.10. Браузеры ставятся отдельно: `scrapling install`
  (= `playwright install chromium` + системные зависимости).
- Если Chromium уже есть, установку браузера можно пропустить и передавать путь
  через `executable_path=` / `scrapling mcp --executable-path`. В примерах ниже
  путь берётся из `SCRAPLING_CHROMIUM`.
- Docker-образ со всеми браузерами: `docker pull pyd4vinci/scrapling`.

## Из чего состоит

| Слой | Классы | Особенности |
| --- | --- | --- |
| **Парсер** | `Selector`, `Selectors` | CSS (с `::text`/`::attr()` как в Parsel), XPath, `find_all` в стиле BeautifulSoup, поиск по тексту и regex, навигация (`parent`, `next`, `below_elements`), `find_similar()`, автогенерация CSS/XPath, `page.markdown()` |
| **Адаптивность** | `adaptive=True`, `auto_save=True` | запоминает отпечаток элемента в SQLite и находит его после редизайна сайта |
| **HTTP** | `Fetcher`, `AsyncFetcher`, `FetcherSession` | curl_cffi: имитация TLS-отпечатка браузера (`impersonate='chrome'`), HTTP/3, реалистичные заголовки, прокси, ретраи |
| **Браузер** | `StealthyFetcher`, `StealthySession`, `AsyncStealthySession` | Patchright (стелс-форк Playwright): подмена отпечатков, `solve_cloudflare`, блокировка ~3500 рекламных доменов, DoH против DNS-утечек, CDP к удалённому браузеру, Shadow DOM, `page_action` для своих действий |
| **Перехват API** | `record_requests=True` → `session.network.search()` | сохраняет ответы XHR/fetch во время работы браузера — данные берутся прямо из JSON API сайта |
| **Спайдеры** | `Spider`, `Request`, `Response` | async-колбэки, конкурентность, несколько сессий в одном спайдере (`sid=`), пауза/возобновление (`crawldir`), стриминг, AutoThrottle, robots.txt, кэш для разработки, экспорт JSON/JSONL/CSV/XML |
| **Шаблоны** | `CrawlSpider`, `SitemapSpider`, `XMLFeedSpider`, `CSVFeedSpider`, `ShopifySpider`, `ContentSpider` | готовые краулеры; `ContentSpider` превращает целый сайт в Markdown-корпус для RAG |
| **Прокси** | `ProxyRotator` | циклическая или своя стратегия ротации, переопределение на уровне запроса |
| **CLI** | `scrapling extract get/post/put/delete/fetch`, `scrapling shell`, `scrapling mcp`, `scrapling install` | скрапинг без кода; в shell есть конвертер curl → Scrapling |
| **AI** | MCP-сервер (16 инструментов), Agent Skill | агент может открывать браузер, кликать, делать скриншоты, читать перехваченные API; контент очищается от скрытого текста против prompt injection |
| **Scrapy** | `scrapling.integrations.scrapy.scrapling_response` | декоратор, чтобы парсить ответы Scrapy парсером Scrapling |

### Инструменты MCP-сервера

`make_request`, `browser_fetch_once` (разовые); `open_request_session`,
`session_make_request`, `browser_open`, `browser_fetch`, `close_session`,
`list_sessions` (сессии); `browser_extract`, `browser_actions`, `browser_evaluate`,
`browser_screenshot` (работа со страницей); `browser_network_requests`,
`browser_network_request` (история сети); `browser_save_state`,
`browser_load_state` (cookies/логин).

Подключение к Claude Code (по документации проекта):
`claude mcp add ScraplingServer "$(pwd)/tools/scrapling/.venv/bin/scrapling-mcp"`.
HTTP-режим: `scrapling mcp --http --auth-token <token>`.

## Что изменилось в 0.5 (важно для старых примеров из интернета)

- `DynamicFetcher` / `DynamicSession` / `PlayWrightFetcher` удалены → используйте `StealthyFetcher` / `StealthySession`.
- `capture_xhr` и `response.captured_xhr` удалены → `StealthySession(record_requests=True)` + `session.network.search(url_pattern=...)`.
- Параметр `keep_cdata` удалён; CLI-команда `extract stealthy-fetch` → `extract fetch`.
- Импорты браузерных типов: `playwright.*` → `patchright.*`.

## Ассеты репозитория

| Путь | Содержимое |
| --- | --- |
| `docs/assets/` | логотип и обложки (`logo.png`, `main_cover.png`, `cover_light/dark.svg/png`), `favicon.ico`, схема архитектуры спайдера (`spider_architecture.png`: Spider → Scheduler → Crawler Engine ↔ Session Manager → Output, плюс Checkpoint system), скриншот shell с конвертером curl |
| `docs/` | документация (сайт на Zensical, readthedocs) + переводы на 9 языков, включая русский (`docs/README_RU.md`, `docs/locales/ru`) |
| `agent-skill/` | Agent Skill `scrapling-official` (`SKILL.md`, справочники `references/`, 4 примера, архив `Scrapling-Skill.zip`) — подходит для Claude Code |
| `images/`, `includes/sponsors.html` | логотипы и блок спонсоров (в основном прокси-провайдеры) |
| `tests/`, `benchmarks.py` | тесты (≈92 % покрытия) и бенчмарк парсера против Parsel, lxml, BS4 и др. |
| `Dockerfile`, `server.json` | образ Docker и манифест MCP-реестра |

## Примеры (проверены в этой установке)

```bash
export SCRAPLING_CHROMIUM=/opt/pw-browsers/chromium   # необязательно
cd tools/scrapling/examples
../.venv/bin/python 01_parser_and_fetcher.py   # CSS/XPath/find_similar/regex + HTTP-запрос и Markdown
../.venv/bin/python 02_browser_and_spider.py   # JS-страница, перехват /api/quotes, спайдер на 3 страницы → quotes.json
../.venv/bin/python 03_adaptive.py             # элемент найден после «редизайна» разметки
SCRAPLING_BIN=../.venv/bin/scrapling ../.venv/bin/python 04_mcp_client.py   # запуск MCP-сервера, список 16 инструментов, вызов make_request
```

Результаты прогона: статический и JS-рендеринг вернули по 10 цитат, браузер перехватил
`GET /api/quotes?page=1` (200), спайдер собрал 30 записей, адаптивный селектор нашёл
`$12` там, где обычный `#cost` вернул пустой список, MCP-сервер отдал 16 инструментов.

> Используйте только в рамках закона, условий сайтов и robots.txt
> (`robots_txt_obey=True` в спайдере).

## Скрипт: лоты investmoscow.ru

`investmoscow_lot.py` собирает всё по лоту «Имущество от города»: открывает карточку
в стелс-браузере, нажимает вкладку «Сведения о процедуре», перехватывает все
JSON-ответы API сайта и вытаскивает цену, задаток, шаг, номер процедуры, сроки и
характеристики объекта.

```bash
cd tools/scrapling
.venv/bin/python investmoscow_lot.py 20208859
.venv/bin/python investmoscow_lot.py https://investmoscow.ru/tenders/tender/20208859 20208860 -o lots
.venv/bin/python investmoscow_lot.py 20208859 --proxy http://user:pass@host:port --headful
```

Результат: `investmoscow_lots/<id>/`
- `summary.json` — поля со страницы (`from_page`) и из API (`from_api`);
- `api/*.json` — сырые ответы API (там все поля, даже те, что не попали в сводку);
- `page.txt`, `page.md` — текст отрисованной карточки.

Для нескольких лотов дополнительно создаётся `all_lots.json`.

> investmoscow.ru обрывает соединения с зарубежных IP. Запускайте из России или через
> российский прокси (`--proxy`).
