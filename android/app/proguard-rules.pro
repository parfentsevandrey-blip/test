# App-specific R8 rules. Library rules (Tor JNI, hev JNI, IPtProxy/gomobile, kotlinx.serialization)
# ship as consumer rules with their modules/artifacts.

# Keep line numbers for readable stack traces in exported diagnostics, hide source file names.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile
