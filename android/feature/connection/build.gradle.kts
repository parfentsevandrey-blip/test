plugins { alias(libs.plugins.opal.android.feature) }

android { namespace = "app.opal.feature.connection" }

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))
    // QR codes of bridges (bridges.torproject.org, @GetBridgesBot): camera + on-device decoder,
    // no Google Play services.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.zxing.cpp)
}
