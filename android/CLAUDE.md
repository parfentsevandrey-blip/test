# Opal — VPN через Tor (Android)

Рабочее название **Opal / Опал** (опал — аморфный кремнезём, «жидкое стекло» с игрой цветов,
как фон-аврора). Альтернативы: **Сквозь / Skvoz**, **Туман / Tuman**. Переименование: `app_name`
в `app/src/main/res/values/strings.xml`, `tile_label`/`vpn_session_name` в `core/tunnel`, строки
с «Opal» в `feature/*/res` и `applicationId` в `app/build.gradle.kts`.

Приложение заворачивает весь трафик устройства в Tor (C-tor) через VpnService; **по умолчанию —
только Snowflake** (встроенные строки Tor Browser, без гонки и без запроса к Settings API).
Гонка транспортов и Settings API — режим «Авто», включается вручную. Никаких своих серверов:
только инфраструктура Tor Project.

Этот файл — источник истины для архитектурных решений. Меняешь решение — меняй здесь.

## Команды

Все команды — из каталога `android/`. JDK 21, Android SDK (platform 37.0, build-tools 37.0.0,
NDK 30.0.16248370). Путь к SDK — в `local.properties` (`sdk.dir=...`, файл не коммитится).

| Задача | Команда |
|---|---|
| Debug APK | `./gradlew :app:assembleDebug` |
| Release APK (подписанный, ABI-сплиты + universal) | `./gradlew :app:assembleRelease` (нужен `keystore.properties`, см. README) |
| Юнит-тесты (JVM + Robolectric) | `./gradlew test` |
| Скриншот-тесты: записать / сверить | `./gradlew recordRoborazziDebug` / `./gradlew verifyRoborazziDebug` |
| Lint | `./gradlew lint` |
| detekt (+compose-rules) | `./gradlew detekt` |
| Форматирование (ktfmt через Spotless) | `./gradlew spotlessApply` / `spotlessCheck` |
| Baseline/Startup profile (нужно устройство) | `./gradlew :app:generateBaselineProfile` |
| Макробенчмарки (нужно устройство) | `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` |
| Тест нативного моста hev на Linux-хосте | `sudo tools/hev-host-test/run.sh` |
| Обновить встроенные мосты из tor-browser-build | `./gradlew :core:tunnel:updateBuiltinBridges` |
| Перегенерировать проверку зависимостей | `./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease testDebugUnitTest lintDebug detekt spotlessCheck` |

В песочнице разработки Maven Central отвечал 429; локально использовался init-скрипт
`~/.gradle/init.d/maven-central-mirror.init.gradle.kts` (зеркало Google). В репозиторий он не
входит, CI и обычная сборка ходят в `mavenCentral()` напрямую.

## Модули

```
:app                  Application, MainActivity, навигация (Navigation 3), ручной DI (AppGraph),
                      WorkManager-воркер обновления каталога.
:core:model           Чистый Kotlin (JVM): состояния туннеля (sealed), машина состояний,
                      парсер bridge lines, модель torrc (DSL), парсер control-протокола, настройки.
:core:data            DataStore (multi-process) — настройки, кеш мостов/статистика, список приложений.
:core:tunnel          Всё сетевое, процесс :tunnel: VpnService, Tor (JNI), IPtProxy, hev (NDK),
                      control-клиент, гонка транспортов, Circumvention API, watchdog, сеть,
                      уведомления, плитка, AIDL-сервер и клиент (TunnelClient для UI).
:core:designsystem    Liquid Glass: GlassSurface/Button/TabBar/Sheet/Chip, аврора-фон, тема,
                      типографика (Inter), иконки (Material Symbols Rounded), строки.
:feature:home|apps|connection|settings|onboarding   Экраны (Compose + ViewModel, UDF).
:baselineprofile      Генератор Baseline/Startup Profile + Macrobenchmark (com.android.test).
build-logic           Convention-плагины opal.android.application|library|compose|feature, opal.jvm.library.
```

## Процессы и потоки данных

