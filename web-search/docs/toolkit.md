# Инструментарий web-search: что установлено и зачем

Инвентаризация на 2026-10-19. Проект: сбор данных с сайтов недвижимости — **наш.дом.рф** (punycode `xn--80az8a.xn--d1aqf.xn--p1ai`) и **cian.ru**. Результаты исследования сайтов и сборщики — в `docs/research-domrf-cian.md` и `collectors/`.

## 1. Где что лежит

| Что | Путь / как запускать |
|---|---|
| Python-окружение (Python 3.13) | `web-search/.venv`: `scrapling[all]==0.5`, `crawl4ai==0.9.5` (`web-search/requirements.txt`) |
| Браузеры Playwright | `web-search/.browsers/` (`chromium-1248`, `chromium_headless_shell-1248`, `ffmpeg-1013`). Бинарник Chrome: `web-search/.browsers/chromium-1248/chrome-linux64/chrome` |
| Установка | `web-search/setup.sh` (идемпотентный; его же запускает хук `SessionStart` из `.claude/settings.json`). Ставит venv через `uv`, Chromium и `rea-agents@6.7.0` через npm |
| MCP-серверы | `.mcp.json`: `scrapling` → `web-search/bin/scrapling-mcp` (stdio, сам ставит `PLAYWRIGHT_BROWSERS_PATH`); `rea` → `npx -y rea-agents@6.7.0 mcp`. Оба включены в `enabledMcpjsonServers` |
| rea CLI | `/opt/node22/bin/rea` 6.7.0 (Node 22.22) |
| Скиллы | `.claude/skills/*`, хеши и источники в `skills-lock.json` (REA-скилл ставится вместе с rea и в lock-файл не попадает) |

**Важно:** перед ручным запуском браузерных инструментов выполнить `export PLAYWRIGHT_BROWSERS_PATH=/home/user/test/web-search/.browsers`, иначе Scrapling и crawl4ai не найдут Chromium.

## 2. Какой инструмент брать под какую задачу

| Этап | Основной инструмент | Альтернатива / дополнение |
|---|---|---|
| Найти, в какие JSON API ходит фронтенд | Scrapling `StealthySession(record_requests=True)` + `session.network.search(...)`; в MCP: `browser_open` → `browser_fetch` → `browser_network_requests` / `browser_network_request` | rea `analyze-web-bundle` (кандидаты эндпоинтов и роутов из JS-бандла без выполнения), `inspect-web-page --include-json-body-shapes`, `capture-browser-scenario` с записью сети |
| Повторить найденный запрос | `scrapling shell` → `uncurl` / `curl2fetcher` (cURL из DevTools превращается в вызов `Fetcher`) | `Fetcher.get/post(..., impersonate="chrome")` |
| Простой фетч HTML/JSON | `Fetcher` / `FetcherSession` (curl_cffi, TLS-имперсонация, HTTP/3) | `scrapling extract get URL out.md` |
| JS-рендеринг и антибот | `StealthyFetcher` / `StealthySession` (Patchright): `solve_cloudflare`, `hide_canvas`, `block_webrtc`, `real_chrome`, `ProxyRotator` | crawl4ai `BrowserConfig(enable_stealth=True)`, `UndetectedAdapter`, цепочка `proxy_config` + `max_retries` + `fallback_fetch_function` |
| Массовый обход каталога (пагинация, карточки) | Scrapling `Spider` / `CrawlSpider` / `SitemapSpider` (чекпоинты, AutoThrottle, `is_blocked`, экспорт `to_jsonl/to_csv`) | crawl4ai `arun_many` + `MemoryAdaptiveDispatcher` + `RateLimiter`; `AsyncUrlSeeder` (sitemap + Common Crawl) |
| Извлечение полей | Scrapling CSS/XPath, `find_similar()`, `adaptive=True` (переживает смену вёрстки) | crawl4ai `JsonCssExtractionStrategy`, `RegexExtractionStrategy`, `DefaultTableExtraction`; LLM-стратегии только при наличии ключа |
| Текст/Markdown для LLM | `response.markdown(main_content_only=True)`, `--ai-targeted` | crawl4ai `fit_markdown` (`PruningContentFilter`, `BM25ContentFilter`) |
| Обзор «что пишут» о сайтах/рынке | скилл `last30days` | — |
| Ревью и аудит кода скрейпера | `ponytail-review`, `ponytail-audit`, `ponytail-debt` | — |
| Схемы пайплайна, графики результатов | `diagram-design` (архитектура, data flow, bar/line/heatmap, ER) | `ui-ux-pro-max --domain chart` для выбора типа графика |
| Отчёт, дашборд, презентация | `impeccable` (режим Operate для дашборда), `hallmark`, `ui-styling`, `slides` | `design-system`, `brand` |
| Вычитка текста отчёта | `humanizer` | — |

