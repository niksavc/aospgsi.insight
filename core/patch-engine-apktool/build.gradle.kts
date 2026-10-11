plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin { androidTarget(); sourceSets { commonMain.dependencies { api(project(":core:patch-api") } } }
android { namespace = "com.bloxinsight.core.apktool"; compileSdk = 35; defaultConfig { minSdk = 24 } }
