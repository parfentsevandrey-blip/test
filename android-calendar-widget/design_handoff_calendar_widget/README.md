# Handoff: Адаптивный календарь — Android App Widget + приложение

> Задача для Claude Code: **развернуть нативный Android-проект и собрать APK** по этому ТЗ.
> Дизайн-референсы лежат в `design_refs/` (HTML-прототипы) — это **эталон внешнего вида и поведения, а не код для копирования**. Нужно воссоздать их средствами Android (Kotlin + Jetpack Glance + Compose).

---

## 0. TL;DR для сборки

```bash
# 1. Скаффолд проекта (структура ниже), затем:
./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
# для подписанного релиза:
./gradlew assembleRelease       # требует keystore (см. раздел 9)
```

Минимальная цель: устанавливаемый `app-debug.apk`, который ставит на домашний экран **виджет календаря (вид месяца)** с динамическим размером, адаптацией к теме устройства, чтением системного календаря и экраном настроек.

---

## 1. Overview

Премиальный календарь-виджет для Android:
- **Виджет на рабочем столе** — вид месяца (сетка), стеклянный (glassmorphism) фон, точки-индикаторы событий, опциональная лента дел дня.
- **Динамический размер** — один виджет, три бакета: 2×2 (мини-сетка), 4×2 (сетка + навигация), 4×4 (сетка + лента событий). Реагирует на ресайз в реальном времени.
- **Адаптивность** — светлая/тёмная тема устройства; поддержка Material You (dynamic color, Android 12+).
- **Системный календарь** — чтение событий через `CalendarContract`, тап по дню/событию открывает приложение.
- **Приложение** — экран дня (недельная лента, крупная дата, карточки событий, FAB «+»).
- **Настройки виджета** — акцентный цвет, прозрачность фона, скругление углов, первый день недели, показ событий, размер шрифта, тема превью. Изменения мгновенно применяются к виджету.

## 2. About the Design Files

В `design_refs/` — HTML-прототипы (Design Components). Это **высокоточные референсы** дизайна и интерактива, не продакшен-код. Открыть `Адаптивный календарь Android.dc.html` в браузере, чтобы увидеть все экраны на пан-канвасе. Реализовать на нативном стеке Android, перечисленном ниже, по установленным паттернам платформы.

## 3. Fidelity

**High-fidelity.** Цвета, типографика, отступы, радиусы, состояния — финальные. Воссоздавать пиксель-в-пиксель, насколько позволяет Glance/Compose (у Glance ограниченный набор примитивов — см. раздел 5).

---

## 4. Технический стек

| Слой | Технология |
|---|---|
| Язык | Kotlin |
| Виджет | **Jetpack Glance** (`androidx.glance:glance-appwidget`) |
| Приложение | Jetpack Compose + Material 3 |
| Хранилище настроек | Glance state (`GlanceStateDefinition` / `PreferencesGlanceStateDefinition`) + DataStore для приложения |
| Календарь | `android.provider.CalendarContract` (Instances + Calendars) |
| Min SDK | 26 (Android 8.0) — Glance требует 23+; берём 26 ради стабильности |
| Target/Compile SDK | 34 |
| Dynamic color | Material You через `dynamicDarkColorScheme`/`dynamicLightColorScheme` (API 31+), fallback на акцент из настроек |