## 3. Scrapling 0.5 (основной скрейпер)

**Назначение:** HTTP-фетч с имперсонацией браузера, stealth-браузер на Patchright, быстрый парсер (`Selector`), фреймворк пауков, MCP-сервер для агента.

**Python API** (`from scrapling.fetchers import ...`):

| Класс | Что делает |
|---|---|
| `Fetcher`, `AsyncFetcher` | `get/post/put/delete`; `impersonate="chrome"`, `stealthy_headers`, `proxy`, `http3`, `params`, `cookies`. Без JS |
| `FetcherSession` | то же, с общими cookies/соединениями; принимает `proxy_rotator` |
| `StealthyFetcher.fetch(url, ...)` | один запрос через stealth-Chromium. Параметры: `headless`, `solve_cloudflare` (таймаут от 60 с), `hide_canvas`, `block_webrtc`, `allow_webgl`, `real_chrome`, `google_search`, `network_idle`, `wait_selector`, `disable_resources`, `block_ads`, `dns_over_https`, `locale`, `proxy`, `page_action`, `extra_flags`, `cdp_url` |
| `StealthySession` / `AsyncStealthySession` | постоянный браузер с пулом вкладок; `record_requests=True` включает историю сети; `max_recorded_requests` (по умолчанию 1000) |
| `ProxyRotator([...])` | ротация прокси для любых сессий; использованный прокси лежит в `response.meta["proxy"]` |
| `Selector` / `Response` | `.css()`, `.xpath()`, `::text`, `::attr()`, `.find_similar()`, `.markdown(main_content_only=True)`, `.json()`, `.status`, `.cookies`, `.history` |

**Перехват API (ключевое для нас):**
```python
with StealthySession(record_requests=True) as s:
    s.fetch(url, network_idle=True)
    for r in s.network.search(url_pattern=r"/api/", resource_type="xhr", limit=None):
        print(r.url, r.status, r.json())
```
Ограничения: тела только текстовых типов, до 1 MiB на ответ и 20 MiB всего; `search()` по умолчанию возвращает до 100 совпадений. В 0.5 удалены `DynamicFetcher`, `capture_xhr`, команда `stealthy-fetch` (см. `docs/migrating-to-v0.5.md` в исходниках).

**Сохранение сессии:** `browser_save_state` / `browser_load_state` (MCP) и аналоги в Python сохраняют cookies, localStorage и IndexedDB в формате Playwright. Через них можно один раз пройти антибот или логин и переиспользовать состояние.

**Пауки** (`scrapling.spiders`): `Spider`, `CrawlSpider` + `CrawlRule` + `LinkExtractor`, `SitemapSpider`, `XMLFeedSpider`, `CSVFeedSpider`, `ContentSpider`, `SiteToMarkdownSpider`, `ShopifySpider`. Что умеют:
- `configure_sessions(manager)`: несколько сессий в одном пауке (`manager.add("http", FetcherSession(...))`, `manager.add("stealth", AsyncStealthySession(...))`); дешёвый HTTP для списков, браузер для защищённых карточек;
- `is_blocked()` и `retry_blocked_request()`, `max_blocked_retries` (по умолчанию 3), AutoThrottle с отступлением при блокировке;
- пауза и возобновление через чекпоинты, dev-кеш ответов, стриминг, хуки `on_start/on_close/on_error/on_scraped_item`;
- `result.items.to_json/to_jsonl/to_csv/to_xml`, подробная статистика (`status_200`, байты по доменам, использованные прокси);
- `robots_txt_obey=True`: в 0.5 ошибка загрузки robots.txt блокирует домен.

**CLI** (`web-search/.venv/bin/scrapling`):

| Команда | Назначение |
|---|---|
| `scrapling extract get\|post\|put\|delete URL OUT.{html,md,txt}` | HTTP-фетч в файл; `-s` CSS-селектор, `--impersonate chrome,firefox`, `-H`, `--cookies`, `--proxy`, `--ai-targeted` |
| `scrapling extract fetch URL OUT` | то же через StealthyFetcher: `--solve-cloudflare`, `--hide-canvas`, `--block-webrtc`, `--network-idle`, `--wait-selector`, `--real-chrome`, `--locale ru-RU`, `--proxy` |
| `scrapling shell` | IPython-консоль: `get/fetch`, `page`, `view(page)`, `uncurl`, `curl2fetcher`; `-c` выполняет код и выходит |
| `scrapling mcp` | MCP-сервер (stdio или `--http` с `--auth-token`) |
| `scrapling install` | установка браузерных зависимостей (у нас этим занимается `setup.sh`) |

