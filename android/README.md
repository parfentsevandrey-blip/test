# Opal — весь трафик Android через Tor

Opal — VPN-приложение для Android, которое заворачивает трафик всех приложений в сеть Tor
(C-tor) и по умолчанию подключается через мост **Snowflake** — с теми же строками, что Tor
Browser (в России — набор Tor Project для РФ: другие домены и резерв через AMP cache). Для сетей, где Snowflake заблокирован, есть режим «Авто» (гонка WebTunnel, obfs4,
meek и мосты из Circumvention Settings API) и свои мосты — их включают вручную. Своих серверов у
приложения нет — только инфраструктура Tor Project. Интерфейс — «жидкое стекло» (Liquid Glass)
на Jetpack Compose.

> **Статус.** Приложение собирается, проходит юнит-, скриншот- и UI-тесты, lint и detekt.
> **Проверено в эмуляторе Android 17** (программная эмуляция, без KVM): установка, онбординг,
> VPN, запуск Tor и Snowflake внутри приложения, WebRTC-канал Snowflake, подключение к настоящей
> сети Tor (Bootstrapped 100 %) и выход трафика другого процесса через VPN с IP из официального
> списка выходных узлов Tor. Волонтёрские прокси Snowflake отсюда недоступны (в среде сборки нет
> UDP) — их заменяли локальные брокер/прокси/сервер той же версии. **На реальном устройстве и в
> российских сетях не проверялось.** Подробности — в [отчёте о проверке](docs/test-report.md).

Архитектура, решения и их причины — в [CLAUDE.md](CLAUDE.md).

## Возможности

- Весь трафик устройства (IPv4 и IPv6) — через Tor; DNS отвечает локальный MapDNS, имена
  резолвятся на выходном узле; UDP (кроме DNS) сразу отклоняется, QUIC откатывается на TCP.
- По умолчанию — Snowflake, как в Tor Browser: приложение не вмешивается в его работу (не
  переключает транспорты, не перезапускает соединение), только показывает, если поиск прокси
  затянулся. По выбору: «Авто» (гонка транспортов + мосты из Circumvention Settings API),
  WebTunnel, obfs4, meek, свои мосты (вставка, QR-код с камеры или из картинки).
- Быстрое подключение: тёплый кеш каталога (фоновое обновление раз в ~12 ч), подготовка при
  открытии приложения, горячий резерв, мягкое переподключение при смене сети.
- Watchdog против «заморозок» TCP-соединений с мостами (obfs4, WebTunnel, meek): новые цепочки →
  другой мост → перезапуск Tor. Для Snowflake — только новые цепочки: потерянный прокси
  Snowflake заменяет сам.
- В режиме «Авто» — честное состояние «Сеть блокирует все способы подключения» с подсказками.
- Раздельное туннелирование (все, кроме выбранных / только выбранные), пресет
  «Российские банки и Госуслуги — напрямую».
- Новая личность (`NEWNYM` с учётом ограничения Tor), страна выхода (только после подключения).
- Плитка в быстрых настройках, уведомление со скоростью и кнопками, «Постоянная VPN».
- Русский (по умолчанию) и английский; светлая, тёмная и автоматическая тема; упрощённая графика.

## Чего не умеет (пределы Tor)

- Tor не переносит UDP: звонки, игры, часть мессенджеров могут не работать.
- Многие сервисы блокируют выходные узлы Tor или показывают капчу (банки, госсервисы,
  стриминги) — для них есть раздельное туннелирование.
- Tor медленнее обычного соединения; Snowflake — самый медленный транспорт.
- Приложения не становятся анонимными: аккаунты и отпечатки по-прежнему вас выдают.
  Для анонимного веб-сёрфинга нужен Tor Browser.
- В режимах «Авто», WebTunnel и obfs4 приложение спрашивает мосты у сервера Tor Project
  (Circumvention Settings API) **напрямую, не через Tor**: сервер видит ваш IP-адрес, как и в
  Tor Browser. В режиме Snowflake (по умолчанию) такого запроса нет.
- Первое подключение через Snowflake может занять 1–2 минуты: нужно найти волонтёрский прокси и
  скачать каталог сети. Повторные — быстрее (тёплый кеш).

## Какой APK ставить

| Файл | Для чего |
|---|---|
| `app-arm64-v8a-release.apk` | почти все телефоны последних лет |
| `app-armeabi-v7a-release.apk` | старые 32-битные телефоны |
| `app-x86_64-release.apk` | эмуляторы и x86-планшеты, Chromebook |
| `app-universal-release.apk` | если не уверены (все архитектуры, крупнее) |

Требуется Android 8.0 (API 26) или новее.

