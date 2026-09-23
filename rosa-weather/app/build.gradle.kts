plugins {
    alias(libs.plugins.rosa.android.application)
    alias(libs.plugins.rosa.android.compose)
    alias(libs.plugins.rosa.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.rosa.weather"

    defaultConfig {
        applicationId = "app.rosa.weather"
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable APK out of the box.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
