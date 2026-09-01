# gomobile bindings are reached from native code by exact name.
-keep class go.** { *; }
-keep class app.veil.tun.** { *; }

# tor-android calls into these from its own JNI layer.
-keep class org.torproject.jni.** { *; }
-keep class net.freehaven.tor.control.** { *; }

# The VpnService and its helpers are named in the manifest.
-keep class app.veil.vpn.vpn.** { *; }

# ViewModels are constructed reflectively by the default factory.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
