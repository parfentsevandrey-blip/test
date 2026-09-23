plugins {
    alias(libs.plugins.rosa.android.library)
    alias(libs.plugins.rosa.android.compose)
}

android {
    namespace = "app.rosa.weather.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.androidx.graphics.shapes)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
