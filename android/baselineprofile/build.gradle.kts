plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.opal.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = 0
    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    targetProjectPath = ":app"
    // Macrobenchmarks instrument another app: the test APK runs in its own process.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

// Generate on a connected device or a Gradle Managed Device:
//   ./gradlew :app:generateBaselineProfile
baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
