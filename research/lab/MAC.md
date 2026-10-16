# Запуск на Mac: то, что нельзя сделать в облачном контейнере

## 1. ru-marketplace-mcp (ЦИАН через ваш Chrome, только чтение)

Источник: <https://github.com/Vladimir-Human/ru-marketplace-mcp>. Подробная настройка Chrome — в его `docs/CDP_SETUP.md`.

1. Склонировать репозиторий и установить его по README:
   ```bash
   git clone https://github.com/Vladimir-Human/ru-marketplace-mcp ~/ru-marketplace-mcp
   ```
   Команды установки берутся из README, они меняются от версии к версии.
2. Запустить отдельный профиль Chrome с портом отладки только на localhost (по `docs/CDP_SETUP.md`):
   ```bash
   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --remote-debugging-port=9222 --user-data-dir="$HOME/.chrome-mcp"
   ```
3. Подключить MCP-сервер к Claude Code на Mac по README репозитория.
4. **Проверка.** Сервер видит вкладку (`curl -s http://127.0.0.1:9222/json/version`), страница ЦИАН открывается в этом окне без проверки на бота.

**Правила те же, что у пайплайна:**
- только чтение;
- паузы 20–40 с;
- капчу не решаем — по README у сервера есть передача проверки человеку (`CHROME_CHALLENGE_HANDOFF_S`), её проходит человек, не автоматика;
- при 403/429 — стоп до завтра.

## 2. Camoufox (если E2 понадобится, а домены GitHub так и не открыты)

```bash
python3 -m venv ~/lab && . ~/lab/bin/activate
pip install -U "camoufox[geoip]" && camoufox fetch
python -c "from camoufox.sync_api import Camoufox; b=Camoufox(headless=True).__enter__(); p=b.new_page(); p.goto('https://example.com'); print(p.title())"
```

Вместо этого можно добавить `api.github.com` и `objects.githubusercontent.com` в Allowed domains облачного окружения. Тогда Camoufox встанет прямо здесь.
