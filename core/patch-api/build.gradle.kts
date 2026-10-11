plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin { androidTarget(); sourceSets { commonMain.dependencies { api(project(":core:apk-model") } } }
android { namespace = "com.bloxinsight.core.patchapi"; compileSdk = 35; defaultConfig { minSdk = 24 } }