### Зависимости (`app/build.gradle.kts`)
```kotlin
dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.13.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

### Разрешения (`AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.READ_CALENDAR" />
```
Запрашивать `READ_CALENDAR` рантайм-диалогом при первом запуске приложения и из экрана настроек виджета. Если не выдано — виджет показывает сетку без точек/событий (graceful degradation).

---

## 5. Структура проекта для скаффолда

```
app/
  src/main/
    AndroidManifest.xml
    java/com/example/calendarwidget/
      MainActivity.kt                  # хост Compose-приложения (экран дня)
      ui/
        DayScreen.kt                   # экран дня (недельная лента, события, FAB)
        WidgetSettingsScreen.kt        # экран настроек виджета (live preview)
        theme/Theme.kt, Color.kt, Type.kt
      widget/
        MonthWidget.kt                 # GlanceAppWidget: sizeMode Responsive, 3 бакета
        MonthWidgetReceiver.kt         # GlanceAppWidgetReceiver
        WidgetState.kt                 # ключи настроек в Glance state
        MonthGrid.kt                   # composable сетки месяца (Glance)
        AgendaList.kt                  # лента событий (Glance)
      data/
        CalendarRepository.kt          # запросы к CalendarContract
        EventModel.kt                  # data class: id, title, start, end, calendarColor, category
        Settings.kt                    # модель настроек + сериализация
    res/
      xml/month_widget_info.xml        # AppWidgetProviderInfo (resizeMode, minWidth/Height, targetCell)
      values/colors.xml, themes.xml
      values-night/themes.xml
      font/manrope_*.ttf               # см. раздел 8 (шрифт)
  build.gradle.kts
build.gradle.kts (root), settings.gradle.kts, gradle/ wrapper
```

### Размерные бакеты виджета (Glance `SizeMode.Responsive`)
```kotlin
companion object {
    val TINY  = DpSize(160.dp, 160.dp)   // 2x2  -> мини-сетка, заголовок-месяц, без навигации
    val WIDE  = DpSize(320.dp, 160.dp)   // 4x2  -> сетка + стрелки навигации, без ленты
    val LARGE = DpSize(320.dp, 320.dp)   // 4x4  -> сетка + лента событий выбранного дня
}
override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, LARGE))
```
В `provideGlance` выбирать layout по `LocalSize.current`. `month_widget_info.xml`: `resizeMode="horizontal|vertical"`, `minWidth="150dp"`, `minHeight="150dp"`, `targetCellWidth/Height` для 4×4 по умолчанию.

> Примечание по Glance: нет произвольного `backdrop-filter`/blur. Glassmorphism эмулировать полупрозрачным `background(ColorProvider)` поверх обоев + тонкой светлой границей (`cornerRadius` + полупрозрачный слой). Скругление через `GlanceModifier.cornerRadius(settings.radius.dp)` (API 31+; на 26–30 использовать предзакруглённый `@drawable` shape с нужным радиусом, генерировать набор радиусов или один компромиссный 20dp).

---

## 6. Screens / Views

### 6.1 Виджет — вид месяца (бакет 4×4)
- **Layout:** вертикальный стек, паддинг 16dp, скругление = настройка (по умолч. 28dp). Полупрозрачный фон.
  - **Header (row, space-between):** слева `«Июнь 2026»` (Manrope ExtraBold, 17sp, letter-spacing −0.01em); справа две круглые кнопки `‹` `›` (26dp, фон `white@6%`) → меняют отображаемый месяц.
  - **Week row (7 колонок):** Пн–Вс (или Вс–Сб), 10sp, weight 600, uppercase, цвет muted; выходные приглушены сильнее.
  - **Grid (7×N, N=5/6):** ячейка = кружок-«бабл» 30dp + ряд точек под ним.
    - Сегодня: рамка 1.6dp акцентом, число цветом акцента.
    - Выбранный день: заливка акцентом, число контрастным цветом (см. функцию контраста), тень `0 6 16 -6 accent`.
    - Дни вне месяца: цвет `white@18%` (тёмная) / `#14141C@20%` (светлая).
    - До 3 точек-событий под числом, цвет = категория календаря (4dp).
  - **Agenda (если включена):** разделитель сверху (1px hairline), заголовок-капс `«26 ИЮНЯ · СЕГОДНЯ»`, строки: цветной квадрат-дот 7dp + название (12.5sp, 600, ellipsis) + время справа (11sp, muted, tabular-nums).
- **Тап по дню** → выбрать день (обновить виджет) + `actionStartActivity(MainActivity, dayExtra)`.

### 6.2 Виджет 2×2 (TINY)
Компактная сетка: заголовок-месяц коротко («Июн»), бабл 22dp, числа 11sp, без навигации и ленты. Точки 3dp.

### 6.3 Виджет 4×2 (WIDE)
Сетка во всю ширину (широкие ячейки) + навигация стрелками. Без ленты (не хватает высоты).

