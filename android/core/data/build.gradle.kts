plugins {
    alias(libs.plugins.opal.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "app.opal.core.data" }

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
