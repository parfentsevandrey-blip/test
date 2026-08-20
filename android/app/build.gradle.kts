import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    // One namespace for both applications, because they share a source tree and therefore share
    // an R class. The applicationId is what actually separates them on a device, and that is set
    // per flavour below.
    namespace = "app.quire"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        versionCode = 56
        versionName = "9.10"
    }

    /**
     * Three applications out of one tree.
     *
     * src/main holds only what both need — the Oklch palette, the widget's card surface, the
     * Material theme — and everything else lives in src/calendar or src/weather, including the
     * manifests. That is what lets the calendar ask for READ_CALENDAR and nothing else while the
     * weather app asks for a location and the network and never sees a calendar.
     */
    flavorDimensions += "app"
    productFlavors {
        create("calendar") {
            dimension = "app"
            applicationId = "app.quire.calendar"
        }
        create("weather") {
            dimension = "app"
            applicationId = "app.quire.weather"
        }
        // The joke that turned out to be a good architecture test: the same forecast, drawn as
        // Windows 95. It installs beside the others and shares everything below the interface.
        create("retro") {
            dimension = "app"
            applicationId = "app.quire.retro"
        }
    }

    /**
     * The weather half of the tree is in three parts, not one.
     *
     * `src/wxcore` is everything that has no opinion about pixels — the Open-Meteo client, the
     * store, the settings, the sky codes, the wake-up jobs and the widget provider — and both
     * weather-facing flavours compile it. `src/weather` is the Material 3 Expressive interface;
     * `src/retro` is the 1995 one. Neither can see the other, which is what keeps the joke from
     * quietly becoming a fork: a bug fixed in the forecast is fixed in both apps, and a stray
     * `MaterialTheme` in the retro build simply will not compile.
     */
    sourceSets {
        // `kotlin`, not `java`: from AGP 9 the Kotlin sources are their own directory set, and a
        // path added only to `java` compiles nothing at all — silently, until every symbol in it
        // comes back unresolved.
        getByName("weather") {
            kotlin.srcDir("src/wxcore/java")
            res.srcDir("src/wxcore/res")
        }
        getByName("retro") {
            kotlin.srcDir("src/wxcore/java")
            res.srcDir("src/wxcore/res")
        }
        getByName("testWeather") { kotlin.srcDir("src/testWxcore/java") }
        getByName("testRetro") { kotlin.srcDir("src/testWxcore/java") }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "DebugProbesKt.bin",
            "META-INF/*.version",
            "kotlin-tooling-metadata.json",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
