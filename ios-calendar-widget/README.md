# Material 3 Expressive Calendar Widget (Android)

> Каталог исторически называется `ios-calendar-widget`, но дизайн переведён на
> **Material 3 Expressive** и функциональность расширена до уровня платных
> приложений-календарей.

Виджет рабочего стола — **вид месяца** в стиле Material 3 Expressive, с
адаптивным размером, реальными событиями устройства и навигацией по месяцам.

## Возможности

- **Реальные события календаря** (`READ_CALENDAR`) — цветные точки на датах
  (цвет берётся из календаря события) и **лента ближайших событий** (agenda) на
  крупном размере. Разрешение запрашивается на экране настроек; без него виджет
  работает как обычная сетка месяца.
- **Навигация по месяцам** — кнопки `‹` / `сегодня` / `›`. Состояние хранится
  **на каждый экземпляр виджета** (Glance state), поэтому два виджета могут
  показывать разные месяцы.
- **Material 3 Expressive** — Material You (динамические цвета из обоев на
  Android 12+) или фиксированный акцент (индиго / зелёный / розовый / янтарь);
  крупные скругления, тональные контейнеры (`secondaryContainer`), «сегодня»
  заполненным `primary`-кругом, чипы событий. Тёмная тема — автоматически.
- **Адаптивный размер** (`SizeMode.Responsive`):
  - *компактный* — сетка месяца + точка-индикатор событий;
  - *средний* — заголовок с навигацией + сетка с точками;
  - *крупный* — то же + лента ближайших событий.
- **Настройки** (Material 3) — запрос разрешения, события вкл/выкл, начало недели
  (Пн/Вс), выбор акцента, живое превью месяца.
- **Актуальность даты** — перерисовка по `DATE_CHANGED` / `TIME_SET` /
  `TIMEZONE_CHANGED` + ежедневный `WorkManager` около полуночи.
- **Тапы** — по дате открывается системный календарь на этом дне; по подсказке о
  разрешении открывается экран настроек.

## Виджет погоды (Open-Meteo)

Отдельный виджет в том же стиле Material 3 Expressive (динамические цвета,
адаптивный размер). Данные — **Open-Meteo**: бесплатный погодный API **без
ключа** (прогноз + геокодинг города).

- **Компактный** — город, текущая температура, иконка и условие.
- **Средний** — добавляет «ощущается», влажность, ветер.
- **Крупный** — прогноз на 5 дней (день · иконка · макс/мин).
- Поиск города (геокодинг Open-Meteo), выбор единиц (°C/км/ч или °F/mph),
  кэш последнего ответа + фоновое обновление раз в час (WorkManager, требует
  сети). Настройки: иконка приложения → «Настройки погоды», или тап по виджету.

## Сборка

Требуется Android Studio (Ladybug+) **или** Android SDK + JDK 17.

```bash
# из каталога ios-calendar-widget/
./gradlew :app:assembleDebug      # APK -> app/build/outputs/apk/debug/
```

`local.properties` с путём к SDK (Android Studio создаёт сам):

```properties
sdk.dir=/path/to/Android/sdk
```

> Проект собран и проверен: `:app:assembleDebug` и `:app:assembleRelease`
> (R8 + минификация) проходят на AGP 8.7 / Kotlin 2.0 / compileSdk 35,
> minSdk 26.

## Структура

```
app/src/main/java/com/monthcalendar/widget/
├── CalendarModel.kt            # сетка месяца (java.time): месяц/начало недели/даты
├── CalendarRepository.kt       # чтение событий устройства (CalendarContract.Instances)
├── CalendarSettings.kt         # DataStore: начало недели, события, акцент
├── AccentSchemes.kt            # фиксированные палитры Material 3 для акцентов
├── WidgetState.kt              # per-widget смещение месяца + action-колбэки навигации
├── CalendarWidget.kt           # Glance-виджет: M3 Expressive, события, agenda, 3 размера
├── CalendarWidgetReceiver.kt   # привязка к AppWidget host + триггеры даты
├── CalendarRefreshWorker.kt    # ежедневная перерисовка через WorkManager
├── MainActivity.kt             # настройки календаря (M3) + ссылка на погоду
├── WeatherActivity.kt          # настройки погоды: поиск города, единицы, превью
├── weather/
│   ├── WeatherStore.kt         # DataStore: локация/единицы + кэш ответа
│   ├── WeatherData.kt          # модель + маппинг WMO-кодов (текст + эмодзи)
│   ├── WeatherRepository.kt    # Open-Meteo: прогноз + геокодинг (HttpURLConnection)
│   ├── WeatherWidget.kt        # Glance-виджет погоды, 3 адаптивных размера
│   ├── WeatherWidgetReceiver.kt
│   └── WeatherWorker.kt        # обновление раз в час (WorkManager)
└── ui/theme/Theme.kt           # Material 3 (динамическая) тема экранов приложения
```

Размеры и режим ресайза — `app/src/main/res/xml/calendar_widget_info.xml`.