**MCP-инструменты** (сервер `scrapling`, 16 шт.): `make_request`, `open_request_session`, `session_make_request`, `browser_fetch_once`, `browser_open`, `browser_fetch`, `browser_actions` (клики, ввод, ожидания), `browser_evaluate` (JS на странице), `browser_extract` (snapshot/HTML/Markdown/текст), `browser_screenshot`, `browser_network_requests`, `browser_network_request`, `browser_save_state`, `browser_load_state`, `list_sessions`, `close_session`.

**Требования:** ключи не нужны. Нужен Chromium из `.browsers` (переменная `PLAYWRIGHT_BROWSERS_PATH`). Документация: `scratchpad/src/Scrapling/docs/` (`fetching/stealthy.md`, `spiders/*.md`, `parsing/adaptive.md`, `cli/*.md`, `ai/mcp-server.md`).

**Для нашего проекта:** основной движок. Для cian.ru, где вероятна антибот-защита, начинать с `StealthySession` + `record_requests` и сохранённым состоянием, затем переходить на `FetcherSession(impersonate="chrome")` с найденными API. Для наш.дом.рф сначала проверить, отдаёт ли сайт данные JSON-ом через XHR (это предположение, не проверено), и если да, работать с API, а не с HTML.

## 4. crawl4ai 0.9.5 (краулер с выдачей в Markdown/JSON)

**Назначение:** асинхронный браузерный краулер на Playwright с фокусом на чистый Markdown и структурированное извлечение. Пересекается со Scrapling. Сильные стороны: готовые стратегии извлечения, deep/adaptive crawl, URL seeding, виртуальный скролл.

**Python API** (`from crawl4ai import ...`):

| Компонент | Что делает |
|---|---|
| `AsyncWebCrawler` + `arun(url, config)` / `arun_many(urls)` | основной вход; результат `CrawlResult`: `.html`, `.cleaned_html`, `.markdown` (`raw_markdown`, `fit_markdown`), `.extracted_content`, `.links`, `.media`, `.tables`, `.network_requests`, `.crawl_stats` |
| `BrowserConfig` | `headless`, `enable_stealth`, `proxy_config`, `user_agent_mode="random"`, профиль; `UndetectedAdapter` для сложных антиботов |
| `CrawlerRunConfig` | `css_selector`, `wait_for`, `js_code`, `session_id` (многошаговые сценарии), `scan_full_page`, `virtual_scroll_config`, `capture_network_requests`, `capture_console_messages`, `cache_mode`, `max_retries`, `proxy_config` (список, допускает `"direct"`), `fallback_fetch_function` |
| Без LLM | `JsonCssExtractionStrategy`, `JsonXPathExtractionStrategy`, `JsonLxmlExtractionStrategy` (схема `baseSelector` + `fields`, вложенные списки), `RegexExtractionStrategy` (встроенные `Email`, `Url`, `PhoneUS`, `Currency` и др. плюс свои шаблоны), `DefaultTableExtraction` |
| С LLM | `LLMExtractionStrategy`, `LLMContentFilter`, `LLMTableExtraction`, `JsonCssExtractionStrategy.generate_schema(html, llm_config=...)` (схему генерирует LLM один раз, дальше извлечение идёт без LLM) |
| Обход | `BFSDeepCrawlStrategy`, `DFSDeepCrawlStrategy`, `BestFirstCrawlingStrategy` + `FilterChain` (`URLPatternFilter`, `DomainFilter`, `ContentTypeFilter`) + скореры (`KeywordRelevanceScorer`, `PathDepthScorer`, `FreshnessScorer`) |
| Умный обход | `AdaptiveCrawler` / `AdaptiveConfig`: останавливается, когда покрытие запроса насыщено (метрики coverage/consistency/saturation) |
| Поиск URL | `AsyncUrlSeeder` + `SeedingConfig(source="sitemap" \| "sitemap+cc")` (sitemap и Common Crawl) |
| Параллельность | `MemoryAdaptiveDispatcher`, `SemaphoreDispatcher`, `RateLimiter`, `CrawlerMonitor` |
| Прочее | `C4A-Script` (DSL сценариев: `c4a_compile`), `BrowserProfiler` (именованные профили с логином), `PDFContentScrapingStrategy` |

