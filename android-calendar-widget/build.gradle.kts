// Top-level build file. Configuration common to all sub-projects lives here.
// AGP 9.x ships built-in Kotlin support, so the standalone Kotlin Android plugin
// is intentionally NOT applied — only the Compose compiler plugin is added.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
