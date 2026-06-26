# Claude Token Widget (Android)

Динамически обновляющийся виджет для рабочего стола Android, который показывает
расход токенов Claude — **текущую сессию** и **недельные лимиты**. Дизайн в стиле
**Material 3 Expressive**, виджет **изменяет размер** в обоих направлениях.

![compact / medium / large](docs/preview.md)

## Что внутри

- **Jetpack Glance** — виджет на Compose-подобном API. Изменение размера сделано
  через `SizeMode.Responsive`: лаунчер сам выбирает компактную, среднюю или
  крупную раскладку под размер ячеек, без отдельного кода в манифесте.
- **Material 3 Expressive** — высокий радиус скругления поверхности, фирменный
  «глиняный» акцент Claude, толстые скруглённые progress-дорожки. На Android 12+
  включается **dynamic color** (палитра из обоев), общая для приложения и
  виджета.
- **Динамическое обновление** — `WorkManager` обновляет снимок расхода каждые
  15 минут (плюс мгновенное обновление при добавлении и по кнопке ↻ на крупном
  виджете). Данные кэшируются в `DataStore`, поэтому виджет рисуется мгновенно.
- **Конфигурационный экран** (Compose, Material 3) — открывается по тапу на
  виджет.

## Сборка

Требуется Android Studio (Ladybug+) **или** Android SDK + JDK 17.

```bash
# из каталога android-token-widget/
./gradlew :app:assembleDebug      # APK -> app/build/outputs/apk/debug/
```

Перед сборкой создайте `local.properties` с путём к SDK (Android Studio делает
это автоматически):

```properties
sdk.dir=/path/to/Android/sdk
```

Затем установите APK и добавьте виджет «Claude Tokens» на рабочий стол через
долгое нажатие → Виджеты.

> Проект собран и проверен: `:app:assembleDebug` и `:app:assembleRelease`
> (R8 + минификация) проходят на AGP 8.7 / Kotlin 2.0 / compileSdk 35,
> minSdk 26.

## Откуда берутся данные

У потребительской подписки Claude **нет публичного API** для остатка лимитов
сессии/недели, поэтому слой данных сделан подключаемым — интерфейс
`UsageRepository` с двумя режимами (переключаются на экране настроек):

1. **Локально / ручной ввод** (по умолчанию, работает из коробки). Вы вводите
   лимиты и текущий расход вручную; фоновый воркер детерминированно «крутит»
   снимок во времени и перекатывает окна по сбросу — так видно, что виджет
   действительно обновляется, без какого-либо бэкенда.

2. **Удалённый API**. Укажите HTTP-эндпоинт, возвращающий JSON в форме
   `UsageData` — виджет будет опрашивать его в фоне (с заголовками
   `Authorization: Bearer <key>` и `x-api-key`, если задан ключ).

   ```json
   {
     "sessionTokensUsed": 84000,
     "sessionTokenLimit": 200000,
     "weeklyTokensUsed": 1260000,
     "weeklyTokenLimit": 7000000,
     "sessionResetAt": 1750000000000,
     "weeklyResetAt": 1750500000000,
     "updatedAt": 1749990000000
   }
   ```

   Эндпоинтом может быть, например, небольшой релей, который вы запускаете
   локально и который читает логи Claude Code, либо обёртка над Anthropic
   Admin Usage & Cost API, маппящая ответ в эту схему.

## Структура

```
app/src/main/java/com/claude/tokenwidget/
├── MainActivity.kt              # хост конфигурационного экрана
├── data/
│   ├── UsageData.kt             # модель снимка + проценты/доли
│   ├── UsageDataStore.kt        # DataStore: кэш снимка + конфиг
│   └── UsageRepository.kt       # источник истины: local (симуляция) + remote
├── widget/
│   ├── TokenWidget.kt           # Glance-виджет, 3 адаптивные раскладки
│   ├── TokenWidgetReceiver.kt   # привязка к AppWidget host, планировщик
│   └── UsageWorker.kt           # WorkManager refresh + RefreshAction (кнопка ↻)
└── ui/
    ├── ConfigScreen.kt          # настройки + предпросмотр (Compose M3)
    └── theme/Theme.kt           # Material 3 (Expressive) + dynamic color
```

Размеры виджета и режим изменения размера заданы в
`app/src/main/res/xml/token_widget_info.xml`.
