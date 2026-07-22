# Lumina Calendar — a premium month widget for Android

A commercial-grade home-screen **month calendar widget** for Android, built entirely on the
latest platform standards: **Android 17 (API 37)**, **Jetpack Glance**, **Jetpack Compose**,
**Material 3 / Material You**, and the **AGP 9 / built-in-Kotlin** build system.

The design language is *editorial*: one hairline, one accent (today), a permanent 6×7 grid, and a
lot of confident whitespace — the polish comes from typography, spacing, and restraint rather than
chrome. It ships with **11 hand-tuned themes**, a full **Material You** dynamic-color mode, and
**40+ customization options**, all with a **live in-app preview** that mirrors the widget exactly.

<p align="center"><em>Change anything in the app and every placed widget updates instantly.</em></p>

---

## Highlights

- **Looks like a product, not a template.** Temperature-matched inks, nested corner radii, a
  two-tone masthead (`July 2026`), tabular day cells with zero digit jitter.
- **11 curated themes** — Paper, Ink, Meridian, Obsidian Gold, Midnight Sapphire, Emerald Noir,
  Daylight, Twilight, Blossom, Alabaster, Onyx (AMOLED) — plus fully custom colors.
- **Material You dynamic color** (Android 12+) that recolors the widget from the wallpaper.
- **Live preview** in the app: a Compose replica driven by the *same* settings + calendar engine
  as the real Glance widget, so what you see is what you get.
- **Responsive**: the widget reads its own measured size and adapts density and weekday-label
  length automatically (compact drops the year; extra-large switches to 2-letter labels).
- **Genuinely interactive**: tap a day to select it (state persists and the grid redraws), tap the
  header to jump to today, or open the system calendar at that date.
- **Always correct**: re-renders on midnight rollover, timezone/locale change, and every setting
  edit.

## The full feature set (40+ controls)

| Group | Controls |
| --- | --- |
| **Theme & color** | Theme mode (System / Light / Dark / AMOLED), 11 presets, Material You dynamic color, custom accent/surface/day/muted/weekend colors, preset background gradients (Sunrise, Ocean, Forest, Twilight, Slate, Obsidian), background opacity, corner radius |
| **Layout** | Calendar view (Month / Two weeks / Single week / Agenda), density, header format, header alignment, weekday hairline, full grid lines, other-month days, fixed 6-row height |
| **Typography** | Weekday label format, label case, header weight, day-number weight, global text size |
| **Calendar** | First day of week, ISO week numbers, weekend days, weekend highlighting, today highlight style (rounded square / circle / inlaid jewel / ring / bold / none), selected-date style, event indicators + style |
| **Behavior** | Tap-a-day action, tap-the-header action |
| **Advanced** | Time format, locale override |

## Architecture

```
app/src/main/java/com/lumina/calendarwidget/
├── MainActivity.kt                 # Compose entry point (edge-to-edge)
├── CalendarApplication.kt          # holds the shared SettingsRepository
├── calendar/
│   ├── CalendarModel.kt            # pure java.time month-grid builder (unit-testable)
│   └── EventRepository.kt          # optional CalendarContract event lookup (permission-gated)
├── data/
│   ├── WidgetSettings.kt           # the settings model + every enum
│   ├── SettingsRepository.kt       # DataStore persistence (single source of truth)
│   └── ThemeCatalog.kt             # 11 presets + color resolution (theme mode, custom, opacity)
├── widget/
│   ├── CalendarGlanceWidget.kt     # the Glance widget: renders month/week/agenda views
│   ├── CalendarWidgetReceiver.kt   # widget host + midnight/timezone refresh
│   ├── WidgetActions.kt            # tap callbacks (select date, jump to today) + intents
│   └── WidgetSupport.kt            # color/typography/dynamic-color/format helpers
└── ui/
    ├── CustomizeScreen.kt          # the customization screen (all sections)
    ├── CustomizeViewModel.kt       # instant preview + debounced persist & widget refresh
    ├── WidgetPreview.kt            # Compose replica of the widget
    ├── Controls.kt                 # reusable Material 3 controls (chips, sliders, swatches…)
    └── theme/                      # app Material 3 theme
```

**Why the widget and the app never drift:** both the live preview and the Glance widget resolve
their palette through `ThemeCatalog.resolve(...)` and build their grid through `CalendarModel`.
Editing a control writes to the one `SettingsRepository` (DataStore) and asks Glance to re-render,
so the home-screen widget and the in-app preview are always the same picture.

## Tech stack

| Component | Version |
| --- | --- |
| compileSdk / targetSdk | **37 (Android 17, "Cinnamon Bun")** |
| minSdk | 26 (Android 8.0) |
| Android Gradle Plugin | **9.1.1** (built-in Kotlin, KGP 2.2.10) |
| Gradle | 9.1.0 · JDK 17 |
| Jetpack Glance | 1.1.1 (`glance-appwidget`, `glance-material3`) |
| Compose BOM | 2026.06.00 · Material 3 |
| DataStore | Preferences 1.1.7 |

## Building

Requires **JDK 17** and the **Android 17 (API 37)** SDK + build-tools 37.

```bash
# From this directory. The wrapper jar is generated on first run / first Android Studio sync.
gradle wrapper --gradle-version 9.1.0     # one-time, if gradlew.jar isn't present
./gradlew :app:assembleDebug              # build the APK
./gradlew :app:installDebug               # install on a connected device/emulator
```

Or just open `android-calendar-widget/` in Android Studio (Ladybug+ / an AGP 9-compatible build)
and press Run.

### Add the widget
Long-press the home screen → **Widgets** → **Lumina Calendar** → drag it out. Resize it to taste;
the layout adapts. Open the app any time to restyle it — changes apply live.

### Event indicators (optional)
Event dots are off until you grant calendar access from **Calendar → Event indicators → Allow
calendar access**. Nothing else needs the permission and the widget is fully functional without it.

## Notes & limitations

- Glance renders `RemoteViews`, so there are no gradients/shadows/animations in the widget itself —
  every effect here is a solid color, a corner radius, a drawable, or type. Preset background
  gradients use bundled `widget_bg_*` drawables.
- Rounded corners on the widget shell use the Glance `cornerRadius` modifier (best on API 31+).
- The "inlaid jewel" today style renders as a filled rounded tile; its faux-glow inner stroke is a
  dark-theme nicety and degrades to the flat fill elsewhere.

## License

Sample/reference project — use it however you like.
