import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Rust core: arm64 for phones, x86_64 for the emulator. `-PhaloAbis=x86_64` builds one.
val haloAbis = (findProperty("haloAbis") as String? ?: "arm64-v8a,x86_64").split(",")
val haloRoot: File = rootDir.parentFile
val rustOut = layout.buildDirectory.dir("generated/halo")

// One version for the whole project: the workspace's, in Cargo.toml.
val haloVersion: String = haloRoot.resolve("Cargo.toml").readLines()
    .dropWhile { it.trim() != "[workspace.package]" }
    .first { it.trim().startsWith("version") }
    .substringAfter('"')
    .substringBefore('"')
val haloVersionCode: Int = haloVersion.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "dev.halo.app"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "dev.halo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = haloVersionCode
        versionName = haloVersion
        ndk { abiFilters += haloAbis }
    }

    buildTypes {
        release {
            // JNA and the generated bindings rely on reflection; no shrinking for now.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compressed native libraries: a much smaller download for sideloading.
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    sourceSets["main"].java.srcDir(rustOut.map { it.dir("kotlin") })
    sourceSets["main"].jniLibs.srcDir(rustOut.map { it.dir("jniLibs") })
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val buildRust by tasks.registering(Exec::class) {
    description = "Builds the Rust core for Android and generates its Kotlin bindings."
    workingDir = haloRoot
    commandLine(
        listOf("bash", "scripts/build-android-rust.sh", rustOut.get().asFile.absolutePath) + haloAbis,
    )
    environment("ANDROID_NDK_HOME", android.ndkDirectory.absolutePath)
    inputs.dir(haloRoot.resolve("crates"))
    inputs.file(haloRoot.resolve("Cargo.lock"))
    inputs.file(haloRoot.resolve("scripts/build-android-rust.sh"))
    inputs.property("abis", haloAbis)
    outputs.dir(rustOut)
}

tasks.named("preBuild") {
    dependsOn(buildRust)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    // Scanning pairing QR codes: CameraX for the preview, ML Kit with the model
    // bundled in the app, so it works offline and without Google Play services.
    val camerax = "1.5.3"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
