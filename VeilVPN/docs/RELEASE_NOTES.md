## Veil — Tor + Snowflake client for macOS 26 Tahoe

Liquid Glass UI, bundled Tor Expert Bundle (universal: Apple silicon + Intel), Snowflake / obfs4 / custom bridges, exit-country selection, live circuit and throughput, automatic system proxy.

### Fixed in 0.7.6 — fewer bounces, budgets that follow the clock
- **A network flap no longer cuts a working tunnel.** When the path was lost and came back while connected, a full reconnect was scheduled regardless — every circuit dropped for a Wi-Fi hiccup or an interface flap that had cost nothing. One probe after the path settles now decides, and only a tunnel that actually fails is bounced.
- **Budgets follow the clock, not the moment tor was warmed.** A standby can sit for hours; the consensus that was fresh when it started has a fixed lifetime, and the stage budgets set from the old freshness were too tight for the directory fetch the next connect had to make — a false stall on the first connect of the day. Freshness is now re-read from the clock at connect time.
- **The hard timeout spares an attempt that is still moving.** A directory fetch over a slow bridge could outlive the budget while bytes were still arriving. Past the budget an attempt is now abandoned only once it has also gone twenty seconds without progress, or has run twice the budget.
- **Ports picked hours ago are re-validated before use.** The SOCKS, HTTP and lane ports are chosen when the standby starts; anything on the Mac may take one meanwhile, and a taken lane port used to fail the whole connect. Each unbound port is checked and re-picked right before it is used, and a listener that fails to bind at activation is re-picked too.
- **"Connecting" counts as launched.** On a slow disk the consensus parse could hold the cold path past the launch deadline before any relay connection was even attempted; tor's own "connecting to a relay" now satisfies it, and the direct-connection deadline gains two seconds.
- **A first connection to a site gets 25 seconds, not 10.** Building a circuit, resolving the name at the exit and a slow server in a row could exceed the bridge's old deadline, and the browser then showed an error for a page that would have loaded. Onion services get 45.

### New in 0.7.5 — a browser that reveals less
- **Hardened browser window** (Security → Controls). What a site sees inside HTTPS — the browser and its version, the operating system, the languages, WebRTC's view of your address — is the browser's to reveal; a proxy cannot reach into an encrypted connection to change it, which is why an IP-check site still names your Mac and your Chrome behind a Tor exit. Veil now opens the browsers already on the Mac the way Tor Browser is built: a separate profile that goes through Veil only, WebRTC kept off the real address, QUIC off, and a generic identity — Firefox through its own fingerprinting resistance (`privacy.resistFingerprinting`: a Windows Firefox, a UTC clock, a standard screen, no WebGL), Chromium browsers (Brave, Chrome, Edge, Vivaldi, Chromium) through a fixed Windows user agent with client hints switched off. Nothing in the everyday profile is touched. The address is still a Tor exit, and every site can tell that it is one: that is Tor working, not a leak.

### New in 0.7.4 — YouTube through Tor, wide
- **One stream, two circuits.** Conflux was switched off whenever its latency mode was off. It is now always on, and by default in throughput mode: Tor builds two legs to the exit and the exit spreads a single stream over both — the only way one TLS connection, a video player's for instance, gets more than one circuit's share. Latency mode remains a switch in Settings → Route.
- **A wide exit for video.** A random exit is a random share of a random pipe. With YouTube through Tor, Veil now reads the consensus after connecting, takes the exits with the highest measured capacity that fit the route's country rules, and maps every YouTube host to the first one that actually carries a stream there (`MapAddress … .exit`, per destination — no global `ExitNodes`, no effect on any other site). The exit is re-verified every fifteen minutes and replaced if it stops answering. Settings → YouTube shows which relay it is; the switch is there too.
- **The whole YouTube session leaves through one exit.** YouTube binds its media URLs to the address that fetched the page, so the player on youtube.com and the video on googlevideo.com must share an exit — which, with a measured circuit per site, they did not. The YouTube host family is now one site for the lane pool, as Tor Browser's first-party isolation would have it.
- YouTube stays through Tor by default; the direct anti-throttling path is a choice in Settings → YouTube, and a choice made there is now remembered as one.

