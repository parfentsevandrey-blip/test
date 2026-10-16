# Лаборатория (`research/lab/`)

Отдельное окружение для экспериментов E1–E7. Рабочий пайплайн (`research/ndr/…`, scratchpad `lots/`) его не использует. Прод-скрипты меняются только после выигравшего эксперимента и двойной проверки (`research/OPTIMIZATION_PLAN.md`).

- **Python:** `research/lab/.venv` (Python 3.13). Версии всех пакетов — в `requirements.lock`. Сам venv, бинарники, профили, HAR и прогоны в репозиторий не попадают (`.gitignore`).
- **Активация:** `. research/lab/.venv/bin/activate`

## Инструменты (установлены 16.10.2026, команды — из README каждого репозитория)

| Инструмент | Версия | Команда установки по README | Статус в облачном контейнере |
|---|---|---|---|
| CloakBrowser | 0.6.3, Chromium 146.0.7680.177 | `pip install cloakbrowser`; бинарник (~200 МБ) скачивается при первом запуске | ✅ Запускается. Предупреждает, что для маскировки под Windows на Linux неполный набор шрифтов (README → «Font setup on Linux») — учесть в E1 |
| Camoufox | 0.5.8 (пакет) | `pip install -U "camoufox[geoip]"`, `camoufox fetch` | ⛔ **Браузер не скачан:** сетевая политика окружения отвечает 403 на `api.github.com` и `github.com/…/releases` (`camoufox sync` / `fetch`). Пакет стоит; нужен домен `api.github.com` (и `objects.githubusercontent.com` для загрузки) в Allowed domains окружения — или запуск на Mac |
| Crawlee for Python | 1.11.0 | `python -m pip install 'crawlee[all]'`; `playwright install` **не запускался** (в контейнере браузеры уже есть: `PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers`) | ✅ Импортируется |
| rebrowser-patches | `rebrowser_playwright` 1.52.0 | README: для Python — пакет `rebrowser-playwright` (`pip install rebrowser-playwright`) | ✅ Импортируется. Браузер — из `/opt/pw-browsers` через `executable_path` |
| TRAWL | образ `ghcr.io/germondai/trawl:latest` (1,7 ГБ), `redis:8.10.2-alpine` | `cp .env.example .env`, `docker compose up -d` (`research/lab/trawl/`) | ✅ Docker в контейнере работает: `dockerd` запускается вручную, образы скачаны. Не запускался — это E3. Живёт, только пока сессия активна |
| mitmproxy | 12.2.3 | Документация: `pipx install mitmproxy` / `pip install mitmproxy` | ✅ `mitmdump --version` |
| mitmproxy2swagger | 0.15.0 | `pip install mitmproxy2swagger` | ✅ Принимает HAR (`-f har`) |
| Splink | 5.0.0 | `pip install splink` (DuckDB по умолчанию) | ✅ |
| Pandera | 0.34.1 | `pip install 'pandera[pandas]'` | ✅ |
| Prefect | 3.8.8 | `pip install -U prefect` | ✅ `prefect version` |
| Typst | 0.15.1 | README: `cargo install --locked typst-cli` (релизы с GitHub закрыты сетевой политикой) | ✅ Собран из исходников: `research/lab/bin/typst/bin/typst` |
| Evidence | CLI 0.10.2 | README: `curl -fsSL https://evidence.studio/install.sh \| sh`. Скрипт прочитан перед запуском; запущен с `EVIDENCE_TELEMETRY_DISABLED=1 DO_NOT_TRACK=1` (скрипт отправляет событие установки, если телеметрия не отключена) | ✅ `/usr/local/bin/evidence` (установщик под root игнорирует каталог из аргумента) |
| ru-marketplace-mcp | — | Требует ваш Chrome (ЦИАН через ваш браузер) | 🍎 Только Mac: см. `MAC.md` |
| telegram-mcp | — | По вашим словам, уже установлен; README предупреждает: не ставить через `pip install telegram-mcp`/`uvx` (на PyPI другой проект), только клон + `uv sync` | ⏸ Ждёт ключей в переменных окружения (`TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_SESSION_STRING`); в репозиторий не попадают |

## Сеть

Закрыто политикой окружения (403 от прокси): `api.github.com`, `github.com/<owner>/<repo>/releases`. Остальное, что понадобилось, открыто:
- `git clone` с GitHub;
- PyPI;
- crates.io;
- Docker Hub и ghcr.io;
- evidence.studio;
- cloakbrowser.dev.
