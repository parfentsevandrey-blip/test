# iOS-style Calendar Widget (Android)

Виджет рабочего стола Android — **вид месяца** в стиле iOS Calendar, с
**адаптивным размером**.

## Что внутри

- **Jetpack Glance** — виджет на Compose-подобном API. Адаптивный размер через
  `SizeMode.Responsive`: лаунчер выбирает компактную / среднюю / крупную
  раскладку под выбранный пользователем размер ячеек, масштабируя шрифты, отступы
  и кружок «сегодня». Resize в обе стороны прописан в `calendar_widget_info.xml`.
- **iOS-дизайн** — белая (тёмная в тёмной теме) скруглённая карточка, красный
  заголовок месяца (systemRed `#FF3B30` / `#FF453A`), серые инициалы дней недели
  (понедельник первый), приглушённые дни соседних месяцев, выходные серым и
  красный круг на сегодняшней дате. Тёмная тема — через `values-night`.
- **Всегда 6 недель** — высота сетки не «прыгает» от месяца к месяцу, как в iOS.
- **Актуальность даты** — ресивер перерисовывает виджет по системным событиям
  `DATE_CHANGED` / `TIME_SET` / `TIMEZONE_CHANGED`, плюс `WorkManager`
  перерисовывает раз в сутки около полуночи (страховка на случай Doze).
- **Тап** открывает системное приложение календаря (`CATEGORY_APP_CALENDAR`).

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

Установите APK, затем долгое нажатие на рабочий стол → **Виджеты** →
«Календарь» → перетащите и измените размер.

> Проект собран и проверен: `:app:assembleDebug` и `:app:assembleRelease`
> (R8 + минификация) проходят на AGP 8.7 / Kotlin 2.0 / compileSdk 35,
> minSdk 26.

## Структура

```
app/src/main/java/com/monthcalendar/widget/
├── CalendarModel.kt            # расчёт сетки месяца (java.time), без Android
├── CalendarWidget.kt           # Glance-виджет, 3 адаптивные раскладки
├── CalendarWidgetReceiver.kt   # привязка к AppWidget host + триггеры даты
├── CalendarRefreshWorker.kt    # ежедневная перерисовка через WorkManager
├── MainActivity.kt             # экран-инструкция с живым превью месяца
└── ui/theme/Theme.kt           # Material 3 тема для экрана приложения
```

Размеры и режим ресайза — `app/src/main/res/xml/calendar_widget_info.xml`.
Палитра (свет/тьма) — `res/values/colors.xml` и `res/values-night/colors.xml`.
