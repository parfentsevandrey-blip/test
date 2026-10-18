# ROM Forge — альтернативные прошивки для Android с Mac

Нативное приложение для macOS (SwiftUI, дизайн Liquid Glass в macOS 26 Tahoe с откатом на материалы в macOS 14–15).
Подключаете любой Android-смартфон по USB — приложение показывает его характеристики, находит все
подходящие альтернативные прошивки и устанавливает выбранную почти без участия пользователя.

## Возможности

- **Характеристики устройства**: модель, кодовое имя, чипсет, ОЗУ, память, экран, батарея, версия Android,
  патч безопасности, состояние загрузчика, A/B-разделы, Treble и т. д. (через `adb`).
- **Каталог прошивок** с автоматической проверкой сборок под конкретное устройство:
  | Прошивка | Источник | Установка |
  |---|---|---|
  | LineageOS | API download.lineageos.org | полностью автоматически, вместе с LineageOS Recovery |
  | crDroid | OTA-JSON проекта | автоматически (recovery берётся от LineageOS, если есть) |
  | Evolution X | OTA-JSON проекта | автоматически (recovery от LineageOS, если есть) |
  | GrapheneOS | releases.grapheneos.org | заводские образы + `flash-all.sh`, с повторной блокировкой загрузчика |
  | /e/OS, LineageOS for microG, CalyxOS, iodéOS, PixelOS, RisingOS, Paranoid Android, Ubuntu Touch, postmarketOS | сайт проекта | ссылка на загрузку под ваше кодовое имя + установка скачанного ZIP |
- **Мастер установки**: загрузка с проверкой SHA-256/MD5 → разблокировка загрузчика → прошивка recovery →
  сброс → `adb sideload` → перезагрузка. Там, где нужно нажать кнопки на телефоне, показывается короткая
  инструкция, а приложение само отслеживает, когда шаг выполнен (смена режима, `getprop`, `fastboot getvar`).
- Учитываются особенности производителей: Xiaomi (Mi Unlock), Motorola/Sony (код разблокировки),
  Samsung (нет fastboot — автоматическая установка отключена), Huawei/Honor (разблокировка невозможна).
- **Android Platform Tools** (`adb`, `fastboot`) ищутся на Mac (Homebrew, Android Studio) или скачиваются
  с сайта Google автоматически при первом запуске.

## Сборка

Требуется macOS 14+ и Xcode 16+ (для Liquid Glass — Xcode 26).

```bash
cd ROMForge
swift run                      # быстрый запуск из терминала
./scripts/build-app.sh         # dist/ROM Forge.app (universal, ad-hoc подпись)
./scripts/build-app.sh --bundle-tools   # то же, но с adb/fastboot внутри .app
```

Или откройте `Package.swift` в Xcode и нажмите ⌘R.

Готовый `.app` также собирается в GitHub Actions (workflow «ROM Forge (macOS)», артефакт `ROM-Forge-macOS`).
Приложение подписано ad-hoc, поэтому при первом запуске откройте его через контекстное меню → «Открыть».

> Приложение не работает в App Sandbox: ему нужно запускать `adb` и `fastboot`.

## Структура

```
Sources/ROMForge/
  App/       точка входа, AppModel (мониторинг устройств, поиск прошивок)
  Models/    устройство, прошивки, каталог
  Services/  Shell, adb/fastboot, Platform Tools, загрузчик, поиск сборок, InstallSession (мастер)
  Views/     SwiftUI-экраны
```

## Предупреждение

Установка альтернативной прошивки стирает все данные на телефоне, а разблокировка загрузчика может
лишить гарантии. Приложение не связано с проектами прошивок и производителями устройств.
