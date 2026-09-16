# TorSS — лёгкий VPN для Windows 11: Tor поверх Shadowsocks

Приложение в трее (один `torss.exe` ≈ 7 МБ, без установщика, без .NET и Electron),
которое поднимает системный VPN (TUN-адаптер) или локальный прокси и гонит трафик
через **Tor**, а сам Tor — через **Shadowsocks-2022 + ShadowTLS** к вашему серверу
за границей. Если сервера нет или он заблокирован — автоматически переключается на
мосты Tor (webtunnel → Snowflake → obfs4 → meek) и на чистый Shadowsocks.

```
 приложения ──► TUN (wintun) / прокси 127.0.0.1:2080
                    │  sing-box: fake-IP DNS, маршрутизация, kill switch
                    ▼
                 Tor (tor.exe)  ── новая личность, цепочки, .onion
                    │  Socks5Proxy 127.0.0.1:2081
                    ▼
                 sing-box: Shadowsocks-2022 ⟶ ShadowTLS v3 (выглядит как TLS к gateway.icloud.com)
                    │
                    ▼
                 ваш VPS за рубежом ──► guard-узлы Tor ──► интернет
```

## Почему именно так (опыт блокировок в РФ)

| Проблема | Что делает TorSS |
|---|---|
| Прямой Tor заблокирован по IP guard-узлов и по DPI | В основном режиме `ss-tor` Tor вообще не выходит в сеть напрямую: все соединения идут через SS-туннель на VPS, guard-узлы достигаются уже из-за границы |
| Обычный Shadowsocks ТСПУ распознаёт по паттерну и режет через минуты/часы | Shadowsocks-2022 (AEAD, стойкий к replay-детекту) завёрнут в **ShadowTLS v3**: настоящее TLS 1.3-рукопожатие проксируется на реальный сайт (`gateway.icloud.com`), для DPI это обычный HTTPS к Apple |
| Встроенные obfs4-мосты Tor давно в чёрных списках | Порядок перебора: сначала ваши мосты **webtunnel** (HTTPS к обычному сайту), потом **Snowflake** (WebRTC + domain fronting через CDN), потом obfs4, потом meek |
| Блокировки «плавают»: что работало утром, отваливается вечером | Сторож каждые 20 с проверяет реальную связность через туннель; после 3 неудач — новая личность Tor, затем переподключение; если режим умер — переход к следующему; повтор с экспоненциальной паузой (20 с → 5 мин). Последний рабочий режим запоминается и пробуется первым |
| Утечки DNS / трафика мимо VPN | Fake-IP DNS: имена уходят в Tor и резолвятся на выходе; в TUN-режиме `strict_route` + опциональный **kill switch** на Windows Firewall (только `sing-box.exe`/`tor.exe` могут ходить в сеть) |
| UDP/QUIC зависают в Tor | UDP в Tor-режимах отбрасывается мгновенно, браузеры сразу откатываются на TCP; в режиме `ss-only` UDP работает (UDP-over-TCP через ShadowTLS) |
| Tor «залипает» на мёртвых guard-узлах | Пункт меню «Сбросить состояние Tor» |
| Заблокировали GitHub/torproject.org — нечем скачать обновления | `fetch-deps.ps1 -Proxy http://127.0.0.1:2080` качает через уже работающий TorSS; для Tor есть зеркала EFF/Calyx |

## Быстрый старт

### 0. Готовый файл: `release/TorSS.exe`