```
 main process (UI)                              :tunnel process
 ┌───────────────────────────┐   AIDL (JSON)   ┌──────────────────────────────────────────────┐
 │ MainActivity (Compose)    │ ──────────────► │ TunnelService : VpnService (FGS systemExempted)│
 │  ViewModels ◄─ TunnelClient│ ◄───────────── │  TunnelController ── ConnectionMachine (reducer)│
 │ DirectoryRefreshWorker     │  onSnapshot()   │   ├─ TorEngine (CTorEngine, libtor.so JNI)     │
 └───────────────────────────┘                  │   │    └─ ControlClient (owning-controller FD)  │
          ▲                                     │   ├─ Transports (IPtProxy: snowflake/obfs4/    │
          │ DataStore (multi-process) ◄───────► │   │    webtunnel/meek_lite, 127.0.0.1:random)  │
                                                │   ├─ HevTunnel (TUN fd → SOCKS Tor, MapDNS)    │
                                                │   ├─ TransportRace, MoatClient, Watchdog       │
                                                │   └─ NetworkMonitor (default network callback) │
                                                │ TunnelTileService (Quick Settings)            │
                                                └──────────────────────────────────────────────┘
```

Падение UI не трогает туннель. Состояние туннеля — `StateFlow<TunnelSnapshot>` в `:tunnel`,
в UI приходит через `ITunnelListener.onSnapshot(json)` (kotlinx.serialization).

## Ключевые решения (ADR)

1. **Ядро — C-tor из `info.guardianproject:tor-android` 0.4.9.12, но без его `TorService`.**
   Из AAR берём только `libtor.so` (Gradle-задача `extractTorNatives`), а JNI-обёртку пишем свою:
   класс `org.torproject.jni.TorService` (имя обязано совпадать с экспортами JNI
   `Java_org_torproject_jni_TorService_*`), это не Android-сервис, а тонкий биндинг.
   Зачем: библиотечный `TorService` жёстко ставит `--CacheDirectory` в `cacheDir` (система может
   очистить → теряем тёплый кеш), делает лишний прогон `--verify-config`, по умолчанию занимает
   фиксированные 9050/8118, тянет устаревший LocalBroadcastManager. Мы кладём DataDirectory и
   CacheDirectory в `filesDir/tor/*` (постоянно) и управляем жизненным циклом сами.
2. **Control-канал — «owning controller» socketpair из tor_api**, без TCP ControlPort и без файла
   сокета: `tor_main_configuration_setup_control_socket()` → Tor получает `__OwningControllerFD`
   (соединение уже аутентифицировано, другие приложения до него не дотянутся физически), при
   закрытии нашего конца Tor завершается. Это строже, чем «ControlPort на 127.0.0.1 + cookie».
3. **Control-клиент — свой, на корутинах** (`ControlClient` + чистый парсер в `:core:model`),
   вместо jtorctl: jtorctl блокирующий (synchronized, поток-диспетчер), многострочные async-события
   обрабатывает построчно; нам нужен Flow событий с тестами на Turbine. Протокол простой (control-spec).
4. **SOCKS Tor — `127.0.0.1:auto`** (случайный порт, узнаём через `GETINFO net/listeners/socks`),
   HTTPTunnelPort/ControlPort не открываются. Порты PT — случайные (IPtProxy слушает `127.0.0.1:0`).
5. **TUN → Tor: hev-socks5-tunnel (vendored, собирается ndk-build из исходников) в режиме MapDNS.**
   DNS получает фиктивные IP из 100.64.0.0/10, TCP уходит в SOCKS уже с доменом — резолв на exit.
   Наш патч (`core/tunnel/src/main/cpp/patches/0001-*.patch`): режим `udp: 'reject'` — любой не-DNS
   UDP получает ICMP/ICMPv6 port unreachable от адреса назначения (сокет сразу получает
   ECONNREFUSED, QUIC откатывается на TCP без таймаута), DNS на порт 53 любого резолвера отвечает
   MapDNS (hijack). Проверено на Linux-ядре (`tools/hev-host-test`, 7/7): MapDNS, hijack, TCP по имени,
   отказ UDP за ~0.2 мс. IPv6-путь на хосте не проверен (ядро песочницы без IPv6).
   Сравнение с `DNSPort`+`AutomapHostsOnResolve`: там каждый DNS-запрос — круг через Tor
   (+RTT на каждое имя), а MapDNS отвечает локально мгновенно и резолвит одновременно с CONNECT.
   Утечек нет в обоих случаях; выбран MapDNS ради скорости.
6. **Маршруты `0.0.0.0/0` и `::/0`**, DNS VpnService = MapDNS-адрес (198.18.0.2). AAAA MapDNS
   отвечает NODATA → приложения идут по IPv4-фиктивным адресам. Собственное приложение исключено
   из туннеля (`addDisallowedApplication`), его сетевые запросы при подключённом Tor идут явно через
   SOCKS Tor; единственное исключение — запрос к Settings API до подключения (domain fronting).
7. **Внутренний kill switch.** TUN поднимается до старта Tor; hev стартует сразу после того, как
   Tor открыл SOCKS-порт (≈100 мс). До готовности цепочек Tor держит потоки в очереди (не отдаёт
   их наружу), UDP отклоняется. Утечек нет ни в одной фазе: мимо TUN трафик не идёт. При
   перезапуске Tor внутри процесса TUN не закрывается (hev получает dup() дескриптора).
   Системные «Постоянная VPN» + «Блокировать соединения без VPN» закрывают окно при смерти процесса.
8. **Процессы:** VpnService, Tor, IPtProxy, hev — в `:tunnel`. Перезапуск Tor в том же процессе
   поддерживается tor_api (сам tor-android запускает `tor_run_main` дважды: verify + run; Orbot
   перезапускает in-process) — это основной путь «жёсткого» рестарта (TUN остаётся поднятым).
   Если повторный старт Tor не удался — процесс `:tunnel` завершается, `START_STICKY`/always-on
   поднимают его заново.
9. **Foreground service type:** `systemExempted` (VPN-приложение с согласием пользователя —
   разрешённый случай; так же делает Orbot). Для горячего резерва (Tor без TUN) при отказе
   системы — фолбэк на `specialUse` (без 6-часового лимита `dataSync` Android 15+).
10. **Транспорты — IPtProxy 5.5.1** (Lyrebird: obfs4, meek_lite, webtunnel; Snowflake 2.14.1;
    covert-dtls v1.5.0 внутри). В Tor: `ClientTransportPlugin <t> socks5 127.0.0.1:<port>`.
    Snowflake-клиент upstream поддерживает `covertdtls-config=`/`covertdtls-fingerprint=`, но обёртка
    Lyrebird (её использует IPtProxy и сам Tor Browser) эти аргументы не пробрасывает — на клиенте
    отпечаток DTLS не настраивается, рандомизация работает на стороне прокси (так и задумано
    командой Snowflake). Проверять при каждом обновлении IPtProxy.
11. **Встроенные мосты** — дословно `projects/tor-expert-bundle/pt_config.json` из tor-browser-build
    (коммит 728c7a37, 2026-09-22) в `core/tunnel/src/main/assets/pt_config.json`; обновление —
    задача `updateBuiltinBridges`. На сегодня обе Snowflake-строки ходят к одному брокеру
    (`1098762253.rsc.cdn77.org`, фронты `app.datapacket.com,www.datapacket.com`) — в разные CDN они
    не разнесены; различаются мосты (fingerprint 2B28…/8838…). Встроенного WebTunnel нет — только
    из Settings API. meek: `meek_lite 192.0.2.20:80 url=https://1603026938.rsc.cdn77.org front=www.phpmyadmin.net`.
12. **Circumvention Settings API (moat)** — `https://bridges.torproject.org/moat/circumvention/{settings,defaults,builtin}`,
    POST, `Content-Type: application/vnd.api+json`, внутри meek_lite (IPtProxy) с аргументом
    `targets=https://1723079976.rsc.cdn77.org|cdn.zk.mk+www.cdn77.com` — ровно как Tor Browser 16.0
    (`extensions.torlauncher.bridgedb_targets`, ветка tor-browser-153.4.0esr-16.0-1). TLS до
    bridges.torproject.org end-to-end внутри meek; DNS-запросов имени моста нет (SOCKS с доменом).
    После подключения — через SOCKS Tor с явной страной.
