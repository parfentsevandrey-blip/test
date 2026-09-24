# App-specific R8 rules. Library rules (Tor JNI, hev JNI, IPtProxy/gomobile, kotlinx.serialization)
# ship as consumer rules with their modules/artifacts.

# Keep line numbers for readable stack traces in exported diagnostics, hide source file names.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile

# Navigation 3 saves back-stack keys by class name and restores them reflectively
# (Class.forName + kotlinx.serialization serializer(KClass)).
-keep class app.opal.ui.Dest { *; }
-keep class app.opal.ui.Dest$* { *; }
