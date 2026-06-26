# Адаптивный календарь — Android App Widget + приложение

Нативная реализация дизайн-хэндоффа «Адаптивный календарь» (Kotlin + Jetpack
Glance + Compose). Воссоздаёт виджет месяца на рабочем столе, экран дня и экран
настроек виджета по ТЗ из `design_handoff_calendar_widget/README.md`.

## Готовый APK

Собранный отладочный APK лежит в [`dist/app-debug.apk`](dist/app-debug.apk).

```bash
adb install -r dist/app-debug.apk
```

Затем: долгий тап по рабочему столу → «Виджеты» → «Адаптивный календарь» →
перетащить и менять размер (2×2 / 4×2 / 4×4).

## Сборка из исходников

Требуется JDK 17+ и Android SDK (Platform 34, Build-Tools 34.0.0).

```bash
# укажите путь к SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

> Wrapper настроен на Gradle 8.14.3. В офлайн/прокси-окружении без доступа к
> `services.gradle.org` можно собрать той же версией Gradle напрямую:
> `gradle :app:assembleDebug`.

## Что реализовано (по acceptance criteria)

| Критерий | Статус |
|---|---|
| Виджет ставится и показывает сетку месяца с правильными днями | ✅ `MonthWidget` + `MonthGrid` |
| Ресайз переключает раскладку 2×2 / 4×2 / 4×4 | ✅ `SizeMode.Responsive(TINY/WIDE/LARGE)` |
| Тёмная/светлая тема устройства; dynamic color на API 31+ | ✅ виджет читает `uiMode`; приложение — `dynamicDark/LightColorScheme` |
| События системного календаря → точки; тап по дню открывает экран дня | ✅ `CalendarRepository` (Instances) + `actionStartActivity` |
| Экран настроек меняет акцент/прозрачность/радиус/первый день/ленту/шрифт/тему, виджет обновляется | ✅ `WidgetSettingsScreen` → DataStore → `MonthWidget().updateAll()` |
| `READ_CALENDAR` запрашивается рантайм; без разрешения виджет не падает | ✅ graceful degradation (пустые события) |
| `./gradlew assembleDebug` собирает рабочий APK | ✅ |

## Структура

```
app/src/main/java/com/example/calendarwidget/
  MainActivity.kt              # хост Compose, запрос READ_CALENDAR, deep-link дня из виджета
  ui/
    App.kt                     # навигация День ⇄ Настройки
    DayScreen.kt               # экран дня (недельная лента, крупная дата, карточки, FAB)
    WidgetSettingsScreen.kt    # настройки + live-preview
    WidgetPreviewCard.kt       # Compose-превью виджета (LARGE)
    theme/                     # Color / Type (Manrope) / Theme (Material You)
  widget/
    MonthWidget.kt             # GlanceAppWidget, 3 бакета размеров
    MonthWidgetReceiver.kt     # GlanceAppWidgetReceiver
    MonthGrid.kt / AgendaList.kt
    WidgetState.kt             # displayed month + навигация (Glance state)
    WidgetPalette.kt           # токены цвета (порт renderVals)
  data/
    CalendarRepository.kt      # CalendarContract.Instances
    CalendarMath.kt            # сетка/офсеты/имена месяцев (порт renderVals)
    EventModel.kt / Settings.kt / ColorUtils.kt
res/
  xml/month_widget_info.xml    # resizeMode, minWidth/Height 150dp, targetCell 4×4
  font/manrope_variable.ttf    # Manrope (SIL OFL)
  values*/…, drawable/…, mipmap-anydpi-v26/…
```

## Замечания по реализации

- **Glassmorphism** в Glance эмулируется полупрозрачным фоном поверх обоев +
  светлой границей (у Glance нет `backdrop-filter`/blur) — как и указано в ТЗ.
- **Выбранный день виджета** = сегодня; тап по любому дню открывает экран дня на
  этой дате (`actionStartActivity` гарантированно запускает приложение). Лента
  событий показывается в бакете 4×4 для текущего месяца.
- **Контраст текста на акценте**, палитры категорий, офсеты сетки и имена
  месяцев портированы 1:1 из `WidgetMonth.dc.html → renderVals()`.
- **Manrope** — переменный шрифт; веса задаются через ось `wght`
  (`FontVariation`), поддерживается на API 26+.