### 6.4 Экран приложения — День (`DayScreen`)
- **Top bar (row):** `‹` (38dp круг) — пред. месяц; по центру `«Июнь 2026»` (17sp, 800); `›` — след. месяц.
- **Недельная лента (row, 7 равных колонок):** сверху день недели (11sp, muted), ниже бабл-число 38dp (radius 14dp): выбранный — заливка акцентом; сегодня — рамка акцентом; ниже точка 5dp если есть события. Тап → выбрать день.
- **Крупная дата:** бейдж акцентом капс `«Сегодня»`/`«Выбрано»`; число 60sp ExtraBold tabular-nums; рядом колонка: день недели (18sp, 700) + месяц родительный (14sp, muted).
- **Секция «События»:** заголовок-капс + счётчик акцентом (`«2 события»`).
- **Карточки событий (column, gap 10dp):** скругление 18dp, фон `white@4.5%`, рамка `white@7%`, паддинг 15dp. Внутри: цветная полоса 4×42dp (цвет категории) + колонка (время 12sp 700 цветом категории; название 15.5sp 700; категория 12sp muted) + шеврон `›`.
- **Пустой день:** пунктирный круг 46dp с `+` и подпись «Свободный день».
- **FAB:** 58dp, radius 20dp, фон акцента, тень `accent`, `+` контрастным цветом, правый-нижний угол.

### 6.5 Экран настроек виджета (`WidgetSettingsScreen`)
- Заголовок «Настройки».
- **Live preview:** карточка с обоями (24dp radius) + предпросмотр виджета (тема = выбранная), мгновенно отражает изменения.
- **Карточки настроек (radius 20dp, фон `white@4.5%`, рамка `white@7%`, паддинг 16dp):**
  1. **Акцентный цвет** — 6 кружков 36dp: `#7C9CFF #54E6C0 #FF8A6B #C9A6FF #FFD27D #8AE0A0`. Активный — кольцо (offset-ring) + scale 1.06.
  2. **Прозрачность фона** — слайдер 0.15–0.9 (шаг 0.05), значение в % справа акцентом.
  3. **Скругление углов** — слайдер 12–40 (шаг 2), «N px» справа.
  4. **Первый день недели** — сегмент-контрол «Понедельник / Воскресенье».
  5. **Список событий** — тумблер (трек 48×28, кноб 22dp), подпись «Показывать дела дня под сеткой».
  6. **Размер шрифта** — сегмент «A / A / A» (0.85 / 1.0 / 1.15).
  7. **Тема превью** — сегмент «Светлая / Тёмная».
- Применение настроек → запись в Glance state → `MonthWidget().updateAll(context)`.

---

## 7. Design Tokens

### Цвета — категории событий
| Категория | Hex | Назначение |
|---|---|---|
| work | = акцент | Работа |
| personal | `#C9A6FF` | Личное |
| health | `#FF8A6B` | Здоровье |
| social | `#54E6C0` | Друзья |

### Акцентная палитра (выбор пользователя)
`#7C9CFF` (по умолч.), `#54E6C0`, `#FF8A6B`, `#C9A6FF`, `#FFD27D`, `#8AE0A0`.
Контраст текста на акценте: яркость L = 0.2126·R+0.7152·G+0.0722·B; если L>0.62 → текст `#0B0B0F`, иначе `#FFFFFF`.

### Поверхности
| Токен | Тёмная | Светлая |
|---|---|---|
| Текст основной | `#F1F1F6` | `#1B1B23` |
| Текст muted | `white 42%` | `#16141C 45%` |
| Вне месяца | `white 18%` | `#14141C 20%` |
| Hairline | `white 10%` | `#141428 10%` |
| Стекло виджета | `rgba(20,20,28, opacity)` | `rgba(255,255,255, min(0.95, opacity+0.32))` |
| Карточка настроек | `white 4.5%`, рамка `white 7%` | — |
| Фон приложения | `linear-gradient(180°, #14131B, #0A0A0E)` | — |

