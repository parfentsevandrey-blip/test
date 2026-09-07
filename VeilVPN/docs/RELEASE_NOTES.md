## Veil — Tor + Snowflake client for macOS 26 Tahoe

Liquid Glass UI, bundled Tor Expert Bundle (universal: Apple silicon + Intel), Snowflake / obfs4 / custom bridges, exit-country selection, live circuit and throughput, automatic system proxy.

### New in 0.5.0
- **Network that "works" but doesn't** — after sleep, or after another VPN disconnects, macOS keeps reporting a live network while nothing gets through until Wi-Fi is toggled. Veil now checks the Internet (TCP to public resolvers plus a name lookup) before starting Tor and after every wake; when the check fails it switches Wi-Fi off and on itself (CoreWLAN, then `networksetup`, then a network-service restart via SystemConfiguration), waits for the network, and only then starts Tor. A transport that stalls on a "working" network triggers the same reset once and is retried. Long sleeps restart Tor outright, Tor never goes dormant, and "Reset Network" (⇧⌘R) is available in the menu, the menu bar, the dashboard and Settings → Network.
- **Dashboard** — the home screen is a live, interactive board: an animated route (Mac → bridge → relays → Internet) where particles follow the real download/upload/padding rates, nodes light up as Tor bootstraps, hover shows details and clicks jump to the right screen; a red fast lane shows YouTube bypassing Tor; the kill switch is drawn as a barrier. Tiles for throughput (live sparkline), session (new identity on click), route latency gauge (Tor check on click), protection, traffic padding (equaliser + toggle), routing donut, YouTube speed, apps and network state.
- **Telegram** — the Telegram app ignores the system proxy and never notices Tor. One click ("Add Veil's proxy to Telegram" on the dashboard or in Settings → Sites) hands Telegram a `tg://socks` link that adds Veil as its SOCKS5 proxy, so it connects through Tor.
- **App Store, iCloud and updates** go direct by default (new "Apple" preset in Settings → Sites) because Tor exits break them; switch it to Tor if you prefer.
- Direct (anti-throttling) connections and reachability probes explicitly avoid the system proxy, so they can never loop back into Veil.

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

### Новое в 0.5.0
- **Сеть, которая «есть», но не работает** — после сна или после отключения другого VPN macOS показывает рабочую сеть, но ничего не проходит, пока не выключить и включить Wi-Fi. Теперь Veil проверяет интернет (TCP к публичным резолверам плюс DNS-запрос) перед запуском Tor и после каждого пробуждения; если проверка не проходит, сам выключает и включает Wi-Fi (CoreWLAN, затем `networksetup`, затем перезапуск сетевой службы через SystemConfiguration), ждёт сеть и только потом запускает Tor. Транспорт, зависший на «рабочей» сети, один раз вызывает тот же сброс и пробуется снова. После долгого сна Tor перезапускается целиком, Tor больше не уходит в спящий режим, а «Сбросить сеть» (⇧⌘R) есть в меню, строке меню, на дашборде и в Настройках → Сеть.
- **Дашборд** — главный экран стал живой интерактивной панелью: анимированный маршрут (Mac → мост → узлы → интернет), где частицы движутся со скоростью реальной загрузки/отдачи/маскировки, узлы загораются по мере загрузки Tor, наведение показывает детали, а клик ведёт на нужный экран; красная «быстрая полоса» показывает YouTube в обход Tor; kill switch нарисован как барьер. Плитки: скорость (живой спарклайн), сессия (новая личность по клику), задержка маршрута (проверка Tor по клику), защита, маскировка трафика (эквалайзер и переключатель), маршрутизация, скорость YouTube, приложения и состояние сети.
- **Telegram** — приложение Telegram игнорирует системный прокси и не замечает Tor. Одна кнопка («Добавить прокси Veil в Telegram» на дашборде или в Настройках → Сайты) передаёт Telegram ссылку `tg://socks`, которая добавляет Veil как SOCKS5-прокси, и Telegram работает через Tor.
- **App Store, iCloud и обновления** по умолчанию идут напрямую (новый пресет «Apple» в Настройках → Сайты), потому что выходные узлы Tor их ломают; при желании переключите на Tor.
- Прямые (антизамедление) соединения и проверки доступности принудительно не используют системный прокси, поэтому не могут зациклиться на самом Veil.

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
