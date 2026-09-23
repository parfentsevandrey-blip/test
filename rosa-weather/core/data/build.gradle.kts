plugins {
    alias(libs.plugins.rosa.android.library)
    alias(libs.plugins.rosa.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.rosa.weather.core.data"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore)
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
