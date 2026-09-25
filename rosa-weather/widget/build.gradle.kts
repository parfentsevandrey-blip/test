plugins {
    alias(libs.plugins.rosa.android.library)
    alias(libs.plugins.rosa.android.compose)
    alias(libs.plugins.rosa.hilt)
}

android {
    namespace = "app.rosa.weather.widget"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Opt-in regeneration of the static picker previews from the real renderer.
    if (project.hasProperty("rosa.previews")) systemProperty("rosa.previews", "true")
    // Opt-in export of documentation images (README) from the gallery tests.
    if (project.hasProperty("rosa.docs")) systemProperty("rosa.docs", rootProject.file("docs/images").absolutePath)
    // Opt-in regeneration of the live weather tiles (res/drawable|layout|color/motion_*).
    if (project.hasProperty("rosa.motion")) systemProperty("rosa.motion", "true")
}
