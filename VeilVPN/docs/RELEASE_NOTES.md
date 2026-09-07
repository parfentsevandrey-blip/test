## Veil — Tor + Snowflake client for macOS 26 Tahoe

Liquid Glass UI, bundled Tor Expert Bundle (universal: Apple silicon + Intel), Snowflake / obfs4 / custom bridges, exit-country selection, live circuit and throughput, automatic system proxy.

### New in 0.4.0
- **Kill switch (fail closed)** — the system proxy is armed before Tor is up and stays pointed at Veil if Tor dies, so proxied apps are blocked instead of leaking. Reconnect or "Restore network" from the banner.
- **Automatic transport** — checks whether Tor is reachable directly, then tries the last working transport, your bridges, Snowflake, obfs4 and meek until one bootstraps; stalled attempts are skipped.
- **Auto-reconnect** after network changes and sleep, with a circuit health check.
- **Bridges from the Tor Project** (Moat / rdsys), directly or through a domain-fronted CDN, and a direct-reachability probe.
- **YouTube**: automatic selection of the fastest anti-throttling technique, homepage speed in the check, per-site presets (Discord, Telegram, Twitch, Instagram, Facebook, X, Signal, RuTube).
- **Privacy**: separate Tor circuit per site (IsolateDestAddr), routing counters in Activity.
- **App**: onboarding, compact mini window (⇧⌘M), route map, menu-bar sparkline, notifications, haptics and sounds, in-app update check, settings export/import, diagnostics report, Shortcuts actions, more keyboard shortcuts.
- **Project**: unit tests run in CI before every DMG; a weekly job watches for new Tor Expert Bundle versions.
- **Languages**: the interface is now available in English, Russian, Ukrainian, Persian and Simplified Chinese.

### Fixed in 0.3.1
- **Traffic padding** could not reach its private onion service. Veil now waits for Tor's `HS_DESC UPLOADED` event before connecting, retries with realistic intervals, and speaks SOCKS5 to Tor itself so `.onion` names (and every other host name) are resolved by Tor, never locally. The HTTP bridge and the Tor check use the same path, which also rules out DNS leaks.

### New in 0.3.0
- **YouTube** — choose how YouTube travels: through Tor, directly with anti-throttling (the TLS ClientHello is fragmented at the SNI so throttling DPI cannot read the host name, like GoodbyeDPI/ByeDPI), or plainly direct. Four fragmentation techniques, a list of other domains that bypass Tor, and a built-in check.
- **YouTube Turbo** — the anti-throttling proxy without Tor at all: only YouTube goes through Veil, everything else is untouched.

### New in 0.2.0
- **Traffic padding (DAITA-style)** — dummy traffic bounced through Tor to a private onion service on your Mac hides the shape of your real traffic from an observer on the local network: background noise, FRONT-style bursts on activity, optional constant-rate mode. Tor's own circuit/connection padding is forced on too.
- **Multihop** — a route card with a live diagram (Mac → Snowflake → middle → exit → Internet), a middle-hop country, per-country exclusions, a "Five Eyes" preset, timed route rotation and the measured route latency.

### Install
1. Open the DMG and drag **Veil.app** to *Applications*.
2. The build is ad-hoc signed (no Apple Developer certificate): on first launch open **System Settings → Privacy & Security → Open Anyway**, or run `xattr -cr /Applications/Veil.app`.
3. Press the power button. macOS asks for an administrator password once to switch the system proxy.

### Новое в 0.4.0
- **Kill switch** — системный прокси включается до запуска Tor и остаётся направленным на Veil, если Tor упадёт: приложения с прокси блокируются, а не утекают. Переподключение или «Вернуть сеть» одной кнопкой.
- **Автоматический транспорт** — проверка прямой доступности Tor, затем последний сработавший транспорт, ваши мосты, Snowflake, obfs4 и meek по очереди; зависшие попытки пропускаются.
- **Автопереподключение** после смены сети и сна с проверкой цепочек.
- **Мосты от Tor Project** (Moat / rdsys) напрямую или через CDN с domain fronting, проверка прямой доступности.
- **YouTube**: автоподбор самой быстрой техники обхода, скорость в проверке, пресеты для сайтов (Discord, Telegram, Twitch, Instagram, Facebook, X, Signal, RuTube).
- **Приватность**: отдельная цепочка на каждый сайт (IsolateDestAddr), счётчики маршрутизации в «Активности».
- **Приложение**: онбординг, компактное мини-окно (⇧⌘M), карта маршрута, спарклайн в строке меню, уведомления, тактильный отклик и звуки, проверка обновлений, экспорт/импорт настроек, отчёт диагностики, действия для Shortcuts, новые сочетания клавиш.
- **Проект**: юнит-тесты в CI перед каждой сборкой DMG; еженедельная проверка новой версии Tor Expert Bundle.
- **Языки**: интерфейс на английском, русском, украинском, персидском и упрощённом китайском.

### Исправлено в 0.3.1
- **Маскировка трафика** не могла достучаться до своего onion-сервиса. Теперь Veil ждёт событие Tor `HS_DESC UPLOADED` перед подключением, повторяет попытки с разумными интервалами и сам говорит с Tor по SOCKS5, поэтому `.onion`-имена (и вообще любые имена хостов) резолвит Tor, а не macOS. HTTP-мост и проверка Tor идут тем же путём — заодно исключены утечки DNS.

### Новое в 0.3.0
- **YouTube** — выбор пути для YouTube: через Tor, напрямую с обходом замедления (TLS ClientHello дробится по SNI, чтобы замедляющий DPI не прочитал имя хоста — как GoodbyeDPI/ByeDPI) или просто напрямую. Четыре техники фрагментации, список других доменов в обход Tor и встроенная проверка.
- **YouTube Turbo** — прокси с обходом замедления вообще без Tor: через Veil идёт только YouTube, остальное не трогается.

### Новое в 0.2.0
- **Маскировка трафика (в стиле DAITA)** — пустой трафик, проходящий через Tor до приватного onion-сервиса на вашем Mac, скрывает форму реального трафика от наблюдателя в локальной сети: фоновый шум, всплески в стиле FRONT при активности, режим постоянной скорости. Также принудительно включён circuit/connection padding самого Tor.
- **Многократный переход** — карточка маршрута с живой схемой (Mac → Snowflake → промежуточный → выходной → интернет), выбор страны промежуточного узла, исключение стран, пресет «Пять глаз», смена маршрута по таймеру и измеренная задержка.

### Установка
1. Откройте DMG и перетащите **Veil.app** в *Программы*.
2. Сборка подписана ad-hoc: при первом запуске откройте **Системные настройки → Конфиденциальность и безопасность → Всё равно открыть**, либо выполните `xattr -cr /Applications/Veil.app`.
3. Нажмите кнопку питания. macOS один раз спросит пароль администратора для переключения системного прокси.

Requires macOS 26 Tahoe.
