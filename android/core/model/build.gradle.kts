plugins {
    alias(libs.plugins.opal.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// Tests parse the real bundled pt_config.json (single source of truth lives in :core:tunnel).
val ptConfig = layout.projectDirectory.file("../tunnel/src/main/assets/pt_config.json")

tasks.withType<Test>().configureEach {
    inputs.file(ptConfig).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("opal.ptConfig", ptConfig.asFile.absolutePath)
}
