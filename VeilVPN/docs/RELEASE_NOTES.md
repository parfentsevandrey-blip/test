## Veil — Tor + Snowflake client for macOS 26 Tahoe

Liquid Glass UI, bundled Tor Expert Bundle (universal: Apple silicon + Intel), Snowflake / obfs4 / custom bridges, exit-country selection, live circuit and throughput, automatic system proxy.

### New in 0.2.0
- **Traffic padding (DAITA-style)** — dummy traffic bounced through Tor to a private onion service on your Mac hides the shape of your real traffic from an observer on the local network: background noise, FRONT-style bursts on activity, optional constant-rate mode. Tor's own circuit/connection padding is forced on too.
- **Multihop** — a route card with a live diagram (Mac → Snowflake → middle → exit → Internet), a middle-hop country, per-country exclusions, a "Five Eyes" preset, timed route rotation and the measured route latency.

### Install
1. Open the DMG and drag **Veil.app** to *Applications*.
2. The build is ad-hoc signed (no Apple Developer certificate): on first launch open **System Settings → Privacy & Security → Open Anyway**, or run `xattr -cr /Applications/Veil.app`.
3. Press the power button. macOS asks for an administrator password once to switch the system proxy.

### Новое в 0.2.0
- **Маскировка трафика (в стиле DAITA)** — пустой трафик, проходящий через Tor до приватного onion-сервиса на вашем Mac, скрывает форму реального трафика от наблюдателя в локальной сети: фоновый шум, всплески в стиле FRONT при активности, режим постоянной скорости. Также принудительно включён circuit/connection padding самого Tor.
- **Многократный переход** — карточка маршрута с живой схемой (Mac → Snowflake → промежуточный → выходной → интернет), выбор страны промежуточного узла, исключение стран, пресет «Пять глаз», смена маршрута по таймеру и измеренная задержка.

### Установка
1. Откройте DMG и перетащите **Veil.app** в *Программы*.
2. Сборка подписана ad-hoc: при первом запуске откройте **Системные настройки → Конфиденциальность и безопасность → Всё равно открыть**, либо выполните `xattr -cr /Applications/Veil.app`.
3. Нажмите кнопку питания. macOS один раз спросит пароль администратора для переключения системного прокси.

Requires macOS 26 Tahoe.