### Fixed in 0.7.3 — the probe advises, the relay decides
- **A censored network no longer gets its Wi-Fi reset for working.** The connectivity probe reaches public resolvers and a few well-known hosts; a network that blocks those can still carry Snowflake, whose rendezvous takes longer than the probe does. A "no Internet" verdict used to cut the attempt on the spot and then reset Wi-Fi. It now only shortens the attempt's leash — six seconds for a relay to answer — and a relay that answers, or real bytes arriving, overrules it: no abort, no Wi-Fi reset. The reset still fires where it belongs, on an attempt that reached nothing at all.
- **A relay answering counts as progress.** After a network hold the percentage stands still while tor talks to the guard; that conversation is now progress for the watchdog, so a slow guard is no longer mistaken for a stall.
- **Waking up asks the tunnel again once Wi-Fi is back.** After sleep the first health probe runs while Wi-Fi is still reassociating and fails for that reason alone. Once the network repair reports the Internet back, the tunnel is asked once more, and a guard connection that survived the nap is left alone instead of being bounced.
- **A tor that is still releasing its data-directory lock no longer fails the launch.** The cold path waits for a killed process to be gone before spawning, and retries once, a second later, if the lock is still held.

### Changed in 0.7.2 — the dashboard is the 0.6.1 one again
- **Home is back to the route diagram and the tiles.** The twelve-stage ledger that 0.7.0 introduced is gone; the dashboard is once more the power button with its status and chips, the live route (Mac → bridge → relays → Internet), and the tiles for speed, session, latency, protection, padding, routing, YouTube, apps and network. Everything under it — warm start, measured circuits, the Security section, the 0.7.1 connection fixes — is unchanged.

### Fixed in 0.7.1 — a connection that is only "connected" once a circuit exists
- **No more "connected" with nothing behind it.** After a hold — every disconnect, every failed transport, every soft reconnect keeps Tor loaded and offline — Tor's bootstrap counter and its `status/circuit-established` flag both stay at their high-water mark and say "done" before a single circuit exists. The second connect of a session could therefore flip to green in three seconds with no route under it, then watch its first page load stall. Veil now accepts "done" from a re-activated Tor only once a circuit has actually been built (a `CIRC … BUILT` event, or an open circuit in `circuit-status`, which lists live circuits alone), polls a second after activation instead of three, and brings the poll forward the moment a circuit completes.
- **A disconnect racing the launch standby no longer strands the next connect.** Stopping Tor while its control handshake was still in flight left that handshake retrying against nothing for fifteen seconds, and a Connect pressed meanwhile joined it and inherited its failure. A stop now ends any handshake in progress at once, and a connect that joined a warm-up which died underneath it starts its own instead of reporting someone else's error.
- **Standby comes back after a failure or a plain disconnect.** When the cold path had to be used — or a failure tore Tor down — nothing was reloaded afterwards, so the next Connect, including the automatic one fifteen seconds later, spawned Tor from scratch. Tor is warmed again two seconds after either, and a warm-up that Connect joined and went live with no longer overwrites the live state with "ready".
- **Fewer probes on a shared first hop.** Snowflake and meek carry every circuit through one volunteer proxy or one CDN front, so four warm-up probes there competed with the first page load for the link that is already the bottleneck. Those transports get two measured circuits; obfs4 and direct keep the configured number.
- **The control-silence limit is twelve seconds again.** The planner was still passing the old six-second value under the watchdog's relaxed default, so a main thread busy for six seconds could read as a dead Tor and abort a working attempt.

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
macOS actually has configured. Every protective control now has exactly one home here, multihop included:
pinning the middle relay's country and the timed route rotation sit next to the kill switch rather
than on the map, because they are protective choices and not place pickers — the Route screen keeps
the map and the country pickers. New controls: cutting connections that are already open when the
kill switch engages, blocking plain HTTP through Tor, deferring the update check until Tor is up,
redacting addresses from exported diagnostics, and choosing what is forgotten when Veil quits.
Country exclusions are no longer silently ignored when multihop is off.

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

