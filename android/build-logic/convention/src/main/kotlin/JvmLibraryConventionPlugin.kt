import app.opal.buildlogic.configureJvmToolchain
import app.opal.buildlogic.configureKotlin
import app.opal.buildlogic.configureQuality
import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureJvmToolchain()
            configureKotlin()
            configureQuality()
        }
}
