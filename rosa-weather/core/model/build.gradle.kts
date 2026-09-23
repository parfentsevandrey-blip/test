plugins {
    alias(libs.plugins.rosa.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
