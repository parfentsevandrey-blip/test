import java.util.Properties

plugins {
    alias(libs.plugins.opal.android.application)
    alias(libs.plugins.opal.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

// Release signing: keystore.properties (git-ignored) or environment variables (CI).
val signing =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun signingValue(key: String, env: String): String? =
    signing.getProperty(key) ?: providers.environmentVariable(env).orNull

val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "app.opal"
    // Same NDK as the native build: used to strip debug symbols from every .so at packaging.
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "app.opal"
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "OPAL_KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "OPAL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OPAL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OPAL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else null
            // Reproducible: no build timestamps / VCS info in the APK.
            vcsInfo.include = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*supportedAbis.toTypedArray())
            isUniversalApk = true
        }
    }

    packaging {
        // Compressed native libraries: roughly halves the download size of an APK meant for manual
        // installation (IPtProxy's Go library alone is ~24 MB per ABI uncompressed). Libraries are
        // 16 KB aligned ELF files, so extraction works on 16 KB page devices.
        jniLibs {
            useLegacyPackaging = true
            // Dependencies also ship x86 (32-bit emulators only); not distributed.
            excludes += "lib/x86/**"
        }
        resources {
            excludes +=
                listOf(
                    "META-INF/*.version",
                    "META-INF/**/LICENSE*",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                )
        }
    }

    // Per-app language switching needs every language in every install.
    bundle { language { enableSplit = false } }

    androidResources {
        // Generates the LocaleConfig for per-app language (ru default, en).
        generateLocaleConfig = true
        // Only the app's languages: library translations for other locales would mix languages
        // in the UI and add size.
        localeFilters += listOf("ru", "en")
    }

    buildFeatures { buildConfig = true }

    testOptions { unitTests { isIncludeAndroidResources = true } }

    dependenciesInfo {
        // No Google-encrypted dependency metadata blob in APKs meant for sideloading.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:connection"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.kotlinx.collections.immutable)

    "baselineProfile"(project(":baselineprofile"))

    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Screenshot goldens live next to the tests and are committed (verifyRoborazziDebug compares).
roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}