### Обои (для превью/демо)
- Тёмные: `radial-gradient(130% 90% at 25% 0%, #2C2945, #16151F 46%, #09090D)`
- Светлые: `radial-gradient(130% 90% at 75% 0%, #FDF4EE, #ECEEF6 52%, #DFE3F0)`

### Типографика
- Семейство: **Manrope** (400/500/600/700/800). См. раздел 8.
- Tabular-nums для всех чисел дат/времени.
- Размеры: число дня 14.5sp (×fontScale), заголовок виджета 17sp/800, крупная дата 60sp/800, заголовок события 15.5sp/700, время 12sp/700, капс-лейблы 10–11sp/700 letter-spacing 0.06–0.08em uppercase.

### Радиусы / тени
- Виджет: настраиваемый 12–40dp (по умолч. 28).
- Бабл дня: 34% (≈ скруглённый квадрат), 30% в compact.
- Карточки: 18–20dp. FAB: 20dp. Бейдж-кнопки навигации: круг.
- Тень выбранного дня: `0 6 16 -6 accent`. Тень FAB: `0 14 30 -8 accent`.

### Размер шрифта (множитель)
S = 0.85, M = 1.0, L = 1.15 — масштабирует все sp в виджете.

---

## 8. Шрифт Manrope

Дизайн использует **Manrope** (открытая лицензия SIL OFL). Claude Code: скачать Manrope (Google Fonts) и положить `manrope_regular/medium/semibold/bold/extrabold.ttf` в `res/font/`, объявить `font-family`. Если по какой-то причине недоступен — fallback на системный (но визуально целиться в Manrope). Не использовать Roboto/Inter как замену по умолчанию без необходимости.

---

## 9. Сборка APK

1. Debug (без подписи, ставится с `adb install`):
   ```bash
   ./gradlew assembleDebug
   # app/build/outputs/apk/debug/app-debug.apk
   ```
2. Release (подписанный):
   ```bash
   keytool -genkey -v -keystore release.keystore -alias key -keyalg RSA -keysize 2048 -validity 10000
   ```
   В `app/build.gradle.kts` прописать `signingConfigs { release { ... } }`, затем:
   ```bash
   ./gradlew assembleRelease
   # app/build/outputs/apk/release/app-release.apk
   ```
3. Установка: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, затем долгий тап по рабочему столу → Виджеты → найти приложение → перетащить, изменить размер.

---

## 10. Acceptance criteria

- [ ] Виджет ставится на домашний экран и показывает сетку месяца с правильными днями.
- [ ] Изменение размера виджета переключает раскладку между 2×2 / 4×2 / 4×4.
- [ ] Тёмная/светлая тема устройства корректно меняет цвета; на API 31+ работает dynamic color.
- [ ] Дни с событиями системного календаря показывают цветные точки; тап по дню открывает экран дня.
- [ ] Экран настроек меняет акцент/прозрачность/радиус/первый день/ленту/шрифт/тему, и виджет обновляется.
- [ ] `READ_CALENDAR` запрашивается рантайм; без разрешения виджет не падает.
- [ ] `./gradlew assembleDebug` собирает рабочий `app-debug.apk`.

## 11. Files (референсы)

- `design_refs/Адаптивный календарь Android.dc.html` — все экраны на пан-канвасе (главный референс).
- `design_refs/WidgetMonth.dc.html` — компонент адаптивной сетки месяца (логика бакетов/состояний/цветов).
- `design_refs/support.js` — рантайм Design Components (нужен только чтобы открыть HTML; к Android не относится).

### Скриншоты эталона
- `design_refs/01_home_widgets.png` — виджет 4×4 на домашнем экране, светлая и тёмная тема.
- `design_refs/02_app_and_settings.png` — экран дня приложения + экран настроек (live preview, акцентные цвета).
- `design_refs/03_adaptive_sizes.png` — три бакета размеров: 2×2, 4×2, 4×4.

> Логика дат, выбор бакета по размеру, расчёт контраста, палитры и стилей состояний — в `WidgetMonth.dc.html` (метод `renderVals`) и в `Адаптивный календарь Android.dc.html`. Это самый точный источник правды по поведению.
