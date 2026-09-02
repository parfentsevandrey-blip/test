plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself; the JetBrains Android plugin must not be
    // applied alongside it.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.veil.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.veil.vpn"
        minSdk = 26
        targetSdk = 37
        versionCode = 17
        versionName = "0.6.1"

        // Only ABIs for which every native dependency (tor, lyrebird/snowflake,
        // veiltun) ships a library. Shipping a mismatched set is how VPN apps
        // end up crashing at first connect on unusual devices.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            // Populated from the environment so no key material lives in git.
            val storePath = System.getenv("VEIL_KEYSTORE")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = System.getenv("VEIL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VEIL_KEY_ALIAS")
                keyPassword = System.getenv("VEIL_KEY_PASSWORD")
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("VEIL_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                // Lets `assembleRelease` produce an installable artifact during
                // development; CI must provide a real key.
                signingConfigs.getByName("debug")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/*.version",
            )
        }
        jniLibs {
            // tor and the pluggable transports are executed from the native
            // library directory; they must stay as real files on disk.
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }
}

dependencies {
    // veiltun.aar: the userspace TCP/IP stack and the pluggable transports,
    // built as one gomobile library. Two gomobile libraries cannot coexist in
    // one app — each ships its own libgojni.so and only one would ever load.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // Dispatchers.Main needs the Android dispatcher; relying on it arriving
    // transitively is how a VPN service ends up crashing on its first stop().
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.graphics.shapes)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tor.android)
}
