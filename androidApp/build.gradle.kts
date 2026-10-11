plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.bloxinsight.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bloxinsight.manager"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":core:apk-model"))
    implementation(project(":core:apk-inspector"))
    implementation(project(":integrations:backend-api"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")
}
