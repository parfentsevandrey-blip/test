# Keep Glance widget receivers referenced from the manifest / AppWidgetProvider.
-keep class com.lumina.calendarwidget.widget.** { *; }

# Glance uses generated layouts and reflection for its RemoteViews translation layer.
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Kotlin metadata / coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
