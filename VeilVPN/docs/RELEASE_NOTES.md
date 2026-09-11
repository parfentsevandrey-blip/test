## Veil — Tor + Snowflake client for macOS 26 Tahoe

Liquid Glass UI, bundled Tor Expert Bundle (universal: Apple silicon + Intel), Snowflake / obfs4 / custom bridges, exit-country selection, live circuit and throughput, automatic system proxy.

### New in 0.7.0 — warm start, measured circuits, a Security section, a dashboard that diagnoses

**Connecting is now one command, not a rebuild.** Veil keeps a single `tor` process loaded but held
offline — `DisableNetwork 1` and no SOCKS listener, which is observationally identical to Tor not
running — so pressing Connect no longer pays for a process spawn, a control handshake and a reload of
the consensus and relay descriptors. Which transport to try first comes from Tor's own `state` file
(the guards it actually confirmed on this machine) and from what has worked on *this* network before,
rather than from a four-second probe placed in front of everything; that probe now runs alongside the
attempt and only reorders what is left. Each attempt is supervised per stage from the events Tor
pushes, so a doomed one dies in seconds instead of burning a flat stall budget — while a slow but
working one is protected by four escapes (bytes arriving, a circuit event, a relay connection
succeeding, and a floor under every stall). Cancelling, disconnecting and switching transport now cost
one command rather than up to nine seconds of shutdown, and `AvoidDiskWrites` is gone so the guard
selection Tor learned actually survives to the next run. Fast connect has three settings: off,
standby (the default), and connect at launch — the last one is labelled plainly, because it puts Tor
traffic on the wire before you asked for it.

**Latency during a session, without touching anything already open.** Veil holds several measured
circuits open at once. Each is a stable SOCKS username/password pair, and Tor's `IsolateSOCKSAuth`
guarantees two streams with different pairs never share a circuit — so four pairs held open *are*
four parallel circuits, with no control-port commands at all. Every circuit is probed continuously
against the same target, and each new connection is sent down one of the fast ones, picked at random
among those within 25 % of the best so a single circuit never congests. Sites are pinned to their
circuit, so an exit IP does not change under a logged-in session. A connection still slow after about
a second is quietly raced on a second circuit; the threshold has a floor, so the common case is never
touched. Replacing a circuit is a key change: it cannot reach a socket that is already open, which is
why a download in flight never notices. The old exit pinning is off by default now — it collapsed
every circuit onto the same two relays, which is both slower under load and a stable pseudonym.

**Security is now its own section.** One screen answers "how protected am I right now?", scoring six
adversaries — someone on your network, your provider, the exit relay, traffic analysis, software on
this Mac, someone with this Mac — each capped by what a userspace proxy can structurally reach. The
score therefore cannot reach 100, and the screen says why. Every finding names what it costs and, where
there is an honest fix, offers one button; the entry-guard row deliberately offers none, because
clearing it would be worse for anonymity, not better. Three presets show the score they would produce
before you commit. A self-test proves the claims instead of asserting them: it opens two circuits with
different isolation keys, resolves a name through Tor rather than on this Mac, and reads back what
macOS actually has configured. New protective controls live here: cutting connections that are already
open when the kill switch engages, blocking plain HTTP through Tor, deferring the update check until
Tor is up, redacting addresses from exported diagnostics, and choosing what is forgotten when Veil
quits. Country exclusions are no longer silently ignored when multihop is off.

**Home is a ledger, not a diagram.** The floating waves and running dots are gone. In their place are
twelve named stages of the path a request takes — network, Tor reachable, guard link, bootstrap,
circuit, measured circuits, exit verified, system proxy, local proxy, throughput, latency, direct
routing — each judged against thresholds that live in one file. Exactly one row is marked at a time:
the bottleneck, chosen by severity and then by chain order, because a downstream number is meaningless
when an upstream stage is broken. A failed exit verification outranks everything, since "this is not
going through Tor" is not a slowdown. One sentence at the top states the verdict, a detail pane shows
the facts behind the selected row and the actions that address it, and every value that is older than
its own refresh interval says so. Idle is rendered as idle rather than as motion: at zero bytes per
second the old animation still glided a comet. The whole screen ticks once a second on data, not
twenty-four times a second on a clock.

### Fixed in 0.6.1 — honest latency, quick wake
- **Latency no longer jumps at random.** The figure used to be the wall time of a full HTTPS fetch from check.torproject.org — a TLS handshake plus a slow, busy server, sampled once. One bad moment became "the" latency. Veil now measures the route itself: a SOCKS5 CONNECT through Tor to an anycast address, timed alone, three samples every 25 seconds against one target at a time, and the tile shows the **median of the last twelve**. Spikes appear as a jitter figure next to it instead of replacing the number, and the exit check goes back to being only an exit check.
- **Waking up is quick again.** A long sleep no longer restarts Tor by itself: Veil opens one real stream through the tunnel, and if traffic flows nothing is touched at all. Only a tunnel that actually fails is restarted. The network check that the wake path just ran is reused by the connection attempt instead of being repeated (that duplicate alone cost up to half a minute), the Wi-Fi reset will not fire twice within 90 seconds, and each transport now gets a bounded budget — a transport that worked last time gets a short leash (70 s, 28 s without progress) rather than two minutes, with an overall four-minute ceiling on the whole attempt.
- **Nothing can hang silently.** Every control-port command has a deadline; a control connection that stays silent is reported and closed instead of stranding the app. Shutting Tor down waits at most three seconds for it. The connecting panel shows how long the attempt has been running.

