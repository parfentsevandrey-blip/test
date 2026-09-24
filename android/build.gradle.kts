// Plugins are declared once here (apply false) so that every module shares a single classpath;
// module build files apply them through the convention plugins in build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}