## Сборка с нуля

### Требования

- JDK 21 (Gradle toolchains подберут его, если он установлен).
- Android SDK: `platforms;android-37.0`, `build-tools;37.0.0`, `ndk;30.0.16248370`.
- ~10 ГБ свободного места, доступ к Google Maven и Maven Central.

```bash
sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "ndk;30.0.16248370" "platform-tools"
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties   # не коммитится
```

### Debug

```bash
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-<abi>-debug.apk (applicationId app.opal.debug)
```

### Подписанный release

Ключ создаётся локально и **никогда не попадает в git** (`*.jks`, `keystore.properties`
в `.gitignore`):

```bash
keytool -genkeypair -keystore ~/opal-release.jks -storetype PKCS12 -alias opal \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Opal"
cat > keystore.properties <<'EOF'
storeFile=/home/you/opal-release.jks
storePassword=...
keyAlias=opal
keyPassword=...
EOF
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-{arm64-v8a,armeabi-v7a,x86_64,universal}-release.apk
```

Вместо файла можно задать переменные окружения `OPAL_KEYSTORE_FILE`,
`OPAL_KEYSTORE_PASSWORD`, `OPAL_KEY_ALIAS`, `OPAL_KEY_PASSWORD` (например, в CI). Без ключа
release собирается неподписанным. Обновить установленное приложение можно только APK,
подписанным тем же ключом, — храните ключ и пароль надёжно.

### Проверки

| Что | Команда |
|---|---|
| Юнит-тесты (JVM + Robolectric) | `./gradlew testDebugUnitTest :core:model:test` |
| Скриншот-тесты: сверить / перезаписать эталоны | `./gradlew :app:verifyRoborazziDebug` / `:app:recordRoborazziDebug` |
| Lint | `./gradlew lintDebug` |
| detekt + compose-rules | `./gradlew detekt` |
| Форматирование (ktfmt) | `./gradlew spotlessCheck` / `spotlessApply` |
| Нативный мост hev на Linux-хосте | `sudo tools/hev-host-test/run.sh` |

Эталонные скриншоты — в `app/src/test/screenshots` (светлая, тёмная тема, упрощённая графика).

### Проверка зависимостей и воспроизводимость

Все артефакты сверяются по SHA-256 из `gradle/verification-metadata.xml`, дистрибутив Gradle —
по `distributionSha256Sum`. После изменения зависимостей:

```bash
./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease testDebugUnitTest lintDebug detekt spotlessCheck
```

В APK нет меток времени и сведений о VCS (`vcsInfo.include = false`), нативный код собирается
из закреплённых исходников, `dependenciesInfo` отключён. Повторная чистая сборка с тем же ключом
даёт побайтно одинаковые APK (см. отчёт о проверке).

### Baseline Profile и замеры (нужно устройство)

```bash
./gradlew :app:generateBaselineProfile                        # Baseline + Startup Profile
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # холодный старт, кадры
```

До генерации используется рукописный профиль `app/src/main/baseline-prof.txt`.

### Встроенные мосты

`core/tunnel/src/main/assets/pt_config.json` — дословно файл Tor Browser
(`tor-browser-build/projects/tor-expert-bundle/pt_config.json`). Обновление:
`./gradlew :core:tunnel:updateBuiltinBridges`, затем проверить diff и закоммитить.

`core/tunnel/src/main/assets/snowflake_regional.json` — строки Snowflake, которые Circumvention
Settings API выдаёт для страны (сейчас `ru`). Обновление — вручную:
`curl -X POST https://bridges.torproject.org/moat/circumvention/settings -H 'Content-Type:
application/vnd.api+json' -d '{"country":"ru"}'`, строки `snowflake` → в файл, дата — в `_fetched`.
Страну приложение определяет локально (сеть → SIM → часовой пояс → локаль), никуда её не
отправляя в режиме Snowflake.

## Мосты: где взять свои

Если не справляются ни Snowflake, ни «Авто» (агрессивная блокировка). При «белых списках» в
мобильной сети (отключения по регионам РФ) ни один транспорт Tor, по данным Tor Project, не
работает — остаётся Wi‑Fi/проводной интернет:

- Telegram-бот **@GetBridgesBot**;
- сайт **bridges.torproject.org** (QR-код можно отсканировать в приложении);
- письмо на **bridges@torproject.org** (отвечает на адреса Gmail и Riseup).

«Подключение» → «Свои мосты» → вставьте строки, отсканируйте QR или выберите картинку.

## Приватность и разрешения