### Исправлено в 0.7.6 — меньше перезапусков, бюджеты по часам
- **Дрожание сети больше не рвёт рабочий туннель.** Если путь пропадал и возвращался при подключённом туннеле, безусловно планировался полный реконнект — все цепочки сбрасывались из-за икоты Wi-Fi или мигания интерфейса, которые ничего не стоили. Теперь одна проба после стабилизации пути решает, и перезапускается только туннель, который действительно не работает.
- **Бюджеты идут по часам, а не по моменту прогрева.** Standby может простоять часы; консенсус, свежий при запуске, живёт ограниченное время, и бюджеты стадий, выставленные по старой свежести, оказывались слишком тесными для загрузки каталога, которую следующему подключению предстояло сделать — ложное «зависание» на первом подключении за день. Свежесть теперь перечитывается по часам в момент подключения.
- **Жёсткий таймаут щадит попытку, которая ещё движется.** Загрузка каталога через медленный мост могла пережить бюджет, пока байты ещё шли. Теперь за пределами бюджета попытка обрывается, только если ещё и двадцать секунд не было прогресса, либо прошло вдвое больше бюджета.
- **Порты, выбранные часы назад, перепроверяются перед использованием.** SOCKS-, HTTP- и порт полос выбираются при старте standby; кто угодно на Mac может занять один из них, и занятый порт полос раньше ронял всё подключение. Каждый ещё не занятый порт проверяется и перевыбирается прямо перед использованием; слушатель, не сумевший привязаться при активации, тоже перевыбирается.
- **«Подключаемся» считается запуском.** На медленном диске разбор консенсуса мог задержать холодный путь дольше срока запуска, ещё до первой попытки соединения с узлом; собственное «подключаемся к узлу» от tor теперь этот срок закрывает, а срок для прямого подключения вырос на две секунды.
- **Первое соединение с сайтом получает 25 секунд вместо 10.** Постройка цепочки, разрешение имени на выходе и медленный сервер подряд могли превысить старый срок моста, и браузер показывал ошибку для страницы, которая загрузилась бы. Onion-сервисы получают 45.

### Новое в 0.7.5 — браузер, который выдаёт меньше
- **Защищённое окно браузера** (Безопасность → Управление). То, что сайт видит внутри HTTPS — браузер и его версию, операционную систему, языки, ваш адрес глазами WebRTC, — выдаёт браузер; прокси не может залезть в зашифрованное соединение и это изменить, поэтому сайт проверки IP по-прежнему называет ваш Mac и ваш Chrome за выходным узлом Tor. Теперь Veil открывает уже установленные браузеры так, как устроен Tor Browser: отдельный профиль, который ходит только через Veil, WebRTC вдали от настоящего адреса, QUIC выключен, личность обобщённая — Firefox через собственную защиту от снятия отпечатка (`privacy.resistFingerprinting`: Firefox под Windows, часы в UTC, стандартный экран, без WebGL), браузеры на Chromium (Brave, Chrome, Edge, Vivaldi, Chromium) — через фиксированный user agent под Windows без client hints. Обычный профиль не затрагивается. Адрес по-прежнему выходной узел Tor, и любой сайт это определит: это Tor работает, а не утечка.

### Новое в 0.7.4 — YouTube через Tor, широко
- **Один поток — две цепочки.** Conflux выключался всякий раз, когда был выключен его режим задержки. Теперь он включён всегда и по умолчанию в режиме пропускной способности: Tor строит два плеча до выходного узла, и выход раскладывает один поток на оба — единственный способ, которым одно TLS-соединение, например видеоплеера, получает больше доли одной цепочки. Режим задержки остался переключателем в Настройки → Маршрут.
- **Широкий выход для видео.** Случайный выход — это случайная доля случайного канала. При YouTube через Tor Veil после подключения читает консенсус, берёт выходы с наибольшей измеренной ёмкостью, подходящие под правила стран маршрута, и привязывает все хосты YouTube к первому, который реально проводит туда поток (`MapAddress … .exit`, по назначению — без глобального `ExitNodes`, без влияния на другие сайты). Выход перепроверяется каждые пятнадцать минут и заменяется, если перестал отвечать. В Настройки → YouTube видно, какой это узел; там же переключатель.
- **Вся сессия YouTube выходит через один выход.** YouTube привязывает адреса медиа к IP, с которого загружена страница, поэтому плеер на youtube.com и видео на googlevideo.com должны делить один выход — а с измеряемой цепочкой на каждый сайт не делили. Семейство хостов YouTube теперь один сайт для пула цепочек, как это делает изоляция по первой стороне в Tor Browser.
- YouTube по умолчанию остаётся через Tor; прямой путь с обходом замедления — выбор в Настройки → YouTube, и сделанный там выбор теперь запоминается как выбор.

