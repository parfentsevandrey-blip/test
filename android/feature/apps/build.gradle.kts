plugins { alias(libs.plugins.opal.android.feature) }

android { namespace = "app.opal.feature.apps" }

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))
}