**Антибот:** детектор `antibot_detector` распознаёт 403/429 с пустым телом, страницы Cloudflare, Akamai, PerimeterX, DataDome, Imperva и капчи. При блокировке перебирает `proxy_config` по кругу (`1 + max_retries` раундов), затем вызывает `fallback_fetch_function`. Итог виден в `result.crawl_stats["resolved_by"]`.

**CLI** (`web-search/.venv/bin/`):

| Команда | Назначение |
|---|---|
| `crwl URL` / `crwl crawl URL` | `-o markdown\|md-fit\|json\|all`, `-B browser.yml`, `-C crawler.yml`, `-e extraction.yml`, `-s schema.json`, `-b "headless=true,..."`, `-c "css_selector=...,scan_full_page=true"`, `--deep-crawl bfs\|dfs\|best-first --max-pages N`, `-p <profile>`, `-j` / `-q` (нужен LLM) |
| `crwl browser start\|stop\|status\|view\|restart`, `crwl cdp` | постоянный браузер / CDP-эндпоинт (подходит для rea `list-browser-targets`) |
| `crwl profiles create\|list\|delete` | профили с сохранённым логином (identity-based crawling) |
| `crwl config list\|get\|set`, `crwl examples` | глобальные настройки и примеры |
| `crawl4ai-doctor`, `crawl4ai-setup`, `crawl4ai-download-models`, `crawl4ai-migrate` | проверка окружения, установка, модели для косинусной кластеризации, миграции БД кеша |

**Требования:** без ключей работает всё, кроме LLM-функций. Для LLM нужен `LLMConfig(provider="openai/gpt-4o-mini", api_token=...)` (через litellm, любой провайдер) или локальная Ollama. `CRAWL4AI_NO_CLOUD_NOTICE=1` отключает баннер облака. Документация: `scratchpad/src/crawl4ai/docs/md_v2/` (`core/`, `extraction/`, `advanced/anti-bot-and-fallback.md`, `advanced/undetected-browser.md`, `advanced/network-console-capture.md`, `core/url-seeding.md`).

**Для нашего проекта:** вторая линия. Пригодится как альтернативный движок, если Patchright у Scrapling блокируют, для декларативных JSON-схем извлечения карточек объявлений (`JsonCssExtractionStrategy`), для бесконечных лент (`virtual_scroll_config`) и для поиска URL объектов через sitemap (`AsyncUrlSeeder`). Держать оба движка в одном пайплайне не стоит: выбрать один после пробных запросов.

## 5. rea 6.7.0 + скилл reverse-engineer-anything (разведка API)

**Назначение:** реверс-инжиниринг: бинарники, APK, Electron/JS-приложения, firmware, сетевые дампы и **веб-страницы через CDP**. Результаты выдаются как Evidence с ID и явными пределами покрытия. Всего 207 MCP-инструментов и около 160 CLI-команд. Для веба нужна малая часть.

**Веб-команды** (CLI `rea <cmd>`, MCP-имя через `_`):

| Команда | Что даёт |
|---|---|
| `list-browser-targets <cdp>` | вкладки браузера на loopback CDP (`http://127.0.0.1:9222`) |
| `inspect-web-page <cdp> <targetId>` | пассивный снимок: DOM, a11y, скрипты, ресурсы, сеть за окно наблюдения; флаги `--include-json-body-shapes`, `--include-websocket-shapes`, `--include-script-sources`, `--include-storage-keys` |
| `analyze-web-bundle <cdp> <targetId>` | статический разбор JS-бандла без выполнения: граф чанков, **кандидаты роутов и API-эндпоинтов**, фингерпринты вендоров (в т. ч. антибот-скриптов), `--fetch-source-maps` |
| `observe-web-session <cdp> <targetId> --observation-ms 30000` | записывает навигацию, пока человек сам кликает по сайту |
| `capture-browser-scenario <json>` | сценарий Playwright, который выполняет агент: действия `goto`, `click`, `fill`, `press`, `select_option`, `wait_for`, `wait_for_timeout`; локаторы `css`/`role`/`test_id`; `capture.network` с `request_body`/`response_body`/`header_values`; секреты редактируются. Режим `launch` требует `executable_path` (наш: `web-search/.browsers/chromium-1248/chrome-linux64/chrome`) |
| `export-web-scripts`, `trace-web-module-imports`, `trace-web-source-location` | выгрузить скрипты из захвата и разбирать их статически (`analyze-javascript-application`) |
| `inspect-web-event-listeners`, `observe-web-execution` | какой код висит на элементе; покрытие исполнения за окно |
| `inspect-web-network-capture <path> har\|mitmproxy\|saz\|qlog` | разбор сохранённого HAR (например, выгруженного из DevTools) без повторных запросов |
| `capture-web-screenshot`, `compare-web-screenshots`, `compare-web-captures` | скриншоты и диффы «до/после» (полезно для отслеживания смены вёрстки) |
| `discover-webmcp-tools` | page-declared WebMCP-инструменты |

