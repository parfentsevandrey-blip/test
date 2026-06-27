# Brick Storm 🧱⚡

Оригинальная аркада в жанре **brick-breaker** на движке **Godot 4.3**, собирается
в Android **APK**. Запускаешь рой шаров снизу, разбиваешь блоки с числами (число =
сколько раз нужно попасть), блоки опускаются вниз каждый ход — продержись как
можно дольше.

> Это **полностью оригинальный проект**: весь код и графика созданы с нуля,
> ничего чужого не используется. **Все улучшения бесплатны** — нет ни рекламы,
> ни внутриигровых покупок. Это by design: монетизации в игре просто нет.

## Что внутри

- `main.gd` — вся игра одним файлом: физика шаров (ручная, с под-шагами против
  «протыкания»), генерация уровней, апгрейды, сохранение рекорда, меню.
- `main.tscn`, `project.godot`, `icon.svg` — сцена, настройки проекта, иконка.
- `export_presets.cfg` — пресет экспорта в Android (arm64-v8a + armeabi-v7a,
  пакет `com.brickstorm.game`).
- `../.github/workflows/build-apk.yml` — CI, который собирает готовый APK.

## Как играть

- **Тяни пальцем** в игровом поле, чтобы прицелиться, — **отпусти**, чтобы
  выстрелить очередью шаров.
- Шары отскакивают от стен и блоков, каждое попадание уменьшает число на блоке.
- Зелёные кружки `+` дают **+1 шар** на остаток забега (рой растёт).
- Каждый ход ряды опускаются на одну клетку. Если блок дошёл до линии запуска —
  игра окончена.
- В меню **UPGRADES** можно бесплатно прокачивать стартовое число шаров и силу
  удара (`+ FREE`).

## Сборка APK

### Вариант 1 — автоматически через GitHub Actions (проще всего)

1. Запушь ветку в GitHub.
2. Открой вкладку **Actions → Build Android APK → Run workflow** (или просто
   запушь изменения в `game/` — сборка стартует сама).
3. Когда workflow завершится, скачай артефакт **`brick-storm-apk`** — внутри
   `brick-storm.apk`. Workflow сам ставит Android SDK, Godot, export-шаблоны и
   генерирует debug-ключ.

### Вариант 2 — локально в редакторе Godot

1. Установи **Godot 4.3** (стандартная версия) и открой папку `game/` как проект.
2. **Editor → Manage Export Templates → Download and Install** (шаблоны 4.3).
3. **Project → Export…**, пресет **Android** уже настроен. Для debug-сборки Godot
   попросит указать Android SDK и debug-keystore:
   - **Editor → Editor Settings → Export → Android**: укажи путь к Android SDK.
   - Сгенерируй debug-ключ (один раз):
     ```bash
     keytool -keyalg RSA -genkeypair -alias androiddebugkey \
       -keypass android -keystore debug.keystore -storepass android \
       -dname "CN=Android Debug,O=Android,C=US" -validity 10000 -deststoretype pkcs12
     ```
     и пропиши его в тех же Editor Settings (Debug Keystore / User `androiddebugkey`
     / Pass `android`).
4. **Export Project…** → `brick-storm.apk`.

### Вариант 3 — командная строка (headless)

```bash
godot --headless --path game --export-debug "Android" build/brick-storm.apk
```
(нужны установленные export-шаблоны 4.3, Android SDK и debug-keystore — см.
`build-apk.yml`, там все шаги выписаны.)

## Установка на телефон

Скинь `brick-storm.apk` на Android-устройство и установи (понадобится разрешение
«установка из неизвестных источников»). Это debug-сборка — для публикации в Google
Play нужен release-ключ и подпись, но для личного использования debug-APK
достаточно.

## Лицензия

Код проекта — твой. Делай с ним что хочешь.
