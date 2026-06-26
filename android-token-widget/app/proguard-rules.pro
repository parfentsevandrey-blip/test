# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.claude.tokenwidget.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.claude.tokenwidget.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