Служебные: `rea doctor --json`, `rea capabilities`, `rea providers`, `rea setup --client claude_code --dry-run`, глобальные `--format json|md|yaml`, `--token-limit`, `--schema`, `--llms`.

**Скилл** (`.claude/skills/reverse-engineer-anything/SKILL.md`, v42): маршрутизация по типу цели, работа от summary к деталям (`detail: "summary"` → `inspect_analysis_view`), обязательное разделение наблюдений, выводов и неизвестного. References: `runtime-observation.md` (кто выполняет действие: пользователь → `inspect_web_page`/`observe_web_session`, агент → `capture_browser_scenario`), `javascript-applications.md`, `evidence-workflows.md`, `connection-and-recovery.md`, `native-and-artifacts.md`, `android-applications.md`.

**Требования:** для веба нужен только Node и Chromium с открытым CDP. Можно запустить `crwl cdp`, `chrome --remote-debugging-port=9222` или режим `launch`. `rea doctor` сейчас показывает `healthy:false` из-за отсутствия Hopper, Ghidra, IDA, JADX и Kuna. Они нужны только для нативных бинарников и APK, на веб не влияют.

**Для нашего проекта:** главный инструмент разведки. Порядок: открыть страницу поиска или карточки в Chromium с CDP, вызвать `analyze-web-bundle` для списка эндпоинтов, затем `inspect-web-page --include-json-body-shapes` для структуры JSON-ответов. Если нужен клик (фильтр, «показать ещё», телефон), используется `capture-browser-scenario` с `response_body`. Найденные эндпоинты потом дёргаются через Scrapling `Fetcher`. Мобильные приложения ЦИАН и ДОМ.РФ теоретически можно разбирать через APK-инструменты (`inspect-android-package`), но для этого нужны JADX и apktool, которых нет.

## 6. last30days (v3.28.0): исследование обсуждений

**Назначение:** собирает, что писали о теме за 30 дней на Reddit, X, YouTube, TikTok, Instagram, HN, Polymarket, GitHub, Bluesky, в вебе, и синтезирует бриф с цитатами. Движок: `scripts/last30days.py` (+107 модулей в `scripts/lib/`: `reddit*.py`, `hackernews.py`, `github.py`, `polymarket.py`, `planner.py`, `rerank.py`, `html_render.py`, `doctor.py`, `setup_wizard.py` и др.), а также `briefing.py`, `watchlist.py`, `store.py` (SQLite). Обязательный протокол: план запроса (`--plan` файл), `doctor --cached --json`, правила вывода LAW 1–11.

**References:** `runtime.md` (поиск Python 3.12+, обязателен до первого запуска), `research-runbook.md`, `synthesis.md`, `setup-wizard.md`, `comparison.md`, `competitors.md`, `discovery.md`, `save-html-brief.md`, `library-queue.md`, `hiring-signals.md`. **Assets:** только демо-медиа (`aging-portrait.jpeg`, `dog-*.png/jpeg`, `swimmom-mockup.jpeg`, `claude-code-rap.mp3`, около 14 МБ), к работе отношения не имеют.

**Требования:** Python 3.12+ (системный 3.13 подходит), Node. Без ключей работает урезанный набор: Reddit (публичный), HN, Polymarket, GitHub. Расширения: `SCRAPECREATORS_API_KEY` (TikTok, Instagram, бэкфилл Reddit и YouTube), `XAI_API_KEY` / `X_BEARER_TOKEN` / `AUTH_TOKEN`+`CT0` (X), `BRAVE_API_KEY`, `PERPLEXITY_API_KEY`, `PARALLEL_API_KEY`, `OPENROUTER_API_KEY`, `OPENAI_API_KEY`. YouTube использует `yt-dlp`, а он **не установлен**. Конфиг `~/.config/last30days/.env` пока не создан, поэтому первый запуск пойдёт через setup wizard. Брифы сохраняются в `~/Documents/Last30Days`.

**Для нашего проекта:** косвенная польза. Можно узнать, как другие парсят ЦИАН и ДОМ.РФ, что сейчас ломается (смена API, новые антибот-меры), какие есть open-source решения на GitHub. Русскоязычные площадки (Habr, Telegram, VC) он не покрывает. Данные о недвижимости он не собирает.

