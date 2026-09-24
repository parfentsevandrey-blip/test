import app.opal.buildlogic.ExtractJniLibsTask
import app.opal.buildlogic.UpdateBuiltinBridgesTask

plugins {
    alias(libs.plugins.opal.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

android {
    namespace = "app.opal.core.tunnel"
    ndkVersion = libs.versions.ndk.get()

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        ndk { abiFilters += supportedAbis }
        externalNativeBuild {
            ndkBuild {
                abiFilters += supportedAbis
                targets += "hev-socks5-tunnel"
                arguments +=
                    listOf(
                        "NDK_APPLICATION_MK=${file("src/main/cpp/Application.mk").absolutePath}",
                        // Upstream commit of the vendored sources (no .git in the vendored copy).
                        "REV_ID=e802f02",
                    )
                // JNI registration target for hev (see HevNative.kt).
                cFlags += listOf("-DPKGNAME=app/opal/core/tunnel/hev", "-DCLSNAME=HevNative")
                // lwIP asserts embed __FILE__: strip the checkout location so the library is
                // byte-identical wherever it is built (and leaks no local paths).
                cFlags += "-ffile-prefix-map=${file("src/main/cpp").absolutePath}/="
            }
        }
    }

    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }
}

// Only libtor.so is taken from tor-android; the JNI binding class is our own
// (org.torproject.jni.TorService in this module) — see CLAUDE.md ADR 1.
val torNative =
    configurations.create("torNative") {
        isCanBeConsumed = false
        isCanBeResolved = true
        isTransitive = false
    }

dependencies {
    torNative(libs.tor.android) { artifact { type = "aar" } }

    api(project(":core:model"))
    api(project(":core:data"))
    implementation(libs.iptproxy)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}

val extractTorNatives =
    tasks.register<ExtractJniLibsTask>("extractTorNatives") {
        aars.from(torNative)
        abis.set(supportedAbis)
        outputDir.set(layout.buildDirectory.dir("generated/torJniLibs"))
    }

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            extractTorNatives,
            ExtractJniLibsTask::outputDir,
        )
        // Release: no DWARF at all. It is stripped from the APK anyway, but the ELF build-id is
        // hashed over it and it records the build directory — without it the library is
        // byte-identical wherever it is built (reproducible APKs).
        if (variant.buildType == "release") variant.externalNativeBuild?.cFlags?.add("-g0")
    }
}

// Manual maintenance task: ./gradlew :core:tunnel:updateBuiltinBridges (see CLAUDE.md ADR 11).
tasks.register<UpdateBuiltinBridgesTask>("updateBuiltinBridges") {
    group = "opal"
    description = "Downloads the current built-in bridges (pt_config.json) from tor-browser-build."
    url.set(
        providers
            .gradleProperty("ptConfigUrl")
            .orElse(
                "https://gitlab.torproject.org/tpo/applications/tor-browser-build/-/raw/main/" +
                    "projects/tor-expert-bundle/pt_config.json"
            )
    )
    target.set(layout.projectDirectory.file("src/main/assets/pt_config.json"))
}