13. **Гонка транспортов (Авто, по выбору пользователя; по умолчанию — Snowflake, см. п. 30)** —
    на уровне одного Tor через `SETCONF Bridge=...`: старт с победителя
    для типа сети; нет прогресса bootstrap 10 с → добавляются все остальные мосты (параллельная
    загрузка дескрипторов мостов в Tor); победитель определяется по ORCONN (какой мост дал первое
    рабочее соединение/цепочку) → остальные убираются `SETCONF`, победитель запоминается.
    Первый запуск — гонка сразу, параллельно с запросом к Settings API.
14. **UDP** Tor не переносит: DNS → MapDNS, остальное — ICMP unreachable (см. п.5).
15. **DI — ручной** (`AppGraph` в `:app`, `TunnelRuntime` в `:tunnel`): два процесса с разными
    графами, мало зависимостей, нет аннотаций → нет ни kapt, ни KSP, быстрее сборка и старт.
16. **Настройки — DataStore (multi-process, JSON через kotlinx.serialization)**: читают оба процесса.
17. **Дизайн — Backdrop 2.0.1 (Kyant0)** + свои компоненты; формы — `io.github.kyant0:shapes`
    (непрерывные скругления, совместимы с lens-эффектом). Деградация: API 33+ — blur+lens+vibrancy;
    31–32 — только blur; <31 — полупрозрачная подложка + шум + блик. «Упрощённая графика» и
    энергосбережение → статичный фон, без lens.
18. **Упаковка .so** — сжатые (`useLegacyPackaging = true`): для APK под ручную установку размер
    скачивания важнее (Go-библиотека IPtProxy ~24 МБ несжатой на ABI). x86 исключён.
19. **GeoIP** — `geoip`/`geoip6` дословно из исходников tor той ревизии, что закреплена в
    tor-android 0.4.9.12 (`external/tor` @ 7aa3dff, выгрузка IPFire Location DB от 2026-09-08,
    лицензия CC BY-SA 4.0 — указана на экране лицензий), как `assets/*.gz`; распаковка в
    `filesDir/tor` после первого подключения, загрузка в Tor через `SETCONF GeoIPFile/GeoIPv6File`
    уже после bootstrap (парсинг 26 МБ не тормозит старт). ExitNodes {cc} включается только после.
    Слияние соседних диапазонов проверено — выигрыш <1 %, поэтому файлы не трогаем.
20. **Лицензия приложения — GPL-3.0-or-later**: IPtProxy содержит Lyrebird под GPL-3 (см. POM
    IPtProxy), совместимость требует GPL для распространяемого APK.
21. **Слои стекла.** Три слоя (`OpalScaffold`): аврора (LayerBackdrop №1) → контент (LayerBackdrop
    №2, сосед, а не родитель авроры — ничего не записывается дважды) → парящее стекло. Стекло внутри
    контента (сфера, чипы, сегменты) преломляет только аврору; таб-бар, шторка и тост — аврору и
    контент (`rememberCombinedBackdrop`). Не больше 4 живых стеклянных поверхностей на экране:
    второстепенные действия в контенте — не стекло (`PanelButton`), списки и карточки — панели.
22. **Аврора** — AGSL-шейдер (API 33+) из четырёх дрейфующих пятен + тонкое зерно против бандинга;
    ниже 33 — радиальные градиенты. `MeshGradientPainter` в Compose 1.12 нет (проверено по
    исходникам). 30 кадров/с через `preferredFrameRate`, пауза вне RESUMED, статичная при
    «Упрощённой графике», энергосбережении и отключённых анимациях. Палитры заданы в Display P3, но
    окно не переводится в wide-color-gamut: на многих устройствах это F16-буферы и лишняя нагрузка
    на GPU при blur; P3 аккуратно обрезается до sRGB. Пересмотреть после замеров на устройстве.
23. **Язык приложения** — `AppLocaleStore` (:core:data): Android 13+ — системный `LocaleManager`
    (виден в системных настройках, применяется ко всем процессам); ниже — файл в
    `noBackupFilesDir` + `attachBaseContext` в активити, VPN-сервисе и плитке (процесс `:tunnel`
    подхватывает язык при следующем запуске). По умолчанию — русский (`values/`).
