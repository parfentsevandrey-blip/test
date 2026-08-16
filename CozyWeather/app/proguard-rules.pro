# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

-keep,includedescriptorclasses class com.cozyhome.weather.**$$serializer { *; }
-keepclassmembers class com.cozyhome.weather.** {
    *** Companion;
}
-keepclasseswithmembers class com.cozyhome.weather.** {
    kotlinx.serialization.KSerializer serializer(...);
}
