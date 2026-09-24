import app.opal.buildlogic.configureAndroid
import app.opal.buildlogic.configureQuality
import app.opal.buildlogic.libs
import app.opal.buildlogic.version
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                lint.targetSdk = libs.version("targetSdk").toInt()
                testOptions.targetSdk = libs.version("targetSdk").toInt()
                // Library modules consume R8 rules from their own consumer-rules.pro.
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
            }
            configureQuality()
        }
}