## 7. ponytail (инженерная дисциплина)

Пакет из шести скиллов, только инструкции, без скриптов и ключей.

| Скилл | Что делает |
|---|---|
| `ponytail` | режим «ленивого сеньора»: минимальное полное изменение, без лишних зависимостей и абстракций; уровни `lite`/`full`/`ultra`; отложенные упрощения помечаются комментарием `shortcut:` |
| `ponytail-review` | ревью диффа/коммита/PR: корректность → безопасность → нагрузка → тесты → скорость → лишний код |
| `ponytail-audit` | то же для всего репозитория, ранжированный отчёт, ничего не меняет |
| `ponytail-debt` | собирает все `shortcut:`/`ponytail:` комментарии в реестр долга (`git grep`) |
| `ponytail-gain` | показывает результаты бенчмарка скилла (39 задач × 5 прогонов) |
| `ponytail-help` | шпаргалка по уровням и командам |

**Для нашего проекта:** полезен при написании скрейпера, чтобы не тащить оба движка, ORM и очереди без нужды. `ponytail-review` перед коммитами, `ponytail-audit` после первой рабочей версии.

## 8. humanizer (v3.1.0): вычитка текста

**Назначение:** переписывает текст, убирая признаки AI-письма: контрасты «не X, а Y», триады, тире, раздувание значимости, рекламный тон, жирные подписи, вступления и концовки ради эффекта. Факты при этом не добавляет и не теряет. Основан на Wikipedia «Signs of AI writing». Есть только `SKILL.md`, `README.md` и `scripts/validate-package.py` (проверка пакета, к работе не относится). Ключей не требует. Работает и с русским текстом: в скилле сказано, что те же конструкции нужно ловить в любом языке.

**Для нашего проекта:** финальная вычитка отчётов и README. На сбор данных не влияет.

## 9. Дизайн и UI: для скрейпинга бесполезны, пригодятся для отчёта или дашборда

Все скиллы ниже **не участвуют в сборе данных**. Они нужны, когда понадобится показать результаты: дашборд объявлений, HTML-отчёт, презентация, схема пайплайна.

### 9.1 ui-ux-pro-max (+ семейство nextlevelbuilder)

- **ui-ux-pro-max:** база знаний с BM25-поиском: `python3 scripts/search.py "<query>" --domain <style|color|chart|landing|product|ux|typography|icons|gsap|react|web|google-fonts>`, `--design-system -p "Name"`, `--stack <react|nextjs|vue|svelte|shadcn|html-tailwind|...>` (22 стека в `data/stacks/*.csv`). Данные в `data/`: `styles.csv` (79 стилей), `colors.csv` (192 палитры), `products.csv` и `ui-reasoning.csv` (192 профиля), `typography.csv` (74 пары шрифтов), `google-fonts.csv` (~1900 шрифтов), `ux-guidelines.csv` (119 правил), `charts.csv` (25 типов графиков с правилами доступности и выбором библиотеки), `icons.csv`, `motion.csv` (GSAP), `react-performance.csv`, `landing.csv`, `app-interface.csv`. References: `quick-reference.md`, `pro-rules.md`. Только stdlib Python. **Полезно:** `--domain chart` подскажет график для цен за м² и динамики, `--domain ux` даст правила для таблиц и фильтров дашборда.
- **ui-styling:** shadcn/ui + Tailwind + canvas-дизайн. Скрипты `scripts/shadcn_add.py button card dialog`, `scripts/tailwind_config_gen.py --colors brand:blue --fonts display:Inter`. References про shadcn (компоненты, темы, a11y) и Tailwind. Assets: `canvas-fonts/` (54 OFL-шрифта .ttf для постеров).
- **design:** логотипы (55 стилей), CIP (фирменный стиль, 50 носителей), иконки, соцкартинки, баннеры, слайды. Скрипты `scripts/logo/{search,generate}.py`, `scripts/cip/{search,generate,render-html}.py`, `scripts/icon/generate.py`. Данные `data/logo/*.csv`, `data/cip/*.csv`, `data/icon/styles.csv`. **Генерация требует ключей:** `GEMINI_API_KEY` (по умолчанию), `ATLASCLOUD_API_KEY` (`--provider atlas`), `MUAPI_API_KEY` (`--provider muapi`). Для проекта практически не нужен.
- **design-system:** трёхслойные токены (primitive → semantic → component). Скрипты: `node scripts/generate-tokens.cjs --config tokens.json -o tokens.css`, `validate-tokens.cjs --dir src/`, `embed-tokens.cjs`, `search-slides.py`, `generate-slide.py`, `slide-token-validator.py`, `html-token-validator.py`, `fetch-background.py` (Pexels/Unsplash). Данные `data/slide-*.csv` (layouts, charts, copy, strategies, typography, color logic, backgrounds). Шаблон `templates/design-tokens-starter.json`.
- **brand:** голос бренда, визуальная идентичность. Скрипты `inject-brand-context.cjs`, `sync-brand-to-tokens.cjs`, `validate-asset.cjs`, `extract-colors.cjs`; шаблон `templates/brand-guidelines-starter.md`; 11 references (voice, palette, typography, logo usage, approval checklist).
- **banner-design:** баннеры для соцсетей, рекламы, hero-блоков (22 стиля). Только `references/banner-sizes-and-styles.md`; экспорт в PNG делается браузером, отдельного скрипта нет.
- **slides:** HTML-презентации с Chart.js, подкоманда `create`. References: `create.md`, `layout-patterns.md`, `html-template.md`, `copywriting-formulas.md`, `slide-strategies.md`; использует скрипты `../design-system/scripts/`. **Полезно** для презентации результатов (графики цен, сравнение ЖК).

