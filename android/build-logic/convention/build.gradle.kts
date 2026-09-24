plugins { `kotlin-dsl` }

group = "app.opal.buildlogic"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

tasks { validatePlugins { enableStricterValidation = true; failOnWarning = true } }

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "opal.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "opal.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "opal.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "opal.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "opal.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