### Исправлено в 0.7.3 — проба советует, узел решает
- **Цензурируемой сети больше не сбрасывают Wi-Fi за то, что она работает.** Проба связности стучится к публичным резолверам и паре известных хостов; сеть, которая их блокирует, всё равно может нести Snowflake, чьё рандеву длится дольше пробы. Вердикт «нет интернета» раньше обрывал попытку на месте и затем сбрасывал Wi-Fi. Теперь он лишь укорачивает поводок — шесть секунд, чтобы узел ответил, — а ответивший узел или пришедшие байты отменяют его: ни обрыва, ни сброса Wi-Fi. Сброс по-прежнему срабатывает там, где нужен: на попытке, которая не достучалась ни до чего.
- **Ответ узла считается прогрессом.** После удержания сети проценты стоят на месте, пока tor разговаривает со сторожевым узлом; теперь этот разговор — прогресс для сторожа попытки, и медленный узел больше не принимается за зависание.
- **После пробуждения туннель проверяется повторно, когда Wi-Fi уже вернулся.** Первая проверка после сна идёт, пока Wi-Fi ещё переподключается, и падает только поэтому. Как только ремонт сети сообщает, что интернет есть, туннель спрашивается ещё раз — и соединение со сторожевым узлом, пережившее короткий сон, остаётся нетронутым вместо перезапуска.
- **Tor, ещё не отпустивший блокировку каталога данных, больше не срывает запуск.** Холодный путь ждёт, пока убитый процесс действительно исчезнет, и один раз повторяет запуск секундой позже, если блокировка ещё держится.

### Изменено в 0.7.2 — дашборд снова как в 0.6.1
- **Главная — снова схема маршрута и плитки.** Ступенчатый список из двенадцати стадий, появившийся в 0.7.0, убран; дашборд — это опять кнопка питания со статусом и «чипами», живой маршрут (Mac → мост → узлы → интернет) и плитки скорости, сессии, задержки, защиты, маскировки, маршрутизации, YouTube, приложений и сети. Всё под ним — тёплый старт, измеряемые цепочки, раздел «Безопасность», исправления подключения из 0.7.1 — без изменений.

### Исправлено в 0.7.1 — «подключено» только когда цепочка действительно есть
- **Больше нет «подключено» без маршрута.** После удержания сети — а так заканчивается каждое отключение, каждый неудавшийся транспорт и каждый мягкий реконнект: Tor остаётся загруженным, но офлайн — счётчик bootstrap и флаг `status/circuit-established` остаются на своём максимуме и говорят «готово» до того, как построена хоть одна цепочка. Второе подключение за сессию могло через три секунды стать зелёным без маршрута под ним, а первая страница — зависнуть. Теперь «готово» от повторно активированного Tor принимается только после реально построенной цепочки (событие `CIRC … BUILT` либо открытая цепочка в `circuit-status`, где перечислены только живые), первый опрос идёт через секунду после активации вместо трёх, а завершение цепочки сдвигает опрос вперёд.
- **Отключение, совпавшее со стартовым standby, больше не ломает следующее подключение.** Остановка Tor в момент, когда рукопожатие управляющего порта ещё шло, оставляла его пятнадцать секунд стучаться в пустоту, а нажатое тем временем «Подключить» присоединялось к нему и наследовало его ошибку. Теперь остановка сразу завершает любое идущее рукопожатие, а подключение, присоединившееся к погибшему прогреву, запускает свой вместо чужой ошибки.
- **Standby возвращается после сбоя и после обычного отключения.** Если пришлось идти холодным путём или сбой снёс Tor, дальше ничего не перезагружалось — и следующее подключение, включая автоматическое через пятнадцать секунд, запускало Tor с нуля. Теперь Tor прогревается снова через две секунды в обоих случаях, а прогрев, к которому присоединилось подключение и ушло в работу, больше не перезаписывает живое состояние словом «готов».
- **Меньше проб на общем первом хопе.** Snowflake и meek пропускают все цепочки через один волонтёрский прокси или один CDN-фронт, и четыре разогревающие пробы там конкурировали с первой страницей за канал, который и так узкое место. Эти транспорты получают две измеряемые цепочки; obfs4 и прямое подключение — сколько настроено.
- **Лимит тишины управляющего порта снова двенадцать секунд.** Планировщик всё ещё передавал старые шесть секунд поверх смягчённого значения сторожа, и занятый шесть секунд главный поток мог выглядеть как мёртвый Tor и оборвать рабочую попытку.

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
