plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

kotlin {
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:apk-model"))
        }
    }
}