### New in 0.6.0 — faster routes, seamless multihop
- **Circuit races** — after connecting and after every route change Veil asks Tor for four extra circuits within your chosen countries, times how fast each one comes up (a circuit that builds fast runs through relays that answer fast), and pins the two quickest exits — and the quickest middle relay when you chose its country. Near-ties go to the relay with far more capacity. Relays are measured again every half hour, on "Measure again", and whenever the pinned ones stop answering (then Tor picks relays on its own again). The Route screen shows the pinned relays with their build times and the latency before → after; the dashboard latency tile shows the same.
- **Seamless route switching** — changing the exit or middle country, the exclusions or a timed rotation no longer sends NEWNYM. Tor gets the new constraints, Veil pre-builds the first circuit of the new route and shows it as soon as it is up; connections already open keep their old circuits until they finish, new ones take the new route at once. Rotation and "New Identity" steer away from the previous relays so the route really moves. The old behaviour (drop everything) is a switch.
- **Conflux in latency mode** — Tor builds two legs to the exit and now prefers the lower-latency leg for sending (`ConfluxClientUX latency`) instead of the higher-throughput one.
- **Several Snowflake proxies at once** — the Snowflake client keeps two volunteer proxies (configurable 1–4) so a slow one no longer holds the session back.

### Refined in 0.5.1
- **Calmer dashboard** — neutral glass tiles with a hairline edge and small-caps headers, no more saturated colour blocks; readouts no longer get cut off.
- **A different traffic animation** — instead of swarms of dots, a few soft comets of light glide along the route: download toward the Mac, upload toward the Internet, at an unhurried pace (about 9–18 s per pass). Throughput changes their length and brightness, not their speed. Padding is a faint violet stipple drifting along the line, the Internet node sends out a slow ripple, and the YouTube bypass is a thin arc with drifting dashes.
- Latency is shown as a thin scale with a knob; traffic padding as a slow layered wave instead of equaliser bars.

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

### Исправлено в 0.6.1 — честная задержка, быстрое пробуждение
- **Задержка больше не скачет случайно.** Раньше это было время полной загрузки страницы с check.torproject.org: TLS-рукопожатие плюс медленный загруженный сервер, и всего один замер. Одна неудачная секунда становилась «той самой» задержкой. Теперь Veil измеряет сам маршрут: SOCKS5-подключение через Tor к anycast-адресу, замеряется только оно, три пробы каждые 25 секунд к одной цели, а плитка показывает **медиану последних двенадцати**. Всплески отображаются рядом как джиттер, а не подменяют число; проверка выхода снова только проверяет выход.
- **Пробуждение снова быстрое.** Долгий сон больше сам по себе не перезапускает Tor: Veil открывает один настоящий поток через туннель, и если трафик идёт — ничего не трогается. Перезапуск только если туннель действительно не работает. Проверка сети, только что выполненная при пробуждении, переиспользуется попыткой подключения, а не повторяется (один этот дубль стоил до полуминуты), сброс Wi-Fi не срабатывает дважды за 90 секунд, а каждому транспорту теперь отведён ограниченный бюджет: сработавший в прошлый раз получает короткий поводок (70 с, 28 с без прогресса) вместо двух минут, и вся попытка ограничена четырьмя минутами.
- **Ничто не зависает молча.** У каждой команды управляющего порта есть срок; молчащее управляющее соединение закрывается с сообщением, а не подвешивает приложение. Остановка Tor ждёт его не дольше трёх секунд. На панели подключения виден таймер попытки.

### Новое в 0.6.0 — быстрее маршрут, бесшовный multihop
- **Гонка цепочек** — после подключения и после каждой смены маршрута Veil просит Tor построить четыре дополнительные цепочки в выбранных странах, измеряет, как быстро каждая поднимается (быстро собирающаяся цепочка идёт через быстро отвечающие узлы), и закрепляет два самых быстрых выходных узла, а при выбранной стране промежуточного узла и самый быстрый промежуточный. При почти равном времени предпочтение узлу с заметно большей пропускной способностью. Узлы измеряются заново каждые полчаса, по кнопке «Измерить снова» и когда закреплённые перестают отвечать (тогда Tor снова выбирает узлы сам). На экране «Маршрут» видны закреплённые узлы с временем сборки и задержка до → после; плитка задержки на дашборде показывает то же.
- **Бесшовное переключение маршрута** — смена выходной или промежуточной страны, исключений и ротация по таймеру больше не шлют NEWNYM. Tor получает новые ограничения, Veil заранее строит первую цепочку нового маршрута и показывает её, как только она готова; открытые соединения остаются на старых цепочках, пока не завершатся, новые сразу идут по новому маршруту. Ротация и «Новая личность» уводят от прежних узлов, чтобы маршрут действительно менялся. Старое поведение (сбросить всё) осталось переключателем.
- **Conflux в режиме задержки** — Tor строит два плеча до выходного узла и теперь отправляет данные по плечу с меньшей задержкой (`ConfluxClientUX latency`), а не по более широкому.
- **Несколько Snowflake-прокси одновременно** — клиент Snowflake держит два волонтёрских прокси (настраивается 1–4), чтобы медленный не тормозил сессию.

### Доработано в 0.5.1
- **Спокойнее дашборд** — нейтральное стекло плиток с тонкой кромкой и заголовками капителью, без насыщенных цветных блоков; показания больше не обрезаются.
- **Другая анимация трафика** — вместо роя точек по маршруту скользят несколько мягких «комет» света: загрузка к Mac, отдача к интернету, неторопливо (около 9–18 с на проход). Скорость трафика меняет их длину и яркость, а не темп. Маскировка — лёгкая фиолетовая рябь вдоль линии, узел «Интернет» изредка пускает медленную волну, обход YouTube — тонкая дуга с плывущим пунктиром.
- Задержка показана тонкой шкалой с бегунком, маскировка трафика — медленной слоистой волной вместо эквалайзера.

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
