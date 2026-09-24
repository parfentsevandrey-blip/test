plugins {
    alias(libs.plugins.opal.android.library)
    alias(libs.plugins.opal.android.compose)
}

android { namespace = "app.opal.core.designsystem" }

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.ui)
    api(libs.kyant.backdrop)
    api(libs.kyant.shapes)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.window.core)
    api(libs.androidx.navigationevent.compose)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit)
}
