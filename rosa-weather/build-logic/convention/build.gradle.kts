plugins {
    `kotlin-dsl`
}

group = "app.rosa.buildlogic"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.rosa.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.rosa.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.rosa.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.rosa.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.rosa.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
    }
}