24. **Навигация (Navigation 3)** — один бэкстек, Главная всегда в корне: вкладка = `[Home, Tab]`,
    вложенные экраны кладутся сверху, таб-бар на них скрыт. Вкладки сменяются кросс-фейдом
    (метаданные entry), переходы вперёд/назад — сдвиг; predictive back даёт `NavDisplay`.
    ViewModel привязаны к entry (`rememberViewModelStoreNavEntryDecorator`), создаются фабриками из
    `AppGraph`.
25. **Скриншот-тесты** — Roborazzi на Robolectric (SDK 36, `pixelCopyRenderMode=hardware`, чтобы
    blur/тени/AGSL попадали в снимок; для JDK 21 — `--add-opens java.base/jdk.internal.access`).
    Эталоны — `app/src/test/screenshots` (ru, 60 % масштаба), варианты: светлая, тёмная,
    упрощённая графика. Зеркало для загрузки android-all — необязательное свойство
    `robolectricRepoUrl` в `~/.gradle/gradle.properties`.
26. **Иконки** — Material Symbols Rounded (Apache-2.0), сгенерированы из официальных SVG в
    `ImageVector` (`OpalIcons`, ленивая сборка): без шрифта-иконок и без material-icons-extended.
27. **Воспроизводимость и цепочка поставки.** `gradle/verification-metadata.xml` (SHA-256 всех
    артефактов; для aapt2 добавлены варианты osx/windows — Gradle пишет только текущую ОС),
    SHA-256 дистрибутива Gradle, `vcsInfo`/`dependenciesInfo` выключены. hev собирается с
    `-ffile-prefix-map` (ассерты lwIP встраивали `__FILE__`) и без DWARF в release (`-g0`:
    build-id хешируется по отладочной информации с путём сборки). Проверено: две чистые сборки
    из разных каталогов дают побайтно одинаковые APK. Любая новая зависимость → перегенерировать
    метаданные (команда в README).
28. **Ассеты `*.gz`** упаковщик APK распаковывает и кладёт без расширения: в репозитории
    `geoip.gz`, в APK — `assets/geoip` (сжатый zip-записью). `TorFiles` открывает простое имя,
    `.gz` — запасной путь (он же работает в Robolectric-тесте).
29. **Отладочные переключатели** — только в debuggable-сборках: файл
    `files/debug_break_snowflake` подменяет брокер Snowflake на `https://broker.invalid/` (проверка
    гонки транспортов). В release-сборке условие `debuggable` ложно всегда.
30. **Режим по умолчанию — Snowflake, и приложение в него не вмешивается** (`ModePolicy`). Версия
    1.0.0 по умолчанию запускала «Авто»: все транспорты сразу, запрос к Settings API, через ~90 с
    без прогресса — «сеть блокирует всё» и DisableNetwork 1/0 (обрыв поиска прокси Snowflake), а
    watchdog при ложном «зависании» (ожидающий поток + 20 с без байтов — обычный простой
    телефона) рвал соединение и заменял рабочий Snowflake-мост на obfs4/meek. Пользователь
    сообщил, что приложение не работает, и просил Snowflake. Теперь: Snowflake — две встроенные
    строки, как в Tor Browser; ни гонки, ни Settings API до подключения, ни DisableNetwork, ни
    смены мостов — Tor и клиент Snowflake повторяют сами; при «зависании» — только NEWNYM (не чаще
    раза в 2 мин); после 2 мин без прогресса — подсказка, без действий. Порог «зависания» — 45 с.
    Разрушающие действия watchdog — только для TCP-транспортов (obfs4, WebTunnel, meek, свои),
    гонка — только в «Авто».
31. **hev `connect-timeout` = 120 с** (было 15 с): hev ждёт ответ SOCKS CONNECT, то есть пока Tor
    дойдёт до сервера через exit-узел; через Snowflake это часто дольше 15 с, и соединения
    приложений рвались. 120 с = `SocksTimeout` Tor.

## Стек

