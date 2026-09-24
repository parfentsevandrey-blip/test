package app.opal.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Shared Android configuration: SDK levels, Java 21 bytecode, lint policy. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        compileSdkMinor = 0
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_21
        compileOptions.targetCompatibility = JavaVersion.VERSION_21
        lint.apply {
            abortOnError = true
            checkDependencies = true
            warningsAsErrors = false
            // Obsolete dependency checks need network access to Maven metadata; versions are
            // pinned deliberately in the catalog and reviewed there instead.
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            // False positive: the ABI list is a variable (arm64-v8a, armeabi-v7a, x86_64 — x86_64
            // is built and shipped, see the APK splits); lint only reads literal filters.
            disable += "ChromeOsAbiSupport"
        }
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
    }
    configureKotlin()
    configureRobolectric()
}

/** JVM settings for Robolectric tests (SDK 36 runtime on JDK 21, screenshot capture). */
private fun Project.configureRobolectric() {
    tasks.withType<Test>().configureEach {
        // Hardware-accelerated capture: RenderEffect blur, shadows and AGSL show up in screenshots.
        systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        // Optional mirror for Robolectric's own android-all download (~/.gradle/gradle.properties).
        providers.gradleProperty("robolectricRepoUrl").orNull?.let {
            systemProperty("robolectric.dependency.repo.url", it)
        }
        maxHeapSize = "2g"
        // Robolectric's SDK 36 runtime reaches into FileDescriptor internals (JDK 21 module rules).
        jvmArgs(
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
    }
}

/** Kotlin compiler flags shared by Android and JVM modules. */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
            )
        }
    }
}

internal fun Project.configureJvmToolchain() {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(libs.version("jvmToolchain").toInt()))
    }
}
