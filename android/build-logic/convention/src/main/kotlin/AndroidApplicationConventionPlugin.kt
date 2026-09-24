import app.opal.buildlogic.configureAndroid
import app.opal.buildlogic.configureQuality
import app.opal.buildlogic.libs
import app.opal.buildlogic.version
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            }
            configureQuality()
        }
}