| Технология | Версия | Зачем | Что заменила |
|---|---|---|---|
| Kotlin | 2.4.20 | язык; встроенный в AGP 9 Kotlin (без `kotlin-android`) | Java / kotlin-android plugin |
| AGP | 9.4.1 | сборка, built-in Kotlin, новый DSL | AGP 8 |
| Gradle | 9.8.0 (+configuration cache, SHA-256 дистрибутива в wrapper) | сборка | — |
| JDK | 21 (toolchains) | компиляция | JDK 17 |
| compileSdk/targetSdk | 37 (android-37.0) | Android 17 | 35/36 |
| minSdk | 26 | tor-android требует ≥24 | — |
| NDK | 30.0.16248370 (r30) | сборка hev, 16 КБ страницы | r2x |
| Compose BOM | 2026.09.00 (UI 1.12.1, Material3 1.4.0) | UI | XML-вёрстка |
| Navigation 3 | 1.2.0 | бэкстек как состояние, predictive back | Navigation 2 / Fragments |
| lifecycle | 2.11.0 | ViewModel, collectAsStateWithLifecycle, Nav3-интеграция | LiveData |
| graphics-shapes | 1.1.0 | Morph (сфера ↔ индикатор) | ручные Path |
| Backdrop (Kyant0) | 2.0.1 | Liquid Glass: blur/lens/vibrancy/highlight | RenderScript blur / сторонние blur |
| Kyant shapes | 1.2.1 | непрерывные (squircle) скругления | RoundedCornerShape |
| DataStore | 1.2.1 (multi-process) | настройки, кеш мостов | SharedPreferences |
| kotlinx.serialization | 1.11.0 | JSON (настройки, IPC, moat) | Gson/Moshi |
| Coroutines | 1.11.0 | structured concurrency, Flow | RxJava/AsyncTask |
| WorkManager | 2.12.0 | фоновое обновление каталога | JobScheduler вручную |
| core-ktx / splashscreen | 1.19.1 / 1.2.0 | Live Update (ProgressStyle), SplashScreen API | свои сплэши |
| activity-compose | 1.13.0 | edge-to-edge, predictive back | onBackPressed |
| CameraX | 1.6.2 | сканер QR мостов | camera1 / ML Kit |
| zxing-cpp (android) | 3.1.1 | декодер QR без Play Services | ML Kit |
| tor-android (только libtor.so) | 0.4.9.12 | C-tor | — |
| IPtProxy | 5.5.1 | Snowflake 2.14.1, Lyrebird (obfs4/meek/webtunnel) | отдельные бинарники PT |
| hev-socks5-tunnel | e802f02 (vendored + патч) | TUN → SOCKS, MapDNS | tun2socks (Go) |
| profileinstaller / baselineprofile / benchmark | 1.4.1 / 1.5.0 / 1.5.0 | Baseline+Startup Profile, Macrobenchmark | — |
| metrics-performance (JankStats) | 1.0.0 | доля подтормаживающих кадров (только debug, в logcat) | — |
| window-core | 1.5.1 | WindowSizeClass | собственные брейкпоинты |
| JUnit4 / Turbine / Robolectric / Roborazzi | 4.13.2 / 1.2.1 / 4.17 / 1.75.0 | юнит-, Flow-, скриншот-тесты | — |
| detekt + compose-rules | 1.23.8 + 0.4.28 | статанализ (compose-rules 0.5+ собраны под detekt 2.0-alpha; 0.4.28 — последняя под стабильный 1.23.8) | — |
| Spotless + ktfmt | 8.10.2 + 0.64 | форматирование | ktlint |
| LeakCanary (debug) | 2.14 | утечки памяти | — |

Не используем: kapt, KSP (нет процессоров аннотаций), LiveData, XML-вёрстку, RxJava, AsyncTask,
onBackPressed, Accompanist, Google Play Services / Firebase / ML Kit, телеметрию.

## Android 17 (target 37): изменения поведения и как закрываем

