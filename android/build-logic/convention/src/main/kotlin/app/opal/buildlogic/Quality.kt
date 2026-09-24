package app.opal.buildlogic

import org.gradle.api.Project

/**
 * Static analysis for every module: ktfmt (via Spotless) and detekt with compose-rules.
 * Configuration files live in the root `config/` directory.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    extensions.configure(com.diffplug.gradle.spotless.SpotlessExtension::class.java) {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktfmt(libs.version("ktfmt")).kotlinlangStyle()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktfmt(libs.version("ktfmt")).kotlinlangStyle()
        }
    }

    extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
        parallel = true
    }
    dependencies.add("detektPlugins", libs.lib("compose-rules-detekt"))
}
