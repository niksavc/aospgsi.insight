plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin { androidTarget(); sourceSets { commonMain.dependencies { } } }
android { namespace = "com.bloxinsight.integrations"; compileSdk = 35; defaultConfig { minSdk = 24 } }