| Изменение | Что делаем |
|---|---|
| Большие экраны (sw≥600dp): нельзя запретить поворот/ресайз | адаптивная вёрстка по WindowSizeClass, без `screenOrientation` |
| ECH по умолчанию для TLS | наш HTTPS к moat — «сырой» SSLSocket внутри meek: ECH-конфиги сам не запрашивает, DNS не делает |
| Certificate Transparency по умолчанию | bridges.torproject.org (Let's Encrypt) содержит SCT — ок |
| `ACCESS_LOCAL_NETWORK` для LAN | приложению LAN не нужен (только 127.0.0.1 и интернет) |
| Лимиты памяти приложения (все приложения) | следим за RSS `:tunnel` (Go + Tor), диагностика читает `ApplicationExitInfo` (`MemoryLimiter:AnonSwap`) |
| static final через reflection/JNI — запрещено | JNI tor пишет только нестатические поля `torConfiguration`/`torControlFd` — ок |
| Безопасная загрузка native (System.load read-only) | используем `System.loadLibrary` из APK — ок |
| BAL hardening | фон активити не стартуем; плитка — `startActivityAndCollapse(PendingIntent)` |
| Новая MessageQueue | рефлексии по MessageQueue нет |
| `usesCleartextTraffic` → network security config | `network_security_config.xml`, cleartext запрещён |

## Риски и открытые вопросы

- Нет устройства/эмулятора в среде сборки: VpnService, Tor, PT, UI проверяются на устройстве по
  чек-листу (README → «Проверка на устройстве»). Всё, что не проверено, так и помечено в отчётах.
- Смешанные транспорты в одном Tor: поведение гарда с мостами разных типов — проверить на устройстве
  (время до победителя). Если плохо — фолбэк на последовательную гонку уровнем выше.
- Размер: libgojni.so ~24 МБ/ABI (несжато). Пересборка IPtProxy без лишних транспортов — v2.
- Robolectric 4.17 и API 37 — скриншот-тесты запускаются на SDK 36 (`@Config(sdk = [36])`).
- Baseline/Startup Profile генерируется только на устройстве; в репозитории — рукописный профиль
  для стартового пути, заменить сгенерированным.
- Техдолг: `TorSession` (~870 строк) — единственный владелец состояния сессии Tor; разнести гонку,
  watchdog и Settings API по отдельным классам, когда появится проверка на устройстве.
- Glass на реальном GPU (lens/blur, 60/120 fps, доля подтормаживающих кадров) не измерен: нет
  устройства. В debug-сборке JankStats пишет долю в logcat (`OpalJank`), для замеров —
  Macrobenchmark из `:baselineprofile`.

## Соглашения

- Код и идентификаторы — английский; UI — русский по умолчанию (`values/`), английский — `values-en/`.
- Пакеты: `app.opal.<module>`; JNI-классы: `org.torproject.jni.TorService` (имя фиксировано),
  `app.opal.core.tunnel.hev.HevNative` (задаётся `-DPKGNAME/-DCLSNAME` в ndk-build).
- UDF: `UiState` (immutable) ← ViewModel ← репозитории/TunnelClient; события — sealed `Action`.
- Никаких фейковых данных и заглушек, имитирующих подключение: пусто — значит «нет данных».
- Логи в релизе — только кольцевой буфер в памяти, без доменов и IP назначения (Tor SafeLogging),
  экспорт диагностики по кнопке с редактированием.
- Секреты (`keystore.properties`, `*.jks`, `local.properties`) не коммитятся.
- Каждая новая зависимость — строка в таблице «Стек» с обоснованием.

## План и статус фаз

- [x] Фаза 0 — разведка, версии, риски, этот файл.
- [x] Фаза 1 — сетевое ядро: VpnService + hev + Tor + Snowflake (собрано, на устройстве не проверено).
- [x] Фаза 2 — стабильность и скорость: гонка, moat, тёплый кеш (+ WorkManager), резерв, сеть,
      watchdog, подготовка при открытии (собрано, на устройстве не проверено).
- [x] Фаза 3 — дизайн-система Liquid Glass, экраны, скриншот-тесты (27 эталонов, JVM).
- [x] Фаза 4 — раздельное туннелирование (+ пресет), свои мосты (вставка, QR с камеры и из
      картинки), новая личность, страна выхода, плитка (собрано, на устройстве не проверено).
- [x] Фаза 5 — генератор Baseline/Startup Profile и Macrobenchmark (запуск — на устройстве),
      рукописный профиль, размер (strip, только ru/en), dependency verification,
      воспроизводимость (проверена), подписанный релиз, README, CI, отчёт `docs/test-report.md`.
      Замеры на устройстве не выполнялись.