Никакой телеметрии, аналитики, крэш-репортеров, Google Play Services и Firebase. Журнал — только
кольцевой буфер в памяти процесса туннеля, без доменов и IP-адресов назначения; экспорт —
только вручную, с возможностью отредактировать текст. `allowBackup=false`.

| Разрешение | Зачем |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Tor и мосты; отслеживание смены сети |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `FOREGROUND_SERVICE_SPECIAL_USE` | VPN работает как foreground-сервис; `specialUse` — запасной тип для горячего резерва |
| `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` | уведомление о статусе и скорости (Live Update на Android 16+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | прямой запрос исключения из оптимизации батареи (по кнопке) |
| `CAMERA` | сканер QR-кодов мостов, запрашивается только при открытии сканера |
| `VIBRATE` | тактильная отдача при смене состояния |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | добавляет WorkManager: фоновое обновление каталога и его перепланирование после перезагрузки |

Экспортированы только: главная активность (лаунчер), VPN-сервис (защищён `BIND_VPN_SERVICE`),
плитка (`BIND_QUICK_SETTINGS_TILE`), `SystemJobService` WorkManager (`BIND_JOB_SERVICE`) и
`ProfileInstallReceiver` (`DUMP`, нужен для установки Baseline Profile через adb).
Список приложений для раздельного туннелирования — через `<queries>` по LAUNCHER-интенту, без
`QUERY_ALL_PACKAGES`. SOCKS Tor и порты транспортов слушают только `127.0.0.1` на случайных
портах; control-канал Tor — сокет-пара внутри процесса.

## Проверка на устройстве (чек-лист)

Ничего из этого не выполнялось — нужен телефон (желательно два: Android 15–17 и Android 8–11).

1. Установить `app-arm64-v8a-release.apk`, пройти онбординг, выдать разрешение VPN.
2. Подключиться; открыть **check.torproject.org** в браузере — «Congratulations».
3. **DNS-утечки**: dnsleaktest.com / browserleaks.com/dns — только адреса выходных узлов Tor.
4. **IPv6**: test-ipv6.com, browserleaks.com/ip — нет вашего IPv6.
5. То же с включённым «Частным DNS» (Настройки → Сеть → Частный DNS).
6. Wi-Fi ↔ LTE, режим полёта вкл/выкл — туннель восстанавливается, без утечек между сетями.
7. Экран выключен 30 минут: `adb shell dumpsys deviceidle force-idle`, затем разблокировать —
   соединение живо или быстро восстанавливается.
8. Свернуть приложение и убить процесс UI: `adb shell am kill app.opal` (процесс `:tunnel` —
   foreground-сервис, он должен выжить) — туннель работает, при открытии UI состояние верное.
9. «Постоянная VPN» + «Блокировать соединения без VPN», перезагрузка — туннель поднимается сам,
   до готовности Tor трафик не уходит мимо.
10. Snowflake по умолчанию: подключение проходит, в журнале диагностики только мосты
    `snowflake`, нет запросов к Settings API. Гонка — в режиме «Авто»: сломать Snowflake
    (debug-сборка: `adb shell run-as app.opal.debug touch files/debug_break_snowflake`,
    переподключиться) — приложение само побеждает через другой транспорт.
11. Settings API: на новом устройстве (или после очистки данных) убедиться, что мосты
    WebTunnel/obfs4 из API настоящие — bootstrap через них проходит.
12. Тёплый кеш: повторное подключение в течение суток — без загрузки полного каталога
    (фазы «Загрузка каталога сети» и «Сведения об узлах» проскакивают почти сразу); через
    1,5–2 суток — с фоновым обновлением и без него.
13. Раздельное туннелирование: исключённое приложение видит ваш обычный IP; с включённой
    блокировкой без VPN — остаётся без сети (как и предупреждает экран).
14. Новая личность — на check.torproject.org меняется IP выхода; страна выхода — IP из
    выбранной страны.
15. Замеры: время до готовности по каждому транспорту (первый запуск, тёплый кеш, горячий
    резерв), холодный старт и доля подтормаживающих кадров (Macrobenchmark), память процесса
    `:tunnel` (`adb shell dumpsys meminfo app.opal:tunnel`), батарея в горячем резерве за час
    (`adb shell dumpsys batterystats app.opal`).

## Лицензия

Opal распространяется под **GNU GPL v3 или новее** ([LICENSE](LICENSE)): IPtProxy включает части
Lyrebird под GPL-3.0. Сторонние компоненты и их лицензии — на экране «Настройки → Лицензии
открытого ПО» (тексты в `feature/settings/src/main/assets/licenses`). Название и иконка —
собственные, без товарных знаков Tor Project и Apple. Tor® — товарный знак The Tor Project, Inc.;
приложение не связано с Tor Project и не одобрено им.