### 9.2 hallmark (v1.1.0)

Анти-«AI-slop» дизайн новых страниц. Глаголы: по умолчанию design flow, `hallmark audit <target>` (только отчёт), `hallmark redesign <target> [--mood]`, `hallmark study <URL|скриншот>` (извлекает «ДНК» дизайна). Каталог из 21 темы (Specimen, Newsprint, Terminal, Midnight, Cobalt и др.; описаны в `SKILL.md` и `references/custom-theme.md`, отдельные файлы только у 5 тем в `references/themes/`), 4 жанра (`genres/`: editorial, modern-minimal, atmospheric, playful), макроструктуры (`macrostructures/`), около 50 компонентов (`components/`: hero h1–h9, nav n1–n13, footer ft1–ft8, `f3-tabular-spec-sheet.md` и др.), `slop-test.md`, `anti-patterns.md`, `responsive.md`. Обязательные правила: токены вместо литералов, проверка на 320/375/414/768 px, никаких выдуманных цифр. Ключей не требует. **Полезно** для лендинга или отчёта; правило «никаких выдуманных метрик» подходит отчётам по данным.

### 9.3 impeccable (v4.6.0, движок 0.1.15)

Дизайн-директор для фронтенда: 24 команды (`shape`, `init` → PRODUCT.md, `document` → DESIGN.md, `extract`, `critique`, `audit`, `polish`, `harden`, `adapt`, `optimize`, `typeset`, `layout`, `colorize`, `animate`, `clarify`, `live`, `generate` и др.). Режимы Persuade, Operate, Read, Experience; **дашборд = Operate**. Каталог `reference/` содержит 42 playbook-а, включая `visualize.md`. Есть `agents/*.toml` (documenter, finish reviewer, asset producer). Launcher `scripts/impeccable` (`impeccable context`, `pin`) ищет платформенный бинарник: **в `scripts/bin/` его нет**, при первом запуске launcher скачает его в кеш (нужна сеть). Режим `live` использует `scripts/live-browser*.js`. **Полезно** при сборке дашборда по собранным объявлениям: `critique`/`audit`/`harden` для таблиц, фильтров и пустых состояний.

### 9.4 diagram-design (v2.6)

Диаграммы как самодостаточный HTML с inline SVG, 45 типов: architecture, data-flow, sequence, ER, db-schema, flowchart, timeline, swimlane, bar, line, heatmap, scatter, treemap, sankey, gantt и др. (по одному `references/type-*.md` на тип). Assets: 217 эталонных HTML (`assets/example-*.html`, светлые, тёмные, анимированные), шаблон `template-motion.html`. Скрипты: `self_check.py <file>` (контракт доступного SVG), `export_svg.py`, `export_png.py` (Playwright; запускать через `web-search/.venv/bin/python`, в системном python его нет), `mermaid_extract.py`, `drawio_extract.py`, `excalidraw_extract.py` (импорт). При первом использовании спрашивает про кастомизацию стиля (`references/style-guide.md`, `onboarding.md`, `profiles.md`). Ключей не требует. **Полезно:** схема пайплайна (разведка → фетч → парсинг → хранилище), ER-схема таблиц объявлений и ЖК, графики (bar, line, heatmap) для отчёта.

## 10. Быстрые команды

