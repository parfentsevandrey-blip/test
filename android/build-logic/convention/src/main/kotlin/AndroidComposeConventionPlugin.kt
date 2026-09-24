import app.opal.buildlogic.lib
import app.opal.buildlogic.libs
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<CommonExtension> { buildFeatures.compose = true }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("config/compose/stability.conf")
                )
                // Opt-in reports: ./gradlew assembleRelease -PcomposeReports=true
                if (providers.gradleProperty("composeReports").orNull == "true") {
                    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                    metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
                }
            }
            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
}
