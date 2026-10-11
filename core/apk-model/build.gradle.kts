plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget()
}

android {
    namespace = "com.bloxinsight.core.apkmodel"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}
