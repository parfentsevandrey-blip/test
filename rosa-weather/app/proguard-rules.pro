# Navigation 3 restores the back stack by serializing NavKey instances.
-keep class * implements androidx.navigation3.runtime.NavKey { *; }
-keepclassmembers class * implements androidx.navigation3.runtime.NavKey {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor pulls optional logging integrations we don't ship.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