Один самодостаточный exe (~32 МБ): внутри уже лежат sing-box 1.14.1, Tor Expert
Bundle 15.0.23 (tor, lyrebird) и wintun 0.14.1. Ничего ставить не нужно
(режим `tor-conjure` в готовый файл не включён ради размера: при желании положите
`conjure-client.exe` из Tor Expert Bundle в `%LOCALAPPDATA%\TorSS\bin\tor\pluggable_transports\`):

1. Скопируйте `TorSS.exe` в любую папку и запустите. SmartScreen покажет
   «Неизвестный издатель» (файл не подписан) → «Подробнее» → «Выполнить в любом случае».
   Затем UAC-запрос прав администратора (нужны для VPN-адаптера).
2. При первом запуске компоненты распаковываются в `%LOCALAPPDATA%\TorSS\bin`
   (5–10 секунд), появляется значок в трее и сразу начинается подключение через
   мосты Tor (`tor-webtunnel` пропускается, пока нет своих мостов → Snowflake → obfs4 → meek).
3. Чтобы получить быстрый и устойчивый режим `ss-tor`, поднимите сервер (шаг 1)
   и вставьте его данные в `config.json` (шаг 3).

Антивирусы иногда ругаются на Tor/sing-box (это известные ложные срабатывания на
инструменты обхода блокировок); при необходимости добавьте `%LOCALAPPDATA%\TorSS`
в исключения. SHA-256 файла — в `release/TorSS.exe.sha256`.

### 1. Сервер (VPS за пределами РФ, Debian/Ubuntu, 5 минут)

```bash
curl -fsSL https://raw.githubusercontent.com/<ваш-аккаунт>/<репо>/main/torss/server/install-server.sh -o install-server.sh
sudo bash install-server.sh            # опции: -p 443  -s gateway.icloud.com
```

Скрипт ставит sing-box, генерирует пароли, включает BBR и в конце печатает готовый
блок `"server": {...}` — его вставить в `config.json` клиента. Рекомендации по VPS:
любая страна без блокировок, IPv4, не «известный VPN-хостинг»; порт 443.
SNI (`-s`) должен быть реальным сайтом с TLS 1.3, доступным из РФ и с VPS
(`gateway.icloud.com`, `www.microsoft.com`, `dl.google.com`).

Если сервера нет — TorSS всё равно работает: пропустите шаг, будут использоваться мосты Tor.

### 2. Сборка клиента самому (если не хотите брать готовый exe)

Нужны [Go 1.24+](https://go.dev/dl/) и PowerShell.

```powershell
git clone <репо> ; cd <репо>\torss
powershell -ExecutionPolicy Bypass -File scripts\fetch-deps.ps1   # sing-box, wintun, Tor Expert Bundle -> bin\
powershell -ExecutionPolicy Bypass -File scripts\build.ps1        # -> dist\TorSS\TorSS.exe (всё внутри) + zip
powershell -ExecutionPolicy Bypass -File scripts\build.ps1 -Lite  # маленький exe + папка bin\ рядом
```

Из Linux/macOS: `bash scripts/build.sh` (см. комментарий в скрипте, как собрать
sing-box.exe без GitHub). `go run ./tools/pack` — упаковка `bin\` в exe.
Если рядом с exe есть папка `bin\`, используется она, иначе — встроенный бандл.

### 3. Запуск

1. Запустите `TorSS.exe`. Он запросит права администратора (нужны для TUN-адаптера)
   и появится в трее серым кружком.
2. В трее: **Настройки → Открыть config.json**, вставьте блок `server` из вывода
   серверного скрипта, сохраните, **Настройки → Перечитать config.json**.
   Файл лежит в `%LOCALAPPDATA%\TorSS\config.json`.
3. Значок: жёлтый — подключение (в меню виден процент загрузки Tor), зелёный —
   работает, красный — все режимы не сработали, идёт повтор.
4. Проверка: откройте https://check.torproject.org — должно быть «Congratulations».

Меню трея: Подключить/Отключить · Режим (авто или конкретный) · Новая личность Tor ·
Kill switch · Автозапуск при входе (планировщик задач, с правами администратора) ·
Настройки (config, лог, папка данных, сброс Tor) · Выход.

## Режимы

| Режим | Что это | Нужен VPS | Анонимность |
|---|---|---|---|
| `ss-tor` | Tor через Shadowsocks+ShadowTLS | да | Tor |
| `tor-webtunnel` | Tor с мостами webtunnel (свои строки из `bridges.lines`) | нет | Tor |
| `tor-snowflake` | Tor через Snowflake (встроенные мосты) | нет | Tor |
| `tor-obfs4` | Tor с obfs4 (свои + встроенные) | нет | Tor |
| `tor-meek` | Tor через meek_lite / CDN, очень медленно | нет | Tor |
| `tor-conjure` | Tor через Conjure (нужны строки мостов) | нет | Tor |
| `tor-direct` | Tor без мостов — для работы за границей | нет | Tor |
| `ss-only` | Только Shadowsocks, быстро, **не анонимно** | да | нет |

`"mode": "auto"` перебирает `mode_order`, пропуская недоступные (нет сервера, нет
мостов). `ss-only` попадает в перебор только при `"allow_ss_only": true`.

### Где взять мосты webtunnel / obfs4

* Telegram: бот `@GetBridgesBot` → `/bridges` (выбрать webtunnel);
* https://bridges.torproject.org/ (через уже поднятый TorSS или Snowflake);
* письмо на `bridges@torproject.org` с Gmail/Riseup, текст `get transport webtunnel`.

Каждую строку — в `bridges.lines` (кавычки, запятые между строками). webtunnel в
2024–2026 самый живучий вариант: это HTTPS к обычному сайту с валидным сертификатом.

## config.json — ключевые параметры

```jsonc
"tun": true,            // false = только прокси 127.0.0.1:2080 (+ системный прокси), админ не нужен
"system_proxy": true,   // в режиме без TUN прописать прокси в настройки Windows
"kill_switch": false,   // блокировать всё, кроме туннеля, пока подключены
"autoconnect": true,    // подключаться при запуске
"ports": {...},         // локальные порты, все на 127.0.0.1
"probe": {
  "interval_sec": 20,   // как часто проверять связность
  "failures_before_reconnect": 3
},
"log_level": "info"     // "debug" — подробные логи tor и sing-box
```

Лог: `%LOCALAPPDATA%\TorSS\torss.log` (ротация на 5 МБ). Флаги: `torss.exe -console`
(лог в консоль), `-no-autoconnect`, `-cleanup` (только снять kill switch/прокси и выйти),
`-config путь`.

## Безопасность и ограничения — прочитайте

* **Это не Tor Browser.** Tor скрывает IP, но браузер по-прежнему выдаёт вас
  cookies, аккаунтами и fingerprint. Для действительно анонимной работы используйте
  Tor Browser (его можно пустить через TorSS: он сам поднимет свой Tor, либо
  укажите ему SOCKS 127.0.0.1:9050).
* В `ss-tor` владелец VPS (вы) видит только зашифрованный Tor-трафик; провайдер видит
  TLS к `gateway.icloud.com`. Guard-узел Tor видит IP VPS, а не ваш домашний.
* `ss-only` — обычный VPN: выходной IP = IP вашего VPS, никакой анонимности.
* Kill switch переводит Windows Firewall в режим «блокировать исходящее» для всех
  профилей и снимается при отключении/выходе; после аварийного завершения TorSS
  при следующем запуске сам восстанавливает политику (`torss.exe -cleanup` — вручную).
* Проверяйте, что скачиваете: `fetch-deps.ps1` сверяет SHA-256 sing-box и сохраняет
  `.asc`-подпись Tor Expert Bundle рядом (`gpg --verify`). Ставьте обновления
  регулярно — Tor и sing-box быстро реагируют на новые методы блокировки.
* Скорость Tor — 2–10 Мбит/с. Для видео/игр включайте `ss-only` через меню «Режим».

## Разработка

```bash
cd torss
go test ./...                                       # генерация конфигов
TOR_BIN=/usr/bin/tor SINGBOX_BIN=~/go/bin/sing-box go test ./...   # + проверка настоящими tor/sing-box
TORSS_SMOKE=1 SINGBOX_BIN=... TOR_BUNDLE_DIR=.../tor go test ./internal/engine -run Smoke -v
GOOS=windows GOARCH=amd64 go build -ldflags "-s -w -H=windowsgui" ./cmd/torss
```

Структура: `cmd/torss` — точка входа; `internal/engine` — автомат состояний
(перебор режимов, сторож, переподключение); `internal/tor` — torrc, control-порт;
`internal/singbox` — генерация конфига sing-box; `internal/bridges` — встроенные мосты
(`pt_config.json` из Tor Browser); `internal/killswitch`, `internal/sysproxy`,
`internal/winutil` — интеграция с Windows; `internal/ui` — трей.

Лицензии компонентов: Tor (BSD-3), sing-box (GPL-3), wintun (GPL-2/MIT/prebuilt), lyrebird (BSD).
