import app.opal.buildlogic.lib
import app.opal.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Android library + Compose + the shared core modules every screen needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("opal.android.library")
            pluginManager.apply("opal.android.compose")
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("kotlinx-collections-immutable"))
                add("testImplementation", libs.lib("junit"))
                add("testImplementation", libs.lib("kotlinx-coroutines-test"))
                add("testImplementation", libs.lib("turbine"))
            }
        }
}
