# JNI binding for libtor.so: the native code looks up this class, its native methods and the two
# fields by name (Java_org_torproject_jni_TorService_*, GetFieldID).
-keep class org.torproject.jni.TorService {
    long torConfiguration;
    int torControlFd;
    native <methods>;
}

# hev-socks5-tunnel registers its natives with RegisterNatives against this exact class name.
-keep class app.opal.core.tunnel.hev.HevNative {
    native <methods>;
}

# IPtProxy (gomobile) ships its own consumer rules; kept here as a safety net.
-keep class go.** { *; }
-keep class IPtProxy.** { *; }