```bash
cd /home/user/test/web-search
export PLAYWRIGHT_BROWSERS_PATH=$PWD/.browsers CRAWL4AI_NO_CLOUD_NOTICE=1
CHROME=$PWD/.browsers/chromium-1248/chrome-linux64/chrome

# HTTP-фетч с имперсонацией Chrome, только нужный блок в Markdown
.venv/bin/scrapling extract get 'https://example.com/' out.md --impersonate chrome -s 'main'
# stealth-браузер, ждём селектор, русская локаль
.venv/bin/scrapling extract fetch 'https://example.com/' out.html --network-idle --locale ru-RU --wait-selector 'main'
# интерактивная отладка селекторов и cURL-запросов из DevTools
.venv/bin/scrapling shell

# crawl4ai: «очищенный» Markdown и неглубокий обход
.venv/bin/crwl 'https://example.com/' -o md-fit
.venv/bin/crwl 'https://example.com/' --deep-crawl bfs --max-pages 20 -o all -O crawl.json

# rea: Chromium с CDP, затем разбор страницы
$CHROME --headless=new --remote-debugging-port=9222 'https://example.com/' &
rea list-browser-targets http://127.0.0.1:9222 --format json
rea analyze-web-bundle http://127.0.0.1:9222 <targetId> --format json
rea inspect-web-page http://127.0.0.1:9222 <targetId> --include-json-body-shapes --observation-ms 5000
rea inspect-web-network-capture /abs/path/session.har har      # HAR из DevTools
rea doctor --json
```

## 11. Требования и ключи: сводка

| Инструмент | Бинарники / окружение | Ключи | Статус |
|---|---|---|---|
| Scrapling | `.venv`, Chromium в `.browsers`, `PLAYWRIGHT_BROWSERS_PATH` | не нужны (прокси свои) | установлен, импорты и MCP отвечают |
| crawl4ai | `.venv`, Chromium | только для LLM-функций (`OPENAI_API_KEY` и др. через `LLMConfig`) | установлен, `crawl4ai-doctor` не запускался |
| rea | Node 22, Chromium с CDP | не нужны | CLI и MCP работают; нативные движки (Hopper, Ghidra, IDA, JADX, Kuna) отсутствуют |
| last30days | Python 3.12+, Node; `yt-dlp` для YouTube (нет) | опционально `SCRAPECREATORS_API_KEY`, `XAI_API_KEY`, `BRAVE_API_KEY` и др. | first-run setup не пройден |
| design | Python | `GEMINI_API_KEY` / `ATLASCLOUD_API_KEY` / `MUAPI_API_KEY` для генерации картинок | поиск по CSV работает без ключей |
| impeccable | платформенный бинарник (скачивается) | нет | бинарник не скачан |
| diagram-design | Python; Playwright для PNG | нет | ок |
| остальные скиллы | Python stdlib / Node | нет | ок |

## 12. Рекомендуемый порядок работы для наш.дом.рф и cian.ru

1. **Разведка** (rea + Scrapling MCP): открыть поиск и карточку объекта, `analyze-web-bundle` → список API-кандидатов; `browser_open(record requests)` → `browser_network_requests`, чтобы увидеть реальные XHR с телами. Записать эндпоинты, параметры пагинации и фильтров, обязательные заголовки и cookies.
2. **Проверка без браузера:** скопировать запрос как cURL → `scrapling shell` → `curl2fetcher`; проверить, отвечает ли API на `Fetcher` с `impersonate="chrome"`. Если да, браузер в продакшене не нужен.
3. **Антибот** (если HTTP режут): `StealthySession` с `solve_cloudflare`/`hide_canvas`, сохранение состояния (`browser_save_state`), `ProxyRotator`. Резервный вариант: crawl4ai `UndetectedAdapter` + `max_retries` + `proxy_config`.
4. **Обход:** Scrapling `Spider` с двумя сессиями (HTTP для API-страниц, stealth для fallback), `is_blocked()`, AutoThrottle, чекпоинты, экспорт `to_jsonl`.
5. **Извлечение:** из API брать `.json()`; из HTML `css()` + `adaptive=True`, либо `JsonCssExtractionStrategy`.
6. **Качество кода:** `ponytail` при разработке, `ponytail-review` перед коммитом.
7. **Отчёт:** `diagram-design` (схема пайплайна, графики), `slides` или `impeccable` (дашборд), `humanizer` (текст).

Ограничения: соблюдать условия использования сайтов и разумную частоту запросов. Пункты про устройство API наш.дом.рф и защиту cian.ru выше являются гипотезами, их нужно проверить на шаге 1.
