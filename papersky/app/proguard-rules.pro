# kotlinx.serialization keeps its generated serializers through the bundled consumer rules.
# Ktor + OkHttp ship consumer rules as well; silence optional platform classes they reference.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
